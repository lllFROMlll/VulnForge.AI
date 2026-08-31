package com.vulnforgeai.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R

/**
 * Lista de comandos Termux essenciais, cada um com botão "Copiar".
 * (A1 — comandos explícitos em Configurações com copiar/colar, um por vez.)
 */
class TermuxCommandsAdapter(
    private val commands: List<Pair<String, String>>
) : RecyclerView.Adapter<TermuxCommandsAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_termux_command, parent, false)
        return Holder(v)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (name, command) = commands[position]
        holder.name.text = name
        holder.command.text = command
        holder.copyBtn.setOnClickListener {
            copyToClipboard(holder.itemView.context, command)
            Toast.makeText(holder.itemView.context, "Comando copiado (cole 1 por vez no Termux)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyToClipboard(context: Context, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Termux", text))
    }

    override fun getItemCount() = commands.size

    class Holder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.termux_name)
        val command: TextView = itemView.findViewById(R.id.termux_command)
        val copyBtn: Button = itemView.findViewById(R.id.termux_copy)
    }
}