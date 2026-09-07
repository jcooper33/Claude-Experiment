package com.jcooper.tracker.data

import android.content.Context
import com.jcooper.tracker.logic.FoodLogRecord
import com.jcooper.tracker.logic.WeightLogRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Single point of contact between the UI and Room; also maps to/from the plain
 * records the (Android-independent) :logic module works with. */
class TrackerRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val foodDao = db.foodEntryDao()
    private val weightDao = db.weightEntryDao()

    fun observeFoodEntries(): Flow<List<FoodEntry>> = foodDao.observeAll()
    fun observeRecentFoodEntries(limit: Int = 20): Flow<List<FoodEntry>> = foodDao.observeRecent(limit)
    fun observeWeightEntries(): Flow<List<WeightEntry>> = weightDao.observeAll()
    fun observeRecentWeightEntries(limit: Int = 20): Flow<List<WeightEntry>> = weightDao.observeRecent(limit)

    suspend fun logFood(description: String, calories: Int, notes: String?, timestampMillis: Long): Long =
        foodDao.insert(FoodEntry(description = description, calories = calories, notes = notes, timestampMillis = timestampMillis))

    suspend fun logWeight(weightLbs: Double, notes: String?, timestampMillis: Long): Long =
        weightDao.insert(WeightEntry(weightLbs = weightLbs, notes = notes, timestampMillis = timestampMillis))

    suspend fun deleteFood(entry: FoodEntry) = foodDao.delete(entry)
    suspend fun deleteWeight(entry: WeightEntry) = weightDao.delete(entry)

    suspend fun allFoodRecords(): List<FoodLogRecord> = foodDao.getAll().map { it.toRecord() }
    suspend fun allWeightRecords(): List<WeightLogRecord> = weightDao.getAll().map { it.toRecord() }

    fun observeAllFoodRecords(): Flow<List<FoodLogRecord>> = foodDao.observeAll().map { list -> list.map { it.toRecord() } }
    fun observeAllWeightRecords(): Flow<List<WeightLogRecord>> = weightDao.observeAll().map { list -> list.map { it.toRecord() } }
}

fun FoodEntry.toRecord() = FoodLogRecord(id, description, calories, notes, timestampMillis)
fun WeightEntry.toRecord() = WeightLogRecord(id, weightLbs, notes, timestampMillis)
