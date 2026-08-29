package com.vulnforgeai.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.ToolExecutor

class WebScanScreen : AppCompatActivity() {

    private lateinit var targetField: EditText
    private lateinit var resultText: TextView
    private lateinit var prefs: UserPrefs
    private lateinit var toolExecutor: ToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_web_scan)

        prefs = UserPrefs(this)
        toolExecutor = ToolExecutor(this)

        targetField = findViewById(R.id.web_target)
        resultText = findViewById(R.id.web_result)

        findViewById<Button>(R.id.web_headers).setOnClickListener {
            val url = targetField.text.toString().trim()
            if (url.isEmpty()) { resultText.text = "Digite uma URL."; return@setOnClickListener }
            runCommand("curl -I $url")
        }

        findViewById<Button>(R.id.web_dirs).setOnClickListener {
            val url = targetField.text.toString().trim()
            if (url.isEmpty()) { resultText.text = "Digite uma URL."; return@setOnClickListener }
            runCommand("python3 -m dirsearch -u $url -e php,html,txt")
        }

        findViewById<Button>(R.id.web_sqli).setOnClickListener {
            val url = targetField.text.toString().trim()
            if (url.isEmpty()) { resultText.text = "Digite uma URL com parâmetro (ex: site.com/p?id=1)."; return@setOnClickListener }
            runCommand("sqlmap -u \"$url\" --batch")
        }
    }

    private fun runCommand(command: String) {
        resultText.text = toolExecutor.handle(command, prefs.mode)
            ?: "Comando preparado. Execute no Termux."
    }
}