package com.example.project_whatsappblue

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

//BD de mensajes
@Dao
interface MessageDao {
    @Insert
    fun insertMessage (message: MessageEntity)

    // Recuperar mensajes usando la MAc del dispositivo
    @Query("SELECT * FROM messages WHERE deviceAddress = :address ORDER BY timestamp ASC")
    fun getMessagesByDevice (address: String) : List<MessageEntity>

    //Enviar mensajes sin conexion
    @Query("SELECT * FROM messages WHERE deviceAddress = :address AND isSent = 0 ORDER BY timestamp ASC")
    fun getUnsentMessages(address: String): List<MessageEntity>

    @Update
    fun updateMessage(message: MessageEntity)
}