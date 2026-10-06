package com.itmda3b33.deliveryapp.ai

/**
 * Spots "I want a human" so the app can hand over straight away, without waiting for Gemini.
 * The chat screen also has a "Talk to a person" button, which always works.
 */
object HumanRequest {

    private val patterns = listOf(
        Regex("""\b(human|real person|actual person|live person|someone real)\b"""),
        Regex("""\b(speak|talk|chat)\s+(to|with)\s+(a|an|some)?\s*(one|person|agent|someone|manager|consultant)\b"""),
        Regex("""\b(an?|your|the)\s+agent\b"""),
        Regex("""\b(manager|supervisor|operator)\b"""),
    )

    fun matches(message: String): Boolean {
        val text = message.lowercase()
        return patterns.any { it.containsMatchIn(text) }
    }
}
