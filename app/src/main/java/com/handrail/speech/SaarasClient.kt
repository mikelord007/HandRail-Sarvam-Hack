package com.handrail.speech

import com.handrail.BuildConfig
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody

@Serializable
private data class SttResponse(
    @SerialName("request_id") val requestId: String? = null,
    val transcript: String = "",
    @SerialName("language_code") val languageCode: String? = null,
)

/**
 * Saaras translates spoken Indian-language audio directly to English text,
 * unlike Sarvam's plain-transcription Saarika model — that's the point of
 * using it here: the agent loop gets consistent English task text no matter
 * which language the user spoke in.
 */
class SaarasClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun transcribe(audioFile: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.asRequestBody("audio/mp4".toMediaType()),
                )
                .addFormDataPart("model", "saaras:v3")
                .addFormDataPart("mode", "translate")
                .build()

            val request = Request.Builder()
                .url("https://api.sarvam.ai/speech-to-text")
                .addHeader("api-subscription-key", BuildConfig.SARVAM_API_KEY)
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IOException("Saaras STT failed: HTTP ${response.code} - $responseBody")
                }
                val parsed = json.decodeFromString<SttResponse>(responseBody)
                Result.success(parsed.transcript)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
