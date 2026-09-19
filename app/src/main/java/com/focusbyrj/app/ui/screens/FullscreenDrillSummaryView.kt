package com.focusbyrj.app.ui.screens

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject
import com.focusbyrj.app.ui.components.*
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

@Composable
fun FullscreenDrillSummaryView(
    message: ChatMessage,
    onClose: () -> Unit,
    onViewSolutions: (String) -> Unit,
    onMessageUpdate: ((ChatMessage) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Color(0xFF131F24) else Color(0xFF131F24) // Authentic Duolingo dark slate canvas
    val context = LocalContext.current
    
    val initiallyClaimed = remember(message.drillSummaryJson) {
        try { JSONObject(message.drillSummaryJson ?: "{}").optBoolean("isClaimed", false) } catch (e: Exception) { false }
    }
    
    var isClaimedState by remember(initiallyClaimed) { mutableStateOf(initiallyClaimed) }
    
    val jsonObj = remember(message.drillSummaryJson) { JSONObject(message.drillSummaryJson ?: "{}") }
    val title = jsonObj.optString("title", "XP Olympian")
    val level = jsonObj.optInt("level", 1)
    val total = jsonObj.optInt("total", 0)
    val correct = jsonObj.optInt("correct", 0)
    val accPct = if (total > 0) (correct * 100) / total else 0
    val time = jsonObj.optString("timeFormatted", "0:00")
    val xp = jsonObj.optInt("xpEarned", 0)
    val gold = jsonObj.optInt("goldEarned", 0)
    val maxCombo = jsonObj.optInt("maxCombo", 0)
    val comboBonusXp = jsonObj.optInt("comboBonusXp", 0)
    val currentStreak = jsonObj.optInt("currentStreak", 0)
    val streakBonusPercent = jsonObj.optInt("streakBonusPercent", 0)
    val isPerfect = jsonObj.optBoolean("isPerfect", false) || (total >= 10 && correct == total)

    val shareText = "⚡ Solved $total questions with $accPct% accuracy in $time! Earned +$xp XP on Focus App!"

    val markAsClaimed = {
        try {
            val obj = JSONObject(message.drillSummaryJson ?: "{}")
            obj.put("isClaimed", true)
            val updatedJson = obj.toString()
            onMessageUpdate?.invoke(message.copy(drillSummaryJson = updatedJson))
        } catch (_: Exception) {}
        isClaimedState = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .statusBarsPadding()
    ) {
        AnimatedContent(
            targetState = isClaimedState,
            transitionSpec = {
                fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
            },
            label = "summary_screen_transition"
        ) { claimed ->
            if (!claimed) {
                // ==================== SCREEN 1: BEFORE CLAIMING XP ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .padding(top = 80.dp, bottom = 100.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val headerTitle = when {
                            maxCombo >= 2 -> "You started a combo!"
                            isPerfect -> "Perfect Drill!"
                            else -> "Drill Complete!"
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 30.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color(0xFF49C0F8),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val headerSubtitle = when {
                            maxCombo >= 2 -> "$maxCombo right answers in a row? Keep it up!"
                            isPerfect -> "You answered all $total questions correctly! Outstanding!"
                            else -> "$correct right out of $total questions! Keep it up!"
                        }
                        Text(
                            text = headerSubtitle,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            ),
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    // Cat Animation
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        val composition by rememberLottieComposition(LottieCompositionSpec.Asset("cat_flying.lottie"))
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = LottieConstants.IterateForever
                        )
                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                        )
                    }

                    // Middle 3 Floating Stat Boxes
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. TOTAL XP Card (Yellow)
                            DuolingoStatBox(
                                modifier = Modifier.weight(1f),
                                headerText = "TOTAL XP",
                                headerColor = Color(0xFFFFC800),
                                headerTextColor = Color(0xFF2B1D00),
                                borderColor = Color(0xFFFFC800),
                                icon = {
                                    DuolingoBoltIcon(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFFFFC800)
                                    )
                                },
                                valueText = "$xp",
                                valueColor = Color(0xFFFFC800)
                            )

                            // 2. COMBO / ACCURACY Card
                            if (maxCombo >= 2) {
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = "COMBO",
                                    headerColor = Color(0xFF1CB0F6),
                                    headerTextColor = Color(0xFF03354E),
                                    borderColor = Color(0xFF1CB0F6),
                                    hasGloss = true,
                                    icon = {
                                        DuolingoTargetIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF1CB0F6)
                                        )
                                    },
                                    valueText = "x$maxCombo",
                                    valueColor = Color(0xFF1CB0F6)
                                )
                            } else if (isPerfect) {
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = "PERFECT",
                                    headerColor = Color(0xFF58CC02),
                                    headerTextColor = Color(0xFF163C01),
                                    borderColor = Color(0xFF58CC02),
                                    icon = {
                                        DuolingoTargetIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF58CC02)
                                        )
                                    },
                                    valueText = "100%",
                                    valueColor = Color(0xFF58CC02)
                                )
                            } else {
                                DuolingoStatBox(
                                    modifier = Modifier.weight(1f),
                                    headerText = "ACCURACY",
                                    headerColor = Color(0xFF58CC02),
                                    headerTextColor = Color(0xFF163C01),
                                    borderColor = Color(0xFF58CC02),
                                    icon = {
                                        DuolingoTargetIcon(
                                            modifier = Modifier.size(24.dp),
                                            color = Color(0xFF58CC02)
                                        )
                                    },
                                    valueText = "$accPct%",
                                    valueColor = Color(0xFF58CC02)
                                )
                            }

                            // 3. SPEEDY Card (Green/Blue)
                            DuolingoStatBox(
                                modifier = Modifier.weight(1f),
                                headerText = "SPEEDY",
                                headerColor = Color(0xFF58CC02),
                                headerTextColor = Color(0xFF163C01),
                                borderColor = Color(0xFF58CC02),
                                hasGloss = true,
                                icon = {
                                    DuolingoClockIcon(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF58CC02)
                                    )
                                },
                                valueText = time,
                                valueColor = Color(0xFF58CC02)
                            )
                        }

                        // Extra bonuses row
                        if (gold > 0 || currentStreak > 0 || maxCombo >= 2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (gold > 0) {
                                    Text(
                                        text = "🪙 +$gold Gold",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color(0xFFFFC800)
                                    )
                                }
                                if (currentStreak > 0) {
                                    if (gold > 0) Text("  •  ", color = Color(0xFF4B606D), fontSize = 12.sp)
                                    Text(
                                        text = "🔥 Day $currentStreak (+${streakBonusPercent}%)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color(0xFFFFC800)
                                    )
                                }
                                if (maxCombo >= 2 && comboBonusXp > 0) {
                                    if (gold > 0 || currentStreak > 0) Text("  •  ", color = Color(0xFF4B606D), fontSize = 12.sp)
                                    Text(
                                        text = "⚡ +$comboBonusXp Combo XP",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        ),
                                        color = Color(0xFF1CB0F6)
                                    )
                                }
                            }
                        }
                    }

                    // Spacer for layout balance
                    Spacer(modifier = Modifier.height(10.dp))
                }
            } else {
                // ==================== SCREEN 2: AFTER CLAIMING XP ====================
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .padding(top = 110.dp, bottom = 120.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Section
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "You’re making great progress sharpening your Aptitude today!",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 25.sp,
                                lineHeight = 33.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val subtitleAnnotated = buildAnnotatedString {
                            append("You practiced ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = Color.White)) {
                                append("$total questions")
                            }
                            append(" today with an average accuracy of ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Black, color = Color.White)) {
                                append("$accPct%")
                            }
                            append("!")
                        }

                        Text(
                            text = subtitleAnnotated,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 17.5.sp,
                                lineHeight = 24.sp
                            ),
                            color = Color(0xFFCBD5E1),
                            textAlign = TextAlign.Center
                        )
                    }

                    // Middle Celebratory Card (Streak)
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = Color(0xFF131F24),
                        border = BorderStroke(2.dp, Color(0xFF28414D)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Top Row: Current vs Longest
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Current Streak
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "🔥",
                                            fontSize = 24.sp,
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                        Text(
                                            text = "${currentStreak.coerceAtLeast(1)} Days",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 22.sp
                                            ),
                                            color = Color(0xFFFF9600)
                                        )
                                    }
                                    Text(
                                        text = "Current Streak",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        color = Color(0xFF8699A6),
                                        modifier = Modifier.padding(start = 28.dp)
                                    )
                                }
                                
                                // Longest Streak
                                Column(horizontalAlignment = Alignment.End) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        DuolingoBoltIcon(
                                            modifier = Modifier.size(24.dp).padding(end = 4.dp),
                                            color = Color(0xFFFFC800)
                                        )
                                        Text(
                                            text = "${Math.max(currentStreak, 1)} Days", // Assume longest is at least current for display
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Black,
                                                fontSize = 22.sp
                                            ),
                                            color = Color(0xFFFFC800)
                                        )
                                    }
                                    Text(
                                        text = "Longest Streak",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                                        color = Color(0xFF8699A6),
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                            }

                            // Middle Row: Daily Streak Bonus
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Daily Streak Bonus",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp
                                    ),
                                    color = Color.White
                                )
                                
                                val currentCycleDay = if (currentStreak == 0) 1 else ((currentStreak - 1) % 7) + 1
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF3B2000), 
                                    border = BorderStroke(1.dp, Color(0xFF8C4C00))
                                ) {
                                    Text(
                                        text = "+${streakBonusPercent.coerceAtLeast(5)}% XP (Day $currentCycleDay/7)",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFFF9600),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            
                            // Bottom Row: 7 circles
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val currentCycleDay = if (currentStreak == 0) 1 else ((currentStreak - 1) % 7) + 1
                                for (i in 1..7) {
                                    val isActive = i <= currentCycleDay
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .border(2.dp, if (isActive) Color(0xFFFF9600) else Color(0xFF4B606D), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isActive) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(26.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFFFF9600)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = "Done",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            } else {
                                                Text(
                                                    text = i.toString(),
                                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Black),
                                                    color = Color(0xFF4B606D)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "D$i",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                                            color = if (isActive) Color(0xFFFF9600) else Color(0xFF4B606D)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }

        // ==================== STICKY BOTTOM ACTION BAR ====================
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = bgColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isClaimedState) {
                    // SCREEN 1 BUTTONS: Share Button + CLAIM XP Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DuolingoShareButton(
                            onClick = {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, shareText)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Math Results")
                                    shareIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(shareIntent)
                                } catch (_: Exception) {}
                            }
                        )

                        Duolingo3DButton(
                            text = "CLAIM XP",
                            modifier = Modifier.weight(1f),
                            onClick = markAsClaimed
                        )
                    }
                } else {
                    // SCREEN 2 BUTTONS: SOLUTIONS 3D Button + CONTINUE Text Button
                    Duolingo3DButton(
                        text = "SOLUTIONS",
                        buttonColor = Color(0xFF1CB0F6),
                        bevelColor = Color(0xFF1899D6),
                        textColor = Color(0xFF042C48),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { message.drillSummaryJson?.let { onViewSolutions(it) } }
                    )

                    TextButton(
                        onClick = onClose,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "CONTINUE",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                letterSpacing = 0.8.sp
                            ),
                            color = Color(0xFF1CB0F6)
                        )
                    }
                }
            }
        }
    }
}
