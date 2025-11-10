package com.example.project_whatsappblue

//Fomato de los mensajes que se envían
//TODO: Añadir un campo color para cambiar el color de la burbuja de texto
data class Message (
    val text: String? = null,
    val imageBase64: String? = null,
    val fromMe: Boolean,

) {
    val isText: Boolean
        get() = text != null && text.isNotBlank()

    val isImage: Boolean
        get() = imageBase64 != null && imageBase64.isNotBlank()
}

