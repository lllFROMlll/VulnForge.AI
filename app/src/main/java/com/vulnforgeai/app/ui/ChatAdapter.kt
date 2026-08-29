package com.vulnforgeai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R

class ChatAdapter(private val messages: List<ChatMessage>) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.senderText.text = msg.sender
        holder.messageText.text = msg.text

        if (msg.isUser) {
            holder.card.background = holder.card.context.getDrawable(R.drawable.chat_bubble_user)
            holder.card.post { holder.card.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT }
        } else {
            holder.card.background = holder.card.context.getDrawable(R.drawable.chat_bubble_ai)
        }
    }

    override fun getItemCount() = messages.size

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.message_container)
        val card: CardView = itemView.findViewById(R.id.message_card)
        val senderText: TextView = itemView.findViewById(R.id.message_sender)
        val messageText: TextView = itemView.findViewById(R.id.message_text)
    }
}