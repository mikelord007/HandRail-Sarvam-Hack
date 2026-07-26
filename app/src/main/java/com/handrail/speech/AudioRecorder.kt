package com.handrail.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records mic audio to a temp AAC/MPEG-4 file for upload to Saaras. */
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): File {
        val file = File.createTempFile("handrail_stt_", ".m4a", context.cacheDir)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        outputFile = file
        return file
    }

    /** Returns the recorded file, or null if recording produced no usable data. */
    fun stop(): File? {
        val file = outputFile
        val current = recorder
        recorder = null
        outputFile = null
        return try {
            current?.stop()
            current?.release()
            file
        } catch (e: RuntimeException) {
            current?.release()
            file?.delete()
            null
        }
    }
}
