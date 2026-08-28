package com.quotaedge.galaxy.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quota_edge_prefs")

class TokenStore(private val context: Context) {
    private val encrypted by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "quota_edge_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getClaudeToken(): String? = encrypted.getString(KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }
    fun getCodexToken(): String? = encrypted.getString(KEY_CODEX, null)?.takeIf { it.isNotBlank() }
    fun getCodexAccountId(): String? = encrypted.getString(KEY_CODEX_ACCOUNT, null)?.takeIf { it.isNotBlank() }

    fun saveClaudeToken(token: String) = encrypted.edit().putString(KEY_CLAUDE, token.trim()).apply()
    fun saveCodexToken(token: String) = encrypted.edit().putString(KEY_CODEX, token.trim()).apply()
    fun saveCodexAccountId(id: String) = encrypted.edit().putString(KEY_CODEX_ACCOUNT, id.trim()).apply()

    fun clearClaude() = encrypted.edit().remove(KEY_CLAUDE).apply()
    fun clearCodex() = encrypted.edit().remove(KEY_CODEX).remove(KEY_CODEX_ACCOUNT).apply()

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_OVERLAY] ?: false }
    val lockScreenEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOCK] ?: true }
    val statusGlanceEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_GLANCE] ?: true }

    suspend fun setOverlayEnabled(v: Boolean) = context.dataStore.edit { it[KEY_OVERLAY] = v }
    suspend fun setLockScreenEnabled(v: Boolean) = context.dataStore.edit { it[KEY_LOCK] = v }
    suspend fun setStatusGlanceEnabled(v: Boolean) = context.dataStore.edit { it[KEY_GLANCE] = v }

    companion object {
        private const val KEY_CLAUDE = "claude_oauth_token"
        private const val KEY_CODEX = "codex_oauth_token"
        private const val KEY_CODEX_ACCOUNT = "codex_account_id"
        private val KEY_OVERLAY = booleanPreferencesKey("overlay_enabled")
        private val KEY_LOCK = booleanPreferencesKey("lock_screen_enabled")
        private val KEY_GLANCE = booleanPreferencesKey("status_glance_enabled")
    }
}
