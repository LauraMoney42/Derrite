// File: MainActivity.kt (Fixed - Auto-hide instruction overlay)
package com.money.pinlocal

import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextWatcher
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

// Import our data models and managers
import com.money.pinlocal.data.Report
import com.money.pinlocal.data.ReportCategory
import com.money.pinlocal.data.Alert
import com.money.pinlocal.data.FavoritePlace
import com.money.pinlocal.data.FavoriteAlert
import com.money.pinlocal.managers.*

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity(),
    LocationManager.LocationListener,
    AlertManager.AlertListener,
    MapManager.MapInteractionListener,
    PhotoManager.PhotoCallback,
    FavoriteManager.FavoriteListener {

    // UI Components
    private lateinit var mapView: MapView
    private lateinit var fabLocation: FloatingActionButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusClose: ImageButton
    private lateinit var btnLanguageToggle: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAlerts: ImageButton
    private lateinit var btnFavorites: ImageButton
    private lateinit var searchBar: TextInputEditText
    private lateinit var btnSearch: ImageButton
    private lateinit var btnClearSearch: ImageButton
    private lateinit var instructionOverlay: LinearLayout  // NEW: Instruction overlay reference

    // Managers
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var reportManager: ReportManager
    private lateinit var locationManager: LocationManager
    private lateinit var mapManager: MapManager
    private lateinit var alertManager: AlertManager
    private lateinit var photoManager: PhotoManager
    private lateinit var favoriteManager: FavoriteManager
    private lateinit var backendClient: BackendClient
    private lateinit var dialogManager: DialogManager
    private lateinit var translationManager: TranslationManager
    private lateinit var alarmManager: AlarmManager
    // State
    private var selectedCategory: ReportCategory = ReportCategory.SAFETY
    private var hasInitialLocationSet = false
    private var hasUserInteracted = false  // NEW: Track user interaction
    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            initializeManagers()
            setupUI()
            initializeApp()
        } catch (e: Exception) {
            android.util.Log.e("PinLocal", "Error in onCreate: ${e.message}", e)
            try {
                setContentView(R.layout.activity_main)
                showStatusCard("App initialization error - please restart", isError = true)
            } catch (innerE: Exception) {
                // If even basic setup fails, do nothing to avoid crash
            }
        }
    }

    private fun initializeManagers() {
        preferencesManager = PreferencesManager(this)
        reportManager = ReportManager(preferencesManager)
        locationManager = LocationManager(this)
        mapManager = MapManager(this)
        alertManager = AlertManager(preferencesManager, reportManager, locationManager)
        photoManager = PhotoManager(this, preferencesManager)
        backendClient = BackendClient()
        favoriteManager = FavoriteManager(preferencesManager, reportManager, locationManager, backendClient)
        dialogManager = DialogManager(this, preferencesManager)
        translationManager = TranslationManager(this)
        alarmManager = AlarmManager(this, preferencesManager)
        // Set listeners
        alertManager.setAlertListener(this)
        favoriteManager.setFavoriteListener(this)
    }

    private fun setupUI() {
        preferencesManager.setAppLanguage(preferencesManager.getSavedLanguage())
        setContentView(R.layout.activity_main)

        setupViews()
        setupMap()
        setupLocationButton()
        setupStatusCard()
        setupBottomNavigation()
        setupSearchBar()
        setupInstructionOverlay()  // NEW: Setup instruction overlay
    }

// Update MainActivity.kt - Subscribe to favorites on startup:

    private fun initializeApp() {
        // Load saved data
        alertManager.loadViewedAlerts()
        reportManager.loadSavedReports()
        favoriteManager.loadSavedData()

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                locationManager.startLocationUpdates(this)
                translationManager.initialize()
                autoLocateOnStartup()
                startTimers()

                // Subscribe to all saved favorites for background notifications
                favoriteManager.subscribeToAllFavorites()

                scheduleInstructionOverlayAutoHide()
            } catch (e: Exception) {
                android.util.Log.e("PinLocal", "Error in delayed initialization: ${e.message}")
            }
        }, 1000)
    }

    // Keep the createReport method with favorite alert checking:
    private fun createReport(location: GeoPoint, text: String, photo: Bitmap?, category: ReportCategory) {
        val detectedLanguage = translationManager.detectLanguage(text)
        val report = reportManager.createReport(location, text, detectedLanguage, photo, category)

        mapManager.addReportToMap(mapView, report, this)
        preferencesManager.setUserHasCreatedReports(true)
        backendClient.submitReport(
            latitude = location.latitude,
            longitude = location.longitude,
            content = text,
            language = detectedLanguage,
            hasPhoto = photo != null,
            category = category
        ) { success: Boolean, message: String ->
            runOnUiThread {

                if (success) {
                    subscribeToAlertsForLocation(location.latitude, location.longitude)
                    favoriteManager.checkForFavoriteAlerts()
                }
                Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
            }
        }
    }


    private fun setupViews() {
        mapView = findViewById(R.id.mapView)
        fabLocation = findViewById(R.id.fab_location)
        statusCard = findViewById(R.id.status_card)
        statusText = findViewById(R.id.status_text)
        statusClose = findViewById(R.id.status_close)
        btnLanguageToggle = findViewById(R.id.btn_language_toggle)
        btnSettings = findViewById(R.id.btn_settings)
        btnAlerts = findViewById(R.id.btn_alerts)
        btnFavorites = findViewById(R.id.btn_favorites)
        searchBar = findViewById(R.id.search_bar)
        btnSearch = findViewById(R.id.btn_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        instructionOverlay = findViewById(R.id.instruction_overlay)  // NEW: Get instruction overlay reference
    }

    private fun setupManagers() {
        preferencesManager = PreferencesManager(this)
        reportManager = ReportManager(preferencesManager)
        locationManager = LocationManager(this)
        mapManager = MapManager(this)
        alertManager = AlertManager(preferencesManager, reportManager, locationManager)
        photoManager = PhotoManager(this, preferencesManager)
        backendClient = BackendClient()
        favoriteManager = FavoriteManager(preferencesManager, reportManager, locationManager, backendClient)
        dialogManager = DialogManager(this, preferencesManager)
        translationManager = TranslationManager(this)

        // Set listeners
        alertManager.setAlertListener(this)

        // CRITICAL: Ensure favorite listener is set up with logging
        android.util.Log.d("MainActivity", "Setting up FavoriteListener")
        favoriteManager.setFavoriteListener(this)
        android.util.Log.d("MainActivity", "FavoriteListener set up successfully")
    }


    // Also update the onFavoritesUpdated method with additional debugging:
    override fun onFavoritesUpdated(favorites: List<FavoritePlace>) {
        android.util.Log.d("MainActivity", "=== onFavoritesUpdated CALLED ===")
        android.util.Log.d("MainActivity", "Thread: ${Thread.currentThread().name}")
        android.util.Log.d("MainActivity", "Received ${favorites.size} favorites")

        // Log each favorite received
        favorites.forEachIndexed { index, favorite ->
            android.util.Log.d("MainActivity", "Favorite $index: ${favorite.name} (ID: ${favorite.id})")
        }

        runOnUiThread {
            try {
                // Clear ALL existing favorite markers first
                android.util.Log.d("MainActivity", "Clearing all favorite markers from map")
                mapManager.clearFavoriteMarkers(mapView)

                // Add all current favorites back to map
                android.util.Log.d("MainActivity", "Adding ${favorites.size} favorites back to map")
                favorites.forEach { favorite ->
                    android.util.Log.d("MainActivity", "Adding favorite to map: ${favorite.name}")
                    mapManager.addFavoriteToMap(mapView, favorite, this)
                }

                // Force map refresh
                mapView.post {
                    mapView.invalidate()
                    android.util.Log.d("MainActivity", "Map invalidated and refreshed")
                }

                android.util.Log.d("MainActivity", "=== onFavoritesUpdated COMPLETED ===")

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error updating favorites on map: ${e.message}")
            }
        }
    }

    private fun setupInstructionOverlay() {
        val btnUserGuideLink = findViewById<TextView>(R.id.btn_user_guide_link)

        // Check if user has created reports before
        val hasCreatedReports = reportManager.getActiveReports().isNotEmpty() ||
                preferencesManager.hasUserCreatedReports()

        // Check if this is a language change
        val isLanguageChange = preferencesManager.isLanguageChange()

        if (hasCreatedReports && !isLanguageChange) {
            // Hide immediately if user has used the app before AND it's not a language change
            instructionOverlay.visibility = View.GONE
            hasUserInteracted = true
        } else {
            // Show instruction overlay for new users OR language changes
            instructionOverlay.visibility = View.VISIBLE
            hasUserInteracted = false

            // Make it clickable to dismiss
            instructionOverlay.setOnClickListener {
                hideInstructionOverlay()
            }

            // Set up User Guide link
            btnUserGuideLink.setOnClickListener {
                hideInstructionOverlay()
                dialogManager.showUserGuideDialog()
            }

            // If it was a language change, clear the flag
            if (isLanguageChange) {
                preferencesManager.setLanguageChange(false)
            }
        }
    }
    // NEW: Schedule auto-hide of instruction overlay
    private fun scheduleInstructionOverlayAutoHide() {
        if (!hasUserInteracted && instructionOverlay.visibility == View.VISIBLE) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!hasUserInteracted && instructionOverlay.visibility == View.VISIBLE) {
                    hideInstructionOverlay()
                }
            }, 8000) // Hide after 8 seconds
        }
    }

    // NEW: Hide instruction overlay with animation
    private fun hideInstructionOverlay() {
        if (instructionOverlay.visibility == View.VISIBLE) {
            hasUserInteracted = true
            preferencesManager.setUserHasCreatedReports(true)  // Remember user has interacted

            ObjectAnimator.ofFloat(instructionOverlay, "alpha", 1f, 0f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(instructionOverlay, "scaleX", 1f, 0.8f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }

            ObjectAnimator.ofFloat(instructionOverlay, "scaleY", 1f, 0.8f).apply {
                duration = 500
                interpolator = DecelerateInterpolator()
                start()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                instructionOverlay.visibility = View.GONE
            }, 500)
        }
    }

    private fun setupMap() {
        mapManager.setupMap(mapView, packageName)
        mapManager.setupMapLongPressListener(mapView, this)

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                // ADD SAFETY CHECK
                if (::mapView.isInitialized && mapView.repository != null) {
                    reportManager.getActiveReports().forEach { report ->
                        mapManager.addReportToMap(mapView, report, this)
                    }

                    favoriteManager.getFavorites().forEach { favorite ->
                        mapManager.addFavoriteToMap(mapView, favorite, this)
                    }
                } else {
                    android.util.Log.w("MainActivity", "MapView not ready, skipping initial load")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading existing items on map: ${e.message}")
            }
        }, 3000) // Increase delay from 2000 to 3000
    }

    private fun setupBottomNavigation() {
        btnLanguageToggle.setOnClickListener {
            animateButtonPress(btnLanguageToggle)
            toggleLanguage()
            hideInstructionOverlay()  // NEW: Hide on any interaction
        }

        btnSettings.setOnClickListener {
            animateButtonPress(btnSettings)
            dialogManager.showSettingsDialog(backendClient)
            hideInstructionOverlay()  // NEW: Hide on any interaction
        }

        btnAlerts.setOnClickListener {
            animateButtonPress(btnAlerts)
            openAlertsScreen()
            hideInstructionOverlay()  // NEW: Hide on any interaction
        }

        btnFavorites.setOnClickListener {
            animateButtonPress(btnFavorites)
            openFavoritesScreen()
            hideInstructionOverlay()  // NEW: Hide on any interaction
        }
    }

    // LocationManager.LocationListener Implementation
    override fun onLocationUpdate(location: Location) {
        val userLocation = GeoPoint(location.latitude, location.longitude)
        mapManager.addLocationMarker(mapView, userLocation)
        alertManager.checkForNewAlerts(location)
        favoriteManager.checkForFavoriteAlerts()
        subscribeToAlertsForLocation(location.latitude, location.longitude)
    }

    override fun onLocationError(error: String) {
        android.util.Log.e("PinLocal", "Location error: $error")
    }
    override fun onNewAlerts(alerts: List<Alert>) {
        val message = alertManager.getAlertSummaryMessage(
            alerts,
            preferencesManager.getSavedLanguage() == "es"
        )
        showStatusCard(message, isLoading = false)

        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        val title = if (isSpanish) "Nueva Alerta" else "New Alert"

        // Check if any alert is a safety alert
        val hasSafetyAlert = alerts.any { it.report.category == ReportCategory.SAFETY }
        val category = if (hasSafetyAlert) "SAFETY" else alerts.first().report.category.code.uppercase()

        if (hasSafetyAlert) {
            // Safety alerts check the alarm override setting
            alarmManager.triggerAlert(title, message, category)
            android.util.Log.d("MainActivity", "Safety alert - checking alarm override setting")
        } else {
            // Fun/Lost alerts ALWAYS use silent notification (ignore override setting)
            alarmManager.triggerSilentNotification(title, message, category)
            android.util.Log.d("MainActivity", "Non-safety alert - using silent notification only")
        }

        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
    }
    override fun onAlertsUpdated(hasUnviewed: Boolean) {
        updateAlertsButtonColor(hasUnviewed)
    }

    override fun onFavoriteAlertsUpdated(alerts: List<FavoriteAlert>, hasUnviewed: Boolean) {
        updateFavoritesButtonColor(hasUnviewed)
    }

    override fun onNewFavoriteAlerts(alerts: List<FavoriteAlert>) {
        val message = favoriteManager.getFavoriteAlertSummaryMessage(
            alerts,
            preferencesManager.getSavedLanguage() == "es"
        )
        showStatusCard(message, isLoading = false)

        // Check for Safety alerts in favorites and trigger alarm
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        val title = if (isSpanish) "Alerta en Favorito" else "Alert at Favorite"

        // Check if any favorite alert is a safety alert
        val hasSafetyAlert = alerts.any { it.report.category == ReportCategory.SAFETY }
        val category = if (hasSafetyAlert) "SAFETY" else alerts.first().report.category.code.uppercase()

        if (hasSafetyAlert) {
            // Safety alerts at favorites check the alarm override setting
            alarmManager.triggerAlert(title, message, category)
            android.util.Log.d("MainActivity", "Safety alert at favorite - checking alarm override setting")
        } else {
            // Fun/Lost alerts at favorites ALWAYS use silent notification
            alarmManager.triggerSilentNotification(title, message, category)
            android.util.Log.d("MainActivity", "Non-safety alert at favorite - using silent notification only")
        }

        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
    }
    // MapManager.MapInteractionListener Implementation
    override fun onLongPress(location: GeoPoint) {
        hideInstructionOverlay()  // NEW: Hide on map interaction
        showMapLongPressOptions(location)
    }

    override fun onReportMarkerClick(report: Report) {
        dialogManager.showReportViewDialog(report, translationManager) { reportId: String ->
            alertManager.markAlertAsViewed(reportId)
        }
    }

    fun onFavoriteMarkerClick(favorite: FavoritePlace) {
        dialogManager.showFavoriteOptionsDialog(favorite, favoriteManager) { favoriteId: String ->
            // Handle favorite actions
        }
    }

    // PhotoManager.PhotoCallback Implementation
    override fun onPhotoSelected(bitmap: Bitmap) {
        val message = if (preferencesManager.getSavedLanguage() == "es")
            "Foto agregada anónimamente" else "Photo added anonymously"
        showStatusCard(message, isLoading = false)
    }

    override fun onPhotoError(message: String) {
        showStatusCard(message, isError = true)
    }

    private fun showMapLongPressOptions(location: GeoPoint) {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        val options = if (isSpanish) {
            arrayOf("Crear Reporte", "Agregar a Favoritos", "Cancelar")
        } else {
            arrayOf("Create Report", "Add to Favorites", "Cancel")
        }

        AlertDialog.Builder(this)
            .setTitle(if (isSpanish) "¿Qué deseas hacer?" else "What would you like to do?")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showReportInputDialog(location)
                    1 -> dialogManager.showAddFavoriteDialog(location, favoriteManager)
                    2 -> { /* Cancel - do nothing */ }
                }
            }
            .show()
    }

    private fun showReportInputDialog(location: GeoPoint) {
        dialogManager.showReportInputDialog(
            location = location,
            selectedCategory = selectedCategory,
            onCategoryChange = { category: ReportCategory ->
                selectedCategory = category
            },
            onPhotoRequest = {
                photoManager.showPhotoSelectionDialog(this)
            },
            onSubmit = { reportLocation: GeoPoint, text: String, photo: Bitmap?, category: ReportCategory ->
                createReport(reportLocation, text, photo, category)
            }
        )
    }

    private fun updateAlertsButtonColor(hasUnviewedAlerts: Boolean) {
        val tintColor = if (hasUnviewedAlerts) {
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        } else {
            ContextCompat.getColor(this, R.color.bottom_nav_icon)
        }
        btnAlerts.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun updateFavoritesButtonColor(hasUnviewedAlerts: Boolean) {
        // Always use the default bottom nav icon color (no red)
        val tintColor = ContextCompat.getColor(this, R.color.bottom_nav_icon)
        btnFavorites.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun startTimers() {
        val cleanupRunnable = object : Runnable {
            override fun run() {
                try {
                    val expiredReports = reportManager.cleanupExpiredReports()
                    expiredReports.forEach { report ->
                        mapManager.removeReportFromMap(mapView, report)
                    }
                    alertManager.removeAlertsForExpiredReports(expiredReports)
                    favoriteManager.removeAlertsForExpiredReports(expiredReports)
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(cleanupRunnable, 60 * 60 * 1000)

        val alertCheckRunnable = object : Runnable {
            override fun run() {
                locationManager.getCurrentLocation()?.let { location ->
                    alertManager.checkForNewAlerts(location)
                    favoriteManager.checkForFavoriteAlerts()
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 2 * 60 * 1000)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(alertCheckRunnable, 10000)
    }

    private fun setupLocationButton() {
        fabLocation.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor("#8E8E93") // iOS gray color
        )

        fabLocation.imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)

        fabLocation.setOnClickListener {
            animateButtonPress(fabLocation)
            getCurrentLocation()
            hideInstructionOverlay()
        }
    }

    private fun setupStatusCard() {
        statusClose.setOnClickListener { hideStatusCard() }
    }

    private fun setupSearchBar() {
        btnSearch.setOnClickListener {
            animateButtonPress(btnSearch)
            performSearch()
            hideInstructionOverlay()  // NEW: Hide on search
        }

        btnClearSearch.setOnClickListener {
            animateButtonPress(btnClearSearch)
            clearSearch()
        }

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideInstructionOverlay()  // NEW: Hide on search
                true
            } else false
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (!s.isNullOrEmpty()) {
                    hideInstructionOverlay()  // NEW: Hide when user starts typing
                }
            }
        })
    }

    private fun performSearch() {
        val query = searchBar.text?.toString()?.trim()
        if (query.isNullOrEmpty()) {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Por favor ingresa una dirección" else "Please enter an address"
            showStatusCard(message, isError = true)
            return
        }

        val message = if (preferencesManager.getSavedLanguage() == "es")
            "Buscando dirección..." else "Searching for address..."
        showStatusCard(message, isLoading = true)

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchBar.windowToken, 0)

        val addresses = locationManager.searchAddress(query)
        if (!addresses.isNullOrEmpty()) {
            val address = addresses[0]
            val location = GeoPoint(address.latitude, address.longitude)

            mapView.controller.animateTo(location, 16.0, 1000L)
            mapManager.addSearchResultMarker(mapView, location, address)

            val foundAddress = locationManager.getFormattedAddress(address)
            val foundMessage = if (preferencesManager.getSavedLanguage() == "es")
                "Encontrado: $foundAddress" else "Found: $foundAddress"
            showStatusCard(foundMessage, isLoading = false)
            Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 4000)
        } else {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Dirección no encontrada" else "Address not found"
            showStatusCard(message, isError = true)
        }
    }

    private fun clearSearch() {
        searchBar.text?.clear()
        btnClearSearch.visibility = View.GONE
        mapManager.clearSearchMarker(mapView)
        hideStatusCard()
    }

    private fun getCurrentLocation() {
        if (!locationManager.hasLocationPermission()) {
            requestLocationPermission()
            return
        }

        val message = if (preferencesManager.getSavedLanguage() == "es")
            "Encontrando tu ubicación..." else "Finding your location..."
        showStatusCard(message, isLoading = true)

        locationManager.getLastLocation { location: Location? ->
            if (location != null) {
                val userLocation = GeoPoint(location.latitude, location.longitude)
                mapView.controller.animateTo(userLocation, 18.0, 1000L)
                mapManager.addLocationMarker(mapView, userLocation)

                val locationText = locationManager.getLocationDescription(location)
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "$locationText" else "$locationText"
                showStatusCard(message, isLoading = false)
                Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 5000)
            } else {
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "No se puede obtener ubicación" else "Unable to get location"
                showStatusCard(message, isError = true)
            }
        }
    }

    private fun openAlertsScreen() {
        val location = locationManager.getCurrentLocation()
        if (location != null) {
            dialogManager.showAlertsDialog(location, alertManager) { reportId: String ->
                alertManager.markAlertAsViewed(reportId)
            }
        } else {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Se necesita ubicación para alertas" else "Location needed for alerts"
            showStatusCard(message, isError = true)
        }
    }

    private fun autoLocateOnStartup() {
        val isLanguageChange = preferencesManager.isLanguageChange()

        if (locationManager.hasLocationPermission()) {
            // Permission already granted - get location immediately
            if (!isLanguageChange) {
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "Encontrando tu ubicación..." else "Finding your location..."
                showStatusCard(message, isLoading = true)
            }
            getCurrentLocationSilently(isStartup = true)
        } else {
            // No permission - auto-request it
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Solicitando permiso de ubicación..." else "Requesting location permission..."
            showStatusCard(message, isLoading = true)
            requestLocationPermission()
        }

        // Clear language change flag if it was set
        if (isLanguageChange) {
            preferencesManager.setLanguageChange(false)
        }

        // Schedule alert checking after location is obtained
        Handler(Looper.getMainLooper()).postDelayed({
            locationManager.getCurrentLocation()?.let { location ->
                alertManager.checkForNewAlerts(location)
            }
        }, 2000)
    }

    private fun getCurrentLocationSilently(isStartup: Boolean = false) {
        locationManager.getLastLocation { location: Location? ->
            if (location != null) {
                val userLocation = GeoPoint(location.latitude, location.longitude)

                if (isStartup && !hasInitialLocationSet) {
                    mapView.controller.setCenter(userLocation)
                    mapView.controller.setZoom(18.0)
                    mapManager.addLocationMarker(mapView, userLocation)
                    hasInitialLocationSet = true
                    hideStatusCard()
                    alertManager.checkForNewAlerts(location)
                    subscribeToAlertsForLocation(location.latitude, location.longitude)
                } else {
                    mapView.controller.animateTo(userLocation, 18.0, 1000L)
                    mapManager.addLocationMarker(mapView, userLocation)
                    if (!isStartup) {
                        val locationText = locationManager.getLocationDescription(location)
                        val message = if (preferencesManager.getSavedLanguage() == "es")
                            "Estás en: $locationText" else "You are at: $locationText"
                        showStatusCard(message, isLoading = false)
                        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 5000)
                    }
                    alertManager.checkForNewAlerts(location)
                }
            } else {
                if (isStartup) {
                    hideStatusCard()
                } else {
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "No se puede obtener ubicación" else "Unable to get location"
                    showStatusCard(message, isError = true)
                }
            }
        }
    }

    private fun subscribeToAlertsForLocation(latitude: Double, longitude: Double) {
        backendClient.subscribeToAlerts(latitude, longitude) { success: Boolean, message: String ->
            // Silent subscription
        }
    }

    private fun toggleLanguage() {
        val currentLang = preferencesManager.getSavedLanguage()
        val newLang = if (currentLang == "es") "en" else "es"

        preferencesManager.saveLanguage(newLang)
        preferencesManager.setAppLanguage(newLang)
        preferencesManager.setLanguageChange(true)
        recreate()
    }

    private fun requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), // Only coarse location
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Se necesita permiso de ubicación" else "Location permission needed"
            showStatusCard(message, isError = true)
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
        Handler(Looper.getMainLooper()).postDelayed({ scaleUp.start() }, 100)
    }

    private fun showStatusCard(message: String, isLoading: Boolean = false, isError: Boolean = false) {
        statusText.text = message

        val textColor = when {
            isError -> Color.RED
            isLoading -> Color.parseColor("#888888")
            else -> Color.parseColor("#3C3C43")
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            LOCATION_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission granted - immediately get location
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Encontrando tu ubicación..." else "Finding your location..."
                    showStatusCard(message, isLoading = true)
                    getCurrentLocationSilently(isStartup = true)
                } else {
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Se requiere permiso de ubicación" else "Location permission required"
                    showStatusCard(message, isError = true)
                }
            }
        }
        photoManager.handlePermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            locationManager.stopLocationUpdates()
            translationManager.cleanup()
            alarmManager.cleanup()
            mapView.onDetach()
        } catch (e: Exception) {
            android.util.Log.e("PinLocal", "Error in onDestroy: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapView.onPause()
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("PinLocal", "Error in onPause: ${e.message}")
        }
    }


    override fun onResume() {
        super.onResume()
        try {
            mapView.onResume()

            // CRITICAL: Add delay to refresh favorites when returning to MainActivity
            // This ensures any deletion operations are fully complete before refreshing
            android.util.Log.d("MainActivity", "onResume - scheduling favorites refresh")
            Handler(Looper.getMainLooper()).postDelayed({
                android.util.Log.d("MainActivity", "onResume - refreshing favorites on map")
                refreshFavoritesOnMap()
            }, 200) // Small delay to ensure deletion is complete

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onResume: ${e.message}")
        }
    }

    private fun refreshFavoritesOnMap() {
        try {
            android.util.Log.d("MainActivity", "=== REFRESHING FAVORITES ON MAP ===")

            // CRITICAL FIX: Reload favorites from preferences first
            favoriteManager.loadSavedData()

            // Get current favorites from manager
            val currentFavorites = favoriteManager.getFavorites()
            android.util.Log.d("MainActivity", "Current favorites count: ${currentFavorites.size}")

            // Clear all existing favorite markers
            mapManager.clearFavoriteMarkers(mapView)

            // Add all current favorites back to map
            currentFavorites.forEach { favorite ->
                android.util.Log.d("MainActivity", "Adding favorite to map: ${favorite.name}")
                mapManager.addFavoriteToMap(mapView, favorite, this)
            }

            // Force map refresh
            mapView.invalidate()

            android.util.Log.d("MainActivity", "=== REFRESH FAVORITES COMPLETED ===")

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error refreshing favorites on map: ${e.message}")
        }
    }
    companion object {
        private const val FAVORITES_ACTIVITY_REQUEST = 1001
    }

    // Update the openFavoritesScreen method in MainActivity.kt:
    private fun openFavoritesScreen() {
        val intent = Intent(this, FavoritesActivity::class.java)
        startActivityForResult(intent, FAVORITES_ACTIVITY_REQUEST)
    }

    // Add this method to MainActivity.kt:
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            FAVORITES_ACTIVITY_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    android.util.Log.d("MainActivity", "Returned from FavoritesActivity - favorites may have changed")

                    // Force refresh favorites on map
                    refreshFavoritesOnMap()

                    // Also re-register the listener in case it was cleared
                    favoriteManager.setFavoriteListener(this)
                }
            }
        }
    }
}