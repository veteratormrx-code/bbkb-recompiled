package dev.bbkb.ime.modernlexicon

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.ArrayList
import java.util.zip.InflaterInputStream

/**
 * Reader for the RUL1 prefix-bucket lexicon used by the 4pda Russian packs.
 * Lookup never scans the whole file: one zlib bucket per 1–3 character key.
 */
class BbdLexicon(private val bytes: ByteArray) {
    private val sections: List<Section>

    init {
        require(bytes.size >= 8) { "bbd too small" }
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.int
        require(magic == MAGIC) { "not an RUL1 lexicon" }
        val count = buffer.int
        val parsed = ArrayList<Section>(count)
        repeat(count) {
            val prefixLen = buffer.short.toInt() and 0xFFFF
            val prefixBytes = ByteArray(prefixLen)
            buffer.get(prefixBytes)
            val offset = buffer.int
            val length = buffer.int
            parsed.add(Section(String(prefixBytes, Charsets.UTF_8), offset, length))
        }
        sections = parsed
    }

    fun lookup(prefix: String, limit: Int): List<Lexeme> = readBucket(normalize(prefix).take(3), limit)
        .filter { normalize(it.word).startsWith(normalize(prefix)) }
        .take(limit)

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
            for (lexeme in readBucket(key, 80)) {
                val wordKey = normalize(lexeme.word)
                if (wordKey.startsWith(norm) || editDistance(norm, wordKey) <= 2) {
                    val previous = hits[lexeme.word]
                    if (previous == null || lexeme.frequency > previous.frequency) {
                        hits[lexeme.word] = lexeme
                    }
                }
            }
        }
        return hits.values.sortedWith(compareBy<Lexeme> { editDistance(norm, normalize(it.word)) }
            .thenByDescending { it.frequency }).take(limit)
    }

    private fun readBucket(key: String, limit: Int): List<Lexeme> {
        if (key.length < 2 || limit <= 0) return emptyList()
        val section = sections.firstOrNull { it.prefix == key } ?: return emptyList()
        if (section.offset < 0 || section.length <= 0 || section.offset + section.length > bytes.size) {
            return emptyList()
        }
        val compressed = bytes.copyOfRange(section.offset, section.offset + section.length)
        val text = InflaterInputStream(ByteArrayInputStream(compressed)).use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val hits = ArrayList<Lexeme>()
        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val word = line.substring(0, tab)
            val frequency = line.substring(tab + 1).toIntOrNull() ?: 0
            hits.add(Lexeme(word, frequency))
            if (hits.size >= limit) break
        }
        return hits
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

    private data class Section(val prefix: String, val offset: Int, val length: Int)

    companion object {
        const val MAGIC = 0x52554C31

        @JvmStatic
        fun normalize(word: String): String = word.lowercase().replace('ё', 'е')
    }
}
