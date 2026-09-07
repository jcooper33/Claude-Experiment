package com.jcooper.tracker.logic

/**
 * Plain data carriers for the logic module. The Room entities in the :app module
 * are mapped to these before any calculation runs, so this module never depends
 * on Android or Room.
 */
data class FoodLogRecord(
    val id: Long,
    val description: String,
    val calories: Int,
    val notes: String?,
    val timestampMillis: Long,
)

data class WeightLogRecord(
    val id: Long,
    val weightLbs: Double,
    val notes: String?,
    val timestampMillis: Long,
)
