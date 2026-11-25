package dev.bbkb.ime.core.suggestion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Locale;

import org.junit.Test;

/** The space bar only repairs one obvious slip, and it keeps the case the user typed. */
public class AutoCorrectConfidenceTest {

    @Test
    public void insertsAMissingInteriorLetter() {
        assertEquals("hello", AutoCorrectConfidence.preservingCase("helo", "hello", Locale.ENGLISH));
        assertEquals("tomorrow", AutoCorrectConfidence.preservingCase("tomorow", "tomorrow", Locale.ENGLISH));
        assertEquals("привет", AutoCorrectConfidence.preservingCase("приет", "привет", Locale.forLanguageTag("ru")));
    }

    @Test
    public void dropsOnlyADoubledInteriorLetterAndKeepsCase() {
        assertEquals("airport", AutoCorrectConfidence.preservingCase("airpport", "Airport", Locale.ENGLISH));
        assertEquals("привет", AutoCorrectConfidence.preservingCase("приввет", "Привет", Locale.forLanguageTag("ru")));
    }

    @Test
    public void swapsAdjacentLetters() {
        assertEquals("the", AutoCorrectConfidence.preservingCase("teh", "the", Locale.ENGLISH));
        assertEquals("The", AutoCorrectConfidence.preservingCase("Teh", "The", Locale.ENGLISH));
    }

    @Test
    public void leavesADifferentWordAlone() {
        assertNull(AutoCorrectConfidence.preservingCase("Gradle", "Grade", Locale.ENGLISH));
        assertNull(AutoCorrectConfidence.preservingCase("Kotlin", "Goblin", Locale.ENGLISH));
        assertNull(AutoCorrectConfidence.preservingCase("дедлайн", "делай", Locale.forLanguageTag("ru")));
        assertNull(AutoCorrectConfidence.preservingCase("Петрова", "Петров", Locale.forLanguageTag("ru")));
        assertNull(AutoCorrectConfidence.preservingCase("йота", "кота", Locale.forLanguageTag("ru")));
        assertNull(AutoCorrectConfidence.preservingCase("websocket", "socket", Locale.ENGLISH));
        assertNull(AutoCorrectConfidence.preservingCase("apple", "Apple", Locale.ENGLISH));
        assertNull(AutoCorrectConfidence.preservingCase("OpenCorpora", "OpenCorporate", Locale.ENGLISH));
        assertNull(AutoCorrectConfidence.preservingCase("São-Paulo", "Sao-Paulo", Locale.ENGLISH));
    }

    @Test
    public void latinImeRefusesPhrasesMostlyCapsAndLongJumps() {
        assertNull(AutoCorrectConfidence.preservingCase("helloworld", "hello world", Locale.ENGLISH));
        assertTrue(AutoCorrectConfidence.isMostlyCaps("NASA"));
        assertTrue(AutoCorrectConfidence.isMostlyCaps("OK"));
        assertFalse(AutoCorrectConfidence.isMostlyCaps("Gradle"));
        assertFalse(AutoCorrectConfidence.isMostlyCaps("I"));
        assertTrue(AutoCorrectConfidence.blockedBySafetyNet("Kotlin", "qwerty", Locale.ENGLISH));
        assertFalse(AutoCorrectConfidence.blockedBySafetyNet("helo", "hello", Locale.ENGLISH));
        assertFalse(AutoCorrectConfidence.blockedBySafetyNet("teh", "the", Locale.ENGLISH));
    }
}
