package com.example.project_whatsappblue

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Base64
import android.widget.*
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.*
import androidx.core.graphics.scale

class ChatActivity : AppCompatActivity() {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private lateinit var chatService: BluetoothChatService

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()

    private lateinit var inputMsg: EditText
    private lateinit var btnSend: Button
    private lateinit var btnImage: Button

    // Vistas del Encabezado (Header)
    private lateinit var remoteNameText: TextView
    private lateinit var remoteImageView: ImageView

    private var isChatActive: Boolean = false
    private lateinit var messageDao: MessageDao
    private var deviceAddress: String? = null

    companion object {
        const val REQUEST_IMAGE_PICK = 101
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // 1. Vincular las vistas del XML que me pasaste
        remoteNameText = findViewById(R.id.remoteName)
        remoteImageView = findViewById(R.id.remoteImage)

        recyclerView = findViewById(R.id.recyclerView)
        inputMsg = findViewById(R.id.inputMsg)
        btnSend = findViewById(R.id.btnSend)
        btnImage = findViewById(R.id.btnImage)

        adapter = MessageAdapter(messages)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        messageDao = AppDatabase.getDatabase(this).messageDao()

        // 2. RECIBIR DATOS: Atrapamos lo que mandó el MainActivity
        deviceAddress = intent.getStringExtra("deviceAddress")
        val deviceName = intent.getStringExtra("deviceName") ?: "Conectando..."

        // 3. ACTUALIZAR INTERFAZ INMEDIATAMENTE (Nombre)
        // Esto cambia "Remote User" por "Galaxy de Juan" al instante
        remoteNameText.text = deviceName

        // Inicializar servicio
        chatService = BluetoothChatService(bluetoothAdapter, this)
        setupChatServiceListeners()
        chatService.startServer()

        if (!deviceAddress.isNullOrBlank()) {
            loadMessages()

            // 4. INTENTAR CARGAR FOTO GUARDADA
            // Si ya has chateado antes con él, buscamos si guardamos su foto
            loadRemoteProfile(deviceAddress!!, deviceName)

            Handler(Looper.getMainLooper()).postDelayed({
                if (isChatActive && !chatService.isConnected) {
                    val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                    if (device != null) {
                        chatService.connectTo(device)
                    }
                }
            }, 1500)
        } else {
            // Si entramos en modo servidor (esperando conexión), dejamos el texto por defecto o ponemos "Esperando..."
            remoteNameText.text = "Esperando conexión..."
        }

        btnSend.setOnClickListener {
            val text = inputMsg.text.toString()
            if (text.isNotBlank()) {
                if (chatService.isConnected) {
                    chatService.send(text)
                }
                addMessage(Message(text = text, fromMe = true), isNewMessage = true)
                inputMsg.text.clear()
            }
        }

        btnImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }
    }

    override fun onResume() {
        super.onResume()
        isChatActive = true
    }

    override fun onPause() {
        super.onPause()
        isChatActive = false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupChatServiceListeners() {
        // ... (Recepción de mensajes e imágenes igual que antes) ...
        chatService.onMessageReceived = { textContent ->
            runOnUiThread { addMessage(Message(text = textContent, fromMe = false), true) }
        }
        chatService.onImageReceived = { imageBase64 ->
            runOnUiThread { addMessage(Message(imageBase64 = imageBase64, fromMe = false), true) }
        }

        // --- AQUÍ SE RECIBE LA FOTO DEL OTRO USUARIO ---
        chatService.onProfileReceived = {
            // Cuando el otro celular nos manda su foto, recargamos el perfil
            deviceAddress?.let {
                runOnUiThread {
                    loadRemoteProfile(it, remoteNameText.text.toString())
                }
            }
        }

        chatService.onConnectionLost = {
            runOnUiThread {
                if (isChatActive) {
                    Toast.makeText(this, "Conexión perdida", Toast.LENGTH_SHORT).show()
                    chatService.startServer()
                }
            }
        }

        chatService.onConnected = { socket ->
            runOnUiThread @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT) {
                val device = socket.remoteDevice
                Toast.makeText(this, "Conectado con ${device.name}", Toast.LENGTH_SHORT).show()

                if (deviceAddress == null) {
                    deviceAddress = device.address
                    loadMessages()
                    // Si nosotros somos el servidor, aquí descubrimos el nombre real
                    loadRemoteProfile(deviceAddress!!, device.name)
                }

                // Aseguramos que el nombre se actualice si estaba genérico
                if (remoteNameText.text == "Esperando conexión..." || remoteNameText.text == "Remote User") {
                    remoteNameText.text = device.name
                }

                // Intercambio de perfiles: Enviamos NUESTRA foto al otro
                val localProfile = loadLocalProfile()
                if (localProfile != null) {
                    // Esperamos un poco para asegurar estabilidad antes de mandar datos pesados
                    Handler(Looper.getMainLooper()).postDelayed({
                        chatService.sendProfile(localProfile.name, localProfile.imageBase64)
                    }, 500)
                }

                sendUnsentMessages()
            }
        }
    }

    // Carga el perfil "Remoto" (del otro usuario)
    private fun loadRemoteProfile(addr: String, fallbackName: String) {
        // 1. Buscamos en preferencias si ya guardamos la foto de este MAC Address antes
        val prefsName = "remote_profile_$addr"
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)

        val savedName = prefs.getString("name", null)
        val savedImage = prefs.getString("image", null)

        // 2. Prioridad de nombres: Guardado > Intent > Fallback
        val finalName = savedName ?: fallbackName
        remoteNameText.text = finalName

        // 3. Si hay foto guardada, la ponemos en el ImageView
        if (savedImage != null) {
            try {
                val bytes = Base64.decode(savedImage, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                remoteImageView.setImageBitmap(bmp)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //Carga el perfil locla ya guardado de un usuario
    private fun loadLocalProfile(): UserProfile? {
        val prefs = getSharedPreferences("UserProfilePrefs", Context.MODE_PRIVATE) // Ojo con el nombre del Prefs
        val savedName = prefs.getString("name", null)
        val savedImage = prefs.getString("imageBase64", null)
        return if (!savedName.isNullOrEmpty() && !savedImage.isNullOrEmpty()) {
            UserProfile(savedName, savedImage)
        } else {
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                try {
                    val originalBitmap = MediaStore.Images.Media.getBitmap(contentResolver, it)
                    val targetWidth = 300
                    val ratio = targetWidth.toFloat() / originalBitmap.width.toFloat()
                    val scaledBitmap = originalBitmap.scale(targetWidth, (originalBitmap.height * ratio).toInt())
                    val imageBase64 = UserProfile.fromBitmap("ChatImage", scaledBitmap).imageBase64

                    if (chatService.isConnected) chatService.sendImage(imageBase64)
                    addMessage(Message(imageBase64 = imageBase64, fromMe = true), true)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    private fun addMessage(message: Message, isNewMessage: Boolean) {
        val currentAddress = deviceAddress ?: return
        val messageIsSent = if (message.fromMe) chatService.isConnected else true
        val messageEntity = MessageEntity(
            deviceAddress = currentAddress,
            textContent = message.text,
            imageBase64 = message.imageBase64,
            fromMe = message.fromMe,
            isSent = messageIsSent
        )
        if (isNewMessage) Thread { messageDao.insertMessage(messageEntity) }.start()

        runOnUiThread {
            messages.add(message)
            adapter.notifyItemInserted(messages.size - 1)
            recyclerView.scrollToPosition(messages.size - 1)
        }
    }

    private fun loadMessages() {
        val currentAddress = deviceAddress ?: return
        Thread {
            val messageEntities = messageDao.getMessagesByDevice(currentAddress)
            val loadedMessages = messageEntities.map { entity ->
                Message(text = entity.textContent, imageBase64 = entity.imageBase64, fromMe = entity.fromMe)
            }
            runOnUiThread {
                messages.clear()
                messages.addAll(loadedMessages)
                adapter.notifyDataSetChanged()
                if (messages.isNotEmpty()) recyclerView.scrollToPosition(messages.size - 1)
            }
        }.start()
    }

    private fun sendUnsentMessages() {
        // Tu lógica original de reenvío
    }

    override fun onDestroy() {
        super.onDestroy()
        chatService.stop()
        isChatActive = false
    }
}