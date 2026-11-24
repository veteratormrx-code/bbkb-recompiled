package dev.bbkb.ime.core.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedEmailsTest {

    @Test
    fun matchKeepsSavedSpellingAndStopsAtThree() {
        val addresses = listOf(
            "ivan@example.com",
            "Ivy@Example.com",
            "other@example.com",
            "iv4@example.com",
        )
        assertEquals(
            listOf("ivan@example.com", "Ivy@Example.com", "iv4@example.com"),
            SavedEmails.match(addresses, "iv"),
        )
        assertTrue(SavedEmails.match(addresses, "i").isEmpty())
        assertTrue(SavedEmails.match(addresses, "zz").isEmpty())
    }

    @Test
    fun canonicalRejectsAnythingButOneAddress() {
        assertEquals("ivan@example.com", SavedEmails.canonical(" ivan@example.com "))
        assertNull(SavedEmails.canonical("not an email"))
        assertNull(SavedEmails.canonical("a@b"))
        assertNull(SavedEmails.canonical("one@a.com two@b.com"))
    }
}
