package com.garfunkel.derriteice

// OkHttp imports
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// Firebase imports
import com.google.firebase.messaging.FirebaseMessaging

// JSON imports
import org.json.JSONObject

// Translation imports
import com.garfunkel.derriteice.translation.AdaptiveTranslator
import com.garfunkel.derriteice.translation.SimpleTranslator

// Coroutines
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

// Android imports
import android.graphics.Color
import android.Manifest
import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import android.widget.TextView

// AndroidX imports
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// Google services
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

// Material design
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText

// OSM imports
import org.osmdroid.config.Configuration as OSMConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon

// Java imports
import java.util.Locale
import java.util.UUID
import kotlin.math.*

data class Report(
    val id: String,
    val location: GeoPoint,
    val originalText: String,
    val originalLanguage: String,
    val hasPhoto: Boolean,
    val photo: Bitmap?,
    val timestamp: Long,
    val expiresAt: Long
)

data class Alert(
    val id: String,
    val report: Report,
    val distanceFromUser: Double,
    val isViewed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

class BackendClient {
    companion object {
        private const val BACKEND_URL = "https://backend-production-cfbe.up.railway.app"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun testConnection(callback: (Boolean, String) -> Unit) {
        val request = Request.Builder()
            .url("$BACKEND_URL/health")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(false, "Cannot connect: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: "No response body"
                        callback(true, "Connected! Response: $body")
                    } else {
                        callback(false, "Server error: ${response.code}")
                    }
                }
            }
        })
    }

    fun submitReport(
        latitude: Double,
        longitude: Double,
        content: String,
        language: String,
        hasPhoto: Boolean,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val json = JSONObject().apply {
                put("lat", latitude)
                put("lng", longitude)
                put("content", content)
                put("language", language)
                put("hasPhoto", hasPhoto)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BACKEND_URL/report")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    callback(false, "Network error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (response.isSuccessful) {
                            val responseBody = response.body?.string() ?: "{}"
                            try {
                                val jsonResponse = JSONObject(responseBody)
                                val success = jsonResponse.optBoolean("success", false)
                                val zone = jsonResponse.optString("zone", "unknown")

                                if (success) {
                                    callback(true, "Report submitted to zone: $zone")
                                } else {
                                    callback(false, "Server rejected report")
                                }
                            } catch (e: Exception) {
                                callback(false, "Invalid server response")
                            }
                        } else {
                            callback(false, "Server error: ${response.code}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback(false, "Request creation failed: ${e.message}")
        }
    }

    fun subscribeToAlerts(
        latitude: Double,
        longitude: Double,
        callback: (Boolean, String) -> Unit
    ) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    callback(false, "Failed to get FCM token: ${task.exception?.message}")
                    return@addOnCompleteListener
                }

                try {
                    val token = task.result
                    val json = JSONObject().apply {
                        put("lat", latitude)
                        put("lng", longitude)
                        put("platform", "android")
                        put("token", token)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = json.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url("$BACKEND_URL/subscribe")
                        .post(requestBody)
                        .build()

                    client.newCall(request).enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            callback(false, "Subscription failed: ${e.message}")
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use {
                                if (response.isSuccessful) {
                                    val responseBody = response.body?.string() ?: "{}"
                                    try {
                                        val jsonResponse = JSONObject(responseBody)
                                        val zones = jsonResponse.optJSONArray("affected_zones")
                                        callback(true, "Subscribed to ${zones?.length() ?: 0} zones")
                                    } catch (e: Exception) {
                                        callback(true, "Subscribed successfully")
                                    }
                                } else {
                                    callback(false, "Subscription error: ${response.code}")
                                }
                            }
                        }
                    })
                } catch (e: Exception) {
                    callback(false, "Subscription request failed: ${e.message}")
                }
            }
            .addOnFailureListener { e ->
                callback(false, "FCM token error: ${e.message}")
            }
    }
}

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

    private lateinit var backendClient: BackendClient
    private lateinit var adaptiveTranslator: AdaptiveTranslator
    private var translatorInitialized = false
    private var currentTranslationJob: Job? = null
    private var lastZoomTime = 0L
    private val zoomThrottleMs = 150L

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
    private val REPORT_RADIUS_METERS = 804.5
    private val REPORT_DURATION_HOURS = 8L
    private val ZIP_CODE_RADIUS_METERS = 8047.0

    private val ALERT_DISTANCE_1_MILE = 1609.0
    private val ALERT_DISTANCE_2_MILES = 3218.0
    private val ALERT_DISTANCE_3_MILES = 4827.0
    private val ALERT_DISTANCE_5_MILES = 8047.0
    private val ALERT_DISTANCE_ZIP_CODE = 8050.0
    private val ALERT_DISTANCE_STATE = 160934.0

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            currentPhoto = stripPhotoMetadata(bitmap)
            val message = if (getSavedLanguage() == "es")
                "Foto agregada anónimamente" else "Photo added anonymously"
            showStatusCard(message, isLoading = false)
        }
    }

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
                    val message = if (getSavedLanguage() == "es")
                        "Foto agregada anónimamente" else "Photo added anonymously"
                    showStatusCard(message, isLoading = false)
                } else {
                    val message = if (getSavedLanguage() == "es")
                        "Error al cargar foto" else "Failed to load photo"
                    showStatusCard(message, isError = true)
                }
            } catch (e: Exception) {
                val message = if (getSavedLanguage() == "es")
                    "Error al cargar foto" else "Failed to load photo"
                showStatusCard(message, isError = true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            preferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            backendClient = BackendClient()

            loadViewedAlerts()
            loadSavedReports()
            setAppLanguage(getSavedLanguage())

            OSMConfiguration.getInstance().load(this, getSharedPreferences("osmdroid", 0))
            OSMConfiguration.getInstance().userAgentValue = packageName

            setContentView(R.layout.activity_main)

            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            geocoder = Geocoder(this, Locale.getDefault())

            setupViews()
            setupMap()
            setupLocationButton()
            setupStatusCard()
            setupInstructionOverlay()
            setupBottomNavigation()
            setupSearchBar()

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    startLocationUpdates()
                    initializeTranslationSystem()
                    autoLocateOnStartup()
                    startReportCleanupTimer()
                    startAlertChecker()
                } catch (e: Exception) {
                    android.util.Log.e("DerriteICE", "Error in delayed initialization: ${e.message}")
                }
            }, 1000)
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error in onCreate: ${e.message}", e)

            try {
                setContentView(R.layout.activity_main)
                showStatusCard("App initialization error - please restart", isError = true)
            } catch (innerE: Exception) {
                // If even basic setup fails, do nothing to avoid crash
            }
        }
    }

    private fun startLocationUpdates() {
        try {
            if (!hasLocationPermission()) return

            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                30000
            ).apply {
                setMinUpdateIntervalMillis(15000)
            }.build()

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                    try {
                        locationResult.lastLocation?.let { location ->
                            currentLocation = location
                            val userLocation = GeoPoint(location.latitude, location.longitude)

                            if (::mapView.isInitialized && mapView.repository != null) {
                                addLocationMarker(userLocation)
                            } else {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (::mapView.isInitialized && mapView.repository != null) {
                                        addLocationMarker(userLocation)
                                    }
                                }, 1000)
                            }

                            subscribeToAlertsForLocation(location.latitude, location.longitude)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("DerriteICE", "Error in location callback: ${e.message}")
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            android.util.Log.e("DerriteICE", "Permission denied for location updates")
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error starting location updates: ${e.message}")
        }
    }

    private fun initializeTranslationSystem() {
        adaptiveTranslator = AdaptiveTranslator(this)
        downloadMLKitModelsInBackground()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    adaptiveTranslator.initialize()
                }
                translatorInitialized = success
            } catch (e: Exception) {
                translatorInitialized = false
            }
        }
    }

    private fun downloadMLKitModelsInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val isWiFi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                } else {
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.activeNetworkInfo
                    networkInfo?.type == ConnectivityManager.TYPE_WIFI
                }

                if (!isWiFi) return@launch

                downloadTranslationModel(
                    com.google.mlkit.nl.translate.TranslateLanguage.SPANISH,
                    com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH,
                    "Spanish to English"
                )

                downloadTranslationModel(
                    com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH,
                    com.google.mlkit.nl.translate.TranslateLanguage.SPANISH,
                    "English to Spanish"
                )
            } catch (e: Exception) {
                // Background download failed
            }
        }
    }

    private suspend fun downloadTranslationModel(sourceLanguage: String, targetLanguage: String, description: String) {
        try {
            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()

            val translator = com.google.mlkit.nl.translate.Translation.getClient(options)

            val downloadConditions = com.google.mlkit.common.model.DownloadConditions.Builder()
                .requireWifi()
                .build()

            val downloadSuccess = withContext(Dispatchers.IO) {
                try {
                    val downloadTask = translator.downloadModelIfNeeded(downloadConditions)
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        downloadTask
                            .addOnSuccessListener { continuation.resume(true) }
                            .addOnFailureListener { continuation.resume(false) }
                    }
                } catch (e: Exception) {
                    false
                }
            }

            if (downloadSuccess) {
                testTranslationModel(translator, description)
            }
        } catch (e: Exception) {
            // Model download failed
        }
    }

    private fun testTranslationModel(translator: com.google.mlkit.nl.translate.Translator, description: String) {
        translator.translate("test")
            .addOnSuccessListener { }
            .addOnFailureListener { }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { task ->
                if (task.exception != null) {
                    continuation.resumeWithException(task.exception!!)
                } else {
                    continuation.resume(task.result)
                }
            }
        }
    }

    private fun intelligentTranslate(
        text: String,
        fromLang: String,
        toLang: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        tryMLKitTranslation(text, fromLang, toLang,
            onSuccess = { result -> onSuccess(result) },
            onFailure = { _ ->
                try {
                    val keywordResult = SimpleTranslator().translateText(text, fromLang, toLang)
                    onSuccess(keywordResult)
                } catch (e: Exception) {
                    onError("Translation unavailable")
                }
            }
        )
    }

    private fun tryMLKitTranslation(
        text: String,
        fromLang: String,
        toLang: String,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        try {
            val sourceLanguage = if (fromLang == "es")
                com.google.mlkit.nl.translate.TranslateLanguage.SPANISH
            else com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH

            val targetLanguage = if (toLang == "es")
                com.google.mlkit.nl.translate.TranslateLanguage.SPANISH
            else com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH

            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()

            val translator = com.google.mlkit.nl.translate.Translation.getClient(options)

            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    if (translatedText.isNotEmpty()) {
                        onSuccess(translatedText)
                    } else {
                        onFailure("Empty result")
                    }
                }
                .addOnFailureListener { e ->
                    onFailure(e.message ?: "Translation failed")
                }
        } catch (e: Exception) {
            onFailure("Setup failed: ${e.message}")
        }
    }

    private fun loadViewedAlerts() {
        val viewedSet = preferences.getStringSet("viewed_alerts", emptySet()) ?: emptySet()
        viewedAlertIds.clear()
        viewedAlertIds.addAll(viewedSet)
    }

    private fun saveViewedAlerts() {
        preferences.edit().putStringSet("viewed_alerts", viewedAlertIds).apply()
    }

    private fun loadSavedReports() {
        try {
            val reportsJson = preferences.getString("saved_reports", "[]") ?: "[]"
            val reportsList = parseReportsFromJson(reportsJson)
            val currentTime = System.currentTimeMillis()
            val validReports = reportsList.filter { it.expiresAt > currentTime }

            activeReports.clear()
            activeReports.addAll(validReports)

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (::mapView.isInitialized && mapView.repository != null) {
                        validReports.forEach { report ->
                            addReportToMap(report)
                        }
                    } else {
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                if (::mapView.isInitialized && mapView.repository != null) {
                                    validReports.forEach { report ->
                                        addReportToMap(report)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DerriteICE", "Error in delayed report loading retry: ${e.message}")
                            }
                        }, 2000)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DerriteICE", "Error in delayed report loading: ${e.message}")
                }
            }, 2000)
        } catch (e: Exception) {
            activeReports.clear()
            android.util.Log.e("DerriteICE", "Error loading saved reports: ${e.message}")
        }
    }

    private fun saveReportsToPreferences() {
        try {
            val reportsJson = convertReportsToJson(activeReports)
            preferences.edit().putString("saved_reports", reportsJson).apply()
        } catch (e: Exception) {
            // Handle save error silently
        }
    }

    private fun parseReportsFromJson(json: String): List<Report> {
        val reports = mutableListOf<Report>()
        try {
            if (json.isBlank() || json == "[]") {
                return reports
            }

            val lines = json.split("|||")
            for (line in lines) {
                try {
                    if (line.trim().isEmpty()) continue

                    val parts = line.split(":::")
                    if (parts.size >= 8) {
                        val report = Report(
                            id = parts[0],
                            location = GeoPoint(
                                parts[1].toDoubleOrNull() ?: 0.0,
                                parts[2].toDoubleOrNull() ?: 0.0
                            ),
                            originalText = parts[3],
                            originalLanguage = parts[4],
                            hasPhoto = parts[5].toBooleanStrictOrNull() ?: false,
                            photo = null,
                            timestamp = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                            expiresAt = parts[7].toLongOrNull() ?: (System.currentTimeMillis() + (8 * 60 * 60 * 1000))
                        )
                        reports.add(report)
                    }
                } catch (e: Exception) {
                    continue
                }
            }
        } catch (e: Exception) {
            // Return empty list if parsing fails completely
        }
        return reports
    }

    private fun convertReportsToJson(reports: List<Report>): String {
        return reports.joinToString("|||") { report ->
            "${report.id}:::${report.location.latitude}:::${report.location.longitude}:::${report.originalText}:::${report.originalLanguage}:::${report.hasPhoto}:::${report.timestamp}:::${report.expiresAt}"
        }
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    private fun checkForNewAlerts() {
        try {
            currentLocation?.let { location ->
                val newAlerts = mutableListOf<Alert>()
                val userAlertDistance = getSavedAlertDistance()

                for (report in activeReports) {
                    try {
                        val distance = calculateDistance(
                            location.latitude, location.longitude,
                            report.location.latitude, report.location.longitude
                        )

                        if (distance <= userAlertDistance) {
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
                    } catch (e: Exception) {
                        continue
                    }
                }

                val hasUnviewed = activeAlerts.any { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
                updateAlertsButtonColor(hasUnviewed)

                if (newAlerts.isNotEmpty()) {
                    val unviewedNewAlerts = newAlerts.filter { !it.isViewed && !viewedAlertIds.contains(it.report.id) }
                    if (unviewedNewAlerts.isNotEmpty()) {
                        val alertDistanceText = getAlertDistanceText(userAlertDistance)
                        val message = if (getSavedLanguage() == "es")
                            "${unviewedNewAlerts.size} nuevas alertas dentro de $alertDistanceText"
                        else
                            "${unviewedNewAlerts.size} new alerts within $alertDistanceText"
                        showStatusCard(message, isLoading = false)
                        Handler(Looper.getMainLooper()).postDelayed({
                            hideStatusCard()
                        }, 3000)
                    }
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }

    private fun startAlertChecker() {
        val alertCheckRunnable = object : Runnable {
            override fun run() {
                checkForNewAlerts()
                Handler(Looper.getMainLooper()).postDelayed(this, 2 * 60 * 1000)
            }
        }
        Handler(Looper.getMainLooper()).postDelayed(alertCheckRunnable, 10000)
    }

    private fun openAlertsScreen() {
        currentLocation?.let { location ->
            showAlertsDialog(location)
        } ?: run {
            val message = if (getSavedLanguage() == "es")
                "Se necesita ubicación para alertas" else "Location needed for alerts"
            showStatusCard(message, isError = true)
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

        loadingContainer.visibility = View.VISIBLE
        noAlertsContainer.visibility = View.GONE
        btnMarkAllRead.visibility = View.GONE

        updateLocationDescription(userLocation, textAlertLocation)

        btnCloseAlerts.setOnClickListener { currentAlertsDialog?.dismiss() }
        btnRefreshAlerts.setOnClickListener {
            refreshAlerts(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }
        btnMarkAllRead.setOnClickListener {
            markAllAlertsAsRead()
            loadAlertsIntoDialog(alertsContainer, noAlertsContainer, loadingContainer, userLocation)
        }

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
                val text = if (getSavedLanguage() == "es")
                    "Alertas para ${locationText.ifEmpty { "tu área" }}"
                else
                    "Alerts for ${locationText.ifEmpty { "your area" }}"
                textView.text = text
            } else {
                val text = if (getSavedLanguage() == "es") "Alertas para tu área" else "Alerts for your area"
                textView.text = text
            }
        } catch (e: IOException) {
            val text = if (getSavedLanguage() == "es") "Alertas para tu área" else "Alerts for your area"
            textView.text = text
        }
    }

    private fun refreshAlerts(alertsContainer: LinearLayout, noAlertsContainer: LinearLayout,
                              loadingContainer: LinearLayout, userLocation: Location) {
        loadingContainer.visibility = View.VISIBLE
        noAlertsContainer.visibility = View.GONE
        alertsContainer.removeAllViews()

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
        try {
            alertsContainer.removeAllViews()

            val userAlertDistance = getSavedAlertDistance()

            val nearbyUnviewedAlerts = activeAlerts
                .filter { !viewedAlertIds.contains(it.report.id) }
                .map { alert ->
                    val currentDistance = calculateDistance(
                        userLocation.latitude, userLocation.longitude,
                        alert.report.location.latitude, alert.report.location.longitude
                    )
                    alert.copy(distanceFromUser = currentDistance)
                }
                .filter { it.distanceFromUser <= userAlertDistance }
                .sortedBy { it.distanceFromUser }

            loadingContainer.visibility = View.GONE

            currentAlertsDialog?.let { dialog ->
                val btnMarkAllRead = dialog.findViewById<Button>(R.id.btn_mark_all_read)

                if (nearbyUnviewedAlerts.isEmpty()) {
                    noAlertsContainer.visibility = View.VISIBLE
                    btnMarkAllRead?.visibility = View.GONE
                } else {
                    noAlertsContainer.visibility = View.GONE
                    btnMarkAllRead?.visibility = View.VISIBLE

                    for (alert in nearbyUnviewedAlerts) {
                        try {
                            val alertItemView = createAlertItemView(alert)
                            alertsContainer.addView(alertItemView)
                        } catch (e: Exception) {
                            continue
                        }
                    }
                }
            }
        } catch (e: Exception) {
            loadingContainer.visibility = View.GONE
            noAlertsContainer.visibility = View.VISIBLE
        }
    }

    private fun createAlertItemView(alert: Alert): View {
        return try {
            val alertView = LayoutInflater.from(this).inflate(R.layout.item_alert, null)

            val textAlertDistance = alertView.findViewById<TextView>(R.id.text_alert_distance)
            val textAlertTime = alertView.findViewById<TextView>(R.id.text_alert_time)
            val newAlertBadge = alertView.findViewById<View>(R.id.new_alert_badge)
            val textAlertContent = alertView.findViewById<TextView>(R.id.text_alert_content)
            val photoIndicator = alertView.findViewById<LinearLayout>(R.id.photo_indicator)
            val btnViewOnMap = alertView.findViewById<Button>(R.id.btn_view_on_map)
            val btnViewDetails = alertView.findViewById<Button>(R.id.btn_view_details)

            textAlertDistance?.text = formatDistance(alert.distanceFromUser)
            textAlertTime?.text = getTimeAgo(alert.report.timestamp)
            textAlertContent?.text = alert.report.originalText ?: "Report content unavailable"

            photoIndicator?.visibility = if (alert.report.hasPhoto) View.VISIBLE else View.GONE
            newAlertBadge?.visibility = View.VISIBLE

            btnViewOnMap?.setOnClickListener {
                try {
                    currentAlertsDialog?.dismiss()
                    showReportOnMap(alert.report)
                    markAlertAsViewed(alert.report.id)
                } catch (e: Exception) {
                    // Silently handle button click errors
                }
            }

            btnViewDetails?.setOnClickListener {
                try {
                    showReportViewDialog(alert.report)
                    markAlertAsViewed(alert.report.id)
                } catch (e: Exception) {
                    // Silently handle button click errors
                }
            }

            alertView
        } catch (e: Exception) {
            TextView(this).apply {
                text = "Error loading alert"
                textSize = 14f
                setPadding(16, 16, 16, 16)
                setTextColor(Color.RED)
            }
        }
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

    private fun markAlertAsViewed(reportId: String) {
        viewedAlertIds.add(reportId)
        saveViewedAlerts()

        val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
        updateAlertsButtonColor(hasUnviewed)

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

    private fun getSavedLanguage(): String {
        return preferences.getString("app_language", "es") ?: "es"
    }

    private fun saveLanguage(languageCode: String) {
        preferences.edit().putString("app_language", languageCode).apply()
    }

    private fun getSavedAlertDistance(): Double {
        return preferences.getFloat("alert_distance", ALERT_DISTANCE_ZIP_CODE.toFloat()).toDouble()
    }

    private fun saveAlertDistance(distance: Double) {
        preferences.edit().putFloat("alert_distance", distance.toFloat()).apply()
    }

    private fun getAlertDistanceText(distance: Double): String {
        val isSpanish = getSavedLanguage() == "es"

        return when (distance) {
            ALERT_DISTANCE_1_MILE -> if (isSpanish) "1 milla" else "1 mile"
            ALERT_DISTANCE_2_MILES -> if (isSpanish) "2 millas" else "2 miles"
            ALERT_DISTANCE_3_MILES -> if (isSpanish) "3 millas" else "3 miles"
            ALERT_DISTANCE_5_MILES -> if (isSpanish) "5 millas" else "5 miles"
            ALERT_DISTANCE_ZIP_CODE -> if (isSpanish) "área de código postal" else "zip code area"
            ALERT_DISTANCE_STATE -> if (isSpanish) "todo el estado" else "state-wide"
            else -> if (isSpanish) "área de código postal (predeterminado)" else "zip code area (default)"
        }
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

        preferences.edit().putBoolean("is_language_change", true).apply()
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
            showSettingsDialog()
        }

        btnAlerts.setOnClickListener {
            animateButtonPress(btnAlerts)
            openAlertsScreen()
        }

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

    private fun showSettingsDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(this).apply {
            text = if (isSpanish) "Configuración" else "Settings"
            textSize = 24f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val versionInfo = TextView(this).apply {
            try {
                val packageInfo = packageManager.getPackageInfo(packageName, 0)
                text = if (isSpanish)
                    "Versión ${packageInfo.versionName} (${packageInfo.versionCode})"
                else
                    "Version ${packageInfo.versionName} (${packageInfo.versionCode})"
            } catch (e: Exception) {
                text = if (isSpanish) "Versión 1.0.0" else "Version 1.0.0"
            }
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(versionInfo)

        val backendStatus = TextView(this).apply {
            text = if (isSpanish) "Verificando conexión del servidor..." else "Checking backend connection..."
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#333333"))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 24)
            }
        }
        dialogLayout.addView(backendStatus)

        backendClient.testConnection { success, message ->
            runOnUiThread {
                if (success) {
                    backendStatus.text = if (isSpanish) "Servidor Conectado" else "Backend Connected"
                    backendStatus.setBackgroundColor(Color.parseColor("#2E7D32"))
                } else {
                    backendStatus.text = if (isSpanish) "Servidor Desconectado" else "Backend Offline"
                    backendStatus.setBackgroundColor(Color.parseColor("#C62828"))
                }
            }
        }

        addSettingsSection(dialogLayout,
            if (isSpanish) "Guía del Usuario" else "User Guide",
            if (isSpanish) "Aprende cómo usar la aplicación" else "Learn how to use the app") {
            showUserGuideDialog()
        }

        addSettingsSection(dialogLayout,
            if (isSpanish) "Preguntas Frecuentes" else "FAQ",
            if (isSpanish) "Preguntas frecuentes" else "Frequently asked questions") {
            showFAQDialog()
        }

        addSettingsSection(dialogLayout,
            if (isSpanish) "Preferencias" else "Preferences",
            if (isSpanish) "Configuración de la aplicación" else "App settings and preferences") {
            showPreferencesDialog()
        }

        addSettingsSection(dialogLayout,
            if (isSpanish) "Política de Privacidad" else "Privacy Policy",
            if (isSpanish) "Cómo protegemos tus datos" else "How we protect your data") {
            showPrivacyPolicyDialog()
        }

        addSettingsSection(dialogLayout,
            if (isSpanish) "Acerca de" else "About",
            if (isSpanish) "Acerca de esta aplicación" else "About this app") {
            showAboutDialog()
        }

        val btnClose = Button(this).apply {
            text = if (isSpanish) "CERRAR" else "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 32, 0, 0)
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun addSettingsSection(container: LinearLayout, title: String, description: String, onClick: () -> Unit) {
        val sectionView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(Color.parseColor("#333333"))
            isClickable = true
            isFocusable = true

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }

            setOnClickListener {
                animateButtonPress(this)
                onClick()
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        sectionView.addView(titleText)

        val descriptionText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            setPadding(0, 8, 0, 0)
        }
        sectionView.addView(descriptionText)

        container.addView(sectionView)
    }

    private fun showAlertDistanceDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(this).apply {
            text = if (isSpanish) "Distancia de Alerta" else "Alert Distance"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val descText = TextView(this).apply {
            text = if (isSpanish)
                "Elige qué tan lejos deben estar los reportes para alertarte:"
            else
                "Choose how far away reports should be to alert you:"
            textSize = 14f
            setTextColor(Color.parseColor("#CCCCCC"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(descText)

        val currentDistance = getSavedAlertDistance()

        val options = if (isSpanish) {
            listOf(
                "1 milla" to ALERT_DISTANCE_1_MILE,
                "2 millas" to ALERT_DISTANCE_2_MILES,
                "3 millas" to ALERT_DISTANCE_3_MILES,
                "5 millas" to ALERT_DISTANCE_5_MILES,
                "Área de código postal" to ALERT_DISTANCE_ZIP_CODE,
                "Todo el estado" to ALERT_DISTANCE_STATE
            )
        } else {
            listOf(
                "1 mile" to ALERT_DISTANCE_1_MILE,
                "2 miles" to ALERT_DISTANCE_2_MILES,
                "3 miles" to ALERT_DISTANCE_3_MILES,
                "5 miles" to ALERT_DISTANCE_5_MILES,
                "Zip code area" to ALERT_DISTANCE_ZIP_CODE,
                "State-wide" to ALERT_DISTANCE_STATE
            )
        }

        var selectedDistance = currentDistance
        val optionViews = mutableListOf<LinearLayout>()

        for ((label, distance) in options) {
            val optionView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 16, 24, 16)
                setBackgroundColor(if (distance == currentDistance) Color.parseColor("#2196F3") else Color.parseColor("#333333"))
                isClickable = true
                isFocusable = true

                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 8)
                }
            }

            val optionText = TextView(this).apply {
                text = label
                textSize = 16f
                setTextColor(Color.WHITE)
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            optionView.addView(optionText)

            val checkIcon = TextView(this).apply {
                text = if (distance == currentDistance) "✓" else ""
                textSize = 16f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            optionView.addView(checkIcon)

            optionView.setOnClickListener {
                selectedDistance = distance

                for (i in optionViews.indices) {
                    val view = optionViews[i]
                    val isSelected = options[i].second == distance

                    view.setBackgroundColor(
                        if (isSelected) Color.parseColor("#2196F3")
                        else Color.parseColor("#333333")
                    )

                    val checkView = view.getChildAt(1) as TextView
                    checkView.text = if (isSelected) "✓" else ""
                }
            }

            optionViews.add(optionView)
            dialogLayout.addView(optionView)
        }

        val buttonLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 32, 0, 0)
            gravity = android.view.Gravity.CENTER
        }

        val btnCancel = Button(this).apply {
            text = if (isSpanish) "CANCELAR" else "CANCEL"
            setTextColor(Color.parseColor("#888888"))
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 16, 0)
            }
        }

        val btnSave = Button(this).apply {
            text = if (isSpanish) "GUARDAR" else "SAVE"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setPadding(32, 16, 32, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        buttonLayout.addView(btnCancel)
        buttonLayout.addView(btnSave)
        dialogLayout.addView(buttonLayout)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            saveAlertDistance(selectedDistance)
            dialog.dismiss()

            val distanceText = getAlertDistanceText(selectedDistance)
            val confirmMessage = if (isSpanish)
                "Distancia de alerta establecida en: $distanceText"
            else
                "Alert distance set to: $distanceText"
            showStatusCard(confirmMessage, isLoading = false)

            Handler(Looper.getMainLooper()).postDelayed({
                hideStatusCard()
            }, 3000)
        }

        dialog.show()
    }

    private fun showUserGuideDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val content = if (isSpanish) {
            """
            Cómo Usar DerriteICE
            
            REPORTAR
            • Presiona y mantén presionado en cualquier lugar del mapa para crear un reporte de seguridad
            • Agrega una descripción de la situación
            • Opcionalmente agrega una foto (los metadatos se eliminan automáticamente)
            • Los reportes son completamente anónimos y expiran después de 8 horas
            
            ALERTAS
            • Recibe notificaciones cuando se hagan reportes dentro de tu distancia elegida
            • Personaliza la distancia de alerta: 1 milla, 2 millas, 3 millas, 5 millas, código postal, o todo el estado
            • Cambia la distancia de alerta en Configuración → Preferencias → Distancia de Alerta
            • Toca el botón de alertas para ver reportes cercanos
            • Los reportes aparecen como círculos rojos en el mapa
            
            MAPA SATELITAL
            • La aplicación usa imágenes satelitales para mejor contexto de ubicación
            • La vista aérea ayuda a identificar puntos de referencia y terreno
            • Mejor precisión para señalar ubicaciones exactas
            
            TRADUCCIÓN
            • La aplicación funciona en español e inglés
            • Toca el botón de idioma para cambiar entre idiomas
            • Los reportes pueden ser traducidos tocando el botón traducir
            
            PRIVACIDAD
            • No se requieren cuentas - completamente anónimo
            • Los datos de ubicación se convierten en zonas anónimas
            • Todos los datos expiran automáticamente después de 8 horas
            • Las fotos tienen metadatos eliminados para privacidad
            
            UBICACIÓN
            • Toca el botón de ubicación para centrar en tu posición
            • Usa la barra de búsqueda para encontrar direcciones específicas
            • Presiona y mantén presionado para reportar incidentes en cualquier ubicación
            
            CONFIGURACIÓN
            • Accede a Configuración para personalizar la distancia de alerta
            • Ve preguntas frecuentes, guía del usuario, y política de privacidad
            • Verifica el estado de conexión del servidor
        """.trimIndent()
        } else {
            """
            How to Use DerriteICE
            
            REPORTING
            • Long press anywhere on the map to create a safety report
            • Add a description of the situation
            • Optionally add a photo (metadata is automatically removed)
            • Reports are completely anonymous and expire after 8 hours
            
            ALERTS  
            • Get notified when reports are made within your chosen distance
            • Customize alert distance: 1 mile, 2 miles, 3 miles, 5 miles, zip code, or state-wide
            • Change alert distance in Settings → Preferences → Alert Distance
            • Tap the alert button to see nearby reports
            • Reports appear as red circles on the map
            
            SATELLITE MAP
            • App uses satellite imagery for better location context
            • Aerial view helps identify landmarks and terrain
            • Better precision for pinpointing exact locations
            
            TRANSLATION
            • App works in Spanish and English
            • Tap language button to switch between languages
            • Reports can be translated by tapping the translate button
            
            PRIVACY
            • No accounts required - completely anonymous
            • Location data is converted to anonymous zones
            • All data expires automatically after 8 hours
            • Photos have metadata stripped for privacy
            
            LOCATION
            • Tap the location button to center on your position
            • Use the search bar to find specific addresses
            • Long press to report incidents at any location
            
            SETTINGS
            • Access Settings to customize alert distance
            • View FAQs, user guide, and privacy policy
            • Check backend connection status
        """.trimIndent()
        }

        showInfoDialog(if (isSpanish) "Guía del Usuario" else "User Guide", content)
    }

    private fun showFAQDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val content = if (isSpanish) {
            """
            Preguntas Frecuentes
            
            P: ¿Es esta aplicación anónima?
            R: Sí, completamente. Sin cuentas, sin rastreo, sin datos personales almacenados.
            
            P: ¿Cuánto tiempo duran los reportes?
            R: Todos los reportes expiran automáticamente y se eliminan después de 8 horas.
            
            P: ¿Puedo personalizar qué tan lejos vienen las alertas?
            R: ¡Sí! Ve a Configuración → Preferencias → Distancia de Alerta para elegir entre 1 milla, 2 millas, 3 millas, 5 millas, área de código postal, o todo el estado.
            
            P: ¿Pueden otros ver mi ubicación?
            R: No. Solo se usan zonas aproximadas (radio de 500m), nunca ubicaciones exactas.
            
            P: ¿Esto agota mi batería?
            R: No. La aplicación está optimizada para uso mínimo de batería.
            
            P: ¿Qué pasa con las fotos que subo?
            R: Todos los metadatos se eliminan antes de subir para completo anonimato.
            
            P: ¿Puedo usar esto sin internet?
            R: Sí, la aplicación funciona sin conexión. Los reportes se sincronizan cuando regresa la conexión.
            
            P: ¿Es esta aplicación gratuita?
            R: Sí, completamente gratuita sin anuncios o compras dentro de la aplicación.
            
            P: ¿Qué idiomas están soportados?
            R: Actualmente español e inglés, con traducción automática.
            
            P: ¿Qué tan precisas son las alertas?
            R: ¡Tú controlas la precisión! Establece alertas para 1 milla para incidentes muy locales, o todo el estado para mayor conciencia.
            
            P: ¿Pueden las autoridades rastrearme?
            R: No. El sistema está diseñado para ser completamente imposible de rastrear.
        """.trimIndent()
        } else {
            """
            Frequently Asked Questions
            
            Q: Is this app anonymous?
            A: Yes, completely. No accounts, no tracking, no personal data stored.
            
            Q: How long do reports last?
            A: All reports automatically expire and delete after 8 hours.
            
            Q: Can I customize how far away alerts come from?
            A: Yes! Go to Settings → Preferences → Alert Distance to choose from 1 mile, 2 miles, 3 miles, 5 miles, zip code area, or state-wide alerts.
            
            Q: Can others see my location?
            A: No. Only approximate zones (500m radius) are used, never exact locations.
            
            Q: Does this drain my battery?
            A: No. The app is optimized for minimal battery usage.
            
            Q: What happens to photos I upload?
            A: All metadata is stripped before upload for complete anonymity.
            
            Q: Can I use this without internet?
            A: Yes, the app works offline. Reports sync when connection returns.
            
            Q: Is this app free?
            A: Yes, completely free with no ads or in-app purchases.
            
            Q: Which languages are supported?
            A: Currently Spanish and English, with automatic translation.
            
            Q: How accurate are the alerts?
            A: You control the accuracy! Set alerts for 1 mile for very local incidents, or state-wide for broader awareness.
            
            Q: Can law enforcement track me?
            A: No. The system is designed to be completely untraceable.
        """.trimIndent()
        }

        showInfoDialog(if (isSpanish) "Preguntas Frecuentes" else "FAQ", content)
    }

    private fun showPreferencesDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(this).apply {
            text = if (isSpanish) "Preferencias de Usuario" else "User Preferences"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val currentLang = if (getSavedLanguage() == "es") {
            if (isSpanish) "Español" else "Spanish"
        } else {
            if (isSpanish) "Inglés" else "English"
        }

        addPreferenceSection(dialogLayout,
            if (isSpanish) "Idioma" else "Language",
            if (isSpanish) "Actual: $currentLang" else "Current: $currentLang"
        )

        val currentAlertDistance = getAlertDistanceText(getSavedAlertDistance())
        addPreferenceSection(dialogLayout,
            if (isSpanish) "Distancia de Alerta" else "Alert Distance",
            if (isSpanish)
                "Recibir alertas de reportes dentro de: $currentAlertDistance"
            else
                "Get alerts for reports within: $currentAlertDistance"
        ) {
            showAlertDistanceDialog()
        }

        addPreferenceSection(dialogLayout,
            if (isSpanish) "Servicios de Ubicación" else "Location Services",
            if (isSpanish) "Requerido para alertas de seguridad" else "Required for safety alerts"
        )

        addPreferenceSection(dialogLayout,
            if (isSpanish) "Nivel de Privacidad" else "Privacy Level",
            if (isSpanish) "Máximo - Anónimo y auto-eliminación" else "Maximum - Anonymous & auto-delete"
        )

        addPreferenceSection(dialogLayout,
            if (isSpanish) "Modo Sin Conexión" else "Offline Mode",
            if (isSpanish) "Habilitado - Sincroniza al conectarse" else "Enabled - Syncs when online"
        )

        addPreferenceSection(dialogLayout,
            if (isSpanish) "Actualización en Segundo Plano" else "Background Refresh",
            if (isSpanish) "Habilitado para alertas en tiempo real" else "Enabled for real-time alerts"
        )

        val btnClose = Button(this).apply {
            text = if (isSpanish) "CERRAR" else "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 32, 0, 0)
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun addPreferenceSection(container: LinearLayout, title: String, description: String, onClick: (() -> Unit)? = null) {
        val sectionView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 20, 24, 20)
            setBackgroundColor(if (onClick != null) Color.parseColor("#333333") else Color.parseColor("#222222"))

            if (onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    animateButtonPress(this)
                    onClick()
                }
            }

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 12)
            }
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        sectionView.addView(titleText)

        val descriptionText = TextView(this).apply {
            text = description
            textSize = 14f
            setTextColor(if (onClick != null) Color.parseColor("#CCCCCC") else Color.parseColor("#888888"))
            setPadding(0, 6, 0, 0)
        }
        sectionView.addView(descriptionText)

        if (onClick != null) {
            val arrowText = TextView(this).apply {
                text = ">"
                textSize = 12f
                setTextColor(Color.parseColor("#888888"))
                gravity = android.view.Gravity.END
                setPadding(0, 0, 0, 0)
            }
            sectionView.addView(arrowText)
        }

        container.addView(sectionView)
    }

    private fun showPrivacyPolicyDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val content = if (isSpanish) {
            """
            Política de Privacidad
            
            ANÓNIMO POR DISEÑO
            • No se requieren cuentas de usuario o registro
            • No se recopila ni almacena información personal
            • Sin seguimiento o analíticas de ningún tipo
            
            PRIVACIDAD DE UBICACIÓN
            • Las coordenadas GPS exactas nunca se almacenan
            • Solo se usan zonas anónimas de 500m
            • Los datos de ubicación expiran después de 8 horas
            
            PRIVACIDAD DE FOTOS
            • Todos los metadatos se eliminan automáticamente
            • No se preservan datos EXIF o información de ubicación
            • Las imágenes se procesan de forma anónima
            
            ALMACENAMIENTO DE DATOS
            • Todos los reportes se auto-eliminan después de 8 horas
            • No hay almacenamiento permanente de datos de usuario
            • Datos locales de la aplicación solo para funcionalidad sin conexión
            
            PRIVACIDAD DE RED
            • Todas las conexiones usan cifrado HTTPS
            • Enrutamiento Tor opcional para anonimato mejorado
            • No se registran direcciones IP en los servidores
            
            PROTECCIÓN LEGAL
            • No hay datos para subpoena o solicitud
            • El sistema anónimo proporciona protección legal
            • No se puede obligar a identificar usuarios
            
            Esta aplicación sigue principios de privacidad por diseño.
            Tu seguridad y privacidad son nuestras principales prioridades.
            """.trimIndent()
        } else {
            """
            Privacy Policy
            
            ANONYMOUS BY DESIGN
            • No user accounts or registration required
            • No personal information collected or stored
            • No tracking or analytics of any kind
            
            LOCATION PRIVACY
            • Exact GPS coordinates never stored
            • Only anonymous 500m zones are used
            • Location data expires after 8 hours
            
            PHOTO PRIVACY
            • All metadata automatically stripped
            • No EXIF data or location info preserved
            • Images processed anonymously
            
            DATA STORAGE
            • All reports auto-delete after 8 hours
            • No permanent storage of any user data
            • Local app data only for offline functionality
            
            NETWORK PRIVACY
            • All connections use HTTPS encryption
            • Optional Tor routing for enhanced anonymity
            • No IP address logging on servers
            
            LEGAL PROTECTION
            • No data to subpoena or request
            • Anonymous system provides legal protection
            • Cannot be compelled to identify users
            
            This app follows privacy-by-design principles.
            Your safety and privacy are our top priorities.
            """.trimIndent()
        }

        showInfoDialog(if (isSpanish) "Política de Privacidad" else "Privacy Policy", content)
    }

    private fun showAboutDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val content = if (isSpanish) {
            """
            Acerca de DerriteICE
            
            MISIÓN
            DerriteICE es una plataforma de reportes de seguridad anónima y privada, diseñada para ayudar a las comunidades a mantenerse informadas sobre problemas de seguridad locales.
            
            CARACTERÍSTICAS
            • Reportes de seguridad anónimos
            • Alertas en tiempo real para incidentes cercanos
            • Multiplataforma (Android, iOS, Web)
            • Soporte bilingüe (Español/Inglés)
            • Traducción automática
            • Protección completa de privacidad
            
            TECNOLOGÍA
            • Infraestructura global en la nube
            • Cifrado de extremo a extremo
            • Zonas geográficas anónimas
            • Expiración automática de datos
            • Herramientas de privacidad de código abierto
            
            PRIVACIDAD PRIMERO
            Construido desde cero con la privacidad como principio fundamental. Nunca se almacenan, rastrean o comparten datos de usuario.
            
            ALCANCE GLOBAL
            Disponible en todo el mundo con soporte de idioma local y sensibilidad cultural.
            
            DIRIGIDO POR LA COMUNIDAD
            Creado para las comunidades, por las comunidades. Tu seguridad es nuestra prioridad.
            
            Servidor: Railway Cloud (Global)
            Versión: ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }}
            
            Hecho con cuidado para comunidades más seguras.
            """.trimIndent()
        } else {
            """
            About DerriteICE
            
            MISSION
            DerriteICE is a privacy-first, anonymous safety reporting platform designed to help communities stay informed about local safety concerns.
            
            FEATURES
            • Anonymous safety reporting
            • Real-time alerts for nearby incidents
            • Cross-platform (Android, iOS, Web)
            • Bilingual support (Spanish/English)
            • Automatic translation
            • Complete privacy protection
            
            TECHNOLOGY
            • Global cloud infrastructure
            • End-to-end encryption
            • Anonymous geographic zones
            • Automatic data expiration
            • Open-source privacy tools
            
            PRIVACY FIRST
            Built from the ground up with privacy as the core principle. No user data is ever stored, tracked, or shared.
            
            GLOBAL REACH
            Available worldwide with local language support and cultural sensitivity.
            
            COMMUNITY DRIVEN
            Created for communities, by communities. Your safety is our priority.
            
            Backend: Railway Cloud (Global)
            Version: ${try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "1.0.0" }}
            
            Made with care for safer communities.
            """.trimIndent()
        }

        showInfoDialog(if (isSpanish) "Acerca de DerriteICE" else "About DerriteICE", content)
    }

    private fun showInfoDialog(title: String, content: String) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 24)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                weight = 1f
            }
        }

        val contentText = TextView(this).apply {
            text = content
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.parseColor("#333333"))
            setLineSpacing(4f, 1f)
        }
        scrollView.addView(contentText)
        dialogLayout.addView(scrollView)

        val btnClose = Button(this).apply {
            text = if (getSavedLanguage() == "es") "CERRAR" else "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                setMargins(0, 24, 0, 0)
            }
        }
        dialogLayout.addView(btnClose)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun subscribeToAlertsForLocation(latitude: Double, longitude: Double) {
        backendClient.subscribeToAlerts(latitude, longitude) { success, message ->
            // Silent subscription
        }
    }

    private fun setupSearchBar() {
        btnSearch.setOnClickListener {
            animateButtonPress(btnSearch)
            val query = searchBar.text?.toString()?.trim()
            if (!query.isNullOrEmpty()) {
                searchAddress(query)
            } else {
                val message = if (getSavedLanguage() == "es")
                    "Por favor ingresa una dirección" else "Please enter an address"
                showStatusCard(message, isError = true)
            }
        }

        btnClearSearch.setOnClickListener {
            animateButtonPress(btnClearSearch)
            clearSearch()
        }

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

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
    }

    private fun searchAddress(query: String) {
        val message = if (getSavedLanguage() == "es")
            "Buscando dirección..." else "Searching for address..."
        showStatusCard(message, isLoading = true)

        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(searchBar.windowToken, 0)

        try {
            val addresses: List<Address>? = geocoder.getFromLocationName(query, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val location = GeoPoint(address.latitude, address.longitude)

                mapView.controller.animateTo(location, 16.0, 1000L)
                addSearchResultMarker(location, address)

                val foundAddress = getFormattedAddress(address)
                val foundMessage = if (getSavedLanguage() == "es")
                    "Encontrado: $foundAddress" else "Found: $foundAddress"
                showStatusCard(foundMessage, isLoading = false)

                Handler(Looper.getMainLooper()).postDelayed({
                    hideStatusCard()
                }, 4000)
            } else {
                val message = if (getSavedLanguage() == "es")
                    "Dirección no encontrada" else "Address not found"
                showStatusCard(message, isError = true)
            }
        } catch (e: IOException) {
            val message = if (getSavedLanguage() == "es")
                "Error de búsqueda" else "Search error"
            showStatusCard(message, isError = true)
        } catch (e: Exception) {
            val message = if (getSavedLanguage() == "es")
                "Error de búsqueda" else "Search error"
            showStatusCard(message, isError = true)
        }
    }

    private fun clearSearch() {
        searchBar.text?.clear()
        btnClearSearch.visibility = View.GONE

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
        try {
            if (!::mapView.isInitialized || mapView.repository == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addSearchResultMarker(location, address)
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
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_search_marker)
                title = "Search Result"
                snippet = getFormattedAddress(address)

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
            android.util.Log.e("DerriteICE", "Error adding search marker: ${e.message}")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addSearchResultMarker(location, address)
                } catch (retryE: Exception) {
                    // Give up if retry also fails
                }
            }, 1000)
        }
    }

    private fun setupMap() {
        try {
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

            setupMapLongPressListener()
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error setting up map: ${e.message}")
            try {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
            } catch (fallbackE: Exception) {
                android.util.Log.e("DerriteICE", "Fallback map setup also failed: ${fallbackE.message}")
            }
        }
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

    private fun autoLocateOnStartup() {
        val isLanguageChange = preferences.getBoolean("is_language_change", false)
        if (isLanguageChange) {
            preferences.edit().putBoolean("is_language_change", false).apply()
            if (hasLocationPermission()) {
                getCurrentLocationSilently(isStartup = true)
            }
        } else {
            if (hasLocationPermission()) {
                val message = if (getSavedLanguage() == "es")
                    "Encontrando tu ubicación..." else "Finding your location..."
                showStatusCard(message, isLoading = true)
                getCurrentLocationSilently(isStartup = true)
            } else {
                hideStatusCard()
            }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            checkForNewAlerts()
        }, 2000)
    }

    private fun getCurrentLocationSilently(isStartup: Boolean = false) {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                    val userLocation = GeoPoint(location.latitude, location.longitude)

                    if (isStartup && !hasInitialLocationSet) {
                        mapView.controller.setCenter(userLocation)
                        mapView.controller.setZoom(18.0)
                        addLocationMarker(userLocation)
                        hasInitialLocationSet = true
                        hideStatusCard()
                        checkForNewAlerts()
                        subscribeToAlertsForLocation(location.latitude, location.longitude)
                    } else {
                        mapView.controller.animateTo(userLocation, 18.0, 1000L)
                        addLocationMarker(userLocation)
                        showLocationAddress(location)
                        checkForNewAlerts()
                    }
                } else {
                    if (isStartup) {
                        hideStatusCard()
                    } else {
                        val message = if (getSavedLanguage() == "es")
                            "No se puede obtener ubicación" else "Unable to get location"
                        showStatusCard(message, isError = true)
                    }
                }
            }.addOnFailureListener {
                if (isStartup) {
                    hideStatusCard()
                } else {
                    val message = if (getSavedLanguage() == "es")
                        "Error de ubicación" else "Location error"
                    showStatusCard(message, isError = true)
                }
            }
        } catch (e: SecurityException) {
            if (!isStartup) {
                val message = if (getSavedLanguage() == "es")
                    "Se necesita permiso de ubicación" else "Location permission needed"
                showStatusCard(message, isError = true)
            }
        }
    }

    private fun showLocationAddress(location: Location) {
        try {
            val addresses: List<Address>? = geocoder.getFromLocation(location.latitude, location.longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val locationText = buildString {
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

                    if (isEmpty()) {
                        append("${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}")
                    }
                }

                val message = if (getSavedLanguage() == "es")
                    "Estás en: $locationText" else "You are at: $locationText"
                showStatusCard(message, isLoading = false)
            } else {
                val coords = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
                val message = if (getSavedLanguage() == "es")
                    "Estás en: $coords" else "You are at: $coords"
                showStatusCard(message, isLoading = false)
            }
        } catch (e: IOException) {
            val coords = "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
            val message = if (getSavedLanguage() == "es")
                "Estás en: $coords" else "You are at: $coords"
            showStatusCard(message, isLoading = false)
        }

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
        mapView.overlays.add(0, mapEventsOverlay)
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
        Handler(Looper.getMainLooper()).postDelayed({
            if (instructionOverlay.visibility == View.VISIBLE) {
                instructionOverlay.visibility = View.GONE
            }
        }, 10000)
    }

    private fun showReportConfirmDialog(location: GeoPoint) {
        val isSpanish = getSavedLanguage() == "es"

        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_report, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnYesReport = dialogView.findViewById<Button>(R.id.btn_yes_report)

        btnCancel.text = if (isSpanish) "Cancelar" else "Cancel"
        btnYesReport.text = if (isSpanish) "Sí, Reportar" else "Yes, Report"

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnYesReport.setOnClickListener {
            dialog.dismiss()
            showReportInputDialog(location)
        }

        dialog.show()
    }

    private fun showReportInputDialog(location: GeoPoint) {
        val isSpanish = getSavedLanguage() == "es"

        val dialogView = layoutInflater.inflate(R.layout.dialog_report_input, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        val editReportText = dialogView.findViewById<TextInputEditText>(R.id.edit_report_text)
        val btnAddPhoto = dialogView.findViewById<Button>(R.id.btn_add_photo)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btn_submit)

        editReportText.hint = if (isSpanish)
            "Describe la situación de seguridad..."
        else
            "Describe the safety situation..."

        btnAddPhoto.text = if (isSpanish) "Agregar Foto" else "Add Photo"
        btnCancel.text = if (isSpanish) "Cancelar" else "Cancel"
        btnSubmit.text = if (isSpanish) "Enviar Reporte" else "Submit Report"

        currentPhoto = null

        btnAddPhoto.setOnClickListener { showPhotoSelectionDialog() }
        btnCancel.setOnClickListener {
            currentPhoto = null
            dialog.dismiss()
        }
        btnSubmit.setOnClickListener {
            val reportText = editReportText.text?.toString()?.trim()
            if (reportText.isNullOrEmpty()) {
                val errorMsg = if (isSpanish)
                    "Por favor ingresa una descripción"
                else
                    "Please enter a description"
                showStatusCard(errorMsg, isError = true)
                return@setOnClickListener
            }

            createReport(location, reportText, currentPhoto)
            currentPhoto = null
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun createReport(location: GeoPoint, text: String, photo: Bitmap?) {
        if (!translatorInitialized) {
            val detectedLanguage = SimpleTranslator().detectLanguage(text)
            createReportWithLanguage(location, text, photo, detectedLanguage)
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val detectedLanguage = adaptiveTranslator.detectLanguage(text)
                createReportWithLanguage(location, text, photo, detectedLanguage)
            } catch (e: Exception) {
                val detectedLanguage = SimpleTranslator().detectLanguage(text)
                createReportWithLanguage(location, text, photo, detectedLanguage)
            }
        }
    }

    private fun createReportWithLanguage(location: GeoPoint, text: String, photo: Bitmap?, detectedLanguage: String) {
        val report = Report(
            id = UUID.randomUUID().toString(),
            location = location,
            originalText = text,
            originalLanguage = detectedLanguage,
            hasPhoto = photo != null,
            photo = photo,
            timestamp = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (REPORT_DURATION_HOURS * 60 * 60 * 1000)
        )

        activeReports.add(report)
        addReportToMap(report)
        saveReportsToPreferences()

        val message = if (getSavedLanguage() == "es") {
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
            hasPhoto = photo != null
        ) { success, message ->
            runOnUiThread {
                if (success) {
                    val successMessage = if (getSavedLanguage() == "es") {
                        "Enviado al servidor"
                    } else {
                        "Sent to server"
                    }
                    showStatusCard(successMessage, isLoading = false)
                    subscribeToAlertsForLocation(location.latitude, location.longitude)
                } else {
                    val errorMessage = if (getSavedLanguage() == "es") {
                        "Error de conexión"
                    } else {
                        "Connection error"
                    }
                    showStatusCard(errorMessage, isError = true)
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    hideStatusCard()
                }, 3000)
            }
        }
    }

    private fun addReportToMap(report: Report) {
        val circle = createReportCircle(report.location)
        mapView.overlays.add(circle)
        reportCircles.add(circle)

        val marker = Marker(mapView).apply {
            position = report.location
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_report_marker)
            title = "Safety Report"

            setOnMarkerClickListener { _, _ ->
                showReportViewDialog(report)
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

    private fun createReportCircle(center: GeoPoint): Polygon {
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
        circle.fillColor = Color.parseColor("#20FF0000")
        circle.strokeColor = Color.parseColor("#80FF0000")
        circle.strokeWidth = 3f

        return circle
    }

    private fun showReportViewDialog(report: Report) {
        val dialogLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(Color.BLACK)
        }

        val titleText = TextView(this).apply {
            text = "Safety Report"
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 32)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        dialogLayout.addView(titleText)

        val textReportContent = TextView(this).apply {
            text = report.originalText
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(16, 16, 16, 32)
            setBackgroundColor(Color.parseColor("#333333"))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        dialogLayout.addView(textReportContent)

        val btnTranslate = Button(this).apply {
            text = "TRANSLATE"
            textSize = 16f
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            setPadding(32, 24, 32, 24)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }
        dialogLayout.addView(btnTranslate)

        val textReportTime = TextView(this).apply {
            text = "Reported: ${getTimeAgo(report.timestamp)}"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 32)
        }
        dialogLayout.addView(textReportTime)

        val btnClose = Button(this).apply {
            text = "CLOSE"
            setTextColor(Color.RED)
            setBackgroundColor(Color.TRANSPARENT)

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
            }
        }
        dialogLayout.addView(btnClose)

        val currentLang = getSavedLanguage()
        var isTranslated = false
        var isTranslating = false

        btnTranslate.setOnClickListener {
            if (isTranslating) return@setOnClickListener

            if (!isTranslated) {
                isTranslating = true
                btnTranslate.text = "Translating..."
                btnTranslate.isEnabled = false

                intelligentTranslate(
                    report.originalText,
                    report.originalLanguage,
                    currentLang,
                    onSuccess = { translatedText ->
                        runOnUiThread {
                            textReportContent.text = translatedText
                            btnTranslate.text = "SHOW ORIGINAL"
                            btnTranslate.setBackgroundColor(Color.parseColor("#4CAF50"))
                            btnTranslate.isEnabled = true
                            isTranslated = true
                            isTranslating = false
                        }
                    },
                    onError = { error ->
                        runOnUiThread {
                            btnTranslate.text = "Translation Failed"
                            btnTranslate.setBackgroundColor(Color.RED)
                            btnTranslate.isEnabled = true
                            isTranslating = false

                            Handler(Looper.getMainLooper()).postDelayed({
                                btnTranslate.text = "TRANSLATE"
                                btnTranslate.setBackgroundColor(Color.parseColor("#2196F3"))
                            }, 2000)
                        }
                    }
                )
            } else {
                textReportContent.text = report.originalText
                btnTranslate.text = "TRANSLATE"
                btnTranslate.setBackgroundColor(Color.parseColor("#2196F3"))
                isTranslated = false
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(true)
            .create()

        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun startReportCleanupTimer() {
        try {
            val cleanupRunnable = object : Runnable {
                override fun run() {
                    try {
                        val currentTime = System.currentTimeMillis()
                        val expiredReports = activeReports.filter { it.expiresAt < currentTime }

                        if (expiredReports.isNotEmpty()) {
                            expiredReports.forEach { report ->
                                try {
                                    removeReportFromMap(report)
                                    activeReports.remove(report)
                                    activeAlerts.removeAll { it.report.id == report.id }
                                } catch (e: Exception) {
                                    // Skip this report if cleanup fails
                                }
                            }
                            saveReportsToPreferences()
                        }

                        val hasUnviewed = activeAlerts.any { !viewedAlertIds.contains(it.report.id) }
                        updateAlertsButtonColor(hasUnviewed)

                        Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                    } catch (e: Exception) {
                        Handler(Looper.getMainLooper()).postDelayed(this, 60 * 60 * 1000)
                    }
                }
            }

            Handler(Looper.getMainLooper()).postDelayed(cleanupRunnable, 60 * 60 * 1000)
        } catch (e: Exception) {
            // If timer setup fails, continue without cleanup timer
        }
    }

    private fun removeReportFromMap(report: Report) {
        try {
            if (!::mapView.isInitialized || mapView.repository == null) {
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
                    android.util.Log.e("DerriteICE", "Error removing marker: ${e.message}")
                }
            }

            if (reportCircles.isNotEmpty()) {
                try {
                    val circle = reportCircles.removeAt(0)
                    mapView.overlays.remove(circle)
                } catch (e: Exception) {
                    android.util.Log.e("DerriteICE", "Error removing circle: ${e.message}")
                }
            }

            mapView.invalidate()
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error in removeReportFromMap: ${e.message}")
        }
    }

    private fun getTimeAgo(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "$minutes minutes ago"
            hours < 24 -> "$hours hours ago"
            else -> "$days days ago"
        }
    }

    private fun showPhotoSelectionDialog() {
        val isSpanish = getSavedLanguage() == "es"

        val options = if (isSpanish) {
            arrayOf("Tomar Foto", "Elegir de Galería")
        } else {
            arrayOf("Take Photo", "Choose from Gallery")
        }

        AlertDialog.Builder(this)
            .setTitle(if (isSpanish) "Agregar Foto" else "Add Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermissionAndCapture()
                    1 -> photoPickerLauncher.launch("image/*")
                }
            }
            .setNegativeButton(if (isSpanish) "Cancelar" else "Cancel", null)
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

        val message = if (getSavedLanguage() == "es")
            "Encontrando tu ubicación..." else "Finding your location..."
        showStatusCard(message, isLoading = true)
        getCurrentLocationSilently(isStartup = false)
    }

    private fun addLocationMarker(location: GeoPoint) {
        try {
            if (!::mapView.isInitialized || mapView.repository == null) {
                Handler(Looper.getMainLooper()).postDelayed({
                    addLocationMarker(location)
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
                icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_location_pin)
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
            android.util.Log.e("DerriteICE", "Error adding location marker: ${e.message}")
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    addLocationMarker(location)
                } catch (retryE: Exception) {
                    // Give up if retry also fails
                }
            }, 1000)
        }
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
            val message = if (getSavedLanguage() == "es")
                "Se necesita permiso de ubicación" else "Location permission needed"
            showStatusCard(message, isError = true)
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
                    val message = if (getSavedLanguage() == "es")
                        "Se requiere permiso de ubicación" else "Location permission required"
                    showStatusCard(message, isError = true)
                }
            }
            CAMERA_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    cameraLauncher.launch(null)
                } else {
                    val message = if (getSavedLanguage() == "es")
                        "Se necesita permiso de cámara" else "Camera permission needed"
                    showStatusCard(message, isError = true)
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            super.onDestroy()

            if (::adaptiveTranslator.isInitialized) {
                adaptiveTranslator.cleanup()
            }
            currentTranslationJob?.cancel()

            mapView.onDetach()
            currentAlertsDialog?.dismiss()
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error in onDestroy: ${e.message}")
        }
    }

    override fun onPause() {
        try {
            super.onPause()
            mapView.onPause()
            System.gc()
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error in onPause: ${e.message}")
        }
    }

    override fun onResume() {
        try {
            super.onResume()
            mapView.onResume()
        } catch (e: Exception) {
            android.util.Log.e("DerriteICE", "Error in onResume: ${e.message}")
        }
    }
}