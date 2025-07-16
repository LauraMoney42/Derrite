// File: BackendClient.kt
package com.money.derrite

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.google.firebase.messaging.FirebaseMessaging
import org.json.JSONObject
import com.money.derrite.data.ReportCategory

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