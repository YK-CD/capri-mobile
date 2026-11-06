package com.capri.mobile.core

import android.content.Context
import org.json.JSONObject

data class ChatMessage(val role: String, val content: String)

class PersonaData(
    val character: String,
    val acks: Map<String, String>,
    val examples: List<Pair<String, String>>,
    val languageExamples: Map<String, List<Pair<String, String>>>,
    val names: Map<String, String>,
    val reminder: String,
    val markers: Map<String, Set<String>>,
    val strongMarkers: Map<String, Set<String>>
) {
    fun ack(language: String): String = acks[language] ?: acks["en"] ?: "Just call me Capri!"
    fun replyName(language: String): String? = names[language]

    fun reminderFor(language: String): String {
        val name = names[language] ?: return reminder
        val marker = "recent conversation language. "
        val at = reminder.indexOf(marker)
        if (at < 0) return reminder
        val replacement = "(Reply only in $name, every word of it, whatever language earlier messages " +
            "were in; never mix in another language. "
        return replacement + reminder.substring(at + marker.length)
    }

    fun build(user: String, language: String, withExamples: Boolean = true): MutableList<ChatMessage> {
        val lang = if (acks.containsKey(language)) language else "en"
        val persona = mobile(character, user)
        val list = mutableListOf(
            ChatMessage("user", persona.replace("{user}", user)),
            ChatMessage("assistant", ack(lang))
        )
        if (withExamples) {
            val picked = if (lang == "en") examples else languageExamples[lang].orEmpty()
            picked.forEach { (q, a) ->
                list += ChatMessage("user", q)
                list += ChatMessage("assistant", a)
            }
        }
        return list
    }

    /** Capri lives on a phone in this port. Keep her voice; change only the desktop furniture. */
    private fun mobile(text: String, user: String): String = text
        .replace("on the desktop of {user}", if (user == "you") "on your phone" else "on the phone of {user}")
        .replace("your desktop", "your phone")
        .replace("their desktop", "their phone")
        .replace("on their windows, stands on the taskbar, and zaps around the screen",
            "hangs over their apps, and zaps around the screen")
        .replace("on their windows, stands on the\ntaskbar, and zaps around the screen",
            "hangs over their apps, and zaps around the screen")
        .replace("real tools on their PC", "real tools on their phone")
        .replace("their PC", "their phone")
        .replace("your PC", "your phone")
        .replace("the desktop of", "the phone of")
        .replace("the desktop", "the phone")
        .replace("Windows OCR", "screen")
}

object Persona {
    @Volatile private var data: PersonaData? = null

    fun get(context: Context): PersonaData {
        data?.let { return it }
        synchronized(this) {
            data?.let { return it }
            val json = context.assets.open("persona.json").bufferedReader().use { it.readText() }
            return parse(json).also { data = it }
        }
    }

    fun parse(json: String): PersonaData {
        val o = JSONObject(json)
        val acks = mutableMapOf<String, String>()
        o.getJSONObject("acks").let { a ->
            a.keys().forEach { k -> acks[k] = a.getString(k) }
        }
        val names = mutableMapOf<String, String>()
        o.getJSONObject("names").let { a ->
            a.keys().forEach { k -> names[k] = a.getString(k) }
        }
        val markers = mutableMapOf<String, Set<String>>()
        o.getJSONObject("markers").let { a ->
            a.keys().forEach { k ->
                val arr = a.getJSONArray(k)
                markers[k] = (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
            }
        }
        val strong = mutableMapOf<String, Set<String>>()
        o.getJSONObject("strong_markers").let { a ->
            a.keys().forEach { k ->
                val arr = a.getJSONArray(k)
                strong[k] = (0 until arr.length()).map { arr.getString(it).lowercase() }.toSet()
            }
        }
        val examples = pairs(o.getJSONArray("examples"))
        val langExamples = mutableMapOf<String, List<Pair<String, String>>>()
        o.getJSONObject("language_examples").let { a ->
            a.keys().forEach { k -> langExamples[k] = pairs(a.getJSONArray(k)) }
        }
        return PersonaData(
            character = o.getString("character"),
            acks = acks,
            examples = examples,
            languageExamples = langExamples,
            names = names,
            reminder = o.getString("reminder"),
            markers = markers,
            strongMarkers = strong
        )
    }

    private fun pairs(arr: org.json.JSONArray): List<Pair<String, String>> =
        (0 until arr.length()).map { i ->
            val p = arr.getJSONArray(i)
            p.getString(0) to p.getString(1)
        }
}

/** Port of capri/brain/persona.py detect_language: short messages inherit the conversation language. */
object LanguageDetector {
    fun detect(text: String, fallback: String, d: PersonaData): String {
        val words = Regex("[\\p{L}\\p{N}’']+").findAll((text ?: "").lowercase())
            .map { it.value }.toSet()
        val scores = d.markers.keys.associateWith { lang ->
            words.intersect(d.markers[lang].orEmpty()).size
        }.toMutableMap()
        d.strongMarkers.forEach { (lang, marks) ->
            if (words.any { it in marks }) scores[lang] = (scores[lang] ?: 0) + 2
        }
        val low = (text ?: "").lowercase()
        if (low.any { it == 'ñ' || it == '¿' || it == '¡' }) scores["es"] = (scores["es"] ?: 0) + 2
        if (low.any { it == 'ã' || it == 'õ' }) scores["pt"] = (scores["pt"] ?: 0) + 2
        if (low.contains('œ')) scores["fr"] = (scores["fr"] ?: 0) + 2
        if (Regex("[а-яё]").containsMatchIn(low)) scores["ru"] = (scores["ru"] ?: 0) + 2
        if (low.any { it in "àèéù" }) scores["it"] = (scores["it"] ?: 0) + 1
        if (low.any { it == 'ì' || it == 'ò' }) scores["it"] = (scores["it"] ?: 0) + 2
        if (low.contains('ß')) scores["de"] = (scores["de"] ?: 0) + 2
        if (low.any { it in "äöü" }) scores["de"] = (scores["de"] ?: 0) + 1
        if (low.any { it in "łśźżąćń" }) scores["pl"] = (scores["pl"] ?: 0) + 2
        val english = scores.remove("en") ?: 0
        val best = scores.maxByOrNull { it.value }?.key ?: "en"
        val bestScore = scores[best] ?: 0
        return if (bestScore >= 2 && bestScore >= english + 2) best
        else if (english >= 2 && english > bestScore) "en"
        else fallback
    }

    fun forText(text: String, fallback: String, d: PersonaData, fixed: String): String =
        if (fixed != "auto" && d.names.containsKey(fixed)) fixed else detect(text, fallback, d)
}
