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
            var exactMatches = 0
            scanBucket(key) { index, lexeme ->
                val wordKey = normalize(lexeme.word)
                val isExact = wordKey.startsWith(norm)
                if (isExact) exactMatches++
                if (isExact || (index < FUZZY_WINDOW && editDistance(norm, wordKey) <= 2)) {
                    val previous = hits[lexeme.word]
                    if (previous == null || lexeme.frequency > previous.frequency) {
                        hits[lexeme.word] = lexeme
                    }
                }
                index + 1 < FUZZY_WINDOW || (key == head && exactMatches < limit)
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
            for (k in prev.indices) prev[k] = cur[k]
        }
        return prev[b.length]
    }

    data class Lexeme(val word: String, val frequency: Int)

    private data class Section(val offset: Int, val length: Int)

    companion object {
        const val MAGIC = 0x52554C31
        private const val FUZZY_WINDOW = 80

        @JvmStatic
        fun normalize(word: String): String = word.lowercase().replace('ё', 'е')
    }
}
