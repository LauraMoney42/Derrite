package com.money.derrite.translation

/**
 * Simple keyword-based translator for safety-critical terms
 */
class SimpleTranslator {

    private val translations = mapOf(
        // Spanish to English
        "es_to_en" to mapOf(
            "policía" to "police",
            "ice" to "ICE",
            "migra" to "immigration",
            "redada" to "raid",
            "peligro" to "danger",
            "cuidado" to "be careful",
            "atención" to "attention",
            "agentes" to "agents",
            "ayuda" to "help",
            "emergencia" to "emergency",
            "aquí" to "here",
            "ahora" to "now",
            "muchos" to "many",
            "varios" to "several",
            "veo" to "I see",
            "hay" to "there are",
            "en" to "in/at",
            "cerca" to "near",
            "dentro" to "inside",
            "fuera" to "outside",
            "patrullas" to "patrol cars",
            "uniforme" to "uniform",
            "documentos" to "documents",
            "corriendo" to "running",
            "esconderse" to "hide",
            "rápido" to "quickly"
        ),

        // English to Spanish
        "en_to_es" to mapOf(
            "police" to "policía",
            "ice" to "ICE",
            "immigration" to "migración",
            "raid" to "redada",
            "danger" to "peligro",
            "careful" to "cuidado",
            "attention" to "atención",
            "agents" to "agentes",
            "help" to "ayuda",
            "emergency" to "emergencia",
            "here" to "aquí",
            "now" to "ahora",
            "many" to "muchos",
            "several" to "varios",
            "see" to "veo",
            "there are" to "hay",
            "in" to "en",
            "at" to "en",
            "near" to "cerca",
            "inside" to "dentro",
            "outside" to "fuera",
            "patrol" to "patrulla",
            "uniform" to "uniforme",
            "documents" to "documentos",
            "running" to "corriendo",
            "hide" to "esconderse",
            "quickly" to "rápido"
        )
    )

    fun translateText(text: String, fromLang: String, toLang: String): String {
        val translationKey = "${fromLang}_to_$toLang"
        val dictionary = translations[translationKey] ?: return text

        var result = text

        // Replace whole words (not partial matches)
        for ((original, translation) in dictionary) {
            val regex = "\\b${Regex.escape(original)}\\b".toRegex(RegexOption.IGNORE_CASE)
            result = regex.replace(result) { matchResult ->
                // Preserve original capitalization
                when {
                    matchResult.value.all { it.isUpperCase() } -> translation.uppercase()
                    matchResult.value.first().isUpperCase() -> translation.replaceFirstChar { it.uppercase() }
                    else -> translation
                }
            }
        }

        return result
    }

    fun detectLanguage(text: String): String {
        val spanishWords = setOf(
            "policía", "peligro", "ayuda", "ice", "migra", "redada",
            "cuidado", "atención", "agentes", "hay", "aquí", "en"
        )

        val englishWords = setOf(
            "police", "danger", "help", "ice", "immigration", "raid",
            "careful", "attention", "agents", "there", "here", "in"
        )

        val lowerText = text.lowercase()
        val spanishCount = spanishWords.count { lowerText.contains(it) }
        val englishCount = englishWords.count { lowerText.contains(it) }

        return when {
            spanishCount > englishCount -> "es"
            englishCount > spanishCount -> "en"
            else -> {
                // Default based on common patterns
                if (lowerText.contains("ción") || lowerText.contains("ñ")) "es" else "en"
            }
        }
    }
}