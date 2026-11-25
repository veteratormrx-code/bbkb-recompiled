package dev.bbkb.ime.modernlexicon

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.InflaterInputStream

/**
 * Reader for the RUL1 prefix-bucket lexicon used by the 4pda Russian packs.
 * Lookup never scans the whole file: one zlib bucket per 1–3 character key.
 */
class BbdLexicon(private val bytes: ByteArray) {
    private val sections: Map<String, Section>

    init {
        require(bytes.size >= 8) { "bbd too small" }
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.int
        require(magic == MAGIC) { "not an RUL1 lexicon" }
        val count = buffer.int
        require(count in 1..(bytes.size - 8) / 12) { "invalid RUL1 bucket count" }
        val parsed = HashMap<String, Section>(count)
        repeat(count) {
            val prefixLen = buffer.short.toInt() and 0xFFFF
            val prefixBytes = ByteArray(prefixLen)
            buffer.get(prefixBytes)
            val offset = buffer.int
            val length = buffer.int
            val prefix = String(prefixBytes, Charsets.UTF_8)
            parsed[prefix] = Section(offset, length)
        }
        sections = parsed
    }

    fun lookup(prefix: String, limit: Int): List<Lexeme> {
        if (limit <= 0) return emptyList()
        val norm = normalize(prefix)
        val hits = ArrayList<Lexeme>()
        scanBucket(norm.take(3)) { _, lexeme ->
            if (normalize(lexeme.word).startsWith(norm)) hits.add(lexeme)
            hits.size < limit
        }
        return hits
    }

    /** Exact one-edit candidates from the compact frequency pack, including edits to the bucket key. */
    internal fun lookupExactEdits(typed: String): List<Lexeme> {
        val word = normalize(typed)
        if (word.length !in 3..MAX_COMMON_WORD_LENGTH || word.any { it !in RUSSIAN_ALPHABET }) {
            return emptyList()
        }
        val candidatesByBucket = HashMap<String, MutableSet<String>>()
        fun add(candidate: String) {
            if (candidate != word) {
                val key = candidate.take(3)
                if (key in sections) candidatesByBucket.getOrPut(key) { HashSet() }.add(candidate)
            }
        }
        for (i in word.indices) {
            add(word.removeRange(i, i + 1))
            if (i + 1 < word.length && word[i] != word[i + 1]) {
                add(word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2))
            }
        }
        for (i in 0..word.length) {
            val before = word.substring(0, i)
            val after = word.substring(i)
            for (letter in RUSSIAN_ALPHABET) add(before + letter + after)
        }

        val hits = ArrayList<Lexeme>()
        for ((key, candidates) in candidatesByBucket) {
            scanBucket(key) { _, lexeme ->
                if (normalize(lexeme.word) in candidates) hits.add(lexeme)
                true
            }
        }
        return hits
    }

    /**
     * Also reads buckets whose 3-letter key is one neighbor-key substitution away, so a typo in
     * the first three letters can still surface the intended word. Exact prefix hits stay first.
     */
    fun lookupWithPrefixTypos(prefix: String, limit: Int, neighbors: Map<Char, String>): List<Lexeme> {
        val norm = normalize(prefix)
        if (norm.length < 2 || limit <= 0) return emptyList()
        val keys = LinkedHashSet<String>()
        val head = norm.take(3)
        keys.add(head)
        for (i in head.indices) {
            val near = neighbors[head[i]] ?: continue
            for (ch in near) {
                keys.add(head.substring(0, i) + ch + head.substring(i + 1))
            }
        }
        val hits = LinkedHashMap<String, Lexeme>()
        for (key in keys) {
            scanBucket(key) { index, lexeme ->
                val wordKey = normalize(lexeme.word)
                val isExact = wordKey.startsWith(norm)
                val distance = when {
                    isExact -> 0
                    index < FUZZY_WINDOW -> editDistance(norm, wordKey)
                    isSingleEdit(norm, wordKey) -> 1
                    else -> 3
                }
                // Distance 1 is the mod's correction (a doubled letter, a missing letter).
                // Distance 2 stays inside the short window so a long bucket cannot flood the strip.
                if (isExact || distance <= 1 || (distance == 2 && index < FUZZY_WINDOW)) {
                    val previous = hits[lexeme.word]
                    if (previous == null || lexeme.frequency > previous.frequency) {
                        hits[lexeme.word] = lexeme
                    }
                }
                index + 1 < CLOSE_SCAN
            }
        }
        return hits.values.sortedWith(compareBy<Lexeme> { editDistance(norm, normalize(it.word)) }
            .thenByDescending { it.frequency }).take(limit)
    }

    private inline fun scanBucket(key: String, visit: (Int, Lexeme) -> Boolean) {
        if (key.length < 2) return
        val section = sections[key] ?: return
        if (section.offset < 0 || section.length <= 0 || section.length > bytes.size - section.offset) return
        val compressed = ByteArrayInputStream(bytes, section.offset, section.length)
        InflaterInputStream(compressed).bufferedReader(Charsets.UTF_8).use { reader ->
            var index = 0
            while (true) {
                val line = reader.readLine() ?: break
                val tab = line.indexOf('\t')
                if (tab <= 0) continue
                val word = line.substring(0, tab)
                val frequency = line.substring(tab + 1).toIntOrNull() ?: 0
                if (!visit(index++, Lexeme(word, frequency))) break
            }
        }
    }

    /** Beyond the fuzzy window only distance 1 matters. Check it without allocating a matrix. */
    private fun isSingleEdit(a: String, b: String): Boolean {
        if (kotlin.math.abs(a.length - b.length) > 1) return false
        val shorter = if (a.length <= b.length) a else b
        val longer = if (a.length <= b.length) b else a
        var i = 0
        while (i < shorter.length && shorter[i] == longer[i]) i++
        if (shorter.length < longer.length) {
            return shorter.regionMatches(i, longer, i + 1, shorter.length - i)
        }
        if (i == shorter.length) return false
        return shorter.regionMatches(i + 1, longer, i + 1, shorter.length - i - 1) ||
            (i + 1 < shorter.length && shorter[i] == longer[i + 1] && shorter[i + 1] == longer[i] &&
                shorter.regionMatches(i + 2, longer, i + 2, shorter.length - i - 2))
    }

    /**
     * One adjacent transposition counts as a single slip. сегодян → сегодня is я/н swapped;
     * plain Levenshtein calls that two edits and then prefers the shorter fragment сегодн.
     */
    private fun editDistance(a: String, b: String): Int {
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

    data class Lexeme(val word: String, val frequency: Int)

    private data class Section(val offset: Int, val length: Int)

    companion object {
        const val MAGIC = 0x52554C31
        private const val RUSSIAN_ALPHABET = "абвгдежзийклмнопрстуфхцчшщъыьэюя"
        private const val MAX_COMMON_WORD_LENGTH = 21
        /** A second edit is only trusted near the start of the bucket. */
        private const val FUZZY_WINDOW = 80
        /**
         * One-edit hits are kept from the whole common pack. Those packs are alphabetical, so
         * привет sits at line 81 and завтра at line 100 — both past the old window of 80, which
         * is why the strip offered приедет and завета instead. The spelling pack runs to tens of
         * thousands of lines; past this cap a one-edit word is too rare to be worth the scan.
         */
        private const val CLOSE_SCAN = 1200

        @JvmStatic
        fun normalize(word: String): String = word.lowercase().replace('ё', 'е')
    }
}
