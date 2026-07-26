package com.handrail.actions

/**
 * Belt-and-suspenders check before any tap: block targets whose visible text
 * suggests a payment, send, or other irreversible action, regardless of what
 * the model was told in its system prompt. This is the real guard — the
 * prompt-level instruction is backup, not the other way around.
 *
 * English keywords match on WORD BOUNDARIES. A plain `contains` matched
 * "pay" inside "Paytm", "Repay", and "Payments" — meaning tapping the Paytm
 * app icon, or a "Repayment history" row, tripped the guard. Indic-script
 * keywords still use substring matching: Kotlin's `Regex` word-boundary
 * (`\b`) is defined in terms of ASCII word characters and doesn't reliably
 * bound Devanagari/Kannada/etc. runs, so `\b` around those scripts would
 * either never match or match in the wrong place.
 */
object IrreversibleActionGuard {

    // English — matched with \b on both sides, case-insensitive.
    private val LATIN_KEYWORDS = listOf(
        "pay", "send", "confirm", "order", "submit", "transfer", "buy",
    )

    // Indic scripts — matched as plain substrings (see class doc).
    private val SCRIPT_KEYWORDS = listOf(
        // Hindi
        "भुगतान", "भेजें", "पुष्टि", "ऑर्डर", "सबमिट", "जमा करें", "ट्रांसफर", "स्थानांतरण", "खरीदें", "खरीदारी",
        // Kannada
        "ಪಾವತಿ", "ಕಳುಹಿಸಿ", "ದೃಢೀಕರಿಸಿ", "ಆರ್ಡರ್", "ಸಲ್ಲಿಸಿ", "ವರ್ಗಾವಣೆ", "ಖರೀದಿ",
        // Tamil
        "செலுத்து", "பணம் செலுத்து", "அனுப்பு", "உறுதிப்படுத்து", "ஆர்டர்", "சமர்ப்பி", "பரிமாறு", "வாங்கு",
        // Telugu
        "చెల్లించు", "పంపండి", "పంపు", "నిర్ధారించండి", "ఆర్డర్", "సమర్పించండి", "బదిలీ", "కొనుగోలు", "కొను",
        // Marathi
        "भरणा", "पैसे भरा", "पाठवा", "पुष्टी करा", "ऑर्डर", "सबमिट करा", "जमा करा", "हस्तांतरण", "खरेदी करा",
        // Bengali
        "পরিশোধ", "পেমেন্ট", "পাঠান", "নিশ্চিত করুন", "অর্ডার", "জমা দিন", "স্থানান্তর", "কিনুন",
        // Gujarati
        "ચુકવણી", "ચૂકવો", "મોકલો", "પુષ્ટિ કરો", "ઓર્ડર", "સબમિટ કરો", "ટ્રાન્સફર", "ખરીદો",
        // Malayalam
        "അടയ്ക്കുക", "പണമടയ്ക്കുക", "അയയ്ക്കുക", "സ്ഥിരീകരിക്കുക", "ഓർഡർ", "സമർപ്പിക്കുക", "കൈമാറ്റം", "വാങ്ങുക",
        // Punjabi
        "ਭੁਗਤਾਨ", "ਭੇਜੋ", "ਪੁਸ਼ਟੀ ਕਰੋ", "ਆਰਡਰ", "ਜਮ੍ਹਾਂ ਕਰੋ", "ਟ੍ਰਾਂਸਫਰ", "ਖਰੀਦੋ",
        // Odia
        "ପେମେଣ୍ଟ", "ଦେୟ", "ପଠାନ୍ତୁ", "ନିଶ୍ଚିତ କରନ୍ତୁ", "ଅର୍ଡର", "ଦାଖଲ କରନ୍ତୁ", "ସ୍ଥାନାନ୍ତର", "କିଣନ୍ତୁ",
    )

    private val latinPatterns = LATIN_KEYWORDS.map { keyword ->
        keyword to Regex("\\b${Regex.escape(keyword)}\\b", RegexOption.IGNORE_CASE)
    }

    /** Returns the matched keyword (for logging/history), or null if nothing matched. */
    fun matchedKeyword(text: String): String? {
        if (text.isBlank()) return null
        latinPatterns.firstOrNull { (_, pattern) -> pattern.containsMatchIn(text) }?.let { (keyword, _) -> return keyword }
        return SCRIPT_KEYWORDS.firstOrNull { text.contains(it) }
    }
}
