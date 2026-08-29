package com.vulnforgeai.app.learning

import android.content.Context
import com.vulnforgeai.app.data.UserPrefs

/**
 * Controla o progresso do modo Aprendiz (missões concluídas).
 */
class MissionManager(context: Context) {

    private val prefs = UserPrefs(context)

    fun isComplete(missionId: Int): Boolean =
        prefs.getBoolean("mission_$missionId", false)

    fun markComplete(missionId: Int) {
        prefs.putBoolean("mission_$missionId", true)
    }

    fun completedIds(): Set<Int> =
        MissionData.allMissions.mapNotNull { if (isComplete(it.id)) it.id else null }.toSet()

    fun completedCount(): Int = completedIds().size

    /** Próxima missão ainda não concluída (para o modo guiado). */
    fun nextPending(): Mission? =
        MissionData.allMissions.firstOrNull { !isComplete(it.id) }
}