// File: FavoritesActivity.kt
package com.money.pinlocal

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.money.pinlocal.data.FavoritePlace
import com.money.pinlocal.managers.FavoriteManager
import com.money.pinlocal.managers.PreferencesManager
import com.money.pinlocal.managers.ReportManager
import com.money.pinlocal.managers.LocationManager

class FavoritesActivity : AppCompatActivity(), FavoriteManager.FavoriteListener {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var favoritesContainer: LinearLayout
    private lateinit var emptyStateText: TextView

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
        // Create simple programmatic layout since we don't have layout files
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.black))
        }

        // Title
        val titleText = TextView(this).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "Lugares Favoritos" else "Favorite Places"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.white))
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        mainLayout.addView(titleText)

        // Back button
        val backButton = Button(this).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "← Volver" else "← Back"
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.transparent))
            setPadding(0, 16, 0, 32)
            setOnClickListener { finish() }
        }
        mainLayout.addView(backButton)

        // Empty state text
        emptyStateText = TextView(this).apply {
            text = if (preferencesManager.getSavedLanguage() == "es")
                "No tienes lugares favoritos aún.\nLong-press en el mapa para agregar lugares."
            else "No favorite places yet.\nLong-press on the map to add places."
            textSize = 16f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.darker_gray))
            setPadding(16, 32, 16, 32)
            gravity = android.view.Gravity.CENTER
        }
        mainLayout.addView(emptyStateText)

        // Container for favorites list
        favoritesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        mainLayout.addView(favoritesContainer)

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
            emptyStateText.visibility = TextView.VISIBLE
        } else {
            emptyStateText.visibility = TextView.GONE

            favorites.forEach { favorite ->
                val favoriteCard = createFavoriteCard(favorite)
                favoritesContainer.addView(favoriteCard)
            }
        }
    }

    private fun createFavoriteCard(favorite: FavoritePlace): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.darker_gray))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }

        // Favorite name
        val nameText = TextView(this).apply {
            text = "⭐ ${favorite.name}"
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.white))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }
        card.addView(nameText)

        // Description if available
        if (favorite.description.isNotEmpty()) {
            val descText = TextView(this).apply {
                text = favorite.description
                textSize = 14f
                setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.darker_gray))
                setPadding(0, 0, 0, 8)
            }
            card.addView(descText)
        }

        // Location info
        val locationText = TextView(this).apply {
            text = "📍 ${String.format("%.4f", favorite.location.latitude)}, ${String.format("%.4f", favorite.location.longitude)}"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.darker_gray))
            setPadding(0, 0, 0, 16)
        }
        card.addView(locationText)

        // Buttons
        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val editButton = Button(this).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "Editar" else "Edit"
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, R.color.primary))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 8, 0)
            }
            setOnClickListener {
                // For now, just finish activity - could implement edit functionality
                finish()
            }
        }

        val deleteButton = Button(this).apply {
            text = if (preferencesManager.getSavedLanguage() == "es") "Eliminar" else "Delete"
            setTextColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.white))
            setBackgroundColor(ContextCompat.getColor(this@FavoritesActivity, android.R.color.holo_red_dark))
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                favoriteManager.removeFavorite(favorite.id)
            }
        }

        buttonLayout.addView(editButton)
        buttonLayout.addView(deleteButton)
        card.addView(buttonLayout)

        return card
    }
}