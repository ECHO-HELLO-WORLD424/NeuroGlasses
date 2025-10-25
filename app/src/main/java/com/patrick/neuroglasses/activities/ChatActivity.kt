package com.patrick.neuroglasses.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.patrick.neuroglasses.R
import com.patrick.neuroglasses.helpers.OpenAIHelper
import com.patrick.neuroglasses.helpers.StreamingAudioPlayer
import com.patrick.neuroglasses.helpers.CameraHelper
import com.patrick.neuroglasses.helpers.AudioRecordingHelper
import java.io.File

/**
 * Chat Activity for native AR Glasses
 *
 * Provides a simple chat interface optimized for AR glasses (480x400 display)
 * Workflow:
 * 1. User enters text or uses voice input
 * 2. Optional: Take photo with glasses camera
 * 3. Send to AI (OpenAI Vision LLM)
 * 4. Receive streaming text response
 * 5. Convert to speech (TTS)
 * 6. Play audio and display text
 */
class ChatActivity : AppCompatActivity() {
    private val appTag = "ChatActivity"

    // UI Components
    private lateinit var imagePreview: ImageView
    private lateinit var chatScrollView: ScrollView
    private lateinit var chatTextView: TextView
    private lateinit var inputEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var recordButton: Button
    private lateinit var settingsButton: Button

    // Helpers
    private lateinit var openAIHelper: OpenAIHelper
    private lateinit var streamingAudioPlayer: StreamingAudioPlayer
    private lateinit var cameraHelper: CameraHelper
    private lateinit var audioRecordingHelper: AudioRecordingHelper

    // State
    private var capturedImage: Bitmap? = null
    private var chatHistory = StringBuilder()
    private var streamingBuffer = StringBuilder()
    private var isCurrentlyStreaming = false

    // Permissions
    private val PERMISSION_REQUEST_CODE = 100
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable fullscreen immersive mode for AR glasses
        // Disabled: Full screen mode doesn't align with glasses orientation
        // enableFullscreenMode()

        setContentView(R.layout.activity_chat)

        // Initialize views
        initializeViews()

        // Initialize helpers
        initializeHelpers()

        // Request permissions
        checkPermissions()

        // Setup back button handler for proper exit on AR glasses
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                Log.i(appTag, "Back pressed - cleaning up and exiting app")
                cleanupAndExit()
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Disabled: Full screen mode doesn't align with glasses orientation
        // if (hasFocus) {
        //     enableFullscreenMode()
        // }
    }

    private fun handleRecordButtonClick() {
        // Check if ASR is enabled in settings
        val useAsr = SettingsActivity.getUseAsr(this)
        if (!useAsr) {
            Toast.makeText(this, "ASR is disabled. Enable it in settings.", Toast.LENGTH_SHORT).show()
            return
        }

        // Don't trigger if already recording
        if (audioRecordingHelper.isRecording()) {
            Toast.makeText(this, "Already recording...", Toast.LENGTH_SHORT).show()
            return
        }

        // Start ASR recording
        triggerAsrRecording()
    }


    private fun cleanupTempFiles() {
        try {
            // Clean up ASR audio recordings
            val audioDir = getExternalFilesDir("audio_recordings")
            audioDir?.listFiles()?.forEach { file ->
                try {
                    if (file.delete()) {
                        Log.d(appTag, "Deleted temp audio file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(appTag, "Error deleting audio file: ${file.name}", e)
                }
            }

            // Clean up TTS audio files
            val ttsDir = getExternalFilesDir("tts_audio")
            ttsDir?.listFiles()?.forEach { file ->
                try {
                    if (file.delete()) {
                        Log.d(appTag, "Deleted TTS file: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.e(appTag, "Error deleting TTS file: ${file.name}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(appTag, "Error during temp files cleanup: ${e.message}", e)
        }
    }

    private fun triggerAsrRecording() {
        Log.i(appTag, "ASR triggered! Starting 4-second recording...")
        appendToChat("🎤 Recording for 4 seconds...\n")

        // Update button appearance
        recordButton.isEnabled = false
        recordButton.alpha = 0.5f

        audioRecordingHelper.startAsrRecording { audioFile ->
            runOnUiThread {
                // Re-enable button
                recordButton.isEnabled = true
                recordButton.alpha = 1.0f

                if (audioFile != null) {
                    Log.i(appTag, "Recording complete, sending to ASR API: ${audioFile.absolutePath}")
                    appendToChat("Processing speech...\n")
                    openAIHelper.callAsrAPI(audioFile)
                } else {
                    Log.e(appTag, "Recording failed - no audio data")
                    appendToChat("Recording failed\n")
                }
            }
        }
    }

    private fun initializeViews() {
        imagePreview = findViewById(R.id.imagePreview)
        chatScrollView = findViewById(R.id.chatScrollView)
        chatTextView = findViewById(R.id.chatTextView)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        recordButton = findViewById(R.id.recordButton)
        settingsButton = findViewById(R.id.settingsButton)

        // Recording button click listener
        recordButton.setOnClickListener {
            handleRecordButtonClick()
        }

        // Settings button click listener
        settingsButton.setOnClickListener {
            startActivity(android.content.Intent(this, SettingsActivity::class.java))
        }

        // Send button click listener
        sendButton.setOnClickListener {
            val message = inputEditText.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                inputEditText.text.clear()
            }
        }

        // Handle Enter key to send message
        inputEditText.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }

        // Handle IME action (for virtual keyboards with Send action)
        inputEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun initializeHelpers() {
        // Initialize Camera helper
        cameraHelper = CameraHelper(this, appTag)
        cameraHelper.setListener(object : CameraHelper.CameraListener {
            override fun onPhotoCaptured(bitmap: Bitmap) {
                runOnUiThread {
                    // Recycle old bitmap to free memory
                    capturedImage?.recycle()
                    capturedImage = bitmap
                    imagePreview.setImageBitmap(capturedImage)
                    Log.i(appTag, "Photo captured")
                }
            }

            override fun onCameraError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Camera error: $error", Toast.LENGTH_SHORT).show()
                }
            }
        })
        cameraHelper.initialize()

        // Initialize Audio Recording helper
        audioRecordingHelper = AudioRecordingHelper(this, appTag)

        // Initialize OpenAI helper
        openAIHelper = OpenAIHelper(this, appTag)
        openAIHelper.setListener(object : OpenAIHelper.OpenAIListener {
            override fun onAsrComplete(text: String) {
                runOnUiThread {
                    Log.i(appTag, "ASR completed: $text")
                    appendToChat("✓ Speech recognized: $text\n")
                    // Put recognized text in input field
                    inputEditText.setText(text)
                    Toast.makeText(this@ChatActivity, "Speech recognized", Toast.LENGTH_SHORT).show()

                    // Clean up audio file immediately after ASR completes
                    audioRecordingHelper.cleanupAsrAudioFile()
                }
            }

            override fun onAsrFailed(error: String) {
                runOnUiThread {
                    appendToChat("ASR Error: $error\n")
                    Toast.makeText(this@ChatActivity, "ASR failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "ASR failed: $error")

                    // Clean up audio file even on failure
                    audioRecordingHelper.cleanupAsrAudioFile()
                }
            }

            override fun onOpenAIStreamingStarted() {
                runOnUiThread {
                    Log.i(appTag, "OpenAI streaming started")
                    streamingBuffer.clear()
                    isCurrentlyStreaming = true
                }
            }

            override fun onOpenAIStreamingChunk(chunk: String, isComplete: Boolean) {
                runOnUiThread {
                    if (!isCurrentlyStreaming) {
                        Log.w(appTag, "Received chunk but not streaming - ignoring")
                        return@runOnUiThread
                    }

                    if (chunk.isNotEmpty()) {
                        streamingBuffer.append(chunk)
                        // Update display with current streaming content
                        chatTextView.text = chatHistory.toString() + "AI: $streamingBuffer"
                    }

                    if (isComplete && isCurrentlyStreaming) {
                        // Finalize the AI response in chat history
                        appendToChat("AI: $streamingBuffer\n")
                        isCurrentlyStreaming = false
                        Log.i(appTag, "Streaming completed")
                    }
                }
            }

            override fun onOpenAIResponse(response: String) {
                runOnUiThread {
                    Log.i(appTag, "OpenAI response: $response")

                    // Check if TTS is enabled in settings
                    val useTts = SettingsActivity.getUseTts(this@ChatActivity)
                    if (useTts) {
                        // Convert response to speech
                        val audioDir = getExternalFilesDir("tts_audio") ?: filesDir
                        openAIHelper.callTtsAPI(response, audioDir)
                    }
                }
            }

            override fun onOpenAIFailed(error: String) {
                runOnUiThread {
                    appendToChat("AI Error: $error\n")
                    Toast.makeText(this@ChatActivity, "AI failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "OpenAI failed: $error")
                }
            }

            override fun onTtsComplete(audioFile: File) {
                runOnUiThread {
                    Log.i(appTag, "TTS complete: ${audioFile.absolutePath}")

                    // Play the complete audio file using StreamingAudioPlayer
                    try {
                        // Initialize the player
                        streamingAudioPlayer.initializeStreaming()

                        // Read the complete audio file
                        val audioData = audioFile.readBytes()
                        Log.i(appTag, "Playing complete audio file: ${audioData.size} bytes")

                        // Feed the entire file as a single chunk
                        streamingAudioPlayer.addChunk(audioData)

                        // Finalize to start playback
                        streamingAudioPlayer.finalizeStreaming()

                        // Delete TTS file after reading to free storage space
                        // Delay slightly to ensure file is fully read
                        cameraHelper.getBackgroundHandler()?.postDelayed({
                            try {
                                if (audioFile.exists() && audioFile.delete()) {
                                    Log.d(appTag, "Deleted TTS file after playback: ${audioFile.name}")
                                }
                            } catch (e: Exception) {
                                Log.e(appTag, "Error deleting TTS file: ${e.message}", e)
                            }
                        }, 1000)
                    } catch (e: Exception) {
                        Log.e(appTag, "Error playing TTS audio: ${e.message}", e)
                        Toast.makeText(this@ChatActivity, "Failed to play audio: ${e.message}", Toast.LENGTH_SHORT).show()
                        // Try to delete the file even on error
                        try {
                            audioFile.delete()
                        } catch (ex: Exception) {
                            Log.e(appTag, "Error deleting failed TTS file: ${ex.message}", ex)
                        }
                    }
                }
            }

            override fun onTtsFailed(error: String) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "TTS failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "TTS failed: $error")
                }
            }

            override fun onTtsStreamingStarted() {
                runOnUiThread {
                    Log.i(appTag, "TTS streaming started")
                    streamingAudioPlayer.initializeStreaming()
                }
            }

            override fun onTtsStreamingChunk(audioChunk: ByteArray, isComplete: Boolean) {
                runOnUiThread {
                    if (audioChunk.isNotEmpty()) {
                        streamingAudioPlayer.addChunk(audioChunk)
                    }

                    if (isComplete) {
                        streamingAudioPlayer.finalizeStreaming()
                        Log.i(appTag, "TTS streaming finalized")
                    }
                }
            }
        })

        // Initialize Streaming Audio Player
        streamingAudioPlayer = StreamingAudioPlayer(appTag)
        streamingAudioPlayer.setListener(object : StreamingAudioPlayer.PlaybackListener {
            override fun onPlaybackStarted() {
                Log.i(appTag, "Audio playback started")
            }

            override fun onPlaybackCompleted() {
                Log.i(appTag, "Audio playback completed")
            }

            override fun onPlaybackError(error: String) {
                runOnUiThread {
                    Toast.makeText(this@ChatActivity, "Playback error: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "Audio playback error: $error")
                }
            }
        })
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (!allGranted) {
                Toast.makeText(this, "Permissions required for camera and microphone", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun sendMessage(message: String) {
        // Clear previous conversation for new request
        chatHistory.clear()
        streamingBuffer.clear()
        isCurrentlyStreaming = false

        // Clear the TextView to ensure clean state
        chatTextView.text = ""

        // Display new user message
        appendToChat("You: $message\n")

        // Clear previous image for new request and recycle to free memory
        capturedImage?.recycle()
        capturedImage = null
        imagePreview.setImageBitmap(null)

        // Check if we should include image
        val includeImage = SettingsActivity.getIncludeImage(this)
        if (includeImage) {
            cameraHelper.takePhoto()
            // Wait a bit for camera, then send
            cameraHelper.getBackgroundHandler()?.postDelayed({
                sendToOpenAI(message)
            }, 1000)
        } else {
            sendToOpenAI(message)
        }
    }

    private fun sendToOpenAI(instruction: String) {
        val includeImage = SettingsActivity.getIncludeImage(this)
        val imageToSend = if (includeImage) capturedImage else null

        Log.d(appTag, "Sending to OpenAI - instruction: $instruction, hasImage: ${imageToSend != null}")
        openAIHelper.callOpenAIStreaming(instruction, imageToSend)
    }

    private fun appendToChat(text: String) {
        chatHistory.append(text)

        // Prevent unbounded memory growth - limit chat history to 10KB
        // (This is a safety mechanism; sendMessage already clears history for each new request)
        if (chatHistory.length > 10240) {
            // Keep only the last 8KB of text
            val keepText = chatHistory.substring(chatHistory.length - 8192)
            chatHistory.clear()
            chatHistory.append("...[earlier messages truncated]...\n")
            chatHistory.append(keepText)
            Log.w(appTag, "Chat history truncated to prevent memory overflow")
        }

        chatTextView.text = chatHistory.toString()

        // Scroll to bottom
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onResume() {
        super.onResume()

        // Ensure camera background thread is running and open camera
        cameraHelper.ensureBackgroundThread()
        cameraHelper.openCamera()
    }

    override fun onPause() {
        super.onPause()

        // Stop recording if active
        if (audioRecordingHelper.isRecording()) {
            audioRecordingHelper.stopRecording()
        }

        // Remove any pending handler callbacks to prevent leaks
        cameraHelper.clearCallbacks()

        // Close camera
        cameraHelper.closeCamera()
    }

    private fun cleanupAndExit() {
        // Clear listeners to prevent memory leaks
        openAIHelper.setListener(null)
        streamingAudioPlayer.setListener(null)
        cameraHelper.setListener(null)
        audioRecordingHelper.setListener(null)

        // Release all helpers
        audioRecordingHelper.release()
        streamingAudioPlayer.release()
        cameraHelper.release()

        // Recycle bitmap to free memory
        capturedImage?.recycle()
        capturedImage = null

        // Clear image preview to release bitmap reference
        imagePreview.setImageBitmap(null)

        // Clean up temp files immediately
        cleanupTempFiles()

        // Use finishAffinity() to completely exit the app and clear all activities
        finishAffinity()

        Log.i(appTag, "App exit complete")
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clear listeners to prevent memory leaks
        openAIHelper.setListener(null)
        streamingAudioPlayer.setListener(null)
        cameraHelper.setListener(null)
        audioRecordingHelper.setListener(null)

        // Release resources
        audioRecordingHelper.release()
        openAIHelper.release()
        streamingAudioPlayer.release()
        cameraHelper.release()

        // Recycle captured image to free memory
        capturedImage?.recycle()
        capturedImage = null

        // Clear image preview to release bitmap reference
        imagePreview.setImageBitmap(null)

        // Clean up temp files
        cleanupTempFiles()
    }
}
