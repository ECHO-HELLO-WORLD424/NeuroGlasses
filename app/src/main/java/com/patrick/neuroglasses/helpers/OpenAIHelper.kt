package com.patrick.neuroglasses.helpers

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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
    val maxTokens: Int
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

data class OpenAIResponse(
    val id: String,
    val choices: List<Choice>
)

data class Choice(
    val message: MessageResponse,
    @SerializedName("finish_reason")
    val finishReason: String
)

data class MessageResponse(
    val role: String,
    val content: String
)

/**
 * Data class for ASR API response
 */
data class AsrResponse(
    val text: String
)

/**
 * OpenAI Helper
 * Handles AI-related API calls including ASR (speech-to-text) and OpenAI chat completion
 */
class OpenAIHelper(private val appTag: String = "OpenAIHelper") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()
    private val chatApiUrl = "https://api.siliconflow.cn/v1/chat/completions"
    private val asrApiUrl = "https://api.siliconflow.cn/v1/audio/transcriptions"
    private val apiKey = "sk-vfpzsggsnvzztaqlvznxhyfgrcccxwdecdbdulkuexvlzdld"

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
         * Called when OpenAI API response is received
         * @param response The AI-generated response text
         */
        fun onOpenAIResponse(response: String)

        /**
         * Called when OpenAI API call fails
         * @param error Error message
         */
        fun onOpenAIFailed(error: String)
    }

    private var listener: OpenAIListener? = null

    /**
     * Set the listener for AI API callbacks
     */
    fun setListener(listener: OpenAIListener) {
        this.listener = listener
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

        Thread {
            try {
                // Create multipart form data request
                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("model", "TeleAI/TeleSpeechASR")
                    .addFormDataPart(
                        "file",
                        audioFile.name,
                        RequestBody.create("audio/wav".toMediaType(), audioFile)
                    )
                    .build()

                // Create HTTP request
                val request = Request.Builder()
                    .url(asrApiUrl)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                Log.d(appTag, "Sending ASR request for file: ${audioFile.name} (${audioFile.length()} bytes)")

                // Execute the request
                client.newCall(request).execute().use { response ->
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
     * Call OpenAI API for chat completion
     * @param instruction The user instruction/prompt
     * @param image Optional image to include in the request (for vision models)
     */
    fun callOpenAI(instruction: String, image: Bitmap?) {
        Log.d(appTag, "OpenAI API called")
        Log.d(appTag, "Instruction: $instruction")
        Log.d(appTag, "Has image: ${image != null}")

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

                // Create the request
                val request = OpenAIRequest(
                    model = "Qwen/Qwen3-VL-30B-A3B-Instruct",
                    messages = listOf(
                        Message(
                            role = "user",
                            content = contentList
                        )
                    ),
                    maxTokens = 1024
                )

                // Convert request to JSON
                val jsonBody = gson.toJson(request)
                Log.d(appTag, "Request JSON: $jsonBody")

                // Create HTTP request
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())
                val httpRequest = Request.Builder()
                    .url(chatApiUrl)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                // Execute the request
                client.newCall(httpRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(appTag, "API call failed: ${response.code} - $errorBody")
                        listener?.onOpenAIFailed("API call failed: ${response.code}")
                        return@Thread
                    }

                    val responseBody = response.body?.string()
                    Log.d(appTag, "Response: $responseBody")

                    if (responseBody != null) {
                        val openAIResponse = gson.fromJson(responseBody, OpenAIResponse::class.java)
                        val aiMessage = openAIResponse.choices.firstOrNull()?.message?.content

                        if (aiMessage != null) {
                            Log.d(appTag, "AI Response: $aiMessage")
                            listener?.onOpenAIResponse(aiMessage)
                        } else {
                            Log.e(appTag, "No response content found")
                            listener?.onOpenAIFailed("No response content")
                        }
                    } else {
                        Log.e(appTag, "Empty response body")
                        listener?.onOpenAIFailed("Empty response")
                    }
                }
            } catch (e: IOException) {
                Log.e(appTag, "Network error: ${e.message}", e)
                listener?.onOpenAIFailed("Network error: ${e.message}")
            } catch (e: Exception) {
                Log.e(appTag, "Error calling OpenAI API: ${e.message}", e)
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
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Release resources
     */
    fun release() {
        listener = null
        Log.d(appTag, "OpenAIHelper released")
    }
}
