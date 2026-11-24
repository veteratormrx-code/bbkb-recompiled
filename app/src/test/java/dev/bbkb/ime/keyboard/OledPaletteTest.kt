package dev.bbkb.ime.keyboard

import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OledPaletteTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun oledPresetsAreTrueBlackWithAndWithoutSeparators() {
        KeyboardColorManager.init(context, KeyboardColorManager.Style.OLED, KeyboardColorManager.Scheme.DARK, false)
        assertEquals(Color.BLACK, KeyboardColorManager.backgroundColor)
        assertEquals(Color.BLACK, KeyboardColorManager.keyColor)
        assertTrue(KeyboardColorManager.styleSpec().drawKeySeparators)
        assertEquals(0xFF3A3A3A.toInt(), KeyboardColorManager.keySeparatorColor)

        KeyboardColorManager.setTheme(KeyboardColorManager.Style.OLED_SEAMLESS, KeyboardColorManager.Scheme.DARK, false)
        assertEquals(Color.BLACK, KeyboardColorManager.backgroundColor)
        assertFalse(KeyboardColorManager.styleSpec().drawKeySeparators)
        assertEquals(Color.BLACK, KeyboardColorManager.keySeparatorColor)
    }
}
