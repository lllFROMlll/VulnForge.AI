package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.Risk
import com.vulnforgeai.app.data.ScanResult

/**
 * Orquestrador do Módulo 3 (Web/XSS). Roda [XssScanner], prioriza achados por
 * pontuação × confiança, alimenta [BrainContext] e prepara linhas p/ Relatório.
 * Segue o mesmo padrão do `NetworkExplorer` (Módulo 2).
 */
class WebExplorer(
    private val scanner: XssScanner,
    private val brain: BrainContext? = null
) {

    /** Testa XSS na URL e devolve achados priorizados. */
    suspend fun explore(url: String, onNarrative: (String) -> Unit): List<ScanResult> {
        onNarrative("🔍 Testando XSS em $url...")
        val results = scanner.scan(url)
        brain?.addAll(results)
        val prioritized = results.sortedByDescending { it.scoreCvss * it.confidence }
        val vuln = prioritized.filter { it.risk == Risk.ALTO || it.risk == Risk.CRITICO }
        onNarrative(
            if (vuln.isEmpty()) "Nenhum reflexo XSS direto em $url."
            else "⚠️ ${vuln.size} possíveis XSS refletidos em $url."
        )
        return prioritized
    }

    /** Prioriza por pontuação × confiança (melhor caminho). */
    fun priorize(results: List<ScanResult>): List<ScanResult> =
        results.sortedByDescending { it.scoreCvss * it.confidence }

    /** Prepara linhas para o Relatório de Operação. */
    fun buildReportLines(findings: List<ScanResult>): List<String> =
        findings.map {
            "[${it.risk.label}] XSS ${it.target} (score ${"%.1f".format(it.scoreCvss)}, conf ${it.confidence}%): ${it.details.take(140)}"
        }
}