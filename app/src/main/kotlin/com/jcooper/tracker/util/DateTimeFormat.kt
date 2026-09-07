package com.jcooper.tracker.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Thin Android-side formatting wrapper; the actual date-bucketing math lives in :logic. */
object DateTimeFormat {
    private fun dateFormat() = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private fun timeFormat() = SimpleDateFormat("h:mm a", Locale.getDefault())
    private fun fullFormat() = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())

    fun dateLabel(epochMillis: Long): String = dateFormat().format(Date(epochMillis))
    fun timeLabel(epochMillis: Long): String = timeFormat().format(Date(epochMillis))
    fun timestampLabel(epochMillis: Long): String = fullFormat().format(Date(epochMillis))
}
