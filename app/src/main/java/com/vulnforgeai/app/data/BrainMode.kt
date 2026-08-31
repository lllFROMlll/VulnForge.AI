package com.vulnforgeai.app.data

/**
 * Modo de autonomia do cérebro/IA (motor global presente em toda a ferramenta).
 *
 * - USER  = Modo 1 — o usuário atua sozinho usando as ferramentas; a IA não interfere.
 * - ASSIST = Modo 2 — a IA está presente, ajuda e apresenta as melhores brechas
 *   (com confiança por número/cor), mas NÃO executa sozinha. Pessoa "hacker amiga".
 * - AUTO  = Modo 3 — a IA executa tudo de forma automática, com todo o poder de
 *   raciocínio (e busca na web quando precisa). Nunca se limita diante de um pedido.
 */
enum class BrainMode(val label: String, val icon: String) {
    USER("Manual", "🖐"),
    ASSIST("Auxiliador", "🤝"),
    AUTO("Automático", "⚡");

    companion object {
        fun fromName(name: String?): BrainMode =
            runCatching { valueOf(name ?: "") }.getOrDefault(ASSIST)
    }
}