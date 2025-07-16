// File: managers/PreferencesManager.kt
package com.money.derrite.managers

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import java.util.Locale

class PreferencesManager(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_ALERT_DISTANCE = "alert_distance"
        private const val KEY_VIEWED_ALERTS = "viewed_alerts"
        private const val KEY_SAVED_REPORTS = "saved_reports"
        private const val KEY_LANGUAGE_CHANGE = "is_language_change"

        // Alert distance constants
        const val ALERT_DISTANCE_1_MILE = 1609.0
        const val ALERT_DISTANCE_2_MILES = 3218.0
        const val ALERT_DISTANCE_3_MILES = 4827.0
        const val ALERT_DISTANCE_5_MILES = 8047.0
        const val ALERT_DISTANCE_ZIP_CODE = 8050.0
        const val ALERT_DISTANCE_STATE = 160934.0
    }

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Language preferences
    fun getSavedLanguage(): String {
        return preferences.getString(KEY_LANGUAGE, "es") ?: "es"
    }

    fun saveLanguage(languageCode: String) {
        preferences.edit().putString(KEY_LANGUAGE, languageCode).apply()
    }

    fun setAppLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    // Alert distance preferences
    fun getSavedAlertDistance(): Double {
        return preferences.getFloat(KEY_ALERT_DISTANCE, ALERT_DISTANCE_ZIP_CODE.toFloat()).toDouble()
    }

    fun saveAlertDistance(distance: Double) {
        preferences.edit().putFloat(KEY_ALERT_DISTANCE, distance.toFloat()).apply()
    }

    fun getAlertDistanceText(distance: Double): String {
        val isSpanish = getSavedLanguage() == "es"

        return when (distance) {
            ALERT_DISTANCE_1_MILE -> if (isSpanish) "1 milla" else "1 mile"
            ALERT_DISTANCE_2_MILES -> if (isSpanish) "2 millas" else "2 miles"
            ALERT_DISTANCE_3_MILES -> if (isSpanish) "3 millas" else "3 miles"
            ALERT_DISTANCE_5_MILES -> if (isSpanish) "5 millas" else "5 miles"
            ALERT_DISTANCE_ZIP_CODE -> if (isSpanish) "área de código postal" else "zip code area"
            ALERT_DISTANCE_STATE -> if (isSpanish) "todo el estado" else "state-wide"
            else -> if (isSpanish) "área de código postal (predeterminado)" else "zip code area (default)"
        }
    }

    // Viewed alerts
    fun getViewedAlerts(): Set<String> {
        return preferences.getStringSet(KEY_VIEWED_ALERTS, emptySet()) ?: emptySet()
    }

    fun saveViewedAlerts(viewedAlerts: Set<String>) {
        preferences.edit().putStringSet(KEY_VIEWED_ALERTS, viewedAlerts).apply()
    }

    // Reports persistence
    fun getSavedReports(): String {
        return preferences.getString(KEY_SAVED_REPORTS, "[]") ?: "[]"
    }

    fun saveReports(reportsJson: String) {
        preferences.edit().putString(KEY_SAVED_REPORTS, reportsJson).apply()
    }

    // Language change tracking
    fun isLanguageChange(): Boolean {
        return preferences.getBoolean(KEY_LANGUAGE_CHANGE, false)
    }

    fun setLanguageChange(isChange: Boolean) {
        preferences.edit().putBoolean(KEY_LANGUAGE_CHANGE, isChange).apply()
    }
}