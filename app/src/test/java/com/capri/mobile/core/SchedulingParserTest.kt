package com.capri.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SchedulingParserTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = LocalDateTime.of(2026, 6, 10, 10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun parsesPortugueseRelativeDuration() {
        assertEquals(10 * 60_000L, SchedulingParser.durationAfter("me lembre em 10 minutos"))
        assertEquals(2 * 3_600_000L, SchedulingParser.durationAfter("daqui a 2 horas"))
        assertEquals(5 * 60_000L, SchedulingParser.duration("timer de 5 min"))
    }

    @Test
    fun parsesEnglishRelativeDuration() {
        assertEquals(20 * 60_000L, SchedulingParser.durationAfter("remind me in 20 minutes"))
        assertEquals(15 * 60_000L, SchedulingParser.durationAfter("in 15 min"))
    }

    @Test
    fun parsesClockTimesInBothLanguages() {
        assertEquals(Clock(19, 30), SchedulingParser.parseClock("às 19h30"))
        assertEquals(Clock(15, 0), SchedulingParser.parseClock("amanhã às 15h"))
        assertEquals(Clock(19, 0), SchedulingParser.parseClock("7 da noite"))
        assertEquals(Clock(15, 0), SchedulingParser.parseClock("tomorrow at 3pm"))
        assertEquals(Clock(9, 45), SchedulingParser.parseClock("9:45am"))
    }

    @Test
    fun parsesTomorrowInPortuguese() {
        val parsed = SchedulingParser.parseWhen("amanhã às 15h", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 11), parsed.date)
        assertEquals(15, parsed.at.let { java.time.Instant.ofEpochMilli(it).atZone(zone).hour })
    }

    @Test
    fun parsesTomorrowInEnglish() {
        val parsed = SchedulingParser.parseWhen("tomorrow at 9am", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 11), parsed.date)
        assertEquals(9, parsed.clock?.hour)
    }

    @Test
    fun rollsPastClockToNextDay() {
        val parsed = SchedulingParser.parseWhen("às 8h", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 11), parsed.date)
    }

    @Test
    fun keepsFutureTimeToday() {
        val parsed = SchedulingParser.parseWhen("hoje às 15h", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 10), parsed.date)
    }

    @Test
    fun pastTimeTodayRollsToTomorrow() {
        val parsed = SchedulingParser.parseWhen("hoje às 8h", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 11), parsed.date)
    }

    @Test
    fun parsesDayOfMonth() {
        val parsed = SchedulingParser.parseWhen("dia 12 às 8h", now, zone)!!
        assertEquals(LocalDate.of(2026, 6, 12), parsed.date)
    }

    @Test
    fun parsesWeekday() {
        val parsed = SchedulingParser.parseWhen("sexta às 18h", now, zone)!!
        assertEquals(java.time.DayOfWeek.FRIDAY, parsed.date.dayOfWeek)
        assertTrue(parsed.date.isAfter(LocalDate.of(2026, 6, 10)))
    }

    @Test
    fun rejectsPhrasesWithoutTime() {
        assertNull(SchedulingParser.parseWhen("reunião com o time", now, zone))
    }

    @Test
    fun stripsTimeExpressionFromReminderText() {
        assertEquals("reunião", SchedulingParser.stripTimeExpression("reunião amanhã às 15h"))
        assertEquals("beber água", SchedulingParser.stripTimeExpression("beber água em 20 minutos"))
        assertEquals("dinner", SchedulingParser.stripTimeExpression("dinner tomorrow at 8pm"))
    }

    @Test
    fun extractsCalendarTitles() {
        assertEquals(
            "reunião com o time",
            SchedulingParser.calendarTitle("agende uma reunião com o time amanhã às 15h")
        )
        assertEquals(
            "dentist appointment",
            SchedulingParser.calendarTitle("schedule dentist appointment tomorrow at 3pm")
        )
    }

    @Test
    fun describesUpcomingTime() {
        val at = LocalDateTime.of(2026, 6, 11, 15, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals("amanhã às 15:00", SchedulingParser.describe(at, pt = true, nowMillis = now, zone = zone))
        assertEquals("tomorrow at 15:00", SchedulingParser.describe(at, pt = false, nowMillis = now, zone = zone))
    }

    @Test
    fun normalizesAccents() {
        assertEquals("amanha as 15h", SchedulingParser.normalize("Amanhã às 15h"))
    }
}
