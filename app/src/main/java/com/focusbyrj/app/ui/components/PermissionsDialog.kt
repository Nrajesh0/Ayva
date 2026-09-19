package com.focusbyrj.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.res.painterResource
import com.focusbyrj.app.R
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.ui.theme.MidnightBlack
import com.focusbyrj.app.ui.theme.TextSecondary
import com.focusbyrj.app.util.PermissionUtils

@Composable
fun SetupPermissionsDialog(
    hasUsageStats: Boolean,
    hasOverlay: Boolean,
    isBatteryUnrestricted: Boolean,
    hasNotifications: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showWhyBatteryDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "System Access",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "To help you stay on track and block distracting apps, Focus needs a few core permissions to work properly.",
                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                PermissionSetupCard(
                    number = "01",
                    title = "Usage Detection",
                    subtitle = "Detects when you open a distracting app so we can intercept it immediately.",
                    isGranted = hasUsageStats,
                    onAction = { PermissionUtils.requestUsageStatsPermission(context) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionSetupCard(
                    number = "02",
                    title = "Strict Enforcement",
                    subtitle = "Allows the focus shield to draw over blocked apps, preventing doom-scrolling.",
                    isGranted = hasOverlay,
                    onAction = { PermissionUtils.requestOverlayPermission(context) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                PermissionSetupCard(
                    number = "03",
                    title = "Background Persistence",
                    subtitle = "Prevents Android from killing your focus sessions to save battery.",
                    isGranted = isBatteryUnrestricted,
                    onAction = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                    onLearnMore = { showWhyBatteryDialog = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
                PermissionSetupCard(
                    number = "04",
                    title = "Notifications",
                    subtitle = "Allows us to alert you when restrictions begin or end.",
                    isGranted = hasNotifications,
                    onAction = { PermissionUtils.requestNotificationPermission(context) }
                )

                Spacer(modifier = Modifier.height(16.dp))
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 48.dp)
                ) {
                    val allGranted = hasUsageStats && hasOverlay && isBatteryUnrestricted && hasNotifications
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (allGranted) MaterialTheme.colorScheme.primary else Color.Transparent,
                            contentColor = if (allGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground
                        ),
                        border = if (!allGranted) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null,
                        shape = RoundedCornerShape(100.dp)
                    ) {
                        Text(
                            text = if (allGranted) "Enter Focus Mode" else "Skip Configuration",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
                        )
                    }
                }
            }
        }
    }

    if (showWhyBatteryDialog) {
        AlertDialog(
            modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
            onDismissRequest = { showWhyBatteryDialog = false },
            title = {
                Text("Background Persistence", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Column {
                    Text(
                        "If you don't allow background persistence, Android might kill the app to save battery, which will prematurely end your focus session.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Setting this to 'Unrestricted' ensures your focus blocks remain active and trigger exactly when they should.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWhyBatteryDialog = false
                        PermissionUtils.requestIgnoreBatteryOptimizations(context)
                    }
                ) {
                    Text("Set to Unrestricted", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWhyBatteryDialog = false }) {
                    Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun PermissionSetupCard(
    number: String,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onAction: () -> Unit,
    onLearnMore: (() -> Unit)? = null
) {
    val alpha = if (isGranted) 1f else 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isGranted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                if (isGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(16.dp)
            )
            .clickable(enabled = !isGranted) { onAction() }
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = number,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            ),
            color = if (isGranted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                lineHeight = 18.sp
            )
            if (onLearnMore != null && !isGranted) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Why is this required?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium.copy(
                        textDecoration = TextDecoration.Underline
                    ),
                    modifier = Modifier.clickable { onLearnMore() }
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        if (isGranted) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Granted",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(24.dp)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Grant",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
