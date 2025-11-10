package com.example.project_whatsappblue

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat

class PairedDeviceAdapter (context: Context,
    private val devices: List<BluetoothDevice>) : ArrayAdapter<BluetoothDevice>(context, 0, devices) {

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun getView (position: Int, convertView: View?, parent: ViewGroup): View {
        val device = devices[position]
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_paired_device, parent, false)
        val ivProfile: ImageView = view.findViewById(R.id.ivRemoteProfile)
        val tvName: TextView = view.findViewById(R.id.tvDeviceName)

        // Cargar dispositivos
        val prefsName = "remote_profile_${device.address}"
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val savedName = prefs.getString("name", null)
        val savedImageBase64 = prefs.getString("image", null)

        val displayName = savedName ?: device.name ?: "Dispositivo Desconocido"
        tvName.text = displayName

        if (!savedImageBase64.isNullOrEmpty()) {
            val bitmap = UserProfile.decodeImage(savedImageBase64)
            if (bitmap != null) {
                ivProfile.setImageBitmap(bitmap)
            } else {
                // Imagen placeholder
                ivProfile.setImageResource(R.drawable.ic_person)
            }
        } else {
            // Imagen placehorlder
            ivProfile.setImageResource(R.drawable.ic_person)
        }
        return view
    }
}