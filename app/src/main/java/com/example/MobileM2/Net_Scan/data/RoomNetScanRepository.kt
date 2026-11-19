package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.NetScan
import com.example.MobileM2.Net_Scan.domain.NetScanMode
import com.example.MobileM2.Net_Scan.domain.NetScanRepository

class RoomNetScanRepository(private val dao: NetScanDao) : NetScanRepository {
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
            ),
        )
    }
}
