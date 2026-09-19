package com.focusbyrj.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.screens.drill.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.GamificationHaptics
import org.json.JSONObject

@Composable
fun FullscreenDrillView(
    activeSession: DrillSession,
    latestQuestionMessage: ChatMessage,
    allQuestions: List<ChatMessage> = emptyList(),
    onNextQuestion: (() -> Unit)? = null,
    onAnswerSubmitted: (Boolean, QuestionRecord) -> Unit,
    onEndSession: () -> Unit
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    val parsedQuestions = remember(activeSession.preGeneratedQuestions.toList(), allQuestions) {
        if (activeSession.preGeneratedQuestions.isNotEmpty()) {
            activeSession.preGeneratedQuestions.mapIndexed { idx, json ->
                ChatMessage(
                    id = "pre_$idx",
                    text = "Arithmetic Drill",
                    isUser = false,
                    isArithmetic = true,
                    arithmeticJson = json
                )
            }
        } else {
            if (allQuestions.isNotEmpty()) allQuestions else listOf(latestQuestionMessage)
        }
    }

    val questionsList = parsedQuestions
    var questionIndex by remember(questionsList.size) { mutableStateOf(activeSession.highestSeenIndex.coerceIn(0, (questionsList.size - 1).coerceAtLeast(0))) }

    LaunchedEffect(questionsList.size) {
        if (activeSession.targetQuestions <= 0 && questionsList.size > 1) {
            questionIndex = (questionsList.size - 1).coerceAtLeast(0)
        }
    }

    LaunchedEffect(questionIndex) {
        if (questionIndex > activeSession.highestSeenIndex) {
            activeSession.highestSeenIndex = questionIndex
        }
    }

    val currentQuestionMessage = questionsList.getOrElse(questionIndex) { latestQuestionMessage }

    // Parse current question
    val json = currentQuestionMessage.arithmeticJson
    var title by remember(currentQuestionMessage.id) { mutableStateOf("Mental Arithmetic") }
    var direction by remember(currentQuestionMessage.id) { mutableStateOf("") }
    var questionText by remember(currentQuestionMessage.id) { mutableStateOf("") }
    var options by remember(currentQuestionMessage.id) { mutableStateOf<List<String>>(emptyList()) }
    var correctIndex by remember(currentQuestionMessage.id) { mutableStateOf(0) }
    var explanation by remember(currentQuestionMessage.id) { mutableStateOf("") }
    var isBookmarked by remember(currentQuestionMessage.id) { mutableStateOf(false) }
    var vocabType by remember(currentQuestionMessage.id) { mutableStateOf<String?>(null) }
    var vocabId by remember(currentQuestionMessage.id) { mutableStateOf<Int?>(null) }
    var timeOnQuestionSec by remember(currentQuestionMessage.id) { mutableStateOf(0) }
    var showGridDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentQuestionMessage.id, json) {
        if (json != null) {
            try {
                val obj = JSONObject(json)
                title = obj.optString("title", "Mental Arithmetic")
                direction = obj.optString("direction", "Read the arithmetic problem carefully and select the single correct option based on fundamental principles.")
                questionText = obj.optString("questionText", "")
                val arr = obj.optJSONArray("options")
                val list = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) list.add(arr.getString(i))
                }
                options = list
                correctIndex = obj.optInt("correctIndex", 0)
                explanation = obj.optString("explanation", "")
                vocabType = if (obj.has("vocabType")) obj.optString("vocabType") else null
                vocabId = if (obj.has("vocabId")) obj.optInt("vocabId") else null
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    LaunchedEffect(currentQuestionMessage.id) {
        timeOnQuestionSec = 0
        while (true) {
            kotlinx.coroutines.delay(1000)
            timeOnQuestionSec++
        }
    }

    // Local state for question selection
    var selectedIndex by remember(currentQuestionMessage.id) { 
        mutableStateOf<Int?>(activeSession.questionRecords.find { it.questionNumber == questionIndex + 1 }?.userSelectedIndex) 
    }

    // Duolingo Brand Theme Colors
    val duolingoGreen = Color(0xFF58CC02)
    val duolingoGreenBevel = Color(0xFF46A302)
    val duolingoGreenBg = Color(0xFF58CC02).copy(alpha = 0.14f)

    val duolingoRed = Color(0xFFFF4B4B)
    val duolingoRedBevel = Color(0xFFD11919)
    val duolingoRedBg = Color(0xFFFF4B4B).copy(alpha = 0.14f)

    val duolingoYellow = Color(0xFFFFC800)
    val duolingoYellowBevel = Color(0xFFD8A800)

    val duolingoBlue = Color(0xFF1CB0F6)
    val duolingoBlueBevel = Color(0xFF1899D6)

    val duolingoOrange = Color(0xFFFF9600)
    val duolingoOrangeBevel = Color(0xFFD47800)

    val duolingoPurple = Color(0xFFCE82FF)
    val duolingoPurpleBevel = Color(0xFFA855F7)

    // Canvas background
    val darkCanvasBg = Color(0xFF0C1418) // Deep Duolingo dark slate
    val cardDarkBg = Color(0xFF131F24)

    // Gentle pulse animation for high combos
    val infiniteTransition = rememberInfiniteTransition(label = "combo_pulse_transition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (activeSession.combo >= 3) 1.04f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (activeSession.combo >= 5) 450 else 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "combo_pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) darkCanvasBg else Color(0xFFF7F9FA))
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 10.dp)
        ) {
            // -------------------------------------------------------------
            // MODULAR TOP HUD & COMBO STREAK CAPSULE
            // -------------------------------------------------------------
            DrillTopBar(
                activeSession = activeSession,
                questionIndex = questionIndex,
                onEndSession = onEndSession,
                onOpenOverview = { showGridDialog = true },
                isDark = isDark
            )

            DrillComboStreakCapsule(
                combo = activeSession.combo,
                pulseScale = pulseScale,
                isBlitz = activeSession.isBlitz,
                isDark = isDark
            )

            // -------------------------------------------------------------
            // MAIN QUESTION & OPTIONS AREA (Solution Testbook Format)
            // -------------------------------------------------------------
            val activeBlue = Color(0xFF2196F3)
            val correctGreen = Color(0xFF00C853)
            val wrongRed = Color(0xFFEF5350)
            val cardBackground = if (isDark) Color(0xFF22262C) else Color(0xFFF1F5F9)
            val cardStrokeColor = if (isDark) Color(0xFF2E333B) else Color(0xFFE2E8F0)
            val textPrimary = if (isDark) Color(0xFFF1F3F5) else Color(0xFF0F172A)
            val textSecondary = if (isDark) Color(0xFF9EA7B4) else Color(0xFF64748B)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 70.dp, bottom = 12.dp)
            ) {
                // Top Question Meta Row: Number Badge Circle + Timer + Marks (+1.0 -0.25) + Warning + Bookmark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Question Number Circle Badge
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(correctGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${questionIndex + 1}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Timer + Time formatted e.g. "28sec"
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${timeOnQuestionSec}sec",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = textSecondary
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Marks: +1.0  -0.25
                    Text(
                        text = "+1.0",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = correctGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "-0.25",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                        color = textSecondary
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Direction line
                if (direction.isNotEmpty()) {
                    Text(
                        text = "Direction: $direction",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Medium,
                            lineHeight = 22.sp
                        ),
                        color = textSecondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Question Prompt
                Text(
                    text = questionText.ifBlank { "..." },
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Normal,
                        lineHeight = 24.sp,
                        fontSize = 17.sp
                    ),
                    color = textPrimary
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Options List (1. 2. 3. 4. 5.)
                options.forEachIndexed { optIndex, optText ->
                    val isCorrectOption = optIndex == correctIndex
                    val isUserChosen = optIndex == selectedIndex

                    val (optBorderColor, optBgColor, optNumberColor) = when {
                        selectedIndex != null && isCorrectOption -> Triple(correctGreen, correctGreen.copy(alpha = 0.12f), correctGreen)
                        selectedIndex != null && isUserChosen && !isCorrectOption -> Triple(wrongRed, wrongRed.copy(alpha = 0.12f), wrongRed)
                        else -> Triple(Color.Transparent, cardBackground, textSecondary)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable(enabled = selectedIndex == null) {
                                selectedIndex = optIndex
                                val isCorrectAnswer = (optIndex == correctIndex)

                                if (isCorrectAnswer) {
                                    DailyQuestManager.recordCorrectAnswer()
                                    val nextCombo = activeSession.combo + 1
                                    DailyQuestManager.recordCombo(nextCombo)
                                    GamificationHaptics.playCombo(context, nextCombo)
                                } else {
                                    GamificationHaptics.playWrong(context)
                                }

                                val qRecord = QuestionRecord(
                                    questionNumber = questionIndex + 1,
                                    title = title,
                                    questionText = questionText,
                                    options = options,
                                    correctIndex = correctIndex,
                                    userSelectedIndex = optIndex,
                                    status = if (isCorrectAnswer) "correct" else "wrong",
                                    explanation = explanation,
                                    vocabType = vocabType,
                                    vocabId = vocabId
                                )
                                activeSession.attemptedIndices.add(questionIndex)
                                onAnswerSubmitted(isCorrectAnswer, qRecord)
                                
                                if (activeSession.targetQuestions > 0) {
                                    coroutineScope.launch {
                                        delay(700)
                                        if (questionIndex < questionsList.size - 1) {
                                            // Auto advance to next question
                                            // Only if the next question is not attempted
                                            if (!activeSession.attemptedIndices.contains(questionIndex + 1)) {
                                                questionIndex++
                                            }
                                        } else {
                                            // Reached end of test, show grid
                                            showGridDialog = true
                                        }
                                    }
                                }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = optBgColor,
                        border = if (optBorderColor != Color.Transparent) BorderStroke(1.5.dp, optBorderColor) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${optIndex + 1}.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontStyle = FontStyle.Italic,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                color = optNumberColor
                            )

                            Spacer(modifier = Modifier.width(14.dp))

                            Text(
                                text = optText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                color = textPrimary,
                                modifier = Modifier.weight(1f)
                            )

                            if (selectedIndex != null) {
                                if (isCorrectOption) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Correct",
                                        tint = correctGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                } else if (isUserChosen) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = "Incorrect",
                                        tint = wrongRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Solution & Explanation Card on answer submission
                AnimatedVisibility(
                    visible = selectedIndex != null && explanation.isNotBlank()
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = cardBackground,
                        border = BorderStroke(1.dp, cardStrokeColor)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lightbulb,
                                    contentDescription = null,
                                    tint = activeBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Explanation & Method",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    ),
                                    color = textPrimary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = cardStrokeColor)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = explanation,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    lineHeight = 22.sp,
                                    fontSize = 14.sp
                                ),
                                color = textPrimary.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // BOTTOM BAR: Prev & Next Navigation Buttons
            // -------------------------------------------------------------
            DrillBottomActionRow(
                questionIndex = questionIndex,
                totalQuestions = questionsList.size,
                onPrevious = {
                    if (questionIndex > 0) questionIndex--
                },
                onNext = {
                    if (questionIndex < questionsList.size - 1) {
                        questionIndex++
                    } else {
                        onNextQuestion?.invoke()
                    }
                },
                cardBackground = cardBackground,
                cardStrokeColor = cardStrokeColor,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                activeBlue = activeBlue
            )
        }

        DrillQuestionPaletteModal(
            showDialog = showGridDialog,
            onDismiss = { showGridDialog = false },
            totalQuestions = if (activeSession.targetQuestions > 0) activeSession.targetQuestions else questionsList.size,
            highestSeenIndex = activeSession.highestSeenIndex,
            attemptedIndices = activeSession.attemptedIndices,
            onQuestionSelected = { idx -> questionIndex = idx },
            onSubmitClick = onEndSession,
            isDark = isDark
        )
    }
}
