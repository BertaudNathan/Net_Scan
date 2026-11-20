package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.GridCell
import com.example.MobileM2.Net_Scan.domain.MapMeasurement
import com.example.MobileM2.Net_Scan.domain.RecordingRepository
import com.example.MobileM2.Net_Scan.domain.SavedRecording
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import com.example.MobileM2.Net_Scan.domain.TrackPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalCoroutinesApi::class)
class RoomRecordingRepository(
    private val recordings: RecordingDao,
    private val netScans: NetScanDao,
) : RecordingRepository {
    override suspend fun start(visitId: Long, startedAt: Long): Long =
        recordings.insert(RecordingEntity(visitId = visitId, startedAt = startedAt, endedAt = null))

    override suspend fun addPoints(recordingId: Long, points: List<TrackPoint>) {
        if (points.isEmpty()) return
        recordings.insertPoints(
            points.map { RecordingPointEntity(recordingId = recordingId, segment = it.segment, x = it.point.x, z = it.point.z) },
        )
    }

    override suspend fun finish(recordingId: Long, endedAt: Long) = recordings.finish(recordingId, endedAt)

    override suspend fun saveCells(recordingId: Long, cells: Map<GridCell, Float>) {
        if (cells.isEmpty()) return
        recordings.saveCells(cells.map { (cell, value) -> RecordingCellEntity(recordingId, cell.col, cell.row, value) })
    }

    override fun observeLatest(visitId: Long): Flow<SavedRecording?> =
        recordings.observeLatestWithPoints(visitId).flatMapLatest { recording ->
            if (recording == null) {
                flowOf(null)
            } else {
                combine(
                    recordings.observePoints(recording.id),
                    netScans.observePlaced(recording.id),
                    recordings.observeCells(recording.id),
                ) { points, scans, cells ->
                    SavedRecording(
                        id = recording.id,
                        startedAt = recording.startedAt,
                        segments = points.groupBy { it.segment }.toSortedMap().values.map { segment ->
                            segment.map { FloorPoint(it.x, it.z) }
                        },
                        measurements = scans.mapNotNull { it.toMeasurement() },
                        cells = cells.associate { GridCell(it.cellCol, it.cellRow) to it.logOdds },
                    )
                }
            }
        }

    private fun NetScanEntity.toMeasurement(): MapMeasurement? {
        val x = mapX ?: return null
        val z = mapZ ?: return null
        val signal = SignalLevel.entries.firstOrNull { it.name == level } ?: return null
        return MapMeasurement(FloorPoint(x, z), signal, id)
    }
}
