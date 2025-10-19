package com.patrick.neuroglasses.activities

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import java.io.File
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.patrick.neuroglasses.R
import com.patrick.neuroglasses.helpers.AICameraHelper
import com.patrick.neuroglasses.helpers.AudioHelper
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.AiEventListener
import com.rokid.cxr.client.utils.ValueUtil
import java.util.Locale

class AITestActivity : AppCompatActivity() {
    private val appTag = "AITestActivity"

    private lateinit var titleTextView: TextView
    private lateinit var toggleAiSceneButton: Button
    private lateinit var aiSceneStatusTextView: TextView
    private lateinit var takePhotoButton: Button
    private lateinit var photoStatusTextView: TextView
    private lateinit var photoImageView: ImageView
    private lateinit var toggleAudioButton: Button
    private lateinit var audioStatusTextView: TextView
    private lateinit var playAudioButton: Button

    private var isAiSceneOpen = false

    // Helper classes
    private lateinit var aiCameraHelper: AICameraHelper
    private lateinit var audioHelper: AudioHelper

    // Media player for audio playback
    private var mediaPlayer: MediaPlayer? = null

    // AI Camera listener implementation
    private val aiCameraListener = object : AICameraHelper.AICameraListener {
        override fun onCameraOpened(message: String) {
            runOnUiThread {
                updatePhotoStatus(message)
                Toast.makeText(this@AITestActivity, "Camera opened successfully", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCameraOpenFailed(message: String) {
            runOnUiThread {
                updatePhotoStatus(message)
                Toast.makeText(this@AITestActivity, "Failed to open camera", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onPhotoStatusUpdate(message: String) {
            runOnUiThread {
                updatePhotoStatus(message)
            }
        }

        override fun onPhotoSuccess(bitmap: Bitmap, dataSize: Int, width: Int, height: Int) {
            runOnUiThread {
                photoImageView.setImageBitmap(bitmap)
                updatePhotoStatus("Photo displayed: $dataSize bytes, ${width}x${height}")
                Toast.makeText(
                    this@AITestActivity,
                    "Photo captured and displayed! ${width}x${height}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        override fun onPhotoFailed(message: String) {
            runOnUiThread {
                updatePhotoStatus(message)
                Toast.makeText(this@AITestActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Audio recording listener implementation
    private val audioRecordingListener = object : AudioHelper.AudioRecordingListener {
        override fun onAudioStreamStarted(codecType: Int, streamType: String?) {
            runOnUiThread {
                Toast.makeText(
                    this@AITestActivity,
                    "Audio stream started: ${if (codecType == 1) "PCM" else "OPUS"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        override fun onAudioDataReceived(chunkSize: Int, totalChunks: Int, totalBytes: Long) {
            runOnUiThread {
                val sizeKB = totalBytes / 1024.0
                updateAudioStatus("Recording: ${String.format(Locale.US, "%.2f", sizeKB)} KB ($totalChunks chunks)")
            }
        }

        override fun onAudioRecordingStarted(message: String) {
            runOnUiThread {
                updateAudioStatus(message)
                updateAudioButtonText()
                Toast.makeText(this@AITestActivity, "Audio recording started", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onAudioRecordingFailed(message: String) {
            runOnUiThread {
                updateAudioStatus(message)
                Toast.makeText(this@AITestActivity, message, Toast.LENGTH_SHORT).show()
            }
        }

        override fun onAudioRecordingStopped(savedFilePath: String?) {
            runOnUiThread {
                if (savedFilePath != null) {
                    val fileName = File(savedFilePath).name
                    updateAudioStatus("Saved: $fileName")
                    Toast.makeText(
                        this@AITestActivity,
                        "Audio saved to:\n$fileName",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val stats = audioHelper.getRecordingStats()
                    if (stats.totalChunks == 0) {
                        updateAudioStatus("Recording stopped (no data)")
                        Toast.makeText(this@AITestActivity, "No audio data recorded", Toast.LENGTH_SHORT).show()
                    } else {
                        updateAudioStatus("Recording stopped (save failed)")
                        Toast.makeText(this@AITestActivity, "Failed to save audio file", Toast.LENGTH_SHORT).show()
                    }
                }
                updateAudioButtonText()
            }
        }

        override fun onAudioStatusUpdate(message: String) {
            runOnUiThread {
                updateAudioStatus(message)
            }
        }
    }

    // AI event listener to receive events from glasses
    private val aiEventListener = object : AiEventListener {
        /**
         * When the AI key is long pressed on the glasses
         */
        override fun onAiKeyDown() {
            runOnUiThread {
                Log.d(appTag, "AI key pressed down - AI scene opening")
                isAiSceneOpen = true
                updateButtonText()
                updateStatus("AI key pressed - scene opening")

                // Auto-start audio recording when AI scene opens
                startAudioRecording()
            }
        }

        /**
         * When the AI key is released (currently has no effect)
         */
        override fun onAiKeyUp() {
            runOnUiThread {
                Log.d(appTag, "AI key released")
                updateStatus("AI key released")
            }
        }

        /**
         * When the AI Scene exits (from glasses side)
         */
        override fun onAiExit() {
            runOnUiThread {
                Log.d(appTag, "AI scene exited from glasses")
                isAiSceneOpen = false
                updateButtonText()
                updateStatus("AI scene exited from glasses")
                Toast.makeText(this@AITestActivity, "AI scene closed by glasses", Toast.LENGTH_SHORT).show()

                // Auto-stop recording, save file, and clear cache when AI scene exits
                stopAndSaveAudioRecording()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_second)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.second_main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        titleTextView = findViewById(R.id.secondTitleTextView)
        toggleAiSceneButton = findViewById(R.id.toggleAiSceneButton)
        aiSceneStatusTextView = findViewById(R.id.aiSceneStatusTextView)
        takePhotoButton = findViewById(R.id.takePhotoButton)
        photoStatusTextView = findViewById(R.id.photoStatusTextView)
        photoImageView = findViewById(R.id.photoImageView)
        toggleAudioButton = findViewById(R.id.toggleAudioButton)
        audioStatusTextView = findViewById(R.id.audioStatusTextView)
        playAudioButton = findViewById(R.id.playAudioButton)

        // Initialize helper classes
        aiCameraHelper = AICameraHelper(appTag)
        aiCameraHelper.setListener(aiCameraListener)

        audioHelper = AudioHelper(this, appTag)
        audioHelper.setListener(audioRecordingListener)

        // Set AI event listener
        setAiEventListener(true)

        // Set audio stream listener to receive audio from glasses.
        // Recording will start/stop automatically based on AI scene state
        audioHelper.setAudioStreamListener(true)

        // Setup button click listeners
        toggleAiSceneButton.setOnClickListener {
            toggleAiScene()
        }

        takePhotoButton.setOnClickListener {
            takePhoto()
        }

        toggleAudioButton.setOnClickListener {
            toggleAudioRecording()
        }

        playAudioButton.setOnClickListener {
            playLastRecording()
        }

        updateStatus("Ready to open AI scene")
        updatePhotoStatus("Ready to take photos")
        updateAudioStatus("Ready - recording starts with AI scene")
        updateAudioButtonText()
    }

    /**
     * Set or remove the AI event listener
     * @param set true: set the listener, false: remove the listener
     */
    private fun setAiEventListener(set: Boolean) {
        CxrApi.getInstance().setAiEventListener(if (set) aiEventListener else null)
        Log.d(appTag, "AI event listener ${if (set) "set" else "removed"}")
    }

    /**
     * Toggle the AI scene on/off
     */
    private fun toggleAiScene() {
        if (isAiSceneOpen) {
            // Close AI scene by sending exit event
            closeAiScene()
        } else {
            // Note: Opening AI scene is typically triggered by physical button on glasses
            // This is just a placeholder to show the expected behavior
            updateStatus("AI scene should be opened by long-pressing the button on the glasses")
            Toast.makeText(this, "Please long-press the AI button on the glasses to open the scene", Toast.LENGTH_LONG).show()

            // For testing purposes, we'll simulate it being open
            // In real usage, this would be triggered by onAiKeyDown event
            isAiSceneOpen = true
            updateButtonText()

            // Auto-start audio recording when AI scene opens
            startAudioRecording()
        }
    }

    /**
     * Send exit event to close the AI scene on glasses
     * @return the status of the exit operation
     */
    private fun closeAiScene() {
        val status = CxrApi.getInstance().sendExitEvent()

        when (status) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.d(appTag, "AI scene exit request sent successfully")
                isAiSceneOpen = false
                updateButtonText()
                updateStatus("AI scene exit request sent")
                Toast.makeText(this, "Closing AI scene...", Toast.LENGTH_SHORT).show()

                // Auto-stop recording, save file, and clear cache when AI scene exits from app
                stopAndSaveAudioRecording()
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(appTag, "Previous AI scene exit request still pending")
                updateStatus("Exit request pending, please wait...")
                Toast.makeText(this, "Please wait, previous request still processing", Toast.LENGTH_SHORT).show()
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(appTag, "AI scene exit request failed")
                updateStatus("Exit request failed")
                Toast.makeText(this, "Failed to close AI scene", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Log.w(appTag, "Unknown AI scene exit status: $status")
                updateStatus("Unknown exit status")
            }
        }
    }

    /**
     * Update the button text based on AI scene state
     */
    private fun updateButtonText() {
        toggleAiSceneButton.text = if (isAiSceneOpen) {
            "Close AI Scene"
        } else {
            "Open AI Scene"
        }
    }

    /**
     * Update the status text view
     */
    private fun updateStatus(message: String) {
        aiSceneStatusTextView.text = message
    }

    /**
     * Update the photo status text view
     */
    private fun updatePhotoStatus(message: String) {
        photoStatusTextView.text = message
    }

    /**
     * Update the audio status text view
     */
    private fun updateAudioStatus(message: String) {
        audioStatusTextView.text = message
    }

    /**
     * Start audio recording (called automatically when AI scene opens)
     */
    private fun startAudioRecording() {
        if (!audioHelper.isRecording) {
            Log.i(appTag, "Auto-starting audio recording for AI scene")
            // Clear any previous cache before starting new recording
            audioHelper.clearAudioCache()
            audioHelper.openAudioRecord(codecType = 1, streamType = "AI_assistant")
        } else {
            Log.d(appTag, "Audio recording already active")
        }
    }

    /**
     * Stop audio recording, save file, and clear cache (called automatically when AI scene exits)
     */
    private fun stopAndSaveAudioRecording() {
        if (audioHelper.isRecording) {
            Log.i(appTag, "Auto-stopping audio recording - AI scene exited")
            // This will stop recording, save the file, and clear the cache
            audioHelper.closeAudioRecord("AI_assistant")
        } else {
            Log.d(appTag, "No active audio recording to stop")
        }
    }

    /**
     * Toggle audio recording on/off (for manual control if needed)
     */
    private fun toggleAudioRecording() {
        if (audioHelper.isRecording) {
            stopAndSaveAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    /**
     * Update the audio button text based on recording state
     */
    private fun updateAudioButtonText() {
        toggleAudioButton.text = if (audioHelper.isRecording) {
            "Stop Audio Recording"
        } else {
            "Start Audio Recording"
        }
    }

    /**
     * Take a photo using the glass camera in AI scene
     */
    private fun takePhoto() {
        aiCameraHelper.takePhoto()
    }

    /**
     * Play the most recently recorded audio file
     */
    private fun playLastRecording() {
        val audioPath = audioHelper.lastSavedAudioPath

        if (audioPath == null) {
            Toast.makeText(this, "No audio recording available to play", Toast.LENGTH_SHORT).show()
            Log.w(appTag, "No audio file to play")
            return
        }

        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            Toast.makeText(this, "Audio file not found", Toast.LENGTH_SHORT).show()
            Log.e(appTag, "Audio file does not exist: $audioPath")
            return
        }

        try {
            // Stop any currently playing audio
            stopAudioPlayback()

            // Create and configure MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioPath)
                prepare()

                // Set completion listener
                setOnCompletionListener {
                    Log.d(appTag, "Audio playback completed")
                    Toast.makeText(this@AITestActivity, "Playback finished", Toast.LENGTH_SHORT).show()
                    stopAudioPlayback()
                }

                // Set error listener
                setOnErrorListener { _, what, extra ->
                    Log.e(appTag, "MediaPlayer error: what=$what, extra=$extra")
                    Toast.makeText(this@AITestActivity, "Error playing audio", Toast.LENGTH_SHORT).show()
                    stopAudioPlayback()
                    true
                }

                // Start playback
                start()
            }

            val fileName = File(audioPath).name
            Toast.makeText(this, "Playing: $fileName", Toast.LENGTH_LONG).show()
            Log.i(appTag, "Started playing audio: $audioPath")

        } catch (e: Exception) {
            Log.e(appTag, "Error playing audio file", e)
            Toast.makeText(this, "Failed to play audio: ${e.message}", Toast.LENGTH_SHORT).show()
            stopAudioPlayback()
        }
    }

    /**
     * Stop audio playback and release MediaPlayer resources
     */
    private fun stopAudioPlayback() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
                Log.d(appTag, "Audio playback stopped")
            }
            release()
        }
        mediaPlayer = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(appTag, "Activity destroying - cleaning up resources")

        // Stop and release media player
        stopAudioPlayback()

        // Release helper resources
        aiCameraHelper.release()
        audioHelper.release()

        // Remove AI event listener
        setAiEventListener(false)
    }
}
