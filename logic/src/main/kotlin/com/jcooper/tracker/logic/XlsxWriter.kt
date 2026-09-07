package com.jcooper.tracker.logic

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free .xlsx writer. Apache POI's XSSF implementation relies on
 * javax.xml.stream / java.awt classes that are unreliable or absent on Android, so
 * instead of pulling that in, this writes the OOXML package (a plain zip of XML
 * parts) directly. Every part below is the smallest valid form Excel, Google
 * Sheets, and LibreOffice all accept: no styles.xml, no shared strings table —
 * string cells use inline strings instead.
 */
object XlsxWriter {

    data class Sheet(val name: String, val headers: List<String>, val rows: List<List<Any?>>)

    fun write(sheets: List<Sheet>, out: OutputStream) {
        require(sheets.isNotEmpty()) { "at least one sheet is required" }
        ZipOutputStream(out).use { zip ->
            writeEntry(zip, "[Content_Types].xml", contentTypesXml(sheets.size))
            writeEntry(zip, "_rels/.rels", rootRelsXml())
            writeEntry(zip, "xl/workbook.xml", workbookXml(sheets))
            writeEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            sheets.forEachIndexed { index, sheet ->
                writeEntry(zip, "xl/worksheets/sheet${index + 1}.xml", sheetXml(sheet))
            }
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypesXml(sheetCount: Int): String {
        val overrides = (1..sheetCount).joinToString("") { i ->
            """<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
$overrides</Types>"""
    }

    private fun rootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun workbookXml(sheets: List<Sheet>): String {
        val entries = sheets.mapIndexed { index, sheet ->
            """<sheet name="${escapeXmlAttr(sheet.name)}" sheetId="${index + 1}" r:id="rId${index + 1}"/>"""
        }.joinToString("")
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheets>$entries</sheets>
</workbook>"""
    }

    private fun workbookRelsXml(sheetCount: Int): String {
        val entries = (1..sheetCount).joinToString("") { i ->
            """<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>"""
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
$entries</Relationships>"""
    }

    private fun sheetXml(sheet: Sheet): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

        sb.append(rowXml(1, sheet.headers.map { it as Any? }))
        sheet.rows.forEachIndexed { i, row -> sb.append(rowXml(i + 2, row)) }

        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun rowXml(rowNumber: Int, values: List<Any?>): String {
        val sb = StringBuilder("""<row r="$rowNumber">""")
        values.forEachIndexed { colIndex, value ->
            val ref = columnRef(colIndex) + rowNumber
            sb.append(cellXml(ref, value))
        }
        sb.append("</row>")
        return sb.toString()
    }

    private fun cellXml(ref: String, value: Any?): String = when (value) {
        null -> ""
        is Int, is Long -> """<c r="$ref"><v>$value</v></c>"""
        is Double, is Float -> """<c r="$ref"><v>${formatNumber(value.toString().toDouble())}</v></c>"""
        is Boolean -> """<c r="$ref" t="b"><v>${if (value) 1 else 0}</v></c>"""
        else -> """<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${escapeXmlText(value.toString())}</t></is></c>"""
    }

    private fun formatNumber(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    /** 0-based column index -> spreadsheet column letters (A, B, ..., Z, AA, ...). */
    private fun columnRef(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (i % 26)))
            i = i / 26 - 1
            if (i < 0) break
        }
        return sb.toString()
    }

    private fun escapeXmlText(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeXmlAttr(s: String): String = escapeXmlText(s)
        .replace("\"", "&quot;")
}
