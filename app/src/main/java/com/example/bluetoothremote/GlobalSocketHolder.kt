package com.example.bluetoothremote

import android.bluetooth.BluetoothSocket
import java.io.OutputStream

object GlobalSocketHolder {
    var socket: BluetoothSocket? = null
    var outputStream: OutputStream? = null
}
