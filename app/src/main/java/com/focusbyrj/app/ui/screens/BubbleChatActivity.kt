package com.focusbyrj.app.ui.screens

import com.focusbyrj.app.ui.screens.chat.*
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
    var highestSeenIndex: Int = 0,
    val isSettled: java.util.concurrent.atomic.AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean(false)
)

data class AppInfo(val name: String, val packageName: String, val category: AppCategory = AppCategory.OTHERS)

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
                                    com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)

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
                                    com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)

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
                                
                                val allTasks = repo.allTasks.first()
                                val completedToday = com.focusbyrj.app.util.CompletedTaskHistoryManager.getTodayCompletedTasks(context)
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
                                
                                val isMorningBriefQuery = (isSummaryCommand && subArg == "morning") || sentText.equals("morning brief", ignoreCase = true) || sentText.equals("morning briefing", ignoreCase = true)
                                val isEveningBriefQuery = (isSummaryCommand && subArg == "evening") || sentText.equals("evening brief", ignoreCase = true) || sentText.equals("evening briefing", ignoreCase = true) || sentText.equals("night brief", ignoreCase = true)
                                
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
                                            val updated = task.copy(dueDate = newDate, updatedAt = System.currentTimeMillis())
                                            repo.updateTask(updated)
                                            TaskReminderHelper.scheduleReminder(context, updated)
                                        }
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
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
                                        val updatedTask = task.copy(dueDate = newDate, updatedAt = System.currentTimeMillis())
                                        repo.updateTask(updatedTask)
                                        TaskReminderHelper.scheduleReminder(context, updatedTask)
                                        TodoWidgetProvider.updateAllWidgets(context)
                                        com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
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
                                                val updatedTask = task.copy(dueDate = parsed.timestamp, updatedAt = System.currentTimeMillis())
                                                repo.updateTask(updatedTask)
                                                TaskReminderHelper.scheduleReminder(context, updatedTask)
                                                TodoWidgetProvider.updateAllWidgets(context)
                                                com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
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
                                        val updatedTask = it.copy(dueDate = System.currentTimeMillis() + 86400000L, updatedAt = System.currentTimeMillis())
                                        repo.updateTask(updatedTask)
                                        TaskReminderHelper.scheduleReminder(context, updatedTask)
                                    }
                                    TodoWidgetProvider.updateAllWidgets(context)
                                    com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
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
                com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
                
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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AyvaChatTimeline(
                    messages = messages,
                    listState = listState,
                    chatFontSizeSp = chatFontSizeSp,
                    activeDrillSession = activeDrillSession,
                    isCatActionPlaying = isCatActionPlaying,
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
                            BubbleChatManager.updateMessage(context, updatedMsg.toPersistedChatMessage())
                        }
                    },
                    onViewSolutions = { json ->
                        showSolutionsJson = json
                    },
                    onDismissMessage = { msgToDismiss ->
                        val updatedList = messages.filter { it.id != msgToDismiss.id }
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
                            val filtered = messages.filter { it.id != promptMsg.id }
                            val angryMsg = ChatMessage(
                                id = "angry_freeze_${System.currentTimeMillis()}",
                                text = "😾 *Day Skipped with Streak Freeze!* (-1,000 🪙)\n\nAyva is grumpy that you skipped today's practice drill, but your streak is protected with a Freeze Shield! 🧊🔥",
                                isUser = false,
                                timestamp = System.currentTimeMillis(),
                                isStreakFreezeSkipped = true
                            )
                            val updated = filtered + angryMsg
                            messages = updated
                            BubbleChatManager.saveMessages(context, updated.map { it.toPersistedChatMessage() }, updateActivityTimestamp = true)
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
                                val completedAt = System.currentTimeMillis()
                                com.focusbyrj.app.util.CompletedTaskHistoryManager.recordCompletedTask(context, task, completedAt)
                                val updated = task.copy(
                                    isCompleted = true,
                                    completedAt = completedAt,
                                    updatedAt = completedAt
                                )
                                repo.updateTask(updated)
                                TaskReminderHelper.cancelReminderById(context, taskId)
                                FocusEconomyManager.completeTaskReward(task.title, task.isPriority, task.type)
                                if (task.recurrence != com.focusbyrj.app.data.RecurrencePattern.NONE) {
                                    val nextTask = TaskReminderHelper.generateNextRecurringTask(task.copy(isCompleted = true, completedAt = completedAt))
                                    val newId = app.database.taskDao().insertTask(nextTask)
                                    TaskReminderHelper.scheduleReminder(context, nextTask.copy(id = newId))
                                }
                                TodoWidgetProvider.updateAllWidgets(context)
                                com.focusbyrj.app.util.sync.supabase.AutoSyncManager.triggerDebouncedSync(context)
                                
                                withContext(Dispatchers.Main) {
                                    val newMessages = messages.map { m ->
                                        if (m.taskSummaryJson != null) {
                                            try {
                                                val arr = org.json.JSONArray(m.taskSummaryJson)
                                                val newArr = org.json.JSONArray()
                                                for (i in 0 until arr.length()) {
                                                    val item = arr.getJSONObject(i)
                                                    if (item.optLong("id") == taskId) {
                                                        item.put("isCompleted", true)
                                                    }
                                                    newArr.put(item)
                                                }
                                                m.copy(taskSummaryJson = newArr.toString())
                                            } catch (e: Exception) { m }
                                        } else m
                                    }
                                    val ackMsg = ChatMessage(
                                        id = "done_${System.currentTimeMillis()}",
                                        text = "Checked off: *${task.title}* 🎉",
                                        isUser = false
                                    )
                                    val updatedList = newMessages + ackMsg
                                    messages = updatedList
                                    BubbleChatManager.saveMessages(context, updatedList.map { it.toPersistedChatMessage() }, updateActivityTimestamp = true)
                                }
                            }
                        }
                    },
                    onFilterChange = { cmd -> sendMessage(cmd) },
                    onHabitLog = {
                        com.focusbyrj.app.util.GamificationHaptics.playLight(context)
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Cat floating overlay
                AyvaCatFloatingView(
                    isVisible = isCatVisible,
                    isActionPlaying = isCatActionPlaying,
                    currentActionAsset = currentCatActionAsset,
                    onDismissAction = {
                        isCatActionPlaying = false
                        catTapCount = 0
                    },
                    onCatTap = {
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
                            val isError = kotlin.random.Random.nextInt(100) == 0
                            currentCatActionAsset = if (isError) {
                                "cat_error.lottie"
                            } else {
                                val actionPool = listOf("cat_action.lottie", "cat_dance.lottie", "cat_dancing.lottie")
                                actionPool.random()
                            }
                            isCatActionPlaying = true
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 0.dp)
                )
            }

            // Bottom Composer Bar
            AnimatedVisibility(
                visible = activeDrillSession == null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                AyvaComposerBar(
                    inputTextFieldValue = inputTextFieldValue,
                    onInputChange = { inputTextFieldValue = it },
                    quickActionCommands = quickActionCommands,
                    onFillCommand = onFillCommand,
                    suggestions = suggestions,
                    onSuggestionClick = { suggestion ->
                        val rep = suggestion.replacementText
                        inputTextFieldValue = TextFieldValue(
                            text = rep,
                            selection = TextRange(rep.length)
                        )
                    },
                    isHighPriority = isHighPriority,
                    onToggleHighPriority = { isHighPriority = !isHighPriority },
                    isPersistent = isPersistent,
                    onTogglePersistent = { isPersistent = !isPersistent },
                    parsedDueDateText = parsedResult?.timestamp?.let { SmartDateParser.formatDueDate(it) },
                    onSend = { sendMessage() }
                )
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
