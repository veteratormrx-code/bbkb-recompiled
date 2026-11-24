package dev.bbkb.ime.core.device.config

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhysicalDisplayTest {

    @Test
    fun formatPutsTheShortSideFirst() {
        assertEquals("720x720", PhysicalDisplay.format(720, 720))
        assertEquals("720x1280", PhysicalDisplay.format(1280, 720))
    }

    @Test
    fun resolutionIsEmptyUntilAContextIsAttached() {
        PhysicalDisplay.clearForTest()
        assertNull(PhysicalDisplay.resolution())
        PhysicalDisplay.attach(ApplicationProvider.getApplicationContext())
        val value = PhysicalDisplay.resolution()
        assertTrue(value!!.matches(Regex("\\d+x\\d+")))
        val sides = value.split("x").map { it.toInt() }
        assertTrue(sides[0] <= sides[1])
        PhysicalDisplay.clearForTest()
    }
}
