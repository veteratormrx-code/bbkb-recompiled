package dev.bbkb.ime.keyboard.inputboard.tools

import androidx.test.core.app.ApplicationProvider
import dev.bbkb.ime.core.layout.LetterLayoutRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WrongLayoutTextTest {
    private val registry by lazy { LetterLayoutRegistry(ApplicationProvider.getApplicationContext()) }

    @Test
    fun convertsBetweenTheShippedPhysicalLayouts() {
        assertEquals("Привет", WrongLayoutText.convert(registry, "Ghbdtn"))
        assertEquals(" Ghbdtn ", WrongLayoutText.convert(registry, " Привет "))
    }

    @Test
    fun leavesMixedTokensAndAddressesAlone() {
        assertNull(WrongLayoutText.convert(registry, "ghbdтn"))
        assertNull(WrongLayoutText.convert(registry, "example.org"))
        assertNull(WrongLayoutText.convert(registry, "zoom42"))
    }
}
