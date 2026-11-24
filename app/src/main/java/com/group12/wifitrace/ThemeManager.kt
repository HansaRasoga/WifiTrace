package com.group12.wifitrace

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** Remembers the user's theme choice (System / Light / Dark) and applies it app-wide. */
object ThemeManager {

    private const val PREFS = "wifitrace_prefs"
    private const val KEY_MODE = "theme_mode"

    fun saved(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    /** Call once at app start. */
    fun apply(context: Context) {
        AppCompatDelegate.setDefaultNightMode(saved(context))
    }

    /** Saves the choice and switches immediately (the activity recreates itself). */
    fun set(context: Context, mode: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MODE, mode)
            .apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
