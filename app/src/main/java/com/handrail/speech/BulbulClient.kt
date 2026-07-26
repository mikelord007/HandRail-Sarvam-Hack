package com.handrail.speech

import android.util.Base64
import com.handrail.BuildConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class TtsRequest(
    val text: String,
    @SerialName("target_language_code") val targetLanguageCode: String,
    val speaker: String,
    val pace: Float,
    val model: String,
)

@Serializable
private data class TtsResponse(
    @SerialName("request_id") val requestId: String? = null,
    val audios: List<String> = emptyList(),
)

class BulbulClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * [speakerId] and [pace] default to Meera-at-normal-speed so existing
     * call sites keep compiling unchanged. `model` is pinned explicitly:
     * verified live against the Sarvam API that the design's speaker names
     * (Meera/Arvind/Pavithra -> anushka/abhilash/vidya, see [Speakers]) are
     * `bulbul:v2` IDs — `bulbul:v1` has a different roster and rejects them.
     */
    suspend fun synthesize(
        text: String,
        languageCode: String,
        speakerId: String = Speakers.DEFAULT.id,
        pace: Float = VoiceSettings.PACE_NORMAL,
    ): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                val bodyJson = json.encodeToString(
                    TtsRequest(
                        text = text,
                        targetLanguageCode = languageCode,
                        speaker = speakerId,
                        pace = pace,
                        model = BULBUL_MODEL,
                    ),
                )
                val request = Request.Builder()
                    .url("https://api.sarvam.ai/text-to-speech")
                    .addHeader("api-subscription-key", BuildConfig.SARVAM_API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException("Bulbul TTS failed: HTTP ${response.code} - $responseBody")
                    }
                    val parsed = json.decodeFromString<TtsResponse>(responseBody)
                    val audioBase64 = parsed.audios.firstOrNull()
                        ?: throw IOException("Bulbul TTS returned no audio")
                    Result.success(Base64.decode(audioBase64, Base64.DEFAULT))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private companion object {
        const val BULBUL_MODEL = "bulbul:v2"
    }
}
