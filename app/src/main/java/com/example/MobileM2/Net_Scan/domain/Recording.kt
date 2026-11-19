package com.example.MobileM2.Net_Scan.domain

import kotlinx.coroutines.flow.Flow

/**
 * Enregistrement sauvegardé : un parcours a son propre repère ARCore, il ne se superpose donc pas
 * aux autres parcours de la visite. Aucune image n'est conservée, seulement des positions et la
 * carte d'occupation qui en est tirée ([cells] : score de chaque case, voir [OccupancyMap]).
 */
data class SavedRecording(
    val id: Long,
    val startedAt: Long,
    val segments: List<List<FloorPoint>>,
    val measurements: List<MapMeasurement>,
    val cells: Map<GridCell, Float> = emptyMap(),
)

interface RecordingRepository {
    /** Crée l'enregistrement et retourne son identifiant. */
    suspend fun start(visitId: Long, startedAt: Long): Long

    suspend fun addPoints(recordingId: Long, points: List<TrackPoint>)

    suspend fun finish(recordingId: Long, endedAt: Long)

    /** Enregistre ou remplace le score de ces cases de la carte d'occupation. */
    suspend fun saveCells(recordingId: Long, cells: Map<GridCell, Float>)

    /** Dernier enregistrement de la visite qui contient au moins une position. */
    fun observeLatest(visitId: Long): Flow<SavedRecording?>
}
