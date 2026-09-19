package com.focusbyrj.app.ui.screens.drill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.drill.DrillQuestionResult

@Composable
fun SolutionsTopBar(
    onBack: () -> Unit,
    onOpenOverview: () -> Unit,
    textPrimary: Color,
    darkSurface: Color,
    activeBlue: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(darkSurface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = textPrimary
            )
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .clickable { onOpenOverview() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "All Sections",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = textPrimary
            )
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = activeBlue,
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onOpenOverview) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = textPrimary
            )
        }
    }
}

@Composable
fun SolutionsQuestionNumberStrip(
    questionsList: List<DrillQuestionResult>,
    selectedIndex: Int,
    stripListState: LazyListState,
    onSelectIndex: (Int) -> Unit,
    onOpenFilters: () -> Unit,
    darkSurface: Color,
    correctGreen: Color,
    wrongRed: Color,
    unattemptedGrey: Color,
    textPrimary: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(darkSurface)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            state = stripListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(questionsList) { index, item ->
                val isSelected = index == selectedIndex
                val statusColor = when (item.status) {
                    "correct" -> correctGreen
                    "wrong" -> wrongRed
                    else -> unattemptedGrey
                }

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, Color.White, CircleShape)
                            } else Modifier
                        )
                        .clickable { onSelectIndex(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${item.qNum}",
                        color = Color.White,
                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Pinned "Filters" Button on the right
        Surface(
            onClick = onOpenFilters,
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF262C34),
            modifier = Modifier
                .padding(end = 12.dp)
                .height(34.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = "Filters",
                    tint = textPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Filters",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun FilterTabPill(
    text: String,
    isSelected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) activeColor else Color(0xFF262C34),
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) Color.White else Color(0xFFADB5BD),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

fun formatSecondsToMinutesSec(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}min ${secs}sec" else "${secs}sec"
}

fun formatSecondsToMmSs(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}"
}
