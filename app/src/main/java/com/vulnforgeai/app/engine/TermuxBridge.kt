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
}