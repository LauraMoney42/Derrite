// File: managers/DialogManager.kt (Complete with Rounded Corners and Fixed Distance Selection)
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
import android.widget.Toast
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

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Confirmar Reporte" else "Confirm Report"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        val messageText = TextView(context).apply {
            text = if (isSpanish) "¿Crear un reporte en esta ubicación?" else "Create a report at this location?"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(messageText)

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnConfirm = Button(context).apply {
            text = if (isSpanish) "Sí, Reportar" else "Yes, Report"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(32, 16, 32, 16)
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnConfirm)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm(location)
        }

        dialog.show()
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

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        // Title
        val titleText = TextView(context).apply {
            text = if (isSpanish) "Crear Reporte" else "Create Report"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Category section
        val categoryLabel = TextView(context).apply {
            text = if (isSpanish) "Categoría" else "Category"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(categoryLabel)

        val categorySpinner = android.widget.Spinner(context).apply {
            setPadding(16, 12, 16, 12)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }

        val categories = ReportCategory.values()
        val categoryNames = categories.map { "${it.getIcon()} ${it.getDisplayName(isSpanish)}" }
        val adapter = object : android.widget.ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, categoryNames) {
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getView(position, convertView, parent)
                val textView = view as TextView
                textView.textSize = 16f
                textView.setTextColor(Color.parseColor("#333333"))
                return view
            }

            override fun getDropDownView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val view = super.getDropDownView(position, convertView, parent)
                val textView = view as TextView
                textView.textSize = 16f
                textView.setTextColor(Color.parseColor("#333333"))
                textView.setPadding(16, 16, 16, 16)
                return view
            }
        }
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        categorySpinner.adapter = adapter
        categorySpinner.setSelection(categories.indexOf(currentCategory))

        categorySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                currentCategory = categories[position]
                onCategoryChange(currentCategory)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        dialogLayout.addView(categorySpinner)

        // Description section
        val descLabel = TextView(context).apply {
            text = if (isSpanish) "Descripción" else "Description"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(descLabel)

        val editReportText = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = if (isSpanish) "Describe la situación..." else "Describe the situation..."
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
            minLines = 3
            maxLines = 5
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
        }
        dialogLayout.addView(editReportText)

        // Add Photo button
        val btnAddPhoto = Button(context).apply {
            text = if (isSpanish) "Agregar Foto" else "Add Photo"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(20, 16, 20, 16)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(btnAddPhoto)

        // Buttons layout
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
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
            text = if (isSpanish) "Enviar" else "Submit"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(32, 16, 32, 16)
            textSize = 16f
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnSubmit)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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

    fun showFavoriteOptionsDialog(
        favorite: FavoritePlace,
        favoriteManager: FavoriteManager,
        onAction: (String) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        // Title with heart icon
        val titleText = TextView(context).apply {
            text = "💗 ${favorite.name}"
            textSize = 20f
            setTextColor(Color.parseColor("#E91E63"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
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
            setTextColor(Color.parseColor("#333333"))
            setPadding(16, 16, 16, 24)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
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
            setTextColor(Color.parseColor("#666666"))
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

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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

    private fun showRemoveFavoriteConfirmDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Eliminar Favorito" else "Remove Favorite"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(titleText)

        val messageText = TextView(context).apply {
            text = if (isSpanish)
                "¿Estás seguro de que quieres eliminar '${favorite.name}' de tus favoritos?"
            else "Are you sure you want to remove '${favorite.name}' from your favorites?"
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(messageText)

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnRemove = Button(context).apply {
            text = if (isSpanish) "Eliminar" else "Remove"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#F44336"))
            setPadding(32, 16, 32, 16)
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnRemove)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnRemove.setOnClickListener {
            favoriteManager.removeFavorite(favorite.id)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showEditFavoriteDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Editar Favorito" else "Edit Favorite"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Name input with current value
        val nameInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            setText(favorite.name)
            hint = if (isSpanish) "Nombre del lugar" else "Place name"
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
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
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(descriptionInput)

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
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
            text = if (isSpanish) "Guardar" else "Save"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(32, 16, 32, 16)
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnSave)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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

    private fun showFavoriteAlertsDialog(favorite: FavoritePlace, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val alerts = favoriteManager.getFavoriteAlerts(favorite.id)

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Alertas para ${favorite.name}" else "Alerts for ${favorite.name}"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        if (alerts.isEmpty()) {
            val messageText = TextView(context).apply {
                text = if (isSpanish) "No hay alertas nuevas para este favorito" else "No new alerts for this favorite"
                textSize = 16f
                setTextColor(Color.parseColor("#666666"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 24)
            }
            dialogLayout.addView(messageText)
        } else {
            val alertMessages = alerts.take(5).map { alert ->
                "${alert.category.getIcon()} ${alert.content.take(50)}..."
            }
            val messageText = TextView(context).apply {
                text = alertMessages.joinToString("\n\n")
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
            }
            dialogLayout.addView(messageText)

            val btnMarkRead = Button(context).apply {
                text = if (isSpanish) "Marcar como Leído" else "Mark as Read"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setPadding(32, 16, 32, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }
            dialogLayout.addView(btnMarkRead)

            btnMarkRead.setOnClickListener {
                favoriteManager.markFavoriteAlertsAsViewed(favorite.id)
            }
        }

        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showAddFavoriteDialog(location: GeoPoint, favoriteManager: FavoriteManager) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Agregar a Favoritos" else "Add to Favorites"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Name input
        val nameInput = TextInputEditText(context).apply {
            hint = if (isSpanish) "Nombre (ej. Escuela de Emma, Casa, Trabajo)" else "Name (e.g., Emma's School, Home, Work)"
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
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
            setTextColor(Color.parseColor("#333333"))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(Color.parseColor("#F5F5F5"))
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
            setTextColor(Color.parseColor("#666666"))
            setPadding(12, 12, 12, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(infoText)

        // Buttons
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
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
            text = if (isSpanish) "Agregar" else "Add"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#E91E63"))
            setPadding(32, 16, 32, 16)
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnAdd)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val name = nameInput.text?.toString()?.trim()
            if (name.isNullOrEmpty()) {
                return@setOnClickListener
            }

            val description = descriptionInput.text?.toString()?.trim() ?: ""

            val favoritePlace = favoriteManager.createFavoriteFromLocation(
                location = location,
                name = name,
                description = description,
                categories = setOf(ReportCategory.SAFETY),
                alertDistance = 1609.0
            )

            favoriteManager.addFavorite(favoritePlace)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun showCategorySelectionDialog(onCategorySelected: (ReportCategory) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Selecciona Categoría" else "Select Category"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
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
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(Color.parseColor("#666666"))
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

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        // Category header with icon
        val categoryTitle = TextView(context).apply {
            text = "${report.category.getIcon()} ${report.category.getDisplayName(isSpanish)}"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, report.category.getColorResId()))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(categoryTitle)

        // Content label
        val contentLabel = TextView(context).apply {
            text = if (isSpanish) "Contenido" else "Content"
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(contentLabel)

        // Report content
        val textReportContent = TextView(context).apply {
            text = report.originalText
            textSize = 16f
            setTextColor(Color.parseColor("#333333"))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(textReportContent)

        // Translate button
        val btnTranslate = Button(context).apply {
            text = if (isSpanish) "Traducir" else "Translate"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(20, 16, 20, 16)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(btnTranslate)

        // Time info
        val timeText = TextView(context).apply {
            text = "📍 ${if (isSpanish) "Reportado" else "Reported"}: ${getTimeAgo(report.timestamp)}"
            textSize = 14f
            setTextColor(Color.parseColor("#666666"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(timeText)

        // Close button
        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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
                        btnTranslate.setBackgroundColor(Color.parseColor("#4CAF50"))
                        btnTranslate.isEnabled = true
                        isTranslated = true
                    },
                    onError = { error ->
                        btnTranslate.text = if (isSpanish) "Error" else "Translation Failed"
                        btnTranslate.setBackgroundColor(Color.parseColor("#F44336"))
                        btnTranslate.isEnabled = true

                        Handler(Looper.getMainLooper()).postDelayed({
                            btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                            btnTranslate.setBackgroundColor(Color.parseColor("#4CAF50"))
                        }, 2000)
                    }
                )
            } else {
                textReportContent.text = report.originalText
                btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                btnTranslate.setBackgroundColor(Color.parseColor("#4CAF50"))
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

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Alertas Cercanas" else "Nearby Alerts"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // CREATE DIALOG FIRST - BEFORE SETTING CLICK LISTENERS
        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val alerts = alertManager.getNearbyUnviewedAlerts(userLocation)

        if (alerts.isEmpty()) {
            val messageText = TextView(context).apply {
                text = if (isSpanish) "No hay alertas nuevas en tu área" else "No new alerts in your area"
                textSize = 16f
                setTextColor(Color.parseColor("#666666"))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 0, 0, 24)
            }
            dialogLayout.addView(messageText)
        } else {
            val alertMessages = alerts.take(5).map { alert ->
                "${alert.report.category.getIcon()} ${alert.report.originalText.take(50)}..."
            }
            val messageText = TextView(context).apply {
                text = alertMessages.joinToString("\n\n")
                textSize = 16f
                setTextColor(Color.parseColor("#333333"))
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
            }
            dialogLayout.addView(messageText)

            val btnMarkRead = Button(context).apply {
                text = if (isSpanish) "Marcar como Leído" else "Mark as Read"
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#4CAF50"))
                setPadding(32, 16, 32, 16)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }
            dialogLayout.addView(btnMarkRead)

            btnMarkRead.setOnClickListener {
                alerts.forEach { alert ->
                    onMarkAsViewed(alert.report.id)
                }
                dialog.dismiss() // NOW dialog is available
            }
        }

        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        dialogLayout.addView(btnClose)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    fun showSettingsDialog(backendClient: BackendClient) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Configuración" else "Settings"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

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

        for ((index, item) in items.withIndex()) {
            val itemButton = Button(context).apply {
                text = item
                setTextColor(Color.parseColor("#333333"))
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                setPadding(20, 16, 20, 16)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
            }
            itemButton.setOnClickListener {
                when (index) {
                    0 -> showPreferencesDialog()
                    1 -> showUserGuideDialog()
                    2 -> showPrivacyPolicyDialog()
                    3 -> showAboutDialog()
                }
            }
            dialogLayout.addView(itemButton)
        }

        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
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

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAlertDistanceDialog(onDistanceSelected: (Double) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val options = if (isSpanish) {
            arrayOf(
                "1 MILLA",
                "2 MILLAS",
                "3 MILLAS",
                "5 MILLAS",
                "ÁREA DE CÓDIGO POSTAL",
                "TODO EL ESTADO"
            )
        } else {
            arrayOf(
                "1 MILE",
                "2 MILES",
                "3 MILES",
                "5 MILES",
                "ZIP CODE AREA",
                "STATE-WIDE"
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

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Distancia de Alerta" else "Alert Distance"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // NOW ADD BUTTONS WITH CLICK LISTENERS
        for ((index, option) in options.withIndex()) {
            val optionButton = Button(context).apply {
                text = option
                setTextColor(Color.parseColor("#333333"))
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                setPadding(20, 16, 20, 16)
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 12)
                }
            }
            optionButton.setOnClickListener {
                onDistanceSelected(distances[index])
                dialog.dismiss() // NOW dialog is available
            }
            dialogLayout.addView(optionButton)
        }

        dialog.show()
    }

    private fun showPreferencesDialog() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Preferencias de Usuario" else "User Preferences"
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        val currentAlertDistance = preferencesManager.getAlertDistanceText(preferencesManager.getSavedAlertDistance())
        val messageText = TextView(context).apply {
            text = if (isSpanish) {
                "Distancia de Alerta actual: $currentAlertDistance\n\nToca OK para cambiar"
            } else {
                "Current Alert Distance: $currentAlertDistance\n\nTap OK to change"
            }
            textSize = 16f
            setTextColor(Color.parseColor("#666666"))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(messageText)

        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnOk = Button(context).apply {
            text = "OK"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(32, 16, 32, 16)
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnOk)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnOk.setOnClickListener {
            dialog.dismiss()
            showAlertDistanceDialog { distance ->
                preferencesManager.saveAlertDistance(distance)
                // Show confirmation feedback
                Toast.makeText(context,
                    if (isSpanish) "Distancia actualizada" else "Distance updated",
                    Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

     fun showUserGuideDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Guía del Usuario" else "User Guide",
            getUserGuideContent()
        )
    }

     fun showPrivacyPolicyDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Política de Privacidad" else "Privacy Policy",
            getPrivacyPolicyContent()
        )
    }

     fun showAboutDialog() {
        showInfoDialog(
            if (preferencesManager.getSavedLanguage() == "es") "Acerca de" else "About",
            getAboutContent()
        )
    }

    private fun showInfoDialog(title: String, content: String) {
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.WHITE)
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor("#333333"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
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
            setTextColor(Color.parseColor("#333333"))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#F5F5F5"))
            setLineSpacing(4f, 1f)
        }
        scrollView.addView(contentText)
        dialogLayout.addView(scrollView)

        val btnClose = Button(context).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "Cerrar" else "Close"
            setTextColor(Color.parseColor("#666666"))
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

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

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