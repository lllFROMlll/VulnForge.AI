package com.vulnforgeai.app.ui

import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.Risk

/**
 * Tipo da mensagem exibida no chat.
 */
enum class MessageType {
    NORMAL,        // conversa normal
    LOG,           // log narrativo de operação (Blitz/scan)
    STEALTH,       // exibição de stealth score antes de ação ruidosa
    WAR_MAP        // mapa de guerra (árvore de ataque)
}

/**
 * Mensagem do chat. Inclui remetente, texto, modo detectado, timestamp,
 * tipo e se veio do usuário.
 */
data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val type: MessageType = MessageType.NORMAL,
    val mode: ChatMode = ChatMode.APRENDIZ,
    val risk: Risk = Risk.INFO,
    val timestamp: Long = System.currentTimeMillis()
)