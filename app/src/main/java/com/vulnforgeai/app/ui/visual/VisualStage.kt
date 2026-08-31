package com.vulnforgeai.app.ui.visual

/**
 * CONTRATO da camada visual central — preparação para o redesign (placa de
 * circuito + rosto de partículas). Nesta fase NÃO implementamos a animação;
 * esta interface isola o comportamento que o redesign futuro vai desenhar.
 *
 * A UI passa a usar este contrato; quando o redesign entrar, basta trocar a
 * implementação ([SimpleVisualStage] por uma animada) sem tocar na lógica.
 */
interface VisualStage {

    /** Repouso: trilhas quase apagadas, "hibernando". */
    fun setIdle()

    /** IA falando: o rosto se revela; `audioLevel` 0..1 controla intensidade. */
    fun onSpeak(audioLevel: Float)

    /** Usuário digitou/teclou: acende um pulso numa trilha. */
    fun onTyping()

    /** Usuário ativou o microfone (voice input) — trilhas passam a reagir à voz. */
    fun onVoiceActive(active: Boolean)

    /** Libera recursos quando a tela é destruída. */
    fun release()
}

/**
 * Implementação atual "simples" (sem animação real). Usada para compilar e
 * rodar sem custo de render; o redesign substitui por uma implementação animada.
 */
class SimpleVisualStage : VisualStage {
    override fun setIdle() = Unit
    override fun onSpeak(audioLevel: Float) = Unit
    override fun onTyping() = Unit
    override fun onVoiceActive(active: Boolean) = Unit
    override fun release() = Unit
}