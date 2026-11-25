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
            val typedNorm = BbdLexicon.normalize(typed)
            val frequencies = HashMap<String, Int>()
            for ((word, frequency) in hits) {
                val key = BbdLexicon.normalize(word)
                frequencies[key] = maxOf(frequencies[key] ?: 0, frequency)
            }
            // The typed string itself is a dictionary word: leave XT9's completion order alone.
            val typedKnown = frequencies.containsKey(typedNorm)
            if (hits.isNotEmpty()) {
                val existing = suggestions.map { it.word.lowercase(locale ?: Locale.ROOT) }.toMutableSet()
                val ranked = hits.entries.sortedWith(
                    compareBy<Map.Entry<String, Int>> { damerau(typedNorm, BbdLexicon.normalize(it.key)) }
                        .thenByDescending { it.value }
                )
                var inserted = 0
                for ((word, frequency) in ranked) {
                    if (inserted >= 8) break
                    val key = word.lowercase(locale ?: Locale.ROOT)
                    if (!existing.add(key)) continue
                    val distance = damerau(typedNorm, BbdLexicon.normalize(word))
                    if (distance > 2 || distance == 0) continue
                    suggestions.add(
                        SuggestedWords.SuggestedWordInfo(
                            word,
                            frequency,
                            2,
                            Dictionary.DICTIONARY_HARDCODED,
                            -1,
                            suggestions.size,
                            null,
                        )
                    )
                    inserted++
                }
            }
            // The mod shows the closest dictionary word first. XT9 often leads with an
            // inflected neighbor (приввет → приедет) while привет sits later or only in the pack.
            if (!typedKnown) promoteClosest(typed, suggestions, frequencies)
        } catch (ignored: Exception) {
            // A damaged pack must not take down the suggestion thread. "bb" and other short keys included.
        }
    }

    /**
     * Closest real words first, lexicon hits before an equally close engine form, then frequency.
     * A longer completion of the typo (здраствуйте) loses to the common word one edit away
     * (здравствуйте) when that word is in the pack.
     */
    internal fun promoteClosest(
        typed: String,
        suggestions: ArrayList<SuggestedWords.SuggestedWordInfo>,
        frequencies: Map<String, Int> = emptyMap(),
    ) {
        if (suggestions.size < 2) return
        val typedNorm = BbdLexicon.normalize(typed)
        val typedEntry = suggestions.firstOrNull {
            BbdLexicon.normalize(it.word) == typedNorm && it.getKind() == 0
        } ?: suggestions.firstOrNull { BbdLexicon.normalize(it.word) == typedNorm }
        val rest = suggestions.filter { it !== typedEntry }
        // прив → привет is a completion. приввет → приедет is not, and that is the list we reorder.
        val leader = rest.firstOrNull() ?: return
        val leaderNorm = BbdLexicon.normalize(leader.word)
        if (leaderNorm.startsWith(typedNorm) && leaderNorm.length > typedNorm.length) return
        val close = rest.filter { damerau(typedNorm, BbdLexicon.normalize(it.word)) <= 2 }
            .sortedWith(
                compareBy<SuggestedWords.SuggestedWordInfo> { damerau(typedNorm, BbdLexicon.normalize(it.word)) }
                    .thenByDescending { frequencies[BbdLexicon.normalize(it.word)] ?: 0 }
                    .thenByDescending { if (it.sourceDictionary === Dictionary.DICTIONARY_HARDCODED) 1 else 0 }
                    .thenByDescending { it.score }
                    .thenBy { it.word.length }
            )
        val far = rest.filter { damerau(typedNorm, BbdLexicon.normalize(it.word)) > 2 }
        suggestions.clear()
        if (typedEntry != null) suggestions.add(typedEntry)
        suggestions.addAll(close)
        suggestions.addAll(far)
    }

    /** Insert, delete, substitution, or one adjacent transposition. Longer jumps count as 3. */
    private fun damerau(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 2) return 3
        val n = a.length
        val m = b.length
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    dp[i][j] = minOf(dp[i][j], dp[i - 2][j - 2] + 1)
                }
            }
        }
        return dp[n][m].coerceAtMost(3)
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
