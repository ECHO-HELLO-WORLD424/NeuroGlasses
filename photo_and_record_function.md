Photography/Video Recording/Audio Recording

# Photography

Due to the need to activate the camera for photography, which is a high-energy operation in normal use, and considering different methods for using the photography results, three photography approaches are provided:

- Single function key photography, with photo results stored in unsynchronized media files.
- Photography in AI scenarios, with photo results transmitted to the mobile device through the Bluetooth channel.
- Provides a photography interface to obtain the storage address of photo results or store photo results in unsynchronized media files.

## 1 Set Photo Parameters for Function Key

You can set parameters for single function key photography through the `fun setPhotoParams(width: Int, height: Int): ValueUtil.CxrStatus` interface.

```kotlin
/**
 * set photo params
 *
 * @param width photo width
 * @param height photo height
 * @return set status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun setPhotoParams(width: Int, height: Int): ValueUtil.CxrStatus{
    return CxrApi.getInstance().setPhotoParams(width, height)
}
```

## 2 Photography in AI Scenarios

In AI scenarios, you can use the `fun openGlassCamera(width: Int, height: Int, quality: Int): ValueUtil.CxrStatus` interface to open the camera, and the `fun takeGlassPhoto(width: Int, height: Int, quality: Int, callback: PhotoResultCallback): ValueUtil.CxrStatus` interface to take photos. You can control photo resolution through the width and height parameters in the interface, control image compression quality through the quality parameter, and obtain WebP format image data through the `PhotoResultCallback` callback.

Allowed resolutions: [4032x3024, 4000x3000, 4032x2268, 3264x2448, 3200x2400,
2268x3024, 2876x2156, 2688x2016, 2582x1936, 2400x1800, 1800x2400,
2560x1440, 2400x1350, 2048x1536, 2016x1512, 1920x1080, 1600x1200,
1440x1080, 1280x720, 720x1280, 1024x768, 800x600, 648x648, 854x480,
800x480, 640x480, 480x640, 352x288, 320x240, 320x180, 176x144].

***Tips: Note that image files are transmitted via Bluetooth. Please choose smaller image formats when possible based on AI scenario requirements.***

Reference code:

```kotlin
// photo result callback
private val result =object :PhotoResultCallback{
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

## 3 Invoke Camera to Take Photos

The SDK provides an interface to directly invoke the camera: `fun takePhoto(width: Int, height: Int, quality: Int, callback: PhotoPathCallback)`. You can obtain the photo result path through the `PhotoPathCallback`. After obtaining the path, you can synchronize the image to the mobile device separately through a file synchronization solution.

Allowed resolutions: [4032x3024, 4000x3000, 4032x2268, 3264x2448, 3200x2400,
2268x3024, 2876x2156, 2688x2016, 2582x1936, 2400x1800, 1800x2400,
2560x1440, 2400x1350, 2048x1536, 2016x1512, 1920x1080, 1600x1200,
1440x1080, 1280x720, 720x1280, 1024x768, 800x600, 648x648, 854x480,
800x480, 640x480, 480x640, 352x288, 320x240, 320x180, 176x144].

Example code:

```kotlin
// photo path callback
private val photoPathResult = object : PhotoPathCallback{
    /**
     * photo path callback
     *
     * @param status photo path status
     * @see ValueUtil.CxrStatus
     * @see ValueUtil.CxrStatus.RESPONSE_SUCCEED response succeed
     * @see ValueUtil.CxrStatus.RESPONSE_INVALID response invalid
     * @see ValueUtil.CxrStatus.RESPONSE_TIMEOUT response timeout
     * @param path photo path
     */
    override fun onPhotoPath(
        status: ValueUtil.CxrStatus?,
        path: String?
    ) {

    }

}

/**
 * take photo path
 *
 * @param width photo width
 * @param height photo height
 * @param quality photo quality
 *
 * @return take photo path result
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun takePhotoPath(width: Int, height: Int, quality: Int): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().takeGlassPhoto(width, height, quality, photoPathResult)
}
```

# Video Recording

## 1 Set Video Recording Parameters

You can set parameters for video recording through the `fun setVideoParams(duration: Int, fps: Int,width: Int, height: Int, unit: Int): ValueUtil.CxrStatus` interface.

```kotlin
/**
 * set video params
 *
 * @param duration duration
 * @param fps fps,support{30}
 * @param width width
 * @param height height
 * @param unit unit,0:minute,1:second
 * @return set video params result
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun setVideoParams(duration: Int, fps: Int, width: Int, height: Int, unit: Int): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().setVideoParams(duration, fps, width, height, unit)
}
```

## 2 Start/Stop Video Recording

Currently, the SDK only supports starting video recording by invoking the glasses-side video recording scene. Use the interface `fun controlScene(sceneType: ValueUtil.CxrSceneType, openOrClose: Boolean, otherParams: String?)`, set sceneType to `ValueUtil.CxrSceneType.VIDEO_RECORD`, openOrClose to `True` to start, and `False` to stop.

Example code:

```kotlin
/**
 * Open or close video record
 *
 * @param openOrClose true: open video record, false: close video record
 * @return ValueUtil.CxrStatus
 * @see ValueUtil.CxrSceneType.VIDEO_RECORD
 * @see CxrApi.controlScene
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun openOrCloseVideoRecord(openOrClose: Boolean): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().controlScene(ValueUtil.CxrSceneType.VIDEO_RECORD, openOrClose, null)
}
```

# Audio Recording

You can start audio recording through the `fun openAudioRecord(codecType: Int, streamType: String?): ValueUtil.CxrStatus?` interface, stop recording through `fun closeAudioRecord(streamType: String): ValueUtil.CxrStatus?`, and set callback listeners for recording results through `fun setVideoStreamListener(callback: AudioStreamListener)`.

Example code:

```kotlin
// Audio stream listener
private val audioStreamListener = object : AudioStreamListener{
    /**
     * Called when audio stream started.
     *
     * @param codecType The stream codec type: 1:pcm, 2:opus.
     * @param streamType The stream type such as "AI_assistant"
     */
    override fun onStartAudioStream(codecType: Int, streamType: String?) {

    }

    /**
     * Called when audio stream data.
     *
     * @param data The audio stream data.
     * @param offset The offset of audio stream data.
     * @param length The length of audio stream data.
     */
    override fun onAudioStream(data: ByteArray?, offset: Int, length: Int) {
    }

}

/**
 * Set audio stream listener.
 *
 * @param set Set true to set audio stream listener, otherwise remove audio stream listener.
 */
fun setVideoStreamListener(set: Boolean) {
    CxrApi.getInstance().setAudioStreamListener(if (set) audioStreamListener else null)
}

/**
 * Open audio record.
 *
 * @param codecType The stream codec type: 1:pcm, 2:opus.
 * @param streamType The stream type such as "AI_assistant", it is same as the stream type in audio stream listener.
 *
 * @return The status of open audio record.
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun openAudioRecord(codecType: Int, streamType: String?): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().openAudioRecord(codecType, streamType)
}

/**
 * Close audio record.
 *
 * @param streamType The stream type such as "AI_assistant", it is same as the stream type in audio stream listener.
 *
 * @return The status of close audio record.
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun closeAudioRecord(streamType: String): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().closeAudioRecord(streamType)
}
```
