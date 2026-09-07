package com.jcooper.tracker.logic

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class XlsxWriterTest {

    private fun sampleSheets() = listOf(
        XlsxWriter.Sheet(
            name = "Food Log",
            headers = listOf("Date", "Description", "Calories"),
            rows = listOf(
                listOf("2026-01-01", "cup of rice & pork", 650),
                listOf("2026-01-02", "<special> chars \"test\"", 400),
            ),
        ),
        XlsxWriter.Sheet(
            name = "Weight Log",
            headers = listOf("Date", "Weight (lbs)"),
            rows = listOf(listOf("2026-01-01", 181.5)),
        ),
    )

    @Test
    fun `produces a well-formed zip with required OOXML parts`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sampleSheets(), out)

        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries[entry.name] = zip.readBytes()
                entry = zip.nextEntry
            }
        }

        assertTrue(entries.containsKey("[Content_Types].xml"))
        assertTrue(entries.containsKey("_rels/.rels"))
        assertTrue(entries.containsKey("xl/workbook.xml"))
        assertTrue(entries.containsKey("xl/_rels/workbook.xml.rels"))
        assertTrue(entries.containsKey("xl/worksheets/sheet1.xml"))
        assertTrue(entries.containsKey("xl/worksheets/sheet2.xml"))
        assertEquals(6, entries.size)

        val dbf = DocumentBuilderFactory.newInstance()
        for ((name, bytes) in entries) {
            val doc = dbf.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            assertTrue(doc.documentElement != null, "part $name should parse as well-formed XML")
        }
    }

    @Test
    fun `escapes special characters so the XML stays well-formed`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sampleSheets(), out)
        var sheet1Bytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet1.xml") sheet1Bytes = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val xml = String(sheet1Bytes!!, Charsets.UTF_8)
        assertTrue(xml.contains("rice &amp; pork"))
        assertTrue(xml.contains("&lt;special&gt;"))
        // Must still parse despite embedded special characters.
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(sheet1Bytes))
    }

    @Test
    fun `numeric cells are written without a type attribute`() {
        val out = ByteArrayOutputStream()
        XlsxWriter.write(sampleSheets(), out)
        var sheet2Bytes: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "xl/worksheets/sheet2.xml") sheet2Bytes = zip.readBytes()
                entry = zip.nextEntry
            }
        }
        val xml = String(sheet2Bytes!!, Charsets.UTF_8)
        assertTrue(xml.contains("<v>181.5</v>"))
    }
}
