package com.example.project_whatsappblue

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.room.Room
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import androidx.core.graphics.scale

class ChatActivity : AppCompatActivity() {

    //Declaración de variables
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var chatService: BluetoothChatService

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private lateinit var inputMsg: EditText
    private lateinit var btnSend: Button
    private lateinit var btnImage: Button

    private lateinit var remoteNameText: TextView
    private lateinit var remoteImageView: ImageView
    private var isChatActive: Boolean = false

    private lateinit var messageDao: MessageDao
    private var deviceAddress: String? = null

    //Env+io de imágenes
    companion object {
        const val REQUEST_IMAGE_PICK = 101
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recyclerView = findViewById(R.id.recyclerView)
        inputMsg = findViewById(R.id.inputMsg)
        btnSend = findViewById(R.id.btnSend)
        btnImage = findViewById(R.id.btnImage)

        remoteNameText = findViewById(R.id.remoteName)
        remoteImageView = findViewById(R.id.remoteImage)

        adapter = MessageAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        messageDao = AppDatabase.getDatabase(this).messageDao()

        chatService = BluetoothChatService(bluetoothAdapter, this)
        deviceAddress = intent.getStringExtra("deviceAddress")

        setupChatServiceListeners()
        chatService.startServer()

        if (!deviceAddress.isNullOrBlank()) {

            loadMessages()
            loadRemoteProfile(deviceAddress!!)

            Handler(Looper.getMainLooper()).postDelayed({
                //Solo conectar si el usuasrio está activo y no se ha conectado
                if (isChatActive && !chatService.isConnected) {
                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device != null) {
                        chatService.connectTo(device)
                    } else {
                        Toast.makeText(this, "Device not found", Toast.LENGTH_SHORT).show()
                    }
                }
            }, 1500) // 1.5 segundos de retraso antes de intentar conectar

        } else {
            Toast.makeText(this, "Waiting for connection...", Toast.LENGTH_SHORT).show()
        }

        // Texto
        btnSend.setOnClickListener {
            val text = inputMsg.text.toString()
            if (text.isNotBlank()) {
                if (chatService.isConnected) {
                    chatService.send(text)
                }
                addMessage(
                    Message(text = text, fromMe = true),
                    isNewMessage = true
                ) //Añadir a la lista de mensajes | BD
                inputMsg.text.clear()
            }
        }

        // Imágenes
        btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }
    }

    //Para reanudar conexión y así
    override fun onResume() {
        super.onResume()
        isChatActive = true
    }

    override fun onPause() {
        super.onPause()
        isChatActive = false
    }

    //Maneja el recibo de mensajes y la conexión
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupChatServiceListeners() {
        chatService.onMessageReceived = { textContent ->
            runOnUiThread {
                addMessage(Message(text = textContent, fromMe = false), isNewMessage = true)
            }
        }
        chatService.onImageReceived = { imageBase64Content ->
            runOnUiThread {
                addMessage(
                    Message(imageBase64 = imageBase64Content, fromMe = false),
                    isNewMessage = true
                )
            }
        }
        chatService.onProfileReceived = {
            deviceAddress?.let {
                runOnUiThread { loadRemoteProfile(it) }
            }
        }
        chatService.onConnectionLost = {
            runOnUiThread {
                if (isChatActive) {
                    Toast.makeText(this, "Connection lost", Toast.LENGTH_SHORT).show()
                    chatService.startServer()
                }

            }
        }

        chatService.onConnected = { socket ->
            runOnUiThread @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) {
                val device = socket.remoteDevice
                Toast.makeText(this, "Connected!", Toast.LENGTH_SHORT).show()

                // Modo Servidor, deviceAddress será null
                if (deviceAddress == null) {
                    deviceAddress = device.address

                    // Cargar historial y perfil del usuario que se conectó
                    loadMessages()
                    loadRemoteProfile(deviceAddress!!)
                }

                // Modo cliente, actualiza perfil
                remoteNameText.text = device.name

                val localProfile = loadLocalProfile()
                if (localProfile != null) {
                    chatService.onSecureConnection = {
                        chatService.sendProfile(localProfile.name, localProfile.imageBase64)
                    }
                }
                chatService.onSecureConnection = {
                    sendUnsentMessages()
                }
            }
        }
    }

    //Reescala imágen enviada pq si no la app se rompe
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                try {

                    val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    val targetWidth = 300
                    val ratio = targetWidth.toFloat() / originalBitmap.width.toFloat()
                    val scaledBitmap =
                        originalBitmap.scale(targetWidth, (originalBitmap.height * ratio).toInt())

                    val imageBase64 = UserProfile.fromBitmap("ChatImage", scaledBitmap).imageBase64

                    if (chatService.isConnected) {
                        chatService.sendImage(imageBase64)
                    }
                    addMessage(
                        Message(imageBase64 = imageBase64, fromMe = true),
                        isNewMessage = true
                    )

                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    //Carga el perfil locla ya guardado de un usuario
    private fun loadLocalProfile(): UserProfile? {
        val prefs = getSharedPreferences(ProfileActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString("name", null)
        val savedImage = prefs.getString("imageBase64", null)

        return if (!savedName.isNullOrEmpty() && !savedImage.isNullOrEmpty()) {
            UserProfile(savedName, savedImage)
        } else {
            null
        }
    }

    //Obtiene el perfil de un usuario al estabelcer conexión
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun loadRemoteProfile(deviceAddress: String) {
        val prefsName = "remote_profile_$deviceAddress"
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        var remoteName = prefs.getString("name", null)
        val remotePhoto = prefs.getString("image", null)

        if (remoteName.isNullOrEmpty()) {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
            remoteName = device?.name ?: "Remoto"
        }

        remoteNameText.text = remoteName

        remotePhoto?.let {
            val bytes = Base64.decode(it, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            remoteImageView.setImageBitmap(bmp)
        }
    }

    //Añade mensajes a la bd
    private fun addMessage(message: Message, isNewMessage: Boolean) {
        val currentAddress = deviceAddress
        if (currentAddress == null) {
            return // No guardar en bd si no hay dirección
        }

        val messageIsSent = if (message.fromMe) {
            chatService.isConnected
        } else {
            true
        }

        val messageEntity = MessageEntity(
            deviceAddress = currentAddress,
            textContent = message.text,
            imageBase64 = message.imageBase64,
            fromMe = message.fromMe,
            isSent = messageIsSent
        )

        if (isNewMessage) {
            Thread {
                messageDao.insertMessage(messageEntity)
            }.start()
        }

        runOnUiThread {
            messages.add(message)
            adapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    //Carga los mensajes guardados
    private fun loadMessages() {
        val currentAddress = deviceAddress
        if (currentAddress.isNullOrBlank()) {
            return // No cargar mensajes si no hay dirección
        }

        Thread {
            val messageEntities = messageDao.getMessagesByDevice(currentAddress)
            val loadedMessages = messageEntities.map { entity ->
                Message(
                    text = entity.textContent,
                    imageBase64 = entity.imageBase64,
                    fromMe = entity.fromMe
                )
            }
            runOnUiThread {
                messages.clear()
                messages.addAll(loadedMessages)
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) {
                    recyclerView.scrollToPosition(messages.size - 1)
                }
            }
        }.start()
    }

    //TODO: Envía los mensajes pendientes, no se pq no funciona
    private fun sendUnsentMessages() {
        val currentAddress = deviceAddress
        if (currentAddress.isNullOrBlank()) return

        Thread {
            val unsentMessages = messageDao.getUnsentMessages(currentAddress)

            if (unsentMessages.isNotEmpty()) {
                runOnUiThread {
                    Toast.makeText(this, "Sending unsent messages...", Toast.LENGTH_SHORT).show()
                }
            }

            for (msg in unsentMessages) {
                // Enviar el mensaje pendiente
                if (msg.textContent != null) {
                    chatService.send(msg.textContent)
                } else if (msg.imageBase64 != null) {
                    chatService.sendImage(msg.imageBase64)
                }

                // Marcar como enviado en la DB
                val updatedMsg = msg.copy(isSent = true)
                messageDao.updateMessage(updatedMsg)

                // Pausa para no saturar el buffer por si las dudas segun por esto no se envían
                Thread.sleep(200)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        chatService.stop()
        isChatActive = false
    }
}

