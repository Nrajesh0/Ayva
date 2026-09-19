package com.focusbyrj.app.ui.screens.drill

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillQuestionPaletteModal(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    totalQuestions: Int,
    highestSeenIndex: Int,
    attemptedIndices: Set<Int>,
    onQuestionSelected: (Int) -> Unit,
    onSubmitClick: () -> Unit,
    isDark: Boolean
) {
    if (!showDialog) return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) Color(0xFF1B1E23) else Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Question Overview Palette",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) Color.White else Color.Black,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Legend
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PaletteLegendItem("Attempted", Color(0xFF3B82F6), true, isDark)
                PaletteLegendItem("Unattempted", Color(0xFF64748B), true, isDark)
                PaletteLegendItem("Unseen", Color(0xFF64748B), false, isDark)
            }

            Text(
                text = "Numerical & Speed Ability",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isDark) Color(0xFFA0AEC0) else Color(0xFF64748B),
                modifier = Modifier.padding(bottom = 14.dp)
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(48.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(totalQuestions) { index ->
                    val isAttempted = attemptedIndices.contains(index)
                    val isUnseen = index > highestSeenIndex
                    val isUnattempted = !isAttempted && !isUnseen

                    val bgColor = when {
                        isAttempted -> Color(0xFF3B82F6)
                        isUnattempted -> if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                        else -> Color.Transparent
                    }
                    val strokeColor = when {
                        isAttempted -> Color.Transparent
                        isUnattempted -> Color.Transparent
                        else -> if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)
                    }
                    val textColor = when {
                        isAttempted -> Color.White
                        isUnattempted -> if (isDark) Color.White else Color.Black
                        else -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(bgColor)
                            .border(1.dp, strokeColor, CircleShape)
                            .clickable {
                                onQuestionSelected(index)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (index + 1).toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    onDismiss()
                    onSubmitClick()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "SUBMIT DRILL",
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
            }
        }
    }
}

@Composable
fun PaletteLegendItem(label: String, color: Color, filled: Boolean, isDark: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (filled) color else Color.Transparent)
                .border(1.dp, if (!filled) color else Color.Transparent, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
        )
    }
}
