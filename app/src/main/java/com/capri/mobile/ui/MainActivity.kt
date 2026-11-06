package com.capri.mobile.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.capri.mobile.R
import com.capri.mobile.core.CapriCore
import com.capri.mobile.overlay.OverlayService
import java.util.Locale

class MainActivity : android.app.Activity() {

    private lateinit var core: CapriCore
    private lateinit var sprite: SpriteView
    private lateinit var bubble: TextView
    private lateinit var status: TextView
    private lateinit var overlayBtn: Button
    private lateinit var input: EditText
    private lateinit var micBtn: ImageButton
    private lateinit var list: ListView
    private lateinit var adapter: ChatAdapter
    private val main = Handler(Looper.getMainLooper())

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var pendingRow: Row? = null
    private val speechListener: (Boolean) -> Unit = { speaking ->
        runOnUiThread {
            if (speaking) sprite.startTalking(120_000) else sprite.stopTalking()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        core = CapriCore.get(this)
        core.refresh()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }

        sprite = findViewById(R.id.sprite)
        bubble = findViewById(R.id.tvBubble)
        status = findViewById(R.id.tvStatus)
        overlayBtn = findViewById(R.id.btnOverlay)
        input = findViewById(R.id.etInput)
        micBtn = findViewById(R.id.btnMic)
        list = findViewById(R.id.listChat)

        adapter = ChatAdapter()
        list.adapter = adapter

        val history = core.memory.allMessages(100)
        history.forEach { adapter.add(Row(it.content, it.role == "user")) }
        adapter.notifyDataSetChanged()
        scrollBottom()

        val welcome = core.welcomeIfEmpty()
        if (welcome != null) {
            adapter.add(Row(welcome, false))
            adapter.notifyDataSetChanged()
            scrollBottom()
            showBubble(welcome)
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { sendInput() }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendInput(); true
            } else false
        }
        micBtn.setOnClickListener { toggleMic() }
        overlayBtn.setOnClickListener { toggleOverlay() }

        sprite.setOnClickListener {
            showBubble(adapter.lastBot().ifBlank { "Yeah? Talk to me, choom." })
            sprite.playOnce("glitch", 700)
        }
        core.voice.addListener(speechListener)
        sprite.startEntrance()
        updateChrome()
        if (intent?.action == ACTION_SHOW_CHAT) input.requestFocus()
    }

    private fun sendInput() {
        if (pendingRow != null) return
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        input.setText("")
        val userRow = Row(text, true)
        adapter.add(userRow)
        val thinking = Row("…", false)
        pendingRow = thinking
        adapter.add(thinking)
        adapter.notifyDataSetChanged()
        scrollBottom()
        input.hideKeyboardIfNeeded()
        core.send(text, onReply = { reply ->
            runOnUiThread {
                pendingRow?.let { adapter.remove(it) }
                pendingRow = null
                adapter.add(Row(reply, false))
                adapter.notifyDataSetChanged()
                scrollBottom()
                showBubble(reply)
                if (!core.settings.ttsEnabled) sprite.startTalking((reply.length * 55L).coerceIn(1200L, 6000L))
            }
        }, onError = { err ->
            runOnUiThread { status.text = "BRAIN LINK FLATLINED // $err" }
        })
        core.stopSpeaking()
    }

    private fun showBubble(text: String) {
        bubble.text = text
        bubble.visibility = View.VISIBLE
        bubble.animate().alpha(1f).setDuration(160).start()
        main.removeCallbacks(hideBubble)
        main.postDelayed(hideBubble, 14_000)
    }

    private val hideBubble = Runnable {
        bubble.animate().alpha(0f).setDuration(250).withEndAction { bubble.visibility = View.GONE }.start()
    }

    private fun updateChrome() {
        val s = core.settings
        val on = OverlayService.isRunning || s.overlayEnabled
        overlayBtn.text = if (on) getString(R.string.overlay_on) else getString(R.string.overlay_off)
        overlayBtn.setTextColor(if (on) getColor(R.color.green) else getColor(R.color.muted))
        status.text = when {
            s.hasBrain -> "BRAIN // ${s.model.take(26).uppercase(Locale.ROOT)}"
            else -> "OFFLINE // NO BRAIN JACKED IN"
        }
        status.setTextColor(if (s.hasBrain) getColor(R.color.green) else getColor(R.color.pink))
    }

    private fun toggleOverlay() {
        if (OverlayService.isRunning) {
            OverlayService.stop(this)
            core.prefs.setOverlayEnabled(false)
            updateChrome()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.permission_overlay, Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                Toast.makeText(this, "Open Settings > Apps > Capri > Display over other apps.", Toast.LENGTH_LONG).show()
            }
            return
        }
        try {
            OverlayService.start(this)
            core.prefs.setOverlayEnabled(true)
        } catch (_: Exception) {
            Toast.makeText(this, "HyperOS blocked the overlay service. Allow background activity in Settings.", Toast.LENGTH_LONG).show()
            core.prefs.setOverlayEnabled(false)
        }
        updateChrome()
    }

    private fun toggleMic() {
        if (listening) {
            recognizer?.stopListening()
            listening = false
            micBtn.setColorFilter(getColor(R.color.green))
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        startListening()
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "No speech recognition service on this phone. Install/enable one in Android settings.", Toast.LENGTH_LONG).show()
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listening = true
                    micBtn.setColorFilter(getColor(R.color.pink))
                    input.hint = "Listening…"
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    listening = false
                    micBtn.setColorFilter(getColor(R.color.green))
                    input.hint = getString(R.string.chat_hint)
                    if (error != SpeechRecognizer.ERROR_CLIENT && error != SpeechRecognizer.ERROR_NO_MATCH) {
                        Toast.makeText(this@MainActivity, "Ears glitched (code $error).", Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    micBtn.setColorFilter(getColor(R.color.green))
                    input.hint = getString(R.string.chat_hint)
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (said.isNotBlank()) {
                        input.setText(said)
                        sendInput()
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                        input.setText(it)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                val lang = core.settings.language
                if (lang != "auto") putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
                else putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            }
            startListening(intent)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
        if (requestCode == REQ_NOTIF) {
            // Foreground service still runs; the notification may be hidden by the user.
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_CHAT) {
            input.requestFocus()
        }
    }

    override fun onResume() {
        super.onResume()
        core.refresh()
        sprite.resumeAnimation()
        // Returning from the overlay-permission screen: honour a saved "on" state.
        if (core.prefs.overlayEnabled() && !OverlayService.isRunning && Settings.canDrawOverlays(this)) {
            try { OverlayService.start(this) } catch (_: Exception) {}
        }
        updateChrome()
    }

    override fun onPause() {
        sprite.pauseAnimation()
        super.onPause()
    }

    override fun onDestroy() {
        core.voice.removeListener(speechListener)
        recognizer?.destroy()
        recognizer = null
        super.onDestroy()
    }

    private fun scrollBottom() {
        list.post { if (adapter.count > 0) list.setSelection(adapter.count - 1) }
    }

    private fun EditText.hideKeyboardIfNeeded() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }

    data class Row(val text: String, val user: Boolean)

    private inner class ChatAdapter : BaseAdapter() {
        private val rows = mutableListOf<Row>()

        fun add(row: Row) = rows.add(row)
        fun remove(row: Row) {
            rows.remove(row)
            notifyDataSetChanged()
        }
        fun lastBot(): String = rows.lastOrNull { !it.user }?.text.orEmpty()
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(this@MainActivity).inflate(R.layout.item_message, parent, false)
            val row = rows[position]
            val root = v.findViewById<LinearLayout>(R.id.rowRoot)
            val tv = v.findViewById<TextView>(R.id.tvMessage)
            tv.text = row.text
            if (row.user) {
                root.gravity = Gravity.END
                tv.setBackgroundResource(R.drawable.bg_bubble_user)
                tv.setTextColor(getColor(R.color.bg_deep))
            } else {
                root.gravity = Gravity.START
                tv.setBackgroundResource(R.drawable.bg_panel)
                tv.setTextColor(getColor(R.color.text))
            }
            return v
        }
    }

    companion object {
        private const val REQ_MIC = 41
        private const val REQ_NOTIF = 42
        private const val ACTION_SHOW_CHAT = "com.capri.mobile.SHOW_CHAT"
    }
}
