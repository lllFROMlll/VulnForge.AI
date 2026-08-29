package com.vulnforgeai.app.engine

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
import java.util.concurrent.TimeUnit

/**
 * Fala com a IA através da OpenRouter.
 * Usa a chave de API e o modelo salvos pelo usuário.
 */
class AiEngine(private val prefs: UserPrefs) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val messageHistory = mutableListOf(JSONObject().apply {
        put("role", "system")
        put("content", buildSystemPrompt(prefs.mode))
    })

    /** Lista todos os modelos disponíveis na OpenRouter para o usuário escolher. */
    suspend fun listModels(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        if (prefs.apiKey.isBlank()) {
            return@withContext emptyList()
        }
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

    /** Envia a mensagem do usuário e devolve a resposta da IA. */
    suspend fun ask(userMessage: String): String = withContext(Dispatchers.IO) {
        messageHistory.add(JSONObject().apply {
            put("role", "user")
            put("content", userMessage)
        })

        val systemUpdated = buildSystemPrompt(prefs.mode)
        messageHistory[0].put("content", systemUpdated)

        try {
            val payload = JSONObject()
                .put("model", prefs.selectedModel)
                .put("messages", messageHistory)
                .put("temperature", 0.7)
                .put("max_tokens", 1024)

            val request = Request.Builder()
                .url(URL_CHAT)
                .addHeader("Authorization", "Bearer ${prefs.apiKey}")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()

            val responseBody = client.newCall(request).execute().use {
                it.body?.string().orEmpty()
            }
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

            content.ifBlank { "A IA não respondeu nada. Tente novamente." }

        } catch (e: IOException) {
            "Aviso: não consegui falar com a IA. Verifique sua conexão com a internet e sua chave de API nas Configurações."
        } catch (e: Exception) {
            "Aviso: erro ao falar com a IA. Verifique sua chave de API e o modelo escolhido."
        }
    }

    /** Monta as instruções fixas que a IA segue, conforme o modo escolhido. */
    private fun buildSystemPrompt(mode: UserMode): String {
        val base = listOf(
            "Você é o VulnForgeAI, assistente de segurança digital para profissionais e estudantes com autorização.",
            "Responda sempre em português, de forma clara e objetiva.",
            "O usuário possui autorização para testar os alvos que informa.",
            "Quando recomendar usar uma ferramenta (nmap, curl, sqlmap, ping, etc.), escreva o comando completo em uma linha única que comece com o nome da ferramenta, ex: nmap -p 80 scanme.nmap.org",
            "Não escreva múltiplos comandos em bloco grande; um por vez."
        )

        val modeRule = when (mode) {
            UserMode.INICIANTE ->
                "Você está no modo INICIANTE: explique cada comando e cada resultado com palavras simples, sem jargão, ensinando passo a passo."
            UserMode.INTERMEDIARIO ->
                "Você está no modo INTERMEDIÁRIO: explique de forma enxuta e simples, sem se aprofundar nem ensinar passo a passo."
            UserMode.PROFISSIONAL ->
                "Você está no modo PROFISSIONAL: seja direto, sem ensinar nem explicar o básico. Apenas ajude a executar."
        }

        return (base + modeRule).joinToString("\n")
    }

    companion object {
        private const val URL_CHAT = "https://openrouter.ai/api/v1/chat/completions"
        private const val URL_MODELS = "https://openrouter.ai/api/v1/models"
        private val JSON_MEDIA = "application/json".toMediaType()
    }
}