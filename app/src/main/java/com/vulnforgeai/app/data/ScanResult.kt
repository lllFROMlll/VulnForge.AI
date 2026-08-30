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
    val timestamp: Long = System.currentTimeMillis()
)

enum class Risk(val label: String) {
    CRITICO("🔴 crítico"),
    ALTO("🟠 alto"),
    MEDIO("🟡 médio"),
    BAIXO("🟢 baixo"),
    INFO("⚪ info")
}