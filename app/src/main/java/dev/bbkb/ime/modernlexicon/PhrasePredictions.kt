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
    private val wordPattern = Regex("[\\p{L}\\p{M}]+(?:['’‐-][\\p{L}\\p{M}]+)*")
    private val sentenceSeparators = charArrayOf('.', '!', '?', '…', '\n', '\r')

    @JvmStatic
    fun nextWords(context: Context, contextBefore: String?, locale: Locale?): List<String> {
        val tokens = tokenize(contextBefore, locale)
        if (tokens.isEmpty()) return emptyList()
        val map = tables.get() ?: load(context).let { loaded ->
            if (tables.compareAndSet(null, loaded)) loaded else checkNotNull(tables.get())
        }
        return rank(tokens, map)
    }

    internal fun rank(tokens: List<String>, map: Map<String, List<Choice>>): List<String> {
        if (tokens.isEmpty()) return emptyList()
        val trigram = if (tokens.size >= 2) map[tokens.takeLast(2).joinToString(" ")] else null
        val bigram = map[tokens.last()]
        return sequenceOf(trigram, bigram)
            .filterNotNull()
            .flatMap { it.sortedByDescending(Choice::count).asSequence() }
            .map(Choice::word)
            .distinct()
            .take(8)
            .toList()
    }

    internal fun tokenize(text: String?, locale: Locale?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val sentence = text.substring(text.lastIndexOfAny(sentenceSeparators) + 1)
        val normalized = sentence.lowercase(locale ?: Locale.ROOT).replace('ё', 'е')
        return wordPattern.findAll(normalized).map { it.value }.toList().takeLast(2)
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
