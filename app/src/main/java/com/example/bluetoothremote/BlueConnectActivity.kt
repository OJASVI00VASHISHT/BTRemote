package com.example.bluetoothremote

import android.Manifest
import android.bluetooth.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.IOException
import java.io.OutputStream
import java.util.*

// Singleton to hold socket and stream (consider replacing with ViewModel/Service for production)

class BluetoothConnectActivity : AppCompatActivity() {

    private lateinit var deviceSpinner: Spinner
    private lateinit var connectBtn: Button
    private val bluetoothManager by lazy { getSystemService(BluetoothManager::class.java) }
    private val bluetoothAdapter: BluetoothAdapter? by lazy { bluetoothManager?.adapter }
    private var deviceList: List<BluetoothDevice> = emptyList()

    companion object {
        private const val REQUEST_ENABLE_BLUETOOTH = 102
        private const val REQUEST_PERMISSIONS = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_connect)

        deviceSpinner = findViewById(R.id.deviceSpinner)
        connectBtn = findViewById(R.id.connectBtn)

        // Check if Bluetooth is supported
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter!!.isEnabled) {
            promptEnableBluetooth()
            return
        }

        // Check and request permissions
        if (!hasAllPermissions()) {
            requestBluetoothPermissions()
        } else {
            loadPairedDevicesIfPermitted()
        }

        // Set up connect button listener
        connectBtn.setOnClickListener {
            val index = deviceSpinner.selectedItemPosition
            if (index in deviceList.indices) {
                connectToDevice(deviceList[index])
            } else {
                Toast.makeText(this, "Please select a valid device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestBluetoothPermissions() {
        // Show rationale for location permission
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            Toast.makeText(
                this,
                "Location permission is needed for Bluetooth scanning",
                Toast.LENGTH_LONG
            ).show()
        }

        val permissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPairedDevicesIfPermitted()
            } else {
                Toast.makeText(
                    this,
                    "All permissions are required. Please grant them in settings.",
                    Toast.LENGTH_LONG
                ).show()
                // Allow retry instead of finishing
                requestBluetoothPermissions()
            }
        }
    }

    private fun promptEnableBluetooth() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH)
        } else {
            Toast.makeText(this, "Bluetooth permissions required", Toast.LENGTH_SHORT).show()
            requestBluetoothPermissions()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BLUETOOTH) {
            if (resultCode == RESULT_OK && bluetoothAdapter!!.isEnabled) {
                if (hasAllPermissions()) {
                    loadPairedDevicesIfPermitted()
                } else {
                    requestBluetoothPermissions()
                }
            } else {
                Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun loadPairedDevicesIfPermitted() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            lifecycleScope.launch { loadPairedDevices() }
        } else {
            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadPairedDevices() {
        try {
            val pairedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
            deviceList = pairedDevices.toList()

            Log.d("BluetoothDebug", "Found ${deviceList.size} devices")
            deviceList.forEach {
                Log.d("BluetoothDebug", "Device: ${it.name} - ${it.address}")
            }

            val deviceNames = if (deviceList.isEmpty())
                listOf("No Paired Devices")
            else
                deviceList.map { "${it.name ?: "Unknown Device"} (${it.address})" }

            withContext(Dispatchers.Main) {
                val adapter = ArrayAdapter(
                    this@BluetoothConnectActivity,
                    android.R.layout.simple_spinner_item,
                    deviceNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                deviceSpinner.adapter = adapter
                deviceSpinner.isEnabled = deviceList.isNotEmpty()
                connectBtn.isEnabled = deviceList.isNotEmpty()

                if (deviceList.isEmpty()) {
                    Toast.makeText(
                        this@BluetoothConnectActivity,
                        "No paired devices found. Pair with HC-05 in Bluetooth settings.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: SecurityException) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@BluetoothConnectActivity, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            var socket: BluetoothSocket? = null
            try {
                socket = device.createRfcommSocketToServiceRecord(
                    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                )
                socket.connect()
                val stream = socket.outputStream
                withContext(Dispatchers.Main) {
                    GlobalSocketHolder.socket = socket
                    GlobalSocketHolder.outputStream = stream
                    startActivity(Intent(this@BluetoothConnectActivity, BluetoothRemoteActivity::class.java))
                }
            } catch (e: SecurityException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothConnectActivity, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
                }
                socket?.close()
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothConnectActivity, "Connection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                socket?.close()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothConnectActivity, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                socket?.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up socket and stream to prevent resource leaks
        try {
            GlobalSocketHolder.socket?.close()
            GlobalSocketHolder.outputStream?.close()
            GlobalSocketHolder.socket = null
            GlobalSocketHolder.outputStream = null
        } catch (e: IOException) {
            Log.e("BluetoothDebug", "Error closing socket/stream: ${e.message}")
        }
    }
}