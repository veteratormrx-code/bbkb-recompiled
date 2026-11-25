package dev.bbkb.ime.keyboard.inputboard.tools

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.inputmethodservice.InputMethodService
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import dev.bbkb.ime.R
import dev.bbkb.ime.core.subtypeswitcher.InputTypeUtils
import dev.bbkb.ime.keyboard.KeyboardColorManager
import dev.bbkb.ime.keyboard.inputboard.clipboard.TextEditingBar
import dev.bbkb.ime.mlkit.HandwritingView
import dev.bbkb.ime.mlkit.MlKitModels

/** In-keyboard panel for text editing, translation, and handwriting. Colors follow the keyboard theme. */
class ToolBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    enum class Mode { EDIT, TRANSLATE, INK }

    var mode: Mode = Mode.EDIT
        private set

    private val status = TextView(context)
    private val sourceLine = TextView(context)
    private val resultLine = TextView(context)
    private val ink = HandwritingView(context)
    private val candidates = LinearLayout(context)
    private val candidateHost = FrameLayout(context)
    private val inkHint = TextView(context)
    private var inkOptions = emptyList<String>()
    private val recognizeLater = Runnable { recognizeNow() }
    private var source = MlKitModels.english()
    private var target = MlKitModels.russian()
    /** The phrase whose direction the user flipped. Other phrases are detected from their letters. */
    private var pinnedText: String? = null
    private var inkLanguage = "ru"
    private var translationResult = ""
    private var sourceText = ""
    /** Swap already exchanged the two sides, so the next open must not read the field over them. */
    private var keepPair = false
    private var replacingSelection = false
    private var replaceBefore = 0
    private var translateAction: Button? = null
    private var translationConnection: InputConnection? = null
    private var translateGeneration = 0
    private var inkGeneration = 0
    private var host: InputMethodService? = null
    private var editCaption: TextView? = null
    private var editPreview: TextView? = null
    private var editSelect: Button? = null
    private var editDelete: Button? = null
    private var editMiddle: Button? = null
    private var editPaste: Button? = null
    private var editSelecting = false
    private var editCutMode = false
    private var editRepeatAction: (() -> Unit)? = null
    private val editRepeat: Runnable = object : Runnable {
        override fun run() {
            val action = editRepeatAction ?: return
            action()
            postDelayed(this, 70)
        }
    }

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        paintChrome()
        prepareLine(status, 4)
        prepareLine(sourceLine, 2)
        prepareLine(resultLine, 2)
        candidates.orientation = HORIZONTAL
        ink.minimumHeight = dp(148)
        ink.onStrokeStarted = { cancelRecognize() }
        ink.onStrokeFinished = { scheduleRecognize() }
        prepareLine(inkHint, 2)
        inkHint.gravity = Gravity.CENTER_VERTICAL
        inkHint.setPadding(dp(12), 0, dp(12), 0)
    }

    fun show(next: Mode, ime: InputMethodService) {
        host = ime
        show(next)
    }

    private fun show(next: Mode) {
        mode = next
        paintChrome()
        detach(status)
        detach(sourceLine)
        detach(resultLine)
        detach(ink)
        detach(candidates)
        removeAllViews()
        stopEditRepeat()
        setInkChrome(next == Mode.INK)
        if (next != Mode.INK) pinBoardToKeyboard()
        when (next) {
            Mode.EDIT -> showEdit()
            Mode.TRANSLATE -> showTranslate()
            Mode.INK -> showInk()
        }
        visibility = VISIBLE
    }

    fun hideBoard() {
        visibility = GONE
        stopEditRepeat()
        setInkChrome(false)
        host = null
        pinnedText = null
        keepPair = false
        dropTranslation()
        cancelRecognize()
        sourceLine.text = ""
        status.text = ""
        candidates.removeAllViews()
        ink.clear()
    }

    fun isUp(): Boolean = visibility == VISIBLE

    private fun showEdit() {
        editSelecting = false
        editCaption = caption(R.string.text_edit_cursor)
        addView(editCaption)
        editPreview = TextView(context).apply {
            setTextColor(KeyboardColorManager.textColor)
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 1
            includeFontPadding = false
            setPadding(dp(8), dp(2), dp(8), dp(2))
            background = cardBackground()
        }
        addView(editPreview, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        addView(editPad(), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(2)
        })
        addView(editWordRow(), LayoutParams(LayoutParams.MATCH_PARENT, dp(36)).apply {
            topMargin = dp(2)
        })
        addView(editActionRow(), LayoutParams(LayoutParams.MATCH_PARENT, dp(40)).apply {
            topMargin = dp(2)
        })
        refreshEdit()
    }

    /** Arrows move the cursor. The centre key makes those same arrows grow a selection. */
    private fun editPad(): View {
        val pad = EvenRows(context)
        fun line(fill: LinearLayout.() -> Unit) {
            val row = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER
                fill()
            }
            pad.addView(row, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        fun LinearLayout.cell(view: View, weight: Float) {
            addView(view, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
                topMargin = dp(4)
                bottomMargin = dp(4)
            })
        }
        fun LinearLayout.gap(weight: Float) {
            addView(View(context), LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
        }
        line {
            gap(1f)
            cell(arrowKey("↑", context.getString(R.string.text_edit_up)) { moveEdit("up") }, 1.15f)
            gap(1f)
        }
        line {
            cell(arrowKey("←", context.getString(R.string.text_edit_left)) { moveEdit("left") }, 1f)
            editSelect = editKey(context.getString(R.string.text_edit_select_toggle))
            cell(editSelect!!, 1.15f)
            cell(arrowKey("→", context.getString(R.string.text_edit_right)) { moveEdit("right") }, 1f)
        }
        line {
            gap(1f)
            cell(arrowKey("↓", context.getString(R.string.text_edit_down)) { moveEdit("down") }, 1.15f)
            gap(1f)
        }
        return pad
    }

    private fun editWordRow(): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        row.addView(
            holdKey(context.getString(R.string.text_edit_word_left), context.getString(R.string.text_edit_word_left)) { moveEdit("word") }.apply { textSize = 16f },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginEnd = dp(4) },
        )
        row.addView(
            holdKey(context.getString(R.string.text_edit_word_right), context.getString(R.string.text_edit_word_right)) { moveEdit("wordRight") }.apply { textSize = 16f },
            LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply { marginStart = dp(4) },
        )
        return row
    }

    private fun editActionRow(): View {
        val row = LinearLayout(context).apply { orientation = HORIZONTAL }
        fun place(view: Button) {
            row.addView(view, LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
        }
        editDelete = holdKey(context.getString(R.string.text_edit_delete), context.getString(R.string.text_edit_delete)) {
            if (editCutMode) {
                editCutMode = false
                stopEditRepeat()
                editCommand("cut")
            } else {
                moveEdit("backspace")
            }
        }.apply { textSize = 15f }
        place(editDelete!!)
        editMiddle = editKey(context.getString(R.string.text_edit_select_all)).apply { textSize = 15f }
        place(editMiddle!!)
        editPaste = editKey(context.getString(R.string.text_edit_paste)).apply { textSize = 15f }
        place(editPaste!!)
        return row
    }

    private fun moveEdit(action: String) {
        val extend = editSelecting && action != "backspace"
        TextEditingBar.perform(toolConnection(), action, extend)
        refreshEdit()
    }

    private fun editCommand(action: String) {
        TextEditingBar.perform(toolConnection(), action, false)
        refreshEdit()
        postDelayed({ if (isUp() && mode == Mode.EDIT) refreshEdit() }, 50)
    }

    private fun refreshEdit() {
        val captionView = editCaption ?: return
        val preview = editPreview ?: return
        val connection = toolConnection()
        if (connection == null) {
            captionView.setText(R.string.translate_no_field)
            preview.setText(R.string.text_edit_empty)
            preview.alpha = 0.7f
            preview.textSize = 16f
            paintEditActions(hasSelection = false, clip = null, live = false)
            return
        }
        val before = connection.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        val after = connection.getTextAfterCursor(200, 0)?.toString().orEmpty()
        val selected = connection.getSelectedText(0)?.toString().orEmpty()
        val clip = clipboardSnippet()
        when {
            before.isEmpty() && after.isEmpty() && selected.isEmpty() -> {
                captionView.setText(R.string.text_edit_cursor)
                preview.setText(R.string.text_edit_empty)
                preview.alpha = 0.7f
                preview.textSize = 16f
            }
            selected.isNotEmpty() -> {
                captionView.text = context.getString(R.string.text_edit_selected, selected.length)
                preview.text = selectionPreview(before, selected, after)
                preview.alpha = 1f
                preview.textSize = 20f
            }
            else -> {
                captionView.setText(if (editSelecting) R.string.text_edit_select_lead else R.string.text_edit_cursor)
                preview.text = cursorPreview(before, after)
                preview.alpha = 1f
                preview.textSize = 20f
            }
        }
        paintEditActions(selected.isNotEmpty(), clip, live = true)
    }

    private fun cursorPreview(before: String, after: String): CharSequence {
        val rawBefore = before.substringAfterLast('\n')
        val rawAfter = after.substringBefore('\n')
        val head = if (rawBefore.length > 22) "…" + rawBefore.takeLast(22) else rawBefore
        val tail = if (rawAfter.length > 22) rawAfter.take(22) + "…" else rawAfter
        val text = SpannableStringBuilder(head).append("│").append(tail)
        text.setSpan(StyleSpan(Typeface.BOLD), head.length, head.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    private fun selectionPreview(before: String, selected: String, after: String): CharSequence {
        val rawBefore = before.substringAfterLast('\n')
        val head = if (rawBefore.length > 10) "…" + rawBefore.takeLast(10) else rawBefore
        val body = selected.replace('\n', ' ').let { if (it.length > 42) it.take(42) + "…" else it }
        val tail = after.substringBefore('\n').take(10)
        val text = SpannableStringBuilder(head)
        val start = text.length
        text.append(body)
        text.setSpan(
            BackgroundColorSpan(
                androidx.core.graphics.ColorUtils.blendARGB(
                    KeyboardColorManager.backgroundColor,
                    KeyboardColorManager.textColor,
                    0.32f,
                ),
            ),
            start,
            text.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
        text.append(tail)
        return text
    }

    /** The clipboard text a paste would insert, or null when nothing readable is there. */
    private fun clipboardSnippet(): String? {
        return try {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
            val clip = manager.primaryClip ?: return null
            if (clip.itemCount == 0) return null
            val text = clip.getItemAt(0).coerceToText(context)?.toString()?.replace('\n', ' ')?.trim().orEmpty()
            if (text.isEmpty()) null else if (text.length > 6) text.take(6) + "…" else text
        } catch (_: RuntimeException) {
            null
        }
    }

    private fun paintEditActions(hasSelection: Boolean, clip: String?, live: Boolean) {
        val select = editSelect ?: return
        select.text = context.getString(if (editSelecting) R.string.text_edit_selecting_on else R.string.text_edit_select_toggle)
        paintEditKey(select, primary = editSelecting, enabled = live)
        select.setOnClickListener {
            editSelecting = !editSelecting
            refreshEdit()
        }
        editCutMode = hasSelection
        val delete = editDelete ?: return
        delete.setText(if (hasSelection) R.string.text_edit_cut else R.string.text_edit_delete)
        paintEditKey(delete, primary = false, enabled = live)
        val middle = editMiddle ?: return
        if (hasSelection) {
            middle.setText(R.string.text_edit_copy)
            paintEditKey(middle, primary = true, enabled = live)
            middle.setOnClickListener { editCommand("copy") }
        } else {
            middle.setText(R.string.text_edit_select_all)
            paintEditKey(middle, primary = false, enabled = live)
            middle.setOnClickListener { editCommand("all") }
        }
        val paste = editPaste ?: return
        paste.text = if (clip != null) context.getString(R.string.text_edit_paste_clip, clip) else context.getString(R.string.text_edit_paste)
        paintEditKey(paste, primary = clip != null && !hasSelection, enabled = live)
        paste.setOnClickListener { editCommand("paste") }
    }

    private fun stopEditRepeat() {
        editRepeatAction = null
        removeCallbacks(editRepeat)
    }

    private fun arrowKey(label: String, description: String, action: () -> Unit): Button {
        return holdKey(label, description, action).apply {
            textSize = 20f
            setPadding(0, 0, 0, 0)
        }
    }

    private fun holdKey(label: String, description: String, action: () -> Unit): Button {
        val key = editKey(label)
        key.contentDescription = description
        key.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    editRepeatAction = action
                    action()
                    removeCallbacks(editRepeat)
                    postDelayed(editRepeat, 320)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopEditRepeat()
                    true
                }
                else -> false
            }
        }
        return key
    }

    private fun editKey(label: String): Button {
        return Button(context).apply {
            text = label
            isAllCaps = false
            transformationMethod = null
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            textSize = 16f
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(4), dp(2), dp(4), dp(2))
            includeFontPadding = false
            paintEditKey(this, primary = false, enabled = true)
        }
    }

    private fun paintEditKey(button: Button, primary: Boolean, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
        if (primary && enabled) {
            button.setTextColor(KeyboardColorManager.backgroundColor)
            button.setBackgroundColor(KeyboardColorManager.textColor)
        } else {
            button.setTextColor(KeyboardColorManager.textColor)
            button.background = cardBackground()
        }
    }

    private fun showTranslate() {
        val holding = keepPair
        keepPair = false
        val peeked = toolConnection()?.let { currentSourceText(it, remember = false) }.orEmpty()
        if (!holding && (pinnedText == null || pinnedText != peeked)) {
            pinnedText = null
            detectDirection(peeked)
        }
        addView(languageRow())
        addView(labeledCard(R.string.translate_source_label, sourceLine), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
        sourceLine.textSize = 15f
        sourceLine.maxLines = 3
        prepareLine(resultLine, 3)
        resultLine.textSize = 18f
        resultLine.gravity = Gravity.CENTER
        resultLine.setPadding(dp(12), dp(6), dp(12), dp(6))
        resultLine.background = cardBackground()
        addView(resultLine, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
            topMargin = dp(4)
        })
        translateAction = inkButton(context.getString(R.string.translate_insert), true) { }
        addView(translateAction, LayoutParams(LayoutParams.MATCH_PARENT, dp(36)).apply {
            topMargin = dp(4)
        })
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            sourceText = ""
            translationResult = ""
            sourceLine.text = blocked
            showResult("", false)
            setTranslateAction(context.getString(R.string.translate_failed), false) {}
            return
        }
        if (holding) showHeldPair() else readFieldAndTranslate()
    }

    /** The pair on screen is the truth. Swap has already exchanged it, so the field stays untouched. */
    private fun showHeldPair() {
        sourceLine.text = sourceText.ifEmpty { context.getString(R.string.translate_source_empty) }
        sourceLine.alpha = if (sourceText.isEmpty()) 0.7f else 1f
        val known = sourceText.takeIf { it.isNotEmpty() }?.let { translations.recall(source, target, it) }
        if (known != null) translationResult = known
        if (translationResult.isNotEmpty()) {
            translationConnection = toolConnection()
            showResult(translationResult, true)
            setTranslateAction(context.getString(R.string.translate_insert), true) { insertTranslation() }
            return
        }
        val connection = toolConnection()
        if (connection != null && sourceText.isNotEmpty()) runTranslation(connection)
    }

    /** Left is the language of the text, right is the language of the translation. */
    private fun languageRow(): View {
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        line.addView(languageSide(R.string.translate_from, languageName(source)), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        line.addView(inkButton(context.getString(R.string.translate_swap), false) { swapLanguages() }, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(48)).apply {
            marginStart = dp(6)
            marginEnd = dp(6)
        })
        line.addView(languageSide(R.string.translate_to, languageName(target)), LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        return line
    }

    /** Label and value share one card, so a short keyboard still fits the result and the button. */
    private fun labeledCard(labelRes: Int, value: TextView): View {
        prepareLine(value, 3)
        value.setPadding(0, dp(2), 0, 0)
        value.setBackgroundColor(0)
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground()
            setPadding(dp(12), dp(6), dp(12), dp(6))
            addView(TextView(context).apply {
                text = context.getString(labelRes)
                setTextColor(KeyboardColorManager.textColor)
                alpha = 0.65f
                textSize = 12f
            })
            addView(value, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
        }
    }

    private fun languageSide(captionRes: Int, name: String): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground()
            setPadding(dp(8), dp(3), dp(8), dp(3))
            addView(TextView(context).apply {
                text = context.getString(captionRes)
                setTextColor(KeyboardColorManager.textColor)
                alpha = 0.65f
                textSize = 12f
            })
            addView(TextView(context).apply {
                text = name
                setTextColor(KeyboardColorManager.textColor)
                textSize = 15f
                setPadding(dp(6), 0, 0, 0)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }
    }

    private fun caption(textRes: Int): TextView {
        return TextView(context).apply {
            text = context.getString(textRes)
            setTextColor(KeyboardColorManager.textColor)
            alpha = 0.65f
            textSize = 12f
            setPadding(dp(2), dp(8), dp(2), 0)
        }
    }

    private fun languageName(lang: String): String = when (lang) {
        MlKitModels.russian() -> context.getString(R.string.translate_lang_ru)
        MlKitModels.english() -> context.getString(R.string.translate_lang_en)
        else -> lang
    }

    /** Key color when it differs from the keyboard; otherwise a light lift so the card is visible. */
    private fun cardColor(): Int {
        val key = KeyboardColorManager.keyColor
        val background = KeyboardColorManager.backgroundColor
        if (key != background) return key
        return androidx.core.graphics.ColorUtils.blendARGB(background, KeyboardColorManager.textColor, 0.14f)
    }

    private fun cardBackground(): android.graphics.drawable.GradientDrawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(cardColor())
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun showResult(text: String, hero: Boolean) {
        resultLine.text = text
        resultLine.textSize = if (hero) 28f else 16f
        resultLine.alpha = if (hero) 1f else 0.7f
        resultLine.gravity = Gravity.CENTER
        resultLine.setTypeface(null, if (hero) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    private fun setTranslateAction(label: String, enabled: Boolean, action: () -> Unit) {
        val button = translateAction ?: return
        button.text = label
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.45f
        button.setOnClickListener { if (button.isEnabled) action() }
    }

    private fun showInk() {
        val blocked = gmsBlockMessage()
        candidateHost.removeAllViews()
        candidateHost.addView(
            candidateScroller(),
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        candidateHost.addView(
            inkHint,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT),
        )
        inkHint.text = blocked ?: context.getString(R.string.handwriting_hint)
        inkHint.visibility = if (inkOptions.isEmpty()) VISIBLE else GONE
        addView(candidateHost, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        addView(ink, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        if (inkOptions.isNotEmpty()) {
            val connection = toolConnection()
            if (connection != null) showCandidateButtons(inkOptions, connection)
        }
        inkBar()
    }

    private var keyboardFrameHeight = ViewGroup.LayoutParams.WRAP_CONTENT
    private var keyboardFrameCaptured = false

    /**
     * The panel is exactly the keyboard. Shorter leaves a gap under the buttons. Taller covers the
     * text field. A weighted child inside wrap_content would otherwise grow to the whole screen.
     */
    private fun setInkChrome(open: Boolean) {
        val frame = parent as? ViewGroup ?: return
        val keyboard = frame.findViewById<View>(R.id.keyboard_view)
        val board = layoutParams ?: return
        val frameHeight = frame.layoutParams
        if (open) {
            val height = keyboardHeight(keyboard)
            if (frameHeight != null && height > 0) {
                captureFrame(frameHeight)
                frameHeight.height = height
                frame.layoutParams = frameHeight
            }
            keyboard?.visibility = GONE
            board.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            keyboard?.visibility = VISIBLE
            board.height = ViewGroup.LayoutParams.MATCH_PARENT
            if (frameHeight != null) restoreFrame(frame, frameHeight)
        }
        layoutParams = board
    }

    /** Keeps editing and translation at the keyboard's height, drawn over the keys. */
    private fun pinBoardToKeyboard() {
        val frame = parent as? ViewGroup ?: return
        val height = keyboardHeight(frame.findViewById(R.id.keyboard_view))
        val frameHeight = frame.layoutParams ?: return
        val board = layoutParams ?: return
        if (height > 0) {
            captureFrame(frameHeight)
            frameHeight.height = height
            frame.layoutParams = frameHeight
        }
        board.height = ViewGroup.LayoutParams.MATCH_PARENT
        layoutParams = board
    }

    private fun keyboardHeight(keyboard: View?): Int {
        return keyboard?.height?.takeIf { it > 0 }
            ?: keyboard?.measuredHeight?.takeIf { it > 0 }
            ?: 0
    }

    private fun captureFrame(frameHeight: ViewGroup.LayoutParams) {
        if (keyboardFrameCaptured) return
        keyboardFrameHeight = frameHeight.height
        keyboardFrameCaptured = true
    }

    private fun restoreFrame(frame: ViewGroup, frameHeight: ViewGroup.LayoutParams) {
        if (!keyboardFrameCaptured) return
        frameHeight.height = keyboardFrameHeight
        frame.layoutParams = frameHeight
        keyboardFrameCaptured = false
    }

    /** One row under the original pad: undo, space inserts the word, clear, language. */
    private fun inkBar() {
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(KeyboardColorManager.backgroundColor)
        }
        fun add(label: String, primary: Boolean, action: () -> Unit) {
            line.addView(inkButton(label, primary, action), LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                marginEnd = dp(4)
            })
        }
        add(context.getString(R.string.handwriting_undo), false) { undoInk() }
        add(context.getString(R.string.handwriting_space), true) { insertInkSpace() }
        add(context.getString(R.string.handwriting_clear), false) { resetInk() }
        add(if (inkLanguage == "ru") "РУ" else "АН", false) { toggleInkLanguage() }
        addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(4)
        })
    }

    private fun swapLanguages() {
        if (sourceText.isNotEmpty() && translationResult.isNotEmpty()) {
            val exchanged = translations.exchange(source, target, sourceText, translationResult)
            source = exchanged.from
            target = exchanged.to
            sourceText = exchanged.source
            translationResult = exchanged.translation
            pinnedText = sourceText
            keepPair = true
            if (mode == Mode.TRANSLATE) show(Mode.TRANSLATE)
            return
        }
        val previous = source
        source = target
        target = previous
        pinnedText = sourceText.ifEmpty { null }
        dropTranslation()
        keepPair = sourceText.isNotEmpty()
        if (mode == Mode.TRANSLATE) show(Mode.TRANSLATE)
    }

    private fun dropTranslation() {
        translateGeneration++
        translationResult = ""
        translationConnection = null
    }

    private fun readFieldAndTranslate() {
        val connection = toolConnection()
        if (connection == null) {
            sourceText = ""
            sourceLine.text = context.getString(R.string.translate_no_field)
            showResult("", false)
            setTranslateAction(context.getString(R.string.translate_no_field), false) {}
            return
        }
        sourceText = currentSourceText(connection, remember = true)
        if (pinnedText != sourceText) {
            val previousSource = source
            val previousTarget = target
            pinnedText = null
            detectDirection(sourceText)
            if (source != previousSource || target != previousTarget) {
                show(Mode.TRANSLATE)
                return
            }
        }
        sourceLine.text = sourceText.ifEmpty { context.getString(R.string.translate_source_empty) }
        sourceLine.alpha = if (sourceText.isEmpty()) 0.7f else 1f
        val known = translations.recall(source, target, sourceText)
        if (known != null) {
            translationResult = known
            translationConnection = connection
            showResult(known, true)
            setTranslateAction(context.getString(R.string.translate_insert), true) { insertTranslation() }
            return
        }
        if (sourceText.isEmpty()) {
            showResult(context.getString(R.string.translate_result_empty), false)
            setTranslateAction(context.getString(R.string.translate_take), true) { readFieldAndTranslate() }
            return
        }
        if (source == target) {
            showResult(context.getString(R.string.translate_failed), false)
            setTranslateAction(context.getString(R.string.translate_swap), true) { swapLanguages() }
            return
        }
        runTranslation(connection)
    }

    /**
     * Cyrillic is Russian, Latin is English. Whichever script has more letters wins.
     * Digits and emoji leave the current pair alone.
     */
    private fun detectDirection(text: String) {
        val lang = scriptLanguage(text) ?: return
        source = lang
        target = if (lang == MlKitModels.russian()) MlKitModels.english() else MlKitModels.russian()
    }

    private fun scriptLanguage(text: String): String? {
        var cyrillic = 0
        var latin = 0
        for (ch in text) {
            if (!ch.isLetter()) continue
            when (Character.UnicodeScript.of(ch.code)) {
                Character.UnicodeScript.CYRILLIC -> cyrillic++
                Character.UnicodeScript.LATIN -> latin++
                else -> Unit
            }
        }
        return when {
            cyrillic == 0 && latin == 0 -> null
            cyrillic >= latin -> MlKitModels.russian()
            else -> MlKitModels.english()
        }
    }

    /** The selection, or the sentence before the cursor. [remember] records what insert replaces. */
    private fun currentSourceText(connection: InputConnection, remember: Boolean): String {
        val selected = connection.getSelectedText(0)?.toString()?.trim().orEmpty()
        if (selected.isNotEmpty()) {
            if (remember) {
                replacingSelection = true
                replaceBefore = 0
            }
            return selected.take(500)
        }
        if (remember) replacingSelection = false
        val raw = connection.getTextBeforeCursor(400, 0)?.toString().orEmpty()
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            if (remember) replaceBefore = 0
            return ""
        }
        val cut = trimmed.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
        val tail = if (cut in 0 until trimmed.length - 1) trimmed.substring(cut + 1).trim() else trimmed
        val sentence = tail.ifEmpty { trimmed }.take(240)
        if (remember) {
            val index = raw.lastIndexOf(sentence)
            replaceBefore = if (index >= 0) raw.length - index else sentence.length
        }
        return sentence
    }

    private fun runTranslation(connection: InputConnection) {
        showResult(context.getString(R.string.translate_working), false)
        setTranslateAction(context.getString(R.string.translate_working), false) {}
        translationResult = ""
        translationConnection = null
        val from = source
        val to = target
        val text = sourceText
        val generation = ++translateGeneration
        MlKitModels.translate(from, to, text) { ok, translated ->
            post {
                if (!isUp() || mode != Mode.TRANSLATE || generation != translateGeneration) return@post
                if (!ok || translated.isEmpty()) {
                    translationResult = ""
                    val missing = !ok && (translated.contains("download", true) || translated.contains("model", true))
                    showResult(
                        if (missing) {
                            context.getString(R.string.translate_download)
                        } else {
                            translated.ifBlank { context.getString(R.string.translate_failed) }
                        },
                        false,
                    )
                    if (missing) {
                        setTranslateAction(context.getString(R.string.translate_download), true) { downloadThenTranslate() }
                    } else {
                        setTranslateAction(context.getString(R.string.translate_failed), true) { readFieldAndTranslate() }
                    }
                    return@post
                }
                translations.remember(from, to, text, translated)
                translationResult = translated
                translationConnection = connection
                showResult(translated, true)
                setTranslateAction(context.getString(R.string.translate_insert), true) { insertTranslation() }
            }
        }
    }

    private fun downloadThenTranslate() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            showResult(blocked, false)
            return
        }
        val connection = toolConnection() ?: return
        setTranslateAction(context.getString(R.string.translate_download), false) {}
        showResult(context.getString(R.string.translate_download), false)
        val generation = ++translateGeneration
        MlKitModels.downloadTranslate(source, target) { ok, message ->
            post {
                if (!isUp() || mode != Mode.TRANSLATE || generation != translateGeneration) return@post
                if (!ok) {
                    showResult(message.ifBlank { context.getString(R.string.translate_failed) }, false)
                    setTranslateAction(context.getString(R.string.translate_download), true) { downloadThenTranslate() }
                    return@post
                }
                runTranslation(connection)
            }
        }
    }

    private fun insertTranslation() {
        val text = translationResult
        val connection = translationConnection
        if (text.isEmpty() || connection == null || toolConnection() !== connection) {
            setTranslateAction(context.getString(R.string.translate_no_field), true) { readFieldAndTranslate() }
            return
        }
        if (!replacingSelection && replaceBefore > 0) {
            connection.deleteSurroundingText(replaceBefore, 0)
        }
        connection.commitText(text, 1)
        val manager = dev.bbkb.ime.keyboard.KeyboardSwitcher.getInstance().unifiedInputBoardManager
        if (manager != null) manager.closeBoard(ToolBoardController.KEY_TRANSLATE) else hideBoard()
    }

    private fun toggleInkLanguage() {
        inkLanguage = if (inkLanguage == "ru") "en" else "ru"
        if (mode == Mode.INK) show(Mode.INK)
        if (ink.hasInk()) scheduleRecognize()
    }

    private fun downloadHandwriting() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            showInkMessage(blocked, offerDownload = false)
            return
        }
        val language = inkLanguage
        val generation = ++inkGeneration
        showInkMessage(context.getString(R.string.handwriting_download), offerDownload = false)
        MlKitModels.downloadInk(language) { ok, _ ->
            post {
                if (!isUp() || mode != Mode.INK || generation != inkGeneration) return@post
                if (ok && ink.hasInk()) scheduleRecognize() else showInkHint()
            }
        }
    }

    private fun undoInk() {
        if (ink.hasInk()) {
            ink.undoStroke()
            if (ink.hasInk()) scheduleRecognize() else showInkHint()
        } else {
            toolConnection()?.deleteSurroundingText(1, 0)
        }
    }

    private fun resetInk() {
        cancelRecognize()
        ink.clear()
        showInkHint()
    }

    private fun insertInkSpace() {
        val best = inkOptions.firstOrNull()
        when {
            best != null -> commitInk(best + " ")
            ink.hasInk() -> recognizeNow(after = { word -> if (word != null) commitInk(word + " ") })
            else -> commitInk(" ")
        }
    }

    private fun scheduleRecognize() {
        removeCallbacks(recognizeLater)
        postDelayed(recognizeLater, 450)
    }

    private fun cancelRecognize() {
        removeCallbacks(recognizeLater)
        inkGeneration++
    }

    private fun recognizeNow(after: ((String?) -> Unit)? = null) {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            showInkMessage(blocked, offerDownload = false)
            return
        }
        if (!ink.hasInk()) {
            showInkHint()
            return
        }
        val connection = toolConnection()
        if (connection == null) {
            showInkMessage(context.getString(R.string.handwriting_no_field), offerDownload = false)
            return
        }
        val generation = ++inkGeneration
        MlKitModels.recognizeInk(inkLanguage, ink.currentInk()) { guess ->
            post {
                if (!isUp() || mode != Mode.INK || generation != inkGeneration) return@post
                if (!guess.ok || guess.texts.isEmpty()) {
                    inkOptions = emptyList()
                    candidates.removeAllViews()
                    val message = guess.message.ifBlank { context.getString(R.string.handwriting_no_match) }
                    val needsModel = guess.message.contains("download", ignoreCase = true) ||
                        guess.message.contains("model", ignoreCase = true)
                    showInkMessage(message, offerDownload = needsModel)
                    after?.invoke(null)
                    return@post
                }
                inkOptions = guess.texts
                showCandidateButtons(guess.texts, connection)
                after?.invoke(guess.texts.first())
            }
        }
    }

    private fun showCandidateButtons(options: List<String>, connection: InputConnection) {
        if (toolConnection() !== connection) {
            showInkMessage(context.getString(R.string.handwriting_no_field), offerDownload = false)
            return
        }
        candidates.removeAllViews()
            val shown = ArrayList<String>(5)
        for (option in options) {
            val text = option.trim()
            if (text.isEmpty() || text in shown) continue
            shown.add(text)
            if (shown.size == 5) break
        }
        if (shown.isEmpty()) {
            showInkMessage(context.getString(R.string.handwriting_no_match), offerDownload = false)
            return
        }
        inkHint.visibility = GONE
        shown.forEachIndexed { index, text ->
            candidates.addView(candidateButton(text, index == 0) { commitInk(text) })
        }
    }

    private fun showInkHint() {
        inkOptions = emptyList()
        candidates.removeAllViews()
        inkHint.text = gmsBlockMessage() ?: context.getString(R.string.handwriting_hint)
        inkHint.visibility = VISIBLE
    }

    private fun showInkMessage(message: String, offerDownload: Boolean) {
        inkOptions = emptyList()
        candidates.removeAllViews()
        inkHint.text = message
        inkHint.visibility = VISIBLE
        if (offerDownload) {
            candidates.addView(candidateButton(context.getString(R.string.handwriting_download), true) {
                downloadHandwriting()
            })
            inkHint.visibility = GONE
        }
    }

    private fun commitInk(text: String) {
        val connection = toolConnection()
        if (connection == null) {
            showInkMessage(context.getString(R.string.handwriting_no_field), offerDownload = false)
            return
        }
        connection.commitText(text, 1)
        cancelRecognize()
        ink.clear()
        showInkHint()
    }

    private fun commitToEditor(text: String, connection: InputConnection?) {
        if (connection == null || toolConnection() !== connection) {
            status.text = "Нет поля ввода"
            return
        }
        connection.commitText(text, 1)
        status.text = "Вставлено"
    }

    private fun toolConnection(): InputConnection? {
        val ime = host ?: return null
        val editor = ime.currentInputEditorInfo ?: return null
        if (InputTypeUtils.isAnyPasswordInputType(editor.inputType)) return null
        return ime.currentInputConnection
    }

    private fun gmsBlockMessage(): String? {
        val gms = MlKitModels.gmsStatus(context)
        return if (gms.gmsAvailable) null else gms.message
    }

    private fun candidateScroller(): View {
        detach(candidates)
        return HorizontalScrollView(context).apply {
            setBackgroundColor(KeyboardColorManager.backgroundColor)
            isHorizontalScrollBarEnabled = false
            addView(
                candidates,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun row(vararg actions: Pair<String, () -> Unit>) {
        actions.toList().chunked(4).forEach { chunk ->
            val line = LinearLayout(context).apply {
                orientation = HORIZONTAL
                setBackgroundColor(KeyboardColorManager.backgroundColor)
            }
            chunk.forEach { (label, action) ->
                line.addView(button(label, action), LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    marginEnd = dp(6)
                })
            }
            addView(line, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(6)
            })
        }
    }

    private fun inkButton(label: String, primary: Boolean, action: () -> Unit): Button {
        val button = button(label, action)
        button.textSize = 14f
        button.minHeight = dp(40)
        button.minimumHeight = dp(40)
        if (primary) {
            button.setTextColor(KeyboardColorManager.backgroundColor)
            button.setBackgroundColor(KeyboardColorManager.textColor)
        }
        return button
    }

    private fun candidateButton(label: String, primary: Boolean, action: () -> Unit): Button {
        val button = button(label, action)
        button.textSize = 18f
        button.setPadding(dp(14), dp(6), dp(14), dp(6))
        if (primary) {
            button.setTextColor(KeyboardColorManager.backgroundColor)
            button.setBackgroundColor(KeyboardColorManager.textColor)
        }
        return button
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(KeyboardColorManager.textColor)
            setBackgroundColor(KeyboardColorManager.keyColor)
            isAllCaps = false
            transformationMethod = null
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
            textSize = 13f
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(36)
            minimumHeight = dp(36)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            includeFontPadding = false
            val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            params.marginEnd = dp(6)
            layoutParams = params
            setOnClickListener { action() }
        }
    }

    private fun prepareLine(view: TextView, lines: Int) {
        view.setTextColor(KeyboardColorManager.textColor)
        view.setBackgroundColor(KeyboardColorManager.backgroundColor)
        view.textSize = 13f
        view.maxLines = lines
        view.ellipsize = TextUtils.TruncateAt.END
        view.includeFontPadding = false
    }

    private fun paintChrome() {
        val background = KeyboardColorManager.backgroundColor
        setBackgroundColor(background)
        candidates.setBackgroundColor(background)
        ink.setColors(KeyboardColorManager.keyColor, KeyboardColorManager.textColor)
        prepareLine(status, 4)
        prepareLine(sourceLine, 2)
        prepareLine(resultLine, 2)
        prepareLine(inkHint, 2)
    }

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        val translations = TranslationMemory()
    }

}

/** Arrow rows share whatever height the board can give, and never shrink below a finger target. */
private class EvenRows(context: Context) : LinearLayout(context) {
    init {
        orientation = VERTICAL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val count = childCount.coerceAtLeast(1)
        val min = (36f * resources.displayMetrics.density).toInt() * count
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val size = MeasureSpec.getSize(heightMeasureSpec)
        val height = if (mode == MeasureSpec.EXACTLY && size > 0) size else min
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }
}
