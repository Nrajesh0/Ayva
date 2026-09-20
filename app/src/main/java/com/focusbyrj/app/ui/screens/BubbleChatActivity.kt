package com.focusbyrj.app.ui.screens

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material3.*
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.Task
import com.focusbyrj.app.data.FocusDatabase
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.data.AppRestriction
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.withStyle
import android.content.pm.PackageManager
import com.airbnb.lottie.RenderMode
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.focusbyrj.app.util.SmartDateParser
import com.focusbyrj.app.util.TaskReminderHelper
import com.focusbyrj.app.widget.TodoWidgetProvider
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.CustomCategoryManager
import com.focusbyrj.app.util.CustomCategory
import com.focusbyrj.app.util.BubbleChatManager
import com.focusbyrj.app.util.PersistedChatMessage
import com.focusbyrj.app.ui.components.ProfessionalSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class QuestionRecord(
    val questionNumber: Int,
    val title: String,
    val questionText: String,
    val options: List<String>,
    val correctIndex: Int,
    val userSelectedIndex: Int?,
    val status: String,
    val explanation: String,
    val vocabType: String? = null,
    val vocabId: Int? = null
)

data class DrillSession(
    val difficulty: String, 
    val targetQuestions: Int = -1, 
    var correct: Int = 0, 
    var total: Int = 0, 
    var xp: Int = 0, 
    var gold: Int = 0,
    var combo: Int = 0,
    var maxCombo: Int = 0,
    val isBlitz: Boolean = false,
    var blitzSecondsRemaining: Int = 60,
    val startTime: Long = System.currentTimeMillis(),
    val questionRecords: MutableList<QuestionRecord> = mutableListOf(),
    val preGeneratedQuestions: MutableList<String> = mutableListOf(),
    val attemptedIndices: MutableSet<Int> = mutableSetOf(),
    val markedForReview: MutableSet<Int> = mutableSetOf(),
    var highestSeenIndex: Int = 0
)

data class QuickActionCommand(val label: String, val commandText: String)

data class ChatMessage(
    val id: String, 
    val text: String, 
    val isUser: Boolean, 
    val timestamp: Long = System.currentTimeMillis(),
    val firstViewedTimestamp: Long = 0L,
    val isArithmetic: Boolean = false,
    val arithmeticJson: String? = null,
    val isDrillSummary: Boolean = false,
    val drillSummaryJson: String? = null,
    val isAptitudeProfile: Boolean = false,
    val isStreakPrompt: Boolean = false,
    val streakPromptJson: String? = null,
    val isTaskSummary: Boolean = false,
    val taskSummaryJson: String? = null,
    val isTalkAction: Boolean = false,
    val talkActionJson: String? = null,
    val pendingActionJson: String? = null,
    val isDailyQuests: Boolean = false,
    val isMysteryBox: Boolean = false,
    val isMorningBrief: Boolean = false,
    val isEveningBrief: Boolean = false,
    val isStreakFreezeSkipped: Boolean = false,
    val isVocabBrief: Boolean = false,
    val isVocabHub: Boolean = false,
    val vocabJson: String? = null,
    val isWelcome: Boolean = false,
    val isHabitsSummary: Boolean = false,
    val habitsSummaryJson: String? = null
)

fun PersistedChatMessage.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        firstViewedTimestamp = firstViewedTimestamp,
        isArithmetic = isArithmetic,
        arithmeticJson = arithmeticJson,
        isDrillSummary = isDrillSummary,
        drillSummaryJson = drillSummaryJson,
        isAptitudeProfile = isAptitudeProfile,
        isStreakPrompt = isStreakPrompt,
        streakPromptJson = streakPromptJson,
        isTaskSummary = isTaskSummary,
        taskSummaryJson = taskSummaryJson,
        isTalkAction = isTalkAction,
        talkActionJson = talkActionJson,
        pendingActionJson = pendingActionJson,
        isDailyQuests = isDailyQuests,
        isMysteryBox = isMysteryBox || id.startsWith("mystery_box_"),
        isMorningBrief = isMorningBrief || id.startsWith("morning_"),
        isEveningBrief = isEveningBrief || id.startsWith("evening_"),
        isStreakFreezeSkipped = isStreakFreezeSkipped || id.startsWith("angry_freeze_"),
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub || id.startsWith("vocab_hub_"),
        vocabJson = vocabJson,
        isWelcome = isWelcome || id.startsWith("welcome_") || 
                    text.contains("Hey! Ayva is on deck", ignoreCase = true) ||
                    text.contains("ready for action", ignoreCase = true) ||
                    text.contains("Ayva here!", ignoreCase = true) ||
                    text.contains("I'm Ayva", ignoreCase = true) ||
                    text.contains("anti-procrastination", ignoreCase = true),
        isHabitsSummary = isHabitsSummary || id.startsWith("habits_"),
        habitsSummaryJson = habitsSummaryJson
    )
}

fun ChatMessage.toPersistedChatMessage(markViewedIfActive: Boolean = true): PersistedChatMessage {
    val viewedTs = if (firstViewedTimestamp > 0L) {
        firstViewedTimestamp
    } else if (markViewedIfActive) {
        System.currentTimeMillis()
    } else {
        0L
    }
    return PersistedChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        timestamp = timestamp,
        firstViewedTimestamp = viewedTs,
        isArithmetic = isArithmetic,
        arithmeticJson = arithmeticJson,
        isDrillSummary = isDrillSummary,
        drillSummaryJson = drillSummaryJson,
        isAptitudeProfile = isAptitudeProfile,
        isStreakPrompt = isStreakPrompt,
        streakPromptJson = streakPromptJson,
        isTaskSummary = isTaskSummary,
        taskSummaryJson = taskSummaryJson,
        isTalkAction = isTalkAction,
        talkActionJson = talkActionJson,
        pendingActionJson = pendingActionJson,
        isDailyQuests = isDailyQuests,
        isMysteryBox = isMysteryBox,
        isMorningBrief = isMorningBrief,
        isEveningBrief = isEveningBrief,
        isStreakFreezeSkipped = isStreakFreezeSkipped,
        isVocabBrief = isVocabBrief,
        isVocabHub = isVocabHub,
        vocabJson = vocabJson,
        isWelcome = isWelcome,
        isHabitsSummary = isHabitsSummary,
        habitsSummaryJson = habitsSummaryJson
    )
}

fun createDrillSessionWithQuestions(difficulty: String, targetQuestions: Int): DrillSession {
    val diffEnum = when (difficulty) {
        "hard" -> com.focusbyrj.app.util.ArithmeticDifficulty.HARD
        "medium" -> com.focusbyrj.app.util.ArithmeticDifficulty.MEDIUM
        else -> com.focusbyrj.app.util.ArithmeticDifficulty.EASY
    }
    val count = if (targetQuestions <= 0) 10 else targetQuestions
    val generated = (0 until count).map {
        try {
            val q = com.focusbyrj.app.util.ArithmeticEngine.generateQuestion(diffEnum)
            org.json.JSONObject().apply {
                put("title", q.title)
                put("questionText", q.questionText)
                val arr = org.json.JSONArray()
                q.options.forEach { arr.put(it) }
                put("options", arr)
                put("correctIndex", q.correctIndex)
                put("explanation", q.explanation)
            }.toString()
        } catch (e: Exception) {
            org.json.JSONObject().apply {
                put("title", "Mental Arithmetic")
                put("questionText", "What is 12 + 15?")
                val arr = org.json.JSONArray()
                listOf("25", "27", "29", "31").forEach { arr.put(it) }
                put("options", arr)
                put("correctIndex", 1)
                put("explanation", "12 + 15 = 27")
            }.toString()
        }
    }.toMutableList()
    return DrillSession(difficulty = difficulty, targetQuestions = targetQuestions, preGeneratedQuestions = generated)
}

class BubbleChatActivity : ComponentActivity() {

    private val closeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.focusbyrj.app.CLOSE_CHAT") {
                sendBroadcast(Intent("com.focusbyrj.app.TRIGGER_CLOSE_ANIM").setPackage(packageName))
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val filter = IntentFilter("com.focusbyrj.app.CLOSE_CHAT")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
        

        
        setContent {
            FocusByRjTheme {
                var isVisible by remember { mutableStateOf(false) }
                var hasOpened by remember { mutableStateOf(false) }
                val context = LocalContext.current
                
                DisposableEffect(Unit) {
                    val animReceiver = object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, i: Intent?) {
                            isVisible = false
                        }
                    }
                    val f = IntentFilter("com.focusbyrj.app.TRIGGER_CLOSE_ANIM")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.registerReceiver(animReceiver, f, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        context.registerReceiver(animReceiver, f)
                    }
                    onDispose { context.unregisterReceiver(animReceiver) }
                }

                LaunchedEffect(Unit) {
                    isVisible = true
                    hasOpened = true
                }
                
                LaunchedEffect(isVisible) {
                    if (!isVisible && hasOpened) {
                        delay(300)
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                isVisible = false
                            })
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    AnimatedVisibility(
                        visible = isVisible,
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeOut()
                    ) {
                        ChatInterface()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        com.focusbyrj.app.service.BubbleService.clearSnooze(this)
        com.focusbyrj.app.util.BubbleChatManager.markAllAsViewed(this)
        com.focusbyrj.app.util.BubbleChatManager.checkAndClearIfInactive(this)
        com.focusbyrj.app.util.BubbleChatManager.updateLastActivityTime(this)
        sendBroadcast(Intent("com.focusbyrj.app.CHAT_OPENED"))
    }

    override fun onPause() {
        super.onPause()
        com.focusbyrj.app.util.BubbleChatManager.updateLastActivityTime(this)
        sendBroadcast(Intent("com.focusbyrj.app.CHAT_CLOSED"))
        if (isFinishing) {
            // Optional: any specific finish logic
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(closeReceiver)

    }
}

@Composable
fun ChatInterface() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val profile by FocusEconomyManager.profileFlow.collectAsState()
    
    val prefs = remember { context.getSharedPreferences("bubble_prefs", android.content.Context.MODE_PRIVATE) }
    
    var messages by remember { 
        BubbleChatManager.markAllAsViewed(context)
        BubbleChatManager.checkAndClearIfInactive(context)
        val stored = BubbleChatManager.getMessages(context)
        val initialList = if (stored.isEmpty()) {
            val welcome = PersistedChatMessage(
                id = "welcome_${System.currentTimeMillis()}",
                text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            BubbleChatManager.saveMessages(context, listOf(welcome))
            listOf(welcome)
        } else {
            stored
        }
        mutableStateOf<List<ChatMessage>>(
            initialList.map { it.toChatMessage() }
        ) 
    }
    var showMenu by remember { mutableStateOf(false) }
    var showFontSizeDialog by remember { mutableStateOf(false) }
    var showNotificationColorsDialog by remember { mutableStateOf(false) }
    var chatFontSizeSp by remember { mutableStateOf(prefs.getFloat("chat_font_size_sp", 15f)) }
    var activeDrillSession by remember { mutableStateOf<DrillSession?>(null) }
    var showDrillSummaryMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showSolutionsJson by remember { mutableStateOf<String?>(null) }
    var showMysteryChestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(activeDrillSession?.isBlitz) {
        if (activeDrillSession?.isBlitz == true) {
            while (activeDrillSession?.isBlitz == true) {
                delay(1000L)
                val session = activeDrillSession ?: break
                if (session.blitzSecondsRemaining > 1) {
                    activeDrillSession = session.copy(blitzSecondsRemaining = session.blitzSecondsRemaining - 1)
                } else {
                    session.blitzSecondsRemaining = 0
                    com.focusbyrj.app.util.GamificationHaptics.playCelebration(context)
                    val summaryMsg = com.focusbyrj.app.util.DrillSummaryHelper.generateSummaryMessage(session)
                    val questState = com.focusbyrj.app.util.DailyQuestManager.stateFlow.value
                    val hasMysteryBox = questState.isEarlyBirdAvailable || questState.isNightOwlAvailable
                    val boxMsg = if (hasMysteryBox) {
                        ChatMessage(
                            id = "mystery_box_${System.currentTimeMillis()}",
                            text = "Daily Mystery Box Unlocked",
                            isUser = false,
                            timestamp = System.currentTimeMillis(),
                            firstViewedTimestamp = System.currentTimeMillis(),
                            isMysteryBox = true
                        )
                    } else null

                    val newMsgs = if (boxMsg != null) {
                        messages.filter { !it.isArithmetic } + summaryMsg + boxMsg
                    } else {
                        messages.filter { !it.isArithmetic } + summaryMsg
                    }
                    messages = newMsgs
                    BubbleChatManager.saveMessages(context, messages.map { it.toPersistedChatMessage() })
                    showDrillSummaryMessage = summaryMsg
                    activeDrillSession = null
                    break
                }
            }
        }
    }

    var inputTextFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    val inputText = inputTextFieldValue.text
    var isHighPriority by remember { mutableStateOf(false) }
    var isPersistent by remember { mutableStateOf(false) }

    var lastInteractionTimestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var showCatForWelcome by remember { mutableStateOf(false) }
    var showCatForInactivity by remember { mutableStateOf(false) }
    var isCatActionPlaying by remember { mutableStateOf(false) }
    var catActionInvocationCount by remember { mutableStateOf(0) }
    var currentCatActionAsset by remember { mutableStateOf("cat_action.lottie") }
    var catTapCount by remember { mutableStateOf(0) }
    var lastCatTapTime by remember { mutableStateOf(0L) }

    LaunchedEffect(lastInteractionTimestamp, messages.size, inputText) {
        if (inputText.isNotBlank()) {
            showCatForWelcome = false
            showCatForInactivity = false
            isCatActionPlaying = false
            catTapCount = 0
            return@LaunchedEffect
        }
        val isWelcomeMsg = messages.size <= 1 && (messages.firstOrNull()?.isUser == false)
        if (isWelcomeMsg) {
            showCatForWelcome = true
        } else {
            showCatForInactivity = false
            delay(15_000L) // 15 seconds of inactivity
            if (inputText.isBlank()) {
                showCatForInactivity = true
            }
        }
    }

    val isCatVisible = (showCatForWelcome || showCatForInactivity || isCatActionPlaying) && activeDrillSession == null

    val quickActionCommands = remember {
        listOf(
            QuickActionCommand("💬 /talk", "/talk "),
            QuickActionCommand("📋 /tasks", "/tasks "),
            QuickActionCommand("🎯 /habit", "/habit"),
            QuickActionCommand("🧹 /clear", "/clear"),
            QuickActionCommand("👤 /profile", "/profile"),
            QuickActionCommand("📚 /vocab", "/vocab"),
            QuickActionCommand("⚡ /blitz", "/blitz"),
            QuickActionCommand("⚡ /drill", "/drill easy 10"),
            QuickActionCommand("📊 /summary", "/summary"),
            QuickActionCommand("🎯 /quests", "/quests"),
            QuickActionCommand("💰 /wager", "/wager"),
            QuickActionCommand("🛡️ /freeze", "/freeze"),
            QuickActionCommand("🗓️ /reschedule", "/reschedule "),
            QuickActionCommand("❓ /help", "/help")
        )
    }

    val onFillCommand = { commandText: String ->
        lastInteractionTimestamp = System.currentTimeMillis()
        showCatForWelcome = false
        showCatForInactivity = false
        isCatActionPlaying = false
        inputTextFieldValue = TextFieldValue(
            text = commandText,
            selection = TextRange(commandText.length)
        )
    }

    val updateFontSize = { newSize: Float ->
        val clamped = newSize.coerceIn(12f, 24f)
        chatFontSizeSp = clamped
        prefs.edit().putFloat("chat_font_size_sp", clamped).apply()
    }
    
    val persistedFlowMessages by BubbleChatManager.messagesFlow.collectAsState()
    
    // Dynamically sync messages whenever background alerts (streak prompts, summaries) are received or updated
    LaunchedEffect(persistedFlowMessages) {
        if (persistedFlowMessages.isNotEmpty()) {
            val incomingIds = persistedFlowMessages.map { it.id }.toSet()
            val existingMap = messages.associateBy { it.id }
            var changed = false
            val merged = mutableListOf<ChatMessage>()

            // Update existing or preserve in order
            for (pMsg in persistedFlowMessages) {
                val existing = existingMap[pMsg.id]
                val converted = pMsg.toChatMessage()
                if (existing == null) {
                    merged.add(converted)
                    changed = true
                } else if (existing.text != converted.text ||
                           existing.streakPromptJson != converted.streakPromptJson ||
                           existing.drillSummaryJson != converted.drillSummaryJson ||
                           existing.taskSummaryJson != converted.taskSummaryJson ||
                           existing.vocabJson != converted.vocabJson ||
                           existing.habitsSummaryJson != converted.habitsSummaryJson) {
                    merged.add(converted)
                    changed = true
                } else {
                    merged.add(existing)
                }
            }

            // Include any locally dispatched messages (user or bot) not yet committed to persistence
            for (msg in messages) {
                if (msg.id !in incomingIds) {
                    merged.add(msg)
                }
            }

            if (changed || merged.size != messages.size) {
                messages = merged
            }
        } else if (messages.isNotEmpty() && messages.none { it.isUser }) {
            // Background cleared the stream
            val welcome = ChatMessage(
                id = "welcome_${System.currentTimeMillis()}",
                text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                isUser = false,
                timestamp = System.currentTimeMillis()
            )
            messages = listOf(welcome)
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                BubbleChatManager.markAllAsViewed(context)
                BubbleChatManager.clearUnread(context)
                BubbleChatManager.updateLastActivityTime(context)
                val stored = BubbleChatManager.getMessages(context)
                if (stored.isNotEmpty()) {
                    val existingIds = messages.map { it.id }.toSet()
                    val newIncoming = stored.filter { it.id !in existingIds }
                    if (newIncoming.isNotEmpty()) {
                        val mapped = newIncoming.map { it.toChatMessage() }
                        messages = messages + mapped
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    LaunchedEffect(messages) {
        BubbleChatManager.saveMessages(context, messages.map { it.toPersistedChatMessage() })
    }
    
    val focusApp = remember { context.applicationContext as com.focusbyrj.app.FocusApplication }
    val allTasksList by focusApp.taskRepository.allTasks.collectAsState(initial = emptyList())
    val pendingTasksList = remember(allTasksList) {
        allTasksList.filter { !it.isCompleted }
            .sortedWith(compareByDescending<com.focusbyrj.app.data.Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE })
    }

    val restrictions by focusApp.database.appRestrictionDao().getAllRestrictions().collectAsState(initial = emptyList())
    val lockedPackages = remember(restrictions) {
        restrictions.filter { it.isRestricted }.map { it.packageName }.toSet()
    }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var lastSummaryTasks by remember { mutableStateOf<List<com.focusbyrj.app.data.Task>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        BubbleChatManager.markAllAsViewed(context)
        BubbleChatManager.clearUnread(context)
        BubbleChatManager.updateLastActivityTime(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            val clearedOrPruned = BubbleChatManager.checkAndClearIfInactive(context, isDrillOrQuizActive = activeDrillSession != null)
            if (clearedOrPruned) {
                val currentPersisted = BubbleChatManager.getMessages(context)
                if (currentPersisted.isEmpty()) {
                    val welcome = ChatMessage(
                        id = "welcome_${System.currentTimeMillis()}",
                        text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        firstViewedTimestamp = System.currentTimeMillis()
                    )
                    messages = listOf(welcome)
                } else {
                    messages = currentPersisted.map { it.toChatMessage() }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val startDrillFromNotification = (context as? android.app.Activity)?.intent?.getBooleanExtra("EXTRA_START_DRILL", false) == true
        if (startDrillFromNotification && activeDrillSession == null) {
            (context as? android.app.Activity)?.intent?.removeExtra("EXTRA_START_DRILL")
            val newSession = createDrillSessionWithQuestions("easy", 10)
            activeDrillSession = newSession
        }

        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val apps = packages.filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName }.mapNotNull { 
                val name = pm.getApplicationLabel(it).toString()
                if (name.isNotBlank()) {
                    val category = getCategoryForApp(it, it.packageName)
                    AppInfo(name, it.packageName, category)
                } else null
            }.sortedBy { it.name }
            installedApps = apps
        }
    }
    
    val suggestions = remember(inputText, installedApps, lockedPackages, pendingTasksList, lastSummaryTasks) {
        if (!inputText.startsWith("/")) return@remember emptyList<Suggestion>()
        val parts = inputText.split(" ")
        val cmd = parts[0].lowercase()
        
        when {
            parts.size == 1 -> {
                val available = listOf("/talk", "/habit", "/habits", "/advice", "/breathe", "/screentime", "/tasks", "/tasks all", "/summary", "/summary all", "/drill", "/blitz", "/quests", "/daily", "/chest", "/box", "/mystery", "/freeze", "/wager", "/profile", "/priority", "/postpone all", "/reschedule", "/clear", "/help")
                available.filter { it.startsWith(cmd) }.map { Suggestion(it, "$it ") }
            }
            (cmd == "/summary" || cmd == "/tasks" || cmd == "/task") && parts.size == 2 -> {
                val typed = parts[1].lowercase()
                val modes = listOf("all", "today")
                modes.filter { it.startsWith(typed) }.map { Suggestion("$cmd $it", "$cmd $it ") }
            }
            cmd == "/drill" && parts.size == 2 -> {
                val typed = parts[1].lowercase()
                val modes = listOf("easy", "medium", "hard")
                modes.filter { it.startsWith(typed) }.map { Suggestion(it, "/drill $it ") }
            }
            cmd == "/drill" && parts.size == 3 -> {
                val typed = parts[2].lowercase()
                val limits = listOf("10", "20", "unlimited")
                limits.filter { it.startsWith(typed) }.map { Suggestion(it, "/drill ${parts[1]} $it") }
            }
            cmd == "/reschedule" && parts.size == 2 -> {
                val typed = parts[1].lowercase()
                val targetTasks = if (lastSummaryTasks.isNotEmpty()) lastSummaryTasks else pendingTasksList
                targetTasks.mapIndexed { index, task -> 
                    val num = (index + 1).toString()
                    val dueStr = if (task.dueDate != null) " (${SmartDateParser.formatDueDate(task.dueDate)})" else ""
                    val display = "$num. ${task.title}$dueStr"
                    Suggestion(display, "/reschedule $num ")
                }.filter { 
                    typed.isEmpty() || it.displayText.startsWith(typed) || it.displayText.lowercase().contains(typed) || it.replacementText.contains(" $typed") 
                }
            }
            cmd == "/reschedule" && parts.size >= 3 -> {
                val num = parts[1]
                val timeTyped = parts.drop(2).joinToString(" ").lowercase()
                val timeOptions = listOf("today 5pm", "tomorrow 9am", "tomorrow 3pm", "tomorrow 6pm", "in 2 hours", "next monday 10am")
                timeOptions.filter { it.contains(timeTyped) }.map {
                    Suggestion(it, "/reschedule $num $it")
                }
            }

            else -> emptyList()
        }
    }


    val parsedResult = remember(inputText) {
        if (inputText.isNotBlank()) SmartDateParser.parse(inputText) else null
    }

    fun sendMessage(overrideText: String? = null) {
        lastInteractionTimestamp = System.currentTimeMillis()
        showCatForWelcome = false
        showCatForInactivity = false
        isCatActionPlaying = false

        val textToSendOriginal = (overrideText ?: inputText).trim()
        var textToSend = textToSendOriginal
        if (textToSend.isNotBlank()) {
            val lowerCheck = textToSend.lowercase()
            if (lowerCheck == "morning brief" || lowerCheck == "morning briefing" || lowerCheck == "good morning") {
                textToSend = "/summary morning"
            } else if (lowerCheck == "evening brief" || lowerCheck == "evening briefing" || lowerCheck == "good evening" || lowerCheck == "night brief") {
                textToSend = "/summary evening"
            }
            val userMsg = ChatMessage(System.currentTimeMillis().toString(), textToSendOriginal, true)
            val updatedUserList = messages + userMsg
            messages = updatedUserList
            BubbleChatManager.saveMessages(context, updatedUserList.map { it.toPersistedChatMessage() })
            var sentText = textToSend
            val effectiveParsed = SmartDateParser.parse(textToSendOriginal)
            val finalTitle = effectiveParsed.cleanText.takeIf { it.isNotBlank() } ?: sentText
            val dueDate = effectiveParsed.timestamp
            val detectedRecurrence = effectiveParsed.recurrence
            
            val wasPriority = isHighPriority
            val wasPersistent = isPersistent
            if (overrideText == null) {
                inputTextFieldValue = TextFieldValue("")
                isHighPriority = false
                isPersistent = false
            }
            
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val app = context.applicationContext as com.focusbyrj.app.FocusApplication
                    val repo = app.taskRepository
                    val db = app.database
                    
                    if (!sentText.startsWith("/")) {
                        val nluResult = com.focusbyrj.app.util.OfflineNluEngine.parse(sentText, pendingTasksList)
                        when (nluResult.intent) {
                                com.focusbyrj.app.util.NluIntent.CONFLICT -> {
                                    val actions = nluResult.conflictOptions.map { opt ->
                                        com.focusbyrj.app.util.TalkAction.AskQuery(
                                            query = opt.command,
                                            buttonLabel = opt.label,
                                            iconEmoji = opt.emoji
                                        )
                                    }
                                    val conflictMsg = ChatMessage(
                                        id = "conflict_${java.util.UUID.randomUUID()}",
                                        text = nluResult.conflictPrompt ?: "🤔 **Conflict Detected:** Did you mean to update an existing task or create a new one?",
                                        isUser = false,
                                        isTalkAction = true,
                                        talkActionJson = com.focusbyrj.app.util.AyvaTalkEngine.serializeActionsJson("conflict", actions)
                                    )
                                    withContext(Dispatchers.Main) {
                                        val updated = messages + conflictMsg
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                    return@launch
                                }
                                com.focusbyrj.app.util.NluIntent.CREATE_TASK -> {
                                    val (extractedTitle, extractedDueDate) = com.focusbyrj.app.util.OfflineNluEngine.extractTaskCreationDetails(sentText)
                                    val titleToUse = nluResult.createdTaskTitle?.takeIf { it.isNotBlank() } ?: extractedTitle
                                    val dueDateToUse = nluResult.targetDateMs ?: extractedDueDate
                                    val lowerText = sentText.lowercase()
                                    val detectedType = when {
                                        lowerText.contains("birthday") || lowerText.contains("bday") -> com.focusbyrj.app.data.TaskType.BIRTHDAY
                                        lowerText.contains("anniversary") -> com.focusbyrj.app.data.TaskType.ANNIVERSARY
                                        else -> com.focusbyrj.app.data.TaskType.TASK
                                    }
                                    val finalRecurrence = if (detectedRecurrence == com.focusbyrj.app.data.RecurrencePattern.NONE && (detectedType == com.focusbyrj.app.data.TaskType.BIRTHDAY || detectedType == com.focusbyrj.app.data.TaskType.ANNIVERSARY)) {
                                        com.focusbyrj.app.data.RecurrencePattern.YEARLY
                                    } else {
                                        detectedRecurrence
                                    }
                                    val parsedNote = SmartDateParser.parse(sentText).note
                                    val newTask = Task(
                                        title = titleToUse.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                        details = parsedNote ?: "",
                                        type = detectedType,
                                        isPriority = wasPriority,
                                        isPersistent = wasPersistent,
                                        recurrence = finalRecurrence,
                                        dueDate = dueDateToUse
                                    )
                                    val newId = repo.insertTask(newTask)
                                    TaskReminderHelper.scheduleReminder(context, newTask.copy(id = newId))
                                    TodoWidgetProvider.updateAllWidgets(context)

                                    val dateStr = if (dueDateToUse != null) " (Due: ${SmartDateParser.formatDueDate(dueDateToUse)})" else ""
                                    val actions = listOf(
                                        com.focusbyrj.app.util.TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                        com.focusbyrj.app.util.TalkAction.AskQuery("/reschedule $newId", "⏰ Change Time")
                                    )
                                    val confirmMsg = ChatMessage(
                                        id = "create_${java.util.UUID.randomUUID()}",
                                        text = "✅ **Added to your radar:**\n• **${newTask.title}**$dateStr",
                                        isUser = false,
                                        isTalkAction = true,
                                        talkActionJson = com.focusbyrj.app.util.AyvaTalkEngine.serializeActionsJson("tasks", actions)
                                    )
                                    withContext(Dispatchers.Main) {
                                        val updated = messages + confirmMsg
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                    return@launch
                                }
                                com.focusbyrj.app.util.NluIntent.LIST_TASKS -> {
                                    sentText = if (nluResult.isAllTasks) "/tasks all" else "/tasks"
                                }
                                com.focusbyrj.app.util.NluIntent.SHOW_PROFILE -> sentText = "/profile"
                                com.focusbyrj.app.util.NluIntent.SHOW_SUMMARY -> sentText = "/summary"
                                com.focusbyrj.app.util.NluIntent.START_DRILL -> sentText = "/drill"
                                com.focusbyrj.app.util.NluIntent.CLEAR_CHAT -> sentText = "/clear"
                                com.focusbyrj.app.util.NluIntent.RESCHEDULE,
                                com.focusbyrj.app.util.NluIntent.COMPLETE,
                                com.focusbyrj.app.util.NluIntent.DELETE,
                                com.focusbyrj.app.util.NluIntent.BLOCK_APP,
                                com.focusbyrj.app.util.NluIntent.BLOCK_FILTER,
                                com.focusbyrj.app.util.NluIntent.UNBLOCK,
                                com.focusbyrj.app.util.NluIntent.START_ROUTINE,
                                com.focusbyrj.app.util.NluIntent.STOP_ROUTINE,
                                com.focusbyrj.app.util.NluIntent.LIST_ROUTINES -> {
                                    sentText = "/talk $sentText"
                                }
                                com.focusbyrj.app.util.NluIntent.UNKNOWN -> {
                                    val lower = sentText.lowercase().trim()
                                    val isQuestionOrSetting = com.focusbyrj.app.util.AyvaTalkEngine.activeSession != null ||
                                        lower.startsWith("how") || lower.startsWith("why") || 
                                        lower.startsWith("what") || lower.startsWith("where") || 
                                        lower.startsWith("who") || lower.startsWith("when") ||
                                        lower.startsWith("can") || lower.startsWith("could") ||
                                        lower.startsWith("should") || lower.startsWith("would") ||
                                        lower.startsWith("is ") || lower.startsWith("are ") || 
                                        lower.startsWith("explain") || lower.startsWith("tell me") || 
                                        lower.startsWith("which") || lower.startsWith("does") || 
                                        lower.startsWith("do ") || lower.contains("?") ||
                                        (lower.startsWith("set ") && (lower.contains("theme") || lower.contains("timer") || lower.contains("mode") || lower.contains("sound") || lower.contains("relief") || lower.contains("language") || lower.contains("strict"))) ||
                                        (lower.startsWith("change ") && (lower.contains("theme") || lower.contains("setting") || lower.contains("mode") || lower.contains("password") || lower.contains("sound"))) ||
                                        (lower.startsWith("turn ") && (lower.contains("on") || lower.contains("off"))) ||
                                        lower.startsWith("freeze") || lower.startsWith("unfreeze") || 
                                        lower.startsWith("enable") || lower.startsWith("disable") || lower.startsWith("toggle") ||
                                        lower.startsWith("activate") || lower.startsWith("deactivate") ||
                                        lower.contains("screentime") || lower.contains("screen time") ||
                                        lower.contains("usage") || lower.contains("breathe") ||
                                        lower.contains("breathing") || lower.contains("relax") ||
                                        lower.contains("calm down") || lower.contains("vacation") || 
                                        lower.contains("settings") || lower.contains("permission") || 
                                        lower.contains("troubleshoot") || 
                                        lower.contains("widget") || lower.contains("relief") || 
                                        lower.contains("strict") || lower.contains("uninstall") || 
                                        lower.contains("video call") || lower.contains("drill") || 
                                        (lower.contains("advice") && !lower.contains("task")) || 
                                        (lower.contains("tips") && !lower.contains("task")) || 
                                        lower.contains("ayva") || 
                                        lower.contains("posture") ||
                                        lower.contains("procrastinat") || lower.contains("distract") ||
                                        lower in listOf(
                                            "hi", "hello", "hey", "hey ayva", "hello ayva", "hi ayva", 
                                            "good morning", "good afternoon", "good evening", "goodnight", 
                                            "howdy", "sup", "help", "guide", "info", "menu", "commands", 
                                            "features", "options", "thanks", "thank you", "thx", "bye"
                                        )
                                    if (isQuestionOrSetting) {
                                        sentText = "/talk $sentText"
                                    }
                                }
                            }
                        }

                    if (sentText.startsWith("/")) {
                        val parts = sentText.split(" ")
                        val cmd = parts[0].lowercase()
                        var replyMsg = "Command not recognized."
                        
                        when (cmd) {
                            "/profile", "/stats", "/xp", "/level" -> {
                                val profMsg = ChatMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    text = "Aptitude Profile",
                                    isUser = false,
                                    isAptitudeProfile = true
                                )
                                withContext(Dispatchers.Main) {
                                    messages = messages + profMsg
                                }
                                return@launch
                            }
                            "/quests", "/daily", "/tasks_learning", "/chest", "/box", "/mystery" -> {
                                val questMsg = ChatMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    text = "Daily Learning Quests & Mystery Chest",
                                    isUser = false,
                                    isDailyQuests = true
                                )
                                withContext(Dispatchers.Main) {
                                    messages = messages + questMsg
                                }
                                return@launch
                            }
                            "/blitz", "/speed" -> {
                                val newSession = createDrillSessionWithQuestions("easy", 10).copy(
                                    difficulty = "easy",
                                    targetQuestions = -1,
                                    isBlitz = true,
                                    blitzSecondsRemaining = 300
                                )
                                withContext(Dispatchers.Main) {
                                    activeDrillSession = newSession
                                }
                                return@launch
                            }
                            "/freeze", "/shield" -> {
                                val count = com.focusbyrj.app.util.AptitudeManager.getStreakFreezesCount()
                                val text = if (count < 3) {
                                    "🛡️ You have $count / 3 Streak Freezes equipped.\n\nEquip a Streak Freeze from `/profile` (200 🪙) to automatically protect your daily streak if you ever miss a practice day!"
                                } else {
                                    "🛡️ You have maximum Streak Freezes equipped (3 / 3)!\n\nYour streak will automatically be protected if you ever miss a practice day."
                                }
                                val response = ChatMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    text = text,
                                    isUser = false
                                )
                                withContext(Dispatchers.Main) {
                                    messages = messages + response
                                }
                                return@launch
                            }
                            "/wager" -> {
                                val prof = com.focusbyrj.app.util.AptitudeManager.profileFlow.value
                                val text = if (prof.isWagerActive) {
                                    "💰 7-Day Wager Active: Day ${prof.wagerDaysCompleted}/7 completed!\n\nMaintain your practice streak to win 10,000 Gold Coins and 100 XP!"
                                } else {
                                    "💰 7-Day Learning Wager\n\nStake 5,000 Gold Coins from your wallet. Practice 7 days in a row to double your coins (10,000 🪙 + 100 XP)!\n\nOpen `/profile` to enter the wager."
                                }
                                val response = ChatMessage(
                                    id = java.util.UUID.randomUUID().toString(),
                                    text = text,
                                    isUser = false
                                )
                                withContext(Dispatchers.Main) {
                                    messages = messages + response
                                }
                                return@launch
                            }
                            "/drill", "/math", "/quiz" -> {
                                val difficultyStr = parts.getOrNull(1)?.lowercase() ?: "easy"
                                val limitStr = parts.getOrNull(2)?.lowercase() ?: "10"
                                val targetQ = when(limitStr) {
                                    "10" -> 10
                                    "20" -> 20
                                    "unlimited", "infinite", "endless" -> -1
                                    else -> 10
                                }
                                val newSession = createDrillSessionWithQuestions(difficultyStr, targetQ)
                                withContext(Dispatchers.Main) {
                                    activeDrillSession = newSession
                                }
                                return@launch
                            }
                            "/clear", "/clean", "/wipe" -> {
                                withContext(Dispatchers.Main) {
                                    val welcome = ChatMessage(
                                        id = "welcome_${java.util.UUID.randomUUID()}",
                                        text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                                        isUser = false,
                                        timestamp = System.currentTimeMillis()
                                    )
                                    messages = listOf(welcome)
                                    BubbleChatManager.saveMessages(context, listOf(
                                        PersistedChatMessage(welcome.id, welcome.text, welcome.isUser, welcome.timestamp)
                                    ))
                                    BubbleChatManager.clearUnread(context)
                                }
                                return@launch
                            }
                            "/talk", "/advice", "/breathe", "/screentime", "/tips", "/coach", "/guide", "/ask", "/how", "/help", "/faq", "/info", "/menu", "/settings", "/vacation", "/streak", "/routines", "/unblock", "/block", "/apps", "/diagnose" -> {
                                val query = if (cmd != "/talk" && parts.size == 1) {
                                    cmd.removePrefix("/")
                                } else if (parts.size > 1) {
                                    parts.drop(1).joinToString(" ")
                                } else {
                                    sentText.removePrefix("/").trim()
                                }
                                val talkResp = try {
                                    com.focusbyrj.app.util.AyvaTalkEngine.answerTalkQueryWithActions(query, context)
                                } catch (e: Exception) {
                                    com.focusbyrj.app.util.AyvaTalkEngine.TalkResponse(
                                        formattedText = "💬 How can I help you? Ask me anything about FocusByRj settings, routines, vacation mode, or troubleshooting.",
                                        actions = listOf(
                                            com.focusbyrj.app.util.TalkAction.NavigateAppScreen("settings", "Open Settings", "⚙️")
                                        )
                                    )
                                }
                                
                                val isPending = talkResp.jsonPayload?.contains("\"status\":\"pending\"") == true
                                val isExecutableAction = talkResp.jsonPayload?.contains("\"status\":\"pending\"") == false && talkResp.jsonPayload != null
                                val hasActions = talkResp.actions.isNotEmpty()

                                val talkMsg = ChatMessage(
                                    id = "talk_${java.util.UUID.randomUUID()}",
                                    text = talkResp.formattedText.ifBlank { "Here is what I found:" },
                                    isUser = false,
                                    isTalkAction = hasActions || isExecutableAction,
                                    talkActionJson = if (!isPending) talkResp.jsonPayload else null,
                                    pendingActionJson = if (isPending) talkResp.jsonPayload else null
                                )
                                withContext(Dispatchers.Main) {
                                    messages = messages + talkMsg
                                }
                                return@launch
                            }
                            "/task", "/tasks", "/summary", "/create", "/add", "/todo" -> {
                                val isSummaryCommand = cmd == "/summary"
                                val subArg = parts.getOrNull(1)?.lowercase()?.trim() ?: ""
                                val isListTasks = isSummaryCommand || (cmd == "/tasks") || ((cmd == "/task") && (subArg.isEmpty() || subArg in listOf("all", "today", "list", "pending", "everything")))
                                
                                if (!isListTasks && (cmd == "/task" || cmd == "/create" || cmd == "/add" || cmd == "/todo")) {
                                    // User is adding a task via /task, /create, /add, or /todo <title>
                                    val rawTaskContent = sentText.removePrefix(cmd).trim().removePrefix("add ").trim()
                                    val parsed = SmartDateParser.parse(rawTaskContent)
                                    val taskTitle = parsed.cleanText.ifBlank { rawTaskContent }
                                    val tDueDate = parsed.timestamp
                                    val lowerContent = rawTaskContent.lowercase()
                                    val tType = when {
                                        lowerContent.contains("birthday") || lowerContent.contains("bday") -> com.focusbyrj.app.data.TaskType.BIRTHDAY
                                        lowerContent.contains("anniversary") -> com.focusbyrj.app.data.TaskType.ANNIVERSARY
                                        else -> com.focusbyrj.app.data.TaskType.TASK
                                    }
                                    val tRecurrence = if (parsed.recurrence == com.focusbyrj.app.data.RecurrencePattern.NONE && (tType == com.focusbyrj.app.data.TaskType.BIRTHDAY || tType == com.focusbyrj.app.data.TaskType.ANNIVERSARY)) {
                                        com.focusbyrj.app.data.RecurrencePattern.YEARLY
                                    } else {
                                        parsed.recurrence
                                    }
                                    val tTask = Task(
                                        title = taskTitle,
                                        details = parsed.note ?: "",
                                        type = tType,
                                        isPriority = wasPriority,
                                        isPersistent = wasPersistent,
                                        recurrence = tRecurrence,
                                        dueDate = tDueDate
                                    )
                                    val createdId = repo.insertTask(tTask)
                                    TaskReminderHelper.scheduleReminder(context, tTask.copy(id = createdId))
                                    TodoWidgetProvider.updateAllWidgets(context)

                                    withContext(Dispatchers.Main) {
                                        val dueStr = if (tDueDate != null) SmartDateParser.formatDueDate(tDueDate) else null
                                        val recStr = if (tRecurrence != com.focusbyrj.app.data.RecurrencePattern.NONE) tRecurrence.name.lowercase() else null
                                        val confirmationText = com.focusbyrj.app.util.AyvaDialogueEngine.getTaskAddedResponse(
                                            context = context,
                                            title = taskTitle,
                                            isPriority = wasPriority,
                                            hasDueDate = tDueDate != null,
                                            dueDateStr = dueStr,
                                            attrStr = recStr
                                        )
                                        val confirmActions = listOf(
                                            com.focusbyrj.app.util.TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                                            com.focusbyrj.app.util.TalkAction.AskQuery("/reschedule $createdId", "⏰ Change Time")
                                        )
                                        val confirmMsg = ChatMessage(
                                            id = "create_${java.util.UUID.randomUUID()}",
                                            text = confirmationText,
                                            isUser = false,
                                            isTalkAction = true,
                                            talkActionJson = com.focusbyrj.app.util.AyvaTalkEngine.serializeActionsJson("tasks", confirmActions)
                                        )
                                        val updated = messages + confirmMsg
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                    return@launch
                                }

                                val isAll = subArg == "all" || subArg == "everything" || subArg == "pending"
                                
                                val now = System.currentTimeMillis()
                                val startOfDay = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                                    set(java.util.Calendar.MINUTE, 0)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                }.timeInMillis
                                val endOfDay = startOfDay + 86400000L - 1
                                
                                // Cleanup old completed tasks
                                repo.deleteCompletedTasksBefore(startOfDay)
                                
                                val allTasks = repo.allTasks.first()
                                val completedToday = allTasks.filter { it.isCompleted && it.completedAt != null && it.completedAt >= startOfDay }
                                val pendingTasks = allTasks.filter { !it.isCompleted }
                                
                                val targetTasks = if (isAll) {
                                    pendingTasks
                                } else {
                                    pendingTasks.filter { it.dueDate == null || (it.dueDate in startOfDay..endOfDay) || it.dueDate < startOfDay }
                                }
                                
                                val overdueCount = targetTasks.count { it.dueDate != null && it.dueDate < now }
                                val sortedTasks = targetTasks.sortedWith(compareByDescending<com.focusbyrj.app.data.Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE })
                                
                                val taskJsonArray = org.json.JSONArray()
                                sortedTasks.forEach { t ->
                                    val obj = org.json.JSONObject().apply {
                                        put("id", t.id)
                                        put("title", t.title)
                                        put("isPriority", t.isPriority)
                                        put("dueDate", t.dueDate ?: 0L)
                                        put("isCompleted", t.isCompleted)
                                        put("isPersistent", t.isPersistent)
                                        put("filterMode", if (isAll) "all" else "today")
                                    }
                                    taskJsonArray.put(obj)
                                }
                                
                                val builder = StringBuilder()
                                val cal = java.util.Calendar.getInstance()
                                val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                                val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
                                val greeting = com.focusbyrj.app.util.AyvaDialogueEngine.getSummaryGreeting(context, isAll, hour, dayOfWeek)
                                builder.append(greeting).append("\n\n")
                                
                                if (completedToday.isNotEmpty() && !isAll) {
                                    builder.append("✅ *__Crushed Today__* *(${completedToday.size})*:\n")
                                    completedToday.forEach { task ->
                                        builder.append("• _${task.title}_\n")
                                    }
                                    builder.append("\n")
                                }
                                
                                val isMorningBriefQuery = (isSummaryCommand && (subArg == "morning" || (subArg != "evening" && !isAll && hour < 12))) || sentText.contains("morning", ignoreCase = true)
                                val isEveningBriefQuery = (isSummaryCommand && (subArg == "evening" || (subArg != "morning" && !isAll && hour >= 17))) || sentText.contains("evening", ignoreCase = true) || sentText.contains("night", ignoreCase = true)
                                
                                if (isMorningBriefQuery || isEveningBriefQuery) {
                                    val vocabRepo = (context.applicationContext as com.focusbyrj.app.FocusApplication).vocabRepository
                                    val newIdiom = vocabRepo.getNextIdiomToLearn()
                                    val newOws = vocabRepo.getNextOwsToLearn()
                                    val revIdiom = vocabRepo.getLastLearnedIdiom()
                                    val revOws = vocabRepo.getLastLearnedOws()
                                    
                                    if (newIdiom != null) vocabRepo.markIdiomLearned(newIdiom)
                                    if (newOws != null) vocabRepo.markOwsLearned(newOws)
                                    
                                    val vocabObj = org.json.JSONObject()
                                    if (newIdiom != null) {
                                        vocabObj.put("idiom", org.json.JSONObject().apply {
                                            put("idiom", newIdiom.idiom)
                                            put("meaning", newIdiom.meaning)
                                        })
                                    }
                                    if (newOws != null) {
                                        vocabObj.put("ows", org.json.JSONObject().apply {
                                            put("term", newOws.term)
                                            put("definition", newOws.definition)
                                        })
                                    }
                                    if (revIdiom != null) {
                                        vocabObj.put("rev_idiom", org.json.JSONObject().apply {
                                            put("idiom", revIdiom.idiom)
                                            put("meaning", revIdiom.meaning)
                                        })
                                    }
                                    if (revOws != null) {
                                        vocabObj.put("rev_ows", org.json.JSONObject().apply {
                                            put("term", revOws.term)
                                            put("definition", revOws.definition)
                                        })
                                    }
                                    
                                    val summaryResponse = ChatMessage(
                                        id = if (isMorningBriefQuery) "morning_${System.currentTimeMillis()}" else "evening_${System.currentTimeMillis()}",
                                        text = if (isMorningBriefQuery) "☀️ Good morning! Let's build your vocabulary today." else "🌙 Good evening! Time for your nightly vocab drip.",
                                        isUser = false,
                                        isMorningBrief = isMorningBriefQuery,
                                        isEveningBrief = isEveningBriefQuery,
                                        isVocabBrief = true,
                                        vocabJson = vocabObj.toString()
                                    )
                                    
                                    withContext(Dispatchers.Main) {
                                        messages = messages + summaryResponse
                                    }
                                    return@launch
                                }

                                if (sortedTasks.isEmpty()) {
                                    builder.append(com.focusbyrj.app.util.AyvaDialogueEngine.getEmptyDayMessage(context)).append("\n")
                                    val nextTask = pendingTasks
                                        .filter { it.dueDate != null && it.dueDate > endOfDay }
                                        .minByOrNull { it.dueDate!! }
                                    if (nextTask != null) {
                                        builder.append("\n🗓️ *__Up Next On The Horizon__*: *${nextTask.title}* _(${SmartDateParser.formatDueDate(nextTask.dueDate)})_\n")
                                    }
                                } else {
                                    val headerTitle = if (isAll) "Pending Tasks" else "On Today's Hit List"
                                    val icon = if (isAll) "⏳" else "⚡"
                                    builder.append("$icon *__${headerTitle}__* *(${sortedTasks.size})*")
                                    if (overdueCount > 0) builder.append(" *[⚠️ $overdueCount Overdue]*")
                                    builder.append(":\n")
                                    
                                    sortedTasks.forEachIndexed { index, task ->
                                        val prefix = if (task.isPriority) "🔥 " else ""
                                        val titleFormatted = if (task.isPriority) "*${task.title}*" else task.title
                                        val dueStr = if (task.dueDate != null) " _(Due: ${SmartDateParser.formatDueDate(task.dueDate)})_" else ""
                                        builder.append("${index + 1}. $prefix$titleFormatted$dueStr\n")
                                    }
                                    if (!isAll) {
                                        val nextTask = pendingTasks
                                            .filter { it.dueDate != null && it.dueDate > endOfDay }
                                            .minByOrNull { it.dueDate!! }
                                        if (nextTask != null) {
                                            builder.append("\n🗓️ *__Up Next On The Horizon__*: *${nextTask.title}* _(${SmartDateParser.formatDueDate(nextTask.dueDate)})_\n")
                                        }
                                    }
                                }
                                
                                val totalToday = completedToday.size + targetTasks.size
                                val percent = if (totalToday > 0) {
                                    (completedToday.size * 100) / totalToday
                                } else {
                                    100
                                }
                                val filledBlocks = (percent / 10).coerceIn(0, 10)
                                val emptyBlocks = 10 - filledBlocks
                                val progressBar = "█".repeat(filledBlocks) + "░".repeat(emptyBlocks)
                                
                                builder.append("\n📊 *__Daily Progress__*:\n")
                                builder.append("`[$progressBar]` *$percent%*")
                                
                                val quote = if (hour < 15) {
                                    com.focusbyrj.app.util.SummaryQuotes.getNextMorningQuote(context)
                                } else {
                                    com.focusbyrj.app.util.SummaryQuotes.getNextEveningQuote(context)
                                }
                                builder.append("\n\n💡 _\"$quote\"_")
                                
                                if (sortedTasks.isNotEmpty()) {
                                    builder.append("\n\n").append(com.focusbyrj.app.util.AyvaDialogueEngine.getReschedulePrompt(context))
                                }
                                
                                val summaryResponse = ChatMessage(
                                    id = "summary_${java.util.UUID.randomUUID()}",
                                    text = builder.toString().trimEnd(),
                                    isUser = false,
                                    isTaskSummary = !isSummaryCommand,
                                    taskSummaryJson = if (isSummaryCommand) null else taskJsonArray.toString(),
                                    isMorningBrief = false,
                                    isEveningBrief = false
                                )
                                
                                withContext(Dispatchers.Main) {
                                    lastSummaryTasks = sortedTasks
                                    val updated = messages + summaryResponse
                                    messages = updated
                                    BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                }
                                return@launch
                            }
                            "/vocab" -> {
                                val sub = parts.getOrNull(1)?.lowercase()?.trim()
                                val vocabRepo = (context.applicationContext as com.focusbyrj.app.FocusApplication).vocabRepository
                                if (sub == "learn_more" || sub == "learn") {
                                    val newIdiom = vocabRepo.getNextIdiomToLearn()
                                    val newOws = vocabRepo.getNextOwsToLearn()
                                    
                                    val vocabObj = org.json.JSONObject()
                                    if (newIdiom != null) {
                                        vocabObj.put("idiom", org.json.JSONObject().apply {
                                            put("id", newIdiom.id ?: -1)
                                            put("idiom", newIdiom.idiom)
                                            put("meaning", newIdiom.meaning)
                                        })
                                    }
                                    if (newOws != null) {
                                        vocabObj.put("ows", org.json.JSONObject().apply {
                                            put("id", newOws.id ?: -1)
                                            put("term", newOws.term)
                                            put("definition", newOws.definition)
                                        })
                                    }
                                    
                                    val summaryResponse = ChatMessage(
                                        id = "vocab_${System.currentTimeMillis()}",
                                        text = "Keep the momentum going! Here are your next ones:",
                                        isUser = false,
                                        isVocabBrief = true,
                                        vocabJson = vocabObj.toString()
                                    )
                                    withContext(Dispatchers.Main) {
                                        val updated = messages + summaryResponse
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                } else {
                                    // Default /vocab, /vocab stats, /srs, /retention shows the Spaced Repetition Hub card
                                    val hubResponse = ChatMessage(
                                        id = "vocab_hub_${System.currentTimeMillis()}",
                                        text = "Spaced Repetition Hub",
                                        isUser = false,
                                        isVocabHub = true
                                    )
                                    withContext(Dispatchers.Main) {
                                        val updated = messages + hubResponse
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                }
                                return@launch
                            }
                            "/vocab_quiz" -> {
                                val vocabRepo = (context.applicationContext as com.focusbyrj.app.FocusApplication).vocabRepository
                                val (learnedIdioms, learnedOws) = vocabRepo.getQuizWords()
                                
                                val quizList = mutableListOf<org.json.JSONObject>()
                                
                                learnedIdioms.forEach { idiom ->
                                    val otherIdioms = vocabRepo.getAllLearnedIdioms().filter { it.id != idiom.id }.shuffled().take(3).map { it.meaning }.toMutableList()
                                    if (otherIdioms.size < 3) {
                                        otherIdioms.addAll(vocabRepo.vocabDao.getUnlearnedIdioms(3).map { it.meaning })
                                    }
                                    val options = (otherIdioms.take(3) + idiom.meaning).shuffled()
                                    val correctIndex = options.indexOf(idiom.meaning)
                                    quizList.add(org.json.JSONObject().apply {
                                        put("title", "Idioms")
                                        put("vocabType", "idiom")
                                        put("vocabId", idiom.id ?: -1)
                                        put("questionText", "What does '${idiom.idiom}' mean?")
                                        val arr = org.json.JSONArray()
                                        options.forEach { arr.put(it) }
                                        put("options", arr)
                                        put("correctIndex", correctIndex)
                                        put("explanation", "The idiom '${idiom.idiom}' means: ${idiom.meaning}")
                                    })
                                }
                                
                                learnedOws.forEach { ows ->
                                    val otherOws = vocabRepo.getAllLearnedOws().filter { it.id != ows.id }.shuffled().take(3).map { it.term }.toMutableList()
                                    if (otherOws.size < 3) {
                                        otherOws.addAll(vocabRepo.vocabDao.getUnlearnedOws(3).map { it.term })
                                    }
                                    val options = (otherOws.take(3) + ows.term).shuffled()
                                    val correctIndex = options.indexOf(ows.term)
                                    quizList.add(org.json.JSONObject().apply {
                                        put("title", "One Word Substitution")
                                        put("vocabType", "ows")
                                        put("vocabId", ows.id ?: -1)
                                        put("questionText", "Find the word for: '${ows.definition}'")
                                        val arr = org.json.JSONArray()
                                        options.forEach { arr.put(it) }
                                        put("options", arr)
                                        put("correctIndex", correctIndex)
                                        put("explanation", "The word for '${ows.definition}' is ${ows.term}.")
                                    })
                                }
                                
                                quizList.shuffle()
                                val finalQuestions = quizList.take(20).map { it.toString() }
                                
                                if (finalQuestions.isEmpty()) {
                                    val summaryResponse = ChatMessage(
                                        id = "vocab_empty_${System.currentTimeMillis()}",
                                        text = "You haven't learned any vocabulary yet! Let's learn some words first with `/vocab learn_more`.",
                                        isUser = false
                                    )
                                    withContext(Dispatchers.Main) {
                                        val updated = messages + summaryResponse
                                        messages = updated
                                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                    }
                                    return@launch
                                }

                                val session = DrillSession(
                                    difficulty = "vocab",
                                    targetQuestions = finalQuestions.size,
                                    isBlitz = false
                                ).apply {
                                    preGeneratedQuestions.addAll(finalQuestions)
                                }
                                
                                withContext(Dispatchers.Main) {
                                    activeDrillSession = session
                                    
                                }
                                return@launch
                            }
                            "/habit", "/habits" -> {
                                val habitRepo = (context.applicationContext as com.focusbyrj.app.FocusApplication).habitRepository
                                val habits = habitRepo.activeHabitsWithProgress.first()
                                
                                val habitJsonArray = org.json.JSONArray()
                                habits.forEach { hp ->
                                    val h = hp.habit
                                    val obj = org.json.JSONObject().apply {
                                        put("id", h.id)
                                        put("title", h.title)
                                        put("description", h.description)
                                        put("iconEmoji", h.iconEmoji)
                                        put("colorHex", h.colorHex)
                                        put("type", h.type.name)
                                        put("targetPerDay", h.targetPerDay)
                                        put("intervalHours", h.intervalHours)
                                        put("intervalMinutes", h.intervalMinutes)
                                        put("windowStartHour", h.windowStartHour)
                                        put("windowStartMinute", h.windowStartMinute)
                                        put("windowEndHour", h.windowEndHour)
                                        put("windowEndMinute", h.windowEndMinute)
                                        put("fixedReminderHour", h.fixedReminderHour)
                                        put("fixedReminderMinute", h.fixedReminderMinute)
                                        put("completedToday", hp.completedToday)
                                        put("targetToday", hp.targetToday)
                                        put("currentStreak", hp.currentStreak)
                                        put("isCompletedToday", hp.isCompletedToday)
                                    }
                                    habitJsonArray.put(obj)
                                }

                                val habitMsg = ChatMessage(
                                    id = "habits_${java.util.UUID.randomUUID()}",
                                    text = "Habit Radar",
                                    isUser = false,
                                    isHabitsSummary = true,
                                    habitsSummaryJson = habitJsonArray.toString()
                                )
                                withContext(Dispatchers.Main) {
                                    val updated = messages + habitMsg
                                    messages = updated
                                    BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                }
                                return@launch
                            }
                            "/reschedule" -> {
                                val allPending = repo.allTasks.first().filter { !it.isCompleted }
                                    .sortedWith(compareByDescending<com.focusbyrj.app.data.Task> { it.isPriority }.thenBy { it.dueDate ?: Long.MAX_VALUE })
                                val targetList = if (lastSummaryTasks.isNotEmpty()) lastSummaryTasks else allPending

                                val rawQuery = parts.drop(1).joinToString(" ").trim()
                                if (rawQuery.isBlank()) {
                                    if (targetList.isEmpty()) {
                                        replyMsg = "No active tasks found to reschedule! 🎯"
                                    } else {
                                        val builder = StringBuilder()
                                        builder.append("📋 *__Pending Tasks for Rescheduling__*:\n")
                                        targetList.forEachIndexed { index, task ->
                                            val prefix = if (task.isPriority) "🔥 " else ""
                                            val dueStr = if (task.dueDate != null) " _(Due: ${SmartDateParser.formatDueDate(task.dueDate)})_" else ""
                                            builder.append("${index + 1}. $prefix${task.title}$dueStr\n")
                                        }
                                        builder.append("\n_Type `/reschedule <number or name> <time>` (e.g. `/reschedule 1 tomorrow at 4pm` or `/reschedule gym 5pm`)_")
                                        replyMsg = builder.toString().trimEnd()
                                    }
                                } else {
                                    val nluResult = com.focusbyrj.app.util.OfflineNluEngine.parse("reschedule $rawQuery", targetList)
                                    if (nluResult.isAllTasks) {
                                        val newDate = nluResult.targetDateMs ?: (System.currentTimeMillis() + 86400000L)
                                        targetList.forEach { task ->
                                            val updated = task.copy(dueDate = newDate)
                                            repo.updateTask(updated)
                                            TaskReminderHelper.scheduleReminder(context, updated)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        replyMsg = "⏰ **Rescheduled all ${targetList.size} tasks** to ${SmartDateParser.formatDueDate(newDate)}."
                                    } else if (nluResult.targetTask != null) {
                                        val task = nluResult.targetTask
                                        val newDate = if (!nluResult.hasExplicitTimeSpecified && task.dueDate != null && nluResult.targetDateMs != null) {
                                            val prevCal = java.util.Calendar.getInstance().apply { timeInMillis = task.dueDate }
                                            val targetCal = java.util.Calendar.getInstance().apply { timeInMillis = nluResult.targetDateMs }
                                            targetCal.set(java.util.Calendar.HOUR_OF_DAY, prevCal.get(java.util.Calendar.HOUR_OF_DAY))
                                            targetCal.set(java.util.Calendar.MINUTE, prevCal.get(java.util.Calendar.MINUTE))
                                            targetCal.set(java.util.Calendar.SECOND, 0)
                                            targetCal.set(java.util.Calendar.MILLISECOND, 0)
                                            targetCal.timeInMillis
                                        } else {
                                            nluResult.targetDateMs ?: (System.currentTimeMillis() + 86400000L)
                                        }
                                        val updatedTask = task.copy(dueDate = newDate)
                                        repo.updateTask(updatedTask)
                                        TaskReminderHelper.scheduleReminder(context, updatedTask)
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        replyMsg = com.focusbyrj.app.util.AyvaDialogueEngine.getRescheduleSuccessResponse(context, task.title, SmartDateParser.formatDueDate(newDate))
                                    } else if (nluResult.matchingTasks.isNotEmpty()) {
                                        val builder = StringBuilder()
                                        builder.append("🤔 **Found ${nluResult.matchingTasks.size} tasks matching '${nluResult.filterQuery ?: rawQuery}':**\n\n")
                                        nluResult.matchingTasks.forEachIndexed { index, task ->
                                            builder.append("${index + 1}. **${task.title}**\n")
                                        }
                                        builder.append("\n_Specify: `/reschedule [number or name] [time]`_")
                                        replyMsg = builder.toString()
                                    } else {
                                        val numStr = parts.getOrNull(1)
                                        val timeStr = parts.drop(2).joinToString(" ")
                                        val num = numStr?.toIntOrNull()
                                        if (num != null && num in 1..targetList.size && timeStr.isNotBlank()) {
                                            val task = targetList[num - 1]
                                            val parsed = SmartDateParser.parse("reschedule to $timeStr")
                                            if (parsed.timestamp != null) {
                                                val updatedTask = task.copy(dueDate = parsed.timestamp)
                                                repo.updateTask(updatedTask)
                                                TaskReminderHelper.scheduleReminder(context, updatedTask)
                                                TodoWidgetProvider.updateAllWidgets(context)
                                                replyMsg = com.focusbyrj.app.util.AyvaDialogueEngine.getRescheduleSuccessResponse(context, task.title, SmartDateParser.formatDueDate(parsed.timestamp))
                                            } else {
                                                replyMsg = "Couldn't decipher '$timeStr'. Try something like 'tomorrow at 3pm' or '5pm'."
                                            }
                                        } else {
                                            replyMsg = "Hmm, couldn't find a task matching '$rawQuery'. Check `/tasks` or use `/reschedule <number> <time>`."
                                        }
                                    }
                                }
                            }
                            "/priority" -> {
                                val tasks = repo.allTasks.first()
                                val priority = tasks.filter { it.isPriority && !it.isCompleted }
                                replyMsg = if (priority.isEmpty()) com.focusbyrj.app.util.AyvaDialogueEngine.getPriorityEmptyResponse(context)
                                    else "🔥 *__Ayva's Priority Radar__*:\n" + priority.joinToString("\n") { "• ${it.title}" }
                            }
                            "/postpone" -> {
                                val tasks = repo.allTasks.first().filter { !it.isCompleted }
                                if (tasks.isEmpty()) {
                                    replyMsg = "No pending tasks to postpone! 🎯"
                                } else {
                                    tasks.forEach { 
                                        val updatedTask = it.copy(dueDate = System.currentTimeMillis() + 86400000L)
                                        repo.updateTask(updatedTask)
                                        TaskReminderHelper.scheduleReminder(context, updatedTask)
                                    }
                                    TodoWidgetProvider.updateAllWidgets(context)
                                    replyMsg = com.focusbyrj.app.util.AyvaDialogueEngine.getPostponeAllResponse(context, tasks.size)
                                }
                            }
                            else -> {
                                val query = sentText.removePrefix("/").trim()
                                val talkResp = try {
                                    com.focusbyrj.app.util.AyvaTalkEngine.answerTalkQueryWithActions(query, context)
                                } catch (e: Exception) {
                                    com.focusbyrj.app.util.AyvaTalkEngine.TalkResponse(
                                        formattedText = "💬 How can I help you? Ask me anything about FocusByRj settings, routines, vacation mode, or troubleshooting.",
                                        actions = listOf(
                                            com.focusbyrj.app.util.TalkAction.NavigateAppScreen("settings", "Open Settings", "⚙️")
                                        )
                                    )
                                }
                                
                                val isPending = talkResp.jsonPayload?.contains("\"status\":\"pending\"") == true
                                val isExecutableAction = talkResp.jsonPayload?.contains("\"status\":\"pending\"") == false && talkResp.jsonPayload != null
                                val hasActions = talkResp.actions.isNotEmpty()

                                val talkMsg = ChatMessage(
                                    id = "talk_${java.util.UUID.randomUUID()}",
                                    text = talkResp.formattedText.ifBlank { "Here is what I found:" },
                                    isUser = false,
                                    isTalkAction = hasActions || isExecutableAction,
                                    talkActionJson = if (!isPending) talkResp.jsonPayload else null,
                                    pendingActionJson = if (isPending) talkResp.jsonPayload else null
                                )
                                withContext(Dispatchers.Main) {
                                    val updated = messages + talkMsg
                                    messages = updated
                                    BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                                }
                                return@launch
                            }
                        }
                        val isSummaryCmd = parts.firstOrNull()?.equals("/summary", ignoreCase = true) == true
                        withContext(Dispatchers.Main) {
                            val replyBotMsg = ChatMessage(
                                id = "bot_${java.util.UUID.randomUUID()}",
                                text = replyMsg,
                                isUser = false,
                                isTaskSummary = isSummaryCmd && lastSummaryTasks.isNotEmpty()
                            )
                            val updated = messages + replyBotMsg
                            messages = updated
                            BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                        }
                        return@launch
                    }
                
                // INTELLIGENCE UPGRADE: Natural language intent interception
                val lowerSent = sentText.lowercase()
                val isExplicitTask = com.focusbyrj.app.util.OfflineNluEngine.isExplicitCreation(sentText)
                val isLikelyTalkIntent = !isExplicitTask && (
                    (lowerSent.startsWith("set ") && (lowerSent.contains("theme") || lowerSent.contains("timer") || lowerSent.contains("mode") || lowerSent.contains("strict") || lowerSent.contains("sound") || lowerSent.contains("haptic"))) ||
                    (lowerSent.startsWith("change ") && (lowerSent.contains("theme") || lowerSent.contains("mode") || lowerSent.contains("setting") || lowerSent.contains("password") || lowerSent.contains("sound"))) ||
                    lowerSent.startsWith("why ") || lowerSent.startsWith("how ") || 
                    lowerSent.startsWith("what is ") || (lowerSent.startsWith("what ") && !lowerSent.contains("task")) ||
                    lowerSent.startsWith("disable ") || lowerSent.startsWith("enable ") || 
                    lowerSent.startsWith("turn on ") || lowerSent.startsWith("turn off ") ||
                    lowerSent.startsWith("freeze") || lowerSent.startsWith("unfreeze") ||
                    lowerSent.contains("vacation") || lowerSent.contains("troubleshoot") ||
                    lowerSent.contains("settings") || lowerSent.contains("permission") ||
                    lowerSent.contains("?")
                )
                
                if (isLikelyTalkIntent) {
                    val talkResp = com.focusbyrj.app.util.AyvaTalkEngine.answerTalkQueryWithActions(sentText, context)
                    val talkMsg = ChatMessage(
                        id = "talk_${java.util.UUID.randomUUID()}",
                        text = talkResp.formattedText,
                        isUser = false,
                        isTalkAction = talkResp.actions.isNotEmpty() || (talkResp.jsonPayload?.contains("\"status\":\"pending\"") == false && talkResp.jsonPayload != null),
                        talkActionJson = if (talkResp.jsonPayload?.contains("\"status\":\"pending\"") == false) talkResp.jsonPayload else null,
                        pendingActionJson = if (talkResp.jsonPayload?.contains("\"status\":\"pending\"") == true) talkResp.jsonPayload else null
                    )
                    withContext(Dispatchers.Main) {
                        val updated = messages + talkMsg
                        messages = updated
                        BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                    }
                    return@launch
                }
                
                val lowerTitle = finalTitle.lowercase()
                val detectedType = when {
                    lowerTitle.contains("birthday") || lowerTitle.contains("bday") -> com.focusbyrj.app.data.TaskType.BIRTHDAY
                    lowerTitle.contains("anniversary") -> com.focusbyrj.app.data.TaskType.ANNIVERSARY
                    else -> com.focusbyrj.app.data.TaskType.TASK
                }
                val finalRecurrence = if (detectedRecurrence == com.focusbyrj.app.data.RecurrencePattern.NONE && (detectedType == com.focusbyrj.app.data.TaskType.BIRTHDAY || detectedType == com.focusbyrj.app.data.TaskType.ANNIVERSARY)) {
                    com.focusbyrj.app.data.RecurrencePattern.YEARLY
                } else {
                    detectedRecurrence
                }
                val newTask = Task(
                    title = finalTitle,
                    details = effectiveParsed.note ?: "",
                    type = detectedType,
                    isPriority = wasPriority,
                    isPersistent = wasPersistent,
                    recurrence = finalRecurrence,
                    dueDate = dueDate
                )
                val newId = repo.insertTask(newTask)
                TaskReminderHelper.scheduleReminder(context, newTask.copy(id = newId))
                TodoWidgetProvider.updateAllWidgets(context)
                
                withContext(Dispatchers.Main) {
                    val attrs = mutableListOf<String>()
                    if (wasPriority) attrs.add("priority")
                    if (wasPersistent) attrs.add("persistent")
                    if (detectedRecurrence != com.focusbyrj.app.data.RecurrencePattern.NONE) {
                        attrs.add(detectedRecurrence.name.lowercase())
                    }
                    
                    val attrStr = if (attrs.isNotEmpty()) attrs.joinToString(" and ") else null
                    val dueStr = if (dueDate != null) SmartDateParser.formatDueDate(dueDate) else null
                    
                    val confirmationText = com.focusbyrj.app.util.AyvaDialogueEngine.getTaskAddedResponse(
                        context = context,
                        title = finalTitle,
                        isPriority = wasPriority,
                        hasDueDate = dueDate != null,
                        dueDateStr = dueStr,
                        attrStr = attrStr
                    )
                    
                    val actions = listOf(
                        com.focusbyrj.app.util.TalkAction.AskQuery("/tasks", "📋 View Tasks"),
                        com.focusbyrj.app.util.TalkAction.AskQuery("/reschedule $newId", "⏰ Change Time")
                    )
                    val confirmMsg = ChatMessage(
                        id = "create_${java.util.UUID.randomUUID()}",
                        text = confirmationText,
                        isUser = false,
                        isTalkAction = true,
                        talkActionJson = com.focusbyrj.app.util.AyvaTalkEngine.serializeActionsJson("tasks", actions)
                    )
                    val updated = messages + confirmMsg
                    messages = updated
                    BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                }
            } catch (e: Throwable) {
                android.util.Log.e("BubbleChatActivity", "Error processing message", e)
                withContext(Dispatchers.Main) {
                    val errMsg = ChatMessage(
                        id = "err_${System.currentTimeMillis()}",
                        text = "⚡ Something unexpected occurred. Type `/talk` or `/summary` to get back on track.",
                        isUser = false
                    )
                    val updated = messages + errMsg
                    messages = updated
                    BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() })
                }
            }
        }
    }
}

    val initialPrefill = (context as? android.app.Activity)?.intent?.getStringExtra("prefill_query")
    LaunchedEffect(initialPrefill) {
        if (!initialPrefill.isNullOrBlank()) {
            sendMessage(overrideText = if (initialPrefill.startsWith("/")) initialPrefill else "/talk $initialPrefill")
            (context as? android.app.Activity)?.intent?.removeExtra("prefill_query")
        }
    }

    val isFullscreenMode = activeDrillSession != null

    val generateNextQuestion: () -> Unit = {
        activeDrillSession?.let { session ->
            if (!session.isBlitz && session.targetQuestions != -1 && session.total >= session.targetQuestions) {
                val summaryMsg = com.focusbyrj.app.util.DrillSummaryHelper.generateSummaryMessage(session)
                val questState = com.focusbyrj.app.util.DailyQuestManager.stateFlow.value
                val hasMysteryBox = questState.isEarlyBirdAvailable || questState.isNightOwlAvailable
                val boxMsg = if (hasMysteryBox) {
                    ChatMessage(
                        id = "mystery_box_${System.currentTimeMillis()}",
                        text = "Daily Mystery Box Unlocked",
                        isUser = false,
                        timestamp = System.currentTimeMillis(),
                        firstViewedTimestamp = System.currentTimeMillis(),
                        isMysteryBox = true
                    )
                } else null

                val newMsgs = if (boxMsg != null) {
                    messages.filter { !it.isArithmetic } + summaryMsg + boxMsg
                } else {
                    messages.filter { !it.isArithmetic } + summaryMsg
                }
                messages = newMsgs
                BubbleChatManager.saveMessages(context, messages.map { it.toPersistedChatMessage() })
                showDrillSummaryMessage = summaryMsg
                activeDrillSession = null
            } else {
                val diffEnum = when (session.difficulty) {
                    "medium" -> com.focusbyrj.app.util.ArithmeticDifficulty.MEDIUM
                    "hard" -> com.focusbyrj.app.util.ArithmeticDifficulty.HARD
                    else -> com.focusbyrj.app.util.ArithmeticDifficulty.EASY
                }
                val nextQ = try {
                    com.focusbyrj.app.util.ArithmeticEngine.generateQuestion(diffEnum)
                } catch (e: Exception) {
                    com.focusbyrj.app.util.ArithmeticQuestion(
                        title = "Mental Arithmetic",
                        questionText = "What is 7 × 8?",
                        options = listOf("54", "56", "58", "64"),
                        correctIndex = 1,
                        explanation = "7 × 8 = 56"
                    )
                }
                val json = org.json.JSONObject().apply {
                    put("title", if (session.isBlitz) "⚡ Speed Blitz" else nextQ.title)
                    put("questionText", nextQ.questionText)
                    val arr = org.json.JSONArray()
                    nextQ.options.forEach { arr.put(it) }
                    put("options", arr)
                    put("correctIndex", nextQ.correctIndex)
                    put("explanation", nextQ.explanation)
                }.toString()
                session.preGeneratedQuestions.add(json)
                activeDrillSession = session.copy()
            }
        }
    }

    // Handlers for active drill / blitz session interactions
    val handleDrillAnswer: (Boolean, QuestionRecord) -> Unit = { isCorrect, qRecord ->
        activeDrillSession?.let { session ->
            session.questionRecords.add(qRecord)
            session.total++
            if (session.difficulty == "vocab" && qRecord.vocabId != null && qRecord.vocabId > 0 && !qRecord.vocabType.isNullOrBlank()) {
                coroutineScope.launch(Dispatchers.IO) {
                    val vocabRepo = (context.applicationContext as com.focusbyrj.app.FocusApplication).vocabRepository
                    vocabRepo.recordQuizResult(qRecord.vocabType, qRecord.vocabId, isCorrect)
                }
            }
            if (isCorrect) {
                session.correct++
                session.combo++
                if (session.combo > session.maxCombo) {
                    session.maxCombo = session.combo
                }
                val comboMultiplier = when {
                    session.combo >= 8 -> 2.0
                    session.combo >= 5 -> 1.5
                    session.combo >= 3 -> 1.25
                    else -> 1.0
                }
                session.xp += (40 * comboMultiplier).toInt()
                session.gold += (20 * comboMultiplier).toInt()
                if (session.isBlitz) {
                    session.blitzSecondsRemaining = session.blitzSecondsRemaining + 30
                }
            } else {
                session.combo = 0
            }
            coroutineScope.launch {
                delay(if (session.isBlitz) 450 else 700) // Fast next question with feedback time
                // Check if session hasn't been ended during delay
                if (activeDrillSession != null) {
                    if (session.targetQuestions <= 0) {
                        generateNextQuestion()
                    }
                }
            }
        }
        Unit
    }

    val handleDrillEnd: () -> Unit = {
        activeDrillSession?.let { session ->
            val summaryMsg = com.focusbyrj.app.util.DrillSummaryHelper.generateSummaryMessage(session)
            val questState = com.focusbyrj.app.util.DailyQuestManager.stateFlow.value
            val hasMysteryBox = questState.isEarlyBirdAvailable || questState.isNightOwlAvailable
            val boxMsg = if (hasMysteryBox) {
                ChatMessage(
                    id = "mystery_box_${System.currentTimeMillis()}",
                    text = "Daily Mystery Box Unlocked",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    firstViewedTimestamp = System.currentTimeMillis(),
                    isMysteryBox = true
                )
            } else null

            val newMsgs = if (boxMsg != null) {
                messages.filter { !it.isArithmetic } + summaryMsg + boxMsg
            } else {
                messages.filter { !it.isArithmetic } + summaryMsg
            }
            messages = newMsgs
            BubbleChatManager.saveMessages(context, messages.map { it.toPersistedChatMessage() })
            showDrillSummaryMessage = summaryMsg
            activeDrillSession = null
        }
        Unit
    }

    val currentDrillSession = activeDrillSession
    val currentSummaryMessage = showDrillSummaryMessage
    val currentSolutions = showSolutionsJson

    if (currentDrillSession != null) {
        val currentJson = currentDrillSession.preGeneratedQuestions.firstOrNull()
        val latestQuestionMessage = ChatMessage(
            id = "drill_active_${System.currentTimeMillis()}",
            text = if (currentDrillSession.isBlitz) "⚡ Speed Blitz" else "Arithmetic Drill",
            isUser = false,
            isArithmetic = true,
            arithmeticJson = currentJson
        )
        FullscreenDrillView(
            activeSession = currentDrillSession,
            latestQuestionMessage = latestQuestionMessage,
            allQuestions = emptyList(),
            onNextQuestion = generateNextQuestion,
            onAnswerSubmitted = handleDrillAnswer,
            onEndSession = handleDrillEnd
        )
    } else if (currentSummaryMessage != null) {
        FullscreenDrillSummaryView(
            message = currentSummaryMessage,
            onClose = { showDrillSummaryMessage = null },
            onViewSolutions = { json ->
                showDrillSummaryMessage = null
                showSolutionsJson = json
            },
            onMessageUpdate = { updatedMsg ->
                val idx = messages.indexOfFirst { it.id == updatedMsg.id }
                if (idx != -1) {
                    val newList = messages.toMutableList()
                    newList[idx] = updatedMsg
                    messages = newList
                }
                if (showDrillSummaryMessage?.id == updatedMsg.id) {
                    showDrillSummaryMessage = updatedMsg
                }
            }
        )
    } else if (currentSolutions != null) {
        FullscreenSolutionsView(
            summaryJson = currentSolutions,
            onClose = { showSolutionsJson = null }
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(top = 110.dp)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) { 
                    detectTapGestures(onTap = { /* Prevent clicks from falling through */ })
                }
        ) {
            // Handle drag bar, title, and menu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp, 
                        end = 16.dp, 
                        top = 10.dp, 
                        bottom = 10.dp
                    )
            ) {
                // Close / dismiss button on left
                IconButton(
                    onClick = { (context as? android.app.Activity)?.finish() },
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterStart)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Center drag handle + title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ayva",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Notification Colors") },
                            leadingIcon = {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            },
                            onClick = {
                                showMenu = false
                                showNotificationColorsDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Text Size") },
                            leadingIcon = {
                                Icon(Icons.Filled.FormatSize, contentDescription = null, modifier = Modifier.size(20.dp))
                            },
                            onClick = {
                                showMenu = false
                                showFontSizeDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Clear History") },
                            onClick = {
                                val welcome = ChatMessage(
                                    id = "welcome_${System.currentTimeMillis()}",
                                    text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                                    isUser = false,
                                    timestamp = System.currentTimeMillis()
                                )
                                messages = listOf(welcome)
                                BubbleChatManager.saveMessages(context, listOf(
                                    PersistedChatMessage(welcome.id, welcome.text, welcome.isUser, welcome.timestamp)
                                ))
                                BubbleChatManager.clearUnread(context)
                                showMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Settings") },
                            onClick = {
                                showMenu = false
                                val i = Intent(context, com.focusbyrj.app.MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    putExtra("navigate_to", "bubble_settings")
                                }
                                context.startActivity(i)
                                (context as? android.app.Activity)?.finish()
                            }
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                val emptyChatText = remember(messages.isEmpty()) {
                    com.focusbyrj.app.util.AyvaDialogueEngine.getClearChatIntro(context)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = parseRichFormattedText(emptyChatText),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = chatFontSizeSp.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = (chatFontSizeSp * 1.45f).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                val reversedMessages = remember(messages) { messages.asReversed() }
                val catSpacerHeight by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isCatActionPlaying) 175.dp else 56.dp,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    ),
                    label = "cat_spacer_height"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        reverseLayout = true
                    ) {
                        val isScrolling = listState.isScrollInProgress
                        item(key = "cat_bottom_spacer") {
                            Spacer(modifier = Modifier.height(catSpacerHeight))
                        }
                        items(
                            count = reversedMessages.size,
                            key = { index -> reversedMessages[index].id },
                            contentType = { index ->
                                val m = reversedMessages[index]
                                when {
                                    m.isUser -> "user_msg"
                                    m.isMorningBrief || m.id.startsWith("morning_") -> "morning_brief"
                                    m.isEveningBrief || m.id.startsWith("evening_") -> "evening_brief"
                                    m.isVocabBrief -> "vocab_brief"
                                    m.isArithmetic -> "arithmetic_msg"
                                    m.isHabitsSummary -> "habits_msg"
                                    m.isTaskSummary -> "task_msg"
                                    else -> "text_msg"
                                }
                            }
                        ) { index ->
                            val msg = reversedMessages[index]
                            val isLatest = index == 0
                            val currentDrill = activeDrillSession
                            val isActiveDrill = isLatest && currentDrill != null && msg.isArithmetic
                            val currentCombo = if (isActiveDrill) currentDrill?.combo ?: 0 else 0
                            val drillProgress = if (isActiveDrill && currentDrill != null && currentDrill.targetQuestions > 0) {
                                Pair(currentDrill.total + 1, currentDrill.targetQuestions)
                            } else null
                            val isBlitzMode = currentDrill?.isBlitz ?: false
                            
                            ChatBubble(
                                message = msg, 
                                fontSizeSp = chatFontSizeSp,
                                isScrolling = isScrolling,
                                isActiveDrill = isActiveDrill,
                                isActiveDrillRunning = activeDrillSession != null,
                                currentCombo = currentCombo,
                                drillProgress = drillProgress,
                                isBlitzMode = isBlitzMode,
                                onQueryClick = { query ->
                                    if (query.startsWith("/")) {
                                        sendMessage(query)
                                    } else {
                                        sendMessage("/talk $query")
                                    }
                                },
                                onMessageUpdate = { updatedMsg ->
                                    val idx = messages.indexOfFirst { it.id == updatedMsg.id }
                                    if (idx != -1) {
                                        val newList = messages.toMutableList()
                                        newList[idx] = updatedMsg
                                        messages = newList
                                    }
                                },
                                onViewSolutions = { json ->
                                    showSolutionsJson = json
                                },
                                onDismiss = {
                                    val updatedList = messages.filter { it.id != msg.id }
                                    if (updatedList.isEmpty()) {
                                        val welcome = ChatMessage(
                                            id = "welcome_${System.currentTimeMillis()}",
                                            text = com.focusbyrj.app.util.AyvaDialogueEngine.getHelloWelcomeMessage(context),
                                            isUser = false,
                                            timestamp = System.currentTimeMillis()
                                        )
                                        messages = listOf(welcome)
                                        BubbleChatManager.saveMessages(context, listOf(welcome.toPersistedChatMessage()), updateActivityTimestamp = false)
                                    } else {
                                        messages = updatedList
                                        BubbleChatManager.saveMessages(context, updatedList.map { it.toPersistedChatMessage() }, updateActivityTimestamp = false)
                                    }
                                },
                                onStartStreakDrill = {
                                    if (activeDrillSession == null) {
                                        val aptProfile = com.focusbyrj.app.util.AptitudeManager.profileFlow.value
                                        val diffStr = when {
                                            aptProfile.titleTier >= 5 -> "hard"
                                            aptProfile.titleTier >= 3 -> "medium"
                                            else -> "easy"
                                        }
                                        val newSession = createDrillSessionWithQuestions(diffStr, 10)
                                        activeDrillSession = newSession
                                    }
                                },
                                onSkipDayWithFreeze = { promptMsg ->
                                    val success = com.focusbyrj.app.util.AptitudeManager.useStreakFreezeToSkipDay(1000)
                                    if (success) {
                                        messages = messages.filter { it.id != promptMsg.id }
                                        val angryMsg = ChatMessage(
                                            id = "angry_freeze_${System.currentTimeMillis()}",
                                            text = "😾 *Day Skipped with Streak Freeze!* (-1,000 🪙)\n\nAyva is grumpy that you skipped today's practice drill, but your streak is protected with a Freeze Shield! 🧊🔥",
                                            isUser = false,
                                            timestamp = System.currentTimeMillis(),
                                            isStreakFreezeSkipped = true
                                        )
                                        messages = messages + angryMsg
                                        currentCatActionAsset = "cat_angry.lottie"
                                        isCatActionPlaying = true
                                        android.widget.Toast.makeText(context, "🧊 Streak Freeze applied! 1,000 Gold spent.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        val currentGold = com.focusbyrj.app.util.FocusEconomyManager.profileFlow.value.gold
                                        android.widget.Toast.makeText(context, "⚠️ Need 1,000 Gold Coins to freeze streak! (You have $currentGold 🪙)", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                onDrillAnswer = handleDrillAnswer,
                                onDrillEnd = handleDrillEnd,
                                onOpenMysteryChest = { showMysteryChestDialog = true },
                            onRescheduleClick = {
                                val rep = "/reschedule "
                                inputTextFieldValue = TextFieldValue(
                                    text = rep,
                                    selection = TextRange(rep.length)
                                )
                            },
                            onTaskToggle = { taskId ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    val app = context.applicationContext as com.focusbyrj.app.FocusApplication
                                    val repo = app.taskRepository
                                    val task = repo.getTaskById(taskId)
                                    if (task != null) {
                                        val newCompleted = !task.isCompleted
                                        val updatedTask = task.copy(
                                            isCompleted = newCompleted,
                                            completedAt = if (newCompleted) System.currentTimeMillis() else null
                                        )
                                        repo.updateTask(updatedTask)
                                        if (newCompleted) {
                                            TaskReminderHelper.cancelReminderById(context, taskId)
                                        } else if (updatedTask.dueDate != null) {
                                            TaskReminderHelper.scheduleReminder(context, updatedTask)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        
                                        withContext(Dispatchers.Main) {
                                            messages = messages.map { m ->
                                                if (m.taskSummaryJson != null) {
                                                    try {
                                                        val arr = org.json.JSONArray(m.taskSummaryJson)
                                                        val newArr = org.json.JSONArray()
                                                        for (i in 0 until arr.length()) {
                                                            val item = arr.getJSONObject(i)
                                                            if (item.optLong("id") == taskId) {
                                                                item.put("isCompleted", newCompleted)
                                                            }
                                                            newArr.put(item)
                                                        }
                                                        m.copy(taskSummaryJson = newArr.toString())
                                                    } catch (e: Exception) { m }
                                                } else m
                                            }
                                            if (newCompleted) {
                                                val ackMsg = ChatMessage(
                                                    id = "done_${System.currentTimeMillis()}",
                                                    text = "Checked off: *${task.title}* 🎉",
                                                    isUser = false
                                                )
                                                messages = messages + ackMsg
                                            }
                                        }
                                    }
                                }
                            },
                            onFilterChange = { cmd ->
                                sendMessage(cmd)
                            },
                            onHabitLog = { habitId ->
                                // Habit progress already incremented and rewards awarded in HabitsChatCard
                                com.focusbyrj.app.util.GamificationHaptics.playLight(context)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                // Animated Peeking Cat / Expanded Action Cat Lottie View
                androidx.compose.animation.AnimatedVisibility(
                    visible = isCatVisible,
                    enter = fadeIn(tween(400)) + slideInVertically(initialOffsetY = { it }, animationSpec = tween(500)),
                    exit = fadeOut(tween(300)) + slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 0.dp)
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = isCatActionPlaying,
                        transitionSpec = {
                            (fadeIn(tween(350)) + scaleIn(initialScale = 0.7f, animationSpec = tween(350)))
                                .togetherWith(fadeOut(tween(250)) + scaleOut(targetScale = 0.7f, animationSpec = tween(250)))
                        },
                        label = "cat_view_transition"
                    ) { playingAction ->
                        if (playingAction) {
                            CatActionLottieView(
                                assetName = currentCatActionAsset,
                                onDismiss = {
                                    isCatActionPlaying = false
                                    catTapCount = 0
                                }
                            )
                        } else {
                            CatLottiePeekingView(
                                onTap = {
                                    val now = System.currentTimeMillis()
                                    if (now - lastCatTapTime > 1500L) {
                                        catTapCount = 1
                                    } else {
                                        catTapCount += 1
                                    }
                                    lastCatTapTime = now
                                    if (catTapCount >= 3) {
                                        catTapCount = 0
                                        catActionInvocationCount += 1
                                        // 1 out of 100 times (1% chance) show error cat, remaining times randomly pick between cat action and cat dance
                                        val isError = kotlin.random.Random.nextInt(100) == 0
                                        currentCatActionAsset = if (isError) {
                                            "cat_error.lottie"
                                        } else {
                                            val actionPool = listOf("cat_action.lottie", "cat_dance.lottie", "cat_dancing.lottie")
                                            actionPool.random()
                                        }
                                        isCatActionPlaying = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        // Input Area
        AnimatedVisibility(
            visible = activeDrillSession == null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
            // Horizontal scrollable quick action commands floating above icons
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(quickActionCommands) { action ->
                    androidx.compose.material3.Surface(
                        onClick = { onFillCommand(action.commandText) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = 11.5.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = suggestions.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.5f), RoundedCornerShape(12.dp)),
                    reverseLayout = true
                ) {
                    items(suggestions) { suggestion ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val rep = suggestion.replacementText
                                    inputTextFieldValue = TextFieldValue(
                                        text = rep,
                                        selection = TextRange(rep.length)
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(suggestion.displayText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha=0.2f))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Toggles Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Ayva Talk Quick Action
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.5f), CircleShape)
                            .clickable {
                                inputTextFieldValue = TextFieldValue(
                                    text = "/talk ",
                                    selection = TextRange(6)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = com.focusbyrj.app.ui.components.AyvaIcon,
                            contentDescription = "Talk to Ayva",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Math Drill Quick Action
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha=0.5f), CircleShape)
                            .clickable {
                                inputTextFieldValue = TextFieldValue(
                                    text = "/drill ",
                                    selection = TextRange(7)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bolt,
                            contentDescription = "Drill",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Priority Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isHighPriority) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isHighPriority) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha=0.5f), CircleShape)
                            .clickable { isHighPriority = !isHighPriority },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Whatshot,
                            contentDescription = "Priority",
                            tint = if (isHighPriority) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Persistent Toggle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isPersistent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (isPersistent) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha=0.5f), CircleShape)
                            .clickable { isPersistent = !isPersistent },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsActive,
                            contentDescription = "Persistent",
                            tint = if (isPersistent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                AnimatedVisibility(visible = parsedResult?.timestamp != null) {
                    Text(
                        text = "Setting due: ${parsedResult?.timestamp?.let { SmartDateParser.formatDueDate(it) }}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = inputTextFieldValue,
                    onValueChange = { inputTextFieldValue = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (inputText.startsWith("/")) "Enter command..." else "Add a new task...") },
                    shape = RoundedCornerShape(24.dp),
                    visualTransformation = CommandVisualTransformation(),
                    trailingIcon = {
                        Box(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                                .clickable { 
                                    val cur = inputTextFieldValue.text
                                    if (!cur.startsWith("/")) {
                                        val newText = "/$cur"
                                        inputTextFieldValue = TextFieldValue(
                                            text = newText,
                                            selection = TextRange(newText.length)
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("/", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() })
                )

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = inputText.isNotBlank()) { sendMessage() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            // Navigation Bar padding
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

        if (showNotificationColorsDialog) {
            com.focusbyrj.app.ui.components.AyvaNotificationColorsDialog(
                onDismiss = { showNotificationColorsDialog = false }
            )
        }

        if (showFontSizeDialog) {
            ChatTextSizeDialog(
                fontSizeSp = chatFontSizeSp,
                onFontSizeChange = { updateFontSize(it) },
                onDismiss = { showFontSizeDialog = false }
            )
        }

        if (showMysteryChestDialog) {
            val qState = com.focusbyrj.app.util.DailyQuestManager.stateFlow.value
            val baseRarity = if (qState.isNightOwlAvailable) {
                com.focusbyrj.app.ui.components.ChestRarity.RARE
            } else {
                com.focusbyrj.app.ui.components.ChestRarity.COMMON
            }
            com.focusbyrj.app.ui.components.DuolingoMysteryChestDialog(
                initialRarity = baseRarity,
                onDismiss = { showMysteryChestDialog = false },
                onClaimed = {
                    showMysteryChestDialog = false
                    com.focusbyrj.app.util.DailyQuestManager.refreshState()
                }
            )
        }
    }
}
}

@Composable
fun ChatTextSizeDialog(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chat Text Size", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Adjust text size for the assistant chat window only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Bubble
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🌅 Good Morning!\nHere is your daily update:\n🎉 All clear for today!",
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(fontSizeSp - 1f) },
                        enabled = fontSizeSp > 12f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease size")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when {
                                fontSizeSp <= 13f -> "Small"
                                fontSizeSp <= 15f -> "Default"
                                fontSizeSp <= 18f -> "Medium"
                                fontSizeSp <= 21f -> "Large"
                                else -> "Extra Large"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(fontSizeSp + 1f) },
                        enabled = fontSizeSp < 24f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase size")
                    }
                }

                // Classic professional slider
                ProfessionalSlider(
                    value = fontSizeSp,
                    onValueChange = { onFontSizeChange(kotlin.math.round(it)) },
                    valueRange = 12f..24f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Small" to 13f,
                        "Default" to 15f,
                        "Large" to 18f,
                        "Huge" to 21f
                    ).forEach { (label, size) ->
                        val isSelected = (fontSizeSp == size)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFontSizeChange(size) },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = { onFontSizeChange(15f) }) {
                Text("Reset")
            }
        }
    )
}

@Composable
fun ChatBubble(
    message: ChatMessage, 
    fontSizeSp: Float = 15f,
    isScrolling: Boolean = false,
    isActiveDrill: Boolean = false,
    isActiveDrillRunning: Boolean = false,
    currentCombo: Int = 0,
    drillProgress: Pair<Int, Int>? = null,
    isBlitzMode: Boolean = false,
    onDrillAnswer: ((Boolean, QuestionRecord) -> Unit)? = null,
    onDrillEnd: (() -> Unit)? = null,
    onStartStreakDrill: (() -> Unit)? = null,
    onSkipDayWithFreeze: ((ChatMessage) -> Unit)? = null,
    onRescheduleClick: (() -> Unit)? = null,
    onTaskToggle: ((Long) -> Unit)? = null,
    onFilterChange: ((String) -> Unit)? = null,
    onNavigateSummary: (() -> Unit)? = null,
    onQueryClick: ((String) -> Unit)? = null,
    onMessageUpdate: (ChatMessage) -> Unit = {},
    onViewSolutions: ((String) -> Unit)? = null,
    onOpenMysteryChest: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    onHabitLog: ((Long) -> Unit)? = null,
    onHabitCreated: ((com.focusbyrj.app.data.Habit) -> Unit)? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (message.isStreakPrompt) {
        StreakPromptCard(
            message = message,
            fontSizeSp = fontSizeSp,
            isActiveDrillRunning = isActiveDrillRunning,
            onStartDrill = { onStartStreakDrill?.invoke() },
            onSkipWithFreeze = { onSkipDayWithFreeze?.invoke(message) }
        )
        return
    }
    if (message.isMysteryBox) {
        val questState by com.focusbyrj.app.util.DailyQuestManager.stateFlow.collectAsState()
        val isChestAvailable = questState.isEarlyBirdAvailable || questState.isNightOwlAvailable
        com.focusbyrj.app.ui.components.MysteryBoxChatCard(
            isAvailable = isChestAvailable,
            onOpenBox = {
                if (isChestAvailable) {
                    onOpenMysteryChest?.invoke()
                }
            }
        )
        return
    }
    if (message.isDrillSummary) {
        DrillSummaryCard(
            message = message, 
            fontSizeSp = fontSizeSp,
            onMessageUpdate = onMessageUpdate,
            onViewSolutions = onViewSolutions,
            onDismiss = onDismiss
        )
        return
    }
    if (message.isAptitudeProfile) {
        com.focusbyrj.app.ui.screens.AptitudeProfileCard()
        return
    }
    if (message.isDailyQuests) {
        DailyQuestsCard()
        return
    }
    if (message.isVocabHub) {
        var vocabStats by remember { mutableStateOf<com.focusbyrj.app.data.VocabStats?>(null) }
        val context = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(message.id) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val repo = (context.applicationContext as? com.focusbyrj.app.FocusApplication)?.vocabRepository
                vocabStats = repo?.getStats()
            }
        }
        com.focusbyrj.app.ui.components.VocabRetentionHubChatCard(
            stats = vocabStats,
            onLaunchQuiz = { onQueryClick?.invoke("/vocab_quiz") },
            onLearnMore = { onQueryClick?.invoke("/vocab learn_more") }
        )
        return
    }

    val isMorning = message.isMorningBrief || message.id.startsWith("morning_")
    val isEvening = message.isEveningBrief || message.id.startsWith("evening_")
    val isWelcome = !message.isUser && (
        message.isWelcome || 
        message.id.startsWith("welcome_") ||
        message.text.contains("Hey! Ayva is on deck", ignoreCase = true) ||
        message.text.contains("ready for action", ignoreCase = true) ||
        message.text.contains("Ayva here!", ignoreCase = true) ||
        message.text.contains("I'm Ayva", ignoreCase = true) ||
        message.text.contains("Hey there! I'm Ayva", ignoreCase = true) ||
        message.text.contains("anti-procrastination", ignoreCase = true) ||
        message.text.contains("cognitive mastery", ignoreCase = true) ||
        message.text.contains("focus & learning companion", ignoreCase = true)
    )

    if (message.isHabitsSummary && !message.isUser) {
        HabitsChatCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onHabitLog = onHabitLog,
            onHabitCreated = onHabitCreated,
            onOpenFullTracker = {
                val intent = Intent(context, com.focusbyrj.app.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("navigate_to", "habits")
                    putExtra("NAV_DESTINATION", "habits")
                }
                context.startActivity(intent)
            }
        )
        return
    }

    if (message.isTaskSummary && !message.isUser && !isMorning && !isEvening) {
        TaskSummaryCard(
            message = message,
            fontSizeSp = fontSizeSp,
            onTaskToggle = onTaskToggle,
            onRescheduleClick = onRescheduleClick,
            onFilterChange = onFilterChange
        )
        return
    }

    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))

    val category = remember(message.text, message.isMorningBrief, message.isEveningBrief, message.isArithmetic, message.isStreakPrompt, message.id) {
        com.focusbyrj.app.util.AyvaAlertCategory.infer(
            text = message.text,
            isMorning = isMorning,
            isEvening = isEvening,
            isDrill = message.isArithmetic || message.isDrillSummary,
            isStreakPrompt = message.isStreakPrompt,
            messageId = message.id
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!message.isUser) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.focusbyrj.app.R.drawable.app_icon),
                    contentDescription = "Ayva",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, category.getComposeNotificationAccent(context).copy(alpha = 0.85f), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            val maxBubbleWidth = if (isWelcome) (330 + (fontSizeSp - 15f) * 10f).coerceIn(330f, 380f).dp
                else if (isMorning || isEvening) (310 + (fontSizeSp - 15f) * 10f).coerceIn(310f, 360f).dp 
                else (280 + (fontSizeSp - 15f) * 10f).coerceIn(280f, 350f).dp
            androidx.compose.material3.Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 24.dp,
                    topEnd = 24.dp,
                    bottomStart = if (message.isUser) 24.dp else 4.dp,
                    bottomEnd = if (message.isUser) 4.dp else 24.dp
                ),
                color = if (message.isUser) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.surface,
                contentColor = if (message.isUser) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                border = if (message.isUser) null else androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                shadowElevation = if (message.isUser) 0.dp else 4.dp
            ) {
                if (message.isArithmetic && message.arithmeticJson != null) {
                    ArithmeticCard(
                        message = message, 
                        fontSizeSp = fontSizeSp,
                        isActiveDrill = isActiveDrill,
                        currentCombo = currentCombo,
                        drillProgress = drillProgress,
                        isBlitzMode = isBlitzMode,
                        onAnswered = onDrillAnswer,
                        onEndDrill = onDrillEnd
                    )
                } else {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = (14 + (fontSizeSp - 15f) * 0.5f).coerceIn(12f, 20f).dp,
                            vertical = (10 + (fontSizeSp - 15f) * 0.5f).coerceIn(8f, 16f).dp
                        )
                    ) {
                        if (isMorning && !message.isUser) {
                            MorningBriefLottieHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(135.dp)
                                    .padding(bottom = 10.dp),
                                isScrolling = isScrolling
                            )
                        } else if (isEvening && !message.isUser) {
                            EveningBriefHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(135.dp)
                                    .padding(bottom = 10.dp),
                                messageId = message.id,
                                isScrolling = isScrolling
                            )
                        } else if (message.isStreakFreezeSkipped && !message.isUser) {
                            CatAngryLottieHeader(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .padding(bottom = 10.dp),
                                isScrolling = isScrolling
                            )
                        }

                        val parsedFormattedText = remember(message.text) {
                            parseRichFormattedText(message.text)
                        }
                        Text(
                            text = parsedFormattedText,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp * 1.45f).sp,
                                letterSpacing = 0.2.sp
                            )
                        )

                        if (isWelcome) {
                            Spacer(modifier = Modifier.height(14.dp))
                            androidx.compose.material3.Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(295.dp),
                                shape = RoundedCornerShape(18.dp),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    androidx.compose.material3.MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                )
                            ) {
                                com.focusbyrj.app.ui.components.CatMagicRiveView(
                                    modifier = Modifier.fillMaxSize(),
                                    messageId = message.id
                                )
                            }
                        }
                        
                        if (message.isVocabBrief && message.vocabJson != null) {
                            val isLearnMoreSession = !message.isMorningBrief && !message.isEveningBrief
                            com.focusbyrj.app.ui.components.VocabBriefContent(
                                vocabJson = message.vocabJson,
                                fontSizeSp = fontSizeSp,
                                isLearnMoreSession = isLearnMoreSession,
                                messageId = message.id,
                                onLearnMoreClick = {
                                    onQueryClick?.invoke("/vocab learn_more")
                                },
                                onQuizClick = {
                                    if (!isLearnMoreSession) {
                                        onQueryClick?.invoke("/vocab_quiz")
                                    }
                                }
                            )
                        }

                        if (message.isTalkAction && !message.talkActionJson.isNullOrBlank()) {
                            com.focusbyrj.app.ui.components.TalkActionChips(
                                talkActionJson = message.talkActionJson,
                                fontSizeSp = fontSizeSp,
                                onQueryClick = onQueryClick
                            )
                        }
                        
                        if (!message.pendingActionJson.isNullOrBlank()) {
                            PendingActionCard(
                                message = message,
                                fontSizeSp = fontSizeSp,
                                onMessageUpdate = onMessageUpdate
                            )
                        }
                    }
                }
            }
        }
        Text(
            text = timeString,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(
                top = 4.dp, 
                start = if (message.isUser) 0.dp else 40.dp, 
                end = if (message.isUser) 4.dp else 0.dp
            )
        )
    }
}

data class TaskItemData(
    val id: Long,
    val title: String,
    val isPriority: Boolean,
    val dueDate: Long,
    val isCompleted: Boolean,
    val isPersistent: Boolean = false,
    val filterMode: String = "today"
)

@Composable
fun TaskSummaryCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    onTaskToggle: ((Long) -> Unit)? = null,
    onRescheduleClick: (() -> Unit)? = null,
    onFilterChange: ((String) -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (300 + (fontSizeSp - 15f) * 12f).coerceIn(300f, 380f).dp

    var showFilterDropdown by remember { mutableStateOf(false) }

    val taskItems = remember(message.taskSummaryJson) {
        val list = mutableListOf<TaskItemData>()
        if (!message.taskSummaryJson.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(message.taskSummaryJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        TaskItemData(
                            id = obj.optLong("id"),
                            title = obj.optString("title", ""),
                            isPriority = obj.optBoolean("isPriority", false),
                            dueDate = obj.optLong("dueDate", 0L),
                            isCompleted = obj.optBoolean("isCompleted", false),
                            isPersistent = obj.optBoolean("isPersistent", false),
                            filterMode = obj.optString("filterMode", "today")
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        list
    }

    val isAllMode = taskItems.firstOrNull()?.filterMode == "all"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.focusbyrj.app.R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isDark) Color(0xFF131316) else Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (isDark) 0.1f else 0.25f)),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Bar with Filter Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (isAllMode) "All Tasks" else "Today's Tasks",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSizeSp * 0.95f).sp
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (taskItems.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    val leftCount = taskItems.count { !it.isCompleted }
                                    Text(
                                        text = "$leftCount left",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Dropdown Selector Button
                        Box {
                            Surface(
                                onClick = { showFilterDropdown = true },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isDark) Color(0xFF1C1C20) else MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isAllMode) "All" else "Today",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Select task view",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = showFilterDropdown,
                                onDismissRequest = { showFilterDropdown = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Today's Tasks") },
                                    onClick = {
                                        showFilterDropdown = false
                                        onFilterChange?.invoke("/tasks")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("All Tasks") },
                                    onClick = {
                                        showFilterDropdown = false
                                        onFilterChange?.invoke("/tasks all")
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.FormatListBulleted, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Clean Task Items List or Empty State
                    if (taskItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAllMode) "No tasks found" else "No tasks scheduled for today",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (fontSizeSp * 0.95f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            )
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            taskItems.forEach { task ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) Color(0xFF131B26) else Color.White,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (task.isPriority && !task.isCompleted) Color(0xFFEF4444).copy(alpha = 0.3f) else MaterialTheme.colorScheme.outline.copy(alpha = if(isDark) 0.1f else 0.18f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onTaskToggle?.invoke(task.id) }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Clickable Circle Checkbox
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent
                                                )
                                                .border(
                                                    width = 2.dp,
                                                    color = when {
                                                        task.isCompleted -> MaterialTheme.colorScheme.primary
                                                        task.isPriority -> Color(0xFFEF4444).copy(alpha = 0.7f)
                                                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                                    },
                                                    shape = CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (task.isCompleted) {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Completed",
                                                    tint = MaterialTheme.colorScheme.onPrimary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Task details
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = task.title,
                                                    style = MaterialTheme.typography.bodyMedium.copy(
                                                        fontSize = (fontSizeSp * 0.95f).sp,
                                                        fontWeight = if (task.isPriority && !task.isCompleted) FontWeight.Bold else FontWeight.SemiBold,
                                                        textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                                    ),
                                                    color = if (task.isCompleted) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    } else if (task.isPriority) {
                                                        Color(0xFFEF4444)
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurface
                                                    }
                                                )
                                            }

                                            if (task.dueDate > 0L) {
                                                val now = System.currentTimeMillis()
                                                val isOverdue = task.dueDate < now && !task.isCompleted
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.AccessTime,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(11.dp),
                                                        tint = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isOverdue) "Overdue: ${SmartDateParser.formatDueDate(task.dueDate)}" else SmartDateParser.formatDueDate(task.dueDate),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Medium
                                                        ),
                                                        color = if (isOverdue) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom action row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { onRescheduleClick?.invoke() },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if(isDark) 0.1f else 0.08f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if(isDark) 0.3f else 0.25f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccessTime,
                                    contentDescription = "Reschedule",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Reschedule",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (fontSizeSp * 0.8f).sp
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Surface(
                            onClick = { onFilterChange?.invoke(if (isAllMode) "/tasks" else "/tasks all") },
                            shape = RoundedCornerShape(10.dp),
                            color = if(isDark) Color(0xFF1C1C20) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if(isDark) 0.2f else 0.2f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isAllMode) Icons.Filled.Today else Icons.Filled.FormatListBulleted,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isAllMode) "Show Today" else "Show All",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = (fontSizeSp * 0.8f).sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

@Composable
fun StreakPromptCard(
    message: ChatMessage,
    fontSizeSp: Float = 15f,
    isActiveDrillRunning: Boolean = false,
    onStartDrill: () -> Unit,
    onSkipWithFreeze: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val economyProfile by com.focusbyrj.app.util.FocusEconomyManager.profileFlow.collectAsState()
    val canAffordFreeze = economyProfile.gold >= 1000
    val json = remember(message.streakPromptJson) {
        try {
            if (message.streakPromptJson != null) org.json.JSONObject(message.streakPromptJson) else null
        } catch (_: Exception) {
            null
        }
    }

    val streak = json?.optInt("streak", 0) ?: 0
    val bonusPercent = json?.optInt("bonusPercent", 0) ?: 0

    val df = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeString = df.format(Date(message.timestamp))
    val maxBubbleWidth = (290 + (fontSizeSp - 15f) * 10f).coerceIn(290f, 360f).dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = com.focusbyrj.app.R.drawable.app_icon),
                contentDescription = "Ayva",
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 4.dp,
                    bottomEnd = 20.dp
                ),
                color = if (isDark) Color(0xFF1E140A) else Color(0xFFFFF7ED),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFF97316).copy(alpha = 0.7f)),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Header Tag
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF97316).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (streak > 0) "🔥 $streak-DAY STREAK" else "⚡ DAILY STREAK",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEA580C)
                                )
                            }
                        }

                        if (bonusPercent > 0) {
                            Text(
                                text = "+$bonusPercent% XP Bonus",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF16A34A)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = parseRichFormattedText(message.text),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.4f).sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onStartDrill,
                        enabled = !isActiveDrillRunning,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEA580C),
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFFEA580C).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.7f)
                        )
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isActiveDrillRunning) Icons.Filled.CheckCircle else Icons.Filled.Bolt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isActiveDrillRunning) "Drill in Progress" else "Start 10-Question Drill",
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSizeSp * 0.95f).sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    DuolingoFreezeButton(
                        canAfford = canAffordFreeze,
                        enabled = !isActiveDrillRunning,
                        fontSizeSp = fontSizeSp,
                        onClick = onSkipWithFreeze
                    )
                }
            }
        }

        Text(
            text = timeString,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = (fontSizeSp * 0.72f).coerceIn(10f, 14f).sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 4.dp, start = 40.dp)
        )
    }
}

@Composable
fun DuolingoFreezeButton(
    canAfford: Boolean,
    enabled: Boolean,
    fontSizeSp: Float,
    onClick: () -> Unit
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDark = isSystemInDarkTheme()

    val topOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isPressed && enabled) 4.dp else 0.dp,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 60),
        label = "duo_btn_press"
    )

    val baseColor = when {
        !enabled -> if (isDark) Color(0xFF1E293B) else Color(0xFF94A3B8)
        canAfford -> Color(0xFF0284C7)
        else -> if (isDark) Color(0xFF1E293B) else Color(0xFF94A3B8)
    }

    val topFaceColor = when {
        !enabled -> if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
        canAfford -> Color(0xFF0EA5E9)
        else -> if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    }

    val textColor = when {
        !enabled -> Color.Gray
        canAfford -> Color.White
        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        // 3D Shadow Base
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(16.dp))
                .background(baseColor)
        )

        // Top Tactile Face (displaces downward on press)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = topOffset)
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(topFaceColor)
                .border(
                    width = 1.dp,
                    color = if (canAfford && enabled) Color(0xFF7DD3FC).copy(alpha = 0.6f) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "🧊",
                    fontSize = 17.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = if (canAfford) "FREEZE STREAK • 1,000 🪙" else "FREEZE STREAK (1,000 🪙)",
                    fontWeight = FontWeight.Black,
                    fontSize = (fontSizeSp * 0.85f).coerceIn(12f, 15f).sp,
                    letterSpacing = 0.5.sp,
                    color = textColor
                )
            }
        }
    }
}

data class AppInfo(val name: String, val packageName: String, val category: AppCategory = AppCategory.OTHERS)
data class Suggestion(val displayText: String, val replacementText: String)

fun parseRichFormattedText(rawText: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val n = rawText.length

    while (i < n) {
        if (i + 1 < n && rawText[i] == '*' && rawText[i + 1] == '*') {
            // Bold: **text**
            val closeIdx = rawText.indexOf("**", i + 2)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 2, closeIdx)
                builder.withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    append(content)
                }
                i = closeIdx + 2
                continue
            }
        } else if (i + 2 < n && rawText[i] == '*' && rawText[i + 1] == '_' && rawText[i + 2] == '_') {
            // Bold + Underline: *__text__*
            val closeIdx = rawText.indexOf("__*", i + 3)
            if (closeIdx != -1) {
                val content = rawText.substring(i + 3, closeIdx)
                builder.withStyle(
                    SpanStyle(
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textDecoration = TextDecoration.Underline
                    )
                ) {
                    append(content)
                }
                i = closeIdx + 3
                continue
            }
        } else if (i + 1 < n && rawText[i] == '_' && rawText[i + 1] == '_') {
            // Underline: __text__
            val closeIdx = rawText.indexOf("__", i + 2)
            if (closeIdx != -1) {
                val content = rawText.substring(i + 2, closeIdx)
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                    append(content)
                }
                i = closeIdx + 2
                continue
            }
        } else if (rawText[i] == '*') {
            // Bold: *text*
            val closeIdx = rawText.indexOf('*', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        } else if (rawText[i] == '_') {
            // Italic: _text_
            val closeIdx = rawText.indexOf('_', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        } else if (rawText[i] == '`') {
            // Code/Monospace: `text`
            val closeIdx = rawText.indexOf('`', i + 1)
            if (closeIdx != -1 && closeIdx > i + 1) {
                val content = rawText.substring(i + 1, closeIdx)
                builder.withStyle(
                    SpanStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                    )
                ) {
                    append(content)
                }
                i = closeIdx + 1
                continue
            }
        }

        builder.append(rawText[i])
        i++
    }

    return builder.toAnnotatedString()
}

class CommandVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val input = text.text
        val builder = AnnotatedString.Builder()
        if (input.startsWith("/")) {
            val parts = input.split(" ", limit = 3)
            val cmd = parts[0]
            builder.withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF3B82F6), fontWeight = FontWeight.Bold)) {
                append(cmd)
            }
            if (parts.size > 1) {
                builder.append(" ")
                builder.append(parts.drop(1).joinToString(" "))
            }
        } else {
            builder.append(input)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun PendingActionCard(message: ChatMessage, fontSizeSp: Float, onMessageUpdate: (ChatMessage) -> Unit) {
    val context = LocalContext.current
    val json = remember(message.pendingActionJson) { org.json.JSONObject(message.pendingActionJson) }
    val status = json.optString("status", "pending")
    val title = json.optString("title", "")
    val displayVal = json.optString("displayVal", "")
    
    Spacer(modifier = Modifier.height(8.dp))
    
    if (status == "pending") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            androidx.compose.material3.Button(
                onClick = {
                    val prefKey = json.optString("prefKey")
                    val prefType = json.optString("prefType")
                    val value = json.optString("value")
                    
                    val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE).edit()
                    val bubblePrefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE).edit()
                    
                    if (prefType == "int") {
                        prefs.putInt(prefKey, value.toIntOrNull() ?: 0)
                        bubblePrefs.putInt(prefKey, value.toIntOrNull() ?: 0)
                    } else if (prefType == "boolean") {
                        prefs.putBoolean(prefKey, value == "true")
                        bubblePrefs.putBoolean(prefKey, value == "true")
                    } else if (prefType == "string") {
                        prefs.putString(prefKey, value)
                        bubblePrefs.putString(prefKey, value)
                    }
                    prefs.apply()
                    bubblePrefs.apply()
                    
                    if (prefKey == "streak_notification_time" || prefKey == "streak_notification_enabled") {
                        com.focusbyrj.app.service.AptitudeReminderReceiver.scheduleDrillReminders(context)
                    } else if (prefKey == "morning_brief_time" || prefKey == "evening_brief_time") {
                        com.focusbyrj.app.service.DailySummaryReceiver.scheduleDailySummaries(context)
                    } else if (prefKey == "vacation_mode") {
                        com.focusbyrj.app.util.AptitudeManager.setVacationMode(context, value == "true")
                    }
                    
                    json.put("status", "executed")
                    val updatedMessage = message.copy(pendingActionJson = json.toString())
                    
                    onMessageUpdate(updatedMessage)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(32.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFF10B981)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Confirm", fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.SemiBold)
            }
            
            androidx.compose.material3.OutlinedButton(
                onClick = {
                    json.put("status", "cancelled")
                    val updatedMessage = message.copy(pendingActionJson = json.toString())
                    
                    onMessageUpdate(updatedMessage)
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).height(32.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text("Cancel", fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else if (status == "executed") {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xFF10B981).copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = androidx.compose.ui.graphics.Color(0xFF10B981), modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Changed successfully.", color = androidx.compose.ui.graphics.Color(0xFF10B981), fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.Medium)
        }
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp)).padding(8.dp)) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancelled", tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Action cancelled.", color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant, fontSize = (fontSizeSp * 0.78f).coerceIn(11f, 13f).sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun CatLottiePeekingView(
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(com.focusbyrj.app.R.raw.cat_animation)
    )
    val animProgress = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(composition) {
        val comp = composition ?: return@LaunchedEffect
        val dur = comp.duration.toLong().coerceIn(1500L, 4000L)
        while (true) {
            // 1. Cat rises up slowly and looks around with cute eyes
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = dur.toInt(),
                    easing = androidx.compose.animation.core.LinearEasing
                )
            )
            // 2. Pause at peak so cat stays visible and looks around
            kotlinx.coroutines.delay(1200L)
            // 3. Cat goes down slowly and smoothly back into hiding instead of abruptly disappearing
            animProgress.animateTo(
                targetValue = 0f,
                animationSpec = androidx.compose.animation.core.tween(
                    durationMillis = (dur * 0.75f).toInt().coerceAtLeast(1000),
                    easing = androidx.compose.animation.core.FastOutSlowInEasing
                )
            )
            // 4. Short rest before coming up again
            kotlinx.coroutines.delay(800L)
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val scaleAnim = remember { androidx.compose.animation.core.Animatable(1f) }

    Box(
        modifier = modifier
            .scale(scaleAnim.value)
            .width(96.dp)
            .height(55.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                coroutineScope.launch {
                    scaleAnim.animateTo(
                        targetValue = 1.15f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    )
                    scaleAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    )
                }
                onTap()
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        LottieAnimation(
            composition = composition,
            progress = { animProgress.value },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun CatActionLottieView(
    assetName: String = "cat_action.lottie",
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset(assetName)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    val startTime = remember { System.currentTimeMillis() }

    val targetSize = when (assetName) {
        "cat_error.lottie" -> 250.dp
        "cat_angry.lottie" -> 195.dp
        "cat_dance.lottie", "cat_dancing.lottie" -> 185.dp
        else -> 175.dp
    }
    val contentScale = if (assetName == "cat_error.lottie") 1.3f else 1.0f

    Box(
        modifier = modifier
            .size(targetSize)
            .scale(contentScale)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // Ignore taps within the first 2 seconds (2000ms) to prevent accidental muscle-memory cancels
                if (System.currentTimeMillis() - startTime >= 2000L) {
                    onDismiss()
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun MorningBriefLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_morning.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}

@Composable
fun EveningBriefHeader(
    modifier: Modifier = Modifier,
    messageId: String? = null,
    isScrolling: Boolean = false
) {
    EveningBriefLottieHeader(modifier = modifier, isScrolling = isScrolling)
}

@Composable
fun EveningBriefLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_evening.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}

@Composable
fun CatAngryLottieHeader(
    modifier: Modifier = Modifier,
    isScrolling: Boolean = false
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.Asset("cat_angry.lottie")
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        isPlaying = !isScrolling,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = false
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFEF4444).copy(alpha = 0.25f),
                        androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.fillMaxSize(),
            renderMode = RenderMode.HARDWARE
        )
    }
}
