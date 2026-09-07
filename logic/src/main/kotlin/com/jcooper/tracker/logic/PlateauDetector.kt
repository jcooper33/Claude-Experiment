package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class PlateauStatus(
    val isPlateau: Boolean,
    val hasSufficientData: Boolean,
    val netChangeLbs: Double?,
    val windowDays: Int,
    val toleranceLbs: Double,
    val dataPointsUsed: Int,
)

/**
 * Fixed-threshold plateau flag: true when the 7-day rolling weight average has
 * moved by no more than [toleranceLbs] over the trailing [windowDays] days.
 * Purely arithmetic — no judgment, no natural-language reasoning.
 */
class PlateauDetector(private val zone: ZoneId, private val statsCalculator: RollingStatsCalculator = RollingStatsCalculator(zone)) {

    fun detect(
        entries: List<WeightLogRecord>,
        asOf: LocalDate,
        windowDays: Int = 14,
        toleranceLbs: Double = 0.5,
        rollingAverageWindowDays: Int = 7,
        minDataPointSpanDays: Int = windowDays / 2,
    ): PlateauStatus {
        val rollingSeries = statsCalculator.rollingWeightAverageSeries(entries, rollingAverageWindowDays)
        val windowStart = asOf.minusDays((windowDays - 1).toLong())
        val pointsInWindow = rollingSeries.filterKeys { !it.isBefore(windowStart) && !it.isAfter(asOf) }
            .toSortedMap()

        if (pointsInWindow.size < 2) {
            return PlateauStatus(
                isPlateau = false,
                hasSufficientData = false,
                netChangeLbs = null,
                windowDays = windowDays,
                toleranceLbs = toleranceLbs,
                dataPointsUsed = pointsInWindow.size,
            )
        }

        val first = pointsInWindow.entries.first()
        val last = pointsInWindow.entries.last()
        val spanDays = ChronoUnit.DAYS.between(first.key, last.key)
        val sufficientSpan = spanDays >= minDataPointSpanDays

        val netChange = last.value - first.value
        return PlateauStatus(
            isPlateau = sufficientSpan && kotlin.math.abs(netChange) <= toleranceLbs,
            hasSufficientData = sufficientSpan,
            netChangeLbs = netChange,
            windowDays = windowDays,
            toleranceLbs = toleranceLbs,
            dataPointsUsed = pointsInWindow.size,
        )
    }
}
