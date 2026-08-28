package com.quotaedge.galaxy.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.quotaedge.galaxy.QuotaEdgeApp
import com.quotaedge.galaxy.data.ProviderQuota
import com.quotaedge.galaxy.data.UsageSnapshot

/**
 * Always-on HUD under the status-bar clock.
 * Transparent (no black plate). Unsynced providers are omitted.
 *
 * On SCREEN_ON we never tear the window down while locked; that used to wipe
 * the HUD until the user manually synced again.
 */
object OverlayHud {
    private const val TAG = "QuotaEdge"
    private const val CLAUDE = 0xFFD97757.toInt()
    private const val CODEX = 0xFF10A37F.toInt()

    private var root: LinearLayout? = null
    private var windowManager: WindowManager? = null
    private var claudeView: TextView? = null
    private var codexView: TextView? = null
    private var wakeReceiver: BroadcastReceiver? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isShowing(): Boolean = root != null

    fun show(context: Context, snapshot: UsageSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { show(context, snapshot) }
            return
        }
        val host = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(host)) {
            Log.w(TAG, "overlay skipped: SYSTEM_ALERT_WINDOW not granted")
            return
        }
        registerWakeReceiver(host)
        if (root == null) {
            attach(host)
        }
        bind(snapshot)
        applyLockVisibility(host)
    }

    /** Wake-safe: attach/bind without tearing down an existing window. */
    fun ensureVisible(context: Context, snapshot: UsageSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { ensureVisible(context, snapshot) }
            return
        }
        show(context, snapshot)
    }

    /** Full recreate (fixes stuck surface after sleep). */
    fun remount(context: Context, snapshot: UsageSnapshot) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { remount(context, snapshot) }
            return
        }
        val host = context.applicationContext
        Log.i(TAG, "overlay remount locked=${isKeyguardLocked(host)}")
        hide(host, unregister = false)
        show(host, snapshot)
    }

    fun hide(context: Context, unregister: Boolean = true) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { hide(context, unregister) }
            return
        }
        val view = root
        if (view != null) {
            try {
                (windowManager ?: context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .removeView(view)
            } catch (e: Exception) {
                Log.w(TAG, "overlay remove failed", e)
            }
        }
        root = null
        windowManager = null
        claudeView = null
        codexView = null
        if (unregister) {
            unregisterWakeReceiver(context.applicationContext)
        }
        Log.i(TAG, "overlay hidden")
    }

    private fun attach(app: Context) {
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = app.resources.displayMetrics.density

        val claude = makeLine(app, CLAUDE)
        val codex = makeLine(app, CODEX)
        claude.text = "●"
        codex.text = "●"

        val column = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            setBackgroundColor(Color.TRANSPARENT)
            elevation = 0f
            addView(claude)
            addView(codex)
            visibility = View.VISIBLE
        }

        val statusH = statusBarHeight(app)
        val yUnderClock = statusH + (2 * density).toInt()
        @Suppress("DEPRECATION")
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (12 * density).toInt()
            y = yUnderClock
            alpha = 1f
            title = "QuotaEdgeHud"
            windowAnimations = 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        try {
            wm.addView(column, params)
            root = column
            windowManager = wm
            claudeView = claude
            codexView = codex
            Log.i(TAG, "overlay attached y=$yUnderClock")
        } catch (e: Exception) {
            Log.e(TAG, "overlay attach failed", e)
            root = null
            windowManager = null
        }
    }

    private fun bind(snapshot: UsageSnapshot) {
        bindLine(claudeView, snapshot.claude)
        bindLine(codexView, snapshot.codex)
        val anyVisible =
            claudeView?.visibility == View.VISIBLE || codexView?.visibility == View.VISIBLE
        if (anyVisible) root?.visibility = View.VISIBLE
        else if (root != null && !isKeyguardLocked(root!!.context)) root?.visibility = View.GONE
    }

    private fun applyLockVisibility(host: Context) {
        val locked = isKeyguardLocked(host)
        val lockOk = QuotaEdgeApp.instance.tokenStore.isLockScreenEnabledSync()
        if (locked && !lockOk) {
            root?.visibility = View.GONE
        } else if (root != null) {
            val anyVisible =
                claudeView?.visibility == View.VISIBLE || codexView?.visibility == View.VISIBLE
            if (anyVisible) root?.visibility = View.VISIBLE
        }
    }

    private fun bindLine(view: TextView?, quota: ProviderQuota) {
        val line = quota.glanceLine()
        if (view == null) return
        if (line.isBlank()) {
            view.visibility = View.GONE
            view.text = ""
        } else {
            view.visibility = View.VISIBLE
            view.text = "● $line"
        }
    }

    private fun makeLine(context: Context, color: Int): TextView {
        return TextView(context).apply {
            setTextColor(color)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            paint.isFakeBoldText = true
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            setShadowLayer(3.5f, 0f, 1f, 0xE6000000.toInt())
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setLineSpacing(0f, 1f)
        }
    }

    private fun statusBarHeight(context: Context): Int {
        val id = context.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) context.resources.getDimensionPixelSize(id)
        else (48 * context.resources.displayMetrics.density).toInt()
    }

    private fun isKeyguardLocked(context: Context): Boolean {
        val km = context.getSystemService(KeyguardManager::class.java)
        return km?.isKeyguardLocked == true
    }

    private fun onWake(context: Context, unlocked: Boolean) {
        val host = context.applicationContext
        UsageSyncService.start(host)
        val snap = QuotaEdgeApp.instance.snapshot.value
        if (unlocked) {
            remount(host, snap)
        } else {
            ensureVisible(host, snap)
        }
    }

    private fun registerWakeReceiver(app: Context) {
        if (wakeReceiver != null) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        Log.i(TAG, "SCREEN_ON → ensureVisible")
                        onWake(context, unlocked = false)
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        Log.i(TAG, "USER_PRESENT → remount")
                        onWake(context, unlocked = true)
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            app.registerReceiver(receiver, filter)
        }
        wakeReceiver = receiver
        Log.i(TAG, "wake receiver registered")
    }

    private fun unregisterWakeReceiver(app: Context) {
        val receiver = wakeReceiver ?: return
        runCatching { app.unregisterReceiver(receiver) }
        wakeReceiver = null
    }
}
