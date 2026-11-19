package com.example.MobileM2.Net_Scan.data

import com.example.MobileM2.Net_Scan.domain.FloorPlan
import com.example.MobileM2.Net_Scan.domain.FloorPlanRepository
import com.example.MobileM2.Net_Scan.domain.PlanMeasurement
import com.example.MobileM2.Net_Scan.domain.PlanPoint
import com.example.MobileM2.Net_Scan.domain.SignalLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomFloorPlanRepository(
    private val plans: FloorPlanDao,
    private val netScans: NetScanDao,
) : FloorPlanRepository {
    override fun observePlan(visitId: Long): Flow<FloorPlan?> = plans.observe(visitId).map { entity ->
        entity?.let { FloorPlan(it.visitId, it.imagePath, it.widthPx, it.heightPx, it.metersPerPixel) }
    }

    override suspend fun savePlan(plan: FloorPlan) =
        plans.save(FloorPlanEntity(plan.visitId, plan.imagePath, plan.widthPx, plan.heightPx, plan.metersPerPixel))

    override suspend fun setScale(visitId: Long, metersPerPixel: Float) = plans.setScale(visitId, metersPerPixel)

    override fun observePlanMeasurements(visitId: Long): Flow<List<PlanMeasurement>> =
        netScans.observePlacedOnPlan(visitId).map { scans ->
            scans.mapNotNull { scan ->
                val x = scan.planX ?: return@mapNotNull null
                val y = scan.planY ?: return@mapNotNull null
                val level = SignalLevel.entries.firstOrNull { it.name == scan.level } ?: return@mapNotNull null
                PlanMeasurement(PlanPoint(x, y), level)
            }
        }
}
