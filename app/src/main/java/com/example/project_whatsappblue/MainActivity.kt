package com.example.project_whatsappblue

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothClass
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat


class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var btnEditProfile: Button
    private lateinit var btnStartServer: Button
    private lateinit var listView: ListView
    private lateinit var discoverButton: Button
    private val pairedDevices = mutableListOf<BluetoothDevice>()

    private lateinit var ivProfileMain: ImageView
    private lateinit var tvProfileName: TextView
    private var userProfile: UserProfile? = null

    //Permisos graciosos
    @RequiresApi(Build.VERSION_CODES.S)
    private val permissions = arrayOf (
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_MEDIA_IMAGES,
    )

    companion object {
        const val REQUEST_EDIT_PROFILE = 100
        const val PREFS_NAME = "UserProfilePrefs"
        const val REQUEST_DEVICE_DISCOVERY = 1001
    }

    private val requestEnableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(this,"Bluetooth required",Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Invoca las vistas
        btnStartServer = findViewById(R.id.btnStartServer)  //TODO: Quitar este
        listView = findViewById(R.id.pairedList)
        discoverButton = findViewById(R.id.btnDiscover)     //TODO: Quitar este ?
        btnEditProfile = findViewById(R.id.btnEditProfile)

        ivProfileMain = findViewById(R.id.ivProfileMain)
        tvProfileName = findViewById(R.id.tvProfileName)

        val prefs = getSharedPreferences("UserProfile", MODE_PRIVATE)
        val name = prefs.getString("name", "User")

        loadUserProfile()
        loadPairedDevices()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_SHORT).show()
        }

        checkAndRequestPermissions()

        //TODO: Quitar este
        btnStartServer.setOnClickListener {
            if (ensureBluetoothEnabled()) {
                val intent = Intent(this, ChatActivity::class.java)
                startActivity(intent)
            }
        }

        //TODO: Quitar este ?
        discoverButton.setOnClickListener {
            val intent = Intent(this, DeviceDiscoveryActivity::class.java)
            startActivityForResult(intent, 1001)
        }

        //Recarga dispositivos emparejados, tambien sus fotos y nombres si se han conectado antes
        findViewById<Button>(R.id.btnRefreshPaired).setOnClickListener {
            loadPairedDevices()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val device = pairedDevices[position]
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("deviceAddress", device.address)
            startActivity(intent)
        }
        loadPairedDevices()

        //Editar perfil
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivityForResult(intent, REQUEST_EDIT_PROFILE)

        }
    }

    //Carga los nombres y fotos de perfil
    private fun loadUserProfile() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val name = prefs.getString("name", null)
        val imageBase64 = prefs.getString("imageBase64", null)

        if (name != null && imageBase64 != null) {
            userProfile = UserProfile(name, imageBase64)
            tvProfileName.text = name
            ivProfileMain.setImageBitmap(UserProfile.decodeImage(imageBase64))
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == Activity.RESULT_OK) {
            val name = data?.getStringExtra("name") ?: return
            val imageBase64 = data.getStringExtra("imageBase64") ?: return
            userProfile = UserProfile(name, imageBase64)
            tvProfileName.text = name
            ivProfileMain.setImageBitmap(UserProfile.decodeImage(imageBase64))
        }

        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val address = data?.getStringExtra("deviceAddress")
            if (address != null) {
                // Uuario seleccionó un dispositivo descubierto
                val device = bluetoothAdapter?.getRemoteDevice(address)
                if (device != null) {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("role", "client")
                    intent.putExtra("deviceAddress", device.address)
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "Select a valid device", Toast.LENGTH_SHORT).show()
                }
            }
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
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 101)
            }
        }
    }

    //Carga los dispositivos ya emparejados por bluetooth, trata que solo se muestren solo celulares
    //pero a mi me sigue saliendo CarromESP32 xd
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun loadPairedDevices() {
        pairedDevices.clear()
        val adapter = bluetoothAdapter

        if (adapter != null) {
            val bonded = adapter.bondedDevices

            if (bonded != null && bonded.isNotEmpty()) {
                val filteredDevices = mutableListOf<BluetoothDevice>()

                for (device in bonded) {
                    val deviceClass = device.bluetoothClass?.majorDeviceClass

                    if (deviceClass != BluetoothClass.Device.Major.PHONE &&
                        deviceClass != BluetoothClass.Device.Major.COMPUTER &&
                        deviceClass != BluetoothClass.Device.Major.NETWORKING &&
                        deviceClass != BluetoothClass.Device.Major.UNCATEGORIZED) {
                        continue
                    }
                   filteredDevices.add(device)
                }

                if (filteredDevices.isNotEmpty()) {
                    pairedDevices.addAll(filteredDevices)
                    val customAdapter = PairedDeviceAdapter(this, pairedDevices)
                    listView.adapter = customAdapter
                } else {
                    listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No paired devices found"))
                }
            } else {
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No paired devices found"))
            }
        }
    }
}


