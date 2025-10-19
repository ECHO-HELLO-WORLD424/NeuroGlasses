## Bluetooth Connection

## 1 Discovering Bluetooth Devices

Device discovery is performed through the standard Android Bluetooth interface.

During scanning, you can use UUID: 00009100-0000-1000-8000-00805f9b34fb to filter Rokid devices.

Here is a simple example:

```kotlin
package com.rokid.cxrandroiddocsample.helpers

//imports

/**
 * Bluetooth Helper
 * @author rokid
 * @date 2025/04/27
 * @param context Activity Register Context
 * @param initStatus Init Status
 * @param deviceFound Device Found
 */
class BluetoothHelper(
    val context: AppCompatActivity,
    val initStatus: (INIT_STATUS) -> Unit,
    val deviceFound: () -> Unit
) {
    companion object {
        const val TAG = "Rokid Glasses CXR-M"

        // Request Code
        const val REQUEST_CODE_PERMISSIONS = 100

        // Required Permissions
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }.toTypedArray()

        // Init Status
        enum class INIT_STATUS {
            NotStart,
            INITING,
            INIT_END
        }
    }

    // Scan Results
    val scanResultMap: ConcurrentHashMap<String, BluetoothDevice> = ConcurrentHashMap()

    // Bonded Devices
    val bondedDeviceMap: ConcurrentHashMap<String, BluetoothDevice> = ConcurrentHashMap()

    // Scanner
    private val scanner by lazy {
        adapter?.bluetoothLeScanner ?: run {
            Toast.makeText(context, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
            showRequestPermissionDialog()
            throw Exception("Bluetooth is not supported!!")
        }
    }

    // Bluetooth Enabled
    @SuppressLint("MissingPermission")
    private val bluetoothEnabled: MutableLiveData<Boolean> = MutableLiveData<Boolean>().apply {
        this.observe(context) {
            if (this.value == true) {
                initStatus.invoke(INIT_STATUS.INIT_END)
                startScan()
            } else {
                showRequestBluetoothEnableDialog()
            }
        }
    }

    //  Bluetooth State Listener
    private val requestBluetoothEnable = context.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            adapter = manager?.adapter
        } else {
            showRequestBluetoothEnableDialog()
        }
    }

    // Bluetooth Adapter
    private var adapter: BluetoothAdapter? = null
        set(value) {
            field = value
            value?.let {
                if (!it.isEnabled) {
                    //to Enable it
                    requestBluetoothEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                    bluetoothEnabled.postValue(true)
                }
            }
        }

    // Bluetooth Manager
    private var manager: BluetoothManager? = null
        set(value) {
            field = value
            initStatus.invoke(INIT_STATUS.INITING)
            value?.let {
                adapter = it.adapter
            } ?: run {
                Toast.makeText(context, "Bluetooth is not supported", Toast.LENGTH_SHORT).show()
                showRequestPermissionDialog()
            }
        }

    // Permission Result
    val permissionResult: MutableLiveData<Boolean> = MutableLiveData<Boolean>().apply {
        this.observe(context) {
            if (it == true) {
                manager =
                    context.getSystemService(AppCompatActivity.BLUETOOTH_SERVICE) as BluetoothManager
            } else {
                showRequestPermissionDialog()
            }
        }
    }

    // Scan Listener
    val scanListener = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.let { r ->
                r.device.name?.let {
                    scanResultMap[it] = r.device
                    deviceFound.invoke()
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Toast.makeText(
                context,
                "Scan Failed $errorCode",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    // check permissions
    fun checkPermissions() {
        initStatus.invoke(INIT_STATUS.NotStart)
        context.requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        context.registerReceiver(
            bluetoothStateListener,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        )
    }

    // Release
    @SuppressLint("MissingPermission")
    fun release() {
        context.unregisterReceiver(bluetoothStateListener)
        stopScan()
        permissionResult.postValue(false)
        bluetoothEnabled.postValue(false)
    }


    // Show Request Permission Dialog
    private fun showRequestPermissionDialog() {
        AlertDialog.Builder(context)
            .setTitle("Permission")
            .setMessage("Please grant the permission")
            .setPositiveButton("OK") { _, _ ->
                context.requestPermissions(
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    context,
                    "Permission does not granted, FINISH",
                    Toast.LENGTH_SHORT
                ).show()
                context.finish()
            }
            .show()
    }

    // Show Request Bluetooth Enable Dialog
    private fun showRequestBluetoothEnableDialog() {
        AlertDialog.Builder(context)
            .setTitle("Bluetooth")
            .setMessage("Please enable the bluetooth")
            .setPositiveButton("OK") { _, _ ->
                requestBluetoothEnable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
            .setNegativeButton("Cancel") { _, _ ->
                Toast.makeText(
                    context,
                    "Bluetooth does not enabled, FINISH",
                    Toast.LENGTH_SHORT
                ).show()
                context.finish()
            }
            .show()
    }

    // Start Scan
    @SuppressLint("MissingPermission")
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        scanResultMap.clear()
        val connectedList = getConnectedDevices()
        for (device in connectedList) {
            device.name?.let {
                if (it.contains("Glasses", false)) {
                    bondedDeviceMap[it] = device
                    deviceFound.invoke()
                }
            }
        }

        adapter?.bondedDevices?.forEach { d ->
            d.name?.let {
                if (it.contains("Glasses", false)) {
                    if (bondedDeviceMap[it] == null) {
                        bondedDeviceMap[it] = d
                    }
                }
                deviceFound.invoke()
            }
        }

        try {
            scanner.startScan(
                listOf<ScanFilter>(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid.fromString("00009100-0000-1000-8000-00805f9b34fb"))//Rokid Glasses Service
                        .build()
                ), ScanSettings.Builder().build(),
                scanListener
            )
        } catch (e: Exception) {
            Toast.makeText(context, "Scan Failed ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Stop Scan
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun stopScan() {
        scanner.stopScan(scanListener)
    }

    //  Get Connected Devices
    @SuppressLint("MissingPermission")
    private fun getConnectedDevices(): List<BluetoothDevice> {
        return adapter?.bondedDevices?.filter { device ->
            try {
                val isConnected =
                    device::class.java.getMethod("isConnected").invoke(device) as Boolean
                isConnected
            } catch (_: Exception) {
                Toast.makeText(context, "Get Connected Devices Failed", Toast.LENGTH_SHORT).show()
                false
            }
        } ?: emptyList()
    }

    // Bluetooth State Listener
    val bluetoothStateListener = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF -> {
                        initStatus.invoke(INIT_STATUS.NotStart)
                        bluetoothEnabled.postValue(false)
                    }
                }
            }
        }
    }

}
```

## 2 Initialize Bluetooth and Get Bluetooth Information

Device initialization is controlled through the CxrApi class of the CXR_M SDK.

Method to initialize the Bluetooth module: `fun initBluetooth(context: Context, device: BluetoothDevice, callback: BluetoothStatusCallback)`.

Here is a simple usage example:

```kotlin
/**
 * Init Bluetooth
 *
 * @param context   Application Context
 * @param device     Bluetooth Device
 */
fun initDevice(context: Context, device: BluetoothDevice){
    /**
     * Init Bluetooth
     *
     * @param context   Application Context
     * @param device     Bluetooth Device
     * @param callback   Bluetooth Status Callback
     */
    CxrApi.getInstance().initBluetooth(context, device,  object : BluetoothStatusCallback{
        /**
         * Connection Info
         *
         * @param socketUuid   Socket UUID
         * @param macAddress   Classic Bluetooth MAC Address
         * @param rokidAccount Rokid Account
         * @param glassesType  Device Type, 0-no display, 1-have display
         */
        override fun onConnectionInfo(
            socketUuid: String?,
            macAddress: String?,
            rokidAccount: String?,
            glassesType: Int
        ) {
            socketUuid?.let { uuid ->
                macAddress?.let { address->
                    connect(context, uuid, address)
                }?:run {
                    Log.e(TAG, "macAddress is null")
                }
            }?:run{
                Log.e(TAG, "socketUuid is null")
            }
        }

        /**
         * Connected
         */
        override fun onConnected() {
        }

        /**
         * Disconnected
         */
        override fun onDisconnected() {
        }

        /**
         * Failed
         *
         * @param errorCode   Error Code:
         * @see ValueUtil.CxrBluetoothErrorCode
         * @see ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID  Parameter Invalid
         * @see ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED BLE Connect Failed
         * @see ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED Socket Connect Failed
         * @see ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown
         */
        override fun onFailed(p0: ValueUtil.CxrBluetoothErrorCode?) {
        }

    })
}
```

Where `BluetoothStatusCallback` is the Bluetooth information listener interface.

Including:

- `fun onConnected()`: Callback when Bluetooth connection is successful
- `fun onDisconnected()`: Callback when Bluetooth connection is lost
- `fun onConnectionInfo(p0: String?, p1: String?)`: Device information update interface
  - `socketUuid`: Device UUID
  - `macAddress`: Device MAC address
  - `rokidAccount`: Rokid account
  - `glassesType`: Glasses type

## 3 Connect to Bluetooth Module

Device connection is controlled through the CxrApi class of the CXR_M SDK.

Method to connect Bluetooth module: `fun connectBluetooth(context: Context, socketUuid: String, macAddress: String, callback: BluetoothStatusCallback)`.

Here is a simple usage example:

```kotlin
/**
 *  Connect
 *
 *  @param context   Application Context
 *  @param socketUuid   Socket UUID
 *  @param macAddress   Classic Bluetooth MAC Address
 */
fun connect(context: Context, socketUuid: String, macAddress: String){
    /**
     * Connect
     */
    CxrApi.getInstance().connectBluetooth(context, socketUuid, macAddress, object : BluetoothStatusCallback{
        /**
         * Connection Info
         *
         * @param socketUuid   Socket UUID
         * @param macAddress   Classic Bluetooth MAC Address
         * @param rokidAccount Rokid Account
         * @param glassesType  Device Type, 0-no display, 1-have display
         */
        override fun onConnectionInfo(
            socketUuid: String?,
            macAddress: String?,
            rokidAccount: String?,
            glassesType: Int
        ) {
            //
        }

        /**
         * Connected
         */
        override fun onConnected() {
            Log.d(TAG, "Connected")
        }

        /**
         * Disconnected
         */
        override fun onDisconnected() {
            Log.d(TAG, "Disconnected")
        }

        /**
         * Failed
         *
         * @param errorCode   Error Code:
         * @see ValueUtil.CxrBluetoothErrorCode
         * @see ValueUtil.CxrBluetoothErrorCode.PARAM_INVALID  Parameter Invalid
         * @see ValueUtil.CxrBluetoothErrorCode.BLE_CONNECT_FAILED BLE Connect Failed
         * @see ValueUtil.CxrBluetoothErrorCode.SOCKET_CONNECT_FAILED Socket Connect Failed
         * @see ValueUtil.CxrBluetoothErrorCode.UNKNOWN Unknown
         */
        override fun onFailed(p0: ValueUtil.CxrBluetoothErrorCode?) {
            Log.e(TAG, "Failed")
        }

    })
}
```

Where `BluetoothStatusCallback` is the Bluetooth information listener interface.

Including:

- `fun onConnected()`: Callback when Bluetooth connection is successful
- `fun onDisconnected()`: Callback when Bluetooth connection is lost
- `fun onConnectionInfo(p0: String?, p1: String?)`: Device information update interface
  - `socketUuid`: Device UUID
  - `macAddress`: Device MAC address
  - `rokidAccount`: Rokid account
  - `glassesType`: Glasses type

## 4 Get Bluetooth Communication Module Connection Status

You can check the current Bluetooth communication module connection status through the `fun isBluetoothConnected():Boolean` method.

Return value:

- `true`: Bluetooth communication module is connected
- `false`: Bluetooth module is not connected

Simple example:

```kotlin
 /**
 * Get Connection Status
 *
 * @return  Connection Status: true-connected, false-disconnected
 */
fun getConnectionStatus(): Boolean{
    return CxrApi.getInstance().isBluetoothConnected
}
```

## 5 Deinitialize Bluetooth

You can deinitialize through `fun deinitBluetooth()`.

Simple example:

```kotlin
/**
 * DeInit Bluetooth
 */
fun deInit(){
    CxrApi.getInstance().deinitBluetooth()
}
```
