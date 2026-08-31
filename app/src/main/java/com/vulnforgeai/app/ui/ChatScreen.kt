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
import com.vulnforgeai.app.data.BrainMode
import com.vulnforgeai.app.data.ChatMode
import com.vulnforgeai.app.data.ConversationStore
import com.vulnforgeai.app.data.DossierStore
import com.vulnforgeai.app.data.Stealth
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.AiEngine
import com.vulnforgeai.app.engine.BlitzEngine
import com.vulnforgeai.app.engine.BrainContext
import com.vulnforgeai.app.engine.IntentParser
import com.vulnforgeai.app.engine.NetworkExplorer
import com.vulnforgeai.app.engine.ToolExecutor
import com.vulnforgeai.app.engine.WebExplorer
import com.vulnforgeai.app.engine.WifiAnalyzer
import com.vulnforgeai.app.engine.XssScanner
import com.vulnforgeai.app.ui.visual.SimpleVisualStage
import com.vulnforgeai.app.ui.visual.VisualStage
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
    private lateinit var wifiAnalyzer: WifiAnalyzer
    private lateinit var networkExplorer: NetworkExplorer
    private lateinit var xssScanner: XssScanner
    private lateinit var webExplorer: WebExplorer
    private lateinit var brainContext: BrainContext
    private lateinit var visualStage: VisualStage

    private val networkResult = mutableListOf<com.vulnforgeai.app.data.ScanResult>()
    private var currentConversationId: Long = -1L
    private var explorationActive = false
    private var xssActive = false

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
        wifiAnalyzer = WifiAnalyzer(this)
        networkExplorer = NetworkExplorer(wifiAnalyzer)
        brainContext = BrainContext()
        xssScanner = XssScanner()
        webExplorer = WebExplorer(xssScanner, brainContext)
        visualStage = SimpleVisualStage()

        prefs.brainMode.let { visualStage.setIdle() }

        recyclerView = findViewById(R.id.chat_recycler)
        tabsRecycler = findViewById(R.id.conversation_tabs)
        modeBar = findViewById(R.id.chat_mode_bar)
        inputField = findViewById(R.id.chat_input)
        sendButton = findViewById(R.id.chat_send)
        voiceButton = findViewById(R.id.voice_button)

        recyclerView.layoutManager = LinearLayoutManager(this)
        tabsRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        adapter = ChatAdapter(messages, onSpeak = { onSpeak(it) }, onAction = { msg, label -> onAction(msg, label) })
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

        findViewById<Button>(R.id.quick_wifi).setOnClickListener { if (!explorationActive) runNetworkExplorer() }
        findViewById<Button>(R.id.quick_blitz).setOnClickListener { runBlitz() }
        findViewById<Button>(R.id.quick_target).setOnClickListener {
            addMessage("Alvo", "Hmm, me diga o alvo (IP ou domínio).", isUser = false)
        }
        findViewById<Button>(R.id.quick_learn).setOnClickListener {
            startActivity(android.content.Intent(this, LearningScreen::class.java))
        }
        findViewById<Button>(R.id.brain_mode_button).setOnClickListener { cycleBrainMode() }
        findViewById<Button>(R.id.conversation_menu_button).setOnClickListener { showConversationMenu() }
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
        val brain = prefs.brainMode
        modeBar.text = "🧠 ${brain.icon} ${brain.label}  •  ${prefs.chatMode.label}  •  ${prefs.selectedModel}"
        findViewById<Button>(R.id.brain_mode_button).text = brain.icon
    }

    /** Alterna entre os 3 modos de autonomia do cérebro (persistido). */
    private fun cycleBrainMode() {
        val values = BrainMode.values()
        val next = values[(prefs.brainMode.ordinal + 1) % values.size]
        prefs.brainMode = next
        updateModeBar()
        addMessage("Cérebro", "Modo ${next.label} (${next.icon}) ativado." +
            if (next == BrainMode.AUTO) "\n🟢 Automático: executo tudo e posso buscar na web. Diga o objetivo." else
            if (next == BrainMode.ASSIST) "\n🤝 Auxiliador: apresento as melhores brechas, você decide." else
            "\n🖐 Manual: você opera as ferramentas, eu não interfiro.", isUser = false)
    }

    /** Menu (A2): conversas, nova conversa, excluir, fixar (até 4). */
    private fun showConversationMenu() {
        val all = conversationStore.all()
        val labels = mutableListOf<Pair<String, () -> Unit>>()
        all.forEach { conv ->
            val pin = if (conv.isPinned) "📌" else "📄"
            labels.add("$pin ${conv.title}" to {
                currentConversationId = conv.id; conversationStore.touch(conv.id); refreshTabs()
            })
        }
        labels.add("➕ Nova conversa" to {
            currentConversationId = conversationStore.create("Nova conversa"); refreshTabs()
        })
        if (all.isNotEmpty()) labels.add("🗑 Excluir conversa" to { showDeletePicker(all) })
        if (all.isNotEmpty()) labels.add("📌 Fixar como 1ª (máx $MAX_PINNED)" to { showPinPicker(all) })

        AlertDialog.Builder(this)
            .setTitle("Conversas")
            .setItems(labels.map { it.first }.toTypedArray()) { _, which -> labels[which].second() }
            .setNegativeButton("Fechar", null)
            .show()
    }

    private fun showPinPicker(all: List<ConversationStore.Conversation>) {
        val pinnedCount = all.count { it.isPinned }
        AlertDialog.Builder(this)
            .setTitle("Fixar conversa como primeira")
            .setItems(all.map { if (it.isPinned) "📌 ${it.title} (fixada)" else "📄 ${it.title}" }.toTypedArray()) { _, which ->
                val conv = all[which]
                if (!conv.isPinned && pinnedCount >= MAX_PINNED) {
                    Toast.makeText(this, "Máximo de $MAX_PINNED conversas fixadas.", Toast.LENGTH_SHORT).show()
                } else {
                    conversationStore.setPinned(conv.id, !conv.isPinned)
                    refreshTabs()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeletePicker(all: List<ConversationStore.Conversation>) {
        AlertDialog.Builder(this)
            .setTitle("Excluir conversa")
            .setItems(all.map { it.title }.toTypedArray()) { _, which ->
                val conv = all[which]
                AlertDialog.Builder(this)
                    .setMessage("Excluir '${conv.title}'? (Dossiê dos alvos continua salvo.)")
                    .setPositiveButton("Excluir") { _, _ ->
                        conversationStore.delete(conv.id)
                        if (currentConversationId == conv.id) {
                            currentConversationId = conversationStore.all().firstOrNull()?.id
                                ?: conversationStore.create("Nova conversa")
                        }
                        refreshTabs()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

        // XSS: se pediram xss e há URL, roda o scan real dentro do chat.
        val url = extractUrl(text)
        if (containsXss(text) && url != null) {
            runXss(url)
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

    /** Detecta se o pedido pede teste XSS. */
    private fun containsXss(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("xss") || t.contains("cross-site") || t.contains("script injection")
    }

    /** Extrai a primeira URL (http/https) do texto. */
    private fun extractUrl(text: String): String? {
        val m = Regex("""https?://[^\s]+""").find(text)
        return m?.value?.trimEnd(')', ',', '.', ';', '"', '\'')
    }

    /** Inicia o Módulo 3 (XSS) dentro do chat — teste real + cérebro. */
    private fun runXss(url: String) {
        if (xssActive) return
        xssActive = true
        addMessage("Operação", "🔥 Testando XSS em $url (Módulo Web)...", isUser = false, type = MessageType.LOG)
        visualStage.onTyping()
        CoroutineScope(Dispatchers.IO).launch {
            val findings = webExplorer.explore(url) { narrate(it) }
            val prioritized = webExplorer.priorize(findings)
            withContext(Dispatchers.Main) {
                prioritized.forEach { r ->
                    addMessage(
                        "XSS", r.details.take(200),
                        isUser = false, type = MessageType.DEVICE_CARD,
                        risk = r.risk, device = r
                    )
                }
                findings.forEach { dossier.addTarget(it.target, it.type, it.details, it.risk.name) }
                brainContext.addAll(findings)
                // Resumo + próximo passo pelo cérebro (respeita o modo).
                addXssSummary(prioritized)
                xssActive = false
                askBrainNextStep("Encontramos resultados de XSS em $url. Qual próximo passo?")
            }
        }
    }

    private fun addXssSummary(findings: List<com.vulnforgeai.app.data.ScanResult>) {
        val vuln = findings.filter { it.risk == com.vulnforgeai.app.data.Risk.ALTO || it.risk == com.vulnforgeai.app.data.Risk.CRITICO }
        if (vuln.isNotEmpty()) {
            addMessage(
                "Cérebro",
                "${vuln.size} possível(is) XSS refletido(s). Maior gravidade: score ${"%.1f".format(vuln.first().scoreCvss)} (conf ${vuln.first().confidence}%).",
                isUser = false
            )
        }
    }

    /** Consulta o cérebro IA (hacker) para o próximo passo, conforme o modo. */
    private fun askBrainNextStep(question: String) {
        val mode = prefs.brainMode
        if (mode == BrainMode.USER) return // modo manual: usuário decide/comanda
        CoroutineScope(Dispatchers.IO).launch {
            val ctx = brainContext.snapshot()
            val searchWeb = mode == BrainMode.AUTO
            val reply = aiEngine.askAsHacker(question, mode, ctx, searchWeb)
            withContext(Dispatchers.Main) {
                if (reply.refused) {
                    addMessage(
                        "🧠 Cérebro",
                        "O modelo atual recusou a tarefa. Tentando modelo alternativo...",
                        isUser = false, mode = ChatMode.PROFISSIONAL
                    )
                    askModelAlt(question, mode)
                } else {
                    addMessage(
                        "🧠 Cérebro (${mode.icon} ${mode.label})",
                        reply.body,
                        isUser = false,
                        type = if (mode == BrainMode.AUTO) MessageType.LOG else MessageType.NORMAL
                    )
                    if (mode == BrainMode.ASSIST && reply.recommendations.isNotEmpty()) {
                        addMessage(
                            "Brechas recomendadas",
                            reply.recommendations.joinToString("\n") { (t, c) -> "• $t (conf $c%)" },
                            isUser = false,
                            type = MessageType.PROMPT_BUTTONS,
                            actions = listOf("Próximo passo", "Continuar aprofundando")
                        )
                    }
                }
            }
        }
    }

    private fun askModelAlt(
        question: String,
        mode: BrainMode
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            val body = aiEngine.tryAltModelAlt(question, mode)
            withContext(Dispatchers.Main) {
                addMessage("🧠 Cérebro (alternativo)", body, isUser = false, mode = ChatMode.PROFISSIONAL)
            }
        }
    }

    private fun onSpeak(msg: ChatMessage) {
        narration.speak(msg.text)
    }

    /** Inicia o fluxo de exploração da rede (Módulo 2) dentro do chat. */
    private fun runNetworkExplorer() {
        if (explorationActive) return
        explorationActive = true
        networkResult.clear()
        addMessage("Operação", "🧭 Iniciando Exploração de Rede (Módulo WiFi)...", isUser = false, type = MessageType.LOG)
        CoroutineScope(Dispatchers.IO).launch {
            val findings = withContext(Dispatchers.IO) {
                networkExplorer.explore(
                    onNarrative = { narrate(it) },
                    onDevice = { dev -> addDeviceCard(dev) }
                )
            }
            networkResult.addAll(findings)
            brainContext.addAll(findings)
            val reportLines = networkExplorer.buildReportLines(findings)
            withContext(Dispatchers.Main) {
                additionSnap(reportLines)
                // Complemento #4 — movimento lateral
                val chain = networkExplorer.buildLateralChain(findings)
                chain.forEach { narrate(it) }
                explorationActive = false
                suggestNextByAI()
            }
        }
    }

    private fun addDeviceCard(dev: com.vulnforgeai.app.data.ScanResult) {
        runOnUiThread {
            addMessage(
                "Dispositivo", dev.details.take(120),
                isUser = false, type = MessageType.DEVICE_CARD,
                risk = dev.risk, device = dev
            )
        }
    }

    /** Adiciona um resumo/continuidade só se houver achados (interface limpa). */
    private fun additionSnap(lines: List<String>) {
        if (lines.isEmpty()) return
        addMessage(
            "VulnForgeAI",
            "Resumo da exploração: ${lines.size} achados priorizados por pontuação × confiança.\n" +
                lines.take(3).joinToString("\n"),
            isUser = false
        )
    }

    /** Percorre o caminho priorizado e consulta a IA (ou heurístico) p/ o próximo passo. */
    private fun suggestNextByAI() {
        val prioritized = networkExplorer.priorize(networkResult)
        if (prioritized.isEmpty()) {
            addMessage("VulnForgeAI", "Nenhum alvo vulnerável encontrado na rede no momento.", isUser = false)
            return
        }
        val best = prioritized.first()
        val state = buildScanState(prioritized, best)
        CoroutineScope(Dispatchers.IO).launch {
            val step = aiEngine.suggestNextStep(state)
            withContext(Dispatchers.Main) {
                val buttons = step.buttonLabels()
                addMessage(
                    "🧠 Cérebro (IA)", "Próximo passo recomendado:\n${step.description}\n\n" +
                        "Alvo: ${step.target} • Score ${"%.1f".format(step.score)} • Confiança ${step.confidence}%\n" +
                        step.explanation,
                    isUser = false,
                    type = MessageType.PROMPT_BUTTONS,
                    risk = com.vulnforgeai.app.data.Risk.fromScore(step.score),
                    actions = buttons,
                    step = step
                )
            }
        }
    }

    private fun buildScanState(
        findings: List<com.vulnforgeai.app.data.ScanResult>,
        best: com.vulnforgeai.app.data.ScanResult
    ): String {
        val sb = StringBuilder("Alvo prioritário: dispositivo ${best.target} | score ${"%.1f".format(best.scoreCvss)} | conf ${best.confidence}%\n")
        findings.take(8).forEachIndexed { i, f ->
            sb.appendLine("dispositivo[${i + 1}] ${f.target} | score ${"%.1f".format(f.scoreCvss)} | conf ${f.confidence}% | proto ${f.protocols.joinToString(",")} | ${f.details.take(60)}")
        }
        return sb.toString()
    }

    /** Trata toque num botão de ação da mensagem. */
    private fun onAction(msg: ChatMessage, label: String) {
        val dev = msg.device
        val step = msg.step
        when {
            label.startsWith("Detalhar") || label.endsWith("dispositivo") -> {
                val d = dev ?: return
                addMessage(d.target, d.details, isUser = false)
                val actions = (d.protocols + "Credencial padrão").distinct()
                askExploitationGate(d)
            }
            label.startsWith("Testar") && step?.protocol != null -> {
                runProtocol(step.target, listOf(step.protocol!!, "ftp"))
            }
            label.startsWith("Executar") -> {
                step?.command?.let { execCommand(it) }
            }
            label.startsWith("Tentar") -> {
                val proto = label.removePrefix("Tentar ").trim()
                dev?.let { runProtocol(it.target, listOf(proto.lowercase())) }
            }
            label.startsWith("Continuar") || label.startsWith("Próximo passo") || label.startsWith("Aprofundar") -> {
                askBrainNextStep("Continue aprofundando a exploração. Qual o próximo passo?")
            }
            else -> {
                suggestNextByAI()
            }
        }
    }

    /** Pergunta como prosseguir (gate triplo) antes de explorar um dispositivo. */
    private fun askExploitationGate(dev: com.vulnforgeai.app.data.ScanResult) {
        if (prefs.confidentMode) {
            runProtocolsAll(dev)
            return
        }
        addMessage(
            "Como quer proceder?",
            "Defina o nível de confirmação para explorar ${dev.target}:",
            isUser = false,
            type = MessageType.PROMPT_BUTTONS,
            actions = listOf("Com Stealth confirmado", "Pedir por protocolo", "Direto (sem confirmar)"),
            device = dev
        )
    }

    private fun runProtocolsAll(dev: com.vulnforgeai.app.data.ScanResult) {
        runProtocol(dev.target, dev.protocols.ifEmpty { listOf("ftp", "http", "rtsp") })
    }

    private fun runProtocol(ip: String, protocols: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            val results = withContext(Dispatchers.IO) {
                wifiAnalyzer.testProtocols(ip, protocols, allowNoConfirmation = true)
            }
            networkResult.addAll(results)
            withContext(Dispatchers.Main) {
                results.forEach { r ->
                    val markers = when {
                        r.details.contains("funcionou", true) || r.details.contains("Extraiu", true) -> "🟢 "
                        r.details.contains("aberto", true) -> "🟡 "
                        else -> "⚪ "
                    }
                    addMessage(r.target, "${markers}${r.details}", isUser = false)
                }
                // Complemento #5 em andamento: persistência no Dossiê
                results.forEach { dossier.addTarget(it.target, it.type, it.details, it.risk.name) }
            }
        }
    }

    private fun execCommand(command: String) {
        addMessage("VulnForgeAI", toolExecutor.handle("$command", prefs.mode) ?: "Comando enviado.", isUser = false)
    }

    private fun addMessage(
        sender: String, text: String, isUser: Boolean,
        mode: ChatMode = ChatMode.APRENDIZ,
        type: MessageType = MessageType.NORMAL,
        risk: com.vulnforgeai.app.data.Risk = com.vulnforgeai.app.data.Risk.INFO,
        actions: List<String> = emptyList(),
        device: com.vulnforgeai.app.data.ScanResult? = null,
        step: com.vulnforgeai.app.data.NextStep? = null
    ) {
        messages.add(ChatMessage(sender, text, isUser, type, mode, risk, actions = actions, device = device, step = step))
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.scrollToPosition(messages.size - 1)
    }

    private fun usesMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 300)
    }

    companion object {
        private const val MAX_PINNED = 4
    }
}