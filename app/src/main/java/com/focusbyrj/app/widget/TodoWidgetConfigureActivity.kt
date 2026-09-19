package com.focusbyrj.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
                        if (appWidgetId != 0) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    initialConfig: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
    onCancel: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf(initialConfig.theme) }
    var selectedAccent by remember { mutableStateOf(initialConfig.accent) }
    var opacity by remember { mutableStateOf(initialConfig.opacityPercent.toFloat()) }
    var cornerRadius by remember { mutableStateOf(initialConfig.cornerRadiusDp.toFloat()) }

    val currentConfig = remember(selectedTheme, selectedAccent, opacity, cornerRadius) {
        WidgetConfig(
            theme = selectedTheme,
            accent = selectedAccent,
            opacityPercent = opacity.toInt(),
            cornerRadiusDp = cornerRadius.toInt()
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Customize Widget",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onSave(currentConfig) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(currentConfig.accentColorInt)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Apply",
                            color = if (selectedAccent == WidgetAccent.MONOCHROME && !selectedTheme.isDark) Color.White else Color(0xFF121516),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Live Preview Card
            Text(
                text = "LIVE PREVIEW",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            WidgetLivePreview(config = currentConfig)

            // Theme Options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Theme Palette",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    WidgetTheme.values().forEach { theme ->
                        val isSelected = theme == selectedTheme
                        val baseColor = Color(android.graphics.Color.parseColor(theme.baseColorHex))
                        val borderColor by animateColorAsState(
                            if (isSelected) Color(currentConfig.accentColorInt) else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            label = "border"
                        )

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = baseColor,
                            border = androidx.compose.foundation.BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
                            modifier = Modifier
                                .width(110.dp)
                                .clickable { selectedTheme = theme }
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(baseColor)
                                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = if (theme.isDark) Color.White else Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = theme.displayName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (theme.isDark) Color.White else Color(0xFF121516),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Accent Color Options
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FormatPaint,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Accent Highlight",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WidgetAccent.values().forEach { accent ->
                        val isSelected = accent == selectedAccent
                        val color = Color(android.graphics.Color.parseColor(accent.hex))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedAccent = accent }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = if (accent == WidgetAccent.MONOCHROME) Color.Black else Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = accent.displayName.split(" ").first(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (isSelected) 1f else 0.7f)
                            )
                        }
                    }
                }
            }

            // Opacity Slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Opacity,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Background Opacity",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(currentConfig.accentColorInt).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${opacity.toInt()}%",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(currentConfig.accentColorInt),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                MinimalProfessionalSlider(
                    value = opacity,
                    onValueChange = { opacity = it },
                    valueRange = 20f..100f,
                    accentColor = Color(currentConfig.accentColorInt)
                )
            }

            // Corner Radius Slider
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RoundedCorner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Corner Curvature",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(currentConfig.accentColorInt).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${cornerRadius.toInt()} dp",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(currentConfig.accentColorInt),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                MinimalProfessionalSlider(
                    value = cornerRadius,
                    onValueChange = { cornerRadius = it },
                    valueRange = 0f..32f,
                    accentColor = Color(currentConfig.accentColorInt)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (config.theme.isDark) {
                if (config.theme == WidgetTheme.OLED) Color(0xFF26292B) else Color(0xFF2E3338)
            } else {
                Color(0xFFD6D9DC)
            }
        ),
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
                    showDivider = false
                )
            }
        }
    }
}

@Composable
fun MinimalProfessionalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val normalized = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val density = LocalDensity.current
        val thumbRadiusDp = 9.dp
        val thumbRadiusPx = with(density) { thumbRadiusDp.toPx() }
        val usableWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        val thumbOffsetPx = thumbRadiusPx + normalized * usableWidth

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val ratio = ((offset.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                        val newValue = valueRange.start + ratio * (valueRange.endInclusive - valueRange.start)
                        onValueChange(newValue)
                    }
                }
                .pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val ratio = ((change.position.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                            val newValue = valueRange.start + ratio * (valueRange.endInclusive - valueRange.start)
                            onValueChange(newValue)
                        }
                    )
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Inactive subtle background track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            // Active accent colored track
            Box(
                modifier = Modifier
                    .width(with(density) { thumbOffsetPx.toDp() })
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(accentColor)
            )

            // Sleek circular thumb
            Box(
                modifier = Modifier
                    .offset(x = with(density) { (thumbOffsetPx - thumbRadiusPx).toDp() })
                    .size(18.dp)
                    .shadow(elevation = if (isDragging) 6.dp else 2.dp, shape = CircleShape)
                    .background(Color.White, CircleShape)
                    .border(
                        width = 3.dp,
                        color = accentColor,
                        shape = CircleShape
                    )
            )
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
    showDivider: Boolean = true
) {
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
                        fontSize = 13.5.sp,
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
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isDone) secondaryText.copy(alpha = 0.45f) else accentColor
                    )
                    if (badge.isNotBlank()) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
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
