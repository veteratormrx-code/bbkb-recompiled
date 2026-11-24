package dev.bbkb.ime.harness

import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KeyboardTapTest {
    @Test
    fun screenTapsEnterEveryCharacter() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val ime = args.getString("bbkbIme")
        val x = args.getString("bbkbKeyX")?.toFloatOrNull()
        val y = args.getString("bbkbKeyY")?.toFloatOrNull()
        assumeTrue("Select and calibrate the IME before this device test", ime != null && x != null && y != null)
        val tapX = requireNotNull(x)
        val tapY = requireNotNull(y)
        val count = args.getString("bbkbTapCount")?.toIntOrNull() ?: 2
        require(count in 1..10_000)
        val context = instrumentation.targetContext
        fun selectedIme() = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        assertEquals(ime, selectedIme())

        ActivityScenario.launch(HarnessActivity::class.java).use { scenario ->
            lateinit var editor: EditText
            scenario.onActivity { activity ->
                val root = activity.findViewById<View>(android.R.id.content)
                editor = root.findViewWithTag<EditText>("plain")
                editor.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            SystemClock.sleep(1500)
            for (batch in 0 until (count + 49) / 50) {
                val batchSize = minOf(50, count - batch * 50)
                repeat(batchSize) {
                    val downTime = SystemClock.uptimeMillis()
                    for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, tapX, tapY, 0)
                        event.source = InputDevice.SOURCE_TOUCHSCREEN
                        try {
                            assertTrue("Touch injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
                        } finally {
                            event.recycle()
                        }
                    }
                    SystemClock.sleep(30)
                }
                // The IME commits over Binder from a different process.
                SystemClock.sleep(200)
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    assertEquals("Batch ${batch + 1}", "b".repeat(batchSize), editor.text.toString())
                    if (batch + 1 < (count + 49) / 50) {
                        editor.text.clear()
                        it.getSystemService(InputMethodManager::class.java).restartInput(editor)
                    }
                }
                SystemClock.sleep(150)
                assertEquals(ime, selectedIme())
            }
        }
    }
}
