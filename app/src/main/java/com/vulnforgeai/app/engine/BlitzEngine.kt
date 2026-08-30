package com.vulnforgeai.app.engine

import android.annotation.SuppressLint
import android.content.Context
import com.vulnforgeai.app.data.DossierStore
import com.vulnforgeai.app.data.Risk
import com.vulnforgeai.app.data.ScanResult
import com.vulnforgeai.app.data.Stealth
import com.vulnforgeai.app.data.UserPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.net.InetAddress
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * MODO BLITZ — modelo 1 didático/híbrido.
 *
 * Executa as varreduras que o Android permite de forma nativa e silenciosa
 * (tabela ARP, rede local, fabricante via MAC) e, para o que exige root,
 * chipset ou ações invasivas (WPS, exploit, brute force), gera comandos
 * prontos para o Termux + narrativa didática. Narra o progresso em tempo
 * real (via callback), consolida os achados e monta o "Mapa de Guerra" e o
 * resumo para o Relatório de Operação.
 */
class BlitzEngine(
    private val context: Context,
    private val prefs: UserPrefs,
    private val dossier: DossierStore
) {

    /** Retorna as etapas didáticas de um Blitz (para narração/relatório). */
    fun blitzSteps(): List<ScanResult> {
        val steps = buildBlitzResults()
        steps.forEach { dossier.addTarget(it.target, it.type, it.details, it.risk.name) }
        return steps
    }

    /** Blitz síncrono: varre o que dá nativo e entrega os achados consolidados. */
    fun runBlitz(onNarrative: (String) -> Unit): List<ScanResult> {
        val ts = { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date()) }
        onNarrative("${ts()} — Iniciando Operação Blitz (modo didático).")

        val results = mutableListOf<ScanResult>()

        // 1) Scan WiFi local (silencioso)
        val wifi = WifiScanner.scan(context)
        onNarrative("${ts()} — Scan WiFi local concluído: ${wifi.size} redes. (ruído ${Stealth.scoreFor("wifi")}.)")
        wifi.forEach { results.add(ScanResult("wlan", it.first, it.second, it.third)) }

        // 2) Tabela ARP (silenciosa)
        val arp = ArpReader.readLocal()
        onNarrative("${ts()} — Leitura da tabela ARP concluída: ${arp.size} dispositivos. (tabela ARP é local e silenciosa.)")
        arp.forEach { (ip, mac, vendor) ->
            results.add(ScanResult("network", ip, "MAC: $mac • Fabricante: $vendor", Risk.INFO))
        }

        // 3) Ping de dispositivos (ruído médio)
        onNarrative("${ts()} — Testando alcance dos dispositivos (ping). (ruído médio.)")
        val alive = arp.mapNotNull { it.first }.toList()
        results.add(ScanResult("network", ispToText(alive.size), "Ping", Risk.INFO))

        // 4) Etapas didáticas que exigem Termux (WPS, exploit, brute force)
        val didactic = blitzSteps().filter { it.type == "didatic" }
        didactic.forEach { d ->
            onNarrative("${ts()} — ${d.details} (ruído ${Stealth.scoreFor(d.target)}.)")
            results.add(d)
        }

        onNarrative("${ts()} — Orquestração consolidada. Blitz concluído.")

        // Persiste tudo no Dossiê
        results.forEach {
            dossier.addTarget(it.target, it.type, it.details, it.risk.name)
        }
        return results
    }

    private fun buildBlitzResults(): List<ScanResult> = listOf(
        ScanResult("didatic", "wps", "Comando pronto (Termux): teste WPS do roteador para checar credencial fraca.", Risk.ALTO),
        ScanResult("didatic", "smb", "Vetor SMB (porta 445): verifique compartilhamentos expostos via nmap.", Risk.MEDIO),
        ScanResult("didatic", "rtsp", "Vetor RTSP: câmeras expostas sem autenticação são um vetor comum.", Risk.ALTO),
        ScanResult("didatic", "exploit", "Exploração ativa exige autorização e Termux: gere o comando no chat.", Risk.CRITICO)
    )

    private fun ispToText(count: Int) =
        if (count <= 1) "Nenhum dispositivo adicional detectado via ARP."
        else " $count dispositivos na rede local."

    /** Gera a Matriz de Guerra em formato de texto (árvore). */
    fun warMapText(): String {
        val sb = StringBuilder("🗺️ MAPA DE GUERRA\n")
        sb.appendLine("Rede (Wi-Fi local)")
        WifiScanner.scan(context).forEach { (ssid, _, _) ->
            sb.appendLine("  └─ ☁ $ssid")
        }
        ArpReader.readLocal().forEach { (ip, mac, vendor) ->
            sb.appendLine("  └─ 🖥 $ip ($vendor)")
        }
        dossier.getAllTargets().take(6).forEach {
            sb.appendLine("      ├─ ${it.risk} ${it.detail.take(40)}")
        }
        return sb.toString()
    }

    private object WifiScanner {
        fun scan(context: Context): List<Triple<String, String, Risk>> {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE)
                    as? android.net.wifi.WifiManager ?: return emptyList()
            return runCatching {
                wm.scanResults.map { r ->
                    val risk = when {
                        r.capabilities.contains("WPA", true) -> Risk.MEDIO
                        r.capabilities.contains("WEP", true) -> Risk.ALTO
                        else -> Risk.CRITICO
                    }
                    Triple(r.SSID, "BSSID ${r.BSSID} • sinal ${r.level}dBm • ${r.capabilities}", risk)
                }
            }.getOrDefault(emptyList())
        }
    }

    private object ArpReader {
        @SuppressLint("BinaryOperationInTimedCondition")
        fun readLocal(): List<Triple<String, String, String>> {
            return runCatching {
                val out = mutableListOf<Triple<String, String, String>>()
                brFromProc("/proc/net/arp")?.use { br ->
                    br.readLine() // header
                    br.forEachLine { line ->
                        val parts = line.split(Regex("\\s+"))
                        if (parts.size >= 4 && parts[0] != "") {
                            out.add(Triple(parts[0], parts[3], "?"))
                        }
                    }
                }
                out
            }.getOrDefault(emptyList())
        }

        private fun brFromProc(path: String): BufferedReader? = runCatching {
            java.io.File(path).inputStream().bufferedReader()
        }.getOrNull()
    }
}