package com.patrick.neuroglasses.helpers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Helper class to manage camera operations for AR Glasses
 * Handles camera initialization, photo capture, and resource cleanup
 */
class CameraHelper(
    private val context: Context,
    private val appTag: String = "CameraHelper"
) {
    interface CameraListener {
        fun onPhotoCaptured(bitmap: Bitmap)
        fun onCameraError(error: String)
    }

    private var listener: CameraListener? = null
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    /**
     * Set the listener for camera callbacks
     * Pass null to clear the listener and prevent memory leaks
     */
    fun setListener(listener: CameraListener?) {
        this.listener = listener
    }

    /**
     * Initialize camera manager and background thread
     */
    fun initialize() {
        cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        // Start background thread for camera operations
        backgroundThread = HandlerThread("CameraBackground").apply { start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    /**
     * Open the camera device
     */
    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Camera permission not granted", Toast.LENGTH_SHORT).show()
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
                    listener?.onCameraError("Camera error code: $error")
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(appTag, "Failed to open camera: ${e.message}", e)
            listener?.onCameraError("Failed to open camera: ${e.message}")
        }
    }

    /**
     * Take a photo with the camera
     */
    fun takePhoto() {
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

                    // Decode bitmap
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    listener?.onPhotoCaptured(bitmap)
                    Log.i(appTag, "Photo captured")
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
                        listener?.onCameraError("Camera capture session configuration failed")
                    }
                }
            )
            camera.createCaptureSession(sessionConfiguration)
        } catch (e: Exception) {
            Log.e(appTag, "Failed to take photo: ${e.message}", e)
            listener?.onCameraError("Failed to take photo: ${e.message}")
        }
    }

    /**
     * Remove all pending handler callbacks
     */
    fun clearCallbacks() {
        backgroundHandler?.removeCallbacksAndMessages(null)
    }

    /**
     * Close camera resources
     */
    fun closeCamera() {
        cameraCaptureSession?.close()
        cameraCaptureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    /**
     * Release all camera resources including background thread
     */
    fun release() {
        clearCallbacks()
        closeCamera()

        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(appTag, "Error stopping background thread", e)
        }

        listener = null
        Log.i(appTag, "Camera helper released")
    }

    /**
     * Restart background thread if needed
     */
    fun ensureBackgroundThread() {
        if (backgroundThread == null) {
            backgroundThread = HandlerThread("CameraBackground").apply { start() }
            backgroundHandler = Handler(backgroundThread!!.looper)
        }
    }

    /**
     * Get the background handler for posting delayed tasks
     */
    fun getBackgroundHandler(): Handler? = backgroundHandler
}
