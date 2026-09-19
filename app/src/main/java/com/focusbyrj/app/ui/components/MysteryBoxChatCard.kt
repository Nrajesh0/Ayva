package com.focusbyrj.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MysteryBoxChatCard(
    onOpenBox: () -> Unit,
    isAvailable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mystery_card_anim")
    
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val orbitRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_rot"
    )

    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "card_box_hover"
    )

    val primaryCyan = Color(0xFF00F0FF)
    val royalIndigo = Color(0xFF6366F1)
    val deepPurple = Color(0xFFA855F7)
    val goldAccent = Color(0xFFF59E0B)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0C1322)
        ),
        border = BorderStroke(
            1.2.dp,
            if (isAvailable) {
                Brush.sweepGradient(
                    colors = listOf(
                        primaryCyan.copy(alpha = 0.7f),
                        royalIndigo.copy(alpha = 0.35f),
                        deepPurple.copy(alpha = 0.8f),
                        goldAccent.copy(alpha = 0.5f),
                        primaryCyan.copy(alpha = 0.7f)
                    )
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.04f))
                )
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isAvailable) 8.dp else 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isAvailable) primaryCyan.copy(alpha = 0.12f) else Color.Transparent,
                            Color(0xFF0C1322)
                        ),
                        center = Offset(100f, 60f),
                        radius = 450f
                    )
                )
                .clickable(enabled = isAvailable, onClick = onOpenBox)
                .padding(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // -------------------------------------------------------------
                // 1. 3D GLOWING QUANTUM VAULT ICON WITH ORBIT RING
                // -------------------------------------------------------------
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF142038), Color(0xFF090F1C))
                            )
                        )
                        .border(
                            1.dp,
                            if (isAvailable) primaryCyan.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(46.dp)
                            .offset(y = if (isAvailable) hoverOffset.dp else 0.dp)
                    ) {
                        val w = size.width
                        val h = size.height
                        val cx = w / 2
                        val cy = h / 2

                        // Orbiting kinetic ring
                        if (isAvailable) {
                            rotate(degrees = orbitRotation, pivot = Offset(cx, cy)) {
                                drawOval(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            primaryCyan.copy(alpha = 0.9f),
                                            royalIndigo.copy(alpha = 0.15f),
                                            deepPurple.copy(alpha = 0.85f)
                                        )
                                    ),
                                    topLeft = Offset(cx - w * 0.44f, cy - h * 0.22f),
                                    size = Size(w * 0.88f, h * 0.44f),
                                    style = Stroke(width = 1.6.dp.toPx())
                                )
                            }
                        }

                        // Core ambient glow
                        if (isAvailable) {
                            drawCircle(
                                color = primaryCyan.copy(alpha = 0.28f * pulse),
                                radius = w * 0.38f,
                                center = Offset(cx, cy)
                            )
                        }

                        // 3D Isometric Facets
                        val topColor = if (isAvailable) Color(0xFF38BDF8) else Color(0xFF475569)
                        val leftColor = if (isAvailable) Color(0xFF0284C7) else Color(0xFF334155)
                        val rightColor = if (isAvailable) Color(0xFF6366F1) else Color(0xFF1E293B)
                        val seamColor = if (isAvailable) Color(0xFFE0F2FE) else Color(0xFF64748B)

                        val topPath = Path().apply {
                            moveTo(cx, cy - h * 0.36f)
                            lineTo(cx + w * 0.34f, cy - h * 0.14f)
                            lineTo(cx, cy + h * 0.08f)
                            lineTo(cx - w * 0.34f, cy - h * 0.14f)
                            close()
                        }

                        val leftPath = Path().apply {
                            moveTo(cx - w * 0.34f, cy - h * 0.14f)
                            lineTo(cx, cy + h * 0.08f)
                            lineTo(cx, cy + h * 0.38f)
                            lineTo(cx - w * 0.34f, cy + h * 0.16f)
                            close()
                        }

                        val rightPath = Path().apply {
                            moveTo(cx, cy + h * 0.08f)
                            lineTo(cx + w * 0.34f, cy - h * 0.14f)
                            lineTo(cx + w * 0.34f, cy + h * 0.16f)
                            lineTo(cx, cy + h * 0.38f)
                            close()
                        }

                        drawPath(topPath, topColor)
                        drawPath(leftPath, leftColor)
                        drawPath(rightPath, rightColor)

                        // Glowing Bevel Highlights
                        drawPath(topPath, seamColor.copy(alpha = 0.6f), style = Stroke(width = 1.2.dp.toPx()))
                        drawPath(leftPath, seamColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))
                        drawPath(rightPath, seamColor.copy(alpha = 0.35f), style = Stroke(width = 1.2.dp.toPx()))

                        // Glowing Center Reactor Node
                        if (isAvailable) {
                            drawCircle(
                                color = Color.White,
                                radius = 2.5.dp.toPx(),
                                center = Offset(cx, cy + h * 0.08f)
                            )
                            drawCircle(
                                color = primaryCyan,
                                radius = 5.dp.toPx() * pulse,
                                center = Offset(cx, cy + h * 0.08f),
                                style = Stroke(width = 1.2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // -------------------------------------------------------------
                // 2. VAULT DETAILS & DESCRIPTIONS (Adaptive & Non-Truncating)
                // -------------------------------------------------------------
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isAvailable) primaryCyan.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(0.8.dp, if (isAvailable) primaryCyan.copy(alpha = 0.4f) else Color.Transparent)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                if (isAvailable) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(primaryCyan)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = if (isAvailable) "READY TO UNLOCK" else "CLAIMED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = if (isAvailable) primaryCyan else Color(0xFF94A3B8)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (isAvailable) "Quantum Mystery Vault" else "Daily Vault Claimed",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            letterSpacing = (-0.2).sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (isAvailable) "Unbox XP, Gold & Streak Freezes" else "New drop available tomorrow",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            color = if (isAvailable) Color(0xFF94A3B8) else Color(0xFF64748B)
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // -------------------------------------------------------------
                // 3. ACTION BUTTON / STATUS INDICATOR
                // -------------------------------------------------------------
                if (isAvailable) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Transparent,
                        border = BorderStroke(
                            1.dp,
                            Brush.linearGradient(listOf(primaryCyan, royalIndigo))
                        ),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(
                                            primaryCyan.copy(alpha = 0.25f),
                                            royalIndigo.copy(alpha = 0.35f)
                                        )
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        fontSize = 11.sp
                                    ),
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Open",
                                    tint = primaryCyan,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Claimed",
                            tint = Color(0xFF34D399),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
