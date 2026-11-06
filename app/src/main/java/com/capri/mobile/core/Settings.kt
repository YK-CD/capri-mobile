package com.capri.mobile.core

import android.content.Context

data class Settings(
    val userName: String = "you",
    val language: String = "auto",
    val provider: String = "Custom / local",
    val baseUrl: String = "",
    val model: String = "",
    val apiKey: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 160,
    val ttsEnabled: Boolean = true,
    val pitch: Float = 1.12f,
    val rate: Float = 1.05f,
    val remoteTtsUrl: String = "",
    val remoteTtsPrompt: String = "",
    val overlayEnabled: Boolean = false,
    val overlaySizeDp: Int = 190,
    val roam: Boolean = true
) {
    val hasBrain: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

data class Preset(val name: String, val baseUrl: String, val model: String, val needsKey: Boolean)

object Presets {
    val all = listOf(
        Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", true),
        Preset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", true),
        Preset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", true),
        Preset("Ollama (LAN)", "http://192.168.1.100:11434/v1", "llama3.2", false),
        Preset("LM Studio (LAN)", "http://192.168.1.100:1234/v1", "local-model", false),
        Preset("Custom / local", "", "", false)
    )
    fun byName(name: String): Preset? = all.firstOrNull { it.name == name }
}

class Prefs(context: Context) {
    private val app = context.applicationContext
    private val p = app.getSharedPreferences("capri", Context.MODE_PRIVATE)
    private val secure = SecurePrefs(app)

    fun load(): Settings {
        val d = Settings()
        return Settings(
            userName = p.getString("user_name", d.userName) ?: d.userName,
            language = p.getString("language", d.language) ?: d.language,
            provider = p.getString("provider", d.provider) ?: d.provider,
            baseUrl = p.getString("base_url", d.baseUrl) ?: d.baseUrl,
            model = p.getString("model", d.model) ?: d.model,
            apiKey = secure.apiKey(),
            temperature = p.getFloat("temperature", d.temperature),
            maxTokens = p.getInt("max_tokens", d.maxTokens),
            ttsEnabled = p.getBoolean("tts_enabled", d.ttsEnabled),
            pitch = p.getFloat("pitch", d.pitch),
            rate = p.getFloat("rate", d.rate),
            remoteTtsUrl = p.getString("remote_tts", "") ?: "",
            remoteTtsPrompt = p.getString("remote_tts_prompt", "") ?: "",
            overlayEnabled = p.getBoolean("overlay_enabled", d.overlayEnabled),
            overlaySizeDp = p.getInt("overlay_size", d.overlaySizeDp),
            roam = p.getBoolean("roam", d.roam)
        )
    }

    fun save(s: Settings) {
        p.edit()
            .putString("user_name", s.userName.trim().ifBlank { "you" })
            .putString("language", s.language)
            .putString("provider", s.provider)
            .putString("base_url", s.baseUrl.trim())
            .putString("model", s.model.trim())
            .putFloat("temperature", s.temperature)
            .putInt("max_tokens", s.maxTokens)
            .putBoolean("tts_enabled", s.ttsEnabled)
            .putFloat("pitch", s.pitch)
            .putFloat("rate", s.rate)
            .putString("remote_tts", s.remoteTtsUrl.trim())
            .putString("remote_tts_prompt", s.remoteTtsPrompt.trim())
            .putBoolean("overlay_enabled", s.overlayEnabled)
            .putInt("overlay_size", s.overlaySizeDp.coerceIn(120, 320))
            .putBoolean("roam", s.roam)
            .apply()
        secure.putApiKey(s.apiKey.trim())
    }

    fun setOverlayEnabled(on: Boolean) = p.edit().putBoolean("overlay_enabled", on).apply()
    fun overlayEnabled(): Boolean = p.getBoolean("overlay_enabled", false)
    fun lastLanguage(): String = p.getString("last_language", "en") ?: "en"
    fun setLastLanguage(lang: String) = p.edit().putString("last_language", lang).apply()
    fun firstRun(): Boolean = p.getBoolean("first_run", true)
    fun doneFirstRun() = p.edit().putBoolean("first_run", false).apply()
    fun overlayX(): Int = p.getInt("overlay_x", -1)
    fun overlayY(): Int = p.getInt("overlay_y", -1)
    fun saveOverlayPos(x: Int, y: Int) = p.edit().putInt("overlay_x", x).putInt("overlay_y", y).apply()
    fun clearSecure() = secure.clear()
}
