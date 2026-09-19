package com.focusbyrj.app.ui.screens.drill

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
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
fun SolutionCardItem(
    question: DrillQuestionResult,
    isReattemptMode: Boolean,
    reattemptSelectedOption: Int?,
    onSelectReattemptOption: (Int) -> Unit,
    cardBackground: Color,
    textPrimary: Color,
    textSecondary: Color,
    correctGreen: Color,
    wrongRed: Color,
    unattemptedGrey: Color,
    timerRed: Color,
    activeBlue: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val statusColor = when (question.status) {
            "correct" -> correctGreen
            "wrong" -> wrongRed
            else -> unattemptedGrey
        }

        // Question Header Row: Number Badge + Time Taken + Marks
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Number Badge
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(statusColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${question.qNum}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Timer + Time formatted
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = null,
                tint = if (question.status == "wrong" || question.timeTakenSec > 35) timerRed else textSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = formatSecondsToMinutesSec(question.timeTakenSec),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = if (question.status == "wrong" || question.timeTakenSec > 35) timerRed else textSecondary
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Marks: +1.0  -0.25
            Text(
                text = "+${question.positiveMarks}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = correctGreen
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "-${question.negativeMarks}",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal),
                color = textSecondary
            )

            Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Direction (if present)
        if (question.direction.isNotEmpty()) {
            Text(
                text = "Direction: ${question.direction}",
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
            text = question.questionText,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 24.sp,
                fontSize = 16.sp
            ),
            color = textPrimary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Options List (1. 2. 3. 4. 5.)
        question.options.forEachIndexed { optIndex, optText ->
            val isCorrectOption = optIndex == question.correctIndex
            val isUserChosen = optIndex == question.userSelectedIndex

            // Highlighting Logic
            val (optBorderColor, optBgColor, optNumberColor) = if (isReattemptMode) {
                val isSelectedInReattempt = reattemptSelectedOption == optIndex
                if (isSelectedInReattempt) {
                    Triple(activeBlue, activeBlue.copy(alpha = 0.12f), activeBlue)
                } else {
                    Triple(Color.Transparent, cardBackground, textSecondary)
                }
            } else {
                when {
                    isCorrectOption -> Triple(
                        correctGreen,
                        correctGreen.copy(alpha = 0.12f),
                        correctGreen
                    )
                    isUserChosen && !isCorrectOption -> Triple(
                        wrongRed,
                        wrongRed.copy(alpha = 0.12f),
                        wrongRed
                    )
                    else -> Triple(Color.Transparent, cardBackground, textSecondary)
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clickable(enabled = isReattemptMode) {
                        onSelectReattemptOption(optIndex)
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
                    // Option index prefix e.g. "1. ", "2. "
                    Text(
                        text = "${optIndex + 1}.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = optNumberColor
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // Option Text
                    Text(
                        text = optText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.5.sp,
                            fontWeight = if (!isReattemptMode && isCorrectOption) FontWeight.Bold else FontWeight.Normal
                        ),
                        color = textPrimary,
                        modifier = Modifier.weight(1f)
                    )

                    // Right Status Icon (Checkmark / Wrong cross / Reattempt radio)
                    if (isReattemptMode) {
                        RadioButton(
                            selected = reattemptSelectedOption == optIndex,
                            onClick = { onSelectReattemptOption(optIndex) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = activeBlue,
                                unselectedColor = textSecondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        when {
                            isCorrectOption -> {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Correct Answer",
                                    tint = correctGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            isUserChosen && !isCorrectOption -> {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Your Answer (Incorrect)",
                                    tint = wrongRed,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
