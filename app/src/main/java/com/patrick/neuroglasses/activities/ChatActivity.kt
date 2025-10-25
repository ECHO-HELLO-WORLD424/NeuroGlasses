package com.patrick.neuroglasses.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.patrick.neuroglasses.R
import com.patrick.neuroglasses.helpers.OpenAIHelper
import com.patrick.neuroglasses.helpers.StreamingAudioPlayer
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.hardware.camera2.*
import android.graphics.ImageFormat
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread

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

    // Helpers
    private lateinit var openAIHelper: OpenAIHelper
    private lateinit var streamingAudioPlayer: StreamingAudioPlayer

    // Camera
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // Audio Recording
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val audioBuffer = ByteArrayOutputStream()
    private var recordingThread: Thread? = null

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

        // Setup camera
        setupCamera()
    }

    private fun enableFullscreenMode() {
        actionBar?.hide()
        supportActionBar?.hide()

        // Use WindowCompat for modern fullscreen (API 30+)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Disabled: Full screen mode doesn't align with glasses orientation
        // if (hasFocus) {
        //     enableFullscreenMode()
        // }
    }

    private fun initializeViews() {
        imagePreview = findViewById(R.id.imagePreview)
        chatScrollView = findViewById(R.id.chatScrollView)
        chatTextView = findViewById(R.id.chatTextView)
        inputEditText = findViewById(R.id.inputEditText)
        sendButton = findViewById(R.id.sendButton)
        val settingsButton: Button = findViewById(R.id.settingsButton)

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
        // Initialize OpenAI helper
        openAIHelper = OpenAIHelper(this, appTag)
        openAIHelper.setListener(object : OpenAIHelper.OpenAIListener {
            override fun onAsrComplete(text: String) {
                runOnUiThread {
                    appendToChat("You (voice): $text\n")
                    Log.i(appTag, "ASR completed: $text")
                    sendToOpenAI(text)
                }
            }

            override fun onAsrFailed(error: String) {
                runOnUiThread {
                    appendToChat("ASR Error: $error\n")
                    Toast.makeText(this@ChatActivity, "ASR failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "ASR failed: $error")
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
                    } catch (e: Exception) {
                        Log.e(appTag, "Error playing TTS audio: ${e.message}", e)
                        Toast.makeText(this@ChatActivity, "Failed to play audio: ${e.message}", Toast.LENGTH_SHORT).show()
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

    private fun setupCamera() {
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        // Start background thread for camera operations
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Camera permission not granted", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cameraId = cameraManager?.cameraIdList?.get(0) ?: return

            cameraManager?.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    Log.i(appTag, "Camera opened")
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice?.close()
                    cameraDevice = null
                    Log.w(appTag, "Camera disconnected")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    cameraDevice?.close()
                    cameraDevice = null
                    Log.e(appTag, "Camera error: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(appTag, "Failed to open camera: ${e.message}", e)
        }
    }

    private fun takePhoto() {
        val camera = cameraDevice
        if (camera == null) {
            openCamera()
            // Retry after a short delay
            backgroundHandler?.postDelayed({ takePhoto() }, 500)
            return
        }

        try {
            // Close existing capture session and image reader to prevent memory leaks
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            imageReader?.close()
            imageReader = null

            // Create image reader
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1)
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    image.close()

                    // Recycle old bitmap to free memory
                    capturedImage?.recycle()

                    // Decode bitmap
                    capturedImage = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    runOnUiThread {
                        imagePreview.setImageBitmap(capturedImage)
                        Log.i(appTag, "Photo captured")
                    }
                }
            }, backgroundHandler)

            // Create capture request
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequest.addTarget(imageReader!!.surface)

            // Create capture session using modern SessionConfiguration API
            val outputConfiguration = OutputConfiguration(imageReader!!.surface)
            val sessionConfiguration = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                listOf(outputConfiguration),
                { command -> backgroundHandler?.post(command) },
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        session.capture(captureRequest.build(), null, backgroundHandler)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(appTag, "Camera capture session configuration failed")
                    }
                }
            )
            camera.createCaptureSession(sessionConfiguration)
        } catch (e: Exception) {
            Log.e(appTag, "Failed to take photo: ${e.message}", e)
        }
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission not granted", Toast.LENGTH_SHORT).show()
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

        runOnUiThread {
            appendToChat("Recording audio...\n")
        }
    }

    private fun stopAudioRecording(): File? {
        isRecording = false
        recordingThread?.join()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null

        if (audioBuffer.size() == 0) {
            return null
        }

        // Save as WAV file
        val audioDir = getExternalFilesDir("audio_recordings") ?: filesDir
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
            return audioFile
        } catch (e: Exception) {
            Log.e(appTag, "Failed to save audio: ${e.message}", e)
            return null
        }
    }

    private fun writeWavHeader(out: FileOutputStream, audioDataSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int) {
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
            takePhoto()
            // Wait a bit for camera, then send
            backgroundHandler?.postDelayed({
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
        chatTextView.text = chatHistory.toString()

        // Scroll to bottom
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun updateLastLine(text: String) {
        // Update the last line (for streaming)
        val lines = chatHistory.toString().split("\n").toMutableList()
        if (lines.isNotEmpty()) {
            lines[lines.lastIndex] = text

            // Update both the display and the history
            val updatedText = lines.joinToString("\n")
            chatTextView.text = updatedText

            // Rebuild chatHistory to keep it in sync
            chatHistory.clear()
            chatHistory.append(updatedText)
        }

        // Scroll to bottom
        chatScrollView.post {
            chatScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onResume() {
        super.onResume()

        // Open camera when activity resumes
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }

        openCamera()
    }

    override fun onPause() {
        super.onPause()

        // Stop recording if active
        if (isRecording) {
            stopAudioRecording()
        }

        // Close camera
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()

        // Stop background thread
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(appTag, "Error stopping background thread", e)
        }

        // Release resources
        openAIHelper.release()
        streamingAudioPlayer.release()

        // Recycle captured image to free memory
        capturedImage?.recycle()
        capturedImage = null

        // Clean up temp files
        val audioDir = getExternalFilesDir("audio_recordings")
        audioDir?.listFiles()?.forEach { it.delete() }

        val ttsDir = getExternalFilesDir("tts_audio")
        ttsDir?.listFiles()?.forEach { it.delete() }
    }
}
