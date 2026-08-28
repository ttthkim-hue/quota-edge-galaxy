package com.quotaedge.galaxy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.quotaedge.galaxy.service.UsageSyncService
import com.quotaedge.galaxy.ui.MainScreen
import com.quotaedge.galaxy.ui.theme.BgDark

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UsageSyncService.start(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgDark)) {
                MainScreen()
            }
        }
    }
}
