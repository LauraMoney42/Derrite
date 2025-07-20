// File: managers/MapManager.kt - FIXED to completely prevent duplicate report pins
package com.money.pinlocal.managers

import com.money.pinlocal.data.ReportCategory
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.location.Address
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.money.pinlocal.R
import com.money.pinlocal.data.Report
import com.money.pinlocal.data.FavoritePlace
import org.osmdroid.config.Configuration as OSMConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import kotlin.math.*

class MapManager(private val context: Context) {

    companion object {
        private const val REPORT_RADIUS_METERS = 804.5
    }

    private var currentLocationMarker: Marker? = null
    private var currentSearchMarker: Marker? = null
    private val reportMarkers = mutableListOf<Marker>()
    private val reportCircles = mutableListOf<Polygon>()
    private val favoriteMarkers = mutableListOf<Marker>()
    private var lastZoomTime = 0L
    private val zoomThrottleMs = 150L

    // CRITICAL: Track which reports are already on the map to prevent duplicates
    private val reportMarkersMap = mutableMapOf<String, Marker>() // reportId -> Marker
    private val reportCirclesMap = mutableMapOf<String, Polygon>() // reportId -> Circle

    interface MapInteractionListener {
        fun onLongPress(location: GeoPoint)
        fun onReportMarkerClick(report: Report)
    }

    interface FavoriteMapInteractionListener {
        fun onFavoriteMarkerClick(favorite: FavoritePlace)
    }

    private fun getReportPinDrawable(category: ReportCategory): Int {
        return when (category) {
            ReportCategory.SAFETY -> R.drawable.ic_report_marker
            ReportCategory.FUN -> R.drawable.ic_fun_marker
            ReportCategory.LOST_MISSING -> R.drawable.ic_lost_marker
        }
    }

    fun setupMap(mapView: MapView, packageName: String) {
        try {
            OSMConfiguration.getInstance().load(context,
                context.getSharedPreferences("osmdroid", 0))
            OSMConfiguration.getInstance().userAgentValue = packageName

            mapView.apply {
                setTileSource(getBestSatelliteSource())
                setMultiTouchControls(true)
                setBuiltInZoomControls(false)
                setFlingEnabled(true)

                isTilesScaledToDpi = false
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false

                minZoomLevel = 5.0
                maxZoomLevel = 19.0

                addMapListener(object : org.osmdroid.events.MapListener {
                    override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                        return true
                    }

                    override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastZoomTime > zoomThrottleMs) {
                            lastZoomTime = currentTime
                        }
                        return true
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error setting up map: ${e.message}")
        }
    }

    fun enableMapInteraction(mapView: MapView, listener: MapInteractionListener) {
        val mapEventReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { listener.onLongPress(it) }
                return true
            }
        }

        val mapEventOverlay = MapEventsOverlay(mapEventReceiver)
        mapView.overlays.add(0, mapEventOverlay)
    }

    // Keep original method name for compatibility
    fun setupMapLongPressListener(mapView: MapView, listener: MapInteractionListener) {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { location ->
                    listener.onLongPress(location)
                }
                return true
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(0, mapEventsOverlay)
    }

    // Keep original method name for compatibility
    fun addLocationMarker(mapView: MapView, location: GeoPoint) {
        try {
            if (mapView.repository == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addLocationMarker(mapView, location)
                }, 500)
                return
            }

            currentLocationMarker?.let { marker ->
                try {
                    mapView.overlays.remove(marker)
                } catch (e: Exception) {
                    // Ignore removal errors
                }
            }

            currentLocationMarker = Marker(mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_location_pin)
                title = "Your Location"

                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }

            mapView.overlays.add(currentLocationMarker)
            mapView.invalidate()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addLocationMarker(mapView, location)
                } catch (retryE: Exception) {
                    // Give up if retry also fails
                }
            }, 1000)
        }
    }

    // New method with address support (can be used optionally)
    fun updateCurrentLocation(mapView: MapView, location: GeoPoint, address: String?) {
        try {
            currentLocationMarker?.let { marker ->
                try {
                    mapView.overlays.remove(marker)
                } catch (e: Exception) {
                    // Ignore removal errors
                }
            }

            currentLocationMarker = Marker(mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_location_pin)
                title = address ?: "Your Location"

                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }

            mapView.overlays.add(currentLocationMarker)
            mapView.invalidate()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    updateCurrentLocation(mapView, location, address)
                } catch (retryE: Exception) {
                    // Give up if retry also fails
                }
            }, 1000)
        }
    }

    fun animateToLocation(mapView: MapView, location: GeoPoint, zoomLevel: Double = 18.0) {
        mapView.controller.animateTo(location, zoomLevel, 1000L)
    }

    fun addSearchResultMarker(mapView: MapView, location: GeoPoint, address: Address) {
        try {
            currentSearchMarker?.let { marker ->
                try {
                    mapView.overlays.remove(marker)
                } catch (e: Exception) {
                    // Ignore removal errors
                }
            }

            currentSearchMarker = Marker(mapView).apply {
                position = location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_search_marker)
                title = "Search Result"

                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }

            mapView.overlays.add(currentSearchMarker)
            mapView.invalidate()
        } catch (e: Exception) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addSearchResultMarker(mapView, location, address)
                } catch (retryE: Exception) {
                    // Give up if retry also fails
                }
            }, 1000)
        }
    }

    fun clearSearchMarker(mapView: MapView) {
        currentSearchMarker?.let { marker ->
            mapView.overlays.remove(marker)
            currentSearchMarker = null
            mapView.invalidate()
        }
    }

    // FIXED: Prevent duplicate report pins with comprehensive checks
    fun addReportToMap(mapView: MapView, report: Report, listener: MapInteractionListener) {
        try {
            if (mapView.repository == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addReportToMap(mapView, report, listener)
                }, 500)
                return
            }

            // CRITICAL: Check if this report is already on the map
            if (reportMarkersMap.containsKey(report.id)) {
                android.util.Log.d("MapManager", "⏭️ Report ${report.id} already on map, skipping duplicate")
                return
            }

            // DOUBLE CHECK: Verify the report isn't in our lists either
            val existingMarker = reportMarkers.find { marker ->
                marker.title?.contains(report.category.getDisplayName(false)) == true &&
                        marker.position.distanceToAsDouble(report.location) < 10.0 // Within 10 meters
            }
            if (existingMarker != null) {
                android.util.Log.d("MapManager", "⏭️ Report ${report.id} appears to already exist at location, skipping")
                return
            }

            android.util.Log.d("MapManager", "📍 Adding report ${report.id} to map")

            // Create and add circle
            val circle = createReportCircle(report.location, report.category)
            mapView.overlays.add(circle)
            reportCircles.add(circle)
            reportCirclesMap[report.id] = circle // Track the circle

            // Create and add marker
            val marker = Marker(mapView).apply {
                position = report.location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                val drawableRes = when (report.category) {
                    ReportCategory.SAFETY -> R.drawable.ic_report_marker
                    ReportCategory.FUN -> R.drawable.ic_fun_marker
                    ReportCategory.LOST_MISSING -> R.drawable.ic_lost_marker
                }

                icon = ContextCompat.getDrawable(context, drawableRes)
                title = "${report.category.getIcon()} ${report.category.getDisplayName(false)}"

                setOnMarkerClickListener { _, _ ->
                    listener.onReportMarkerClick(report)
                    true
                }

                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }

            mapView.overlays.add(marker)
            reportMarkers.add(marker)
            reportMarkersMap[report.id] = marker // Track the marker by report ID

            android.util.Log.d("MapManager", "✅ Added report ${report.id} to map successfully")
            mapView.invalidate()
        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error adding report to map: ${e.message}")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addReportToMap(mapView, report, listener)
                } catch (retryE: Exception) {
                    android.util.Log.e("MapManager", "Retry failed: ${retryE.message}")
                }
            }, 1000)
        }
    }

    // Remove a specific report from the map
    fun removeReportFromMap(mapView: MapView, report: Report) {
        try {
            if (mapView.repository == null) {
                return
            }

            android.util.Log.d("MapManager", "🗑️ Removing report ${report.id} from map")

            // Remove marker using tracked map
            val marker = reportMarkersMap[report.id]
            if (marker != null) {
                try {
                    mapView.overlays.remove(marker)
                    reportMarkers.remove(marker)
                    reportMarkersMap.remove(report.id)
                    android.util.Log.d("MapManager", "✅ Removed marker for report ${report.id}")
                } catch (e: Exception) {
                    android.util.Log.e("MapManager", "Error removing marker: ${e.message}")
                }
            }

            // Remove circle using tracked map
            val circle = reportCirclesMap[report.id]
            if (circle != null) {
                try {
                    mapView.overlays.remove(circle)
                    reportCircles.remove(circle)
                    reportCirclesMap.remove(report.id)
                    android.util.Log.d("MapManager", "✅ Removed circle for report ${report.id}")
                } catch (e: Exception) {
                    android.util.Log.e("MapManager", "Error removing circle: ${e.message}")
                }
            }

            mapView.invalidate()
        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error in removeReportFromMap: ${e.message}")
        }
    }

    fun animateToReport(mapView: MapView, report: Report) {
        mapView.controller.animateTo(report.location, 18.0, 1000L)

        val reportMarker = reportMarkersMap[report.id]
        reportMarker?.let { marker ->
            marker.showInfoWindow()
            ObjectAnimator.ofFloat(marker, "alpha", 1f, 0.5f, 1f).apply {
                duration = 1000
                repeatCount = 2
                start()
            }
        }
    }

    // IMPROVED: Clear all report markers and circles with proper cleanup
    fun clearAllReportMarkers(mapView: MapView) {
        try {
            android.util.Log.d("MapManager", "🧹 Clearing all report markers and circles")

            // Remove all markers using tracked map
            val markersToRemove = reportMarkersMap.values.toList()
            for (marker in markersToRemove) {
                try {
                    mapView.overlays.remove(marker)
                } catch (e: Exception) {
                    // Continue on error
                }
            }

            // Remove all circles using tracked map
            val circlesToRemove = reportCirclesMap.values.toList()
            for (circle in circlesToRemove) {
                try {
                    mapView.overlays.remove(circle)
                } catch (e: Exception) {
                    // Continue on error
                }
            }

            // Clear all tracking collections
            reportMarkers.clear()
            reportCircles.clear()
            reportMarkersMap.clear()
            reportCirclesMap.clear()

            mapView.invalidate()
            android.util.Log.d("MapManager", "✅ Cleared all report markers and circles")
        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error clearing report markers: ${e.message}")
        }
    }

    // NEW: Safe method to refresh all reports on map (prevents duplicates)
    fun refreshReportsOnMap(mapView: MapView, reports: List<Report>, listener: MapInteractionListener) {
        try {
            android.util.Log.d("MapManager", "🔄 Refreshing map with ${reports.size} reports")

            // Clear existing reports
            clearAllReportMarkers(mapView)

            // Add all reports fresh
            reports.forEach { report ->
                addReportToMap(mapView, report, listener)
            }

            android.util.Log.d("MapManager", "✅ Map refresh completed")
        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error refreshing reports on map: ${e.message}")
        }
    }

    // NEW: Check if a report is already on the map
    fun isReportOnMap(reportId: String): Boolean {
        return reportMarkersMap.containsKey(reportId)
    }

    // NEW: Get count of reports currently on map
    fun getReportCountOnMap(): Int {
        return reportMarkersMap.size
    }

    fun clearFavoriteMarkers(mapView: MapView) {
        try {
            android.util.Log.d("MapManager", "=== CLEARING FAVORITE MARKERS ===")
            android.util.Log.d("MapManager", "favoriteMarkers.size: ${favoriteMarkers.size}")

            val markersToRemove = favoriteMarkers.toList()
            var removedCount = 0

            for ((index, marker) in markersToRemove.withIndex()) {
                try {
                    val wasRemoved = mapView.overlays.remove(marker)
                    android.util.Log.d("MapManager", "Marker $index (${marker.title}): removed=$wasRemoved")
                    if (wasRemoved) removedCount++
                } catch (e: Exception) {
                    android.util.Log.e("MapManager", "Error removing marker $index: ${e.message}")
                }
            }

            favoriteMarkers.clear()
            android.util.Log.d("MapManager", "Removed $removedCount markers from overlays")
            mapView.invalidate()
            android.util.Log.d("MapManager", "=== CLEAR FAVORITE MARKERS COMPLETED ===")

        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error in clearFavoriteMarkers: ${e.message}")
        }
    }

    fun addFavoriteToMap(mapView: MapView, favorite: FavoritePlace, listener: Any) {
        try {
            android.util.Log.d("MapManager", "=== addFavoriteToMap CALLED ===")
            android.util.Log.d("MapManager", "Favorite: ${favorite.name}")

            if (mapView.repository == null) {
                android.util.Log.w("MapManager", "MapView repository is null, scheduling retry")
                Handler(Looper.getMainLooper()).postDelayed({
                    addFavoriteToMap(mapView, favorite, listener)
                }, 500)
                return
            }

            android.util.Log.d("MapManager", "Creating favorite marker for: ${favorite.name}")

            val marker = Marker(mapView).apply {
                position = favorite.location
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                icon = ContextCompat.getDrawable(context, R.drawable.ic_favorite_pin)
                title = "💖 ${favorite.name}"
                snippet = favorite.getEnabledCategoriesText(false)

                setOnMarkerClickListener { _, _ ->
                    if (listener is FavoriteMapInteractionListener) {
                        listener.onFavoriteMarkerClick(favorite)
                    }
                    true
                }

                alpha = 0f
                ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                    duration = 500
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }

            mapView.overlays.add(marker)
            favoriteMarkers.add(marker)

            android.util.Log.d("MapManager", "✅ Added favorite marker successfully")
            mapView.invalidate()

        } catch (e: Exception) {
            android.util.Log.e("MapManager", "Error in addFavoriteToMap: ${e.message}")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addFavoriteToMap(mapView, favorite, listener)
                } catch (retryE: Exception) {
                    android.util.Log.e("MapManager", "Retry failed for favorite: ${retryE.message}")
                }
            }, 1000)
        }
    }

    private fun createReportCircle(center: GeoPoint, category: com.money.pinlocal.data.ReportCategory): Polygon {
        val circle = Polygon()

        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0
        val radiusInDegrees = REPORT_RADIUS_METERS / earthRadius * (180.0 / Math.PI)

        for (i in 0..360 step 10) {
            val angle = Math.toRadians(i.toDouble())
            val lat = center.latitude + radiusInDegrees * cos(angle)
            val lng = center.longitude + radiusInDegrees * sin(angle) / cos(Math.toRadians(center.latitude))
            points.add(GeoPoint(lat, lng))
        }

        circle.points = points
        circle.fillColor = ContextCompat.getColor(context, category.getFillColorResId())
        circle.strokeColor = ContextCompat.getColor(context, category.getStrokeColorResId())
        circle.strokeWidth = 3f

        return circle
    }

    private fun getBestSatelliteSource(): org.osmdroid.tileprovider.tilesource.ITileSource {
        return try {
            TileSourceFactory.USGS_SAT
        } catch (e: Exception) {
            try {
                TileSourceFactory.DEFAULT_TILE_SOURCE
            } catch (e2: Exception) {
                TileSourceFactory.MAPNIK
            }
        }
    }
}