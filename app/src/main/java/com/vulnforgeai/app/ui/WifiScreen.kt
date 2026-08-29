package com.vulnforgeai.app.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.ToolExecutor

class WifiScreen : AppCompatActivity() {

    private lateinit var scanButton: Button
    private lateinit var resultText: TextView
    private lateinit var wifimanager: WifiManager
    private lateinit var prefs: UserPrefs
    private lateinit var toolExecutor: ToolExecutor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        prefs = UserPrefs(this)
        toolExecutor = ToolExecutor(this)
        scanButton = findViewById(R.id.wifi_scan_button)
        resultText = findViewById(R.id.wifi_result)
        wifimanager = getSystemService(Context.WIFI_SERVICE) as WifiManager

        scanButton.setOnClickListener { if (hasLocationPermission()) startScan() else requestLocation() }

        resultText.text = "Toque em 'Escanear redes WiFi' para ver as redes próximas.\n\n" +
            "Para testar a segurança do roteador, use o chat e peça um nmap no IP do roteador."
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 200
        )
    }

    private fun startScan() {
        if (!hasLocationPermission()) {
            resultText.text = "Permissão de localização necessária para ver redes próximas."
            return
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val results = wifimanager.scanResults
                val sb = StringBuilder("📶 Redes encontradas: ${results.size}\n\n")
                results.forEachIndexed { index, result ->
                    val security = when {
                        result.capabilities.contains("WPA") -> "🔒 WPA"
                        result.capabilities.contains("WEP") -> "🔓 WEP (fraco!)"
                        else -> "🔴 Sem senha (perigo!)"
                    }
                    sb.appendLine("${index + 1}. ${result.SSID}")
                    sb.appendLine("   $security | Sinal: ${result.level}dBm")
                    sb.appendLine("   BSSID: ${result.BSSID} | Freq: ${result.frequency}MHz")
                    sb.appendLine()
                }
                sb.appendLine("Para testar a segurança de uma rede, use o chat e peça um nmap no IP do roteador.")
                resultText.text = sb.toString()
                runCatching { unregisterReceiver(this) }
            }
        }
        registerReceiver(receiver, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        resultText.text = "🔍 Escaneando redes próximas..."
        wifimanager.startScan()
    }
}