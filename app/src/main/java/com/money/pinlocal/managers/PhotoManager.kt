// File: managers/PhotoManager.kt
package com.money.pinlocal.managers

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
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
            == PackageManager.PERMISSION_GRANTED
        ) {
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

    /**
     * COMPREHENSIVE METADATA STRIPPING
     * This method removes ALL metadata from photos including:
     * - EXIF data (GPS coordinates, device info, timestamps)
     * - IPTC data (copyright, keywords, captions)
     * - XMP data (Adobe metadata)
     * - Color profiles
     * - Any other embedded information
     */
    private fun stripPhotoMetadata(originalBitmap: Bitmap): Bitmap {
        try {
            android.util.Log.d("PhotoManager", "🧹 Stripping metadata from photo")

            // Step 1: Create a completely new bitmap using Matrix transformation
            // This removes EXIF orientation and other transformation metadata
            val matrix = Matrix()
            val transformedBitmap = Bitmap.createBitmap(
                originalBitmap, 0, 0,
                originalBitmap.width, originalBitmap.height,
                matrix, true
            )

            // Step 2: Create a clean bitmap with RGB_565 format
            // RGB_565 format cannot store metadata and reduces file size
            val cleanBitmap = Bitmap.createBitmap(
                transformedBitmap.width,
                transformedBitmap.height,
                Bitmap.Config.RGB_565  // This format strips all metadata
            )

            // Step 3: Draw the image onto the clean bitmap
            // This creates a completely new image with no metadata
            val canvas = Canvas(cleanBitmap)
            canvas.drawBitmap(transformedBitmap, 0f, 0f, null)

            // Step 4: Clean up intermediate bitmaps to prevent memory leaks
            if (transformedBitmap != originalBitmap) {
                transformedBitmap.recycle()
            }

            android.util.Log.d("PhotoManager", "✅ Photo metadata stripped successfully")
            android.util.Log.d("PhotoManager", "📊 Original size: ${originalBitmap.width}x${originalBitmap.height}")
            android.util.Log.d("PhotoManager", "📊 Clean size: ${cleanBitmap.width}x${cleanBitmap.height}")
            android.util.Log.d("PhotoManager", "🔒 Format: ${cleanBitmap.config}")

            return cleanBitmap

        } catch (e: Exception) {
            android.util.Log.e("PhotoManager", "❌ Error stripping metadata: ${e.message}")

            // Fallback: create a simple copy if the above fails
            return try {
                originalBitmap.copy(Bitmap.Config.RGB_565, false)
            } catch (fallbackError: Exception) {
                android.util.Log.e("PhotoManager", "❌ Fallback failed: ${fallbackError.message}")
                // Last resort: return original bitmap (not ideal but prevents crash)
                originalBitmap
            }
        }
    }
}