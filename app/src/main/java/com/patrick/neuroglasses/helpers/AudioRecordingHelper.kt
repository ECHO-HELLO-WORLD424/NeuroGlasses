package com.patrick.neuroglasses.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Helper class to manage audio recording operations for AR Glasses
 * Handles audio recording, WAV file generation, and ASR integration
 */
class AudioRecordingHelper(
    private val context: Context,
    private val appTag: String = "AudioRecordingHelper"
) {
    interface RecordingListener {
        fun onRecordingStarted()
        fun onRecordingStopped(audioFile: File?)
        fun onRecordingFailed(error: String)
    }

    private var listener: RecordingListener? = null
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()
    private var recordingThread: Thread? = null
    private var asrRecordingTimer: Thread? = null
    private var currentAsrAudioFile: File? = null

    /**
     * Set the listener for recording callbacks
     * Pass null to clear the listener and prevent memory leaks
     */
    fun setListener(listener: RecordingListener?) {
        this.listener = listener
    }

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Start audio recording with default settings (16kHz, mono, 16-bit)
     */
    fun startRecording() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Microphone permission not granted", Toast.LENGTH_SHORT).show()
            listener?.onRecordingFailed("Microphone permission not granted")
            return
        }

        val sampleRate = 16000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize
        )

        audioBuffer.reset()
        isRecording = true

        audioRecord?.startRecording()

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                if (read > 0) {
                    audioBuffer.write(buffer, 0, read)
                }
            }
        }
        recordingThread?.start()

        listener?.onRecordingStarted()
        Log.i(appTag, "Audio recording started")
    }

    /**
     * Stop audio recording and save to WAV file
     * @return File containing the audio recording, or null if failed
     */
    fun stopRecording(): File? {
        isRecording = false
        recordingThread?.join()
        recordingThread = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        if (audioBuffer.size() == 0) {
            audioBuffer.reset()
            Log.w(appTag, "No audio data recorded")
            listener?.onRecordingStopped(null)
            return null
        }

        // Save as WAV file
        val audioDir = context.getExternalFilesDir("audio_recordings") ?: context.filesDir
        audioDir.mkdirs()
        val audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.wav")

        try {
            FileOutputStream(audioFile).use { fos ->
                // Write WAV header
                writeWavHeader(fos, audioBuffer.size(), 16000, 1, 16)
                // Write audio data
                audioBuffer.writeTo(fos)
            }

            audioBuffer.reset()
            Log.i(appTag, "Audio saved to: ${audioFile.absolutePath}")
            listener?.onRecordingStopped(audioFile)
            return audioFile
        } catch (e: Exception) {
            Log.e(appTag, "Failed to save audio: ${e.message}", e)
            audioBuffer.reset()
            listener?.onRecordingFailed("Failed to save audio: ${e.message}")
            return null
        }
    }

    /**
     * Start a timed ASR recording (4 seconds)
     * @param onComplete Callback when recording completes with the audio file
     */
    fun startAsrRecording(onComplete: (File?) -> Unit) {
        if (isRecording) {
            Log.w(appTag, "Already recording")
            return
        }

        Log.i(appTag, "ASR triggered! Starting 4-second recording...")
        startRecording()

        // Stop recording after 4 seconds
        asrRecordingTimer = Thread {
            try {
                Thread.sleep(4000)
                val audioFile = stopRecording()
                currentAsrAudioFile = audioFile
                onComplete(audioFile)
            } catch (e: InterruptedException) {
                Log.d(appTag, "ASR recording timer interrupted: ${e.stackTrace}")
                if (isRecording) {
                    stopRecording()
                }
                onComplete(null)
            }
        }
        asrRecordingTimer?.start()
    }

    /**
     * Clean up the current ASR audio file
     */
    fun cleanupAsrAudioFile() {
        currentAsrAudioFile?.let { file ->
            try {
                if (file.exists()) {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(appTag, "Deleted ASR audio file: ${file.absolutePath}")
                    } else {
                        Log.w(appTag, "Failed to delete ASR audio file: ${file.absolutePath}")
                    }
                }
            } catch (e: Exception) {
                Log.e(appTag, "Error deleting ASR audio file: ${e.message}", e)
            } finally {
                currentAsrAudioFile = null
            }
        }
    }

    /**
     * Write WAV file header
     */
    private fun writeWavHeader(
        out: FileOutputStream,
        audioDataSize: Int,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        header.put("RIFF".toByteArray())
        header.putInt(36 + audioDataSize)
        header.put("WAVE".toByteArray())

        // fmt chunk
        header.put("fmt ".toByteArray())
        header.putInt(16)  // fmt chunk size
        header.putShort(1)  // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(sampleRate * channels * bitsPerSample / 8)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())

        // data chunk
        header.put("data".toByteArray())
        header.putInt(audioDataSize)

        out.write(header.array())
    }

    /**
     * Force stop any ongoing recording
     */
    fun forceStop() {
        if (isRecording) {
            isRecording = false
            recordingThread?.interrupt()
            recordingThread = null
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        }

        asrRecordingTimer?.interrupt()
        asrRecordingTimer = null
    }

    /**
     * Release all resources and clean up
     */
    fun release() {
        forceStop()

        // Close audio buffer
        try {
            audioBuffer.close()
        } catch (e: Exception) {
            Log.e(appTag, "Error closing audio buffer: ${e.message}", e)
        }

        cleanupAsrAudioFile()
        listener = null
        Log.i(appTag, "Audio recording helper released")
    }
}
