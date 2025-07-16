// File: data/Models.kt
package com.money.derrite.data

import android.graphics.Bitmap
import org.osmdroid.util.GeoPoint
import com.money.derrite.R

// Category enum for report types
enum class ReportCategory(val displayName: String, val code: String) {
    SAFETY("Safety", "safety"),
    FUN("Fun", "fun"),
    LOST_MISSING("Lost/Missing", "lost");

    fun getDisplayName(isSpanish: Boolean): String {
        return when (this) {
            SAFETY -> if (isSpanish) "Seguridad" else "Safety"
            FUN -> if (isSpanish) "Diversión" else "Fun"
            LOST_MISSING -> if (isSpanish) "Perdido/Desaparecido" else "Lost/Missing"
        }
    }

    fun getIcon(): String {
        return when (this) {
            SAFETY -> "⚠️"
            FUN -> "🎉"
            LOST_MISSING -> "🔍"
        }
    }

    fun getColorResId(): Int {
        return when (this) {
            SAFETY -> R.color.category_safety
            FUN -> R.color.category_fun
            LOST_MISSING -> R.color.category_lost
        }
    }

    fun getFillColorResId(): Int {
        return when (this) {
            SAFETY -> R.color.category_safety_fill
            FUN -> R.color.category_fun_fill
            LOST_MISSING -> R.color.category_lost_fill
        }
    }

    fun getStrokeColorResId(): Int {
        return when (this) {
            SAFETY -> R.color.category_safety_stroke
            FUN -> R.color.category_fun_stroke
            LOST_MISSING -> R.color.category_lost_stroke
        }
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