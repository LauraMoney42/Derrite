// File: MainActivity.kt (Fixed)
package com.money.derrite
import com.money.derrite.BackendClient
import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Address
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

// Import our data models and managers
import com.money.derrite.data.Report
import com.money.derrite.data.ReportCategory
import com.money.derrite.data.Alert
import com.money.derrite.managers.*

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView

class MainActivity : AppCompatActivity(),
    LocationManager.LocationListener,
    AlertManager.AlertListener,
    MapManager.MapInteractionListener,
    PhotoManager.PhotoCallback {

    // UI Components
    private lateinit var mapView: MapView
    private lateinit var fabLocation: FloatingActionButton
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusText: TextView
    private lateinit var statusClose: ImageButton
    private lateinit var btnLanguageToggle: Button
    private lateinit var btnSettings: ImageButton
    private lateinit var btnAlerts: ImageButton
    private lateinit var searchBar: TextInputEditText
    private lateinit var btnSearch: ImageButton
    private lateinit var btnClearSearch: ImageButton

    // Managers
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var reportManager: ReportManager
    private lateinit var locationManager: LocationManager
    private lateinit var mapManager: MapManager
    private lateinit var alertManager: AlertManager
    private lateinit var photoManager: PhotoManager
    private lateinit var backendClient: BackendClient
    private lateinit var dialogManager: DialogManager
    private lateinit var translationManager: TranslationManager

    // State
    private var selectedCategory: ReportCategory = ReportCategory.SAFETY
    private var hasInitialLocationSet = false
    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            initializeManagers()
            setupUI()
            initializeApp()
        } catch (e: Exception) {
            android.util.Log.e("Derrite", "Error in onCreate: ${e.message}", e)
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
        dialogManager = DialogManager(this, preferencesManager)
        translationManager = TranslationManager(this)

        // Set listeners
        alertManager.setAlertListener(this)
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
    }

    private fun initializeApp() {
        // Load saved data
        alertManager.loadViewedAlerts()
        reportManager.loadSavedReports()

        Handler(Looper.getMainLooper()).postDelayed({
            try {
                locationManager.startLocationUpdates(this)
                translationManager.initialize()
                autoLocateOnStartup()
                startTimers()
            } catch (e: Exception) {
                android.util.Log.e("Derrite", "Error in delayed initialization: ${e.message}")
            }
        }, 1000)
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
        searchBar = findViewById(R.id.search_bar)
        btnSearch = findViewById(R.id.btn_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
    }

    private fun setupMap() {
        mapManager.setupMap(mapView, packageName)
        mapManager.setupMapLongPressListener(mapView, this)

        // Load existing reports on map
        Handler(Looper.getMainLooper()).postDelayed({
            reportManager.getActiveReports().forEach { report ->
                mapManager.addReportToMap(mapView, report, this)
            }
        }, 2000)
    }

    private fun setupLocationButton() {
        fabLocation.setOnClickListener {
            animateButtonPress(fabLocation)
            getCurrentLocation()
        }
    }

    private fun setupStatusCard() {
        statusClose.setOnClickListener { hideStatusCard() }
    }

    private fun setupBottomNavigation() {
        btnLanguageToggle.setOnClickListener {
            animateButtonPress(btnLanguageToggle)
            toggleLanguage()
        }

        btnSettings.setOnClickListener {
            animateButtonPress(btnSettings)
            dialogManager.showSettingsDialog(backendClient)
        }

        btnAlerts.setOnClickListener {
            animateButtonPress(btnAlerts)
            openAlertsScreen()
        }
    }

    private fun setupSearchBar() {
        btnSearch.setOnClickListener {
            animateButtonPress(btnSearch)
            performSearch()
        }

        btnClearSearch.setOnClickListener {
            animateButtonPress(btnClearSearch)
            clearSearch()
        }

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
    }

    // LocationManager.LocationListener Implementation
    override fun onLocationUpdate(location: Location) {
        val userLocation = GeoPoint(location.latitude, location.longitude)
        mapManager.addLocationMarker(mapView, userLocation)
        alertManager.checkForNewAlerts(location)
        subscribeToAlertsForLocation(location.latitude, location.longitude)
    }

    override fun onLocationError(error: String) {
        android.util.Log.e("Derrite", "Location error: $error")
    }

    // AlertManager.AlertListener Implementation
    override fun onNewAlerts(alerts: List<Alert>) {
        val message = alertManager.getAlertSummaryMessage(
            alerts,
            preferencesManager.getSavedLanguage() == "es"
        )
        showStatusCard(message, isLoading = false)
        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
    }

    override fun onAlertsUpdated(hasUnviewed: Boolean) {
        updateAlertsButtonColor(hasUnviewed)
    }

    // MapManager.MapInteractionListener Implementation
    override fun onLongPress(location: GeoPoint) {
        dialogManager.showReportConfirmDialog(location) { confirmedLocation: GeoPoint ->
            showReportInputDialog(confirmedLocation)
        }
    }

    override fun onReportMarkerClick(report: Report) {
        dialogManager.showReportViewDialog(report, translationManager) { reportId: String ->
            alertManager.markAlertAsViewed(reportId)
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

    // UI Methods
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

    private fun createReport(location: GeoPoint, text: String, photo: Bitmap?, category: ReportCategory) {
        val detectedLanguage = translationManager.detectLanguage(text)
        val report = reportManager.createReport(location, text, detectedLanguage, photo, category)

        mapManager.addReportToMap(mapView, report, this)

        val message = if (preferencesManager.getSavedLanguage() == "es") {
            "Enviando al servidor..."
        } else {
            "Sending to server..."
        }
        showStatusCard(message, isLoading = true)

        backendClient.submitReport(
            latitude = location.latitude,
            longitude = location.longitude,
            content = text,
            language = detectedLanguage,
            hasPhoto = photo != null,
            category = category
        ) { success: Boolean, message: String ->
            runOnUiThread {
                val resultMessage = if (success) {
                    if (preferencesManager.getSavedLanguage() == "es") "Enviado al servidor"
                    else "Sent to server"
                } else {
                    if (preferencesManager.getSavedLanguage() == "es") "Error de conexión"
                    else "Connection error"
                }
                showStatusCard(resultMessage, isError = !success)

                if (success) {
                    subscribeToAlertsForLocation(location.latitude, location.longitude)
                }

                Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
            }
        }
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
                    "Estás en: $locationText" else "You are at: $locationText"
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
        if (isLanguageChange) {
            preferencesManager.setLanguageChange(false)
            if (locationManager.hasLocationPermission()) {
                getCurrentLocationSilently(isStartup = true)
            }
        } else {
            if (locationManager.hasLocationPermission()) {
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "Encontrando tu ubicación..." else "Finding your location..."
                showStatusCard(message, isLoading = true)
                getCurrentLocationSilently(isStartup = true)
            } else {
                hideStatusCard()
            }
        }

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

    private fun startTimers() {
        // Report cleanup timer
        val cleanupRunnable = object : Runnable {
            override fun run() {
                try {
                    val expiredReports = reportManager.cleanupExpiredReports()
                    expiredReports.forEach { report ->
                        mapManager.removeReportFromMap(mapView, report)
                    }
                    alertManager.removeAlertsForExpiredReports(expiredReports)
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(cleanupRunnable, 60 * 60 * 1000)

        // Alert checker timer
        val alertCheckRunnable = object : Runnable {
            override fun run() {
                locationManager.getCurrentLocation()?.let { location ->
                    alertManager.checkForNewAlerts(location)
                }
                Handler(Looper.getMainLooper()).postDelayed(this, 2 * 60 * 1000)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(alertCheckRunnable, 10000)
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
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Se necesita permiso de ubicación" else "Location permission needed"
            showStatusCard(message, isError = true)
        }
    }

    private fun updateAlertsButtonColor(hasUnviewedAlerts: Boolean) {
        val tintColor = if (hasUnviewedAlerts) {
            ContextCompat.getColor(this, android.R.color.holo_red_light)
        } else {
            ContextCompat.getColor(this, R.color.bottom_nav_icon)
        }
        btnAlerts.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
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
            else -> Color.parseColor("#4CAF50")
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
                    getCurrentLocation()
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
            mapView.onDetach()
        } catch (e: Exception) {
            android.util.Log.e("Derrite", "Error in onDestroy: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapView.onPause()
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("Derrite", "Error in onPause: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            mapView.onResume()
        } catch (e: Exception) {
            android.util.Log.e("Derrite", "Error in onResume: ${e.message}")
        }
    }
}