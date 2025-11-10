package com.example.project_whatsappblue

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject
import java.util.*

class BluetoothChatService (private val adapter: BluetoothAdapter?, private val context: Context) {

    companion object {
        private const val  TAG = "BluetoothChatService"
        val APP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        const val SERVICE_NAME = "BTChatService"
    }

    //Definición de variables
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null
    private var connectedThread: ConnectedThread? = null

    var onConnected: ((BluetoothSocket) -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onImageReceived: ((String) -> Unit)? = null
    var onProfileReceived: (() -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null
    var isConnected: Boolean = false
        private set

    //Iniciar el proceso de escucha de conexiones
    fun startServer() {
        if (acceptThread != null) {
            acceptThread?.cancel()
            acceptThread = null
        }

        // Detener la conexión activa si se espera una nueva
        if (connectedThread != null) {
            connectedThread?.cancel()
            connectedThread = null
        }

        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    //Intenta conectarse a un dispositivo
    fun connectTo(device: BluetoothDevice) {
        if (connectThread != null) {
            connectThread?.cancel()
            connectThread = null
        }

        // Detener la conexión activa si estamos iniciando una nueva
        if (connectedThread != null) {
            connectedThread?.cancel()
            connectedThread = null
        }

        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    //Conexión establecida
    fun connected(socket: BluetoothSocket) {
        connectThread = null

        acceptThread?.cancel()
        acceptThread = null

        connectedThread = ConnectedThread(socket)
        connectedThread?.start()

        onConnected?.invoke(socket)

        isConnected = true
        onConnected?.invoke(socket)
    }

    //Solo texto
    fun send(message: String) {
        val messageJson = JSONObject().apply {
            put("type", "text")
            put("content", message)
        }
        connectedThread?.write(messageJson.toString().toByteArray())
    }

    //Solo imágen
    fun sendImage(imageBase64: String) {
        val imageJson = JSONObject().apply {
            put("type", "image")
            put("content", imageBase64)
        }
        connectedThread?.write(imageJson.toString().toByteArray())
    }

    //Envía el perfil a la otra persona en formato json
    fun sendProfile(name: String, imageBase64: String?) {
        val profileJson = JSONObject().apply {
            put("type", "profile")
            put("name", name)
            put("image", imageBase64 ?: "")
        }
        connectedThread?.write(profileJson.toString().toByteArray())
    }

    //Detiene la conexión
    fun stop() {
        acceptThread?.cancel(); acceptThread = null
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        isConnected = false
    }

    //Acepta una conexión de socket
    private inner class AcceptThread : Thread() {
        private var serverSocket: BluetoothServerSocket? = null

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun run() {
            try {
                serverSocket = adapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, APP_UUID)
                var socket: BluetoothSocket?
                var running = true

                while (running) {
                    socket = serverSocket?.accept()
                    if (socket != null) {
                        connected(socket) // conexión aceptada
                        running = false
                    }
                }
            } catch (e: IOException) {
                //Log.e(TAG, "AcceptThread IOException: ${e.message}") | DEBUG
            } finally {
                try {
                    serverSocket?.close()
                } catch (closeException: IOException) {
                    //Log.e(TAG, "Could not close the server socket: ${closeException.message}") | DEBUG
                }
            }
        }

        fun cancel() {
            try { serverSocket?.close() } catch (e: IOException) { }
        }
    }

    //Intenta una comexión a un dispositivo,
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private var socket: BluetoothSocket? = null

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun run() {
            try {
                socket = device.createRfcommSocketToServiceRecord(APP_UUID)

                adapter?.cancelDiscovery()
                socket?.connect()
                socket?.let { connected(it) }
            } catch (e: IOException) {
                try {
                    socket?.close()
                } catch (ex: IOException) { }
                onConnectionLost?.invoke()
            }
        }
        fun cancel() {
            try {
                socket?.close()
            } catch (e: IOException) { }
        }
    }

    //Envío de mensajes y del perfil remoto
    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inStream: InputStream? = socket.inputStream
        private val outStream: OutputStream? = socket.outputStream

        override fun run() {

            val buffer = ByteArray(1024 * 16) //Para mandar imágenes
            var bytes: Int
            val messageBuilder = StringBuilder()

            val deviceAddress = socket.remoteDevice.address
            val prefsName = "remote_profile_$deviceAddress"

            try {
                while (true) {
                    bytes = inStream?.read(buffer) ?: -1

                    if (bytes > 0) {
                        val fragment = String(buffer, 0, bytes)
                        messageBuilder.append(fragment)

                        if (messageBuilder.toString().trim().endsWith("}")) {
                            val receivedData = messageBuilder.toString()
                            messageBuilder.clear()

                            try {
                                val json = JSONObject(receivedData)
                                val type = json.getString("type")

                                when (type) {
                                    "profile" -> {
                                        val remoteName = json.getString("name")
                                        val remoteImage = json.getString("image")

                                        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                                        prefs.edit().apply {
                                            putString("name", remoteName)
                                            putString("image", remoteImage)
                                            apply()
                                        }
                                        onProfileReceived?.invoke()
                                    }
                                    "text" -> {
                                        val content = json.getString("content")
                                        onMessageReceived?.invoke(content)
                                    }
                                    "image" -> {
                                        val content = json.getString("content")
                                        onImageReceived?.invoke(content)
                                    }
                                }
                            } catch (e: Exception) {
                                isConnected = false
                                onConnectionLost?.invoke()
                            }
                        }
                    } else if (bytes == -1) {
                        break
                    }
                }
            } catch (e: IOException) {
                onConnectionLost?.invoke()
            }
        }

        //Maneja el envío
        fun write(bytes: ByteArray) {
            try {
                outStream?.write(bytes)
            } catch (e: IOException) {
            }
        }

        fun cancel() {
            try { socket.close() } catch (e: IOException) { }
        }
    }
}
