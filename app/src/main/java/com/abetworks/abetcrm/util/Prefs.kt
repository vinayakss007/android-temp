package com.abetworks.abetcrm.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object Prefs {
    const val KEY_AUTH_TOKEN = "auth_token"
    const val KEY_TENANT_ID  = "tenant_id"
    const val KEY_API_URL    = "api_url"
    const val KEY_USER_NAME  = "user_name"
    const val KEY_USER_EMAIL = "user_email"

    private fun prefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context, "abetcrm_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular prefs if encryption unavailable (emulator edge case)
        context.getSharedPreferences("abetcrm_prefs", Context.MODE_PRIVATE)
    }

    fun get(context: Context, key: String, default: String = "") =
        prefs(context).getString(key, default) ?: default

    fun set(context: Context, key: String, value: String) =
        prefs(context).edit().putString(key, value).apply()

    fun clear(context: Context) = prefs(context).edit().clear().apply()

    fun isLoggedIn(context: Context) = get(context, KEY_AUTH_TOKEN).isNotBlank()
}
