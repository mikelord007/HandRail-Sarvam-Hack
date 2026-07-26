package com.handrail.speech

import com.handrail.BuildConfig
import java.util.concurrent.TimeUnit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
private data class StreamTtsRequest(
    val text: String,
    @SerialName("target_language_code") val targetLanguageCode: String,
    val speaker: String,
    val pace: Float,
    val model: String,
    @SerialName("output_audio_codec") val outputAudioCodec: String,
    @SerialName("speech_sample_rate") val speechSampleRate: Int,
)

class BulbulClient {

    private val json = Json { ignoreUnknownKeys = true }

    /** Shared with [NarrationPlayer], which executes the streamed call itself so it can read the response body incrementally instead of waiting for [OkHttpClient.newCall] to return a fully-buffered result. */
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Builds (but does not execute) a request against Bulbul's streaming
     * endpoint — raw PCM arrives chunk by chunk as it's generated, instead
     * of the non-streaming endpoint's single JSON blob with the full
     * base64-encoded clip, which can't start playing until synthesis of the
     * entire line has finished. [NarrationPlayer.playStream] executes this
     * and plays chunks as they arrive.
     *
     * [output_audio_codec]=linear16 (raw PCM) is requested specifically —
     * anything container-based (mp3/wav/etc) would need enough bytes
     * buffered to parse a header before a player could start, which defeats
     * the point of streaming.
     *
     * [speakerId] and [pace] default to Meera-at-normal-speed so existing
     * call sites keep compiling unchanged. `model` is pinned explicitly:
     * verified live against the Sarvam API that the design's speaker names
     * (Meera/Arvind/Pavithra -> anushka/abhilash/vidya, see [Speakers]) are
     * `bulbul:v2` IDs — `bulbul:v1` has a different roster and rejects them.
     */
    fun streamRequest(
        text: String,
        languageCode: String,
        speakerId: String = Speakers.DEFAULT.id,
        pace: Float = VoiceSettings.PACE_NORMAL,
    ): Request {
        val bodyJson = json.encodeToString(
            StreamTtsRequest(
                text = text,
                targetLanguageCode = languageCode,
                speaker = speakerId,
                pace = pace,
                model = BULBUL_MODEL,
                outputAudioCodec = "linear16",
                speechSampleRate = STREAM_SAMPLE_RATE,
            ),
        )
        return Request.Builder()
            .url("https://api.sarvam.ai/text-to-speech/stream")
            .addHeader("api-subscription-key", BuildConfig.SARVAM_API_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()
    }

    companion object {
        /** Sample rate requested in [streamRequest] — [NarrationPlayer] must configure its `AudioTrack` to match, since linear16 has no header to read this back from. */
        const val STREAM_SAMPLE_RATE = 22050
        private const val BULBUL_MODEL = "bulbul:v2"
    }
}
