package com.example.project_whatsappblue

import androidx.room.Entity
import androidx.room.PrimaryKey

//Para la BD, también guarda las direcciones de dispositivos para cargar perfiles
@Entity(tableName = "messages")
data class MessageEntity (
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val deviceAddress: String,
    val textContent: String?,
    val imageBase64: String?,
    val fromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val isSent: Boolean = true
)

