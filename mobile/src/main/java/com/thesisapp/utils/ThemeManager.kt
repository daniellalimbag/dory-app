package com.thesisapp.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object ThemeManager {
    private const val PREF = "ui_prefs"
    private const val KEY_NIGHT_MODE = "night_mode"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getNightMode(context: Context): Int {
        val saved = prefs(context).getInt(KEY_NIGHT_MODE, Int.MIN_VALUE)
        return if (saved == Int.MIN_VALUE) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        } else {
            saved
        }
    }

    fun isDarkModeEnabled(context: Context): Boolean =
        getNightMode(context) == AppCompatDelegate.MODE_NIGHT_YES

    fun setDarkModeEnabled(context: Context, enabled: Boolean) {
        val mode = if (enabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }

        prefs(context).edit().putInt(KEY_NIGHT_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun applySavedTheme(context: Context) {
        AppCompatDelegate.setDefaultNightMode(getNightMode(context))
    }
}
