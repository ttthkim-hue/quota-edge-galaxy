package com.quotaedge.galaxy.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/** Restart foreground sync after reboot so the HUD keeps working. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) {
            return
        }
        Log.i("QuotaEdge", "boot → start UsageSyncService")
        runCatching { UsageSyncService.start(context.applicationContext) }
            .onFailure { Log.e("QuotaEdge", "boot start failed", it) }
    }
}
