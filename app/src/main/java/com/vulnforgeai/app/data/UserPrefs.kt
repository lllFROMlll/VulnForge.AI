package com.vulnforgeai.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException

/**
 * Guarda os ajustes do usuário de forma segura:
 * a chave da API (OpenRouter) fica criptografada, o resto em preferências comuns.
 */
class UserPrefs(context: Context) {

    private val encrypted: SharedPreferences = run {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                FILE_ENC,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: IOException) {
            context.getSharedPreferences(FILE_ENC, Context.MODE_PRIVATE)
        }
    }

    private val plain: SharedPreferences =
        context.getSharedPreferences(FILE_PLAIN, Context.MODE_PRIVATE)

    var apiKey: String
        get() = encrypted.getString(KEY_API, "") ?: ""
        set(value) = encrypted.edit().putString(KEY_API, value.trim()).apply()

    var selectedModel: String
        get() = plain.getString(KEY_MODEL, "openrouter/auto").toString()
        set(value) = plain.edit().putString(KEY_MODEL, value.trim()).apply()

    var mode: UserMode
        get() {
            val name = plain.getString(KEY_MODE, UserMode.INICIANTE.name)
            return runCatching { UserMode.valueOf(name!!) }.getOrDefault(UserMode.INICIANTE)
        }
        set(value) = plain.edit().putString(KEY_MODE, value.name).apply()

    var chatMode: ChatMode
        get() {
            val name = plain.getString(KEY_CHAT_MODE, ChatMode.AUTO.name)
            return runCatching { ChatMode.valueOf(name!!) }.getOrDefault(ChatMode.AUTO)
        }
        set(value) = plain.edit().putString(KEY_CHAT_MODE, value.name).apply()

    var confidentMode: Boolean
        get() = plain.getBoolean(KEY_CONFIDENT, false)
        set(value) = plain.edit().putBoolean(KEY_CONFIDENT, value).apply()

    var narrationEnabled: Boolean
        get() = plain.getBoolean(KEY_NARRATION, true)
        set(value) = plain.edit().putBoolean(KEY_NARRATION, value).apply()

    var selectedVoice: String
        get() = plain.getString(KEY_VOICE, "").toString()
        set(value) = plain.edit().putString(KEY_VOICE, value).apply()

    /** Prazo de validade da memória persistente em dias. Padrão: 30 (1 mês). */
    val memoryExpiryDays: Int
        get() = plain.getInt(KEY_MEMORY_DAYS, 30)

    fun setMemoryExpiryDays(days: Int) =
        plain.edit().putInt(KEY_MEMORY_DAYS, days.coerceIn(1, 2400)).apply()

    var termuxServerHost: String
        get() = plain.getString(KEY_HOST, "").toString()
        set(value) = plain.edit().putString(KEY_HOST, value.trim()).apply()

    fun getBoolean(key: String, default: Boolean): Boolean =
        plain.getBoolean(key, default)

    fun putBoolean(key: String, value: Boolean) =
        plain.edit().putBoolean(key, value).apply()

    companion object {
        private const val FILE_ENC = "vulnforge_prefs_enc"
        private const val FILE_PLAIN = "vulnforge_prefs"
        private const val KEY_API = "api_key"
        private const val KEY_MODEL = "model"
        private const val KEY_MODE = "mode"
        private const val KEY_HOST = "termux_host"
        private const val KEY_CHAT_MODE = "chat_mode"
        private const val KEY_CONFIDENT = "confident_mode"
        private const val KEY_NARRATION = "narration_enabled"
        private const val KEY_VOICE = "voice"
        private const val KEY_MEMORY_DAYS = "memory_expiry_days"
    }
}