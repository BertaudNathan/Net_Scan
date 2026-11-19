package com.example.MobileM2.Net_Scan.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.MobileM2.Net_Scan.domain.FloorPlanRepository
import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.RecordingRepository
import com.example.MobileM2.Net_Scan.domain.ReportGenerator
import com.example.MobileM2.Net_Scan.domain.VisitReport
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import com.example.MobileM2.Net_Scan.domain.buildVisitReport
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.logging.Level
import java.util.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReportState {
    data object Loading : ReportState
    data object Missing : ReportState
    data object Error : ReportState
    data class Ready(val report: VisitReport, val file: File) : ReportState
}

enum class ReportMessage { SAVED, SAVE_FAILED, NO_APP }

/**
 * Prépare le rapport d'une visite à l'ouverture de l'écran, à partir des mesures enregistrées.
 * Le fichier reste sur l'appareil tant que l'utilisateur ne le télécharge pas ou ne l'envoie pas.
 */
class ReportViewModel(
    private val visitId: Long,
    private val visitRepository: VisitRepository,
    private val netScanRepository: NetScanRepository,
    private val planRepository: FloorPlanRepository,
    private val recordingRepository: RecordingRepository,
    private val generator: ReportGenerator,
    private val clock: () -> Long = System::currentTimeMillis,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val logger = Logger.getLogger("ReportViewModel")
    private val mutableState = MutableStateFlow<ReportState>(ReportState.Loading)
    private val mutableMessage = MutableStateFlow<ReportMessage?>(null)
    private val mutableExporting = MutableStateFlow(false)
    private var loadJob: Job? = null

    val state: StateFlow<ReportState> = mutableState.asStateFlow()
    val message: StateFlow<ReportMessage?> = mutableMessage.asStateFlow()
    val exporting: StateFlow<Boolean> = mutableExporting.asStateFlow()

    init { load() }

    fun retry() {
        if (mutableState.value == ReportState.Error) load()
    }

    private fun load() {
        loadJob?.cancel()
        mutableState.value = ReportState.Loading
        loadJob = viewModelScope.launch {
            mutableState.value = try {
                val visit = visitRepository.observeVisit(visitId).first()
                if (visit == null) {
                    ReportState.Missing
                } else {
                    val scans = netScanRepository.observeForVisit(visitId).first()
                    // Une visite se cartographie d'une seule façon : plan importé ou parcours ARCore.
                    val report = when (visit.mode) {
                        MappingMode.MANUAL -> buildVisitReport(visit, scans, clock(), plan = planRepository.observePlan(visitId).first())
                        MappingMode.ARCORE ->
                            buildVisitReport(visit, scans, clock(), recording = recordingRepository.observeLatest(visitId).first())
                    }
                    ReportState.Ready(report, generator.generate(report))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to build the report", error)
                ReportState.Error
            }
        }
    }

    /** Copie le rapport vers l'emplacement choisi par l'utilisateur (Téléchargements, Drive…). */
    fun exportTo(open: () -> OutputStream?) {
        val ready = mutableState.value as? ReportState.Ready ?: return
        if (mutableExporting.value) return
        mutableExporting.value = true
        viewModelScope.launch {
            mutableMessage.value = try {
                withContext(io) {
                    val output = open() ?: throw IOException("Destination unavailable")
                    output.use { out -> ready.file.inputStream().use { it.copyTo(out) } }
                }
                ReportMessage.SAVED
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logger.log(Level.WARNING, "Unable to export the report", error)
                ReportMessage.SAVE_FAILED
            } finally {
                mutableExporting.value = false
            }
        }
    }

    fun noAppToSend() { mutableMessage.value = ReportMessage.NO_APP }
    fun messageShown() { mutableMessage.value = null }
}
