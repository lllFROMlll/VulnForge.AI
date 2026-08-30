package com.vulnforgeai.app.audio

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vulnforgeai.app.R
import com.vulnforgeai.app.data.UserPrefs

/**
 * Tela de seleção de voz para a narração. Lista as vozes TTS disponíveis,
 * permite ouvir/testar cada uma e salvar a escolha. Se houver poucas vozes,
 * sugere instalar o Google Text-to-Speech com acesso direto à Play Store.
 * Sempre atualiza a lista a cada abertura.
 */
class VoicePickerScreen : AppCompatActivity() {

    private lateinit var voiceList: RecyclerView
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView
    private lateinit var installButton: Button
    private lateinit var narration: Narration
    private lateinit var prefs: UserPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_picker)

        prefs = UserPrefs(this)
        narration = Narration(this, prefs)

        voiceList = findViewById(R.id.voice_list)
        statusText = findViewById(R.id.voice_status)
        hintText = findViewById(R.id.voice_hint)
        installButton = findViewById(R.id.voice_install_google)

        voiceList.layoutManager = LinearLayoutManager(this)

        installButton.setOnClickListener {
            openGoogleTtsOnPlayStore()
        }

        renderVoices()
    }

    override fun onResume() {
        super.onResume()
        // Sempre atualiza a lista a cada abertura (captura vozes novas).
        renderVoices()
    }

    private fun renderVoices() {
        val voices = narration.listVoiceLabels()
        if (voices.isEmpty()) {
            statusText.text = "Nenhuma voz TTS encontrada."
            showInstallCard(true)
            return
        }
        statusText.text = "${voices.size} voz(es) disponível(is). Toque para escolher."
        val selected = prefs.selectedVoice
        voiceList.adapter = VoiceAdapter(voices, selected) { voiceId ->
            prefs.selectedVoice = voiceId
            narration.applyVoice(voiceId)
            narration.speak("Esta é a minha voz.")
            Toast.makeText(this, "Voz escolhida e salva.", Toast.LENGTH_SHORT).show()
            renderVoices()
        }
        if (narration.hasFewVoices()) {
            showInstallCard(true)
        } else {
            showInstallCard(false)
        }
    }

    private fun showInstallCard(show: Boolean) {
        val vis = if (show) View.VISIBLE else View.GONE
        hintText.visibility = vis
        installButton.visibility = vis
        if (show) {
            hintText.text = "Você tem poucas vozes. Instalar o Google Text-to-Speech melhora a qualidade da narração."
        }
    }

    private fun openGoogleTtsOnPlayStore() {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts"))
            )
        }.onFailure {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.tts"))
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        narration.shutdown()
    }
}

class VoiceAdapter(
    private val voices: List<Pair<String, String>>,
    private val selectedId: String,
    private val onClick: (String) -> Unit
) : RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VoiceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_voice, parent, false)
        return VoiceViewHolder(view)
    }

    override fun onBindViewHolder(holder: VoiceViewHolder, position: Int) {
        val (id, label) = voices[position]
        val isSelected = id == selectedId
        holder.idText.text = if (isSelected) "✔ $id" else id
        holder.labelText.text = label
        holder.itemView.setOnClickListener { onClick(id) }
    }

    override fun getItemCount() = voices.size

    class VoiceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val idText: TextView = itemView.findViewById(R.id.voice_item_id)
        val labelText: TextView = itemView.findViewById(R.id.voice_item_label)
    }
}