package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

data class Visit(
    val id: Long,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
)

interface VisitRepository {
    fun observeVisits(): Flow<List<Visit>>
    fun observeVisit(id: Long): Flow<Visit?>
    suspend fun createVisit(building: String, client: String, address: String)
}
