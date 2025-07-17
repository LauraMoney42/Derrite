package com.money.pinlocal.translation

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.Translator
import kotlinx.coroutines.*
import kotlin.coroutines.resume

/**
 * Adaptive translation system that intelligently chooses between ML Kit and keyword fallback
 */
class AdaptiveTranslator(private val context: Context) {

    companion object {
        private const val TAG = "AdaptiveTranslator"
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val TRANSLATION_TIMEOUT_MS = 10000L // 10 seconds
    }

    private val deviceCapabilityDetector = DeviceCapabilityDetector(context)
    private val keywordTranslator = SimpleTranslator() // Your existing translator
    private val modelManager = RemoteModelManager.getInstance()

    // ML Kit components
    private var spanishToEnglishTranslator: Translator? = null
    private var englishToSpanishTranslator: Translator? = null
    private val languageIdentifier = LanguageIdentification.getClient()

    // Device capabilities
    private var deviceCapabilities: DeviceCapabilityReport? = null
    private var isMLKitReady = false
    private var isInitializing = false

    // Translation callbacks
    interface TranslationCallback {
        fun onSuccess(translatedText: String, method: TranslationMethod, confidence: Float = 1.0f)
        fun onError(error: String, fallbackAvailable: Boolean = true)
        fun onProgress(status: String)
    }

    enum class TranslationMethod {
        ML_KIT_ONLINE,      // ML Kit with downloaded models
        ML_KIT_BASIC,       // ML Kit basic functionality
        KEYWORD_FALLBACK,   // Your existing keyword system
        HYBRID              // Combination of methods
    }

    /**
     * Initialize the adaptive translator
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitializing) {
            Log.d(TAG, "Already initializing...")
            return@withContext isMLKitReady
        }

        isInitializing = true

        try {
            // Assess device capabilities
            deviceCapabilities = deviceCapabilityDetector.assessCapabilities()
            Log.i(TAG, "Device capabilities: ${deviceCapabilities?.getSummary()}")

            // Initialize based on capabilities
            when (deviceCapabilities?.translationCapability) {
                DeviceCapabilityDetector.Companion.TranslationCapability.ML_KIT_FULL -> {
                    initializeMLKitFull()
                }
                DeviceCapabilityDetector.Companion.TranslationCapability.ML_KIT_BASIC -> {
                    initializeMLKitBasic()
                }
                DeviceCapabilityDetector.Companion.TranslationCapability.KEYWORD_ONLY -> {
                    Log.i(TAG, "Using keyword-only translation for this device")
                    isMLKitReady = false
                }
                null -> {
                    Log.w(TAG, "Could not determine device capabilities")
                    isMLKitReady = false
                }
            }

            Log.i(TAG, "Adaptive translator initialized. ML Kit ready: $isMLKitReady")
            return@withContext true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize adaptive translator", e)
            isMLKitReady = false
            return@withContext false
        } finally {
            isInitializing = false
        }
    }

    /**
     * Initialize ML Kit with full model downloading capability
     */
    private suspend fun initializeMLKitFull() = withContext(Dispatchers.IO) {
        try {
            // Create translators
            val esEnOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.SPANISH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            spanishToEnglishTranslator = Translation.getClient(esEnOptions)

            val enEsOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
            englishToSpanishTranslator = Translation.getClient(enEsOptions)

            // Try to download models if needed (don't fail if this doesn't work)
            try {
                val downloadConditions = DownloadConditions.Builder()
                    .requireWifi() // Only download on WiFi to save data
                    .build()

                // Download models asynchronously - don't wait for completion
                spanishToEnglishTranslator?.downloadModelIfNeeded(downloadConditions)
                englishToSpanishTranslator?.downloadModelIfNeeded(downloadConditions)

                Log.i(TAG, "ML Kit model download started")
            } catch (e: Exception) {
                Log.w(TAG, "Model download failed, but will continue with on-demand translation", e)
            }

            isMLKitReady = true
            Log.i(TAG, "ML Kit full initialization complete")

        } catch (e: Exception) {
            Log.w(TAG, "ML Kit full initialization failed, falling back to basic", e)
            initializeMLKitBasic()
        }
    }

    /**
     * Initialize ML Kit with basic functionality (no pre-downloaded models)
     */
    private suspend fun initializeMLKitBasic() = withContext(Dispatchers.IO) {
        try {
            // Create translators without downloading models
            val esEnOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.SPANISH)
                .setTargetLanguage(TranslateLanguage.ENGLISH)
                .build()
            spanishToEnglishTranslator = Translation.getClient(esEnOptions)

            val enEsOptions = TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build()
            englishToSpanishTranslator = Translation.getClient(enEsOptions)

            isMLKitReady = true
            Log.i(TAG, "ML Kit basic initialization complete")

        } catch (e: Exception) {
            Log.w(TAG, "ML Kit basic initialization failed", e)
            isMLKitReady = false
        }
    }

    /**
     * Translate text using the best available method
     */
    fun translateText(
        text: String,
        fromLanguage: String,
        toLanguage: String,
        callback: TranslationCallback
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            callback.onProgress("Analyzing text...")

            try {
                // First, try ML Kit if available
                if (isMLKitReady && canUseMLKit(fromLanguage, toLanguage)) {
                    translateWithMLKit(text, fromLanguage, toLanguage, callback)
                } else {
                    // Fall back to keyword translation
                    translateWithKeywords(text, fromLanguage, toLanguage, callback)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Translation failed", e)
                callback.onError("Translation failed: ${e.message}")
            }
        }
    }

    /**
     * Translate using ML Kit - Simplified version
     */
    private suspend fun translateWithMLKit(
        text: String,
        fromLanguage: String,
        toLanguage: String,
        callback: TranslationCallback
    ) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Translating with AI...")

            val translator = when {
                fromLanguage == "es" && toLanguage == "en" -> spanishToEnglishTranslator
                fromLanguage == "en" && toLanguage == "es" -> englishToSpanishTranslator
                else -> null
            }

            if (translator == null) {
                Log.w(TAG, "No ML Kit translator for $fromLanguage -> $toLanguage")
                translateWithKeywords(text, fromLanguage, toLanguage, callback)
                return@withContext
            }

            // Simple approach - use callbacks directly
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    val result = translatedText ?: ""
                    if (result.isNotEmpty()) {
                        val confidence = assessTranslationQuality(text, result, fromLanguage, toLanguage)
                        CoroutineScope(Dispatchers.Main).launch {
                            callback.onSuccess(result, TranslationMethod.ML_KIT_ONLINE, confidence)
                        }
                    } else {
                        // ML Kit returned empty, try keyword fallback
                        CoroutineScope(Dispatchers.Main).launch {
                            translateWithKeywords(text, fromLanguage, toLanguage, callback)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "ML Kit translation failed, falling back to keywords", e)
                    CoroutineScope(Dispatchers.Main).launch {
                        translateWithKeywords(text, fromLanguage, toLanguage, callback)
                    }
                }

        } catch (e: Exception) {
            Log.w(TAG, "ML Kit translation failed, falling back to keywords", e)
            translateWithKeywords(text, fromLanguage, toLanguage, callback)
        }
    }

    /**
     * Translate using keyword fallback
     */
    private suspend fun translateWithKeywords(
        text: String,
        fromLanguage: String,
        toLanguage: String,
        callback: TranslationCallback
    ) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Using keyword translation...")

            val result = keywordTranslator.translateText(text, fromLanguage, toLanguage)

            withContext(Dispatchers.Main) {
                callback.onSuccess(result, TranslationMethod.KEYWORD_FALLBACK)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Keyword translation failed", e)
            withContext(Dispatchers.Main) {
                callback.onError("Keyword translation failed: ${e.message}", false)
            }
        }
    }

    /**
     * Detect language using ML Kit or fallback to keyword detection
     */
    suspend fun detectLanguage(text: String): String = withContext(Dispatchers.IO) {
        if (isMLKitReady) {
            try {
                // Simple approach - use callback instead of coroutines
                var result = "und"
                val job = CompletableDeferred<String>()

                languageIdentifier.identifyLanguage(text)
                    .addOnSuccessListener { languageCode ->
                        job.complete(languageCode ?: "und")
                    }
                    .addOnFailureListener {
                        job.complete("und")
                    }

                // Wait for result with timeout
                result = withTimeoutOrNull(5000L) { job.await() } ?: "und"

                // ML Kit returns "und" for undetermined
                if (result != "und" && result.isNotEmpty()) {
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit language detection failed", e)
            }
        }

        // Fallback to keyword detection
        return@withContext keywordTranslator.detectLanguage(text)
    }

    /**
     * Check if ML Kit can handle this language pair
     */
    private fun canUseMLKit(fromLanguage: String, toLanguage: String): Boolean {
        return (fromLanguage == "es" && toLanguage == "en") ||
                (fromLanguage == "en" && toLanguage == "es")
    }

    /**
     * Assess translation quality based on various metrics
     */
    private fun assessTranslationQuality(
        original: String,
        translated: String,
        fromLang: String,
        toLang: String
    ): Float {
        // Simple quality assessment metrics
        var confidence = 1.0f

        // Check for obvious failures
        if (translated.isEmpty() || translated == original) {
            confidence -= 0.5f
        }

        // Check length ratio (shouldn't be too different)
        val lengthRatio = translated.length.toFloat() / original.length.toFloat()
        if (lengthRatio < 0.3f || lengthRatio > 3.0f) {
            confidence -= 0.3f
        }

        return confidence.coerceIn(0.0f, 1.0f)
    }

    /**
     * Get current translation capability
     */
    fun getTranslationCapability(): DeviceCapabilityDetector.Companion.TranslationCapability? {
        return deviceCapabilities?.translationCapability
    }

    /**
     * Get device capability summary
     */
    fun getDeviceSummary(): String {
        return deviceCapabilities?.getSummary() ?: "Assessment pending"
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            spanishToEnglishTranslator?.close()
            englishToSpanishTranslator?.close()
            languageIdentifier.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cleanup", e)
        }
    }
}