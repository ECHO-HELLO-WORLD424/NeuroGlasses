Data Operations

## 1 Send Data to Glasses

**Before sending data to the glasses, ensure the device is in a Bluetooth connected state.**

You can send stream data to the glasses through the `fun sendStream(type: CxrStreamType, stream: ByteArray, fileName: String, cb: SendStatusCallback): CxrStatus?` method. You can be notified when the data stream is sent successfully in the `fun onSendStreamSucceed()` callback of the `SendStatusCallback` interface, and obtain failure reasons in the `fun onSendFailed(p0: ValueUtil.CxrSendErrorCode?)` callback.

```kotlin
val streamCallback = object : SendStatusCallback{
    /**
     * send succeed
     */
    override fun onSendSucceed() {

    }

    /**
     * send failed
     * @param errorCode
     * @see ValueUtil.CxrSendErrorCode
     */
    override fun onSendFailed(errorCode: ValueUtil.CxrSendErrorCode?) {
        TODO("Not yet implemented")
    }

}

/**
 * send stream
 * @param type
 * @see ValueUtil.CxrStreamType
 * @see ValueUtil.CxrStreamType.WORD_TIPS teleprompter words
 * @param stream
 * @param fileName
 * @return send status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun sendStream(type: ValueUtil.CxrStreamType, stream: ByteArray, fileName: String): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().sendStream(type, stream, fileName, streamCallback)
}
```

## 2 Read Unsynchronized Media Files from Glasses

**When reading unsynchronized media files from the glasses, ensure the device is in a Bluetooth connected state**

You can obtain unsynchronized media files from the glasses through the `fun getUnsyncNum(cb: UnsyncNumResultCallback): CxrStatus` method. The quantity of unsynchronized media files is returned in the `fun onUnsyncNumResult(status: ValueUtil.CxrStatus?,audioNum: Int,pictureNum: Int,videoNum: Int)` callback of the `UnsyncNumResultCallback` interface.

```kotlin
// get unsync num
private val unSyncCallback = object : UnsyncNumResultCallback{
    /**
     * get un sync num result
     * @param status
     * @see ValueUtil.CxrStatus
     * @see ValueUtil.CxrStatus.RESPONSE_SUCCEED response succeed
     * @see ValueUtil.CxrStatus.RESPONSE_INVALID response invalid
     * @see ValueUtil.CxrStatus.RESPONSE_TIMEOUT response timeout
     * @param audioNum
     * @param pictureNum
     * @param videoNum
     */
    override fun onUnsyncNumResult(
        status: ValueUtil.CxrStatus?,
        audioNum: Int,
        pictureNum: Int,
        videoNum: Int
    ) {

    }

}

/**
 * get unsync num
 *
 * @return get unsync num status
 * @see ValueUtil.CxrStatus
 * @see ValueUtil.CxrStatus.REQUEST_SUCCEED request succeed
 * @see ValueUtil.CxrStatus.REQUEST_WAITING request waiting, do not request again
 * @see ValueUtil.CxrStatus.REQUEST_FAILED request failed
 */
fun getUnsyncNum(): ValueUtil.CxrStatus? {
    return CxrApi.getInstance().getUnsyncNum(unSyncCallback)
}
```

## 3 Monitor Glasses Media File Updates

You can monitor media file updates from the glasses by setting the `MediaFilesUpdateListener` interface.

```kotlin
// set media files update listener
private val mediaFileUpdateListener = object : MediaFilesUpdateListener {
    /**
     * media files updated
     */
    override fun onMediaFilesUpdated() {
    }
}
/**
 * set media files update listener
 *
 * @param set true: set listener, false: remove listener
 */
fun setMediaFilesUpdateListener(set: Boolean){
    CxrApi.getInstance().setMediaFilesUpdateListener(if (set) mediaFileUpdateListener else null)
}
```

## 4 Synchronize Media Files

**To synchronize media files from the glasses, you need to use the Wi-Fi communication module. Please complete the Wi-Fi communication module initialization before synchronization**

### 4.1 Synchronize All Files of Specified Type

You can start media file synchronization through the `fun startSync(svaePaht: String, types: Array<ValueUtil.CxrMediaType>, SyncStatusCallbackcallback): Boolean` method.

Where:

`savaPath`: File storage path (please note file management permissions)

`types`: `CxrMediaType`, supports synchronizing multiple types simultaneously.

- `CxrMediaType`:
  - AUDIO: Audio files
  - PICTURE: Image files
  - VIDEO: Video files
  - ALL: All files

`callback`: `CxrSyncCallback`, synchronization callback

- `CxrSyncCallback`:
  - `fun onSyncStart()`: Callback when synchronization starts
  - `fun onSingleFileSynced(fileName: String?)`: Callback when a single file <fileName> is successfully synchronized
  - `fun onSyncFailed()`: Callback when synchronization fails
  - `fun onSyncFinished()`: Callback when synchronization is complete

Return value: Returns true on successful request, false on failed request.

Example:

```kotlin
// sync status callback
private val syncCallback = object : SyncStatusCallback{
    /**
     * sync start
     */
    override fun onSyncStart() {
    }

    /**
     * sync single file
     * @param fileName file name which sync success
     */
    override fun onSingleFileSynced(fileName: String?) {
    }

    /**
     * sync failed
     */
    override fun onSyncFailed() {
    }

    /**
     * sync finished
     */
    override fun onSyncFinished() {
    }
}

/**
 * start sync
 *
 * @param svaePaht save path
 * @param types sync types
 * @return sync status true: sync succeed, false: sync failed
 */
fun startSync(svaePaht: String, types: Array<ValueUtil.CxrMediaType>): Boolean {
    return CxrApi.getInstance().startSync(svaePaht, types, syncCallback)
}
```

### 4.2 Synchronize a Single File

You can also use the `fun syncSingleFiles(savePath: String, mediaType: ValueUtil.CxrMediaType, filePath: String, SyncStatusCallbackcallback): Boolean` interface to synchronize a single media file of a specified type.

Where:

- filePath: File path on the glasses

```kotlin
// sync status callback
private val syncCallback = object : SyncStatusCallback{
    /**
     * sync start
     */
    override fun onSyncStart() {
    }

    /**
     * sync single file
     * @param fileName file name which sync success
     */
    override fun onSingleFileSynced(fileName: String?) {
    }

    /**
     * sync failed
     */
    override fun onSyncFailed() {
    }

    /**
     * sync finished
     */
    override fun onSyncFinished() {
    }
}
/**
 * sync single file
 *
 * @param savePath save path
 * @param mediaType media type
 * @param fileName file name
 *
 * @return sync status true: sync succeed, false: sync failed
 */
fun syncSingleFiles(savePath: String , mediaType: ValueUtil.CxrMediaType, fileName: String): Boolean {
    return CxrApi.getInstance().syncSingleFile(savePath,  mediaType,fileName, syncCallback)
}
```

### 4.3 Stop Synchronization

You can stop synchronization during the synchronization process using the `fun stopSync()` method

```kotlin
private fun stopSync() {
    CxrApi.getInstance().stopSync()
}
```

# 
