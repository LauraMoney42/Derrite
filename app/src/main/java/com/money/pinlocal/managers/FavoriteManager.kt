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

    fun setFavoriteListener(listener: FavoriteListener?) {
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
        subscribeToFavoriteAlerts(favorite)
        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        return favorite
    }

    fun addFavorite(favoritePlace: FavoritePlace) {
        favoritePlaces.add(favoritePlace)
        saveFavoritesToPreferences()
        subscribeToFavoriteAlerts(favoritePlace)
        favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
    }

    // CRITICAL: Remove a favorite place with enhanced logging
    fun removeFavorite(favoriteId: String): Boolean {
        android.util.Log.d("FavoriteManager", "=== REMOVING FAVORITE ===")
        android.util.Log.d("FavoriteManager", "Favorite ID to remove: $favoriteId")
        android.util.Log.d("FavoriteManager", "Current favorites count: ${favoritePlaces.size}")

        val favorite = favoritePlaces.find { it.id == favoriteId }
        if (favorite == null) {
            android.util.Log.e("FavoriteManager", "Favorite not found with ID: $favoriteId")
            return false
        }

        android.util.Log.d("FavoriteManager", "Removing favorite: ${favorite.name}")

        // Remove from favorites list
        val removed = favoritePlaces.removeAll { it.id == favoriteId }

        // Remove associated alerts
        favoriteAlerts.removeAll { it.favoritePlace.id == favoriteId }

        // Remove from viewed alerts
        viewedFavoriteAlertIds.removeAll { alertId ->
            alertId.endsWith(favoriteId)
        }

        if (removed) {
            // Save to preferences immediately
            saveFavoritesToPreferences()
            saveViewedFavoriteAlerts()

            android.util.Log.d("FavoriteManager", "Successfully removed favorite: ${favorite.name}")
            android.util.Log.d("FavoriteManager", "Remaining favorites count: ${favoritePlaces.size}")

            // Notify listeners AFTER removal is complete
            favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())

            android.util.Log.d("FavoriteManager", "Notified listeners of favorites update")
        }

        return removed
    }

    // Subscribe to backend for background notifications
    private fun subscribeToFavoriteAlerts(favorite: FavoritePlace) {
        backendClient.subscribeToAlerts(
            latitude = favorite.location.latitude,
            longitude = favorite.location.longitude
        ) { success, message ->
            android.util.Log.d("FavoriteManager", "Subscribed to alerts for ${favorite.name}: $success")
        }
    }

    // Subscribe all favorites on app startup
    fun subscribeToAllFavorites() {
        for (favorite in favoritePlaces) {
            subscribeToFavoriteAlerts(favorite)
        }
        android.util.Log.d("FavoriteManager", "Subscribed to all ${favoritePlaces.size} favorites")
    }

    // Get all favorite places
    fun getFavorites(): List<FavoritePlace> = favoritePlaces.toList()

    // Get favorite by ID
    fun getFavoriteById(id: String): FavoritePlace? = favoritePlaces.find { it.id == id }

    // Create favorite from location
    fun createFavoriteFromLocation(
        location: GeoPoint,
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

    // Check for new alerts near favorite places
    fun checkForFavoriteAlerts() {
        android.util.Log.d("FavoriteManager", "=== CHECKING FOR FAVORITE ALERTS ===")

        // PRIVACY-SAFE: Simple time-based check - don't process alerts if we recently created a report
        if (reportManager.shouldSkipAlertsCheck()) {
            android.util.Log.d("FavoriteManager", "⏰ Skipping favorite alert check - recent report created")
            return
        }

        val newAlerts = mutableListOf<FavoriteAlert>()

        for (favorite in favoritePlaces) {
            android.util.Log.d("FavoriteManager", "Checking favorite: ${favorite.name}")

            for (report in reportManager.getActiveReports()) {
                // Skip if this favorite doesn't want alerts for this category
                if (!favorite.shouldAlertForCategory(report.category)) {
                    continue
                }

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
                        android.util.Log.d("FavoriteManager", "✅ Creating favorite alert for report: ${report.id} at ${favorite.name}")

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

        android.util.Log.d("FavoriteManager", "New favorite alerts created: ${newAlerts.size}")

        if (newAlerts.isNotEmpty()) {
            val unviewedNewAlerts = newAlerts.filter {
                !it.isViewed && !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
            }

            if (unviewedNewAlerts.isNotEmpty()) {
                android.util.Log.d("FavoriteManager", "🔔 Triggering ${unviewedNewAlerts.size} favorite alerts")
                favoriteListener?.onNewFavoriteAlerts(unviewedNewAlerts)
            }
        }

        val hasUnviewed = favoriteAlerts.any {
            !viewedFavoriteAlertIds.contains(it.report.id + it.favoritePlace.id)
        }
        favoriteListener?.onFavoriteAlertsUpdated(favoriteAlerts.toList(), hasUnviewed)

        android.util.Log.d("FavoriteManager", "=== END FAVORITE ALERT CHECK ===")
    }
    // Get favorite alerts
    fun getFavoriteAlerts(favoriteId: String): List<FavoriteAlert> {
        return favoriteAlerts.filter { it.favoritePlace.id == favoriteId }
    }

    // Mark favorite alerts as viewed
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

    // Update an existing favorite
    fun updateFavorite(updatedFavorite: FavoritePlace) {
        val index = favoritePlaces.indexOfFirst { it.id == updatedFavorite.id }
        if (index != -1) {
            favoritePlaces[index] = updatedFavorite
            saveFavoritesToPreferences()
            favoriteListener?.onFavoritesUpdated(favoritePlaces.toList())
        }
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
                        val description = if (parts.size > 9) parts[9] else ""

                        val favorite = FavoritePlace(
                            id = parts[0],
                            name = parts[1],
                            description = description,
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
            "${favorite.id}:::${favorite.name}:::${favorite.location.latitude}:::${favorite.location.longitude}:::${favorite.alertDistance}:::${favorite.enableSafetyAlerts}:::${favorite.enableFunAlerts}:::${favorite.enableLostAlerts}:::${favorite.createdAt}:::${favorite.description}"
        }
    }
}