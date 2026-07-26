package com.handrail.agent

import com.handrail.perception.PerceptionResult

/**
 * Detects password/OTP/verification screens so the agent loop can hand
 * control back instead of attempting to read or enter credentials.
 */
object LoginWallDetector {

    private val KEYWORDS = listOf(
        "otp", "one-time password", "verify", "verification code",
        "ओटीपी", "सत्यापित करें", "पासवर्ड",
        "ಒಟಿಪಿ", "ಪರಿಶೀಲಿಸಿ", "ಪಾಸ್‌ವರ್ಡ್",
    )

    fun isLoginWall(perception: PerceptionResult): Boolean {
        if (perception.refMap.values.any { it.isPassword }) return true

        val lowerText = perception.serialized.lowercase()
        return KEYWORDS.any { lowerText.contains(it.lowercase()) }
    }
}
