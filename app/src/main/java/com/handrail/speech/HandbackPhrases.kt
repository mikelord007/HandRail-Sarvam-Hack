package com.handrail.speech

/**
 * The spoken line when the irreversible-action guard trips — the one moment
 * Handrail narrates BEFORE handing the phone back rather than before acting.
 * `%s` is replaced with the blocked button's own visible text (e.g. "Pay
 * ₹450"), so the spoken line names the actual thing it's refusing to tap.
 *
 * The hand-back CARD's own copy (title, body, "yours to make" aside) stays
 * English and inline in HandbackScreen, per this app's decision to keep UI
 * copy English-only and reserve multilingual strings for the spoken channel.
 */
object HandbackPhrases {

    private val ABOUT_TO_DO = mapOf(
        "hi-IN" to "मैं यहीं रुकता हूँ — \"%s\" वाला काम आप खुद कर लीजिए",
        "kn-IN" to "ನಾನು ಇಲ್ಲಿ ನಿಲ್ಲಿಸುತ್ತೇನೆ — \"%s\" ಅನ್ನು ದಯವಿಟ್ಟು ನೀವೇ ಮಾಡಿ",
        "ta-IN" to "நான் இங்கே நிறுத்துகிறேன் — \"%s\" என்பதை தயவுசெய்து நீங்களே செய்யுங்கள்",
        "te-IN" to "నేను ఇక్కడ ఆగుతున్నాను — \"%s\" అనేది దయచేసి మీరే చేయండి",
        "mr-IN" to "मी इथेच थांबतो — \"%s\" हे कृपया तुम्हीच करा",
        "bn-IN" to "আমি এখানেই থামছি — \"%s\" টি দয়া করে আপনি নিজে করুন",
        "gu-IN" to "હું અહીં જ અટકું છું — \"%s\" કૃપા કરીને તમે જાતે કરો",
        "ml-IN" to "ഞാൻ ഇവിടെ നിർത്തുന്നു — \"%s\" ദയവായി നിങ്ങൾ തന്നെ ചെയ്യൂ",
        "pa-IN" to "ਮੈਂ ਇੱਥੇ ਹੀ ਰੁਕਦਾ ਹਾਂ — \"%s\" ਕਿਰਪਾ ਕਰਕੇ ਤੁਸੀਂ ਖੁਦ ਕਰੋ",
        "od-IN" to "ମୁଁ ଏଠାରେ ରହୁଛି — \"%s\" ଦୟାକରି ଆପଣ ନିଜେ କରନ୍ତୁ",
        "en-IN" to "I'll stop here — please do \"%s\" yourself.",
    )

    fun aboutToDo(languageCode: String, actionLabel: String): String {
        val template = ABOUT_TO_DO[languageCode] ?: ABOUT_TO_DO.getValue("en-IN")
        return template.replace("%s", actionLabel)
    }
}
