package com.garfunkel.derriteice

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.config.Configuration as OSMConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import java.io.IOException
import java.util.Locale
import java.util.UUID
import kotlin.math.*

data class Report(
    val id: String,
    val location: GeoPoint,
    val text: String,
    val hasPhoto: Boolean,
    val photo: Bitmap?,
    val timestamp: Long,
    val expiresAt: Long
)

data class Alert(
    val id: String,
    val report: Report,
    val distanceFromUser: Double, // in meters
    val isViewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var fabLocation: FloatingActionButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusClose: ImageButton
    private lateinit var instructionOverlay: LinearLayout
    private lateinit var btnLanguageToggle: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAlerts: ImageButton
    private lateinit var searchBar: TextInputEditText
    private lateinit var btnSearch: ImageButton
    private lateinit var btnClearSearch: ImageButton
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private lateinit var preferences: SharedPreferences

    private var currentLocationMarker: Marker? = null
    private var currentSearchMarker: Marker? = null
    private var currentPhoto: Bitmap? = null
    private var currentLocation: Location? = null
    private val activeReports = mutableListOf<Report>()
    private val reportMarkers = mutableListOf<Marker>()
    private val reportCircles = mutableListOf<Polygon>()
    private val activeAlerts = mutableListOf<Alert>()
    private val viewedAlertIds = mutableSetOf<String>()
    private var hasInitialLocationSet = false
    private var currentAlertsDialog: AlertDialog? = null

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val CAMERA_PERMISSION_REQUEST = 1002
    private val REPORT_RADIUS_METERS = 804.5 // 0.5 miles (hyper-local coverage)
    private val REPORT_DURATION_HOURS = 8L
    private val ALERT_RADIUS_METERS = 1609.0 // 1 mile for alerts
    private val ZIP_CODE_RADIUS_METERS = 8047.0 // ~5 miles (approximate zip code area)

    // Camera launcher for photo capture
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            currentPhoto = stripPhotoMetadata(bitmap)
            showStatusCard(getString(R.string.photo_added_anonymously), isLoading = false)
        }
    }

    // Photo picker for gallery images
    private val photoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    currentPhoto = stripPhotoMetadata(bitmap)
                    showStatusCard(getString(R.string.photo_added_anonymously), isLoading = false)
                } else {
                    showStatusCard(getString(R.string.failed_to_load_photo), isError = true)
                }
            } catch (e: Exception) {
                showStatusCard(getString(R.string.failed_to_load_photo), isError = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize preferences
        preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        // Load viewed alerts from preferences
        loadViewedAlerts()

        // Set app language to Spanish by default (or user's saved preference)
        setAppLanguage(getSavedLanguage())

        // Configure OSMDroid
        OSMConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", 0))
        OSMConfiguration.getInstance().userAgentValue = packageName

        setContentView(R.layout.activity_main)

        // Initialize services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        // Initialize views
        setupViews()
        setupMap()
        setupLocationButton()
        setupStatusCard()
        setupInstructionOverlay()
        setupBottomNavigation()
        setupSearchBar()

        // Auto-center on user location when app opens
        autoLocateOnStartup()

        // Start cleanup timer for expired reports
        startReportCleanupTimer()

        // Start periodic alert checking
        startAlertChecker()
    }

    // Alert System Implementation
    private fun loadViewedAlerts() {
        val viewedSet = preferences.getStringSet("viewed_alerts", emptySet()) ?: emptySet()
        viewedAlertIds.clear()
        viewedAlertIds.addAll(viewedSet)
    }

    private fun saveViewedAlerts() {
        preferences.edit().putStringSet("viewed_alerts", viewedAlertIds).apply()
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun checkForNewAlerts() {
        currentLocation?.let { location ->
            val newAlerts = mutableListOf<Alert>()

            // Check all active reports for proximity to user
            for (report in activeReports) {
                val distance = calculateDistance(
                    location.latitude, location.longitude,
                    report.location.latitude, report.location.longitude
                )

                // If report is within alert radius (and not the user's own report based on timing)
                if (distance <= ZIP_CODE_RADIUS_METERS) {
                    val existingAlert = activeAlerts.find { it.report.id == report.id }
                    if (existingAlert == null) {
                        val alert = Alert(
                            id = UUID.randomUUID().toString(),
                            report = report,
                            distanceFromUser = distance,
                            isViewed = viewedAlertIds.contains(report.id)
                        )
                        newAlerts.add(alert)
                        activeAlerts.add(alert)
                    }
                }
            }

            // Update alerts button if there are unviewed alerts
            val hasUnviewed = activeAlerts.any { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
            updateAlertsButtonColor(hasUnviewed)

            // Show brief notification if new alerts were found
            if (newAlerts.isNotEmpty()) {
                val unviewedNewAlerts = newAlerts.filter { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
                if (unviewedNewAlerts.isNotEmpty()) {
                    showStatusCard(getString(R.string.new_alerts_found, unviewedNewAlerts.size), isLoading = false)
                    Handler(Looper.getMainLooper()).postDelayed({
                        hideStatusCard()
                    }, 3000)
                }
            }
        }
    }

    private fun startAlertChecker() {
        val alertCheckRunnable = object : Runnable {
            override fun run() {
                checkForNewAlerts()
                // Check for alerts every 2 minutes
                Handler(Looper.getMainLooper()).postDelayed(this, 2 * 60 * 1000)
            }
        }

        // Start alert checking after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed(alertCheckRunnable, 10000)
    }

    private fun openAlertsScreen() {
        currentLocation?.let { location ->
            showAlertsDialog(location)
        } ?: run {
            showStatusCard(getString(R.string.location_needed_for_alerts), isError = true)
        }
    }

    private fun showAlertsDialog(userLocation: Location) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_alerts, null)
        currentAlertsDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val btnCloseAlerts = dialogView.findViewById<ImageButton>(R.id.btn_close_alerts)
        val textAlertLocation = dialogView.findViewById<TextView>(R.id.text_alert_location)
        val alertsContainer = dialogView.findViewById<LinearLayout>(R.id.alerts_container)
        val noAlertsContainer = dialogView.findViewById<LinearLayout>(R.id.no_alerts_container)
        val loadingContainer = dialogView.findViewById<LinearLayout>(R.id.loading_container)
        val btnRefreshAlerts = dialogView.findViewById<Button>(R.id.btn_refresh_alerts)
        val btnMarkAllRead = dialogView.findViewById<Button>(R.id.btn_mark_all_read)

        // Show loading initially
        loadingContainer.visibility = View.VISIBLE
        noAlertsContainer.visibility = View.GONE

        // Initially hide Mark All Read button until we know if there are alerts
        btnMarkAllRead.visibility = View.GONE

        // Get user's current area description
        updateLocationDescription(userLocation, textAlertLocation)

        btnCloseAlerts.setOnClickListener {
            currentAlertsDialog?.dismiss()
        }

        btnRefreshAlerts.setOnClickListener {
            refreshAlerts(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }

        btnMarkAllRead.setOnClickListener {
            markAllAlertsAsRead()
            loadAlertsIntoDialog(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }

        // Load alerts after a short delay to show loading state
        Handler(Looper.getMainLooper()).postDelayed({
            loadAlertsIntoDialog(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }, 1000)

        currentAlertsDialog?.show()
    }

    private fun updateLocationDescription(location: Location, textView: TextView) {
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val locationText = buildString {
                    if (!address.locality.isNullOrEmpty()) {
                        append(address.locality)
                    }
                    if (!address.adminArea.isNullOrEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(address.adminArea)
                    }
                    if (!address.postalCode.isNullOrEmpty()) {
                        if (isNotEmpty()) append(" ")
                        append(address.postalCode)
                    }
                }
                textView.text = getString(R.string.alerts_for_area, locationText.ifEmpty { getString(R.string.your_area) })
            } else {
                textView.text = getString(R.string.alerts_for_your_area)
            }
        } catch (e: IOException) {
            textView.text = getString(R.string.alerts_for_your_area)
        }
    }

    private fun refreshAlerts(alertsContainer: LinearLayout, noAlertsContainer: LinearLayout,
                              loadingContainer: LinearLayout, userLocation: Location) {
        loadingContainer.visibility = View.VISIBLE
        noAlertsContainer.visibility = View.GONE
        alertsContainer.removeAllViews()

        // Hide Mark All Read button during loading
        currentAlertsDialog?.let { dialog ->
            val btnMarkAllRead = dialog.findViewById<Button>(R.id.btn_mark_all_read)
            btnMarkAllRead?.visibility = View.GONE
        }

        checkForNewAlerts()

        Handler(Looper.getMainLooper()).postDelayed({
            loadAlertsIntoDialog(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }, 1000)
    }

    private fun loadAlertsIntoDialog(alertsContainer: LinearLayout, noAlertsContainer: LinearLayout,
                                     loadingContainer: LinearLayout, userLocation: Location) {
        alertsContainer.removeAllViews()

        // Filter alerts within ZIP code area, not viewed, and sort by distance
        val nearbyUnviewedAlerts = activeAlerts
            .filter { it.distanceFromUser <= ZIP_CODE_RADIUS_METERS }
            .filter { !viewedAlertIds.contains(it.report.id) }
            .sortedBy { it.distanceFromUser }

        loadingContainer.visibility = View.GONE

        // Get the dialog buttons
        currentAlertsDialog?.let { dialog ->
            val btnMarkAllRead = dialog.findViewById<Button>(R.id.btn_mark_all_read)

            if (nearbyUnviewedAlerts.isEmpty()) {
                noAlertsContainer.visibility = View.VISIBLE
                btnMarkAllRead?.visibility = View.GONE  // Hide button when no alerts
            } else {
                noAlertsContainer.visibility = View.GONE
                btnMarkAllRead?.visibility = View.VISIBLE  // Show button when alerts exist

                for (alert in nearbyUnviewedAlerts) {
                    val alertItemView = createAlertItemView(alert)
                    alertsContainer.addView(alertItemView)
                }
            }
        }
    }

    private fun createAlertItemView(alert: Alert): View {
        val alertView = LayoutInflater.from(this).inflate(R.layout.item_alert, null)

        val alertIcon = alertView.findViewById<ImageView>(R.id.alert_icon)
        val textAlertDistance = alertView.findViewById<TextView>(R.id.text_alert_distance)
        val textAlertTime = alertView.findViewById<TextView>(R.id.text_alert_time)
        val newAlertBadge = alertView.findViewById<View>(R.id.new_alert_badge)
        val textAlertContent = alertView.findViewById<TextView>(R.id.text_alert_content)
        val photoIndicator = alertView.findViewById<LinearLayout>(R.id.photo_indicator)
        val btnViewOnMap = alertView.findViewById<Button>(R.id.btn_view_on_map)
        val btnViewDetails = alertView.findViewById<Button>(R.id.btn_view_details)

        // Set content
        textAlertDistance.text = formatDistance(alert.distanceFromUser)
        textAlertTime.text = getTimeAgo(alert.report.timestamp)
        textAlertContent.text = alert.report.text

        // Show photo indicator if report has photo
        photoIndicator.visibility = if (alert.report.hasPhoto) View.VISIBLE else View.GONE

        // Always show new badge since we're only showing unviewed alerts
        newAlertBadge.visibility = View.VISIBLE

        // Button actions
        btnViewOnMap.setOnClickListener {
            currentAlertsDialog?.dismiss()
            showReportOnMap(alert.report)
            markAlertAsViewed(alert.report.id)
        }

        btnViewDetails.setOnClickListener {
            showReportViewDialog(alert.report)
            markAlertAsViewed(alert.report.id)
        }

        return alertView
    }

    private fun formatDistance(distanceInMeters: Double): String {
        val distanceInFeet = distanceInMeters * 3.28084
        val distanceInMiles = distanceInMeters / 1609.0

        return when {
            distanceInMeters < 1609 -> "${distanceInFeet.roundToInt()} ft away"
            else -> "${distanceInMiles.format(1)} mi away"
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    private fun showReportOnMap(report: Report) {
        mapView.controller.animateTo(report.location, 18.0, 1000L)

        // Find and highlight the report marker
        val reportMarker = reportMarkers.find { marker ->
            marker.position.latitude == report.location.latitude &&
                    marker.position.longitude == report.location.longitude
        }

        reportMarker?.let { marker ->
            // Show info bubble for the marker
            marker.showInfoWindow()

            // Optional: Add a pulse animation to draw attention
            ObjectAnimator.ofFloat(marker, "alpha", 1f, 0.5f, 1f).apply {
                duration = 1000
                repeatCount = 2
                start()
            }
        }
    }

    private fun markAlertAsViewed(reportId: String) {
        viewedAlertIds.add(reportId)
        saveViewedAlerts()

        // Update alerts button color
        val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
        updateAlertsButtonColor(hasUnviewed)

        // Refresh the alerts dialog if it's currently open
        currentAlertsDialog?.let { dialog ->
            if (dialog.isShowing) {
                currentLocation?.let { location ->
                    val dialogView = dialog.findViewById<View>(android.R.id.content)
                    dialogView?.let { view ->
                        val alertsContainer = view.findViewById<LinearLayout>(R.id.alerts_container)
                        val noAlertsContainer = view.findViewById<LinearLayout>(R.id.no_alerts_container)
                        val loadingContainer = view.findViewById<LinearLayout>(R.id.loading_container)

                        if (alertsContainer != null && noAlertsContainer != null && loadingContainer != null) {
                            loadAlertsIntoDialog(alertsContainer, noAlertsContainer, loadingContainer, location)
                        }
                    }
                }
            }
        }
    }

    private fun markAllAlertsAsRead() {
        for (alert in activeAlerts) {
            viewedAlertIds.add(alert.report.id)
        }
        saveViewedAlerts()
        updateAlertsButtonColor(false)
    }

    // Rest of the existing code...
    private fun getSavedLanguage(): String {
        return preferences.getString("app_language", "es") ?: "es" // Default to Spanish on first run
    }

    private fun saveLanguage(languageCode: String) {
        preferences.edit().putString("app_language", languageCode).apply()
    }

    private fun setAppLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)

        val config = Configuration()
        config.setLocale(locale)

        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun toggleLanguage() {
        val currentLang = getSavedLanguage()
        val newLang = if (currentLang == "es") "en" else "es"

        saveLanguage(newLang)
        setAppLanguage(newLang)

        // Mark that this is a language change recreation
        preferences.edit().putBoolean("is_language_change", true).apply()

        // Recreate activity to apply language changes
        recreate()
    }

    private fun setupViews() {
        mapView = findViewById(R.id.mapView)
        fabLocation = findViewById(R.id.fab_location)
        statusCard = findViewById(R.id.status_card)
        statusText = findViewById(R.id.status_text)
        statusClose = findViewById(R.id.status_close)
        instructionOverlay = findViewById(R.id.instruction_overlay)
        btnLanguageToggle = findViewById(R.id.btn_language_toggle)
        btnSettings = findViewById(R.id.btn_settings)
        btnAlerts = findViewById(R.id.btn_alerts)
        searchBar = findViewById(R.id.search_bar)
        btnSearch = findViewById(R.id.btn_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
    }

    private fun setupBottomNavigation() {
        btnLanguageToggle.setOnClickListener {
            animateButtonPress(btnLanguageToggle)
            toggleLanguage()
        }

        btnSettings.setOnClickListener {
            animateButtonPress(btnSettings)
            // TODO: Open settings activity when ready
        }

        btnAlerts.setOnClickListener {
            animateButtonPress(btnAlerts)
            openAlertsScreen()
        }

        // Initialize alerts button color
        updateAlertsButtonColor(false)
    }

    private fun updateAlertsButtonColor(hasUnviewedAlerts: Boolean = false) {
        val tintColor = if (hasUnviewedAlerts) {
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        } else {
            ContextCompat.getColor(this, R.color.bottom_nav_icon)
        }

        btnAlerts.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun setupSearchBar() {
        // Search button click
        btnSearch.setOnClickListener {
            animateButtonPress(btnSearch)
            val query = searchBar.text?.toString()?.trim()
            if (!query.isNullOrEmpty()) {
                searchAddress(query)
            } else {
                showStatusCard(getString(R.string.please_enter_address), isError = true)
            }
        }

        // Clear search button
        btnClearSearch.setOnClickListener {
            animateButtonPress(btnClearSearch)
            clearSearch()
        }

        // Handle Enter key on search bar
        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    searchAddress(query)
                }
                true
            } else {
                false
            }
        }

        // Show/hide clear button based on text
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
    }

    private fun searchAddress(query: String) {
        showStatusCard(getString(R.string.searching_address), isLoading = true)

        // Hide keyboard
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchBar.windowToken, 0)

        try {
            // Use geocoder to find address
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val location = GeoPoint(address.latitude, address.longitude)

                // Animate to searched location
                mapView.controller.animateTo(location, 16.0, 1000L)

                // Add a temporary marker for the search result
                addSearchResultMarker(location, address)

                // Show success message with found address
                val foundAddress = getFormattedAddress(address)
                showStatusCard(getString(R.string.found_address, foundAddress), isLoading = false)

                // Auto-hide status after 4 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    hideStatusCard()
                }, 4000)

            } else {
                showStatusCard(getString(R.string.address_not_found), isError = true)
            }
        } catch (e: IOException) {
            showStatusCard(getString(R.string.search_error), isError = true)
        } catch (e: Exception) {
            showStatusCard(getString(R.string.search_error), isError = true)
        }
    }

    private fun clearSearch() {
        searchBar.text?.clear()
        btnClearSearch.visibility = View.GONE

        // Remove search result marker if it exists
        currentSearchMarker?.let { marker ->
            mapView.overlays.remove(marker)
            currentSearchMarker = null
            mapView.invalidate()
        }

        hideStatusCard()
    }

    private fun getFormattedAddress(address: Address): String {
        return buildString {
            if (!address.thoroughfare.isNullOrEmpty()) {
                append(address.thoroughfare)
                if (!address.subThoroughfare.isNullOrEmpty()) {
                    append(" ${address.subThoroughfare}")
                }
            } else if (!address.featureName.isNullOrEmpty()) {
                append(address.featureName)
            }

            if (!address.locality.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(address.locality)
            }

            if (!address.adminArea.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(address.adminArea)
            }

            if (!address.countryName.isNullOrEmpty()) {
                if (isNotEmpty()) append(", ")
                append(address.countryName)
            }
        }
    }

    private fun addSearchResultMarker(location: GeoPoint, address: Address) {
        // Remove previous search marker
        currentSearchMarker?.let { marker ->
            mapView.overlays.remove(marker)
        }

        currentSearchMarker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_search_marker)
            title = getString(R.string.search_result)
            snippet = getFormattedAddress(address)

            // Add entrance animation
            alpha = 0f
            ObjectAnimator.ofFloat(this, "alpha", 0f, 1f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }
        }

        mapView.overlays.add(currentSearchMarker)
        mapView.invalidate()
    }

    private fun setupMap() {
        // Professional map configuration
        mapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            setFlingEnabled(true)
        }

        // Set default location (will be overridden by auto-location)
        val defaultPoint = GeoPoint(40.7128, -74.0060) // NYC fallback
        mapView.controller.setZoom(15.0)
        mapView.controller.setCenter(defaultPoint)

        // Add long press listener for reporting
        setupMapLongPressListener()
    }

    private fun autoLocateOnStartup() {
        // Check if this is a recreation due to language change
        val isLanguageChange = preferences.getBoolean("is_language_change", false)
        if (isLanguageChange) {
            // Clear the flag and skip location popup
            preferences.edit().putBoolean("is_language_change", false).apply()
            if (hasLocationPermission()) {
                getCurrentLocationSilently(isStartup = true)
            }
            return
        }

        if (hasLocationPermission()) {
            showStatusCard(getString(R.string.finding_location), isLoading = true)
            getCurrentLocationSilently(isStartup = true)
        } else {
            // If no permission, just keep the default map location
            hideStatusCard()
        }
    }

    private fun getCurrentLocationSilently(isStartup: Boolean = false) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                    val userLocation = GeoPoint(location.latitude, location.longitude)

                    if (isStartup && !hasInitialLocationSet) {
                        // First time - center map on user location with closer zoom AND show marker
                        mapView.controller.setCenter(userLocation)
                        mapView.controller.setZoom(18.0) // Closer zoom for better detail
                        addLocationMarker(userLocation) // Add blue marker immediately on startup
                        hasInitialLocationSet = true
                        hideStatusCard()

                        // Check for alerts once we have location
                        checkForNewAlerts()
                    } else {
                        // Manual location button press - animate and show address
                        mapView.controller.animateTo(userLocation, 18.0, 1000L)
                        addLocationMarker(userLocation)
                        showLocationAddress(location)

                        // Check for alerts when location updates
                        checkForNewAlerts()
                    }
                } else {
                    if (isStartup) {
                        hideStatusCard()
                    } else {
                        showStatusCard(getString(R.string.unable_to_get_location), isError = true)
                    }
                }
            }.addOnFailureListener {
                if (isStartup) {
                    hideStatusCard()
                } else {
                    showStatusCard(getString(R.string.location_error), isError = true)
                }
            }
        } catch (e: SecurityException) {
            if (!isStartup) {
                showStatusCard(getString(R.string.location_permission_needed), isError = true)
            }
        }
    }

    private fun showLocationAddress(location: Location) {
        // Try to get address from coordinates
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val locationText = buildString {
                    // Try to build a nice address string
                    if (!address.thoroughfare.isNullOrEmpty()) {
                        append(address.thoroughfare)
                        if (!address.subThoroughfare.isNullOrEmpty()) {
                            append(" ${address.subThoroughfare}")
                        }
                    } else if (!address.featureName.isNullOrEmpty()) {
                        append(address.featureName)
                    }

                    if (!address.locality.isNullOrEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(address.locality)
                    }

                    if (!address.adminArea.isNullOrEmpty()) {
                        if (isNotEmpty()) append(", ")
                        append(address.adminArea)
                    }

                    // If we couldn't build a nice address, fall back to coordinates
                    if (isEmpty()) {
                        append("${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}")
                    }
                }

                showStatusCard(getString(R.string.you_are_at, locationText), isLoading = false)
            } else {
                // Fallback to coordinates if geocoding fails
                val coords = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                showStatusCard(getString(R.string.you_are_at, coords), isLoading = false)
            }
        } catch (e: IOException) {
            // Geocoding failed, show coordinates
            val coords = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
            showStatusCard(getString(R.string.you_are_at, coords), isLoading = false)
        }

        // Auto-hide status after 5 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            hideStatusCard()
        }, 5000)
    }

    private fun setupMapLongPressListener() {
        val mapEventsReceiver = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                return false
            }

            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { location ->
                    showReportConfirmDialog(location)
                }
                return true
            }
        }

        val mapEventsOverlay = MapEventsOverlay(mapEventsReceiver)
        mapView.overlays.add(0, mapEventsOverlay) // Add as first overlay
    }

    private fun setupLocationButton() {
        fabLocation.setOnClickListener {
            animateButtonPress(fabLocation)
            getCurrentLocation()
        }
    }

    private fun setupStatusCard() {
        statusClose.setOnClickListener {
            hideStatusCard()
        }
    }

    private fun setupInstructionOverlay() {
        // Auto-hide instruction after 10 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            if (instructionOverlay.visibility == View.VISIBLE) {
                instructionOverlay.visibility = View.GONE
            }
        }, 10000)
    }

    private fun showReportConfirmDialog(location: GeoPoint) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_report, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnYesReport = dialogView.findViewById<Button>(R.id.btn_yes_report)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnYesReport.setOnClickListener {
            dialog.dismiss()
            showReportInputDialog(location)
        }

        dialog.show()
    }

    private fun showReportInputDialog(location: GeoPoint) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_input, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val editReportText = dialogView.findViewById<TextInputEditText>(R.id.edit_report_text)
        val btnAddPhoto = dialogView.findViewById<Button>(R.id.btn_add_photo)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btn_submit)

        // Reset photo for new report
        currentPhoto = null

        btnAddPhoto.setOnClickListener {
            showPhotoSelectionDialog()
        }

        btnCancel.setOnClickListener {
            currentPhoto = null
            dialog.dismiss()
        }

        btnSubmit.setOnClickListener {
            val reportText = editReportText.text?.toString()?.trim()
            if (reportText.isNullOrEmpty()) {
                showStatusCard(getString(R.string.please_enter_description), isError = true)
                return@setOnClickListener
            }

            createReport(location, reportText, currentPhoto)
            currentPhoto = null
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createReport(location: GeoPoint, text: String, photo: Bitmap?) {
        val report = Report(
            id = UUID.randomUUID().toString(),
            location = location,
            text = text,
            hasPhoto = photo != null,
            photo = photo,
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (REPORT_DURATION_HOURS * 60 * 60 * 1000)
        )

        activeReports.add(report)
        addReportToMap(report)

        showStatusCard(getString(R.string.report_created), isLoading = false)

        // Auto-hide status after 3 seconds
        Handler(Looper.getMainLooper()).postDelayed({
            hideStatusCard()
        }, 3000)

        // Check for new alerts after creating a report (for other users' areas)
        Handler(Looper.getMainLooper()).postDelayed({
            checkForNewAlerts()
        }, 5000)
    }

    private fun addReportToMap(report: Report) {
        // Create red circle overlay (0.5-mile radius)
        val circle = createReportCircle(report.location)
        mapView.overlays.add(circle)
        reportCircles.add(circle)

        // Create report marker
        val marker = Marker(mapView).apply {
            position = report.location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_report_marker)
            title = getString(R.string.safety_report)

            // Handle marker tap to show report details
            setOnMarkerClickListener { _, _ ->
                showReportViewDialog(report)
                true
            }

            // Add entrance animation
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

    private fun createReportCircle(center: GeoPoint): Polygon {
        val circle = Polygon()

        // Create circle points (0.5-mile radius)
        val points = mutableListOf<GeoPoint>()
        val earthRadius = 6371000.0 // Earth radius in meters
        val radiusInDegrees = REPORT_RADIUS_METERS / earthRadius * (180.0 / Math.PI)

        for (i in 0..360 step 10) {
            val angle = Math.toRadians(i.toDouble())
            val lat = center.latitude + radiusInDegrees * cos(angle)
            val lng = center.longitude + radiusInDegrees * sin(angle) / cos(Math.toRadians(center.latitude))
            points.add(GeoPoint(lat, lng))
        }

        circle.points = points
        circle.fillColor = Color.parseColor("#20FF0000") // Translucent red for reports
        circle.strokeColor = Color.parseColor("#80FF0000") // Semi-transparent red ring for reports
        circle.strokeWidth = 3f

        return circle
    }

    private fun showReportViewDialog(report: Report) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_view_report, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val textReportContent = dialogView.findViewById<TextView>(R.id.text_report_content)
        val imageReportPhoto = dialogView.findViewById<ImageView>(R.id.image_report_photo)
        val textReportTime = dialogView.findViewById<TextView>(R.id.text_report_time)
        val btnClose = dialogView.findViewById<Button>(R.id.btn_close)

        // Set report content
        textReportContent.text = report.text
        textReportTime.text = getString(R.string.reported_time, getTimeAgo(report.timestamp))

        // Show photo if available
        if (report.hasPhoto && report.photo != null) {
            imageReportPhoto.setImageBitmap(report.photo)
            imageReportPhoto.visibility = View.VISIBLE
        } else {
            imageReportPhoto.visibility = View.GONE
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun startReportCleanupTimer() {
        val cleanupRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val expiredReports = activeReports.filter { it.expiresAt < currentTime }

                expiredReports.forEach { report ->
                    removeReportFromMap(report)
                    activeReports.remove(report)

                    // Also remove from alerts
                    activeAlerts.removeAll { it.report.id == report.id }
                }

                // Update alerts button after cleanup
                val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
                updateAlertsButtonColor(hasUnviewed)

                // Schedule next cleanup in 1 hour
                Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
            }
        }

        // Start cleanup timer (check every hour)
        Handler(Looper.getMainLooper()).postDelayed(cleanupRunnable, 60 * 60 * 1000)
    }

    private fun removeReportFromMap(report: Report) {
        // Find and remove marker
        val markerToRemove = reportMarkers.find { marker ->
            marker.position.latitude == report.location.latitude &&
                    marker.position.longitude == report.location.longitude
        }
        markerToRemove?.let { marker ->
            mapView.overlays.remove(marker)
            reportMarkers.remove(marker)
        }

        // Find and remove circle
        if (reportCircles.isNotEmpty()) {
            val circle = reportCircles.removeAt(0) // Remove oldest circle
            mapView.overlays.remove(circle)
        }

        mapView.invalidate()
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> getString(R.string.just_now)
            minutes < 60 -> getString(R.string.minutes_ago, minutes)
            hours < 24 -> getString(R.string.hours_ago, hours)
            else -> getString(R.string.days_ago, days)
        }
    }

    private fun showPhotoSelectionDialog() {
        val options = arrayOf(getString(R.string.take_photo), getString(R.string.choose_from_gallery))
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_photo))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionAndCapture()
                    1 -> photoPickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    private fun requestCameraPermissionAndCapture() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(null)
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
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
        if (!hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        showStatusCard(getString(R.string.finding_location), isLoading = true)
        getCurrentLocationSilently(isStartup = false)
    }

    private fun addLocationMarker(location: GeoPoint) {
        currentLocationMarker?.let { marker ->
            mapView.overlays.remove(marker)
        }

        currentLocationMarker = Marker(mapView).apply {
            position = location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_pin)
            title = getString(R.string.your_location)

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

        val textColor = when {
            isError -> ContextCompat.getColor(this, R.color.error)
            isLoading -> ContextCompat.getColor(this, R.color.text_secondary)
            else -> ContextCompat.getColor(this, R.color.success)
        }
        statusText.setTextColor(textColor)

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
            showStatusCard(getString(R.string.location_permission_needed), isError = true)
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
                    showStatusCard(getString(R.string.location_permission_required), isError = true)
                }
            }
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(null)
                } else {
                    showStatusCard(getString(R.string.camera_permission_needed), isError = true)
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
        currentAlertsDialog?.dismiss()
    }
}