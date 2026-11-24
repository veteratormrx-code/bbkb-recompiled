package dev.bbkb.ime.modernlexicon

import android.content.Context
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Next-word table. A key is one previous word (bigram) or two previous words (trigram).
 * Counts from later shards are kept: the loader sums them instead of keeping the first row.
 */
object PhrasePredictions {
    data class Choice(val word: String, val count: Int)

    private val tables = AtomicReference<Map<String, List<Choice>>?>()

    @JvmStatic
    fun nextWords(context: Context, contextBefore: String?, locale: Locale?): List<String> {
        val tokens = tokenize(contextBefore, locale)
        if (tokens.isEmpty()) return emptyList()
        val map = tables.get() ?: load(context).also { tables.compareAndSet(null, it) }
        val merged = LinkedHashMap<String, Int>()
        if (tokens.size >= 2) {
            add(merged, map["${tokens[tokens.size - 2]} ${tokens[tokens.size - 1]}"])
        }
        add(merged, map[tokens.last()])
        return merged.entries.sortedByDescending { it.value }.take(8).map { it.key }
    }

    private fun add(into: LinkedHashMap<String, Int>, choices: List<Choice>?) {
        if (choices == null) return
        for (choice in choices) {
            into[choice.word] = maxOf(into[choice.word] ?: 0, choice.count)
        }
    }

    private fun tokenize(text: String?, locale: Locale?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        return text.trim().lowercase(locale ?: Locale.ROOT).replace('ё', 'е')
            .split(Regex("\\s+")).filter { it.length >= 2 }.takeLast(2)
    }

    private fun load(context: Context): Map<String, List<Choice>> {
        val out = HashMap<String, List<Choice>>()
        for (name in listOf("bbmodern/phrases-ru.idx", "bbmodern/phrases-en.idx")) {
            try {
                context.assets.open(name).bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val tab = line.indexOf('\t')
                        if (tab <= 0) return@forEach
                        val key = line.substring(0, tab)
                        val choices = line.substring(tab + 1).split('|').mapNotNull { piece ->
                            val colon = piece.lastIndexOf(':')
                            if (colon <= 0) return@mapNotNull null
                            val word = piece.substring(0, colon)
                            val count = piece.substring(colon + 1).toIntOrNull() ?: return@mapNotNull null
                            if (word.isEmpty()) null else Choice(word, count)
                        }
                        if (choices.isNotEmpty()) out[key] = choices
                    }
                }
            } catch (ignored: Exception) {
                // Optional asset.
            }
        }
        return out
    }
}
