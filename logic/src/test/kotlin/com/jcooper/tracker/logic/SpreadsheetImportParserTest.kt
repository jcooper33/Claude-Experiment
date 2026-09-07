package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpreadsheetImportParserTest {

    private val zone = ZoneOffset.UTC

    @Test
    fun `imports a realistic Calorie Log and Weight Log export`() {
        val calorieLog = XlsxReader.SheetTable(
            headers = listOf("Date", "Time", "Meal/Item", "Calories", "Notes"),
            rows = listOf(
                listOf("2026-08-31", "7:00 AM", "Sugar-free double shot energy coffee", "5", "Post-fast; water only until 11:30am"),
                listOf("2026-08-31", "11:30 AM", "1 lb baby carrots", "185", null),
                listOf("2026-09-01", null, "3 large hard-boiled eggs", "234", "Day 2"),
                listOf(null, null, null, null, null), // trailing blank row, should be silently skipped
            ),
        )
        val weightLog = XlsxReader.SheetTable(
            headers = listOf("Date", "Weight_lbs", "Notes", "Change vs Prev", "Change vs Start"),
            rows = listOf(
                listOf("2026-08-31", "364", "Starting weight", "0", "0"),
                listOf("2026-09-02", "355.4", null, "-8.6", "-8.6"),
            ),
        )

        val result = SpreadsheetImportParser.parse(mapOf("Calorie Log" to calorieLog, "Weight Log" to weightLog), zone)

        assertEquals(3, result.foodEntries.size)
        assertEquals(2, result.weightEntries.size)
        assertTrue(result.warnings.isEmpty(), "unexpected warnings: ${result.warnings}")

        val first = result.foodEntries[0]
        assertEquals("Sugar-free double shot energy coffee", first.description)
        assertEquals(5, first.calories)
        assertEquals("Post-fast; water only until 11:30am", first.notes)
        val expectedMillis = LocalDate.of(2026, 8, 31).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expectedMillis, first.timestampMillis)

        // Row with no Time value falls back to noon rather than being dropped.
        val thirdEntryDate = java.time.Instant.ofEpochMilli(result.foodEntries[2].timestampMillis).atZone(zone).toLocalDate()
        assertEquals(LocalDate.of(2026, 9, 1), thirdEntryDate)

        assertEquals(364.0, result.weightEntries[0].weightLbs)
        assertEquals("Starting weight", result.weightEntries[0].notes)
        assertEquals(355.4, result.weightEntries[1].weightLbs)
    }

    @Test
    fun `skips rows with unparseable required fields and reports why`() {
        val calorieLog = XlsxReader.SheetTable(
            headers = listOf("Date", "Meal/Item", "Calories"),
            rows = listOf(
                listOf("not-a-date", "mystery meal", "400"),
                listOf("2026-01-01", "missing calories", "n/a"),
                listOf("2026-01-02", "valid entry", "500"),
            ),
        )
        val weightLog = XlsxReader.SheetTable(headers = listOf("Date", "Weight_lbs"), rows = emptyList())
        val result = SpreadsheetImportParser.parse(mapOf("Calorie Log" to calorieLog, "Weight Log" to weightLog), zone)

        assertEquals(1, result.foodEntries.size)
        assertEquals("valid entry", result.foodEntries[0].description)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `reports missing sheets instead of guessing`() {
        val result = SpreadsheetImportParser.parse(emptyMap(), zone)
        assertEquals(0, result.foodEntries.size)
        assertEquals(0, result.weightEntries.size)
        assertEquals(2, result.warnings.size)
    }

    @Test
    fun `blank description falls back to a placeholder rather than dropping the calories`() {
        val calorieLog = XlsxReader.SheetTable(
            headers = listOf("Date", "Meal/Item", "Calories"),
            rows = listOf(listOf("2026-01-01", null, "300")),
        )
        val result = SpreadsheetImportParser.parse(mapOf("Calorie Log" to calorieLog), zone)
        assertEquals(1, result.foodEntries.size)
        assertEquals("(imported entry)", result.foodEntries[0].description)
    }
}
