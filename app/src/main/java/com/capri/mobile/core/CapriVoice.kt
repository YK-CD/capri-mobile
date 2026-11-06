package com.capri.mobile.core

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/** System TTS by default, optional Capri's original GPT-SoVITS cloned voice over the network. */
class CapriVoice(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val listeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var player: MediaPlayer? = null
    private var settings = Settings()
    private var generation = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = notify(true)
                    override fun onDone(utteranceId: String?) = notify(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = notify(false)
                })
                pending?.invoke()
                pending = null
            }
        }
    }

    fun update(s: Settings) {
        settings = s
    }

    fun addListener(l: (Boolean) -> Unit) { listeners += l }
    fun removeListener(l: (Boolean) -> Unit) { listeners -= l }

    fun speak(text: String, language: String) {
        if (!settings.ttsEnabled) return
        val clean = clean(text)
        if (clean.isBlank()) return
        stop()
        val myGen = generation
        if (settings.remoteTtsUrl.isNotBlank()) {
            io.execute {
                try {
                    val bytes = remoteTts(clean, language)
                    if (myGen != generation) return@execute
                    val f = File(context.cacheDir, "capri_tts.wav")
                    f.writeBytes(bytes)
                    main.post {
                        if (myGen != generation) return@post
                        try {
                            player = MediaPlayer().apply {
                                setDataSource(f.absolutePath)
                                setOnPreparedListener { it.start(); notify(true) }
                                setOnCompletionListener { notify(false); it.release(); player = null }
                                setOnErrorListener { mp, _, _ -> notify(false); mp.release(); player = null; true }
                                prepareAsync()
                            }
                        } catch (_: Exception) {
                            system(clean, language)
                        }
                    }
                } catch (_: Exception) {
                    main.post { system(clean, language) }
                }
            }
        } else {
            system(clean, language)
        }
    }

    private fun system(clean: String, language: String) {
        val doSpeak = {
            val t = tts
            if (t != null) {
                t.setPitch(settings.pitch.coerceIn(0.5f, 2.0f))
                t.setSpeechRate(settings.rate.coerceIn(0.5f, 2.0f))
                if (t.setLanguage(locale(language)) < TextToSpeech.LANG_AVAILABLE) {
                    t.setLanguage(Locale.ENGLISH)
                }
                notify(true)
                t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "capri")
            }
        }
        if (ready) doSpeak() else pending = doSpeak
    }

    fun stop() {
        generation++
        player?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        player = null
        tts?.stop()
        notify(false)
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        io.shutdownNow()
    }

    private fun notify(speaking: Boolean) {
        main.post { listeners.forEach { it(speaking) } }
    }

    private fun locale(language: String): Locale = when (language) {
        "es" -> Locale("es", "ES")
        "pt" -> Locale("pt", "BR")
        "fr" -> Locale.FRENCH
        "ru" -> Locale("ru", "RU")
        "it" -> Locale.ITALIAN
        "de" -> Locale.GERMAN
        "pl" -> Locale("pl", "PL")
        else -> Locale.US
    }

    private fun clean(text: String): String = text
        .replace(Regex("\\[\\[[^]]*]]"), "")
        .replace(Regex("(?i)<think\\b[^>]*>.*?</think\\s*>"), "")
        .replace(Regex("[*_#`>]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun remoteTts(text: String, language: String): ByteArray {
        val url = URL(settings.remoteTtsUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 8_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        val body = JSONObject().apply {
            put("text", text)
            put("text_lang", if (language == "auto") "en" else language)
            put("prompt_text", settings.remoteTtsPrompt)
            put("prompt_lang", "en")
            put("ref_audio_path", "")
            put("speed_factor", 1.0)
        }
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        if (conn.responseCode !in 200..299) throw IllegalStateException("TTS HTTP ${conn.responseCode}")
        val bytes = conn.inputStream.readBytes()
        conn.disconnect()
        if (bytes.size < 100) throw IllegalStateException("empty TTS audio")
        return bytes
    }
}
