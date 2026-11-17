package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

data class Scan(
    val id: Long,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
)

interface ScanRepository {
    fun observeScans(): Flow<List<Scan>>
    suspend fun createScan(building: String, client: String, address: String)
}
