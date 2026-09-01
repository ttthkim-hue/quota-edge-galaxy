package com.quotaedge.galaxy.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quotaedge.galaxy.data.ProviderQuota
import com.quotaedge.galaxy.data.UsageSnapshot
import com.quotaedge.galaxy.ui.theme.ClaudeOrange
import com.quotaedge.galaxy.ui.theme.CodexGreen
import com.quotaedge.galaxy.ui.theme.GrokGray
import com.quotaedge.galaxy.ui.theme.TextMuted

@Composable
fun ProviderGlanceBlock(
    color: Color,
    quota: ProviderQuota,
    fontSize: TextUnit = 11.sp,
) {
    val line = quota.glanceLine()
    if (line.isBlank()) return
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(
                line,
                color = color,
                fontSize = fontSize,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
        if (quota.error != null) {
            Text(quota.error.take(40), color = Color(0xFFFF453A), fontSize = 9.sp)
        }
    }
}

@Composable
fun DualGlancePanel(
    claude: ProviderQuota,
    codex: ProviderQuota,
    fontSize: TextUnit = 11.sp,
) {
    TripleGlancePanel(claude, codex, null, fontSize)
}

@Composable
fun TripleGlancePanel(
    claude: ProviderQuota,
    codex: ProviderQuota,
    grok: ProviderQuota?,
    fontSize: TextUnit = 11.sp,
) {
    val items = listOfNotNull(
        claude.takeIf { it.isGlanceReady() }?.let { ClaudeOrange to it },
        codex.takeIf { it.isGlanceReady() }?.let { CodexGreen to it },
        grok?.takeIf { it.isGlanceReady() }?.let { GrokGray to it },
    )
    Column {
        if (items.isEmpty()) {
            Text("동기화된 항목 없음", color = TextMuted, fontSize = 12.sp)
            return
        }
        items.forEachIndexed { index, (color, quota) ->
            if (index > 0) Spacer(Modifier.size(2.dp))
            ProviderGlanceBlock(color, quota, fontSize)
        }
    }
}

@Composable
fun SnapshotGlancePanel(snapshot: UsageSnapshot, fontSize: TextUnit = 11.sp) {
    TripleGlancePanel(snapshot.claude, snapshot.codex, snapshot.grok, fontSize)
}
