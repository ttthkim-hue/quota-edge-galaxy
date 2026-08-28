package com.quotaedge.galaxy.service

import android.content.Context
import com.quotaedge.galaxy.QuotaEdgeApp

/** Compatibility entry used by the settings toggles / activity lifecycle. */
object OverlayService {
    fun start(context: Context) {
        // Prefer ensure/remount-safe path — never wipe HUD while locked.
        OverlayHud.ensureVisible(context, QuotaEdgeApp.instance.snapshot.value)
    }

    fun stop(context: Context) {
        OverlayHud.hide(context)
    }

    fun refresh(context: Context) {
        OverlayHud.show(context, QuotaEdgeApp.instance.snapshot.value)
    }
}
