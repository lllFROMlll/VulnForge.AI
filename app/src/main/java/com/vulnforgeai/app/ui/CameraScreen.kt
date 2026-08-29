package com.vulnforgeai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.vulnforgeai.app.R
import com.vulnforgeai.app.camera.CameraAnalyzer
import com.vulnforgeai.app.camera.OcrProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraScreen : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var resultText: TextView
    private lateinit var captureButton: Button

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService

    private val ocr = OcrProcessor()
    private val analyzer = CameraAnalyzer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.camera_preview)
        resultText = findViewById(R.id.camera_result)
        captureButton = findViewById(R.id.camera_capture)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
        }

        captureButton.setOnClickListener { captureAndAnalyze() }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                resultText.text = "Erro ao iniciar câmera: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndAnalyze() {
        val capture = imageCapture ?: return
        capture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    resultText.text = "Lendo texto da imagem..."
                    val mediaImage = image.image
                    if (mediaImage != null) {
                        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                        ocr.recognize(
                            inputImage,
                            onResult = { text ->
                                resultText.text = if (text.isBlank())
                                    "Nenhum texto encontrado na imagem."
                                else analyzer.analyzeText(text)
                            },
                            onError = { resultText.text = "Erro no OCR: ${it.message}" }
                        )
                    } else {
                        resultText.text = "Não foi possível ler a imagem."
                    }
                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    resultText.text = "Erro ao capturar: ${exception.message}"
                }
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}