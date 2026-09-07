package com.jcooper.tracker.importer

import android.content.Context
import android.net.Uri
import com.jcooper.tracker.data.FoodEntry
import com.jcooper.tracker.data.TrackerRepository
import com.jcooper.tracker.data.WeightEntry
import com.jcooper.tracker.logic.SpreadsheetImportParser
import com.jcooper.tracker.logic.XlsxReader
import java.time.ZoneId

data class ImportSummary(
    val foodImported: Int,
    val weightImported: Int,
    val warnings: List<String>,
)

/**
 * Bridges a picked .xlsx file to the database: reads it with [XlsxReader], interprets it
 * with [SpreadsheetImportParser] (both dependency-free, defined in :logic so the parsing
 * itself is unit-tested there), then bulk-inserts whatever it recognized. Purely additive —
 * it does not de-duplicate against existing entries, so importing the same file twice
 * creates duplicates.
 */
class ImportManager(private val context: Context, private val repository: TrackerRepository) {

    suspend fun importFrom(uri: Uri): ImportSummary {
        val sheets = context.contentResolver.openInputStream(uri)?.use { input ->
            XlsxReader.readWorkbookSheets(input)
        } ?: return ImportSummary(0, 0, listOf("Couldn't open the selected file."))

        val result = SpreadsheetImportParser.parse(sheets, ZoneId.systemDefault())

        repository.importFood(
            result.foodEntries.map {
                FoodEntry(description = it.description, calories = it.calories, notes = it.notes, timestampMillis = it.timestampMillis)
            },
        )
        repository.importWeight(
            result.weightEntries.map {
                WeightEntry(weightLbs = it.weightLbs, notes = it.notes, timestampMillis = it.timestampMillis)
            },
        )

        return ImportSummary(result.foodEntries.size, result.weightEntries.size, result.warnings)
    }
}
