package com.brainunplug.app.util

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("unplug_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_REEL_COUNT = "key_reel_count"
        private const val KEY_LAST_DATE = "key_last_date"
        private const val KEY_DAILY_LIMIT = "key_daily_limit"
        private const val KEY_STREAK = "key_streak"
        private const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"
        private const val KEY_BLOCK_ENABLED = "key_block_enabled"
    }

    private fun getTodayDateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }

    fun getTodayReels(): Int {
        checkDayReset()
        return prefs.getInt(KEY_REEL_COUNT, 0)
    }

    fun incrementReel(): Int {
        checkDayReset()
        val newCount = getTodayReels() + 1
        prefs.edit().putInt(KEY_REEL_COUNT, newCount).apply()
        return newCount
    }

    fun resetToday() {
        prefs.edit().putInt(KEY_REEL_COUNT, 0).apply()
    }

    fun getDailyLimit(): Int {
        return prefs.getInt(KEY_DAILY_LIMIT, 50)
    }

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, limit).apply()
    }

    fun getStreak(): Int {
        return prefs.getInt(KEY_STREAK, 1)
    }

    fun isOverlayEnabled(): Boolean {
        return prefs.getBoolean(KEY_OVERLAY_ENABLED, true)
    }

    fun setOverlayEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, enabled).apply()
    }

    private fun checkDayReset() {
        val today = getTodayDateString()
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        if (lastDate != today) {
            // New day detected!
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_REEL_COUNT, 0)
                .apply()
        }
    }
}
