package com.vulnforgeai.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.audio.Narration
import com.vulnforgeai.app.audio.VoiceInput
import com.vulnforgeai.app.audio.VoicePickerScreen
import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.ConversationStore
import com.vulnforgeai.app.data.DossierStore
import com.vulnforgeai.app.data.Stealth
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.AiEngine
import com.vulnforgeai.app.engine.BlitzEngine
import com.vulnforgeai.app.engine.IntentParser
import com.vulnforgeai.app.engine.ToolExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChatScreen : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tabsRecycler: RecyclerView
    private lateinit var modeBar: TextView
    private lateinit var inputField: EditText
    private lateinit var sendButton: Button
    private lateinit var voiceButton: Button

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    private lateinit var prefs: UserPrefs
    private lateinit var aiEngine: AiEngine
    private lateinit var toolExecutor: ToolExecutor
    private lateinit var blitzEngine: BlitzEngine
    private lateinit var dossier: DossierStore
    private lateinit var conversationStore: ConversationStore
    private lateinit var narration: Narration
    private lateinit var voiceInput: VoiceInput

    private var currentConversationId: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        prefs = UserPrefs(this)
        dossier = DossierStore(this)
        conversationStore = ConversationStore(this)
        aiEngine = AiEngine(prefs, dossier)
        blitzEngine = BlitzEngine(this, prefs, dossier)
        toolExecutor = ToolExecutor(this)
        narration = Narration(this, prefs)
        voiceInput = VoiceInput(this)

        recyclerView = findViewById(R.id.chat_recycler)
        tabsRecycler = findViewById(R.id.conversation_tabs)
        modeBar = findViewById(R.id.chat_mode_bar)
        inputField = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.chat_send)
        voiceButton = findViewById(R.id.voice_button)

        recyclerView.layoutManager = LinearLayoutManager(this)
        tabsRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        adapter = ChatAdapter(messages) { onSpeak(it) }
        recyclerView.adapter = adapter

        ensureDossieCleanup()
        ensureConversation()
        updateModeBar()

        // Saudação com contexto do Dossiê (perfil evolutivo)
        val dossierSummary = dossier.getAllTargets()
        if (dossierSummary.isNotEmpty()) {
            val recent = dossierSummary.first()
            addMessage("Bem-vindo de volta.", "No seu último uso vimos: ${recent.targetName} — ${recent.detail.take(60)}. Quer verificar de novo?", isUser = false)
        } else {
            addMessage("VulnForgeAI", "Olá! Sou seu assistente de segurança.\n\nTente: 'escaneia 192.168.1.1', 'modo blitz', 'testa segurança do WiFi' ou 'analisa https://exemplo.com'.\nSempre com autorização sobre os alvos.", isUser = false)
        }

        sendButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) { inputField.setText(""); sendMessage(text) }
        }

        voiceButton.setOnClickListener {
            if (usesMicPermission()) {
                voiceInput.start(
                    onResult = { runOnUiThread { inputField.setText(it); sendMessage(it) } },
                    onError = { runOnUiThread { addMessage("VulnForgeAI", it, isUser = false) } }
                )
            } else {
                requestMicPermission()
            }
        }

        findViewById<Button>(R.id.quick_wifi).setOnClickListener { sendMessage("modo blitz wifi") }
        findViewById<Button>(R.id.quick_blitz).setOnClickListener { runBlitz() }
        findViewById<Button>(R.id.quick_target).setOnClickListener {
            addMessage("Alvo", "Hmm, me diga o alvo (IP ou domínio).", isUser = false)
        }
        findViewById<Button>(R.id.quick_learn).setOnClickListener {
            startActivity(android.content.Intent(this, LearningScreen::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        updateModeBar()
        refreshTabs()
    }

    override fun onDestroy() {
        super.onDestroy()
        narration.shutdown()
        voiceInput.destroy()
    }

    private fun updateModeBar() {
        modeBar.text = "Modo: ${prefs.chatMode.label}  •  Modelo: ${prefs.selectedModel}"
    }

    private fun ensureDossieCleanup() {
        // Auto-destruição conforme o prazo configurável (padrão 30 dias).
        dossier.cleanup(prefs.memoryExpiryDays)
        conversationStore.cleanup(prefs.memoryExpiryDays)
    }

    private fun ensureConversation() {
        val all = conversationStore.all()
        if (all.isEmpty()) {
            currentConversationId = conversationStore.create("Nova conversa")
        } else {
            currentConversationId = all.first().id
        }
        refreshTabs()
    }

    private fun refreshTabs() {
        val all = conversationStore.all()
        ConversationTabsAdapter(
            all,
            currentConversationId,
            onSelect = { id ->
                currentConversationId = id
                conversationStore.touch(id)
                refreshTabs()
                addMessage("Abas", "Conversa trocada. Como posso ajudar?", isUser = false)
            },
            onPin = { id ->
                val conv = conversationStore.getById(id) ?: return@ConversationTabsAdapter
                conversationStore.setPinned(id, !conv.isPinned)
                refreshTabs()
            },
            onDelete = { id ->
                AlertDialog.Builder(this)
                    .setTitle("Excluir conversa")
                    .setMessage("Excluir esta conversa? Os dados de alvos no Dossiê continuam salvos.")
                    .setPositiveButton("Excluir") { _, _ ->
                        conversationStore.delete(id)
                        if (currentConversationId == id) {
                            currentConversationId = conversationStore.all().firstOrNull()?.id ?: conversationStore.create("Nova conversa")
                        }
                        refreshTabs()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        ).let { tabsRecycler.adapter = it }
    }

    private fun sendMessage(text: String) {
        addMessage("Você", text, isUser = true)
        conversationStore.touch(currentConversationId)

        // Interpreta intenção
        val intent = IntentParser.parse(text)
        val resolvedMode = IntentParser.resolveMode(prefs.chatMode, text)

        // Blitz: fluxo especial
        if (intent.isBlitz) {
            runBlitz()
            return
        }

        // Stealth: avisa antes de ação ruidosa, exceto em Modo Confiante
        if (intent.isScan || intent.isExploit) {
            val stealth = Stealth.forAction(text)
            if (stealth.level.score >= 6 && !prefs.confidentMode) {
                addStealthWarning(text, resolvedMode, stealth.level.label, stealth.level.score)
                return
            }
        }

        // Modo Confiante / exploração: pede confirmação por padrão (senão direto)
        if (intent.isExploit && !prefs.confidentMode) {
            confirmExploit(text, resolvedMode)
            return
        }

        dispatchToAI(text, resolvedMode, seedForNewConversation())
    }

    private fun seedForNewConversation(): String? =
        if (currentConversationId == -1L) null else null

    private fun addStealthWarning(original: String, mode: ChatMode, levelLabel: String, score: Int) {
        addMessage(
            "Stealth",
            "⚡ Esta varredura é $levelLabel (ruído $score/10). Um administrador ou sistema de detecção pode notar.\n\nQuer que eu continue? (ou diga 'modo confiante' para ir direto)",
            isUser = false,
            type = MessageType.STEALTH
        )
        // Falaremos com a IA para continuar, mas agora usuario responde pelo input normal.
    }

    private fun confirmExploit(text: String, mode: ChatMode) {
        dispatchToAI(text, mode) // didático: a IA gera o comando/explica; sem executar propriamente
    }

    private fun dispatchToAI(text: String, mode: ChatMode, seed: String? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val aiResponse = aiEngine.ask(text, mode, seed)
                val toolMessage = toolExecutor.handle(aiResponse, prefs.mode)
                val finalResponse = if (toolMessage != null) "$aiResponse\n\n---\n$toolMessage" else aiResponse
                withContext(Dispatchers.Main) {
                    addMessage("VulnForgeAI", finalResponse, isUser = false, mode = mode)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    addMessage("VulnForgeAI", "Erro ao processar. Verifique sua chave e conexão.", isUser = false)
                }
            }
        }
    }

    private fun runBlitz() {
        val ts = { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()) }
        addMessage("Operação", "${ts()} — Iniciando Blitz (didático).", isUser = false, type = MessageType.LOG)
        CoroutineScope(Dispatchers.IO).launch {
            val results = withContext(Dispatchers.IO) { blitzEngine.runBlitz { narrate(it) } }
            withContext(Dispatchers.Main) {
                val consolidated = results.filter { !it.details.startsWith("Comando pronto") || true }
                val sb = StringBuilder("Encontrei estas vulnerabilidades/pontos:\n")
                results.forEachIndexed { i, r ->
                    sb.appendLine("${i + 1}. [${r.risk.label}] ${r.target}: ${r.details.take(80)}")
                }
                sb.appendLine("\nGostaria de explorar alguma?")
                addMessage("VulnForgeAI", sb.toString(), isUser = false)
                // Mapa de Guerra
                addMessage("Mapa de Guerra", blitzEngine.warMapText(), isUser = false, type = MessageType.WAR_MAP)
            }
        }
    }

    private fun narrate(line: String) {
        runOnUiThread { addMessage("Operação", line, isUser = false, type = MessageType.LOG) }
    }

    private fun onSpeak(msg: ChatMessage) {
        narration.speak(msg.text)
    }

    private fun addMessage(sender: String, text: String, isUser: Boolean,
                           mode: ChatMode = ChatMode.APRENDIZ,
                           type: MessageType = MessageType.NORMAL) {
        messages.add(ChatMessage(sender, text, isUser, type, mode))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun usesMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 300)
    }
}