package dev.bbkb.ime.modernlexicon

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

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

    private fun first(typed: String): String =
        lexicon.lookupWithPrefixTypos(typed, 8, emptyMap()).first().word
}
