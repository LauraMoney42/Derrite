package com.garfunkel.derriteice.translation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import java.io.File

/**
 * Detects device capabilities to determine optimal translation strategy
 */
class DeviceCapabilityDetector(private val context: Context) {

    companion object {
        private const val TAG = "DeviceCapability" // Shortened from 24 to 16 characters

        // Minimum requirements for ML Kit
        private const val MIN_RAM_MB = 2048 // 2GB RAM
        private const val MIN_STORAGE_MB = 500 // 500MB free storage
        private const val MIN_API_LEVEL = 21 // Android 5.0

        // Performance tiers
        enum class DeviceClass {
            HIGH_END,    // Latest devices, plenty of resources
            MID_RANGE,   // Moderate resources, can handle ML Kit
            LOW_END      // Limited resources, keyword fallback only
        }

        enum class TranslationCapability {
            ML_KIT_FULL,        // Full ML Kit with downloadable models
            ML_KIT_BASIC,       // Basic ML Kit with smaller models
            KEYWORD_ONLY        // Fallback to keyword translation
        }
    }

    private val activityManager: ActivityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    /**
     * Comprehensive device capability assessment
     */
    fun assessCapabilities(): DeviceCapabilityReport {
        val report = DeviceCapabilityReport(
            ramMB = getAvailableRAM(),
            storageMB = getAvailableStorage(),
            apiLevel = Build.VERSION.SDK_INT,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            cpuCores = Runtime.getRuntime().availableProcessors(),
            isLowRamDevice = activityManager.isLowRamDevice,
            batteryOptimized = isBatteryOptimized()
        )

        report.deviceClass = classifyDevice(report)
        report.translationCapability = determineTranslationCapability(report)

        Log.i(TAG, "Device assessment: $report")
        return report
    }

    /**
     * Get available RAM in megabytes
     */
    private fun getAvailableRAM(): Long {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN -> {
                memInfo.totalMem / (1024 * 1024)
            }
            else -> {
                // Estimate for older devices
                when {
                    memInfo.availMem > 1024 * 1024 * 1024 -> 2048 // > 1GB available = ~2GB total
                    memInfo.availMem > 512 * 1024 * 1024 -> 1024  // > 512MB available = ~1GB total
                    else -> 512 // Assume low-end device
                }
            }
        }
    }

    /**
     * Get available storage in megabytes
     */
    private fun getAvailableStorage(): Long {
        return try {
            val stat = StatFs(context.filesDir.path)
            val availableBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize.toLong()
            }
            availableBytes / (1024 * 1024)
        } catch (e: Exception) {
            Log.w(TAG, "Could not determine storage", e)
            100 // Conservative estimate
        }
    }

    /**
     * Check if device is running in battery optimization mode
     */
    private fun isBatteryOptimized(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                powerManager.isPowerSaveMode
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Classify device into performance tiers
     */
    private fun classifyDevice(report: DeviceCapabilityReport): DeviceClass {
        return when {
            // High-end: Modern device with plenty of resources
            report.ramMB >= 4096 &&
                    report.apiLevel >= 26 &&
                    report.cpuCores >= 8 &&
                    !report.isLowRamDevice -> DeviceClass.HIGH_END

            // Mid-range: Decent resources, can handle ML Kit
            report.ramMB >= MIN_RAM_MB &&
                    report.apiLevel >= MIN_API_LEVEL &&
                    report.storageMB >= MIN_STORAGE_MB &&
                    !report.isLowRamDevice -> DeviceClass.MID_RANGE

            // Low-end: Limited resources (default case)
            else -> DeviceClass.LOW_END
        }
    }

    /**
     * Determine optimal translation capability based on device assessment
     */
    private fun determineTranslationCapability(report: DeviceCapabilityReport): TranslationCapability {
        return when (report.deviceClass) {
            DeviceClass.HIGH_END -> {
                if (report.storageMB >= 1000 && !report.batteryOptimized) {
                    TranslationCapability.ML_KIT_FULL
                } else {
                    TranslationCapability.ML_KIT_BASIC
                }
            }

            DeviceClass.MID_RANGE -> {
                if (report.storageMB >= MIN_STORAGE_MB && report.ramMB >= 3072) {
                    TranslationCapability.ML_KIT_BASIC
                } else {
                    TranslationCapability.KEYWORD_ONLY
                }
            }

            DeviceClass.LOW_END -> TranslationCapability.KEYWORD_ONLY

            null -> TranslationCapability.KEYWORD_ONLY // Handle null case
        }
    }

    /**
     * Quick check if ML Kit is recommended for this device
     */
    fun isMLKitRecommended(): Boolean {
        val report = assessCapabilities()
        return report.translationCapability != TranslationCapability.KEYWORD_ONLY
    }

    /**
     * Check if device can handle model downloads
     */
    fun canDownloadModels(): Boolean {
        val report = assessCapabilities()
        return report.translationCapability == TranslationCapability.ML_KIT_FULL &&
                report.storageMB >= 1000 &&
                !report.batteryOptimized
    }
}

/**
 * Comprehensive device capability report
 */
data class DeviceCapabilityReport(
    val ramMB: Long,
    val storageMB: Long,
    val apiLevel: Int,
    val deviceModel: String,
    val cpuCores: Int,
    val isLowRamDevice: Boolean,
    val batteryOptimized: Boolean,
    var deviceClass: DeviceCapabilityDetector.Companion.DeviceClass? = null,
    var translationCapability: DeviceCapabilityDetector.Companion.TranslationCapability? = null
) {

    override fun toString(): String {
        return """
            DeviceCapabilityReport(
                device=$deviceModel,
                ram=${ramMB}MB,
                storage=${storageMB}MB,
                api=$apiLevel,
                cores=$cpuCores,
                lowRam=$isLowRamDevice,
                batteryOpt=$batteryOptimized,
                class=$deviceClass,
                translation=$translationCapability
            )
        """.trimIndent()
    }

    /**
     * Human-readable device summary
     */
    fun getSummary(): String {
        return when (deviceClass) {
            DeviceCapabilityDetector.Companion.DeviceClass.HIGH_END ->
                "High-end device with full ML Kit support"
            DeviceCapabilityDetector.Companion.DeviceClass.MID_RANGE ->
                "Mid-range device with basic ML Kit support"
            DeviceCapabilityDetector.Companion.DeviceClass.LOW_END ->
                "Entry-level device using keyword translation"
            null -> "Assessment pending"
        }
    }
}