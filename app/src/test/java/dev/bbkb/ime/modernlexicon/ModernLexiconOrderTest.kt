package dev.bbkb.ime.modernlexicon

import dev.bbkb.ime.core.engine.Dictionary
import dev.bbkb.ime.core.suggestion.SuggestedWords
import org.junit.Assert.assertEquals
import org.junit.Test

/** The first suggestion is the closest dictionary word, the way the 4PDA strip leads. */
class ModernLexiconOrderTest {

    @Test
    fun doubledLetterYieldsTheDictionaryWordAheadOfAnInflection() {
        val list = listOf(
            word("приввет", 0, Dictionary.DICTIONARY_USER_TYPED),
            word("приедет", 900, null),
            word("придет", 800, null),
            word("привет", 500, Dictionary.DICTIONARY_HARDCODED),
        )
        assertEquals(listOf("приввет", "привет", "приедет", "придет"), order("приввет", list))
    }

    @Test
    fun missingLetterPrefersTheCommonWordOverAShorterFragment() {
        val list = listOf(
            word("сегодян", 0, Dictionary.DICTIONARY_USER_TYPED),
            word("сегодн", 10, Dictionary.DICTIONARY_HARDCODED),
            word("сегодня", 400, Dictionary.DICTIONARY_HARDCODED),
        )
        assertEquals("сегодня", order("сегодян", list)[1])
    }

    @Test
    fun aRealPrefixKeepsTheEngineOrder() {
        val list = listOf(
            word("прив", 0, Dictionary.DICTIONARY_USER_TYPED),
            word("привет", 10, null),
            word("приветы", 99, Dictionary.DICTIONARY_HARDCODED),
        )
        assertEquals(listOf("прив", "привет", "приветы"), order("прив", list))
    }

    @Test
    fun frequencyBeatsAnEquallyCloseFragment() {
        val list = listOf(
            word("здравсвуйте", 0, Dictionary.DICTIONARY_USER_TYPED),
            word("здраствуйте", 50, Dictionary.DICTIONARY_HARDCODED),
            word("здравствуйте", 900, Dictionary.DICTIONARY_HARDCODED),
        )
        assertEquals("здравствуйте", order("здравсвуйте", list)[1])
    }

    private fun order(typed: String, words: List<SuggestedWords.SuggestedWordInfo>): List<String> {
        val list = ArrayList(words)
        ModernLexicon.promoteClosest(typed, list)
        return list.map { it.word }
    }

    private fun word(text: String, score: Int, source: Dictionary?): SuggestedWords.SuggestedWordInfo {
        return SuggestedWords.SuggestedWordInfo(text, score, if (source === Dictionary.DICTIONARY_USER_TYPED) 0 else 2, source, -1, -1, null)
    }
}
