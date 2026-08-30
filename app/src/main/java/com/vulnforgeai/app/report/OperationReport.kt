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
 * Relatório de Operação do MODO BLITZ: registro cronológico (narrativa)
 * + explicação didática de cada vulnerabilidade encontrada.
 * Usa geração de PDF nativa do Android (sem dependência extra).
 */
class OperationReport(private val context: Context) {

    /**
     * Gera o PDF.
     * @param title título do relatório
     * @param narrative linhas narrativas (log cronológico com timestamps)
     * @param findings achados consolidados (vulnerabilidades + explicação didática)
     * @param didactic true = inclui explicação didática; false = só lista enxuta
     */
    fun generate(title: String, narrative: List<String>, findings: List<String>, didactic: Boolean): File {
        val page = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val doc = PdfDocument()
        val currentPage = doc.startPage(page)
        val canvas = currentPage.canvas

        val titlePaint = Paint().apply { isAntiAlias = true; textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val sectionPaint = Paint().apply { isAntiAlias = true; textSize = 13f; typeface = android.graphics.Typeface.DEFAULT_BOLD }
        val bodyPaint = Paint().apply { isAntiAlias = true; textSize = 10f }
        val logPaint = Paint().apply { isAntiAlias = true; textSize = 9f; typeface = android.graphics.Typeface.MONOSPACE }

        var y = 60f
        canvas.drawText("VulnForgeAI - RELATÓRIO DE OPERAÇÃO", 50f, y, titlePaint); y += 30f
        canvas.drawText("Título: $title", 50f, y, sectionPaint); y += 20f
        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        canvas.drawText("Data: $date", 50f, y, bodyPaint); y += 30f

        // 1) Narrativa cronológica
        canvas.drawText("1. CRONOLOGIA DA OPERAÇÃO", 50f, y, sectionPaint); y += 22f
        if (narrative.isNotEmpty()) {
            narrative.forEach { line ->
                canvas.drawText(line, 50f, y, logPaint); y += 16f
            }
        } else {
            canvas.drawText("Sem narrativa registrada.", 50f, y, bodyPaint); y += 20f
        }
        y += 18f

        // 2) Achados
        canvas.drawText("2. ACHADOS", 50f, y, sectionPaint); y += 22f
        if (findings.isEmpty()) {
            canvas.drawText("Nenhum achado informado.", 50f, y, bodyPaint); y += 20f
        } else {
            findings.forEach { finding ->
                val color = when {
                    finding.contains("cr\u00edtico", true) || finding.contains("critical", true) -> Color.RED
                    finding.contains("alto", true) -> Color.rgb(255, 140, 0)
                    finding.contains("m\u00e9dio", true) || finding.contains("médio", true) -> Color.rgb(255, 215, 0)
                    finding.contains("baixo", true) -> Color.rgb(0, 150, 50)
                    else -> Color.GRAY
                }
                bodyPaint.color = color
                canvas.drawText("• $finding", 50f, y, bodyPaint); y += 18f
            }
        }
        y += 16f

        // 3) Explicação didática (opcional)
        if (didactic) {
            canvas.drawText("3. EXPLICAÇÃO DIDÁTICA", 50f, y, sectionPaint); y += 22f
            findings.forEach { finding ->
                canvas.drawText("• $finding", 50f, y, bodyPaint); y += 16f
                canvas.drawText("   Por que acontece e como proteger: consulte o chat para orientação passo a passo.", 60f, y, bodyPaint); y += 18f
            }
        }

        doc.finishPage(currentPage)
        val out = getOutFile(title)
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    private fun getOutFile(title: String): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val safe = title.replace(Regex("[^A-Za-z0-9 _-]"), "").replace(" ", "_").take(40)
        val name = "VulnForge_Op_${safe}_${System.currentTimeMillis()}.pdf"
        return File(dir, name)
    }
}