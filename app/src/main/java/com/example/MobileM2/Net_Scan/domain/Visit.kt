package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

/**
 * Façon de placer les scans réseau, choisie à la création de la visite : ARCore suit le téléphone
 * et dessine la pièce, ou la personne se place elle-même sur un plan importé.
 */
enum class MappingMode { ARCORE, MANUAL }

data class Visit(
    val id: Long,
    val building: String,
    val client: String,
    val address: String,
    val createdAt: Long,
    val mode: MappingMode = MappingMode.ARCORE,
)

interface VisitRepository {
    fun observeVisits(): Flow<List<Visit>>
    fun observeVisit(id: Long): Flow<Visit?>
    suspend fun createVisit(building: String, client: String, address: String, mode: MappingMode)
}
