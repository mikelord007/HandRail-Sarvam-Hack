package com.handrail.speech

/**
 * [displayName] is English and MUST stay English-only — it fills `%LANGUAGE%`
 * in both the narration and agent system prompts (see AssistActivity and
 * AgentLoop), and feeding a non-English name to the model there would change
 * agent behaviour for no UI benefit. [nativeName] and [latinName] are
 * additive, display-only fields for the language picker (e.g. "हिन्दी" / "Hindi").
 */
data class Language(val code: String, val displayName: String, val nativeName: String, val latinName: String)

/** Sarvam's supported Bulbul/Saaras language codes, in the design's onboarding order. */
object SupportedLanguages {
    val ALL = listOf(
        Language("en-IN", "English", nativeName = "English", latinName = "India"),
        Language("hi-IN", "Hindi", nativeName = "हिन्दी", latinName = "Hindi"),
        Language("kn-IN", "Kannada", nativeName = "ಕನ್ನಡ", latinName = "Kannada"),
        Language("ta-IN", "Tamil", nativeName = "தமிழ்", latinName = "Tamil"),
        Language("te-IN", "Telugu", nativeName = "తెలుగు", latinName = "Telugu"),
        Language("mr-IN", "Marathi", nativeName = "मराठी", latinName = "Marathi"),
        Language("bn-IN", "Bengali", nativeName = "বাংলা", latinName = "Bengali"),
        Language("gu-IN", "Gujarati", nativeName = "ગુજરાતી", latinName = "Gujarati"),
        Language("ml-IN", "Malayalam", nativeName = "മലയാളം", latinName = "Malayalam"),
        Language("pa-IN", "Punjabi", nativeName = "ਪੰਜਾਬੀ", latinName = "Punjabi"),
        Language("od-IN", "Odia", nativeName = "ଓଡ଼ିଆ", latinName = "Odia"),
    )

    /** Onboarding forces an explicit choice on first run; this is only the fallback for a corrupt/missing preference. */
    val DEFAULT = ALL.first { it.code == "en-IN" }
}
