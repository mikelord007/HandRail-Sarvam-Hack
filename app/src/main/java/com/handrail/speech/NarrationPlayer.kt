package com.handrail.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Plays a Bulbul streaming-TTS response as raw PCM via `AudioTrack`,
 * writing each chunk to the track as it arrives off the network instead of
 * waiting for the full clip — audio starts as soon as the first chunk is
 * decoded rather than after the whole line has been synthesized, per
 * CLAUDE.md's latency-hiding rule.
 *
 * Serializes playback so overlapping narrations queue instead of talking
 * over each other. Also tracks the currently-playing `AudioTrack` so [stop]
 * can interrupt it — the assist overlay's Stop / × / "No, stop" all need to
 * cut Handrail off mid-sentence, not just cancel the coroutine that's
 * awaiting playback (cancelling the coroutine alone leaves the audio
 * hardware still talking).
 */
// context is unused now that playback is AudioTrack-based (no temp file, no
// MediaPlayer) — kept so existing `NarrationPlayer(applicationContext)`
// call sites don't need to change.
class NarrationPlayer(@Suppress("UNUSED_PARAMETER") context: Context) {

    private val mutex = Mutex()

    @Volatile private var activeTrack: AudioTrack? = null
    @Volatile private var stopRequested = false

    /** Streams and plays [request] against [client]; [sampleRate] must match what the request asked Bulbul to synthesize at (see [BulbulClient.STREAM_SAMPLE_RATE]). */
    suspend fun playStream(
        client: OkHttpClient,
        request: Request,
        sampleRate: Int = BulbulClient.STREAM_SAMPLE_RATE,
    ): Result<Unit> =
        mutex.withLock {
            playStreamInternal(client, request, sampleRate)
        }

    /** Stops whatever is currently speaking, if anything. Safe to call when nothing is playing. */
    fun stop() {
        stopRequested = true
        val track = activeTrack
        activeTrack = null
        if (track != null) {
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stop() on an already-stopped/released AudioTrack", e)
            }
            track.release()
        }
    }

    private suspend fun playStreamInternal(
        client: OkHttpClient,
        request: Request,
        sampleRate: Int,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        stopRequested = false
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            val message = "AudioTrack.getMinBufferSize failed for sampleRate=$sampleRate"
            Log.e(TAG, message)
            return@withContext Result.failure(IllegalStateException(message))
        }

        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBufferSize * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        activeTrack = track

        try {
            track.play()
            // Mirrors the old MediaPlayer path's timeout: a stalled network
            // read here would otherwise hang the mutex forever, silently
            // freezing every subsequent narration and, in the agent loop,
            // the whole takeover task.
            val result = withTimeoutOrNull(NARRATION_TIMEOUT_MS) {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val body = response.body?.string().orEmpty()
                        return@use Result.failure<Unit>(
                            java.io.IOException("Bulbul TTS stream failed: HTTP ${response.code} - $body"),
                        )
                    }
                    val source = response.body?.source()
                        ?: return@use Result.failure<Unit>(java.io.IOException("Bulbul TTS stream returned no body"))
                    val buffer = ByteArray(minBufferSize)
                    while (!stopRequested) {
                        val read = source.read(buffer)
                        if (read == -1) break
                        track.write(buffer, 0, read)
                    }
                    Result.success(Unit)
                }
            }
            result ?: run {
                Log.w(TAG, "Narration stream timed out")
                Result.failure(java.io.IOException("Narration stream timed out"))
            }
        } catch (e: Exception) {
            if (stopRequested) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Failed to stream narration", e)
                Result.failure(e)
            }
        } finally {
            if (activeTrack === track) {
                activeTrack = null
            }
            try {
                track.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stop() on an already-stopped/released AudioTrack", e)
            }
            track.release()
        }
    }

    private companion object {
        const val TAG = "NarrationPlayer"
        const val NARRATION_TIMEOUT_MS = 10_000L
    }
}
