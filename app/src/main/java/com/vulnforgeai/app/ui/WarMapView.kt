package com.vulnforgeai.app.ui

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.Risk

/**
 * Mapa de Guerra (attack graph) em forma de árvore de texto.
 * Renderiza redes, dispositivos e vulnerabilidades com cores por severidade.
 */
class WarMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextView(context, attrs) {

    data class Node(
        val label: String,
        val children: List<Node> = emptyList(),
        val risk: Risk = Risk.INFO
    )

    fun render(rootLabel: String, children: List<Node>) {
        val sb = SpannableStringBuilder()
        sb.append("\uD83D\uDDFA️ ").append(rootLabel).append("\n")
        children.forEach { node ->
            appendNode(sb, node, 1)
        }
        this.text = sb
    }

    private fun appendNode(sb: SpannableStringBuilder, node: Node, depth: Int) {
        val indent = "    ".repeat(depth)
        val icon = when (node.risk) {
            Risk.CRITICO -> "🔴"
            Risk.ALTO -> "🟠"
            Risk.MEDIO -> "🟡"
            Risk.BAIXO -> "🟢"
            Risk.INFO -> "⚪"
        }
        val startOfLine = sb.length
        sb.append(indent)
        sb.append(icon).append(" ").append(node.label).append("\n")
        sb.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, colorFor(node.risk))),
            startOfLine, sb.length - 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        node.children.forEach { appendNode(sb, it, depth + 1) }
    }

    private fun colorFor(risk: Risk): Int = when (risk) {
        Risk.CRITICO -> R.color.error_red
        Risk.ALTO -> R.color.warning_yellow
        Risk.MEDIO -> R.color.info_blue
        else -> R.color.text_secondary
    }
}