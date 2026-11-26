package dev.bbkb.ime.harness

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ClipboardGrantTest {
    @Test
    fun oldClipboardImageUriIsUnreadableInANewEditorSession() {
        val supplied = InstrumentationRegistry.getArguments().getString("fixtureUri")
        assumeNotNull(supplied)
        val uri = Uri.parse(requireNotNull(supplied))
        require(uri.scheme == "content" && uri.authority == "dev.bbkb.ime.debug.fileprovider" &&
            uri.pathSegments.size == 2 && uri.pathSegments.first() == "clipboard_images")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val failure = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read() }
        }.exceptionOrNull()
        assertTrue("A second editor session should be denied access to the previous image URI",
            failure is SecurityException)
    }
}
