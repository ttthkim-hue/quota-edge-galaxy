package com.quotaedge.galaxy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
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
            val c1 = prefs.getString("claude_l1", "--%/--%")!!
            val c2 = prefs.getString("claude_l2", "---m/-.-d")!!
            val x1 = prefs.getString("codex_l1", "--%/--%")!!
            val x2 = prefs.getString("codex_l2", "---m/-.-d")!!
            val open = Intent(context, MainActivity::class.java)
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .background(ColorProvider(0xFF17171A))
                        .padding(12.dp)
                        .clickable(actionStartActivity(open)),
                ) {
                    Text(
                        "Quota Edge",
                        style = TextStyle(color = ColorProvider(0xFF8E8E93), fontSize = 11.sp),
                    )
                    Spacer(GlanceModifier.height(6.dp))
                    Text(
                        "● C $c1",
                        style = TextStyle(
                            color = ColorProvider(0xFFD97757),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        "  $c2",
                        style = TextStyle(color = ColorProvider(0xFF8E8E93), fontSize = 10.sp),
                    )
                    Spacer(GlanceModifier.height(4.dp))
                    Text(
                        "● X $x1",
                        style = TextStyle(
                            color = ColorProvider(0xFF10A37F),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Text(
                        "  $x2",
                        style = TextStyle(color = ColorProvider(0xFF8E8E93), fontSize = 10.sp),
                    )
                }
            }
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val cn = ComponentName(context, QuotaWidgetReceiver::class.java)
            val ids = mgr.getAppWidgetIds(cn)
            if (ids.isEmpty()) return
            runBlocking {
                ids.forEach { widgetId ->
                    QuotaWidget().update(context, GlanceId(widgetId))
                }
            }
        }
    }
}

class QuotaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuotaWidget()
}
