package com.focusbyrj.app.ui.screens.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.ui.components.ProfessionalSlider

@Composable
fun ChatTextSizeDialog(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.FormatSize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Chat Text Size", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Adjust text size for the assistant chat window only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Live Preview Bubble
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "🌅 Good Morning!\nHere is your daily update:\n🎉 All clear for today!",
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.45f).sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Stepper Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(fontSizeSp - 1f) },
                        enabled = fontSizeSp > 12f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Decrease size")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${fontSizeSp.toInt()} sp",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = when {
                                fontSizeSp <= 13f -> "Small"
                                fontSizeSp <= 15f -> "Default"
                                fontSizeSp <= 18f -> "Medium"
                                fontSizeSp <= 21f -> "Large"
                                else -> "Extra Large"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    FilledTonalIconButton(
                        onClick = { onFontSizeChange(fontSizeSp + 1f) },
                        enabled = fontSizeSp < 24f,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Increase size")
                    }
                }

                // Classic professional slider
                ProfessionalSlider(
                    value = fontSizeSp,
                    onValueChange = { onFontSizeChange(kotlin.math.round(it)) },
                    valueRange = 12f..24f,
                    modifier = Modifier.fillMaxWidth()
                )

                // Quick preset buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Small" to 13f,
                        "Default" to 15f,
                        "Large" to 18f,
                        "Huge" to 21f
                    ).forEach { (label, size) ->
                        val isSelected = (fontSizeSp == size)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onFontSizeChange(size) },
                            label = { Text(label, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        dismissButton = {
            TextButton(onClick = { onFontSizeChange(15f) }) {
                Text("Reset")
            }
        }
    )
}
