package com.patrick.neuroglasses.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.patrick.neuroglasses.R
import com.patrick.neuroglasses.helpers.AICameraHelper
import com.patrick.neuroglasses.helpers.AudioHelper
import com.patrick.neuroglasses.helpers.CustomSceneHelper
import com.patrick.neuroglasses.helpers.OpenAIHelper
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.AiEventListener
import java.io.File

/**
 * AI Chat Configuration Activity
 *
 * This activity allows users to:
 * - Configure whether to include images in AI chat
 * - Configure whether to use ASR (voice) or predefined instructions
 * - Manage predefined instructions
 * - Process AI requests when the AI key is pressed on glasses
 */
class AITestActivity : AppCompatActivity() {
    private val appTag = "AITestActivity"

    // UI Components
    private lateinit var titleTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var includeImageCheckBox: CheckBox
    private lateinit var useAsrCheckBox: CheckBox
    private lateinit var newInstructionEditText: EditText
    private lateinit var addInstructionButton: Button
    private lateinit var instructionsListView: ListView
    private lateinit var processingStatusTextView: TextView
    private lateinit var capturedImageView: ImageView
    private lateinit var showResultButton: Button

    // Helpers
    private lateinit var aiCameraHelper: AICameraHelper
    private lateinit var audioHelper: AudioHelper
    private lateinit var openAIHelper: OpenAIHelper
    private lateinit var customSceneHelper: CustomSceneHelper

    // Data
    private val predefinedInstructions = mutableListOf<String>()
    private lateinit var instructionsAdapter: ArrayAdapter<String>

    // State
    private var isAiSceneOpen = false
    private var capturedImage: Bitmap? = null
    private var recordedAudioFile: File? = null
    private var lastResultText: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // Initialize UI components
        initializeViews()

        // Initialize helpers
        initializeHelpers()

        // Setup predefined instructions
        setupInstructions()

        // Setup listeners
        setupListeners()

        updateStatus("Ready. Press AI key on glasses to start.")
    }

    private fun initializeViews() {
        titleTextView = findViewById(R.id.secondTitleTextView)
        statusTextView = findViewById(R.id.statusTextView)
        includeImageCheckBox = findViewById(R.id.includeImageCheckBox)
        useAsrCheckBox = findViewById(R.id.useAsrCheckBox)
        newInstructionEditText = findViewById(R.id.newInstructionEditText)
        addInstructionButton = findViewById(R.id.addInstructionButton)
        instructionsListView = findViewById(R.id.instructionsListView)
        processingStatusTextView = findViewById(R.id.processingStatusTextView)
        capturedImageView = findViewById(R.id.capturedImageView)
        showResultButton = findViewById(R.id.showResultButton)
    }

    private fun initializeHelpers() {
        // Initialize AI camera helper
        aiCameraHelper = AICameraHelper(appTag)
        aiCameraHelper.setListener(object : AICameraHelper.AICameraListener {
            override fun onCameraOpened(message: String) {
                Log.d(appTag, "Camera opened: $message")
            }

            override fun onCameraOpenFailed(message: String) {
                Log.e(appTag, "Camera open failed: $message")
            }

            override fun onPhotoStatusUpdate(message: String) {
                Log.d(appTag, "Photo status: $message")
            }

            override fun onPhotoSuccess(bitmap: Bitmap, dataSize: Int, width: Int, height: Int) {
                runOnUiThread {
                    capturedImage = bitmap
                    showProcessingUI(true)
                    capturedImageView.setImageBitmap(bitmap)
                    capturedImageView.visibility = View.VISIBLE
                    updateProcessingStatus("Image captured: ${width}x${height}")
                    Log.i(appTag, "Photo captured successfully: ${width}x${height}, $dataSize bytes")
                }
            }

            override fun onPhotoFailed(message: String) {
                runOnUiThread {
                    updateProcessingStatus("Image capture failed: $message")
                    Toast.makeText(this@AITestActivity, "Failed to capture image", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "Photo capture failed: $message")
                }
            }
        })

        // Initialize audio helper
        audioHelper = AudioHelper(this, appTag)
        audioHelper.setListener(object : AudioHelper.AudioRecordingListener {
            override fun onAudioStreamStarted(codecType: Int, streamType: String?) {
                Log.d(appTag, "Audio stream started: codec=$codecType, stream=$streamType")
            }

            override fun onAudioDataReceived(chunkSize: Int, totalChunks: Int, totalBytes: Long) {
                runOnUiThread {
                    updateProcessingStatus("Recording audio: ${totalBytes / 1024} KB")
                }
            }

            override fun onAudioRecordingStarted(message: String) {
                Log.i(appTag, "Audio recording started: $message")
            }

            override fun onAudioRecordingFailed(message: String) {
                runOnUiThread {
                    updateProcessingStatus("Audio recording failed: $message")
                    Toast.makeText(this@AITestActivity, "Audio recording failed", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "Audio recording failed: $message")
                }
            }

            override fun onAudioRecordingStopped(savedFilePath: String?) {
                runOnUiThread {
                    if (savedFilePath != null) {
                        recordedAudioFile = File(savedFilePath)
                        updateProcessingStatus("Audio saved: ${recordedAudioFile?.name}")
                        Log.i(appTag, "Audio recording saved: $savedFilePath")

                        // Process the request now that audio is ready
                        processAiRequest()
                    } else {
                        updateProcessingStatus("No audio recorded")
                        Log.w(appTag, "No audio data recorded")
                        // Still process request even without audio
                        processAiRequest()
                    }
                }
            }

            override fun onAudioStatusUpdate(message: String) {
                Log.d(appTag, "Audio status: $message")
            }
        })

        // Set audio stream listener
        audioHelper.setAudioStreamListener(true)

        // Initialize OpenAI helper
        openAIHelper = OpenAIHelper(this, appTag)
        openAIHelper.setListener(object : OpenAIHelper.OpenAIListener {
            override fun onAsrComplete(text: String) {
                runOnUiThread {
                    updateProcessingStatus("ASR result: $text")
                    Log.i(appTag, "ASR completed: $text")
                    // Call OpenAI with ASR text
                    sendToOpenAI(text)
                }
            }

            override fun onAsrFailed(error: String) {
                runOnUiThread {
                    updateProcessingStatus("ASR failed: $error")
                    Toast.makeText(this@AITestActivity, "ASR failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "ASR failed: $error")
                }
            }

            override fun onOpenAIResponse(response: String) {
                runOnUiThread {
                    updateProcessingStatus("AI response received")
                    Log.i(appTag, "OpenAI response: $response")

                    // Store the response
                    lastResultText = response

                    // Convert response to speech
                    updateProcessingStatus("Converting to speech...")
                    val audioDir = getExternalFilesDir("tts_audio") ?: filesDir
                    openAIHelper.callTtsAPI(response, audioDir)
                }
            }

            override fun onOpenAIFailed(error: String) {
                runOnUiThread {
                    updateProcessingStatus("OpenAI failed: $error")
                    Toast.makeText(this@AITestActivity, "OpenAI failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "OpenAI failed: $error")
                }
            }

            override fun onTtsComplete(audioFile: File) {
                runOnUiThread {
                    updateProcessingStatus("Speech generated successfully")
                    Log.i(appTag, "TTS complete: ${audioFile.absolutePath}")

                    // Set audio file in custom scene helper
                    customSceneHelper.setAudioFile(audioFile)

                    // Get the text response from lastResultText or extract from processing
                    lastResultText?.let { displayResultInCustomUI(it) }
                }
            }

            override fun onTtsFailed(error: String) {
                runOnUiThread {
                    updateProcessingStatus("TTS failed: $error")
                    Toast.makeText(this@AITestActivity, "Speech generation failed: $error", Toast.LENGTH_SHORT).show()
                    Log.e(appTag, "TTS failed: $error")

                    // Still display the text result even if TTS fails
                    lastResultText?.let { displayResultInCustomUI(it) }
                }
            }
        })

        // Initialize Custom Scene helper
        customSceneHelper = CustomSceneHelper(appTag)
        customSceneHelper.setListener(object : CustomSceneHelper.CustomSceneListener {
            override fun onSceneOpened() {
                runOnUiThread {
                    updateProcessingStatus("Result displayed on glasses")
                    Toast.makeText(this@AITestActivity, "Result displayed on glasses", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onSceneOpenFailed(errorCode: Int) {
                runOnUiThread {
                    updateProcessingStatus("Failed to display on glasses: $errorCode")
                    Toast.makeText(this@AITestActivity, "Failed to display on glasses: $errorCode", Toast.LENGTH_LONG).show()
                }
            }

            override fun onSceneClosed() {
                runOnUiThread {
                    Log.d(appTag, "Custom scene closed")
                }
            }

            override fun onSceneUpdated() {
                runOnUiThread {
                    Log.d(appTag, "Custom scene updated")
                }
            }
        })
        customSceneHelper.initializeCustomViewListener()
    }

    private fun setupInstructions() {
        // Add some default instructions
        predefinedInstructions.add("What do you see in this image?")
        predefinedInstructions.add("Describe the scene")
        predefinedInstructions.add("Identify objects in the image")
        predefinedInstructions.add("Translate the text in this image")

        // Setup adapter
        instructionsAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            predefinedInstructions
        )
        instructionsListView.adapter = instructionsAdapter

        // Handle instruction clicks (delete on long click)
        instructionsListView.setOnItemLongClickListener { _, _, position, _ ->
            showDeleteInstructionDialog(position)
            true
        }
    }

    private fun setupListeners() {
        // Add instruction button
        addInstructionButton.setOnClickListener {
            val newInstruction = newInstructionEditText.text.toString().trim()
            if (newInstruction.isNotEmpty()) {
                predefinedInstructions.add(newInstruction)
                instructionsAdapter.notifyDataSetChanged()
                newInstructionEditText.text.clear()
                Toast.makeText(this, "Instruction added", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Please enter an instruction", Toast.LENGTH_SHORT).show()
            }
        }

        // Show result button
        showResultButton.setOnClickListener {
            lastResultText?.let { result ->
                displayResultInCustomUI(result)
            }
        }

        // AI event listener
        CxrApi.getInstance().setAiEventListener(object : AiEventListener {
            override fun onAiKeyDown() {
                runOnUiThread {
                    Log.d(appTag, "AI key pressed - starting request")
                    isAiSceneOpen = true
                    onAiKeyPressed()
                }
            }

            override fun onAiKeyUp() {
                // Not used
            }

            override fun onAiExit() {
                runOnUiThread {
                    Log.d(appTag, "AI scene exited")
                    isAiSceneOpen = false
                    // Stop audio recording if active
                    if (audioHelper.isRecording) {
                        audioHelper.closeAudioRecord("AI_assistant")
                    }
                }
            }
        })
    }

    /**
     * Called when AI key is pressed on glasses
     * This starts the AI request process
     */
    private fun onAiKeyPressed() {
        updateStatus("Processing AI request...")
        showProcessingUI(true)

        // Reset state
        capturedImage = null
        recordedAudioFile = null
        capturedImageView.visibility = View.GONE

        // Always start audio recording (SDK requirement)
        startAudioRecording()

        // Capture image if configured
        if (includeImageCheckBox.isChecked) {
            updateProcessingStatus("Capturing image...")
            aiCameraHelper.takePhoto()
        } else {
            updateProcessingStatus("Skipping image capture (not enabled)")
        }
    }

    /**
     * Start audio recording
     */
    private fun startAudioRecording() {
        if (!audioHelper.isRecording) {
            Log.i(appTag, "Starting audio recording")
            audioHelper.clearAudioCache()
            audioHelper.openAudioRecord(codecType = 1, streamType = "AI_assistant")
            updateProcessingStatus("Recording audio...")
        }
    }

    /**
     * Process the AI request
     * Called after audio recording is stopped
     */
    private fun processAiRequest() {
        val useAsr = useAsrCheckBox.isChecked

        if (useAsr) {
            // Use ASR to get text from audio
            processWithASR()
        } else {
            // Show instruction selection dialog
            showInstructionSelectionDialog()
        }
    }

    /**
     * Process request using ASR
     */
    private fun processWithASR() {
        updateProcessingStatus("Processing voice with ASR...")

        if (recordedAudioFile == null) {
            Toast.makeText(this, "No audio recorded", Toast.LENGTH_SHORT).show()
            updateProcessingStatus("Error: No audio available")
            return
        }

        // Call ASR API using helper
        openAIHelper.callAsrAPI(recordedAudioFile!!)
    }

    /**
     * Show dialog to select predefined instruction
     */
    private fun showInstructionSelectionDialog() {
        if (predefinedInstructions.isEmpty()) {
            Toast.makeText(this, "No predefined instructions. Please add some first.", Toast.LENGTH_LONG).show()
            updateProcessingStatus("Error: No predefined instructions")
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Instruction")
            .setItems(predefinedInstructions.toTypedArray()) { _, which ->
                val selectedInstruction = predefinedInstructions[which]
                updateProcessingStatus("Instruction: $selectedInstruction")
                sendToOpenAI(selectedInstruction)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                updateProcessingStatus("Cancelled")
                showProcessingUI(false)
            }
            .show()
    }

    /**
     * Send request to OpenAI with instruction and optional image
     */
    private fun sendToOpenAI(instruction: String) {
        updateProcessingStatus("Sending to AI...")

        val hasImage = capturedImage != null && includeImageCheckBox.isChecked
        val imageToSend = if (hasImage) capturedImage else null

        // Call OpenAI API using helper
        openAIHelper.callOpenAI(instruction, imageToSend)
    }

    /**
     * Display result in custom UI on glasses
     */
    private fun displayResultInCustomUI(resultText: String) {
        Log.i(appTag, "Displaying result in custom UI: $resultText")

        // Store the result
        lastResultText = resultText
        showResultButton.isEnabled = true

        updateProcessingStatus("Displaying result on glasses...")

        // Use CustomSceneHelper to display
        val status = customSceneHelper.displayTextResult(resultText)
        Log.d(appTag, "Display status: $status")
    }

    /**
     * Show/hide processing UI elements
     */
    private fun showProcessingUI(show: Boolean) {
        processingStatusTextView.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            capturedImageView.visibility = View.GONE
        }
    }

    /**
     * Update status text
     */
    private fun updateStatus(message: String) {
        statusTextView.text = message
        Log.d(appTag, "Status: $message")
    }

    /**
     * Update processing status text
     */
    private fun updateProcessingStatus(message: String) {
        processingStatusTextView.text = message
        Log.d(appTag, "Processing: $message")
    }

    /**
     * Show dialog to delete instruction
     */
    private fun showDeleteInstructionDialog(position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Instruction")
            .setMessage("Delete \"${predefinedInstructions[position]}\"?")
            .setPositiveButton("Delete") { _, _ ->
                predefinedInstructions.removeAt(position)
                instructionsAdapter.notifyDataSetChanged()
                Toast.makeText(this, "Instruction deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(appTag, "Activity destroying")

        // Release resources
        aiCameraHelper.release()
        audioHelper.release()
        openAIHelper.release()
        customSceneHelper.release()

        // Remove listeners
        CxrApi.getInstance().setAiEventListener(null)
    }
}
