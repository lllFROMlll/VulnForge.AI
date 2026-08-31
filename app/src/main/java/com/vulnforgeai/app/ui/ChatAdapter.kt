package com.vulnforgeai.app.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.Risk

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val onSpeak: (ChatMessage) -> Unit,
    private val onAction: (ChatMessage, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.messageText.text = msg.text

        when (msg.type) {
            MessageType.LOG -> {
                holder.card.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.chat_bubble_log)
                holder.senderText.visibility = View.VISIBLE
                holder.senderText.text = "🕓 " + timestampLabel(msg.timestamp)
                holder.speakButton.visibility = View.GONE
                bindDevice(holder, msg, show = false)
                bindActions(holder, msg, show = false)
            }
            MessageType.DEVICE_CARD -> {
                holder.card.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.chat_bubble_ai)
                holder.senderText.text = senderLabel(msg)
                holder.speakButton.visibility = View.GONE
                bindDevice(holder, msg, show = true)
                bindActions(holder, msg, show = false)
            }
            MessageType.PROMPT_BUTTONS -> {
                holder.card.background = ContextCompat.getDrawable(holder.itemView.context, R.drawable.chat_bubble_ai)
                holder.senderText.text = senderLabel(msg)
                holder.speakButton.visibility =
                    if (msg.isUser) View.GONE else View.VISIBLE
                holder.speakButton.setOnClickListener { onSpeak(msg) }
                bindDevice(holder, msg, show = false)
                bindActions(holder, msg, show = true)
            }
            else -> {
                holder.senderText.text = senderLabel(msg)
                holder.speakButton.visibility =
                    if (msg.isUser) View.GONE else View.VISIBLE
                holder.speakButton.setOnClickListener { onSpeak(msg) }
                holder.card.background = ContextCompat.getDrawable(
                    holder.itemView.context,
                    if (msg.isUser) R.drawable.chat_bubble_user else R.drawable.chat_bubble_ai
                )
                bindDevice(holder, msg, show = false)
                bindActions(holder, msg, show = false)
            }
        }
    }

    override fun getItemCount() = messages.size

    private fun bindDevice(holder: ChatViewHolder, msg: ChatMessage, show: Boolean) {
        holder.deviceCard.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val dev = msg.device ?: return
        holder.deviceTitle.text = dev.target
        holder.deviceMeta.text = dev.details
        holder.deviceScore.text = "Score ${"%.1f".format(dev.scoreCvss)} • Confiança ${dev.confidence}%"
        holder.deviceScore.setTextColor(riskColor(dev.risk))
    }

    private fun bindActions(holder: ChatViewHolder, msg: ChatMessage, show: Boolean) {
        holder.actionRow.removeAllViews()
        holder.actionRow.visibility = if (show && msg.actions.isNotEmpty()) View.VISIBLE else View.GONE
        if (!show) return
        val ctx = holder.itemView.context
        msg.actions.forEach { label ->
            val btn = MaterialButton(ctx, null, com.google.android.material.R.attr.buttonStyle).apply {
                text = label
                textSize = 12f
                setTextColor(Color.WHITE)
                isAllCaps = false
                backgroundTintList = ContextCompat.getColorStateList(ctx, R.color.primary)
                cornerRadius = 8
            }
            val lp = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            ).apply { marginStart = 4; marginEnd = 4 }
            btn.layoutParams = lp
            btn.setOnClickListener { onAction(msg, label) }
            holder.actionRow.addView(btn)
        }
    }

    private fun riskColor(risk: Risk): Int = when (risk) {
        Risk.CRITICO -> Color.RED
        Risk.ALTO -> Color.rgb(255, 140, 0)
        Risk.MEDIO -> Color.rgb(255, 215, 0)
        Risk.BAIXO -> Color.rgb(0, 150, 50)
        Risk.INFO -> Color.GRAY
    }

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
        val deviceCard: CardView = itemView.findViewById(R.id.device_card)
        val deviceTitle: TextView = itemView.findViewById(R.id.device_title)
        val deviceMeta: TextView = itemView.findViewById(R.id.device_meta)
        val deviceScore: TextView = itemView.findViewById(R.id.device_score)
        val actionRow: LinearLayout = itemView.findViewById(R.id.action_row)
    }
}