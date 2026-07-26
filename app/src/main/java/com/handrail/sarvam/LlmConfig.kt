package com.handrail.sarvam

import com.handrail.BuildConfig

enum class LlmProvider {
    OPENROUTER,
    SARVAM,
}

/**
 * Single switch for both agent-loop providers. Flip ACTIVE_PROVIDER to
 * SARVAM by Saturday 12:00 and never flip back — see CLAUDE.md.
 */
object LlmConfig {

    val ACTIVE_PROVIDER: LlmProvider = LlmProvider.OPENROUTER

    val baseUrl: String
        get() = when (ACTIVE_PROVIDER) {
            LlmProvider.OPENROUTER -> "https://openrouter.ai/api/v1/"
            LlmProvider.SARVAM -> "https://api.sarvam.ai/v1/"
        }

    val model: String
        get() = when (ACTIVE_PROVIDER) {
            LlmProvider.OPENROUTER -> "openai/gpt-4o-mini"
            LlmProvider.SARVAM -> "sarvam-105b"
        }

    val apiKey: String
        get() = when (ACTIVE_PROVIDER) {
            LlmProvider.OPENROUTER -> BuildConfig.OPENROUTER_API_KEY
            LlmProvider.SARVAM -> BuildConfig.SARVAM_API_KEY
        }
}
