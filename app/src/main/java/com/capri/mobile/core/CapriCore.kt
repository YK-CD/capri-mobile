package com.capri.mobile.core

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** Shared brain + memory + voice used by the app screen and the floating overlay. */
class CapriCore private constructor(context: Context) {
    val appContext: Context = context.applicationContext
    val prefs = Prefs(appContext)
    val memory = MemoryStore(appContext)
    val persona = Persona.get(appContext)
    val voice = CapriVoice(appContext)
    private val llm = LlmClient()
    private val router = IntentRouter(appContext, memory)
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "capri-brain").apply { priority = Thread.NORM_PRIORITY - 1 }
    }
    private val main = Handler(Looper.getMainLooper())

    @Volatile var settings: Settings = prefs.load()
        private set
    @Volatile var lastError: String = ""
        private set

    fun refresh() {
        settings = prefs.load()
        voice.update(settings)
    }

    fun history(): List<ChatMessage> = memory.history(12)

    fun welcomeIfEmpty(): String? {
        if (memory.messageCount() > 0) return null
        val line = persona.ack(prefs.lastLanguage())
        memory.addMessage("assistant", line)
        return line
    }

    /**
     * Runs one turn off the UI thread. [onReply] arrives on the main thread with Capri's line; [onError] is for
     * diagnostics (the reply itself still carries an in-character error line).
     */
    fun send(text: String, onReply: (String) -> Unit, onThinking: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        settings = prefs.load()
        voice.update(settings)
        onThinking?.let { thinking -> main.post { thinking() } }
        io.execute {
            val language = LanguageDetector.forText(clean, prefs.lastLanguage(), persona, settings.language)
            prefs.setLastLanguage(language)
            val history = memory.history(12)
            memory.addMessage("user", clean)
            MemoryExtractor.auto(clean).forEach { memory.remember(it) }

            var reply: String
            var error = ""
            val route = try {
                router.route(clean, language)
            } catch (e: Exception) {
                null
            }
            if (route != null) {
                reply = route.reply
            } else if (!settings.hasBrain) {
                reply = OfflineBrain.reply(clean, language, persona)
            } else {
                try {
                    reply = askModel(clean, history, language)
                } catch (e: BrainDown) {
                    error = e.message ?: "Brain link failed."
                    lastError = error
                    reply = OfflineBrain.errorLine(persona, language, error)
                } catch (e: Exception) {
                    error = e.message ?: e.javaClass.simpleName
                    lastError = error
                    reply = OfflineBrain.errorLine(persona, language, error)
                }
            }
            lastError = error
            reply = sanitize(reply)
            memory.addMessage("assistant", reply)
            val finalReply = reply
            main.post {
                onReply(finalReply)
                if (error.isNotBlank()) onError?.invoke(error)
                voice.speak(finalReply, language)
            }
            if (settings.hasBrain && error.isBlank()) maybeSummarize(language)
        }
    }

    private fun askModel(text: String, history: List<ChatMessage>, language: String): String {
        val msgs = persona.build(settings.userName, language, true)
        msgs += history
        val notes = (memory.relevant(text, settings.userName, 10) + memory.episodes(text).map { "($it)" })
            .distinct()
        val last = if (notes.isEmpty()) text else text + "\n\n[" + notes.joinToString(" ") + "]"
        msgs += ChatMessage("user", last + "\n" + persona.reminderFor(language))
        val cfg = LlmClient.Config(
            baseUrl = settings.baseUrl,
            model = settings.model,
            apiKey = settings.apiKey,
            temperature = settings.temperature,
            maxTokens = settings.maxTokens
        )
        return llm.chat(cfg, msgs)
    }

    private fun sanitize(raw: String): String {
        var t = raw.trim()
        t = t.replace(Regex("(?is)<think\\b[^>]*>.*?</think\\s*>"), "")
        t = t.replace(Regex("\\[\\[.*?]]"), "")
        t = t.replace(Regex("^\\s*(?:capri|campy)\\s*:\\s*", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\*\\*|__"), "")
        t = t.replace(Regex("\\n{3,}"), "\n\n")
        return t.trim().ifBlank { "Done, choom." }
    }

    private fun maybeSummarize(language: String) {
        try {
            val old = memory.oldUnsummarized(14)
            if (old.isEmpty()) return
            val transcript = old.joinToString("\n") { m ->
                (if (m.role == "user") settings.userName else "Capri") + ": " + m.content
            }
            val cfg = LlmClient.Config(settings.baseUrl, settings.model, settings.apiKey, 0.2f, 90)
            val summary = llm.summarize(cfg, transcript)
            if (summary.isNotBlank()) {
                memory.addEpisode(summary)
                memory.archive(old.map { it.id })
            }
        } catch (_: Exception) {
            // Summaries are a luxury; never break a chat over them.
        }
    }

    fun speak(text: String, language: String = prefs.lastLanguage()) = voice.speak(text, language)
    fun stopSpeaking() = voice.stop()
    fun clearChat() = memory.clearHistory()
    fun clearAll() { memory.clearAll(); prefs.clearSecure() }
    fun lastLanguage(): String = prefs.lastLanguage()

    fun notifySettingsChanged() {
        refresh()
        appContext.sendBroadcast(Intent(ACTION_SETTINGS_CHANGED).setPackage(appContext.packageName))
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.capri.mobile.SETTINGS_CHANGED"

        @Volatile private var instance: CapriCore? = null
        fun get(context: Context): CapriCore = instance ?: synchronized(this) {
            instance ?: CapriCore(context).also { instance = it }
        }
    }
}
