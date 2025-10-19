AI Scene Operations


# 1 Listen to AI Events from Glasses


The CXR-M SDK can set up an `AiEventListener` through `fun setAiEventListener()` to listen for AI scene events from the Glasses.

```kotlin
private val aiEventListener = object : AiEventListener {
 /**
 * When the key long pressed
 */
 override fun onAiKeyDown() {
 } 
/**
 * When the key released, currently their have no effect
 */
 override fun onAiKeyUp() {
 } 
/**
 * When the Ai Scene exit
 */
 override fun onAiExit() {
 }
} 
/**
 * Set the AiEventListener
 * @param set true: set the listener, false: remove the listener
 */
fun setAiEventListener(set: Boolean) {
 CxrApi.getInstance().setAiEventListener(if (set) aiEventListener else null)
}
```

# 2 Send Exit Event to Glasses

The mobile app can actively send an exit AI event `fun sendExitEvent(): ValueUtil.CxrStatus?` to exit the AI scene on the glasses.

```kotlin
/**
 * Send the exit event to the Ai Scene
 * @return the status of the exit operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun sendExitEvent(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().sendExitEvent()
}
```

# 3 Send ASR Content to Glasses

After the mobile app receives ASR (Automatic Speech Recognition) results, it can push the content to the glasses using `fun sendAsrContent(content: String): ValueUtil.CxrStatus?`. If the ASR recognition result is empty, you also need to notify the glasses using `fun notifyAsrNone(): ValueUtil.CxrStatus?`. Additionally, ASR recognition errors should be notified to the glasses using `fun notifyAsrError(): ValueUtil.CxrStatus?`. Finally, when ASR recognition ends, you need to notify the glasses using `fun notifyAsrEnd(): ValueUtil.CxrStatus?`.

```kotlin
/**
 * Send the asr content to the Ai Scene
 * @param content the content to send
 * @return the status of the send operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun sendAsrContent(content: String): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().sendAsrContent(content)
}

/**
 * Notify the Ai Scene that the asr content is none
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyAsrNone(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyAsrNone()
}

/**
 * Notify the Ai Scene that the asr content is error
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyAsrError(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyAsrError()
}

/**
 * Notify the Ai Scene that the asr content is end
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyAsrEnd(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyAsrEnd()
}
```

# 4 Camera Operations in AI Workflow

During the AI process, if you need to capture real-time images from the Glasses camera, you can first use `fun openGlassCamera(width: Int, height: Int, quality: Int): ValueUtil.CxrStatus?` (optional) to open the camera, then use `fun takePhoto(width: Int, height: Int, quality: Int, callback: PhotoResultCallback): ValueUtil.CxrStatus?` to take a photo and receive the result in the `PhotoResultCallback`. Note that the quality value range is [0-100].

```kotlin
// photo result callback
private val result = object : PhotoResultCallback {
    /**
     * photo result callback
     *
     * @param status photo take status
     * @see ValueUtil.CxrStatus
     * @see ValueUtil.CxrStatus.RESPONSE_SUCCEED response succeed
     * @see ValueUtil.CxrStatus.RESPONSE_INVALID response invalid
     * @see ValueUtil.CxrStatus.RESPONSE_TIMEOUT response timeout
     * @param photo WebP photo data byte array
     */
    override fun onPhotoResult(
        status: ValueUtil.CxrStatus?,
        photo: ByteArray?
    ) {

    }
}

/**
 * open ai camera
 *
 * @param width photo width
 * @param height photo height
 * @param quality photo quality range [0-100]
 *
 * @return open camera result
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun aiOpenCamera(width: Int, height: Int, quality: Int): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().openGlassCamera(width, height, quality)
}

/**
 * take photo
 *
 * @param width photo width
 * @param height photo height
 * @param quality photo quality range[0-100]
 *
 * @return take photo result
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun takePhoto(width: Int, height: Int, quality: Int): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().takeGlassPhoto(width, height, quality, result)
}
```

# 5 AI Response Results in AI Workflow

After passing ASR results and images to the AI, the AI will return results. Typically, the application will choose to use TTS (Text-to-Speech) for voice playback. You can notify the glasses of the AI results using `fun sendTTSContent(content: String): ValueUtil.CxrStatus?`, and notify the glasses when TTS playback is finished using `fun notifyTtsAudioFinished(): ValueUtil.CxrStatus?`.

```kotlin
/**
 * Send the tts(Ai Return) content to the Ai Scene
 * @param content the content to send
 * @return the status of the send operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun sendTTSContent(content: String): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().sendTtsContent(content)
}

/**
 * Notify the Ai Scene that the tts audio is finished
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyTtsAudioFinished(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyTtsAudioFinished()
}
```

# 6 AI Error Handling

- No network: `fun notifyNoNetwork(): ValueUtil.CxrStatus?`
- Image upload error: `fun notifyPicUploadError(): ValueUtil.CxrStatus?`
- AI request failed: `fun notifyAiError(): ValueUtil.CxrStatus?`

```kotlin
/**
 * Notify the Ai Scene that there is no network
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyNoNetwork(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyNoNetwork()
}

/**
 * Notify the Ai Scene that the pic upload error
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyPicUploadError(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyPicUploadError()
}

/**
 * Notify the Ai Scene that the ai error
 * @return the status of the notify operation
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun notifyAiError(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().notifyAiError()
}
```


