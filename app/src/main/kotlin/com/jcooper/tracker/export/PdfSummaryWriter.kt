package com.jcooper.tracker.export

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.jcooper.tracker.logic.FoodLogRecord
import com.jcooper.tracker.logic.PlateauDetector
import com.jcooper.tracker.logic.RollingStatsCalculator
import com.jcooper.tracker.logic.WeightLogRecord
import java.io.OutputStream
import java.time.LocalDate
import java.time.ZoneId

/**
 * Renders the same figures as the XLSX "Rolling Averages" / "Plateau Status" sheets
 * as a simple one-column PDF summary, using Android's built-in PdfDocument (no
 * external PDF library needed).
 */
object PdfSummaryWriter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val LINE_HEIGHT = 20f

    fun write(
        out: OutputStream,
        food: List<FoodLogRecord>,
        weight: List<WeightLogRecord>,
        asOf: LocalDate,
        dailyTarget: Int,
        plateauWindowDays: Int,
        plateauToleranceLbs: Double,
        zone: ZoneId,
    ) {
        val lines = buildLines(food, weight, asOf, dailyTarget, plateauWindowDays, plateauToleranceLbs, zone)

        val document = PdfDocument()
        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val bodyPaint = Paint().apply { textSize = 12f }

        var page: PdfDocument.Page? = null
        var canvas: android.graphics.Canvas? = null
        var y = 0f
        var pageNumber = 0

        fun startPage() {
            page?.let { document.finishPage(it) }
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(info)
            canvas = page!!.canvas
            y = MARGIN
            canvas!!.drawText("Offline Tracker — Coaching Summary", MARGIN, y, titlePaint)
            y += LINE_HEIGHT * 2
        }

        startPage()
        for (line in lines) {
            if (y > PAGE_HEIGHT - MARGIN) startPage()
            canvas!!.drawText(line, MARGIN, y, bodyPaint)
            y += LINE_HEIGHT
        }
        page?.let { document.finishPage(it) }

        document.writeTo(out)
        document.close()
    }

    private fun buildLines(
        food: List<FoodLogRecord>,
        weight: List<WeightLogRecord>,
        asOf: LocalDate,
        dailyTarget: Int,
        plateauWindowDays: Int,
        plateauToleranceLbs: Double,
        zone: ZoneId,
    ): List<String> {
        val stats = RollingStatsCalculator(zone)
        val plateau = PlateauDetector(zone, stats)
        val status = plateau.detect(weight, asOf, plateauWindowDays, plateauToleranceLbs)

        val lines = mutableListOf<String>()
        lines += "As of: $asOf"
        lines += ""
        lines += "Today's total calories: ${stats.caloriesForDay(food, asOf)} / target $dailyTarget"
        lines += "7-day avg calories: %.0f".format(stats.averageDailyCalories(food, 7, asOf))
        lines += "14-day avg calories: %.0f".format(stats.averageDailyCalories(food, 14, asOf))
        lines += ""
        val change7 = stats.averageWeightChangePerDay(weight, 7, asOf)
        val change14 = stats.averageWeightChangePerDay(weight, 14, asOf)
        lines += "7-day avg weight change: ${change7?.let { "%.2f lbs/day".format(it) } ?: "n/a"}"
        lines += "14-day avg weight change: ${change14?.let { "%.2f lbs/day".format(it) } ?: "n/a"}"
        lines += ""
        lines += "Days logged: ${stats.daysLogged(food)}"
        lines += "Days over target: ${stats.daysOverTarget(food, dailyTarget)}"
        lines += ""
        lines += "Plateau window: $plateauWindowDays days, tolerance: %.1f lbs".format(plateauToleranceLbs)
        lines += when {
            !status.hasSufficientData -> "Plateau status: not enough weigh-ins yet"
            status.isPlateau -> "Plateau status: FLAGGED (net change %.2f lbs)".format(status.netChangeLbs)
            else -> "Plateau status: not flagged (net change %.2f lbs)".format(status.netChangeLbs ?: 0.0)
        }
        return lines
    }
}
