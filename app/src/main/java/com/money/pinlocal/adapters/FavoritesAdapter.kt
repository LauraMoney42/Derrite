// File: adapters/FavoritesAdapter.kt
package com.money.pinlocal.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.money.pinlocal.R
import com.money.pinlocal.data.FavoritePlace
import com.money.pinlocal.managers.PreferencesManager

class FavoritesAdapter(
    private val onFavoriteClick: (FavoritePlace) -> Unit,
    private val onViewOnMapClick: (FavoritePlace) -> Unit,
    private val onViewAlertsClick: (FavoritePlace) -> Unit,
    private val preferencesManager: PreferencesManager
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    private var favorites = listOf<FavoritePlace>()

    fun updateFavorites(newFavorites: List<FavoritePlace>) {
        favorites = newFavorites
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(favorites[position])
    }

    override fun getItemCount(): Int = favorites.size

    inner class FavoriteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textFavoriteName: TextView = itemView.findViewById(R.id.text_favorite_name)
        private val textAlertSettings: TextView = itemView.findViewById(R.id.text_alert_settings)
        private val textRecentAlerts: TextView = itemView.findViewById(R.id.text_recent_alerts)
        private val newAlertsBadge: View = itemView.findViewById(R.id.new_alerts_badge)
        private val btnFavoriteOptions: ImageButton = itemView.findViewById(R.id.btn_favorite_options)
        private val btnViewAlerts: Button = itemView.findViewById(R.id.btn_view_alerts)
        private val btnViewOnMap: Button = itemView.findViewById(R.id.btn_view_on_map)

        fun bind(favorite: FavoritePlace) {
            val isSpanish = preferencesManager.getSavedLanguage() == "es"

            // Set favorite name
            textFavoriteName.text = favorite.name

            // Set alert settings summary
            val distance = favorite.getAlertDistanceText(isSpanish)
            val categories = favorite.getEnabledCategoriesText(isSpanish)
            textAlertSettings.text = "$categories • $distance"

            // Set up click listeners
            btnFavoriteOptions.setOnClickListener {
                onFavoriteClick(favorite)
            }

            btnViewAlerts.setOnClickListener {
                onViewAlertsClick(favorite)
            }

            btnViewOnMap.setOnClickListener {
                onViewOnMapClick(favorite)
            }

            // Update button text based on language
            if (isSpanish) {
                btnViewAlerts.text = "Ver Alertas"
                btnViewOnMap.text = "Ver en Mapa"
            } else {
                btnViewAlerts.text = "View Alerts"
                btnViewOnMap.text = "View on Map"
            }

            // TODO: Update with actual alert counts when available
            // For now, hide recent alerts and badge
            textRecentAlerts.visibility = View.GONE
            newAlertsBadge.visibility = View.GONE
        }
    }
}