package com.jcooper.tracker.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.jcooper.tracker.data.SettingsStore
import com.jcooper.tracker.logic.ExportBuilder
import com.jcooper.tracker.logic.FoodLogRecord
import com.jcooper.tracker.logic.WeightLogRecord
import com.jcooper.tracker.logic.XlsxWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * Generates the export file on disk (app-private cache, no storage permission needed)
 * and hands it to the system share sheet via a FileProvider content:// URI. This is
 * the only place in the app that touches anything resembling "sharing" — it's
 * on-demand, user-triggered, and never runs in the background.
 */
class ExportManager(private val context: Context) {

    private val zone: ZoneId = ZoneId.systemDefault()
    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun exportXlsxAndShare(food: List<FoodLogRecord>, weight: List<WeightLogRecord>, settings: SettingsStore) {
        val sheets = ExportBuilder(zone).buildSheets(
            food,
            weight,
            LocalDate.now(zone),
            settings.plateauThresholdDays,
            settings.plateauToleranceLbs.toDouble(),
        )

        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "offline_tracker_${fileStamp.format(Date())}.xlsx")
        FileOutputStream(file).use { out -> XlsxWriter.write(sheets, out) }

        shareFile(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    }

    fun exportPdfAndShare(food: List<FoodLogRecord>, weight: List<WeightLogRecord>, settings: SettingsStore) {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "offline_tracker_summary_${fileStamp.format(Date())}.pdf")
        FileOutputStream(file).use { out ->
            PdfSummaryWriter.write(
                out,
                food,
                weight,
                LocalDate.now(zone),
                settings.dailyCalorieTarget,
                settings.plateauThresholdDays,
                settings.plateauToleranceLbs.toDouble(),
                zone,
            )
        }
        shareFile(file, "application/pdf")
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(chooser)
        } else {
            Toast.makeText(context, file.absolutePath, Toast.LENGTH_LONG).show()
        }
    }
}
