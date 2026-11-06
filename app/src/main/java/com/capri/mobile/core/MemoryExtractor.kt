package com.capri.mobile.core

/** Turns first-person chat into second-person memory notes Capri can use ("I love jazz" -> "you love jazz"). */
object MemoryExtractor {

    fun explicit(text: String): String? {
        val t = text.trim()
        if (t.endsWith("?") || Regex("\\b(?:when|if) (?:you|u) said\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return null
        // Keep timed requests ("me lembre de X em 10 min") for the reminder router, not the memory shard.
        if (Regex("\\b(?:em|daqui a|dentro de)\\s+\\d", RegexOption.IGNORE_CASE).containsMatchIn(t) &&
            Regex("\\blembr", RegexOption.IGNORE_CASE).containsMatchIn(t)
        ) return null
        val en = Regex(
            "^(?:hey\\s+)?(?:capri|campy)?[,\\s]*(?:please\\s+)?(?:remember|don'?t forget|keep in mind|note)\\s+(?:that\\s+)?(.+)$",
            RegexOption.IGNORE_CASE
        ).find(t)
        val pt = Regex(
            "^(?:oi\\s+|ei\\s+)?(?:capri)?[,\\s]*(?:por favor\\s+)?(?:lembre-se|lembra|lembre|não esqueça|nao esqueca|" +
                "anota|anote|guarda|guarde|tenha em mente)\\s+(?:de\\s+)?que\\s+(.+)$",
            RegexOption.IGNORE_CASE
        ).find(t)
        val raw = (en ?: pt)?.groupValues?.get(1) ?: return null
        val fact = toYou(raw)
        return fact.takeIf { it.length in 2..240 }
    }

    fun auto(text: String): List<String> {
        val t = text.trim()
        if (t.endsWith("?")) return emptyList()
        if (Regex("\\b(?:remember|when you said|did i|do i|lembra|lembre|esqueça|esqueca)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) return emptyList()
        val out = mutableListOf<String>()

        Regex("\\bmy name is\\s+([\\p{L}\\p{N} _-]{2,40})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your name is ${it.groupValues[1].trim()}"
        }
        Regex("\\bmeu nome (?:é|e)\\s+([\\p{L}\\p{N} _-]{2,40})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your name is ${it.groupValues[1].trim()}"
        }
        Regex("\\bcall me\\s+([\\p{L}\\p{N} _-]{2,30})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "call them ${it.groupValues[1].trim()}"
        }
        Regex("\\bme chama(?:m)? de\\s+([\\p{L}\\p{N} _-]{2,30})", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "call them ${it.groupValues[1].trim()}"
        }
        Regex("\\bi (?:really |kinda |kind of |totally )?(like|love|hate|dislike|can't stand|don't like)\\s+(.+?)(?:[.!]|$)",
            RegexOption.IGNORE_CASE).find(t)?.let {
            val verb = it.groupValues[1].lowercase()
            val thing = it.groupValues[2].trim().take(80)
            if (thing.isNotBlank()) out += "you $verb $thing"
        }
        Regex("\\beu (?:realmente |meio |até |ate )?(gosto|amo|odeio|detesto|não gosto|nao gosto)\\s+(?:muito\\s+)?(?:de\\s+)?(.+?)(?:[.!]|$)",
            RegexOption.IGNORE_CASE).find(t)?.let {
            val verb = when (it.groupValues[1].lowercase()) {
                "gosto" -> "like"
                "amo" -> "love"
                "odeio", "detesto", "não gosto", "nao gosto" -> "hate"
                else -> "like"
            }
            val thing = it.groupValues[2].trim().take(80)
            if (thing.isNotBlank()) out += "you $verb $thing"
        }
        Regex("\\bi(?:'m| am) from\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you are from ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\beu sou de\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you are from ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bi live in\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you live in ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\beu moro (?:em|no|na)\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you live in ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bmy fav(?:ou?rite)?\\s+([\\p{L} ]{2,25})\\s+is\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your favorite ${it.groupValues[1].trim()} is ${it.groupValues[2].trim().take(60)}"
        }
        Regex("\\bmeu (?:filme|jogo|livro|som|estilo|comida|time|anime|serie|série) favorit[oa] (?:é|e)\\s+(.+?)(?:[.!]|$)",
            RegexOption.IGNORE_CASE).find(t)?.let {
            out += "your favorite thing is ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bi work (?:as|at)\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you work ${if (t.lowercase().contains(" work as ")) "as" else "at"} ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\beu trabalho (?:como|em|na|no)\\s+(.+?)(?:[.!]|$)", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you work ${it.groupValues[1].trim().take(60)}"
        }
        Regex("\\bi(?:'m| am) (allergic to|vegetarian|vegan)\\b", RegexOption.IGNORE_CASE).find(t)?.let {
            out += "you are ${it.groupValues[1].lowercase()}"
        }
        Regex("\\beu sou (alérgic[oa] a|vegetarian[oa]|vegan[oa])\\b", RegexOption.IGNORE_CASE).find(t)?.let {
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
        // Portuguese first person -> second person, only whole words.
        t = Regex("\\beu sou\\b", RegexOption.IGNORE_CASE).replace(t, "você é")
        t = Regex("\\beu estou\\b", RegexOption.IGNORE_CASE).replace(t, "você está")
        t = Regex("\\beu tô\\b", RegexOption.IGNORE_CASE).replace(t, "você está")
        t = Regex("\\beu tenho\\b", RegexOption.IGNORE_CASE).replace(t, "você tem")
        t = Regex("\\beu gosto\\b", RegexOption.IGNORE_CASE).replace(t, "você gosta")
        t = Regex("\\beu amo\\b", RegexOption.IGNORE_CASE).replace(t, "você ama")
        t = Regex("\\beu odeio\\b", RegexOption.IGNORE_CASE).replace(t, "você odeia")
        t = Regex("\\beu moro\\b", RegexOption.IGNORE_CASE).replace(t, "você mora")
        t = Regex("\\beu trabalho\\b", RegexOption.IGNORE_CASE).replace(t, "você trabalha")
        t = Regex("\\bmeu\\b", RegexOption.IGNORE_CASE).replace(t, "seu")
        t = Regex("\\bminha\\b", RegexOption.IGNORE_CASE).replace(t, "sua")
        t = Regex("\\bmeus\\b", RegexOption.IGNORE_CASE).replace(t, "seus")
        t = Regex("\\bminhas\\b", RegexOption.IGNORE_CASE).replace(t, "suas")
        t = Regex("\\bmim\\b", RegexOption.IGNORE_CASE).replace(t, "você")
        return t.trim().trimEnd('.', '!', ' ').ifBlank { t }
    }
}
