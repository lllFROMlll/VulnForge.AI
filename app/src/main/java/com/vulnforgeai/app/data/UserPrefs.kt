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
    }
}