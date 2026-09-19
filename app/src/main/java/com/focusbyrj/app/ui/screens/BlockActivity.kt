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

package com.focusbyrj.app.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import com.focusbyrj.app.ui.theme.MidnightBlack
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.WindowCompat
import com.focusbyrj.app.service.FocusExitTracker
import com.focusbyrj.app.ui.theme.*
import com.focusbyrj.app.util.FocusQuotes
import com.focusbyrj.app.util.TemporaryUnlockManager
import kotlinx.coroutines.delay

class BlockActivity : ComponentActivity() {
    private val closeReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.focusbyrj.app.CLOSE_BLOCK_SCREEN") {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val filter = android.content.IntentFilter("com.focusbyrj.app.CLOSE_BLOCK_SCREEN")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(closeReceiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(closeReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(closeReceiver)
        } catch (e: Exception) {}
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("focus_prefs", android.content.Context.MODE_PRIVATE)
        val secureRecents = prefs.getBoolean("secure_recents", true)
        if (secureRecents) {
            
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        val savedModeId = prefs.getString("overlay_theme_mode", "system") ?: "system"
        val systemDarkMode = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDarkMode = when (savedModeId) {
            "dark" -> true
            "light" -> false
            else -> systemDarkMode
        }
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        insetsController.isAppearanceLightStatusBars = !isDarkMode
        insetsController.isAppearanceLightNavigationBars = !isDarkMode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        
        enableEdgeToEdge()

        val packageName = intent.getStringExtra("package_name") ?: ""
        val quote = intent.getStringExtra("quote") ?: ""
        val mode = intent.getStringExtra("mode") ?: "HARD"

        setContent {
            FocusByRjTheme {
                BlockScreenContent(
                    packageName = packageName,
                    quote = quote,
                    mode = mode,
                    onExit = { goHome(packageName) },
                    onUnlock = {
                        val unlockMins = getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                            .getInt("soft_unlock_duration", 5)
                        com.focusbyrj.app.util.FocusEconomyManager.applySoftUnlockPenalty()
                        TemporaryUnlockManager.grantUnlock(this, packageName, unlockMins)
                        finish()
                    }
                )
            }
        }
    }

    private fun goHome(pkgName: String) {
        FocusExitTracker.notifyExited(pkgName)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            startActivity(homeIntent)
            finish()
        } catch (e: Exception) {
            // Silently ignore intent launch failure
        }
        moveTaskToBack(true)
        finish()
    }
}

@Composable
fun BlockScreenContent(
    packageName: String,
    quote: String,
    mode: String,
    onExit: () -> Unit,
    onUnlock: () -> Unit
) {
    val isHardMode = mode.equals("HARD", ignoreCase = true)
    val context = LocalContext.current
    
    val currentThemeMode by com.focusbyrj.app.util.AppThemeManager.themeModeFlow.collectAsState()
    val systemInDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkMode = currentThemeMode.isDarkTheme ?: systemInDark
    val bgColor = if (isDarkMode) Color(0xFF07090E) else Color(0xFFF8FAFC)
    val cardBgColor = if (isDarkMode) Color.White.copy(alpha = 0.03f) else Color.White
    val cardStrokeColor = if (isDarkMode) Color.White.copy(alpha = 0.1f) else Color(0xFFE2E8F0)
    val iconBgColor = if (isDarkMode) Color(0xFF141620) else Color(0xFFF1F5F9)
    val iconStrokeColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFE2E8F0)
    val primaryTextColor = if (isDarkMode) Color(0xFFCBD5E1).copy(alpha = 0.95f) else Color(0xFF1E293B)
    val secondaryTextColor = if (isDarkMode) Color(0xFFCBD5E1) else Color(0xFF475569)
    val tertiaryTextColor = if (isDarkMode) TextSecondary else Color(0xFF64748B)
    val outlineBtnTextColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val outlineBtnStrokeColor = if (isDarkMode) Color.White.copy(alpha = 0.15f) else Color(0xFFCBD5E1)
    val filledBtnBgColor = if (isDarkMode) Color.White else Color(0xFF0F172A)
    val filledBtnTextColor = if (isDarkMode) Color(0xFF08090E) else Color.White
    val timerProgressBarBg = if (isDarkMode) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)
    val timerProgressFg = if (isDarkMode) Color.White else Color(0xFF334155)

    var appLabel by remember(packageName) { mutableStateOf(packageName) }
    var appIconDrawable by remember(packageName) { mutableStateOf<Drawable?>(null) }
    var dominantAppColor by remember { mutableStateOf(Color(0xFF6366F1)) } 
    
    val displayedQuote = remember(quote) {
        FocusQuotes.getQuoteOrDefault(quote)
    }

    LaunchedEffect(packageName) {
        if (packageName.isNotBlank()) {
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                appLabel = pm.getApplicationLabel(info).toString()
                appIconDrawable = pm.getApplicationIcon(info)
            } catch (e: Exception) {
                appLabel = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            }
        }
    }

    LaunchedEffect(appIconDrawable) {
        appIconDrawable?.let { drawable ->
            try {
                val bitmap = drawable.toBitmap(128, 128)
                androidx.palette.graphics.Palette.from(bitmap).generate { palette ->
                    val rgb = palette?.dominantSwatch?.rgb ?: palette?.vibrantSwatch?.rgb ?: palette?.mutedSwatch?.rgb
                    if (rgb != null) {
                        dominantAppColor = Color(rgb)
                    }
                }
            } catch (e: Exception) {
                // Ignore extraction failures
            }
        }
    }

    val totalSoftLockSeconds = remember {
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            .getInt("soft_lock_duration", 10)
    }

    val unlockDurationMinutes = remember {
        context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            .getInt("soft_unlock_duration", 5)
    }

    var timeLeft by remember { mutableStateOf(if (isHardMode) 0 else totalSoftLockSeconds) }

    LaunchedEffect(isHardMode) {
        if (!isHardMode) {
            while (timeLeft > 0) {
                delay(1000L)
                timeLeft -= 1
            }
        }
    }

    BackHandler {
        onExit()
    }

    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val quoteScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quoteScale"
    )
    val quoteAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "quoteAlpha"
    )
    val iconOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconOffsetY"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(cardBgColor)
                    .border(
                        1.dp,
                        cardStrokeColor,
                        RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    
                    
                    Box(
                        modifier = Modifier
                            .offset(y = iconOffsetY.dp)
                            .size(96.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(dominantAppColor.copy(alpha = 0.25f))
                                .blur(24.dp)
                        )
                        
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(iconBgColor)
                                .border(1.dp, iconStrokeColor, RoundedCornerShape(22.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (appIconDrawable != null) {
                                Image(
                                    bitmap = appIconDrawable!!.toBitmap(100, 100).asImageBitmap(),
                                    contentDescription = appLabel,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = null,
                                    tint = if (isHardMode) AccentRose else secondaryTextColor,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = appLabel,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.2.sp
                        ),
                        color = primaryTextColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = if (isHardMode) "Focus Shielded" else "Mindful Pause",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isHardMode) AccentRose else secondaryTextColor,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            .scale(quoteScale)
                            .alpha(quoteAlpha),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "“$displayedQuote”",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 26.sp,
                                letterSpacing = 0.3.sp
                            ),
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    if (isHardMode) {
                        Text(
                            text = "This app is strictly locked to honor your focus commitment.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = tertiaryTextColor,
                            textAlign = TextAlign.Center,
                            lineHeight = 22.sp
                        )

                        Spacer(modifier = Modifier.height(26.dp))

                        Button(
                            onClick = onExit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AccentRose,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(28.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Text(
                                "Exit to Home",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        AnimatedContent(
                            targetState = timeLeft > 0,
                            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(300)) },
                            label = "TimerTransition"
                        ) { isCountingDown ->
                            if (isCountingDown) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val progress by animateFloatAsState(
                                        targetValue = if (totalSoftLockSeconds > 0) timeLeft.toFloat() / totalSoftLockSeconds.toFloat() else 0f,
                                        label = "ProgressAnimation"
                                    )

                                    Text(
                                        text = if (timeLeft < 10) "00:0$timeLeft" else "00:$timeLeft",
                                        style = MaterialTheme.typography.displayMedium.copy(
                                            fontWeight = FontWeight.Light,
                                            letterSpacing = 6.sp
                                        ),
                                        color = secondaryTextColor,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(16.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.6f)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(timerProgressBarBg)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progress)
                                                .fillMaxHeight()
                                                .background(timerProgressFg)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    Text(
                                        text = "Mindful pause in progress",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            letterSpacing = 1.sp
                                        ),
                                        color = tertiaryTextColor,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(32.dp))

                                    OutlinedButton(
                                        onClick = onExit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, outlineBtnStrokeColor),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = outlineBtnTextColor
                                        )
                                    ) {
                                        Text(
                                            "Exit to Home",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Pause Completed",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = secondaryTextColor,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = "Would you like to open $appLabel for $unlockDurationMinutes minutes or exit?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = tertiaryTextColor,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp
                                    )

                                    Spacer(modifier = Modifier.height(32.dp))

                                    Button(
                                        onClick = onUnlock,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(56.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = filledBtnBgColor,
                                            contentColor = filledBtnTextColor
                                        ),
                                        shape = RoundedCornerShape(28.dp),
                                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                    ) {
                                        Text(
                                            "Open for $unlockDurationMinutes Minutes",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.height(14.dp))

                                    OutlinedButton(
                                        onClick = onExit,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, outlineBtnStrokeColor),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color.Transparent,
                                            contentColor = outlineBtnTextColor
                                        )
                                    ) {
                                        Text(
                                            "Exit to Home",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Normal)
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
