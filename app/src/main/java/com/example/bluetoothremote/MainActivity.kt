// MainActivity.kt
package com.example.bluetoothremote

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val connectButton = findViewById<Button>(R.id.launchConnectBtn)
        val remoteButton = findViewById<Button>(R.id.launchRemoteBtn)

        connectButton.setOnClickListener {
            val intent = Intent(this, BluetoothConnectActivity::class.java)
            startActivity(intent)
        }

        remoteButton.setOnClickListener {
            val intent = Intent(this, BluetoothRemoteActivity::class.java)
            startActivity(intent)
        }
    }
}