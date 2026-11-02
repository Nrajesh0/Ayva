package com.focusbyrj.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.theme.FocusByRjTheme

class TodoWidgetConfigureActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Set the result to CANCELED. This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED)

        // Find the widget id from the intent.
        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        // If this activity was started without an intent specifying which widget to configure,
        // check if any widget exists or default to id 0
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            val appWidgetManager = AppWidgetManager.getInstance(this)
            val ids = appWidgetManager.getAppWidgetIds(android.content.ComponentName(this, TodoWidgetProvider::class.java))
            appWidgetId = if (ids != null && ids.isNotEmpty()) ids[0] else 0
        }

        val currentConfig = WidgetConfigHelper.getConfig(this, appWidgetId)

        setContent {
            FocusByRjTheme {
                WidgetConfigScreen(
                    initialConfig = currentConfig,
                    onSave = { updatedConfig ->
                        WidgetConfigHelper.saveConfig(this, appWidgetId, updatedConfig)

                        // Push widget update
                        val appWidgetManager = AppWidgetManager.getInstance(this)
                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && appWidgetId != 0) {
                            TodoWidgetProvider.updateWidget(this, appWidgetManager, appWidgetId)
                        } else {
                            TodoWidgetProvider.updateAllWidgets(this)
                        }

                        // Make sure we pass back the original appWidgetId
                        val resultValue = Intent().apply {
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        }
                        setResult(RESULT_OK, resultValue)
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun WidgetConfigScreen(
    initialConfig: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
    onCancel: () -> Unit
) {
    var theme by rememberSaveable { mutableStateOf(initialConfig.theme) }
    var accent by rememberSaveable { mutableStateOf(initialConfig.accent) }
    var opacity by rememberSaveable { mutableIntStateOf(initialConfig.opacityPercent) }
    var radius by rememberSaveable { mutableIntStateOf(initialConfig.cornerRadiusDp) }
    var textSize by rememberSaveable { mutableStateOf(initialConfig.textSize) }
    val draft = WidgetConfig(theme, accent, opacity, radius, textSize)
    WidgetEditor("Tasks widget", listOf("Style"), "${theme.displayName} · ${textSize.spValue.toInt()} sp",
        onSave = { onSave(draft) }, onCancel = onCancel, preview = { WidgetLivePreview(draft) }) { section ->
        when (section) {
            0 -> {
                WidgetChoiceRow("Theme", theme, WidgetTheme.entries, { it.displayName }, { theme = it },
                    swatch = { Color(android.graphics.Color.parseColor(it.baseColorHex)) })
                WidgetChoiceRow("Accent", accent, WidgetAccent.entries, { it.displayName }, { accent = it },
                    swatch = { Color(android.graphics.Color.parseColor(it.hex)) })
                WidgetChoiceRow("Font size", textSize, WidgetTextSize.entries, { "${it.spValue.toInt()} sp" }, { textSize = it })
                WidgetSliderRow("Opacity", opacity, "%", 20..100) { opacity = it }
                WidgetSliderRow("Corner radius", radius, " dp", 0..32) { radius = it }
            }
        }
    }
}

@Composable
fun WidgetLivePreview(config: WidgetConfig) {
    val bgColor = Color(config.backgroundColorInt)
    val accentColor = Color(config.accentColorInt)
    val primaryText = Color(config.primaryTextColorInt)
    val secondaryText = Color(config.secondaryTextColorInt)
    val itemBg = Color(config.itemBackgroundColorInt)

    Surface(
        shape = RoundedCornerShape(config.cornerRadiusDp.dp),
        color = bgColor,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(config.cornerRadiusDp.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Tasks",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = primaryText
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "3",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = accentColor
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (config.accent == WidgetAccent.MONOCHROME && !config.theme.isDark) Color.White else Color(0xFF121516)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tabs (Clean professional rounded rectangle)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Today",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (config.accent == WidgetAccent.MONOCHROME && !config.theme.isDark) Color.White else Color(0xFF121516)
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (config.theme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Upcoming",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = secondaryText
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (config.theme.isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.05f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = secondaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Preview Task Rows (Clean flat professional list rows)
            Column {
                PreviewTaskRow(
                    title = "Deep work focus block",
                    due = "Today, 5:00 PM",
                    badge = "↻ daily",
                    primaryText = primaryText,
                    accentColor = accentColor,
                    secondaryText = secondaryText,
                    isDone = false,
                    titleSizeSp = config.textSize.spValue,
                    showDivider = true
                )
                PreviewTaskRow(
                    title = "Review priority targets",
                    due = "Today, 7:30 PM",
                    badge = "● persistent",
                    primaryText = primaryText,
                    accentColor = accentColor,
                    secondaryText = secondaryText,
                    isDone = false,
                    titleSizeSp = config.textSize.spValue,
                    showDivider = true
                )
                PreviewTaskRow(
                    title = "Review project milestone",
                    due = "Today, 8:30 PM",
                    badge = "● persistent",
                    primaryText = primaryText,
                    accentColor = accentColor,
                    secondaryText = secondaryText,
                    isDone = true,
                    titleSizeSp = config.textSize.spValue,
                    showDivider = false
                )
            }
        }
    }
}

@Composable
fun PreviewTaskRow(
    title: String,
    due: String,
    badge: String,
    primaryText: Color,
    accentColor: Color,
    secondaryText: Color,
    isDone: Boolean,
    titleSizeSp: Float = 14f,
    showDivider: Boolean = true
) {
    val titleSp = titleSizeSp.sp
    val subtitleSp = (titleSizeSp * 0.8f).coerceAtLeast(10f).sp
    val badgeSp = (titleSizeSp * 0.72f).coerceAtLeast(9f).sp

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isDone) accentColor else Color.Transparent)
                    .border(
                        width = if (isDone) 0.dp else 1.5.dp,
                        color = if (isDone) accentColor else secondaryText.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isDone) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF121516),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = titleSp,
                        fontWeight = FontWeight.Normal
                    ),
                    color = if (isDone) primaryText.copy(alpha = 0.45f) else primaryText
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    Text(
                        text = due,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = subtitleSp),
                        color = if (isDone) secondaryText.copy(alpha = 0.45f) else accentColor
                    )
                    if (badge.isNotBlank()) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeSp),
                            color = secondaryText.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp)
                    .height(0.8.dp)
                    .background(secondaryText.copy(alpha = 0.12f))
            )
        }
    }
}
