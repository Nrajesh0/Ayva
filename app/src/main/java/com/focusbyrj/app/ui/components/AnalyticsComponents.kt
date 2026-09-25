package com.focusbyrj.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.util.HeatmapTheme
import com.focusbyrj.app.util.UserProfile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HeatmapAndStreaksWidget(
    dailyUsage: Map<Int, Long>,
    theme: HeatmapTheme,
    profile: UserProfile,
    currentStreak: Int = profile.currentStreak,
    longestStreak: Int = profile.longestStreak,
    streakTypeLabel: String = "Streaks",
    isDrillMode: Boolean = false,
    onToggleStreakSource: (() -> Unit)? = null
) {
    var selectedTileInfo by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Header with Mode Indicator & Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isDrillMode) "Activity • Drills" else "Activity • Focus",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Last 30 days consistency",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                if (onToggleStreakSource != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onToggleStreakSource() }
                            .testTag("streak_source_toggle")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = "Switch streak source",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isDrillMode) "Focus" else "Drills",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Streak Indicators (Seamless studio layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🔥", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "Current Streak",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$currentStreak ${if (currentStreak == 1) "day" else "days"}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🏆", fontSize = 20.sp)
                    Column {
                        Text(
                            text = "Best Streak",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "$longestStreak ${if (longestStreak == 1) "day" else "days"}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Full-Width 30-Day Activity Heatmap Grid
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                for (row in 0..4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        for (col in 0..5) {
                            val daysAgo = (4 - row) * 6 + (5 - col)
                            val targetCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
                            val usage = dailyUsage[targetCal.get(Calendar.DAY_OF_YEAR)] ?: 0L

                            val emptyTileColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

                            val boxColor = if (isDrillMode) {
                                when {
                                    usage <= 0L -> emptyTileColor
                                    usage == 1L -> theme.colors[1]
                                    usage in 2L..3L -> theme.colors[2]
                                    usage in 4L..5L -> theme.colors[3]
                                    else -> theme.colors[4]
                                }
                            } else {
                                when {
                                    usage <= 0L -> emptyTileColor
                                    usage < 15 * 60 * 1000L -> theme.colors[1]
                                    usage < 30 * 60 * 1000L -> theme.colors[2]
                                    usage < 60 * 60 * 1000L -> theme.colors[3]
                                    else -> theme.colors[4]
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.2f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(boxColor)
                                    .clickable {
                                        val dateStr = if (daysAgo == 0) "Today" else if (daysAgo == 1) "Yesterday" else {
                                            SimpleDateFormat("MMM d", Locale.getDefault()).format(targetCal.time)
                                        }
                                        selectedTileInfo = if (isDrillMode) {
                                            if (usage <= 0L) "$dateStr: 0 drills" else "$dateStr: $usage drill${if (usage > 1) "s" else ""}"
                                        } else {
                                            val mins = usage / (60 * 1000L)
                                            if (mins <= 0L) "$dateStr: 0m focus" else "$dateStr: ${mins}m focus"
                                        }
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer with Tile Details and Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedTileInfo ?: (if (isDrillMode) "Tap a tile to view drills" else "Tap a tile to view focus"),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (selectedTileInfo != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1
                )

                // Color ramp legend
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Less",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors[1]))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors[2]))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors[3]))
                    Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(theme.colors[4]))
                    Text(
                        text = "More",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
