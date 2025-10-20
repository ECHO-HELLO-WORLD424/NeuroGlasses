package com.patrick.neuroglasses.helpers

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue

/**
 * Streaming audio player for MP3/audio chunks
 * Uses in-memory queue with MediaCodec + AudioTrack for true streaming playback
 */
class StreamingAudioPlayer(private val appTag: String = "StreamingAudioPlayer") {

    // Queue to hold incoming audio chunks
    private val audioChunkQueue = LinkedBlockingQueue<ByteArray>()

    private var audioTrack: AudioTrack? = null
    private var mediaCodec: MediaCodec? = null

    private var isPlaying = false
    private var playbackThread: Thread? = null
    private var shouldContinuePlaying = true
    private var isStreamComplete = false

    // Audio format parameters (will be set when codec is configured)
    private var sampleRate = 24000 // Default, will be updated from MediaFormat
    private var channelCount = 1 // Default, will be updated from MediaFormat

    /**
     * Listener interface for playback events
     */
    interface PlaybackListener {
        fun onPlaybackStarted()
        fun onPlaybackCompleted()
        fun onPlaybackError(error: String)
    }

    private var listener: PlaybackListener? = null

    fun setListener(listener: PlaybackListener) {
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
     * Play audio using MediaCodec and AudioTrack with in-memory queue
     */
    private fun playStreamingAudio() {
        try {
            // Setup MediaCodec for MP3 decoding
            val mime = "audio/mpeg" // MP3
            mediaCodec = MediaCodec.createDecoderByType(mime)

            // Configure with basic MP3 format
            // We'll get the actual format from the codec after we feed it data
            val format = MediaFormat.createAudioFormat(mime, sampleRate, channelCount)
            mediaCodec?.configure(format, null, null, 0)
            mediaCodec?.start()

            Log.i(appTag, "MediaCodec started for MP3 decoding")

            // Track if we've initialized AudioTrack yet
            var audioTrackInitialized = false

            // Notify playback started
            listener?.onPlaybackStarted()

            val info = MediaCodec.BufferInfo()
            var totalChunksProcessed = 0
            var inputEOS = false
            var outputEOS = false

            // Main decode loop
            while (shouldContinuePlaying && !outputEOS) {

                // INPUT: Feed encoded MP3 data to codec
                if (!inputEOS) {
                    val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000) ?: -1

                    if (inputBufferIndex >= 0) {
                        val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)

                        if (inputBuffer != null) {
                            inputBuffer.clear()

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

                            if (chunk != null) {
                                // We have data - feed it to the codec
                                inputBuffer.put(chunk)
                                mediaCodec?.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    chunk.size,
                                    0,
                                    0
                                )
                                totalChunksProcessed++
                                Log.v(appTag, "Fed chunk $totalChunksProcessed to codec: ${chunk.size} bytes")
                            } else if (isStreamComplete) {
                                // Stream is complete and no more chunks - signal EOS
                                Log.d(appTag, "Signaling end of stream to codec")
                                mediaCodec?.queueInputBuffer(
                                    inputBufferIndex,
                                    0,
                                    0,
                                    0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEOS = true
                            } else {
                                // No data available yet but stream not complete - queue empty buffer
                                mediaCodec?.queueInputBuffer(inputBufferIndex, 0, 0, 0, 0)
                            }
                        }
                    }
                }

                // OUTPUT: Get decoded PCM data and write to AudioTrack
                val outputBufferIndex = mediaCodec?.dequeueOutputBuffer(info, 10000) ?: -1

                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        // Format changed - initialize AudioTrack with actual parameters
                        val outputFormat = mediaCodec?.outputFormat
                        if (outputFormat != null && !audioTrackInitialized) {
                            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                            Log.i(appTag, "Output format: sample rate=$sampleRate, channels=$channelCount")

                            // Setup AudioTrack with actual format
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
                            audioTrackInitialized = true
                            Log.i(appTag, "AudioTrack initialized and playing")
                        }
                    }

                    outputBufferIndex >= 0 -> {
                        val outputBuffer = mediaCodec?.getOutputBuffer(outputBufferIndex)

                        if (outputBuffer != null && info.size > 0 && audioTrackInitialized) {
                            // Write PCM data to AudioTrack
                            val pcmData = ByteArray(info.size)
                            outputBuffer.get(pcmData)

                            val written = audioTrack?.write(pcmData, 0, pcmData.size) ?: 0
                            Log.v(appTag, "Wrote $written bytes to AudioTrack")
                        }

                        mediaCodec?.releaseOutputBuffer(outputBufferIndex, false)

                        // Check for end of stream
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            Log.d(appTag, "Reached end of output stream")
                            outputEOS = true
                        }
                    }
                }
            }

            // Wait a bit for AudioTrack to finish playing buffered data
            if (audioTrackInitialized) {
                Thread.sleep(200)
            }

            Log.i(appTag, "Playback completed successfully (processed $totalChunksProcessed chunks)")
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
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaCodec = null

            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null

            isPlaying = false
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
     * Check if currently playing
     */
    fun isPlaying(): Boolean = isPlaying

    /**
     * Release all resources
     */
    fun release() {
        stop()
        listener = null
        Log.d(appTag, "StreamingAudioPlayer released")
    }
}
