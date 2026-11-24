package dev.bbkb.ime.keyboard.inputboard.clipboard

import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEditingBarTest {
    @Test
    fun previousWordMovesTheCursorWithoutDeletingText() {
        val connection = mock(InputConnection::class.java)
        val events = ArgumentCaptor.forClass(KeyEvent::class.java)
        TextEditingBar.perform(connection, "word")

        verify(connection, times(2)).sendKeyEvent(events.capture())
        assertEquals(listOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP), events.allValues.map { it.action })
        assertTrue(events.allValues.all { it.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && it.isCtrlPressed })
        verifyNoMoreInteractions(connection)
    }
}
