package com.vulnforgeai.app.data

/**
 * Próxima ação recomendada pelo motor de IA (cérebro) durante a exploração.
 * Estrutura compila os resultados/intelelência para o app executar e renderizar
 * nas fichas/botões do chat (loop "act-then-analyze").
 */
data class NextStep(
    val target: String,             // IP/SSID do alvo
    val action: String,             // o que fazer (ex.: "testar credencial", "verificar CVE", "pivotar")
    val protocol: String?,          // protocolo sugerido (ex.: "ftp", "rtsp"), se aplicável
    val command: String?,           // comando Termux sugerido, se aplicável
    val score: Float = 0f,          // pontuação CVSS-like 0..10 estimada
    val confidence: Int = 0,        // confiança 0..100
    val explanation: String = "",   // didática: por que este passo / o que faz
    val description: String = ""    // descrição legível para a ficha do chat
) {
    /** Labels dos botões de ação oferecidos ao usuário (gate/execução). */
    fun buttonLabels(): List<String> = buildList {
        if (command != null) add("Executar no Termux")
        if (protocol != null) add("Testar $protocol")
        add("Próximo alvo")
    }
}