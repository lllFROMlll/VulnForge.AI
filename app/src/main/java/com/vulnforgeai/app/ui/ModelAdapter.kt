package com.vulnforgeai.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R

class ModelAdapter(
    private val models: List<Pair<String, String>>,
    private val selectedId: String,
    private val onClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<ModelAdapter.ModelViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_model, parent, false)
        return ModelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModelViewHolder, position: Int) {
        val model = models[position]
        val isSelected = model.first == selectedId
        holder.idText.text = if (isSelected) "✔ ${model.first}" else model.first
        holder.nameText.text = model.second
        holder.itemView.setOnClickListener { onClick(model) }
    }

    override fun getItemCount() = models.size

    class ModelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idText: TextView = itemView.findViewById(R.id.model_item_id)
        val nameText: TextView = itemView.findViewById(R.id.model_item_name)
    }
}