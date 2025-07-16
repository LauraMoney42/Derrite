// File: managers/MapManager.kt
package com.money.derrite.managers

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.location.Address
import android.os.Handler
import android.os.Looper
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.money.derrite.R
import com.money.derrite.data.Report
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
    private var lastZoomTime = 0L
    private val zoomThrottleMs = 150L

    interface MapInteractionListener {
        fun onLongPress(location: GeoPoint)
        fun onReportMarkerClick(report: Report)
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
                        if (currentTime - lastZoomTime < zoomThrottleMs) {
                            return true
                        }
                        lastZoomTime = currentTime
                        return true
                    }
                })
            }

            val defaultPoint = GeoPoint(40.7128, -74.0060)
            mapView.controller.setZoom(15.0)
            mapView.controller.setCenter(defaultPoint)
        } catch (e: Exception) {
            // Fallback to basic map
            try {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
            } catch (fallbackE: Exception) {
                // Log error but continue
            }
        }
    }

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

    fun addSearchResultMarker(mapView: MapView, location: GeoPoint, address: Address) {
        try {
            if (mapView.repository == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addSearchResultMarker(mapView, location, address)
                }, 500)
                return
            }

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

    fun addReportToMap(mapView: MapView, report: Report, listener: MapInteractionListener) {
        val circle = createReportCircle(report.location, report.category)
        mapView.overlays.add(circle)
        reportCircles.add(circle)

        val marker = Marker(mapView).apply {
            position = report.location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_report_marker)
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
        mapView.invalidate()
    }

    fun removeReportFromMap(mapView: MapView, report: Report) {
        try {
            if (mapView.repository == null) {
                return
            }

            val markerToRemove = reportMarkers.find { marker ->
                marker.position.latitude == report.location.latitude &&
                        marker.position.longitude == report.location.longitude
            }
            markerToRemove?.let { marker ->
                try {
                    mapView.overlays.remove(marker)
                    reportMarkers.remove(marker)
                } catch (e: Exception) {
                    // Log error but continue
                }
            }

            if (reportCircles.isNotEmpty()) {
                try {
                    val circle = reportCircles.removeAt(0)
                    mapView.overlays.remove(circle)
                } catch (e: Exception) {
                    // Log error but continue
                }
            }

            mapView.invalidate()
        } catch (e: Exception) {
            // Log error but continue
        }
    }

    fun animateToReport(mapView: MapView, report: Report) {
        mapView.controller.animateTo(report.location, 18.0, 1000L)

        val reportMarker = reportMarkers.find { marker ->
            marker.position.latitude == report.location.latitude &&
                    marker.position.longitude == report.location.longitude
        }

        reportMarker?.let { marker ->
            marker.showInfoWindow()
            ObjectAnimator.ofFloat(marker, "alpha", 1f, 0.5f, 1f).apply {
                duration = 1000
                repeatCount = 2
                start()
            }
        }
    }

    private fun createReportCircle(center: GeoPoint, category: com.money.derrite.data.ReportCategory): Polygon {
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