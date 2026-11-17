package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalLevelTest {
    @Test fun noServiceIsAResultNotAnError() {
        assertEquals(SignalLevel.NONE, classifySignal(inService = false, rsrpDbm = -70.0, sinrDb = 20.0))
    }

    @Test fun rsrpThresholdsFollowTheDocumentedTable() {
        assertEquals(SignalLevel.HIGH, classifySignal(true, rsrpDbm = -79.0, sinrDb = null))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, rsrpDbm = -80.0, sinrDb = null))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, rsrpDbm = -100.0, sinrDb = null))
        assertEquals(SignalLevel.LOW, classifySignal(true, rsrpDbm = -101.0, sinrDb = null))
    }

    @Test fun sinrThresholdsFollowTheDocumentedTable() {
        assertEquals(SignalLevel.HIGH, classifySignal(true, rsrpDbm = null, sinrDb = 14.0))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, rsrpDbm = null, sinrDb = 13.0))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, rsrpDbm = null, sinrDb = 0.0))
        assertEquals(SignalLevel.LOW, classifySignal(true, rsrpDbm = null, sinrDb = -1.0))
    }

    @Test fun keepsTheWorstOfBothIndicators() {
        assertEquals(SignalLevel.LOW, classifySignal(true, rsrpDbm = -70.0, sinrDb = -3.0))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, rsrpDbm = -90.0, sinrDb = 20.0))
    }

    @Test fun fallsBackToTheSystemLevelWhenRadioValuesAreUnavailable() {
        assertEquals(SignalLevel.LOW, classifySignal(true, null, null, systemLevel = 1))
        assertEquals(SignalLevel.MEDIUM, classifySignal(true, null, null, systemLevel = 2))
        assertEquals(SignalLevel.HIGH, classifySignal(true, null, null, systemLevel = 4))
    }

    @Test fun cannotClassifyWithoutAnyValue() {
        assertNull(classifySignal(true, rsrpDbm = null, sinrDb = null, systemLevel = null))
    }
}
