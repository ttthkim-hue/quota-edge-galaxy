package com.quotaedge.galaxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quotaedge.galaxy.MainActivity
import com.quotaedge.galaxy.QuotaEdgeApp
import com.quotaedge.galaxy.R
import com.quotaedge.galaxy.widget.QuotaWidget
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsageSyncService : Service() {
    private val scope = CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> Log.e(TAG, "sync scope error", e) },
    )
    private var loopJob: Job? = null
    private var overlayJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(QuotaEdgeApp.instance.snapshot.value))
        overlayJob?.cancel()
        overlayJob = scope.launch {
            val app = QuotaEdgeApp.instance
            combine(
                app.tokenStore.overlayEnabled,
                app.tokenStore.statusGlanceEnabled,
                app.snapshot,
            ) { overlay, glance, snap ->
                Triple(overlay, glance, snap)
            }.collect { (overlay, glance, snap) ->
                withContext(Dispatchers.Main) {
                    if (overlay || glance) OverlayHud.show(this@UsageSyncService, snap)
                    else OverlayHud.hide(this@UsageSyncService)
                }
            }
        }
        loopJob?.cancel()
        loopJob = scope.launch {
            while (isActive) {
                refresh()
                delay(60_000)
            }
        }
        return START_STICKY
    }

    private suspend fun refresh() {
        runCatching {
            val app = QuotaEdgeApp.instance
            val snap = app.usageRepository.refresh()
            app.updateSnapshot(snap)
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(snap))
            QuotaWidget.updateAll(this)
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) return
            Log.e(TAG, "refresh failed", it)
        }
    }

    private fun buildNotification(snap: com.quotaedge.galaxy.data.UsageSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = listOf(snap.claude.glanceLine(), snap.codex.glanceLine())
            .filter { it.isNotBlank() }
            .map { "● $it" }
        val body = lines.joinToString("\n").ifBlank { "동기화된 항목 없음 — 앱에서 로그인" }
        val summary = lines.joinToString("  ").ifBlank { "동기화 대기" }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle("Quota Edge")
            .setContentText(summary)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Quota sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Claude & Codex usage refresh"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    override fun onDestroy() {
        loopJob?.cancel()
        overlayJob?.cancel()
        // Keep HUD attached across brief service restarts; wake/user-present will remount if needed.
        super.onDestroy()
    }

    companion object {
        private const val TAG = "QuotaEdge"
        private const val CHANNEL_ID = "quota_sync"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            runCatching {
                context.startForegroundService(Intent(context, UsageSyncService::class.java))
            }.onFailure { Log.e(TAG, "UsageSyncService.start failed", it) }
        }
    }
}
