/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.ui.screens.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/**
 * Data model for a row in a dynamic Google Keep staggered image collage
 */
data class CollageRowConfig(
    val startIndex: Int,
    val itemsInRow: Int,
    val weights: List<Float>,
    val rowHeight: Dp
)

/**
 * Computes aesthetic Google Keep row distributions with staggered, varied column widths
 * in each row (all items in the same row share the exact same height, but have varied, dynamic widths).
 */
fun buildGoogleKeepCollageRows(
    totalImages: Int,
    maxImagesToShow: Int = totalImages,
    isCardPreview: Boolean = false
): List<CollageRowConfig> {
    val count = totalImages.coerceAtMost(maxImagesToShow)
    if (count <= 0) return emptyList()

    val rows = mutableListOf<CollageRowConfig>()

    when (count) {
        1 -> {
            rows.add(CollageRowConfig(0, 1, listOf(1f), if (isCardPreview) 180.dp else 220.dp))
        }
        2 -> {
            // Keep often gives a dynamic 1.25f vs 0.75f or 1f vs 1f
            rows.add(CollageRowConfig(0, 2, listOf(1.15f, 0.85f), if (isCardPreview) 130.dp else 165.dp))
        }
        3 -> {
            // Row 1: 1 hero wide image
            // Row 2: 2 images (asymmetric widths)
            if (isCardPreview) {
                rows.add(CollageRowConfig(0, 1, listOf(1f), 125.dp))
                rows.add(CollageRowConfig(1, 2, listOf(1.1f, 0.9f), 90.dp))
            } else {
                rows.add(CollageRowConfig(0, 1, listOf(1f), 175.dp))
                rows.add(CollageRowConfig(1, 2, listOf(1.15f, 0.85f), 135.dp))
            }
        }
        4 -> {
            // 2 rows with staggered varied widths
            val h = if (isCardPreview) 95.dp else 140.dp
            rows.add(CollageRowConfig(0, 2, listOf(1.2f, 0.8f), h))
            rows.add(CollageRowConfig(2, 2, listOf(0.8f, 1.2f), h))
        }
        5 -> {
            // Row 1: 2 images (1.15f, 0.85f)
            // Row 2: 3 images (0.9f, 1.2f, 0.9f)
            val h1 = if (isCardPreview) 100.dp else 145.dp
            val h2 = if (isCardPreview) 85.dp else 125.dp
            rows.add(CollageRowConfig(0, 2, listOf(1.15f, 0.85f), h1))
            rows.add(CollageRowConfig(2, 3, listOf(0.85f, 1.3f, 0.85f), h2))
        }
        6 -> {
            // Row 1: 3 images (1.1f, 0.8f, 1.1f)
            // Row 2: 3 images (0.85f, 1.3f, 0.85f)
            val h = if (isCardPreview) 88.dp else 130.dp
            rows.add(CollageRowConfig(0, 3, listOf(1.15f, 0.85f, 1.0f), h))
            rows.add(CollageRowConfig(3, 3, listOf(0.85f, 1.25f, 0.9f), h))
        }
        7 -> {
            // Row 1: 2 images (1.2f, 0.8f)
            // Row 2: 3 images (0.9f, 1.2f, 0.9f)
            // Row 3: 2 images (0.8f, 1.2f)
            val h1 = if (isCardPreview) 88.dp else 135.dp
            val h2 = if (isCardPreview) 80.dp else 120.dp
            val h3 = if (isCardPreview) 88.dp else 135.dp
            rows.add(CollageRowConfig(0, 2, listOf(1.2f, 0.8f), h1))
            rows.add(CollageRowConfig(2, 3, listOf(0.9f, 1.2f, 0.9f), h2))
            rows.add(CollageRowConfig(5, 2, listOf(0.8f, 1.2f), h3))
        }
        8 -> {
            // Row 1: 3 images (1.1f, 0.85f, 1.05f)
            // Row 2: 2 images (1.15f, 0.85f)
            // Row 3: 3 images (0.85f, 1.25f, 0.9f)
            val h1 = if (isCardPreview) 82.dp else 125.dp
            val h2 = if (isCardPreview) 90.dp else 135.dp
            val h3 = if (isCardPreview) 82.dp else 125.dp
            rows.add(CollageRowConfig(0, 3, listOf(1.1f, 0.85f, 1.05f), h1))
            rows.add(CollageRowConfig(3, 2, listOf(1.15f, 0.85f), h2))
            rows.add(CollageRowConfig(5, 3, listOf(0.85f, 1.25f, 0.9f), h3))
        }
        9 -> {
            // 9 images: 3 in a row, stacked one below other (3 rows x 3 columns)
            // Each row has uniform height, with varied, organic column widths (e.g. wide-short-medium)
            val h = if (isCardPreview) 82.dp else 125.dp
            rows.add(CollageRowConfig(0, 3, listOf(1.25f, 0.85f, 0.90f), h))
            rows.add(CollageRowConfig(3, 3, listOf(0.85f, 1.30f, 0.85f), h))
            rows.add(CollageRowConfig(6, 3, listOf(0.90f, 0.85f, 1.25f), h))
        }
        else -> {
            // 10+ images: chunk into rows of 3 and 2 with rhythmic alternating weights
            var currentIdx = 0
            var rowIndex = 0
            while (currentIdx < count) {
                val remaining = count - currentIdx
                val itemsInThisRow = when {
                    remaining == 4 -> 2
                    remaining >= 3 -> 3
                    else -> remaining
                }

                val weights = when (itemsInThisRow) {
                    1 -> listOf(1f)
                    2 -> if (rowIndex % 2 == 0) listOf(1.2f, 0.8f) else listOf(0.8f, 1.2f)
                    3 -> when (rowIndex % 3) {
                        0 -> listOf(1.25f, 0.85f, 0.90f)
                        1 -> listOf(0.85f, 1.30f, 0.85f)
                        else -> listOf(0.90f, 0.85f, 1.25f)
                    }
                    else -> List(itemsInThisRow) { 1f }
                }

                val h = if (itemsInThisRow == 3) {
                    if (isCardPreview) 82.dp else 125.dp
                } else {
                    if (isCardPreview) 92.dp else 135.dp
                }

                rows.add(CollageRowConfig(currentIdx, itemsInThisRow, weights, h))
                currentIdx += itemsInThisRow
                rowIndex++
            }
        }
    }

    return rows
}

/**
 * Google Keep Image Collage for Note Cards (with +N overflow overlay when images > 4)
 */
@Composable
fun KeepCardImageCollage(
    imageUris: List<String>,
    modifier: Modifier = Modifier
) {
    if (imageUris.isEmpty()) return

    val totalCount = imageUris.size
    val maxToShow = 4
    val isOverflow = totalCount > maxToShow
    val displayUris = if (isOverflow) imageUris.take(maxToShow) else imageUris
    val rows = buildGoogleKeepCollageRows(
        totalImages = displayUris.size,
        isCardPreview = true
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            rows.forEach { rowConfig ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowConfig.rowHeight),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    for (i in 0 until rowConfig.itemsInRow) {
                        val imgIndex = rowConfig.startIndex + i
                        if (imgIndex < displayUris.size) {
                            val uri = displayUris[imgIndex]
                            val weight = rowConfig.weights.getOrElse(i) { 1f }
                            val isLastDisplayedItem = isOverflow && imgIndex == maxToShow - 1

                            Box(
                                modifier = Modifier
                                    .weight(weight)
                                    .fillMaxHeight()
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(uri)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Note attachment ${imgIndex + 1}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isLastDisplayedItem) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color.Black.copy(alpha = 0.65f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "+${totalCount - maxToShow + 1}",
                                                color = Color.White,
                                                style = MaterialTheme.typography.titleMedium.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Google Keep Image Collage for Note Editor
 * Displays all attached images (supports 1, 2, 3, 4, 9, or N images) with Google Keep's
 * organic row heights and varied column widths, plus individual remove actions and click previews.
 */
@Composable
fun KeepEditorImageCollage(
    imageUris: List<String>,
    onImageClick: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (imageUris.isEmpty()) return

    val rows = buildGoogleKeepCollageRows(
        totalImages = imageUris.size,
        isCardPreview = false
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { rowConfig ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowConfig.rowHeight),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 0 until rowConfig.itemsInRow) {
                    val imgIndex = rowConfig.startIndex + i
                    if (imgIndex < imageUris.size) {
                        val uri = imageUris[imgIndex]
                        val weight = rowConfig.weights.getOrElse(i) { 1f }

                        EditorImageTile(
                            uri = uri,
                            onImageClick = onImageClick,
                            onRemoveImage = onRemoveImage,
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorImageTile(
    uri: String,
    onImageClick: (String) -> Unit,
    onRemoveImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onImageClick(uri) }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .crossfade(true)
                .build(),
            contentDescription = "Attached image",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // Google Keep Translucent Remove Action Button
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(30.dp)
                .clip(CircleShape)
                .clickable { onRemoveImage(uri) },
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.65f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove image",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
