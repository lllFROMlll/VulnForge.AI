package com.vulnforgeai.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.AiEngine
import com.vulnforgeai.app.engine.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatScreen : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var modeBar: TextView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    private lateinit var prefs: UserPrefs
    private lateinit var aiEngine: AiEngine
    private lateinit var toolExecutor: ToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        prefs = UserPrefs(this)
        aiEngine = AiEngine(prefs)
        toolExecutor = ToolExecutor(this)

        recyclerView = findViewById(R.id.chat_recycler)
        modeBar = findViewById(R.id.chat_mode_bar)
        inputField = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.chat_send)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ChatAdapter(messages)
        recyclerView.adapter = adapter

        updateModeBar()

        addMessage("VulnForgeAI", "Olá! Sou seu assistente de segurança.\n\n" +
            "Você pode me pedir para:\n" +
            "• Escanear um IP: 'escaneia 192.168.1.1'\n" +
            "• Testar WiFi: 'testa segurança do WiFi'\n" +
            "• Analisar um site: 'analisa https://exemplo.com'\n" +
            "• Explorar um servidor IPTV: 'escaneia IPTV 200.200.200.200'\n\n" +
            "Recomendo testar com a autorização e dentro de alvos que você possui.", isUser = false)

        sendButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) {
                inputField.setText("")
                sendMessage(text)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateModeBar()
    }

    private fun updateModeBar() {
        modeBar.text = "Modo: ${prefs.mode.label}  •  Modelo: ${prefs.selectedModel}"
    }

    private fun sendMessage(text: String) {
        addMessage("Você", text, isUser = true)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val aiResponse = aiEngine.ask(text)
                val toolMessage = toolExecutor.handle(aiResponse, prefs.mode)

                val finalResponse = if (toolMessage != null) {
                    "$aiResponse\n\n---\n$toolMessage"
                } else {
                    aiResponse
                }

                withContext(Dispatchers.Main) {
                    addMessage("VulnForgeAI", finalResponse, isUser = false)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMessage("VulnForgeAI",
                        "Erro ao processar. Verifique sua chave de API e sua conexão.", isUser = false)
                }
            }
        }
    }

    private fun addMessage(sender: String, text: String, isUser: Boolean) {
        messages.add(ChatMessage(sender, text, isUser))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }
}