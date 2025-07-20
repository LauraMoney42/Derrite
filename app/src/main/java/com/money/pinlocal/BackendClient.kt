// File: BackendClient.kt (Fixed with fetchReports method)
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
        photo: String? = null,  // ADD THIS PARAMETER
        category: ReportCategory,
        callback: (Boolean, String) -> Unit
    ) {
        try {
            val json = JSONObject().apply {
                put("lat", latitude)
                put("lng", longitude)
                put("content", content)
                put("language", language)
                put("hasPhoto", hasPhoto)
                put("category", category.code)
                if (hasPhoto && photo != null) {
                    put("photo", photo)  // ADD THIS LINE
                }
            }

            android.util.Log.d("BackendClient", "Submitting report with photo: $hasPhoto, photo data length: ${photo?.length ?: 0}")

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

    //  Fetch ALL reports globally
    fun fetchAllReports(callback: (Boolean, List<Report>, String) -> Unit) {
        try {
            android.util.Log.d("BackendClient", "🌍 Fetching ALL reports globally")

            val request = Request.Builder()
                .url("$BACKEND_URL/reports/all")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    android.util.Log.e("BackendClient", "❌ Network error fetching all reports: ${e.message}")
                    callback(false, emptyList(), "Network error: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val responseBody = response.body?.string() ?: "{}"

                            if (response.isSuccessful) {
                                val jsonResponse = JSONObject(responseBody)
                                val success = jsonResponse.optBoolean("success", false)

                                if (success) {
                                    val reportsArray = jsonResponse.optJSONArray("reports") ?: JSONArray()
                                    val reports = parseReportsFromJson(reportsArray)

                                    android.util.Log.d("BackendClient", "✅ Successfully parsed ${reports.size} reports globally")
                                    callback(true, reports, "Successfully fetched ${reports.size} reports")
                                } else {
                                    val errorMessage = jsonResponse.optString("message", "Unknown error")
                                    callback(false, emptyList(), errorMessage)
                                }
                            } else {
                                callback(false, emptyList(), "Server error: ${response.code}")
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("BackendClient", "❌ Error parsing all reports response: ${e.message}")
                            callback(false, emptyList(), "Failed to parse server response: ${e.message}")
                        }
                    }
                }
            })
        } catch (e: Exception) {
            callback(false, emptyList(), "Request creation failed: ${e.message}")
        }
    }
    // NEW: Parse reports from JSON array received from server
    private fun parseReportsFromJson(reportsArray: JSONArray): List<Report> {
        val reports = mutableListOf<Report>()

        try {
            for (i in 0 until reportsArray.length()) {
                try {
                    val reportJson = reportsArray.getJSONObject(i)

                    val id = reportJson.optString("id", UUID.randomUUID().toString())
                    val lat = reportJson.optDouble("lat", 0.0)
                    val lng = reportJson.optDouble("lng", 0.0)
                    val content = reportJson.optString("content", "")
                    val language = reportJson.optString("language", "en")
                    val hasPhoto = reportJson.optBoolean("hasPhoto", false)
                    val categoryCode = reportJson.optString("category", "safety")
                    val timestamp = reportJson.optLong("timestamp", System.currentTimeMillis())

                    // Parse category
                    val category = ReportCategory.values().find { it.code == categoryCode }
                        ?: ReportCategory.SAFETY

                    // Calculate expiration (8 hours from timestamp)
                    val expiresAt = timestamp + (8 * 60 * 60 * 1000)

                    // Only include non-expired reports
                    if (expiresAt > System.currentTimeMillis()) {
                        val report = Report(
                            id = id,
                            location = GeoPoint(lat, lng),
                            originalText = content,
                            originalLanguage = language,
                            hasPhoto = hasPhoto,
                            photo = null, // Photos are not downloaded, just indicated
                            timestamp = timestamp,
                            expiresAt = expiresAt,
                            category = category
                        )

                        reports.add(report)
                        android.util.Log.d("BackendClient", "📍 Parsed report: ${category.displayName} - ${content.take(30)}...")
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

        return reports
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