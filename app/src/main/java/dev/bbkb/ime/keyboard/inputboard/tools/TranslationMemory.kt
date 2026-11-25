package dev.bbkb.ime.keyboard.inputboard.tools

/**
 * A translation pair stays itself. The first model answer is kept, and the way back is that
 * original text, so translating back and forth does not walk the meaning into a different sentence.
 * Swap is the same idea: the two sides change places and the words do not go through the model again.
 */
class TranslationMemory(private val capacity: Int = 40) {
    private data class Pairing(val translation: String)

    private val pairs = object : LinkedHashMap<String, Pairing>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pairing>?) = size > capacity
    }

    fun recall(from: String, to: String, text: String): String? {
        val key = key(from, to, text) ?: return null
        return pairs[key]?.translation
    }

    fun remember(from: String, to: String, source: String, translation: String) {
        if (from.isEmpty() || to.isEmpty() || from == to) return
        val cleanSource = source.trim()
        val cleanTranslation = translation.trim()
        if (cleanSource.isEmpty() || cleanTranslation.isEmpty()) return
        put(from, to, cleanSource, cleanTranslation)
        put(to, from, cleanTranslation, cleanSource)
    }

    fun exchange(from: String, to: String, source: String, translation: String): Exchanged {
        remember(from, to, source, translation)
        return Exchanged(
            from = to,
            to = from,
            source = translation.trim(),
            translation = source.trim(),
        )
    }

    data class Exchanged(
        val from: String,
        val to: String,
        val source: String,
        val translation: String,
    )

    private fun put(from: String, to: String, source: String, translation: String) {
        val key = key(from, to, source) ?: return
        pairs[key] = Pairing(translation)
    }

    private fun key(from: String, to: String, text: String): String? {
        val normalized = text.trim().replace(WHITESPACE, " ")
        if (normalized.isEmpty()) return null
        return "$from\u0000$to\u0000$normalized"
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
