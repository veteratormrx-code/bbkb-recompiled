package dev.bbkb.ime.keyboard.inputboard.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ClipboardSearchTest {
    @Test
    fun selectsTheMatchingRecentAndPinnedClipsInTheirExistingOrder() {
        val entries = (1..200).map {
            ClipEntry(it.toLong(), if (it % 40 == 0) "Адрес $it" else "Message $it",
                null, "text/plain", it.toLong(), isPinned = it == 80)
        }
        val found = entries.filter { ClipboardView.matches(it, "адрес", ClipboardView.Filter.TEXT, false) }
        assertEquals(listOf(40L, 80L, 120L, 160L, 200L), found.map { it.id })
        assertTrue(found[1].isPinned)
    }

    @Test
    fun filtersUrlsAndImageLabelsAndSkipsSensitiveClips() {
        val link = ClipEntry(1, "Open https://example.org/x", null, "text/plain", 1)
        val image = ClipEntry(2, "", "Чек за май", "image/png", 2, imageFile = "test.png")
        assertTrue(ClipboardView.matches(link, "example", ClipboardView.Filter.LINK, false))
        assertFalse(ClipboardView.matches(link, "example", ClipboardView.Filter.TEXT, false))
        assertTrue(ClipboardView.matches(image, "чек", ClipboardView.Filter.IMAGE, false))
        assertFalse(ClipboardView.matches(link, "example", ClipboardView.Filter.LINK, true))
    }
}
