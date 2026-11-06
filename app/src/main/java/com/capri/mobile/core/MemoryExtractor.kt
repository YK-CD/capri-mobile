package com.capri.mobile.core

/** Turns first-person chat into second-person memory notes Capri can use ("I love jazz" -> "you love jazz"). */
object MemoryExtractor {

    fun explicit(text: String): String? {
        val t = text.trim()
        if (t.endsWith("?") || Regex("\\b(?:when|if) (?:you|u) said\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return null
        val m = Regex("^(?:hey\\s+)?(?:capri|campy)?[,\\s]*(?:please\\s+)?(?:remember|don'?t forget|keep in mind|note)\\s+(?:that\\s+)?(.+)$",
            RegexOption.IGNORE_CASE).find(t) ?: return null
        val fact = toYou(m.groupValues[1])
        return fact.takeIf { it.length in 2..240 }
    }

    fun auto(text: String): List<String> {
        val t = text.trim()
        if (t.endsWith("?")) return emptyList()
        if (Regex("\\b(?:remember|when you said|did i|do i)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return emptyList()
        val out = mutableListOf<String>()

        Regex("\\bmy name is\\s+([\\p{L}\\p{N} _-]{2,40})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your name is ${it.groupValues[1].trim()}"
        }
        Regex("\\bcall me\\s+([\\p{L}\\p{N} _-]{2,30})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "call them ${it.groupValues[1].trim()}"
        }
        Regex("\\bi (?:really |kinda |kind of |totally )?(like|love|hate|dislike|can't stand|don't like)\\s+(.+?)(?:[.!]|$)",
            RegexOption.IGNORE_CASE).find(t)?.let {
            val verb = it.groupValues[1].lowercase()
            val thing = it.groupValues[2].trim().take(80)
            if (thing.isNotBlank()) out += "you $verb $thing"
        }
        Regex("\\bi(?:'m| am) from\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you are from ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bi live in\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you live in ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bmy fav(?:ou?rite)?\\s+([\\p{L} ]{2,25})\\s+is\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your favorite ${it.groupValues[1].trim()} is ${it.groupValues[2].trim().take(60)}"
        }
        Regex("\\bi work (?:as|at)\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you work ${if (t.lowercase().contains(" work as ")) "as" else "at"} ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bi(?:'m| am) (allergic to|vegetarian|vegan)\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you are ${it.groupValues[1].lowercase()}"
        }
        return out.distinct().take(3)
    }

    /** First person -> the "you" voice the persona prompt asks for. */
    fun toYou(raw: String): String {
        var t = raw.trim().replace(Regex("\\s+"), " ")
        t = Regex("\\bI'm\\b", RegexOption.IGNORE_CASE).replace(t, "you're")
        t = Regex("\\bI am\\b", RegexOption.IGNORE_CASE).replace(t, "you are")
        t = Regex("\\bI've\\b", RegexOption.IGNORE_CASE).replace(t, "you've")
        t = Regex("\\bI'll\\b", RegexOption.IGNORE_CASE).replace(t, "you'll")
        t = Regex("\\bI'd\\b", RegexOption.IGNORE_CASE).replace(t, "you'd")
        t = Regex("\\bmyself\\b", RegexOption.IGNORE_CASE).replace(t, "yourself")
        t = Regex("\\bmy\\b", RegexOption.IGNORE_CASE).replace(t, "your")
        t = Regex("\\bmine\\b", RegexOption.IGNORE_CASE).replace(t, "yours")
        t = Regex("\\bme\\b", RegexOption.IGNORE_CASE).replace(t, "you")
        t = Regex("\\bI\\b").replace(t, "you")
        return t.trim().trimEnd('.', '!', ' ').ifBlank { t }
    }
}
