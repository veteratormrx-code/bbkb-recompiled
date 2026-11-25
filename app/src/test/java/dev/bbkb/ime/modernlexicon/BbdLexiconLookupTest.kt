package dev.bbkb.ime.modernlexicon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.util.Locale

/** The common pack is alphabetical, so a one-edit word past line 80 must still come back first. */
class BbdLexiconLookupTest {
    private val lexicon = BbdLexicon(
        File("src/main/assets/bbmodern/ru-common.bbd").readBytes()
    )

    @Test
    fun doubledLetterFindsTheCommonWord() {
        assertEquals("привет", first("приввет"))
        assertEquals("обычно", first("обыччно"))
        assertEquals("завтра", first("завттра"))
    }

    @Test
    fun aMissingLetterFindsTheCommonWord() {
        assertEquals("здравствуйте", first("здравсвуйте"))
        assertEquals("сегодня", first("сегодян"))
    }

    @Test
    fun exactEditsFindWordsAcrossPrefixBuckets() {
        for ((typo, expected) in listOf(
            "рпивет" to "привет",
            "пподарок" to "подарок",
            "ривет" to "привет",
            "вопррос" to "вопрос",
        )) {
            assertEquals(expected, lexicon.lookupExactEdits(typo).maxByOrNull { it.frequency }?.word)
        }
    }

    @Test
    fun aCommonAlternativePreventsReplacingAnAmbiguousWord() {
        val russian = Locale.forLanguageTag("ru")
        assertEquals(true, ModernLexicon.hasCloseAlternative("урк", "рук", russian, lexicon.lookupExactEdits("урк")))
        assertEquals(false, ModernLexicon.hasCloseAlternative("рпивет", "привет", russian, lexicon.lookupExactEdits("рпивет")))
    }

    private fun first(typed: String): String =
        lexicon.lookupWithPrefixTypos(typed, 8, emptyMap()).first().word
}
