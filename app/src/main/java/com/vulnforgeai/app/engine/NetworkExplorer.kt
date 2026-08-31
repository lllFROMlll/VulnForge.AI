package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.ScanResult

/**
 * Orquestra a exploração da rede local (Módulo 2) em árvore de decisão:
 * Scanner → Redes → Dispositivos → Exploração → Resultados.
 *
 * Reúne os motores técnicos ([WifiAnalyzer]) e os complementos exclusivos:
 *  #1 Priorização (melhor caminho = pontuação × confiança)
 *  #4 Movimento lateral (hosts pivô via SMB/RDP/SSH)
 *  #5 Preparação de dados p/ Relatório de Operação
 *
 * As decisões de "o que aprofundar em cada alvo" podem ser refinadas pelo
 * motor de IA (AiEngine.suggestNextStep); este orquestrador mantém o núcleo
 * técnico funcional mesmo sem a chamada à IA.
 */
class NetworkExplorer(
    private val analyzer: WifiAnalyzer
) {

    /** Rede conectada. */
    private var network: String? = null

    /**
     * Executa a árvore completa de forma incremental.
     * @param onNarrative callback de log didático (um passo por vez).
     * @return os achados consolidados em ordem de prioridade (melhor caminho primeiro).
     */
    fun explore(onNarrative: (String) -> Unit, onDevice: (ScanResult) -> Unit): List<ScanResult> {
        val ts = { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date()) }
        onNarrative("${ts()} — Iniciando exploração da rede (Módulo WiFi).")

        // Scanner → Redes
        val networks = analyzer.scanNetworks()
        onNarrative("${ts()} — Scanner WiFi: ${networks.size} redes encontradas.")
        val router = networks.maxByOrNull { it.scoreCvss }
        network = router?.target
        if (router != null) {
            onNarrative("${ts()} — Roteador/alvo prioritário: '${router.target}' (score ${fmt(router.scoreCvss)}, fit ${router.confidence}%).")
        }

        // → Dispositivos
        val devices = analyzer.discoverDevices()
        onNarrative("${ts()} — Descoberta: ${devices.size} dispositivos na rede local (ARP).")
        devices.forEach(onDevice)

        // → Exploração (tentativas lógicas por dispositivo, já que sem confirmação por botão aqui roda direto em cada ficha)
        val explored = devices.flatMap { d ->
            if (d.protocols.isEmpty()) emptyList()
            else analyzer.testProtocols(d.target, d.protocols, allowNoConfirmation = true)
        }

        // → Resultados (consolidado + priorização #1)
        val findings = (devices + explored)
            .sortedByDescending { it.scoreCvss * it.confidence } // melhor caminho primeiro
        val prioritized = priorize(findings)
        onNarrative("${ts()} — Exploração concluída. ${displayed(prioritized.size)} achados priorizados.")
        return prioritized
    }

    /** Complemento #1 — melhor caminho: ordena por pontuação × confiança e marca top. */
    fun priorize(results: List<ScanResult>): List<ScanResult> {
        return results.sortedByDescending { it.scoreCvss * it.confidence }
    }

    /**
     * Complemento #4 — movimento lateral: detecta hosts que podem pivotar
     * (SMB/RDP/SSH abertos) e sugere a sequência até um "host-alvo".
     */
    fun buildLateralChain(devices: List<ScanResult>): List<String> {
        val pivots = devices.mapNotNull { d ->
            val hasPivot = d.protocols.any { it == "smb" || it == "rdp" || it == "ssh" }
            if (hasPivot) d.target to d.protocols.filter { it == "smb" || it == "rdp" || it == "ssh" } else null
        }
        if (pivots.isEmpty()) return emptyList()
        return buildList {
            add("Movimento lateral detectado: ${pivots.size} possíveis pontos de pivô.")
            pivots.forEach { (ip, ps) -> add("  → $ip pode pivotar via ${ps.joinToString(",")}.") }
            add("Sugestão de cadeia: ${pivots.joinToString(" → ") { it.first }}")
        }
    }

    /** Complemento #5 — prepara os achados como linhas p/ Relatório de Operação. */
    fun buildReportLines(findings: List<ScanResult>): List<String> {
        return findings.map { f ->
            "[${f.risk.label}] ${f.target} (score ${fmt(f.scoreCvss)}, conf ${f.confidence}%): ${f.details.take(140)}"
        }
    }

    /** Resumo didático da criptografia do roteador (para narração/chat). */
    fun routerCryptoNote(capabilities: String): String = analyzer.analyzeCrypto(capabilities)

    private fun fmt(v: Float): String =
        String.format(java.util.Locale.US, "%.1f", v)

    private fun displayed(n: Int) = n
}