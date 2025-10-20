package com.patrick.neuroglasses.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.util.Base64
import android.util.Log
import com.patrick.neuroglasses.R
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.CustomViewListener
import com.rokid.cxr.client.extend.infos.IconInfo
import com.rokid.cxr.client.utils.ValueUtil
import java.io.ByteArrayOutputStream
import java.io.File
import androidx.core.graphics.scale

/**
 * Custom Scene Helper
 * Handles custom UI display on AR glasses
 */
class CustomSceneHelper(
    private val context: Context,
    private val appTag: String = "CustomSceneHelper"
) {

    /**
     * Listener interface for custom view events
     */
    interface CustomSceneListener {
        /**
         * Called when custom view is successfully opened
         */
        fun onSceneOpened()

        /**
         * Called when custom view fails to open
         * @param errorCode The error code
         */
        fun onSceneOpenFailed(errorCode: Int)

        /**
         * Called when custom view is closed
         */
        fun onSceneClosed()

        /**
         * Called when custom view is updated
         */
        fun onSceneUpdated()
    }

    private var listener: CustomSceneListener? = null
    private var isCustomViewListenerSet = false
    private var mediaPlayer: MediaPlayer? = null
    private var audioFileToPlay: File? = null

    // Icon variables
    private var iconsSent = false
    private val aiIconName = "ai_icon"

    /**
     * Set the listener for custom scene events
     */
    fun setListener(listener: CustomSceneListener) {
        this.listener = listener
    }

    /**
     * Initialize the custom view listener
     * Should be called once during setup
     */
    fun initializeCustomViewListener() {
        if (!isCustomViewListenerSet) {
            CxrApi.getInstance().setCustomViewListener(object : CustomViewListener {
                override fun onIconsSent() {
                    Log.d(appTag, "Custom view icons sent")
                }

                override fun onOpened() {
                    Log.d(appTag, "Custom view opened")
                    listener?.onSceneOpened()

                    // Play audio if available
                    audioFileToPlay?.let { playAudio(it) }
                }

                override fun onOpenFailed(errorCode: Int) {
                    Log.e(appTag, "Custom view open failed: $errorCode")
                    listener?.onSceneOpenFailed(errorCode)
                }

                override fun onUpdated() {
                    Log.d(appTag, "Custom view updated")
                    listener?.onSceneUpdated()
                }

                override fun onClosed() {
                    Log.d(appTag, "Custom view closed")
                    listener?.onSceneClosed()
                }
            })
            isCustomViewListenerSet = true
            Log.d(appTag, "Custom view listener initialized")
        }
    }

    /**
     * Set the audio file to play when custom view opens
     * @param audioFile The audio file to play
     */
    fun setAudioFile(audioFile: File) {
        audioFileToPlay = audioFile
        Log.d(appTag, "Audio file set: ${audioFile.absolutePath}")
    }

    /**
     * Play audio file
     * @param audioFile The audio file to play
     */
    private fun playAudio(audioFile: File) {
        if (!audioFile.exists()) {
            Log.e(appTag, "Audio file does not exist: ${audioFile.path}")
            return
        }

        try {
            // Stop any currently playing audio
            stopAudio()

            // Create and configure MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(audioFile.absolutePath)
                prepare()

                // Set completion listener
                setOnCompletionListener {
                    Log.d(appTag, "Audio playback completed")
                    stopAudio()
                }

                // Set error listener
                setOnErrorListener { _, what, extra ->
                    Log.e(appTag, "MediaPlayer error: what=$what, extra=$extra")
                    stopAudio()
                    true
                }

                // Start playback
                start()
            }

            Log.i(appTag, "Started playing audio: ${audioFile.name}")
        } catch (e: Exception) {
            Log.e(appTag, "Error playing audio file: ${e.message}", e)
            stopAudio()
        }
    }

    /**
     * Stop audio playback
     */
    private fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) {
                stop()
                Log.d(appTag, "Audio playback stopped")
            }
            release()
        }
        mediaPlayer = null
    }

    /**
     * Send AI icon to glasses
     * @return The status of the request
     */
    private fun sendAiIcon(): ValueUtil.CxrStatus? {
        if (iconsSent) {
            Log.d(appTag, "Icons already sent, skipping")
            return ValueUtil.CxrStatus.REQUEST_SUCCEED
        }

        try {
            // Load the AI icon from drawable resources using R identifier
            val bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.ai_icon_small)
            if (bitmap == null) {
                Log.e(appTag, "Failed to decode AI icon bitmap")
                return ValueUtil.CxrStatus.REQUEST_FAILED
            }

            // Resize bitmap to fit within 128x128 requirement if needed
            val maxSize = 128
            val resizedBitmap = if (bitmap.width > maxSize || bitmap.height > maxSize) {
                val scale = minOf(maxSize.toFloat() / bitmap.width, maxSize.toFloat() / bitmap.height)
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                bitmap.scale(newWidth, newHeight).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }

            // Convert bitmap to Base64
            val base64Icon = bitmapToBase64(resizedBitmap)
            resizedBitmap.recycle()

            if (base64Icon == null) {
                Log.e(appTag, "Failed to convert bitmap to Base64")
                return ValueUtil.CxrStatus.REQUEST_FAILED
            }

            // Create IconInfo and send to glasses
            val iconInfo = IconInfo(aiIconName, base64Icon)
            val status = CxrApi.getInstance().sendCustomViewIcons(listOf(iconInfo))

            if (status == ValueUtil.CxrStatus.REQUEST_SUCCEED) {
                iconsSent = true
                Log.i(appTag, "AI icon sent successfully")
            } else {
                Log.e(appTag, "Failed to send AI icon: $status")
            }

            return status
        } catch (e: Exception) {
            Log.e(appTag, "Error sending AI icon: ${e.message}", e)
            return ValueUtil.CxrStatus.REQUEST_FAILED
        }
    }

    /**
     * Convert bitmap to Base64 string
     * @param bitmap The bitmap to convert
     * @return Base64 encoded string or null if failed
     */
    private fun bitmapToBase64(bitmap: Bitmap): String? {
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            val byteArray = outputStream.toByteArray()
            Base64.encodeToString(byteArray, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(appTag, "Error converting bitmap to Base64: ${e.message}", e)
            null
        }
    }

    /**
     * Display text result on glasses using custom UI
     * @param resultText The text to display
     * @return The status of the request
     */
    fun displayTextResult(resultText: String): ValueUtil.CxrStatus? {
        Log.i(appTag, "Displaying text result: $resultText")

        // Escape quotes in the result text for JSON
        val escapedText = resultText.replace("\"", "\\\"")

        // Send icon first
        sendAiIcon()

        // Create custom view JSON using RelativeLayout with icon on top left
        val customViewData = """
            {
                "type": "RelativeLayout",
                "props": {
                    "layout_width": "match_parent",
                    "layout_height": "match_parent",
                    "backgroundColor": "#FF000000",
                    "paddingStart": "20dp",
                    "paddingTop": "20dp",
                    "paddingEnd": "20dp",
                    "paddingBottom": "20dp"
                },
                "children": [
                    {
                        "type": "ImageView",
                        "props": {
                            "id": "iv_ai_icon",
                            "layout_width": "48dp",
                            "layout_height": "48dp",
                            "name": "$aiIconName",
                            "scaleType": "center_inside",
                            "layout_alignParentStart": "true",
                            "layout_alignParentTop": "true"
                        }
                    },
                    {
                        "type": "TextView",
                        "props": {
                            "id": "tv_result",
                            "layout_width": "match_parent",
                            "layout_height": "wrap_content",
                            "text": "$escapedText",
                            "textSize": "16sp",
                            "textColor": "#FF00FF00",
                            "textStyle": "bold",
                            "layout_below": "iv_ai_icon",
                            "marginTop": "15dp"
                        }
                    }
                ]
            }
        """.trimIndent()

        // Open custom UI with result
        val status = CxrApi.getInstance().openCustomView(customViewData)
        Log.d(appTag, "Open custom view status: $status")

        return status
    }

    /**
     * Update the displayed text
     * @param newText The new text to display
     * @return The status of the request
     */
    fun updateTextResult(newText: String): ValueUtil.CxrStatus? {
        Log.i(appTag, "Updating text result: $newText")

        // Escape quotes in the text for JSON
        val escapedText = newText.replace("\"", "\\\"")

        // Create update JSON
        val updateData = """
            [
                {
                    "action": "update",
                    "id": "tv_result",
                    "props": {
                        "text": "$escapedText"
                    }
                }
            ]
        """.trimIndent()

        val status = CxrApi.getInstance().updateCustomView(updateData)
        Log.d(appTag, "Update custom view status: $status")

        return status
    }

    /**
     * Close the custom view
     * @return The status of the request
     */
    fun closeCustomView(): ValueUtil.CxrStatus? {
        Log.d(appTag, "Closing custom view")
        val status = CxrApi.getInstance().closeCustomView()
        Log.d(appTag, "Close custom view status: $status")
        return status
    }

    /**
     * Release resources and remove listener
     */
    fun release() {
        stopAudio()
        CxrApi.getInstance().setCustomViewListener(null)
        listener = null
        isCustomViewListenerSet = false
        audioFileToPlay = null
        Log.d(appTag, "CustomSceneHelper released")
    }
}
