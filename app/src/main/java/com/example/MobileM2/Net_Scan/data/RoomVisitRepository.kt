package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.MappingMode
import com.example.MobileM2.Net_Scan.domain.Visit
import com.example.MobileM2.Net_Scan.domain.VisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomVisitRepository(private val dao: VisitDao) : VisitRepository {
    override fun observeVisits(): Flow<List<Visit>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeVisit(id: Long): Flow<Visit?> = dao.observeById(id).map { it?.toDomain() }

    override suspend fun createVisit(building: String, client: String, address: String, mode: MappingMode) {
        dao.insert(
            VisitEntity(
                building = building,
                client = client,
                address = address,
                createdAt = System.currentTimeMillis(),
                mappingMode = mode.name,
            ),
        )
    }

    private fun VisitEntity.toDomain() = Visit(
        id = id,
        building = building,
        client = client,
        address = address,
        createdAt = createdAt,
        mode = MappingMode.entries.firstOrNull { it.name == mappingMode } ?: MappingMode.ARCORE,
    )
}
