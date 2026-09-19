package com.focusbyrj.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Clean, standard professional slider component.
 * Features a smooth continuous track and a circular dot thumb with fluid dragging.
 * Does not render tick marks, line bars, or discrete dot arrays.
 */
@Composable
fun ProfessionalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    trackHeight: Dp = 6.dp,
    thumbDiameter: Dp = 20.dp,
    enabled: Boolean = true
) {
    var isDragging by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val rangeSpan = (valueRange.endInclusive - valueRange.start).let { if (it <= 0f) 1f else it }
        val normalized = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

        val density = LocalDensity.current
        val thumbRadiusPx = with(density) { (thumbDiameter / 2).toPx() }
        val usableWidth = (widthPx - thumbRadiusPx * 2).coerceAtLeast(1f)
        val thumbOffsetPx = thumbRadiusPx + normalized * usableWidth

        val animatedElevation by animateFloatAsState(
            targetValue = if (isDragging) 6f else 2f,
            label = "thumb_elevation"
        )
        val animatedScale by animateFloatAsState(
            targetValue = if (isDragging) 1.15f else 1.0f,
            label = "thumb_scale"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(valueRange, usableWidth) {
                                detectTapGestures { offset ->
                                    val ratio = ((offset.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                                    val newValue = valueRange.start + ratio * rangeSpan
                                    onValueChange(newValue)
                                }
                            }
                            .pointerInput(valueRange, usableWidth) {
                                detectHorizontalDragGestures(
                                    onDragStart = { isDragging = true },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onHorizontalDrag = { change, _ ->
                                        change.consume()
                                        val ratio = ((change.position.x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                                        val newValue = valueRange.start + ratio * rangeSpan
                                        onValueChange(newValue)
                                    }
                                )
                            }
                    } else Modifier
                ),
            contentAlignment = Alignment.CenterStart
        ) {
            // Background Inactive Track (Clean smooth continuous pill)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(trackColor)
            )

            // Active Accent Track (Clean smooth continuous pill)
            Box(
                modifier = Modifier
                    .width(with(density) { thumbOffsetPx.toDp() })
                    .height(trackHeight)
                    .clip(RoundedCornerShape(trackHeight / 2))
                    .background(accentColor)
            )

            // Sleek Circular Dot Thumb (Pure dot dragging)
            val currentThumbSize = thumbDiameter * animatedScale
            val currentThumbRadiusDp = currentThumbSize / 2
            Box(
                modifier = Modifier
                    .offset(x = with(density) { thumbOffsetPx.toDp() - currentThumbRadiusDp })
                    .size(currentThumbSize)
                    .shadow(elevation = animatedElevation.dp, shape = CircleShape)
                    .background(Color.White, CircleShape)
                    .border(
                        width = 3.5.dp,
                        color = accentColor,
                        shape = CircleShape
                    )
            )
        }
    }
}
