package com.money.pinlocal.translation

import android.content.Context
import android.util.Log

/**
 * Simple testing utility for the adaptive translation system
 */
class TranslationTestingUtility(private val context: Context) {

    companion object {
        private const val TAG = "TranslationTesting"
    }

    private val adaptiveTranslator = AdaptiveTranslator(context)
    private val simpleTranslator = SimpleTranslator()

    /**
     * Test data for basic translation testing
     */
    private val testPhrases = listOf(
        "policía aquí",
        "ICE agents nearby",
        "peligro en la escuela",
        "help needed now",
        "redada en el centro"
    )

    data class SimpleTestResult(
        val phrase: String,
        val detected: String,
        val translated: String,
        val method: String,
        val success: Boolean
    )

    /**
     * Run basic device capability test
     */
    fun runDeviceTest(): String {
        val detector = DeviceCapabilityDetector(context)
        val capabilities = detector.assessCapabilities()

        return buildString {
            appendLine("🔧 DEVICE TEST RESULTS")
            appendLine("=" * 30)
            appendLine("Model: ${capabilities.deviceModel}")
            appendLine("RAM: ${capabilities.ramMB}MB")
            appendLine("Storage: ${capabilities.storageMB}MB")
            appendLine("API Level: ${capabilities.apiLevel}")
            appendLine("Device Class: ${capabilities.deviceClass}")
            appendLine("Translation Method: ${capabilities.translationCapability}")
            appendLine("Summary: ${capabilities.getSummary()}")
        }
    }

    /**
     * Test keyword translation (synchronous)
     */
    fun testKeywordTranslation(): String {
        val results = mutableListOf<SimpleTestResult>()

        for (phrase in testPhrases) {
            try {
                val detected = simpleTranslator.detectLanguage(phrase)
                val targetLang = if (detected == "es") "en" else "es"
                val translated = simpleTranslator.translateText(phrase, detected, targetLang)

                results.add(SimpleTestResult(
                    phrase = phrase,
                    detected = detected,
                    translated = translated,
                    method = "Keyword",
                    success = translated != phrase
                ))
            } catch (e: Exception) {
                results.add(SimpleTestResult(
                    phrase = phrase,
                    detected = "error",
                    translated = "failed",
                    method = "Keyword",
                    success = false
                ))
            }
        }

        return formatTestResults(results)
    }

    /**
     * Test ML Kit availability (basic check)
     */
    fun testMLKitAvailability(): String {
        return try {
            // Try to create a basic translator to see if ML Kit is available
            val options = com.google.mlkit.nl.translate.TranslatorOptions.Builder()
                .setSourceLanguage(com.google.mlkit.nl.translate.TranslateLanguage.SPANISH)
                .setTargetLanguage(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH)
                .build()
            val translator = com.google.mlkit.nl.translate.Translation.getClient(options)

            "✅ ML Kit is available on this device"
        } catch (e: Exception) {
            "❌ ML Kit not available: ${e.message}"
        }
    }

    /**
     * Get translation capability recommendation
     */
    fun getRecommendation(): String {
        val detector = DeviceCapabilityDetector(context)
        val capabilities = detector.assessCapabilities()

        return when (capabilities.translationCapability) {
            DeviceCapabilityDetector.Companion.TranslationCapability.ML_KIT_FULL -> {
                "🤖 Recommendation: Your device supports full AI translation with model downloads. You'll get the best translation quality."
            }
            DeviceCapabilityDetector.Companion.TranslationCapability.ML_KIT_BASIC -> {
                "⚡ Recommendation: Your device supports smart AI translation. Good balance of quality and performance."
            }
            DeviceCapabilityDetector.Companion.TranslationCapability.KEYWORD_ONLY -> {
                "📝 Recommendation: Your device uses keyword translation. Optimized for safety terms and battery life."
            }
            null -> {
                "⚠️ Could not determine optimal translation method for your device."
            }
        }
    }

    /**
     * Format test results for display
     */
    private fun formatTestResults(results: List<SimpleTestResult>): String {
        return buildString {
            appendLine("📝 KEYWORD TRANSLATION TEST")
            appendLine("=" * 35)

            val successful = results.count { it.success }
            appendLine("Results: $successful/${results.size} successful")
            appendLine()

            for (result in results) {
                val status = if (result.success) "✅" else "❌"
                appendLine("$status '${result.phrase}'")
                appendLine("   Detected: ${result.detected}")
                appendLine("   Result: ${result.translated}")
                appendLine()
            }

            val accuracy = (successful.toFloat() / results.size * 100).toInt()
            appendLine("Overall accuracy: $accuracy%")
        }
    }

    /**
     * Run comprehensive test suite
     */
    fun runAllTests(): String {
        return buildString {
            appendLine(runDeviceTest())
            appendLine()
            appendLine(testMLKitAvailability())
            appendLine()
            appendLine(testKeywordTranslation())
            appendLine()
            appendLine(getRecommendation())
        }
    }

    /**
     * Simple benchmark test
     */
    fun runSimpleBenchmark(): String {
        val testPhrase = "policía aquí peligro"
        val iterations = 5
        val times = mutableListOf<Long>()

        repeat(iterations) {
            val startTime = System.currentTimeMillis()
            simpleTranslator.translateText(testPhrase, "es", "en")
            times.add(System.currentTimeMillis() - startTime)
        }

        val avgTime = times.average()
        return "⚡ Keyword translation benchmark: ${avgTime.toInt()}ms average"
    }

    /**
     * Clean up resources (minimal for this simple version)
     */
    fun cleanup() {
        Log.d(TAG, "Simple testing utility cleanup complete")
    }
}

/**
 * Extension function for string repetition
 */
private operator fun String.times(n: Int): String = this.repeat(n)