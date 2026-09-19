package com.focusbyrj.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Authentic Duolingo Brand Design System Tokens
 */
object DuolingoPalette {
    val Green = Color(0xFF58CC02)
    val GreenBevel = Color(0xFF46A302)
    val GreenDark = Color(0xFF163C01)

    val Red = Color(0xFFFF4B4B)
    val RedBevel = Color(0xFFD11919)

    val Yellow = Color(0xFFFFC800)
    val YellowBevel = Color(0xFFD8A800)
    val YellowDark = Color(0xFF2B1D00)

    val Blue = Color(0xFF1CB0F6)
    val BlueBevel = Color(0xFF1899D6)
    val BlueDark = Color(0xFF042C48)

    val Orange = Color(0xFFFF9600)
    val OrangeBevel = Color(0xFFD47800)

    val Purple = Color(0xFFCE82FF)
    val PurpleBevel = Color(0xFFA855F7)

    val SlateBackground = Color(0xFF131F24)
    val DeepCanvasDark = Color(0xFF0C1418)
    val BorderDark = Color(0xFF20343D)
    val CardSurfaceDark = Color(0xFF1A2B32)
}

/**
 * 3D Tactile Duolingo Button with bottom bevel depth and tactile press animation.
 */
@Composable
fun Duolingo3DButton(
    text: String,
    modifier: Modifier = Modifier,
    buttonColor: Color = DuolingoPalette.Blue,
    bevelColor: Color = DuolingoPalette.BlueBevel,
    textColor: Color = DuolingoPalette.BlueDark,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressOffsetY by animateFloatAsState(
        targetValue = if (isPressed) 3.5f else 0f,
        animationSpec = tween(durationMillis = 60),
        label = "duolingo_btn_press"
    )

    Box(
        modifier = modifier
            .height(52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        // Bottom Shadow / Bevel Lip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset(y = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bevelColor)
        )

        // Top Face of Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .offset(y = pressOffsetY.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(buttonColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    fontSize = 15.5.sp,
                    letterSpacing = 0.8.sp
                ),
                color = textColor
            )
        }
    }
}

/**
 * Exact Duolingo Share Button (Dark container with blue upload arrow)
 */
@Composable
fun DuolingoShareButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = DuolingoPalette.SlateBackground,
        border = BorderStroke(2.5.dp, DuolingoPalette.BorderDark),
        modifier = modifier.size(52.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(20.dp)) {
                val iconColor = DuolingoPalette.Blue
                val strokeW = 2.4.dp.toPx()
                // Box container outline
                val path = Path().apply {
                    moveTo(size.width * 0.12f, size.height * 0.42f)
                    lineTo(size.width * 0.12f, size.height * 0.88f)
                    lineTo(size.width * 0.88f, size.height * 0.88f)
                    lineTo(size.width * 0.88f, size.height * 0.42f)
                }
                drawPath(path, iconColor, style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round))
                // Upward arrow stem
                drawLine(
                    iconColor,
                    start = Offset(size.width * 0.5f, size.height * 0.65f),
                    end = Offset(size.width * 0.5f, size.height * 0.12f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Left arrow head
                drawLine(
                    iconColor,
                    start = Offset(size.width * 0.28f, size.height * 0.32f),
                    end = Offset(size.width * 0.5f, size.height * 0.12f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // Right arrow head
                drawLine(
                    iconColor,
                    start = Offset(size.width * 0.72f, size.height * 0.32f),
                    end = Offset(size.width * 0.5f, size.height * 0.12f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * Exact Duolingo Stat Box (Yellow, Green, Sky Blue)
 */
@Composable
fun DuolingoStatBox(
    modifier: Modifier = Modifier,
    headerText: String,
    headerColor: Color,
    headerTextColor: Color,
    borderColor: Color,
    hasGloss: Boolean = false,
    icon: @Composable () -> Unit,
    valueText: String,
    valueColor: Color
) {
    Surface(
        modifier = modifier.height(98.dp),
        shape = RoundedCornerShape(20.dp),
        color = borderColor,
        border = BorderStroke(2.8.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp))
        ) {
            // Header Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(headerColor),
                contentAlignment = Alignment.Center
            ) {
                if (hasGloss) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val glossColor = Color.White.copy(alpha = 0.28f)
                        drawLine(
                            color = glossColor,
                            start = Offset(size.width * 0.70f, 0f),
                            end = Offset(size.width * 0.82f, size.height),
                            strokeWidth = 6.dp.toPx()
                        )
                        drawLine(
                            color = glossColor,
                            start = Offset(size.width * 0.86f, 0f),
                            end = Offset(size.width * 0.94f, size.height),
                            strokeWidth = 3.dp.toPx()
                        )
                    }
                }
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        letterSpacing = 0.4.sp
                    ),
                    color = headerTextColor,
                    maxLines = 1
                )
            }

            // Dark Container Body
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                    .background(DuolingoPalette.SlateBackground)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    icon()
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp
                        ),
                        color = valueColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * Custom Duolingo Lightning Bolt Vector
 */
@Composable
fun DuolingoBoltIcon(modifier: Modifier = Modifier, color: Color = DuolingoPalette.Yellow) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.58f, size.height * 0.05f)
            lineTo(size.width * 0.16f, size.height * 0.54f)
            lineTo(size.width * 0.48f, size.height * 0.54f)
            lineTo(size.width * 0.40f, size.height * 0.95f)
            lineTo(size.width * 0.86f, size.height * 0.42f)
            lineTo(size.width * 0.54f, size.height * 0.42f)
            close()
        }
        drawPath(path, color, style = Fill)
    }
}

/**
 * Custom Duolingo Bullseye Target Vector
 */
@Composable
fun DuolingoTargetIcon(modifier: Modifier = Modifier, color: Color = DuolingoPalette.Green) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.48f, size.height * 0.52f)
        val strokeW = size.width * 0.12f
        // Outer ring
        drawCircle(color = color, radius = size.width * 0.40f, center = center, style = Stroke(width = strokeW))
        // Inner bullseye
        drawCircle(color = color, radius = size.width * 0.18f, center = center, style = Fill)
        // Upper right target arrow flight
        drawLine(
            color = color,
            start = Offset(size.width * 0.65f, size.height * 0.35f),
            end = Offset(size.width * 0.92f, size.height * 0.08f),
            strokeWidth = strokeW * 0.9f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Custom Duolingo Stopwatch Clock Vector
 */
@Composable
fun DuolingoClockIcon(modifier: Modifier = Modifier, color: Color = DuolingoPalette.Blue) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width * 0.5f, size.height * 0.55f)
        val outerRadius = size.width * 0.40f
        // Filled outer dial
        drawCircle(color = color, radius = outerRadius, center = center, style = Fill)
        // Dark interior
        drawCircle(color = DuolingoPalette.SlateBackground, radius = outerRadius * 0.70f, center = center, style = Fill)
        // Clock hands
        val handStroke = size.width * 0.10f
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, center.y - outerRadius * 0.52f),
            strokeWidth = handStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x + outerRadius * 0.42f, center.y),
            strokeWidth = handStroke,
            cap = StrokeCap.Round
        )
        // Top stopwatch crown button
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.02f),
            end = Offset(size.width * 0.5f, size.height * 0.15f),
            strokeWidth = handStroke * 1.1f,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Duolingo Early Bird Chest Graphic matching the authentic Duolingo UI.
 * Wooden/peach body planks with horizontal grooves, gold side straps with 3 rivets each,
 * and a center golden sunburst lock emblem with keyhole.
 */
@Composable
fun EarlyBirdChestGraphic(modifier: Modifier = Modifier) {
    DuolingoChest3DGraphic(
        rarity = ChestRarity.COMMON,
        modifier = modifier
    )
}

/**
 * Duolingo Night Owl Chest Graphic matching the authentic Duolingo UI.
 */
@Composable
fun NightOwlChestGraphic(modifier: Modifier = Modifier) {
    DuolingoChest3DGraphic(
        rarity = ChestRarity.RARE,
        modifier = modifier
    )
}

