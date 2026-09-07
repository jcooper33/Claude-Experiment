package com.jcooper.tracker.logic

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RollingStatsCalculatorTest {

    private val zone: ZoneId = ZoneOffset.UTC
    private val calc = RollingStatsCalculator(zone)

    private fun millisFor(date: LocalDate, hour: Int = 12): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun food(date: LocalDate, calories: Int, id: Long = 0): FoodLogRecord =
        FoodLogRecord(id, "test", calories, null, millisFor(date))

    private fun weight(date: LocalDate, lbs: Double, id: Long = 0): WeightLogRecord =
        WeightLogRecord(id, lbs, null, millisFor(date))

    @Test
    fun `daily totals sum same-day entries`() {
        val day = LocalDate.of(2026, 1, 5)
        val entries = listOf(food(day, 500), food(day, 300), food(day.plusDays(1), 200))
        val totals = calc.dailyCalorieTotals(entries)
        assertEquals(800, totals[day])
        assertEquals(200, totals[day.plusDays(1)])
    }

    @Test
    fun `average daily calories counts unlogged days as zero`() {
        val asOf = LocalDate.of(2026, 1, 7)
        // Only log on day asOf and asOf-1; 7-day window includes 5 unlogged days.
        val entries = listOf(food(asOf, 2100), food(asOf.minusDays(1), 1900))
        val avg = calc.averageDailyCalories(entries, windowDays = 7, asOf = asOf)
        // (2100 + 1900 + 0*5) / 7
        assertEquals(4000.0 / 7, avg, 0.0001)
    }

    @Test
    fun `days logged and days over target`() {
        val d1 = LocalDate.of(2026, 2, 1)
        val d2 = d1.plusDays(1)
        val d3 = d1.plusDays(2)
        val entries = listOf(food(d1, 2500), food(d2, 1800), food(d3, 2600))
        assertEquals(3, calc.daysLogged(entries))
        assertEquals(2, calc.daysOverTarget(entries, dailyTarget = 2000))
    }

    @Test
    fun `daily weight averages average same-day weigh-ins`() {
        val day = LocalDate.of(2026, 3, 1)
        val entries = listOf(weight(day, 180.0), weight(day, 182.0))
        assertEquals(181.0, calc.dailyWeightAverages(entries)[day])
    }

    @Test
    fun `rolling weight average only uses points within window`() {
        val d1 = LocalDate.of(2026, 4, 1)
        val entries = (0 until 10).map { weight(d1.plusDays(it.toLong()), 200.0 - it, id = it.toLong()) }
        val series = calc.rollingWeightAverageSeries(entries, rollingWindowDays = 7)
        // On day 10 (index 9, value 191), window covers indices 3..9 (values 197..191)
        val lastDay = d1.plusDays(9)
        val expected = (197.0 + 196.0 + 195.0 + 194.0 + 193.0 + 192.0 + 191.0) / 7
        assertEquals(expected, series[lastDay]!!, 0.0001)
    }

    @Test
    fun `average weight change per day requires at least two points`() {
        val d1 = LocalDate.of(2026, 5, 1)
        val single = listOf(weight(d1, 200.0))
        assertNull(calc.averageWeightChangePerDay(single, windowDays = 14, asOf = d1))

        val entries = listOf(weight(d1, 200.0), weight(d1.plusDays(10), 195.0))
        val change = calc.averageWeightChangePerDay(entries, windowDays = 14, asOf = d1.plusDays(10))
        assertEquals(-0.5, change!!, 0.0001)
    }
}
