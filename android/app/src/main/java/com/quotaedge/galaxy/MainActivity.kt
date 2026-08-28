package com.quotaedge.galaxy

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.core.content.ContextCompat
import com.quotaedge.galaxy.service.OverlayHud
import com.quotaedge.galaxy.service.UsageSyncService
import com.quotaedge.galaxy.ui.MainScreen
import com.quotaedge.galaxy.ui.theme.BgDark

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* optional — sync still works without banners */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeRequestNotifications()
        UsageSyncService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgDark)) {
                MainScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UsageSyncService.start(this)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            // Remount only when unlocked so screen-off/on does not wipe the HUD.
            OverlayHud.remount(this, (application as QuotaEdgeApp).snapshot.value)
        }
    }

    private fun maybeRequestNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
