package com.vulnforgeai.app

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.ui.CameraScreen
import com.vulnforgeai.app.ui.ChatScreen
import com.vulnforgeai.app.ui.IptvScreen
import com.vulnforgeai.app.ui.LearningScreen
import com.vulnforgeai.app.ui.PortScanScreen
import com.vulnforgeai.app.ui.ReportScreen
import com.vulnforgeai.app.ui.SettingsScreen
import com.vulnforgeai.app.ui.WebScanScreen
import com.vulnforgeai.app.ui.WifiScreen

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: UserPrefs

    private val modules = listOf(
        ModuleItem("📶", "WiFi Scanner", "Veja redes e teste segurança", WifiScreen::class.java),
        ModuleItem("🌐", "Web Scan", "SQLi, XSS, diretórios", WebScanScreen::class.java),
        ModuleItem("📺", "IPTV Scan", "Servidores IPTV vulneráveis", IptvScreen::class.java),
        ModuleItem("🔌", "Port Scanner", "Descubra portas abertas", PortScanScreen::class.java),
        ModuleItem("📸", "Câmera", "Scanner visual com OCR", CameraScreen::class.java),
        ModuleItem("📚", "Aprender", "Missões guiadas para iniciantes", LearningScreen::class.java),
        ModuleItem("📋", "Relatórios", "Histórico e relatórios", ReportScreen::class.java),
        ModuleItem("⚙️", "Config", "Chave, modelo e modo", SettingsScreen::class.java)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = UserPrefs(this)

        val recyclerView: RecyclerView = findViewById(R.id.module_grid)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = ModuleAdapter(modules) { module ->
            startActivity(Intent(this, module.targetActivity))
        }

        findViewById<Button>(R.id.main_open_chat).setOnClickListener {
            startActivity(Intent(this, ChatScreen::class.java))
        }

        val footer: TextView = findViewById(R.id.main_footer)
        footer.text = "Modo: ${prefs.mode.label}  •  IA: OpenRouter  •  Modelo: ${prefs.selectedModel}"

        if (!prefs.getBoolean("legal_ack", false)) {
            showLegalDialog()
        }
    }

    private fun showLegalDialog() {
        AlertDialog.Builder(this)
            .setTitle("Uso autorizado")
            .setMessage("O VulnForgeAI destina-se a testes de segurança em alvos que você possui ou possui autorização para testar. Use apenas com permissão.")
            .setPositiveButton("Entendi") { _, _ ->
                prefs.putBoolean("legal_ack", true)
            }
            .setCancelable(false)
            .show()
    }
}