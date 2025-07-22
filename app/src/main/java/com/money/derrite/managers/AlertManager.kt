// File: managers/AlertManager.kt - PRIVACY-SAFE VERSION
package com.money.derrite.managers

import android.location.Location
import com.money.derrite.data.Alert
import com.money.derrite.data.Report
import java.util.UUID

class AlertManager(
    private val preferencesManager: PreferencesManager,
    private val reportManager: ReportManager,
    private val locationManager: LocationManager
) {

    private val activeAlerts = mutableListOf<Alert>()
    private val viewedAlertIds = mutableSetOf<String>()

    interface AlertListener {
        fun onNewAlerts(alerts: List<Alert>)
        fun onAlertsUpdated(hasUnviewed: Boolean)
    }

    private var alertListener: AlertListener? = null

    fun setAlertListener(listener: AlertListener) {
        this.alertListener = listener
    }

    fun loadViewedAlerts() {
        val viewedSet = preferencesManager.getViewedAlerts()
        viewedAlertIds.clear()
        viewedAlertIds.addAll(viewedSet)
    }

    fun checkForNewAlerts(userLocation: Location) {
        try {
            android.util.Log.d("AlertManager", "=== CHECKING FOR NEW ALERTS ===")

            // PRIVACY-SAFE: Simple time-based check - don't process alerts if we recently created a report
            if (reportManager.shouldSkipAlertsCheck()) {
                android.util.Log.d("AlertManager", "⏰ Skipping alert check - recent report created")
                return
            }

            android.util.Log.d("AlertManager", "User location: ${userLocation.latitude}, ${userLocation.longitude}")

            val newAlerts = mutableListOf<Alert>()
            val userAlertDistance = preferencesManager.getSavedAlertDistance()
            android.util.Log.d("AlertManager", "Alert distance: $userAlertDistance meters")

            val allReports = reportManager.getActiveReports()
            android.util.Log.d("AlertManager", "Total active reports: ${allReports.size}")

            for (report in allReports) {
                try {
                    android.util.Log.d("AlertManager", "Checking report: ${report.id} - ${report.category.name}")

                    val distance = locationManager.calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        report.location.latitude, report.location.longitude
                    )
                    android.util.Log.d("AlertManager", "Distance: $distance meters (threshold: $userAlertDistance)")

                    if (distance <= userAlertDistance) {
                        android.util.Log.d("AlertManager", "Report is within alert distance!")

                        val existingAlert = activeAlerts.find { it.report.id == report.id }
                        if (existingAlert == null) {
                            android.util.Log.d("AlertManager", "✅ Creating new alert for report: ${report.id}")

                            val alert = Alert(
                                id = UUID.randomUUID().toString(),
                                report = report,
                                distanceFromUser = distance,
                                isViewed = viewedAlertIds.contains(report.id)
                            )
                            newAlerts.add(alert)
                            activeAlerts.add(alert)
                        } else {
                            android.util.Log.d("AlertManager", "Alert already exists for report: ${report.id}")
                        }
                    } else {
                        android.util.Log.d("AlertManager", "Report is outside alert distance")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AlertManager", "Error processing report ${report.id}: ${e.message}")
                    continue
                }
            }

            android.util.Log.d("AlertManager", "New alerts created: ${newAlerts.size}")

            val hasUnviewed = activeAlerts.any { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
            android.util.Log.d("AlertManager", "Has unviewed alerts: $hasUnviewed")
            alertListener?.onAlertsUpdated(hasUnviewed)

            if (newAlerts.isNotEmpty()) {
                val unviewedNewAlerts = newAlerts.filter {
                    !it.isViewed && !viewedAlertIds.contains(it.report.id)
                }
                android.util.Log.d("AlertManager", "Unviewed new alerts: ${unviewedNewAlerts.size}")

                if (unviewedNewAlerts.isNotEmpty()) {
                    android.util.Log.d("AlertManager", "🚨 TRIGGERING NEW ALERTS CALLBACK")
                    unviewedNewAlerts.forEach { alert ->
                        android.util.Log.d("AlertManager", "  - Alert: ${alert.report.category.name} - ${alert.report.originalText.take(30)}")
                    }
                    alertListener?.onNewAlerts(unviewedNewAlerts)
                } else {
                    android.util.Log.d("AlertManager", "All new alerts are already viewed")
                }
            } else {
                android.util.Log.d("AlertManager", "No new alerts created")
            }

            android.util.Log.d("AlertManager", "=== END ALERT CHECK ===")
        } catch (e: Exception) {
            android.util.Log.e("AlertManager", "Error in checkForNewAlerts: ${e.message}")
        }
    }

    fun getNearbyUnviewedAlerts(userLocation: Location): List<Alert> {
        val userAlertDistance = preferencesManager.getSavedAlertDistance()

        return activeAlerts
            .filter { !viewedAlertIds.contains(it.report.id) }
            .map { alert ->
                val currentDistance = locationManager.calculateDistance(
                    userLocation.latitude, userLocation.longitude,
                    alert.report.location.latitude, alert.report.location.longitude
                )
                alert.copy(distanceFromUser = currentDistance)
            }
            .filter { it.distanceFromUser <= userAlertDistance }
            .sortedBy { it.distanceFromUser }
    }

    fun markAlertAsViewed(reportId: String) {
        viewedAlertIds.add(reportId)
        saveViewedAlerts()

        val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
        alertListener?.onAlertsUpdated(hasUnviewed)
    }

    fun markAllAlertsAsRead() {
        for (alert in activeAlerts) {
            viewedAlertIds.add(alert.report.id)
        }
        saveViewedAlerts()
        alertListener?.onAlertsUpdated(false)
    }

    fun removeAlertsForExpiredReports(expiredReports: List<Report>) {
        val expiredReportIds = expiredReports.map { it.id }.toSet()
        activeAlerts.removeAll { it.report.id in expiredReportIds }

        val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
        alertListener?.onAlertsUpdated(hasUnviewed)
    }

    private fun saveViewedAlerts() {
        preferencesManager.saveViewedAlerts(viewedAlertIds)
    }

    fun getAlertSummaryMessage(newAlerts: List<Alert>, isSpanish: Boolean): String {
        val userAlertDistance = preferencesManager.getSavedAlertDistance()
        val alertDistanceText = preferencesManager.getAlertDistanceText(userAlertDistance)

        return if (isSpanish) {
            "${newAlerts.size} nuevas alertas dentro de $alertDistanceText"
        } else {
            "${newAlerts.size} new alerts within $alertDistanceText"
        }
    }
}