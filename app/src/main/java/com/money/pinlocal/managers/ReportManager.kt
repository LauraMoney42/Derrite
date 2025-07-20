// File: managers/ReportManager.kt - PRIVACY-SAFE SOLUTION
package com.money.pinlocal.managers

import android.graphics.Bitmap
import com.money.pinlocal.data.Report
import com.money.pinlocal.data.ReportCategory
import org.osmdroid.util.GeoPoint
import java.util.UUID

class ReportManager(private val preferencesManager: PreferencesManager) {

    companion object {
        private const val REPORT_DURATION_HOURS = 8L
        private const val KEY_LAST_REPORT_TIMESTAMP = "last_report_timestamp"
        private const val ALERT_COOLDOWN_MS = 60000L // 60 seconds - don't check alerts for 1 minute after creating a report
    }

    private val activeReports = mutableListOf<Report>()
    private var lastReportCreatedTimestamp = 0L

    fun createReport(
        location: GeoPoint,
        text: String,
        detectedLanguage: String,
        photo: Bitmap?,
        category: ReportCategory
    ): Report {
        val report = Report(
            id = UUID.randomUUID().toString(),
            location = location,
            originalText = text,
            originalLanguage = detectedLanguage,
            hasPhoto = photo != null,
            photo = photo,
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (REPORT_DURATION_HOURS * 60 * 60 * 1000),
            category = category
        )

        activeReports.add(report)

        // PRIVACY-SAFE: Just track WHEN we created a report, not what it was
        lastReportCreatedTimestamp = System.currentTimeMillis()
        preferencesManager.preferences.edit()
            .putLong(KEY_LAST_REPORT_TIMESTAMP, lastReportCreatedTimestamp)
            .apply()

        android.util.Log.d("ReportManager", "Created report, alert cooldown until: ${lastReportCreatedTimestamp + ALERT_COOLDOWN_MS}")

        saveReportsToPreferences()
        return report
    }

    // PRIVACY-SAFE: Check if we should skip alerts (recent report creation)
    fun shouldSkipAlertsCheck(): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeSinceLastReport = currentTime - lastReportCreatedTimestamp
        val shouldSkip = timeSinceLastReport < ALERT_COOLDOWN_MS

        if (shouldSkip) {
            android.util.Log.d("ReportManager", "Skipping alerts check - recent report created ${timeSinceLastReport}ms ago")
        }

        return shouldSkip
    }

    // Load the last report timestamp
    fun loadLastReportTimestamp() {
        lastReportCreatedTimestamp = preferencesManager.preferences.getLong(KEY_LAST_REPORT_TIMESTAMP, 0L)
        android.util.Log.d("ReportManager", "Loaded last report timestamp: $lastReportCreatedTimestamp")
    }

    fun getActiveReports(): List<Report> = activeReports.toList()

    fun addReport(report: Report) {
        val existingReport = activeReports.find { it.id == report.id }
        if (existingReport == null) {
            activeReports.add(report)
            saveReportsToPreferences()
        }
    }

    fun removeReport(report: Report) {
        activeReports.remove(report)
        saveReportsToPreferences()
    }

    fun cleanupExpiredReports(): List<Report> {
        val currentTime = System.currentTimeMillis()
        val expiredReports = activeReports.filter { it.expiresAt < currentTime }

        expiredReports.forEach { report ->
            activeReports.remove(report)
        }

        if (expiredReports.isNotEmpty()) {
            saveReportsToPreferences()
        }

        return expiredReports
    }

    fun loadSavedReports() {
        try {
            val reportsJson = preferencesManager.getSavedReports()
            val reportsList = parseReportsFromJson(reportsJson)
            val currentTime = System.currentTimeMillis()
            val validReports = reportsList.filter { it.expiresAt > currentTime }

            activeReports.clear()
            activeReports.addAll(validReports)
        } catch (e: Exception) {
            activeReports.clear()
        }
    }

    private fun saveReportsToPreferences() {
        try {
            val reportsJson = convertReportsToJson(activeReports)
            preferencesManager.saveReports(reportsJson)
        } catch (e: Exception) {
            // Handle save error silently
        }
    }

    private fun parseReportsFromJson(json: String): List<Report> {
        val reports = mutableListOf<Report>()
        try {
            if (json.isBlank() || json == "[]") {
                return reports
            }

            val lines = json.split("|||")
            for (line in lines) {
                try {
                    if (line.trim().isEmpty()) continue

                    val parts = line.split(":::")
                    if (parts.size >= 8) {
                        val categoryCode = if (parts.size > 8) parts[8] else "safety"
                        val category = ReportCategory.values().find { it.code == categoryCode }
                            ?: ReportCategory.SAFETY

                        val report = Report(
                            id = parts[0],
                            location = GeoPoint(
                                parts[1].toDoubleOrNull() ?: 0.0,
                                parts[2].toDoubleOrNull() ?: 0.0
                            ),
                            originalText = parts[3],
                            originalLanguage = parts[4],
                            hasPhoto = parts[5].toBooleanStrictOrNull() ?: false,
                            photo = null,
                            timestamp = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                            expiresAt = parts[7].toLongOrNull()
                                ?: (System.currentTimeMillis() + (8 * 60 * 60 * 1000)),
                            category = category
                        )
                        reports.add(report)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            // Return empty list if parsing fails completely
        }
        return reports
    }

    private fun convertReportsToJson(reports: List<Report>): String {
        return reports.joinToString("|||") { report ->
            "${report.id}:::${report.location.latitude}:::${report.location.longitude}:::${report.originalText}:::${report.originalLanguage}:::${report.hasPhoto}:::${report.timestamp}:::${report.expiresAt}:::${report.category.code}"
        }
    }

    fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hours ago"
            else -> "$days days ago"
        }
    }
}