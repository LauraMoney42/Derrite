package com.garfunkel.derriteice

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fabLocation: FloatingActionButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusClose: ImageButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var currentLocationMarker: Marker? = null

    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure OSMDroid with better performance settings
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize views
        setupViews()
        setupMap()
        setupLocationButton()
        setupStatusCard()
    }

    private fun setupViews() {
        mapView = findViewById(R.id.mapView)
        fabLocation = findViewById(R.id.fab_location)
        statusCard = findViewById(R.id.status_card)
        statusText = findViewById(R.id.status_text)
        statusClose = findViewById(R.id.status_close)
    }

    private fun setupMap() {
        // Professional map configuration
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false) // Hide default zoom controls for cleaner look
            setFlingEnabled(true)
        }

        // Set initial location with smooth animation
        val startPoint = GeoPoint(40.7128, -74.0060) // NYC default
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(startPoint)
    }

    private fun setupLocationButton() {
        fabLocation.setOnClickListener {
            // Add subtle animation feedback
            animateButtonPress(fabLocation)
            getCurrentLocation()
        }
    }

    private fun setupStatusCard() {
        statusClose.setOnClickListener {
            hideStatusCard()
        }
    }

    private fun animateButtonPress(view: View) {
        val scaleDown = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 0.95f)
        val scaleUp = ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1.0f)

        scaleDown.duration = 100
        scaleUp.duration = 100
        scaleDown.interpolator = DecelerateInterpolator()
        scaleUp.interpolator = DecelerateInterpolator()

        scaleDown.start()
        Handler(Looper.getMainLooper()).postDelayed({
            scaleUp.start()
        }, 100)
    }

    private fun getCurrentLocation() {
        // Check permissions first
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        // Show loading state
        showStatusCard("Finding your location...", isLoading = true)

        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Smooth animation to user location
                    val userLocation = GeoPoint(location.latitude, location.longitude)

                    // Animate map to location
                    mapView.controller.animateTo(userLocation, 18.0, 1000L)

                    // Add professional location marker
                    addLocationMarker(userLocation)

                    // Show success message
                    showStatusCard("Location found!", isLoading = false)

                    // Auto-hide status after 3 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        hideStatusCard()
                    }, 3000)

                } else {
                    showStatusCard("Unable to get location", isLoading = false, isError = true)
                }
            }.addOnFailureListener { exception ->
                showStatusCard("Location error", isLoading = false, isError = true)
            }
        } catch (e: SecurityException) {
            showStatusCard("Location permission needed", isLoading = false, isError = true)
        }
    }

    private fun addLocationMarker(location: GeoPoint) {
        // Remove existing marker
        currentLocationMarker?.let { marker ->
            mapView.overlays.remove(marker)
        }

        // Create professional location marker
        currentLocationMarker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Use custom professional icon
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_pin)

            title = "Your Location"
            snippet = "Lat: ${String.format("%.4f", location.latitude)}, Lng: ${String.format("%.4f", location.longitude)}"

            // Add subtle entrance animation
            alpha = 0f
            ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        mapView.overlays.add(currentLocationMarker)
        mapView.invalidate()
    }

    private fun showStatusCard(message: String, isLoading: Boolean = false, isError: Boolean = false) {
        statusText.text = message

        // Set appropriate colors based on state
        val textColor = when {
            isError -> ContextCompat.getColor(this, R.color.error)
            isLoading -> ContextCompat.getColor(this, R.color.text_secondary)
            else -> ContextCompat.getColor(this, R.color.success)
        }
        statusText.setTextColor(textColor)

        // Show card with animation
        if (statusCard.visibility != View.VISIBLE) {
            statusCard.visibility = View.VISIBLE
            statusCard.alpha = 0f
            statusCard.translationY = -100f

            ObjectAnimator.ofFloat(statusCard, "alpha", 0f, 1f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(statusCard, "translationY", -100f, 0f).apply {
                duration = 300
                interpolator = DecelerateInterpolator()
                start()
            }
        }
    }

    private fun hideStatusCard() {
        if (statusCard.visibility == View.VISIBLE) {
            ObjectAnimator.ofFloat(statusCard, "alpha", 1f, 0f).apply {
                duration = 200
                start()
            }

            ObjectAnimator.ofFloat(statusCard, "translationY", 0f, -100f).apply {
                duration = 200
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                statusCard.visibility = View.GONE
            }, 200)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            showStatusCard("Location permission needed", isError = true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    getCurrentLocation()
                } else {
                    showStatusCard("Location permission is required", isError = true)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }
}