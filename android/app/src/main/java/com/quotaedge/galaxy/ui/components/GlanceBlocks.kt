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
import com.quotaedge.galaxy.ui.theme.ClaudeOrange
import com.quotaedge.galaxy.ui.theme.CodexGreen
import com.quotaedge.galaxy.ui.theme.TextMuted

@Composable
fun ProviderGlanceBlock(
    label: String,
    color: Color,
    quota: ProviderQuota,
    fontSize: TextUnit = 11.sp,
    mono: Boolean = true,
) {
    val ff = if (mono) FontFamily.Monospace else FontFamily.Default
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("●", color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(4.dp))
            Text(label, color = color, fontSize = fontSize, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(6.dp))
            Text(
                quota.line1().trim(),
                color = Color.White,
                fontSize = fontSize,
                fontFamily = ff,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            quota.line2(),
            color = TextMuted,
            fontSize = (fontSize.value - 1).sp,
            fontFamily = ff,
            modifier = Modifier.width(120.dp),
        )
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
    Column {
        ProviderGlanceBlock("C", ClaudeOrange, claude, fontSize)
        Spacer(Modifier.size(3.dp))
        ProviderGlanceBlock("X", CodexGreen, codex, fontSize)
    }
}
