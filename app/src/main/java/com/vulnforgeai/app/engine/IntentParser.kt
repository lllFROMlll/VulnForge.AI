package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.ChatMode

/**
 * Interpreta a intenção do pedido do usuário e decide qual modo de
 * apresentação usar (quando o modo está em AUTOMÁTICO), além de detectar
 * ações especiais como Blitz, varreduras, exploração e confirmação.
 */
object IntentParser {

    data class ParsedIntent(
        val mode: ChatMode,
        val isBlitz: Boolean,
        val isScan: Boolean,
        val isExploit: Boolean,
        val asksConfirmation: Boolean
    )

    fun resolveMode(prefMode: ChatMode, text: String): ChatMode {
        if (prefMode != ChatMode.AUTO) return prefMode
        return detectModeFromText(text)
    }

    fun parse(text: String): ParsedIntent {
        val lower = text.lowercase()
        return ParsedIntent(
            mode = detectModeFromText(text),
            isBlitz = containsAny(lower, listOf("blitz", "varre", "bateria", "tudo de uma vez", "full scan")),
            isScan = containsAny(lower, listOf("scan", "varre", "escane", "escaneie", "analisa", "verifica", "porta", "wifi", "site", "iptv")),
            isExploit = containsAny(lower, listOf("exploit", "invadir", "força bruta", "brute", "senha", "hack", "wps")),
            asksConfirmation = containsAny(lower, listOf("sim", "ok", "pode", "quero", "vai", "confirma", "continuar", "continue", "todas", "1 a 1", "uma de cada vez"))
        )
    }

    private fun detectModeFromText(text: String): ChatMode {
        val lower = text.lowercase()
        // Sinais de aprendizado/curiosidade -> Aprendiz
        if (containsAny(lower, listOf("o que significa", "como funciona", "explique", "ensina", "aprendiz", "sou novo",
                "por que", "para que serve", "me ajuda a entender", "didático"))) {
            return ChatMode.APRENDIZ
        }
        // Sinais profissionais/diretos -> Profissional
        if (containsAny(lower, listOf("modo profissional", "direto", "sem explicação", "só executa", "turbo",
                "profissional"))) {
            return ChatMode.PROFISSIONAL
        }
        // Sinais de resumo rápido -> Intermediário
        if (containsAny(lower, listOf("resumo", "rápido", "curto", "intermediário", "enxuto"))) {
            return ChatMode.INTERMEDIARIO
        }
        // Padrão: pedagogia leve (favorável ao iniciante explicar)
        return ChatMode.APRENDIZ
    }

    private fun containsAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }
}