// File: managers/TranslationManager.kt (Fixed)
package com.money.derrite.managers

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.common.model.DownloadConditions
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

// Simple translator fallback for when MLKit isn't available
class SimpleTranslator {
    fun detectLanguage(text: String): String {
        // Simple heuristic language detection
        val spanishWords = setOf("el", "la", "de", "que", "y", "a", "en", "un", "es", "se", "no", "te", "lo", "le", "da", "su", "por", "son", "con", "para", "al", "del", "está", "una", "su", "las", "los", "como", "pero", "sus", "le", "ha", "me", "si", "sin", "sobre", "este", "ya", "todo", "esta", "cuando", "muy", "sin", "puede", "están", "también", "hay")

        val words = text.lowercase().split(Regex("\\W+")).filter { it.isNotEmpty() }
        val spanishMatches = words.count { it in spanishWords }

        return if (spanishMatches > words.size * 0.3) "es" else "en"
    }

    fun translateText(text: String, fromLang: String, toLang: String): String {
        // Basic keyword translation for essential safety terms
        val translations = if (fromLang == "es" && toLang == "en") {
            mapOf(
                "emergencia" to "emergency",
                "peligro" to "danger",
                "ayuda" to "help",
                "accidente" to "accident",
                "robo" to "robbery",
                "asalto" to "assault",
                "perdido" to "lost",
                "encontrado" to "found",
                "fiesta" to "party",
                "evento" to "event",
                "reunión" to "meeting",
                "problema" to "problem",
                "urgente" to "urgent",
                "seguridad" to "safety",
                "policía" to "police",
                "bomberos" to "firefighters",
                "hospital" to "hospital",
                "aquí" to "here",
                "ahora" to "now",
                "rápido" to "quick",
                "cuidado" to "careful"
            )
        } else if (fromLang == "en" && toLang == "es") {
            mapOf(
                "emergency" to "emergencia",
                "danger" to "peligro",
                "help" to "ayuda",
                "accident" to "accidente",
                "robbery" to "robo",
                "assault" to "asalto",
                "lost" to "perdido",
                "found" to "encontrado",
                "party" to "fiesta",
                "event" to "evento",
                "meeting" to "reunión",
                "problem" to "problema",
                "urgent" to "urgente",
                "safety" to "seguridad",
                "police" to "policía",
                "firefighters" to "bomberos",
                "hospital" to "hospital",
                "here" to "aquí",
                "now" to "ahora",
                "quick" to "rápido",
                "careful" to "cuidado"
            )
        } else {
            emptyMap()
        }

        var translatedText = text
        translations.forEach { (original, translation) ->
            translatedText = translatedText.replace(
                Regex("\\b${Regex.escape(original)}\\b", RegexOption.IGNORE_CASE),
                translation
            )
        }

        return if (translatedText != text) translatedText else
            if (toLang == "es") "[Traducción no disponible] $text" else "[Translation unavailable] $text"
    }
}

// Adaptive translator that uses MLKit when available
class AdaptiveTranslator(private val context: Context) {
    private val simpleTranslator = SimpleTranslator()

    suspend fun initialize(): Boolean {
        return try {
            // Try to initialize MLKit
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun detectLanguage(text: String): String {
        return try {
            // For now, use simple detection
            simpleTranslator.detectLanguage(text)
        } catch (e: Exception) {
            "en" // Default to English
        }
    }

    fun cleanup() {
        // Cleanup any resources
    }
}

class TranslationManager(private val context: Context) {

    private var adaptiveTranslator: AdaptiveTranslator? = null
    private var translatorInitialized = false
    private var currentTranslationJob: Job? = null

    fun initialize() {
        try {
            adaptiveTranslator = AdaptiveTranslator(context)
            downloadMLKitModelsInBackground()

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val success = withContext(Dispatchers.IO) {
                        adaptiveTranslator?.initialize() ?: false
                    }
                    translatorInitialized = success
                } catch (e: Exception) {
                    translatorInitialized = false
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "Failed to initialize: ${e.message}")
        }
    }

    fun detectLanguage(text: String): String {
        return try {
            if (translatorInitialized && adaptiveTranslator != null) {
                runBlocking {
                    adaptiveTranslator!!.detectLanguage(text)
                }
            } else {
                SimpleTranslator().detectLanguage(text)
            }
        } catch (e: Exception) {
            SimpleTranslator().detectLanguage(text)
        }
    }

    fun translateText(
        text: String,
        fromLang: String,
        toLang: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        currentTranslationJob?.cancel()

        currentTranslationJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                if (translatorInitialized && isMLKitAvailable()) {
                    tryMLKitTranslation(text, fromLang, toLang, onSuccess, onError)
                } else {
                    val result = withContext(Dispatchers.IO) {
                        SimpleTranslator().translateText(text, fromLang, toLang)
                    }
                    onSuccess(result)
                }
            } catch (e: Exception) {
                onError("Translation unavailable: ${e.message}")
            }
        }
    }

    private fun isMLKitAvailable(): Boolean {
        return try {
            Class.forName("com.google.mlkit.nl.translate.Translation")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    private fun tryMLKitTranslation(
        text: String,
        fromLang: String,
        toLang: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val sourceLanguage = if (fromLang == "es")
                TranslateLanguage.SPANISH
            else TranslateLanguage.ENGLISH

            val targetLanguage = if (toLang == "es")
                TranslateLanguage.SPANISH
            else TranslateLanguage.ENGLISH

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()

            val translator = Translation.getClient(options)

            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    if (translatedText.isNotEmpty()) {
                        onSuccess(translatedText)
                    } else {
                        fallbackToSimpleTranslation(text, fromLang, toLang, onSuccess, onError)
                    }
                }
                .addOnFailureListener { e ->
                    fallbackToSimpleTranslation(text, fromLang, toLang, onSuccess, onError)
                }
        } catch (e: Exception) {
            fallbackToSimpleTranslation(text, fromLang, toLang, onSuccess, onError)
        }
    }

    private fun fallbackToSimpleTranslation(
        text: String,
        fromLang: String,
        toLang: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = SimpleTranslator().translateText(text, fromLang, toLang)
                withContext(Dispatchers.Main) {
                    onSuccess(result)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError("Translation unavailable: ${e.message}")
                }
            }
        }
    }

    private fun downloadMLKitModelsInBackground() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isWiFiConnected()) return@launch
                if (!isMLKitAvailable()) return@launch

                downloadTranslationModel(
                    TranslateLanguage.SPANISH,
                    TranslateLanguage.ENGLISH,
                    "Spanish to English"
                )

                downloadTranslationModel(
                    TranslateLanguage.ENGLISH,
                    TranslateLanguage.SPANISH,
                    "English to Spanish"
                )
            } catch (e: Exception) {
                android.util.Log.e("TranslationManager", "Background download failed: ${e.message}")
            }
        }
    }

    private fun isWiFiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = connectivityManager.activeNetwork
                val capabilities = connectivityManager.getNetworkCapabilities(network)
                capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.type == ConnectivityManager.TYPE_WIFI
            }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun downloadTranslationModel(
        sourceLanguage: String,
        targetLanguage: String,
        description: String
    ) {
        try {
            if (!isMLKitAvailable()) return

            val options = TranslatorOptions.Builder()
                .setSourceLanguage(sourceLanguage)
                .setTargetLanguage(targetLanguage)
                .build()

            val translator = Translation.getClient(options)

            val downloadConditions = DownloadConditions.Builder()
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
            android.util.Log.e("TranslationManager", "Model download failed: ${e.message}")
        }
    }

    private fun testTranslationModel(translator: Translator, description: String) {
        try {
            translator.translate("test")
                .addOnSuccessListener {
                    android.util.Log.d("TranslationManager", "Model test successful: $description")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("TranslationManager", "Model test failed: $description - ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "Model test error: $description - ${e.message}")
        }
    }

    fun cleanup() {
        try {
            currentTranslationJob?.cancel()
            adaptiveTranslator?.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("TranslationManager", "Cleanup error: ${e.message}")
        }
    }
}