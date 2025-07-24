// File: managers/DialogManager.kt (Updated - Safety Reports Only)
package com.money.derrite.managers

import android.util.Base64
import android.graphics.Paint
import com.money.derrite.BackendClient
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
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
import com.money.derrite.R
import com.money.derrite.data.Report
import com.money.derrite.data.ReportCategory
import com.money.derrite.data.FavoritePlace
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.Locale
import android.graphics.Color

class DialogManager(
    private val context: Context,
    private val preferencesManager: PreferencesManager
) {

    fun showReportConfirmDialog(location: GeoPoint, onConfirm: (GeoPoint) -> Unit) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Confirmar Reporte de Seguridad" else "Confirm Safety Report"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        val messageText = TextView(context).apply {
            text = if (isSpanish) "Crear un reporte en esta ubicación" else "Create a report at this location"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(Color.parseColor("#5A8A5A"))
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
//        selectedCategory: ReportCategory,
//        onCategoryChange: (ReportCategory) -> Unit,
        onPhotoRequest: (onPhotoSelected: (Bitmap) -> Unit) -> Unit,
        onSubmit: (GeoPoint, String, Bitmap?, ReportCategory) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        // Force safety category since we only support safety reports now
        val currentCategory = ReportCategory.SAFETY
        var selectedPhoto: Bitmap? = null

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        // Title
        val titleText = TextView(context).apply {
            text = if (isSpanish) "Crear Reporte de Seguridad" else "Create Safety Report"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)


//        val categoryDisplay = TextView(context).apply {
//            text = "${currentCategory.getIcon()} ${currentCategory.getDisplayName(isSpanish)}"
//            textSize = 16f
//            setTextColor(ContextCompat.getColor(context, currentCategory.getColorResId()))
//            setPadding(16, 12, 16, 12)
//            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.MATCH_PARENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            ).apply {
//                setMargins(0, 0, 0, 20)
//            }
//        }
//        dialogLayout.addView(categoryDisplay)

        // Description section
        val descLabel = TextView(context).apply {
            text = if (isSpanish) "Descripción" else "Description"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(descLabel)

        val editReportText = com.google.android.material.textfield.TextInputEditText(context).apply {
            hint = if (isSpanish) "Describe la situación..." else "Describe the situation..."
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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

        // Photo preview container
        val photoPreviewContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = android.view.View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        val photoPreview = android.widget.ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(200, 200).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
        }
        photoPreviewContainer.addView(photoPreview)

        // DEFINE btnAddPhoto BEFORE using it
        val btnAddPhoto = Button(context).apply {
            text = if (isSpanish) "Agregar Foto" else "Add Photo"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            setPadding(20, 16, 20, 16)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }

        val removePhotoButton = Button(context).apply {
            text = if (isSpanish) "Remover Foto" else "Remove Photo"
            setTextColor(Color.parseColor("#FF3B30"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 8, 0, 0)
            }
            setOnClickListener {
                selectedPhoto = null
                photoPreviewContainer.visibility = android.view.View.GONE
                btnAddPhoto.text = if (isSpanish) "Agregar Foto" else "Add Photo"
            }
        }
        photoPreviewContainer.addView(removePhotoButton)

        // Add views to dialog
        dialogLayout.addView(photoPreviewContainer)
        dialogLayout.addView(btnAddPhoto)

        // Buttons layout
        val buttonLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(context).apply {
            text = if (isSpanish) "Cancelar" else "Cancel"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(Color.parseColor("#5A8A5A"))
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

        // NOW set click listeners (after all views are defined)
        btnAddPhoto.setOnClickListener {
            onPhotoRequest { bitmap ->
                selectedPhoto = bitmap
                photoPreview.setImageBitmap(bitmap)
                photoPreviewContainer.visibility = android.view.View.VISIBLE
                btnAddPhoto.text = if (isSpanish) "Cambiar Foto" else "Change Photo"

                android.util.Log.d("DialogManager", "Photo received in dialog: ${bitmap.width}x${bitmap.height}")
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSubmit.setOnClickListener {
            val reportText = editReportText.text?.toString()?.trim()
            if (reportText.isNullOrEmpty()) {
                editReportText.error = if (isSpanish) "Ingresa una descripción" else "Enter a description"
                return@setOnClickListener
            }

            android.util.Log.d("DialogManager", "Submitting safety report with photo: ${selectedPhoto != null}")
            onSubmit(location, reportText, selectedPhoto, currentCategory)
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface))
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
                        "Tipo: Seguridad"
            } else {
                "Alerts: ${favorite.getAlertDistanceText(false)}\n" +
                        "Type: Safety"
            }
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(16, 16, 16, 24)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setBackgroundColor(Color.parseColor("#5A8A5A"))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Eliminar Favorito" else "Remove Favorite"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 16)
        }
        dialogLayout.addView(titleText)

        val messageText = TextView(context).apply {
            text = if (isSpanish)
                "¿Estás seguro de que quieres eliminar '${favorite.name}' de tus favoritos?"
            else "Are you sure you want to remove '${favorite.name}' from your favorites?"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Editar Favorito" else "Edit Favorite"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Name input with current value
        val nameInput = com.google.android.material.textfield.TextInputEditText(context).apply {
            setText(favorite.name)
            hint = if (isSpanish) "Nombre del lugar" else "Place name"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(Color.parseColor("#5A8A5A"))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Alertas para ${favorite.name}" else "Alerts for ${favorite.name}"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        if (alerts.isEmpty()) {
            val messageText = TextView(context).apply {
                text = if (isSpanish) "No hay alertas nuevas para este favorito" else "No new alerts for this favorite"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(16, 16, 16, 16)
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
                setBackgroundColor(Color.parseColor("#5A8A5A"))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Agregar a Favoritos" else "Add to Favorites"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Name input
        val nameInput = TextInputEditText(context).apply {
            hint = if (isSpanish) "Nombre (ej. Escuela de Emma, Casa, Trabajo)" else "Name (e.g., Emma's School, Home, Work)"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(Color.parseColor("#999999"))
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
                "💡 Recibirás alertas de seguridad para este lugar incluso cuando la app esté cerrada"
            else
                "💡 You'll receive safety alerts for this place even when the app is closed"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(12, 12, 12, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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

            // Only create safety alerts since we removed other categories
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

    fun showReportViewDialog(
        report: Report,
        translationManager: TranslationManager,
        onMarkAsViewed: (String) -> Unit
    ) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(contentLabel)

        // Report content
        val textReportContent = TextView(context).apply {
            text = report.originalText
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(textReportContent)

        // ADD PHOTO DISPLAY
        if (report.hasPhoto && report.photo != null) {
            val photoLabel = TextView(context).apply {
                text = if (isSpanish) "Foto" else "Photo"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(0, 0, 0, 8)
            }
            dialogLayout.addView(photoLabel)

            val photoImageView = android.widget.ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    600
                ).apply {
                    setMargins(0, 0, 0, 24)
                }
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            }

            try {
                // Just set the bitmap directly if it exists
                photoImageView.setImageBitmap(report.photo)
                android.util.Log.d("DialogManager", "Photo displayed successfully")
            } catch (e: Exception) {
                android.util.Log.e("DialogManager", "Error displaying photo: ${e.message}")
                photoImageView.visibility = android.view.View.GONE
            }

            dialogLayout.addView(photoImageView)
        }
        val btnTranslate = TextView(context).apply {
            text = if (isSpanish) "Traducir" else "Translate"
            setTextColor(Color.parseColor("#2196F3")) // Blue color for hyperlink
            textSize = 16f
            setPadding(0, 16, 0, 16)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG // Underline for hyperlink style
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, // Only as wide as needed
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
                gravity = android.view.Gravity.START // Left aligned
            }

            // Add ripple effect when clicked
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.parseColor("#E3F2FD")),
                null,
                null
            )

            // Add some padding for easier clicking
            setPadding(8, 8, 8, 8)
        }
        dialogLayout.addView(btnTranslate)

        // Update the click listener accordingly - the functionality remains the same
        var isTranslated = false

        btnTranslate.setOnClickListener {
            if (!isTranslated) {
                btnTranslate.text = if (isSpanish) "Traduciendo..." else "Translating..."
                btnTranslate.setTextColor(ContextCompat.getColor(context, R.color.text_secondary)) // Gray during loading
                btnTranslate.paintFlags = btnTranslate.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv() // Remove underline during loading

                val targetLang = preferencesManager.getSavedLanguage()
                translationManager.translateText(
                    text = report.originalText,
                    fromLang = report.originalLanguage,
                    toLang = targetLang,
                    onSuccess = { translatedText ->
                        textReportContent.text = translatedText
                        btnTranslate.text = if (isSpanish) "Mostrar Original" else "Show Original"
                        btnTranslate.setTextColor(Color.parseColor("#2196F3")) // Back to blue
                        btnTranslate.paintFlags = btnTranslate.paintFlags or Paint.UNDERLINE_TEXT_FLAG // Add underline back
                        isTranslated = true
                    },
                    onError = { error ->
                        btnTranslate.text = if (isSpanish) "Error en traducción" else "Translation Failed"
                        btnTranslate.setTextColor(Color.parseColor("#F44336")) // Red for error
                        btnTranslate.paintFlags = btnTranslate.paintFlags or Paint.UNDERLINE_TEXT_FLAG

                        Handler(Looper.getMainLooper()).postDelayed({
                            btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                            btnTranslate.setTextColor(Color.parseColor("#2196F3")) // Back to blue
                        }, 2000)
                    }
                )
            } else {
                textReportContent.text = report.originalText
                btnTranslate.text = if (isSpanish) "Traducir" else "Translate"
                btnTranslate.setTextColor(Color.parseColor("#2196F3"))
                btnTranslate.paintFlags = btnTranslate.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                isTranslated = false
            }
        }

        // Time info
        val timeText = TextView(context).apply {
            text = "📍 ${if (isSpanish) "Reportado" else "Reported"}: ${getTimeAgo(report.timestamp)}"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(timeText)

        // Close button
        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Alertas de Seguridad Cercanas" else "Nearby Safety Alerts"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
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
                text = if (isSpanish) "No hay alertas de seguridad nuevas en tu área" else "No new safety alerts in your area"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setPadding(16, 16, 16, 16)
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
                setBackgroundColor(Color.parseColor("#5A8A5A"))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Configuración" else "Settings"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        val items = if (isSpanish) {
            arrayOf(
                "Preferencias de Usuario",
                "Guía del Usuario",
                "Política de Privacidad",
                "Acerca de"
            )
        } else {
            arrayOf(
                "User Preferences",
                "User Guide",
                "Privacy Policy",
                "About"
            )
        }

        for ((index, item) in items.withIndex()) {
            val itemButton = Button(context).apply {
                text = item
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
                "20 MILLAS",
                "TODO EL ESTADO"
            )
        } else {
            arrayOf(
                "1 MILE",
                "2 MILES",
                "3 MILES",
                "5 MILES",
                "20 MILES",
                "STATE-WIDE"
            )
        }

        val distances = arrayOf(
            1609.0,    // 1 mile
            3218.0,    // 2 miles
            4827.0,    // 3 miles
            8047.0,    // 5 miles
            32187.0,   // 20 miles
            160934.0   // state-wide
        )

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Distancia de Alerta" else "Alert Distance"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
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
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
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

    // Complete updated showPreferencesDialog method in DialogManager.kt
    private fun showPreferencesDialog() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = if (isSpanish) "Preferencias de Usuario" else "User Preferences"
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        dialogLayout.addView(titleText)

        // Alert Distance Section
        val distanceLabel = TextView(context).apply {
            text = if (isSpanish) "Distancia de Alerta" else "Alert Distance"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 8)
        }
        dialogLayout.addView(distanceLabel)

        val currentAlertDistance = preferencesManager.getAlertDistanceText(preferencesManager.getSavedAlertDistance())
        val distanceDisplay = TextView(context).apply {
            text = currentAlertDistance
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(16, 12, 16, 12)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 20)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                showAlertDistanceDialog { distance ->
                    preferencesManager.saveAlertDistance(distance)
                    text = preferencesManager.getAlertDistanceText(distance)
                }
            }
        }
        dialogLayout.addView(distanceDisplay)

        // Alarm Override Section
        val alarmLabel = TextView(context).apply {
            text = if (isSpanish) "Configuración de Alarma" else "Alarm Settings"
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(0, 0, 0, 12)
        }
        dialogLayout.addView(alarmLabel)

        val alarmLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 8)
            }
        }

        val alarmSwitchLabel = TextView(context).apply {
            text = if (isSpanish) "Alarma de SEGURIDAD anula modo silencio" else "SAFETY Alarm overrides silent mode"
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val alarmSwitch = com.google.android.material.materialswitch.MaterialSwitch(context).apply {
            isChecked = preferencesManager.getAlarmOverrideSilent()

            // Set custom colors for better visibility
            val enabledColor = ContextCompat.getColor(context, R.color.accent) // Your system blue
            val disabledColor = ContextCompat.getColor(context, R.color.text_tertiary) // Gray

            // Track colors (background)
            val trackColors = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    enabledColor,
                    disabledColor
                )
            )
            trackTintList = trackColors

            // Thumb colors (circle)
            val thumbColors = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf(-android.R.attr.state_checked)
                ),
                intArrayOf(
                    Color.WHITE,
                    Color.WHITE
                )
            )
            thumbTintList = thumbColors

            setOnCheckedChangeListener { _, isChecked ->
                preferencesManager.saveAlarmOverrideSilent(isChecked)
            }
        }

        alarmLayout.addView(alarmSwitchLabel)
        alarmLayout.addView(alarmSwitch)
        dialogLayout.addView(alarmLayout)

        // Alarm description
        val alarmDescription = TextView(context).apply {
            text = if (isSpanish) {
                "Cuando está activado, las alertas de SEGURIDAD reproducirán un sonido fuerte incluso en modo silencio"
            } else {
                "When enabled, SAFETY alerts will play a loud alarm sound even when in silent mode"
            }
            textSize = 12f
            setTextColor(Color.parseColor("#888888"))
            setPadding(16, 4, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(alarmDescription)

        // Info Section
        val infoText = TextView(context).apply {
            text = if (isSpanish) {
                "Las alertas se activarán cuando se reporten incidentes de seguridad cerca de tu ubicación actual o de tus lugares favoritos, dentro de tu distancia elegida"
            } else {
                "Alerts will trigger when safety incidents are reported near your current location or your favorite places, within your chosen distance"
            }
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(infoText)

        // Close Button
        val btnClose = Button(context).apply {
            text = if (isSpanish) "Cerrar" else "Close"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
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
            setBackgroundColor(ContextCompat.getColor(context, R.color.dialog_background))
            background = ContextCompat.getDrawable(context, R.drawable.white_card_background)
        }

        val titleText = TextView(context).apply {
            text = title
            textSize = 20f
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
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
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setPadding(16, 16, 16, 16)
            setBackgroundColor(ContextCompat.getColor(context, R.color.surface_variant))
            setLineSpacing(4f, 1f)

            // Make links clickable
            autoLinkMask = android.text.util.Linkify.WEB_URLS
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
            setLinkTextColor(Color.parseColor("#007AFF")) // Blue color
        }
        scrollView.addView(contentText)
        dialogLayout.addView(scrollView)

        val btnClose = Button(context).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "Cerrar" else "Close"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
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
            "CREAR UN REPORTE DE SEGURIDAD\n" +
                    "• Presiona y mantén presionado en cualquier lugar del mapa para crear un reporte\n" +
                    "• Describe la situación de seguridad\n" +
                    "• Opcional: Agrega una foto anónima\n\n" +
                    "FAVORITOS\n" +
                    "• Presiona y mantén presionado en el mapa y selecciona 'Agregar a Favoritos'\n" +
                    "• Nombra el lugar y configura las alertas\n" +
                    "• Recibe notificaciones cuando algo pase cerca\n\n" +
                    "ALERTAS\n" +
                    "• Recibe notificaciones cuando se hagan reportes de seguridad dentro de tu distancia elegida\n" +
                    "• Recibe alertas automáticas de seguridad cuando algo suceda cerca de tu ubicación actual y tus lugares favoritos\n" +
                    "• Personaliza la distancia de alerta en Configuración\n" +
                    "• Las alertas de seguridad pueden sonar con volumen alto incluso cuando tu teléfono esté en modo silencio o no molestar\n" +
                    "• Puedes activar o desactivar esta función en Configuración > Preferencias > 'Alarma anula modo silencio'\n\n" +
                    "PRIVACIDAD\n" +
                    "• Completamente anónimo - no se requieren nombres de usuario\n" +
                    "• No se guardan direcciones IP, IDs de dispositivo, o información personal\n" +
                    "• Tus nombres de lugares favoritos se guardan solo en tu teléfono, no en nuestros servidores\n" +
                    "• Para máxima privacidad, usa nombres genéricos como 'Lugar 1' en lugar de 'Casa' o 'Escuela'\n" +
                    "• Todos los reportes se eliminan automáticamente después de 8 horas"
        } else {
            "CREATE A SAFETY REPORT\n" +
                    "• Long press anywhere on the map to create a report\n" +
                    "• Describe the safety situation\n" +
                    "• Optional: Add an anonymous photo\n\n" +
                    "FAVORITES\n" +
                    "• Long press on the map and select 'Add to Favorites'\n" +
                    "• Name the place and configure alerts\n" +
                    "• Get notified when something happens nearby\n\n" +
                    "ALERTS\n" +
                    "• Get notified when safety reports are made within your chosen distance\n" +
                    "• Receive automatic safety alerts when something happens near your current location and your favorite places\n" +
                    "• Customize alert distance in Settings\n" +
                    "• Safety alerts can sound at full volume even when your phone is on silent or do not disturb mode\n" +
                    "• You can enable or disable this feature in Settings > Preferences > 'Alarm overrides silent mode'\n\n" +
                    "PRIVACY\n" +
                    "• Completely anonymous - no usernames required\n" +
                    "• No IP addresses, device IDs, or personal information is stored\n" +
                    "• Your favorite place names are saved only on your phone, not on our servers\n" +
                    "• For maximum privacy, use generic names like 'Place 1' instead of 'Home' or 'School'\n" +
                    "• All reports automatically delete after 8 hours"
        }
    }

    private fun getPrivacyPolicyContent(): String {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        return if (isSpanish) {
            "ANÓNIMO POR DISEÑO\n" +
                    "• No se requieren cuentas de usuario o registro\n" +
                    "• No se recopilan direcciones IP, IDs de dispositivo, o información personal\n" +
                    "• Completamente anónimo sin identificadores únicos\n\n" +
                    "PRIVACIDAD DE UBICACIÓN\n" +
                    "• Las coordenadas GPS nunca se almacenan en nuestros servidores\n" +
                    "• Solo se usan zonas anónimas de 500m para procesamiento\n" +
                    "• Los nombres de lugares favoritos se guardan solo en tu teléfono\n\n" +
                    "PROTECCIÓN DE FOTOS\n" +
                    "• Toda información oculta se elimina de las fotos antes de enviarlas\n" +
                    "• Tu teléfono borra datos como ubicación GPS de la imagen\n" +
                    "• El servidor también elimina cualquier información personal restante\n" +
                    "• Solo se guarda la imagen sin datos personales\n\n" +
                    "ALMACENAMIENTO DE DATOS\n" +
                    "• Todos los reportes se auto-eliminan después de 8 horas\n" +
                    "• No hay almacenamiento permanente de datos de usuario\n" +
                    "• Los favoritos no se almacenan en nuestros servidores\n\n" +
                    "RECOMENDACIONES DE PRIVACIDAD\n" +
                    "• Usa nombres genéricos como 'Lugar 1' en lugar de 'Casa' o 'Escuela'\n" +
                    "• Evita incluir información personal en las descripciones de reportes\n" +
                    "• Las fotos son seguras - toda información personal se elimina automáticamente"
        } else {
            "ANONYMOUS BY DESIGN\n" +
                    "• No user accounts or registration required\n" +
                    "• No IP addresses, device IDs, or personal information collected\n" +
                    "• Completely anonymous with no unique identifiers\n\n" +
                    "LOCATION PRIVACY\n" +
                    "• GPS coordinates are never stored on our servers\n" +
                    "• Only anonymous 500m zones are used for processing\n" +
                    "• Favorite place names are saved only on your phone\n\n" +
                    "PHOTO PROTECTION\n" +
                    "• All hidden information is removed from photos before sending\n" +
                    "• Your phone deletes data like GPS location from the image\n" +
                    "• The server also removes any remaining personal information\n" +
                    "• Only the clean image is saved with no personal data\n\n" +
                    "DATA STORAGE\n" +
                    "• All reports auto-delete after 8 hours\n" +
                    "• No permanent storage of any user data\n" +
                    "• Favorites are not stored on our servers\n\n" +
                    "PRIVACY RECOMMENDATIONS\n" +
                    "• Use generic names like 'Place 1' instead of 'Home' or 'School'\n" +
                    "• Avoid including personal information in report descriptions\n" +
                    "• Photos are safe - all personal information is automatically removed"
        }
    }

    private fun getAboutContent(): String {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        return if (isSpanish) {
            "MISIÓN\n" +
                    "Derrite es una plataforma de reportes de seguridad anónima y privada, diseñada para ayudar a las comunidades a mantenerse informadas sobre problemas de seguridad locales.\n\n" +
                    "CARACTERÍSTICAS\n" +
                    "• Reportes de seguridad anónimos con ubicación\n" +
                    "• Lugares favoritos con alertas personalizadas\n" +
                    "• Alertas en tiempo real\n" +
                    "• Soporte bilingüe (Español/Inglés)\n" +
                    "• Protección completa de privacidad\n\n" +
                    "ALERTAS DE SEGURIDAD EN SEGUNDO PLANO\n" +
                    "Incluso cuando la aplicación no está abierta, recibirás alertas automáticas de seguridad para tus lugares favoritos. El sistema monitorea continuamente tu área y lugares importantes, enviando notificaciones inmediatas cuando se reportan problemas de seguridad cerca.\n\n" +
                    "PRIVACIDAD PRIMERO\n" +
                    "Construido desde cero con la privacidad como principio fundamental. Nunca se almacenan, rastrean o comparten datos de usuario.\n\n" +
                    "Compatible con dispositivos Android más antiguos\n" +
                    "¡APLICACIÓN iOS PRÓXIMAMENTE!\n\n" +
                    "kindcode.us"
        } else {
            "MISSION\n" +
                    "Derrite is a privacy-first, anonymous safety reporting platform designed to help communities stay informed about local safety concerns.\n\n" +
                    "FEATURES\n" +
                    "• Anonymous safety reporting with location\n" +
                    "• Favorite places with custom alerts\n" +
                    "• Real-time safety alerts\n" +
                    "• Bilingual support (Spanish/English)\n" +
                    "• Complete privacy protection\n\n" +
                    "BACKGROUND SAFETY ALERTS\n" +
                    "Even when the app isn't open, you'll receive automatic safety alerts for your favorite places. The system continuously monitors your area and important locations, sending immediate notifications when safety concerns are reported nearby.\n\n" +
                    "PRIVACY FIRST\n" +
                    "Built from the ground up with privacy as the core principle. No user data is ever stored, tracked, or shared.\n\n" +
                    "Compatible with older Android devices.\n" +
                    "iOS APP COMING SOON!\n\n" +
                    "kindcode.us"
        }
    }
}