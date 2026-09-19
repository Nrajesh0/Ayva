package com.focusbyrj.app.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.drill.DrillQuestionResult
import com.focusbyrj.app.data.drill.DrillSessionRepository
import com.focusbyrj.app.data.drill.DrillSummary
import com.focusbyrj.app.ui.screens.drill.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

enum class FilterTab {
    ALL, INCORRECT, CORRECT, OVERTIME, UNATTEMPTED
}

enum class SolutionSortOrder {
    DEFAULT, ALPHABETICAL, TIME_TAKEN, ACCURACY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenSolutionsView(
    summaryJson: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        context.sendBroadcast(android.content.Intent("com.focusbyrj.app.HIDE_BUBBLE"))
        onDispose {
            context.sendBroadcast(android.content.Intent("com.focusbyrj.app.SHOW_BUBBLE"))
        }
    }

    // Palette strictly matching the reference Testbook / Exam dark interface
    val darkBackground = Color(0xFF131518)
    val darkSurface = Color(0xFF1B1E23)
    val cardBackground = Color(0xFF22262C)
    val cardStrokeColor = Color(0xFF2E333B)
    val activeBlue = Color(0xFF2196F3)
    val correctGreen = Color(0xFF00C853)
    val wrongRed = Color(0xFFEF5350)
    val unattemptedGrey = Color(0xFF5A626E)
    val textPrimary = Color(0xFFF1F3F5)
    val textSecondary = Color(0xFF9EA7B4)
    val timerRed = Color(0xFFFF5252)

    var examTitle by remember { mutableStateOf("Arithmetic & Speed Drill") }
    val questionsList = remember(summaryJson) { mutableStateListOf<DrillQuestionResult>() }
    val bookmarkedQuestions = remember { mutableStateMapOf<Int, Boolean>() }

    // Screen State
    var showFilterOverview by remember { mutableStateOf(false) }
    var selectedFilterTab by remember { mutableStateOf(FilterTab.ALL) }
    var sortOrder by remember { mutableStateOf(SolutionSortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var selectedQuestionIndex by remember { mutableStateOf(0) }
    var isReattemptMode by remember { mutableStateOf(false) }
    val reattemptUserAnswers = remember { mutableStateMapOf<Int, Int>() } // qNum -> selectedOptIndex

    // Load data: Try repository first (low memory, no JSON string parsing), then fallback to JSON
    LaunchedEffect(summaryJson) {
        questionsList.clear()
        withContext(Dispatchers.IO) {
            var loadedSummary: DrillSummary? = null
            // Check if summaryJson is a sessionId
            if (summaryJson.isNotBlank() && !summaryJson.startsWith("{")) {
                loadedSummary = DrillSessionRepository.getSummary(summaryJson)
            }
            if (loadedSummary == null && summaryJson.isNotBlank()) {
                loadedSummary = DrillSummary.fromJson(summaryJson)
            }

            withContext(Dispatchers.Main) {
                if (loadedSummary != null && loadedSummary.questions.isNotEmpty()) {
                    examTitle = if (loadedSummary.isBlitz) "⚡ Speed Blitz Review" else "${loadedSummary.title} Solutions"
                    questionsList.addAll(loadedSummary.questions)
                } else {
                    // Fallback to legacy parsing if needed
                    try {
                        val obj = JSONObject(summaryJson)
                        val isBlitz = obj.optBoolean("isBlitz", false)
                        val categoryTitle = obj.optString("title", "Arithmetic & Speed Drill")
                        examTitle = if (isBlitz) "⚡ Speed Blitz Review" else "$categoryTitle Solutions"
                        val qArr = obj.optJSONArray("questions")
                        if (qArr != null && qArr.length() > 0) {
                            for (i in 0 until qArr.length()) {
                                questionsList.add(DrillQuestionResult.fromJson(qArr.getJSONObject(i), 20, i))
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    val totalCount = questionsList.size
    val correctCount = questionsList.count { it.status == "correct" }
    val incorrectCount = questionsList.count { it.status == "wrong" }
    val unattemptedCount = questionsList.count { it.status == "unattempted" }
    val overtimeCount = questionsList.count { it.timeTakenSec > 35 }

    val currentQuestion = questionsList.getOrNull(selectedQuestionIndex)
    val stripListState = rememberLazyListState()
    val scrollState = rememberScrollState()

    LaunchedEffect(selectedQuestionIndex) {
        if (questionsList.isNotEmpty() && selectedQuestionIndex in questionsList.indices) {
            stripListState.animateScrollToItem(selectedQuestionIndex)
            scrollState.scrollTo(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (showFilterOverview) {
            // =========================================================================
            // SCREEN 2: ALL QUESTIONS / FILTER OVERVIEW LIST
            // =========================================================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(darkBackground)
            ) {
                // Top App Bar for Question Overview
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(darkSurface)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showFilterOverview = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textPrimary
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = "Question Overview",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            ),
                            color = textPrimary
                        )
                        Text(
                            text = "$totalCount Questions Total",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = textSecondary
                        )
                    }
                }

                HorizontalDivider(color = cardStrokeColor, thickness = 1.dp)

                // Top Filter Tabs (Horizontal Pills)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterTabPill(
                        text = "All ($totalCount)",
                        isSelected = selectedFilterTab == FilterTab.ALL,
                        activeColor = activeBlue,
                        onClick = { selectedFilterTab = FilterTab.ALL }
                    )
                    FilterTabPill(
                        text = "Incorrect ($incorrectCount)",
                        isSelected = selectedFilterTab == FilterTab.INCORRECT,
                        activeColor = activeBlue,
                        onClick = { selectedFilterTab = FilterTab.INCORRECT }
                    )
                    FilterTabPill(
                        text = "Overtime ($overtimeCount)",
                        isSelected = selectedFilterTab == FilterTab.OVERTIME,
                        activeColor = activeBlue,
                        onClick = { selectedFilterTab = FilterTab.OVERTIME }
                    )
                    FilterTabPill(
                        text = "Unattempted ($unattemptedCount)",
                        isSelected = selectedFilterTab == FilterTab.UNATTEMPTED,
                        activeColor = activeBlue,
                        onClick = { selectedFilterTab = FilterTab.UNATTEMPTED }
                    )
                    FilterTabPill(
                        text = "Correct ($correctCount)",
                        isSelected = selectedFilterTab == FilterTab.CORRECT,
                        activeColor = activeBlue,
                        onClick = { selectedFilterTab = FilterTab.CORRECT }
                    )
                }

                // Section Header Row: Title & Sort Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUANTITATIVE & REASONING",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 13.sp
                        ),
                        color = textPrimary
                    )

                    // Sort Icon & Dropdown Menu
                    Box {
                        IconButton(
                            onClick = { showSortMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort Questions",
                                tint = if (sortOrder != SolutionSortOrder.DEFAULT) activeBlue else textSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false },
                            modifier = Modifier.background(cardBackground)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Default (Question #)", color = textPrimary) },
                                onClick = {
                                    sortOrder = SolutionSortOrder.DEFAULT
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortOrder == SolutionSortOrder.DEFAULT) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = activeBlue)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Alphabetical (A-Z)", color = textPrimary) },
                                onClick = {
                                    sortOrder = SolutionSortOrder.ALPHABETICAL
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortOrder == SolutionSortOrder.ALPHABETICAL) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = activeBlue)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Time Taken (Slowest First)", color = textPrimary) },
                                onClick = {
                                    sortOrder = SolutionSortOrder.TIME_TAKEN
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortOrder == SolutionSortOrder.TIME_TAKEN) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = activeBlue)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Accuracy (Hardest First)", color = textPrimary) },
                                onClick = {
                                    sortOrder = SolutionSortOrder.ACCURACY
                                    showSortMenu = false
                                },
                                leadingIcon = {
                                    if (sortOrder == SolutionSortOrder.ACCURACY) {
                                        Icon(Icons.Filled.Check, contentDescription = null, tint = activeBlue)
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Filtered and Sorted List of Question Cards
                val filteredQuestions = remember(selectedFilterTab, sortOrder, questionsList) {
                    val list = when (selectedFilterTab) {
                        FilterTab.ALL -> questionsList.mapIndexed { idx, item -> Pair(idx, item) }
                        FilterTab.INCORRECT -> questionsList.mapIndexed { idx, item -> Pair(idx, item) }.filter { it.second.status == "wrong" }
                        FilterTab.CORRECT -> questionsList.mapIndexed { idx, item -> Pair(idx, item) }.filter { it.second.status == "correct" }
                        FilterTab.OVERTIME -> questionsList.mapIndexed { idx, item -> Pair(idx, item) }.filter { it.second.timeTakenSec > 35 }
                        FilterTab.UNATTEMPTED -> questionsList.mapIndexed { idx, item -> Pair(idx, item) }.filter { it.second.status == "unattempted" }
                    }
                    when (sortOrder) {
                        SolutionSortOrder.DEFAULT -> list
                        SolutionSortOrder.ALPHABETICAL -> list.sortedBy { it.second.questionText }
                        SolutionSortOrder.TIME_TAKEN -> list.sortedByDescending { it.second.timeTakenSec }
                        SolutionSortOrder.ACCURACY -> list.sortedBy { it.second.accuracyPct }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    itemsIndexed(filteredQuestions) { _, (originalIndex, qItem) ->
                        val isBookmarked = bookmarkedQuestions[qItem.qNum] == true
                        val statusColor = when (qItem.status) {
                            "correct" -> correctGreen
                            "wrong" -> wrongRed
                            else -> unattemptedGrey
                        }
                        val timeStr = formatSecondsToMmSs(qItem.timeTakenSec)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedQuestionIndex = originalIndex
                                    showFilterOverview = false
                                },
                            shape = RoundedCornerShape(16.dp),
                            color = cardBackground,
                            border = BorderStroke(1.dp, cardStrokeColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(statusColor),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${qItem.qNum}",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = if (qItem.status == "wrong" || qItem.timeTakenSec > 35) timerRed else textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = timeStr,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                        color = if (qItem.status == "wrong" || qItem.timeTakenSec > 35) timerRed else textSecondary
                                    )

                                    Spacer(modifier = Modifier.weight(1f))

                                    IconButton(
                                        onClick = {
                                            if (isBookmarked) bookmarkedQuestions.remove(qItem.qNum)
                                            else bookmarkedQuestions[qItem.qNum] = true
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                            contentDescription = "Bookmark",
                                            tint = if (isBookmarked) activeBlue else textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = qItem.questionText.ifEmpty { "Problem #${qItem.qNum}" },
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Normal,
                                        lineHeight = 20.sp
                                    ),
                                    color = textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Floating Pill: "Back to Question View"
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            ) {
                Surface(
                    onClick = { showFilterOverview = false },
                    shape = RoundedCornerShape(28.dp),
                    color = activeBlue,
                    shadowElevation = 8.dp,
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Back to Questions",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        } else {
            // =========================================================================
            // SCREEN 1: DETAILED SOLUTION QUESTION VIEW
            // =========================================================================
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Modular Top App Bar
                SolutionsTopBar(
                    onBack = onClose,
                    onOpenOverview = { showFilterOverview = true },
                    textPrimary = textPrimary,
                    darkSurface = darkSurface,
                    activeBlue = activeBlue
                )

                // Modular Question Number Strip
                SolutionsQuestionNumberStrip(
                    questionsList = questionsList,
                    selectedIndex = selectedQuestionIndex,
                    stripListState = stripListState,
                    onSelectIndex = { selectedQuestionIndex = it },
                    onOpenFilters = { showFilterOverview = true },
                    darkSurface = darkSurface,
                    correctGreen = correctGreen,
                    wrongRed = wrongRed,
                    unattemptedGrey = unattemptedGrey,
                    textPrimary = textPrimary
                )

                HorizontalDivider(color = cardStrokeColor, thickness = 1.dp)

                // Main Scrollable Question Body
                if (currentQuestion != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        // Modular Solution Card Item (Question prompt and options)
                        SolutionCardItem(
                            question = currentQuestion,
                            isReattemptMode = isReattemptMode,
                            reattemptSelectedOption = reattemptUserAnswers[currentQuestion.qNum],
                            onSelectReattemptOption = { optIdx ->
                                reattemptUserAnswers[currentQuestion.qNum] = optIdx
                            },
                            cardBackground = cardBackground,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            correctGreen = correctGreen,
                            wrongRed = wrongRed,
                            unattemptedGrey = unattemptedGrey,
                            timerRed = timerRed,
                            activeBlue = activeBlue
                        )

                        // Modular Explanation Section
                        if (!isReattemptMode) {
                            ExplanationSection(
                                explanation = currentQuestion.explanation,
                                cardBackground = cardBackground,
                                cardStrokeColor = cardStrokeColor,
                                textPrimary = textPrimary,
                                activeBlue = activeBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }

                // Bottom Bar: Reattempt Mode Toggle + Previous/Next Navigation Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = darkSurface,
                    border = BorderStroke(1.dp, cardStrokeColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Reattempt Mode Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Reattempt Mode",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp
                                ),
                                color = textPrimary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = isReattemptMode,
                                onCheckedChange = { isReattemptMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = activeBlue,
                                    uncheckedThumbColor = textSecondary,
                                    uncheckedTrackColor = cardBackground
                                )
                            )
                        }

                        // Right Navigation Controls (Prev & Next buttons)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (selectedQuestionIndex > 0) {
                                IconButton(
                                    onClick = { selectedQuestionIndex-- },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(cardBackground, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Previous",
                                        tint = textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (selectedQuestionIndex < questionsList.size - 1) {
                                        selectedQuestionIndex++
                                    } else {
                                        showFilterOverview = true
                                    }
                                },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(activeBlue, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Next",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
