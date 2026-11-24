package dev.bbkb.ime.keyboard.inputboard.tools

import android.content.Context
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.TextUtils
import android.util.AttributeSet
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
import dev.bbkb.ime.keyboard.inputboard.clipboard.TextEditingBar
import dev.bbkb.ime.mlkit.HandwritingView
import dev.bbkb.ime.mlkit.MlKitModels

/** In-keyboard panel for text editing, translation, and handwriting. Background stays #000000. */
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
    private var source = MlKitModels.english()
    private var target = MlKitModels.russian()
    private var inkLanguage = "ru"
    private var translationResult = ""
    private var translationConnection: InputConnection? = null
    private var translateGeneration = 0
    private var inkGeneration = 0
    private var host: InputMethodService? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.BLACK)
        setPadding(dp(8), dp(6), dp(8), dp(6))
        prepareLine(status, 4)
        prepareLine(sourceLine, 2)
        prepareLine(resultLine, 2)
        candidates.orientation = HORIZONTAL
        candidates.setBackgroundColor(Color.BLACK)
        ink.minimumHeight = dp(88)
    }

    fun show(next: Mode, ime: InputMethodService) {
        host = ime
        show(next)
    }

    private fun show(next: Mode) {
        mode = next
        detach(status)
        detach(sourceLine)
        detach(resultLine)
        detach(ink)
        detach(candidates)
        removeAllViews()
        setBackgroundColor(Color.BLACK)
        addView(status)
        when (next) {
            Mode.EDIT -> showEdit()
            Mode.TRANSLATE -> showTranslate()
            Mode.INK -> showInk()
        }
        visibility = VISIBLE
    }

    fun hideBoard() {
        visibility = GONE
        host = null
        dropTranslation()
        inkGeneration++
        sourceLine.text = ""
        status.text = ""
        candidates.removeAllViews()
        ink.clear()
    }

    fun isUp(): Boolean = visibility == VISIBLE

    private fun showEdit() {
        status.setText(R.string.settings_uim_toggle_text_edit)
        row(
            context.getString(R.string.text_edit_select_all) to { TextEditingBar.perform(toolConnection(), "all") },
            context.getString(R.string.text_edit_cut) to { TextEditingBar.perform(toolConnection(), "cut") },
            context.getString(R.string.text_edit_copy) to { TextEditingBar.perform(toolConnection(), "copy") },
            context.getString(R.string.text_edit_paste) to { TextEditingBar.perform(toolConnection(), "paste") },
            context.getString(R.string.text_edit_word_back) to { TextEditingBar.perform(toolConnection(), "word") },
            context.getString(R.string.text_edit_left) to { TextEditingBar.perform(toolConnection(), "left") },
            context.getString(R.string.text_edit_right) to { TextEditingBar.perform(toolConnection(), "right") },
        )
    }

    private fun showTranslate() {
        val blocked = gmsBlockMessage()
        status.text = blocked ?: pairLabel()
        if (sourceLine.text.isNullOrBlank()) sourceLine.text = "Текст: —"
        resultLine.text = if (translationResult.isEmpty()) "Перевод: —" else "Перевод: $translationResult"
        addView(sourceLine)
        addView(resultLine)
        val english = MlKitModels.english()
        val russian = MlKitModels.russian()
        row(
            languageChip("Ист.", english, source) to { chooseSource(english) },
            languageChip("Ист.", russian, source) to { chooseSource(russian) },
            "Поменять" to { swapLanguages() },
            languageChip("Цель", english, target) to { chooseTarget(english) },
            languageChip("Цель", russian, target) to { chooseTarget(russian) },
        )
        row(
            "Скачать" to { downloadTranslation() },
            "Перевести" to { translateSelection() },
            "Вставить" to { insertTranslation() },
        )
    }

    private fun showInk() {
        status.text = gmsBlockMessage() ?: "Рукописный ввод: $inkLanguage"
        addView(ink, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        addView(candidateScroller())
        row(
            inkChip("ru") to { chooseInkLanguage("ru") },
            inkChip("en") to { chooseInkLanguage("en") },
            "Скачать" to { downloadHandwriting() },
            "Отмена штриха" to {
                ink.undoStroke()
                clearCandidates()
            },
            "Очистить" to {
                ink.clear()
                clearCandidates()
            },
            "Распознать" to { recognize() },
        )
    }

    private fun chooseSource(lang: String) {
        if (source == lang) return
        source = lang
        dropTranslation()
        if (mode == Mode.TRANSLATE) show(Mode.TRANSLATE)
    }

    private fun chooseTarget(lang: String) {
        if (target == lang) return
        target = lang
        dropTranslation()
        if (mode == Mode.TRANSLATE) show(Mode.TRANSLATE)
    }

    private fun swapLanguages() {
        val previous = source
        source = target
        target = previous
        dropTranslation()
        if (mode == Mode.TRANSLATE) show(Mode.TRANSLATE)
    }

    private fun dropTranslation() {
        translateGeneration++
        translationResult = ""
        translationConnection = null
        resultLine.text = "Перевод: —"
    }

    private fun downloadTranslation() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            status.text = blocked
            return
        }
        val from = source
        val to = target
        val generation = ++translateGeneration
        status.text = "Скачивание $from → $to"
        MlKitModels.downloadTranslate(from, to) { _, msg ->
            post {
                if (isUp() && mode == Mode.TRANSLATE && generation == translateGeneration) status.text = msg
            }
        }
    }

    private fun translateSelection() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            status.text = blocked
            return
        }
        if (source == target) {
            status.text = "Выберите разные языки"
            return
        }
        val connection = toolConnection()
        if (connection == null) {
            status.text = "Нет поля ввода"
            return
        }
        val selected = connection.getSelectedText(0)?.toString()?.takeIf { it.isNotBlank() }
            ?: connection.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        val text = selected.trim()
        if (text.isEmpty()) {
            dropTranslation()
            sourceLine.text = "Текст: —"
            resultLine.text = "Перевод: —"
            status.text = "Нет текста"
            return
        }
        sourceLine.text = "Текст: $text"
        resultLine.text = "Перевод: …"
        status.text = "Перевод…"
        translationResult = ""
        translationConnection = null
        val from = source
        val to = target
        val generation = ++translateGeneration
        MlKitModels.translate(from, to, text) { ok, translated ->
            post {
                if (!isUp() || mode != Mode.TRANSLATE || generation != translateGeneration ||
                    toolConnection() !== connection) return@post
                if (!ok || translated.isEmpty()) {
                    translationResult = ""
                    resultLine.text = "Перевод: —"
                    status.text = if (!ok) translated.ifBlank { "Ошибка перевода" } else "Пустой результат"
                    return@post
                }
                translationResult = translated
                translationConnection = connection
                resultLine.text = "Перевод: $translated"
                status.text = "$from → $to"
            }
        }
    }

    private fun insertTranslation() {
        val text = translationResult
        if (text.isEmpty()) {
            status.text = "Нет результата"
            return
        }
        commitToEditor(text, translationConnection)
    }

    private fun chooseInkLanguage(tag: String) {
        if (inkLanguage == tag) return
        inkLanguage = tag
        clearCandidates()
        if (mode == Mode.INK) show(Mode.INK)
    }

    private fun downloadHandwriting() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            status.text = blocked
            return
        }
        val language = inkLanguage
        val generation = ++inkGeneration
        status.text = "Скачивание $language"
        MlKitModels.downloadInk(language) { _, msg ->
            post {
                if (isUp() && mode == Mode.INK && generation == inkGeneration) status.text = msg
            }
        }
    }

    private fun recognize() {
        val blocked = gmsBlockMessage()
        if (blocked != null) {
            status.text = blocked
            return
        }
        val connection = toolConnection()
        if (connection == null) {
            status.text = "Нет поля ввода"
            return
        }
        status.text = "Распознавание…"
        val generation = ++inkGeneration
        candidates.removeAllViews()
        MlKitModels.recognizeInk(inkLanguage, ink.currentInk()) { ok, text ->
            post {
                if (!isUp() || mode != Mode.INK || generation != inkGeneration ||
                    toolConnection() !== connection) return@post
                if (!ok) {
                    candidates.removeAllViews()
                    status.text = text.ifBlank { "Нет варианта" }
                    return@post
                }
                // recognizeInk returns one string. The row still caps at three buttons.
                showCandidateButtons(listOf(text), connection)
            }
        }
    }

    private fun showCandidateButtons(options: List<String>, connection: InputConnection) {
        candidates.removeAllViews()
        val shown = ArrayList<String>(3)
        for (option in options) {
            val text = option.trim()
            if (text.isEmpty() || text in shown) continue
            shown.add(text)
            if (shown.size == 3) break
        }
        if (shown.isEmpty()) {
            status.text = "Нет варианта"
            return
        }
        for (text in shown) {
            candidates.addView(button(text) { commitToEditor(text, connection) })
        }
        status.text = if (shown.size == 1) shown[0] else shown.joinToString(" · ")
    }

    private fun clearCandidates() {
        inkGeneration++
        candidates.removeAllViews()
        if (mode == Mode.INK) {
            status.text = gmsBlockMessage() ?: "Рукописный ввод: $inkLanguage"
        }
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

    private fun pairLabel(): String = "$source → $target"

    private fun languageChip(role: String, lang: String, selected: String): String {
        val label = "$role ${shortCode(lang)}"
        return if (selected == lang) "[$label]" else label
    }

    private fun inkChip(tag: String): String {
        val label = tag.uppercase()
        return if (inkLanguage == tag) "[$label]" else label
    }

    private fun shortCode(lang: String): String = when (lang) {
        MlKitModels.english() -> "EN"
        MlKitModels.russian() -> "RU"
        else -> lang.uppercase()
    }

    private fun candidateScroller(): View {
        detach(candidates)
        return HorizontalScrollView(context).apply {
            setBackgroundColor(Color.BLACK)
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
        val line = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }
        actions.forEach { (label, action) -> line.addView(button(label, action)) }
        val scroller = HorizontalScrollView(context).apply {
            setBackgroundColor(Color.BLACK)
            isHorizontalScrollBarEnabled = false
            addView(
                line,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
        addView(scroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun button(label: String, action: () -> Unit): Button {
        return Button(context).apply {
            text = label
            setTextColor(Color.WHITE)
            setBackgroundColor(0xFF222222.toInt())
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
        view.setTextColor(Color.WHITE)
        view.setBackgroundColor(Color.BLACK)
        view.textSize = 13f
        view.maxLines = lines
        view.ellipsize = TextUtils.TruncateAt.END
        view.includeFontPadding = false
    }

    private fun detach(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

}
