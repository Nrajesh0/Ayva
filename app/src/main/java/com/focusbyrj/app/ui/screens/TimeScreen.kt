package com.focusbyrj.app.ui.screens

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.focusbyrj.app.ui.theme.AccentViolet
import com.focusbyrj.app.ui.theme.BorderGlass
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import com.focusbyrj.app.ui.theme.SurfaceDark
import com.focusbyrj.app.ui.theme.SurfaceVariantDark
import com.focusbyrj.app.util.AppUsageData
import com.focusbyrj.app.util.UsageStatsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun TimeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPermission = remember { UsageStatsHelper.hasUsageStatsPermission(context) }
    var usageStats by remember { mutableStateOf<List<AppUsageData>>(emptyList()) }
    var totalTimeMs by remember { mutableStateOf(0L) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0L) }

    suspend fun loadStats() {
        if (UsageStatsHelper.hasUsageStatsPermission(context)) {
            val stats = withContext(Dispatchers.IO) {
                UsageStatsHelper.getTodayUsageStats(context)
                    .filter { it.packageName != "android" && it.packageName != "com.android.systemui" && it.timeInForegroundMs >= 1000L }
            }
            usageStats = stats
            totalTimeMs = stats.sumOf { it.timeInForegroundMs }
        }
    }

    // Auto-refresh periodically while looking at the screen
    LaunchedEffect(hasPermission, refreshTrigger) {
        while (true) {
            loadStats()
            delay(10_000L) // Refresh every 10 seconds for real-time live usage
        }
    }

    // Refresh whenever user returns to the app / screen
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger = System.currentTimeMillis()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Permission Required", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(16.dp))
            Text("FocusLock needs Usage Access to display your screen time.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { UsageStatsHelper.requestUsageStatsPermission(context) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val hours = totalTimeMs / (1000 * 60 * 60)
    val minutes = (totalTimeMs / (1000 * 60)) % 60

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(24.dp)
            ) {
                IconButton(
                    onClick = { refreshTrigger = System.currentTimeMillis() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 12.dp, y = (-12).dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh screen time",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "TOTAL ACTIVE SCREEN TIME",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp,
                                fontSize = 10.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(hours.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("h", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp, end = 8.dp))
                        Text(minutes.toString(), style = MaterialTheme.typography.displayLarge.copy(fontSize = 52.sp, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                        Text("m", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp, start = 4.dp))
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    if (usageStats.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            val colors = listOf(Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF87171), Color(0xFFA78BFA), Color(0xFF2DD4BF), Color(0xFFFB923C))
                            usageStats.take(6).forEachIndexed { index, stat ->
                                val weight = (stat.timeInForegroundMs.toFloat() / totalTimeMs.toFloat()).coerceAtLeast(0.01f)
                                Box(modifier = Modifier.weight(weight).fillMaxHeight().background(colors[index % colors.size]))
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "App Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${usageStats.size} apps recorded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(14.dp))
        }
        
        val colors = listOf(Color(0xFF60A5FA), Color(0xFF34D399), Color(0xFFFBBF24), Color(0xFFF87171), Color(0xFFA78BFA), Color(0xFF2DD4BF), Color(0xFFFB923C))
        items(
            count = usageStats.size,
            key = { index -> usageStats[index].packageName },
            contentType = { "AppUsageItem" }
        ) { index ->
            val stat = usageStats[index]
            val percentage = if (totalTimeMs > 0) stat.timeInForegroundMs.toFloat() / totalTimeMs else 0f
            val h = stat.timeInForegroundMs / (1000 * 60 * 60)
            val m = (stat.timeInForegroundMs / (1000 * 60)) % 60
            val s = (stat.timeInForegroundMs / 1000) % 60
            val timeStr = when {
                h > 0 -> "${h}h ${m}m"
                m > 0 -> "${m}m"
                else -> "${s}s"
            }
            
            val pm = context.packageManager
            val icon = remember(stat.packageName) { com.focusbyrj.app.util.ImageUtils.getAppIcon(pm, stat.packageName) }
            
            AppUsageItem(
                appName = stat.appName, 
                icon = icon,
                time = timeStr, 
                percentage = percentage, 
                color = colors[index % colors.size]
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
fun AppUsageItem(appName: String, icon: androidx.compose.ui.graphics.ImageBitmap?, time: String, percentage: Float, color: Color) {
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                RoundedCornerShape(20.dp)
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(color.copy(alpha = 0.15f))
                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(13.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    androidx.compose.foundation.Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(7.dp)
                    )
                } else {
                    Text(appName.take(1).uppercase(), color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(14.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Mini indicator bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(percentage.coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    time,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${(percentage * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = color
                )
            }
        }
    }
}
