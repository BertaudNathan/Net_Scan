package com.example.MobileM2.Net_Scan.domain

import java.io.File
import java.text.Normalizer

/** Mesure numérotée dans le rapport : le numéro (P1, P2…) sert à la reporter sur le plan. */
data class ReportPoint(val number: Int, val scan: NetScan) {
    val label: String get() = "P$number"
}

/**
 * Contenu d'un rapport de visite, indépendant du format de sortie.
 * Les points sont dans l'ordre chronologique des mesures.
 */
data class VisitReport(
    val visit: Visit,
    val points: List<ReportPoint>,
    val generatedAt: Long,
) {
    /** Nombre de points par niveau, du pire au meilleur, niveaux absents compris (à zéro). */
    val levelCounts: Map<SignalLevel, Int> =
        SignalLevel.entries.associateWith { level -> points.count { it.scan.summary.level == level } }

    /** Opérateurs mesurés, dans l'ordre de première mesure. */
    val operators: List<String> = points.mapNotNull { it.scan.operatorName?.takeIf(String::isNotBlank) }.distinct()

    /** Points au niveau faible ou inexistant : les zones à couvrir en priorité. */
    val weakPoints: List<ReportPoint> =
        points.filter { it.scan.summary.level == SignalLevel.NONE || it.scan.summary.level == SignalLevel.LOW }

    val isEmpty: Boolean get() = points.isEmpty()
}

fun buildVisitReport(visit: Visit, scans: List<NetScan>, generatedAt: Long): VisitReport {
    val ordered = scans.sortedWith(compareBy<NetScan> { it.startedAt }.thenBy { it.id })
    return VisitReport(
        visit = visit,
        points = ordered.mapIndexed { index, scan -> ReportPoint(index + 1, scan) },
        generatedAt = generatedAt,
    )
}

/**
 * Nom de fichier lisible et sûr pour tous les systèmes : « rapport-entrepot-nord-2026-11-19.pdf ».
 * [date] est déjà formatée (aaaa-mm-jj) par l'appelant, qui connaît le fuseau horaire.
 */
fun reportFileName(building: String, date: String): String {
    val slug = Normalizer.normalize(building, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .take(MAX_SLUG_LENGTH)
        .trim('-')
    return if (slug.isEmpty()) "rapport-$date.pdf" else "rapport-$slug-$date.pdf"
}

private const val MAX_SLUG_LENGTH = 40

/** Produit le fichier du rapport, stocké sur l'appareil. Aucune donnée n'est envoyée. */
interface ReportGenerator {
    suspend fun generate(report: VisitReport): File
}
