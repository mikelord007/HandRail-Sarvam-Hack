package com.handrail.speech

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Serializes playback so overlapping narrations queue instead of talking
 * over each other. Also tracks the currently-playing MediaPlayer so [stop]
 * can interrupt it — the assist overlay's Stop / × / "No, stop" all need to
 * cut Handrail off mid-sentence, not just cancel the coroutine that's
 * awaiting playback (cancelling the coroutine alone leaves the audio
 * hardware still talking).
 */
class NarrationPlayer(private val context: Context) {

    private val mutex = Mutex()

    @Volatile private var activePlayer: MediaPlayer? = null
    @Volatile private var activeDone: CompletableDeferred<Unit>? = null

    suspend fun play(audio: ByteArray) {
        mutex.withLock {
            playInternal(audio)
        }
    }

    /** Stops whatever is currently speaking, if anything. Safe to call when nothing is playing. */
    fun stop() {
        val player = activePlayer
        activePlayer = null
        if (player != null) {
            try {
                player.stop()
            } catch (e: Exception) {
                Log.w(TAG, "stop() on an already-stopped/released player", e)
            }
            player.release()
        }
        activeDone?.complete(Unit)
        activeDone = null
    }

    private suspend fun playInternal(audio: ByteArray) = withContext(Dispatchers.IO) {
        val file = File.createTempFile("narration", ".wav", context.cacheDir)
        try {
            val done = CompletableDeferred<Unit>()
            activeDone = done
            val player = MediaPlayer()
            activePlayer = player
            try {
                file.writeBytes(audio)
                player.setOnCompletionListener {
                    activePlayer = null
                    it.release()
                    done.complete(Unit)
                }
                player.setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    activePlayer = null
                    mp.release()
                    done.complete(Unit)
                    true
                }
                player.setDataSource(file.absolutePath)
                player.prepare()
                player.start()
                // A completion/error callback can go unfired if the system
                // reclaims audio focus mid-playback (e.g. the agent just
                // navigated into an app that grabs it) — that was observed
                // wedging done.await() forever, which — since play() is
                // called synchronously from the agent loop's narration steps
                // and every playback shares one mutex — silently froze the
                // ENTIRE takeover task. A stuck narration must not be able to
                // block the loop indefinitely.
                if (withTimeoutOrNull(NARRATION_TIMEOUT_MS) { done.await() } == null) {
                    Log.w(TAG, "Narration playback timed out, releasing MediaPlayer")
                    activePlayer = null
                    player.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to play narration", e)
                activePlayer = null
                player.release()
            } finally {
                activeDone = null
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val TAG = "NarrationPlayer"
        const val NARRATION_TIMEOUT_MS = 10_000L
    }
}
