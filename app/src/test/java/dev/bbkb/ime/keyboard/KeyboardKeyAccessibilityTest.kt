package dev.bbkb.ime.keyboard

import androidx.test.core.app.ApplicationProvider
import dev.bbkb.ime.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class KeyboardKeyAccessibilityTest {

    @Test
    fun menuCodesUseTheirToolNamesAndLettersKeepTheLabel() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(
            context.getString(R.string.settings_uim_toggle_emoji),
            KeyboardKeyAccessibility.describe(context, -11, null),
        )
        assertEquals(
            context.getString(R.string.accessibility_keyboard_letters),
            KeyboardKeyAccessibility.describe(context, -3, ""),
        )
        assertEquals("б", KeyboardKeyAccessibility.describe(context, 'б'.code, "б"))
    }
}
