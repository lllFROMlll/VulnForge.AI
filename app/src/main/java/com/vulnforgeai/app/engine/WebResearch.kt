package com.vulnforgeai.app.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Busca na internet ("web research") para o cérebro quando ele travar ou
 * precisar de conhecimento atual (CVEs, exploits, payloads, técnicas).
 *
 * Estratégia: se o modelo ativo expor tooluse/web, a chamada é delegada à IA
 * (ver AiEngine); caso contrário, este componente consulta um endpoint de
 * busca de apoio (DuckDuckGo lite) e devolve texto útil para injetar no
 * contexto. Nunca é obrigatório: em falha retorna uma indicação clara.
 */
class WebResearch {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Busca por um termo e devolve até [maxResults] trechos de texto resumidos.
     * Pode retornar vazio em erro/offline.
     */
    suspend fun search(query: String, maxResults: Int = 5): String = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext ""
        runCatching {
            val url = "https://lite.duckduckgo.com/lite/?q=" + java.net.URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build()
            val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
            val snippets = extractResults(body, maxResults)
            if (snippets.isEmpty()) "Pesquisa web por '$query' não retornou resultados estruturados."
            else snippets.joinToString("\n")
        }.getOrElse {
            "Pesquisa web por '$query' indisponível (sem conexão ou limite). Sigo com conhecimento da sessão."
        }
    }

    private fun extractResults(html: String, max: Int): List<String> {
        // duckduckgo lite: linhas com links de resultado; pegamos texto próximo a "result-link".
        val out = mutableListOf<String>()
        val re = Regex("""result-link[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
        re.findAll(html).forEach { m ->
            var t = m.groupValues[1].trim()
            t = t.replace(Regex("<[^>]+>"), "").replace(Regex("\\s+"), " ").trim()
            if (t.isNotEmpty()) out.add(t)
        }
        return out.take(max)
    }
}