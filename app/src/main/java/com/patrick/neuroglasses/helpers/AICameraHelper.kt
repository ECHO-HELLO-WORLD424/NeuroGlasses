package com.patrick.neuroglasses.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.PhotoResultCallback
import com.rokid.cxr.client.utils.ValueUtil

/**
 * Helper class for AI scene camera operations using PhotoResultCallback.
 * This handles camera operations that return WebP image data as byte arrays.
 *
 * Note: This is different from CameraHelper which uses PhotoPathCallback
 * and returns file paths on the glasses.
 */
class AICameraHelper(private val appTag: String = "AICameraHelper") {

    // Camera state
    var isCameraOpen = false
        private set

    // Callback interface for camera events
    interface AICameraListener {
        fun onCameraOpened(message: String)
        fun onCameraOpenFailed(message: String)
        fun onPhotoStatusUpdate(message: String)
        fun onPhotoSuccess(bitmap: Bitmap, dataSize: Int, width: Int, height: Int)
        fun onPhotoFailed(message: String)
    }

    private var listener: AICameraListener? = null

    fun setListener(listener: AICameraListener) {
        this.listener = listener
    }

    /**
     * Photo result callback with comprehensive logging
     */
    private val photoResultCallback = PhotoResultCallback { status, photo ->
        /**
         * Called when photo capture is complete
         * @param status photo take status
         * @param photo WebP photo data byte array
         */
        Log.d(appTag, "=== Photo Result Callback Triggered ===")
        Log.d(appTag, "Photo status: $status")
        Log.d(appTag, "Photo data null: ${photo == null}")
        Log.d(appTag, "Photo data size: ${photo?.size ?: 0} bytes")

        when (status) {
            ValueUtil.CxrStatus.RESPONSE_SUCCEED -> {
                Log.i(appTag, "Photo captured successfully!")
                if (photo != null && photo.isNotEmpty()) {
                    Log.i(appTag, "Photo data received: ${photo.size} bytes (WebP format)")

                    // Log first few bytes for debugging (header check)
                    if (photo.size >= 12) {
                        val headerBytes = photo.take(12).joinToString(" ") {
                            "%02X".format(it)
                        }
                        Log.d(appTag, "Photo header bytes: $headerBytes")
                    }

                    // Decode WebP image to Bitmap
                    try {
                        Log.d(appTag, "Attempting to decode WebP image data...")
                        val bitmap = BitmapFactory.decodeByteArray(photo, 0, photo.size)

                        if (bitmap != null) {
                            Log.i(appTag, "WebP decoded successfully! Bitmap size: ${bitmap.width}x${bitmap.height}")
                            Log.d(appTag, "Bitmap config: ${bitmap.config}")
                            Log.d(appTag, "Bitmap byte count: ${bitmap.byteCount}")

                            listener?.onPhotoSuccess(bitmap, photo.size, bitmap.width, bitmap.height)
                        } else {
                            Log.e(appTag, "Failed to decode WebP - BitmapFactory returned null")
                            listener?.onPhotoFailed("Photo received but decode failed")
                        }
                    } catch (e: Exception) {
                        Log.e(appTag, "Exception while decoding WebP image", e)
                        Log.e(appTag, "Exception type: ${e.javaClass.simpleName}")
                        Log.e(appTag, "Exception message: ${e.message}")
                        listener?.onPhotoFailed("Photo decode error: ${e.message}")
                    }
                } else {
                    Log.w(appTag, "Photo captured but data is null or empty!")
                    listener?.onPhotoFailed("Photo captured but no data received")
                }
            }

            ValueUtil.CxrStatus.RESPONSE_INVALID -> {
                Log.e(appTag, "Photo capture failed: Invalid response")
                listener?.onPhotoFailed("Photo failed: Invalid response")
            }

            ValueUtil.CxrStatus.RESPONSE_TIMEOUT -> {
                Log.e(appTag, "Photo capture failed: Timeout")
                listener?.onPhotoFailed("Photo failed: Timeout")
            }

            else -> {
                Log.e(appTag, "Photo capture failed with unknown status: $status")
                listener?.onPhotoFailed("Photo failed: Unknown status")
            }
        }
        Log.d(appTag, "=== Photo Result Callback Complete ===")
    }

    /**
     * Open the glass camera for AI scene photo capture
     * This is optional - takePhoto can be called directly
     * @param width photo width (default 1920)
     * @param height photo height (default 1080)
     * @param quality photo quality 0-100 (default 85)
     */
    fun openGlassCamera(width: Int = 1920, height: Int = 1080, quality: Int = 85) {
        Log.d(appTag, "=== Opening Glass Camera ===")
        Log.d(appTag, "Camera parameters - Width: $width, Height: $height, Quality: $quality")

        val status = CxrApi.getInstance().openGlassCamera(width, height, quality)

        Log.d(appTag, "Open camera status: $status")

        when (status) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.i(appTag, "Camera opened successfully")
                isCameraOpen = true
                listener?.onCameraOpened("Camera opened (${width}x${height}, Q:$quality)")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(appTag, "Camera open request is waiting - previous request still processing")
                listener?.onPhotoStatusUpdate("Camera opening in progress...")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(appTag, "Failed to open camera")
                listener?.onCameraOpenFailed("Camera open failed")
            }
            else -> {
                Log.w(appTag, "Unknown camera open status: $status")
                listener?.onPhotoStatusUpdate("Unknown camera status")
            }
        }

        Log.d(appTag, "=== Open Camera Complete ===")
    }

    /**
     * Take a photo using the glass camera in AI scene. Note: Due to SDK issue, the width and height is reversed
     * @param width photo width (default 1920)
     * @param height photo height (default 1080)
     * @param quality photo quality 0-100 (default 85)
     */
    fun takePhoto(width: Int = 1080, height: Int = 1920, quality: Int = 85) {
        Log.d(appTag, "=== Take Photo Requested ===")
        Log.d(appTag, "Photo parameters - Width: $width, Height: $height, Quality: $quality")
        Log.d(appTag, "Camera open: $isCameraOpen")

        // Optionally open camera first if not already open
        if (!isCameraOpen) {
            Log.i(appTag, "Camera not open, opening camera first...")
            openGlassCamera(width, height, quality)
        }

        // Take the photo
        Log.d(appTag, "Calling takeGlassPhoto API...")
        val status = CxrApi.getInstance().takeGlassPhoto(width, height, quality, photoResultCallback)

        Log.d(appTag, "Take photo request status: $status")

        when (status) {
            ValueUtil.CxrStatus.REQUEST_SUCCEED -> {
                Log.i(appTag, "Photo capture request sent successfully - waiting for callback")
                listener?.onPhotoStatusUpdate("Capturing photo... (${width}x${height}, Q:$quality)")
            }
            ValueUtil.CxrStatus.REQUEST_WAITING -> {
                Log.w(appTag, "Photo capture request waiting - previous request still processing")
                listener?.onPhotoStatusUpdate("Photo request pending...")
            }
            ValueUtil.CxrStatus.REQUEST_FAILED -> {
                Log.e(appTag, "Photo capture request failed!")
                listener?.onPhotoFailed("Photo capture failed")
            }
            else -> {
                Log.e(appTag, "Unknown photo capture status: $status")
                listener?.onPhotoStatusUpdate("Unknown photo status: $status")
            }
        }

        Log.d(appTag, "=== Take Photo Request Complete ===")
        Log.d(appTag, "Note: Photo result will be delivered via PhotoResultCallback")
    }

    /**
     * Release camera resources
     */
    fun release() {
        if (isCameraOpen) {
            Log.d(appTag, "Releasing camera resources")
            isCameraOpen = false
        }
    }
}
