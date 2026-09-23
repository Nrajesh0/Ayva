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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Share
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.focusbyrj.app.util.sync.VaultCryptoEngine
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusbyrj.app.data.note.ArchiveVaultSecurity
import kotlinx.coroutines.delay

/**
 * 2026 Minimal Calm: 6-dot passcode indicator with fluid spring scale and subtle harmonic shake.
 */
@Composable
fun ArchiveVaultPinDots(
    pinLength: Int,
    isError: Boolean,
    modifier: Modifier = Modifier,
    shakeTrigger: Int = 0
) {
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            shakeOffset.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 380
                    0f at 0
                    (-18f) at 45
                    18f at 90
                    (-12f) at 135
                    12f at 180
                    (-7f) at 225
                    7f at 270
                    (-3f) at 315
                    0f at 380
                }
            )
        }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.offset(x = shakeOffset.value.dp)
    ) {
        for (i in 0 until 6) {
            val isFilled = i < pinLength
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.0f else 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "dot_scale_$i"
            )

            val dotColor = when {
                isError -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> Color.Transparent
            }

            val borderColor = when {
                isError -> MaterialTheme.colorScheme.error
                isFilled -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
            }

            Box(
                modifier = Modifier
                    .size(13.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(
                        BorderStroke(1.8.dp, borderColor),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * 2026 Tactile Numeric Keypad: Soft-touch frosted circular surfaces with calm Nordic typography and letters.
 */
@Composable
fun ArchiveVaultNumericKeypad(
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onLeftActionClick: (() -> Unit)? = null,
    leftActionLabel: String? = null,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val keypadData = listOf(
        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        for (row in keypadData) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for ((digit, subtext) in row) {
                    KeypadCircleButton(
                        digit = digit,
                        subtext = subtext,
                        isEnabled = isEnabled,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDigitClick(digit)
                        }
                    )
                }
            }
        }

        // Bottom Row: Clear / Left Action, "0", Backspace
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Action
            if (onLeftActionClick != null && leftActionLabel != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onLeftActionClick()
                        }
                ) {
                    Text(
                        text = leftActionLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.2.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(66.dp))
            }

            // Zero Button
            KeypadCircleButton(
                digit = "0",
                subtext = "",
                isEnabled = isEnabled,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDigitClick("0")
                }
            )

            // Backspace Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        }
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 0.08f else 0.03f)
                        ),
                        CircleShape
                    )
                    .clickable(enabled = isEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBackspaceClick()
                    }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Backspace",
                    tint = if (isEnabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun KeypadCircleButton(
    digit: String,
    subtext: String,
    isEnabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(66.dp)
            .clip(CircleShape)
            .background(
                if (isEnabled) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = if (isEnabled) 0.08f else 0.03f)
                ),
                CircleShape
            )
            .clickable(
                enabled = isEnabled,
                onClick = onClick
            )
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = digit,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.3).sp
                ),
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)
                }
            )
            if (subtext.isNotEmpty()) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 1.2.sp
                    ),
                    color = if (isEnabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
                    }
                )
            }
        }
    }
}

/**
 * 2026 Modern M3 Bottom Sheet: Unlock Archive Vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultUnlockDialog(
    onDismiss: () -> Unit,
    onVerify: (String) -> ArchiveVaultSecurity.VerifyResult,
    onSuccess: () -> Unit,
    initialLockoutSeconds: Long = 0L,
    onEmergencyRecoveryClick: (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    var pin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    var lockoutSeconds by remember { mutableLongStateOf(initialLockoutSeconds) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(lockoutSeconds) {
        if (lockoutSeconds > 0L) {
            while (lockoutSeconds > 0L) {
                delay(1000L)
                lockoutSeconds -= 1L
            }
            errorMessage = null
            isError = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Shield,
                    contentDescription = "Secret Vault",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Archive Vault",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (lockoutSeconds > 0L) {
                    "Too many incorrect attempts"
                } else {
                    "Enter 6-digit passcode to unlock"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = if (lockoutSeconds > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            // PIN Indicator Dots
            ArchiveVaultPinDots(
                pinLength = pin.length,
                isError = isError,
                shakeTrigger = shakeTrigger
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Lockout Banner or Error Info
            if (lockoutSeconds > 0L) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.widthIn(max = 280.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.LockClock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Try again in ${lockoutSeconds}s",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            } else if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tactile Numeric Keypad
            ArchiveVaultNumericKeypad(
                isEnabled = lockoutSeconds == 0L,
                onDigitClick = { digit ->
                    if (pin.length < 6 && lockoutSeconds == 0L) {
                        val newPin = pin + digit
                        pin = newPin
                        errorMessage = null
                        isError = false

                        if (newPin.length == 6) {
                            when (val res = onVerify(newPin)) {
                                is ArchiveVaultSecurity.VerifyResult.Success -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSuccess()
                                }
                                is ArchiveVaultSecurity.VerifyResult.Incorrect -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    errorMessage = if (res.remainingAttempts > 0) {
                                        "Incorrect passcode (${res.remainingAttempts} left)"
                                    } else {
                                        "Incorrect passcode"
                                    }
                                    pin = ""
                                }
                                is ArchiveVaultSecurity.VerifyResult.LockedOut -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    lockoutSeconds = res.remainingSeconds
                                    pin = ""
                                }
                                is ArchiveVaultSecurity.VerifyResult.Error -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    errorMessage = res.message
                                    pin = ""
                                }
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                        errorMessage = null
                        isError = false
                    }
                },
                onLeftActionClick = {
                    pin = ""
                    errorMessage = null
                    isError = false
                },
                leftActionLabel = if (pin.isNotEmpty()) "Clear" else null
            )

            if (onEmergencyRecoveryClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onEmergencyRecoveryClick,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Forgot PIN? Use Emergency Phrase",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 2026 Modern M3 Bottom Sheet: First-Time Setup Flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultFirstTimeDialog(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onPasscodeSet: (String, List<String>?, Boolean) -> Unit
) {
    var step by remember { mutableStateOf(FirstTimeStep.INTRO) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        when (step) {
            FirstTimeStep.INTRO -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = "Secret Vault",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Secret Vault Setup",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Protect your archived notes with a secure 6-digit passcode and a 12-word emergency recovery phrase.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { step = FirstTimeStep.ENTER_PIN },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Set 6-Digit Passcode",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onSkip,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Skip for Now",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            FirstTimeStep.ENTER_PIN -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                step = FirstTimeStep.INTRO
                                firstPin = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Step 1 of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Create Passcode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Enter 6 digits for your vault",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ArchiveVaultPinDots(
                        pinLength = firstPin.length,
                        isError = false
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    ArchiveVaultNumericKeypad(
                        onDigitClick = { digit ->
                            if (firstPin.length < 6) {
                                val updated = firstPin + digit
                                firstPin = updated
                                if (updated.length == 6) {
                                    step = FirstTimeStep.CONFIRM_PIN
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (firstPin.isNotEmpty()) {
                                firstPin = firstPin.dropLast(1)
                            }
                        }
                    )
                }
            }

            FirstTimeStep.CONFIRM_PIN -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                step = FirstTimeStep.ENTER_PIN
                                confirmPin = ""
                                errorMessage = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Step 2 of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Confirm Passcode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Re-enter the 6 digits to confirm",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ArchiveVaultPinDots(
                        pinLength = confirmPin.length,
                        isError = isError,
                        shakeTrigger = shakeTrigger
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!errorMessage.isNullOrEmpty()) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ArchiveVaultNumericKeypad(
                        onDigitClick = { digit ->
                            if (confirmPin.length < 6) {
                                val updated = confirmPin + digit
                                confirmPin = updated
                                errorMessage = null
                                isError = false

                                if (updated.length == 6) {
                                    if (updated == firstPin) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        step = FirstTimeStep.SHOW_MNEMONIC
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isError = true
                                        shakeTrigger += 1
                                        errorMessage = "Passcodes do not match"
                                        confirmPin = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                                errorMessage = null
                                isError = false
                            }
                        }
                    )
                }
            }

            FirstTimeStep.SHOW_MNEMONIC -> {
                val mnemonicWords = remember { VaultCryptoEngine.generate12WordMnemonic() }
                var hasConfirmedBackup by remember { mutableStateOf(false) }
                var isCopied by remember { mutableStateOf(false) }
                val clipboardManager = LocalClipboardManager.current

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                step = FirstTimeStep.CONFIRM_PIN
                                confirmPin = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Step 3 of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Emergency Recovery Phrase",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Write down or copy these 12 words in order. If you ever forget your PIN, this phrase is the ONLY way to recover your vault without data loss.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // 12-Word Grid (4 rows x 3 cols)
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (row in 0 until 4) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    for (col in 0 until 3) {
                                        val idx = row * 3 + col
                                        val word = mnemonicWords.getOrElse(idx) { "" }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surface,
                                            border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${idx + 1}.",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = word,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(mnemonicWords.joinToString(" ")))
                            isCopied = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                    ) {
                        Icon(
                            imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isCopied) "Phrase Copied to Clipboard ✓" else "Copy Recovery Phrase",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { hasConfirmedBackup = !hasConfirmedBackup }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = hasConfirmedBackup,
                            onCheckedChange = { hasConfirmedBackup = it },
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "I have safely backed up this 12-word recovery phrase",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onPasscodeSet(firstPin, mnemonicWords, true)
                        },
                        enabled = hasConfirmedBackup,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Complete Vault Setup",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPasscodeSet(firstPin, mnemonicWords, false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Back Up Later in Vault Options",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Backward compatibility overloads for ArchiveVaultFirstTimeDialog.
 */
@Composable
fun ArchiveVaultFirstTimeDialog(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onPasscodeSet: (String, List<String>?) -> Unit
) {
    ArchiveVaultFirstTimeDialog(
        onDismiss = onDismiss,
        onSkip = onSkip,
        onPasscodeSet = { pin, words, _ -> onPasscodeSet(pin, words) }
    )
}

@Composable
fun ArchiveVaultFirstTimeDialog(
    onDismiss: () -> Unit,
    onSkip: () -> Unit,
    onPasscodeSet: (String) -> Unit
) {
    ArchiveVaultFirstTimeDialog(
        onDismiss = onDismiss,
        onSkip = onSkip,
        onPasscodeSet = { pin, _, _ -> onPasscodeSet(pin) }
    )
}

private enum class FirstTimeStep {
    INTRO,
    ENTER_PIN,
    CONFIRM_PIN,
    SHOW_MNEMONIC
}

private enum class RecoveryStep {
    ENTER_PHRASE,
    ENTER_NEW_PIN,
    CONFIRM_NEW_PIN
}

/**
 * 2026 Modern M3 Bottom Sheet: Emergency Vault Recovery using 12-Word BIP-39 phrase.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultMnemonicRecoveryDialog(
    onDismiss: () -> Unit,
    onRecover: (List<String>, String) -> Boolean,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(RecoveryStep.ENTER_PHRASE) }
    var phraseInput by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val parsedWords = remember(phraseInput) {
        phraseInput.trim().lowercase().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    }
    val isWordCountValid = parsedWords.size == 12
    val isChecksumValid = remember(parsedWords) {
        if (isWordCountValid) VaultCryptoEngine.validateMnemonic(parsedWords) else false
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        when (step) {
            RecoveryStep.ENTER_PHRASE -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = "Emergency Recovery",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Emergency Vault Recovery",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 19.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Enter your 12-word recovery phrase to safely reset your vault PIN and restore full access to your archived notes.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = phraseInput,
                        onValueChange = {
                            phraseInput = it
                            errorMessage = null
                            isError = false
                        },
                        placeholder = {
                            Text(
                                "word1 word2 word3 ... word12",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Live word validation status
                    val statusText = when {
                        phraseInput.isBlank() -> "Type or paste your 12 words separated by spaces"
                        !isWordCountValid -> "${parsedWords.size} of 12 words entered"
                        !isChecksumValid -> "⚠️ Checksum error or invalid BIP-39 word"
                        else -> "✓ Valid 12-Word BIP-39 Phrase"
                    }
                    val statusColor = when {
                        phraseInput.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        !isWordCountValid -> MaterialTheme.colorScheme.secondary
                        !isChecksumValid -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = statusColor
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            step = RecoveryStep.ENTER_NEW_PIN
                        },
                        enabled = isChecksumValid,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Next: Set New PIN",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            RecoveryStep.ENTER_NEW_PIN -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                step = RecoveryStep.ENTER_PHRASE
                                newPin = ""
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Step 2 of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Set New Passcode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Enter a new 6-digit passcode for your vault",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ArchiveVaultPinDots(
                        pinLength = newPin.length,
                        isError = false
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    ArchiveVaultNumericKeypad(
                        onDigitClick = { digit ->
                            if (newPin.length < 6) {
                                val updated = newPin + digit
                                newPin = updated
                                if (updated.length == 6) {
                                    step = RecoveryStep.CONFIRM_NEW_PIN
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (newPin.isNotEmpty()) {
                                newPin = newPin.dropLast(1)
                            }
                        }
                    )
                }
            }

            RecoveryStep.CONFIRM_NEW_PIN -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .navigationBarsPadding()
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                step = RecoveryStep.ENTER_NEW_PIN
                                confirmPin = ""
                                errorMessage = null
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Text(
                            text = "Step 3 of 3",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.size(32.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Confirm New Passcode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "Re-enter the 6 digits to confirm",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    ArchiveVaultPinDots(
                        pinLength = confirmPin.length,
                        isError = isError,
                        shakeTrigger = shakeTrigger
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!errorMessage.isNullOrEmpty()) {
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ArchiveVaultNumericKeypad(
                        onDigitClick = { digit ->
                            if (confirmPin.length < 6) {
                                val updated = confirmPin + digit
                                confirmPin = updated
                                errorMessage = null
                                isError = false

                                if (updated.length == 6) {
                                    if (updated == newPin) {
                                        val recovered = onRecover(parsedWords, updated)
                                        if (recovered) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onSuccess()
                                        } else {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isError = true
                                            shakeTrigger += 1
                                            errorMessage = "Recovery failed: phrase does not match stored vault"
                                            confirmPin = ""
                                        }
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isError = true
                                        shakeTrigger += 1
                                        errorMessage = "Passcodes do not match"
                                        confirmPin = ""
                                    }
                                }
                            }
                        },
                        onBackspaceClick = {
                            if (confirmPin.isNotEmpty()) {
                                confirmPin = confirmPin.dropLast(1)
                                errorMessage = null
                                isError = false
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * 2026 Modern M3 Bottom Sheet: Vault Options (Lock, Change PIN, Disable).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultSettingsDialog(
    vaultStatus: ArchiveVaultSecurity.VaultStatus,
    onDismiss: () -> Unit,
    onLockVault: () -> Unit,
    onChangePasscode: () -> Unit,
    onDisablePasscode: () -> Unit,
    onSetPasscode: () -> Unit,
    onEmergencyRecoveryClick: (() -> Unit)? = null,
    isRecoveryPhraseBackedUp: Boolean = true,
    onExportRecoveryPhrase: (() -> Unit)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Vault Options",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.3).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Vault Status Card
            val isEnabled = vaultStatus == ArchiveVaultSecurity.VaultStatus.ENABLED
            Surface(
                color = if (isEnabled) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(
                    1.dp,
                    if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            )
                    ) {
                        Icon(
                            imageVector = if (isEnabled) Icons.Outlined.Shield else Icons.Outlined.LockOpen,
                            contentDescription = null,
                            tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = if (isEnabled) "Passcode Active" else "Passcode Disabled",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isEnabled) "Encrypted with 6-digit PIN" else "Vault is open without PIN",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isEnabled) {
                // Option 1: Lock Vault Now
                CalmVaultActionRow(
                    icon = Icons.Outlined.Lock,
                    title = "Lock Vault",
                    subtitle = "Immediately lock and return to Notes",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onLockVault
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Option 2: Change Passcode
                CalmVaultActionRow(
                    icon = Icons.Outlined.Key,
                    title = "Change Passcode",
                    subtitle = "Update your 6-digit vault passcode",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onChangePasscode
                )

                if (onExportRecoveryPhrase != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Option 3: Export Recovery Phrase
                    CalmVaultActionRow(
                        icon = Icons.Outlined.Key,
                        title = "Export Recovery Phrase",
                        subtitle = if (isRecoveryPhraseBackedUp) {
                            "View and export your 12-word recovery phrase"
                        } else {
                            "Action needed: View & back up your 12 words"
                        },
                        iconTint = if (isRecoveryPhraseBackedUp) MaterialTheme.colorScheme.primary else Color(0xFFE5A93C),
                        onClick = onExportRecoveryPhrase
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )

                // Option 4: Turn Off Passcode
                CalmVaultActionRow(
                    icon = Icons.Outlined.LockOpen,
                    title = "Turn Off Passcode",
                    subtitle = "Remove passcode protection from Archive",
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = onDisablePasscode
                )

                if (onEmergencyRecoveryClick != null) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Option 5: Emergency Phrase Recovery
                    CalmVaultActionRow(
                        icon = Icons.Outlined.Key,
                        title = "Emergency Phrase Recovery",
                        subtitle = "Recover vault using your 12-word phrase",
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = onEmergencyRecoveryClick
                    )
                }
            } else {
                // Option 1: Set Passcode
                CalmVaultActionRow(
                    icon = Icons.Outlined.Shield,
                    title = "Set 6-Digit Passcode",
                    subtitle = "Protect Archive with encrypted PIN",
                    iconTint = MaterialTheme.colorScheme.primary,
                    onClick = onSetPasscode
                )
            }
        }
    }
}

/**
 * 2026 Modern M3 Bottom Sheet: View and Export 12-Word Recovery Phrase.
 * Allows authenticated users who unlocked their archive vault to view, copy, share,
 * and mark their emergency recovery phrase as backed up.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultExportPhraseDialog(
    phraseWords: List<String>,
    isBackedUp: Boolean,
    onDismiss: () -> Unit,
    onMarkBackedUp: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var isCopied by remember { mutableStateOf(false) }
    var hasConfirmedBackup by remember { mutableStateOf(isBackedUp) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        if (isBackedUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else Color(0xFFE5A93C).copy(alpha = 0.15f)
                    )
            ) {
                Icon(
                    imageVector = Icons.Outlined.Key,
                    contentDescription = null,
                    tint = if (isBackedUp) MaterialTheme.colorScheme.primary else Color(0xFFE5A93C),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Vault Recovery Phrase",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "These 12 words are the ONLY key to recovering your archived notes if you forget your PIN.",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Status Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isBackedUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color(0xFFE5A93C).copy(alpha = 0.12f),
                border = BorderStroke(
                    1.dp,
                    if (isBackedUp) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else Color(0xFFE5A93C).copy(alpha = 0.35f)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = if (isBackedUp) Icons.Outlined.Check else Icons.Outlined.LockClock,
                        contentDescription = null,
                        tint = if (isBackedUp) MaterialTheme.colorScheme.primary else Color(0xFFE5A93C),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBackedUp) "Recovery Phrase Backed Up ✓" else "Action Needed: Phrase Not Backed Up",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isBackedUp) MaterialTheme.colorScheme.primary else Color(0xFFE5A93C)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 12-Word Grid (4 rows x 3 cols)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in 0 until 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0 until 3) {
                                val idx = row * 3 + col
                                val word = phraseWords.getOrElse(idx) { "" }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 6.dp, horizontal = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${idx + 1}.",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = word,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons: Copy & Share
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(phraseWords.joinToString(" ")))
                        isCopied = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.Key,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopied) "Copied ✓" else "Copy Phrase",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Secret Vault Recovery Phrase")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Focus Secret Vault Recovery Phrase:\n\n${phraseWords.joinToString(" ")}\n\n⚠️ Store this phrase safely offline. Never share it with anyone."
                            )
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Export Recovery Phrase"))
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Share / Export",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Security Warning Callout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🔒 Never share these 12 words with anyone. Anyone with this phrase can decrypt your archived notes.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (!isBackedUp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { hasConfirmedBackup = !hasConfirmedBackup }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasConfirmedBackup,
                        onCheckedChange = { hasConfirmedBackup = it },
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "I have safely written down or stored this phrase",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onMarkBackedUp()
                    },
                    enabled = hasConfirmedBackup,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Mark as Backed Up",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalmVaultActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.10f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 2026 Modern M3 Bottom Sheet: Change Passcode Flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultChangePasscodeDialog(
    onDismiss: () -> Unit,
    onVerifyCurrentPin: (String) -> ArchiveVaultSecurity.VerifyResult,
    onSaveNewPin: (String) -> Boolean,
    onSuccess: () -> Unit
) {
    var step by remember { mutableStateOf(ChangePinStep.VERIFY_OLD) }
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            // Top row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step != ChangePinStep.VERIFY_OLD) {
                    IconButton(
                        onClick = {
                            when (step) {
                                ChangePinStep.ENTER_NEW -> {
                                    step = ChangePinStep.VERIFY_OLD
                                    currentPin = ""
                                    newPin = ""
                                    errorMessage = null
                                }
                                ChangePinStep.CONFIRM_NEW -> {
                                    step = ChangePinStep.ENTER_NEW
                                    confirmPin = ""
                                    errorMessage = null
                                }
                                else -> {}
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(32.dp))
                }

                Text(
                    text = when (step) {
                        ChangePinStep.VERIFY_OLD -> "Verify Identity"
                        ChangePinStep.ENTER_NEW -> "Step 1 of 2"
                        ChangePinStep.CONFIRM_NEW -> "Step 2 of 2"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = when (step) {
                    ChangePinStep.VERIFY_OLD -> "Verify Current Passcode"
                    ChangePinStep.ENTER_NEW -> "New Passcode"
                    ChangePinStep.CONFIRM_NEW -> "Confirm New Passcode"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = when (step) {
                    ChangePinStep.VERIFY_OLD -> "Enter your existing 6-digit passcode"
                    ChangePinStep.ENTER_NEW -> "Choose a new 6-digit passcode"
                    ChangePinStep.CONFIRM_NEW -> "Re-enter the new 6 digits to confirm"
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(18.dp))

            val activePin = when (step) {
                ChangePinStep.VERIFY_OLD -> currentPin
                ChangePinStep.ENTER_NEW -> newPin
                ChangePinStep.CONFIRM_NEW -> confirmPin
            }

            ArchiveVaultPinDots(
                pinLength = activePin.length,
                isError = isError,
                shakeTrigger = shakeTrigger
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            ArchiveVaultNumericKeypad(
                onDigitClick = { digit ->
                    when (step) {
                        ChangePinStep.VERIFY_OLD -> {
                            if (currentPin.length < 6) {
                                val updated = currentPin + digit
                                currentPin = updated
                                errorMessage = null
                                isError = false
                                if (updated.length == 6) {
                                    when (val res = onVerifyCurrentPin(updated)) {
                                        is ArchiveVaultSecurity.VerifyResult.Success -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            step = ChangePinStep.ENTER_NEW
                                        }
                                        is ArchiveVaultSecurity.VerifyResult.Incorrect -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isError = true
                                            shakeTrigger += 1
                                            errorMessage = "Incorrect passcode (${res.remainingAttempts} left)"
                                            currentPin = ""
                                        }
                                        is ArchiveVaultSecurity.VerifyResult.LockedOut -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isError = true
                                            shakeTrigger += 1
                                            errorMessage = "Lockout active. Try again in ${res.remainingSeconds}s"
                                            currentPin = ""
                                        }
                                        is ArchiveVaultSecurity.VerifyResult.Error -> {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isError = true
                                            shakeTrigger += 1
                                            errorMessage = res.message
                                            currentPin = ""
                                        }
                                    }
                                }
                            }
                        }
                        ChangePinStep.ENTER_NEW -> {
                            if (newPin.length < 6) {
                                val updated = newPin + digit
                                newPin = updated
                                errorMessage = null
                                isError = false
                                if (updated.length == 6) {
                                    step = ChangePinStep.CONFIRM_NEW
                                }
                            }
                        }
                        ChangePinStep.CONFIRM_NEW -> {
                            if (confirmPin.length < 6) {
                                val updated = confirmPin + digit
                                confirmPin = updated
                                errorMessage = null
                                isError = false
                                if (updated.length == 6) {
                                    if (updated == newPin) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (onSaveNewPin(updated)) {
                                            onSuccess()
                                        } else {
                                            errorMessage = "Failed to update passcode"
                                        }
                                    } else {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isError = true
                                        shakeTrigger += 1
                                        errorMessage = "Passcodes do not match"
                                        confirmPin = ""
                                    }
                                }
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    when (step) {
                        ChangePinStep.VERIFY_OLD -> if (currentPin.isNotEmpty()) currentPin = currentPin.dropLast(1)
                        ChangePinStep.ENTER_NEW -> if (newPin.isNotEmpty()) newPin = newPin.dropLast(1)
                        ChangePinStep.CONFIRM_NEW -> if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    }
                    errorMessage = null
                    isError = false
                }
            )
        }
    }
}

private enum class ChangePinStep {
    VERIFY_OLD,
    ENTER_NEW,
    CONFIRM_NEW
}

/**
 * 2026 Modern M3 Bottom Sheet: Disable Passcode Confirmation Flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveVaultDisableConfirmDialog(
    onDismiss: () -> Unit,
    onVerifyCurrentPin: (String) -> ArchiveVaultSecurity.VerifyResult,
    onDisableConfirmed: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableStateOf(0) }
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockOpen,
                    contentDescription = "Disable protection",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Turn Off Vault Passcode",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    letterSpacing = (-0.3).sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "Enter current 6-digit passcode to remove protection",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            ArchiveVaultPinDots(
                pinLength = currentPin.length,
                isError = isError,
                shakeTrigger = shakeTrigger
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (!errorMessage.isNullOrEmpty()) {
                Text(
                    text = errorMessage ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            ArchiveVaultNumericKeypad(
                onDigitClick = { digit ->
                    if (currentPin.length < 6) {
                        val updated = currentPin + digit
                        currentPin = updated
                        errorMessage = null
                        isError = false
                        if (updated.length == 6) {
                            when (val res = onVerifyCurrentPin(updated)) {
                                is ArchiveVaultSecurity.VerifyResult.Success -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDisableConfirmed()
                                }
                                is ArchiveVaultSecurity.VerifyResult.Incorrect -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    errorMessage = "Incorrect passcode (${res.remainingAttempts} left)"
                                    currentPin = ""
                                }
                                is ArchiveVaultSecurity.VerifyResult.LockedOut -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    errorMessage = "Lockout active. Try again in ${res.remainingSeconds}s"
                                    currentPin = ""
                                }
                                is ArchiveVaultSecurity.VerifyResult.Error -> {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isError = true
                                    shakeTrigger += 1
                                    errorMessage = res.message
                                    currentPin = ""
                                }
                            }
                        }
                    }
                },
                onBackspaceClick = {
                    if (currentPin.isNotEmpty()) {
                        currentPin = currentPin.dropLast(1)
                        errorMessage = null
                        isError = false
                    }
                }
            )
        }
    }
}
