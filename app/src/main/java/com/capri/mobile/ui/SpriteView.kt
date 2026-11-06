package com.capri.mobile.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.util.LruCache
import android.view.View
import kotlin.random.Random

data class ClipSpec(val frames: Int, val fps: Float, val ratio: Float)

object Sprites {
    val clips = mapOf(
        "idle" to ClipSpec(16, 8f, 1.11f),
        "blink" to ClipSpec(4, 8f, 1.11f),
        "blink_glitch" to ClipSpec(4, 8f, 1.11f),
        "grin" to ClipSpec(16, 8f, 1.11f),
        "glitch" to ClipSpec(10, 15f, 1.11f),
        "dangle" to ClipSpec(24, 10f, 1.11f),
        "sleep" to ClipSpec(16, 6f, 1.11f),
        "wake" to ClipSpec(13, 15f, 1.11f),
        "mouth" to ClipSpec(5, 12f, 1.11f),
        "perch" to ClipSpec(16, 8f, 1f),
        "perch_blink" to ClipSpec(4, 8f, 1f),
        "perch_blink_glitch" to ClipSpec(4, 8f, 1f),
        "perch_glitch" to ClipSpec(10, 15f, 1f)
    )

    private val cache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    // Decode strips off the UI thread so the first blink/glitch doesn't stutter.
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
        Thread(r, "capri-sprite-decode").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
        }
    }

    fun prefetch(context: Context, vararg names: String) {
        val app = context.applicationContext
        io.execute {
            names.forEach { name -> try { strip(app, name) } catch (_: Exception) {} }
        }
    }

    fun strip(context: Context, name: String): Bitmap? {
        cache.get(name)?.let { return it }
        return try {
            val bmp = context.assets.open("capri/$name.png").use { BitmapFactory.decodeStream(it) }
            if (bmp != null) cache.put(name, bmp)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun trim() = cache.evictAll()
}

/** Draws Capri's original sprite strips, with idle blinks, glitches and a talking mouth overlay. */
class SpriteView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val handler = Handler(Looper.getMainLooper())
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private var baseClip = "idle"
    private var baseStart = SystemClock.uptimeMillis()
    private var oneShot: String? = null
    private var oneShotStart = 0L
    private var blinkAt = 0L
    private var blinkStart = 0L
    private var blinkWhich = "blink"
    private var grinStart = 0L
    private var sleeping = false
    private var speakingUntil = 0L
    private var running = false
    private val dst = RectF()
    private val src = Rect()

    var onTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null

    init {
        isClickable = true
        contentDescription = "Capri"
    }

    fun startEntrance() {
        alpha = 0f
        animate().alpha(1f).setDuration(420).start()
        playOnce("glitch")
    }

    fun pauseAnimation() {
        running = false
        handler.removeCallbacks(tick)
    }

    fun resumeAnimation() {
        running = true
        wakeTicker()
    }

    fun playOnce(name: String, ms: Long = 0) {
        oneShot = name
        oneShotStart = SystemClock.uptimeMillis()
        wakeTicker()
    }

    fun sleep() {
        sleeping = true
        oneShot = null
        baseClip = "sleep"
        baseStart = SystemClock.uptimeMillis()
        wakeTicker()
    }

    fun wake() {
        if (!sleeping) return
        sleeping = false
        oneShot = "wake"
        oneShotStart = SystemClock.uptimeMillis()
        baseClip = "idle"
        baseStart = SystemClock.uptimeMillis()
        wakeTicker()
    }

    fun startTalking(ms: Long = 3500) {
        speakingUntil = SystemClock.uptimeMillis() + ms
        wakeTicker()
    }

    fun stopTalking() {
        speakingUntil = 0
        invalidate()
    }

    val isTalking: Boolean get() = SystemClock.uptimeMillis() < speakingUntil

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        nextBlink()
        Sprites.prefetch(context, "idle", "blink", "mouth", "glitch")
        wakeTicker()
    }

    override fun onDetachedFromWindow() {
        running = false
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) {
            running = true
            wakeTicker()
        } else {
            handler.removeCallbacks(tick)
        }
    }

    private fun nextBlink() {
        blinkAt = SystemClock.uptimeMillis() + Random.nextLong(2400, 6400)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running || visibility != VISIBLE) return
            val now = SystemClock.uptimeMillis()
            oneShot?.let { name ->
                val spec = Sprites.clips[name] ?: return@let
                if (now - oneShotStart >= (spec.frames / spec.fps * 1000).toLong()) oneShot = null
            }
            if (!sleeping && baseClip == "idle" && now >= blinkAt) {
                blinkStart = now
                blinkWhich = if (Random.nextInt(100) < 12) "blink_glitch" else "blink"
                if (Random.nextInt(100) < 5) grinStart = now
                nextBlink()
            }
            if (grinStart > 0 && now - grinStart > 2200) grinStart = 0
            invalidate()
            val fps = when {
                isTalking -> 12f
                oneShot == "glitch" || oneShot == "wake" -> 15f
                sleeping -> 6f
                else -> 8f
            }
            handler.removeCallbacks(this)
            handler.postDelayed(this, (1000f / fps).toLong().coerceAtLeast(60))
        }
    }

    private fun wakeTicker() {
        handler.removeCallbacks(tick)
        if (running && visibility == VISIBLE) handler.post(tick)
    }

    private fun activeClip(now: Long): Pair<String, Long> {
        oneShot?.let { return it to oneShotStart }
        if (sleeping) return "sleep" to baseStart
        if (grinStart > 0 && now - grinStart < 2200) return "grin" to grinStart
        if (blinkStart > 0 && now - blinkStart < 500) return blinkWhich to blinkStart
        return baseClip to baseStart
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = SystemClock.uptimeMillis()
        val (clipName, start) = activeClip(now)
        val spec = Sprites.clips[clipName] ?: return
        val strip = Sprites.strip(context, clipName) ?: return
        val frameW = strip.width / spec.frames
        var frame = (((now - start) / 1000f) * spec.fps).toInt()
        if (clipName == "grin") frame %= spec.frames
        frame = frame.coerceIn(0, spec.frames - 1)
        src.set(frame * frameW, 0, (frame + 1) * frameW, strip.height)
        fit(strip.height, frameW)
        canvas.drawBitmap(strip, src, dst, paint)

        if (isTalking && clipName !in setOf("sleep", "wake", "glitch")) {
            val mouth = Sprites.strip(context, "mouth") ?: return
            val mspec = Sprites.clips.getValue("mouth")
            val mw = mouth.width / mspec.frames
            val mframe = ((((now - baseStart) / 1000f) * mspec.fps).toInt() % mspec.frames)
            src.set(mframe * mw, 0, (mframe + 1) * mw, mouth.height)
            // mouth strip shares the body canvas; scale it with the body
            val sx = dst.width() / frameW
            val save = canvas.save()
            canvas.translate(dst.left, dst.top)
            canvas.scale(sx, dst.height() / strip.height)
            canvas.drawBitmap(mouth, src, RectF(0f, 0f, mw.toFloat(), mouth.height.toFloat()), paint)
            canvas.restoreToCount(save)
        }
    }

    private fun fit(frameH: Int, frameW: Int) {
        val vw = width.toFloat()
        val vh = height.toFloat()
        if (vw <= 0f || vh <= 0f) return
        val scale = minOf(vw / frameW, vh / frameH)
        val dw = frameW * scale
        val dh = frameH * scale
        val left = (vw - dw) / 2f
        val top = vh - dh
        dst.set(left, top, left + dw, top + dh)
    }

    override fun performClick(): Boolean {
        super.performClick()
        onTap?.invoke()
        return true
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                pressed = true
                downTime = SystemClock.uptimeMillis()
            }
            android.view.MotionEvent.ACTION_UP -> {
                pressed = false
                val held = SystemClock.uptimeMillis() - downTime
                if (held > 520) onLongPress?.invoke() else performClick()
            }
            android.view.MotionEvent.ACTION_CANCEL -> pressed = false
        }
        return true
    }

    private var pressed = false
    private var downTime = 0L
}
