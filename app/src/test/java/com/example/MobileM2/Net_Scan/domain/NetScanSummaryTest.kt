package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetScanSummaryTest {
    private fun lte(rsrp: Int?, sinr: Int? = null, cellId: Long? = 1, band: Int? = 20) = SignalSample(
        inService = true,
        technology = NetworkTechnology.LTE,
        rsrpDbm = rsrp,
        sinrDb = sinr,
        cellId = cellId,
        band = band,
    )

    @Test fun nothingToSummarizeWithoutSamples() {
        assertNull(summarize(emptyList()))
    }

    @Test fun usesTheMedianSoOutliersDoNotChangeTheResult() {
        val summary = summarize(listOf(lte(-95), lte(-60), lte(-97), lte(-96), lte(-130)))!!
        assertEquals(-96.0, summary.rsrpDbm!!, 0.0)
        assertEquals(SignalLevel.MEDIUM, summary.level)
        assertEquals(5, summary.sampleCount)
    }

    @Test fun medianOfAnEvenCountIsTheMiddleAverage() {
        val summary = summarize(listOf(lte(-90, 4), lte(-100, 8)))!!
        assertEquals(-95.0, summary.rsrpDbm!!, 0.0)
        assertEquals(6.0, summary.sinrDb!!, 0.0)
    }

    @Test fun unavailableSinrDegradesGracefully() {
        val summary = summarize(listOf(lte(-75), lte(-76)))!!
        assertNull(summary.sinrDb)
        assertEquals(SignalLevel.HIGH, summary.level)
    }

    @Test fun mostlyOutOfServiceMeansNoService() {
        val samples = listOf(SignalSample(inService = false), SignalSample(inService = false), lte(-70))
        val summary = summarize(samples)!!
        assertEquals(SignalLevel.NONE, summary.level)
        assertNull(summary.rsrpDbm)
    }

    @Test fun keepsTheMostFrequentTechnologyAndTheLatestIdentifiedCell() {
        val nr = SignalSample(inService = true, technology = NetworkTechnology.NR, rsrpDbm = -85, cellId = 9)
        val unidentified = lte(-92, cellId = null, band = null)
        val summary = summarize(listOf(lte(-90, cellId = 1), lte(-91, cellId = 2), nr, unidentified))!!
        assertEquals(NetworkTechnology.LTE, summary.technology)
        assertEquals(9L, summary.cellId)
        assertNull("Band and cell come from the same reading", summary.band)
    }

    @Test fun inServiceWithoutAnyValueCannotBeSummarized() {
        assertNull(summarize(listOf(SignalSample(inService = true))))
    }
}
