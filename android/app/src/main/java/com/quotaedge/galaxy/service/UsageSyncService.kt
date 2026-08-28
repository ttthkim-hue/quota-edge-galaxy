package com.quotaedge.galaxy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.quotaedge.galaxy.MainActivity
import com.quotaedge.galaxy.QuotaEdgeApp
import com.quotaedge.galaxy.R
import com.quotaedge.galaxy.widget.QuotaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class UsageSyncService : Service() {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification(QuotaEdgeApp.instance.snapshot.value))
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
        val app = QuotaEdgeApp.instance
        val snap = app.usageRepository.refresh()
        app.updateSnapshot(snap)
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(snap))
        QuotaWidget.updateAll(this)
        OverlayService.refresh(this)
    }

    private fun buildNotification(snap: com.quotaedge.galaxy.data.UsageSnapshot): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = buildString {
            append("C ${snap.claude.line1()}  ${snap.claude.line2()}\n")
            append("X ${snap.codex.line1()}  ${snap.codex.line2()}")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_quota)
            .setContentTitle("Quota Edge")
            .setContentText("C ${snap.claude.line1()} ${snap.claude.line2()} · X ${snap.codex.line1()} ${snap.codex.line2()}")
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
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "quota_sync"
        private const val NOTIF_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UsageSyncService::class.java))
        }
    }
}
