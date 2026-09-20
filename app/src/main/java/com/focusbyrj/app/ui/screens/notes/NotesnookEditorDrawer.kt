/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it program will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app.ui.screens.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.FormatAlignRight
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private val NotesnookGreen = Color(0xFF22C55E) // Signature Notesnook active green
private val TileBgDark = Color(0xFF282C32)
private val TileBgLight = Color(0xFFE2E6ED)

/**
 * Authentic Notesnook-style rich text formatting toolbar.
 * Implements two-tier expandable rows, popup menus, active style indicators,
 * font/heading selectors, and 3x6 numbered list hierarchy.
 */
@Composable
fun NotesnookEditorDrawer(
    activeStyles: ActiveStyles,
    onToggleBold: () -> Unit,
    onToggleItalic: () -> Unit,
    onToggleUnderline: () -> Unit,
    onToggleStrikethrough: () -> Unit,
    onToggleHighlight: () -> Unit,
    onToggleCode: () -> Unit,
    onToggleSubscript: () -> Unit,
    onToggleSuperscript: () -> Unit,
    onToggleHeading: (Int) -> Unit,
    onToggleBullet: () -> Unit,
    onToggleNumbered: (prefix: String) -> Unit,
    onToggleQuote: () -> Unit,
    onInsertDivider: () -> Unit,
    onInsertLink: () -> Unit,
    onInsertCallout: (String) -> Unit,
    onInsertTimestamp: () -> Unit,
    onOpenInsertMenu: () -> Unit = {},
    onIndent: (Boolean) -> Unit,
    onClearFormatting: () -> Unit,
    selectedFontKey: String,
    onFontChange: (String) -> Unit,
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    lineHeightSp: Float,
    onLineHeightChange: (Float) -> Unit,
    isChecklistMode: Boolean,
    onToggleChecklistMode: () -> Unit,
    onCloseDrawer: () -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    var showUpperTier by remember { mutableStateOf(true) }
    var showHeadingMenu by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }
    var showListMenu by remember { mutableStateOf(false) }
    var showCalloutMenu by remember { mutableStateOf(false) }

    val barBg = if (isDark) Color(0xFF16181B) else Color(0xFFF3F4F6)
    val popupBg = if (isDark) Color(0xFF202328) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color(0xFF2C3038) else Color(0xFFE2E6ED)
    val onBgColor = if (isDark) Color(0xFFE3E7ED) else Color(0xFF2D3139)
    val activeTint = NotesnookGreen
    val tileBg = if (isDark) TileBgDark else TileBgLight

    Surface(
        color = barBg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("notesnook_compact_toolbar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // ==========================================
            // TIER 1 (UPPER ROW): Extended & Math/Code Tools
            // ==========================================
            AnimatedVisibility(
                visible = showUpperTier,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val upperScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .horizontalScroll(upperScrollState)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Outdent / Indent
                    NotesnookTileButton(
                        onClick = { onIndent(true) },
                        contentDescription = "Outdent",
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatAlignLeft,
                            contentDescription = "Outdent",
                            tint = onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    NotesnookTileButton(
                        onClick = { onIndent(false) },
                        contentDescription = "Indent",
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatAlignRight,
                            contentDescription = "Indent",
                            tint = onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Inline Code Pill `< >`
                    NotesnookTileButton(
                        isActive = activeStyles.isCode,
                        onClick = onToggleCode,
                        contentDescription = "Inline Code",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Text(
                            text = "< >",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeStyles.isCode) activeTint else onBgColor
                            )
                        )
                    }

                    // Subscript `x₂`
                    NotesnookTileButton(
                        isActive = activeStyles.isSubscript,
                        onClick = onToggleSubscript,
                        contentDescription = "Subscript",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Text(
                            text = "x₂",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeStyles.isSubscript) activeTint else onBgColor
                            )
                        )
                    }

                    // Superscript `x²`
                    NotesnookTileButton(
                        isActive = activeStyles.isSuperscript,
                        onClick = onToggleSuperscript,
                        contentDescription = "Superscript",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Text(
                            text = "x²",
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeStyles.isSuperscript) activeTint else onBgColor
                            )
                        )
                    }

                    // Highlight Marker `✏ ˅`
                    NotesnookTileButton(
                        isActive = activeStyles.isHighlight,
                        onClick = onToggleHighlight,
                        contentDescription = "Highlight",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "==",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (activeStyles.isHighlight) activeTint else onBgColor
                                )
                            )
                        }
                    }

                    // List Hierarchy Popup Trigger `1≡ ˅`
                    Box {
                        NotesnookTileButton(
                            isActive = showListMenu || activeStyles.isNumbered,
                            onClick = { showListMenu = !showListMenu },
                            contentDescription = "Numbered List Styles",
                            isDark = isDark,
                            activeTint = activeTint
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FormatListNumbered,
                                    contentDescription = "List Styles",
                                    tint = if (showListMenu || activeStyles.isNumbered) activeTint else onBgColor,
                                    modifier = Modifier.size(17.dp)
                                )
                                Icon(
                                    imageVector = if (showListMenu) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = if (showListMenu || activeStyles.isNumbered) activeTint else onBgColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Notesnook 3x6 List Hierarchy Popup (Screenshot 1)
                        if (showListMenu) {
                            Popup(
                                alignment = Alignment.TopStart,
                                onDismissRequest = { showListMenu = false },
                                properties = PopupProperties(
                                    focusable = false,
                                    dismissOnClickOutside = true,
                                    dismissOnBackPress = true
                                )
                            ) {
                                NotesnookListHierarchyPopup(
                                    onSelectStyle = { prefix ->
                                        onToggleNumbered(prefix)
                                        showListMenu = false
                                    },
                                    isDark = isDark
                                )
                            }
                        }
                    }

                    // Bullet List `•≡`
                    NotesnookTileButton(
                        isActive = activeStyles.isBullet,
                        onClick = onToggleBullet,
                        contentDescription = "Bullet List",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FormatListBulleted,
                            contentDescription = "Bullet List",
                            tint = if (activeStyles.isBullet) activeTint else onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Checklist `☑`
                    NotesnookTileButton(
                        isActive = isChecklistMode,
                        onClick = onToggleChecklistMode,
                        contentDescription = "Checklist",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckBox,
                            contentDescription = "Checklist",
                            tint = if (isChecklistMode) activeTint else onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Link `🔗`
                    NotesnookTileButton(
                        onClick = onInsertLink,
                        contentDescription = "Insert Link",
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = "Insert Link",
                            tint = onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Math / Formula / Quote `fx`
                    NotesnookTileButton(
                        isActive = activeStyles.isQuote,
                        onClick = onToggleQuote,
                        contentDescription = "Quote / Formula",
                        isDark = isDark,
                        activeTint = activeTint
                    ) {
                        Text(
                            text = "fx",
                            style = TextStyle(
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (activeStyles.isQuote) activeTint else onBgColor
                            )
                        )
                    }

                    // Callout / Pin `📌 ˅`
                    Box {
                        NotesnookTileButton(
                            isActive = showCalloutMenu,
                            onClick = { showCalloutMenu = !showCalloutMenu },
                            contentDescription = "Callout / Pin",
                            isDark = isDark,
                            activeTint = activeTint
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PushPin,
                                contentDescription = "Callout",
                                tint = if (showCalloutMenu) activeTint else onBgColor,
                                modifier = Modifier.size(17.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showCalloutMenu,
                            onDismissRequest = { showCalloutMenu = false }
                        ) {
                            listOf(
                                "💡 Note" to "💡 NOTE",
                                "⚠️ Warning" to "⚠️ WARNING",
                                "📌 Important" to "📌 IMPORTANT",
                                "🚀 Tip" to "🚀 TIP"
                            ).forEach { (label, tag) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = TextStyle(fontSize = 13.sp)) },
                                    onClick = {
                                        onInsertCallout(tag)
                                        showCalloutMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Divider `—`
                    NotesnookTileButton(
                        onClick = onInsertDivider,
                        contentDescription = "Horizontal Divider",
                        isDark = isDark
                    ) {
                        Icon(
                            imageVector = Icons.Filled.HorizontalRule,
                            contentDescription = "Horizontal Divider",
                            tint = onBgColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (showUpperTier) {
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = borderColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }

            // ==========================================
            // TIER 2 (LOWER ROW): Primary Notesnook Strip
            // ==========================================
            val lowerScrollState = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .horizontalScroll(lowerScrollState)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Insert `+` (Opens authentic Notesnook Insert Sheet)
                NotesnookTileButton(
                    onClick = onOpenInsertMenu,
                    contentDescription = "Insert Elements",
                    isDark = isDark
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Insert",
                        tint = activeTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Clear formatting `⌫`
                NotesnookTileButton(
                    onClick = onClearFormatting,
                    contentDescription = "Clear formatting",
                    isDark = isDark
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatClear,
                        contentDescription = "Clear formatting",
                        tint = onBgColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                // `[ B ]` Bold Tile
                NotesnookTileButton(
                    isActive = activeStyles.isBold,
                    onClick = onToggleBold,
                    contentDescription = "Bold",
                    isDark = isDark,
                    activeTint = activeTint
                ) {
                    Text(
                        text = "B",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activeStyles.isBold) activeTint else onBgColor
                        )
                    )
                }

                // `[ I ]` Italic Tile
                NotesnookTileButton(
                    isActive = activeStyles.isItalic,
                    onClick = onToggleItalic,
                    contentDescription = "Italic",
                    isDark = isDark,
                    activeTint = activeTint
                ) {
                    Text(
                        text = "I",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontStyle = FontStyle.Italic,
                            fontWeight = FontWeight.Bold,
                            color = if (activeStyles.isItalic) activeTint else onBgColor
                        )
                    )
                }

                // `[ U ]` Underline Tile
                NotesnookTileButton(
                    isActive = activeStyles.isUnderline,
                    onClick = onToggleUnderline,
                    contentDescription = "Underline",
                    isDark = isDark,
                    activeTint = activeTint
                ) {
                    Text(
                        text = "U",
                        style = TextStyle(
                            fontSize = 14.sp,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = FontWeight.Bold,
                            color = if (activeStyles.isUnderline) activeTint else onBgColor
                        )
                    )
                }

                // `[ S ]` Strikethrough Tile
                NotesnookTileButton(
                    isActive = activeStyles.isStrikethrough,
                    onClick = onToggleStrikethrough,
                    contentDescription = "Strikethrough",
                    isDark = isDark,
                    activeTint = activeTint
                ) {
                    Text(
                        text = "S",
                        style = TextStyle(
                            fontSize = 14.sp,
                            textDecoration = TextDecoration.LineThrough,
                            fontWeight = FontWeight.Bold,
                            color = if (activeStyles.isStrikethrough) activeTint else onBgColor
                        )
                    )
                }

                // `[ ⋮ ]` Toggle Upper Tier Bar
                NotesnookTileButton(
                    isActive = showUpperTier,
                    onClick = { showUpperTier = !showUpperTier },
                    contentDescription = "Toggle extended options",
                    isDark = isDark,
                    activeTint = activeTint
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More tools",
                        tint = if (showUpperTier) activeTint else onBgColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                CompactSeparator(isDark)

                // `—` `16px` `+` (Inline Text Size Stepper)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDark) Color(0xFF1A1D21) else Color(0xFFE8EBF0))
                        .padding(horizontal = 4.dp)
                ) {
                    // Decrement `-`
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val next = (fontSizeSp - 1f).coerceIn(11f, 32f)
                                onFontSizeChange(next)
                                onLineHeightChange(next * 1.45f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Remove,
                            contentDescription = "Decrease size",
                            tint = onBgColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    // Size display
                    Text(
                        text = "${fontSizeSp.toInt()}px",
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = onBgColor
                        ),
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    // Increment `+`
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                val next = (fontSizeSp + 1f).coerceIn(11f, 32f)
                                onFontSizeChange(next)
                                onLineHeightChange(next * 1.45f)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Increase size",
                            tint = onBgColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                CompactSeparator(isDark)

                // `Paragraph ˅` / `H1 ˅` Dropdown Menu
                val headingLabel = when (activeStyles.headingLevel) {
                    1 -> "H1"
                    2 -> "H2"
                    3 -> "H3"
                    4 -> "H4"
                    5 -> "H5"
                    6 -> "H6"
                    else -> "Paragraph"
                }

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeStyles.headingLevel > 0) activeTint.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { showHeadingMenu = true }
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = headingLabel,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (activeStyles.headingLevel > 0) activeTint else activeTint
                            )
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = activeTint,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showHeadingMenu,
                        onDismissRequest = { showHeadingMenu = false }
                    ) {
                        val headings = listOf(
                            0 to "Paragraph",
                            1 to "H1",
                            2 to "H2",
                            3 to "H3",
                            4 to "H4",
                            5 to "H5",
                            6 to "H6"
                        )
                        headings.forEach { (level, name) ->
                            val isSelected = activeStyles.headingLevel == level
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = name,
                                            style = TextStyle(
                                                fontSize = if (level in 1..3) (16 - level).sp else 14.sp,
                                                fontWeight = if (level > 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) activeTint else onBgColor
                                            )
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = activeTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onToggleHeading(level)
                                    showHeadingMenu = false
                                }
                            )
                        }
                    }
                }

                CompactSeparator(isDark)

                // `Sans-serif ˅` Font Selector Dropdown Menu
                val currentFontName = KeepFontPalette.allFonts.firstOrNull {
                    it.key.equals(selectedFontKey, ignoreCase = true)
                }?.name ?: "Sans-serif"

                Box {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFontMenu = true }
                            .padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = currentFontName,
                            style = TextStyle(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = onBgColor
                            )
                        )
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = onBgColor.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showFontMenu,
                        onDismissRequest = { showFontMenu = false }
                    ) {
                        KeepFontPalette.allFonts.forEach { fontItem ->
                            val isSelected = fontItem.key.equals(selectedFontKey, ignoreCase = true)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = fontItem.name,
                                            style = TextStyle(
                                                fontFamily = fontItem.fontFamily,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) activeTint else onBgColor
                                            )
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = null,
                                                tint = activeTint,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    onFontChange(fontItem.key)
                                    showFontMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom rounded tile button inspired directly by Notesnook's dark tiles.
 */
@Composable
private fun NotesnookTileButton(
    onClick: () -> Unit,
    contentDescription: String,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    activeTint: Color = NotesnookGreen,
    content: @Composable () -> Unit
) {
    val tileBg = when {
        isActive -> if (isDark) TileBgDark else TileBgLight
        else -> Color.Transparent
    }
    val border = when {
        isActive -> BorderStroke(1.dp, activeTint.copy(alpha = 0.5f))
        else -> null
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(tileBg)
            .then(if (border != null) Modifier.border(border, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 3-column Numbered List Hierarchy Popup (Screenshot 1 from Notesnook)
 * Column 1: 1. 2. 3. / I. II. III.
 * Column 2: A. B. C. / i. ii. iii.
 * Column 3: a. b. c. / α. β. γ.
 */
@Composable
private fun NotesnookListHierarchyPopup(
    onSelectStyle: (prefix: String) -> Unit,
    isDark: Boolean
) {
    val bg = if (isDark) Color(0xFF1E2126) else Color(0xFFFFFFFF)
    val pillBg = if (isDark) Color(0xFF2C3038) else Color(0xFFE5E9F0)
    val textColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF212529)

    Surface(
        color = bg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isDark) Color(0xFF333842) else Color(0xFFD0D5DD)),
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .widthIn(min = 280.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: 1. 2. 3. | A. B. C. | a. b. c.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ListFormatPillColumn(
                    labels = listOf("1.", "2.", "3."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("1. ") },
                    modifier = Modifier.weight(1f)
                )
                ListFormatPillColumn(
                    labels = listOf("A.", "B.", "C."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("A. ") },
                    modifier = Modifier.weight(1f)
                )
                ListFormatPillColumn(
                    labels = listOf("a.", "b.", "c."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("a. ") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Row 2: I. II. III. | i. ii. iii. | α. β. γ.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ListFormatPillColumn(
                    labels = listOf("I.", "II.", "III."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("I. ") },
                    modifier = Modifier.weight(1f)
                )
                ListFormatPillColumn(
                    labels = listOf("i.", "ii.", "iii."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("i. ") },
                    modifier = Modifier.weight(1f)
                )
                ListFormatPillColumn(
                    labels = listOf("α.", "β.", "γ."),
                    pillBg = pillBg,
                    textColor = textColor,
                    onClick = { onSelectStyle("α. ") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ListFormatPillColumn(
    labels: List<String>,
    pillBg: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { lbl ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = lbl,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    ),
                    modifier = Modifier.width(18.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(pillBg)
                )
            }
        }
    }
}

@Composable
private fun CompactSeparator(isDark: Boolean) {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(20.dp)
            .background(if (isDark) Color(0xFF333842) else Color(0xFFD0D5DD))
    )
}
