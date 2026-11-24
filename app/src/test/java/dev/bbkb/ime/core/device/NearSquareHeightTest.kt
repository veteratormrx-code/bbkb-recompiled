package dev.bbkb.ime.core.device

import android.content.Context
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
class NearSquareHeightTest {

    private val resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun extraCompactOnASquarePanelUsesFortyFivePercent() {
        resources.displayMetrics.widthPixels = 720
        resources.displayMetrics.heightPixels = 720
        assertTrue(ResourceConfigManager.isNearSquareScreen(resources))
        assertEquals(324, ResourceConfigManager.nearSquareKeyboardHeight(resources, "extra_compact"))
        assertEquals(-1, ResourceConfigManager.nearSquareKeyboardHeight(resources, "regular"))
    }

    @Test
    fun aPhoneKeepsTheOrdinaryHeightScale() {
        resources.displayMetrics.widthPixels = 1080
        resources.displayMetrics.heightPixels = 2400
        assertFalse(ResourceConfigManager.isNearSquareScreen(resources))
        assertEquals(-1, ResourceConfigManager.nearSquareKeyboardHeight(resources, "extra_compact"))
    }
}
