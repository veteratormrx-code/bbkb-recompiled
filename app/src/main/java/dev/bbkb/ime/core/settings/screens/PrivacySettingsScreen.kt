package dev.bbkb.ime.core.settings.screens

import androidx.compose.runtime.Composable
import dev.bbkb.ime.R
import dev.bbkb.ime.core.settings.ui.Nav
import dev.bbkb.ime.core.settings.ui.SettingsScreenHost
import dev.bbkb.ime.core.settings.ui.Simple
import dev.bbkb.ime.core.settings.ui.drawableIcon

/**
 * Points at the learning preferences that already exist. Incognito is an editor flag
 * (`IME_FLAG_NO_PERSONALIZED_LEARNING`), so this screen does not invent a switch for it.
 */
@Composable
fun PrivacySettingsScreen(
    onNavigateToSuggestions: () -> Unit,
    onNavigateToLearning: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    SettingsScreenHost(R.string.settings_privacy_title, onNavigateBack, listOf(
        Nav(
            title = R.string.settings_pred_personalized_title,
            summary = R.string.settings_privacy_personalized_summary,
            icon = drawableIcon(R.drawable.ic_settings_learned_words),
            onClick = { onNavigateToSuggestions() },
        ),
        Nav(
            title = R.string.settings_dict_dynamic_learning_title,
            summary = R.string.settings_privacy_learning_summary,
            icon = drawableIcon(R.drawable.ic_settings_learning),
            onClick = { onNavigateToLearning() },
        ),
        Simple(
            title = R.string.settings_privacy_incognito_title,
            summary = R.string.settings_privacy_incognito_summary,
            icon = drawableIcon(R.drawable.ic_settings_m_info),
        ),
    ))
}
