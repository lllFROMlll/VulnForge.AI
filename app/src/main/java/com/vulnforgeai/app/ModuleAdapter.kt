package com.vulnforgeai.app

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.vulnforgeai.app.ui.CameraScreen
import com.vulnforgeai.app.ui.IptvScreen
import com.vulnforgeai.app.ui.LearningScreen
import com.vulnforgeai.app.ui.PortScanScreen
import com.vulnforgeai.app.ui.ReportScreen
import com.vulnforgeai.app.ui.SettingsScreen
import com.vulnforgeai.app.ui.WebScanScreen
import com.vulnforgeai.app.ui.WifiScreen

data class ModuleItem(
    val icon: String,
    val title: String,
    val description: String,
    val targetActivity: Class<*>
)

class ModuleAdapter(
    private val modules: List<ModuleItem>,
    private val onClick: (ModuleItem) -> Unit
) : RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ModuleViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_module_card, parent, false)
        return ModuleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ModuleViewHolder, position: Int) {
        val module = modules[position]
        holder.iconText.text = module.icon
        holder.titleText.text = module.title
        holder.descriptionText.text = module.description
        holder.cardView.setOnClickListener { onClick(module) }
    }

    override fun getItemCount() = modules.size

    class ModuleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView = itemView.findViewById(R.id.module_card)
        val iconText: TextView = itemView.findViewById(R.id.module_icon)
        val titleText: TextView = itemView.findViewById(R.id.module_title)
        val descriptionText: TextView = itemView.findViewById(R.id.module_description)
    }
}