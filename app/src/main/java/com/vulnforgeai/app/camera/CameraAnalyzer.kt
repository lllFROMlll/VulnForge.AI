package com.vulnforgeai.app.camera

/**
 * Procura informações de segurança dentro de um texto extraído de foto.
 */
class CameraAnalyzer {

    fun analyzeText(text: String): String {
        val sb = StringBuilder("📄 TEXTO EXTRAÍDO:\n${text.take(600)}\n\n")

        val ips = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""")
            .findAll(text).map { it.value }.toList()
        if (ips.isNotEmpty()) sb.appendLine("🌐 IPs: ${ips.joinToString(", ")}")

        val emails = Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}""")
            .findAll(text).map { it.value }.toList()
        if (emails.isNotEmpty()) sb.appendLine("📧 Emails: ${emails.joinToString(", ")}")

        val urls = Regex("""https?://[^\s]+""")
            .findAll(text).map { it.value }.toList()
        if (urls.isNotEmpty()) sb.appendLine("🔗 URLs: ${urls.joinToString(", ")}")

        val phones = Regex("""\(?\d{2}\)?\s?\d{4,5}-?\d{4}""")
            .findAll(text).map { it.value }.toList()
        if (phones.isNotEmpty()) sb.appendLine("📞 Telefones: ${phones.joinToString(", ")}")

        val credWords = listOf("senha", "password", "pass", "credencial", "login", "admin")
        if (credWords.any { text.lowercase().contains(it) }) {
            sb.appendLine("🔐 ⚠️ Possível credencial encontrada na imagem.")
        }

        return sb.toString()
    }
}