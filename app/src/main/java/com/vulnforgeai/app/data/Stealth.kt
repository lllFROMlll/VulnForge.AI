package com.vulnforgeai.app.data

/**
 * Nível de ruído/furtividade (Stealth Score) de uma ação: 0 = silencioso, 10 = muito ruidoso.
 */
enum class StealthLevel(val score: Int, val label: String) {
    SILENCIOSO(2, "silencioso"),
    MEDIO(5, "médio"),
    RUIDOSO(8, "ruidoso"),
    MUITO_RUIDOSO(10, "muito ruidoso")
}

data class StealthInfo(
    val level: StealthLevel,
    val tip: String
)

object Stealth {
    /** Marca o nível de ruído típico de cada tipo de ação. */
    fun forAction(action: String): StealthInfo = when {
        action.contains("arp", true) -> StealthInfo(STEALTH_ARP, "Leitura da tabela ARP é silenciosa e local.")
        action.contains("wlan", true) || action.contains("wifi", true) -> StealthInfo(STEALTH_WIFI, "Scan WiFi local costuma ter ruído baixo.")
        action.contains("ping", true) -> StealthInfo(STEALTH_PING, "Ping de muitos dispositivos pode ser notado por monitoramento.")
        action.contains("login", true) || action.contains("brute", true) || action.contains("hydra", true) ->
            StealthInfo(STEALTH_LOGIN, "Tentativas de login geralmente geram logs no servidor.")
        action.contains("exploit", true) || action.contains("wps", true) ->
            StealthInfo(STEALTH_EXPLOIT, "Exploração ativa é o ruído mais alto e pode derrubar o alvo.")
        else -> StealthInfo(STEALTH_GENERIC, "Ação genérica de varredura. Ruído dependente do alvo.")
    }

    fun scoreFor(action: String): Int = forAction(action).level.score

    private val STEALTH_ARP = StealthLevel.SILENCIOSO
    private val STEALTH_WIFI = StealthLevel.SILENCIOSO
    private val STEALTH_PING = StealthLevel.RUIDOSO
    private val STEALTH_LOGIN = StealthLevel.MEDIO
    private val STEALTH_EXPLOIT = StealthLevel.MUITO_RUIDOSO
    private val STEALTH_GENERIC = StealthLevel.MEDIO
}