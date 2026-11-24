package dev.bbkb.ime.keyboard.inputboard.clipboard

import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.inputmethodservice.InputMethodService
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import dev.bbkb.ime.R
import dev.bbkb.ime.keyboard.KeyboardColorManager

/** Editing actions on the clipboard board. Uses the focused editor, themed from the keyboard palette. */
object TextEditingBar {
    private const val BAR_ID = 0x441001

    @JvmStatic
    fun attach(parent: RelativeLayout) {
        if (parent.findViewById<LinearLayout>(BAR_ID) != null) return
        val bar = LinearLayout(parent.context).apply {
            id = BAR_ID
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
                setOnClickListener { perform(parent.context, action) }
            })
        }
        val params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT,
        )
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        parent.addView(bar, params)
        val list = parent.findViewById<android.view.View>(R.id.clipboardView)
        (list.layoutParams as? RelativeLayout.LayoutParams)?.addRule(RelativeLayout.BELOW, BAR_ID)
    }

    @JvmStatic
    fun perform(context: Context, action: String) {
        val ime = findIme(context) ?: return
        val connection = ime.currentInputConnection ?: return
        when (action) {
            "all" -> connection.performContextMenuAction(android.R.id.selectAll)
            "cut" -> connection.performContextMenuAction(android.R.id.cut)
            "copy" -> connection.performContextMenuAction(android.R.id.copy)
            "paste" -> connection.performContextMenuAction(android.R.id.paste)
            "left" -> connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
            "right" -> connection.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT))
            "word" -> connection.deleteSurroundingText(16, 0)
        }
    }

    private fun findIme(context: Context): InputMethodService? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is InputMethodService) return current
            current = current.baseContext
        }
        return current as? InputMethodService
    }
}
