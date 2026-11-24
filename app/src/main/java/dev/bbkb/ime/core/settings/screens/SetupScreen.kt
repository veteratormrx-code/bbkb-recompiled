package dev.bbkb.ime.core.settings.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.Composable
import dev.bbkb.ime.R
import dev.bbkb.ime.core.settings.ui.Category
import dev.bbkb.ime.core.settings.ui.Nav
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.core.settings.ui.Simple

/**
 * First launch. Each row opens a step the user can skip. Back and the last row both finish setup.
 */
@Composable
fun SetupScreen(
    onNavigateToDeviceConfig: () -> Unit,
    onNavigateToKeylayout: () -> Unit,
    onNavigateToLanguage: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onDone: () -> Unit,
) {
    SettingsScreenHost(R.string.settings_setup_title, onDone, listOf(
        Category(R.string.settings_setup_returning_header),
        Nav(
            title = R.string.settings_setup_restore_title,
            summary = R.string.settings_setup_restore_summary,
            onClick = { _ -> onNavigateToBackupRestore() },
        ),
        Category(R.string.settings_setup_steps_header),
        Nav(
            title = R.string.settings_setup_enable_title,
            summary = R.string.settings_setup_enable_summary,
            onClick = { context ->
                context.startActivity(
                    Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        ),
        Nav(
            title = R.string.settings_setup_set_default_title,
            summary = R.string.settings_setup_set_default_summary,
            onClick = { context ->
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.showInputMethodPicker()
            },
        ),
        Nav(
            title = R.string.settings_setup_device_title,
            summary = R.string.settings_setup_device_summary,
            onClick = { _ -> onNavigateToDeviceConfig() },
        ),
        Nav(
            title = R.string.settings_setup_keylayout_title,
            summary = R.string.settings_setup_keylayout_summary,
            onClick = { _ -> onNavigateToKeylayout() },
        ),
        Nav(
            title = R.string.settings_setup_language_title,
            summary = R.string.settings_setup_language_summary,
            onClick = { _ -> onNavigateToLanguage() },
        ),
        Simple(
            title = R.string.settings_setup_skip_title,
            summary = R.string.settings_setup_skip_summary,
            onClick = { _ -> onDone() },
        ),
    ))
}
