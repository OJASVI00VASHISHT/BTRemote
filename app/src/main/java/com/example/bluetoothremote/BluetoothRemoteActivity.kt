// BluetoothRemoteActivity.kt
package com.example.bluetoothremote

import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.io.IOException

class BluetoothRemoteActivity : AppCompatActivity() {
    private lateinit var upBtn: Button
    private lateinit var downBtn: Button
    private lateinit var leftBtn: Button
    private lateinit var rightBtn: Button
    private lateinit var kick1Btn: Button
    private lateinit var kick2Btn: Button
    private var idleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_remote)

        upBtn = findViewById(R.id.upBtn)
        downBtn = findViewById(R.id.downBtn)
        leftBtn = findViewById(R.id.leftBtn)
        rightBtn = findViewById(R.id.rightBtn)
        kick1Btn = findViewById(R.id.kick1Btn)
        kick2Btn = findViewById(R.id.kick2Btn)

        setCommand(upBtn, 'F')
        setCommand(downBtn, 'B')
        setCommand(leftBtn, 'L')
        setCommand(rightBtn, 'R')
        setCommand(kick1Btn, 'P')
        setCommand(kick2Btn, 'C')

        startIdleSignal()
    }

    private fun setCommand(button: Button, command: Char) {
        button.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    idleJob?.cancelAndJoin()

                    val out = GlobalSocketHolder.outputStream
                    if (out != null) {
                        out.write(command.code)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@BluetoothRemoteActivity,
                                "Not connected to Bluetooth device",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        return@launch
                    }

                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@BluetoothRemoteActivity,
                            "Command failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } finally {
                    startIdleSignal()
                }
            }
        }
    }


    private fun startIdleSignal() {
        idleJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val out = GlobalSocketHolder.outputStream
                    if (out != null) {
                        out.write('S'.code)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@BluetoothRemoteActivity,
                                "Not connected to Bluetooth device",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        break
                    }
                    delay(1000)
                } catch (e: IOException) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@BluetoothRemoteActivity,
                            "Idle signal failed: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    break
                }
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        idleJob?.cancel()
        GlobalSocketHolder.outputStream?.close()
        GlobalSocketHolder.socket?.close()
    }
}