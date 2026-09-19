package com.focusbyrj.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.GamificationHaptics
import org.json.JSONObject

@Composable
fun ArithmeticCard(
    message: ChatMessage, 
    fontSizeSp: Float,
    isActiveDrill: Boolean = false,
    currentCombo: Int = 0,
    drillProgress: Pair<Int, Int>? = null, // (currentQIndex, targetQuestions)
    isBlitzMode: Boolean = false,
    onAnswered: ((Boolean, QuestionRecord) -> Unit)? = null,
    onEndDrill: (() -> Unit)? = null
) {
    var selectedIndex by rememberSaveable(message.id) { mutableStateOf<Int?>(null) }
    val context = LocalContext.current

    val json = message.arithmeticJson ?: return
    val title: String
    val questionText: String
    val options: List<String>
    val correctIndex: Int
    val explanation: String

    try {
        val obj = JSONObject(json)
        title = obj.getString("title")
        questionText = obj.getString("questionText")
        val arr = obj.getJSONArray("options")
        val parsedOptions = mutableListOf<String>()
        for (i in 0 until arr.length()) parsedOptions.add(arr.getString(i))
        options = parsedOptions
        correctIndex = obj.getInt("correctIndex")
        explanation = obj.getString("explanation")
    } catch (e: Exception) {
        Text("Error loading drill.", modifier = Modifier.padding(16.dp))
        return
    }

    val isDark = isSystemInDarkTheme()
    val duolingoGreen = Color(0xFF58CC02)
    val duolingoGreenBevel = Color(0xFF46A302)
    val duolingoRed = Color(0xFFFF4B4B)
    val duolingoRedBevel = Color(0xFFD11919)
    val duolingoYellow = Color(0xFFFFC800)
    val duolingoYellowBevel = Color(0xFFD8A800)
    val duolingoBlue = Color(0xFF1CB0F6)
    val duolingoBlueBevel = Color(0xFF1899D6)
    val cardDarkBg = Color(0xFF131F24)

    // Pulsing animation for active combo badge
    val infiniteTransition = rememberInfiniteTransition(label = "combo_glow")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (currentCombo >= 3) 1.05f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (currentCombo >= 5) 450 else 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    Column(modifier = Modifier.padding(16.dp)) {
        // Tag Header with Animated Combo Meter & Progress
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isBlitzMode) Icons.Filled.Bolt else Icons.Filled.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = if (isBlitzMode) duolingoYellow else duolingoBlue
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Black),
                    color = if (isBlitzMode) duolingoYellow else duolingoBlue
                )
            }

            // Duolingo 3D Combo Badge
            if (isActiveDrill && currentCombo > 0) {
                val (comboText, comboColor, comboBevel) = when {
                    currentCombo >= 8 -> Triple("⚡ ${currentCombo}x UNSTOPPABLE!", Color(0xFFCE82FF), Color(0xFFA855F7))
                    currentCombo >= 5 -> Triple("🔥 ${currentCombo}x ON FIRE!", duolingoRed, duolingoRedBevel)
                    currentCombo >= 3 -> Triple("🔥 ${currentCombo}x STREAK!", Color(0xFFFF9600), Color(0xFFD47800))
                    else -> Triple("🎯 ${currentCombo} Combo", duolingoBlue, duolingoBlueBevel)
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = comboBevel,
                    modifier = Modifier.scale(if (currentCombo >= 3) pulseScale else 1.0f)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) cardDarkBg else Color.White)
                            .border(BorderStroke(1.5.dp, comboColor), RoundedCornerShape(10.dp))
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = comboText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                            color = comboColor
                        )
                    }
                }
            }
        }

        // Active Drill Question Progress Bar
        if (isActiveDrill && drillProgress != null && drillProgress.second > 0) {
            val currentQ = drillProgress.first
            val totalQ = drillProgress.second
            val progressFraction = (currentQ.toFloat() / totalQ.toFloat()).coerceIn(0f, 1f)
            val animatedProgress by animateFloatAsState(
                targetValue = progressFraction,
                animationSpec = tween(durationMillis = 350),
                label = "drill_q_progress"
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Question $currentQ of $totalQ",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isDark) Color(0xFF839EAB) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (currentCombo >= 3) {
                    Text(
                        text = when {
                            currentCombo >= 8 -> "2.0x XP Boost"
                            currentCombo >= 5 -> "1.5x XP Boost"
                            else -> "+50 XP Boost"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black),
                        color = Color(0xFFFF9600)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (isDark) Color(0xFF20343D) else Color(0xFFE5E5E5))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceAtLeast(0.02f))
                        .clip(RoundedCornerShape(5.dp))
                        .background(duolingoGreen)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .padding(horizontal = 2.dp, vertical = 0.5.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(Color.White.copy(alpha = 0.35f))
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // Question
        Text(
            text = questionText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black, fontSize = (fontSizeSp + 4f).sp),
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Options Grid
        options.forEachIndexed { index, option ->
            val isSelected = selectedIndex == index
            val isCorrect = index == correctIndex
            val showAsCorrect = selectedIndex != null && isCorrect
            val showAsWrong = isSelected && !isCorrect

            val bevelColor = when {
                showAsCorrect -> duolingoGreenBevel
                showAsWrong -> duolingoRedBevel
                else -> if (isDark) Color(0xFF16252C) else Color(0xFFCBD5E1)
            }

            val borderColor = when {
                showAsCorrect -> duolingoGreen
                showAsWrong -> duolingoRed
                else -> if (isDark) Color(0xFF263A45) else Color(0xFFE2E8F0)
            }

            val innerBg = when {
                showAsCorrect -> if (isDark) cardDarkBg else Color(0xFFF0FDF4)
                showAsWrong -> if (isDark) cardDarkBg else Color(0xFFFEF2F2)
                else -> if (isDark) cardDarkBg else Color.White
            }

            val textColor = when {
                showAsCorrect -> duolingoGreen
                showAsWrong -> duolingoRed
                else -> if (isDark) Color(0xFFE2E8F0) else Color(0xFF1E293B)
            }

            val targetScale = if (showAsCorrect) 1.02f else if (showAsWrong) 0.98f else 1.0f
            val animatedScale by animateFloatAsState(
                targetValue = targetScale,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                label = "option_scale"
            )

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = bevelColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .scale(animatedScale)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = if (showAsCorrect || showAsWrong) 2.dp else 3.5.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(innerBg)
                        .border(BorderStroke(2.dp, borderColor), RoundedCornerShape(12.dp))
                        .clickable(enabled = selectedIndex == null) {
                            selectedIndex = index
                            val isCorrectAnswer = (index == correctIndex)

                            // Haptic and combo feedback
                            if (isCorrectAnswer) {
                                DailyQuestManager.recordCorrectAnswer()
                                val nextCombo = currentCombo + 1
                                DailyQuestManager.recordCombo(nextCombo)
                                GamificationHaptics.playCombo(context, nextCombo)

                                if (!isActiveDrill) {
                                    FocusEconomyManager.addRewards(baseXp = 40, baseGold = 20)
                                }
                            } else {
                                GamificationHaptics.playWrong(context)
                            }

                            if (isActiveDrill) {
                                val qRecord = QuestionRecord(
                                    questionNumber = (drillProgress?.first ?: 0),
                                    title = title,
                                    questionText = questionText,
                                    options = options,
                                    correctIndex = correctIndex,
                                    userSelectedIndex = index,
                                    status = if (isCorrectAnswer) "correct" else "wrong",
                                    explanation = explanation
                                )
                                onAnswered?.invoke(isCorrectAnswer, qRecord)
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = (fontSizeSp + 1f).sp),
                            color = textColor
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (showAsCorrect) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (currentCombo >= 1) {
                                    val nextComboPreview = currentCombo + 1
                                    val xpBonus = when {
                                        nextComboPreview >= 8 -> "+80 XP"
                                        nextComboPreview >= 5 -> "+60 XP"
                                        nextComboPreview >= 3 -> "+50 XP"
                                        else -> "+40 XP"
                                    }
                                    Text(
                                        text = if (isBlitzMode) "$xpBonus • +30s" else xpBonus,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = duolingoGreen,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }
                                Text("✓", fontSize = 18.sp, color = duolingoGreen, fontWeight = FontWeight.Black)
                            }
                        } else if (showAsWrong) {
                            Text("✕", fontSize = 18.sp, color = duolingoRed, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // Explanation Reveal
        AnimatedVisibility(visible = selectedIndex != null) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(bottom = 12.dp))
                Text(
                    text = explanation,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = fontSizeSp.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (isActiveDrill) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    text = "End Drill",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    modifier = Modifier
                        .clickable { onEndDrill?.invoke() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
