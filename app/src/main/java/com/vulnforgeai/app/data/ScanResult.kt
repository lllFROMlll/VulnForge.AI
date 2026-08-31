package com.vulnforgeai.app.data

import java.util.Date

/**
 * Resultado de um scan/módulo, usado como contexto no chat e no Dossiê.
 */
data class ScanResult(
    val type: String,          // wlan, network, port, web, iptv, ...
    val target: String,        // SSID, IP, URL...
    val details: String,       // texto do resultado
    val risk: Risk = Risk.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    // Campos do Módulo 2 (WiFi explorer). Defaults preservam compatibilidade.
    val scoreCvss: Float = 0f,       // severidade CVSS-like 0..10
    val confidence: Int = 0,         // grau de confiança 0..100
    val protocols: List<String> = emptyList(), // smb/ftp/http/telnet/rtsp
    val extracted: List<String> = emptyList()  // dados extraídos por tentativa
)

enum class Risk(val label: String) {
    CRITICO("🔴 crítico"),
    ALTO("🟠 alto"),
    MEDIO("🟡 médio"),
    BAIXO("🟢 baixo"),
    INFO("⚪ info");

    companion object {
        /** Mapeia um score CVSS-like 0..10 para um nível de risco. */
        fun fromScore(score: Float): Risk = when {
            score >= 8.5f -> CRITICO
            score >= 6.5f -> ALTO
            score >= 4.0f -> MEDIO
            score >= 2.0f -> BAIXO
            else -> INFO
        }
    }
}