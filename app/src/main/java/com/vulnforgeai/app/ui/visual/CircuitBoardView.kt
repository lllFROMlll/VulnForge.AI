package com.vulnforgeai.app.ui.visual

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Camada visual central do REDESIGN: painel tipo placa de circuito.
 *
 * - Fundo escuro com trilhas (linhas) de circuito quase apagadas em repouso.
 * - Um rosto formado por partículas de luz se revela quando a IA fala,
 *   com intensidade proporcional ao nível de áudio (0..1).
 * - Cliques/teclas disparam um pulso de luz passando pelas trilhas.
 *
 * Esta é uma implementação real (Canvas leve, sem OpenGL) que substitui o
 * contrato [VisualStage]. É desligável/troca-se fácil caso o redesign maior
 * (OpenGL/sombras) venha depois.
 */
class CircuitBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), VisualStage {

    private val tracePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(28, 0, 188, 212)
        isAntiAlias = true
    }
    private val particlePaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.CYAN
        isAntiAlias = true
    }
    private val pulsePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.WHITE
        isAntiAlias = true
    }

    // Trilhas (segmentos de linha) do "circuito".
    private val traces = listOf(
        floatArrayOf(0f, 0.3f, 0.35f, 0.3f),
        floatArrayOf(0.35f, 0.3f, 0.5f, 0.15f),
        floatArrayOf(0.5f, 0.15f, 0.85f, 0.15f),
        floatArrayOf(0f, 0.7f, 0.3f, 0.7f),
        floatArrayOf(0.3f, 0.7f, 0.45f, 0.85f),
        floatArrayOf(0.45f, 0.85f, 0.8f, 0.85f),
        floatArrayOf(0.7f, 0.4f, 0.9f, 0.4f),
        floatArrayOf(0.15f, 0.5f, 0.15f, 0.6f),
        floatArrayOf(0.85f, 0.6f, 0.85f, 0.45f)
    )

    // Posições do "rosto" (nós) — partículas que se revelam ao falar.
    private val faceNodes = listOf(
        0.5f to 0.35f, 0.42f to 0.45f, 0.58f to 0.45f,
        0.47f to 0.55f, 0.53f to 0.55f
    )

    private var audioLevel = 0f
    private val pulses = mutableListOf<ValueAnimator>()
    private val pulsePos = mutableListOf<Float>()
    private var activeParticle = 0f

    private val drift = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2600
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.RESTART
        addUpdateListener {
            tracePaint.color = Color.argb(
                (28 + (Math.sin(it.animatedValue as Float * 6.28) * 14).toInt().coerceIn(0, 40)),
                0, 188, 212
            )
            activeParticle = it.animatedValue as Float
            invalidate()
        }
        start()
    }

    init {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Trilhas do circuito.
        traces.forEach { t ->
            tracePaint.alpha = if (audioLevel > 0.35f) 200 else 90
            canvas.drawLine(t[0] * w, t[1] * h, t[2] * w, t[3] * h, tracePaint)
        }

        // Pulso de luz transitando (feito por animator global).
        pulsePos.forEachIndexed { i, pos ->
            if (pos >= 0f && pos <= 1f) {
                val idx = (pos * (traces.size - 1)).toInt().coerceIn(0, traces.size - 1)
                val t = traces[idx]
                val x = t[0] * w + (t[2] - t[0]) * w * (pos - 0f)
                val y = t[1] * h + (t[3] - t[1]) * h * (pos - 0f)
                canvas.drawCircle(x, y, 3.5f, pulsePaint)
            }
        }

        // Rosto de partículas — revela com a fala (audioLevel).
        val reveal = audioLevel.coerceIn(0f, 1f) * 1.4f * activeParticle
        faceNodes.forEach { (fx, fy) ->
            val cx = fx * w
            val cy = fy * h
            val r = (2f + reveal * 6f).coerceAtLeast(1.5f)
            particlePaint.alpha = (40 + reveal * 200).toInt().coerceIn(30, 255)
            canvas.drawCircle(cx, cy, r, particlePaint)
        }
    }

    override fun setIdle() {
        audioLevel = 0f
        invalidate()
    }

    override fun onSpeak(level: Float) {
        audioLevel = level.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onTyping() {
        // Dispara um pulso de luz pelas trilhas.
        if (pulses.size > 6) pulses.removeAt(0)
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener {
                if (pulsePos.size < 8) pulsePos.add(it.animatedValue as Float)
                invalidate()
            }
            start()
        }
        pulses.add(anim)
        pulsePos.clear()
        invalidate()
    }

    override fun onVoiceActive(active: Boolean) {
        // Em repouso de voz mantém o equilíbrio; sem ação extra agora.
        invalidate()
    }

    override fun release() {
        drift.cancel()
        pulses.forEach { it.cancel() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}