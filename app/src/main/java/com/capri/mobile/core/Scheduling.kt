package com.capri.mobile.core

import java.text.Normalizer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Clock(val hour: Int, val minute: Int)
data class ParsedWhen(val at: Long, val date: LocalDate, val clock: Clock?)

/**
 * Tiny natural-language date/time parser for Capri's mobile hands.
 *
 * It understands a deliberately narrow, predictable slice of English and Brazilian Portuguese:
 * "in 10 minutes", "em 10 minutos", "tomorrow at 3pm", "amanhã às 15h", "hoje às 9h",
 * "dia 12 às 8h", "sexta às 18h", "12/06 às 19h". Anything fancier is left to the model.
 */
object SchedulingParser {

    private val UNITS = mapOf(
        "s" to 1_000L, "seg" to 1_000L, "segs" to 1_000L, "segundo" to 1_000L, "segundos" to 1_000L,
        "sec" to 1_000L, "secs" to 1_000L, "second" to 1_000L, "seconds" to 1_000L,
        "m" to 60_000L, "min" to 60_000L, "mins" to 60_000L, "minuto" to 60_000L, "minutos" to 60_000L,
        "minute" to 60_000L, "minutes" to 60_000L,
        "h" to 3_600_000L, "hr" to 3_600_000L, "hrs" to 3_600_000L, "hora" to 3_600_000L,
        "horas" to 3_600_000L, "hour" to 3_600_000L, "hours" to 3_600_000L,
        "d" to 86_400_000L, "dia" to 86_400_000L, "dias" to 86_400_000L,
        "day" to 86_400_000L, "days" to 86_400_000L
    )

    private val DURATION = Regex(
        "(\\d{1,4})\\s*(segundos|segundo|segs|seg|seconds|second|secs|sec|minutos|minuto|mins|min|minutes|minute|" +
            "horas|hora|hrs|hr|hours|hour|dias|dia|days|day|s|m|h|d)\\b"
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "domingo" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY,
        "segunda" to DayOfWeek.MONDAY, "segunda-feira" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "terca" to DayOfWeek.TUESDAY, "terca-feira" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY, "quarta-feira" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "quinta" to DayOfWeek.THURSDAY, "quinta-feira" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "sexta" to DayOfWeek.FRIDAY, "sexta-feira" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY
    )

    private const val MERIDIEM = "(am|pm|da manha|da tarde|da noite|de la manana|de la tarde|de la noche)"

    /** Lowercase, accent-free form used for matching. Keeps punctuation and length reasonably intact. */
    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

    /** "em 10 minutos" / "in 10 minutes" -> milliseconds from now, or null. */
    fun durationAfter(text: String): Long? {
        val n = normalize(text)
        val m = Regex(
            "(?:\\bem|\\bdaqui\\s+a|\\bdaqui|\\bdentro\\s+de|\\bin)\\s+" + DURATION.pattern
        ).find(n) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        val unit = UNITS[m.groupValues[2]] ?: return null
        return (amount * unit).coerceIn(1_000L, 40L * 86_400_000L)
    }

    /** Duration mentioned anywhere ("timer de 5 minutos", "2 hours"), or null. */
    fun duration(text: String): Long? {
        val n = normalize(text)
        val m = DURATION.find(n) ?: return null
        val amount = m.groupValues[1].toLongOrNull() ?: return null
        val unit = UNITS[m.groupValues[2]] ?: return null
        return (amount * unit).coerceIn(1_000L, 40L * 86_400_000L)
    }

    /** Extracts a clock time from an alarm/calendar phrase. "às 7h30", "19:45", "7 da noite". */
    fun parseClock(text: String): Clock? {
        val n = normalize(text)
        val candidates = listOf(
            Regex("(?:\\bas\\b|\\bat\\b|\\bpelas\\b|\\bpara\\b|\\bfor\\b|@)\\s*(\\d{1,2})(?:\\s*[:h]\\s*(\\d{2}))?\\s*" + MERIDIEM + "?"),
            Regex("(\\d{1,2})\\s*[:h]\\s*(\\d{2})\\s*" + MERIDIEM + "?"),
            Regex("(\\d{1,2})\\s*h(?:oras?)?\\s*" + MERIDIEM + "?"),
            Regex("(\\d{1,2})(?:\\s*[:h]\\s*(\\d{2}))?\\s*" + MERIDIEM)
        )
        for (re in candidates) {
            val m = re.find(n) ?: continue
            var hour = m.groupValues[1].toIntOrNull() ?: continue
            val minute = m.groupValues[2].toIntOrNull() ?: 0
            val mer = m.groupValues[3]
            hour = applyMeridiem(hour, mer)
            if (hour in 0..23 && minute in 0..59) return Clock(hour, minute)
        }
        return null
    }

    private fun applyMeridiem(hour: Int, mer: String): Int = when (mer) {
        "pm", "da tarde", "da noite", "de la tarde", "de la noche" -> if (hour in 1..11) hour + 12 else hour
        "am", "da manha", "de la manana" -> if (hour == 12) 0 else hour
        else -> hour
    }

    /**
     * Parses a concrete moment. Returns null when the phrase holds neither a date nor a time.
     * Times in the past without an explicit date roll to tomorrow.
     */
    fun parseWhen(text: String, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedWhen? {
        val n = normalize(text)
        durationAfter(n)?.let { rel ->
            val at = nowMillis + rel
            return ParsedWhen(at, Instant.ofEpochMilli(at).atZone(zone).toLocalDate(), null)
        }
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

        var date: LocalDate? = null
        val explicitDay: Boolean
        when {
            n.contains("depois de amanha") || n.contains("day after tomorrow") -> date = today.plusDays(2)
            Regex("\\bamanha\\b").containsMatchIn(n) || Regex("\\btomorrow\\b").containsMatchIn(n) ||
                Regex("\\bmanana\\b").containsMatchIn(n) -> date = today.plusDays(1)
            Regex("\\bhoje\\b").containsMatchIn(n) || Regex("\\btoday\\b").containsMatchIn(n) ||
                Regex("\\bhoy\\b").containsMatchIn(n) -> date = today
        }
        val domMatch = Regex("\\bdia\\s+(\\d{1,2})\\b").find(n)
        val slashMatch = Regex("\\b(\\d{1,2})/(\\d{1,2})(?:/(\\d{2,4}))?\\b").find(n)
        if (date == null) date = weekdayDate(n, today)
        if (date == null && domMatch != null) date = nextDayOfMonth(domMatch.groupValues[1].toIntOrNull(), today)
        if (date == null && slashMatch != null) {
            date = nextSlashDate(
                slashMatch.groupValues[1].toIntOrNull(),
                slashMatch.groupValues[2].toIntOrNull(),
                slashMatch.groupValues[3].toIntOrNull(),
                today
            )
        }
        explicitDay = date != null && (domMatch != null || slashMatch != null || weekdayDate(n, today) != null)
        val clock = parseClock(n)
        if (date == null && clock == null) return null

        var day = date ?: today
        val time = clock?.let { LocalTime.of(it.hour, it.minute) } ?: LocalTime.of(9, 0)
        var at = LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli()
        val past = at <= nowMillis + 1_000
        if (past && !explicitDay) {
            // "hoje às 8h" said at 9h, or just "às 8h": the next occurrence is tomorrow.
            day = day.plusDays(1)
            at = LocalDateTime.of(day, time).atZone(zone).toInstant().toEpochMilli()
        }
        return ParsedWhen(at, day, clock)
    }

    private fun weekdayDate(n: String, today: LocalDate): LocalDate? {
        val wanted = WEEKDAYS.entries.firstOrNull { (name, _) ->
            if (name.contains('-')) n.contains(name) else Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(n)
        }?.value ?: return null
        var ahead = (wanted.value - today.dayOfWeek.value + 7) % 7
        if (ahead == 0) ahead = 7
        return today.plusDays(ahead.toLong())
    }

    private fun nextDayOfMonth(day: Int?, today: LocalDate): LocalDate? {
        if (day == null || day !in 1..31) return null
        var candidate = safeDate(today.year, today.monthValue, day, today)
        if (candidate == null || candidate.isBefore(today)) {
            val month = today.plusMonths(1)
            candidate = safeDate(month.year, month.monthValue, day, today)
        }
        return candidate
    }

    private fun nextSlashDate(day: Int?, month: Int?, year: Int?, today: LocalDate): LocalDate? {
        if (day == null || month == null || day !in 1..31 || month !in 1..12) return null
        val y = when {
            year == null -> today.year
            year < 100 -> 2000 + year
            else -> year
        }
        var date = safeDate(y, month, day, today) ?: return null
        if (date.isBefore(today)) date = safeDate(y + 1, month, day, today) ?: return null
        return date
    }

    private fun safeDate(year: Int, month: Int, day: Int, today: LocalDate): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: Exception) {
        null
    }

    /** "reunião amanhã às 15h" -> "reunião". Returns null when nothing but time remains. */
    fun stripTimeExpression(raw: String): String {
        val tokens = Regex("\\S+").findAll(raw.trim()).toList()
        val cut = markerIndex(tokens)
        val kept = if (cut > 0) raw.substring(0, tokens[cut].range.first) else raw
        return kept.trim()
            .trimEnd(',', '.', '!', '?', '-', ';', ' ')
            .trim()
    }

    /** Best-effort event title from "agende uma reunião com o time amanhã às 15h". */
    fun calendarTitle(raw: String): String? {
        var t = raw.trim().replace(Regex("\\s+"), " ")
        t = t.replace(Regex("^(?:hey\\s+)?capri[,\\s]*", RegexOption.IGNORE_CASE), "")
        t = t.replace(
            Regex(
                "^(?:agende|agendar|agenda|marque|marcar|marco|crie|criar|adicione|adicionar|coloque|colocar|" +
                    "schedule|create|add|new event|novo evento)\\b[\\s,]*",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
        t = t.replace(Regex("^(?:um|uma|o|a|os|as|the|an?)\\s+", RegexOption.IGNORE_CASE), "")
        val tokens = Regex("\\S+").findAll(t).toList()
        val cut = markerIndex(tokens)
        if (cut == 0) return null
        val kept = if (cut > 0) t.substring(0, tokens[cut].range.first) else t
        var title = kept.trim().trimEnd(',', '.', '!', '?', '-', ';', ' ')
        title = title.replace(
            Regex("\\s+(?:de|do|da|dos|das|na|no|nas|nos|para|pra|com|e|on|at|for|in|the)$", RegexOption.IGNORE_CASE),
            ""
        ).trim()
        title = title.replace(Regex("\\s+"), " ")
        if (title.length < 2) return null
        return title.take(120)
    }

    private fun markerIndex(tokens: List<MatchResult>): Int {
        val dayNames = setOf(
            "amanha", "hoje", "tomorrow", "today", "depois", "segunda", "terca", "quarta", "quinta",
            "sexta", "sabado", "domingo", "monday", "tuesday", "wednesday", "thursday", "friday",
            "saturday", "sunday", "daqui"
        )
        for ((i, token) in tokens.withIndex()) {
            if (i == 0) continue
            val n = normalize(token.value).trim(',', '.', ';', ':')
            val next = tokens.getOrNull(i + 1)?.value?.let { normalize(it).trim(',', '.', ';', ':') }
            val nextNext = tokens.getOrNull(i + 2)?.value?.let { normalize(it).trim(',', '.', ';', ':') }
            val numeric = next?.matches(Regex("\\d{1,2}(?::\\d{2})?(?:h\\d{0,2})?")) == true
            val isMarker = when {
                n in dayNames -> true
                n == "dia" && next != null && next.matches(Regex("\\d{1,2}")) -> true
                (n == "as" || n == "at" || n == "pelas") && numeric -> true
                n == "em" && next != null && next.matches(Regex("\\d{1,4}")) &&
                    nextNext != null && UNITS.containsKey(nextNext) -> true
                n == "in" && next != null && next.matches(Regex("\\d{1,4}")) -> true
                n.matches(Regex("\\d{1,2}[:h]\\d{2}")) -> true
                n.matches(Regex("\\d{1,2}h")) -> true
                n.matches(Regex("\\d{1,2}/\\d{1,2}(?:/\\d{2,4})?")) -> true
                else -> false
            }
            if (isMarker) return i
        }
        return -1
    }

    /** "amanhã às 15:00" / "tomorrow at 3:00 PM". */
    fun describe(
        atMillis: Long,
        pt: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String {
        val at = Instant.ofEpochMilli(atMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val day = when {
            at.toLocalDate() == today -> if (pt) "hoje" else "today"
            at.toLocalDate() == today.plusDays(1) -> if (pt) "amanhã" else "tomorrow"
            else -> DateTimeFormatter.ofPattern(if (pt) "dd/MM" else "MMM d", if (pt) Locale("pt", "BR") else Locale.ENGLISH)
                .format(at.toLocalDate())
        }
        val time = String.format(Locale.US, "%02d:%02d", at.hour, at.minute)
        return if (pt) "$day às $time" else "$day at $time"
    }
}
