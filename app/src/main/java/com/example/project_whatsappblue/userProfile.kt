package com.example.project_whatsappblue

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

//Funciones para las imágenes y que no explote cuando cargas una
//Explota si no les bajas la resolución xd, ya entendí pq wasa baja la calidad a todo
class UserProfile (val name: String, val imageBase64: String) {
    companion object {
        fun fromBitmap (name: String, bitmap: Bitmap): UserProfile{
            val biteArrayOutputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, biteArrayOutputStream)
            val encoded = Base64.encodeToString(biteArrayOutputStream.toByteArray(), Base64.DEFAULT)
            return UserProfile(name, encoded)

        }

        fun decodeImage(base64: String): Bitmap? {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
    }
}