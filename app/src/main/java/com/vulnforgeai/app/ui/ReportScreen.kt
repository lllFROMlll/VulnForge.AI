package com.vulnforgeai.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.vulnforgeai.app.R
import com.vulnforgeai.app.report.ReportGenerator
import java.io.File

class ReportScreen : AppCompatActivity() {

    private lateinit var titleField: EditText
    private lateinit var findingsField: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        titleField = findViewById(R.id.report_title)
        findingsField = findViewById(R.id.report_findings)
        statusText = findViewById(R.id.report_status)

        findViewById<Button>(R.id.report_generate).setOnClickListener {
            val title = titleField.text.toString().trim().ifEmpty { "Relat\u00f3rio" }
            val lines = findingsField.text.toString().lines().map { it.trim() }.filter { it.isNotEmpty() }
            try {
                val generator = ReportGenerator(this)
                val file: File = generator.generate(title, lines)
                statusText.text = "PDF criado: ${file.absolutePath}"
                Toast.makeText(this, "PDF gerado com sucesso!", Toast.LENGTH_SHORT).show()
                sharePdf(file)
            } catch (e: Exception) {
                statusText.text = "Erro ao gerar PDF: ${e.message}"
            }
        }
    }

    private fun sharePdf(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "Compartilhar relatório"))
        } catch (e: Exception) {
            statusText.text = "PDF salvo, mas n\u00e3o foi poss\u00edvel compartilhar: ${e.message}"
        }
    }
}