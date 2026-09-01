package com.quotaedge.galaxy.widget

import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.quotaedge.galaxy.MainActivity
import kotlinx.coroutines.runBlocking

class QuotaWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = context.getSharedPreferences("quota_cache", Context.MODE_PRIVATE)
            val claude = prefs.getString("claude_line", "")!!.trim()
            val codex = prefs.getString("codex_line", "")!!.trim()
            val grok = prefs.getString("grok_line", "")!!.trim()
            val open = Intent(context, MainActivity::class.java)
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .background(ColorProvider(Color.parseColor("#17171A")))
                        .padding(12.dp)
                        .clickable(actionStartActivity(open)),
                ) {
                    Text(
                        "Quota Edge",
                        style = TextStyle(
                            color = ColorProvider(Color.parseColor("#8E8E93")),
                            fontSize = 11.sp,
                        ),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    var shown = false
                    if (claude.isNotEmpty()) {
                        Text("● $claude", style = lineStyle("#D97757"), maxLines = 1)
                        shown = true
                    }
                    if (codex.isNotEmpty()) {
                        if (shown) Spacer(GlanceModifier.height(2.dp))
                        Text("● $codex", style = lineStyle("#10A37F"), maxLines = 1)
                        shown = true
                    }
                    if (grok.isNotEmpty()) {
                        if (shown) Spacer(GlanceModifier.height(2.dp))
                        Text("● $grok", style = lineStyle("#E8E8EA"), maxLines = 1)
                        shown = true
                    }
                    if (!shown) {
                        Text(
                            "동기화된 항목 없음",
                            style = TextStyle(
                                color = ColorProvider(Color.parseColor("#8E8E93")),
                                fontSize = 11.sp,
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private fun lineStyle(hex: String) = TextStyle(
            color = ColorProvider(Color.parseColor(hex)),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )

        fun updateAll(context: Context) {
            runBlocking {
                val manager = GlanceAppWidgetManager(context)
                val widget = QuotaWidget()
                manager.getGlanceIds(QuotaWidget::class.java).forEach { glanceId ->
                    widget.update(context, glanceId)
                }
            }
        }
    }
}

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
