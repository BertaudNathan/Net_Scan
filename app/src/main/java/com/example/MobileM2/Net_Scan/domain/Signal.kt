package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

/** Technologie radio de la cellule servante. */
enum class NetworkTechnology { GSM, UMTS, LTE, NR, UNKNOWN }

/**
 * Lecture radio instantanée. Une valeur absente vaut null : en 5G NSA notamment,
 * le modem ne fournit souvent ni SINR ni RSRP 5G.
 */
data class SignalSample(
    val inService: Boolean,
    val technology: NetworkTechnology = NetworkTechnology.UNKNOWN,
    val rsrpDbm: Int? = null,
    val sinrDb: Int? = null,
    /** Niveau 0 à 4 calculé par Android, utilisé seulement quand RSRP et SINR manquent (2G, 3G). */
    val systemLevel: Int? = null,
    val band: Int? = null,
    val cellId: Long? = null,
)

/** Les quatre niveaux montrés au commercial, du pire au meilleur. */
enum class SignalLevel { NONE, LOW, MEDIUM, HIGH }

/** Seuils documentés dans la synthèse du projet. Les bornes appartiennent au niveau « moyen ». */
object SignalThresholds {
    const val RSRP_HIGH_ABOVE_DBM = -80.0
    const val RSRP_LOW_BELOW_DBM = -100.0
    const val SINR_HIGH_ABOVE_DB = 13.0
    const val SINR_LOW_BELOW_DB = 0.0
}

/**
 * Classe une mesure en retenant le pire des deux indicateurs.
 * Retourne null quand le téléphone est en service mais qu'aucune valeur n'est exploitable.
 */
fun classifySignal(
    inService: Boolean,
    rsrpDbm: Double?,
    sinrDb: Double?,
    systemLevel: Int? = null,
): SignalLevel? {
    if (!inService) return SignalLevel.NONE
    val levels = listOfNotNull(
        rsrpDbm?.let { level(it, SignalThresholds.RSRP_HIGH_ABOVE_DBM, SignalThresholds.RSRP_LOW_BELOW_DBM) },
        sinrDb?.let { level(it, SignalThresholds.SINR_HIGH_ABOVE_DB, SignalThresholds.SINR_LOW_BELOW_DB) },
    )
    if (levels.isNotEmpty()) return levels.min()
    return when (systemLevel) {
        null -> null
        in Int.MIN_VALUE..1 -> SignalLevel.LOW
        2 -> SignalLevel.MEDIUM
        else -> SignalLevel.HIGH
    }
}

private fun level(value: Double, highAbove: Double, lowBelow: Double) = when {
    value > highAbove -> SignalLevel.HIGH
    value < lowBelow -> SignalLevel.LOW
    else -> SignalLevel.MEDIUM
}

/** Opérateur affiché en en-tête : celui de la SIM données, sur lequel le téléphone est enregistré. */
sealed interface OperatorStatus {
    data object Unknown : OperatorStatus
    data class Connected(val name: String?, val technology: NetworkTechnology) : OperatorStatus
    data object NoService : OperatorStatus
    data object NoSim : OperatorStatus
    data object RadioOff : OperatorStatus
    data object Unavailable : OperatorStatus
}

/** Conditions système nécessaires à un scan réseau, hors permissions. */
enum class SignalAvailability { AVAILABLE, NO_TELEPHONY, NO_SIM, AIRPLANE_MODE, LOCATION_DISABLED }

/**
 * Source des mesures radio. L'implémentation réelle lit le modem ; la simulée sert aux tests
 * et doit être signalée à l'écran via [isSimulated].
 */
interface SignalSource {
    val isSimulated: Boolean
    fun observeOperator(): Flow<OperatorStatus>
    fun availability(): SignalAvailability

    /** Émet à chaque mise à jour du modem. Nécessite ACCESS_FINE_LOCATION et READ_PHONE_STATE. */
    fun observeSignal(): Flow<SignalSample>
}
