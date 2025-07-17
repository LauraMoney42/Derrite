// File: managers/FavoriteManager.kt
package com.money.pinlocal.managers

import com.money.pinlocal.BackendClient
import com.money.pinlocal.data.FavoritePlace
import com.money.pinlocal.data.FavoriteAlert
import com.money.pinlocal.data.Report
import org.osmdroid.util.GeoPoint

class FavoriteManager(
    private val preferencesManager: PreferencesManager,
    private val reportManager: ReportManager,
    private val locationManager: LocationManager,
    private val backendClient: BackendClient
) {

    private val favoritePlaces = mutableListOf<FavoritePlace>()
    private val favoriteAlerts = mutableListOf<FavoriteAlert>()
    private val viewedFavoriteAlertIds = mutableSetOf<String>()

    interface FavoriteListener {
        fun onFavoritesUpdated(favorites: List<FavoritePlace>)
        fun onFavoriteAlertsUpdated(alerts: List<FavoriteAlert>, hasUnviewed: Boolean)
        fun onNewFavoriteAlerts(alerts: List<FavoriteAlert>)
    }

    private var favoriteListener: FavoriteListener? = null

    fun setFavoriteListener(listener: FavoriteListener) {
        this.favoriteListener = listener
    }

    // Load saved favorites and alerts
    fun loadSavedData() {
        try {
            val favoritesJson = preferencesManager.getSavedFavorites()
            val favoritesList = parseFavoritesFromJson(favoritesJson)
            favoritePlaces.clear()
            favoritePlaces.addAll(favoritesList)

            val viewedSet = preferencesManager.getViewedFavoriteAlerts()
            viewedFavoriteAlertIds.clear()
            viewedFavoriteAlertIds.addAll(viewedSet)

            favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        } catch (e: Exception) {
            android.util.Log.e("FavoriteManager", "Error loading favorites: ${e.message}")
        }
    }

    // Add a new favorite place
    fun addFavorite(
        name: String,
        location: GeoPoint,
        alertDistance: Double = 1609.0,
        enableSafety: Boolean = true,
        enableFun: Boolean = false,
        enableLost: Boolean = true
    ): FavoritePlace {
        val favorite = FavoritePlace(
            name = name,
            location = location,
            alertDistance = alertDistance,
            enableSafetyAlerts = enableSafety,
            enableFunAlerts = enableFun,
            enableLostAlerts = enableLost
        )

        favoritePlaces.add(favorite)
        saveFavoritesToPreferences()

        // Subscribe to alerts for this location
        subscribeToFavoriteAlerts(favorite)

        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        return favorite
    }

    // Update an existing favorite
    fun updateFavorite(
        favoriteId: String,
        name: String? = null,
        alertDistance: Double? = null,
        enableSafety: Boolean? = null,
        enableFun: Boolean? = null,
        enableLost: Boolean? = null
    ): Boolean {
        val index = favoritePlaces.indexOfFirst { it.id == favoriteId }
        if (index == -1) return false

        val currentFavorite = favoritePlaces[index]
        val updatedFavorite = currentFavorite.copy(
            name = name ?: currentFavorite.name,
            alertDistance = alertDistance ?: currentFavorite.alertDistance,
            enableSafetyAlerts = enableSafety ?: currentFavorite.enableSafetyAlerts,
            enableFunAlerts = enableFun ?: currentFavorite.enableFunAlerts,
            enableLostAlerts = enableLost ?: currentFavorite.enableLostAlerts
        )

        favoritePlaces[index] = updatedFavorite
        saveFavoritesToPreferences()

        // Re-subscribe with new settings
        subscribeToFavoriteAlerts(updatedFavorite)

        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        return true
    }

    // Remove a favorite place
    fun removeFavorite(favoriteId: String): Boolean {
        val favorite = favoritePlaces.find { it.id == favoriteId } ?: return false

        favoritePlaces.removeAll { it.id == favoriteId }
        favoriteAlerts.removeAll { it.favoritePlace.id == favoriteId }

        saveFavoritesToPreferences()
        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())

        return true
    }

    // Get all favorite places
    fun getFavorites(): List<FavoritePlace> = favoritePlaces.toList()

    // Get favorite by ID
    fun getFavoriteById(id: String): FavoritePlace? = favoritePlaces.find { it.id == id }

    // Check for new alerts near favorite places
    fun checkForFavoriteAlerts() {
        val newAlerts = mutableListOf<FavoriteAlert>()

        for (favorite in favoritePlaces) {
            for (report in reportManager.getActiveReports()) {
                // Skip if this favorite doesn't want alerts for this category
                if (!favorite.shouldAlertForCategory(report.category)) continue

                val distance = locationManager.calculateDistance(
                    favorite.location.latitude, favorite.location.longitude,
                    report.location.latitude, report.location.longitude
                )

                if (distance <= favorite.alertDistance) {
                    // Check if we already have an alert for this report/favorite combo
                    val existingAlert = favoriteAlerts.find {
                        it.report.id == report.id && it.favoritePlace.id == favorite.id
                    }

                    if (existingAlert == null) {
                        val alert = FavoriteAlert(
                            favoritePlace = favorite,
                            report = report,
                            distanceFromFavorite = distance,
                            isViewed = viewedFavoriteAlertIds.contains(report.id + favorite.id)
                        )
                        newAlerts.add(alert)
                        favoriteAlerts.add(alert)
                    }
                }
            }
        }

        if (newAlerts.isNotEmpty()) {
            val unviewedNewAlerts = newAlerts.filter {
                !it.isViewed && !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
            }

            if (unviewedNewAlerts.isNotEmpty()) {
                favoriteListener?.onNewFavoriteAlerts(unviewedNewAlerts)
            }
        }

        val hasUnviewed = favoriteAlerts.any {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), hasUnviewed)
    }

    fun addFavorite(favoritePlace: FavoritePlace) {
        favoritePlaces.add(favoritePlace)
        saveFavoritesToPreferences()
        subscribeToFavoriteAlerts(favoritePlace)
        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
    }

    fun createFavoriteFromLocation(
        location: org.osmdroid.util.GeoPoint,
        name: String,
        description: String,
        categories: Set<com.money.pinlocal.data.ReportCategory>,
        alertDistance: Double
    ): FavoritePlace {
        return FavoritePlace(
            name = name,
            description = description,
            location = location,
            alertDistance = alertDistance,
            enableSafetyAlerts = categories.contains(com.money.pinlocal.data.ReportCategory.SAFETY),
            enableFunAlerts = categories.contains(com.money.pinlocal.data.ReportCategory.FUN),
            enableLostAlerts = categories.contains(com.money.pinlocal.data.ReportCategory.LOST_MISSING)
        )
    }

    fun getFavoriteAlerts(favoriteId: String): List<FavoriteAlert> {
        return favoriteAlerts.filter { it.favoritePlace.id == favoriteId }
    }

    fun markFavoriteAlertsAsViewed(favoriteId: String) {
        for (alert in favoriteAlerts) {
            if (alert.favoritePlace.id == favoriteId) {
                viewedFavoriteAlertIds.add(alert.report.id + alert.favoritePlace.id)
            }
        }
        saveViewedFavoriteAlerts()

        val hasUnviewed = favoriteAlerts.any {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), hasUnviewed)
    }

    fun updateFavorite(updatedFavorite: FavoritePlace) {
        val index = favoritePlaces.indexOfFirst { it.id == updatedFavorite.id }
        if (index != -1) {
            favoritePlaces[index] = updatedFavorite
            saveFavoritesToPreferences()
            favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        }
    }
    // Get unviewed favorite alerts
    fun getUnviewedFavoriteAlerts(): List<FavoriteAlert> {
        return favoriteAlerts.filter {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }.sortedBy { it.distanceFromFavorite }
    }

    // Mark favorite alert as viewed
    fun markFavoriteAlertAsViewed(reportId: String, favoriteId: String) {
        val alertKey = reportId + favoriteId
        viewedFavoriteAlertIds.add(alertKey)
        saveViewedFavoriteAlerts()

        val hasUnviewed = favoriteAlerts.any {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), hasUnviewed)
    }

    // Mark all favorite alerts as viewed
    fun markAllFavoriteAlertsAsViewed() {
        for (alert in favoriteAlerts) {
            viewedFavoriteAlertIds.add(alert.report.id + alert.favoritePlace.id)
        }
        saveViewedFavoriteAlerts()
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), false)
    }

    // Remove alerts for expired reports
    fun removeAlertsForExpiredReports(expiredReports: List<Report>) {
        val expiredReportIds = expiredReports.map { it.id }.toSet()
        favoriteAlerts.removeAll { it.report.id in expiredReportIds }

        val hasUnviewed = favoriteAlerts.any {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), hasUnviewed)
    }

    // Subscribe to alerts for a favorite location
    private fun subscribeToFavoriteAlerts(favorite: FavoritePlace) {
        backendClient.subscribeToAlerts(
            latitude = favorite.location.latitude,
            longitude = favorite.location.longitude
        ) { success, message ->
            // Silent subscription - we don't need to show status for favorite subscriptions
        }
    }

    // Subscribe to all favorites
    fun subscribeToAllFavorites() {
        for (favorite in favoritePlaces) {
            subscribeToFavoriteAlerts(favorite)
        }
    }

    // Get alert summary message
    fun getFavoriteAlertSummaryMessage(newAlerts: List<FavoriteAlert>, isSpanish: Boolean): String {
        return if (newAlerts.size == 1) {
            val alert = newAlerts.first()
            alert.getAlertMessage(isSpanish)
        } else {
            if (isSpanish) {
                "${newAlerts.size} nuevas alertas en lugares favoritos"
            } else {
                "${newAlerts.size} new alerts at favorite places"
            }
        }
    }

    // Persistence methods
    private fun saveFavoritesToPreferences() {
        try {
            val favoritesJson = convertFavoritesToJson(favoritePlaces)
            preferencesManager.saveFavorites(favoritesJson)
        } catch (e: Exception) {
            android.util.Log.e("FavoriteManager", "Error saving favorites: ${e.message}")
        }
    }

    private fun saveViewedFavoriteAlerts() {
        preferencesManager.saveViewedFavoriteAlerts(viewedFavoriteAlertIds)
    }

    private fun parseFavoritesFromJson(json: String): List<FavoritePlace> {
        val favorites = mutableListOf<FavoritePlace>()
        try {
            if (json.isBlank() || json == "[]") {
                return favorites
            }

            val lines = json.split("|||")
            for (line in lines) {
                try {
                    if (line.trim().isEmpty()) continue

                    val parts = line.split(":::")
                    if (parts.size >= 8) {
                        val favorite = FavoritePlace(
                            id = parts[0],
                            name = parts[1],
                            location = GeoPoint(
                                parts[2].toDoubleOrNull() ?: 0.0,
                                parts[3].toDoubleOrNull() ?: 0.0
                            ),
                            alertDistance = parts[4].toDoubleOrNull() ?: 1609.0,
                            enableSafetyAlerts = parts[5].toBooleanStrictOrNull() ?: true,
                            enableFunAlerts = parts[6].toBooleanStrictOrNull() ?: false,
                            enableLostAlerts = parts[7].toBooleanStrictOrNull() ?: true,
                            createdAt = if (parts.size > 8) parts[8].toLongOrNull() ?: System.currentTimeMillis()
                            else System.currentTimeMillis()
                        )
                        favorites.add(favorite)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            // Return empty list if parsing fails completely
        }
        return favorites
    }

    private fun convertFavoritesToJson(favorites: List<FavoritePlace>): String {
        return favorites.joinToString("|||") { favorite ->
            "${favorite.id}:::${favorite.name}:::${favorite.location.latitude}:::${favorite.location.longitude}:::${favorite.alertDistance}:::${favorite.enableSafetyAlerts}:::${favorite.enableFunAlerts}:::${favorite.enableLostAlerts}:::${favorite.createdAt}"
        }
    }
}