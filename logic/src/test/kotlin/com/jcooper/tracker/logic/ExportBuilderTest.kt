package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportBuilderTest {

    private val zone = ZoneOffset.UTC
    private val builder = ExportBuilder(zone)

    @Test
    fun `builds all four sheets with expected headers`() {
        val day = LocalDate.of(2026, 6, 1)
        val millis = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        val food = listOf(FoodLogRecord(1, "oatmeal", 400, null, millis))
        val weight = listOf(WeightLogRecord(1, 190.0, null, millis))

        val sheets = builder.buildSheets(food, weight, day, plateauWindowDays = 14, plateauToleranceLbs = 0.5)

        assertEquals(listOf("Food Log", "Weight Log", "Rolling Averages", "Plateau Status"), sheets.map { it.name })
        assertEquals(listOf("Date", "Time", "Description", "Calories", "Notes"), sheets[0].headers)
        assertEquals(1, sheets[0].rows.size)
        assertTrue(sheets[3].rows.any { it[0] == "Plateau flagged" })
    }
}
