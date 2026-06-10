package com.example.expensereader.util

import android.content.Context

object SmartSavingPrefs {
    private const val PREFS = "smart_saving_prefs"
    private const val KEY_STARTED = "smart_saving_started"

    fun isStarted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, 0).getBoolean(KEY_STARTED, false)

    fun setStarted(context: Context, started: Boolean) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_STARTED, started).apply()
    }
}
