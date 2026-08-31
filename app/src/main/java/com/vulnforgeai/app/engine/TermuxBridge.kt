package com.vulnforgeai.app.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Cuida da ligação com o app Termux, que é onde os comandos
 * (nmap, curl, sqlmap etc.) realmente rodam no celular.
 */
class TermuxBridge(private val context: Context) {

    /** Diz se o Termux está instalado no aparelho. */
    fun isTermuxInstalled(): Boolean = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo("com.termux", 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Tenta abrir o Termux já com o comando digitado. */
    fun openTermuxWithCommand(command: String): Boolean {
        return try {
            val intent = Intent().apply {
                setClassName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startService(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Instruções simples de como instalar o Termux. */
    fun getInstallTutorial(): String = """
        📱 COMO INSTALAR O TERMUX

        Passo 1: Baixe o Termux pelo site F-Droid (a versão da Play Store é desatualizada).
            Site: https://f-droid.org/packages/com.termux/

        Passo 2: Instale o arquivo baixado (permita 'fontes desconhecidas' se pedir).

        Passo 3: Abra o Termux e digite:
            pkg update -y
            pkg upgrade -y

        Passo 4: Instale as ferramentas:
            pkg install nmap curl git python -y
            pip install sqlmap

        Pronto! Volte ao app e use o comando novamente.
    """.trimIndent()

    /** Tenta ABRIR o app Termux (se instalado). Retorna true se iniciou. */
    fun openTermux(): Boolean {
        if (!isTermuxInstalled()) return false
        return try {
            val intent = context.packageManager
                .getLaunchIntentForPackage("com.termux")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                openTermuxWithCommand("pwd")
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Comandos prontos para copiar e colar. */
    fun getEssentialCommands(): Map<String, String> = mapOf(
        "Escanear portas rápidas" to "nmap -p 80,443,22,8080 -sV ALVO",
        "Escanear todas as portas" to "nmap -p- -sV ALVO",
        "Ping test" to "ping -c 4 ALVO",
        "Ver IP público" to "curl ifconfig.me",
        "Headers de um site" to "curl -I https://exemplo.com",
        "SQL Injection" to "sqlmap -u \"URL\" --batch",
        "Descobrir diretórios" to "python3 -m dirsearch -u https://exemplo.com -e php,html,txt"
    )

    /**
     * Sequência de configuração OBRIGATÓRIA do Termux (na ordem correta) para a
     * ferramenta funcionar plenamente. Copie e cole um por um.
     */
    fun getSetupCommandsOrdered(): List<Pair<String, String>> = listOf(
        "1. Atualizar pacotes" to "pkg update -y",
        "2. Atualizar (upgrade)" to "pkg upgrade -y",
        "3. Instalar ferramentas base" to "pkg install nmap curl git python python3 -y",
        "4. Instalar sqlmap" to "pip install sqlmap",
        "5. Instalar XSS (dalfox)" to "go install github.com/hahwul/dalfox/v2@latest",
        "6. Instalar XSS (xsser)" to "pip install xsser",
        "7. Instalar WPS (reaver)" to "pkg install reaver",
        "8. Instalar WPS (wash)" to "pkg install aircrack-ng"
    )
}