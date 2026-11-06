package com.capri.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectorTest {

    private val persona = PersonaData(
        character = "",
        acks = mapOf("en" to "hi", "es" to "hola", "fr" to "salut"),
        examples = emptyList(),
        languageExamples = emptyMap(),
        names = mapOf("en" to "English", "es" to "Spanish", "fr" to "French"),
        reminder = "recent conversation language. Reply in English.",
        markers = mapOf(
            "es" to setOf("hola", "gracias", "estoy", "que"),
            "fr" to setOf("bonjour", "merci", "suis", "avec"),
            "en" to setOf("hello", "thanks", "the", "and")
        ),
        strongMarkers = mapOf(
            "es" to setOf("hola", "gracias"),
            "fr" to setOf("bonjour", "merci"),
            "en" to setOf("hello", "thanks")
        )
    )

    @Test
    fun detectsSpanishFromStrongMarkers() {
        assertEquals("es", LanguageDetector.detect("hola gracias", "en", persona))
    }

    @Test
    fun detectsFrenchFromStrongMarkers() {
        assertEquals("fr", LanguageDetector.detect("bonjour merci", "en", persona))
    }

    @Test
    fun shortAmbiguousMessageInheritsConversation() {
        assertEquals("es", LanguageDetector.detect("ok", "es", persona))
    }

    @Test
    fun fixedLanguageOverridesDetection() {
        assertEquals("fr", LanguageDetector.forText("hola", "en", persona, "fr"))
    }

    @Test
    fun autoLanguageStillDetects() {
        assertEquals("es", LanguageDetector.forText("hola gracias", "en", persona, "auto"))
    }
}
