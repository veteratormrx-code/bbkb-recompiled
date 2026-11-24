package dev.bbkb.ime.core.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bbkb.ime.R
import dev.bbkb.ime.core.device.profile.DeviceProfile
import dev.bbkb.ime.core.settings.ui.Nav
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.core.settings.ui.drawableIcon

/**
 * Links the gesture controls that already exist. The switches stay on the on-screen keyboard,
 * the physical keyboard, language switching, and auxiliary input.
 */
@Composable
fun GesturesSettingsScreen(
    onNavigateToOnScreenKeyboard: () -> Unit,
    onNavigateToVkbGestures: () -> Unit,
    onNavigateToCkbGestures: () -> Unit,
    onNavigateToLanguageSwitching: () -> Unit,
    onNavigateToShake: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val hasTouchKeypad = remember { DeviceProfile.current()?.hasTouchKeypad() ?: false }

    SettingsScreenHost(R.string.settings_category_gestures, onNavigateBack, listOf(
        Nav(
            title = R.string.settings_keyboard_title,
            summary = R.string.settings_gestures_onscreen_summary,
            icon = drawableIcon(R.drawable.ic_settings_keyboard_virtual),
            onClick = { onNavigateToOnScreenKeyboard() },
        ),
        Nav(
            title = R.string.vkb_gestures_configure_title,
            summary = R.string.ckb_gestures_configure_summary,
            icon = drawableIcon(R.drawable.ic_settings_gesture_ckb_configure),
            onClick = { onNavigateToVkbGestures() },
        ),
        Nav(
            title = R.string.ckb_gestures_configure_title,
            summary = R.string.settings_gestures_ckb_summary,
            icon = drawableIcon(R.drawable.ic_settings_gesture_ckb),
            visible = { hasTouchKeypad },
            onClick = { onNavigateToCkbGestures() },
        ),
        Nav(
            title = R.string.settings_lang_spacebar_switch_title,
            summary = R.string.settings_gestures_spacebar_summary,
            icon = drawableIcon(R.drawable.ic_settings_language_spacebar_swipe),
            onClick = { onNavigateToLanguageSwitching() },
        ),
        Nav(
            title = R.string.settings_shake_title,
            summary = R.string.settings_gestures_shake_summary,
            icon = drawableIcon(R.drawable.ic_settings_shake_horizontal),
            onClick = { onNavigateToShake() },
        ),
    ))
}
