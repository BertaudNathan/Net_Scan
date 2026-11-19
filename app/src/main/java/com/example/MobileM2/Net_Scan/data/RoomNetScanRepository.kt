package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.FloorPoint
import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository
import com.example.MobileM2.Net_Scan.domain.NetScanSummary
import com.example.MobileM2.Net_Scan.domain.NetworkTechnology
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomNetScanRepository(private val dao: NetScanDao) : NetScanRepository {
    override fun observeForVisit(visitId: Long): Flow<List<NetScan>> =
        dao.observeForVisit(visitId).map { rows ->
            rows.map { row ->
                NetScan(
                    visitId = row.visitId,
                    startedAt = row.startedAt,
                    durationSeconds = row.durationSeconds,
                    mode = row.timerSeconds?.let { NetScanMode.Timer(it) } ?: NetScanMode.Free,
                    operatorName = row.operatorName,
                    id = row.id,
                    recordingId = row.recordingId,
                    position = row.mapX?.let { x -> row.mapZ?.let { z -> FloorPoint(x, z) } },
                    planPosition = row.planX?.let { u -> row.planY?.let { v -> PlanPoint(u, v) } },
                    summary = NetScanSummary(
                        level = SignalLevel.valueOf(row.level),
                        technology = NetworkTechnology.valueOf(row.technology),
                        rsrpDbm = row.rsrpMedianDbm,
                        sinrDb = row.sinrMedianDb,
                        sampleCount = row.sampleCount,
                        band = row.band,
                        cellId = row.cellId,
                    ),
                )
            }
        }

    override suspend fun save(scan: NetScan) {
        dao.insert(
            NetScanEntity(
                visitId = scan.visitId,
                startedAt = scan.startedAt,
                durationSeconds = scan.durationSeconds,
                timerSeconds = (scan.mode as? NetScanMode.Timer)?.seconds,
                operatorName = scan.operatorName,
                technology = scan.summary.technology.name,
                level = scan.summary.level.name,
                rsrpMedianDbm = scan.summary.rsrpDbm,
                sinrMedianDb = scan.summary.sinrDb,
                sampleCount = scan.summary.sampleCount,
                band = scan.summary.band,
                cellId = scan.summary.cellId,
                recordingId = scan.recordingId,
                mapX = scan.position?.x,
                mapZ = scan.position?.z,
                planX = scan.planPosition?.u,
                planY = scan.planPosition?.v,
            ),
        )
    }
}
