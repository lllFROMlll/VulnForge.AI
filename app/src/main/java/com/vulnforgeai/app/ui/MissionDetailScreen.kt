package com.vulnforgeai.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserMode
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.learning.Mission
import com.vulnforgeai.app.learning.MissionData
import com.vulnforgeai.app.learning.MissionManager

class MissionDetailScreen : AppCompatActivity() {

    companion object {
        const val EXTRA_MISSION_ID = "mission_id"
    }

    private lateinit var prefs: UserPrefs
    private lateinit var manager: MissionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mission_detail)

        prefs = UserPrefs(this)
        manager = MissionManager(this)

        val missionId = intent.getIntExtra(EXTRA_MISSION_ID, 1)
        val mission = MissionData.allMissions.firstOrNull { it.id == missionId }
            ?: return

        findViewById<TextView>(R.id.mission_detail_title).text = "${mission.id}. ${mission.title}"
        findViewById<TextView>(R.id.mission_detail_objective).text = "Objetivo: ${mission.objective}"

        val container: LinearLayout = findViewById(R.id.mission_steps_container)
        container.removeAllViews()

        mission.steps.forEach { step ->
            val builder = StringBuilder()
            builder.appendLine("${step.instruction}")
            step.command?.let { builder.appendLine("\nComando: $it") }

            // Mostra explicação completa só no modo Iniciante.
            if (prefs.mode == UserMode.INICIANTE && !step.explanation.isNullOrBlank()) {
                builder.appendLine("\n📖 ${step.explanation}")
            }
            builder.appendLine("")
            container.addView(makeStepText(builder.toString()))
        }

        val completeButton: Button = findViewById(R.id.mission_complete_button)
        completeButton.backgroundTintList = ContextCompat.getColorStateList(
            this,
            if (manager.isComplete(missionId)) R.color.primary else R.color.success_green
        )
        completeButton.text = if (manager.isComplete(missionId)) "Missão concluída ✔" else "Concluí esta missão"
        completeButton.setOnClickListener {
            manager.markComplete(missionId)
            completeButton.text = "Missão concluída ✔"
            completeButton.backgroundTintList = ContextCompat.getColorStateList(this, R.color.primary)
        }
    }

    private fun makeStepText(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            textSize = 14f
            setPadding(0, 6, 0, 6)
        }
    }
}