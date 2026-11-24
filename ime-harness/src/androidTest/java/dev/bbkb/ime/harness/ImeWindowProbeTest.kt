package dev.bbkb.ime.harness

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Temporary probe: can accessibility see the IME window and its key/candidate nodes? */
@RunWith(AndroidJUnit4::class)
class ImeWindowProbeTest {
    private fun walk(node: android.view.accessibility.AccessibilityNodeInfo?, depth: Int, out: StringBuilder) {
        if (node == null) return
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        out.append(
            "  ".repeat(depth) + node.className + " id=" + node.viewIdResourceName +
                " vis=" + node.isVisibleToUser + " clk=" + node.isClickable +
                " t='" + node.text + "' d='" + node.contentDescription +
                "' " + rect + "\n",
        )
        for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1, out)
    }

    @Test
    fun dumpWindows() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(HarnessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val editor = activity.findViewById<View>(android.R.id.content)
                    .findViewWithTag<EditText>("plain")
                editor.requestFocus()
                activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
            SystemClock.sleep(2500)
            instrumentation.waitForIdleSync()
            val serviceInfo = instrumentation.uiAutomation.serviceInfo
            serviceInfo.flags = serviceInfo.flags or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            instrumentation.uiAutomation.serviceInfo = serviceInfo
            SystemClock.sleep(1000)
            val windows = instrumentation.uiAutomation.windows
            val report = StringBuilder()
            report.append("windows=").append(windows.size).append('\n')
            for (window in windows) {
                report.append(
                    "window type=" + window.type + " layer=" + window.layer +
                        " focused=" + window.isFocused +
                        " rootPkg=" + window.root?.packageName + '\n',
                )
                walk(window.root, 1, report)
            }
            for (line in report.lines()) Log.i("IMEPROBE", line)
            SystemClock.sleep(500)
            scenario.onActivity { activity ->
                val editor = activity.findViewById<View>(android.R.id.content)
                    .findViewWithTag<EditText>("plain")
                Log.i("IMEPROBE", "editor=" + editor.text)
            }
        }
    }
}
