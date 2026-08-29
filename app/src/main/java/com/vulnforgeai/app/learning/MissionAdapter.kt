package com.vulnforgeai.app.learning

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R

class MissionAdapter(
    private val missions: List<Mission>,
    private val completedIds: Set<Int>,
    private val onClick: (Mission) -> Unit
) : RecyclerView.Adapter<MissionAdapter.MissionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MissionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_mission, parent, false)
        return MissionViewHolder(view)
    }

    override fun onBindViewHolder(holder: MissionViewHolder, position: Int) {
        val mission = missions[position]
        val done = completedIds.contains(mission.id)
        holder.titleText.text = if (done) "✔ ${mission.id}. ${mission.title}" else "${mission.id}. ${mission.title}"
        holder.metaText.text = "Nível ${mission.level} • ${if (done) "Concluída" else "Pendente"}"
        holder.itemView.setOnClickListener { onClick(mission) }
    }

    override fun getItemCount() = missions.size

    class MissionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleText: TextView = itemView.findViewById(R.id.mission_item_title)
        val metaText: TextView = itemView.findViewById(R.id.mission_item_meta)
    }
}