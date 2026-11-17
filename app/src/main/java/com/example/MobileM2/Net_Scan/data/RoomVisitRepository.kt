package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomVisitRepository(private val dao: VisitDao) : VisitRepository {
    override fun observeVisits(): Flow<List<Visit>> = dao.observeAll().map { rows ->
        rows.map { Visit(it.id, it.building, it.client, it.address, it.createdAt) }
    }

    override suspend fun createVisit(building: String, client: String, address: String) {
        dao.insert(
            VisitEntity(
                building = building,
                client = client,
                address = address,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
