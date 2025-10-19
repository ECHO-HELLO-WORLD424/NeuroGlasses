package com.patrick.neuroglasses.helpers

import android.graphics.Bitmap
import android.util.Log
import java.io.File

/**
 * OpenAI Helper
 * Handles AI-related API calls including ASR (speech-to-text) and OpenAI chat completion
 */
class OpenAIHelper(private val appTag: String = "OpenAIHelper") {

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

        // TODO: Implement actual ASR API call
        // For now, return dummy text
        val dummyText = "What do you see in this image?"

        listener?.onAsrComplete(dummyText)
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

        // TODO: Implement actual OpenAI API call
        // For now, return dummy response
        val dummyResponse = buildString {
            append("This is a dummy AI response to: \"$instruction\". ")
            if (image != null) {
                append("Image analysis would be included here.")
            }
        }

        listener?.onOpenAIResponse(dummyResponse)
    }

    /**
     * Release resources
     */
    fun release() {
        listener = null
        Log.d(appTag, "OpenAIHelper released")
    }
}
