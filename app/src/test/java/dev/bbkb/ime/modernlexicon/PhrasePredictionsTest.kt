package dev.bbkb.ime.modernlexicon

import dev.bbkb.ime.modernlexicon.PhrasePredictions.Choice
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PhrasePredictionsTest {
    @Test
    fun moreSpecificContextPrecedesFrequentBigramFallback() {
        val table = mapOf(
            "am" to listOf(Choice("here", 500), Choice("ready", 400)),
            "i am" to listOf(Choice("ready", 3)),
            "we am" to listOf(Choice("there", 2)),
        )
        assertEquals(listOf("ready", "here"), PhrasePredictions.rank(listOf("i", "am"), table))
        assertEquals(listOf("there", "here", "ready"), PhrasePredictions.rank(listOf("we", "am"), table))
        assertEquals(listOf("here", "ready"), PhrasePredictions.rank(listOf("am"), table))
    }

    @Test
    fun tokenizationKeepsOneLetterWordsAndRespectsSentenceBoundaries() {
        assertEquals(listOf("i", "am"), PhrasePredictions.tokenize("Yes. I am, ", Locale.ENGLISH))
        assertEquals(listOf("в", "елке"), PhrasePredictions.tokenize("Где? В ёлке ", Locale.forLanguageTag("ru")))
        assertEquals(emptyList<String>(), PhrasePredictions.tokenize("The end. ", Locale.ENGLISH))
    }

    @Test
    fun anEmptyFieldUsesTheMostCommonContinuations() {
        assertEquals(
            listOf("я", "и", "в"),
            PhrasePredictions.topWords(mapOf("в" to 3, "я" to 9, "и" to 5, "дом" to 1), 3),
        )
    }
}
