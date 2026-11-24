package dev.bbkb.ime.core.settings.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.bbkb.ime.R
import dev.bbkb.ime.core.device.profile.DeviceProfile
import dev.bbkb.ime.core.gesture.arbiter.VkbGestureAssignments
import dev.bbkb.ime.core.locale.LocaleUtils
import dev.bbkb.ime.core.settings.search.settingsSearchAnchor
import dev.bbkb.ime.core.settings.ui.Category
import dev.bbkb.ime.core.settings.ui.Nav
import dev.bbkb.ime.core.settings.ui.RowSummary
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.core.settings.ui.Toggle
import dev.bbkb.ime.core.settings.ui.boolPref
import dev.bbkb.ime.core.settings.ui.drawableIcon

/**
 * The gesture switches live here. Assigning what each swipe does stays on its own page.
 */
@Composable
fun GesturesSettingsScreen(
    onNavigateToVkbGestures: () -> Unit,
    onNavigateToCkbGestures: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    val hasTouchKeypad = remember { DeviceProfile.current()?.hasTouchKeypad() ?: false }
    val isChineseLocale = remember { LocaleUtils.isCurrentSubtypeChinese() }

    SettingsScreenHost(R.string.settings_category_gestures, onNavigateBack, listOf(
        Toggle(
            store = boolPref("type_by_swiping_vkb", R.bool.config_vkb_type_by_swiping_enabled_by_build_config),
            title = R.string.settings_vkb_type_by_swiping_title,
            icon = drawableIcon(R.drawable.ic_settings_swipe_typing),
            summary = RowSummary.Res(R.string.settings_vkb_type_by_swiping_summary),
            enabled = { !isChineseLocale },
            modifier = Modifier.settingsSearchAnchor("type_by_swiping_vkb"),
        ),
        Toggle(
            store = boolPref(VkbGestureAssignments.KEY_ENABLED, R.bool.config_vkb_swipe_gestures_enabled_by_build_config),
            title = R.string.settings_vkb_swipe_gestures_title,
            icon = drawableIcon(R.drawable.ic_settings_gesture_ckb),
            summary = RowSummary.Res(R.string.settings_vkb_swipe_gestures_switch_summary),
            modifier = Modifier.settingsSearchAnchor("swipe_gesture_vkb"),
        ),
        Nav(
            title = R.string.vkb_gestures_configure_title,
            summary = R.string.ckb_gestures_configure_summary,
            icon = drawableIcon(R.drawable.ic_settings_gesture_ckb_configure),
            onClick = { onNavigateToVkbGestures() },
        ),
        Toggle(
            store = boolPref("pref_spacebar_language_switching", true),
            title = R.string.settings_lang_spacebar_switch_title,
            icon = drawableIcon(R.drawable.ic_settings_language_spacebar_swipe),
            summary = RowSummary.Res(R.string.settings_lang_spacebar_switch_summary),
            modifier = Modifier.settingsSearchAnchor("pref_spacebar_language_switching"),
        ),
        Nav(
            title = R.string.ckb_gestures_configure_title,
            summary = R.string.settings_gestures_ckb_summary,
            icon = drawableIcon(R.drawable.ic_settings_gesture_ckb),
            visible = { hasTouchKeypad },
            onClick = { onNavigateToCkbGestures() },
        ),
        Category(R.string.settings_shake_title),
        shakeAction("shake_x_action", R.string.settings_shake_x_axis_title, R.drawable.ic_settings_shake_horizontal,
            Modifier.settingsSearchAnchor("shake_x_action")),
        shakeAction("shake_y_action", R.string.settings_shake_y_axis_title, R.drawable.ic_settings_shake_vertical,
            Modifier.settingsSearchAnchor("shake_y_action")),
        shakeAction("shake_z_action", R.string.settings_shake_z_axis_title, R.drawable.ic_settings_shake_diagonal,
            Modifier.settingsSearchAnchor("shake_z_action")),
        shakeAction("shake_fallback_action", R.string.settings_shake_fallback_title, R.drawable.ic_settings_shake_other,
            Modifier.settingsSearchAnchor("shake_fallback_action")),
    ))
}
