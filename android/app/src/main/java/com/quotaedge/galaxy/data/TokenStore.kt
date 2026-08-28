package com.quotaedge.galaxy.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "quota_edge_prefs")

class TokenStore(private val context: Context) {
    private val plain: SharedPreferences =
        context.getSharedPreferences("quota_tokens", Context.MODE_PRIVATE)

    private val encrypted: SharedPreferences? by lazy {
        runCatching {
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
        }.onFailure { Log.e("QuotaEdge", "Encrypted prefs failed, using plain", it) }
            .getOrNull()
    }

    private fun prefs(): SharedPreferences = encrypted ?: plain

    fun getClaudeToken(): String? =
        prefs().getString(KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }
            ?: plain.getString(KEY_CLAUDE, null)?.takeIf { it.isNotBlank() }

    fun getClaudeRefresh(): String? =
        prefs().getString(KEY_CLAUDE_REFRESH, null)?.takeIf { it.isNotBlank() }
            ?: plain.getString(KEY_CLAUDE_REFRESH, null)?.takeIf { it.isNotBlank() }

    fun getClaudeExpiresAt(): Long =
        prefs().getLong(KEY_CLAUDE_EXPIRES, 0L).takeIf { it > 0 }
            ?: plain.getLong(KEY_CLAUDE_EXPIRES, 0L)

    fun getCodexToken(): String? =
        prefs().getString(KEY_CODEX, null)?.takeIf { it.isNotBlank() }
            ?: plain.getString(KEY_CODEX, null)?.takeIf { it.isNotBlank() }

    fun getCodexRefresh(): String? =
        prefs().getString(KEY_CODEX_REFRESH, null)?.takeIf { it.isNotBlank() }
            ?: plain.getString(KEY_CODEX_REFRESH, null)?.takeIf { it.isNotBlank() }

    fun getCodexAccountId(): String? =
        prefs().getString(KEY_CODEX_ACCOUNT, null)?.takeIf { it.isNotBlank() }
            ?: plain.getString(KEY_CODEX_ACCOUNT, null)?.takeIf { it.isNotBlank() }

    fun getCodexExpiresAt(): Long =
        prefs().getLong(KEY_CODEX_EXPIRES, 0L).takeIf { it > 0 }
            ?: plain.getLong(KEY_CODEX_EXPIRES, 0L)

    fun isClaudeLinked(): Boolean = !getClaudeToken().isNullOrBlank()
    fun isCodexLinked(): Boolean = !getCodexToken().isNullOrBlank()

    fun saveClaudeSession(accessToken: String, refreshToken: String?, expiresAtMs: Long) {
        writeAll { ed ->
            ed.putString(KEY_CLAUDE, accessToken.trim())
            ed.putString(KEY_CLAUDE_REFRESH, refreshToken?.trim().orEmpty())
            ed.putLong(KEY_CLAUDE_EXPIRES, expiresAtMs)
            ed.putBoolean(KEY_CLAUDE_LINKED, true)
        }
        Log.i("QuotaEdge", "Claude session saved tokenLen=${accessToken.length}")
    }

    fun saveCodexSession(
        accessToken: String,
        refreshToken: String?,
        accountId: String?,
        expiresAtMs: Long,
    ) {
        writeAll { ed ->
            ed.putString(KEY_CODEX, accessToken.trim())
            ed.putString(KEY_CODEX_REFRESH, refreshToken?.trim().orEmpty())
            ed.putLong(KEY_CODEX_EXPIRES, expiresAtMs)
            ed.putBoolean(KEY_CODEX_LINKED, true)
            if (!accountId.isNullOrBlank()) ed.putString(KEY_CODEX_ACCOUNT, accountId.trim())
        }
        Log.i("QuotaEdge", "Codex session saved tokenLen=${accessToken.length} account=${accountId != null}")
    }

    fun clearClaude() = writeAll { ed ->
        ed.remove(KEY_CLAUDE).remove(KEY_CLAUDE_REFRESH).remove(KEY_CLAUDE_EXPIRES)
            .putBoolean(KEY_CLAUDE_LINKED, false)
    }

    fun clearCodex() = writeAll { ed ->
        ed.remove(KEY_CODEX).remove(KEY_CODEX_REFRESH).remove(KEY_CODEX_ACCOUNT).remove(KEY_CODEX_EXPIRES)
            .putBoolean(KEY_CODEX_LINKED, false)
    }

    private fun writeAll(block: (SharedPreferences.Editor) -> Unit) {
        listOfNotNull(encrypted, plain).forEach { sp ->
            val ed = sp.edit()
            block(ed)
            if (!ed.commit()) Log.e("QuotaEdge", "prefs commit failed")
        }
    }

    private val uiPrefs: SharedPreferences =
        context.getSharedPreferences("quota_ui", Context.MODE_PRIVATE)

    val overlayEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_OVERLAY] ?: uiPrefs.getBoolean(PREF_OVERLAY, true)
    }
    val lockScreenEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_LOCK] ?: uiPrefs.getBoolean(PREF_LOCK, true)
    }
    val statusGlanceEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_GLANCE] ?: uiPrefs.getBoolean(PREF_GLANCE, true)
    }

    fun isOverlayEnabledSync(): Boolean = uiPrefs.getBoolean(PREF_OVERLAY, true)
    fun isLockScreenEnabledSync(): Boolean = uiPrefs.getBoolean(PREF_LOCK, true)
    fun isGlanceEnabledSync(): Boolean = uiPrefs.getBoolean(PREF_GLANCE, true)

    suspend fun setOverlayEnabled(v: Boolean) {
        uiPrefs.edit().putBoolean(PREF_OVERLAY, v).apply()
        context.dataStore.edit { it[KEY_OVERLAY] = v }
    }

    suspend fun setLockScreenEnabled(v: Boolean) {
        uiPrefs.edit().putBoolean(PREF_LOCK, v).apply()
        context.dataStore.edit { it[KEY_LOCK] = v }
    }

    suspend fun setStatusGlanceEnabled(v: Boolean) {
        uiPrefs.edit().putBoolean(PREF_GLANCE, v).apply()
        context.dataStore.edit { it[KEY_GLANCE] = v }
    }

    companion object {
        private const val KEY_CLAUDE = "claude_oauth_token"
        private const val KEY_CLAUDE_REFRESH = "claude_refresh_token"
        private const val KEY_CLAUDE_EXPIRES = "claude_expires_at"
        private const val KEY_CLAUDE_LINKED = "claude_linked"
        private const val KEY_CODEX = "codex_oauth_token"
        private const val KEY_CODEX_REFRESH = "codex_refresh_token"
        private const val KEY_CODEX_ACCOUNT = "codex_account_id"
        private const val KEY_CODEX_EXPIRES = "codex_expires_at"
        private const val KEY_CODEX_LINKED = "codex_linked"
        private const val PREF_OVERLAY = "overlay_enabled"
        private const val PREF_LOCK = "lock_screen_enabled"
        private const val PREF_GLANCE = "status_glance_enabled"
        private val KEY_OVERLAY = booleanPreferencesKey("overlay_enabled")
        private val KEY_LOCK = booleanPreferencesKey("lock_screen_enabled")
        private val KEY_GLANCE = booleanPreferencesKey("status_glance_enabled")
    }
}
