package com.jcooper.tracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Insert
    suspend fun insertAll(entries: List<FoodEntry>): List<Long>

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM food_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<FoodEntry>>

    @Query("SELECT * FROM food_entries ORDER BY timestampMillis ASC")
    suspend fun getAll(): List<FoodEntry>

    @Query("SELECT * FROM food_entries ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodEntry>>
}

@Dao
interface WeightEntryDao {
    @Insert
    suspend fun insert(entry: WeightEntry): Long

    @Insert
    suspend fun insertAll(entries: List<WeightEntry>): List<Long>

    @Update
    suspend fun update(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY timestampMillis ASC")
    suspend fun getAll(): List<WeightEntry>

    @Query("SELECT * FROM weight_entries ORDER BY timestampMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WeightEntry>>
}
