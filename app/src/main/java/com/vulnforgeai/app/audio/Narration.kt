package com.vulnforgeai.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.vulnforgeai.app.data.UserPrefs
import java.util.Locale

/**
 * Narração (TTS) das respostas do chat. Usa a voz escolhida pelo usuário
 * (seleção salva em UserPrefs.voice), ou a melhor voz natural disponível no
 * aparelho, evitando a voz padrão quando houver alternativa melhor.
 */
class Narration(context: Context, private val prefs: UserPrefs) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val appContext = context.applicationContext

    init {
        tts = TextToSpeech(appContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            applyVoice(prefs.selectedVoice)
        }
    }

    /** Aplica a voz preferida (por id/nome). Se vazia, escolhe a melhor disponível. */
    fun applyVoice(voiceId: String) {
        val engine = tts ?: return
        val engineName = engine.getDefaultEngine()
        val best = pickBestVoice(engine, voiceId)
        if (best != null && engine.setVoice(best) == TextToSpeech.SUCCESS) {
            return
        }
        // Fallback: aplica diretamente pela engine se houver voz com esse id.
        val chosen = pickVoiceById(engine, voiceId)
        if (chosen != null) engine.setVoice(chosen)
    }

    /** Fala o texto. Retorna true se conseguiu disparar. */
    fun speak(text: String): Boolean {
        val engine = tts ?: return false
        if (!ready) return false
        applyVoice(prefs.selectedVoice)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "narration")
        return true
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }

    /** Vozes disponíveis no aparelho (para a tela de seleção). */
    fun availableVoices(): List<Voice> = tts?.voices?.toList() ?: emptyList()

    fun hasFewVoices(): Boolean = availableVoices().size < 4

    /** Lista as vozes legíveis com id e nome. */
    fun listVoiceLabels(): List<Pair<String, String>> =
        availableVoices().map { it.name to voiceLabel(it) }

    private fun voiceLabel(v: Voice): String {
        val loc = "${v.locale.language}-${v.locale.country} (${v.features.size} feat.)"
        return "${if (v.isNetworkConnectionRequired) "☁ " else "📦 "}$loc"
    }

    private fun pickBestVoice(engine: TextToSpeech, preferredId: String): Voice? {
        // Se o usuário selecionou uma, tenta por igual ao id.
        preferredId.takeIf { it.isNotBlank() }?.let { id ->
            return pickVoiceById(engine, id)
        }
        // Senão, escolhe a voz com maior qualidade natural: voice name que
        // indique qualidade superior (ex.: alta qualidade / "highquality").
        val voices = engine.voices?.toList().orEmpty()
        val pt = voices.filter { it.locale.language.equals("pt", true) }
        val base = pt.ifEmpty { voices }
        return base.firstOrNull { it.name.contains("high", true) || it.name.contains("300mbps", true) }
            ?: base.firstOrNull { !it.isNetworkConnectionRequired }
    }

    private fun pickVoiceById(engine: TextToSpeech, id: String): Voice? {
        val voices = engine.voices?.toList().orEmpty()
        return voices.firstOrNull { it.name == id || it.name.contains(id, true) }
    }

    companion object {
        private const val MIN_TEXT_LENGTH = 2
        fun isSpeakable(text: String): Boolean = text.length >= MIN_TEXT_LENGTH
    }
}