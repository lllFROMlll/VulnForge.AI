package com.vulnforgeai.app.data

/**
 * Modo de apresentação do chat. AUTO = a IA detecta o nível pelo texto.
 */
enum class ChatMode(val label: String) {
    AUTO("Automático"),
    APRENDIZ("Aprendiz"),
    INTERMEDIARIO("Intermediário"),
    PROFISSIONAL("Profissional")
}