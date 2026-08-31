package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.ScanResult

/**
 * Estado de sessão em memória que agrega achados de TODOS os módulos
 * (alvo/tipo/score/confiança/extração). É injetado no system prompt da IA a
 * cada passo para que ela correlacione informações entre módulos ("abrir
 * caminho"), sem depender apenas do Dossiê persistente.
 *
 * A IA (cérebro) correlaciona: uma câmera + porta 554 (RTSP) pode sugerir
 * testar streaming; um servidor exposto pode reutilizar credencial de outro
 * achado etc.
 */
class BrainContext {

    private val findings = mutableListOf<ScanResult>()
    @Volatile private var lastTarget: String? = null

    /** Registra um achado de qualquer módulo (mantém amostra recente, não estoura memória). */
    @Synchronized
    fun add(result: ScanResult) {
        if (result.target.isNotBlank()) lastTarget = result.target
        if (findings.count { it.target == result.target && it.protocols == result.protocols } > 0) return
        findings.add(result)
        if (findings.size > 60) findings.removeAt(0) // amostra recente para prompt
    }

    /** Adiciona vários de uma vez. */
    @Synchronized
    fun addAll(results: List<ScanResult>) = results.forEach { add(it) }

    /** Limpa o estado de sessão (ex.: nova tarefa). */
    @Synchronized
    fun clear() {
        findings.clear()
        lastTarget = null
    }

    @Synchronized
    fun isEmpty(): Boolean = findings.isEmpty()

    @Synchronized
    fun lastTarget(): String? = lastTarget

    /** Texto compacto e injetável no system prompt (correlação entre módulos). */
    @Synchronized
    fun snapshot(): String {
        if (findings.isEmpty()) return "Contexto da sessão: ainda sem achados."
        val sb = StringBuilder("CONTEXTO VIVO DA SESSÃO (correlação entre módulos):\n")
        findings.take(25).forEach { r ->
            val proto = if (r.protocols.isEmpty()) "" else " proto=" + r.protocols.joinToString(",")
            val extr = if (r.extracted.isNotEmpty()) " extraiu=" + r.extracted.joinToString(",").take(60) else ""
            sb.appendLine("- [${r.risk.label}] ${r.target} (score ${"%.1f".format(r.scoreCvss)}, conf ${r.confidence}%)${proto}$extr")
        }
        sb.appendLine("Use essas informações para correlacionar e sugerir o próximo passo. Uma pista de um módulo pode abrir caminho de exploração em outro.")
        return sb.toString()
    }
}