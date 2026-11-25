package dev.bbkb.ime.core.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import dev.bbkb.ime.core.settings.PrefsManager
import dev.bbkb.ime.core.suggestion.SavedEmails
import dev.bbkb.ime.R
import dev.bbkb.ime.core.settings.search.settingsSearchAnchor
import dev.bbkb.ime.core.settings.ui.LocalSpacing
import dev.bbkb.ime.core.settings.ui.PreferenceCategory
import dev.bbkb.ime.core.settings.ui.PreferenceGroup
import dev.bbkb.ime.core.settings.ui.SettingsScaffold
import dev.bbkb.ime.core.settings.ui.SwitchPreference
import dev.bbkb.ime.core.settings.ui.rememberContactsPermission

@Composable
fun PredictionsSuggestionsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PrefsManager.getPrefs(context) }
    
    
    
    var showPredictions by remember {
        mutableStateOf(prefs.getBoolean("show_predictions", true))
    }
    var onKeyPredictions by remember {
        mutableStateOf(prefs.getBoolean("on_key_predictions", true))
    }
    var emojiPredictions by remember {
        // Same resource default SettingsValues.loadEmojiPredictionsSetting reads.
        mutableStateOf(
            prefs.getBoolean("emoji_predictions", context.resources.getBoolean(R.bool.config_default_emoji_predictions))
        )
    }
    var emojiAfterSpace by remember {
        mutableStateOf(prefs.getBoolean("emoji_after_space", true))
    }
    var nextWordPrediction by remember {
        mutableStateOf(prefs.getBoolean("next_word_prediction", true))
    }
    var personalizedSuggestions by remember {
        mutableStateOf(prefs.getBoolean("pref_key_use_personalized_dicts", true))
    }
    var inlineAutofill by remember {
        mutableStateOf(prefs.getBoolean("pref_inline_autofill_enabled", true))
    }
    var suggestContacts by remember {
        mutableStateOf(prefs.getBoolean("pref_key_use_contacts_dict", true))
    }
    var forceSuggestions by remember {
        mutableStateOf(prefs.getBoolean("pref_force_suggestions", false))
    }
    var searchSuggestions by remember {
        mutableStateOf(prefs.getBoolean("pref_search_suggestions", true))
    }
    var savedEmails by remember {
        mutableStateOf(SavedEmails.isEnabled(context))
    }
    var savedAddresses by remember {
        mutableStateOf(SavedEmails.addresses(context))
    }
    var emailDraft by remember { mutableStateOf("") }
    
    // Turning "Suggest from contacts" on asks for the contacts permission; a grant turns it on.
    val contacts = rememberContactsPermission { granted ->
        if (granted) {
            suggestContacts = true
            prefs.edit().putBoolean("pref_key_use_contacts_dict", true).apply()
        }
    }
    
    
    SettingsScaffold(
        title = context.getString(R.string.settings_suggestion_title),
        onNavigateBack = onNavigateBack,
    ) { paddingValues ->
        val spacing = LocalSpacing.current
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            PreferenceGroup {
                // Master toggle
                SwitchPreference(
                    title = context.getString(R.string.settings_pred_show_predictions_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_prediction),
                    summary = context.getString(R.string.settings_pred_show_predictions_summary),
                    checked = showPredictions,
                    modifier = Modifier.settingsSearchAnchor("show_predictions"),
                    onCheckedChange = { newValue ->
                        showPredictions = newValue
                        prefs.edit().putBoolean("show_predictions", newValue).apply()
                    }
                )
            }
            
            
            PreferenceCategory(title = context.getString(R.string.settings_category_display))
            PreferenceGroup {
                SwitchPreference(
                    title = context.getString(R.string.settings_pred_on_key_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_on_key_suggestions),
                    summary = context.getString(R.string.settings_pred_on_key_summary),
                    enabled = showPredictions,
                    checked = onKeyPredictions,
                    modifier = Modifier.settingsSearchAnchor("on_key_predictions"),
                    onCheckedChange = { newValue ->
                        onKeyPredictions = newValue
                        prefs.edit().putBoolean("on_key_predictions", newValue).apply()
                    }
                )
            
                // Overrides an app's "no suggestions" declaration (e.g. Google Keep).
                SwitchPreference(
                    title = context.getString(R.string.settings_pred_force_suggestions_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_force_suggestions),
                    summary = context.getString(R.string.settings_pred_force_suggestions_summary),
                    enabled = showPredictions,
                    checked = forceSuggestions,
                    modifier = Modifier.settingsSearchAnchor("pref_force_suggestions"),
                    onCheckedChange = { newValue ->
                        forceSuggestions = newValue
                        prefs.edit().putBoolean("pref_force_suggestions", newValue).apply()
                    }
                )

                SwitchPreference(
                    title = context.getString(R.string.settings_search_suggestions_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_prediction),
                    summary = context.getString(R.string.settings_search_suggestions_summary),
                    checked = searchSuggestions,
                    modifier = Modifier.settingsSearchAnchor("pref_search_suggestions"),
                    onCheckedChange = { newValue ->
                        searchSuggestions = newValue
                        prefs.edit().putBoolean("pref_search_suggestions", newValue).apply()
                    }
                )
            }

            PreferenceCategory(title = context.getString(R.string.settings_saved_emails_title))
            PreferenceGroup {
                SwitchPreference(
                    title = context.getString(R.string.settings_saved_emails_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_prediction),
                    summary = context.getString(R.string.settings_saved_emails_summary),
                    checked = savedEmails,
                    modifier = Modifier.settingsSearchAnchor("pref_saved_emails_enabled"),
                    onCheckedChange = { newValue ->
                        savedEmails = newValue
                        SavedEmails.setEnabled(context, newValue)
                    }
                )
                if (savedEmails) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = emailDraft,
                            onValueChange = { emailDraft = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text(context.getString(R.string.settings_saved_emails_hint)) },
                        )
                        TextButton(onClick = {
                            if (SavedEmails.add(context, emailDraft)) {
                                emailDraft = ""
                                savedAddresses = SavedEmails.addresses(context)
                            }
                        }) {
                            Text(context.getString(R.string.settings_saved_emails_add))
                        }
                    }
                    if (savedAddresses.isEmpty()) {
                        Text(
                            text = context.getString(R.string.settings_saved_emails_empty),
                            modifier = Modifier.padding(horizontal = spacing.medium),
                        )
                    }
                    savedAddresses.forEach { address ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(text = address, modifier = Modifier.weight(1f).padding(horizontal = spacing.medium))
                            TextButton(onClick = {
                                SavedEmails.remove(context, address)
                                savedAddresses = SavedEmails.addresses(context)
                            }) {
                                Text(context.getString(R.string.settings_saved_emails_remove))
                            }
                        }
                    }
                }
            }

            PreferenceCategory(title = context.getString(R.string.settings_category_sources))
            PreferenceGroup {
                SwitchPreference(
                    title = context.getString(R.string.settings_pred_next_word_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_next_word),
                    summary = context.getString(R.string.settings_pred_next_word_summary),
                    enabled = showPredictions,
                    checked = nextWordPrediction,
                    modifier = Modifier.settingsSearchAnchor("next_word_prediction"),
                    onCheckedChange = { newValue ->
                        nextWordPrediction = newValue
                        prefs.edit().putBoolean("next_word_prediction", newValue).apply()
                    }
                )

                SwitchPreference(
                    title = context.getString(R.string.settings_pred_emoji_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_emoji_suggestions),
                    summary = context.getString(R.string.settings_pred_emoji_summary),
                    enabled = showPredictions,
                    checked = emojiPredictions,
                    modifier = Modifier.settingsSearchAnchor("emoji_predictions"),
                    onCheckedChange = { newValue ->
                        emojiPredictions = newValue
                        prefs.edit().putBoolean("emoji_predictions", newValue).apply()
                    }
                )

                SwitchPreference(
                    title = context.getString(R.string.settings_pred_emoji_after_space_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_emoji_suggestions),
                    summary = context.getString(R.string.settings_pred_emoji_after_space_summary),
                    enabled = showPredictions && emojiPredictions,
                    checked = emojiAfterSpace,
                    modifier = Modifier.settingsSearchAnchor("emoji_after_space"),
                    onCheckedChange = { newValue ->
                        emojiAfterSpace = newValue
                        prefs.edit().putBoolean("emoji_after_space", newValue).apply()
                    }
                )
            
                SwitchPreference(
                    title = context.getString(R.string.settings_pred_personalized_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_learned_words),
                    summary = context.getString(R.string.settings_pred_personalized_summary),
                    enabled = showPredictions,
                    checked = personalizedSuggestions,
                    modifier = Modifier.settingsSearchAnchor("pref_key_use_personalized_dicts"),
                    onCheckedChange = { newValue ->
                        personalizedSuggestions = newValue
                        prefs.edit().putBoolean("pref_key_use_personalized_dicts", newValue).apply()
                    }
                )

                // Re-read at the start of every input session; no IME restart needed.
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    SwitchPreference(
                        title = context.getString(R.string.settings_pred_inline_autofill_title),
                        icon = ImageVector.vectorResource(R.drawable.ic_settings_inline_autofill),
                        summary = context.getString(R.string.settings_pred_inline_autofill_summary),
                        checked = inlineAutofill,
                        modifier = Modifier.settingsSearchAnchor("pref_inline_autofill_enabled"),
                        onCheckedChange = { newValue ->
                            inlineAutofill = newValue
                            prefs.edit().putBoolean("pref_inline_autofill_enabled", newValue).apply()
                        }
                    )
                }

                SwitchPreference(
                    title = context.getString(R.string.settings_pred_contacts_title),
                    icon = ImageVector.vectorResource(R.drawable.ic_settings_m_contacts),
                    summary = if (contacts.granted) {
                        context.getString(R.string.settings_pred_contacts_summary_enabled)
                    } else {
                        context.getString(R.string.settings_pred_contacts_summary_disabled)
                    },
                    checked = suggestContacts && contacts.granted,
                    enabled = showPredictions,
                    modifier = Modifier.settingsSearchAnchor("pref_key_use_contacts_dict"),
                    onCheckedChange = { newValue ->
                        if (newValue && !contacts.granted) {
                            contacts.request()
                        } else {
                            suggestContacts = newValue
                            prefs.edit().putBoolean("pref_key_use_contacts_dict", newValue).apply()
                        }
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(spacing.extraLarge))
        }
    }
}
