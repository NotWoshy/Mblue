package com.example.project_whatsappblue

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Vistas existentes
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnRefresh: LinearLayout
    private lateinit var listView: ListView

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private var userProfile: UserProfile? = null

    // Permisos Android 12+
    @RequiresApi(Build.VERSION_CODES.S)
    private val permissions = arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    companion object {
        const val REQUEST_EDIT_PROFILE = 100
        const val PREFS_NAME = "UserProfilePrefs"
    }

    private val requestEnableBt =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) {
                Toast.makeText(this, "Bluetooth es requerido", Toast.LENGTH_SHORT).show()
            } else {
                loadPairedDevices()
            }
        }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Vincular vistas
        listView = findViewById(R.id.pairedList)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnRefresh = findViewById(R.id.btnRefreshPaired)

        loadUserProfileData()
        checkAndRequestPermissions()
        ensureBluetoothEnabled()
        loadPairedDevices()

        // Botón refrescar lista
        btnRefresh.setOnClickListener {
            loadPairedDevices()
            Toast.makeText(this, "Lista actualizada", Toast.LENGTH_SHORT).show()
        }

        // Botón editar perfil
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivityForResult(intent, REQUEST_EDIT_PROFILE)
        }

        // Presionar un dispositivo → iniciar ChatActivity
        listView.setOnItemClickListener { _, _, position, _ ->
            val device = pairedDevices[position]
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("deviceAddress", device.address)
            startActivity(intent)
        }
    }

    // Cargar datos SOLO en memoria
    private fun loadUserProfileData() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString("name", null)
        val imageBase64 = prefs.getString("imageBase64", null)

        if (name != null && imageBase64 != null) {
            userProfile = UserProfile(name, imageBase64)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == Activity.RESULT_OK) {
            val name = data?.getStringExtra("name") ?: return
            val imageBase64 = data.getStringExtra("imageBase64") ?: return
            userProfile = UserProfile(name, imageBase64)
        }
    }

    private fun ensureBluetoothEnabled(): Boolean {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            requestEnableBt.launch(enableBtIntent)
            return false
        }
        return true
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = permissions.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
            }
        } else {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    101
                )
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun loadPairedDevices() {
        pairedDevices.clear()
        val adapter = bluetoothAdapter

        if (adapter != null && adapter.isEnabled) {
            val bonded = adapter.bondedDevices

            if (bonded != null && bonded.isNotEmpty()) {

                val filtered = bonded.filter { device ->
                    val cls = device.bluetoothClass?.majorDeviceClass
                    cls == BluetoothClass.Device.Major.PHONE ||
                            cls == BluetoothClass.Device.Major.COMPUTER ||
                            cls == BluetoothClass.Device.Major.NETWORKING
                }

                if (filtered.isNotEmpty()) {
                    pairedDevices.addAll(filtered)
                    listView.adapter = PairedDeviceAdapter(this, pairedDevices)
                } else {
                    listView.adapter = ArrayAdapter(
                        this, android.R.layout.simple_list_item_1,
                        listOf("No valid devices found")
                    )
                }

            } else {
                listView.adapter = ArrayAdapter(
                    this, android.R.layout.simple_list_item_1,
                    listOf("No paired devices")
                )
            }

        } else {
            listView.adapter = null
        }
    }
}
