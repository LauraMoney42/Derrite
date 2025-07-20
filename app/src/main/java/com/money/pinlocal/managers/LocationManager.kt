// File: managers/LocationManager.kt
package com.money.pinlocal.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.osmdroid.util.GeoPoint
import java.io.IOException
import java.util.Locale
import kotlin.math.*

class LocationManager(private val context: Context) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var geocoder: Geocoder = Geocoder(context, Locale.getDefault())
    private var currentLocation: Location? = null
    private var locationCallback: LocationCallback? = null

    interface LocationListener {
        fun onLocationUpdate(location: Location)
        fun onLocationError(error: String)
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun getCurrentLocation(): Location? = currentLocation

    fun startLocationUpdates(listener: LocationListener) {
        try {
            if (!hasLocationPermission()) return

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                15000  // Reduced from 30000 for more frequent updates
            ).apply {
                setMinUpdateIntervalMillis(10000)  // Reduced from 15000
                setMaxUpdateDelayMillis(30000)  // Add max delay
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    try {
                        locationResult.lastLocation?.let { location ->
                            currentLocation = location
                            listener.onLocationUpdate(location)
                        }
                    } catch (e: Exception) {
                        listener.onLocationError("Error in location callback: ${e.message}")
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            listener.onLocationError("Permission denied for location updates")
        } catch (e: Exception) {
            listener.onLocationError("Error starting location updates: ${e.message}")
        }
    }

    fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    fun getLastLocation(callback: (Location?) -> Unit) {
        try {
            if (!hasLocationPermission()) {
                callback(null)
                return
            }

            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                currentLocation = location
                callback(location)
            }.addOnFailureListener {
                callback(null)
            }
        } catch (e: SecurityException) {
            callback(null)
        }
    }

    fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun formatDistance(distanceInMeters: Double): String {
        val distanceInFeet = distanceInMeters * 3.28084
        val distanceInMiles = distanceInMeters / 1609.0

        return when {
            distanceInMeters < 1609 -> "${distanceInFeet.roundToInt()} ft away"
            else -> "${distanceInMiles.format(1)} mi away"
        }
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)

    fun getLocationDescription(location: Location): String {
        return try {
            val addresses: List<Address>? = geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1
            )

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                buildString {
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
            } else {
                "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
            }
        } catch (e: IOException) {
            "${String.format("%.4f", location.latitude)}, ${String.format("%.4f", location.longitude)}"
        }
    }

    fun searchAddress(query: String): List<Address>? {
        return try {
            geocoder.getFromLocationName(query, 1)
        } catch (e: IOException) {
            null
        }
    }

    fun getFormattedAddress(address: Address): String {
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
}