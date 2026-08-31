package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.Risk
import com.vulnforgeai.app.data.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Módulo 3 — Scanner XSS. Testa de verdade (sem root) se uma URL/alvo reflete
 * payloads XSS. Envia payloads reais via GET nos parâmetros e detecta "reflexão
 * sem escape" (o payload aparece cru na resposta), o que indica XSS refletido.
 *
 * Prepara também o comando de apoio p/ Termux (dalfox/xsser). Entra no padrão
 * dos outros motores: retorna [ScanResult] com score CVSS + confiança + cor.
 */
class XssScanner {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // Marcadores únicos embutidos nos payloads para detectar reflexão crua.
    private val payloads = listOf(
        """<script>alert('VFA1')</script>""",
        """"><svg onload=alert('VFA2')>""",
        """javascript:alert('VFA3')""",
        """'"><img src=x onerror=alert('VFA4')>""",
        """<iframe src="javascript:alert('VFA5')"></iframe>"""
    )

    /** Testa uma URL inteira; descobre parâmetros GET e roda payloads. */
    suspend fun scan(url: String): List<ScanResult> = withContext(Dispatchers.IO) {
        val params = extractParams(url)
        if (params.isEmpty()) return@withContext emptyList()

        val out = mutableListOf<ScanResult>()
        params.forEach { (key, _) ->
            var reflected = false
            var payloadUsed = ""
            var snippet = ""
            for (payload in payloads) {
                val attacked = inject(url, key, payload)
                val body = fetch(attacked)
                if (body != null && body.contains(payload, ignoreCase = true)) {
                    reflected = true
                    payloadUsed = payload
                    snippet = extractSnippet(body, payload)
                    break
                }
            }
            if (reflected) {
                out.add(
                    ScanResult(
                        type = "xss",
                        target = url,
                        details = "XSS REFLETIDO no parâmetro '$key'. Payload refletido cru: ${payloadUsed.take(60)}. " +
                            "Trecho: $snippet\nComando Termux de apoio: dalfox url ${quote(url)} --param $key",
                        risk = Risk.ALTO,
                        scoreCvss = 7.0f,
                        confidence = 82,
                        protocols = listOf("xss"),
                        extracted = listOf("XSS refletido em $key (payload ${payloadUsed.take(40)})")
                    )
                )
            }
        }
        if (out.isEmpty()) {
            out.add(
                ScanResult(
                    type = "xss",
                    target = url,
                    details = "Nenhum reflexo XSS cru detectado nos ${params.size} parâmetros testados. Pode ainda haver XSS DOM/armazenado; aprofundar com ferramenta (dalfox/xsser) no Termux.",
                    risk = Risk.BAIXO,
                    scoreCvss = 2.0f,
                    confidence = 55,
                    protocols = listOf("xss")
                )
            )
        }
        out
    }

    /** Testa um único parâmetro explicitadamente (ex.: via campo "Testar XSS"). */
    suspend fun scanParam(urlBase: String, param: String): ScanResult = withContext(Dispatchers.IO) {
        var reflected = false
        var payloadUsed = ""
        for (payload in payloads) {
            val attacked = inject(urlBase, param, payload)
            val body = fetch(attacked)
            if (body != null && body.contains(payload, ignoreCase = true)) {
                reflected = true
                payloadUsed = payload
                break
            }
        }
        if (reflected) {
            ScanResult(
                type = "xss", target = urlBase, risk = Risk.ALTO, scoreCvss = 7.0f, confidence = 85,
                protocols = listOf("xss"),
                extracted = listOf("XSS refletido em '$param'"),
                details = "XSS REFLETIDO no parâmetro '$param' (payload ${payloadUsed.take(50)}). Confirme no navegador/Termux."
            )
        } else {
            ScanResult(
                type = "xss", target = urlBase, risk = Risk.BAIXO, scoreCvss = 1.5f, confidence = 60,
                protocols = listOf("xss"),
                details = "Parâmetro '$param' não refletiu payloads testados diretamente."
            )
        }
    }

    private fun extractParams(url: String): List<Pair<String, String>> {
        val marker = url.indexOf('?')
        if (marker < 0) return emptyList()
        val query = url.substring(marker + 1)
        return query.split("&").mapNotNull { kv ->
            val idx = kv.indexOf('=')
            if (idx > 0) {
                val k = kv.substring(0, idx)
                val v = if (idx + 1 < kv.length) kv.substring(idx + 1) else ""
                k to v
            } else null
        }
    }

    private fun inject(url: String, param: String, value: String): String {
        val base = url.substringBefore("?")
        val params = extractParams(url).map { (k, v) -> k to (if (k == param) value else v) }
        val q = params.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }
        return if (params.isEmpty()) base else "$base?$q"
    }

    private fun fetch(url: String): String? = runCatching {
        val req = Request.Builder().url(url).addHeader("User-Agent", "VulnForgeAI/1.0").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful && resp.code != 405) return null
            resp.body?.string()?.take(120_000)
        }
    }.getOrNull()

    private fun extractSnippet(body: String, payload: String): String {
        val i = body.indexOf(payload)
        if (i < 0) return ""
        val start = (i - 40).coerceAtLeast(0)
        val end = (i + payload.length + 40).coerceAtMost(body.length)
        return body.substring(start, end).replace(Regex("\\s+"), " ").trim().take(160)
    }

    private fun quote(s: String) = "\"$s\""
}