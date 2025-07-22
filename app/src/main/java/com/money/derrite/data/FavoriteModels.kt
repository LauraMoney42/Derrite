// File: data/FavoriteModels.kt
package com.money.derrite.data

import org.osmdroid.util.GeoPoint
import java.util.UUID

data class FavoritePlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val location: GeoPoint,
    val alertDistance: Double = 1609.0, // 1 mile default
    val enableSafetyAlerts: Boolean = true, // Always true for safety-only app
    val createdAt: Long = System.currentTimeMillis()
) {

    /**
     * Get alert distance text based on language
     */
    fun getAlertDistanceText(isSpanish: Boolean): String {
        return when (alertDistance) {
            1609.0 -> if (isSpanish) "1 milla" else "1 mile"
            3218.0 -> if (isSpanish) "2 millas" else "2 miles"
            4827.0 -> if (isSpanish) "3 millas" else "3 miles"
            8047.0 -> if (isSpanish) "5 millas" else "5 miles"
            32187.0 -> if (isSpanish) "20 millas" else "20 miles"
            160934.0 -> if (isSpanish) "todo el estado" else "state-wide"
            else -> if (isSpanish) "distancia personalizada" else "custom distance"
        }
    }

    /**
     * Get enabled alerts text - always Safety for this app
     */
    fun getEnabledAlertsText(isSpanish: Boolean): String {
        return if (isSpanish) "Seguridad" else "Safety"
    }

    /**
     * Get enabled categories text - always Safety
     */
    fun getEnabledCategoriesText(isSpanish: Boolean): String {
        return getEnabledAlertsText(isSpanish)
    }

    /**
     * Check if this favorite should receive alerts - always true for safety
     */
    fun shouldReceiveAlert(category: ReportCategory): Boolean {
        return category == ReportCategory.SAFETY && enableSafetyAlerts
    }

    /**
     * Check if should alert for category - always true for safety
     */
    fun shouldAlertForCategory(category: ReportCategory): Boolean {
        return shouldReceiveAlert(category)
    }
}

data class FavoriteAlert(
    val id: String = UUID.randomUUID().toString(),
    val favoritePlace: FavoritePlace,
    val report: Report,
    val distanceFromFavorite: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val isViewed: Boolean = false
) {
    fun getAlertMessage(isSpanish: Boolean): String {
        val distance = String.format("%.1f", distanceFromFavorite / 1609.0)
        return if (isSpanish) {
            "Nueva alerta de seguridad en ${favoritePlace.name} (${distance} millas)"
        } else {
            "New safety alert at ${favoritePlace.name} (${distance} miles)"
        }
    }

    val category: ReportCategory get() = report.category
    val content: String get() = report.originalText
}