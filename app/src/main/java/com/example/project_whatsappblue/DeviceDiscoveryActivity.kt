package com.example.project_whatsappblue

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

//TODO: Realmente no se utiliza, el plan era emparejar los dispositivos desde aquí
//TODO: pero mejor que el botón abra la configuración bluetooth del teléfono para emparejar desde ajustes
class DeviceDiscoveryActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var listView: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private val devices = mutableListOf<BluetoothDevice>()
    private val deviceNames = mutableListOf<String>()

    private val enableBtLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        runOnUiThread {
                            if (!devices.contains(it)) {
                                devices.add(it)
                                deviceNames.add("${it.name ?: "Unknown Device"}\n${it.address}")
                                adapter.notifyDataSetChanged()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    //Toast.makeText(this@DeviceDiscoveryActivity, "Búsqueda finalizada", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.device_discovery)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        listView = findViewById(R.id.deviceList)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, deviceNames)
        listView.adapter = adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no soportado", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Solicitar permisos en tiempo de ejecución
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            ), 1)
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBtLauncher.launch(enableBtIntent)
        }

        startDiscovery()

        listView.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter.cancelDiscovery()
            val selectedDevice = devices[position]

            val resultIntent = Intent()
            resultIntent.putExtra("deviceAddress", selectedDevice.address)
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startDiscovery() {
        if (bluetoothAdapter.isDiscovering)
                bluetoothAdapter.cancelDiscovery()

        deviceNames.clear()
        devices.clear()
        adapter.notifyDataSetChanged()

        Toast.makeText(this, "Searching devices", Toast.LENGTH_SHORT).show()
        bluetoothAdapter.startDiscovery()

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(receiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }
}