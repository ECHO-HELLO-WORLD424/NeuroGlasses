package com.patrick.neuroglasses.activities

import android.Manifest
import android.content.Intent
import android.util.Log
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.patrick.neuroglasses.R
import com.patrick.neuroglasses.helpers.BluetoothHelper
import com.rokid.cxr.client.extend.CxrApi
import com.rokid.cxr.client.extend.callbacks.BluetoothStatusCallback
import com.rokid.cxr.client.utils.ValueUtil

class MainActivity : AppCompatActivity() {
    private val appTag = "NeuroGlasses"

    companion object {
        // Request Code
        const val REQUEST_CODE_PERMISSIONS = 100
    }

    private lateinit var bluetoothHelper: BluetoothHelper
    private lateinit var deviceListView: ListView
    private lateinit var statusTextView: TextView
    private lateinit var navigateButton: Button
    private lateinit var testUIButton: Button
    private lateinit var scanButton: Button
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val deviceList = mutableListOf<String>()
    private val deviceMap = mutableMapOf<String, BluetoothDevice>()
    private var isGlassesConnected = false

    // Helper method to check if Bluetooth permissions are granted
    private fun hasBluetoothPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
        }
    }


    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize UI components
        deviceListView = findViewById(R.id.deviceListView)
        statusTextView = findViewById(R.id.statusTextView)
        navigateButton = findViewById(R.id.navigateButton)
        testUIButton = findViewById(R.id.testUIButton)
        scanButton = findViewById(R.id.scanButton)

        // Setup device list adapter
        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceList)
        deviceListView.adapter = deviceListAdapter

        // Setup device list click listener
        deviceListView.setOnItemClickListener { _, _, position, _ ->
            val deviceName = deviceList[position]
            val device = deviceMap[deviceName]
            device?.let {
                showConnectDialog(it)
            }
        }

        // Setup navigate button
        navigateButton.setOnClickListener {
            val intent = Intent(this, AITestActivity::class.java)
            startActivity(intent)
        }

        // Setup settings button
        testUIButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        // Setup scan button
        scanButton.setOnClickListener {
            if (hasBluetoothPermissions()) {
                try {
                    bluetoothHelper.startScan()
                    statusTextView.setText(R.string.status_scanning)
                } catch (e: SecurityException) {
                    Log.e(appTag, "SecurityException when starting scan", e)
                    Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize BluetoothHelper
        bluetoothHelper = BluetoothHelper(
            context = this,
            initStatus = { status ->
                runOnUiThread {
                    when (status) {
                        BluetoothHelper.Companion.INITSTATUS.NotStart -> {
                            statusTextView.setText(R.string.status_bluetooth_not_started)
                        }
                        BluetoothHelper.Companion.INITSTATUS.INITING -> {
                            statusTextView.setText(R.string.status_bluetooth_initializing)
                        }
                        BluetoothHelper.Companion.INITSTATUS.INIT_END -> {
                            statusTextView.setText(R.string.status_bluetooth_initialized)
                        }
                    }
                }
            },
            deviceFound = {
                runOnUiThread {
                    updateDeviceList()
                }
            }
        )

        // Check permissions
        bluetoothHelper.checkPermissions()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_PERMISSIONS -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
                if (allGranted) {
                    Log.d(appTag, "Bluetooth permissions granted")
                    bluetoothHelper.permissionResult.postValue(true)
                } else {
                    Log.d(appTag, "Bluetooth permissions denied")
                    bluetoothHelper.permissionResult.postValue(false)
                }
            }
        }
    }

    private fun updateDeviceList() {
        deviceList.clear()
        deviceMap.clear()

        // Add bonded devices
        bluetoothHelper.bondedDeviceMap.forEach { (name, device) ->
            val displayName = getString(R.string.device_bonded, name)
            deviceList.add(displayName)
            deviceMap[displayName] = device
        }

        // Add scanned devices
        bluetoothHelper.scanResultMap.forEach { (name, device) ->
            if (!deviceMap.containsValue(device)) {
                deviceList.add(name)
                deviceMap[name] = device
            }
        }

        deviceListAdapter.notifyDataSetChanged()
        statusTextView.text = getString(R.string.status_devices_found, deviceList.size)
    }

    private fun showConnectDialog(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val deviceName = device.name ?: "Unknown Device"
            AlertDialog.Builder(this)
                .setTitle(R.string.dialog_connect_title)
                .setMessage(getString(R.string.dialog_connect_message, deviceName))
                .setPositiveButton(R.string.dialog_connect_positive) { _, _ ->
                    connectToDevice(device)
                }
                .setNegativeButton(R.string.dialog_connect_negative, null)
                .show()
        } catch (e: SecurityException) {
            Log.e(appTag, "SecurityException when accessing device name", e)
            Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermissions()) {
            Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val deviceName = device.name ?: "Unknown Device"
            statusTextView.text = getString(R.string.status_connecting, deviceName)

            // Initialize Bluetooth with the device
            CxrApi.getInstance().initBluetooth(this, device, object : BluetoothStatusCallback {
                override fun onConnectionInfo(
                    socketUuid: String?,
                    macAddress: String?,
                    rokidAccount: String?,
                    glassesType: Int
                ) {
                    runOnUiThread {
                        Log.d(appTag, "Connection Info: UUID=$socketUuid, MAC=$macAddress, Account=$rokidAccount, Type=$glassesType")
                        socketUuid?.let { uuid ->
                            macAddress?.let { address ->
                                // Connect to the device
                                connectBluetooth(uuid, address)
                            } ?: run {
                                Log.e(appTag, "macAddress is null")
                                statusTextView.setText(R.string.status_connection_failed_mac_null)
                            }
                        } ?: run {
                            Log.e(appTag, "socketUuid is null")
                            statusTextView.setText(R.string.status_connection_failed_uuid_null)
                        }
                    }
                }

                override fun onConnected() {
                    runOnUiThread {
                        Log.d(appTag, "Device connected (initBluetooth callback)")
                        isGlassesConnected = true
                        try {
                            statusTextView.text = getString(R.string.status_connected, device.name ?: "Unknown Device")
                        } catch (e: SecurityException) {
                            Log.e(appTag, "SecurityException when accessing device name", e)
                            statusTextView.setText(R.string.status_connected_successfully)
                        }
                        Toast.makeText(this@MainActivity, R.string.toast_connected, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onDisconnected() {
                    runOnUiThread {
                        Log.d(appTag, "Device disconnected (initBluetooth callback)")
                        isGlassesConnected = false
                        try {
                            statusTextView.text = getString(R.string.status_disconnected, device.name ?: "Unknown Device")
                        } catch (e: SecurityException) {
                            Log.e(appTag, "SecurityException when accessing device name", e)
                            statusTextView.setText(R.string.status_disconnected_simple)
                        }
                        Toast.makeText(this@MainActivity, R.string.toast_disconnected, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailed(errorCode: ValueUtil.CxrBluetoothErrorCode?) {
                    runOnUiThread {
                        Log.e(appTag, "Connection failed: $errorCode")
                        statusTextView.text = getString(R.string.status_connection_failed, errorCode.toString())
                        Toast.makeText(this@MainActivity, getString(R.string.toast_connection_failed, errorCode.toString()), Toast.LENGTH_SHORT).show()
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e(appTag, "SecurityException when connecting to device", e)
            Toast.makeText(this, R.string.toast_bluetooth_permission_not_granted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectBluetooth(socketUuid: String, macAddress: String) {
        CxrApi.getInstance().connectBluetooth(this, socketUuid, macAddress, object : BluetoothStatusCallback {
            override fun onConnectionInfo(
                socketUuid: String?,
                macAddress: String?,
                rokidAccount: String?,
                glassesType: Int
            ) {
                // Connection info already received
            }

            override fun onConnected() {
                runOnUiThread {
                    Log.d(appTag, "Bluetooth connected (connectBluetooth callback)")
                    isGlassesConnected = true
                    statusTextView.setText(R.string.status_connected_successfully)
                }
            }

            override fun onDisconnected() {
                runOnUiThread {
                    Log.d(appTag, "Bluetooth disconnected (connectBluetooth callback)")
                    isGlassesConnected = false
                    statusTextView.setText(R.string.status_disconnected_simple)
                }
            }

            override fun onFailed(errorCode: ValueUtil.CxrBluetoothErrorCode?) {
                runOnUiThread {
                    Log.e(appTag, "Bluetooth connection failed: $errorCode")
                    statusTextView.text = getString(R.string.status_bluetooth_connection_failed, errorCode.toString())
                }
            }
        })
    }


    override fun onDestroy() {
        super.onDestroy()
        // Only disconnect Bluetooth if the activity is finishing (not just being recreated)
        if (isFinishing) {
            bluetoothHelper.release()
            CxrApi.getInstance().deinitBluetooth()
            Log.d(appTag, "Activity finishing - Bluetooth disconnected")

            // Clean up temporary audio files
            cleanupTempAudioFiles()
        } else {
            Log.d(appTag, "Activity being recreated - keeping Bluetooth connection")
        }
    }

    /**
     * Clean up temporary audio files in audio_recordings and tts_audio directories
     */
    private fun cleanupTempAudioFiles() {
        try {
            // Clean up audio recordings directory
            val audioRecordingsDir = getExternalFilesDir("audio_recordings")
            if (audioRecordingsDir != null && audioRecordingsDir.exists()) {
                val deletedCount = deleteDirectoryContents(audioRecordingsDir)
                Log.d(appTag, "Cleaned up audio_recordings: deleted $deletedCount files")
            }

            // Clean up TTS audio directory
            val ttsAudioDir = getExternalFilesDir("tts_audio")
            if (ttsAudioDir != null && ttsAudioDir.exists()) {
                val deletedCount = deleteDirectoryContents(ttsAudioDir)
                Log.d(appTag, "Cleaned up tts_audio: deleted $deletedCount files")
            }
        } catch (e: Exception) {
            Log.e(appTag, "Error cleaning up temporary audio files: ${e.message}", e)
        }
    }

    /**
     * Delete all files in a directory (but keep the directory itself)
     * @param directory The directory to clean
     * @return Number of files deleted
     */
    private fun deleteDirectoryContents(directory: java.io.File): Int {
        var deletedCount = 0
        try {
            directory.listFiles()?.forEach { file ->
                if (file.isFile) {
                    if (file.delete()) {
                        deletedCount++
                        Log.d(appTag, "Deleted file: ${file.name}")
                    } else {
                        Log.w(appTag, "Failed to delete file: ${file.name}")
                    }
                } else if (file.isDirectory) {
                    // Recursively delete subdirectories
                    deletedCount += deleteDirectoryContents(file)
                    if (file.delete()) {
                        Log.d(appTag, "Deleted directory: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(appTag, "Error deleting directory contents: ${e.message}", e)
        }
        return deletedCount
    }
}
