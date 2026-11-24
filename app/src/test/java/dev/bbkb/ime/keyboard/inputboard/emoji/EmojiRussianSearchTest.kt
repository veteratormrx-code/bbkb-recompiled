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
 * fully-qualified emoji (thumbs up, neutral face); the TSV does not. Emoji 18 names in the TSV
 * do not invent keys the dataset lacks.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EmojiRussianSearchTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val provider: EmojibaseDataProvider by lazy {
        val entries = context.assets.open(EmojibaseCompactFormat.ASSET).bufferedReader(Charsets.UTF_8).use {
            EmojibaseCompactFormat.read(it).entries
        }
        EmojibaseDataProvider(context).apply { applyDataset(entries) { BooleanArray(it.size) { true } } }
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
    fun anEmoji18NameIsNotInvented() {
        assertTrue(provider.searchEmojis("трескается").isEmpty())
    }
}
