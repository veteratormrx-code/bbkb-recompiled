package dev.bbkb.ime.keyboard.inputboard

import android.content.Context
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.bbkb.ime.R
import dev.bbkb.ime.keyboard.KeyboardColorManager

/** An in-board query. Its keys never write to the application's active input connection. */
class BoardQueryInput(
    context: Context,
    private val hint: String,
    private val onQueryChanged: (String) -> Unit,
) : LinearLayout(context) {
    private val query = StringBuilder()
    private val keys = LinearLayout(context).apply { orientation = VERTICAL }
    private val display = TextView(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setTextColor(KeyboardColorManager.textColor)
        setPadding(dp(8), 0, dp(8), 0)
        maxLines = 1
        text = this@BoardQueryInput.hint
        setOnClickListener { toggleKeys() }
    }
    private var symbols = false
    private var russian = false
    private var shifted = false

    init {
        orientation = VERTICAL
        addView(LinearLayout(context).apply {
            addView(this@BoardQueryInput.display, LayoutParams(0, dp(40), 1f))
            addView(key("⌕", hint) { toggleKeys() }, LayoutParams(dp(42), dp(40)))
            addView(key("×", context.getString(R.string.board_clear_query)) { clear() },
                LayoutParams(dp(42), dp(40)))
        })
        addView(keys)
        keys.visibility = GONE
    }

    fun reset() {
        clear()
        keys.visibility = GONE
    }

    private fun clear() {
        if (query.isEmpty()) return
        query.clear()
        notifyQuery()
    }

    private fun notifyQuery() {
        val value = query.toString()
        display.text = value.ifEmpty { hint }
        onQueryChanged(value)
    }

    private fun toggleKeys() {
        if (keys.visibility == VISIBLE) {
            keys.visibility = GONE
        } else {
            layoutKeys()
            keys.visibility = VISIBLE
        }
    }

    private fun layoutKeys() {
        keys.removeAllViews()
        val rows = when {
            symbols -> listOf("1234567890", ".:/-@_?&=+")
            russian -> listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        for (row in rows) {
            keys.addView(LinearLayout(context).apply {
                for (letter in row) {
                    val shown = if (shifted) letter.uppercaseChar() else letter
                    addView(key(shown.toString()) {
                        if (query.length < 100) {
                            query.append(shown)
                            notifyQuery()
                            if (shifted) {
                                shifted = false
                                layoutKeys()
                            }
                        }
                    }, LayoutParams(0, dp(36), 1f))
                }
            })
        }
        keys.addView(LinearLayout(context).apply {
            addView(key(if (russian) "EN" else "RU") {
                russian = !russian
                symbols = false
                layoutKeys()
            }, LayoutParams(0, dp(36), 1f))
            addView(key(if (symbols) "ABC" else "123") {
                symbols = !symbols
                layoutKeys()
            }, LayoutParams(0, dp(36), 1f))
            addView(key("⇧", context.getString(R.string.pref_lock_row_shift)) {
                shifted = !shifted
                layoutKeys()
            }, LayoutParams(0, dp(36), 1f))
            addView(key("␣", context.getString(R.string.board_query_space)) {
                if (query.length < 100) {
                    query.append(' ')
                    notifyQuery()
                }
            }, LayoutParams(0, dp(36), 1f))
            addView(key("⌫", context.getString(R.string.board_delete_query_character)) {
                if (query.isNotEmpty()) {
                    query.deleteCharAt(query.lastIndex)
                    notifyQuery()
                }
            }, LayoutParams(0, dp(36), 1f))
            addView(key("✓", context.getString(R.string.board_show_results)) { keys.visibility = GONE },
                LayoutParams(0, dp(36), 1f))
        })
    }

    private fun key(label: String, description: String = label, action: () -> Unit): Button =
        Button(context).apply {
            text = label
            contentDescription = description
            textSize = 14f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(2), 0, dp(2), 0)
            setTextColor(KeyboardColorManager.textColor)
            setBackgroundColor(KeyboardColorManager.keyColor)
            setOnClickListener { action() }
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
