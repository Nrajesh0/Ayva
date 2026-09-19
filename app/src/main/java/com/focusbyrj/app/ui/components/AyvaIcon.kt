package com.focusbyrj.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val AyvaIcon: ImageVector
    get() = ImageVector.Builder(
        name = "AyvaIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Main center sparkle
        path(fill = SolidColor(Color.White)) {
            moveTo(12f, 1f)
            curveTo(12f, 7.5f, 16.5f, 12f, 23f, 12f)
            curveTo(16.5f, 12f, 12f, 16.5f, 12f, 23f)
            curveTo(12f, 16.5f, 7.5f, 12f, 1f, 12f)
            curveTo(7.5f, 12f, 12f, 7.5f, 12f, 1f)
            close()
        }
        // Top left smaller sparkle
        path(fill = SolidColor(Color.White)) {
            moveTo(6.5f, 3f)
            curveTo(6.5f, 4.5f, 7.5f, 5.5f, 9f, 5.5f)
            curveTo(7.5f, 5.5f, 6.5f, 6.5f, 6.5f, 8f)
            curveTo(6.5f, 6.5f, 5.5f, 5.5f, 4f, 5.5f)
            curveTo(5.5f, 5.5f, 6.5f, 4.5f, 6.5f, 3f)
            close()
        }
        // Bottom right smaller sparkle
        path(fill = SolidColor(Color.White)) {
            moveTo(18f, 15f)
            curveTo(18f, 16f, 19f, 17f, 20f, 17f)
            curveTo(19f, 17f, 18f, 18f, 18f, 19f)
            curveTo(18f, 18f, 17f, 17f, 16f, 17f)
            curveTo(17f, 17f, 18f, 16f, 18f, 15f)
            close()
        }
    }.build()
