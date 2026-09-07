package com.jcooper.tracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "food_entries")
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val description: String,
    val calories: Int,
    val notes: String?,
    val timestampMillis: Long,
)

@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weightLbs: Double,
    val notes: String?,
    val timestampMillis: Long,
)
