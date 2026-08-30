package com.vulnforgeai.app.engine

import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.UserMode
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
            // Sempre reaplica o system prompt dinâmico atualizado no início.
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
                // org.json do Android não serializa List<JSONObject> diretamente;
                // montamos um JSONArray explícito para garantir o campo "messages".
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

    /** Recria o histórico a partir do system prompt. */
    fun resetContext(mode: ChatMode) {
        messageHistory.clear()
        messageHistory.add(jsonSystem(buildSystemPrompt(mode)))
    }

    /** Monta as instruções fixas que a IA segue, conforme o modo e o contexto. */
    private fun buildSystemPrompt(mode: ChatMode): String {
        val now = System.currentTimeMillis()
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(now))
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))

        val base = listOf(
            "Você é o VulnForgeAI, assistente de segurança digital para profissionais e estudantes com autorização.",
            "Responda sempre em português, de forma clara e objetiva.",
            "O usuário possui autorização para testar os alvos que informa.",
            "DATA E HORA ATUAIS (REAIS do celular): $date às $time.",
            "IMPORTANTE: confie nesse valor de data e hora. NUNCA suponha, NÃO invente outra data/hora, NÃO diga 'hoje' de forma genérica sem usar a data informada.",
            "Quando recomendar usar uma ferramenta (nmap, curl, sqlmap, ping, etc.), escreva o comando completo em uma linha única que comece com o nome da ferramenta, ex: nmap -p 80 scanme.nmap.org",
            "Escreva um comando por vez, não vários em bloco.",
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

        // Injeta contexto do Dossiê se o usuário mencionar um alvo conhecido.
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
            // Mantém o system (índice 0) e remove o mais antigo de user/assistant.
            if (messageHistory.size > 1) messageHistory.removeAt(1)
        }
    }

    companion object {
        private const val URL_CHAT = "https://openrouter.ai/api/v1/chat/completions"
        private const val URL_MODELS = "https://openrouter.ai/api/v1/models"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}