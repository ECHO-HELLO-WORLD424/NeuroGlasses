package com.patrick.neuroglasses.helpers

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.patrick.neuroglasses.activities.SettingsActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Data classes for OpenAI API request/response
 */
data class OpenAIRequest(
    val model: String,
    val messages: List<Message>,
    @SerializedName("max_tokens")
    val maxTokens: Int,
    val stream: Boolean = false
)

data class Message(
    val role: String,
    val content: List<Content>
)

data class Content(
    val type: String,
    val text: String? = null,
    @SerializedName("image_url")
    val imageUrl: ImageUrl? = null
)

data class ImageUrl(
    val url: String
)

/**
 * Data classes for streaming responses
 */
data class StreamingResponse(
    val id: String,
    val choices: List<StreamingChoice>
)

data class StreamingChoice(
    val delta: Delta,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Delta(
    val role: String?,
    val content: String?
)

/**
 * Data class for ASR API response
 */
data class AsrResponse(
    val text: String
)

/**
 * Data class for TTS API request
 */
data class TtsRequest(
    val model: String,
    val input: String,
    val voice: String,
    @SerializedName("response_format")
    val responseFormat: String = "pcm",
    @SerializedName("sample_rate")
    val sampleRate: Int = 44100
)

/**
 * OpenAI Helper
 * Handles AI-related API calls including ASR (speech-to-text) and OpenAI chat completion
 */
class OpenAIHelper(private val context: Context, private val appTag: String = "OpenAIHelper") {

    private val gson = Gson()

    // Cached OkHttpClient instances to avoid creating new clients for each request
    private var standardClient: OkHttpClient? = null
    private var streamingClient: OkHttpClient? = null
    private var lastTimeoutSeconds: Long = 0

    // Get configuration from SharedPreferences
    private fun getApiBaseUrl(): String = SettingsActivity.getApiBaseUrl(context)
    private fun getApiToken(): String = SettingsActivity.getApiToken(context)
    private fun getApiTimeout(): Int = SettingsActivity.getApiTimeout(context)
    private fun getSystemPrompt(): String = SettingsActivity.getSystemPrompt(context)
    private fun getVlmModel(): String = SettingsActivity.getVlmModel(context)
    private fun getVlmMaxTokens(): Int = SettingsActivity.getVlmMaxTokens(context)
    private fun getAsrModel(): String = SettingsActivity.getAsrModel(context)
    private fun getTtsModel(): String = SettingsActivity.getTtsModel(context)
    private fun getTtsVoice(): String = SettingsActivity.getTtsVoice(context)

    // Build OkHttpClient with configurable timeout and caching
    private fun getClient(isStreaming: Boolean = false): OkHttpClient {
        val timeoutSeconds = getApiTimeout().toLong()

        // Rebuild clients if timeout changed
        if (timeoutSeconds != lastTimeoutSeconds) {
            standardClient = null
            streamingClient = null
            lastTimeoutSeconds = timeoutSeconds
        }

        // Return cached client or create new one
        return if (isStreaming) {
            streamingClient ?: OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds * 2, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                // Add DNS configuration for better connectivity
                .dns(CustomDns())
                .build().also { streamingClient = it }
        } else {
            standardClient ?: OkHttpClient.Builder()
                .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                // Add DNS configuration for better connectivity
                .dns(CustomDns())
                .build().also { standardClient = it }
        }
    }

    private fun getChatApiUrl(): String = "${getApiBaseUrl()}/chat/completions"
    private fun getAsrApiUrl(): String = "${getApiBaseUrl()}/audio/transcriptions"
    private fun getTtsApiUrl(): String = "${getApiBaseUrl()}/audio/speech"

    /**
     * Listener interface for AI API callbacks
     */
    interface OpenAIListener {
        /**
         * Called when ASR processing is complete
         * @param text The recognized text from audio
         */
        fun onAsrComplete(text: String)

        /**
         * Called when ASR processing fails
         * @param error Error message
         */
        fun onAsrFailed(error: String)

        /**
         * Called when OpenAI API response is received (non-streaming)
         * @param response The AI-generated response text
         */
        fun onOpenAIResponse(response: String)

        /**
         * Called when OpenAI streaming starts
         */
        fun onOpenAIStreamingStarted() {}

        /**
         * Called when a streaming chunk is received
         * @param chunk The text chunk received
         * @param isComplete Whether this is the final chunk
         */
        fun onOpenAIStreamingChunk(chunk: String, isComplete: Boolean) {}

        /**
         * Called when OpenAI API call fails
         * @param error Error message
         */
        fun onOpenAIFailed(error: String)

        /**
         * Called when TTS audio file is ready
         * @param audioFile The generated audio file
         */
        fun onTtsComplete(audioFile: File)

        /**
         * Called when TTS processing fails
         * @param error Error message
         */
        fun onTtsFailed(error: String)

        /**
         * Called when TTS streaming starts
         */
        fun onTtsStreamingStarted() {}

        /**
         * Called when a TTS audio chunk is received
         * @param audioChunk The audio data chunk
         * @param isComplete Whether this is the final chunk
         */
        fun onTtsStreamingChunk(audioChunk: ByteArray, isComplete: Boolean) {}
    }

    private var listener: OpenAIListener? = null

    /**
     * Set the listener for AI API callbacks
     */
    fun setListener(listener: OpenAIListener) {
        this.listener = listener
    }

    /**
     * Check if device has internet connectivity
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Call ASR API to convert audio to text
     * @param audioFile The audio file to process
     */
    fun callAsrAPI(audioFile: File) {
        Log.d(appTag, "ASR API called with file: ${audioFile.name}")

        if (!audioFile.exists()) {
            Log.e(appTag, "Audio file does not exist: ${audioFile.path}")
            listener?.onAsrFailed("Audio file not found")
            return
        }

        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.e(appTag, "No network connectivity")
            listener?.onAsrFailed("No internet connection. Please check your network settings.")
            return
        }

        Thread {
            try {
                // Create multipart form data request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", getAsrModel())
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        audioFile.asRequestBody("audio/wav".toMediaType())
                    )
                    .build()

                // Create HTTP request
                val request = Request.Builder()
                    .url(getAsrApiUrl())
                    .addHeader("Authorization", "Bearer ${getApiToken()}")
                    .post(requestBody)
                    .build()

                Log.d(appTag, "Sending ASR request for file: ${audioFile.name} (${audioFile.length()} bytes)")

                // Execute the request
                getClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(appTag, "ASR API call failed: ${response.code} - $errorBody")
                        listener?.onAsrFailed("ASR API call failed: ${response.code}")
                        return@Thread
                    }

                    val responseBody = response.body?.string()
                    Log.d(appTag, "ASR Response: $responseBody")

                    if (responseBody != null) {
                        val asrResponse = gson.fromJson(responseBody, AsrResponse::class.java)
                        val recognizedText = asrResponse.text

                        if (recognizedText.isNotEmpty()) {
                            Log.d(appTag, "ASR recognized text: $recognizedText")
                            listener?.onAsrComplete(recognizedText)
                        } else {
                            Log.e(appTag, "ASR returned empty text")
                            listener?.onAsrFailed("No text recognized")
                        }
                    } else {
                        Log.e(appTag, "Empty ASR response body")
                        listener?.onAsrFailed("Empty response")
                    }
                }
            } catch (e: IOException) {
                Log.e(appTag, "Network error during ASR: ${e.message}", e)
                listener?.onAsrFailed("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(appTag, "Error calling ASR API: ${e.message}", e)
                listener?.onAsrFailed("Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Call OpenAI API for chat completion with streaming
     * @param instruction The user instruction/prompt
     * @param image Optional image to include in the request (for vision models)
     */
    fun callOpenAIStreaming(instruction: String, image: Bitmap?) {
        Log.d(appTag, "OpenAI Streaming API called")
        Log.d(appTag, "Instruction: $instruction")
        Log.d(appTag, "Has image: ${image != null}")

        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.e(appTag, "No network connectivity")
            listener?.onOpenAIFailed("No internet connection. Please check your network settings.")
            return
        }

        Thread {
            try {
                // Build the message content
                val contentList = mutableListOf<Content>()

                // Add text instruction
                contentList.add(Content(type = "text", text = instruction))

                // Add image if provided
                if (image != null) {
                    val base64Image = bitmapToBase64(image)
                    val imageDataUrl = "data:image/png;base64,$base64Image"
                    contentList.add(
                        Content(
                            type = "image_url",
                            imageUrl = ImageUrl(url = imageDataUrl)
                        )
                    )
                }

                // Build the messages list with system prompt
                val messagesList = mutableListOf<Message>()

                // Add system message
                val systemPrompt = getSystemPrompt()
                messagesList.add(
                    Message(
                        role = "system",
                        content = listOf(Content(type = "text", text = systemPrompt))
                    )
                )

                // Add user message
                messagesList.add(
                    Message(
                        role = "user",
                        content = contentList
                    )
                )

                // Create the request with streaming enabled
                val request = OpenAIRequest(
                    model = getVlmModel(),
                    messages = messagesList,
                    maxTokens = getVlmMaxTokens(),
                    stream = true
                )

                // Convert request to JSON
                val jsonBody = gson.toJson(request)
                Log.d(appTag, "Request JSON: $jsonBody")

                // Create HTTP request
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(getChatApiUrl())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${getApiToken()}")
                    .post(requestBody)
                    .build()

                // Notify streaming started
                listener?.onOpenAIStreamingStarted()

                // Execute the request with streaming-specific timeout
                getClient(isStreaming = true).newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(appTag, "API call failed: ${response.code} - $errorBody")
                        listener?.onOpenAIFailed("API call failed: ${response.code}")
                        return@Thread
                    }

                    // Read the streaming response
                    val source = response.body?.source()
                    if (source == null) {
                        Log.e(appTag, "Empty response body")
                        listener?.onOpenAIFailed("Empty response")
                        return@Thread
                    }

                    val fullResponse = StringBuilder()
                    okio.Buffer()

                    // Parse Server-Sent Events with buffered reading to avoid blocking on newlines
                    try {
                        while (!source.exhausted()) {
                            // Read available data without blocking indefinitely
                            // This uses a buffered approach that won't wait for newlines
                            val line = try {
                                source.readUtf8LineStrict()
                            } catch (_: java.io.EOFException) {
                                // Handle case where stream ends without final newline
                                Log.d(appTag, "Stream ended")
                                break
                            }

                            if (line.isEmpty()) {
                                // SSE uses blank lines as delimiters between messages
                                continue
                            }

                            Log.v(appTag, "Raw SSE line: $line")

                            // SSE format: "data: {json}"
                            if (line.startsWith("data: ")) {
                                val data = line.substring(6).trim()

                                // Check for [DONE] marker
                                if (data == "[DONE]") {
                                    Log.d(appTag, "Streaming completed with [DONE] marker")
                                    listener?.onOpenAIStreamingChunk("", true)
                                    break
                                }

                                // Skip empty data lines
                                if (data.isEmpty()) {
                                    continue
                                }

                                try {
                                    // Parse the JSON chunk
                                    val streamingResponse = gson.fromJson(data, StreamingResponse::class.java)
                                    val delta = streamingResponse.choices.firstOrNull()?.delta
                                    val content = delta?.content

                                    if (content != null && content.isNotEmpty()) {
                                        fullResponse.append(content)
                                        Log.d(appTag, "Received chunk: $content")

                                        // Check if this is the final chunk
                                        val finishReason = streamingResponse.choices.firstOrNull()?.finishReason
                                        val isComplete = finishReason != null

                                        listener?.onOpenAIStreamingChunk(content, isComplete)
                                    }
                                } catch (e: Exception) {
                                    Log.e(appTag, "Error parsing streaming chunk: ${e.message}, data: $data")
                                    // Continue processing remaining chunks
                                }
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        Log.e(appTag, "Streaming read timeout - this may indicate the server is not sending data in SSE format")
                        listener?.onOpenAIFailed("Streaming timeout: server may not be sending proper SSE format")
                        return@Thread
                    }

                    // Also send the full response to the non-streaming callback for backwards compatibility
                    val finalResponse = fullResponse.toString()
                    if (finalResponse.isNotEmpty()) {
                        Log.d(appTag, "Full streaming response: $finalResponse")
                        listener?.onOpenAIResponse(finalResponse)
                    } else {
                        Log.w(appTag, "Streaming completed but no content was received")
                    }
                }
            } catch (e: IOException) {
                Log.e(appTag, "Network error: ${e.message}", e)
                listener?.onOpenAIFailed("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(appTag, "Error calling OpenAI Streaming API: ${e.message}", e)
                listener?.onOpenAIFailed("Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Convert Bitmap to base64 string
     * @param bitmap The bitmap to convert
     * @return Base64 encoded string
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        try {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
            val byteArray = byteArrayOutputStream.toByteArray()
            return Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } finally {
            byteArrayOutputStream.close()
        }
    }

    /**
     * Call TTS API to convert text to speech with streaming support
     * @param text The text to convert to speech
     * @param outputDir The directory to save the audio file
     * @param streaming If true, streams audio chunks as they arrive; if false, waits for complete file
     */
    fun callTtsAPI(text: String, outputDir: File, streaming: Boolean = false) {
        Log.d(appTag, "TTS API called with text: $text (streaming: $streaming)")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        // Check network connectivity
        if (!isNetworkAvailable()) {
            Log.e(appTag, "No network connectivity")
            listener?.onTtsFailed("No internet connection. Please check your network settings.")
            return
        }

        Thread {
            try {
                // Create the TTS request with PCM format for streaming
                val request = TtsRequest(
                    model = getTtsModel(),
                    input = text,
                    voice = getTtsVoice(),
                    responseFormat = "pcm"
                )

                // Convert request to JSON
                val jsonBody = gson.toJson(request)
                Log.d(appTag, "TTS Request JSON: $jsonBody")

                // Create HTTP request
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(getTtsApiUrl())
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer ${getApiToken()}")
                    .post(requestBody)
                    .build()

                if (streaming) {
                    // Streaming mode: send chunks as they arrive
                    callTtsAPIStreaming(httpRequest, outputDir)
                } else {
                    // Non-streaming mode: wait for complete file
                    callTtsAPINonStreaming(httpRequest, outputDir)
                }
            } catch (e: IOException) {
                Log.e(appTag, "Network error during TTS: ${e.message}", e)
                listener?.onTtsFailed("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(appTag, "Error calling TTS API: ${e.message}", e)
                listener?.onTtsFailed("Error: ${e.message}")
            }
        }.start()
    }

    /**
     * Call TTS API in streaming mode
     */
    private fun callTtsAPIStreaming(httpRequest: Request, outputDir: File) {
        // Execute the request with streaming
        getClient(isStreaming = true).newCall(httpRequest).execute().use { response ->
            val contentType = response.body?.contentType()
            val contentLength = response.body?.contentLength()
            Log.d(appTag, "TTS response received: code=${response.code}, contentType=$contentType, contentLength=$contentLength")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(appTag, "TTS API call failed: ${response.code} - $errorBody")
                listener?.onTtsFailed("TTS API call failed: ${response.code}")
                return
            }

            // Check if response is actually audio
            if (contentType?.toString()?.startsWith("text/") == true) {
                // Server returned text instead of audio - likely an error message
                val errorText = response.body?.string() ?: "Unknown error"
                Log.e(appTag, "TTS API returned text instead of audio: $errorText")
                listener?.onTtsFailed("TTS API error: $errorText")
                return
            }

            // Notify streaming started
            listener?.onTtsStreamingStarted()
            Log.d(appTag, "TTS streaming started")

            // Read the streaming response
            val inputStream = response.body?.byteStream()
            if (inputStream == null) {
                Log.e(appTag, "Empty TTS response body")
                listener?.onTtsFailed("Empty audio response")
                return
            }

            Log.d(appTag, "Input stream obtained, starting to read chunks...")

            // Create file to save complete audio for later use
            val timestamp = System.currentTimeMillis()
            val audioFile = File(outputDir, "tts_result_$timestamp.pcm")
            val fileOutputStream = audioFile.outputStream()

            try {
                // Stream audio data in chunks
                val buffer = ByteArray(4096) // 4KB chunks
                var bytesRead: Int
                var totalBytes = 0L
                var chunkCount = 0

                Log.d(appTag, "Starting read loop...")
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    chunkCount++
                    Log.d(appTag, "Read attempt #$chunkCount: $bytesRead bytes")

                    if (bytesRead > 0) {
                        // Create a chunk of the exact size read
                        val chunk = buffer.copyOf(bytesRead)

                        // Save to file
                        fileOutputStream.write(chunk)
                        totalBytes += bytesRead

                        // Send chunk to listener for immediate playback
                        listener?.onTtsStreamingChunk(chunk, false)

                        Log.d(appTag, "TTS chunk received: $bytesRead bytes (total: $totalBytes)")
                    }
                }

                Log.d(appTag, "Read loop completed after $chunkCount iterations")

                // Notify streaming complete
                listener?.onTtsStreamingChunk(ByteArray(0), true)
                Log.d(appTag, "TTS streaming completed: $totalBytes bytes")

                // Close file stream
                fileOutputStream.close()

                // Notify that the complete file is ready
                Log.d(appTag, "TTS audio saved to: ${audioFile.absolutePath} ($totalBytes bytes)")
                listener?.onTtsComplete(audioFile)

            } catch (e: Exception) {
                fileOutputStream.close()
                Log.e(appTag, "Error during TTS streaming: ${e.message}", e)
                listener?.onTtsFailed("Streaming error: ${e.message}")
            }
        }
    }

    /**
     * Call TTS API in non-streaming mode (original behavior)
     */
    private fun callTtsAPINonStreaming(httpRequest: Request, outputDir: File) {
        // Execute the request
        getClient().newCall(httpRequest).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(appTag, "TTS API call failed: ${response.code} - $errorBody")
                listener?.onTtsFailed("TTS API call failed: ${response.code}")
                return
            }

            // Save the audio data to file
            val audioBytes = response.body?.bytes()
            if (audioBytes != null && audioBytes.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val audioFile = File(outputDir, "tts_result_$timestamp.pcm")

                audioFile.outputStream().use { fileOut ->
                    fileOut.write(audioBytes)
                }

                Log.d(appTag, "TTS audio saved to: ${audioFile.absolutePath} (${audioBytes.size} bytes)")
                listener?.onTtsComplete(audioFile)
            } else {
                Log.e(appTag, "Empty TTS response body")
                listener?.onTtsFailed("Empty audio response")
            }
        }
    }

    /**
     * Release resources
     */
    fun release() {
        listener = null
        // Clear cached HTTP clients
        standardClient = null
        streamingClient = null
        Log.d(appTag, "OpenAIHelper released")
    }

    /**
     * Custom DNS implementation with fallback DNS servers
     * Helps resolve connectivity issues when default DNS fails
     */
    private inner class CustomDns : Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            try {
                // First try system DNS
                return Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                Log.w(appTag, "System DNS failed for $hostname, trying fallback DNS: ${e.message}")

                // Fallback to Google DNS (8.8.8.8 and 8.8.4.4)
                try {
                    val addresses = mutableListOf<java.net.InetAddress>()

                    // Try using InetAddress.getAllByName as backup
                    val fallbackAddresses = java.net.InetAddress.getAllByName(hostname)
                    addresses.addAll(fallbackAddresses)

                    if (addresses.isNotEmpty()) {
                        Log.d(appTag, "Fallback DNS succeeded for $hostname")
                        return addresses
                    }
                } catch (fallbackError: Exception) {
                    Log.e(appTag, "Fallback DNS also failed for $hostname: ${fallbackError.message}")
                }

                // If all fails, rethrow the original exception
                throw e
            }
        }
    }
}
