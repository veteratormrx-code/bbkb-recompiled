package dev.bbkb.ime.modernlexicon

import android.content.Context
import dev.bbkb.ime.core.engine.Dictionary
import dev.bbkb.ime.core.suggestion.SuggestedWords
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Merges the extended Russian lexicon into an XT9 suggestion list.
 * Runs on the suggestion thread. Password and no-learn fields must not call [merge].
 */
object ModernLexicon {
    private val cache = ConcurrentHashMap<String, BbdLexicon>()
    private val packs = listOf(
        "bbmodern/ru-common.bbd",
        "bbmodern/ru-spelling.bbd",
        "bbmodern/ru-opencorpora.bbd",
        "bbmodern/ru-unimorph.bbd",
        "bbmodern/ru-conversational.bbd",
    )

    private val neighbors = mapOf(
        'й' to "цу", 'ц' to "йук", 'у' to "цке", 'к' to "уен", 'е' to "кн",
        'н' to "ег", 'г' to "нш", 'ш' to "гщ", 'щ' to "шз", 'з' to "щх", 'х' to "зъ",
        'ф' to "ыв", 'ы' to "фва", 'в' to "ыап", 'а' to "впр", 'п' to "аро",
        'р' to "пол", 'о' to "рлд", 'л' to "одж", 'д' to "лжэ", 'ж' to "дэ",
        'я' to "чс", 'ч' to "ясм", 'с' to "чми", 'м' to "сит", 'и' to "мть",
        'т' to "иьб", 'ь' to "тбю", 'б' to "ью", 'ю' to "бь",
    )

    @JvmStatic
    fun merge(
        context: Context,
        typed: String?,
        suggestions: ArrayList<SuggestedWords.SuggestedWordInfo>,
        locale: Locale?,
    ) {
        if (typed.isNullOrBlank() || typed.length < 2 || typed.any { it.isDigit() }) return
        if (!typed.any { it in 'А'..'я' || it == 'ё' || it == 'Ё' }) return
        try {
            val hits = LinkedHashMap<String, Int>()
            for (pack in packs) {
                val lexicon = load(context, pack) ?: continue
                for (lexeme in lexicon.lookupWithPrefixTypos(typed, 12, neighbors)) {
                    val score = lexeme.frequency + if (lexeme.frequency == 0) 1 else 0
                    hits[lexeme.word] = maxOf(hits[lexeme.word] ?: 0, score)
                }
            }
            if (hits.isEmpty()) return
            val existing = suggestions.map { it.word.lowercase(locale ?: Locale.ROOT) }.toMutableSet()
            val ranked = hits.entries.sortedByDescending { rank(typed, it.key, it.value) }
            var inserted = 0
            for ((word, frequency) in ranked) {
                if (inserted >= 6) break
                val key = word.lowercase(locale ?: Locale.ROOT)
                if (!existing.add(key)) continue
                val distance = editDistance(BbdLexicon.normalize(typed), BbdLexicon.normalize(word))
                val kind = if (distance == 0 || BbdLexicon.normalize(word).startsWith(BbdLexicon.normalize(typed))) 1 else 2
                val score = rank(typed, word, frequency)
                suggestions.add(
                    SuggestedWords.SuggestedWordInfo(
                        word,
                        score,
                        kind,
                        Dictionary.DICTIONARY_HARDCODED,
                        -1,
                        suggestions.size,
                        null,
                    )
                )
                inserted++
            }
        } catch (ignored: Exception) {
            // A damaged pack must not take down the suggestion thread. "bb" and other short keys included.
        }
    }

    private fun rank(typed: String, word: String, frequency: Int): Int {
        val a = BbdLexicon.normalize(typed)
        val b = BbdLexicon.normalize(word)
        var score = frequency.coerceAtMost(1_000_000)
        if (b.startsWith(a)) score += 50_000 + (20 - (b.length - a.length).coerceAtLeast(0)) * 100
        val distance = editDistance(a, b)
        score -= distance * 8_000
        if (distance == 1 && adjacentTypo(a, b)) score += 4_000
        return score
    }

    private fun adjacentTypo(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diffs = 0
        for (i in a.indices) {
            if (a[i] == b[i]) continue
            diffs++
            if (diffs > 1) return false
            val near = neighbors[a[i]] ?: return false
            if (b[i] !in near) return false
        }
        return diffs == 1
    }

    private fun editDistance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev.indices.forEach { prev[it] = cur[it] }
        }
        return prev[b.length]
    }

    private fun load(context: Context, asset: String): BbdLexicon? {
        cache[asset]?.let { return it }
        return try {
            val bytes = context.assets.open(asset).use { it.readBytes() }
            BbdLexicon(bytes).also { cache[asset] = it }
        } catch (missing: Exception) {
            null
        }
    }
}
