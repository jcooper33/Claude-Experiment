package com.jcooper.tracker.logic

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object DateUtils {

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Inclusive list of calendar days ending at [endInclusive], oldest first. */
    fun trailingDays(endInclusive: LocalDate, windowDays: Int): List<LocalDate> {
        require(windowDays > 0) { "windowDays must be positive" }
        return (0 until windowDays).map { endInclusive.minusDays((windowDays - 1 - it).toLong()) }
    }

    private val DISPLAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun formatTimestamp(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(DISPLAY_FORMAT)
}
