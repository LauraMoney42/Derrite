// File: managers/DialogManager.kt (Fixed)
package com.money.derrite.managers

import com.money.derrite.BackendClient
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.money.derrite.R
import com.money.derrite.data.Report
import com.money.derrite.data.ReportCategory
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.Locale

class DialogManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    fun showReportConfirmDialog(location: GeoPoint, onConfirm: (GeoPoint) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Create a simple dialog since we might not have the layout file
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Confirmar Reporte" else "Confirm Report")
        builder.setMessage(if (isSpanish) "¿Crear un reporte en esta ubicación?" else "Create a report at this location?")

        builder.setPositiveButton(if (isSpanish) "Sí, Reportar" else "Yes, Report") { dialog, _ ->
            dialog.dismiss()
            onConfirm(location)
        }

        builder.setNegativeButton(if (isSpanish) "Cancelar" else "Cancel") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    fun showReportInputDialog(
        location: GeoPoint,
        selectedCategory: ReportCategory,
        onCategoryChange: (ReportCategory) -> Unit,
        onPhotoRequest: () -> Unit,
        onSubmit: (GeoPoint, String, Bitmap?, ReportCategory) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Create dialog layout programmatically
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        // Title
        val titleText = TextView(context).apply {
            text = if (isSpanish) "Crear Reporte" else "Create Report"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        // Category display
        val categoryDisplay = TextView(context).apply {
            updateCategoryDisplay(this, selectedCategory, isSpanish)
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        dialogLayout.addView(categoryDisplay)

        // Text input
        val editReportText = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = if (isSpanish) "Describe la situación..." else "Describe the situation..."
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        dialogLayout.addView(editReportText)

        // Buttons layout
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        val btnSelectCategory = Button(context).apply {
            text = if (isSpanish) "Cambiar Categoría" else "Change Category"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 0)
            }
        }

        val btnAddPhoto = Button(context).apply {
            text = if (isSpanish) "Agregar Foto" else "Add Photo"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(16, 12, 16, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(8, 0, 0, 0)
            }
        }

        buttonLayout.addView(btnSelectCategory)
        buttonLayout.addView(btnAddPhoto)
        dialogLayout.addView(buttonLayout)

        // Submit/Cancel buttons
        val submitLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 0)
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "CANCELAR" else "CANCEL"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnSubmit = Button(context).apply {
            text = if (isSpanish) "ENVIAR REPORTE" else "SUBMIT REPORT"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF0000"))
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        submitLayout.addView(btnCancel)
        submitLayout.addView(btnSubmit)
        dialogLayout.addView(submitLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnSelectCategory.setOnClickListener {
            showCategorySelectionDialog { category ->
                onCategoryChange(category)
                dialog.dismiss()
                // Recreate dialog with new category
                showReportInputDialog(location, category, onCategoryChange, onPhotoRequest, onSubmit)
            }
        }

        btnAddPhoto.setOnClickListener { onPhotoRequest() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val reportText = editReportText.text?.toString()?.trim()
            if (reportText.isNullOrEmpty()) {
                // Could show an error here, but for now just return
                return@setOnClickListener
            }
            onSubmit(location, reportText, null, selectedCategory)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showCategorySelectionDialog(onCategorySelected: (ReportCategory) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Selecciona Categoría" else "Select Category"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val categories = ReportCategory.values()

        for (category in categories) {
            val categoryView = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(ContextCompat.getColor(context, category.getColorResId()))
                isClickable = true
                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }

            val iconText = TextView(context).apply {
                text = category.getIcon()
                textSize = 24f
                setPadding(0, 0, 16, 0)
            }
            categoryView.addView(iconText)

            val categoryInfo = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val titleText = TextView(context).apply {
                text = category.getDisplayName(isSpanish)
                textSize = 18f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            categoryInfo.addView(titleText)

            val descText = TextView(context).apply {
                text = getCategoryDescription(category, isSpanish)
                textSize = 14f
                setTextColor(Color.parseColor("#DDDDDD"))
                setPadding(0, 4, 0, 0)
            }
            categoryInfo.addView(descText)

            categoryView.addView(categoryInfo)

            categoryView.setOnClickListener {
                onCategorySelected(category)
            }

            dialogLayout.addView(categoryView)
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "CANCELAR" else "CANCEL"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 32, 0, 0)
            }
        }
        dialogLayout.addView(btnCancel)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showReportViewDialog(
        report: Report,
        translationManager: TranslationManager,
        onMarkAsViewed: (String) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(context).apply {
            text = "${report.category.getIcon()} ${report.category.getDisplayName(isSpanish)}"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, report.category.getColorResId()))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val textReportContent = TextView(context).apply {
            text = report.originalText
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.parseColor("#333333"))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        dialogLayout.addView(textReportContent)

        val btnTranslate = Button(context).apply {
            text = if (isSpanish) "TRADUCIR" else "TRANSLATE"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }
        dialogLayout.addView(btnTranslate)

        val timeText = TextView(context).apply {
            text = "Reported: ${getTimeAgo(report.timestamp)}"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(timeText)

        val btnClose = Button(context).apply {
            text = if (isSpanish) "CERRAR" else "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        dialogLayout.addView(btnClose)

        var isTranslated = false

        btnTranslate.setOnClickListener {
            if (!isTranslated) {
                btnTranslate.text = if (isSpanish) "Traduciendo..." else "Translating..."
                btnTranslate.isEnabled = false

                val targetLang = preferencesManager.getSavedLanguage()
                translationManager.translateText(
                    text = report.originalText,
                    fromLang = report.originalLanguage,
                    toLang = targetLang,
                    onSuccess = { translatedText ->
                        textReportContent.text = translatedText
                        btnTranslate.text = if (isSpanish) "MOSTRAR ORIGINAL" else "SHOW ORIGINAL"
                        btnTranslate.setBackgroundColor(Color.parseColor("#4CAF50"))
                        btnTranslate.isEnabled = true
                        isTranslated = true
                    },
                    onError = { error ->
                        btnTranslate.text = if (isSpanish) "Error de Traducción" else "Translation Failed"
                        btnTranslate.setBackgroundColor(Color.RED)
                        btnTranslate.isEnabled = true

                        Handler(Looper.getMainLooper()).postDelayed({
                            btnTranslate.text = if (isSpanish) "TRADUCIR" else "TRANSLATE"
                            btnTranslate.setBackgroundColor(Color.parseColor("#2196F3"))
                        }, 2000)
                    }
                )
            } else {
                textReportContent.text = report.originalText
                btnTranslate.text = if (isSpanish) "TRADUCIR" else "TRANSLATE"
                btnTranslate.setBackgroundColor(Color.parseColor("#2196F3"))
                isTranslated = false
            }
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener {
            dialog.dismiss()
            onMarkAsViewed(report.id)
        }

        dialog.show()
    }

    fun showAlertsDialog(
        userLocation: Location,
        alertManager: AlertManager,
        onMarkAsViewed: (String) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Simplified alerts dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Alertas Cercanas" else "Nearby Alerts")

        val alerts = alertManager.getNearbyUnviewedAlerts(userLocation)

        if (alerts.isEmpty()) {
            builder.setMessage(if (isSpanish) "No hay alertas nuevas en tu área" else "No new alerts in your area")
        } else {
            val alertMessages = alerts.take(5).map { alert ->
                "${alert.report.category.getIcon()} ${alert.report.originalText.take(50)}..."
            }
            builder.setMessage(alertMessages.joinToString("\n\n"))

            builder.setPositiveButton(if (isSpanish) "Marcar como Leído" else "Mark as Read") { _, _ ->
                alerts.forEach { alert ->
                    onMarkAsViewed(alert.report.id)
                }
            }
        }

        builder.setNegativeButton(if (isSpanish) "Cerrar" else "Close", null)
        builder.show()
    }

    fun showSettingsDialog(backendClient: BackendClient) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Simplified settings dialog
        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Configuración" else "Settings")

        val items = if (isSpanish) {
            arrayOf(
                "Preferencias",
                "Guía del Usuario",
                "Política de Privacidad",
                "Acerca de"
            )
        } else {
            arrayOf(
                "Preferences",
                "User Guide",
                "Privacy Policy",
                "About"
            )
        }

        builder.setItems(items) { _, which ->
            when (which) {
                0 -> showPreferencesDialog()
                1 -> showUserGuideDialog()
                2 -> showPrivacyPolicyDialog()
                3 -> showAboutDialog()
            }
        }

        builder.setNegativeButton(if (isSpanish) "Cerrar" else "Close", null)
        builder.show()
    }

    private fun showPreferencesDialog() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Preferencias de Usuario" else "User Preferences")

        val currentAlertDistance = preferencesManager.getAlertDistanceText(preferencesManager.getSavedAlertDistance())
        val message = if (isSpanish) {
            "Distancia de Alerta actual: $currentAlertDistance\n\nToca OK para cambiar"
        } else {
            "Current Alert Distance: $currentAlertDistance\n\nTap OK to change"
        }

        builder.setMessage(message)
        builder.setPositiveButton("OK") { _, _ ->
            showAlertDistanceDialog()
        }
        builder.setNegativeButton(if (isSpanish) "Cerrar" else "Close", null)
        builder.show()
    }

    private fun showAlertDistanceDialog() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val options = if (isSpanish) {
            arrayOf(
                "1 milla",
                "2 millas",
                "3 millas",
                "5 millas",
                "Área de código postal",
                "Todo el estado"
            )
        } else {
            arrayOf(
                "1 mile",
                "2 miles",
                "3 miles",
                "5 miles",
                "Zip code area",
                "State-wide"
            )
        }

        val distances = arrayOf(
            PreferencesManager.ALERT_DISTANCE_1_MILE,
            PreferencesManager.ALERT_DISTANCE_2_MILES,
            PreferencesManager.ALERT_DISTANCE_3_MILES,
            PreferencesManager.ALERT_DISTANCE_5_MILES,
            PreferencesManager.ALERT_DISTANCE_ZIP_CODE,
            PreferencesManager.ALERT_DISTANCE_STATE
        )

        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Distancia de Alerta" else "Alert Distance")
        builder.setItems(options) { _, which ->
            preferencesManager.saveAlertDistance(distances[which])
        }
        builder.show()
    }

    private fun showUserGuideDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Guía del Usuario" else "User Guide",
            getUserGuideContent()
        )
    }

    private fun showPrivacyPolicyDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Política de Privacidad" else "Privacy Policy",
            getPrivacyPolicyContent()
        )
    }

    private fun showAboutDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Acerca de" else "About",
            getAboutContent()
        )
    }

    private fun showInfoDialog(title: String, content: String) {
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
        }

        val contentText = TextView(context).apply {
            text = content
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#333333"))
            setLineSpacing(4f, 1f)
        }
        scrollView.addView(contentText)
        dialogLayout.addView(scrollView)

        val btnClose = Button(context).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "CERRAR" else "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 24, 0, 0)
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateCategoryDisplay(textView: TextView, category: ReportCategory, isSpanish: Boolean) {
        textView.text = "${category.getIcon()} ${category.getDisplayName(isSpanish)}"
        textView.setTextColor(ContextCompat.getColor(context, category.getColorResId()))
    }

    private fun getCategoryDescription(category: ReportCategory, isSpanish: Boolean): String {
        return when (category) {
            ReportCategory.SAFETY -> if (isSpanish)
                "Emergencias, peligros, seguridad" else
                "Emergencies, hazards, safety concerns"
            ReportCategory.FUN -> if (isSpanish)
                "Eventos, fiestas, actividades" else
                "Events, parties, activities"
            ReportCategory.LOST_MISSING -> if (isSpanish)
                "Personas o cosas perdidas" else
                "Lost people or items"
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
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

    private fun getUserGuideContent(): String {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        return if (isSpanish) {
            "Cómo Usar Derrite\n\n" +
                    "REPORTAR\n" +
                    "• Presiona y mantén presionado en cualquier lugar del mapa para crear un reporte\n" +
                    "• Selecciona una categoría: Seguridad (rojo), Diversión (amarillo), o Perdido/Desaparecido (azul)\n" +
                    "• Agrega una descripción de la situación\n\n" +
                    "ALERTAS\n" +
                    "• Recibe notificaciones cuando se hagan reportes dentro de tu distancia elegida\n" +
                    "• Personaliza la distancia de alerta en Configuración\n\n" +
                    "PRIVACIDAD\n" +
                    "• No se requieren cuentas - completamente anónimo\n" +
                    "• Todos los datos expiran automáticamente después de 8 horas"
        } else {
            "How to Use Derrite\n\n" +
                    "REPORTING\n" +
                    "• Long press anywhere on the map to create a report\n" +
                    "• Select a category: Safety (red), Fun (yellow), or Lost/Missing (blue)\n" +
                    "• Add a description of the situation\n\n" +
                    "ALERTS\n" +
                    "• Get notified when reports are made within your chosen distance\n" +
                    "• Customize alert distance in Settings\n\n" +
                    "PRIVACY\n" +
                    "• No accounts required - completely anonymous\n" +
                    "• All data expires automatically after 8 hours"
        }
    }

    private fun getPrivacyPolicyContent(): String {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        return if (isSpanish) {
            "Política de Privacidad\n\n" +
                    "ANÓNIMO POR DISEÑO\n" +
                    "• No se requieren cuentas de usuario o registro\n" +
                    "• No se recopila ni almacena información personal\n\n" +
                    "PRIVACIDAD DE UBICACIÓN\n" +
                    "• Las coordenadas GPS exactas nunca se almacenan\n" +
                    "• Solo se usan zonas anónimas de 500m\n\n" +
                    "ALMACENAMIENTO DE DATOS\n" +
                    "• Todos los reportes se auto-eliminan después de 8 horas\n" +
                    "• No hay almacenamiento permanente de datos de usuario"
        } else {
            "Privacy Policy\n\n" +
                    "ANONYMOUS BY DESIGN\n" +
                    "• No user accounts or registration required\n" +
                    "• No personal information collected or stored\n\n" +
                    "LOCATION PRIVACY\n" +
                    "• Exact GPS coordinates never stored\n" +
                    "• Only anonymous 500m zones are used\n\n" +
                    "DATA STORAGE\n" +
                    "• All reports auto-delete after 8 hours\n" +
                    "• No permanent storage of any user data"
        }
    }

    private fun getAboutContent(): String {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        return if (isSpanish) {
            "Acerca de Derrite\n\n" +
                    "MISIÓN\n" +
                    "Derrite es una plataforma de reportes anónima y privada, diseñada para ayudar a las comunidades a mantenerse informadas sobre problemas locales.\n\n" +
                    "CARACTERÍSTICAS\n" +
                    "• Reportes anónimos con categorías\n" +
                    "• Alertas en tiempo real\n" +
                    "• Soporte bilingüe (Español/Inglés)\n" +
                    "• Protección completa de privacidad\n\n" +
                    "PRIVACIDAD PRIMERO\n" +
                    "Construido desde cero con la privacidad como principio fundamental. Nunca se almacenan, rastrean o comparten datos de usuario."
        } else {
            "About Derrite\n\n" +
                    "MISSION\n" +
                    "Derrite is a privacy-first, anonymous reporting platform designed to help communities stay informed about local concerns.\n\n" +
                    "FEATURES\n" +
                    "• Anonymous reporting with categories\n" +
                    "• Real-time alerts\n" +
                    "• Bilingual support (Spanish/English)\n" +
                    "• Complete privacy protection\n\n" +
                    "PRIVACY FIRST\n" +
                    "Built from the ground up with privacy as the core principle. No user data is ever stored, tracked, or shared."
        }
    }
}