package com.vulnforgeai.app.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Reconhecimento de fala nativo (STT) para o usuário digitar por voz.
 * Usa o motor de reconhecimento padrão do aparelho.
 */
class VoiceInput(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    /**
     * Inicia a captura de voz. O resultado é entregue em [onResult]
     * (texto reconhecido) ou em [onError] (mensagem amigável).
     */
    fun start(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Reconhecimento de voz não disponível neste aparelho.")
            return
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Não entendi. Tente de novo."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Demorou demais. Fale algo."
                    else -> "Erro ao ouvir. Tente novamente."
                }
                onError(msg)
                sr.destroy()
                recognizer = null
            }
            override fun onResults(results: Bundle) {
                val text = results
                    .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) onResult(text) else onError("Não entendi. Tente de novo.")
                sr.destroy()
                recognizer = null
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        sr.startListening(intent)
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}