// File: managers/PhotoManager.kt
package com.money.derrite.managers

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PhotoManager(
    private val activity: AppCompatActivity,
    private val preferencesManager: PreferencesManager
) {

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 1002
    }

    interface PhotoCallback {
        fun onPhotoSelected(bitmap: Bitmap)
        fun onPhotoError(message: String)
    }

    private var currentCallback: PhotoCallback? = null

    private val cameraLauncher = activity.registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val cleanBitmap = stripPhotoMetadata(bitmap)
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Foto agregada anónimamente" else "Photo added anonymously"
            currentCallback?.onPhotoSelected(cleanBitmap)
        } else {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Error al tomar foto" else "Failed to take photo"
            currentCallback?.onPhotoError(message)
        }
    }

    private val photoPickerLauncher = activity.registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = activity.contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val cleanBitmap = stripPhotoMetadata(bitmap)
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Foto agregada anónimamente" else "Photo added anonymously"
                    currentCallback?.onPhotoSelected(cleanBitmap)
                } else {
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Error al cargar foto" else "Failed to load photo"
                    currentCallback?.onPhotoError(message)
                }
            } catch (e: Exception) {
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "Error al cargar foto" else "Failed to load photo"
                currentCallback?.onPhotoError(message)
            }
        }
    }

    fun showPhotoSelectionDialog(callback: PhotoCallback) {
        currentCallback = callback
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val options = if (isSpanish) {
            arrayOf("Tomar Foto", "Elegir de Galería")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }

        AlertDialog.Builder(activity)
            .setTitle(if (isSpanish) "Agregar Foto" else "Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionAndCapture()
                    1 -> photoPickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton(if (isSpanish) "Cancelar" else "Cancel", null)
            .show()
    }

    private fun requestCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }
    }

    fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(null)
                } else {
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Se necesita permiso de cámara" else "Camera permission needed"
                    currentCallback?.onPhotoError(message)
                }
            }
        }
    }

    private fun stripPhotoMetadata(originalBitmap: Bitmap): Bitmap {
        val cleanBitmap = Bitmap.createBitmap(
            originalBitmap.width,
            originalBitmap.height,
            Bitmap.Config.RGB_565
        )

        val canvas = Canvas(cleanBitmap)
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)

        return cleanBitmap
    }
}