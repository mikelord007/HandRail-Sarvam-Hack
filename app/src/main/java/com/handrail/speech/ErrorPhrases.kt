package com.handrail.speech

/**
 * Generic "I couldn't do that" fallback narration, per language, so a
 * failure never falls silent even when nothing else about it is known yet.
 * Hardcoded and network-independent — this must work even when whatever
 * just failed was the LLM or network itself.
 */
object ErrorPhrases {

    private val COULD_NOT_DO_THAT = mapOf(
        "hi-IN" to "मुझे यह नहीं मिला",
        "kn-IN" to "ನನಗೆ ಇದು ಸಿಗಲಿಲ್ಲ",
        "ta-IN" to "எனக்கு இது கிடைக்கவில்லை",
        "te-IN" to "నాకు ఇది దొరకలేదు",
        "mr-IN" to "मला हे सापडले नाही",
        "bn-IN" to "আমি এটি খুঁজে পাইনি",
        "gu-IN" to "મને આ મળ્યું નહીં",
        "ml-IN" to "എനിക്ക് ഇത് കിട്ടിയില്ല",
        "pa-IN" to "ਮੈਨੂੰ ਇਹ ਨਹੀਂ ਮਿਲਿਆ",
        "od-IN" to "ମୋତେ ଏହା ମିଳିଲା ନାହିଁ",
        "en-IN" to "I couldn't do that.",
    )

    fun couldNotDoThat(languageCode: String): String =
        COULD_NOT_DO_THAT[languageCode] ?: COULD_NOT_DO_THAT.getValue("en-IN")

    private val STOPPED_FOR_YOU = mapOf(
        "hi-IN" to "मैं यहीं रुक रहा हूँ, आप आगे खुद कर लीजिए",
        "kn-IN" to "ನಾನು ಇಲ್ಲಿ ನಿಲ್ಲಿಸುತ್ತಿದ್ದೇನೆ, ದಯವಿಟ್ಟು ಮುಂದೆ ನೀವೇ ಮುಂದುವರಿಸಿ",
        "ta-IN" to "நான் இங்கே நிறுத்துகிறேன், தயவுசெய்து நீங்களே தொடரவும்",
        "te-IN" to "నేను ఇక్కడ ఆగుతున్నాను, దయచేసి మీరే కొనసాగించండి",
        "mr-IN" to "मी इथेच थांबतो, कृपया तुम्हीच पुढे करा",
        "bn-IN" to "আমি এখানেই থামছি, দয়া করে আপনি নিজে এগিয়ে যান",
        "gu-IN" to "હું અહીં જ અટકું છું, કૃપા કરીને તમે જાતે આગળ કરો",
        "ml-IN" to "ഞാൻ ഇവിടെ നിർത്തുന്നു, ദയവായി നിങ്ങൾ തന്നെ തുടരൂ",
        "pa-IN" to "ਮੈਂ ਇੱਥੇ ਹੀ ਰੁਕਦਾ ਹਾਂ, ਕਿਰਪਾ ਕਰਕੇ ਤੁਸੀਂ ਖੁਦ ਅੱਗੇ ਕਰੋ",
        "od-IN" to "ମୁଁ ଏଠାରେ ରହୁଛି, ଦୟାକରି ଆପଣ ନିଜେ ଆଗକୁ କରନ୍ତୁ",
        "en-IN" to "I'll stop here — please continue yourself.",
    )

    fun stoppedForYou(languageCode: String): String =
        STOPPED_FOR_YOU[languageCode] ?: STOPPED_FOR_YOU.getValue("en-IN")

    private val LOGIN_OR_OTP_DETECTED = mapOf(
        "hi-IN" to "यह लॉगिन या OTP स्क्रीन जैसा लगता है, मैं यहीं रुकता हूँ — आप इसे खुद पूरा कर लीजिए",
        "kn-IN" to "ಇದು ಲಾಗಿನ್ ಅಥವಾ OTP ಸ್ಕ್ರೀನ್‌ನಂತೆ ಕಾಣುತ್ತದೆ, ನಾನು ಇಲ್ಲಿ ನಿಲ್ಲಿಸುತ್ತೇನೆ — ದಯವಿಟ್ಟು ಇದನ್ನು ನೀವೇ ಪೂರ್ಣಗೊಳಿಸಿ",
        "ta-IN" to "இது உள்நுழைவு அல்லது OTP திரையாகத் தெரிகிறது, நான் இங்கே நிறுத்துகிறேன் — தயவுசெய்து இதை நீங்களே முடிக்கவும்",
        "te-IN" to "ఇది లాగిన్ లేదా OTP స్క్రీన్ లా కనిపిస్తోంది, నేను ఇక్కడ ఆగుతున్నాను — దయచేసి దీన్ని మీరే పూర్తి చేయండి",
        "mr-IN" to "हे लॉगिन किंवा OTP स्क्रीन दिसत आहे, मी इथेच थांबतो — कृपया हे तुम्हीच पूर्ण करा",
        "bn-IN" to "এটি লগইন বা OTP স্ক্রিনের মতো মনে হচ্ছে, আমি এখানেই থামছি — দয়া করে এটি নিজে সম্পূর্ণ করুন",
        "gu-IN" to "આ લોગિન અથવા OTP સ્ક્રીન લાગે છે, હું અહીં જ અટકું છું — કૃપા કરીને આ તમે જાતે પૂર્ણ કરો",
        "ml-IN" to "ഇത് ലോഗിൻ അല്ലെങ്കിൽ OTP സ്ക്രീൻ ആണെന്ന് തോന്നുന്നു, ഞാൻ ഇവിടെ നിർത്തുന്നു — ദയവായി ഇത് നിങ്ങൾ തന്നെ പൂർത്തിയാക്കൂ",
        "pa-IN" to "ਇਹ ਲਾਗਇਨ ਜਾਂ OTP ਸਕ੍ਰੀਨ ਜਾਪਦੀ ਹੈ, ਮੈਂ ਇੱਥੇ ਹੀ ਰੁਕਦਾ ਹਾਂ — ਕਿਰਪਾ ਕਰਕੇ ਇਸਨੂੰ ਖੁਦ ਪੂਰਾ ਕਰੋ",
        "od-IN" to "ଏହା ଲଗଇନ୍ କିମ୍ବା OTP ସ୍କ୍ରିନ୍ ପରି ଲାଗୁଛି, ମୁଁ ଏଠାରେ ରହୁଛି — ଦୟାକରି ଏହାକୁ ନିଜେ ସମାପ୍ତ କରନ୍ତୁ",
        "en-IN" to "This looks like a login or OTP screen — I'll stop here, please complete it yourself.",
    )

    fun loginOrOtpDetected(languageCode: String): String =
        LOGIN_OR_OTP_DETECTED[languageCode] ?: LOGIN_OR_OTP_DETECTED.getValue("en-IN")
}
