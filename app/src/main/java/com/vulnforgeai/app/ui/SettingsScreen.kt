package com.vulnforgeai.app.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserMode
import com.vulnforgeai.app.data.UserPrefs
import com.vulnforgeai.app.engine.AiEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsScreen : AppCompatActivity() {

    private lateinit var keyField: EditText
    private lateinit var fallbackField: EditText
    private lateinit var loadButton: Button
    private lateinit var useButton: Button
    private lateinit var saveButton: Button
    private lateinit var modelList: RecyclerView
    private lateinit var modelStatus: TextView
    private lateinit var statusText: TextView
    private lateinit var modeGroup: RadioGroup
    private lateinit var radioIniciante: RadioButton
    private lateinit var radioIntermediario: RadioButton
    private lateinit var radioProfissional: RadioButton

    private var cachedModels = emptyList<Pair<String, String>>()
    private var selectedModel = ""

    private lateinit var prefs: UserPrefs
    private lateinit var aiEngine: AiEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = UserPrefs(this)
        aiEngine = AiEngine(prefs)

        keyField = findViewById(R.id.settings_api_key)
        fallbackField = findViewById(R.id.settings_model_fallback)
        loadButton = findViewById(R.id.settings_load_models)
        useButton = findViewById(R.id.settings_use_fallback)
        saveButton = findViewById(R.id.settings_save)
        modelList = findViewById(R.id.settings_model_list)
        modelStatus = findViewById(R.id.settings_model_status)
        statusText = findViewById(R.id.settings_status)
        modeGroup = findViewById(R.id.settings_mode_group)
        radioIniciante = findViewById(R.id.settings_mode_iniciante)
        radioIntermediario = findViewById(R.id.settings_mode_intermediario)
        radioProfissional = findViewById(R.id.settings_mode_profissional)

        modelList.layoutManager = LinearLayoutManager(this)

        keyField.setText(prefs.apiKey)
        fallbackField.setText(prefs.selectedModel)
        selectedModel = prefs.selectedModel
        selectModeRadio(prefs.mode)

        loadButton.setOnClickListener { loadModels() }

        useButton.setOnClickListener {
            val id = fallbackField.text.toString().trim()
            if (id.isNotEmpty()) {
                selectedModel = id
                statusText.text = "Modelo escolhido: $id"
                renderModels()
            }
        }

        saveButton.setOnClickListener {
            prefs.apiKey = keyField.text.toString()
            if (selectedModel.isNotEmpty()) prefs.selectedModel = selectedModel
            prefs.mode = currentMode()
            statusText.text = "Tudo salvo! Você já pode usar o chat."
            Toast.makeText(this, "Configurações salvas", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadModels() {
        if (keyField.text.toString().trim().isBlank()) {
            Toast.makeText(this, "Coloque sua chave da API primeiro", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.apiKey = keyField.text.toString()
        modelStatus.text = "Carregando modelos..."
        CoroutineScope(Dispatchers.IO).launch {
            val models = aiEngine.listModels()
            withContext(Dispatchers.Main) {
                cachedModels = models
                if (models.isEmpty()) {
                    modelStatus.text = "Não consegui carregar. Confira sua chave e sua conexão."
                } else {
                    modelStatus.text = "${models.size} modelos encontrados. Toque para escolher."
                    renderModels()
                }
            }
        }
    }

    private fun renderModels() {
        val adapter = ModelAdapter(cachedModels, selectedModel) { model ->
            selectedModel = model.first
            fallbackField.setText(model.first)
            statusText.text = "Modelo escolhido: ${model.first}"
            renderModels()
        }
        modelList.adapter = adapter
    }

    private fun currentMode(): UserMode = when (modeGroup.checkedRadioButtonId) {
        R.id.settings_mode_iniciante -> UserMode.INICIANTE
        R.id.settings_mode_intermediario -> UserMode.INTERMEDIARIO
        R.id.settings_mode_profissional -> UserMode.PROFISSIONAL
        else -> UserMode.INICIANTE
    }

    private fun selectModeRadio(mode: UserMode) {
        when (mode) {
            UserMode.INICIANTE -> radioIniciante.isChecked = true
            UserMode.INTERMEDIARIO -> radioIntermediario.isChecked = true
            UserMode.PROFISSIONAL -> radioProfissional.isChecked = true
        }
    }
}