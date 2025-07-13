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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
import kotlin.math.cos
import kotlin.math.sin

data class Report(
    val id: String,
    val location: GeoPoint,
    val text: String,
    val hasPhoto: Boolean,
    val photo: Bitmap?,
    val timestamp: Long,
    val expiresAt: Long
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
    private var hasInitialLocationSet = false

    private val LOCATION_PERMISSION_REQUEST = 1001
    private val CAMERA_PERMISSION_REQUEST = 1002
    private val REPORT_RADIUS_METERS = 804.5 // 0.5 miles (hyper-local coverage)
    private val REPORT_DURATION_HOURS = 8L

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
    }

    private fun getSavedLanguage(): String {
        return preferences.getString("app_language", "es") ?: "es" // Default to Spanish
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
                    } else {
                        // Manual location button press - animate and show address
                        mapView.controller.animateTo(userLocation, 18.0, 1000L)
                        addLocationMarker(userLocation)
                        showLocationAddress(location)
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
                }

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
    }
}