// File: managers/ReportManager.kt
package com.money.pinlocal.managers

import android.graphics.Bitmap
import com.money.pinlocal.data.Report
import com.money.pinlocal.data.ReportCategory
import org.osmdroid.util.GeoPoint
import java.util.UUID

class ReportManager(private val preferencesManager: PreferencesManager) {

    companion object {
        private const val REPORT_DURATION_HOURS = 8L
    }

    private val activeReports = mutableListOf<Report>()

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
        saveReportsToPreferences()
        return report
    }

    fun getActiveReports(): List<Report> = activeReports.toList()

    fun addReport(report: Report) {
        activeReports.add(report)
        saveReportsToPreferences()
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
                        // Handle backward compatibility - if no category, default to SAFETY
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