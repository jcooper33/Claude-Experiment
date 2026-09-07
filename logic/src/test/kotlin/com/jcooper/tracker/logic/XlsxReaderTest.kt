package com.jcooper.tracker.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class XlsxReaderTest {

    private val excelEpoch = LocalDate.of(1899, 12, 30)
    private fun serialFor(date: LocalDate): Long = ChronoUnit.DAYS.between(excelEpoch, date)

    /**
     * Builds a minimal but structurally real .xlsx: a real workbook.xml/rels indirection,
     * a shared strings table, a custom date numFmt wired through cellXfs, and two sheets —
     * mirroring exactly what a real spreadsheet tool (Excel/LibreOffice/openpyxl) emits,
     * as opposed to what XlsxWriter itself would produce.
     */
    private fun buildFixture(dateSerial: Long): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            entry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheets>
                <sheet name="Calorie Log" sheetId="1" r:id="rId1"/>
                <sheet name="Weight Log" sheetId="2" r:id="rId2"/>
                </sheets>
                </workbook>""",
            )
            entry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="worksheet" Target="worksheets/sheet2.xml"/>
                </Relationships>""",
            )
            entry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <numFmts count="1"><numFmt numFmtId="165" formatCode="yyyy\-mm\-dd"/></numFmts>
                <cellXfs count="2">
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0"/>
                <xf numFmtId="165" fontId="0" fillId="0" borderId="0"/>
                </cellXfs>
                </styleSheet>""",
            )
            entry(
                "xl/sharedStrings.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6" uniqueCount="6">
                <si><t>Date</t></si>
                <si><t>Meal/Item</t></si>
                <si><t>Calories</t></si>
                <si><t>cup of rice &amp; pork</t></si>
                <si><t>Weight_lbs</t></si>
                <si><t>Notes</t></si>
                </sst>""",
            )
            entry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1" t="s"><v>2</v></c></row>
                <row r="2"><c r="A2" s="1" t="n"><v>$dateSerial</v></c><c r="B2" t="s"><v>3</v></c><c r="C2" t="n"><v>650</v></c></row>
                </sheetData>
                </worksheet>""",
            )
            entry(
                "xl/worksheets/sheet2.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>4</v></c><c r="C1" t="s"><v>5</v></c></row>
                <row r="2"><c r="A2" s="1" t="n"><v>$dateSerial</v></c><c r="B2" t="n"><v>181.5</v></c><c r="C2"/></row>
                </sheetData>
                </worksheet>""",
            )
        }
        return out.toByteArray()
    }

    @Test
    fun `resolves sheet names, shared strings, and date-styled numeric cells`() {
        val targetDate = LocalDate.of(2026, 8, 31)
        val bytes = buildFixture(serialFor(targetDate))

        val sheets = XlsxReader.readWorkbookSheets(ByteArrayInputStream(bytes))

        assertEquals(setOf("Calorie Log", "Weight Log"), sheets.keys)

        val food = sheets.getValue("Calorie Log")
        assertEquals(listOf("Date", "Meal/Item", "Calories"), food.headers)
        assertEquals(listOf(targetDate.toString(), "cup of rice & pork", "650"), food.rows[0])

        val weight = sheets.getValue("Weight Log")
        assertEquals(listOf("Date", "Weight_lbs", "Notes"), weight.headers)
        assertEquals(targetDate.toString(), weight.rows[0][0])
        assertEquals("181.5", weight.rows[0][1])
        assertNull(weight.rows[0][2])
    }
}
