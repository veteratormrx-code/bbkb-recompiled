package dev.bbkb.ime.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolShiftGateTest {

    @Test
    fun aPhysicalSymbolKeyboardWithoutAnOnScreenKeyStillOpensTheOverlay() {
        assertFalse(KeyboardSwitcher.refusesSymbolShift(false, false, true, false))
        assertTrue(KeyboardSwitcher.refusesSymbolShift(false, false, false, false))
        assertTrue(KeyboardSwitcher.refusesSymbolShift(false, true, true, true))
        assertFalse(KeyboardSwitcher.refusesSymbolShift(true, false, false, false))
    }
}
