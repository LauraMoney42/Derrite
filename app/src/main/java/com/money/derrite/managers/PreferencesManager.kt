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
        private const val KEY_USER_HAS_CREATED_REPORTS = "user_has_created_reports"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_FAVORITE_ALERTS = "favorite_alerts"
        private const val KEY_VIEWED_FAVORITE_ALERTS = "viewed_favorite_alerts"
        private const val KEY_ALARM_OVERRIDE_SILENT = "alarm_override_silent"

        // Alert distance constants
        const val ALERT_DISTANCE_1_MILE = 1609.0
        const val ALERT_DISTANCE_2_MILES = 3218.0
        const val ALERT_DISTANCE_3_MILES = 4827.0
        const val ALERT_DISTANCE_5_MILES = 8047.0
        const val ALERT_DISTANCE_20_MILES = 32187.0
        const val ALERT_DISTANCE_STATE = 160934.0
    }

    // Make preferences accessible to ReportManager
    val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ... rest of existing methods stay exactly the same ...

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

    fun getAlarmOverrideSilent(): Boolean {
        return preferences.getBoolean(KEY_ALARM_OVERRIDE_SILENT, false)
    }

    fun saveAlarmOverrideSilent(override: Boolean) {
        preferences.edit().putBoolean(KEY_ALARM_OVERRIDE_SILENT, override).apply()
    }

    fun getSavedAlertDistance(): Double {
        return preferences.getFloat(KEY_ALERT_DISTANCE, ALERT_DISTANCE_20_MILES.toFloat()).toDouble()
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
            ALERT_DISTANCE_20_MILES -> if (isSpanish) "20 millas" else "20 miles"
            ALERT_DISTANCE_STATE -> if (isSpanish) "todo el estado" else "state-wide"
            else -> if (isSpanish) "área de código postal (predeterminado)" else "zip code area (default)"
        }
    }

    fun getViewedAlerts(): Set<String> {
        return preferences.getStringSet(KEY_VIEWED_ALERTS, emptySet()) ?: emptySet()
    }

    fun saveViewedAlerts(viewedAlerts: Set<String>) {
        preferences.edit().putStringSet(KEY_VIEWED_ALERTS, viewedAlerts).apply()
    }

    fun getSavedReports(): String {
        return preferences.getString(KEY_SAVED_REPORTS, "[]") ?: "[]"
    }

    fun saveReports(reportsJson: String) {
        preferences.edit().putString(KEY_SAVED_REPORTS, reportsJson).apply()
    }

    fun isLanguageChange(): Boolean {
        return preferences.getBoolean(KEY_LANGUAGE_CHANGE, false)
    }

    fun setLanguageChange(isChange: Boolean) {
        preferences.edit().putBoolean(KEY_LANGUAGE_CHANGE, isChange).apply()
    }

    fun getSavedFavorites(): String {
        return preferences.getString(KEY_FAVORITES, "[]") ?: "[]"
    }

    fun saveFavorites(favoritesJson: String) {
        preferences.edit().putString(KEY_FAVORITES, favoritesJson).apply()
    }

    fun getSavedFavoriteAlerts(): String {
        return preferences.getString(KEY_FAVORITE_ALERTS, "[]") ?: "[]"
    }

    fun saveFavoriteAlerts(favoriteAlertsJson: String) {
        preferences.edit().putString(KEY_FAVORITE_ALERTS, favoriteAlertsJson).apply()
    }

    fun getViewedFavoriteAlerts(): Set<String> {
        return preferences.getStringSet(KEY_VIEWED_FAVORITE_ALERTS, emptySet()) ?: emptySet()
    }

    fun saveViewedFavoriteAlerts(viewedFavoriteAlerts: Set<String>) {
        preferences.edit().putStringSet(KEY_VIEWED_FAVORITE_ALERTS, viewedFavoriteAlerts).apply()
    }

    fun hasUserCreatedReports(): Boolean {
        return preferences.getBoolean(KEY_USER_HAS_CREATED_REPORTS, false)
    }

    fun setUserHasCreatedReports(hasCreated: Boolean) {
        preferences.edit().putBoolean(KEY_USER_HAS_CREATED_REPORTS, hasCreated).apply()
    }
}