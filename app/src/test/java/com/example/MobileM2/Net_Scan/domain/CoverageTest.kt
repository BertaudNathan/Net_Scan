package com.example.MobileM2.Net_Scan.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverageTest {
    /** Plan de 2000 × 1000 pixels, 1 cm par pixel : 20 × 10 m, soit 40 × 20 cases de 50 cm. */
    private val plan = FloorPlan(visitId = 1, imagePath = "plan.png", widthPx = 2000, heightPx = 1000, metersPerPixel = 0.01f)
    private val grid = FloorGrid(0.5f)

    /** Bâtiment de 16 × 8 m, à 2 m du bord gauche et 1 m du haut du plan. */
    private val building = rectangle(2.1f, 1.1f, 18.1f, 9.1f)

    @Test fun aClosedOutlineGivesTheInsideOfTheBuilding() {
        val interior = buildingInterior(plan, building, grid)
        // Murs dans les colonnes 4 et 36, les lignes 2 et 18 : l'intérieur est entre les deux, sans la marge du plan.
        assertEquals(31 * 15, interior.size)
        assertTrue(GridCell(20, 10) in interior)
        assertFalse(GridCell(1, 1) in interior)
        assertFalse(GridCell(4, 10) in interior)
    }

    @Test fun anOpenOutlineCannotBeAnalysed() {
        assertEquals(Coverage.OutlineOpen, analyzeCoverage(plan, emptyList(), building.dropLast(1), grid))
        assertEquals(Coverage.OutlineOpen, analyzeCoverage(plan, emptyList(), emptyList(), grid))
        assertNull(analyzeCoverage(plan.copy(metersPerPixel = null), emptyList(), building, grid))
    }

    @Test fun aDoorwayStillClosesTheOutlineButAWideOpeningDoesNot() {
        val walls = building.dropLast(2) + wall(2.1f, 9.1f, 2.1f, 1.1f)
        // Porte de 1,5 m dans le mur du bas.
        val door = walls + wall(18.1f, 9.1f, 10.6f, 9.1f) + wall(9.1f, 9.1f, 2.1f, 9.1f)
        assertTrue(analyzeCoverage(plan, emptyList(), door, grid) is Coverage.Analyzed)
        // Ouverture de 4 m : le dehors entre dans le bâtiment.
        val opening = walls + wall(18.1f, 9.1f, 12.1f, 9.1f) + wall(8.1f, 9.1f, 2.1f, 9.1f)
        assertEquals(Coverage.OutlineOpen, analyzeCoverage(plan, emptyList(), opening, grid))
    }

    @Test fun aTinyEnclosureIsNotABuilding() {
        assertEquals(Coverage.OutlineOpen, analyzeCoverage(plan, emptyList(), rectangle(5.1f, 5.1f, 6.1f, 6.1f), grid))
    }

    @Test fun cellsFarFromEveryScanAreToMeasure() {
        val analyzed = analyzeCoverage(plan, listOf(FloorPoint(10.1f, 5.1f)), building, grid) as Coverage.Analyzed
        // Le scan au centre couvre 5 m autour de lui : les deux bouts du bâtiment restent à mesurer.
        assertFalse(GridCell(20, 10) in analyzed.uncovered)
        assertFalse(GridCell(27, 10) in analyzed.uncovered)
        assertTrue(GridCell(6, 10) in analyzed.uncovered)
        assertTrue(GridCell(34, 10) in analyzed.uncovered)
        assertTrue(analyzed.ratio in 0.3f..0.8f)
        // Un scan suggéré de chaque côté, dans le bâtiment.
        assertTrue(analyzed.suggestions.any { it.u < 0.35f } && analyzed.suggestions.any { it.u > 0.65f })
        assertTrue(analyzed.suggestions.all { grid.cellOf(plan.toMeters(it)!!) in analyzed.interior })
    }

    @Test fun scanningTheSuggestionsCoversTheBuilding() {
        val first = analyzeCoverage(plan, emptyList(), building, grid) as Coverage.Analyzed
        assertEquals(0f, first.ratio)
        assertTrue(first.suggestions.isNotEmpty())

        val after = analyzeCoverage(plan, first.suggestions.map { plan.toMeters(it)!! }, building, grid) as Coverage.Analyzed
        // Il ne reste que des recoins trop petits pour mériter un scan.
        assertTrue(after.ratio > 0.95f)
        assertTrue(after.suggestions.isEmpty())
    }

    @Test fun aRoomBehindAWallWithoutDoorNeedsItsOwnScan() {
        val walls = building + wall(10.1f, 1.1f, 10.1f, 9.1f)
        val analyzed = analyzeCoverage(plan, listOf(FloorPoint(9f, 5.1f)), walls, grid) as Coverage.Analyzed
        // À 2,3 m du scan, mais derrière la cloison : à mesurer.
        assertTrue(grid.cellOf(FloorPoint(11.3f, 5.1f)) in analyzed.uncovered)
        assertTrue(analyzed.suggestions.any { plan.toMeters(it)!!.x > 10.6f })
    }

    @Test fun planPointsComeBackFromMetres() {
        assertEquals(PlanPoint(0.5f, 0.25f), plan.toPlan(FloorPoint(10f, 2.5f)))
        assertNull(plan.copy(metersPerPixel = null).toPlan(FloorPoint(10f, 2.5f)))
    }

    /** Murs d'un rectangle, en mètres depuis le coin haut gauche du plan, dans le sens des aiguilles d'une montre. */
    private fun rectangle(left: Float, top: Float, right: Float, bottom: Float): List<PlanWall> = listOf(
        wall(left, top, right, top),
        wall(right, top, right, bottom),
        wall(right, bottom, left, bottom),
        wall(left, bottom, left, top),
    )

    private fun wall(x0: Float, z0: Float, x1: Float, z1: Float) = PlanWall(0, PlanPoint(x0 / 20f, z0 / 10f), PlanPoint(x1 / 20f, z1 / 10f))
}
