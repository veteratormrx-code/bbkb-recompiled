package dev.bbkb.ime.modernlexicon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater

class BbdLexiconTest {
    @Test
    fun lookupFindsAPrefixAndSurvivesAMissingBucket() {
        val lexicon = BbdLexicon(sample("при" to "привет\t10\n"))
        assertEquals("привет", lexicon.lookup("при", 5).single().word)
        assertTrue(lexicon.lookup("bb", 5).isEmpty())
    }

    @Test
    fun yoFoldsToE() {
        assertEquals("елка", BbdLexicon.normalize("Ёлка"))
    }

    @Test
    fun matchingPrefixBeyondTheCandidateWindowIsStillFound() {
        val prefix = "при"
        val body = (1..100).joinToString("") { "приставка$it\t1\n" } + "привет\t10\n"
        val lexicon = BbdLexicon(sample(prefix to body))
        assertEquals("привет", lexicon.lookup("ПРИВ", 1).single().word)
        assertEquals("привет", lexicon.lookupWithPrefixTypos("прив", 1, emptyMap()).single().word)
    }

    private fun sample(vararg buckets: Pair<String, String>): ByteArray {
        val sections = buckets.map { (prefix, body) ->
            val deflater = Deflater(7)
            deflater.setInput(body.toByteArray())
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(256)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                out.write(buf, 0, n)
            }
            deflater.end()
            prefix.toByteArray() to out.toByteArray()
        }
        var header = 8
        sections.forEach { header += 2 + it.first.size + 8 }
        val file = ByteArrayOutputStream()
        val head = ByteBuffer.allocate(header)
        head.putInt(BbdLexicon.MAGIC)
        head.putInt(sections.size)
        var offset = header
        for ((prefix, data) in sections) {
            head.putShort(prefix.size.toShort())
            head.put(prefix)
            head.putInt(offset)
            head.putInt(data.size)
            offset += data.size
        }
        file.write(head.array())
        sections.forEach { file.write(it.second) }
        return file.toByteArray()
    }
}
