package com.example.project_whatsappblue

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.graphics.scale

//Edición del pefil, y carga de este
class ProfileActivity : AppCompatActivity() {

    private lateinit var ivProfile: ImageView
    private lateinit var etName: EditText
    private lateinit var btnSave: Button
    private var selectedBitmap: Bitmap? = null

    companion object {
        const val REQUEST_IMAGE_PICK = 101
        const val PREFS_NAME = "UserProfilePrefs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        ivProfile = findViewById(R.id.ivProfile)
        etName = findViewById(R.id.etName)
        btnSave = findViewById(R.id.btnSave)

        // Cargar datos guardados si existen
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedName = prefs.getString("name", "")
        val savedImage = prefs.getString("imageBase64", null)

        if (!savedName.isNullOrEmpty()) etName.setText(savedName)
        savedImage?.let {
            val bitmap = UserProfile.decodeImage(it)
            ivProfile.setImageBitmap(bitmap)
            selectedBitmap = bitmap
        }

        ivProfile.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            startActivityForResult(intent, REQUEST_IMAGE_PICK)
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val bitmap = selectedBitmap ?: return@setOnClickListener

            val userProfile = UserProfile.fromBitmap(name, bitmap)

            // Guardar en SharedPreferences
            prefs.edit() {
                putString("name", userProfile.name)
                putString("imageBase64", userProfile.imageBase64)
            }

            // Devolver resultado al MainActivity
            val resultIntent = Intent().apply {
                putExtra("name", userProfile.name)
                putExtra("imageBase64", userProfile.imageBase64)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == Activity.RESULT_OK) {
            val imageUri: Uri? = data?.data
            imageUri?.let {
                try {
                    val contentResolver = this.contentResolver
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, it)
                    val scaledBitmap =
                        bitmap.scale(200, (bitmap.height * (200.0 / bitmap.width)).toInt())

                    selectedBitmap = scaledBitmap
                    ivProfile.setImageBitmap(scaledBitmap)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }
        }
    }
}
