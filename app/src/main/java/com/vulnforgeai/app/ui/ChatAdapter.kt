package com.vulnforgeai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.ChatMode

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val onSpeak: (ChatMessage) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.messageText.text = msg.text

        if (msg.type == MessageType.LOG) {
            // Log narrativo: monoespaçado
            holder.card.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.chat_bubble_log)
            holder.senderText.visibility = View.VISIBLE
            holder.senderText.text = "🕓 " + timestampLabel(msg.timestamp)
            holder.speakButton.visibility = View.GONE
        } else {
            holder.senderText.text = senderLabel(msg)
            holder.speakButton.visibility =
                if (msg.isUser) View.GONE else View.VISIBLE
            holder.speakButton.setOnClickListener { onSpeak(msg) }

            holder.card.background = ContextCompat.getDrawable(
                holder.itemView.context,
                if (msg.isUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_ai
            )
        }
    }

    override fun getItemCount() = messages.size

    private fun senderLabel(msg: ChatMessage): String {
        val modeIcon = when (msg.mode) {
            ChatMode.APRENDIZ -> "🔰"
            ChatMode.INTERMEDIARIO -> "⚡"
            ChatMode.PROFISSIONAL -> "💀"
            ChatMode.AUTO -> "🤖"
        }
        return if (msg.isUser) msg.sender else "$msg.sender ($modeIcon)"
    }

    private fun timestampLabel(ms: Long): String {
        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        return fmt.format(java.util.Date(ms))
    }

    class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: LinearLayout = itemView.findViewById(R.id.message_container)
        val card: CardView = itemView.findViewById(R.id.message_card)
        val senderText: TextView = itemView.findViewById(R.id.message_sender)
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val speakButton: TextView = itemView.findViewById(R.id.message_speak)
    }
}