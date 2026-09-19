package com.focusbyrj.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.GamificationHaptics
import com.focusbyrj.app.ui.components.DuolingoBoltIcon
import kotlin.math.roundToInt

@Composable
fun AptitudeProfileCard() {
    val profile by AptitudeManager.profileFlow.collectAsState()
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    
    // Sleek Modern Colors
    val cardBg = if (isDark) Color(0xFF131F24) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF28414D) else Color(0xFFE5E5E5)
    val textColor = if (isDark) Color.White else Color(0xFF2B2B2B)
    val mutedTextColor = if (isDark) Color(0xFF8699A6) else Color(0xFF888888)
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Hero Card: Level Progress & Key Stats
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                val xpNeeded = profile.xpForNextLevel - profile.xp
                val range = (profile.xpForNextLevel - profile.xpForCurrentLevel).toFloat().takeIf { it > 0f } ?: 1f
                val progress = ((profile.xp - profile.xpForCurrentLevel).toFloat() / range).coerceIn(0f, 1f)

                // Top Level Title Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1CB0F6).copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, Color(0xFF1CB0F6).copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Lvl ${profile.level}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                color = Color(0xFF1CB0F6),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = profile.title,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF58CC02).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${profile.xp} EXP",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = Color(0xFF58CC02),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            maxLines = 1
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // Slim Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isDark) Color(0xFF243642) else Color(0xFFE5E5E5))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceAtLeast(0.001f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF58CC02))
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${profile.xp - profile.xpForCurrentLevel} / ${profile.xpForNextLevel - profile.xpForCurrentLevel} EXP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = mutedTextColor
                    )
                    Text(
                        text = if (xpNeeded > 0) "$xpNeeded XP to Lvl ${profile.level + 1}" else "Level Up Ready!",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                        color = mutedTextColor
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // Integrated 4 Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactStatItem("Level", "Lvl ${profile.level}", textColor, mutedTextColor)
                    CompactDivider(borderColor)
                    CompactStatItem("Drills", "${profile.totalDrills}", textColor, mutedTextColor)
                    CompactDivider(borderColor)
                    CompactStatItem("Questions", "${profile.totalQuestions}", textColor, mutedTextColor)
                    CompactDivider(borderColor)
                    CompactStatItem("Accuracy", "${profile.accuracy.roundToInt()}%", textColor, mutedTextColor)
                }
            }
        }

        // Streak & Consistency Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Streak Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔥", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${profile.currentStreak}d Streak",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 14.sp),
                            color = Color(0xFFFF9600)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        DuolingoBoltIcon(
                            modifier = Modifier.size(16.dp),
                            color = Color(0xFFFFC800)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${Math.max(profile.currentStreak, profile.longestStreak)}d Best",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = Color(0xFFFFC800)
                        )
                    }

                    val currentCycleDay = if (profile.currentStreak == 0) 1 else ((profile.currentStreak - 1) % 7) + 1
                    val bonusText = if (profile.currentStreak == 0) "Daily Drill" else "+${profile.streakBonusPercent}% XP"
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFFF9600).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = bonusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 11.sp),
                            color = Color(0xFFFF9600),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 7 Day Streak Circles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentCycleDay = if (profile.currentStreak == 0) 1 else ((profile.currentStreak - 1) % 7) + 1
                    for (i in 1..7) {
                        val isActive = i <= currentCycleDay
                        val circleColor = if (isActive) Color(0xFFFF9600) else borderColor
                        
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isActive) Color(0xFFFF9600) else Color.Transparent)
                                .border(1.5.dp, circleColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isActive) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Done",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    text = i.toString(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                    color = mutedTextColor
                                )
                            }
                        }
                    }
                }
            }
        }

        // Perks & Safeguards Card (Streak Freeze & Wager)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = cardBg,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Streak Freeze
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("🛡️", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Streak Freeze (${profile.streakFreezesCount}/3)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = textColor
                            )
                            Text(
                                text = "Auto-protects streak if missed",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                                color = mutedTextColor
                            )
                        }
                    }
                    if (profile.streakFreezesCount < 3) {
                        Button(
                            onClick = {
                                val success = AptitudeManager.buyStreakFreeze(1000)
                                if (success) {
                                    GamificationHaptics.playCelebration(context)
                                    Toast.makeText(context, "🛡️ Streak Freeze Equipped!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Need 1,000 Gold to equip shield", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1CB0F6))
                        ) {
                            Text("Equip (1k 🪙)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    } else {
                        Text("Equipped", fontSize = 11.sp, color = Color(0xFF58CC02), fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = borderColor.copy(alpha = 0.5f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                
                // 7-Day Wager Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Text("💰", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (profile.isWagerActive) "Wager Active (${profile.wagerDaysCompleted}/7)" else "7-Day Streak Wager",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                color = textColor
                            )
                            Text(
                                text = if (profile.isWagerActive) "Maintain streak to win 10k 🪙" else "Stake 5k 🪙 to double in 7 days",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                                color = mutedTextColor
                            )
                        }
                    }
                    if (!profile.isWagerActive) {
                        Button(
                            onClick = {
                                val success = AptitudeManager.startWager(5000)
                                if (success) {
                                    GamificationHaptics.playCelebration(context)
                                    Toast.makeText(context, "💰 Wager started! Maintain 7-day streak!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Need 5,000 Gold to start wager", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC800))
                        ) {
                            Text("Stake (5k 🪙)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2B1D00))
                        }
                    } else {
                        Text("Active", fontSize = 11.sp, color = Color(0xFFFFC800), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStatItem(label: String, value: String, textColor: Color, mutedTextColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = 15.sp),
            color = textColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp),
            color = mutedTextColor
        )
    }
}

@Composable
private fun CompactDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(color.copy(alpha = 0.4f))
    )
}

