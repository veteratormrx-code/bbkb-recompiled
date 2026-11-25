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
    private val starters = AtomicReference<Map<String, List<String>>?>()
    private val wordPattern = Regex("[\\p{L}\\p{M}]+(?:['’‐-][\\p{L}\\p{M}]+)*")
    private val sentenceSeparators = charArrayOf('.', '!', '?', '…', '\n', '\r')

    @JvmStatic
    fun nextWords(context: Context, contextBefore: String?, locale: Locale?): List<String> {
        ensureLoaded(context)
        val tokens = tokenize(contextBefore, locale)
        if (tokens.isEmpty()) return starterWords(locale, contextBefore)
        return rank(tokens, checkNotNull(tables.get()))
    }

    /** Most common continuations, used when the field has no previous word yet. */
    private fun starterWords(locale: Locale?, contextBefore: String?): List<String> {
        val lang = if (locale?.language == "ru") "ru" else "en"
        val words = starters.get()?.get(lang).orEmpty()
        if (!startsSentence(contextBefore)) return words
        val cmp = locale ?: Locale.ROOT
        return words.map { word -> word.replaceFirstChar { it.titlecase(cmp) } }
    }

    private fun startsSentence(contextBefore: String?): Boolean {
        if (contextBefore.isNullOrBlank()) return true
        val end = contextBefore.trimEnd().lastOrNull() ?: return true
        return end in sentenceSeparators
    }

    private fun ensureLoaded(context: Context) {
        if (tables.get() != null) return
        val loaded = load(context)
        tables.compareAndSet(null, loaded.phrases)
        starters.compareAndSet(null, loaded.starters)
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

    internal fun topWords(counts: Map<String, Int>, limit: Int = 8): List<String> {
        return counts.entries.sortedByDescending { it.value }.take(limit).map { it.key }
    }

    private data class Loaded(val phrases: Map<String, List<Choice>>, val starters: Map<String, List<String>>)

    private fun load(context: Context): Loaded {
        val out = HashMap<String, List<Choice>>()
        val starters = HashMap<String, List<String>>()
        for ((name, lang) in listOf("bbmodern/phrases-ru.idx" to "ru", "bbmodern/phrases-en.idx" to "en")) {
            val counts = HashMap<String, Int>()
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
                        if (choices.isEmpty()) return@forEach
                        out[key] = choices
                        for (choice in choices) counts.merge(choice.word, choice.count, Int::plus)
                    }
                }
            } catch (ignored: Exception) {
                // Optional asset.
            }
            starters[lang] = topWords(counts)
        }
        return Loaded(out, starters)
    }
}
