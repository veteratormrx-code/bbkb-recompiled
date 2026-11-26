package dev.bbkb.ime.keyboard.slideboard

import android.content.SharedPreferences
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.bbkb.ime.R
import dev.bbkb.ime.core.BlackBerryIME
import dev.bbkb.ime.core.settings.PrefsManager
import dev.bbkb.ime.core.settings.screens.readQuickPhrases
import dev.bbkb.ime.core.settings.ui.BbkbDialogs
import dev.bbkb.ime.keyboard.inputboard.BoardQueryInput
import org.json.JSONArray
import org.json.JSONObject

object PhraseTemplates {
    private const val PREF = "pref_phrase_templates"
    private const val LIMIT = 100
    private val placeholder = Regex("\\{([\\p{L}\\p{N}_ -]{1,40})\\}")

    data class Template(val group: String, val text: String)

    fun read(prefs: SharedPreferences): List<Template> {
        val json = prefs.all[PREF] as? String ?: return emptyList()
        if (json.length > 130_000) return emptyList()
        return runCatching {
            val entries = JSONArray(json)
            List(minOf(entries.length(), LIMIT)) { entries.optJSONObject(it) }
                .mapNotNull { entry ->
                    val text = entry?.optString("text").orEmpty().take(1000)
                    text.takeIf { it.isNotBlank() }?.let { Template(entry?.optString("group").orEmpty().take(48), it) }
                }
        }.getOrDefault(emptyList())
    }

    fun save(prefs: SharedPreferences, templates: List<Template>) {
        val json = JSONArray()
        templates.take(LIMIT).filter { it.text.isNotBlank() }.forEach {
            json.put(JSONObject().put("group", it.group.trim().take(48)).put("text", it.text.take(1000)))
        }
        prefs.edit().putString(PREF, json.toString()).apply()
    }

    internal fun fields(text: String): List<String> =
        placeholder.findAll(text).map { it.groupValues[1] }.distinct().take(12).toList()

    internal fun fill(text: String, values: Map<String, String>): String =
        placeholder.replace(text) { values[it.groupValues[1]] ?: it.value }

    @JvmStatic
    fun show(ime: BlackBerryIME) {
        val connection = ime.currentInputConnection ?: return
        val generation = ime.getInputLogic().sessionGeneration
        val prefs = PrefsManager.getPrefs(ime)
        val legacyGroup = ime.getString(R.string.settings_quick_phrases_screen_title)
        val templates = readQuickPhrases(ime, prefs).filter { it.isNotBlank() }.map {
            Template(legacyGroup, it)
        } + read(prefs)
        val content = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL }
        val rows = LinearLayout(ime).apply { orientation = LinearLayout.VERTICAL }
        val maxHeight = (300 * ime.resources.displayMetrics.density).toInt()
        val scroll = ScrollView(ime).apply { addView(rows) }
        val dialog = BbkbDialogs.builder(ime)
            .setTitle(R.string.phrase_templates_title)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        fun validSession(): Boolean =
            ime.getInputLogic().sessionGeneration == generation && ime.currentInputConnection === connection
        fun insert(text: String) {
            if (validSession()) ime.getInputLogic().pasteText(text)
            dialog.dismiss()
        }
        fun prepare(template: Template) {
            val names = fields(template.text)
            if (names.isEmpty()) {
                insert(template.text)
                return
            }
            content.removeAllViews()
            rows.removeAllViews()
            val values = mutableMapOf<String, String>()
            val preview = TextView(ime).apply { text = template.text }
            val insert = Button(ime).apply {
                setText(R.string.phrase_template_insert)
                isEnabled = false
                setOnClickListener { insert(preview.text.toString()) }
            }
            for (name in names) {
                rows.addView(BoardQueryInput(ime, name) { value ->
                    if (value.isBlank()) values.remove(name) else values[name] = value
                    preview.text = fill(template.text, values)
                    insert.isEnabled = names.all(values::containsKey)
                })
            }
            content.addView(preview)
            content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight))
            content.addView(insert)
        }
        fun filter(query: String) {
            rows.removeAllViews()
            for ((group, matching) in templates.filter {
                it.text.contains(query.trim(), ignoreCase = true) || it.group.contains(query.trim(), ignoreCase = true)
            }.groupBy { it.group }) {
                if (group.isNotBlank()) rows.addView(TextView(ime).apply { text = group })
                for (template in matching) {
                    rows.addView(Button(ime).apply {
                        text = template.text
                        maxLines = 2
                        setOnClickListener { prepare(template) }
                    })
                }
            }
        }
        content.addView(BoardQueryInput(ime, ime.getString(R.string.phrase_template_search), ::filter))
        content.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxHeight))
        filter("")
        ime.showAlertDialog(dialog)
    }
}
