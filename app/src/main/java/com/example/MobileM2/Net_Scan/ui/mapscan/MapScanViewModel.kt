package com.example.MobileM2.Net_Scan.ui.mapscan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.FloorMap
import com.example.MobileM2.Net_Scan.domain.FloorPose
import com.example.MobileM2.Net_Scan.domain.LiveMapping
import com.example.MobileM2.Net_Scan.domain.MapMeasurement
import com.example.MobileM2.Net_Scan.domain.MapRules
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.OccupancyMap
import com.example.MobileM2.Net_Scan.domain.OperatorStatus
import com.example.MobileM2.Net_Scan.domain.PositionSource
import com.example.MobileM2.Net_Scan.domain.PositionState
import com.example.MobileM2.Net_Scan.domain.PositioningUnavailable
import com.example.MobileM2.Net_Scan.domain.RecordingRepository
import com.example.MobileM2.Net_Scan.domain.SavedRecording
import com.example.MobileM2.Net_Scan.domain.ScanSample
import com.example.MobileM2.Net_Scan.domain.SignalAvailability
import com.example.MobileM2.Net_Scan.domain.SignalSample
import com.example.MobileM2.Net_Scan.domain.SignalSource
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import com.example.MobileM2.Net_Scan.domain.medianPoint
import com.example.MobileM2.Net_Scan.domain.summarize
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface VisitState {
    data object Loading : VisitState
    data object Missing : VisitState
    data object Error : VisitState
    data class Loaded(val visit: Visit) : VisitState
}

sealed interface RecordingState {
    data object Idle : RecordingState

    /**
     * [saveFailed] : la dernière écriture du parcours a échoué ; elle sera retentée.
     * [realigning] : après une perte de suivi, la nouvelle portion attend d'être recalée sur la carte.
     */
    data class Recording(
        val elapsedSeconds: Int,
        val saveFailed: Boolean = false,
        val realigning: Boolean = false,
    ) : RecordingState
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

    /** [movedDuringScan] : le téléphone s'est déplacé pendant la mesure, sa position est approximative. */
    data class Completed(val result: NetScan, val saveState: SaveState, val movedDuringScan: Boolean = false) : NetScanState
    data class Unavailable(val problem: NetScanProblem) : NetScanState
}

sealed interface MapState {
    data object Loading : MapState

    /** Aucun parcours enregistré pour cette visite. */
    data object Empty : MapState

    data object Error : MapState

    /**
     * [live] : enregistrement en cours, avec la position actuelle [pose] quand le suivi la donne.
     * Sinon, dernier parcours sauvegardé, commencé à [recordedAt].
     */
    data class Ready(val map: FloorMap, val live: Boolean, val pose: FloorPose?, val recordedAt: Long?) : MapState
}

/**
 * Écran d'une visite : aperçu caméra, carte du parcours avec heatmap, scans réseau ponctuels et
 * enregistrement du parcours. Un enregistrement suit la trajectoire ARCore ; chaque scan réseau
 * fait pendant un enregistrement est placé sur sa carte. Lancer un scan démarre l'enregistrement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapScanViewModel(
    private val visitId: Long,
    visitRepository: VisitRepository,
    private val netScanRepository: NetScanRepository,
    private val recordingRepository: RecordingRepository,
    private val signalSource: SignalSource,
    private val positionSource: PositionSource,
    private val clock: () -> Long = System::currentTimeMillis,
    private val mapDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val logger = Logger.getLogger(MapScanViewModel::class.java.name)
    private val operatorRefresh = MutableStateFlow(0)
    private val mutableMode = MutableStateFlow(NetScanMode.DEFAULT)
    private val mutableNetScan = MutableStateFlow<NetScanState>(NetScanState.Idle)
    private val mutableRecording = MutableStateFlow<RecordingState>(RecordingState.Idle)
    private val liveMap = MutableStateFlow(FloorMap.build(emptyList(), emptyList()))
    private val samples = mutableListOf<SignalSample>()
    private val scanSamples = mutableListOf<ScanSample>()
    /** Position corrigée (recalée) pendant l'enregistrement, null hors suivi. */
    private val correctedPose = MutableStateFlow<FloorPose?>(null)
    private var scanStartedAt = 0L
    private var scanJob: Job? = null
    private var active: ActiveRecording? = null
    /** Enregistrement en cours au lancement du scan réseau, pour placer son résultat. */
    private var scanRecording: ActiveRecording? = null

    val isSimulated: Boolean = signalSource.isSimulated || positionSource.isSimulated
    val mode: StateFlow<NetScanMode> = mutableMode.asStateFlow()
    val netScan: StateFlow<NetScanState> = mutableNetScan.asStateFlow()
    val recording: StateFlow<RecordingState> = mutableRecording.asStateFlow()

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

    val position: StateFlow<PositionState> = positionSource.position
        .catch { error ->
            logger.log(Level.WARNING, "Positioning failed", error)
            emit(PositionState.Unavailable(PositioningUnavailable.ERROR))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PositionState.Starting)

    /** Carte en direct pendant un enregistrement, sinon dernier parcours sauvegardé. */
    val map: StateFlow<MapState> = mutableRecording
        .map { it is RecordingState.Recording }
        .distinctUntilChanged()
        .flatMapLatest { recording -> if (recording) liveMapState() else savedMapState() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapState.Loading)

    init {
        viewModelScope.launch { position.collect(::onPosition) }
    }

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
        // Une mesure n'a de sens sur la carte que pendant un enregistrement.
        if (active == null && position.value !is PositionState.Unavailable) startRecording()
        scanRecording = active
        val mode = mutableMode.value
        samples.clear()
        scanSamples.clear()
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
                val tracked = position.value as? PositionState.Tracking
                if (tracked != null) scanRecording?.let { scanSamples += it.mapping.sample(tracked.pose.point) }
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
        scanSamples.clear()
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
        if (state.saveState == SaveState.FAILED) save(state.result, state.movedDuringScan)
    }

    fun toggleRecording() {
        if (active != null) stopRecording() else startRecording()
    }

    /** Avant de quitter l'écran : abandonne le scan en cours et sauvegarde la fin du parcours. */
    fun leave() {
        cancelNetScan()
        stopRecording()
    }

    private fun startRecording() {
        if (active != null) return
        val recording = ActiveRecording(startedAt = clock())
        active = recording
        correctedPose.value = (position.value as? PositionState.Tracking)?.let { recording.mapping.onPose(it.pose) }
        liveMap.value = FloorMap.build(emptyList(), emptyList())
        mutableRecording.value = RecordingState.Recording(0)
        recording.job = viewModelScope.launch {
            launch {
                var elapsed = 0
                while (true) {
                    delay(1_000)
                    elapsed++
                    mutableRecording.update { (it as? RecordingState.Recording)?.copy(elapsedSeconds = elapsed) ?: it }
                }
            }
            // Ce que filme la caméra dessine la pièce et ses murs.
            launch {
                positionSource.observations.collect { observation ->
                    withContext(mapDispatcher) { recording.mapping.onObservation(observation) }
                    syncRealigning(recording)
                }
            }
            // La carte et la sauvegarde suivent la marche au plus une fois par seconde.
            launch {
                while (true) {
                    rebuildLiveMap(recording)
                    delay(1_000)
                }
            }
            while (true) {
                persist(recording)
                delay(1_000)
            }
        }
    }

    private fun stopRecording() {
        val recording = active ?: return
        active = null
        mutableRecording.value = RecordingState.Idle
        // Démarrage immédiat et non annulable : la fin du parcours est écrite même si l'écran se ferme.
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                recording.job?.cancelAndJoin()
                // Ce qui attendait un recalage est gardé tel quel plutôt que perdu.
                withContext(mapDispatcher) { recording.mapping.finish() }
                persist(recording)
                val id = recording.id ?: return@withContext
                try {
                    recordingRepository.finish(id, clock())
                } catch (error: Exception) {
                    logger.log(Level.WARNING, "Unable to close recording", error)
                }
            }
        }
    }

    private fun onPosition(state: PositionState) {
        val recording = active ?: return
        if (state is PositionState.Tracking) {
            correctedPose.value = recording.mapping.onPose(state.pose)
        } else {
            recording.mapping.onTrackingLost()
            correctedPose.value = null
        }
        syncRealigning(recording)
    }

    private fun syncRealigning(recording: ActiveRecording) {
        if (active !== recording) return
        val realigning = recording.mapping.isRealigning
        mutableRecording.update { (it as? RecordingState.Recording)?.copy(realigning = realigning) ?: it }
    }

    private fun liveMapState(): Flow<MapState> = combine(liveMap, correctedPose) { map, pose ->
        MapState.Ready(map, live = true, pose = pose, recordedAt = null)
    }

    private fun savedMapState(): Flow<MapState> = recordingRepository.observeLatest(visitId)
        .map<SavedRecording?, MapState> { saved ->
            if (saved == null) {
                MapState.Empty
            } else {
                val occupancy = OccupancyMap(initial = saved.cells).snapshot()
                MapState.Ready(
                    FloorMap.build(saved.segments, saved.measurements, occupancy = occupancy),
                    live = false,
                    pose = null,
                    recordedAt = saved.startedAt,
                )
            }
        }
        .flowOn(mapDispatcher)
        .catch { error ->
            logger.log(Level.WARNING, "Unable to load the last recording", error)
            emit(MapState.Error)
        }

    private suspend fun rebuildLiveMap(recording: ActiveRecording) {
        val version = recording.mapping.version to recording.version
        if (version == recording.builtVersion) return
        val measurements = recording.measurements.toList()
        val map = withContext(mapDispatcher) {
            FloorMap.build(recording.mapping.segments(), measurements, occupancy = recording.mapping.occupancy())
        }
        recording.builtVersion = version
        if (active === recording) liveMap.value = map
    }

    private suspend fun persist(recording: ActiveRecording) {
        val id = recording.id ?: try {
            recordingRepository.start(visitId, recording.startedAt).also { recording.id = it }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.log(Level.WARNING, "Unable to create recording", error)
            markSaveFailed(recording, true)
            return
        }
        val points = recording.mapping.drainPendingPoints()
        val cells = recording.mapping.drainChanges()
        if (points.isEmpty() && cells.isEmpty()) return
        try {
            if (points.isNotEmpty()) recordingRepository.addPoints(id, points)
        } catch (error: Exception) {
            recording.mapping.restorePendingPoints(points)
            recording.mapping.restoreChanges(cells.keys)
            if (error is CancellationException) throw error
            logger.log(Level.WARNING, "Unable to save recording points", error)
            markSaveFailed(recording, true)
            return
        }
        try {
            if (cells.isNotEmpty()) recordingRepository.saveCells(id, cells)
            markSaveFailed(recording, false)
        } catch (error: Exception) {
            recording.mapping.restoreChanges(cells.keys)
            if (error is CancellationException) throw error
            logger.log(Level.WARNING, "Unable to save the room map", error)
            markSaveFailed(recording, true)
        }
    }

    private fun markSaveFailed(recording: ActiveRecording, failed: Boolean) {
        if (active !== recording) return
        mutableRecording.update { (it as? RecordingState.Recording)?.copy(saveFailed = failed) ?: it }
    }

    private fun stopWith(problem: NetScanProblem) {
        scanJob?.cancel()
        scanJob = null
        samples.clear()
        scanSamples.clear()
        mutableNetScan.value = NetScanState.Unavailable(problem)
    }

    private fun complete(mode: NetScanMode, elapsedSeconds: Int) {
        val summary = summarize(samples.toList())
        samples.clear()
        val recording = scanRecording
        val recordingId = recording?.id
        // Positions recalées avec ce qu'on sait maintenant de leur portion de parcours.
        val points = recording?.let { scanSamples.map(it.mapping::correct) }.orEmpty()
        scanSamples.clear()
        // Sans enregistrement sauvegardé, la position ne pourrait être retrouvée sur aucune carte.
        val position = if (recordingId != null) medianPoint(points) else null
        val moved = position != null && points.any { it.distanceTo(position) > MapRules.STILL_RADIUS }
        if (summary == null) {
            mutableNetScan.value = NetScanState.Unavailable(NetScanProblem.NO_DATA)
            return
        }
        val operatorName = (operator.value as? OperatorStatus.Connected)?.name
        save(NetScan(visitId, scanStartedAt, elapsedSeconds, mode, operatorName, summary, recordingId, position), moved)
    }

    private fun save(result: NetScan, moved: Boolean) {
        mutableNetScan.value = NetScanState.Completed(result, SaveState.SAVING, moved)
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
            if (outcome == SaveState.SAVED) place(result)
            val current = mutableNetScan.value
            if (current is NetScanState.Completed && current.result == result) {
                mutableNetScan.value = current.copy(saveState = outcome)
            }
        }
    }

    /** Une mesure sauvegardée apparaît sur la carte en direct de son enregistrement. */
    private fun place(result: NetScan) {
        val position = result.position ?: return
        val recording = scanRecording?.takeIf { it.id == result.recordingId } ?: return
        recording.measurements += MapMeasurement(position, result.summary.level)
        recording.version++
    }

    private fun SignalAvailability.toProblem(): NetScanProblem? = when (this) {
        SignalAvailability.AVAILABLE -> null
        SignalAvailability.NO_TELEPHONY -> NetScanProblem.NO_TELEPHONY
        SignalAvailability.NO_SIM -> NetScanProblem.NO_SIM
        SignalAvailability.AIRPLANE_MODE -> NetScanProblem.AIRPLANE_MODE
        SignalAvailability.LOCATION_DISABLED -> NetScanProblem.LOCATION_DISABLED
    }

    /** Enregistrement en cours : trajectoire et pièce recalées, mesures placées, état de la sauvegarde. */
    private class ActiveRecording(val startedAt: Long) {
        val mapping = LiveMapping()
        val measurements = mutableListOf<MapMeasurement>()
        var id: Long? = null
        var job: Job? = null
        /** Incrémentée à chaque mesure placée ; avec celle de [mapping], dit si la carte doit être recalculée. */
        var version = 0
        var builtVersion: Pair<Int, Int>? = null
    }
}
