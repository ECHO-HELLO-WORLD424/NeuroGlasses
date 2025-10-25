package com.patrick.neuroglasses.helpers

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * Streaming audio player for PCM audio chunks
 * Uses in-memory queue with AudioTrack for true streaming playback
 * PCM format allows feeding arbitrary chunks without frame boundary issues
 */
class StreamingAudioPlayer(private val appTag: String = "StreamingAudioPlayer") {

    // Queue to hold incoming audio chunks
    private val audioChunkQueue = LinkedBlockingQueue<ByteArray>()

    private var audioTrack: AudioTrack? = null

    private var isPlaying = false
    private var playbackThread: Thread? = null
    private var shouldContinuePlaying = true
    private var isStreamComplete = false

    // Audio format parameters for PCM from TTS API
    // Default: 44.1kHz, mono, 16-bit PCM
    private val sampleRate = 44100
    private val channelCount = 1

    /**
     * Listener interface for playback events
     */
    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackCompleted()
        fun onPlaybackError(error: String)
    }

    private var listener: PlaybackListener? = null

    /**
     * Set the listener for playback callbacks
     * Pass null to clear the listener and prevent memory leaks
     */
    fun setListener(listener: PlaybackListener?) {
        this.listener = listener
    }

    /**
     * Initialize streaming playback
     * Prepares the player for receiving audio chunks
     */
    fun initializeStreaming() {
        try {
            Log.d(appTag, "Initializing streaming playback")

            // Stop any previous playback before starting new stream
            stop()

            // Reset state
            audioChunkQueue.clear()
            isStreamComplete = false
            shouldContinuePlaying = true
            isPlaying = false

            Log.i(appTag, "Streaming initialized, ready to receive chunks")
        } catch (e: Exception) {
            Log.e(appTag, "Error initializing streaming: ${e.message}", e)
            listener?.onPlaybackError("Failed to initialize streaming: ${e.message}")
        }
    }

    /**
     * Add audio chunk to the streaming queue
     * @param chunk The audio data chunk (MP3 encoded)
     */
    fun addChunk(chunk: ByteArray) {
        try {
            if (chunk.isEmpty()) {
                Log.v(appTag, "Skipping empty chunk")
                return
            }

            // Filter out small chunks that can cause hissing/clicking sounds
            if (chunk.size < 100) {
                Log.v(appTag, "Skipping small chunk: ${chunk.size} bytes (< 100 bytes)")
                return
            }

            audioChunkQueue.offer(chunk)
            Log.v(appTag, "Added chunk: ${chunk.size} bytes (queue size: ${audioChunkQueue.size})")

            // Start playback immediately when first chunk arrives
            if (!isPlaying && !audioChunkQueue.isEmpty()) {
                startStreamingPlayback()
            }
        } catch (e: Exception) {
            Log.e(appTag, "Error adding chunk: ${e.message}", e)
        }
    }

    /**
     * Start playback of the streaming audio
     */
    private fun startStreamingPlayback() {
        try {
            if (isPlaying) {
                Log.d(appTag, "Playback already started, ignoring")
                return
            }

            Log.i(appTag, "Starting streaming playback")
            isPlaying = true
            shouldContinuePlaying = true

            // Start playback in a separate thread
            playbackThread = Thread {
                try {
                    playStreamingAudio()
                } catch (e: Exception) {
                    Log.e(appTag, "Error in playback thread: ${e.message}", e)
                    listener?.onPlaybackError("Playback error: ${e.message}")
                } finally {
                    cleanupPlaybackResources()
                }
            }
            playbackThread?.start()

        } catch (e: Exception) {
            Log.e(appTag, "Error starting playback: ${e.message}", e)
            listener?.onPlaybackError("Failed to start playback: ${e.message}")
            isPlaying = false
        }
    }

    /**
     * Play PCM audio using AudioTrack with in-memory queue
     * Much simpler than MP3 - no decoding needed, just write directly to AudioTrack
     */
    private fun playStreamingAudio() {
        try {
            // Setup AudioTrack for PCM playback
            val channelConfig = if (channelCount == 1)
                AudioFormat.CHANNEL_OUT_MONO
            else
                AudioFormat.CHANNEL_OUT_STEREO

            val minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                channelConfig,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
            Log.i(appTag, "AudioTrack initialized and playing (${sampleRate}Hz, ${channelCount}ch)")

            // Notify playback started
            listener?.onPlaybackStarted()

            var totalChunksProcessed = 0
            var totalBytesWritten = 0L

            // Main playback loop - just write PCM chunks directly to AudioTrack
            while (shouldContinuePlaying) {
                // Try to get a chunk from the queue
                val chunk = if (isStreamComplete && audioChunkQueue.isEmpty()) {
                    null // No more data
                } else {
                    // Wait a bit for data if stream is not complete
                    audioChunkQueue.poll(
                        if (isStreamComplete) 0 else 100,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                    )
                }

                if (chunk != null && chunk.isNotEmpty()) {
                    // Write PCM data directly to AudioTrack
                    val written = audioTrack?.write(chunk, 0, chunk.size) ?: 0
                    totalChunksProcessed++
                    totalBytesWritten += written
                    Log.v(appTag, "Wrote chunk $totalChunksProcessed to AudioTrack: $written bytes")
                } else if (isStreamComplete && audioChunkQueue.isEmpty()) {
                    // Stream is complete and no more chunks
                    Log.d(appTag, "All chunks processed, ending playback")
                    break
                }
            }

            // Wait a bit for AudioTrack to finish playing buffered data
            Thread.sleep(200)

            Log.i(appTag, "Playback completed successfully (processed $totalChunksProcessed chunks, $totalBytesWritten bytes)")
            listener?.onPlaybackCompleted()

        } catch (e: Exception) {
            Log.e(appTag, "Error during playback: ${e.message}", e)
            listener?.onPlaybackError("Playback error: ${e.message}")
        }
    }

    /**
     * Finalize streaming (called when all chunks received)
     */
    fun finalizeStreaming() {
        try {
            Log.d(appTag, "Finalizing streaming (queue size: ${audioChunkQueue.size})")

            // Mark stream as complete
            isStreamComplete = true

            // If playback hasn't started yet (no chunks received), start it now
            // so the thread can properly complete
            if (!isPlaying && audioChunkQueue.isEmpty()) {
                Log.d(appTag, "No audio data received, skipping playback")
                listener?.onPlaybackCompleted()
            } else if (!isPlaying && !audioChunkQueue.isEmpty()) {
                // Start playback with whatever chunks we have
                startStreamingPlayback()
            }
        } catch (e: Exception) {
            Log.e(appTag, "Error finalizing streaming: ${e.message}", e)
        }
    }

    /**
     * Clean up playback resources
     */
    private fun cleanupPlaybackResources() {
        try {
            // Clean up AudioTrack
            audioTrack?.apply {
                try {
                    // Pause first to stop playback immediately
                    pause()
                    // Flush any buffered audio data to prevent fragments from playing
                    flush()
                    // Now stop and release
                    stop()
                } catch (e: IllegalStateException) {
                    Log.w(appTag, "AudioTrack already stopped: ${e.message}")
                }
                release()
            }
            audioTrack = null

            isPlaying = false
            Log.d(appTag, "Playback resources cleaned up")
        } catch (e: Exception) {
            Log.e(appTag, "Error cleaning up playback resources: ${e.message}", e)
        }
    }

    /**
     * Stop playback and release resources
     */
    fun stop() {
        try {
            Log.d(appTag, "Stopping playback")

            shouldContinuePlaying = false
            isStreamComplete = true // Signal that no more data is coming

            // Wait for playback thread to finish
            playbackThread?.join(2000)
            playbackThread = null

            cleanupPlaybackResources()

            // Clear the queue
            audioChunkQueue.clear()

            isPlaying = false
        } catch (e: Exception) {
            Log.e(appTag, "Error stopping playback: ${e.message}", e)
        }
    }

    /**
     * Release all resources
     */
    fun release() {
        stop()
        listener = null
        Log.d(appTag, "StreamingAudioPlayer released")
    }
}
