package com.quotaedge.galaxy.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material3.OutlinedButton
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
import com.quotaedge.galaxy.auth.ClaudeOAuth
import com.quotaedge.galaxy.auth.CodexOAuth
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
    val overlayOn by app.tokenStore.overlayEnabled.collectAsState(initial = true)
    val lockOn by app.tokenStore.lockScreenEnabled.collectAsState(initial = true)
    val glanceOn by app.tokenStore.statusGlanceEnabled.collectAsState(initial = true)

    var claudeLinked by remember { mutableStateOf(app.tokenStore.isClaudeLinked()) }
    var codexLinked by remember { mutableStateOf(app.tokenStore.isCodexLinked()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var statusMsg by remember { mutableStateOf<String?>(null) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        statusMsg = msg
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Quota Edge", fontWeight = FontWeight.Bold)
                        Text(
                            "Claude · Codex 남은 한도 · v1.1",
                            color = TextMuted,
                            fontSize = 12.sp,
                        )
                    }
                },
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
            val debugStatus = remember(snapshot.updatedAtEpochMs) {
                context.getSharedPreferences("quota_debug", android.content.Context.MODE_PRIVATE)
                    .getString("last_status", null)
            }
            if (debugStatus != null) {
                Text(debugStatus, color = TextMuted, fontSize = 12.sp)
            }
            if (statusMsg != null) {
                Text(statusMsg!!, color = TextMuted, fontSize = 12.sp)
            }
            snapshot.claude.error?.let {
                Text("Claude 오류: $it", color = Color(0xFFFF453A), fontSize = 12.sp)
            }
            snapshot.codex.error?.let {
                Text("Codex 오류: $it", color = Color(0xFFFF453A), fontSize = 12.sp)
            }

            Button(
                onClick = {
                    UsageSyncService.start(context)
                    scope.launch {
                        busy = "sync"
                        runCatching { app.updateSnapshot(app.usageRepository.refresh()) }
                            .onSuccess {
                                val st = context.getSharedPreferences("quota_debug", android.content.Context.MODE_PRIVATE)
                                    .getString("last_status", "동기화 완료")
                                toast(st ?: "동기화 완료")
                                claudeLinked = app.tokenStore.isClaudeLinked()
                                codexLinked = app.tokenStore.isCodexLinked()
                            }
                            .onFailure { toast(it.message ?: "동기화 실패") }
                        busy = null
                    }
                },
                enabled = busy == null,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
            ) { Text(if (busy == "sync") "동기화 중…" else "지금 동기화") }

            SettingsCard("연동 — Claude") {
                Text(
                    if (claudeLinked) "상태: 연동됨" else "상태: 미연동",
                    color = if (claudeLinked) ClaudeOrange else TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    "버튼을 누르면 앱 안에서 Claude 로그인 화면이 열립니다. 승인 후 자동 연결됩니다.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = "claude"
                            runCatching { ClaudeOAuth.login(context, app.tokenStore) }
                                .onSuccess {
                                    claudeLinked = true
                                    toast("Claude 연동 완료")
                                    UsageSyncService.start(context)
                                    app.updateSnapshot(app.usageRepository.refresh())
                                }
                                .onFailure { toast("Claude 로그인 실패: ${it.message}") }
                            busy = null
                        }
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeOrange),
                ) {
                    Text(
                        when {
                            busy == "claude" -> "로그인 대기 중…"
                            claudeLinked -> "Claude 다시 로그인"
                            else -> "Claude로 로그인"
                        },
                    )
                }
                if (claudeLinked) {
                    OutlinedButton(
                        onClick = {
                            app.tokenStore.clearClaude()
                            claudeLinked = false
                            toast("Claude 연동 해제")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Claude 연동 해제") }
                }
            }

            SettingsCard("연동 — Codex") {
                Text(
                    if (codexLinked) "상태: 연동됨" else "상태: 미연동",
                    color = if (codexLinked) CodexGreen else TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    "버튼을 누르면 앱 안에서 ChatGPT 로그인 화면이 열립니다. 승인 후 자동 연결됩니다.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                Button(
                    onClick = {
                        scope.launch {
                            busy = "codex"
                            runCatching { CodexOAuth.login(context, app.tokenStore) }
                                .onSuccess {
                                    codexLinked = true
                                    toast("Codex 연동 완료")
                                    UsageSyncService.start(context)
                                    app.updateSnapshot(app.usageRepository.refresh())
                                }
                                .onFailure { toast("Codex 로그인 실패: ${it.message}") }
                            busy = null
                        }
                    },
                    enabled = busy == null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = CodexGreen),
                ) {
                    Text(
                        when {
                            busy == "codex" -> "로그인 대기 중…"
                            codexLinked -> "Codex 다시 로그인"
                            else -> "Codex로 로그인"
                        },
                    )
                }
                if (codexLinked) {
                    OutlinedButton(
                        onClick = {
                            app.tokenStore.clearCodex()
                            codexLinked = false
                            toast("Codex 연동 해제")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Codex 연동 해제") }
                }
            }

            SettingsCard("표시") {
                ToggleRow("상시 표시 (다른 앱 위에)", overlayOn || glanceOn) { on ->
                    scope.launch {
                        app.tokenStore.setOverlayEnabled(on)
                        app.tokenStore.setStatusGlanceEnabled(on)
                    }
                    if (on) {
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
                ToggleRow("잠금화면에도 표시", lockOn) {
                    scope.launch {
                        app.tokenStore.setLockScreenEnabled(it)
                        if (overlayOn || glanceOn) OverlayService.start(context)
                    }
                }
                Text(
                    "시계 아래에 글자만 표시됩니다. ‘다른 앱 위에 표시’를 허용하세요. One UI는 잠금화면에서 오버레이를 숨길 수 있습니다.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
            }

            SettingsCard("안정성") {
                Text(
                    "재부팅 후에도 동기화가 다시 시작됩니다. 배터리 최적화를 끄면 백그라운드 갱신이 더 안정적입니다.",
                    color = TextMuted,
                    fontSize = 12.sp,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("배터리 최적화 제외") }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("미리보기 (남은% · 미연동은 미표기)", color = TextMuted, fontSize = 12.sp)
            DualGlancePanel(snapshot.claude, snapshot.codex, fontSize = 13.sp)
            if (snapshot.claude.isGlanceReady() || snapshot.codex.isGlanceReady()) {
                Text("상세", color = TextMuted, fontSize = 12.sp)
                if (snapshot.claude.connected) {
                    Text("Claude\n${snapshot.claude.detailSummary()}", color = ClaudeOrange, fontSize = 12.sp)
                }
                if (snapshot.codex.connected) {
                    Text("Codex\n${snapshot.codex.detailSummary()}", color = CodexGreen, fontSize = 12.sp)
                }
            }
            Text(
                "Plus: 5h%/주간% · 리셋 m/d  ·  Pro: 주간만 (최대 7.0d)",
                color = TextMuted,
                fontSize = 11.sp,
            )
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
