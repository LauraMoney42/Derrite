// File: managers/DialogManager.kt (Complete with Favorites Support)
package com.money.pinlocal.managers

import com.money.pinlocal.BackendClient
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
import com.google.android.material.materialswitch.MaterialSwitch
import com.money.pinlocal.R
import com.money.pinlocal.data.Report
import com.money.pinlocal.data.ReportCategory
import com.money.pinlocal.data.FavoritePlace
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.Locale



class DialogManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    fun showReportConfirmDialog(location: GeoPoint, onConfirm: (GeoPoint) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

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
        var currentCategory = selectedCategory

        // Main container with iOS-style background
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Content card with rounded corners
        val contentCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            background = ContextCompat.getDrawable(context, R.drawable.ios_card_background)
        }

        // Title
        val titleText = TextView(context).apply {
            text = if (isSpanish) "Crear Reporte" else "Create Report"
            textSize = 22f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        contentCard.addView(titleText)

        // Category section
        val categoryCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_input_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val categoryLabel = TextView(context).apply {
            text = if (isSpanish) "Categoría" else "Category"
            textSize = 20f  // ← CHANGED FROM 14f TO 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        categoryCard.addView(categoryLabel)

        val categorySpinner = android.widget.Spinner(context).apply {
            setPadding(0, 12, 0, 12)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPopupBackgroundResource(R.drawable.ios_input_background)
        }

        val categories = ReportCategory.values()
        val categoryNames = categories.map { "${it.getIcon()} ${it.getDisplayName(isSpanish)}" }
        val adapter = object : android.widget.ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, categoryNames) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view as TextView
                textView.textSize = 20f
                textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_dropdown_arrow, 0)
                textView.compoundDrawablePadding = 8
                return view
            }

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView
                textView.textSize = 18f
                textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textView.setPadding(16, 16, 16, 16)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        categorySpinner.setSelection(categories.indexOf(currentCategory))

// ADD THESE MISSING LINES:
        categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentCategory = categories[position]
                onCategoryChange(currentCategory)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        categoryCard.addView(categorySpinner)  // ← THIS WAS MISSING!
        contentCard.addView(categoryCard)

        // Description section
        val descCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_input_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val descLabel = TextView(context).apply {
            text = if (isSpanish) "Descripción" else "Description"
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        descCard.addView(descLabel)

        val editReportText = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = if (isSpanish) "Describe la situación..." else "Describe the situation..."
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_tertiary))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setPadding(0, 8, 0, 8)
            minLines = 3
            maxLines = 5
        }
        descCard.addView(editReportText)
        contentCard.addView(descCard)

        // Add Photo button (modern iOS style)
        val btnAddPhoto = Button(context).apply {
            text = if (isSpanish) "  Agregar Foto" else "  Add Photo"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_button_secondary)
            setPadding(20, 16, 20, 16)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_camera_modern, 0, 0, 0)
            compoundDrawablePadding = 8
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }
        contentCard.addView(btnAddPhoto)

        // Buttons layout
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            background = ContextCompat.getDrawable(context, R.drawable.ios_button_text)
            setPadding(32, 16, 32, 16)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnSubmit = Button(context).apply {
            text = if (isSpanish) "Enviar" else "Submit"  // ← SHORTENED TEXT
            setTextColor(ContextCompat.getColor(context, R.color.white))
            background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)  // ← ROUNDED GREEN
            setPadding(32, 16, 32, 16)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnSubmit)
        contentCard.addView(buttonLayout)
        dialogLayout.addView(contentCard)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

// Set rounded background
        dialog.window?.setBackgroundDrawableResource(R.drawable.ios_dialog_background)

        btnAddPhoto.setOnClickListener { onPhotoRequest() }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSubmit.setOnClickListener {
            val reportText = editReportText.text?.toString()?.trim()
            if (reportText.isNullOrEmpty()) {
                editReportText.error = if (isSpanish) "Ingresa una descripción" else "Enter a description"
                return@setOnClickListener
            }
            onSubmit(location, reportText, null, currentCategory)
            dialog.dismiss()
        }

        dialog.show()
    }

    // NEW: Show Favorite Options Dialog (Missing method that was causing the error)
    fun showFavoriteOptionsDialog(
        favorite: FavoritePlace,
        favoriteManager: FavoriteManager,
        onAction: (String) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        // Title with heart icon
        val titleText = TextView(context).apply {
            text = "💗 ${favorite.name}"
            textSize = 20f
            setTextColor(Color.parseColor("#E91E63"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        // Favorite info
        val infoText = TextView(context).apply {
            text = if (isSpanish) {
                "Alertas: ${favorite.getAlertDistanceText(true)}\n" +
                        "Tipos: ${favorite.getEnabledAlertsText(true)}"
            } else {
                "Alerts: ${favorite.getAlertDistanceText(false)}\n" +
                        "Types: ${favorite.getEnabledAlertsText(false)}"
            }
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 24)
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(infoText)

        // Options buttons
        val btnViewAlerts = Button(context).apply {
            text = if (isSpanish) "Ver Alertas" else "View Alerts"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        val btnEdit = Button(context).apply {
            text = if (isSpanish) "Editar Favorito" else "Edit Favorite"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        val btnRemove = Button(context).apply {
            text = if (isSpanish) "Eliminar Favorito" else "Remove Favorite"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F44336"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }

        dialogLayout.addView(btnViewAlerts)
        dialogLayout.addView(btnEdit)
        dialogLayout.addView(btnRemove)
        dialogLayout.addView(btnCancel)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnViewAlerts.setOnClickListener {
            dialog.dismiss()
            showFavoriteAlertsDialog(favorite, favoriteManager)
        }

        btnEdit.setOnClickListener {
            dialog.dismiss()
            showEditFavoriteDialog(favorite, favoriteManager)
        }

        btnRemove.setOnClickListener {
            dialog.dismiss()
            showRemoveFavoriteConfirmDialog(favorite, favoriteManager)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
            onAction(favorite.id)
        }

        dialog.show()
    }

    // NEW: Show Remove Favorite Confirmation
    private fun showRemoveFavoriteConfirmDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        AlertDialog.Builder(context)
            .setTitle(if (isSpanish) "Eliminar Favorito" else "Remove Favorite")
            .setMessage(if (isSpanish)
                "¿Estás seguro de que quieres eliminar '${favorite.name}' de tus favoritos?"
            else "Are you sure you want to remove '${favorite.name}' from your favorites?")
            .setPositiveButton(if (isSpanish) "Eliminar" else "Remove") { _, _ ->
                favoriteManager.removeFavorite(favorite.id)
            }
            .setNegativeButton(if (isSpanish) "Cancelar" else "Cancel", null)
            .show()
    }

    private fun showEditFavoriteDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Editar Favorito" else "Edit Favorite"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        // Name input with current value
        val nameInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            setText(favorite.name)
            hint = if (isSpanish) "Nombre del lugar" else "Place name"
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
        dialogLayout.addView(nameInput)

        // Description input with current value
        val descriptionInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            setText(favorite.description)
            hint = if (isSpanish) "Descripción" else "Description"
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
        dialogLayout.addView(descriptionInput)

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
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

        val btnSave = Button(context).apply {
            text = if (isSpanish) "GUARDAR" else "SAVE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF4081"))
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnSave)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = nameInput.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                nameInput.error = if (isSpanish) "Ingresa un nombre" else "Enter a name"
                return@setOnClickListener
            }

            val description = descriptionInput.text?.toString()?.trim() ?: ""

            val updatedFavorite = favorite.copy(
                name = name,
                description = description
            )

            favoriteManager.updateFavorite(updatedFavorite)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteFavoriteConfirmDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        AlertDialog.Builder(context)
            .setTitle(if (isSpanish) "Eliminar Favorito" else "Delete Favorite")
            .setMessage(if (isSpanish) "¿Eliminar '${favorite.name}' de favoritos?" else "Delete '${favorite.name}' from favorites?")
            .setPositiveButton(if (isSpanish) "Eliminar" else "Delete") { _, _ ->
                favoriteManager.removeFavorite(favorite.id)
            }
            .setNegativeButton(if (isSpanish) "Cancelar" else "Cancel", null)
            .show()
    }
    // NEW: Show Favorite Alerts Dialog
    private fun showFavoriteAlertsDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val alerts = favoriteManager.getFavoriteAlerts(favorite.id)

        val builder = AlertDialog.Builder(context)
        builder.setTitle(if (isSpanish) "Alertas para ${favorite.name}" else "Alerts for ${favorite.name}")

        if (alerts.isEmpty()) {
            builder.setMessage(if (isSpanish) "No hay alertas nuevas para este favorito" else "No new alerts for this favorite")
        } else {
            val alertMessages = alerts.take(5).map { alert ->
                "${alert.category.getIcon()} ${alert.content.take(50)}..."
            }
            builder.setMessage(alertMessages.joinToString("\n\n"))

            builder.setPositiveButton(if (isSpanish) "Marcar como Leído" else "Mark as Read") { _, _ ->
                favoriteManager.markFavoriteAlertsAsViewed(favorite.id)
            }
        }

        builder.setNegativeButton(if (isSpanish) "Cerrar" else "Close", null)
        builder.show()
    }


// Update DialogManager.kt - Simple add favorite dialog (no privacy toggle):

    fun showAddFavoriteDialog(location: GeoPoint, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Agregar a Favoritos" else "Add to Favorites"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        // Name input
        val nameInput = TextInputEditText(context).apply {
            hint = if (isSpanish) "Nombre (ej. Escuela de Emma, Casa, Trabajo)" else "Name (e.g., Emma's School, Home, Work)"
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
        dialogLayout.addView(nameInput)

        // Description input
        val descriptionInput = TextInputEditText(context).apply {
            hint = if (isSpanish) "Descripción (opcional)" else "Description (optional)"
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
        dialogLayout.addView(descriptionInput)

        // Info text about background notifications
        val infoText = TextView(context).apply {
            text = if (isSpanish)
                "💡 Recibirás alertas para este lugar incluso cuando la app esté cerrada"
            else
                "💡 You'll receive alerts for this place even when the app is closed"
            textSize = 12f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(12, 12, 12, 16)
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        dialogLayout.addView(infoText)

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
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

        val btnAdd = Button(context).apply {
            text = if (isSpanish) "AGREGAR" else "ADD"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF4081"))
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnAdd)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val name = nameInput.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                return@setOnClickListener
            }

            val description = descriptionInput.text?.toString()?.trim() ?: ""

            // Create favorite with background notifications enabled
            val favoritePlace = favoriteManager.createFavoriteFromLocation(
                location = location,
                name = name,
                description = description,
                categories = setOf(ReportCategory.SAFETY), // Default to safety alerts
                alertDistance = 1609.0 // 1 mile default
            )

            favoriteManager.addFavorite(favoritePlace)
            dialog.dismiss()

            // Show confirmation
            android.util.Log.d("DialogManager", "Added favorite: $name with background notifications")
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
            textSize = 22f
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
                textSize = 20f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            categoryInfo.addView(titleText)

            val descText = TextView(context).apply {
                text = getCategoryDescription(category, isSpanish)
                textSize = 20f
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

        // Main container with iOS-style background
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Content card with rounded corners
        val contentCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            background = ContextCompat.getDrawable(context, R.drawable.ios_card_background)
        }

        // Category header with icon
        val categoryHeader = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(16, 16, 16, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_input_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val categoryIcon = TextView(context).apply {
            text = report.category.getIcon()
            textSize = 28f
            setPadding(0, 0, 12, 0)
        }

        val categoryTitle = TextView(context).apply {
            text = report.category.getDisplayName(isSpanish)
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, report.category.getColorResId()))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        categoryHeader.addView(categoryIcon)
        categoryHeader.addView(categoryTitle)
        contentCard.addView(categoryHeader)

        // Report content section
        val contentSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_input_background)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val contentLabel = TextView(context).apply {
            text = if (isSpanish) "Contenido" else "Content"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val textReportContent = TextView(context).apply {
            text = report.originalText
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 8, 0, 8)
            setLineSpacing(0f, 1.4f)  // ← CORRECT METHOD (extraSpace, multiplier)
        }

        contentSection.addView(contentLabel)
        contentSection.addView(textReportContent)
        contentCard.addView(contentSection)

        // Translate button (iOS-style)
        val btnTranslate = Button(context).apply {
            text = if (isSpanish) "Traducir" else "Translate"
            setTextColor(ContextCompat.getColor(context, R.color.white))
            background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)
            setPadding(20, 16, 20, 16)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        contentCard.addView(btnTranslate)

        // Time info
        val timeSection = TextView(context).apply {
            text = "📍 ${if (isSpanish) "Reportado" else "Reported"}: ${getTimeAgo(report.timestamp)}"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(16, 12, 16, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            background = ContextCompat.getDrawable(context, R.drawable.ios_input_background)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        contentCard.addView(timeSection)

        // Close button
        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            background = ContextCompat.getDrawable(context, R.drawable.ios_button_text)
            setPadding(32, 16, 32, 16)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        contentCard.addView(btnClose)

        dialogLayout.addView(contentCard)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        // Set rounded background
        dialog.window?.setBackgroundDrawableResource(R.drawable.ios_dialog_background)

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
                        btnTranslate.text = if (isSpanish) "Mostrar Original" else "Show Original"
                        btnTranslate.background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)
                        btnTranslate.isEnabled = true
                        isTranslated = true
                    },
                    onError = { error ->
                        btnTranslate.text = if (isSpanish) "Error" else "Translation Failed"
                        btnTranslate.background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)
                        btnTranslate.isEnabled = true

                        Handler(Looper.getMainLooper()).postDelayed({
                            btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                            btnTranslate.background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)
                        }, 2000)
                    }
                )
            } else {
                textReportContent.text = report.originalText
                btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                btnTranslate.background = ContextCompat.getDrawable(context, R.drawable.ios_button_success)
                isTranslated = false
            }
        }

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

    private fun showAlertDistanceDialog(onDistanceSelected: (Double) -> Unit) {
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
            1609.0,    // 1 mile
            3218.0,    // 2 miles
            4827.0,    // 3 miles
            8047.0,    // 5 miles
            8050.0,    // zip code area
            160934.0   // state-wide
        )

        AlertDialog.Builder(context)
            .setTitle(if (isSpanish) "Distancia de Alerta" else "Alert Distance")
            .setItems(options) { _, which ->
                onDistanceSelected(distances[which])
            }
            .show()
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
            showAlertDistanceDialog { distance ->
                preferencesManager.saveAlertDistance(distance)
            }
        }
        builder.setNegativeButton(if (isSpanish) "Cerrar" else "Close", null)
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
            "Cómo Usar PinLocal\n\n" +
                    "REPORTAR\n" +
                    "• Presiona y mantén presionado en cualquier lugar del mapa para crear un reporte\n" +
                    "• Selecciona una categoría: Seguridad (rojo), Diversión (amarillo), o Perdido/Desaparecido (azul)\n" +
                    "• Agrega una descripción de la situación\n\n" +
                    "FAVORITOS\n" +
                    "• Presiona y mantén presionado en el mapa y selecciona 'Agregar a Favoritos'\n" +
                    "• Nombra el lugar y configura las alertas\n" +
                    "• Recibe notificaciones cuando algo pase cerca\n\n" +
                    "ALERTAS\n" +
                    "• Recibe notificaciones cuando se hagan reportes dentro de tu distancia elegida\n" +
                    "• Personaliza la distancia de alerta en Configuración\n\n" +
                    "PRIVACIDAD\n" +
                    "• No se requieren cuentas - completamente anónimo\n" +
                    "• Todos los datos expiran automáticamente después de 8 horas"
        } else {
            "How to Use PinLocal\n\n" +
                    "REPORTING\n" +
                    "• Long press anywhere on the map to create a report\n" +
                    "• Select a category: Safety (red), Fun (yellow), or Lost/Missing (blue)\n" +
                    "• Add a description of the situation\n\n" +
                    "FAVORITES\n" +
                    "• Long press on the map and select 'Add to Favorites'\n" +
                    "• Name the place and configure alerts\n" +
                    "• Get notified when something happens nearby\n\n" +
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
            "Acerca de PinLocal\n\n" +
                    "MISIÓN\n" +
                    "PinLocal es una plataforma de reportes anónima y privada, diseñada para ayudar a las comunidades a mantenerse informadas sobre problemas locales.\n\n" +
                    "CARACTERÍSTICAS\n" +
                    "• Reportes anónimos con categorías\n" +
                    "• Lugares favoritos con alertas personalizadas\n" +
                    "• Alertas en tiempo real\n" +
                    "• Soporte bilingüe (Español/Inglés)\n" +
                    "• Protección completa de privacidad\n\n" +
                    "PRIVACIDAD PRIMERO\n" +
                    "Construido desde cero con la privacidad como principio fundamental. Nunca se almacenan, rastrean o comparten datos de usuario."
        } else {
            "About PinLocal\n\n" +
                    "MISSION\n" +
                    "PinLocal is a privacy-first, anonymous reporting platform designed to help communities stay informed about local concerns.\n\n" +
                    "FEATURES\n" +
                    "• Anonymous reporting with categories\n" +
                    "• Favorite places with custom alerts\n" +
                    "• Real-time alerts\n" +
                    "• Bilingual support (Spanish/English)\n" +
                    "• Complete privacy protection\n\n" +
                    "PRIVACY FIRST\n" +
                    "Built from the ground up with privacy as the core principle. No user data is ever stored, tracked, or shared."
        }
    }
}