package com.example.MobileM2.Net_Scan.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidthClassTest {
    @Test fun followsTheMaterialBreakpoints() {
        assertEquals(WidthClass.COMPACT, WidthClass.of(411))
        assertEquals(WidthClass.COMPACT, WidthClass.of(599))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(600))
        assertEquals(WidthClass.MEDIUM, WidthClass.of(839))
        assertEquals(WidthClass.EXPANDED, WidthClass.of(840))
        assertEquals(WidthClass.EXPANDED, WidthClass.of(1280))
    }

    @Test fun onlyAPhoneInPortraitIsNarrow() {
        assertFalse(WidthClass.COMPACT.isWide)
        assertTrue(WidthClass.MEDIUM.isWide)
        assertTrue(WidthClass.EXPANDED.isWide)
    }
}
