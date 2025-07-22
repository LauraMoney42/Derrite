// File: MainActivity.kt (Complete Updated Version with Enhanced Location Initialization)
package com.money.derrite

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
import com.money.derrite.data.Report
import com.money.derrite.data.ReportCategory
import com.money.derrite.data.Alert
import com.money.derrite.data.FavoritePlace
import com.money.derrite.data.FavoriteAlert
import com.money.derrite.managers.*

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
    private lateinit var instructionOverlay: LinearLayout

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
//    private var selectedCategory: ReportCategory = ReportCategory.SAFETY
    private var hasInitialLocationSet = false
    private var hasUserInteracted = false
    private val LOCATION_PERMISSION_REQUEST = 1001

    // Sync state
    private var isAppInForeground = true
    private var syncHandler: Handler? = null
    private var syncRunnable: Runnable? = null

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

        // Load the last report timestamp for time-based alert blocking
        reportManager.loadLastReportTimestamp()

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

        android.util.Log.d("MainActivity", "All managers initialized with privacy-safe alert blocking")
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
        setupInstructionOverlay()
    }

    private fun initializeApp() {
        android.util.Log.d("MainActivity", "Starting app initialization...")

        // Load saved data first
        alertManager.loadViewedAlerts()
        reportManager.loadSavedReports()
        favoriteManager.loadSavedData()

        // Start location updates immediately
        locationManager.startLocationUpdates(this)
        translationManager.initialize()

        // Simple delayed auto-locate (like original)
        Handler(Looper.getMainLooper()).postDelayed({
            autoLocateOnStartup()
        }, 1500) // Simple 1.5 second delay

        // Start other services with delays
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                startTimers()
                startPeriodicReportSync()
                favoriteManager.subscribeToAllFavorites()
                scheduleInstructionOverlayAutoHide()
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error in delayed initialization: ${e.message}")
            }
        }, 2000)

        // Fetch reports much later to avoid conflicts
        Handler(Looper.getMainLooper()).postDelayed({
            fetchAllReportsInitially()
        }, 5000) // Reduced from 8 seconds
    }

    private fun autoLocateOnStartup() {
        val isLanguageChange = preferencesManager.isLanguageChange()
        android.util.Log.d("MainActivity", "Auto-locating on startup")

        if (locationManager.hasLocationPermission()) {
            if (!isLanguageChange) {
                val message = if (preferencesManager.getSavedLanguage() == "es")
                    "Encontrando tu ubicación..." else "Finding your location..."
                showStatusCard(message, isLoading = true)
            }
            getCurrentLocationSilently(isStartup = true)
        } else {
            val message = if (preferencesManager.getSavedLanguage() == "es")
                "Solicitando permiso de ubicación..." else "Requesting location permission..."
            showStatusCard(message, isLoading = true)
            requestLocationPermission()
        }

        if (isLanguageChange) {
            preferencesManager.setLanguageChange(false)
        }

        // Check for alerts after location should be set
        Handler(Looper.getMainLooper()).postDelayed({
            locationManager.getCurrentLocation()?.let { location ->
                alertManager.checkForNewAlerts(location)
            }
        }, 3000)
    }

    private fun fetchAllReportsInitially() {
        val isSpanish = preferencesManager.getSavedLanguage() == "es"
//        val fetchMessage = if (isSpanish) "Cargando todos los pins..." else "Loading all pins..."
//        showStatusCard(fetchMessage, isLoading = true)

        android.util.Log.d("MainActivity", "INITIAL FETCH - Starting...")

        // Fetch ALL reports globally
        backendClient.fetchAllReports { success: Boolean, reports: List<Report>, message: String ->
            runOnUiThread {
                android.util.Log.d("MainActivity", "INITIAL FETCH - Result: success=$success, reports=${reports.size}")

                if (success && reports.isNotEmpty()) {
                    android.util.Log.d("MainActivity", "INITIAL FETCH - Processing ${reports.size} reports")

                    // IMPROVED: Use the new refresh method to prevent duplicates
                    val validReports = mutableListOf<Report>()

                    reports.forEach { report ->
                        try {
                            android.util.Log.d("MainActivity", "Adding initial report: ${report.id} - ${report.category.name}")
                            reportManager.addReport(report)
                            validReports.add(report)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error processing initial report ${report.id}: ${e.message}")
                        }
                    }

                    // Use the new refresh method to add all reports safely
                    if (::mapView.isInitialized && mapView.repository != null) {
                        mapManager.refreshReportsOnMap(mapView, validReports, this)
                        android.util.Log.d("MainActivity", "Refreshed map with ${validReports.size} pins")
                    } else {
                        android.util.Log.e("MainActivity", "MapView not ready for initial pins")
                    }

                    val successMessage = if (isSpanish)
                        "Cargados ${validReports.size} pins globalmente"
                    else
                        "Loaded ${validReports.size} pins globally"
                    showStatusCard(successMessage, isLoading = false)

                    // Check for alerts after initial reports are loaded
                    val currentLocation = locationManager.getCurrentLocation()
                    if (currentLocation != null) {
                        android.util.Log.d("MainActivity", "Checking for alerts after initial report load")
                        alertManager.checkForNewAlerts(currentLocation)
                        favoriteManager.checkForFavoriteAlerts()
                    }

                    Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)

                } else if (success && reports.isEmpty()) {
                    android.util.Log.d("MainActivity", "INITIAL FETCH - No reports found")
                    val noReportsMessage = if (isSpanish)
                        "No hay reportes activos"
                    else
                        "No active reports"
                    showStatusCard(noReportsMessage, isLoading = false)
                    Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 2000)

                } else {
                    android.util.Log.e("MainActivity", "INITIAL FETCH - Failed: $message")
                    val errorMessage = if (isSpanish)
                        "Error cargando reportes"
                    else
                        "Error loading reports"
                    showStatusCard(errorMessage, isError = true)
                    Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 2000)
                }
            }
        }
    }

    private fun silentFetchReports(onComplete: (Boolean) -> Unit = {}) {
        // Check if we should throttle silent fetches
        val now = System.currentTimeMillis()
        val timeSinceLastSilentFetch = now - (preferencesManager.preferences.getLong("last_silent_fetch", 0))

        if (timeSinceLastSilentFetch < 30000) { // Don't fetch more than once every 30 seconds
            android.util.Log.d("MainActivity", "Throttling silent fetch - too recent")
            onComplete(true)
            return
        }

        // Record this fetch attempt
        preferencesManager.preferences.edit().putLong("last_silent_fetch", now).apply()

        backendClient.fetchAllReports { success: Boolean, reports: List<Report>, message: String ->
            runOnUiThread {
                try {
                    if (success && reports.isNotEmpty()) {
                        android.util.Log.d("MainActivity", "SILENT FETCH - Processing ${reports.size} server reports")

                        var newReportsCount = 0
                        var skippedReportsCount = 0
                        val newReports = mutableListOf<Report>()

                        // Get existing report IDs for comparison
                        val existingReportIds = reportManager.getActiveReports().map { it.id }.toSet()

                        reports.forEach { serverReport ->
                            android.util.Log.d("MainActivity", "Processing server report: ${serverReport.id}")

                            if (!existingReportIds.contains(serverReport.id)) {
                                // IMPROVED: Check if already on map as well
                                if (!mapManager.isReportOnMap(serverReport.id)) {
                                    android.util.Log.d("MainActivity", "NEW report found: ${serverReport.id}")
                                    reportManager.addReport(serverReport)
                                    newReports.add(serverReport)
                                    newReportsCount++
                                } else {
                                    android.util.Log.d("MainActivity", "SKIPPED - Already on map: ${serverReport.id}")
                                    skippedReportsCount++
                                }
                            } else {
                                android.util.Log.d("MainActivity", "SKIPPED - Already exists locally: ${serverReport.id}")
                                skippedReportsCount++
                            }
                        }

                        android.util.Log.d("MainActivity", "Final counts: new=$newReportsCount, skipped=$skippedReportsCount")

                        // IMPROVED: Only add new reports to map, not all reports
                        if (newReportsCount > 0 && ::mapView.isInitialized && mapView.repository != null) {
                            try {
                                // Add only the new reports to the map
                                newReports.forEach { report ->
                                    mapManager.addReportToMap(mapView, report, this@MainActivity)
                                }

                                mapView.post {
                                    mapView.invalidate()
                                    android.util.Log.d("MainActivity", "Map invalidated after adding $newReportsCount reports")
                                }

                                // Check for alerts only when NEW reports are added
                                val currentLocation = locationManager.getCurrentLocation()
                                if (currentLocation != null) {
                                    android.util.Log.d("MainActivity", "Checking for alerts due to $newReportsCount new server reports")
                                    alertManager.checkForNewAlerts(currentLocation)
                                    favoriteManager.checkForFavoriteAlerts()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("MainActivity", "Error adding new reports to map: ${e.message}")
                            }
                        }

                        onComplete(true)
                    } else if (!success && message.contains("Rate limit", ignoreCase = true)) {
                        android.util.Log.w("MainActivity", "Silent fetch rate limited: $message")
                        onComplete(false) // Don't retry immediately
                    } else {
                        android.util.Log.w("MainActivity", "Silent fetch: no reports or failed: $message")
                        onComplete(success)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error processing silent fetch: ${e.message}")
                    onComplete(false)
                }
            }
        }
    }

    // IMPROVED: Clean up expired reports with proper map cleanup
    private fun cleanupExpiredReports() {
        try {
            android.util.Log.d("MainActivity", "Starting cleanup of expired reports")

            val expiredReports = reportManager.cleanupExpiredReports()

            if (expiredReports.isNotEmpty()) {
                android.util.Log.d("MainActivity", "Found ${expiredReports.size} expired reports to remove")

                // Remove from map
                if (::mapView.isInitialized && mapView.repository != null) {
                    expiredReports.forEach { expiredReport ->
                        mapManager.removeReportFromMap(mapView, expiredReport)
                    }
                    mapView.invalidate()
                }

                // Clean up alerts
                alertManager.removeAlertsForExpiredReports(expiredReports)
                favoriteManager.removeAlertsForExpiredReports(expiredReports)

                android.util.Log.d("MainActivity", "Cleanup completed for ${expiredReports.size} expired reports")
            } else {
                android.util.Log.d("MainActivity", "No expired reports to clean up")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during cleanup: ${e.message}")
        }
    }

    // NEW: Method to force refresh all reports on map (useful for debugging)
    private fun forceRefreshMap() {
        try {
            android.util.Log.d("MainActivity", "Force refreshing entire map")

            if (::mapView.isInitialized && mapView.repository != null) {
                val allReports = reportManager.getActiveReports()
                mapManager.refreshReportsOnMap(mapView, allReports, this)

                android.util.Log.d("MainActivity", "Force refresh completed with ${allReports.size} reports")
                android.util.Log.d("MainActivity", "Reports on map: ${mapManager.getReportCountOnMap()}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error during force refresh: ${e.message}")
        }
    }

    private fun createReport(location: GeoPoint, text: String, photo: Bitmap?, category: ReportCategory) {
        android.util.Log.d("MainActivity", "CREATING REPORT - Starting alert cooldown period")

        val detectedLanguage = translationManager.detectLanguage(text)
        val report = reportManager.createReport(location, text, detectedLanguage, photo, category)

        android.util.Log.d("MainActivity", "Report created with ID: ${report.id}")
        android.util.Log.d("MainActivity", "Alert checking will be blocked for 60 seconds")

        preferencesManager.setUserHasCreatedReports(true)

        // Show immediate feedback that report is being processed
        val isSpanish = preferencesManager.getSavedLanguage() == "es"

        // Convert photo to base64 if present
        val photoBase64 = if (photo != null) {
            try {
                val stream = java.io.ByteArrayOutputStream()
                val success = photo.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)

                if (success) {
                    val byteArray = stream.toByteArray()
                    stream.close()
                    val base64String = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
                    "data:image/jpeg;base64,$base64String"
                } else {
                    null
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error converting photo to base64: ${e.message}")
                null
            }
        } else {
            null
        }

        backendClient.submitReport(
            latitude = location.latitude,
            longitude = location.longitude,
            content = text,
            language = detectedLanguage,
            hasPhoto = photo != null,
            photo = photoBase64,
            category = category
        ) { success: Boolean, message: String ->
            runOnUiThread {
                if (success) {
                    android.util.Log.d("MainActivity", "Report successfully submitted to server")
                    subscribeToAlertsForLocation(location.latitude, location.longitude)
//
//                    val successMessage = if (isSpanish) {
//                        if (photo != null) "Reporte enviado con foto" else "Reporte enviado"
//                    } else {
//                        if (photo != null) "Report sent with photo" else "Report sent"
//                    }
//                    showStatusCard(successMessage, isLoading = false)
                    Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
                } else {
                    android.util.Log.e("MainActivity", "Failed to submit report: $message")

                    // Enhanced error messages for different failure types
                    val errorMessage = when {
                        message.contains("Rate limit", ignoreCase = true) ||
                                message.contains("429", ignoreCase = true) -> {
                            if (isSpanish) {
                                "Demasiadas peticiones. Tu pin se enviará en unos momentos..."
                            } else {
                                "Too many requests. Your pin will be sent shortly..."
                            }
                        }
                        message.contains("Network error", ignoreCase = true) -> {
                            if (isSpanish) {
                                "Error de conexión. Reintentando..."
                            } else {
                                "Connection error. Retrying..."
                            }
                        }
                        message.contains("Server error", ignoreCase = true) -> {
                            if (isSpanish) {
                                "Error del servidor. Reintentando..."
                            } else {
                                "Server error. Retrying..."
                            }
                        }
                        else -> {
                            if (isSpanish) {
                                "Error enviando pin: $message"
                            } else {
                                "Error sending pin: $message"
                            }
                        }
                    }

                    // For rate limits and network errors, show as warning (yellow) not error (red)
                    val isWarning = message.contains("Rate limit", ignoreCase = true) ||
                            message.contains("Network error", ignoreCase = true) ||
                            message.contains("Server error", ignoreCase = true)

                    if (isWarning) {
                        showStatusCard(errorMessage, isLoading = false, isError = false)
                        // Hide warning after longer delay
                        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 5000)
                    } else {
                        showStatusCard(errorMessage, isError = true)
                        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
                    }
                }
            }
        }
    }

    // Enhanced status card with warning color support
    private fun showStatusCard(message: String, isLoading: Boolean = false, isError: Boolean = false, isWarning: Boolean = false) {
        statusText.text = message

        val textColor = when {
            isError -> Color.RED
            isWarning -> Color.parseColor("#FF8C00") // Orange for warnings
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

    // Alert callbacks remain the same - no additional filtering needed
    override fun onNewAlerts(alerts: List<Alert>) {
        android.util.Log.d("MainActivity", "NEW ALERTS DETECTED: ${alerts.size} alerts")
        alerts.forEach { alert ->
            android.util.Log.d("MainActivity", "Alert: ${alert.report.category} - ${alert.report.originalText.take(30)}")
        }

        val message = alertManager.getAlertSummaryMessage(
            alerts,
            preferencesManager.getSavedLanguage() == "es"
        )
        showStatusCard(message, isLoading = false)

        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        val title = if (isSpanish) "Nueva Alerta" else "New Alert"

        val hasSafetyAlert = alerts.any { it.report.category == ReportCategory.SAFETY }
        val category = if (hasSafetyAlert) "SAFETY" else alerts.first().report.category.code.uppercase()

        // Trigger alert - will respect user's alarm override setting
        alarmManager.triggerAlert(title, message, category)

        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
    }

    override fun onNewFavoriteAlerts(alerts: List<FavoriteAlert>) {
        android.util.Log.d("MainActivity", "NEW FAVORITE ALERTS: ${alerts.size} alerts")

        val message = favoriteManager.getFavoriteAlertSummaryMessage(
            alerts,
            preferencesManager.getSavedLanguage() == "es"
        )
        showStatusCard(message, isLoading = false)

        val isSpanish = preferencesManager.getSavedLanguage() == "es"
        val title = if (isSpanish) "Alerta en Favorito" else "Alert at Favorite"

        val hasSafetyAlert = alerts.any { it.report.category == ReportCategory.SAFETY }
        val category = if (hasSafetyAlert) "SAFETY" else alerts.first().report.category.code.uppercase()

        // Trigger alert - will respect user's alarm override setting
        alarmManager.triggerAlert(title, message, category)

        Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
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
        instructionOverlay = findViewById(R.id.instruction_overlay)
    }

    override fun onFavoritesUpdated(favorites: List<FavoritePlace>) {
        runOnUiThread {
            try {
                android.util.Log.d("MainActivity", "onFavoritesUpdated: ${favorites.size} favorites")
                mapManager.clearFavoriteMarkers(mapView)

                favorites.forEach { favorite ->
                    mapManager.addFavoriteToMap(mapView, favorite, this)
                }

                mapView.post {
                    mapView.invalidate()
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error updating favorites on map: ${e.message}")
            }
        }
    }

    private fun setupInstructionOverlay() {
        val btnUserGuideLink = findViewById<TextView>(R.id.btn_user_guide_link)

        val hasCreatedReports = reportManager.getActiveReports().isNotEmpty() ||
                preferencesManager.hasUserCreatedReports()

        val isLanguageChange = preferencesManager.isLanguageChange()

        if (hasCreatedReports && !isLanguageChange) {
            instructionOverlay.visibility = View.GONE
            hasUserInteracted = true
        } else {
            instructionOverlay.visibility = View.VISIBLE
            hasUserInteracted = false

            instructionOverlay.setOnClickListener {
                hideInstructionOverlay()
            }

            btnUserGuideLink.setOnClickListener {
                hideInstructionOverlay()
                dialogManager.showUserGuideDialog()
            }

            if (isLanguageChange) {
                preferencesManager.setLanguageChange(false)
            }
        }
    }

    private fun scheduleInstructionOverlayAutoHide() {
        if (!hasUserInteracted && instructionOverlay.visibility == View.VISIBLE) {
            Handler(Looper.getMainLooper()).postDelayed({
                if (!hasUserInteracted && instructionOverlay.visibility == View.VISIBLE) {
                    hideInstructionOverlay()
                }
            }, 8000)
        }
    }

    private fun hideInstructionOverlay() {
        if (instructionOverlay.visibility == View.VISIBLE) {
            hasUserInteracted = true
            preferencesManager.setUserHasCreatedReports(true)

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
        android.util.Log.d("MainActivity", "Setting up map...")

        mapManager.setupMap(mapView, packageName)
        mapManager.setupMapLongPressListener(mapView, this)

        android.util.Log.d("MainActivity", "Map setup completed, repository status: ${mapView.repository != null}")

        // IMPROVED: Check map repository status and add delayed loading
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                android.util.Log.d("MainActivity", "Delayed map check - Repository ready: ${mapView.repository != null}")

                if (::mapView.isInitialized && mapView.repository != null) {
                    android.util.Log.d("MainActivity", "Map repository is ready, loading existing items")

                    val reports = reportManager.getActiveReports()
                    android.util.Log.d("MainActivity", "Loading ${reports.size} existing reports")
                    reports.forEach { report ->
                        mapManager.addReportToMap(mapView, report, this)
                    }

                    val favorites = favoriteManager.getFavorites()
                    android.util.Log.d("MainActivity", "Loading ${favorites.size} existing favorites")
                    favorites.forEach { favorite ->
                        mapManager.addFavoriteToMap(mapView, favorite, this)
                    }

                    android.util.Log.d("MainActivity", "All existing items loaded on map")
                } else {
                    android.util.Log.w("MainActivity", "Map repository not ready after 3 seconds, will retry")
                    // Retry once more
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (::mapView.isInitialized && mapView.repository != null) {
                            android.util.Log.d("MainActivity", "Map repository ready on retry")
                            reportManager.getActiveReports().forEach { report ->
                                mapManager.addReportToMap(mapView, report, this)
                            }
                            favoriteManager.getFavorites().forEach { favorite ->
                                mapManager.addFavoriteToMap(mapView, favorite, this)
                            }
                        } else {
                            android.util.Log.e("MainActivity", "Map repository still not ready after retry")
                        }
                    }, 2000)
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading existing items on map: ${e.message}")
            }
        }, 3000)
    }

    private fun setupBottomNavigation() {
        btnLanguageToggle.setOnClickListener {
            animateButtonPress(btnLanguageToggle)
            toggleLanguage()
            hideInstructionOverlay()
        }

        btnSettings.setOnClickListener {
            animateButtonPress(btnSettings)
            dialogManager.showSettingsDialog(backendClient)
            hideInstructionOverlay()
        }

        btnAlerts.setOnClickListener {
            animateButtonPress(btnAlerts)
            openAlertsScreen()
            hideInstructionOverlay()
        }

        btnFavorites.setOnClickListener {
            animateButtonPress(btnFavorites)
            openFavoritesScreen()
            hideInstructionOverlay()
        }
    }

    override fun onLocationUpdate(location: Location) {
        val userLocation = GeoPoint(location.latitude, location.longitude)
        mapManager.addLocationMarker(mapView, userLocation)

        alertManager.checkForNewAlerts(location)
        favoriteManager.checkForFavoriteAlerts()

        subscribeToAlertsForLocation(location.latitude, location.longitude)
    }

    override fun onLocationError(error: String) {
        android.util.Log.e("Derrite", "Location error: $error")
    }

    override fun onAlertsUpdated(hasUnviewed: Boolean) {
        updateAlertsButtonColor(hasUnviewed)
    }

    override fun onFavoriteAlertsUpdated(alerts: List<FavoriteAlert>, hasUnviewed: Boolean) {
        updateFavoritesButtonColor(hasUnviewed)
    }

    // MapManager.MapInteractionListener Implementation
    override fun onLongPress(location: GeoPoint) {
        hideInstructionOverlay()
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
            arrayOf("Crear Nuevo Reporte", "Agregar a Favoritos", "Cancelar")
        } else {
            arrayOf("Create New Report", "Add to Favorites", "Cancel")
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
            // selectedCategory = ReportCategory.SAFETY, // Always safety
            // onCategoryChange = { category: ReportCategory ->
            //     // No longer needed - always safety
            // },
            onPhotoRequest = { onPhotoSelected ->
                val photoCallback = object : PhotoManager.PhotoCallback {
                    override fun onPhotoSelected(bitmap: Bitmap) {
                        onPhotoSelected(bitmap)
                    }

                    override fun onPhotoError(message: String) {
                        showStatusCard(message, isError = true)
                    }
                }
                photoManager.showPhotoSelectionDialog(photoCallback)
            },
            onSubmit = { reportLocation: GeoPoint, text: String, photo: Bitmap?, category: ReportCategory ->
                createReport(reportLocation, text, photo, ReportCategory.SAFETY) // Always safety
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
        val tintColor = ContextCompat.getColor(this, R.color.bottom_nav_icon)
        btnFavorites.imageTintList = android.content.res.ColorStateList.valueOf(tintColor)
    }

    private fun startPeriodicReportSync() {
        syncHandler = Handler(Looper.getMainLooper())
        syncRunnable = object : Runnable {
            override fun run() {
                try {
                    // Only sync if app is in foreground
                    if (isAppInForeground && ::mapView.isInitialized && mapView.repository != null) {
                        silentFetchReports { success ->
                            android.util.Log.d("MainActivity", "Foreground sync completed: $success")
                        }
                    }

                    // Schedule next sync only if still in foreground
                    if (isAppInForeground) {
                        syncHandler?.postDelayed(this, 30000)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in periodic sync: ${e.message}")
                    if (isAppInForeground) {
                        syncHandler?.postDelayed(this, 30000)
                    }
                }
            }
        }

        // Start first sync after 10 seconds
        syncHandler?.postDelayed(syncRunnable!!, 10000)
    }

    private fun stopPeriodicReportSync() {
        syncRunnable?.let { runnable ->
            syncHandler?.removeCallbacks(runnable)
        }
        syncHandler = null
        syncRunnable = null
    }

    private fun startTimers() {
        val cleanupRunnable = object : Runnable {
            override fun run() {
                try {
                    cleanupExpiredReports()
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                } catch (e: Exception) {
                    Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                }
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(cleanupRunnable, 60 * 60 * 1000)

        // Alert checking with server fetching - FIXED to only check alerts when new reports are fetched
        val alertCheckRunnable = object : Runnable {
            override fun run() {
                try {
                    // Fetch new reports from server first, alerts will be checked in silentFetchReports
                    // if new reports are found
                    silentFetchReports { success ->
                        android.util.Log.d("MainActivity", "Periodic fetch completed: $success")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error in periodic alert check: ${e.message}")
                }

                // Schedule next check in 2 minutes
                Handler(Looper.getMainLooper()).postDelayed(this, 2 * 60 * 1000)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(alertCheckRunnable, 10000)
    }

    private fun setupLocationButton() {
        fabLocation.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.fab_background)
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
            hideInstructionOverlay()
        }

        btnClearSearch.setOnClickListener {
            animateButtonPress(btnClearSearch)
            clearSearch()
        }

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                hideInstructionOverlay()
                true
            } else false
        }

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                if (!s.isNullOrEmpty()) {
                    hideInstructionOverlay()
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
                showStatusCard(locationText, isLoading = false)
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

    private fun getCurrentLocationSilently(isStartup: Boolean = false) {
        android.util.Log.d("MainActivity", "🗺️ getCurrentLocationSilently called with isStartup: $isStartup")
        android.util.Log.d("MainActivity", "🗺️ hasInitialLocationSet: $hasInitialLocationSet")

        locationManager.getLastLocation { location: Location? ->
            if (location != null) {
                val userLocation = GeoPoint(location.latitude, location.longitude)
                android.util.Log.d("MainActivity", "🗺️ Got location: ${userLocation.latitude}, ${userLocation.longitude}")

                if (isStartup && !hasInitialLocationSet) {
                    mapView.controller.setCenter(userLocation)
                    mapView.controller.setZoom(18.0)
                    mapView.controller.setCenter(userLocation)
                    mapView.controller.setZoom(18.0)

// FORCE REFRESH
                    mapView.invalidate()

// CHECK AGAIN AFTER REFRESH
                    Handler(Looper.getMainLooper()).postDelayed({
                        android.util.Log.d("MainActivity", "🔍 After refresh - center: ${mapView.mapCenter}")
                        android.util.Log.d("MainActivity", "🔍 After refresh - zoom: ${mapView.zoomLevelDouble}")
                    }, 500)
                    android.util.Log.d("MainActivity", "🗺️ Map center set to: ${userLocation.latitude}, ${userLocation.longitude}")
                    android.util.Log.d("MainActivity", "🗺️ Map zoom set to: 18.0")

                    mapManager.addLocationMarker(mapView, userLocation)
                    hasInitialLocationSet = true

                    android.util.Log.d("MainActivity", "🗺️ hasInitialLocationSet now: $hasInitialLocationSet")
                    // Check for alerts and subscribe
                    alertManager.checkForNewAlerts(location)
                    subscribeToAlertsForLocation(location.latitude, location.longitude)

                    Handler(Looper.getMainLooper()).postDelayed({
                        mapView.invalidate()
                        mapView.requestLayout()
                    }, 500)

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


    // ENHANCED: More robust initial location setting
    private fun setInitialMapLocation(userLocation: GeoPoint) {
        try {
            android.util.Log.d("MainActivity", "Setting initial map location: ${userLocation.latitude}, ${userLocation.longitude}")
            android.util.Log.d("MainActivity", "Map state - initialized: ${::mapView.isInitialized}, repository: ${mapView.repository != null}")

            // Double-check map state
            if (!::mapView.isInitialized) {
                android.util.Log.e("MainActivity", "MapView not initialized!")
                throw Exception("MapView not initialized")
            }

            if (mapView.repository == null) {
                android.util.Log.e("MainActivity", "MapView repository is null!")
                throw Exception("MapView repository is null")
            }

            // Set the location
            mapView.controller.setCenter(userLocation)
            mapView.controller.setZoom(18.0)
            mapManager.addLocationMarker(mapView, userLocation)
            hasInitialLocationSet = true

            android.util.Log.d("MainActivity", "Map location set successfully")
            hideStatusCard()

            // Check for alerts and subscribe after location is set
            val location = android.location.Location("").apply {
                latitude = userLocation.latitude
                longitude = userLocation.longitude
            }
            alertManager.checkForNewAlerts(location)
            subscribeToAlertsForLocation(userLocation.latitude, userLocation.longitude)

            // Force map refresh
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    mapView.invalidate()
                    mapView.requestLayout()
                    android.util.Log.d("MainActivity", "Initial location set successfully - map refreshed")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error refreshing map after location set: ${e.message}")
                }
            }, 500)

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error setting initial map location: ${e.message}")
            hideStatusCard()

            // Don't retry forever - just log the failure
            android.util.Log.e("MainActivity", "Failed to set initial location, giving up")
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
        android.util.Log.d("MainActivity", "Requesting location permission")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            android.util.Log.w("MainActivity", "Pre-M device, should have location permission")
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
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Encontrando tu ubicación..." else "Finding your location..."
                    showStatusCard(message, isLoading = true)
                    getCurrentLocationSilently(isStartup = true)
                } else {
                    val message = if (preferencesManager.getSavedLanguage() == "es")
                        "Se requiere permiso de ubicación" else "Location permission required"
                    showStatusCard(message, isError = true)
                    Handler(Looper.getMainLooper()).postDelayed({ hideStatusCard() }, 3000)
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
            android.util.Log.e("Derrite", "Error in onDestroy: ${e.message}")
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            mapView.onResume()
            isAppInForeground = true

            // Sync once when returning to foreground
            Handler(Looper.getMainLooper()).postDelayed({
                silentFetchReports { success ->
                    android.util.Log.d("MainActivity", "Resume sync completed: $success")
                }
            }, 1000)

            // Restart periodic sync
            if (syncRunnable == null) {
                startPeriodicReportSync()
            }

            // Existing favorites refresh code...
            refreshFavoritesOnMap()

        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error in onResume: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            mapView.onPause()
            isAppInForeground = false
            stopPeriodicReportSync() // Stop battery-draining sync
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("Derrite", "Error in onPause: ${e.message}")
        }
    }

    private fun refreshFavoritesOnMap() {
        try {
            favoriteManager.loadSavedData()

            val currentFavorites = favoriteManager.getFavorites()
            mapManager.clearFavoriteMarkers(mapView)

            currentFavorites.forEach { favorite ->
                mapManager.addFavoriteToMap(mapView, favorite, this)
            }

            mapView.invalidate()
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error refreshing favorites on map: ${e.message}")
        }
    }

    companion object {
        private const val FAVORITES_ACTIVITY_REQUEST = 1001
    }

    private fun openFavoritesScreen() {
        val intent = Intent(this, FavoritesActivity::class.java)
        startActivityForResult(intent, FAVORITES_ACTIVITY_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            FAVORITES_ACTIVITY_REQUEST -> {
                if (resultCode == RESULT_OK) {
                    refreshFavoritesOnMap()
                    favoriteManager.setFavoriteListener(this)
                }
            }
        }
    }
}