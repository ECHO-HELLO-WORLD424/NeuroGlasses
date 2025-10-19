package com.patrick.neuroglasses.activities

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.patrick.neuroglasses.R
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.infos.IconInfo
import com.rokid.cxr.client.extend.listeners.CustomViewListener

class UITestActivity : AppCompatActivity() {
    private val appTag = "UITestActivity"
    private lateinit var toggleUIButton: Button
    private var isCustomUIOpen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ui_test)

        toggleUIButton = findViewById(R.id.toggleUIButton)

        // Set up custom view listener
        CxrApi.getInstance().setCustomViewListener(object : CustomViewListener {
            override fun onIconsSent() {
                Log.i(appTag, "Custom view icons sent")
                runOnUiThread {
                    Toast.makeText(this@UITestActivity, "Icons uploaded successfully", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onOpened() {
                Log.i(appTag, "Custom view opened")
                runOnUiThread {
                    isCustomUIOpen = true
                    updateButtonText()
                    Toast.makeText(this@UITestActivity, "Custom UI opened on glasses", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onOpenFailed(errorCode: Int) {
                Log.e(appTag, "Custom view open failed: $errorCode")
                runOnUiThread {
                    Toast.makeText(this@UITestActivity, "Failed to open custom UI: $errorCode", Toast.LENGTH_LONG).show()
                }
            }

            override fun onUpdated() {
                Log.i(appTag, "Custom view updated")
            }

            override fun onClosed() {
                Log.i(appTag, "Custom view closed")
                runOnUiThread {
                    isCustomUIOpen = false
                    updateButtonText()
                    Toast.makeText(this@UITestActivity, "Custom UI closed", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // Set up toggle button
        toggleUIButton.setOnClickListener {
            if (isCustomUIOpen) {
                closeCustomUI()
            } else {
                openCustomUI()
            }
        }

        updateButtonText()
    }

    private fun openCustomUI() {
        // First, upload the image
        uploadImage()

        // Then open the custom view with the JSON layout
        val customViewJson = """
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
                        "id": "tv_title",
                        "layout_width": "wrap_content",
                        "layout_height": "wrap_content",
                        "text": "Test UI",
                        "textSize": "18sp",
                        "textColor": "#FF00FF00",
                        "textStyle": "bold",
                        "marginBottom": "30dp"
                    }
                },
                {
                    "type": "ImageView",
                    "props": {
                        "id": "iv_test_image",
                        "layout_width": "120dp",
                        "layout_height": "120dp",
                        "name": "test_image",
                        "scaleType": "center"
                    }
                }
            ]
        }
        """.trimIndent()

        Log.d(appTag, "Opening custom view with JSON: $customViewJson")
        val status = CxrApi.getInstance().openCustomView(customViewJson)
        Log.d(appTag, "Open custom view status: $status")
    }

    private fun uploadImage() {
        try {
            // Load the webp image directly from drawable resources as raw bytes
            val inputStream = resources.openRawResource(+ R.drawable.test_small)
            val webpBytes = inputStream.readBytes()
            inputStream.close()

            // Convert webp bytes to Base64 directly (preserves animation)
            val base64Image = Base64.encodeToString(webpBytes, Base64.NO_WRAP)

            // Create IconInfo and send to glasses
            val iconInfo = IconInfo("test_image", base64Image)
            val status = CxrApi.getInstance().sendCustomViewIcons(listOf(iconInfo))

            Log.d(appTag, "Sending custom view icons (WebP format), status: $status")
        } catch (e: Exception) {
            Log.e(appTag, "Failed to upload image", e)
            runOnUiThread {
                Toast.makeText(this, "Failed to upload image: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun closeCustomUI() {
        val status = CxrApi.getInstance().closeCustomView()
        Log.d(appTag, "Close custom view status: $status")
    }

    private fun updateButtonText() {
        toggleUIButton.text = if (isCustomUIOpen) "Close Custom UI" else "Open Custom UI"
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up listener
        CxrApi.getInstance().setCustomViewListener(null)
    }
}
