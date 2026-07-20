package com.example.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("auto_input_tester_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_INPUT_X = "input_x"
        private const val KEY_INPUT_Y = "input_y"
        private const val KEY_SUBMIT_X = "submit_x"
        private const val KEY_SUBMIT_Y = "submit_y"
        
        private const val KEY_INPUT_TYPE = "input_type"
        private const val KEY_MIN_LENGTH = "min_length"
        private const val KEY_MAX_LENGTH = "max_length"
        private const val KEY_EXACT_LENGTH_ENABLED = "exact_length_enabled"
        private const val KEY_EXACT_LENGTH = "exact_length"
        
        private const val KEY_WORKER_COUNT = "worker_count"
        private const val KEY_DELAY_MS = "delay_ms"
        private const val KEY_ULTRA_FAST = "ultra_fast"
        
        private const val KEY_DISCLAIMER_AGREED = "disclaimer_agreed"
        private const val KEY_SAFETY_MAX_ATTEMPTS = "safety_max_attempts"
        private const val KEY_SAFETY_TIMEOUT_MINS = "safety_timeout_mins"
    }

    var inputX: Float
        get() = prefs.getFloat(KEY_INPUT_X, -1f)
        set(value) = prefs.edit().putFloat(KEY_INPUT_X, value).apply()

    var inputY: Float
        get() = prefs.getFloat(KEY_INPUT_Y, -1f)
        set(value) = prefs.edit().putFloat(KEY_INPUT_Y, value).apply()

    var submitX: Float
        get() = prefs.getFloat(KEY_SUBMIT_X, -1f)
        set(value) = prefs.edit().putFloat(KEY_SUBMIT_X, value).apply()

    var submitY: Float
        get() = prefs.getFloat(KEY_SUBMIT_Y, -1f)
        set(value) = prefs.edit().putFloat(KEY_SUBMIT_Y, value).apply()

    var inputType: String
        get() = prefs.getString(KEY_INPUT_TYPE, "NUMBERS_ONLY") ?: "NUMBERS_ONLY"
        set(value) = prefs.edit().putString(KEY_INPUT_TYPE, value).apply()

    var minLength: Int
        get() = prefs.getInt(KEY_MIN_LENGTH, 4)
        set(value) = prefs.edit().putInt(KEY_MIN_LENGTH, value).apply()

    var maxLength: Int
        get() = prefs.getInt(KEY_MAX_LENGTH, 6)
        set(value) = prefs.edit().putInt(KEY_MAX_LENGTH, value).apply()

    var exactLengthEnabled: Boolean
        get() = prefs.getBoolean(KEY_EXACT_LENGTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_EXACT_LENGTH_ENABLED, value).apply()

    var exactLength: Int
        get() = prefs.getInt(KEY_EXACT_LENGTH, 4)
        set(value) = prefs.edit().putInt(KEY_EXACT_LENGTH, value).apply()

    var workerCount: Int
        get() = prefs.getInt(KEY_WORKER_COUNT, 4)
        set(value) = prefs.edit().putInt(KEY_WORKER_COUNT, value).apply()

    var delayMs: Long
        get() = prefs.getLong(KEY_DELAY_MS, 300L)
        set(value) = prefs.edit().putLong(KEY_DELAY_MS, value).apply()

    var ultraFastEnabled: Boolean
        get() = prefs.getBoolean(KEY_ULTRA_FAST, false)
        set(value) = prefs.edit().putBoolean(KEY_ULTRA_FAST, value).apply()

    var disclaimerAgreed: Boolean
        get() = prefs.getBoolean(KEY_DISCLAIMER_AGREED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISCLAIMER_AGREED, value).apply()

    var safetyMaxAttempts: Int
        get() = prefs.getInt(KEY_SAFETY_MAX_ATTEMPTS, 5000)
        set(value) = prefs.edit().putInt(KEY_SAFETY_MAX_ATTEMPTS, value).apply()

    var safetyTimeoutMins: Int
        get() = prefs.getInt(KEY_SAFETY_TIMEOUT_MINS, 10)
        set(value) = prefs.edit().putInt(KEY_SAFETY_TIMEOUT_MINS, value).apply()

    fun isTargetSetup(): Boolean {
        return inputX >= 0 && inputY >= 0 && submitX >= 0 && submitY >= 0
    }

    fun clearCoordinates() {
        prefs.edit()
            .remove(KEY_INPUT_X)
            .remove(KEY_INPUT_Y)
            .remove(KEY_SUBMIT_X)
            .remove(KEY_SUBMIT_Y)
            .apply()
    }
}
