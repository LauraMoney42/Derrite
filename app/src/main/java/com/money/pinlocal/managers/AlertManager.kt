// File: managers/AlertManager.kt
package com.money.pinlocal.managers

import android.location.Location
import com.money.pinlocal.data.Alert
import com.money.pinlocal.data.Report
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
            val newAlerts = mutableListOf<Alert>()
            val userAlertDistance = preferencesManager.getSavedAlertDistance()

            for (report in reportManager.getActiveReports()) {
                try {
                    val distance = locationManager.calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        report.location.latitude, report.location.longitude
                    )

                    if (distance <= userAlertDistance) {
                        val existingAlert = activeAlerts.find { it.report.id == report.id }
                        if (existingAlert == null) {
                            val alert = Alert(
                                id = UUID.randomUUID().toString(),
                                report = report,
                                distanceFromUser = distance,
                                isViewed = viewedAlertIds.contains(report.id)
                            )
                            newAlerts.add(alert)
                            activeAlerts.add(alert)
                        }
                    }
                } catch (e: Exception) {
                    continue
                }
            }

            val hasUnviewed = activeAlerts.any { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
            alertListener?.onAlertsUpdated(hasUnviewed)

            if (newAlerts.isNotEmpty()) {
                val unviewedNewAlerts = newAlerts.filter {
                    !it.isViewed && !viewedAlertIds.contains(it.report.id)
                }
                if (unviewedNewAlerts.isNotEmpty()) {
                    alertListener?.onNewAlerts(unviewedNewAlerts)
                }
            }
        } catch (e: Exception) {
            // Silently fail
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