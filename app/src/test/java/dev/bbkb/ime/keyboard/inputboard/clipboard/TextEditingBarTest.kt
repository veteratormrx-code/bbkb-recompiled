package dev.bbkb.ime.keyboard.inputboard.clipboard

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.HorizontalScrollView
import android.widget.RelativeLayout
import androidx.test.core.app.ApplicationProvider
import dev.bbkb.ime.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
    fun theScrollBarAndClipboardContentShareTheSameLayout() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val outer = RelativeLayout(context)
        val container = RelativeLayout(context).also(outer::addView)
        val list = View(context).apply { id = R.id.clipboardView }.also(container::addView)
        val empty = View(context).apply { id = R.id.empty_clipboard }.also(container::addView)

        TextEditingBar.attach(outer, context)
        TextEditingBar.attach(outer, context)

        val bar = container.getChildAt(2) as HorizontalScrollView
        assertEquals(3, container.childCount)
        assertSame(container, bar.parent)
        assertEquals(bar.id, (list.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.BELOW))
        assertEquals(bar.id, (empty.layoutParams as RelativeLayout.LayoutParams).getRule(RelativeLayout.BELOW))
    }

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
