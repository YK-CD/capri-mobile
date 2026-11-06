package com.capri.mobile.overlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.capri.mobile.R
import com.capri.mobile.core.CapriCore
import com.capri.mobile.core.IntentRouter
import com.capri.mobile.ui.MainActivity
import com.capri.mobile.ui.SettingsActivity
import com.capri.mobile.ui.SpriteView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Capri's floating body: a small draggable overlay window plus a focusable chat panel above it. */
class OverlayService : Service() {

    private lateinit var core: CapriCore
    private lateinit var wm: WindowManager
    private val main = Handler(Looper.getMainLooper())

    private var sprite: SpriteView? = null
    private var spriteParams: WindowManager.LayoutParams? = null
    private var panel: View? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var chatText: TextView? = null
    private var panelInput: EditText? = null
    private var panelSend: ImageButton? = null
    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    private var screenW = 1080
    private var screenH = 2400
    private var topInset = 0
    private var bottomInset = 0
    private var sleeping = false
    private var thinking = false

    private val voiceListener: (Boolean) -> Unit = { speaking ->
        main.post { if (speaking) sprite?.startTalking(120_000) else sprite?.stopTalking() }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                IntentRouter.ACTION_SLEEP -> {
                    sleeping = true
                    sprite?.sleep()
                }
                IntentRouter.ACTION_WAKE -> {
                    sleeping = false
                    sprite?.wake()
                }
                CapriCore.ACTION_SETTINGS_CHANGED -> {
                    core.refresh()
                    if (!core.prefs.overlayEnabled()) {
                        stopSelf()
                    } else {
                        closePanel()
                        rebuildSprite()
                    }
                }
                Intent.ACTION_SCREEN_OFF -> sprite?.pauseAnimation()
                Intent.ACTION_SCREEN_ON -> sprite?.resumeAnimation()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        core = CapriCore.get(this)
        core.refresh()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        isRunning = true
        startForegroundCompat()
        if (!Settings.canDrawOverlays(this)) {
            // Permission was revoked while the service was being started; nothing to show.
            core.prefs.setOverlayEnabled(false)
            stopSelf()
            return
        }
        bounds()
        createSprite()
        val filter = IntentFilter().apply {
            addAction(IntentRouter.ACTION_SLEEP)
            addAction(IntentRouter.ACTION_WAKE)
            addAction(CapriCore.ACTION_SETTINGS_CHANGED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
        core.voice.addListener(voiceListener)
        scheduleRoam()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            core.prefs.setOverlayEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!Settings.canDrawOverlays(this)) {
            core.prefs.setOverlayEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closePanel()
        bounds()
        rebuildSprite()
    }

    override fun onDestroy() {
        isRunning = false
        main.removeCallbacksAndMessages(null)
        try { unregisterReceiver(receiver) } catch (_: Exception) {}
        core.voice.removeListener(voiceListener)
        recognizer?.destroy()
        recognizer = null
        closePanel()
        sprite?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        sprite = null
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Keeps Capri's floating character alive."
                    setShowBadge(false)
                }
            )
        }
        val open = PendingIntent.getActivity(
            this, 10,
            Intent(this, MainActivity::class.java)
                .setAction(IntentRouter.ACTION_SHOW_CHAT)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val hide = PendingIntent.getService(
            this, 11, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val n = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Capri")
            .setContentText(getString(R.string.notification_text))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification), "Chat", open).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_notification), "Hide", hide).build())
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(Notification.PRIORITY_LOW)
            .setColor(getColor(R.color.pink))
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (_: Exception) {
            // Notifications blocked or foreground start rejected: the overlay itself is still usable.
        }
    }

    private fun bounds() {
        if (Build.VERSION.SDK_INT >= 30) {
            val m = wm.currentWindowMetrics
            val b = m.bounds
            val ins = m.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
            )
            screenW = b.width()
            screenH = b.height()
            topInset = ins.top
            bottomInset = ins.bottom
        } else {
            @Suppress("DEPRECATION")
            val d = wm.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            d.getRealSize(size)
            screenW = size.x
            screenH = size.y
            topInset = (24 * resources.displayMetrics.density).roundToInt()
            bottomInset = (24 * resources.displayMetrics.density).roundToInt()
        }
    }

    private fun createSprite() {
        val h = dp(core.settings.overlaySizeDp)
        val w = (h * 0.625f).roundToInt()
        val view = SpriteView(this).apply {
            contentDescription = "Capri — tap to chat, drag to move"
            setOnTouchListener(touchListener)
        }
        val x0 = core.prefs.overlayX().let { if (it < 0) screenW - w - dp(8) else it }
        val y0 = core.prefs.overlayY().let { if (it < 0) (screenH * 0.55f).roundToInt() else it }
        val lp = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = x0.coerceIn(0, (screenW - w).coerceAtLeast(0))
            y = y0.coerceIn(topInset, (screenH - bottomInset - h).coerceAtLeast(topInset))
            title = "Capri"
        }
        sprite = view
        spriteParams = lp
        try {
            wm.addView(view, lp)
        } catch (_: Exception) {
            sprite = null
            spriteParams = null
            core.prefs.setOverlayEnabled(false)
            stopSelf()
            return
        }
        view.startEntrance()
    }

    private fun rebuildSprite() {
        val old = sprite
        val lp = spriteParams
        val nx = lp?.x
        val ny = lp?.y
        sprite = null
        if (old != null) try { wm.removeView(old) } catch (_: Exception) {}
        createSprite()
        val np = spriteParams
        if (np != null && nx != null && ny != null) {
            np.x = nx.coerceIn(0, (screenW - np.width).coerceAtLeast(0))
            np.y = ny.coerceIn(topInset, (screenH - bottomInset - np.height).coerceAtLeast(topInset))
            sprite?.let { wm.updateViewLayout(it, np) }
        }
        if (sleeping) sprite?.sleep()
    }

    private val touchListener = View.OnTouchListener { v, ev ->
        val lp = spriteParams ?: return@OnTouchListener false
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = ev.rawX
                downY = ev.rawY
                startX = lp.x
                startY = lp.y
                downAt = System.currentTimeMillis()
                moved = false
                true
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dx) > dp(5) || abs(dy) > dp(5)) moved = true
                if (moved) {
                    lp.x = (startX + dx.toInt()).coerceIn(0, (screenW - lp.width).coerceAtLeast(0))
                    lp.y = (startY + dy.toInt()).coerceIn(topInset, (screenH - bottomInset - lp.height).coerceAtLeast(topInset))
                    try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
                }
                true
            }
            android.view.MotionEvent.ACTION_UP -> {
                val held = System.currentTimeMillis() - downAt
                if (moved) {
                    core.prefs.saveOverlayPos(lp.x, lp.y)
                    scheduleRoam()
                } else if (held > 520) {
                    openMain()
                } else {
                    sprite?.playOnce("glitch", 650)
                    togglePanel()
                }
                true
            }
            android.view.MotionEvent.ACTION_CANCEL -> true
            else -> false
        }
    }

    private fun togglePanel() {
        if (panel != null) closePanel() else openPanel()
    }

    private fun openPanel() {
        closePanel()
        val p = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_panel)
            setPadding(dp(10), dp(8), dp(10), dp(10))
            elevation = dp(10).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(this).apply {
            text = "CAPRI // ONLINE"
            setTextColor(getColor(R.color.pink))
            textSize = 11f
            letterSpacing = 0.12f
        }
        val close = iconButton(R.drawable.ic_close) { closePanel() }
        header.addView(headerTitle, LinearLayout.LayoutParams(0, dp(34), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(34), dp(34)))
        p.addView(header)

        chatText = TextView(this).apply {
            text = lastLine()
            setTextColor(getColor(R.color.text))
            textSize = 13f
            maxLines = 7
            setLineSpacing(0f, 1.08f)
            movementMethod = ScrollingMovementMethod()
        }
        val scroll = ScrollView(this).apply { addView(chatText) }
        p.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(96)))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
        }
        panelInput = EditText(this).apply {
            hint = "Talk to Capri…"
            setHintTextColor(getColor(R.color.muted))
            setTextColor(getColor(R.color.text))
            textSize = 13f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setBackgroundResource(R.drawable.bg_input)
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendFromPanel(); true } else false
            }
            setOnFocusChangeListener { _, focused ->
                if (focused) main.post { positionPanel(top = true) }
            }
        }
        val mic = iconButton(R.drawable.ic_mic) { micFromPanel() }.apply {
            setColorFilter(getColor(R.color.green))
        }
        panelSend = iconButton(R.drawable.ic_send) { sendFromPanel() }.apply {
            setBackgroundResource(R.drawable.bg_button_pink)
            setColorFilter(getColor(R.color.bg_deep))
        }
        row.addView(panelInput, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        row.addView(mic, LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(6) })
        row.addView(panelSend, LinearLayout.LayoutParams(dp(42), dp(42)))
        p.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        footer.addView(textButton("OPEN CHAT") { openMain(); closePanel() })
        footer.addView(textButton("SETTINGS") {
            try {
                startActivity(Intent(this@OverlayService, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                Toast.makeText(this@OverlayService, "Could not open settings.", Toast.LENGTH_SHORT).show()
            }
            closePanel()
        })
        p.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })

        val width = minOf(dp(320), screenW - dp(20))
        val lp = WindowManager.LayoutParams(
            width, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            title = "Capri chat"
        }
        panel = p
        panelParams = lp
        try { wm.addView(p, lp) } catch (e: Exception) { panel = null; return }
        p.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST)
        )
        positionPanel(top = false)
    }

    private fun positionPanel(top: Boolean = false) {
        val p = panel ?: return
        val lp = panelParams ?: return
        val sp = spriteParams ?: return
        val pw = lp.width
        val ph = p.measuredHeight.coerceAtLeast(dp(120))
        lp.x = (sp.x + sp.width / 2 - pw / 2).coerceIn(dp(8), (screenW - pw - dp(8)).coerceAtLeast(dp(8)))
        val above = sp.y - ph - dp(8)
        lp.y = when {
            top -> topInset + dp(8)
            above >= topInset -> above
            sp.y + sp.height + dp(8) + ph <= screenH - bottomInset -> sp.y + sp.height + dp(8)
            else -> (topInset + dp(8)).coerceAtMost((screenH - bottomInset - ph).coerceAtLeast(topInset))
        }
        try { wm.updateViewLayout(p, lp) } catch (_: Exception) {}
    }

    private fun closePanel() {
        val p = panel ?: return
        panel = null
        panelParams = null
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(p.windowToken, 0)
        try { wm.removeView(p) } catch (_: Exception) {}
        chatText = null
        panelInput = null
        panelSend = null
        if (listening) {
            recognizer?.stopListening()
            listening = false
        }
    }

    private fun sendFromPanel() {
        if (thinking) return
        val text = panelInput?.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) return
        panelInput?.setText("")
        thinking = true
        panelSend?.isEnabled = false
        chatText?.text = "you: $text\n…"
        core.stopSpeaking()
        core.send(text, onReply = { reply ->
            thinking = false
            panelSend?.isEnabled = true
            chatText?.text = reply
            sprite?.playOnce("glitch", 500)
            if (!core.settings.ttsEnabled) sprite?.startTalking((reply.length * 55L).coerceIn(1200L, 6000L))
            main.post { positionPanel(top = false) }
        }, onError = { err ->
            thinking = false
            panelSend?.isEnabled = true
            chatText?.append("\n[brain link: $err]")
        })
    }

    private fun micFromPanel() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Open the full chat once to grant the mic.", Toast.LENGTH_LONG).show()
            openMain()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognition service on this phone.", Toast.LENGTH_LONG).show()
            return
        }
        if (listening) {
            recognizer?.stopListening()
            listening = false
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    listening = true
                    panelInput?.hint = "Listening…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    listening = false
                    panelInput?.hint = "Talk to Capri…"
                }
                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    panelInput?.hint = "Talk to Capri…"
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (said.isNotBlank()) {
                        panelInput?.setText(said)
                        sendFromPanel()
                    }
                }
                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                        panelInput?.setText(it)
                        panelInput?.setSelection(it.length)
                    }
                }
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                val lang = core.settings.language
                if (lang != "auto") putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
            }
            startListening(intent)
        }
    }

    private fun lastLine(): String {
        val last = core.memory.allMessages(1).lastOrNull()
        return last?.content?.ifBlank { null } ?: "Yeah? Talk to me, choom."
    }

    private fun openMain() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(IntentRouter.ACTION_SHOW_CHAT)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        } catch (_: Exception) {
            // Background activity starts can be blocked on HyperOS; the notification stays as fallback.
        }
    }

    private fun scheduleRoam() {
        main.removeCallbacks(roam)
        if (!core.settings.roam) return
        main.postDelayed(roam, Random.nextLong(35_000, 75_000))
    }

    private val roam = object : Runnable {
        override fun run() {
            val p = sprite
            val lp = spriteParams
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (p != null && lp != null && panel == null && !sleeping && pm.isInteractive) {
                val nx = Random.nextInt(0, (screenW - lp.width).coerceAtLeast(1))
                val minY = topInset + ((screenH - topInset - bottomInset - lp.height) * 0.25f).toInt()
                val maxY = (screenH - bottomInset - lp.height).coerceAtLeast(minY)
                val ny = if (maxY > minY) Random.nextInt(minY, maxY) else minY
                val sx = lp.x
                val sy = lp.y
                p.playOnce("glitch", 700)
                val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 900
                    addUpdateListener { a ->
                        val f = a.animatedValue as Float
                        lp.x = (sx + (nx - sx) * f).toInt().coerceIn(0, (screenW - lp.width).coerceAtLeast(0))
                        lp.y = (sy + (ny - sy) * f).toInt().coerceIn(topInset, (screenH - bottomInset - lp.height).coerceAtLeast(topInset))
                        try { wm.updateViewLayout(p, lp) } catch (_: Exception) {}
                    }
                    start()
                }
                core.prefs.saveOverlayPos(nx, ny)
            }
            main.postDelayed(this, Random.nextLong(35_000, 75_000))
        }
    }

    private fun iconButton(icon: Int, click: () -> Unit): ImageButton = ImageButton(this).apply {
        setImageResource(icon)
        setBackgroundResource(R.drawable.bg_button)
        setColorFilter(getColor(R.color.text))
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(7), dp(7), dp(7), dp(7))
        setOnClickListener { click() }
    }

    private fun textButton(label: String, click: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 10f
        setTextColor(getColor(R.color.pink))
        setBackgroundResource(R.drawable.bg_button)
        minHeight = 0
        minimumHeight = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setOnClickListener { click() }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var downAt = 0L
    private var moved = false

    companion object {
        private const val CHANNEL = "capri_overlay"
        private const val NOTIF_ID = 1001
        private const val ACTION_STOP = "com.capri.mobile.overlay.STOP"

        @Volatile var isRunning = false
            private set

        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            context.getSharedPreferences("capri", Context.MODE_PRIVATE).edit().putBoolean("overlay_enabled", true).apply()
            val i = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(i) else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
            context.getSharedPreferences("capri", Context.MODE_PRIVATE).edit().putBoolean("overlay_enabled", false).apply()
        }
    }
}
