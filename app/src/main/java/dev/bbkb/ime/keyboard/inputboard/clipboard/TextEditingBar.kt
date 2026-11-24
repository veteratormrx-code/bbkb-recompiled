package dev.bbkb.ime.keyboard.inputboard.clipboard

import android.content.Context
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.inputmethodservice.InputMethodService
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import dev.bbkb.ime.R
import dev.bbkb.ime.core.subtypeswitcher.InputTypeUtils
import dev.bbkb.ime.keyboard.KeyboardColorManager

/** Editing actions on the clipboard board. Uses the focused editor, themed from the keyboard palette. */
object TextEditingBar {
    private const val BAR_ID = 0x441001

    @JvmStatic
    fun attach(parent: RelativeLayout, editorContext: Context) {
        val list = parent.findViewById<android.view.View>(R.id.clipboardView) ?: return
        val container = list.parent as? RelativeLayout ?: return
        if (container.findViewById<HorizontalScrollView>(BAR_ID) != null) return
        val bar = LinearLayout(parent.context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(KeyboardColorManager.backgroundColor)
        }
        val actions = listOf(
            R.string.text_edit_select_all to "all",
            R.string.text_edit_cut to "cut",
            R.string.text_edit_copy to "copy",
            R.string.text_edit_paste to "paste",
            R.string.text_edit_left to "left",
            R.string.text_edit_right to "right",
            R.string.text_edit_word_back to "word",
        )
        for ((label, action) in actions) {
            bar.addView(Button(parent.context).apply {
                text = parent.context.getString(label)
                setTextColor(KeyboardColorManager.textColor)
                setBackgroundColor(KeyboardColorManager.keyColor)
                setOnClickListener {
                    val ime = editorContext as? InputMethodService ?: return@setOnClickListener
                    val editor = ime.currentInputEditorInfo ?: return@setOnClickListener
                    if (!InputTypeUtils.isAnyPasswordInputType(editor.inputType)) {
                        perform(ime.currentInputConnection, action)
                    }
                }
            })
        }
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT,
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        container.addView(HorizontalScrollView(parent.context).apply {
            id = BAR_ID
            isHorizontalScrollBarEnabled = false
            addView(bar)
        }, params)
        (list.layoutParams as? RelativeLayout.LayoutParams)?.addRule(RelativeLayout.BELOW, BAR_ID)
        val empty = container.findViewById<android.view.View>(R.id.empty_clipboard)
        (empty?.layoutParams as? RelativeLayout.LayoutParams)?.addRule(RelativeLayout.BELOW, BAR_ID)
    }

    @JvmStatic
    fun perform(connection: InputConnection?, action: String) {
        if (connection == null) return
        when (action) {
            "all" -> connection.performContextMenuAction(android.R.id.selectAll)
            "cut" -> connection.performContextMenuAction(android.R.id.cut)
            "copy" -> connection.performContextMenuAction(android.R.id.copy)
            "paste" -> connection.performContextMenuAction(android.R.id.paste)
            "left" -> moveCursor(connection, KeyEvent.KEYCODE_DPAD_LEFT)
            "right" -> moveCursor(connection, KeyEvent.KEYCODE_DPAD_RIGHT)
            "word" -> moveCursor(connection, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.META_CTRL_ON)
        }
    }

    private fun moveCursor(connection: InputConnection, code: Int, modifiers: Int = 0) {
        val start = SystemClock.uptimeMillis()
        connection.sendKeyEvent(KeyEvent(start, start, KeyEvent.ACTION_DOWN, code, 0, modifiers))
        connection.sendKeyEvent(KeyEvent(start, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0, modifiers))
    }
}
