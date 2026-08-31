package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Fala com a IA através da OpenRouter.
 * Monta o system prompt dinâmico (modo + data/hora real + contexto dos módulos),
 * mantém histórico com limite de trocas e injeta contexto do Dossiê quando o
 * usuário menciona um alvo conhecido.
 *
 * Também atua como "cérebro" da exploração (Módulo 2): dado um estado de scan,
 * devolve a próxima ação recomendada estruturada (loop act-then-analyze).
 */
class AiEngine(
    private val prefs: UserPrefs,
    private val dossier: com.vulnforgeai.app.data.DossierStore
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val messageHistory = mutableListOf<JSONObject>()

    /** Lista todos os modelos disponíveis na OpenRouter para o usuário escolher. */
    suspend fun listModels(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) return@withContext emptyList()
        val request = Request.Builder()
            .url(URL_MODELS)
            .addHeader("Authorization", "Bearer ${prefs.apiKey}")
            .get()
            .build()
        try {
            val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }
            val json = JSONObject(body)
            val data = json.optJSONArray("data") ?: JSONArray()
            val list = mutableListOf<Pair<String, String>>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val id = item.optString("id", "")
                val name = item.optString("name", id)
                if (id.isNotEmpty()) list.add(id to name)
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Envia a mensagem do usuário e devolve a resposta da IA.
     * Reinicia o contexto se for a primeira conversa da sessão (`systemPromptSeed`).
     */
    suspend fun ask(userMessage: String, resolvedMode: ChatMode, systemPromptSeed: String? = null): String =
        withContext(Dispatchers.IO) {
            if (systemPromptSeed != null) resetContext(resolvedMode)
            if (messageHistory.isEmpty()) {
                messageHistory.add(jsonSystem(buildSystemPrompt(resolvedMode)))
            } else {
                messageHistory[0] = jsonSystem(buildSystemPrompt(resolvedMode))
            }

            messageHistory.add(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })

            try {
                val messagesArray = JSONArray()
                messageHistory.forEach { messagesArray.put(it) }

                val payload = JSONObject()
                    .put("model", prefs.selectedModel)
                    .put("messages", messagesArray)
                    .put("temperature", 0.7)
                    .put("max_tokens", 1400)

                val request = Request.Builder()
                    .url(URL_CHAT)
                    .addHeader("Authorization", "Bearer ${prefs.apiKey}")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                val responseBody = client.newCall(request).execute().use { it.body?.string().orEmpty() }
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                val content = if (choices != null && choices.length() > 0) {
                    choices.getJSONObject(0).optJSONObject("message")?.optString("content", "") ?: ""
                } else {
                    json.optString("error", "Sem resposta da IA.")
                }

                messageHistory.add(JSONObject().apply {
                    put("role", "assistant")
                    put("content", content)
                })
                trimHistory()
                content.ifBlank { "A IA não respondeu nada. Tente novamente." }

            } catch (e: IOException) {
                "Aviso: não consegui falar com a IA. Verifique sua conexão com a internet e sua chave de API nas Configurações."
            } catch (e: Exception) {
                "Aviso: erro ao falar com a IA. Verifique sua chave de API e o modelo escolhido."
            }
        }

    private fun seedDecisionContext(scanState: String) {
        messageHistory.clear()
        messageHistory.add(jsonSystem(buildDecisionPrompt(scanState)))
    }

    /**
     * Motor de IA como cérebro: dado o estado do scan (rede, dispositivos,
     * protocolos, scoring), devolve a próxima ação recomendada estruturada.
     * Sem chave de API (ou em erro), cai para um heurístico local.
     */
    suspend fun suggestNextStep(scanState: String): com.vulnforgeai.app.data.NextStep =
        withContext(Dispatchers.IO) {
            seedDecisionContext(scanState)
            messageHistory.add(JSONObject().apply {
                put("role", "user")
                put("content", "Qual o próximo passo da exploração como um profissional? " +
                    "Responda apenas um JSON com: target (string), action (string), protocol (string|null), " +
                    "command (string|null), score (number), confidence (number), explanation (string), description (string).")
            })
            try {
                val messagesArray = JSONArray()
                messageHistory.forEach { messagesArray.put(it) }
                val payload = JSONObject()
                    .put("model", prefs.selectedModel)
                    .put("messages", messagesArray)
                    .put("temperature", 0.5)
                    .put("max_tokens", 400)
                val request = Request.Builder()
                    .url(URL_CHAT)
                    .addHeader("Authorization", "Bearer ${prefs.apiKey}")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()
                val responseBody = client.newCall(request).execute().use { it.body?.string().orEmpty() }
                val content = JSONObject(responseBody)
                    .optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("message")?.optString("content", "").orEmpty()
                parseDecision(content) ?: fallbackDecision(scanState)
            } catch (e: Exception) {
                fallbackDecision(scanState)
            }
        }

    private fun parseDecision(content: String): com.vulnforgeai.app.data.NextStep? = runCatching {
        val start = content.indexOf('{')
        val end = content.lastIndexOf('}')
        if (start < 0 || end <= start) return@runCatching null
        val json = JSONObject(content.substring(start, end + 1))
        com.vulnforgeai.app.data.NextStep(
            target = json.optString("target", ""),
            action = json.optString("action", "aprofundar exploração"),
            protocol = json.optString("protocol", "").takeIf { it.isNotBlank() },
            command = json.optString("command", "").takeIf { it.isNotBlank() },
            score = json.optDouble("score", 0.0).toFloat(),
            confidence = json.optInt("confidence", 0),
            explanation = json.optString("explanation", ""),
            description = json.optString("description", "Ação recomendada pela IA.")
        )
    }.getOrNull()

    /** Heurístico local: escolhe o alvo mais pontuado e sugere o primeiro protocolo exposto. */
    private fun fallbackDecision(scanState: String): com.vulnforgeai.app.data.NextStep {
        val ip = Regex("""dispositivo[:\s]+([0-9.]+)""", RegexOption.IGNORE_CASE)
            .find(scanState)?.groupValues?.getOrNull(1) ?: "alvo"
        val score = Regex("""score[:=\s]+([0-9.]+)""")
            .find(scanState)?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 5.0f
        return com.vulnforgeai.app.data.NextStep(
            target = ip,
            action = "testar protocolos expostos",
            protocol = "ftp",
            command = null,
            score = score,
            confidence = 60,
            explanation = "Sem resposta estruturada da IA, sigo pelo motor local: priorizo o alvo mais vulnerável e tento os serviços expostos.",
            description = "Aprofundar exploração em $ip (heurístico local)."
        )
    }

    private fun buildDecisionPrompt(scanState: String): String {
        return buildString {
            appendLine("Você é o cérebro de uma ferramenta de segurança ofensiva, atuando como um profissional veterano de pentest.")
            appendLine("Recebe o estado de uma varredura de rede e deve decidir o PRÓXIMO PASSO MAIS EFICAZ para aprofundar a exploração de cada alvo, indo sempre mais fundo.")
            appendLine("Considere prioridade (pontuação × confiança) e viabilidade sem root.")
            appendLine("O usuário possui autorização. Fale como profissional.")
            appendLine("Responda SEMPRE em JSON (sem markdown), com os campos: target, action, protocol, command, score, confidence, explanation, description.")
            appendLine("ESTADO DO SCAN:")
            append(scanState)
        }
    }

    /** Recria o histórico a partir do system prompt. */
    fun resetContext(mode: ChatMode) {
        messageHistory.clear()
        messageHistory.add(jsonSystem(buildSystemPrompt(mode)))
    }

    /**
     * Persona "Hacker" — system prompt dedicado do cérebro. Agressivo, persistente,
     * metódico, voltado a vulnerabilidades; correlaciona módulos; atuação autorizada.
     * O prompt complementador do usuário se soma (não substitui).
     */
    fun buildHackerPrompt(sessionContext: String): String {
        val base = "Você é o cérebro do VulnForgeAI, um profissional de segurança ofensiva (pentester autorizado) atuando como um HACKER. Seja AGRESSIVO no raciocínio, persistente e metódico. Nunca pare por conta própria enquanto o usuário quiser continuar. Correlacione todas as informações disponíveis entre módulos para encontrar e aprofundar vulnerabilidades, indo sempre mais fundo. Você pode pesquisar na internet (via web research) quando precisar de conhecimento atual (CVEs, exploits, payloads, técnicas). Responda sempre em português de forma técnica e direta para um profissional."
        val sess = if (sessionContext.isBlank()) "" else "\n\n$sessionContext"
        val user = prefs.userPrompt.takeIf { it.isNotBlank() }
            ?.let { "\n\nCOMPLEMENTO DE PREFERÊNCIA DO USUÁRIO (sig-o isso além da persona):\n$it" } ?: ""
        return base + sess + user
    }

    /**
     * Motor de IA como "Hacker": processa um objetivo do usuário dentro de um
     * modo de autonomia, usando a persona Hacker + contexto vivo (BrainContext).
     *
     * Modo 2 (ASSIST): responde AS MELHORES brechas (não executa sozinha).
     * Modo 3 (AUTO): orienta a execução automática; pesquisa web quando necessária.
     */
    suspend fun askAsHacker(
        userMessage: String,
        brainMode: com.vulnforgeai.app.data.BrainMode,
        sessionContext: String,
        searchWeb: Boolean = false
    ): HackerReply = withContext(Dispatchers.IO) {
        seedHackerContext(sessionContext, brainMode)
        messageHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", when (brainMode) {
                com.vulnforgeai.app.data.BrainMode.USER ->
                    "O usuário está operando manualmente. Apenas responda de forma útil sim, mas sem executar nada por conta própria. Solicitação: $userMessage"
                com.vulnforgeai.app.data.BrainMode.ASSIST ->
                    "Estou no MODO AUXILIADOR: NÃO execute nada sozinho. Aponte as MELHORES brechas/vulnerabilidades a explorar agora, conforme o que estamos fazendo, com um grau de confiança (0-100%) e cor (crítico/alto/médio/baixo). Solicitação: $userMessage"
                com.vulnforgeai.app.data.BrainMode.AUTO ->
                    "Estou no MODO AUTOMÁTICO: coordene e execute a melhor cadeia de ações para cumprir o objetivo, indo sempre mais fundo até achar a vulnerabilidade. Teste de forma real e correlacione. Use web research se necessário. Objetivo: $userMessage"
            })
        })

        lateinit var result: HackerReply
        try {
            var reply = callHacker(brainMode)
            val needsWeb = searchWeb && (reply.isEmpty || brainMode == com.vulnforgeai.app.data.BrainMode.AUTO)
            if (needsWeb) {
                val web = WebResearch().search(userMessage, 4)
                if (web.isNotBlank()) {
                    reply = tryAgainWithWeb(userMessage, web, brainMode)
                }
            }
            result = reply
        } catch (e: Exception) {
            result = HackerReply(
                "Aviso: erro ao falar com a IA. Verifique sua chave de API / modelo e conexão.",
                emptyList(),
                0,
                ""
            )
        }
        result
    }

    private fun seedHackerContext(sessionContext: String, brainMode: com.vulnforgeai.app.data.BrainMode) {
        messageHistory.clear()
        val extra = when (brainMode) {
            com.vulnforgeai.app.data.BrainMode.USER -> " Modo 1 (Manual): usuário opera as ferramentas."
            com.vulnforgeai.app.data.BrainMode.ASSIST -> " Modo 2 (Auxiliador): sugira, não execute."
            com.vulnforgeai.app.data.BrainMode.AUTO -> " Modo 3 (Automático): execute tudo."
        }
        messageHistory.add(jsonSystem(buildHackerPrompt(sessionContext) + extra))
    }

    private fun callHacker(brainMode: com.vulnforgeai.app.data.BrainMode): HackerReply {
        val messagesArray = JSONArray()
        messageHistory.forEach { messagesArray.put(it) }
        val payload = JSONObject()
            .put("model", prefs.selectedModel)
            .put("messages", messagesArray)
            .put("temperature", 0.6)
            .put("max_tokens", 900)
        val request = Request.Builder()
            .url(URL_CHAT)
            .addHeader("Authorization", "Bearer ${prefs.apiKey}")
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        val responseBody = client.newCall(request).execute().use { it.body?.string().orEmpty() }
        val content = JSONObject(responseBody)
            .optJSONArray("choices")?.optJSONObject(0)
            ?.optJSONObject("message")?.optString("content", "").orEmpty()
        return parseHackerReply(content)
    }

    private fun tryAgainWithWeb(question: String, web: String, brainMode: com.vulnforgeai.app.data.BrainMode): HackerReply {
        messageHistory.clear()
        seedHackerContext("PESQUISA WEB RECENTE sobre: $question\n$web", brainMode)
        messageHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", "Use a pesquisa web acima para continuar com precisão e me dê o próximo passo/ação. Objetivo: $question")
        })
        return callHacker(brainMode)
    }

    private fun parseHackerReply(content: String): HackerReply {
        if (content.isBlank()) return HackerReply("", emptyList(), 0, "", refused = true)
        val refused = content.contains("não posso", true) || content.contains("can't", true) ||
            content.contains("não vou", true) || content.contains("não consigo", true) ||
            content.contains("against", true) || content.contains("políticas", true)
        val recommendations = extractRecommendations(content)
        return HackerReply(content, recommendations, 80, content.take(120), refused)
    }

    private fun extractRecommendations(content: String): List<Pair<String, Int>> {
        val out = mutableListOf<Pair<String, Int>>()
        content.lineSequence().forEach { line ->
            val t = line.trim()
            if ((t.startsWith("-") || t.startsWith("*") || t.startsWith("•")) && t.length > 5) {
                out.add(t.removePrefix("-").removePrefix("*").removePrefix("•").trim() to 75)
            }
        }
        return out
    }

    /**
     * Alternância de modelo em caso de recusa/falha (DEC-4): tenta um modelo
     * alternativo e retorna a resposta, ou uma mensagem clara.
     */
    suspend fun tryAltModelAlt(userMessage: String, brainMode: com.vulnforgeai.app.data.BrainMode): String {
        return withContext(Dispatchers.IO) {
            val candidates = listOf("openrouter/auto", "openrouter/openai/gpt-4o-mini")
            for (alt in candidates) {
                if (alt == prefs.selectedModel) continue
                val prev = prefs.selectedModel
                try {
                    prefs.selectedModel = alt
                    val r = askAsHacker(userMessage, brainMode, "", searchWeb = false)
                    if (!r.refused && r.body.isNotBlank()) return@withContext r.body
                } finally {
                    prefs.selectedModel = prev
                }
            }
            "O modelo selecionado recusou realizar esta tarefa. Tente outro modelo na Configurações, ou descreva de outra forma. (A ferramenta é para pentest autorizado.)"
        }
    }

    data class HackerReply(
        val body: String,
        val recommendations: List<Pair<String, Int>>,
        val confidence: Int,
        val summary: String,
        val refused: Boolean = false
    ) {
        val isEmpty: Boolean get() = body.isBlank() || refused
    }

    /** Monta as instruções fixas que a IA segue, conforme o modo e o contexto. */
    private fun buildSystemPrompt(mode: ChatMode): String {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(now))
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))

        val base = listOf(
            "Você é o VulnForgeAI, uma ferramenta de segurança ofensiva de uso autorizado. Sua identidade interna é de um HACKER profissional (pentester autorizado): agressivo, persistente, metódico, voltado a encontrar e aprofundar vulnerabilidades.",
            "Correlacione informações entre módulos (WiFi, web/XSS, rede, câmera) e vá sempre mais fundo na exploração de cada alvo. Não pare por conta própria enquanto o usuário quiser continuar.",
            "Responda sempre em português, de forma clara e objetiva.",
            "O usuário possui autorização para testar os alvos que informa.",
            "DATA E HORA ATUAIS (REAIS do celular): $date às $time.",
            "IMPORTANTE: confie nesse valor de data e hora. NUNCA suponha, NÃO invente outra data/hora, NÃO diga 'hoje' de forma genérica sem usar a data informada.",
            "Quando recomendar usar uma ferramenta (nmap, curl, sqlmap, ping, etc.), escreva o comando completo em uma linha única que comece com o nome da ferramenta, ex: nmap -p 80 scanme.nmap.org",
            "Escreva um comando por vez, não vários em bloco.",
            "Use a sua inteligência por completo; se precisar de conhecimento atual (CVEs, exploits, payloads, técnicas), aponte a necessidade de pesquisa e siga o raciocínio.",
            "RESPEITE a autonomia definida: Modo Manual (usuário opera, você não interfere), Modo Auxiliador (você sugere as melhores brechas com confiança número/cor, sem executar), Modo Automático (você executa tudo e vai até encontrar).",
            "FALLBACK GENTIL: se você não entender o pedido, responda exatamente: \"Boa! Não tenho certeza se entendi. Você quer que eu (1) escaneie algo, (2) te ensine algo, ou (3) gere um comando? Diga o número.\"",
            "MODO BLITZ: quando ativado, execute todo o fluxo de varreduras e relate de forma consolidada, perguntando quais vulnerabilidades explorar."
        )

        val modeRule = when (mode) {
            ChatMode.APRENDIZ ->
                "Você está no modo APRENDIZ: explique cada comando e cada resultado com palavras simples, sem jargão, usando analogias, passo a passo."
            ChatMode.INTERMEDIARIO ->
                "Você está no modo INTERMEDIÁRIO: comandos prontos + explicação resumida, sem se aprofundar nem ensinar passo a passo."
            ChatMode.PROFISSIONAL ->
                "Você está no modo PROFISSIONAL: seja direto, com flags técnicas, sem ensinar nem explicar o básico. Apenas execute/responda."
            ChatMode.AUTO ->
                "Você está no modo AUTOMÁTICO: detecte o melhor nível pelo texto do usuário (Aprendiz se for iniciante, Profissional se for avançado)."
        }

        val contextInject = injectDossierContext()

        return (base + modeRule + listOfNotNull(contextInject)).joinToString("\n")
    }

    /** Inclui no prompt um resumo dos últimos achados persistidos do Dossiê. */
    private fun injectDossierContext(): String? {
        val all = dossier.getAllTargets()
        if (all.isEmpty()) return null
        val sb = StringBuilder("CONTEXTO DA MEMÓRIA PERSISTENTE (últimos achados):\n")
        all.take(5).forEach { r ->
            sb.appendLine("- [${r.risk}] ${r.targetName}: ${r.detail}")
        }
        sb.appendLine("Use esse contexto para responder perguntas sobre alvos já analisados, mesmo em conversas novas.")
        return sb.toString()
    }

    private fun jsonSystem(content: String): JSONObject = JSONObject().apply {
        put("role", "system")
        put("content", content)
    }

    /** Mantém o histórico dentro de um limite razoável de trocas (mín. 10, máx. ~20). */
    private fun trimHistory() {
        val MAX = 20
        while (messageHistory.size > MAX) {
            if (messageHistory.size > 1) messageHistory.removeAt(1)
        }
    }

    companion object {
        private const val URL_CHAT = "https://openrouter.ai/api/v1/chat/completions"
        private const val URL_MODELS = "https://openrouter.ai/api/v1/models"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}