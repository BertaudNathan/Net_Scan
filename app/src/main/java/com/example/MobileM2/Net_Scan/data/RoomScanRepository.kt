package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.Scan
import com.example.MobileM2.Net_Scan.domain.ScanRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomScanRepository(private val dao: ScanDao) : ScanRepository {
    override fun observeScans(): Flow<List<Scan>> = dao.observeAll().map { rows ->
        rows.map { Scan(it.id, it.building, it.client, it.address, it.createdAt) }
    }

    override suspend fun createScan(building: String, client: String, address: String) {
        dao.insert(
            ScanEntity(
                building = building,
                client = client,
                address = address,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
