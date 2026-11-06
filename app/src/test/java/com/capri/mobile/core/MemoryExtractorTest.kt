package com.capri.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoryExtractorTest {

    @Test
    fun explicitRememberConvertsFirstPerson() {
        assertEquals("you love jazz", MemoryExtractor.explicit("remember that I love jazz"))
    }

    @Test
    fun explicitRememberAcceptsAddressingCapri() {
        assertEquals("you're from Poland", MemoryExtractor.explicit("Capri, please remember that I'm from Poland"))
    }

    @Test
    fun questionsAreNotStored() {
        assertNull(MemoryExtractor.explicit("do you remember what I said?"))
    }

    @Test
    fun autoExtractsName() {
        assertEquals(listOf("your name is Alex"), MemoryExtractor.auto("my name is Alex"))
    }

    @Test
    fun autoExtractsPreference() {
        assertEquals(listOf("you love jazz"), MemoryExtractor.auto("I love jazz."))
    }

    @Test
    fun autoSkipsQuestions() {
        assertEquals(emptyList<String>(), MemoryExtractor.auto("do I like jazz?"))
    }

    @Test
    fun explicitRememberInPortuguese() {
        assertEquals("você ama jazz", MemoryExtractor.explicit("lembre-se que eu amo jazz"))
    }

    @Test
    fun timedPortugueseReminderIsNotMemory() {
        assertNull(MemoryExtractor.explicit("me lembre de beber água em 20 minutos"))
    }

    @Test
    fun autoExtractsPortugueseName() {
        assertEquals(listOf("your name is Alex"), MemoryExtractor.auto("meu nome é Alex"))
    }

    @Test
    fun autoExtractsPortuguesePreference() {
        assertEquals(listOf("you love jazz"), MemoryExtractor.auto("eu amo jazz."))
    }
}
