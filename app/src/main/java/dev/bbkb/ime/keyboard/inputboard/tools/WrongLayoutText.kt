package dev.bbkb.ime.keyboard.inputboard.tools

import dev.bbkb.ime.core.layout.LetterLayoutRegistry

internal object WrongLayoutText {
    fun convert(registry: LetterLayoutRegistry, text: String): String? {
        val english = registry.builtIn("en") ?: return null
        val russian = registry.builtIn("ru") ?: return null
        val forward = english.keys.mapNotNull { (code, key) ->
            val other = russian.keys[code]?.keySpec ?: return@mapNotNull null
            if (key.keySpec !in 'a'.code..'z'.code || !Character.isLetter(other)) return@mapNotNull null
            key.keySpec.toChar() to other.toChar()
        }.toMap()
        val reverse = forward.entries.groupBy({ it.value }, { it.key })
            .mapNotNull { (key, values) -> values.singleOrNull()?.let { key to it } }.toMap()
        val token = text.trim()
        if (token.length < 2) return null
        val lowered = token.lowercase()
        val mapping = when {
            lowered.all(forward::containsKey) -> forward
            lowered.all(reverse::containsKey) -> reverse
            else -> return null
        }
        val replacement = token.map { character ->
            val mapped = mapping.getValue(character.lowercaseChar())
            if (character.isUpperCase()) mapped.uppercaseChar() else mapped
        }.joinToString("")
        return text.replaceRange(text.indexOf(token), text.indexOf(token) + token.length, replacement)
    }
}
