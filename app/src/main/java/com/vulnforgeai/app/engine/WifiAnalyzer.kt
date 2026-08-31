package com.vulnforgeai.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import com.vulnforgeai.app.data.Risk
import com.vulnforgeai.app.data.ScanResult
import java.io.BufferedReader
import java.net.Socket

/**
 * Motor técnico do Módulo 2 (WiFi / rede local). Executa de verdade, sem root:
 * scan WiFi, análise de criptografia, descoberta por ARP, fingerprint por
 * OUI (MAC) + portas, e tentativas lógicas/silenciosas de protocolo
 * (SMB/FTP/HTTP/Telnet/RTSP) com credencial padrão por fabricante.
 *
 * Tudo é silencioso (handshake/banner/banner grab) e nunca executa força
 * bruta externa. WPS ativo é tratado didaticamente no Termux (sem root).
 */
class WifiAnalyzer(private val context: Context) {

    /** Redes WiFi próximas com análise de criptografia e risco. */
    @SuppressLint("MissingPermission")
    fun scanNetworks(): List<ScanResult> {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return emptyList()
        return runCatching {
            wm.scanResults.map { r ->
                val (risk, score, conf) = cryptoRisk(r.capabilities)
                ScanResult(
                    type = "wlan",
                    target = r.SSID,
                    details = buildCryptoDetail(r.capabilities, r.level, r.frequency),
                    risk = risk,
                    scoreCvss = score,
                    confidence = conf
                )
            }
        }.getOrDefault(emptyList())
    }

    /** Explicação didática + pontos fracos de cada criptografia. */
    fun analyzeCrypto(capabilities: String): String {
        return when {
            capabilities.contains("WPA3", true) ->
                "WPA3 (SAE): o padrão mais forte atual. Resiste a brute force offline de handshake. " +
                    "Pontos fracos: exige suporte do roteador e do dispositivo; algumas implementações antigas têm falhas de downgrade (dragonblood)."
            capabilities.contains("WPA2", true) ->
                "WPA2 (AES/CCMP): forte, mas vulnerável a KRACK e à captura de handshake para brute force offline " +
                    "se a senha for fraca. Uma senha curta/comum (ex.: 12345678, senha) é quebrável via dicionário."
            capabilities.contains("WPA", true) ->
                "WPA1 (TKIP): legado e fraco. Usa TKIP (RC4) que já foi comprometido (Beck-Tews/Chop-Chop). " +
                    "Muito vulnerável a brute force offline. Evite."
            capabilities.contains("WEP", true) ->
                "WEP: criptografia quebrada. Pode ser quebrada em minutos capturando pacotes (IV reuse). " +
                    "Nunca use. Altíssima vulnerabilidade."
            else ->
                "Rede ABERTA (sem senha): qualquer pessoa pode se conectar. Tráfego, se sem TLS, pode ser interceptado. " +
                    "Risco crítico."
        }
    }

    /** Dispositivos na rede local via tabela ARP (IP + MAC + fabricante estimado). */
    fun discoverDevices(): List<ScanResult> {
        val rows = readArp()
        return rows.map { (ip, mac) ->
            val vendor = macVendor(mac)
            val ports = tryProbePorts(ip)
            val (deviceType, risk, score, conf) = fingerprint(mac, ports)
            ScanResult(
                type = "network",
                target = ip,
                details = "MAC: $mac • Fabricante: $vendor • Tipo estimado: $deviceType • " +
                    "Portas: ${ports.joinToString(",") { it.first.toString() }}\nSugestão: ${suggestionFor(deviceType)}",
                risk = risk,
                scoreCvss = score,
                confidence = conf,
                protocols = ports.map { it.second }.distinct()
            )
        }
    }

    /** Tenta credencial padrão nos serviços expostos e retorna o que extraiu. */
    fun testProtocols(ip: String, protocols: List<String>, allowNoConfirmation: Boolean): List<ScanResult> {
        val out = mutableListOf<ScanResult>()
        protocols.distinct().forEach { proto ->
            val attempt = attempt(ip, proto, allowNoConfirmation)
            out.add(
                ScanResult(
                    type = "network",
                    target = ip,
                    details = attempt,
                    risk = if (attempt.contains("extraiu", true) || attempt.contains("funcionou", true))
                        Risk.CRITICO else Risk.INFO,
                    scoreCvss = if (attempt.contains("funcionou", true)) 8.5f else 0f,
                    confidence = 80,
                    protocols = listOf(proto),
                    extracted = extractFrom(attempt)
                )
            )
        }
        return out
    }

    /** Busca credenciais padrão por fabricante (default creds) — complemento #2. */
    fun defaultCredsFor(macVendor: String): List<Pair<String, String>> {
        val v = macVendor.lowercase()
        return DEFAULT_CREDS[v] ?: DEFAULT_CREDS_GENERIC
    }

    /** Mapa versão->CVEs conhecidas (base local simplificada) — complemento #3. */
    fun versionToCve(banner: String): List<String> {
        return CVE_BASE.filterKeys { banner.contains(it, true) }
            .values
            .flatten()
    }

    private fun cryptoRisk(caps: String): Triple<Risk, Float, Int> = when {
        caps.contains("WPA3", true) -> Triple(Risk.INFO, 1.5f, 85)
        caps.contains("WPA2", true) -> Triple(Risk.BAIXO, 3.0f, 70)
        caps.contains("WPA", true) -> Triple(Risk.MEDIO, 5.5f, 75)
        caps.contains("WEP", true) -> Triple(Risk.ALTO, 7.5f, 90)
        else -> Triple(Risk.CRITICO, 9.5f, 95)
    }

    private fun buildCryptoDetail(caps: String, level: Int, freq: Int): String {
        return "${analyzeCrypto(caps)}\nSinal: ${level}dBm • Freq: ${freq}MHz • Capabilities: $caps\n" +
            "Tipo de segurança: ${cryptoLabel(caps)}"
    }

    private fun cryptoLabel(caps: String) = when {
        caps.contains("WPA3", true) -> "WPA3"
        caps.contains("WPA2", true) -> "WPA2"
        caps.contains("WPA", true) -> "WPA1"
        caps.contains("WEP", true) -> "WEP"
        else -> "Aberta"
    }

    private fun readArp(): List<Pair<String, String>> = runCatching {
        val out = mutableListOf<Pair<String, String>>()
        brFromProc("/proc/net/arp")?.use { br ->
            br.readLine()
            br.forEachLine { line ->
                val p = line.split(Regex("\\s+"))
                if (p.size >= 4 && p[0].isNotEmpty()) out.add(p[0] to p[3])
            }
        }
        out
    }.getOrDefault(emptyList())

    private fun brFromProc(path: String): BufferedReader? =
        runCatching { java.io.File(path).inputStream().bufferedReader() }.getOrNull()

    private fun macVendor(mac: String): String {
        val oui = mac.replace(":", "").replace("-", "").take(6).lowercase()
        return OUI.find { oui.startsWith(it.first) }?.second ?: "desconhecido"
    }

    /** Fingerprint fino de dispositivo por OUI + portas abertas. */
    private fun fingerprint(mac: String, open: List<Pair<Int, String>>): FingerprintInfo {
        val vendor = macVendor(mac)
        // Roteadores/APs frequentemente 80/443/8080 + o próprio OUI de fabricante de rede
        val isRouter = open.any { it.first == 80 || it.first == 443 || it.first == 8080 } &&
            (vendor.contains("tp-link") || vendor.contains("huawei") || vendor.contains("d-link") ||
                vendor.contains("linksys") || vendor.contains("netgear") || vendor.contains("tenda"))
        val isCamera = open.any { it.first == 554 } || vendor.contains("dahua") || vendor.contains("hikvision")
        val isTv = open.any { it.first == 7000 || it.first == 8008 } || vendor.contains("samsung") ||
            vendor.contains("lg") || vendor.contains("sony")
        val isPc = open.any { it.first == 445 || it.first == 139 || it.first == 22 || it.first == 3389 }

        val type = when {
            isCamera -> "Câmera IP"
            isRouter -> "Roteador"
            isTv -> "Smart TV"
            isPc -> "Computador/Notebook"
            else -> "Dispositivo móvel/IoT"
        }
        // Vulnerabilidade: quanto mais portas de serviço abertas, maior a superfície.
        val score = when {
            isCamera -> 7.0f
            isRouter -> 6.0f
            isPc -> (4.0f + open.size).coerceAtMost(9.0f)
            isTv -> 3.0f
            else -> 2.0f
        }
        return FingerprintInfo(type, Risk.fromScore(score), score, (60 + open.size * 5).coerceAtMost(95))
    }

    private fun suggestionFor(type: String): String = when {
        type.contains("Câmera") -> "RTSP sem autenticação (porta 554) e painel web com credencial padrão."
        type.contains("Roteador") -> "Painel web admin (80/443/8080) com credenciais padrão e WPS."
        type.contains("Computador") -> "SMB (445/139), RDP (3389) ou SSH (22) expostos. Testar compartilhamentos e credenciais default."
        type.contains("Smart TV") -> "Serviços de DLNA/upnp (7000/8008) podem expor dados sem autenticação."
        else -> "Portas abertas de serviço (http/upnp) — verificar painéis expostos."
    }

    /** Banco de dados de credenciais padrão por fabricante. */
    private fun tryProbePorts(ip: String): List<Pair<Int, String>> {
        val protoByPort = mapOf(
            21 to "ftp", 22 to "ssh", 23 to "telnet", 80 to "http", 443 to "https",
            445 to "smb", 139 to "smb", 554 to "rtsp", 8080 to "http", 3389 to "rdp"
        )
        return protoByPort.mapNotNull { (port, proto) ->
            if (portOpen(ip, port)) port to proto else null
        }
    }

    private fun portOpen(ip: String, port: Int): Boolean = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, port), 900)
            true
        }
    }.getOrDefault(false)

    /** Tentativa lógica/silenciosa de um protocolo (handshake/banner/cred default). */
    private fun attempt(ip: String, proto: String, allowNoConfirmation: Boolean): String {
        return when (proto) {
            "ftp" -> ftpAttempt(ip)
            "http" -> httpAttempt(ip)
            "rtsp" -> rtspAttempt(ip)
            "telnet" -> genericBanner(ip, 23, "Telnet")
            "ssh" -> bannerAttempt(ip, 22, "SSH")
            "smb" -> smbAttempt(ip)
            "rdp" -> bannerAttempt(ip, 3389, "RDP")
            else -> "Protocolo $proto não suportado nesta tentativa."
        }
    }

    private fun bannerAttempt(ip: String, port: Int, name: String): String = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, port), 1200)
            s.soTimeout = 1500
            val banner = s.getInputStream().bufferedReader().use { it.readText().take(120) }
            val cves = versionToCve(banner)
            if (banner.isNotBlank())
                "Obtive banner de $name: '$banner'. Versão identificada. CVEs conhecidas: ${if (cves.isEmpty()) "nenhuma na base local" else cves.joinToString(", ")}.\n" +
                    "Extraiu banner do serviço $name."
            else "Serviço $name aberto, mas sem banner retornado (silencioso)."
        }
    }.getOrElse { "Tentativa de $name: conexão falhou ou negada." } + " [tentado]"

    private fun ftpAttempt(ip: String): String {
        val banner = runCatching {
            Socket().use { s ->
                s.connect(java.net.InetSocketAddress(ip, 21), 1200)
                s.soTimeout = 1200
                s.getInputStream().bufferedReader().use { it.readLine() ?: "" }
            }
        }.getOrDefault("")
        if (banner.isBlank()) return "FTP: conexão sem banner (provavelmente fechado). [tentado]"
        val creds = DEFAULT_CREDS_GENERIC
        val tried = creds.firstOrNull { tryLogin(ip, 21, it.first, it.second) }
        return if (tried != null)
            "FTP aberto e credencial padrão FUNCIONOU: ${tried.first}:${tried.second}. Acesso ao serviço. Extraiu acesso FTP com credencial default.\n[banner: $banner]"
        else
            "FTP: banner '$banner'. Credencial padrão negada (seguro). [tentado]"
    }

    private fun httpAttempt(ip: String): String = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, 80), 1200)
            s.soTimeout = 1500
            s.getOutputStream().write("GET / HTTP/1.0\r\nHost: $ip\r\n\r\n".toByteArray())
            val resp = s.getInputStream().bufferedReader().use { it.readLines().take(10).joinToString(" | ") }
            val cves = versionToCve(resp)
            "HTTP responde. Headers: $resp. Versão identificada. CVEs: ${if (cves.isEmpty()) "nenhuma na base" else cves.joinToString(",")}. Extraiu headers/banner HTTP."
        }
    }.getOrElse { "HTTP: conexão falhou ou negada." } + " [tentado]"

    private fun rtspAttempt(ip: String): String = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, 554), 1200)
            s.soTimeout = 1500
            s.getOutputStream().write("OPTIONS rtsp://$ip/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray())
            val resp = s.getInputStream().bufferedReader().use { it.readLine() ?: "" }
            "RTSP responde: '$resp'. Extraiu capacidade RTSP (provável câmera). Teste streaming via Termux: ffprobe rtsp://$ip/stream1 "
        }
    }.getOrElse { "RTSP: conexão falhou ou negada." } + " [tentado]"

    private fun smbAttempt(ip: String): String = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, 445), 1200)
            "Porta SMB (445) aberta. Se compartilhamentos expostos, teste via Termux: smbclient -L //$ip/. Extraiu confirmação de SMB aberto."
        }
    }.getOrElse { "SMB: porta 445 negada." } + " [tentado]"

    private fun genericBanner(ip: String, port: Int, name: String): String = runCatching {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, port), 1200)
            s.soTimeout = 1500
            val b = s.getInputStream().bufferedReader().use { it.readText().take(80) }
            "Serviço $name aberto. Banner: '$b'. Extraiu banner."
        }
    }.getOrElse { "$name: conexão negada." } + " [tentado]"

    private fun tryLogin(ip: String, port: Int, user: String, pass: String): Boolean = try {
        Socket().use { s ->
            s.connect(java.net.InetSocketAddress(ip, port), 1200)
            s.soTimeout = 1500
            val r = s.getInputStream().bufferedReader()
            r.readLine()
            s.getOutputStream().write("USER $user\r\n".toByteArray())
            r.readLine()
            s.getOutputStream().write("PASS $pass\r\n".toByteArray())
            val resp = r.readLine() ?: ""
            !resp.startsWith("530") && resp.contains("230")
        }
    } catch (e: Exception) {
        false
    }

    private fun extractFrom(attempt: String): List<String> {
        val start = attempt.indexOf("Extraiu")
        return if (start >= 0) listOf(attempt.substring(start)) else emptyList()
    }

    private data class FingerprintInfo(val type: String, val risk: Risk, val score: Float, val conf: Int)

    companion object {
        private val OUI = listOf(
            "a4:83" to "dahua", "3c:ef:8c" to "dahua", "00:1f:cf" to "hikvision", "44:19:b6" to "hikvision",
            "e4:f4:c6" to "tp-link", "50:c7:bf" to "tp-link", "40:4d:8e" to "tp-link",
            "68:7f:74" to "linksys", "00:1c:10" to "netgear", "c0:3f:0e" to "netgear",
            "70:4f:57" to "huawei", "00:1d:db" to "samsung", "9c:37:f4" to "samsung",
            "e8:4e:06" to "lg", "30:05:5c" to "sony", "10:c3:7b" to "sony", "94:34:56" to "sagemcom"
        )

        private val DEFAULT_CREDS = mapOf(
            "dahua" to listOf("admin" to "admin", "admin" to "123456", "root" to "admin"),
            "hikvision" to listOf("admin" to "12345", "admin" to "admin", "root" to "12345"),
            "tp-link" to listOf("admin" to "admin", "admin" to ""),
            "linksys" to listOf("admin" to "admin", "admin" to "password"),
            "netgear" to listOf("admin" to "password", "admin" to "1234", "admin" to ""),
            "huawei" to listOf("admin" to "admin", "admin" to "@Huawei"),
            "lg" to listOf("admin" to "admin", "root" to "root"),
            "samsung" to listOf("admin" to "admin", "user" to "password")
        )

        private val DEFAULT_CREDS_GENERIC: List<Pair<String, String>> = listOf(
            "admin" to "admin", "admin" to "1234", "root" to "root",
            "root" to "1234", "admin" to ""
        )

        private val CVE_BASE = mapOf(
            "apache/2.4.49" to listOf("CVE-2021-41773 (path traversal/RCE, crítica)"),
            "apache/2.4.50" to listOf("CVE-2021-42013 (RCE, crítica)"),
            "openssh_7.7" to listOf("CVE-2020-15778 (scp injection)"),
            "nginx/1.16" to listOf("CVE-2019-20372 (error_page RCE)"),
            "uw httpd" to listOf("CVE-2019-20178 (path traversal D-Link)"),
            "hidvision" to listOf("CVE-2017-7921 (Hikvision RCE)" )
        )
    }
}