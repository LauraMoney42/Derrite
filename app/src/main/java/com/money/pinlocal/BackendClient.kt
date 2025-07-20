// File: BackendClient.kt (Enhanced with Rate Limiting & Retry Logic)
package com.money.pinlocal

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import org.json.JSONArray
import com.money.pinlocal.data.ReportCategory
import com.money.pinlocal.data.Report
import org.osmdroid.util.GeoPoint
import java.util.UUID
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class BackendClient {
    companion object {
        private const val BACKEND_URL = "https://backend-production-cfbe.up.railway.app"
        private const val MAX_REQUESTS_PER_MINUTE = 10
        private const val RATE_LIMIT_WINDOW_MS = 60000L // 1 minute
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Rate limiting
    private val requestTimes = ConcurrentLinkedQueue<Long>()
    private val isProcessingQueue = AtomicBoolean(false)
    private val lastRequestTime = AtomicLong(0)
    private val requestQueue = ConcurrentLinkedQueue<QueuedRequest>()

    data class QueuedRequest(
        val request: Request,
        val callback: (Boolean, String) -> Unit,
        val retryCount: Int = 0,
        val requestType: String = "generic"
    )
    private fun parseReportsFromJson(responseBody: String): List<Report> {
        val reports = mutableListOf<Report>()

        try {
            android.util.Log.d("BackendClient", "📋 Parsing response body: ${responseBody.take(200)}...")

            val jsonResponse = JSONObject(responseBody)
            val success = jsonResponse.optBoolean("success", false)

            if (!success) {
                android.util.Log.w("BackendClient", "⚠️ Server returned success=false")
                return reports
            }

            val reportsArray = jsonResponse.optJSONArray("reports")
            if (reportsArray == null) {
                android.util.Log.w("BackendClient", "⚠️ No reports array in response")
                return reports
            }

            android.util.Log.d("BackendClient", "📊 Processing ${reportsArray.length()} reports from server")

            for (i in 0 until reportsArray.length()) {
                try {
                    val reportObj = reportsArray.getJSONObject(i)

                    val id = reportObj.optString("id", UUID.randomUUID().toString())
                    val lat = reportObj.optDouble("lat", 0.0)
                    val lng = reportObj.optDouble("lng", 0.0)
                    val content = reportObj.optString("content", "")
                    val language = reportObj.optString("language", "en")
                    val hasPhoto = reportObj.optBoolean("hasPhoto", false)
                    val timestamp = reportObj.optLong("timestamp", System.currentTimeMillis())

                    val categoryCode = reportObj.optString("category", "safety")
                    val category = ReportCategory.values().find { it.code == categoryCode }
                        ?: ReportCategory.SAFETY

                    // Calculate expiration (8 hours from timestamp)
                    val expiresAt = timestamp + (8 * 60 * 60 * 1000)

                    // Convert photo from base64 to bitmap if present
                    var photoBitmap: android.graphics.Bitmap? = null
                    if (hasPhoto) {
                        try {
                            val photoData = reportObj.optString("photo", null)
                            if (!photoData.isNullOrEmpty()) {
                                // Remove the data:image/jpeg;base64, prefix if present
                                val base64Data = if (photoData.startsWith("data:image")) {
                                    photoData.substring(photoData.indexOf(",") + 1)
                                } else {
                                    photoData
                                }

                                // Decode base64 to byte array
                                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.NO_WRAP)

                                // Convert byte array to bitmap
                                photoBitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                                if (photoBitmap != null) {
                                    android.util.Log.d("BackendClient", "✅ Successfully converted photo for report $id")
                                } else {
                                    android.util.Log.w("BackendClient", "⚠️ Failed to decode photo bitmap for report $id")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BackendClient", "❌ Error converting photo for report $id: ${e.message}")
                            photoBitmap = null
                        }
                    }

                    // Only include non-expired reports
                    if (expiresAt > System.currentTimeMillis()) {
                        val report = Report(
                            id = id,
                            location = GeoPoint(lat, lng),
                            originalText = content,
                            originalLanguage = language,
                            hasPhoto = hasPhoto,
                            photo = photoBitmap, // Now properly converted from base64!
                            timestamp = timestamp,
                            expiresAt = expiresAt,
                            category = category
                        )

                        reports.add(report)
                        android.util.Log.d("BackendClient", "📍 Parsed report: ${category.displayName} - ${content.take(30)}... [Photo: ${if (photoBitmap != null) "Yes" else "No"}]")
                    } else {
                        android.util.Log.d("BackendClient", "⏰ Skipping expired report: $id")
                    }

                } catch (e: Exception) {
                    android.util.Log.e("BackendClient", "❌ Error parsing individual report: ${e.message}")
                    continue
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackendClient", "❌ Error parsing reports array: ${e.message}")
        }

        android.util.Log.d("BackendClient", "✅ Successfully parsed ${reports.size} valid reports")
        return reports
    }
    // Rate limiting check
    private fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()

        // Remove old requests outside the window
        while (requestTimes.isNotEmpty() && (now - requestTimes.peek()) > RATE_LIMIT_WINDOW_MS) {
            requestTimes.poll()
        }

        // Check if we're under the limit
        if (requestTimes.size >= MAX_REQUESTS_PER_MINUTE) {
            android.util.Log.w("BackendClient", "🚫 Rate limit reached: ${requestTimes.size}/$MAX_REQUESTS_PER_MINUTE requests in last minute")
            return false
        }

        // Add minimum delay between requests (2 seconds)
        val timeSinceLastRequest = now - lastRequestTime.get()
        if (timeSinceLastRequest < 2000) {
            android.util.Log.w("BackendClient", "⏱️ Too frequent requests, need to wait ${2000 - timeSinceLastRequest}ms")
            return false
        }

        return true
    }

    private fun recordRequest() {
        val now = System.currentTimeMillis()
        requestTimes.offer(now)
        lastRequestTime.set(now)
    }

    private fun processRequestQueue() {
        if (!isProcessingQueue.compareAndSet(false, true)) {
            return // Already processing
        }

        fun processNext() {
            val queuedRequest = requestQueue.poll()
            if (queuedRequest == null) {
                isProcessingQueue.set(false)
                return
            }

            if (canMakeRequest()) {
                recordRequest()
                executeRequest(queuedRequest) {
                    // Continue processing queue after delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        processNext()
                    }, 2000) // 2 second delay between requests
                }
            } else {
                // Can't make request now, wait and try again
                requestQueue.offer(queuedRequest) // Put it back at the front
                Handler(Looper.getMainLooper()).postDelayed({
                    processNext()
                }, 5000) // Wait 5 seconds before retry
            }
        }

        processNext()
    }

    private fun executeRequest(queuedRequest: QueuedRequest, onComplete: () -> Unit) {
        android.util.Log.d("BackendClient", "📡 Executing ${queuedRequest.requestType} request (attempt ${queuedRequest.retryCount + 1})")

        client.newCall(queuedRequest.request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("BackendClient", "❌ Network error for ${queuedRequest.requestType}: ${e.message}")
                handleRequestFailure(queuedRequest, "Network error: ${e.message}", onComplete)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    when (response.code) {
                        200, 201 -> {
                            // Success
                            handleSuccessResponse(queuedRequest, response, onComplete)
                        }
                        429 -> {
                            // Rate limited
                            android.util.Log.w("BackendClient", "⚠️ Rate limited (429) for ${queuedRequest.requestType}")
                            handleRateLimit(queuedRequest, onComplete)
                        }
                        in 500..599 -> {
                            // Server error - retry
                            android.util.Log.w("BackendClient", "⚠️ Server error ${response.code} for ${queuedRequest.requestType}")
                            handleRetryableError(queuedRequest, "Server error: ${response.code}", onComplete)
                        }
                        else -> {
                            // Client error - don't retry
                            android.util.Log.e("BackendClient", "❌ Client error ${response.code} for ${queuedRequest.requestType}")
                            queuedRequest.callback(false, "Server error: ${response.code}")
                            onComplete()
                        }
                    }
                }
            }
        })
    }

    private fun handleSuccessResponse(queuedRequest: QueuedRequest, response: Response, onComplete: () -> Unit) {
        try {
            val responseBody = response.body?.string() ?: "{}"

            when (queuedRequest.requestType) {
                "submitReport" -> {
                    val jsonResponse = JSONObject(responseBody)
                    val success = jsonResponse.optBoolean("success", false)
                    val zone = jsonResponse.optString("zone", "unknown")
                    val message = if (success) "Report submitted to zone: $zone" else "Server rejected report"
                    queuedRequest.callback(success, message)
                }
                else -> {
                    // For fetchReports and other requests, just pass the response body
                    // The actual parsing is handled in the wrapped callback
                    queuedRequest.callback(true, responseBody)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BackendClient", "❌ Error parsing response for ${queuedRequest.requestType}: ${e.message}")
            queuedRequest.callback(false, "Invalid server response")
        }
        onComplete()
    }
    private fun handleRequestFailure(queuedRequest: QueuedRequest, errorMessage: String, onComplete: () -> Unit) {
        if (queuedRequest.retryCount < MAX_RETRIES) {
            // Retry with exponential backoff
            val retryDelay = INITIAL_RETRY_DELAY_MS * (1L shl queuedRequest.retryCount) // 2^retryCount
            android.util.Log.i("BackendClient", "🔄 Retrying ${queuedRequest.requestType} in ${retryDelay}ms (attempt ${queuedRequest.retryCount + 1}/$MAX_RETRIES)")

            Handler(Looper.getMainLooper()).postDelayed({
                val retryRequest = queuedRequest.copy(retryCount = queuedRequest.retryCount + 1)
                requestQueue.offer(retryRequest)
                onComplete()
            }, retryDelay)
        } else {
            android.util.Log.e("BackendClient", "❌ Max retries exceeded for ${queuedRequest.requestType}")
            queuedRequest.callback(false, errorMessage)
            onComplete()
        }
    }

    private fun handleRateLimit(queuedRequest: QueuedRequest, onComplete: () -> Unit) {
        // For rate limits, wait longer before retry
        val retryDelay = 10000L + (queuedRequest.retryCount * 5000L) // 10s, 15s, 20s...
        android.util.Log.i("BackendClient", "⏱️ Rate limited, retrying ${queuedRequest.requestType} in ${retryDelay}ms")

        Handler(Looper.getMainLooper()).postDelayed({
            val retryRequest = queuedRequest.copy(retryCount = queuedRequest.retryCount + 1)
            if (retryRequest.retryCount <= MAX_RETRIES) {
                requestQueue.offer(retryRequest)
            } else {
                queuedRequest.callback(false, "Rate limit exceeded - please try again later")
            }
            onComplete()
        }, retryDelay)
    }

    private fun handleRetryableError(queuedRequest: QueuedRequest, errorMessage: String, onComplete: () -> Unit) {
        if (queuedRequest.retryCount < MAX_RETRIES) {
            val retryDelay = INITIAL_RETRY_DELAY_MS * (1L shl queuedRequest.retryCount)
            android.util.Log.i("BackendClient", "🔄 Server error, retrying ${queuedRequest.requestType} in ${retryDelay}ms")

            Handler(Looper.getMainLooper()).postDelayed({
                val retryRequest = queuedRequest.copy(retryCount = queuedRequest.retryCount + 1)
                requestQueue.offer(retryRequest)
                onComplete()
            }, retryDelay)
        } else {
            queuedRequest.callback(false, errorMessage)
        }
        onComplete()
    }

    fun submitReport(
        latitude: Double,
        longitude: Double,
        content: String,
        language: String,
        hasPhoto: Boolean,
        photo: String? = null,
        category: ReportCategory,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            android.util.Log.d("BackendClient", "📝 Queuing report submission: ${category.displayName}")

            val json = JSONObject().apply {
                put("lat", latitude)
                put("lng", longitude)
                put("content", content)
                put("language", language)
                put("hasPhoto", hasPhoto)
                put("category", category.code)
                if (hasPhoto && photo != null) {
                    put("photo", photo)
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$BACKEND_URL/report")
                .post(requestBody)
                .build()

            val queuedRequest = QueuedRequest(request, callback, 0, "submitReport")
            requestQueue.offer(queuedRequest)
            processRequestQueue()

        } catch (e: Exception) {
            android.util.Log.e("BackendClient", "❌ Error creating report request: ${e.message}")
            callback(false, "Request creation failed: ${e.message}")
        }
    }


    fun fetchAllReports(callback: (Boolean, List<Report>, String) -> Unit) {
        try {
            android.util.Log.d("BackendClient", "🌍 Queuing fetch all reports request")

            val request = Request.Builder()
                .url("$BACKEND_URL/reports/all")
                .get()
                .build()

            // Wrap the callback to match our QueuedRequest type
            val wrappedCallback: (Boolean, String) -> Unit = { success, message ->
                android.util.Log.d("BackendClient", "📥 Wrapped callback called: success=$success, message length=${message.length}")
                if (success) {
                    try {
                        android.util.Log.d("BackendClient", "🔄 Starting to parse reports from JSON...")
                        val reports = parseReportsFromJson(message)
                        android.util.Log.d("BackendClient", "✅ Successfully parsed ${reports.size} reports, calling main callback")
                        callback(success, reports, "Success")
                    } catch (e: Exception) {
                        android.util.Log.e("BackendClient", "❌ Failed to parse reports: ${e.message}")
                        callback(false, emptyList(), "Failed to parse reports: ${e.message}")
                    }
                } else {
                    android.util.Log.w("BackendClient", "⚠️ Request failed, calling callback with error: $message")
                    callback(false, emptyList(), message)
                }
            }

            val queuedRequest = QueuedRequest(request, wrappedCallback, 0, "fetchReports")
            requestQueue.offer(queuedRequest)
            processRequestQueue()

        } catch (e: Exception) {
            android.util.Log.e("BackendClient", "❌ Error creating fetch request: ${e.message}")
            callback(false, emptyList(), "Request creation failed: ${e.message}")
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
                        put("fcmToken", token)
                    }

                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val requestBody = json.toString().toRequestBody(mediaType)

                    val request = Request.Builder()
                        .url("$BACKEND_URL/subscribe")
                        .post(requestBody)
                        .build()

                    val queuedRequest = QueuedRequest(request, callback, 0, "subscribeAlerts")
                    requestQueue.offer(queuedRequest)
                    processRequestQueue()

                } catch (e: Exception) {
                    callback(false, "Request creation failed: ${e.message}")
                }
            }
    }

    // Method to check current queue status (useful for debugging)
    fun getQueueStatus(): String {
        return "Queue size: ${requestQueue.size}, Processing: ${isProcessingQueue.get()}, Recent requests: ${requestTimes.size}"
    }

    // Method to clear the queue if needed (emergency)
    fun clearQueue() {
        requestQueue.clear()
        isProcessingQueue.set(false)
        android.util.Log.w("BackendClient", "🧹 Request queue cleared")
    }
}