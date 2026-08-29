package com.vulnforgeai.app.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gera um arquivo PDF com os achados de segurança.
 * Usa a geração de PDF nativa do Android, sem dependências extras.
 */
class ReportGenerator(private val context: Context) {

    data class Finding(val text: String, val severity: Severity)
    enum class Severity { CRITICO, ALTO, MEDIO, BAIXO, INFO }

    /** Cria o PDF a partir de linhas de texto e devolve o caminho do arquivo. */
    fun generate(title: String, rawLines: List<String>): File {
        val findings = classify(rawLines)
        val page = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val doc = PdfDocument()
        val currentPage = doc.startPage(page)

        val paint = Paint().apply { isAntiAlias = true }
        val titlePaint = Paint().apply { isAntiAlias = true; textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val headerPaint = Paint().apply { isAntiAlias = true; textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val bodyPaint = Paint().apply { isAntiAlias = true; textSize = 11f }

        var y = 60f
        val canvas = currentPage.canvas

        canvas.drawText("VulnForgeAI - Relatório de Segurança", 50f, y, titlePaint); y += 30f
        canvas.drawText("Título: $title", 50f, y, headerPaint); y += 20f
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Data: $date", 50f, y, bodyPaint); y += 30f

        findings.forEach { finding ->
            val color = when (finding.severity) {
                Severity.CRITICO -> Color.RED
                Severity.ALTO -> Color.rgb(255, 140, 0)
                Severity.MEDIO -> Color.rgb(255, 215, 0)
                Severity.BAIXO -> Color.rgb(0, 150, 50)
                Severity.INFO -> Color.GRAY
            }
            bodyPaint.color = color
            bodyPaint.textSize = 12f
            canvas.drawText("[${finding.severity.name}] ${finding.text}", 50f, y, bodyPaint)
            y += 24f
        }

        doc.finishPage(currentPage)

        val out = getOutFile(title)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun classify(lines: List<String>): List<Finding> {
        val lower = lines.joinToString(" ").lowercase(Locale.getDefault())
        val severity = when {
            listOf("cr\u00edtico", "critical", "rce", "root", "senha exposta", "sql").any { lower.contains(it) } ->
                Severity.CRITICO
            listOf("alto", "admin", "backup", "backdoor").any { lower.contains(it) } -> Severity.ALTO
            listOf("m\u00e9dio", "xss", "csrf").any { lower.contains(it) } -> Severity.MEDIO
            listOf("baixo", "informa\u00e7\u00e3o", "porta aberta").any { lower.contains(it) } -> Severity.BAIXO
            else -> Severity.INFO
        }
        return lines.map { Finding(it, severity) }.ifEmpty { listOf(Finding("Nenhum achado informado.", Severity.INFO)) }
    }

    private fun getOutFile(title: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "").replace(" ", "_").take(40)
        val name = "VulnForge_${safe}_${System.currentTimeMillis()}.pdf"
        return File(dir, name)
    }
}