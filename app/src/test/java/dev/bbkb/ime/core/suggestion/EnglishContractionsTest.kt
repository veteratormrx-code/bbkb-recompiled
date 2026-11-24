package dev.bbkb.ime.core.suggestion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EnglishContractionsTest {

    @Test
    fun englishMacrosExpandCommonContractions() {
        val file = File("src/main/assets/substitution_macros/en/macros.json")
        val text = file.readText()
        assertTrue(text.contains(". dont don't"))
        assertTrue(text.contains(". im I'm"))
        assertTrue(text.contains(". yall y'all"))
    }
}
