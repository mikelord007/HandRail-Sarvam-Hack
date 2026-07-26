package com.handrail.speech

/**
 * Everything a Bulbul/agent call needs about how to speak — bundled so
 * [com.handrail.agent.AgentLoop] takes one parameter instead of four.
 */
data class VoiceSettings(
    val language: Language,
    val speaker: Speaker,
    /** Bulbul `pace`: 1.0 normal, ~0.65 for "Speak slowly". Valid API range is up to 3.0. */
    val pace: Float,
    val narrateEveryStep: Boolean,
) {
    companion object {
        const val PACE_NORMAL = 1.0f
        const val PACE_SLOW = 0.65f
    }
}
