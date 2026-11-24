package dev.bbkb.ime.harness

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Drives the installed IME with real touch events over the autocorrect corpus and records what the
 * keyboard actually offered and committed.
 *
 * Configuration is read from /sdcard/Download/bbkb-bench.properties so the same driver runs against
 * upstream, the 4PDA mod and this fork:
 *   corpus=/sdcard/Download/bbkb-corpus.csv
 *   output=/sdcard/Download/bbkb-bench-en.json
 *   language=en|ru        rows to run; the driver switches the layout itself
 *   limit=0               (0 = every row)
 *   probeOnly=true        calibrate, verify the key map and stop
 *
 * Geometry comes from the shipped qwerty_vkb KDB (1080x600 units, same layout in every BBKB build);
 * the vertical offset is measured at runtime by scanning one column, so number rows or a different
 * keyboard height need no reconfiguration. Nothing is hard-coded to this AVD's pixel size.
 */
@RunWith(AndroidJUnit4::class)
class AutocorrectBenchmarkTest {
    private lateinit var instrumentation: Instrumentation
    private lateinit var scenarioRef: ActivityScenario<HarnessActivity>
    private lateinit var editor: EditText
    private var rowTapToCharacterMs = -1L
    private var rowSuggestionLatencyMs = -1L
    private var rowSuggestionsChanged = false

    private companion object {
        // The harness reads and writes its own external files dir: /sdcard/Download needs scoped
        // storage access this debug app does not request.
        const val BASE = "/sdcard/Android/data/dev.bbkb.ime.harness/files"
        const val UNITS_WIDE = 1080.0
        // qwerty_vkb.xml: row tops 9, 158, 307 and 456 with 140-unit-tall keys.
        val ROW_LABELS = listOf(
            listOf('q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'),
            listOf('a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l'),
            listOf('z', 'x', 'c', 'v', 'b', 'n', 'm'),
        )
        val ROW_LEFT = listOf(0.0, 0.0, 162.0)
        val ROW_WIDTH = listOf(108.0, 120.0, 108.0)
        const val SPACE_CENTRE_X = 540.0
        const val BACKSPACE_CENTRE_X = 999.0
        const val SHIFT_CENTRE_X = 81.0
        const val ROW_PITCH = 149.0   // 158 - 9 units between row centres
        const val KEY_HALF = 70.0
        // Shift is a stateful modifier on every BBKB build, but how long the raised state survives
        // before it clears itself differs: the 4PDA mod drops it within a few hundred milliseconds,
        // so a letter sent later arrives lowercase. Calibration determines which cadence the
        // installed build accepts, and typing uses that one.
        const val SHIFT_SETTLE_MS = 380L
        val SHIFT_DELAY_CANDIDATES = listOf(100L, SHIFT_SETTLE_MS, 700L)
        /** Finger-on-key time for one injected tap; a real thumb never taps for 0 ms. */
        const val DEFAULT_HOLD_MS = 45L

        val RU_ROWS = listOf(
            "йцукенгшщз".toList(),
            "фывапролд".toList(),
            "ячсмить".toList(),
        )

        // Words for the stress run: common, unambiguous, and all reachable with one tap per letter.
        val EN_WORDS = listOf(
            "the", "meeting", "tomorrow", "please", "review", "budget", "project", "update",
            "schedule", "invoice", "customer", "delivery", "quality", "report", "morning",
            "answer", "question", "because", "between", "through", "already", "another",
            "keyboard", "message", "address", "weather", "holiday", "station", "picture",
        )
        val RU_WORDS = listOf(
            "привет", "сегодня", "работа", "встреча", "завтра", "спасибо", "пожалуйста",
            "сообщение", "вопрос", "ответ", "проект", "отчёт", "проверка", "доставка",
            "качество", "клиент", "деньги", "телефон", "адрес", "погода", "отпуск",
            "станция", "картина", "вечером", "утром", "быстро", "медленно", "хорошо",
        )
    }

    private class Layout(
        val letters: Map<Char, Pair<Int, Int>>,
        val space: Pair<Int, Int>,
        val backspace: Pair<Int, Int>,
        val shift: Pair<Int, Int>,
        val stripBand: IntRange,
        val scale: Double,
        val shiftDelayMs: Long,
        /** False when the build would not accept a synthetic tap on its shift key. */
        val shiftEnabled: Boolean,
    )

    private val imeWindow: AccessibilityWindowInfo?
        get() = instrumentation.uiAutomation.windows
            .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }

    private fun collect(root: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (root == null) return
        out.add(root)
        for (index in 0 until root.childCount) collect(root.getChild(index), out)
    }

    private fun imeNodes(): List<AccessibilityNodeInfo> {
        val collected = mutableListOf<AccessibilityNodeInfo>()
        collect(imeWindow?.root, collected)
        return collected
    }

    private fun editorText(): String {
        var text = ""
        scenarioRef.onActivity { text = editor.text.toString() }
        return text
    }

    private fun resetEditor() {
        scenarioRef.onActivity {
            editor.text.clear()
            it.getSystemService(InputMethodManager::class.java).restartInput(editor)
        }
    }

    /**
     * Clears the field without cycling the input session. `restartInput` can hide a heavy IME for a
     * few hundred milliseconds, and a tap that lands in that gap commits nothing — exactly the
     * failure a geometry probe must not see.
     */
    private fun clearEditor() {
        scenarioRef.onActivity { editor.text.clear() }
    }

    /** Focuses the field with a real tap, which is the only way some builds bring the keyboard up. */
    private fun focusEditorByTap() {
        var point = 0 to 0
        scenarioRef.onActivity {
            val location = IntArray(2)
            editor.getLocationOnScreen(location)
            point = (location[0] + editor.width / 2) to (location[1] + editor.height / 2)
        }
        tap(point.first, point.second)
        SystemClock.sleep(400)
    }

    /**
     * Injects one tap. The finger stays down for [holdMs]: a zero-length press is not what a thumb
     * produces, and some builds (the 4PDA mod's shift key among them) only accept a modifier when
     * the press has a real duration, so an instantaneous DOWN/UP silently does nothing.
     */
    private fun tap(x: Int, y: Int, holdMs: Long = DEFAULT_HOLD_MS) {
        val downTime = SystemClock.uptimeMillis()
        for (action in intArrayOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            if (action == MotionEvent.ACTION_UP) SystemClock.sleep(holdMs)
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                assertTrue("Touch injection failed at $x,$y",
                    instrumentation.uiAutomation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
    }

    private fun swipe(fromX: Int, toX: Int, y: Int, steps: Int = 6, stepMs: Long = 18) {
        val downTime = SystemClock.uptimeMillis()
        for (index in 0..steps) {
            val action = when (index) {
                0 -> MotionEvent.ACTION_DOWN
                steps -> MotionEvent.ACTION_UP
                else -> MotionEvent.ACTION_MOVE
            }
            val x = fromX + (toX - fromX) * index / steps
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                x.toFloat(), y.toFloat(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try {
                instrumentation.uiAutomation.injectInputEvent(event, true)
            } finally {
                event.recycle()
            }
            SystemClock.sleep(stepMs)
        }
    }

    private fun imeRoot(): AccessibilityNodeInfo =
        imeWindow?.root ?: error("the IME window is not visible")

    private fun findById(node: AccessibilityNodeInfo?, idSuffix: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.viewIdResourceName?.endsWith(idSuffix) == true) return node
        for (index in 0 until node.childCount) {
            findById(node.getChild(index), idSuffix)?.let { return it }
        }
        return null
    }

    private fun nodeTexts(root: AccessibilityNodeInfo?): List<Triple<String, Int, Int>> {
        if (root == null) return emptyList()
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(root, nodes)
        return nodes.mapNotNull { node ->
            val text = node.text?.toString()?.trim()
            if (text.isNullOrEmpty()) return@mapNotNull null
            val rect = Rect()
            node.getBoundsInScreen(rect)
            Triple(text, rect.top, rect.left)
        }
    }

    /**
     * What the keyboard currently offers, from both surfaces it can use:
     *
     *  - the aux bar (`aux_bar_view`): the ordered suggestion strip on physical keyboards and in
     *    builds that keep predictions out of the key area;
     *  - `flick_suggestion_view`: this fork shows predictions above the letter keys instead of the
     *    strip on the on-screen keyboard, and the words are exposed as text nodes there.
     *
     * Everything is located by view id, which upstream, the 4PDA mod and this fork share, so no
     * pixel band has to be guessed. Only text nodes are read, so tool icons cannot leak in.
     */
    private fun suggestions(): List<String> {
        val root = imeRoot()
        val strip = nodeTexts(findById(root, ":id/aux_bar_view"))
            .sortedBy { it.third }.map { it.first }.distinct()
        val flick = nodeTexts(findById(root, ":id/flick_suggestion_view"))
            .sortedWith(compareBy({ it.second }, { it.third })).map { it.first }.distinct()
        return strip + flick.filterNot { it in strip }
    }

    /** Words in the strip only, in the order the keyboard draws them. */
    private fun stripWords(): List<String> =
        nodeTexts(findById(imeRoot(), ":id/aux_bar_view"))
            .sortedBy { it.third }.map { it.first }.distinct()

    /** Waits until the keyboard offers something or the budget runs out; returns the latency. */
    private fun awaitSuggestions(budgetMs: Long = 600): Pair<List<String>, Long> {
        val start = System.nanoTime()
        var offered = suggestions()
        while (offered.isEmpty() && (System.nanoTime() - start) / 1_000_000 < budgetMs) {
            SystemClock.sleep(10)
            offered = suggestions()
        }
        return offered to (System.nanoTime() - start) / 1_000_000
    }

    /**
     * Waits until the offered words differ from [before] and returns them with the latency. Reading
     * the first non-empty list would return whatever the previous keystroke produced, which is why
     * every row measures the change after its own last tap.
     */
    private fun awaitChange(before: List<String>, budgetMs: Long = 300): Pair<List<String>, Long> {
        val start = System.nanoTime()
        var offered = suggestions()
        var polls = 0
        while (offered == before && (System.nanoTime() - start) / 1_000_000 < budgetMs && polls < 6) {
            SystemClock.sleep(10)
            offered = suggestions()
            polls++
        }
        return offered to (System.nanoTime() - start) / 1_000_000
    }

    private fun probe(x: Int, y: Int): String {
        clearEditor()
        SystemClock.sleep(40)
        tap(x, y)
        SystemClock.sleep(90)
        return editorText()
    }

    private fun labelsFor(language: String): List<List<Char>> =
        if (language == "ru") RU_ROWS else ROW_LABELS

    /** True for the Cyrillic block BBKB uses, including ё which sits outside а..я. */
    private fun Char.isCyrillic(): Boolean = this in 'А'..'я' || this == 'ё' || this == 'Ё'

    /**
     * Locates the key grid and returns it labelled for [language].
     *
     * The vertical pass taps one column from the bottom up and records which character each tap
     * committed. The three lowest letter rows are the letter rows whatever language the keyboard is
     * in, so the grid is found even when the previous run left the keyboard on Cyrillic. The script
     * of those characters also says which layout is on screen: if it is not the requested one, the
     * space bar is swiped to switch, and the grid is then re-measured, because the Cyrillic rows
     * hold more keys than the Latin ones and every position has to come from the keyboard itself.
     */
    private fun calibrate(language: String = config()["language"] ?: "en"): Layout {
        val root = imeRoot()
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        val scale = bounds.width() / UNITS_WIDE
        val columnX = (bounds.left + SPACE_CENTRE_X * scale).toInt()
        val step = (18 * scale).toInt().coerceAtLeast(6)

        // Character at the centre column -> [first y, last y] that committed it, bottom-up.
        val hits = LinkedHashMap<Char, IntArray>()
        var y = bounds.bottom - (40 * scale).toInt()
        while (y > bounds.top + (200 * scale).toInt() && hits.count { it.key.isLetter() } < 3) {
            val text = probe(columnX, y)
            if (text.length == 1) {
                val span = hits.getOrPut(text[0]) { intArrayOf(y, y) }
                span[0] = minOf(span[0], y)
                span[1] = maxOf(span[1], y)
            }
            y -= step
        }
        resetEditor()
        val letterRows = hits.keys.filter { it.isLetter() }.take(3)
        check(letterRows.size == 3) { "could not locate the letter rows (found ${hits.keys})" }
        fun centreOf(char: Char): Int = hits.getValue(char).let { (it[0] + it[1]) / 2 }

        val onScreen = if (letterRows.any { it.isCyrillic() }) "ru" else "en"
        val requested = language
        val labels = labelsFor(requested)
        fun formulaX(row: Int, column: Int): Int =
            (bounds.left + (ROW_LEFT[row] + ROW_WIDTH[row] * column + ROW_WIDTH[row] / 2) * scale).toInt()

        // Bottom-up order: z-row, a-row, q-row.
        val rowY = intArrayOf(centreOf(letterRows[2]), centreOf(letterRows[1]), centreOf(letterRows[0]))
        val space = (bounds.left + SPACE_CENTRE_X * scale).toInt() to
            (hits[' ']?.let { (it[0] + it[1]) / 2 } ?: (centreOf(letterRows[0]) + (ROW_PITCH * scale).toInt()))

        // The key grid is read off the keyboard rather than derived from a label table: the Cyrillic
        // rows do not hold the same number of keys as the Latin ones (the fork's RU bottom row has
        // nine letters to Latin's seven), so a position taken from the Latin grid commits a
        // neighbouring Cyrillic letter. Each row is walked outwards from its centre and the
        // character each tap commits is recorded, which also reproduces the mod's own insets.
        val scanStep = (24 * scale).toInt().coerceAtLeast(12)
        var rowExtent = mapOf<Int, IntRange>()
        fun scanRows(): Map<Char, Pair<Int, Int>> {
            val measured = mutableMapOf<Char, Pair<Int, Int>>()
            val extents = mutableMapOf<Int, IntRange>()
            val centreX = (bounds.left + SPACE_CENTRE_X * scale).toInt()
            for (row in 0..2) {
                val perChar = mutableMapOf<Char, MutableList<Int>>()
                fun sample(x: Int): Boolean {
                    val char = probe(x, rowY[row]).singleOrNull()?.lowercaseChar()
                    if (char == null || !char.isLetter()) return false
                    perChar.getOrPut(char) { mutableListOf() }.add(x)
                    return true
                }
                sample(centreX)
                // Two taps in a row that commit nothing end the letter band: the neighbouring key is
                // a function key (shift on the left, backspace on the right), and the symbol-page key
                // sits beyond it, so the walk never switches the layout out from under the scan.
                var misses = 0
                var x = centreX - scanStep
                while (x > bounds.left && misses < 2) {
                    if (sample(x)) misses = 0 else misses++
                    x -= scanStep
                }
                misses = 0
                x = centreX + scanStep
                while (x < bounds.right && misses < 2) {
                    if (sample(x)) misses = 0 else misses++
                    x += scanStep
                }
                for ((char, xs) in perChar) {
                    measured[char] = xs.sorted()[xs.size / 2] to rowY[row]
                }
                if (perChar.isNotEmpty()) {
                    val xs = perChar.values.flatten()
                    extents[row] = xs.min()..xs.max()
                }
            }
            resetEditor()
            rowExtent = extents
            return measured
        }

        var measured = scanRows()
        // The grid is measured in whatever language the keyboard is showing; switch before the
        // measurement is used for the corpus. The top-left letter is a valid tap in both layouts.
        if (onScreen != requested) {
            val probeAt = measured.entries.filter { it.value.second == rowY[0] }.minByOrNull { it.value.first }?.value
                ?: (formulaX(0, 0) to rowY[0])
            selectLanguage(requested, probeAt, space)
            measured = scanRows()
        }

        val unmeasured = mutableListOf<Char>()
        // Everything the scan observed is typed by position; the label table only supplies a formula
        // fallback for a corpus character the scan could not reach, so the mapping never depends on
        // a hand-written row list matching the build.
        val letters = buildMap {
            putAll(measured)
            for ((row, chars) in labels.withIndex()) {
                for ((column, char) in chars.withIndex()) {
                    if (containsKey(char)) continue
                    unmeasured.add(char)
                    put(char, formulaX(row, column) to rowY[row])
                }
            }
        }
        println("BBKB-GRID " + rowY.indices.joinToString(" | ") { row ->
            "r$row:" + measured.filter { it.value.second == rowY[row] }.entries
                .sortedBy { it.value.first }.joinToString("") { "${it.key}@${it.value.first}" }
        })
        // The key pitch and the function-key margins come from the measured bottom row, so they stay
        // in step with whichever script is on screen. The margins use the *observed* extent of the
        // row, not the label table's: the Cyrillic row is longer, and stepping out from the seventh
        // Latin-width key lands on a letter instead of the backspace.
        val bottomXs = labels[2].mapNotNull { letters[it]?.first }.sorted()
        val pitch = if (bottomXs.size > 1) (bottomXs.last() - bottomXs.first()) / (bottomXs.size - 1)
            else (ROW_WIDTH[2] * scale).toInt()
        val bottomSpan = rowExtent[2] ?: (bottomXs.firstOrNull()?.let { it..it } ?: IntRange(0, bounds.width()))
        val shiftCandidates = (1..5).map { n -> (bottomSpan.first - n * pitch / 4) to rowY[2] }
        val backspaceCandidates = (1..5).map { n -> (bottomSpan.last + n * pitch / 4) to rowY[2] }
        // The candidate strip is the band directly above the key grid; key legends are drawn, not
        // text nodes, so every text node there belongs to the strip.
        val stripBottom = rowY[0] - (KEY_HALF * scale).toInt() - 2
        val stripBand = (stripBottom - (250 * scale).toInt()).coerceAtLeast(bounds.top)..stripBottom
        var layout = Layout(letters, space, backspaceCandidates.first(), shiftCandidates.first(), stripBand, scale,
            SHIFT_SETTLE_MS, shiftEnabled = true)

        // Cross-check: the first, middle and last letter keys must commit what the scan predicted.
        val samples = listOf(labels[0].first(), labels[0].last(), labels[1].first(), labels[2].first(), labels[2].last())
        for (char in samples) {
            val (x, y1) = layout.letters.getValue(char)
            val committed = probe(x, y1)
            check(committed.equals(char.toString(), ignoreCase = true)) {
                "calibration mismatch: key for '$char' committed '${committed.take(4)}'" +
                    (if (unmeasured.isEmpty()) "" else " (formula fallback used for $unmeasured)")
            }
        }
        // Shift must raise the next letter and then fall back by itself. The key has no readable
        // legend, so the only proof is the effect: tap it, tap 'q', look at the case. How long the
        // build holds the raised state differs (the 4PDA mod clears it within a few hundred ms), so
        // the cadence is part of what calibration measures, and typing then uses it.
        val firstLetter = layout.letters.getValue(labels[0].first())
        val shiftAttempts = mutableListOf<String>()
        fun shiftWorks(at: Pair<Int, Int>, delayMs: Long): Boolean {
            clearEditor()
            SystemClock.sleep(80)
            tap(at.first, at.second)
            SystemClock.sleep(delayMs)
            tap(firstLetter.first, firstLetter.second)
            SystemClock.sleep(180)
            val shifted = editorText()
            shiftAttempts += "${at.first}x${at.second}@${delayMs}:${shifted.take(4)}"
            return shifted.isNotEmpty() && shifted[0].isUpperCase() &&
                shifted[0].equals(labels[0].first(), ignoreCase = true)
        }
        var shiftPoint: Pair<Int, Int>? = null
        var shiftDelayMs = SHIFT_SETTLE_MS
        search@ for (delayMs in SHIFT_DELAY_CANDIDATES) {
            for (candidate in shiftCandidates) {
                if (shiftWorks(candidate, delayMs)) {
                    shiftPoint = candidate
                    shiftDelayMs = delayMs
                    break@search
                }
            }
        }
        if (shiftPoint == null) {
            // A build may place shift outside the row-3 margin the constants predict (the 4PDA mod
            // insets its function keys). Sweep the bottom-left corner before giving up.
            val cornerXs = generateSequence(bounds.left + (24 * scale).toInt()) { it + (pitch / 2).toInt() }
                .takeWhile { it < letters.getValue('z').first }
                .toList()
            val cornerYs = (rowY[2] - (80 * scale).toInt()..rowY[2] + (80 * scale).toInt()
                step (40 * scale).toInt().coerceAtLeast(20)).toList()
            outer@ for (cy in cornerYs) {
                for (cx in cornerXs) {
                    if (shiftWorks(cx to cy, SHIFT_DELAY_CANDIDATES.first())) {
                        shiftPoint = cx to cy
                        break@outer
                    }
                }
            }
        }
        // A build can also refuse synthetic touches on its modifier keys: the 4PDA mod accepts a
        // shift tap from the system input queue but ignores the same tap injected through the
        // accessibility bridge, while every letter key accepts both. Rather than fail the run, the
        // corpus is typed lower-case on such a build and the results carry that fact; the manual
        // control (system injection) is recorded in the report so the limitation is not mistaken
        // for a keyboard defect.
        val shiftEnabled = shiftPoint != null
        val effectiveShift = shiftPoint ?: shiftCandidates.first()
        layout = Layout(letters, space, layout.backspace, effectiveShift, stripBand, scale, shiftDelayMs, shiftEnabled)
        // Backspace must delete: the key has no legend to read, so prove it by effect.
        var backspacePoint: Pair<Int, Int>? = null
        val backspaceAttempts = mutableListOf<String>()
        for (candidate in backspaceCandidates) {
            clearEditor()
            SystemClock.sleep(60)
            val anchor = layout.letters.getValue(labels[1][0])
            tap(anchor.first, anchor.second)
            SystemClock.sleep(100)
            val anchorText = editorText()
            if (anchorText.isEmpty()) {
                backspaceAttempts.add("${candidate.first}x${candidate.second}: anchor '${labels[1][0]}' at ${anchor.first}x${anchor.second} committed nothing")
                continue
            }
            tap(candidate.first, candidate.second)
            SystemClock.sleep(120)
            val afterText = editorText()
            if (afterText.isEmpty()) {
                backspacePoint = candidate
                break
            }
            backspaceAttempts.add("${candidate.first}x${candidate.second}: '$anchorText' -> '$afterText'")
        }
        checkNotNull(backspacePoint) { "backspace key did not delete the probe character (${backspaceAttempts.joinToString("; ")})" }
        resetEditor()
        return Layout(letters, space, backspacePoint, effectiveShift, stripBand, scale, shiftDelayMs, shiftEnabled)
    }

    /**
     * Swipes the space bar until the layout matches [language]; fails if it never does.
     *
     * The first switch to a language whose dictionary has never been opened takes seconds (the pack
     * is imported before the layout answers), so the probe is polled rather than sampled once, and
     * the gesture is repeated in both directions because a swipe that starts too close to the space
     * bar's edge is eaten by the neighbouring key.
     */
    private fun selectLanguage(language: String, firstKey: Pair<Int, Int>, space: Pair<Int, Int>) {
        val probeChar = if (language == "ru") 'й' else 'q'
        val (x, y) = firstKey
        // Case is ignored: the language is what matters, and a build that leaves Shift latched after
        // calibration probes commits 'Q' where the harness asked for 'q'.
        fun matches(text: String) = text.equals(probeChar.toString(), ignoreCase = true)
        if (matches(probe(x, y))) {
            resetEditor()
            return
        }
        resetEditor()
        var last = ""
        // Which gesture a build treats as a language switch is not shared: the fork answers a fast
        // flick across the space bar, a slower drag is what some builds' detectors require. Each
        // variant is tried in both directions, because a swipe that starts too close to the space
        // bar's edge is attributed to the neighbouring key.
        val gestures = listOf(Triple(200, 6, 18L), Triple(160, 10, 26L), Triple(220, 12, 30L))
        var attempt = 0
        while (attempt < gestures.size * 2) {
            val (reach, steps, stepMs) = gestures[attempt / 2]
            if (attempt % 2 == 0) swipe(space.first - reach, space.first + reach, space.second, steps, stepMs)
            else swipe(space.first + reach, space.first - reach, space.second, steps, stepMs)
            val deadline = System.nanoTime() + 4_000_000_000L
            while (System.nanoTime() < deadline) {
                SystemClock.sleep(250)
                last = probe(x, y)
                if (matches(last)) {
                    resetEditor()
                    return
                }
            }
            resetEditor()
            attempt++
        }
        check(matches(last)) {
            "could not switch the keyboard to '$language' by swiping the space bar " +
                "(last probe '$last' at $x,$y over space ${space.first},${space.second})"
        }
    }

    private fun config(): Map<String, String> {
        val arguments = InstrumentationRegistry.getArguments()
        val config = mutableMapOf<String, String>()
        for (key in listOf("language", "limit", "probeOnly", "tag", "field", "gap", "settle", "probeWord", "grid",
                "sx", "sy", "qx", "qy", "delays", "points", "dump", "sample",
                "stress", "keys", "minutes", "clipboard")) {
            arguments.getString(key)?.let { config[key] = it }
        }
        return config
    }

    /**
     * Diagnostic: taps a grid over the IME window and records the single character each point
     * commits. Used to understand a build whose key geometry does not match the shared layout
     * assumptions before trusting any corpus number from it.
     */
    @Test
    fun gridProbe() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("diagnostic only", config()["grid"] == "true")
        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            editor = activity.findViewById<View>(android.R.id.content)
                .findViewWithTag<EditText>("prose")
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        val waitStart = System.nanoTime()
        while (imeWindow?.root == null && (System.nanoTime() - waitStart) / 1_000_000 < 10_000) {
            SystemClock.sleep(250)
        }
        SystemClock.sleep(500)
        focusEditorByTap()

        val bounds = Rect()
        imeRoot().getBoundsInScreen(bounds)
        val output = StringBuilder("bounds=$bounds\n")
        // Column centres stay clear of the space bar's own edges, so a hit is a letter or a
        // function key, not the empty gap between keys.
        val columns = (1..9).map { bounds.left + bounds.width() * it / 10 }
        var y = bounds.bottom - 20
        while (y > bounds.top) {
            val hits = columns.map { x -> probe(x, y) }
            output.append("y=$y\t").append(hits.joinToString("|") { it.take(12) }).append('\n')
            y -= 30
        }
        val path = File(
            instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir,
            "bbkb-grid.json").absolutePath
        File(path).writeText(output.toString())
        scenarioRef.close()
    }

    /**
     * Diagnostic: copies the sample image in the harness, opens the keyboard's clipboard panel and
     * dumps every IME node, so the image row can be identified before an insertion test taps it.
     *
     *   am instrument -e class ...#clipboardProbe -e clipboard true -e tag probe
     */
    @Test
    fun clipboardProbe() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("diagnostic only", config()["clipboard"] == "true")
        val tag = config()["tag"] ?: "run"
        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            val root = activity.findViewById<View>(android.R.id.content)
            val button = findButtonByText(root, "Copy sample image")
            checkNotNull(button) { "the harness has no 'Copy sample image' button" }
            button.performClick()
            editor = root.findViewWithTag<EditText>("paste image here")
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        val waitStart = System.nanoTime()
        while (imeWindow?.root == null && (System.nanoTime() - waitStart) / 1_000_000 < 10_000) {
            SystemClock.sleep(250)
        }
        SystemClock.sleep(800)

        val output = StringBuilder()
        output.append("clipboard: ").append(clipboardSummary()).append('\n')
        output.append("before:\n").append(dumpImeNodes()).append('\n')
        val panel = imeNodes().firstOrNull {
            (it.contentDescription?.toString() ?: "").equals("Clipboard", ignoreCase = true) && it.isClickable
        }
        output.append("clipboard key: ").append(panel?.let { node -> Rect().also { node.getBoundsInScreen(it) } })
            .append('\n')
        if (panel != null) {
            val rect = Rect()
            panel.getBoundsInScreen(rect)
            tap(rect.centerX(), rect.centerY())
            SystemClock.sleep(1200)
            output.append("after:\n").append(dumpImeNodes()).append('\n')
        }
        val path = File(
            instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir,
            "bbkb-clipboard-$tag.json").absolutePath
        File(path).writeText(output.toString())
        scenarioRef.close()
    }

    private fun findButtonByText(view: View, label: String): Button? {
        if (view is Button && view.text?.toString() == label) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findButtonByText(view.getChildAt(index), label)?.let { return it }
            }
        }
        return null
    }

    private fun dumpImeNodes(): String = buildString {
        for (node in imeNodes()) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            append(node.className?.toString()?.substringAfterLast('.'))
            append(" text='").append(node.text).append('\'')
            append(" desc='").append(node.contentDescription).append('\'')
            append(" clickable=").append(node.isClickable)
            append(" ").append(rect).append('\n')
        }
    }

    /** What the platform clipboard currently holds, for the clipboard probe's report. */
    private fun clipboardSummary(): String {
        var summary = "empty"
        scenarioRef.onActivity { activity ->
            val clip = activity.getSystemService(android.content.ClipboardManager::class.java).primaryClip
            summary = if (clip == null) "empty" else
                "${clip.description?.mimeTypeCount} item(s) of ${clip.description?.getMimeType(0)}"
        }
        return summary
    }

    /**
     * Diagnostic: injects a tap at a given point, waits, taps 'q', and reports the resulting text.
     * Used to test whether a build's shift key accepts synthetic touch and how long it needs.
     */
    @Test
    fun shiftProbe() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        val config = config()
        assumeTrue("diagnostic only", config["sx"] != null)
        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            editor = activity.findViewById<View>(android.R.id.content)
                .findViewWithTag<EditText>("prose")
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        SystemClock.sleep(1500)
        focusEditorByTap()
        SystemClock.sleep(1500)

        val sx = config.getValue("sx").toInt()
        val sy = config.getValue("sy").toInt()
        val qx = config["qx"]?.toInt() ?: 54
        val qy = config["qy"]?.toInt() ?: 1810
        val delays = (config["delays"] ?: "100").split(',').map { it.trim().toLong() }
        // Optional explicit sweep, "x:y" pairs, to map a key whose position is not where the shared
        // layout constants predict it.
        val points = config["points"]?.split(',')?.map { pair ->
            val (px, py) = pair.trim().split(':')
            px.toInt() to py.toInt()
        } ?: listOf(sx to sy)
        val output = StringBuilder()
        fun shiftState(): String {
            val nodes = mutableListOf<AccessibilityNodeInfo>()
            collect(imeWindow?.root, nodes)
            val node = nodes.firstOrNull {
                (it.contentDescription?.toString() ?: "").contains("Shift", ignoreCase = true)
            } ?: return "no shift node"
            return "desc='${node.contentDescription}' selected=${node.isSelected} " +
                "checked=${node.isChecked} enabled=${node.isEnabled}"
        }
        output.append("before: ${shiftState()}\n")
        for ((px, py) in points) {
            for (delay in delays) {
                clearEditor()
                SystemClock.sleep(150)
                tap(px, py)
                output.append("afterTap($px,$py): ${shiftState()}\n")
                SystemClock.sleep(delay)
                tap(qx, qy)
                SystemClock.sleep(300)
                output.append("point=$px,$py delay=$delay text=[${editorText()}]\n")
            }
        }
        File(
            (instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir),
            "bbkb-shift.json").writeText(output.toString())
        scenarioRef.close()
    }

    /**
     * Diagnostic: writes every accessibility node of the IME window (id, text, description, bounds).
     * Keys drawn on a custom canvas do not appear, but anything the build exposes as a real view
     * does — which is how a build-specific layout is understood without guessing pixels.
     */
    @Test
    fun dumpIme() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("diagnostic only", config()["dump"] == "true")
        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            editor = activity.findViewById<View>(android.R.id.content)
                .findViewWithTag<EditText>("prose")
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        SystemClock.sleep(1500)
        focusEditorByTap()
        SystemClock.sleep(2000)

        val output = StringBuilder()
        val window = imeWindow
        output.append("window=${window?.let { Rect().also { r -> it.getBoundsInScreen(r) }.toString() }}\n")
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        collect(window?.root, nodes)
        for (node in nodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            output.append(
                "id=${node.viewIdResourceName} cls=${node.className} " +
                    "text='${node.text}' desc='${node.contentDescription}' $rect clickable=${node.isClickable}\n"
            )
        }
        File(
            (instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir),
            "bbkb-ime-nodes.txt").writeText(output.toString())
        scenarioRef.close()
    }

    @Test
    fun runCorpus() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        val config = config()
        val language = config["language"] ?: "en"
        val tag = config["tag"] ?: "run"
        val limit = config["limit"]?.toIntOrNull() ?: 0
        val probeOnly = config["probeOnly"]?.toBoolean() ?: false
        // Autocorrect only happens in fields that ask for it (TYPE_TEXT_FLAG_AUTO_CORRECT).
        val field = config["field"] ?: "prose"
        // Human typing is ~100-150 ms a key. Slower typing also lets the on-key prediction layer
        // repaint after each key: it coalesces refreshes, and faster cadences leave it a key behind.
        val gapMs = config["gap"]?.toLongOrNull() ?: 150L
        val settleMs = config["settle"]?.toLongOrNull() ?: 130L
        val outputPath = File(
            instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir,
            "bbkb-bench-$tag-$language.json").absolutePath

        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        val rows = instrumentation.targetContext.assets.open("autocorrect-corpus.csv")
            .bufferedReader().use { it.readLines() }.drop(1).mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 4) return@mapNotNull null
                if (parts[0] != language) return@mapNotNull null
                Triple(parts[1], parts[2], parts[3])
            }.let { all ->
                // A stratified sample keeps every scenario type represented. Taking the first N rows
                // would only ever reach the first group, because the corpus is ordered by type.
                val perCase = config["sample"]?.toIntOrNull() ?: 0
                if (perCase <= 0) all else all.withIndex()
                    .groupBy { it.value.first }
                    .values
                    .flatMap { group ->
                        val stride = (group.size / perCase).coerceAtLeast(1)
                        group.filterIndexed { i, _ -> i % stride == 0 }.take(perCase)
                    }
                    .sortedBy { it.index }
                    .map { it.value }
            }.let { if (limit > 0) it.take(limit) else it }
        check(rows.isNotEmpty()) { "no $language rows in the corpus" }

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            editor = activity.findViewById<View>(android.R.id.content)
                .findViewWithTag<EditText>(field)
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        // A freshly cleared or freshly selected IME can take a few seconds to come up; asking again
        // is what turns "the IME window is not visible" into a reliable run.
        focusEditorByTap()
        val waitStart = System.nanoTime()
        while (imeWindow?.root == null && (System.nanoTime() - waitStart) / 1_000_000 < 10_000) {
            SystemClock.sleep(250)
            scenarioRef.onActivity { activity ->
                activity.getSystemService(InputMethodManager::class.java)
                    .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        SystemClock.sleep(600)
        instrumentation.waitForIdleSync()

        val report = JSONObject()
        val imeId = Settings.Secure.getString(instrumentation.targetContext.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD)
        report.put("ime", imeId)
        report.put("language", language)

        // Calibration returns the grid labelled for the requested language, switching the keyboard
        // itself if it was left on the other one by an earlier run.
        val layout = calibrate()
        report.put("letters", layout.letters.keys.sorted().joinToString(""))
        report.put("shiftAvailable", layout.shiftEnabled)
        report.put("results", JSONArray())
        if (probeOnly) {
            File(outputPath).writeText(report.toString(2))
            scenarioRef.close()
            return
        }

        // Diagnostic mode: type one word slowly and record what the keyboard offers after every
        // key and at several settle delays. Shows whether the on-key prediction layer lags.
        config["probeWord"]?.let { word ->
            resetEditor()
            SystemClock.sleep(300)
            val timeline = JSONArray()
            for ((index, ch) in word.withIndex()) {
                val (x, y) = layout.letters.getValue(ch.lowercaseChar())
                if (ch.isUpperCase() && layout.shiftEnabled) {
                    tap(layout.shift.first, layout.shift.second)
                    SystemClock.sleep(layout.shiftDelayMs)
                }
                tap(x, y)
                SystemClock.sleep(150)
                timeline.put(JSONObject().put("afterKey", word.substring(0, index + 1))
                    .put("text", editorText()).put("offered", JSONArray(suggestions())))
            }
            for (delay in listOf(100L, 300L, 600L, 1000L)) {
                SystemClock.sleep(delay)
                timeline.put(JSONObject().put("afterKey", "settle+$delay")
                    .put("text", editorText()).put("offered", JSONArray(suggestions())))
            }
            report.put("timeline", timeline)
            File(outputPath).writeText(report.toString(2))
            scenarioRef.close()
            return
        }

        val results = report.getJSONArray("results")
        val stripFirstLatency = mutableListOf<Long>()
        // Warm-up: the very first word typed after the session starts can be answered from the
        // previous input session; it is discarded so no corpus row carries that artefact.
        val warmUpWord = if (language == "ru") "привет" else "the"
        run {
            resetEditor()
            SystemClock.sleep(300)
            for (char in warmUpWord) {
                val (x, y) = layout.letters.getValue(char)
                tap(x, y)
                SystemClock.sleep(120)
            }
            SystemClock.sleep(250)
            tap(layout.space.first, layout.space.second)
            SystemClock.sleep(200)
            tap(layout.backspace.first, layout.backspace.second)
            resetEditor()
            SystemClock.sleep(200)
        }
        for ((index, row) in rows.withIndex()) {
            val (case, typed, target) = row
            // Only characters reachable by a single tap (shift for capitals) are typed; accented
            // and punctuation-bearing samples are reported as skipped, never counted as passes.
            val unsupported = typed.filter { layout.letters[it.lowercaseChar()] == null }
            if (unsupported.isNotEmpty()) {
                results.put(JSONObject()
                    .put("index", index).put("case", case).put("typed", typed).put("target", target)
                    .put("status", "SKIPPED_UNSUPPORTED_CHAR")
                    .put("unsupported", unsupported))
                continue
            }
            val positions = typed.map { layout.letters.getValue(it.lowercaseChar()) }
            var typedText = ""
            repeat(2) { attempt ->
                resetEditor()
                // restartInput tears the input session down; typing before the new session is up is
                // what makes the keyboard answer with predictions for an empty field.
                SystemClock.sleep(300)
                var tapToCharacterMs = -1L
                var suggestionLatencyMs = -1L
                var suggestionsChanged = false
                for ((tapIndex, position) in positions.withIndex()) {
                    val (x, y) = position
                    val isLast = tapIndex == positions.lastIndex
                    val before = if (isLast) suggestions() else emptyList()
                    val start = System.nanoTime()
                    if (typed[tapIndex].isUpperCase() && layout.shiftEnabled) {
                        tap(layout.shift.first, layout.shift.second)
                        SystemClock.sleep(layout.shiftDelayMs)
                    }
                    tap(x, y)
                    if (isLast) {
                        while (!editorText().equals(typed, ignoreCase = true) &&
                            (System.nanoTime() - start) / 1_000_000 < 400) {
                            SystemClock.sleep(5)
                        }
                        tapToCharacterMs = (System.nanoTime() - start) / 1_000_000
                        val (after, latency) = awaitChange(before)
                        suggestionLatencyMs = latency
                        suggestionsChanged = after != before
                        // A second update usually follows the first within a frame or two; let it land.
                        SystemClock.sleep(settleMs)
                    } else {
                        SystemClock.sleep(gapMs)
                    }
                }
                typedText = editorText()
                rowTapToCharacterMs = tapToCharacterMs
                rowSuggestionLatencyMs = suggestionLatencyMs
                rowSuggestionsChanged = suggestionsChanged
                if (typedText.equals(typed, ignoreCase = true) || attempt == 1) return@repeat
            }
            val offered = suggestions()
            val strip = stripWords()
            if (strip.isNotEmpty()) stripFirstLatency.add(rowSuggestionLatencyMs)
            tap(layout.space.first, layout.space.second)
            SystemClock.sleep(150)
            val afterSpace = editorText()
            tap(layout.backspace.first, layout.backspace.second)
            SystemClock.sleep(130)
            val afterUndo = editorText()
            resetEditor()
            SystemClock.sleep(30)

            val ordered = strip.ifEmpty { offered }
            // When the build could not be shifted, every sample went in lower-case, so the
            // comparisons have to fold case or a correct repair would be scored as a miss.
            val folded = !layout.shiftEnabled
            fun same(actual: String, expected: String) = actual.equals(expected, ignoreCase = folded)
            results.put(JSONObject()
                .put("index", index)
                .put("case", case)
                .put("typed", typed)
                .put("typedText", typedText)
                .put("target", target)
                .put("caseFolded", folded)
                .put("strip", JSONArray(strip))
                .put("offered", JSONArray(offered))
                .put("offeredHit", offered.any { same(it, target) })
                .put("top1", ordered.firstOrNull() ?: JSONObject.NULL)
                .put("top1Hit", ordered.firstOrNull()?.let { same(it, target) } ?: false)
                .put("top3Hit", ordered.take(3).any { same(it, target) })
                .put("afterSpace", afterSpace)
                .put("autocorrected", same(afterSpace.trim(), target))
                .put("preserved", same(afterSpace.trim(), typed))
                .put("afterUndo", afterUndo)
                .put("tapToCharacterMs", rowTapToCharacterMs)
                .put("suggestionLatencyMs", rowSuggestionLatencyMs)
                .put("suggestionsChanged", rowSuggestionsChanged)
                .put("status", "OK"))
        }
        report.put("stripLatencySamples", stripFirstLatency.size)
        File(outputPath).writeText(report.toString(2))
        scenarioRef.close()
    }

    /**
     * Long continuous run: real taps only, alternating languages and cycling the tool panels, to
     * look for crashes, ANRs, dropped keystrokes, duplicated characters, spontaneous layout changes
     * and unbounded memory growth. Memory itself is sampled from outside (see
     * `tools/qa/Invoke-StressTest.ps1`) because `dumpsys meminfo` is not available to the app.
     *
     *   am instrument -e class ...#stress -e stress true -e keys 10000 -e minutes 30 -e tag fork
     */
    @Test
    fun stress() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        val config = config()
        assumeTrue("stress only", config["stress"] == "true")
        val targetKeys = config["keys"]?.toIntOrNull() ?: 10_000
        val budgetMinutes = config["minutes"]?.toLongOrNull() ?: 30L
        val tag = config["tag"] ?: "run"
        val outputPath = File(
            instrumentation.targetContext.getExternalFilesDir(null) ?: instrumentation.targetContext.filesDir,
            "bbkb-stress-$tag.json").absolutePath

        val serviceInfo = instrumentation.uiAutomation.serviceInfo
        serviceInfo.flags = serviceInfo.flags or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        instrumentation.uiAutomation.serviceInfo = serviceInfo

        scenarioRef = ActivityScenario.launch(HarnessActivity::class.java)
        scenarioRef.onActivity { activity ->
            editor = activity.findViewById<View>(android.R.id.content)
                .findViewWithTag<EditText>("prose")
            editor.requestFocus()
            activity.getSystemService(InputMethodManager::class.java)
                .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
        }
        focusEditorByTap()
        val waitStart = System.nanoTime()
        while (imeWindow?.root == null && (System.nanoTime() - waitStart) / 1_000_000 < 10_000) {
            SystemClock.sleep(250)
        }
        SystemClock.sleep(600)

        val latin = calibrate("en")
        val latinQ = latin.letters.getValue('q')
        // Each language is calibrated in place, so the Cyrillic corpus is typed on measured keys
        // rather than on Latin coordinates relabelled by position.
        val ruLayout = runCatching { calibrate("ru") }.getOrNull()
        selectLanguage("en", latinQ, latin.space)

        val startedAt = System.nanoTime()
        val deadline = startedAt + budgetMinutes * 60_000_000_000L
        val latencies = mutableListOf<Long>()
        var typed = 0
        var lostCharacters = 0
        var duplicatedCharacters = 0
        var panelCycles = 0
        var panelFailures = 0
        var keyboardCycles = 0
        var languageSwitches = 0
        var wordIndex = 0
        var phaseIsRussian = false
        val errors = JSONArray()

        fun currentLayout() = if (phaseIsRussian && ruLayout != null) ruLayout else latin
        fun words() = if (phaseIsRussian) RU_WORDS else EN_WORDS

        fun switchPhase() {
            if (ruLayout == null) return
            val target = if (phaseIsRussian) "en" else "ru"
            selectLanguage(target, latinQ, latin.space)
            phaseIsRussian = !phaseIsRussian
            languageSwitches++
        }

        /** Opens a tool panel from the toolbar and returns to the letters. */
        fun cyclePanel(): Boolean {
            val labels = listOf("Emojis", "Clipboard", "Select, cut, or copy", "Keyboard mode")
            val label = labels[panelCycles % labels.size]
            val node = imeNodes().firstOrNull {
                (it.contentDescription?.toString() ?: "").equals(label, ignoreCase = true) && it.isClickable
            } ?: return false
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() == 0) return false
            tap(rect.centerX(), rect.centerY())
            SystemClock.sleep(400)
            // Close whatever opened; 'Keyboard mode' is the letters page on every build.
            val back = imeNodes().firstOrNull {
                (it.contentDescription?.toString() ?: "").equals("Keyboard mode", ignoreCase = true) && it.isClickable
            }
            if (back != null) {
                val backRect = Rect()
                back.getBoundsInScreen(backRect)
                if (backRect.width() > 0 && label != "Keyboard mode") {
                    tap(backRect.centerX(), backRect.centerY())
                    SystemClock.sleep(300)
                }
            }
            return true
        }

        try {
            while (typed < targetKeys && System.nanoTime() < deadline) {
                val layout = currentLayout()
                val word = words()[wordIndex % words().size]
                wordIndex++
                clearEditor()
                var expected = ""
                var typedHere = 0
                for (ch in word) {
                    val position = layout.letters[ch] ?: continue
                    tap(position.first, position.second, holdMs = 40)
                    expected += ch
                    typedHere++
                    SystemClock.sleep(40)
                    if (typedHere % 5 == 0) {
                        val start = System.nanoTime()
                        while (editorText().length < expected.length &&
                            (System.nanoTime() - start) / 1_000_000 < 200) {
                            SystemClock.sleep(5)
                        }
                        latencies.add((System.nanoTime() - start) / 1_000_000)
                    }
                }
                typed += typedHere
                val actual = editorText()
                if (actual.length < expected.length) lostCharacters += expected.length - actual.length
                if (actual.length > expected.length) duplicatedCharacters += actual.length - expected.length
                tap(layout.space.first, layout.space.second)
                SystemClock.sleep(60)

                if (typed > 0 && typed / 300 != (typed - typedHere) / 300) {
                    if (cyclePanel()) panelCycles++ else panelFailures++
                }
                if (typed > 0 && typed / 400 != (typed - typedHere) / 400) {
                    // Hide and show the keyboard with the same session to exercise rebinding.
                    instrumentation.runOnMainSync { editor.clearFocus() }
                    SystemClock.sleep(150)
                    focusEditorByTap()
                    keyboardCycles++
                }
                if (typed > 0 && typed / 800 != (typed - typedHere) / 800) switchPhase()
            }
        } catch (error: Throwable) {
            errors.put("${error.javaClass.simpleName}: ${error.message}")
        }

        val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000
        val report = JSONObject()
            .put("ime", Settings.Secure.getString(
                instrumentation.targetContext.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD))
            .put("keys", typed)
            .put("elapsedMs", elapsedMs)
            .put("keysPerSecond", if (elapsedMs > 0) typed * 1000.0 / elapsedMs else 0.0)
            .put("lostCharacters", lostCharacters)
            .put("duplicatedCharacters", duplicatedCharacters)
            .put("panelCycles", panelCycles)
            .put("panelFailures", panelFailures)
            .put("keyboardCycles", keyboardCycles)
            .put("languageSwitches", languageSwitches)
            .put("russianAvailable", ruLayout != null)
            .put("latencySamples", latencies.size)
            .put("latencyP50", percentile(latencies, 0.50))
            .put("latencyP95", percentile(latencies, 0.95))
            .put("latencyP99", percentile(latencies, 0.99))
            .put("errors", errors)
        File(outputPath).writeText(report.toString(2))
        scenarioRef.close()
    }

    private fun percentile(values: List<Long>, share: Double): Long {
        if (values.isEmpty()) return -1
        val ordered = values.sorted()
        val index = (share * (ordered.size - 1)).toInt().coerceIn(0, ordered.size - 1)
        return ordered[index]
    }
}
