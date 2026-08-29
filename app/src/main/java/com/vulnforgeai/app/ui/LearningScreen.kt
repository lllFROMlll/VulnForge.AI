package com.vulnforgeai.app.ui

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.learning.Mission
import com.vulnforgeai.app.learning.MissionAdapter
import com.vulnforgeai.app.learning.MissionData
import com.vulnforgeai.app.learning.MissionManager

class LearningScreen : AppCompatActivity() {

    private lateinit var progressText: TextView
    private lateinit var manager: MissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_learning)

        manager = MissionManager(this)
        progressText = findViewById(R.id.learning_progress)

        val list: RecyclerView = findViewById(R.id.learning_list)
        list.layoutManager = LinearLayoutManager(this)

        refresh(list)
    }

    override fun onResume() {
        super.onResume()
        val list: RecyclerView = findViewById(R.id.learning_list)
        refresh(list)
    }

    private fun refresh(list: RecyclerView) {
        progressText.text = "Progresso: ${manager.completedCount()}/${MissionData.allMissions.size} missões"
        val adapter = MissionAdapter(MissionData.allMissions, manager.completedIds()) { mission ->
            openMission(mission)
        }
        list.adapter = adapter
    }

    private fun openMission(mission: Mission) {
        val intent = Intent(this, MissionDetailScreen::class.java)
        intent.putExtra(MissionDetailScreen.EXTRA_MISSION_ID, mission.id)
        startActivity(intent)
    }
}