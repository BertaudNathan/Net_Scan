package com.example.MobileM2.Net_Scan.data.signal

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn

/**
 * Mesures réelles du modem, sur la SIM utilisée pour les données (l'opérateur réellement
 * utilisé en double SIM). Les callbacks arrivent sur le thread principal.
 */
class TelephonySignalSource(context: Context) : SignalSource {
    private val context = context.applicationContext

    override val isSimulated = false

    override fun availability(): SignalAvailability {
        val telephony = telephony() ?: return SignalAvailability.NO_TELEPHONY
        if (isAirplaneModeOn()) return SignalAvailability.AIRPLANE_MODE
        if (telephony.simState != TelephonyManager.SIM_STATE_READY) return SignalAvailability.NO_SIM
        val location = context.getSystemService(LocationManager::class.java)
        if (location?.isLocationEnabled != true) return SignalAvailability.LOCATION_DISABLED
        return SignalAvailability.AVAILABLE
    }

    override fun observeOperator(): Flow<OperatorStatus> = callbackFlow {
        val telephony = telephony()
        if (telephony == null) {
            send(OperatorStatus.Unavailable)
            awaitClose()
            return@callbackFlow
        }
        var serviceState: Int? = null
        var signalTechnology = try {
            telephony.signalStrength?.serving()?.technology
        } catch (_: SecurityException) {
            null
        } ?: NetworkTechnology.UNKNOWN
        var displayTechnology = NetworkTechnology.UNKNOWN

        fun publish() {
            val technology = displayTechnology.takeUnless { it == NetworkTechnology.UNKNOWN } ?: signalTechnology
            trySend(operatorStatus(telephony, serviceState, technology))
        }

        // Écoutes séparées : si l'une demande une permission absente, les autres fonctionnent quand même.
        val service = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
            override fun onServiceStateChanged(state: ServiceState) {
                serviceState = state.state
                publish()
            }
        }
        val signal = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(strength: SignalStrength) {
                signalTechnology = strength.serving().technology
                publish()
            }
        }
        // Affiche « 5G » en NSA comme la barre d'état ; demande READ_PHONE_STATE sur certaines versions.
        val display = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                displayTechnology = info.technology()
                publish()
            }
        }
        publish()
        val registered = listOf(service, signal, display).filter { telephony.tryRegister(it) }
        awaitClose { registered.forEach(telephony::unregisterTelephonyCallback) }
    }.distinctUntilChanged().flowOn(Dispatchers.Main)

    override fun observeSignal(): Flow<SignalSample> = callbackFlow {
        val telephony = telephony() ?: throw IllegalStateException("Telephony is not available")
        var strength: SignalStrength? = telephony.signalStrength
        var serviceState: Int? = null
        var cells: List<CellInfo> = emptyList()

        fun publish() {
            strength?.let { trySend(sampleOf(it, serviceState, cells)) }
        }

        val signal = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(value: SignalStrength) {
                strength = value
                publish()
            }
        }
        val service = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
            override fun onServiceStateChanged(state: ServiceState) {
                serviceState = state.state
                publish()
            }
        }
        // Identifiant et bande de la cellule : nécessite ACCESS_FINE_LOCATION.
        val cell = object : TelephonyCallback(), TelephonyCallback.CellInfoListener {
            override fun onCellInfoChanged(value: MutableList<CellInfo>) {
                cells = value
                publish()
            }
        }
        // Sans cette écoute, il n'y a pas de mesure : une SecurityException ici arrête le scan.
        telephony.registerTelephonyCallback(mainExecutor(), signal)
        val optional = listOf(service, cell).filter { telephony.tryRegister(it) }
        cells = try {
            telephony.allCellInfo.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        publish()
        awaitClose { (optional + signal).forEach(telephony::unregisterTelephonyCallback) }
    }.flowOn(Dispatchers.Main)

    private fun telephony(): TelephonyManager? {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY_RADIO_ACCESS)) return null
        val base = context.getSystemService(TelephonyManager::class.java) ?: return null
        val dataSubscription = SubscriptionManager.getDefaultDataSubscriptionId()
        return if (SubscriptionManager.isValidSubscriptionId(dataSubscription)) {
            base.createForSubscriptionId(dataSubscription)
        } else {
            base
        }
    }

    private fun mainExecutor(): Executor = context.mainExecutor

    private fun TelephonyManager.tryRegister(callback: TelephonyCallback): Boolean = try {
        registerTelephonyCallback(mainExecutor(), callback)
        true
    } catch (_: SecurityException) {
        false
    }

    private fun isAirplaneModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0

    private fun operatorStatus(
        telephony: TelephonyManager,
        serviceState: Int?,
        technology: NetworkTechnology,
    ): OperatorStatus {
        val name = telephony.networkOperatorName.trim().ifEmpty { telephony.simOperatorName.trim() }.ifEmpty { null }
        return when {
            isAirplaneModeOn() || serviceState == ServiceState.STATE_POWER_OFF -> OperatorStatus.RadioOff
            telephony.simState != TelephonyManager.SIM_STATE_READY -> OperatorStatus.NoSim
            serviceState == ServiceState.STATE_OUT_OF_SERVICE ||
                serviceState == ServiceState.STATE_EMERGENCY_ONLY -> OperatorStatus.NoService
            serviceState == null && name == null -> OperatorStatus.Unknown
            else -> OperatorStatus.Connected(name, technology)
        }
    }
}

private data class Serving(
    val technology: NetworkTechnology,
    val rsrpDbm: Int? = null,
    val sinrDb: Int? = null,
    val level: Int? = null,
)

internal fun sampleOf(strength: SignalStrength, serviceState: Int?, cells: List<CellInfo>): SignalSample {
    val inService = serviceState?.let { it == ServiceState.STATE_IN_SERVICE } ?: strength.cellSignalStrengths.isNotEmpty()
    if (!inService) return SignalSample(inService = false)
    val serving = strength.serving()
    val registered = cells.filter { it.isRegistered }
    val cell = registered.firstOrNull { it.technology() == serving.technology } ?: registered.firstOrNull()
    return SignalSample(
        inService = true,
        technology = serving.technology,
        rsrpDbm = serving.rsrpDbm,
        sinrDb = serving.sinrDb,
        systemLevel = serving.level,
        band = cell?.band(),
        cellId = cell?.cellId(),
    )
}

/** En 5G NSA, la 5G n'est retenue que si le modem fournit vraiment son RSRP ; sinon l'ancre 4G. */
private fun SignalStrength.serving(): Serving {
    val all = cellSignalStrengths
    all.filterIsInstance<CellSignalStrengthNr>().firstOrNull { it.ssRsrp.isAvailable() }?.let {
        return Serving(NetworkTechnology.NR, it.ssRsrp, it.ssSinr.orNull(), it.level)
    }
    all.filterIsInstance<CellSignalStrengthLte>().firstOrNull { it.rsrp.isAvailable() }?.let {
        return Serving(NetworkTechnology.LTE, it.rsrp, it.rssnr.orNull(), it.level)
    }
    val other: CellSignalStrength = all.firstOrNull() ?: return Serving(NetworkTechnology.UNKNOWN)
    return Serving(other.technology(), level = other.level)
}

private fun CellSignalStrength.technology() = when (this) {
    is CellSignalStrengthNr -> NetworkTechnology.NR
    is CellSignalStrengthLte -> NetworkTechnology.LTE
    is CellSignalStrengthWcdma, is CellSignalStrengthTdscdma -> NetworkTechnology.UMTS
    is CellSignalStrengthGsm -> NetworkTechnology.GSM
    else -> NetworkTechnology.UNKNOWN
}

private fun CellInfo.technology() = when (this) {
    is CellInfoNr -> NetworkTechnology.NR
    is CellInfoLte -> NetworkTechnology.LTE
    is CellInfoWcdma, is CellInfoTdscdma -> NetworkTechnology.UMTS
    is CellInfoGsm -> NetworkTechnology.GSM
    else -> NetworkTechnology.UNKNOWN
}

private fun CellInfo.cellId(): Long? = when (this) {
    is CellInfoNr -> (cellIdentity as CellIdentityNr).nci.takeIf { it != CellInfo.UNAVAILABLE_LONG }
    is CellInfoLte -> cellIdentity.ci.orNull()?.toLong()
    is CellInfoWcdma -> cellIdentity.cid.orNull()?.toLong()
    is CellInfoTdscdma -> cellIdentity.cid.orNull()?.toLong()
    is CellInfoGsm -> cellIdentity.cid.orNull()?.toLong()
    else -> null
}

private fun CellInfo.band(): Int? = when (this) {
    is CellInfoNr -> (cellIdentity as CellIdentityNr).bands.firstOrNull()
    is CellInfoLte -> cellIdentity.bands.firstOrNull()
    else -> null
}

private fun TelephonyDisplayInfo.technology(): NetworkTechnology = when (overrideNetworkType) {
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA,
    TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> NetworkTechnology.NR
    else -> when (networkType) {
        TelephonyManager.NETWORK_TYPE_NR -> NetworkTechnology.NR
        TelephonyManager.NETWORK_TYPE_LTE -> NetworkTechnology.LTE
        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> NetworkTechnology.UMTS
        TelephonyManager.NETWORK_TYPE_GSM, TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE -> NetworkTechnology.GSM
        else -> NetworkTechnology.UNKNOWN
    }
}

private fun Int.isAvailable() = this != CellInfo.UNAVAILABLE
private fun Int.orNull() = takeIf { it.isAvailable() }
