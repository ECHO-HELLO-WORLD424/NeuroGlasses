package com.patrick.neuroglasses.helpers

import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.listeners.CustomViewListener
import com.rokid.cxr.client.utils.ValueUtil

/**
 * Custom Scene Helper
 * Handles custom UI display on AR glasses
 */
class CustomSceneHelper(private val appTag: String = "CustomSceneHelper") {

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
     * Display text result on glasses using custom UI
     * @param resultText The text to display
     * @return The status of the request
     */
    fun displayTextResult(resultText: String): ValueUtil.CxrStatus? {
        Log.i(appTag, "Displaying text result: $resultText")

        // Escape quotes in the result text for JSON
        val escapedText = resultText.replace("\"", "\\\"")

        // Create custom view JSON using LinearLayout format
        val customViewData = """
            {
                "type": "LinearLayout",
                "props": {
                    "layout_width": "match_parent",
                    "layout_height": "match_parent",
                    "orientation": "vertical",
                    "gravity": "center_horizontal",
                    "paddingTop": "100dp",
                    "paddingBottom": "100dp",
                    "backgroundColor": "#FF000000"
                },
                "children": [
                    {
                        "type": "TextView",
                        "props": {
                            "id": "tv_result",
                            "layout_width": "wrap_content",
                            "layout_height": "wrap_content",
                            "text": "$escapedText",
                            "textSize": "16sp",
                            "textColor": "#FF00FF00",
                            "textStyle": "bold"
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
        CxrApi.getInstance().setCustomViewListener(null)
        listener = null
        isCustomViewListenerSet = false
        Log.d(appTag, "CustomSceneHelper released")
    }
}
