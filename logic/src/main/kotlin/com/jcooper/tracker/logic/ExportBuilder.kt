package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneId

/** Builds the four export sheets (mirrored in the PDF summary) from raw entries. */
class ExportBuilder(private val zone: ZoneId) {

    private val stats = RollingStatsCalculator(zone)
    private val plateau = PlateauDetector(zone, stats)

    fun buildSheets(
        foodEntries: List<FoodLogRecord>,
        weightEntries: List<WeightLogRecord>,
        asOf: LocalDate,
        plateauWindowDays: Int,
        plateauToleranceLbs: Double,
    ): List<XlsxWriter.Sheet> {
        val foodSheet = XlsxWriter.Sheet(
            name = "Food Log",
            headers = listOf("Date", "Time", "Description", "Calories", "Notes"),
            rows = foodEntries.sortedBy { it.timestampMillis }.map { e ->
                val ts = DateUtils.formatTimestamp(e.timestampMillis, zone)
                val (date, time) = ts.split(" ")
                listOf(date, time, e.description, e.calories, e.notes.orEmpty())
            },
        )

        val weightSheet = XlsxWriter.Sheet(
            name = "Weight Log",
            headers = listOf("Date", "Time", "Weight (lbs)", "Notes"),
            rows = weightEntries.sortedBy { it.timestampMillis }.map { e ->
                val ts = DateUtils.formatTimestamp(e.timestampMillis, zone)
                val (date, time) = ts.split(" ")
                listOf(date, time, e.weightLbs, e.notes.orEmpty())
            },
        )

        val dailyCalories = stats.dailyCalorieTotals(foodEntries)
        val avgRowsHeader = listOf("Date", "Calories That Day", "7-Day Avg Calories", "14-Day Avg Calories", "7-Day Avg Weight (lbs)")
        val rollingWeight = stats.rollingWeightAverageSeries(weightEntries, 7)
        val allDates = (dailyCalories.keys + rollingWeight.keys).toSortedSet()
        val avgRows = allDates.map { day ->
            listOf(
                day.toString(),
                dailyCalories[day] ?: 0,
                round1(stats.averageDailyCalories(foodEntries, 7, day)),
                round1(stats.averageDailyCalories(foodEntries, 14, day)),
                rollingWeight[day]?.let { round1(it) },
            )
        }
        val averagesSheet = XlsxWriter.Sheet(name = "Rolling Averages", headers = avgRowsHeader, rows = avgRows)

        val status = plateau.detect(weightEntries, asOf, plateauWindowDays, plateauToleranceLbs)
        val plateauSheet = XlsxWriter.Sheet(
            name = "Plateau Status",
            headers = listOf("Field", "Value"),
            rows = listOf(
                listOf("As of", asOf.toString()),
                listOf("Plateau window (days)", status.windowDays),
                listOf("Tolerance (lbs)", status.toleranceLbs),
                listOf("Sufficient data", status.hasSufficientData),
                listOf("Net change over window (lbs)", status.netChangeLbs?.let { round1(it) } ?: "n/a"),
                listOf("Plateau flagged", status.isPlateau),
            ),
        )

        return listOf(foodSheet, weightSheet, averagesSheet, plateauSheet)
    }

    private fun round1(d: Double): Double = Math.round(d * 10.0) / 10.0
}
