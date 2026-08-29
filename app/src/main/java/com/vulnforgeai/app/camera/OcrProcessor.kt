package com.vulnforgeai.app.camera

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * Lê o texto que aparece em uma foto (OCR) usando o ML Kit do Google.
 */
class OcrProcessor {

    private val recognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Lê o texto da imagem e chama onResult com o texto encontrado. */
    fun recognize(image: InputImage, onResult: (String) -> Unit, onError: (Exception) -> Unit) {
        recognizer.process(image)
            .addOnSuccessListener { visionText -> onResult(visionText.text) }
            .addOnFailureListener { e -> onError(e) }
    }
}