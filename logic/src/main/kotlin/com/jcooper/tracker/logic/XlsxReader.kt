package com.jcooper.tracker.logic

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.LocalDate
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.floor

/**
 * Minimal, dependency-free .xlsx reader — the counterpart to [XlsxWriter]. Reads real
 * spreadsheets produced by Excel/Google Sheets/LibreOffice/openpyxl, not just files this
 * app wrote itself: it resolves the workbook's sheet-name-to-part mapping through
 * workbook.xml.rels, looks up shared strings, and detects which numeric cells are
 * actually dates by walking styles.xml (built-in date format ids plus any custom
 * numFmt whose format code looks like a date/time pattern). No Apache POI — same
 * reasoning as XlsxWriter: POI's XSSF implementation is unreliable on Android.
 */
object XlsxReader {

    data class SheetTable(val headers: List<String>, val rows: List<List<String?>>)

    private val EXCEL_EPOCH: LocalDate = LocalDate.of(1899, 12, 30)

    private val BUILTIN_DATE_FORMAT_IDS = setOf(
        14, 15, 16, 17, 18, 19, 20, 21, 22,
        27, 28, 29, 30, 31, 32, 33, 34, 35, 36,
        45, 46, 47, 50, 57,
    )

    fun readWorkbookSheets(input: InputStream): Map<String, SheetTable> {
        val parts = readZipEntries(input)
        val dbf = DocumentBuilderFactory.newInstance()
        fun parseXml(bytes: ByteArray): Document = dbf.newDocumentBuilder().parse(ByteArrayInputStream(bytes))

        val sharedStrings = parts["xl/sharedStrings.xml"]?.let { parseSharedStrings(parseXml(it)) } ?: emptyList()
        val dateStyleIndices = parts["xl/styles.xml"]?.let { parseDateStyleIndices(parseXml(it)) } ?: emptySet()

        val workbookBytes = parts["xl/workbook.xml"] ?: return emptyMap()
        val workbookDoc = parseXml(workbookBytes)
        val relsDoc = parts["xl/_rels/workbook.xml.rels"]?.let { parseXml(it) }
        val ridToTarget = relsDoc?.let { parseRelationships(it) } ?: emptyMap()

        val result = mutableMapOf<String, SheetTable>()
        val sheetNodes = workbookDoc.getElementsByTagName("sheet")
        for (i in 0 until sheetNodes.length) {
            val el = sheetNodes.item(i) as Element
            val name = el.getAttribute("name")
            val rid = el.getAttribute("r:id")
            val target = ridToTarget[rid] ?: continue
            val sheetBytes = parts["xl/$target"] ?: continue
            result[name] = parseSheet(parseXml(sheetBytes), sharedStrings, dateStyleIndices)
        }
        return result
    }

    private fun readZipEntries(input: InputStream): Map<String, ByteArray> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun parseRelationships(doc: Document): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("Relationship")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            map[el.getAttribute("Id")] = el.getAttribute("Target")
        }
        return map
    }

    private fun parseSharedStrings(doc: Document): List<String> {
        val siNodes = doc.getElementsByTagName("si")
        val result = ArrayList<String>(siNodes.length)
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as Element
            val tNodes = si.getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) sb.append(tNodes.item(j).textContent)
            result += sb.toString()
        }
        return result
    }

    /** Returns the set of cellXfs indices (the values a cell's `s` attribute references) that represent a date/time. */
    private fun parseDateStyleIndices(doc: Document): Set<Int> {
        val customFormatCodes = mutableMapOf<Int, String>()
        val numFmtNodes = doc.getElementsByTagName("numFmt")
        for (i in 0 until numFmtNodes.length) {
            val el = numFmtNodes.item(i) as Element
            val id = el.getAttribute("numFmtId").toIntOrNull() ?: continue
            customFormatCodes[id] = el.getAttribute("formatCode")
        }

        val cellXfsNodes = doc.getElementsByTagName("cellXfs")
        if (cellXfsNodes.length == 0) return emptySet()
        val xfNodes = (cellXfsNodes.item(0) as Element).getElementsByTagName("xf")

        val dateIndices = mutableSetOf<Int>()
        for (i in 0 until xfNodes.length) {
            val el = xfNodes.item(i) as Element
            val numFmtId = el.getAttribute("numFmtId").toIntOrNull() ?: continue
            val isDate = numFmtId in BUILTIN_DATE_FORMAT_IDS ||
                customFormatCodes[numFmtId]?.let { isDateFormatCode(it) } == true
            if (isDate) dateIndices += i
        }
        return dateIndices
    }

    private fun isDateFormatCode(formatCode: String): Boolean {
        val stripped = formatCode
            .replace(Regex("\\[[^]]*]"), "")
            .replace(Regex("\"[^\"]*\""), "")
            .lowercase()
        if (stripped.isBlank() || stripped == "general" || stripped == "@") return false
        return stripped.contains('y') || stripped.contains('d') || stripped.contains('h')
    }

    private fun parseSheet(doc: Document, sharedStrings: List<String>, dateStyleIndices: Set<Int>): SheetTable {
        val rowNodes = doc.getElementsByTagName("row")
        if (rowNodes.length == 0) return SheetTable(emptyList(), emptyList())

        fun readRow(rowEl: Element, width: Int?): List<String?> {
            val cellNodes = rowEl.getElementsByTagName("c")
            val cells = mutableMapOf<Int, String?>()
            var maxCol = width ?: 0
            for (i in 0 until cellNodes.length) {
                val c = cellNodes.item(i) as Element
                val ref = c.getAttribute("r")
                val col = columnIndexFromRef(ref)
                if (width == null && col + 1 > maxCol) maxCol = col + 1
                cells[col] = readCellValue(c, sharedStrings, dateStyleIndices)
            }
            return (0 until maxCol).map { cells[it] }
        }

        val headerRow = readRow(rowNodes.item(0) as Element, null)
        val headers = headerRow.map { it?.trim().orEmpty() }
        val width = headers.size

        val dataRows = mutableListOf<List<String?>>()
        for (i in 1 until rowNodes.length) {
            dataRows += readRow(rowNodes.item(i) as Element, width)
        }
        return SheetTable(headers, dataRows)
    }

    private fun readCellValue(c: Element, sharedStrings: List<String>, dateStyleIndices: Set<Int>): String? {
        val type = c.getAttribute("t")
        val styleIndex = c.getAttribute("s").toIntOrNull()

        if (type == "inlineStr") {
            val isNodes = c.getElementsByTagName("is")
            if (isNodes.length == 0) return null
            val tNodes = (isNodes.item(0) as Element).getElementsByTagName("t")
            val sb = StringBuilder()
            for (j in 0 until tNodes.length) sb.append(tNodes.item(j).textContent)
            return sb.toString()
        }

        val vNodes = c.getElementsByTagName("v")
        if (vNodes.length == 0) return null
        val raw = vNodes.item(0).textContent

        return when (type) {
            "s" -> raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }
            "str" -> raw
            "b" -> if (raw == "1") "true" else "false"
            else -> {
                val value = raw.toDoubleOrNull() ?: return raw
                if (styleIndex != null && styleIndex in dateStyleIndices) {
                    excelSerialToString(value)
                } else {
                    formatNumber(value)
                }
            }
        }
    }

    private fun excelSerialToString(value: Double): String {
        val wholeDays = floor(value).toLong()
        val date = EXCEL_EPOCH.plusDays(wholeDays)
        val fraction = value - wholeDays
        if (fraction <= 0.0001) return date.toString()
        val totalSeconds = Math.round(fraction * 86400)
        val hour = totalSeconds / 3600
        val minute = (totalSeconds % 3600) / 60
        return "%s %02d:%02d".format(date, hour, minute)
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun columnIndexFromRef(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return index - 1
    }
}
