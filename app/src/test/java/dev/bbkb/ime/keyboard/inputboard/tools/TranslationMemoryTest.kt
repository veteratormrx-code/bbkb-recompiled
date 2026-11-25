package dev.bbkb.ime.keyboard.inputboard.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationMemoryTest {
    @Test
    fun tenRoundTripsStayOnTheOriginalWords() {
        val memory = TranslationMemory()
        val original = "Привет, как дела?"
        var calls = 0
        fun engine(from: String, text: String): String {
            calls++
            return if (from == "ru") {
                listOf("Hi, how are you?", "Hello there", "How's it going?", "What's up?")[calls - 1]
            } else {
                "совсем другой смысл $calls"
            }
        }

        var text = original
        var from = "ru"
        var to = "en"
        var translated = memory.recall(from, to, text) ?: engine(from, text).also {
            memory.remember(from, to, text, it)
        }
        repeat(10) {
            val back = memory.recall(to, from, translated) ?: engine(to, translated).also {
                memory.remember(to, from, translated, it)
            }
            assertEquals(original, back)
            val forward = memory.recall(from, to, back) ?: engine(from, back).also {
                memory.remember(from, to, back, it)
            }
            assertEquals(translated, forward)
            text = back
        }
        assertEquals(original, text)
        assertEquals(1, calls)
    }

    @Test
    fun tenSwapsLandBackOnTheSamePair() {
        val memory = TranslationMemory()
        var from = "ru"
        var to = "en"
        var source = "Я на работе"
        var translation = "I am at work"
        repeat(10) {
            val exchanged = memory.exchange(from, to, source, translation)
            from = exchanged.from
            to = exchanged.to
            source = exchanged.source
            translation = exchanged.translation
        }
        assertEquals("ru", from)
        assertEquals("en", to)
        assertEquals("Я на работе", source)
        assertEquals("I am at work", translation)
    }

    @Test
    fun oneSwapExchangesTheSidesWithoutANewTranslation() {
        val memory = TranslationMemory()
        val exchanged = memory.exchange("ru", "en", "Привет", "Hello")
        assertEquals("en", exchanged.from)
        assertEquals("ru", exchanged.to)
        assertEquals("Hello", exchanged.source)
        assertEquals("Привет", exchanged.translation)
        assertEquals("Привет", memory.recall("en", "ru", "Hello"))
    }

    @Test
    fun extraSpacesStillFindTheSamePair() {
        val memory = TranslationMemory()
        memory.remember("ru", "en", "Привет", "Hello")
        assertEquals("Hello", memory.recall("ru", "en", "  Привет  "))
        assertEquals("Привет", memory.recall("en", "ru", "Hello\n"))
    }

    @Test
    fun anUnknownSentenceIsNotInvented() {
        val memory = TranslationMemory()
        memory.remember("ru", "en", "Привет", "Hello")
        assertNull(memory.recall("ru", "en", "Пока"))
    }
}
