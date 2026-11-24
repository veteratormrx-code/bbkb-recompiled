package dev.bbkb.ime.keyboard.inputboard.emoji

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Russian rows in `bbmodern/emoji-search.tsv` are search aliases. Emojibase stores U+FE0F on
 * fully-qualified emoji (thumbs up, neutral face); the TSV does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiRussianSearchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val provider: EmojibaseDataProvider by lazy {
        EmojibaseDataProvider(context).apply {
            applyDataset(readDataset().entries) { BooleanArray(it.size) { true } }
        }
    }

    @Test
    fun aFullyQualifiedEmojiMatchesItsUnqualifiedRussianRow() {
        assertEquals(listOf("👍️"), provider.searchEmojis("лайк").map { it.emoji })
        assertEquals(listOf("👍️"), provider.searchEmojis("большой палец вверх").map { it.emoji })
        assertEquals("👍️", provider.topHit("лайк", 30)?.emoji)
        assertNull(provider.topHit("лайк", EmojiSearchScoring.STRIP_MIN_RELEVANCE))
    }

    @Test
    fun anExactKeyAndAMultiWordRussianNameBothMatch() {
        assertTrue(provider.searchEmojis("улыбка").any { it.emoji == "😀" })
        assertEquals(listOf("😐️"), provider.searchEmojis("все равно").map { it.emoji })
    }

    @Test
    fun theSupplementAddsNewEmojiAndSkinTonesToTheCatalog() {
        val crackingFace = String(Character.toChars(0x1FAEB))
        val leftThumb = String(Character.toChars(0x1FAF9))
        assertTrue(provider.searchEmojis("трескается").any { it.emoji == crackingFace })
        assertTrue(provider.searchEmojis("cracking face").any { it.emoji == crackingFace })
        assertEquals(5, provider.getSkinToneVariants(leftThumb).size)
    }

    @Test
    fun plainFlagsAndBidirectionalArrowsRemainInTheCatalog() {
        val catalog = provider.getAllEmojis().map { it.emoji.replace("\uFE0F", "") }.toSet()
        assertTrue(setOf("🏳", "🏴", "↔", "↕").all { it in catalog })
    }
}
