package com.example.MobileM2.Net_Scan.domain

/** Position brute prise pour un scan réseau, avec la portion de parcours dont elle relève. */
data class ScanSample(val segment: Int, val raw: FloorPoint)

/**
 * Carte d'un enregistrement en cours : trajectoire et carte d'occupation, avec recalage automatique.
 *
 * Après une perte de suivi (ou un saut), ARCore repart souvent d'une origine légèrement décalée :
 * sans correction, la suite du parcours et ses murs seraient dédoublés. La nouvelle portion est donc
 * mise en attente le temps d'en voir quelques murs, recalée sur la carte déjà tracée ([MapMatcher]),
 * puis ajoutée. Faute de murs communs (pièce nouvelle), elle est ajoutée telle quelle au bout de
 * quelques secondes. Positions et observations arrivent brutes ; tout ce qui sort est corrigé.
 * Sûr entre fils d'exécution.
 */
class LiveMapping(val grid: FloorGrid = FloorGrid()) {
    private val track = TrackBuilder()
    private val map = OccupancyMap(grid)
    /** Correction de chaque portion, de la brute vers la carte. */
    private val transforms = mutableListOf(FloorTransform.IDENTITY)
    private var lastRaw: FloorPoint? = null
    private var broken = false
    private var pending: Pending? = null

    /** Augmente à chaque changement de la trajectoire ou de la carte. */
    @Volatile var version = 0
        private set

    /** Une portion attend d'être recalée. */
    val isRealigning: Boolean
        @Synchronized get() = pending != null

    private val current: Int get() = transforms.lastIndex

    /** Position brute ; retourne la position corrigée. */
    @Synchronized
    fun onPose(raw: FloorPose): FloorPose {
        val previous = lastRaw
        val jump = previous != null && previous.distanceTo(raw.point) > MapRules.MAX_POSE_JUMP
        if (previous != null && (broken || jump)) startSegment(raw.point)
        broken = false
        lastRaw = raw.point
        val transform = transforms[current]
        val waiting = pending
        if (waiting != null) {
            waiting.points += raw.point
        } else if (track.add(transform.apply(raw.point))) {
            version++
        }
        return transform.apply(raw)
    }

    /** Perte de suivi : la prochaine position ouvrira une nouvelle portion. */
    @Synchronized
    fun onTrackingLost() {
        if (lastRaw != null) broken = true
    }

    /** Vue brute de la scène ; retourne true si la carte affichable a changé. */
    @Synchronized
    fun onObservation(raw: SceneObservation): Boolean {
        val estimate = transforms[current]
        val waiting = pending
        if (waiting == null) {
            val changed = map.integrate(estimate.apply(raw))
            if (changed) version++
            return changed
        }
        waiting.observations += raw
        waiting.map.integrate(estimate.apply(raw))
        val count = waiting.observations.size
        if (count >= AlignmentRules.MIN_PENDING_OBSERVATIONS && count % 2 == 0) {
            MapMatcher.align(map.snapshot(), waiting.map.snapshot(), waiting.pivot, grid)?.let {
                settle(it.transform)
                return true
            }
        }
        if (count >= AlignmentRules.MAX_PENDING_OBSERVATIONS) {
            settle(FloorTransform.IDENTITY)
            return true
        }
        return false
    }

    /** Fin de l'enregistrement : ce qui attendait un recalage est ajouté tel quel. */
    @Synchronized
    fun finish() = settle(FloorTransform.IDENTITY)

    /** Échantillon de position pour un scan réseau, corrigé plus tard par [correct]. */
    @Synchronized
    fun sample(raw: FloorPoint) = ScanSample(current, raw)

    /** Position corrigée d'un échantillon, avec le recalage connu à présent pour sa portion. */
    @Synchronized
    fun correct(sample: ScanSample): FloorPoint = transforms[sample.segment].apply(sample.raw)

    @Synchronized
    fun segments(): List<List<FloorPoint>> = track.segments()

    @Synchronized
    fun occupancy(): Occupancy = map.snapshot()

    @Synchronized
    fun drainPendingPoints(): List<TrackPoint> = track.drainPending()

    @Synchronized
    fun restorePendingPoints(points: List<TrackPoint>) = track.restorePending(points)

    @Synchronized
    fun drainChanges(): Map<GridCell, Float> = map.drainChanges()

    @Synchronized
    fun restoreChanges(cells: Collection<GridCell>) = map.restoreChanges(cells)

    private fun startSegment(firstRaw: FloorPoint) {
        settle(FloorTransform.IDENTITY)
        val estimate = transforms[current]
        transforms += estimate
        track.breakSegment()
        // Sans murs déjà tracés, il n'y a rien sur quoi se recaler.
        if (map.snapshot().occupied.size >= AlignmentRules.MIN_WALLS) {
            pending = Pending(estimate.apply(firstRaw), OccupancyMap(grid))
        }
    }

    /** Applique la [correction] à la portion en attente et l'ajoute à la carte. */
    private fun settle(correction: FloorTransform) {
        val waiting = pending ?: return
        pending = null
        val transform = transforms[current].then(correction)
        transforms[current] = transform
        waiting.observations.forEach { map.integrate(transform.apply(it)) }
        waiting.points.forEach { track.add(transform.apply(it)) }
        version++
    }

    private class Pending(val pivot: FloorPoint, val map: OccupancyMap) {
        val observations = mutableListOf<SceneObservation>()
        val points = mutableListOf<FloorPoint>()
    }
}
