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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    // Vistas del layout
    private lateinit var btnEditProfile: LinearLayout
    private lateinit var btnRefresh: LinearLayout
    private lateinit var listView: ListView

    private val pairedDevices = mutableListOf<BluetoothDevice>()
    private var userProfile: UserProfile? = null

    // Permisos requeridos
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

    // Callback para cuando se activa el Bluetooth
    private val requestEnableBt = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth es requerido", Toast.LENGTH_SHORT).show()
        } else {
            // SOLUCIÓN AL ERROR: Verificar permiso antes de cargar dispositivos
            if (hasBluetoothPermission()) {
                loadPairedDevices()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Vincular vistas
        listView = findViewById(R.id.pairedList)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnRefresh = findViewById(R.id.btnRefreshPaired)

        // Cargar datos en memoria
        loadUserProfileData()

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no soportado", Toast.LENGTH_SHORT).show()
        }

        // Pedir permisos y activar BT
        checkAndRequestPermissions()
        ensureBluetoothEnabled()

        // 2. Botón Actualizar
        btnRefresh.setOnClickListener {
            if (hasBluetoothPermission()) {
                loadPairedDevices()
                Toast.makeText(this, "Lista actualizada", Toast.LENGTH_SHORT).show()
            } else {
                checkAndRequestPermissions()
            }
        }

        // 3. Botón Editar Perfil
        btnEditProfile.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            startActivityForResult(intent, REQUEST_EDIT_PROFILE)
        }

        // 4. Click en lista
        listView.setOnItemClickListener { _, _, position, _ ->
            val device = pairedDevices[position]
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("deviceAddress", device.address)
            startActivity(intent)
        }

        // Carga inicial segura
        if (hasBluetoothPermission()) {
            loadPairedDevices()
        }
    }

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

    // Función auxiliar para verificar permisos rápidamente
    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        }
        return true // En versiones viejas no se requería este permiso específico en tiempo de ejecución de la misma forma
    }

    private fun ensureBluetoothEnabled(): Boolean {
        // SOLUCIÓN AL ERROR: Verificación explicita antes de tocar el adaptador
        if (!hasBluetoothPermission()) {
            return false
        }

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

    private fun loadPairedDevices() {
        // SOLUCIÓN AL ERROR: "Guard clause" (Cláusula de guardia)
        // Si no hay permiso, detenemos la función aquí mismo. El compilador se queda feliz.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        pairedDevices.clear()
        val adapter = bluetoothAdapter

        if (adapter != null && adapter.isEnabled) {
            val bonded = adapter.bondedDevices

            if (bonded != null && bonded.isNotEmpty()) {
                val filteredDevices = mutableListOf<BluetoothDevice>()

                for (device in bonded) {
                    val deviceClass = device.bluetoothClass?.majorDeviceClass

                    if (deviceClass != BluetoothClass.Device.Major.PHONE &&
                        deviceClass != BluetoothClass.Device.Major.COMPUTER &&
                        deviceClass != BluetoothClass.Device.Major.NETWORKING) {
                        continue
                    }
                    filteredDevices.add(device)
                }

                if (filteredDevices.isNotEmpty()) {
                    pairedDevices.addAll(filteredDevices)
                    val customAdapter = PairedDeviceAdapter(this, pairedDevices)
                    listView.adapter = customAdapter
                } else {
                    listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No valid devices found"))
                }
            } else {
                listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, listOf("No paired devices"))
            }
        } else {
            listView.adapter = null
        }
    }
}