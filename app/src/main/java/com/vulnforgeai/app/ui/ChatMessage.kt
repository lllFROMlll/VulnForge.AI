package com.vulnforgeai.app.ui

import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.NextStep
import com.vulnforgeai.app.data.Risk
import com.vulnforgeai.app.data.ScanResult

/**
 * Tipo da mensagem exibida no chat.
 */
enum class MessageType {
    NORMAL,        // conversa normal
    LOG,           // log narrativo de operação (Blitz/scan)
    STEALTH,       // exibição de stealth score antes de ação ruidosa
    WAR_MAP,       // mapa de guerra (árvore de ataque)
    DEVICE_CARD,   // ficha compacta de dispositivo descoberto no Módulo WiFi
    PROMPT_BUTTONS // pergunta com botões de ação ("prosseguir"/decisão)
}

/**
 * Mensagem do chat. Inclui remetente, texto, modo detectado, timestamp,
 * tipo e se veio do usuário. Para `DEVICE_CARD`/`PROMPT_BUTTONS`, carrega
 * dados adicionais (pontuação, confiança, ações, extração).
 */
data class ChatMessage(
    val sender: String,
    val text: String,
    val isUser: Boolean,
    val type: MessageType = MessageType.NORMAL,
    val mode: ChatMode = ChatMode.APRENDIZ,
    val risk: Risk = Risk.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    // Módulo WiFi / complementos
    val actions: List<String> = emptyList(),        // labels dos botões (PROMPT_BUTTONS)
    val device: ScanResult? = null,                 // dados da ficha de dispositivo
    val step: NextStep? = null                      // próximo passo recomendado pela IA
)