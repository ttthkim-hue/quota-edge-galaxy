package com.quotaedge.galaxy.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quotaedge.galaxy.QuotaEdgeApp
import com.quotaedge.galaxy.data.UsageSnapshot
import com.quotaedge.galaxy.service.OverlayService
import com.quotaedge.galaxy.service.UsageSyncService
import com.quotaedge.galaxy.ui.components.DualGlancePanel
import com.quotaedge.galaxy.ui.theme.BgDark
import com.quotaedge.galaxy.ui.theme.ClaudeOrange
import com.quotaedge.galaxy.ui.theme.CodexGreen
import com.quotaedge.galaxy.ui.theme.SurfaceDark
import com.quotaedge.galaxy.ui.theme.TextMuted
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as QuotaEdgeApp
    val scope = rememberCoroutineScope()
    val snapshot by app.snapshot.collectAsState()
    val overlayOn by app.tokenStore.overlayEnabled.collectAsState(initial = false)
    val lockOn by app.tokenStore.lockScreenEnabled.collectAsState(initial = true)
    val glanceOn by app.tokenStore.statusGlanceEnabled.collectAsState(initial = true)

    var claudeToken by remember { mutableStateOf(app.tokenStore.getClaudeToken().orEmpty()) }
    var codexToken by remember { mutableStateOf(app.tokenStore.getCodexToken().orEmpty()) }
    var codexAccount by remember { mutableStateOf(app.tokenStore.getCodexAccountId().orEmpty()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Quota Edge", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDark, titleContentColor = Color.White),
            )
        },
        containerColor = BgDark,
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PreviewCard(snapshot)
            Button(
                onClick = {
                    UsageSyncService.start(context)
                    scope.launch {
                        app.updateSnapshot(app.usageRepository.refresh())
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
            ) { Text("지금 동기화") }

            SettingsCard("연동 — Claude") {
                OutlinedTextField(
                    value = claudeToken,
                    onValueChange = { claudeToken = it },
                    label = { Text("OAuth Bearer Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = {
                    app.tokenStore.saveClaudeToken(claudeToken)
                    UsageSyncService.start(context)
                }, colors = ButtonDefaults.buttonColors(containerColor = ClaudeOrange)) {
                    Text("Claude 저장 & 연동")
                }
            }

            SettingsCard("연동 — Codex") {
                OutlinedTextField(
                    value = codexToken,
                    onValueChange = { codexToken = it },
                    label = { Text("OAuth Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = codexAccount,
                    onValueChange = { codexAccount = it },
                    label = { Text("ChatGPT-Account-Id") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Button(onClick = {
                    app.tokenStore.saveCodexToken(codexToken)
                    app.tokenStore.saveCodexAccountId(codexAccount)
                    UsageSyncService.start(context)
                }, colors = ButtonDefaults.buttonColors(containerColor = CodexGreen)) {
                    Text("Codex 저장 & 연동")
                }
            }

            SettingsCard("표시") {
                ToggleRow("상태바 glance (시간 아래)", glanceOn) {
                    scope.launch { app.tokenStore.setStatusGlanceEnabled(it) }
                    if (it) OverlayService.start(context) else OverlayService.stop(context)
                }
                ToggleRow("잠금화면 glance", lockOn) {
                    scope.launch { app.tokenStore.setLockScreenEnabled(it) }
                }
                ToggleRow("오버레이 서비스", overlayOn) {
                    scope.launch { app.tokenStore.setOverlayEnabled(it) }
                    if (it) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                            !Settings.canDrawOverlays(context)
                        ) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        } else {
                            OverlayService.start(context)
                        }
                    } else OverlayService.stop(context)
                }
                Text(
                    "포맷: 5h%/주간% (1줄) + 142m/3.2d (리셋, 1줄)",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("배터리 최적화 제외 (백그라운드 갱신)") }
            }
        }
    }
}

@Composable
private fun PreviewCard(snapshot: UsageSnapshot) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Live glance", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            DualGlancePanel(snapshot.claude, snapshot.codex, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = Color.White)
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
