package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlateauDetectorTest {

    private val zone = ZoneOffset.UTC
    private val stats = RollingStatsCalculator(zone)
    private val detector = PlateauDetector(zone, stats)

    private fun millisFor(date: LocalDate): Long = date.atTime(8, 0).atZone(zone).toInstant().toEpochMilli()
    private fun weight(date: LocalDate, lbs: Double, id: Long = 0): WeightLogRecord =
        WeightLogRecord(id, lbs, null, millisFor(date))

    @Test
    fun `flags plateau when weight is essentially flat for the full window`() {
        val start = LocalDate.of(2026, 1, 1)
        // 20 days of daily weigh-ins oscillating narrowly around 180 lbs.
        val entries = (0 until 20).map { i ->
            val jitter = if (i % 2 == 0) 0.2 else -0.2
            weight(start.plusDays(i.toLong()), 180.0 + jitter, id = i.toLong())
        }
        val asOf = start.plusDays(19)
        val status = detector.detect(entries, asOf, windowDays = 14, toleranceLbs = 0.5)
        assertTrue(status.hasSufficientData)
        assertTrue(status.isPlateau, "expected plateau, netChange=${status.netChangeLbs}")
    }

    @Test
    fun `does not flag plateau during a steady loss`() {
        val start = LocalDate.of(2026, 1, 1)
        val entries = (0 until 20).map { i -> weight(start.plusDays(i.toLong()), 200.0 - i * 0.3, id = i.toLong()) }
        val asOf = start.plusDays(19)
        val status = detector.detect(entries, asOf, windowDays = 14, toleranceLbs = 0.5)
        assertTrue(status.hasSufficientData)
        assertFalse(status.isPlateau)
    }

    @Test
    fun `reports insufficient data with a short history`() {
        val start = LocalDate.of(2026, 1, 1)
        val entries = listOf(weight(start, 180.0), weight(start.plusDays(1), 179.8))
        val status = detector.detect(entries, start.plusDays(1), windowDays = 14, toleranceLbs = 0.5)
        assertFalse(status.hasSufficientData)
        assertFalse(status.isPlateau)
    }

    @Test
    fun `respects a configurable tolerance`() {
        val start = LocalDate.of(2026, 1, 1)
        val entries = (0 until 20).map { i -> weight(start.plusDays(i.toLong()), 200.0 - i * 0.1, id = i.toLong()) }
        val asOf = start.plusDays(19)
        val loose = detector.detect(entries, asOf, windowDays = 14, toleranceLbs = 5.0)
        val strict = detector.detect(entries, asOf, windowDays = 14, toleranceLbs = 0.1)
        assertEquals(true, loose.isPlateau)
        assertEquals(false, strict.isPlateau)
    }
}
