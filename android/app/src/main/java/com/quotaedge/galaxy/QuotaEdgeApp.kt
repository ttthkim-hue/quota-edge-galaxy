package com.quotaedge.galaxy

import android.app.Application
import com.quotaedge.galaxy.data.SnapshotCache
import com.quotaedge.galaxy.data.TokenStore
import com.quotaedge.galaxy.data.UsageRepository
import com.quotaedge.galaxy.data.UsageSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuotaEdgeApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var usageRepository: UsageRepository
        private set

    private val _snapshot = MutableStateFlow(UsageSnapshot.empty())
    val snapshot: StateFlow<UsageSnapshot> = _snapshot.asStateFlow()

    fun updateSnapshot(s: UsageSnapshot) {
        _snapshot.value = s
        getSharedPreferences("quota_cache", MODE_PRIVATE).edit()
            .putString("claude_line", s.claude.glanceLine())
            .putString("codex_line", s.codex.glanceLine())
            .putString("grok_line", s.grok.glanceLine())
            .putLong("updated", s.updatedAtEpochMs)
            .apply()
        SnapshotCache.save(this, s)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenStore = TokenStore(this)
        usageRepository = UsageRepository(this, tokenStore)
        SnapshotCache.load(this)?.let { cached ->
            _snapshot.value = cached
            getSharedPreferences("quota_cache", MODE_PRIVATE).edit()
                .putString("claude_line", cached.claude.glanceLine())
                .putString("codex_line", cached.codex.glanceLine())
                .putString("grok_line", cached.grok.glanceLine())
                .putLong("updated", cached.updatedAtEpochMs)
                .apply()
        }
    }

    companion object {
        lateinit var instance: QuotaEdgeApp
            private set
    }
}
