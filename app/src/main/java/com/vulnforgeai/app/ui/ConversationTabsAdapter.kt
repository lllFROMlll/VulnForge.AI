package com.vulnforgeai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.ConversationStore

/**
 * Barra de abas de conversação. Cada aba: título, opções de fixar e excluir.
 */
class ConversationTabsAdapter(
    private val conversations: List<ConversationStore.Conversation>,
    private val selectedId: Long,
    private val onSelect: (Long) -> Unit,
    private val onPin: (Long) -> Unit,
    private val onDelete: (Long) -> Unit
) : RecyclerView.Adapter<ConversationTabsAdapter.TabViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val conv = conversations[position]
        val isSelected = conv.id == selectedId
        holder.titleText.text = if (conv.isPinned) "📌 ${conv.title}" else conv.title
        holder.titleText.alpha = if (isSelected) 1f else 0.6f
        holder.itemView.setOnClickListener {
            if (!isSelected) onSelect(conv.id)
        }
        // Ações: toque longo para fixar/desafixar; parte "✖" para excluir.
        holder.itemView.setOnLongClickListener {
            onPin(conv.id)
            true
        }
        holder.actionsText.setOnClickListener { onDelete(conv.id) }
    }

    override fun getItemCount() = conversations.size

    class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.tab_item_title)
        val actionsText: TextView = itemView.findViewById(R.id.tab_item_actions)
    }
}