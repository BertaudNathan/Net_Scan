package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import com.example.MobileM2.Net_Scan.domain.summarize
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VisitState {
    data object Loading : VisitState
    data object Missing : VisitState
    data object Error : VisitState
    data class Loaded(val visit: Visit) : VisitState
}

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val elapsedSeconds: Int) : RecordingState
}

sealed interface ScanHistoryState {
    data object Loading : ScanHistoryState
    data object Empty : ScanHistoryState
    data object Error : ScanHistoryState
    data class Success(val scans: List<NetScan>) : ScanHistoryState
}

enum class NetScanProblem {
    PERMISSION_DENIED, NO_TELEPHONY, NO_SIM, AIRPLANE_MODE, LOCATION_DISABLED, NO_DATA, SIGNAL_ERROR
}

enum class SaveState { SAVING, SAVED, FAILED }

sealed interface NetScanState {
    data object Idle : NetScanState

    data class Running(
        val mode: NetScanMode,
        val elapsedSeconds: Int,
        /** Médiane des lectures depuis le début : stable, et identique au résultat final. */
        val summary: NetScanSummary?,
        val latest: SignalSample?,
    ) : NetScanState {
        val remainingSeconds: Int? get() = (mode as? NetScanMode.Timer)?.let { maxOf(0, it.seconds - elapsedSeconds) }
        val canFinish: Boolean get() = mode is NetScanMode.Free && elapsedSeconds >= NetScanMode.MIN_DURATION_SECONDS
    }

    data class Completed(val result: NetScan, val saveState: SaveState) : NetScanState
    data class Unavailable(val problem: NetScanProblem) : NetScanState
}

/**
 * Écran d'une visite : aperçu caméra, carte (à venir), scans réseau ponctuels et enregistrement
 * du parcours. L'enregistrement et les scans réseau sont indépendants.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapScanViewModel(
    private val visitId: Long,
    visitRepository: VisitRepository,
    private val netScanRepository: NetScanRepository,
    private val signalSource: SignalSource,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val logger = Logger.getLogger(MapScanViewModel::class.java.name)
    private val operatorRefresh = MutableStateFlow(0)
    private val historyRefresh = MutableStateFlow(0)
    private val mutableMode = MutableStateFlow(NetScanMode.DEFAULT)
    private val mutableNetScan = MutableStateFlow<NetScanState>(NetScanState.Idle)
    private val mutableRecording = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val samples = mutableListOf<SignalSample>()
    private var scanStartedAt = 0L
    private var scanJob: Job? = null
    private var recordingJob: Job? = null

    val isSimulated: Boolean = signalSource.isSimulated
    val mode: StateFlow<NetScanMode> = mutableMode.asStateFlow()
    val netScan: StateFlow<NetScanState> = mutableNetScan.asStateFlow()
    val recording: StateFlow<RecordingState> = mutableRecording.asStateFlow()

    val history: StateFlow<ScanHistoryState> = historyRefresh
        .flatMapLatest {
            netScanRepository.observeForVisit(visitId)
                .map<List<NetScan>, ScanHistoryState> { scans ->
                    if (scans.isEmpty()) ScanHistoryState.Empty else ScanHistoryState.Success(scans)
                }
                .onStart { emit(ScanHistoryState.Loading) }
                .catch { error ->
                    logger.log(Level.WARNING, "Unable to load scan history", error)
                    emit(ScanHistoryState.Error)
                }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ScanHistoryState.Loading)

    fun retryHistory() { historyRefresh.value++ }

    val visit: StateFlow<VisitState> = visitRepository.observeVisit(visitId)
        .map<Visit?, VisitState> { if (it == null) VisitState.Missing else VisitState.Loaded(it) }
        .catch { error ->
            logger.log(Level.WARNING, "Unable to load visit", error)
            emit(VisitState.Error)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, VisitState.Loading)

    val operator: StateFlow<OperatorStatus> = operatorRefresh
        .flatMapLatest { signalSource.observeOperator() }
        .catch { error ->
            logger.log(Level.WARNING, "Unable to read operator", error)
            emit(OperatorStatus.Unavailable)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, OperatorStatus.Unknown)

    fun hasActivityInProgress(): Boolean =
        mutableRecording.value is RecordingState.Recording || mutableNetScan.value is NetScanState.Running

    fun selectMode(mode: NetScanMode) {
        if (mutableNetScan.value !is NetScanState.Running) mutableMode.value = mode
    }

    fun onPermissionsDenied() {
        if (mutableNetScan.value !is NetScanState.Running) {
            mutableNetScan.value = NetScanState.Unavailable(NetScanProblem.PERMISSION_DENIED)
        }
    }

    /** À appeler une fois les permissions accordées. */
    fun startNetScan() {
        if (mutableNetScan.value is NetScanState.Running) return
        signalSource.availability().toProblem()?.let {
            mutableNetScan.value = NetScanState.Unavailable(it)
            return
        }
        // Les permissions viennent peut-être d'être accordées : l'en-tête peut lire plus de détails.
        operatorRefresh.value++
        val mode = mutableMode.value
        samples.clear()
        scanStartedAt = clock()
        mutableNetScan.value = NetScanState.Running(mode, elapsedSeconds = 0, summary = null, latest = null)
        scanJob = viewModelScope.launch {
            var latest: SignalSample? = null
            val reader = signalSource.observeSignal()
                .onEach { latest = it }
                .catch { error ->
                    logger.log(Level.WARNING, "Signal reading failed", error)
                    stopWith(if (error is SecurityException) NetScanProblem.PERMISSION_DENIED else NetScanProblem.SIGNAL_ERROR)
                }
                .launchIn(this)
            var elapsed = 0
            // Une lecture par seconde : la médiane pondère chaque valeur par sa durée.
            while (true) {
                delay(1_000)
                elapsed++
                latest?.let(samples::add)
                mutableNetScan.value = NetScanState.Running(mode, elapsed, summarize(samples), latest)
                if (mode is NetScanMode.Timer && elapsed >= mode.seconds) break
            }
            reader.cancel()
            complete(mode, elapsed)
        }
    }

    fun finishNetScan() {
        val running = mutableNetScan.value as? NetScanState.Running ?: return
        if (!running.canFinish) return
        scanJob?.cancel()
        scanJob = null
        complete(running.mode, running.elapsedSeconds)
    }

    fun cancelNetScan() {
        if (mutableNetScan.value !is NetScanState.Running) return
        scanJob?.cancel()
        scanJob = null
        samples.clear()
        mutableNetScan.value = NetScanState.Idle
    }

    fun dismissNetScan() {
        when (val state = mutableNetScan.value) {
            is NetScanState.Running -> return
            is NetScanState.Completed -> if (state.saveState == SaveState.SAVING) return
            else -> Unit
        }
        mutableNetScan.value = NetScanState.Idle
    }

    fun retrySave() {
        val state = mutableNetScan.value as? NetScanState.Completed ?: return
        if (state.saveState == SaveState.FAILED) save(state.result)
    }

    fun toggleRecording() {
        recordingJob?.let {
            it.cancel()
            recordingJob = null
            mutableRecording.value = RecordingState.Idle
            return
        }
        mutableRecording.value = RecordingState.Recording(0)
        recordingJob = viewModelScope.launch {
            var elapsed = 0
            while (true) {
                delay(1_000)
                elapsed++
                mutableRecording.value = RecordingState.Recording(elapsed)
            }
        }
    }

    private fun stopWith(problem: NetScanProblem) {
        scanJob?.cancel()
        scanJob = null
        samples.clear()
        mutableNetScan.value = NetScanState.Unavailable(problem)
    }

    private fun complete(mode: NetScanMode, elapsedSeconds: Int) {
        val summary = summarize(samples.toList())
        samples.clear()
        if (summary == null) {
            mutableNetScan.value = NetScanState.Unavailable(NetScanProblem.NO_DATA)
            return
        }
        val operatorName = (operator.value as? OperatorStatus.Connected)?.name
        save(NetScan(visitId, scanStartedAt, elapsedSeconds, mode, operatorName, summary))
    }

    private fun save(result: NetScan) {
        mutableNetScan.value = NetScanState.Completed(result, SaveState.SAVING)
        viewModelScope.launch {
            val outcome = try {
                netScanRepository.save(result)
                SaveState.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to save net scan", error)
                SaveState.FAILED
            }
            val current = mutableNetScan.value
            if (current is NetScanState.Completed && current.result == result) {
                mutableNetScan.value = current.copy(saveState = outcome)
            }
        }
    }

    private fun SignalAvailability.toProblem(): NetScanProblem? = when (this) {
        SignalAvailability.AVAILABLE -> null
        SignalAvailability.NO_TELEPHONY -> NetScanProblem.NO_TELEPHONY
        SignalAvailability.NO_SIM -> NetScanProblem.NO_SIM
        SignalAvailability.AIRPLANE_MODE -> NetScanProblem.AIRPLANE_MODE
        SignalAvailability.LOCATION_DISABLED -> NetScanProblem.LOCATION_DISABLED
    }
}
