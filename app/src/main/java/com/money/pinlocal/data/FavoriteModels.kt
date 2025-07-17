// File: data/FavoriteModels.kt (FIXED)
package com.money.pinlocal.data

import org.osmdroid.util.GeoPoint
import java.util.UUID

data class FavoritePlace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "", // ADDED: Missing description field
    val location: GeoPoint,
    val alertDistance: Double = 1609.0, // 1 mile default
    val enableSafetyAlerts: Boolean = true,
    val enableFunAlerts: Boolean = false,
    val enableLostAlerts: Boolean = true,
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
            8050.0 -> if (isSpanish) "área de código postal" else "zip code area"
            160934.0 -> if (isSpanish) "todo el estado" else "state-wide"
            else -> if (isSpanish) "distancia personalizada" else "custom distance"
        }
    }

    /**
     * Get enabled alerts text based on language
     */
    fun getEnabledAlertsText(isSpanish: Boolean): String {
        val enabledAlerts = mutableListOf<String>()

        if (enableSafetyAlerts) {
            enabledAlerts.add(if (isSpanish) "Seguridad" else "Safety")
        }
        if (enableFunAlerts) {
            enabledAlerts.add(if (isSpanish) "Diversión" else "Fun")
        }
        if (enableLostAlerts) {
            enabledAlerts.add(if (isSpanish) "Perdidos" else "Lost")
        }

        return if (enabledAlerts.isEmpty()) {
            if (isSpanish) "Ninguna" else "None"
        } else {
            enabledAlerts.joinToString(", ")
        }
    }

    /**
     * Get enabled categories text based on language
     */
    fun getEnabledCategoriesText(isSpanish: Boolean): String {
        return getEnabledAlertsText(isSpanish)
    }

    /**
     * Check if this favorite should receive alerts for a specific category
     */
    fun shouldReceiveAlert(category: ReportCategory): Boolean {
        return when (category) {
            ReportCategory.SAFETY -> enableSafetyAlerts
            ReportCategory.FUN -> enableFunAlerts
            ReportCategory.LOST_MISSING -> enableLostAlerts
        }
    }

    // ADDED: Missing method
    fun shouldAlertForCategory(category: ReportCategory): Boolean {
        return shouldReceiveAlert(category)
    }
}

data class FavoriteAlert(
    val id: String = UUID.randomUUID().toString(),
    val favoritePlace: FavoritePlace, // FIXED: Changed to FavoritePlace object
    val report: Report, // FIXED: Changed to Report object
    val distanceFromFavorite: Double, // FIXED: Renamed field
    val timestamp: Long = System.currentTimeMillis(),
    val isViewed: Boolean = false
) {
    // ADDED: Missing methods
    fun getAlertMessage(isSpanish: Boolean): String {
        val distance = String.format("%.1f", distanceFromFavorite / 1609.0) // Convert to miles
        return if (isSpanish) {
            "Nueva alerta en ${favoritePlace.name} (${distance} millas)"
        } else {
            "New alert at ${favoritePlace.name} (${distance} miles)"
        }
    }

    val category: ReportCategory get() = report.category
    val content: String get() = report.originalText
}