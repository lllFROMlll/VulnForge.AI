package com.vulnforgeai.app.engine

import android.content.Context
import com.vulnforgeai.app.data.UserMode

/**
 * Analisa a resposta da IA, encontra o comando que ela sugeriu e
 * envia para o Termux (se instalado) ou mostra para copiar e colar.
 */
class ToolExecutor(private val context: Context) {

    private val termux = TermuxBridge(context)

    /** Comandos que a resposta da IA pode conter. */
    private val commandPatterns = listOf(
        Regex("""nmap\s+\S.*"""),
        Regex("""curl\s+\S.*"""),
        Regex("""sqlmap\s+\S.*"""),
        Regex("""ping\s+\S.*"""),
        Regex("""python3?\s+-m dirsearch.*"""),
        Regex("""whois\s+\S.*"""),
        Regex("""nslookup\s+\S.*"""),
        Regex("""hydra\s+\S.*"""),
        Regex("""openssl\s+s_client.*"""),
        Regex("""wget\s+\S.*""")
    )

    /**
     * Procura um comando na resposta da IA e o executa/se mostra.
     * Devolve o texto de resultado (ou instruções) ou null se não houver comando.
     */
    fun handle(aiResponse: String, mode: UserMode): String? {
        val command = findCommand(aiResponse) ?: return null
        return executeOrShow(command, mode)
    }

    private fun findCommand(text: String): String? {
        for (pattern in commandPatterns) {
            val match = pattern.find(text) ?: continue
            val candidate = match.value.trimEnd()
            if (candidate.isNotEmpty()) return candidate
        }
        return null
    }

    private fun executeOrShow(command: String, mode: UserMode): String {
        val explanation = when (mode) {
            UserMode.INICIANTE ->
                "📖 Este comando executa uma ferramenta de segurança. O VulnForgeAI vai tentar abrir o Termux para você.\n"
            UserMode.INTERMEDIARIO ->
                "🧰 Uma ferramenta foi detectada. Abrindo o Termux para executar.\n"
            UserMode.PROFISSIONAL -> ""
        }

        if (termux.isTermuxInstalled()) {
            val opened = termux.openTermuxWithCommand(command)
            return if (opened) {
                explanation +
                    "⚡ Comando enviado para o Termux:\n$command\n\n" +
                    "O resultado aparece no Termux. Digite o resultado de volta aqui se quiser que eu explique (nos modos Iniciante/Intermediário)."
            } else {
                explanation +
                    "📋 O Termux está instalado mas não aceitou abrir direto. Cole o comando nele:\n$command"
            }
        } else {
            return explanation +
                "📋 Copie e cole este comando no Termux:\n\n$command\n\n" +
                termux.getInstallTutorial() + "\n\nDepois de instalar, volte e use de novo."
        }
    }
}