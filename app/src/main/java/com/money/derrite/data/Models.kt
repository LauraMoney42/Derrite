// File: data/Models.kt
package com.money.derrite.data

import android.graphics.Bitmap
import org.osmdroid.util.GeoPoint
import com.money.derrite.R

// Category enum for report types - SAFETY ONLY
enum class ReportCategory(val displayName: String, val code: String) {
    SAFETY("Safety", "safety");

    fun getDisplayName(isSpanish: Boolean): String {
        return if (isSpanish) "Seguridad" else "Safety"
    }

    fun getIcon(): String {
        return "⚠️"
    }

    fun getColorResId(): Int {
        return R.color.category_safety
    }

    fun getFillColorResId(): Int {
        return R.color.category_safety_fill
    }

    fun getStrokeColorResId(): Int {
        return R.color.category_safety_stroke
    }
}

data class Report(
    val id: String,
    val location: GeoPoint,
    val originalText: String,
    val originalLanguage: String,
    val hasPhoto: Boolean,
    val photo: Bitmap?,
    val timestamp: Long,
    val expiresAt: Long,
    val category: ReportCategory = ReportCategory.SAFETY
)

data class Alert(
    val id: String,
    val report: Report,
    val distanceFromUser: Double,
    val isViewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)