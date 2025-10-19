package com.patrick.neuroglasses.helpers

import android.content.Context
import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.AudioStreamListener
import com.rokid.cxr.client.utils.ValueUtil
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helper class for audio recording operations from Rokid glasses.
 * Handles audio stream capture, buffering, and file saving.
 */
class AudioHelper(private val context: Context, private val appTag: String = "AudioHelper") {

    // Audio recording state
    var isRecording = false
        private set

    // Audio recording variables
    private val audioChunks = mutableListOf<ByteArray>()
    private var currentCodecType: Int = 1 // Default to PCM
    private var totalAudioBytes: Long = 0

    // Track the most recently saved audio file path
    var lastSavedAudioPath: String? = null
        private set

    // Callback interface for audio events
    interface AudioRecordingListener {
        fun onAudioStreamStarted(codecType: Int, streamType: String?)
        fun onAudioDataReceived(chunkSize: Int, totalChunks: Int, totalBytes: Long)
        fun onAudioRecordingStarted(message: String)
        fun onAudioRecordingFailed(message: String)
        fun onAudioRecordingStopped(savedFilePath: String?)
        fun onAudioStatusUpdate(message: String)
    }

    private var listener: AudioRecordingListener? = null

    fun setListener(listener: AudioRecordingListener) {
        this.listener = listener
    }

    /**
     * Audio stream listener to receive audio data from glasses
     */
    private val audioStreamListener = object : AudioStreamListener {
        /**
         * Called when audio stream started.
         * @param codecType The stream codec type: 1:pcm, 2:opus.
         * @param streamType The stream type such as "AI_assistant"
         */
        override fun onStartAudioStream(codecType: Int, streamType: String?) {
            Log.d(appTag, "=== Audio Stream Started ===")
            Log.d(appTag, "Codec type: ${if (codecType == 1) "PCM" else if (codecType == 2) "OPUS" else "Unknown ($codecType)"}")
            Log.d(appTag, "Stream type: $streamType")

            // Store codec type for file extension
            currentCodecType = codecType

            // Clear previous audio chunks and reset counter
            audioChunks.clear()
            totalAudioBytes = 0
            Log.i(appTag, "Audio buffer cleared, ready to record")

            listener?.onAudioStreamStarted(codecType, streamType)
        }

        /**
         * Called when audio stream data is received.
         * @param data The audio stream data.
         * @param offset The offset of audio stream data.
         * @param length The length of audio stream data.
         */
        override fun onAudioStream(data: ByteArray?, offset: Int, length: Int) {
            Log.d(appTag, "Audio data received - Offset: $offset, Length: $length, Data null: ${data == null}")

            if (data != null && length > 0) {
                Log.i(appTag, "Audio chunk: $length bytes")

                // Extract the actual audio data from the chunk
                val audioChunk = ByteArray(length)
                System.arraycopy(data, offset, audioChunk, 0, length)

                // Add to our list of chunks
                audioChunks.add(audioChunk)
                totalAudioBytes += length

                Log.d(appTag, "Chunk stored. Total chunks: ${audioChunks.size}, Total bytes: $totalAudioBytes")

                listener?.onAudioDataReceived(length, audioChunks.size, totalAudioBytes)
            }
        }
    }

    /**
     * Set or remove the audio stream listener
     * @param set true: set the listener, false: remove the listener
     */
    fun setAudioStreamListener(set: Boolean) {
        CxrApi.getInstance().setAudioStreamListener(if (set) audioStreamListener else null)
        Log.d(appTag, "Audio stream listener ${if (set) "set" else "removed"}")
    }

    /**
     * Open audio recording from glasses
     * @param codecType The stream codec type: 1:pcm, 2:opus
     * @param streamType The stream type identifier (e.g., "AI_assistant")
     * @return The status of the open audio record request
     */
    fun openAudioRecord(codecType: Int = 1, streamType: String = "AI_assistant"): ValueUtil.CxrStatus? {
        Log.d(appTag, "=== Opening Audio Record ===")
        Log.d(appTag, "Codec type: ${if (codecType == 1) "PCM (1)" else if (codecType == 2) "OPUS (2)" else "Unknown ($codecType)"}")
        Log.d(appTag, "Stream type: $streamType")

        val status = CxrApi.getInstance().openAudioRecord(codecType, streamType)
        Log.d(appTag, "Open audio record status: $status")

        when (status) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.i(appTag, "Audio recording started successfully")
                isRecording = true
                listener?.onAudioRecordingStarted("Audio recording started")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(appTag, "Audio recording request waiting - previous request still processing")
                listener?.onAudioStatusUpdate("Audio request pending...")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(appTag, "Failed to start audio recording")
                listener?.onAudioRecordingFailed("Audio recording failed")
            }
            else -> {
                Log.w(appTag, "Unknown audio recording status: $status")
                listener?.onAudioStatusUpdate("Unknown audio status")
            }
        }

        Log.d(appTag, "=== Open Audio Record Complete ===")
        return status
    }

    /**
     * Write WAV header to the output stream
     * WAV format specification for PCM audio
     */
    private fun writeWavHeader(outputStream: FileOutputStream, dataSize: Long) {
        val sampleRate = 16000 // 16kHz sample rate (typical for Rokid glasses)
        val channels = 1 // Mono audio
        val bitsPerSample = 16 // 16-bit PCM

        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        // RIFF header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToBytes((dataSize + 36).toInt())) // File size - 8
        outputStream.write("WAVE".toByteArray())

        // fmt subchunk
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToBytes(16)) // Subchunk1Size (16 for PCM)
        outputStream.write(shortToBytes(1)) // AudioFormat (1 for PCM)
        outputStream.write(shortToBytes(channels.toShort())) // NumChannels
        outputStream.write(intToBytes(sampleRate)) // SampleRate
        outputStream.write(intToBytes(byteRate)) // ByteRate
        outputStream.write(shortToBytes(blockAlign.toShort())) // BlockAlign
        outputStream.write(shortToBytes(bitsPerSample.toShort())) // BitsPerSample

        // data subchunk
        outputStream.write("data".toByteArray())
        outputStream.write(intToBytes(dataSize.toInt())) // Subchunk2Size
    }

    /**
     * Convert integer to little-endian byte array
     */
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    /**
     * Convert short to little-endian byte array
     */
    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }

    /**
     * Save accumulated audio chunks to a file
     * @return The file path if successful, null otherwise
     */
    fun saveAudioToFile(): String? {
        if (audioChunks.isEmpty()) {
            Log.w(appTag, "No audio data to save")
            return null
        }

        try {
            Log.d(appTag, "=== Saving Audio to File ===")
            Log.d(appTag, "Total chunks to save: ${audioChunks.size}")
            Log.d(appTag, "Total bytes to save: $totalAudioBytes")

            // Create timestamp for unique filename
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            // Determine file extension based on codec
            val fileExtension = when (currentCodecType) {
                1 -> "wav"  // PCM saved as WAV for easy playback
                2 -> "opus" // OPUS audio
                else -> "raw"
            }

            val fileName = "audio_${timestamp}.$fileExtension"

            // Save to app's external files directory (no permissions needed)
            val audioDir = File(context.getExternalFilesDir(null), "audio_recordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
                Log.d(appTag, "Created audio directory: ${audioDir.absolutePath}")
            }

            val audioFile = File(audioDir, fileName)
            Log.i(appTag, "Saving audio to: ${audioFile.absolutePath}")

            // Write all chunks to file
            FileOutputStream(audioFile).use { outputStream ->
                // Write WAV header for PCM audio
                if (currentCodecType == 1) {
                    writeWavHeader(outputStream, totalAudioBytes)
                }

                var bytesWritten = 0L
                audioChunks.forEach { chunk ->
                    outputStream.write(chunk)
                    bytesWritten += chunk.size
                }
                Log.d(appTag, "Bytes written: $bytesWritten")
            }

            val fileSizeKB = audioFile.length() / 1024.0
            Log.i(appTag, "Audio file saved successfully!")
            Log.i(appTag, "File size: ${String.format("%.2f", fileSizeKB)} KB")
            Log.i(appTag, "File path: ${audioFile.absolutePath}")

            // Clear chunks after saving
            audioChunks.clear()
            totalAudioBytes = 0
            Log.d(appTag, "Audio buffer cleared")

            // Store the path of the most recently saved file
            lastSavedAudioPath = audioFile.absolutePath

            return audioFile.absolutePath

        } catch (e: Exception) {
            Log.e(appTag, "Error saving audio file", e)
            Log.e(appTag, "Exception type: ${e.javaClass.simpleName}")
            Log.e(appTag, "Exception message: ${e.message}")
            return null
        }
    }

    /**
     * Close audio recording from glasses
     * @param streamType The stream type identifier (must match the one used in openAudioRecord)
     * @return The status of the close audio record request
     */
    fun closeAudioRecord(streamType: String = "AI_assistant"): ValueUtil.CxrStatus? {
        Log.d(appTag, "=== Closing Audio Record ===")
        Log.d(appTag, "Stream type: $streamType")

        val status = CxrApi.getInstance().closeAudioRecord(streamType)
        Log.d(appTag, "Close audio record status: $status")

        when (status) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.i(appTag, "Audio recording stopped successfully")
                isRecording = false

                // Save the recorded audio to file
                val savedFilePath = saveAudioToFile()

                if (savedFilePath != null) {
                    Log.i(appTag, "Audio file notification: $savedFilePath")
                } else {
                    if (audioChunks.isEmpty()) {
                        Log.w(appTag, "No audio data recorded")
                    } else {
                        Log.e(appTag, "Failed to save audio file")
                    }
                }

                listener?.onAudioRecordingStopped(savedFilePath)
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(appTag, "Audio stop request waiting - previous request still processing")
                listener?.onAudioStatusUpdate("Audio stop request pending...")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(appTag, "Failed to stop audio recording")
                listener?.onAudioRecordingFailed("Audio stop failed")
            }
            else -> {
                Log.w(appTag, "Unknown audio stop status: $status")
                listener?.onAudioStatusUpdate("Unknown audio stop status")
            }
        }

        Log.d(appTag, "=== Close Audio Record Complete ===")
        return status
    }

    /**
     * Clear audio cache without saving
     * Used when you want to discard recorded audio
     */
    fun clearAudioCache() {
        Log.d(appTag, "=== Clearing Audio Cache ===")
        Log.d(appTag, "Discarding ${audioChunks.size} chunks, $totalAudioBytes bytes")
        audioChunks.clear()
        totalAudioBytes = 0
        Log.d(appTag, "Audio cache cleared")
    }

    /**
     * Get current recording statistics
     */
    fun getRecordingStats(): RecordingStats {
        return RecordingStats(
            isRecording = isRecording,
            totalChunks = audioChunks.size,
            totalBytes = totalAudioBytes,
            codecType = currentCodecType
        )
    }

    /**
     * Data class for recording statistics
     */
    data class RecordingStats(
        val isRecording: Boolean,
        val totalChunks: Int,
        val totalBytes: Long,
        val codecType: Int
    )

    /**
     * Release audio resources and remove listeners
     */
    fun release() {
        if (isRecording) {
            Log.d(appTag, "Releasing audio resources - stopping active recording")
            closeAudioRecord()
        }
        setAudioStreamListener(false)
        audioChunks.clear()
        totalAudioBytes = 0
    }
}
