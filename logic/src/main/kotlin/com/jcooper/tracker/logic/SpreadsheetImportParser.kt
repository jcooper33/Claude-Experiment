package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ImportedFoodEntry(val description: String, val calories: Int, val notes: String?, val timestampMillis: Long)
data class ImportedWeightEntry(val weightLbs: Double, val notes: String?, val timestampMillis: Long)

data class ImportResult(
    val foodEntries: List<ImportedFoodEntry>,
    val weightEntries: List<ImportedWeightEntry>,
    val warnings: List<String>,
)

/**
 * Turns sheets read by [XlsxReader] into importable entries. Column headers are matched
 * by keyword rather than exact text, so this tolerates the small header-wording
 * differences between spreadsheet tools/versions (e.g. "Meal/Item" vs "Description",
 * "Weight_lbs" vs "Weight (lbs)") as long as the sheet names contain "Calorie" / "Weight"
 * and the columns contain the expected concepts. A row missing its date or its required
 * number (calories / weight) is skipped with a warning rather than guessed at.
 */
object SpreadsheetImportParser {

    private val DEFAULT_TIME: LocalTime = LocalTime.of(12, 0)

    private val TIME_FORMATS = listOf(
        DateTimeFormatter.ofPattern("h:mm a", Locale.US),
        DateTimeFormatter.ofPattern("H:mm", Locale.US),
    )

    private val DATE_FORMATS = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.US),
    )

    fun parse(sheets: Map<String, XlsxReader.SheetTable>, zone: ZoneId): ImportResult {
        val warnings = mutableListOf<String>()

        val foodSheet = sheets.entries.firstOrNull { it.key.contains("calorie", ignoreCase = true) }
        val weightSheet = sheets.entries.firstOrNull { it.key.contains("weight", ignoreCase = true) }

        val food = if (foodSheet != null) {
            parseFoodSheet(foodSheet.value, zone, warnings)
        } else {
            warnings += "No sheet with 'Calorie' in its name was found; no food entries imported."
            emptyList()
        }

        val weight = if (weightSheet != null) {
            parseWeightSheet(weightSheet.value, zone, warnings)
        } else {
            warnings += "No sheet with 'Weight' in its name was found; no weight entries imported."
            emptyList()
        }

        return ImportResult(food, weight, warnings)
    }

    private fun findColumn(headers: List<String>, keywords: List<String>): Int? {
        val index = headers.indexOfFirst { h -> keywords.any { k -> h.trim().lowercase(Locale.US).contains(k) } }
        return index.takeIf { it >= 0 }
    }

    private fun parseFoodSheet(table: XlsxReader.SheetTable, zone: ZoneId, warnings: MutableList<String>): List<ImportedFoodEntry> {
        val dateCol = findColumn(table.headers, listOf("date"))
        val timeCol = findColumn(table.headers, listOf("time"))
        val descCol = findColumn(table.headers, listOf("meal", "item", "description", "food"))
        val calCol = findColumn(table.headers, listOf("calorie", "cal"))
        val notesCol = findColumn(table.headers, listOf("note"))

        if (dateCol == null || calCol == null) {
            warnings += "Calorie Log sheet is missing a recognizable Date or Calories column; no food entries imported."
            return emptyList()
        }

        val result = mutableListOf<ImportedFoodEntry>()
        table.rows.forEachIndexed { index, row ->
            val rowNum = index + 2
            val dateText = row.getOrNull(dateCol)?.trim()
            val date = dateText?.let { parseDate(it) }
            if (date == null) {
                if (!dateText.isNullOrBlank()) warnings += "Calorie Log row $rowNum: couldn't parse date '$dateText', skipped."
                return@forEachIndexed
            }

            val calText = row.getOrNull(calCol)?.trim()
            val calories = calText?.toDoubleOrNull()?.let { Math.round(it).toInt() }
            if (calories == null) {
                warnings += "Calorie Log row $rowNum: couldn't parse calories '${calText.orEmpty()}', skipped."
                return@forEachIndexed
            }

            val time = timeCol?.let { row.getOrNull(it)?.trim() }?.let { parseTime(it) } ?: DEFAULT_TIME
            val description = descCol?.let { row.getOrNull(it)?.trim() }
                .takeUnless { it.isNullOrBlank() } ?: "(imported entry)"
            val notes = notesCol?.let { row.getOrNull(it)?.trim() }.takeUnless { it.isNullOrBlank() }

            val millis = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
            result += ImportedFoodEntry(description, calories, notes, millis)
        }
        return result
    }

    private fun parseWeightSheet(table: XlsxReader.SheetTable, zone: ZoneId, warnings: MutableList<String>): List<ImportedWeightEntry> {
        val dateCol = findColumn(table.headers, listOf("date"))
        val weightCol = findColumn(table.headers, listOf("weight"))
        val notesCol = findColumn(table.headers, listOf("note"))

        if (dateCol == null || weightCol == null) {
            warnings += "Weight Log sheet is missing a recognizable Date or Weight column; no weight entries imported."
            return emptyList()
        }

        val result = mutableListOf<ImportedWeightEntry>()
        table.rows.forEachIndexed { index, row ->
            val rowNum = index + 2
            val dateText = row.getOrNull(dateCol)?.trim()
            val date = dateText?.let { parseDate(it) }
            if (date == null) {
                if (!dateText.isNullOrBlank()) warnings += "Weight Log row $rowNum: couldn't parse date '$dateText', skipped."
                return@forEachIndexed
            }

            val weightText = row.getOrNull(weightCol)?.trim()
            val weight = weightText?.toDoubleOrNull()
            if (weight == null) {
                warnings += "Weight Log row $rowNum: couldn't parse weight '${weightText.orEmpty()}', skipped."
                return@forEachIndexed
            }

            val notes = notesCol?.let { row.getOrNull(it)?.trim() }.takeUnless { it.isNullOrBlank() }
            val millis = LocalDateTime.of(date, DEFAULT_TIME).atZone(zone).toInstant().toEpochMilli()
            result += ImportedWeightEntry(weight, notes, millis)
        }
        return result
    }

    private fun parseDate(text: String): LocalDate? {
        val datePart = text.split(" ").first()
        for (fmt in DATE_FORMATS) {
            try {
                return LocalDate.parse(datePart, fmt)
            } catch (e: DateTimeParseException) {
                // try next format
            }
        }
        return null
    }

    private fun parseTime(text: String): LocalTime? {
        val normalized = text.trim().uppercase(Locale.US)
        for (fmt in TIME_FORMATS) {
            try {
                return LocalTime.parse(normalized, fmt)
            } catch (e: DateTimeParseException) {
                // try next format
            }
        }
        return null
    }
}
