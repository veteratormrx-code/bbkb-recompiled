package dev.bbkb.ime.core.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SetupStateTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun clear() {
        PrefsManager.getPrefs(context).edit().clear().commit()
    }

    @Test
    fun aNewStoreShowsSetupAndAnExistingStoreDoesNot() {
        assertTrue(SetupState.shouldShowSetup(context))
        PrefsManager.getPrefs(context).edit().putBoolean("show_predictions", true).commit()
        assertFalse(SetupState.shouldShowSetup(context))
        PrefsManager.getPrefs(context).edit().clear().putBoolean(SetupState.KEY_COMPLETED, true).commit()
        assertFalse(SetupState.shouldShowSetup(context))
    }
}
