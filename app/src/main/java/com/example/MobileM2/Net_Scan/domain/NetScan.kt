package com.example.MobileM2.Net_Scan.domain

/** Durée d'un scan réseau : minuteur imposé ou chrono libre arrêté par l'utilisateur. */
sealed interface NetScanMode {
    data class Timer(val seconds: Int) : NetScanMode
    data object Free : NetScanMode

    companion object {
        /** Le modem ne se met à jour que toutes les quelques secondes : en dessous, la médiane n'est pas fiable. */
        const val MIN_DURATION_SECONDS = 20
        val TIMER_CHOICES = listOf(20, 30, 60)
        val DEFAULT: NetScanMode = Timer(30)
    }
}

/** Synthèse d'un scan réseau : médianes sur toute la durée et niveau qui en découle. */
data class NetScanSummary(
    val level: SignalLevel,
    val technology: NetworkTechnology,
    val rsrpDbm: Double?,
    val sinrDb: Double?,
    val sampleCount: Int,
    val band: Int?,
    val cellId: Long?,
)

/**
 * Scan réseau terminé, rattaché à une visite. En mode ARCore, [position] le place sur la carte de
 * l'enregistrement [recordingId] ; elle est absente si le suivi n'a donné aucune position pendant le
 * scan. En mode manuel, [planPosition] est l'endroit du plan où la personne s'est placée.
 */
data class NetScan(
    val visitId: Long,
    val startedAt: Long,
    val durationSeconds: Int,
    val mode: NetScanMode,
    val operatorName: String?,
    val summary: NetScanSummary,
    val recordingId: Long? = null,
    val position: FloorPoint? = null,
    val planPosition: PlanPoint? = null,
)

interface NetScanRepository {
    suspend fun save(scan: NetScan)
}

/**
 * Résume des lectures prises à intervalle régulier. Si le téléphone est hors service la moitié
 * du temps ou plus, le résultat est « inexistant ». Retourne null s'il n'y a rien d'exploitable.
 */
fun summarize(samples: List<SignalSample>): NetScanSummary? {
    if (samples.isEmpty()) return null
    val inService = samples.filter { it.inService }
    if (inService.size * 2 <= samples.size) {
        return NetScanSummary(SignalLevel.NONE, NetworkTechnology.UNKNOWN, null, null, samples.size, null, null)
    }
    val rsrp = median(inService.mapNotNull { it.rsrpDbm })
    val sinr = median(inService.mapNotNull { it.sinrDb })
    val systemLevel = median(inService.mapNotNull { it.systemLevel })?.toInt()
    val level = classifySignal(true, rsrp, sinr, systemLevel) ?: return null
    val identified = inService.lastOrNull { it.cellId != null }
    return NetScanSummary(
        level = level,
        technology = mostFrequentTechnology(inService),
        rsrpDbm = rsrp,
        sinrDb = sinr,
        sampleCount = samples.size,
        band = identified?.band,
        cellId = identified?.cellId,
    )
}

private fun median(values: List<Int>): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle].toDouble() else (sorted[middle - 1] + sorted[middle]) / 2.0
}

private fun mostFrequentTechnology(samples: List<SignalSample>): NetworkTechnology {
    val counts = samples.groupingBy { it.technology }.eachCount()
    val best = counts.values.max()
    // En cas d'égalité, la technologie la plus récente l'emporte.
    return samples.last { counts.getValue(it.technology) == best }.technology
}
