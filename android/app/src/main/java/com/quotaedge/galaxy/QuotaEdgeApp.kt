package com.quotaedge.galaxy

import android.app.Application
import com.quotaedge.galaxy.data.TokenStore
import com.quotaedge.galaxy.data.UsageRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class QuotaEdgeApp : Application() {
    lateinit var tokenStore: TokenStore
        private set
    lateinit var usageRepository: UsageRepository
        private set

    private val _snapshot = MutableStateFlow(com.quotaedge.galaxy.data.UsageSnapshot.empty())
    val snapshot: StateFlow<com.quotaedge.galaxy.data.UsageSnapshot> = _snapshot.asStateFlow()

    fun updateSnapshot(s: com.quotaedge.galaxy.data.UsageSnapshot) {
        _snapshot.value = s
        getSharedPreferences("quota_cache", MODE_PRIVATE).edit()
            .putString("claude_l1", s.claude.line1())
            .putString("claude_l2", s.claude.line2())
            .putString("codex_l1", s.codex.line1())
            .putString("codex_l2", s.codex.line2())
            .putLong("updated", s.updatedAtEpochMs)
            .apply()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        tokenStore = TokenStore(this)
        usageRepository = UsageRepository(tokenStore)
    }

    companion object {
        lateinit var instance: QuotaEdgeApp
            private set
    }
}
