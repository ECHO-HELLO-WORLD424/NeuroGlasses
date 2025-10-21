package com.patrick.neuroglasses.activities

import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.patrick.neuroglasses.R

class SettingsActivity : AppCompatActivity() {

    // UI Components
    private lateinit var includeImageCheckBox: CheckBox
    private lateinit var useAsrCheckBox: CheckBox
    private lateinit var useTtsCheckBox: CheckBox
    private lateinit var apiBaseUrlEditText: EditText
    private lateinit var apiTokenEditText: EditText
    private lateinit var apiTimeoutEditText: EditText
    private lateinit var systemPromptEditText: EditText
    private lateinit var vlmModelEditText: EditText
    private lateinit var vlmMaxTokensEditText: EditText
    private lateinit var asrModelEditText: EditText
    private lateinit var ttsModelEditText: EditText
    private lateinit var ttsVoiceEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var resetButton: Button

    companion object {
        // SharedPreferences keys
        const val PREFS_NAME = "OpenAISettings"
        const val KEY_INCLUDE_IMAGE = "include_image"
        const val KEY_USE_ASR = "use_asr"
        const val KEY_USE_TTS = "use_tts"
        const val KEY_API_BASE_URL = "api_base_url"
        const val KEY_API_TOKEN = "api_token"
        const val KEY_API_TIMEOUT = "api_timeout"
        const val KEY_SYSTEM_PROMPT = "system_prompt"
        const val KEY_VLM_MODEL = "vlm_model"
        const val KEY_VLM_MAX_TOKENS = "vlm_max_tokens"
        const val KEY_ASR_MODEL = "asr_model"
        const val KEY_TTS_MODEL = "tts_model"
        const val KEY_TTS_VOICE = "tts_voice"

        // Default values
        const val DEFAULT_INCLUDE_IMAGE = true
        const val DEFAULT_USE_ASR = false
        const val DEFAULT_USE_TTS = true
        const val DEFAULT_API_BASE_URL = "https://api.siliconflow.cn/v1"
        const val DEFAULT_API_TOKEN = ""
        const val DEFAULT_API_TIMEOUT = 15
        const val DEFAULT_SYSTEM_PROMPT = "You are a helpful AI assistance, trying to answer user's questions."
        const val DEFAULT_VLM_MODEL = "Qwen/Qwen3-VL-30B-A3B-Instruct"
        const val DEFAULT_VLM_MAX_TOKENS = 1024
        const val DEFAULT_ASR_MODEL = "TeleAI/TeleSpeechASR"
        const val DEFAULT_TTS_MODEL = "FunAudioLLM/CosyVoice2-0.5B"
        const val DEFAULT_TTS_VOICE = "speech:CozyNeuro:d1jud8rk20jc738kdhng:awfuidogpvwibtwohfca"

        // Helper functions to get configuration values
        fun getIncludeImage(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_INCLUDE_IMAGE, DEFAULT_INCLUDE_IMAGE)
        }

        fun getUseAsr(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_USE_ASR, DEFAULT_USE_ASR)
        }

        fun getUseTts(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(KEY_USE_TTS, DEFAULT_USE_TTS)
        }

        fun getApiBaseUrl(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL) ?: DEFAULT_API_BASE_URL
        }

        fun getApiToken(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_API_TOKEN, DEFAULT_API_TOKEN) ?: DEFAULT_API_TOKEN
        }

        fun getApiTimeout(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(KEY_API_TIMEOUT, DEFAULT_API_TIMEOUT)
        }

        fun getSystemPrompt(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        }

        fun getVlmModel(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_VLM_MODEL, DEFAULT_VLM_MODEL) ?: DEFAULT_VLM_MODEL
        }

        fun getVlmMaxTokens(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getInt(KEY_VLM_MAX_TOKENS, DEFAULT_VLM_MAX_TOKENS)
        }

        fun getAsrModel(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_ASR_MODEL, DEFAULT_ASR_MODEL) ?: DEFAULT_ASR_MODEL
        }

        fun getTtsModel(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_TTS_MODEL, DEFAULT_TTS_MODEL) ?: DEFAULT_TTS_MODEL
        }

        fun getTtsVoice(context: Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE) ?: DEFAULT_TTS_VOICE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable fullscreen immersive mode for AR glasses
        enableFullscreenMode()

        setContentView(R.layout.activity_settings)

        // Initialize UI components
        includeImageCheckBox = findViewById(R.id.includeImageCheckBox)
        useAsrCheckBox = findViewById(R.id.useAsrCheckBox)
        useTtsCheckBox = findViewById(R.id.useTtsCheckBox)
        apiBaseUrlEditText = findViewById(R.id.apiBaseUrlEditText)
        apiTokenEditText = findViewById(R.id.apiTokenEditText)
        apiTimeoutEditText = findViewById(R.id.apiTimeoutEditText)
        systemPromptEditText = findViewById(R.id.systemPromptEditText)
        vlmModelEditText = findViewById(R.id.vlmModelEditText)
        vlmMaxTokensEditText = findViewById(R.id.vlmMaxTokensEditText)
        asrModelEditText = findViewById(R.id.asrModelEditText)
        ttsModelEditText = findViewById(R.id.ttsModelEditText)
        ttsVoiceEditText = findViewById(R.id.ttsVoiceEditText)
        saveButton = findViewById(R.id.saveButton)
        resetButton = findViewById(R.id.resetButton)

        // Load current settings
        loadSettings()

        // Set up button listeners
        saveButton.setOnClickListener {
            saveSettings()
        }

        resetButton.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        includeImageCheckBox.isChecked = prefs.getBoolean(KEY_INCLUDE_IMAGE, DEFAULT_INCLUDE_IMAGE)
        useAsrCheckBox.isChecked = prefs.getBoolean(KEY_USE_ASR, DEFAULT_USE_ASR)
        useTtsCheckBox.isChecked = prefs.getBoolean(KEY_USE_TTS, DEFAULT_USE_TTS)
        apiBaseUrlEditText.setText(prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL))
        apiTokenEditText.setText(prefs.getString(KEY_API_TOKEN, DEFAULT_API_TOKEN))
        apiTimeoutEditText.setText(prefs.getInt(KEY_API_TIMEOUT, DEFAULT_API_TIMEOUT).toString())
        systemPromptEditText.setText(prefs.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT))
        vlmModelEditText.setText(prefs.getString(KEY_VLM_MODEL, DEFAULT_VLM_MODEL))
        vlmMaxTokensEditText.setText(prefs.getInt(KEY_VLM_MAX_TOKENS, DEFAULT_VLM_MAX_TOKENS).toString())
        asrModelEditText.setText(prefs.getString(KEY_ASR_MODEL, DEFAULT_ASR_MODEL))
        ttsModelEditText.setText(prefs.getString(KEY_TTS_MODEL, DEFAULT_TTS_MODEL))
        ttsVoiceEditText.setText(prefs.getString(KEY_TTS_VOICE, DEFAULT_TTS_VOICE))
    }

    private fun saveSettings() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val editor = prefs.edit()

            // Save feature toggles
            editor.putBoolean(KEY_INCLUDE_IMAGE, includeImageCheckBox.isChecked)
            editor.putBoolean(KEY_USE_ASR, useAsrCheckBox.isChecked)
            editor.putBoolean(KEY_USE_TTS, useTtsCheckBox.isChecked)

            // Validate and save API base URL
            val apiBaseUrl = apiBaseUrlEditText.text.toString().trim()
            if (apiBaseUrl.isEmpty()) {
                Toast.makeText(this, "API Base URL cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_API_BASE_URL, apiBaseUrl)

            // Save API token
            val apiToken = apiTokenEditText.text.toString().trim()
            editor.putString(KEY_API_TOKEN, apiToken)

            // Validate and save API timeout
            val apiTimeoutStr = apiTimeoutEditText.text.toString().trim()
            val apiTimeout = apiTimeoutStr.toIntOrNull()
            if (apiTimeout == null || apiTimeout <= 0) {
                Toast.makeText(this, "API Timeout must be a positive number (in seconds)", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putInt(KEY_API_TIMEOUT, apiTimeout)

            // Validate and save system prompt
            val systemPrompt = systemPromptEditText.text.toString().trim()
            if (systemPrompt.isEmpty()) {
                Toast.makeText(this, "System Prompt cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_SYSTEM_PROMPT, systemPrompt)

            // Validate and save VLM model
            val vlmModel = vlmModelEditText.text.toString().trim()
            if (vlmModel.isEmpty()) {
                Toast.makeText(this, "VLM Model cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_VLM_MODEL, vlmModel)

            // Validate and save VLM max tokens
            val vlmMaxTokensStr = vlmMaxTokensEditText.text.toString().trim()
            val vlmMaxTokens = vlmMaxTokensStr.toIntOrNull()
            if (vlmMaxTokens == null || vlmMaxTokens <= 0) {
                Toast.makeText(this, "VLM Max Tokens must be a positive number", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putInt(KEY_VLM_MAX_TOKENS, vlmMaxTokens)

            // Validate and save ASR model
            val asrModel = asrModelEditText.text.toString().trim()
            if (asrModel.isEmpty()) {
                Toast.makeText(this, "ASR Model cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_ASR_MODEL, asrModel)

            // Validate and save TTS model
            val ttsModel = ttsModelEditText.text.toString().trim()
            if (ttsModel.isEmpty()) {
                Toast.makeText(this, "TTS Model cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_TTS_MODEL, ttsModel)

            // Validate and save TTS voice
            val ttsVoice = ttsVoiceEditText.text.toString().trim()
            if (ttsVoice.isEmpty()) {
                Toast.makeText(this, "TTS Voice cannot be empty", Toast.LENGTH_SHORT).show()
                return
            }
            editor.putString(KEY_TTS_VOICE, ttsVoice)

            // Apply changes
            editor.apply()

            Toast.makeText(this, "Settings saved successfully", Toast.LENGTH_SHORT).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving settings: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun resetToDefaults() {
        includeImageCheckBox.isChecked = DEFAULT_INCLUDE_IMAGE
        useAsrCheckBox.isChecked = DEFAULT_USE_ASR
        useTtsCheckBox.isChecked = DEFAULT_USE_TTS
        apiBaseUrlEditText.setText(DEFAULT_API_BASE_URL)
        apiTokenEditText.setText(DEFAULT_API_TOKEN)
        apiTimeoutEditText.setText(DEFAULT_API_TIMEOUT.toString())
        systemPromptEditText.setText(DEFAULT_SYSTEM_PROMPT)
        vlmModelEditText.setText(DEFAULT_VLM_MODEL)
        vlmMaxTokensEditText.setText(DEFAULT_VLM_MAX_TOKENS.toString())
        asrModelEditText.setText(DEFAULT_ASR_MODEL)
        ttsModelEditText.setText(DEFAULT_TTS_MODEL)
        ttsVoiceEditText.setText(DEFAULT_TTS_VOICE)

        Toast.makeText(this, "Reset to default values", Toast.LENGTH_SHORT).show()
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
        if (hasFocus) {
            enableFullscreenMode()
        }
    }
}
