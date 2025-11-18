package com.example.project_whatsappblue

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.widget.Toast
import androidx.annotation.RequiresPermission
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

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
    var onSecureConnection: (() -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onImageReceived: ((String) -> Unit)? = null
    var onProfileReceived: (() -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null
    var isConnected: Boolean = false
        private set

    // variables para la seguridad
    private var myPrivateKey: PrivateKey? = null
    private var sharedSecretKey: SecretKeySpec? = null
    var isSecure: Boolean = false
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

        isSecure = false
        initHandshake()

        onConnected?.invoke(socket)

        isConnected = true
        onConnected?.invoke(socket)
    }

    private fun initHandshake() { // 🤝
        try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val myKeyPair = kpg.generateKeyPair()
            myPrivateKey = myKeyPair.private

            val myPublicKeyBytes = myKeyPair.public.encoded
            val myPublicKeyBase64 = Base64.getEncoder().encodeToString(myPublicKeyBytes) // MUX

            // formato JSON si
            val handshakeJson = JSONObject().apply {
                put("type", "handshake")
                put("key", myPublicKeyBase64)
            }

            connectedThread?.write(handshakeJson.toString().toByteArray())

        } catch (e /*TooMuchOfAPussyException*/ :Exception) {
            e.printStackTrace()
        }
    }

    private fun endHandshake(remotePublicKeyBase64: String) { // 🤝
        try {
            val remoteKeyBytes = Base64.getDecoder().decode(remotePublicKeyBase64)
            val keyFactory = KeyFactory.getInstance("EC")
            val keySpec = X509EncodedKeySpec(remoteKeyBytes)
            val remotePublicKey : PublicKey = keyFactory.generatePublic(keySpec)

            val keyAgreement = KeyAgreement.getInstance("ECDH")
            keyAgreement.init(myPrivateKey)
            keyAgreement.doPhase(remotePublicKey, true)
            val sharedSecret = keyAgreement.generateSecret() // diffie fiffie

            val salt = ByteArray(32) { 0x00 }
            val info = "chat session key".toByteArray()
            val keyLen = 32

            val derivedKey = hkdfDerivationKey(sharedSecret, salt, info, keyLen)
            sharedSecretKey = SecretKeySpec(derivedKey, "AES")

            isSecure = true
            onSecureConnection?.invoke()

            // TODO: Toast de que la conexion es segura

        } catch (e : Exception) {
            e.printStackTrace()
        }
    }

    fun hkdfDerivationKey(secret: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int) : ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        val params = HKDFParameters(secret, salt, info)
        hkdf.init(params)
        val derivedKey = ByteArray(length)
        hkdf.generateBytes(derivedKey, 0, length)
        return derivedKey
    }

    fun cipherMessage(message: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")

        val iv = ByteArray(12)
        val secureRandom = SecureRandom()
        secureRandom.nextBytes(iv)

        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, sharedSecretKey, gcmSpec)

        val cipherText = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        val output = iv + cipherText

        return Base64.getEncoder().encodeToString(output)
    }

    fun decipherMessage(encoded: String) : String {
        try {
            val data = Base64.getDecoder().decode(encoded)
            val iv = data.copyOfRange(0, 12)
            val cipherText = data.copyOfRange(12, data.size)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, sharedSecretKey, gcmSpec)
            val plainText = cipher.doFinal(cipherText)

            return String(plainText)
        } catch (e : Exception) {
            e.printStackTrace()
        }
        return ""
    }

    //Solo texto
    fun send(message: String) {
        // TODO: Cipher here before sending
        if (!isSecure || sharedSecretKey == null) {
            // Puedes almacenar el mensaje para enviar después o simplemente no enviarlo
            return
        }
        var cipherText = message
        cipherText = cipherMessage(message)
        val messageJson = JSONObject().apply {
            put("type", "text")
            put("content", cipherText)
        }
        connectedThread?.write(messageJson.toString().toByteArray())
    }

    //Solo imágen
    fun sendImage(imageBase64: String) {
        if (!isSecure || sharedSecretKey == null) return
        // TODO: Cipher here before sending
        var cipherText = imageBase64
        cipherText = cipherMessage(imageBase64)
        val imageJson = JSONObject().apply {
            put("type", "image")
            put("content", cipherText)
        }
        connectedThread?.write(imageJson.toString().toByteArray())
    }

    //Envía el perfil a la otra persona en formato json
    fun sendProfile(name: String, imageBase64: String?) {
        if (!isSecure || sharedSecretKey == null) return
        // TODO: Cipher here before sending
        var cipherName = name
        var cipherImage = imageBase64

        cipherName = cipherMessage(name)
        if (imageBase64 != null) {
            cipherImage = cipherMessage(imageBase64)
        }

        val profileJson = JSONObject().apply {
            put("type", "profile")
            put("name", cipherName)
            put("image", cipherImage ?: "")
        }
        connectedThread?.write(profileJson.toString().toByteArray())
    }

    //Detiene la conexión
    fun stop() {
        acceptThread?.cancel(); acceptThread = null
        connectThread?.cancel(); connectThread = null
        connectedThread?.cancel(); connectedThread = null
        isSecure = false
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

                                if (!isSecure) {
                                    if (!type.equals("handshake")){ // ignorar envio si no se ha hecho handshake por inseguridad
                                        continue
                                    }
                                    val remoteKey = json.getString("key")
                                    endHandshake(remoteKey)
                                }

                                when (type) {
                                    "profile" -> {
                                        val remoteName = decipherMessage(json.getString("name"))
                                        var remoteImage = ""
                                        if (json.getString("image") != null) {
                                            remoteImage = decipherMessage(json.getString("image"))
                                        }

                                        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                                        prefs.edit().apply {
                                            putString("name", remoteName)
                                            putString("image", remoteImage)
                                            apply()
                                        }
                                        onProfileReceived?.invoke()
                                    }
                                    "text" -> {
                                        val content = decipherMessage(json.getString("content"))
                                        onMessageReceived?.invoke(content)
                                    }
                                    "image" -> {
                                        val content = decipherMessage(json.getString("content"))
                                        onImageReceived?.invoke(content)
                                    }
                                }
                            } catch (e: Exception) {
                                isSecure = false
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
