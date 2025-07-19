// File: FavoritesActivity.kt - Apple-inspired Design
package com.money.pinlocal

import android.os.Bundle
import android.graphics.Color
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.money.pinlocal.data.FavoritePlace
import com.money.pinlocal.managers.FavoriteManager
import com.money.pinlocal.managers.PreferencesManager
import com.money.pinlocal.managers.ReportManager
import com.money.pinlocal.managers.LocationManager
import androidx.appcompat.app.AlertDialog
import com.money.pinlocal.managers.DialogManager

class FavoritesActivity : AppCompatActivity(), FavoriteManager.FavoriteListener {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var emptyStateContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupManagers()
        setupUI()
        loadFavorites()
    }

    private fun setupManagers() {
        preferencesManager = PreferencesManager(this)
        val reportManager = ReportManager(preferencesManager)
        val locationManager = LocationManager(this)
        val backendClient = BackendClient()

        favoriteManager = FavoriteManager(preferencesManager, reportManager, locationManager, backendClient)
        favoriteManager.setFavoriteListener(this)
    }

    private fun setupUI() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Main container with white background
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        // Header with clean white background
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 48, 24, 24)
            setBackgroundColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        // Back button with chevron
        val backButton = Button(this).apply {
            text = "‹ ${if (isSpanish) "Atrás" else "Back"}"
            setTextColor(Color.parseColor("#0066CC"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 17f
            setPadding(0, 12, 24, 12)
            setOnClickListener {
                setResult(RESULT_OK)
                finish()
            }
        }
        headerLayout.addView(backButton)

        // Title centered
        val titleText = TextView(this).apply {
            text = if (isSpanish) "Lugares Favoritos" else "Favorite Places"
            textSize = 22f
            setTextColor(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = android.view.Gravity.CENTER
            }
            gravity = android.view.Gravity.CENTER
        }
        headerLayout.addView(titleText)

        // Spacer to balance the back button
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
        }
        headerLayout.addView(spacer)

        mainLayout.addView(headerLayout)

        // Divider line
        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#C6C6C8"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            )
        }
        mainLayout.addView(divider)

        // Content container with light gray background
        val contentContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F2F2F7"))
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Empty state container
        emptyStateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(40, 80, 40, 80)
            visibility = android.view.View.VISIBLE
        }

        val emptyIcon = TextView(this).apply {
            text = "💙"
            textSize = 64f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
        }
        emptyStateContainer.addView(emptyIcon)

        val emptyTitle = TextView(this).apply {
            text = if (isSpanish) "No hay lugares favoritos" else "No Favorite Places"
            textSize = 22f
            setTextColor(Color.parseColor("#1C1C1E"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        emptyStateContainer.addView(emptyTitle)

        val emptyDescription = TextView(this).apply {
            text = if (isSpanish)
                "Agrega lugares importantes para recibir alertas cuando algo suceda cerca. Para privacidad, puedes usar apodos para lugares personales como tu casa o trabajo."
            else
                "Add important places to get alerts when something happens nearby. For privacy, you can use nicknames for personal locations like your home or work."
            textSize = 15f
            setTextColor(Color.parseColor("#8E8E93"))
            gravity = android.view.Gravity.CENTER
            setLineSpacing(4f, 1f)
            setPadding(0, 0, 0, 16) // Reduced bottom padding
        }
        emptyStateContainer.addView(emptyDescription)

        val faqLink = TextView(this).apply {
            text = if (isSpanish) "Ver FAQ para más detalles" else "See FAQ for more details"
            textSize = 15f
            setTextColor(Color.parseColor("#0066CC")) // Apple blue
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                // Show FAQ dialog
                val dialogManager = com.money.pinlocal.managers.DialogManager(this@FavoritesActivity, preferencesManager)
                dialogManager.showUserGuideDialog()
            }
        }
        emptyStateContainer.addView(faqLink)

        contentContainer.addView(emptyStateContainer)

        // Favorites container
        favoritesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        contentContainer.addView(favoritesContainer)

        mainLayout.addView(contentContainer)
        setContentView(mainLayout)
    }


    private fun loadFavorites() {
        favoriteManager.loadSavedData()
    }

    override fun onFavoritesUpdated(favorites: List<FavoritePlace>) {
        runOnUiThread {
            updateFavoritesList(favorites)
        }
    }

    override fun onFavoriteAlertsUpdated(alerts: List<com.money.pinlocal.data.FavoriteAlert>, hasUnviewed: Boolean) {
        // Handle alert updates if needed
    }

    override fun onNewFavoriteAlerts(alerts: List<com.money.pinlocal.data.FavoriteAlert>) {
        // Handle new alerts if needed
    }

    private fun updateFavoritesList(favorites: List<FavoritePlace>) {
        favoritesContainer.removeAllViews()

        if (favorites.isEmpty()) {
            emptyStateContainer.visibility = android.view.View.VISIBLE
        } else {
            emptyStateContainer.visibility = android.view.View.GONE

            favorites.forEach { favorite ->
                val favoriteCard = createFavoriteCard(favorite)
                favoritesContainer.addView(favoriteCard)
            }
        }
    }

    private fun createFavoriteCard(favorite: FavoritePlace): LinearLayout {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Card container with white background and rounded corners
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
            // Add subtle border
            background = ContextCompat.getDrawable(this@FavoritesActivity, R.drawable.white_card_background)
        }

        // Header with name and heart icon
        val headerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }

        val heartIcon = TextView(this).apply {
            text = "♥"
            textSize = 20f
            setTextColor(Color.parseColor("#FF69B4"))
            setPadding(0, 0, 12, 0)
        }
        headerLayout.addView(heartIcon)

        val nameText = TextView(this).apply {
            text = favorite.name
            textSize = 17f
            setTextColor(Color.parseColor("#1C1C1E"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(nameText)

        card.addView(headerLayout)

        // Description if available
        if (favorite.description.isNotEmpty()) {
            val descText = TextView(this).apply {
                text = favorite.description
                textSize = 13f
                setTextColor(Color.parseColor("#8E8E93"))
                setPadding(32, 0, 0, 8) // Indent to align with text after heart
            }
            card.addView(descText)
        }

        // Settings info
        val settingsText = TextView(this).apply {
            text = "${favorite.getAlertDistanceText(isSpanish)} • ${favorite.getEnabledAlertsText(isSpanish)}"
            textSize = 13f
            setTextColor(Color.parseColor("#8E8E93"))
            setPadding(32, 0, 0, 16) // Indent to align with text after heart
        }
        card.addView(settingsText)

        // Divider line
        val divider = android.view.View(this).apply {
            setBackgroundColor(Color.parseColor("#C6C6C8"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                setMargins(32, 0, 0, 16) // Indent divider
            }
        }
        card.addView(divider)

        // Action buttons with clean styling
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.END
        }


        val deleteButton = Button(this).apply {
            text = if (isSpanish) "Eliminar" else "Delete"
            setTextColor(Color.parseColor("#FF3B30"))
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 15f
            setPadding(20, 8, 20, 8)
            setOnClickListener {
                showDeleteConfirmation(favorite)
            }
        }

        buttonLayout.addView(deleteButton)
        card.addView(buttonLayout)

        return card
    }

    private fun showDeleteConfirmation(favorite: FavoritePlace) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(if (isSpanish) "Eliminar favorito" else "Delete Favorite")
            .setMessage(if (isSpanish) "¿Eliminar '${favorite.name}'?" else "Delete '${favorite.name}'?")
            .setPositiveButton(if (isSpanish) "Eliminar" else "Delete", null)
            .setNegativeButton(if (isSpanish) "Cancelar" else "Cancel", null)
            .create()

        dialog.show()

        // Style the buttons after showing
        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.apply {
            setTextColor(Color.parseColor("#FF3B30")) // Red color for delete
            textSize = 16f
            setOnClickListener {
                android.util.Log.d("FavoritesActivity", "Deleting favorite: ${favorite.name}")

                val success = favoriteManager.removeFavorite(favorite.id)
                android.util.Log.d("FavoritesActivity", "Delete result: $success")

                if (success) {
                    setResult(RESULT_OK)
                    loadFavorites()
                }
                dialog.dismiss()
            }
        }

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE)?.apply {
            setTextColor(Color.parseColor("#0066CC")) // Blue color for cancel
            textSize = 16f
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            favoriteManager.setFavoriteListener(null)
            setResult(RESULT_OK)
        } catch (e: Exception) {
            android.util.Log.e("FavoritesActivity", "Error in onDestroy: ${e.message}")
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
        setResult(RESULT_OK)
    }
}