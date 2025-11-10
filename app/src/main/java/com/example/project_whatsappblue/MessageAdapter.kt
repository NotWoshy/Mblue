package com.example.project_whatsappblue

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

//Define el fromato del chat (Remitente y destinatario)
class MessageAdapter (private val messages: List<Message>): RecyclerView.Adapter<MessageAdapter.MessageViewHolder> () {

    class MessageViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val llLeftContainer: LinearLayout? = view.findViewById(R.id.llLeftContainer)
        val llRightContainer: LinearLayout? = view.findViewById(R.id.llRightContainer)

        val tvLeft: TextView? = view.findViewById(R.id.tvLeft)
        val tvRight: TextView? = view.findViewById(R.id.tvRight)

        val ivLeft: ImageView? = view.findViewById(R.id.ivLeft)
        val ivRight: ImageView? = view.findViewById(R.id.ivRight)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val m = messages[position]

        holder.llLeftContainer?.visibility = View.GONE
        holder.llRightContainer?.visibility = View.GONE

        holder.tvLeft?.visibility = View.GONE
        holder.tvRight?.visibility = View.GONE
        holder.ivLeft?.visibility = View.GONE
        holder.ivRight?.visibility = View.GONE

        //Si usuario envía, lado derecho, si no, lado izquierdo
        if (m.fromMe) {
            holder.llRightContainer?.visibility = View.VISIBLE
            if (m.isText) {
                holder.tvRight?.text = m.text
                holder.tvRight?.visibility = View.VISIBLE
            } else if (m.isImage) {
                val bmp = UserProfile.decodeImage(m.imageBase64!!)
                holder.ivRight?.setImageBitmap(bmp)
                holder.ivRight?.visibility = View.VISIBLE
            }
        } else {
            holder.llLeftContainer?.visibility = View.VISIBLE
            if (m.isText) {
                holder.tvLeft?.text = m.text
                holder.tvLeft?.visibility = View.VISIBLE
            } else if (m.isImage) {
                val bmp = UserProfile.decodeImage(m.imageBase64!!)
                holder.ivLeft?.setImageBitmap(bmp)
                holder.ivLeft?.visibility = View.VISIBLE
            }
        }
    }
}