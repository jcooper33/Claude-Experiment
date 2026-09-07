package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure arithmetic over food/weight logs. No I/O, no Android dependency, no AI —
 * every number here is a deterministic function of the stored entries.
 */
class RollingStatsCalculator(private val zone: ZoneId) {

    /** Sum of calories per calendar day, keyed by local date. */
    fun dailyCalorieTotals(entries: List<FoodLogRecord>): Map<LocalDate, Int> =
        entries.groupBy { DateUtils.toLocalDate(it.timestampMillis, zone) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.calories } }

    /** Total calories logged for [day] (0 if nothing logged). */
    fun caloriesForDay(entries: List<FoodLogRecord>, day: LocalDate): Int =
        dailyCalorieTotals(entries)[day] ?: 0

    /**
     * Average daily calorie intake over the trailing [windowDays] ending at [asOf],
     * inclusive. Days with no logged food count as 0 calories for that day, so the
     * average reflects true adherence over the window, not just days you happened
     * to log.
     */
    fun averageDailyCalories(entries: List<FoodLogRecord>, windowDays: Int, asOf: LocalDate): Double {
        val totals = dailyCalorieTotals(entries)
        val days = DateUtils.trailingDays(asOf, windowDays)
        val sum = days.sumOf { totals[it] ?: 0 }
        return sum.toDouble() / windowDays
    }

    fun daysLogged(entries: List<FoodLogRecord>): Int = dailyCalorieTotals(entries).size

    fun daysOverTarget(entries: List<FoodLogRecord>, dailyTarget: Int): Int =
        dailyCalorieTotals(entries).values.count { it > dailyTarget }

    /** Mean weight per calendar day (average of same-day weigh-ins), keyed by local date. */
    fun dailyWeightAverages(entries: List<WeightLogRecord>): Map<LocalDate, Double> =
        entries.groupBy { DateUtils.toLocalDate(it.timestampMillis, zone) }
            .mapValues { (_, dayEntries) -> dayEntries.sumOf { it.weightLbs } / dayEntries.size }
            .toSortedMap()

    /**
     * A [rollingWindowDays]-day rolling average of daily weight, evaluated only on
     * days that have at least one weigh-in. Each point averages whatever daily
     * weight-averages fall within the trailing window (missing days are simply
     * excluded rather than treated as zero, since weight has no natural "zero day").
     */
    fun rollingWeightAverageSeries(entries: List<WeightLogRecord>, rollingWindowDays: Int = 7): Map<LocalDate, Double> {
        val daily = dailyWeightAverages(entries)
        if (daily.isEmpty()) return emptyMap()
        val dates = daily.keys.toList()
        return dates.associateWith { day ->
            val windowStart = day.minusDays((rollingWindowDays - 1).toLong())
            val pointsInWindow = daily.filterKeys { !it.isBefore(windowStart) && !it.isAfter(day) }.values
            pointsInWindow.sum() / pointsInWindow.size
        }
    }

    /**
     * Average lbs/day change over the trailing [windowDays] ending at [asOf], computed
     * from the first and last daily weight-average points that fall inside the window.
     * Returns null if there are fewer than 2 weigh-in days inside the window.
     */
    fun averageWeightChangePerDay(entries: List<WeightLogRecord>, windowDays: Int, asOf: LocalDate): Double? {
        val daily = dailyWeightAverages(entries)
        val windowStart = asOf.minusDays((windowDays - 1).toLong())
        val pointsInWindow = daily.filterKeys { !it.isBefore(windowStart) && !it.isAfter(asOf) }
        if (pointsInWindow.size < 2) return null
        val first = pointsInWindow.entries.first()
        val last = pointsInWindow.entries.last()
        val elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(first.key, last.key)
        if (elapsedDays <= 0) return null
        return (last.value - first.value) / elapsedDays
    }
}
