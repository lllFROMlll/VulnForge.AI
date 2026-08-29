package com.vulnforgeai.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.ToolExecutor

class IptvScreen : AppCompatActivity() {

    private lateinit var targetField: EditText
    private lateinit var resultText: TextView
    private lateinit var prefs: UserPrefs
    private lateinit var toolExecutor: ToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_iptv)

        prefs = UserPrefs(this)
        toolExecutor = ToolExecutor(this)

        targetField = findViewById(R.id.iptv_target)
        resultText = findViewById(R.id.iptv_result)

        findViewById<Button>(R.id.iptv_check_panel).setOnClickListener {
            val target = targetField.text.toString().trim()
            if (target.isEmpty()) { resultText.text = "Digite um IP ou domínio."; return@setOnClickListener }
            runCommand("curl -s -m 10 http://$target:8888/")
        }

        findViewById<Button>(R.id.iptv_scan_ports).setOnClickListener {
            val target = targetField.text.toString().trim()
            if (target.isEmpty()) { resultText.text = "Digite um IP ou domínio."; return@setOnClickListener }
            runCommand("nmap -p 80,443,8888,8899,8805 -sV $target")
        }

        findViewById<Button>(R.id.iptv_common_creds).setOnClickListener {
            val target = targetField.text.toString().trim()
            if (target.isEmpty()) { resultText.text = "Digite um IP ou domínio."; return@setOnClickListener }
            runCommand("hydra -l admin -P /data/data/com.termux/files/usr/share/wordlists/rockyou.txt http-get://$target:8888/")
        }
    }

    private fun runCommand(command: String) {
        resultText.text = toolExecutor.handle(command, prefs.mode)
            ?: "Comando preparado. Execute no Termux."
    }
}