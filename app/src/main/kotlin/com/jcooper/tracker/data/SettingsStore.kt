package com.jcooper.tracker.data

import android.content.Context
import android.content.SharedPreferences

/** Plain SharedPreferences wrapper for the small set of user-editable numbers. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tracker_settings", Context.MODE_PRIVATE)

    var dailyCalorieTarget: Int
        get() = prefs.getInt(KEY_DAILY_TARGET, DEFAULT_DAILY_TARGET)
        set(value) = prefs.edit().putInt(KEY_DAILY_TARGET, value).apply()

    var plateauThresholdDays: Int
        get() = prefs.getInt(KEY_PLATEAU_DAYS, DEFAULT_PLATEAU_DAYS)
        set(value) = prefs.edit().putInt(KEY_PLATEAU_DAYS, value).apply()

    var plateauToleranceLbs: Float
        get() = prefs.getFloat(KEY_PLATEAU_TOLERANCE, DEFAULT_PLATEAU_TOLERANCE)
        set(value) = prefs.edit().putFloat(KEY_PLATEAU_TOLERANCE, value).apply()

    /** Stored only for reference; the app never acts on this value. */
    var deficitDefault: Int
        get() = prefs.getInt(KEY_DEFICIT_DEFAULT, DEFAULT_DEFICIT)
        set(value) = prefs.edit().putInt(KEY_DEFICIT_DEFAULT, value).apply()

    companion object {
        private const val KEY_DAILY_TARGET = "daily_calorie_target"
        private const val KEY_PLATEAU_DAYS = "plateau_threshold_days"
        private const val KEY_PLATEAU_TOLERANCE = "plateau_tolerance_lbs"
        private const val KEY_DEFICIT_DEFAULT = "deficit_default"

        const val DEFAULT_DAILY_TARGET = 2000
        const val DEFAULT_PLATEAU_DAYS = 14
        const val DEFAULT_PLATEAU_TOLERANCE = 0.5f
        const val DEFAULT_DEFICIT = 500
    }
}
