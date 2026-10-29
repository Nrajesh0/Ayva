package com.focusbyrj.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.GamificationHaptics
import com.focusbyrj.app.util.MysteryReward
import com.focusbyrj.app.util.backup.MaintenanceGate
import kotlinx.coroutines.delay
import kotlin.random.Random

enum class ChestRarity(
    val title: String,
    val oddsPercentage: String,
    val badgeLabel: String,
    val iconSymbol: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val bgTopColor: Color,
    val bgBottomColor: Color,
    val glowColor: Color,
    val gemColor: Color
) {
    COMMON(
        title = "QUANTUM",
        oddsPercentage = "55%",
        badgeLabel = "COMMON • 55% DROP",
        iconSymbol = "💎",
        primaryColor = Color(0xFF00F0FF),
        secondaryColor = Color(0xFF0284C7),
        bgTopColor = Color(0xFF0C2A4D),
        bgBottomColor = Color(0xFF06101F),
        glowColor = Color(0xFF00F0FF),
        gemColor = Color(0xFF38BDF8)
    ),
    RARE(
        title = "AURORA",
        oddsPercentage = "28%",
        badgeLabel = "RARE • 28% DROP",
        iconSymbol = "🔮",
        primaryColor = Color(0xFF818CF8),
        secondaryColor = Color(0xFF4F46E5),
        bgTopColor = Color(0xFF1E1B4B),
        bgBottomColor = Color(0xFF0A0826),
        glowColor = Color(0xFF818CF8),
        gemColor = Color(0xFFA5B4FC)
    ),
    EPIC(
        title = "CELESTIAL",
        oddsPercentage = "13%",
        badgeLabel = "EPIC • 13% DROP",
        iconSymbol = "✨",
        primaryColor = Color(0xFFC084FC),
        secondaryColor = Color(0xFF7E22CE),
        bgTopColor = Color(0xFF3B0764),
        bgBottomColor = Color(0xFF150226),
        glowColor = Color(0xFFE879F9),
        gemColor = Color(0xFFF472B6)
    ),
    LEGENDARY(
        title = "SOLARIS",
        oddsPercentage = "4%",
        badgeLabel = "LEGENDARY • 4% JACKPOT",
        iconSymbol = "👑",
        primaryColor = Color(0xFFFBBF24),
        secondaryColor = Color(0xFFD97706),
        bgTopColor = Color(0xFF451A03),
        bgBottomColor = Color(0xFF1C0A02),
        glowColor = Color(0xFFFFD700),
        gemColor = Color(0xFFFFEA00)
    );

    fun nextTier(): ChestRarity {
        return when (this) {
            COMMON -> RARE
            RARE -> EPIC
            EPIC -> LEGENDARY
            LEGENDARY -> LEGENDARY
        }
    }

    companion object {
        fun rollRarity(baseTier: ChestRarity? = null): ChestRarity {
            val roll = Random.nextInt(1, 101)
            return when (baseTier) {
                RARE -> {
                    when {
                        roll <= 50 -> RARE
                        roll <= 85 -> EPIC
                        else -> LEGENDARY
                    }
                }
                EPIC -> {
                    when {
                        roll <= 70 -> EPIC
                        else -> LEGENDARY
                    }
                }
                LEGENDARY -> LEGENDARY
                else -> {
                    when {
                        roll <= 55 -> COMMON
                        roll <= 83 -> RARE
                        roll <= 96 -> EPIC
                        else -> LEGENDARY
                    }
                }
            }
        }
    }
}

/**
 * Modern 3D Graphic used across card previews and badges
 */
@Composable
fun DuolingoChest3DGraphic(
    rarity: ChestRarity,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF161F33), Color(0xFF0A0F1A))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(listOf(rarity.primaryColor.copy(alpha = 0.6f), rarity.secondaryColor.copy(alpha = 0.2f))),
                RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.75f)) {
            val w = size.width
            val h = size.height
            val cx = w / 2
            val cy = h / 2

            // Background glow
            drawCircle(
                color = rarity.primaryColor.copy(alpha = 0.25f),
                radius = w * 0.45f,
                center = Offset(cx, cy)
            )

            // 3D Isometric cube facets
            val topPath = Path().apply {
                moveTo(cx, cy - h * 0.38f)
                lineTo(cx + w * 0.36f, cy - h * 0.14f)
                lineTo(cx, cy + h * 0.10f)
                lineTo(cx - w * 0.36f, cy - h * 0.14f)
                close()
            }

            val leftPath = Path().apply {
                moveTo(cx - w * 0.36f, cy - h * 0.14f)
                lineTo(cx, cy + h * 0.10f)
                lineTo(cx, cy + h * 0.42f)
                lineTo(cx - w * 0.36f, cy + h * 0.18f)
                close()
            }

            val rightPath = Path().apply {
                moveTo(cx, cy + h * 0.10f)
                lineTo(cx + w * 0.36f, cy - h * 0.14f)
                lineTo(cx + w * 0.36f, cy + h * 0.18f)
                lineTo(cx, cy + h * 0.42f)
                close()
            }

            drawPath(topPath, rarity.primaryColor)
            drawPath(leftPath, rarity.secondaryColor)
            drawPath(rightPath, Color(0xFF0F172A))

            // Metallic/Holographic Edges
            drawPath(topPath, Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.2.dp.toPx()))
            drawPath(leftPath, Color.White.copy(alpha = 0.3f), style = Stroke(width = 1.2.dp.toPx()))
            drawPath(rightPath, Color.White.copy(alpha = 0.3f), style = Stroke(width = 1.2.dp.toPx()))

            // Glowing Core Orb
            drawCircle(
                color = Color.White,
                radius = 2.5.dp.toPx(),
                center = Offset(cx, cy + h * 0.10f)
            )
            drawCircle(
                color = rarity.primaryColor,
                radius = 5.5.dp.toPx(),
                center = Offset(cx, cy + h * 0.10f),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }
    }
}

/** Smoke Paw portrait reveal. Rewards are resolved once, independently of animation/skip. */
@Composable
fun DuolingoMysteryChestDialog(
    initialRarity: ChestRarity = ChestRarity.COMMON,
    onDismiss: () -> Unit,
    onClaimed: ((MysteryReward) -> Unit)? = null
) {
    val context = LocalContext.current
    var rewardXp by rememberSaveable { mutableIntStateOf(-1) }
    var rewardGold by rememberSaveable { mutableIntStateOf(0) }
    var rewardFreezes by rememberSaveable { mutableIntStateOf(0) }
    var rewardBonus by rememberSaveable { mutableIntStateOf(0) }
    var rarityName by rememberSaveable { mutableStateOf(initialRarity.name) }
    var openedAt by rememberSaveable { mutableLongStateOf(0L) }
    var revealed by rememberSaveable { mutableStateOf(false) }
    var delivered by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sceneReady by remember { mutableStateOf(false) }
    var sceneFailed by remember { mutableStateOf(false) }
    val busy by MaintenanceGate.busy.collectAsState()
    val rarity = ChestRarity.valueOf(rarityName)
    val reward = if (rewardXp < 0) null else MysteryReward(
        xp = rewardXp, gold = rewardGold, streakFreezeAwarded = rewardFreezes > 0,
        bonusGoldInsteadOfFreeze = rewardBonus, rarityTitle = rarity.title,
        freezesCount = rewardFreezes.coerceAtLeast(1)
    )
    val reduceMotion = remember {
        if (Build.VERSION.SDK_INT >= 26) !ValueAnimator.areAnimatorsEnabled()
        else Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val opening = reward != null && !revealed
    fun finish() {
        if (delivered) return
        delivered = true
        reward?.let { onClaimed?.invoke(it) }
        onDismiss()
    }
    fun open() {
        if (reward != null || busy || delivered) return
        val granted = try { DailyQuestManager.openMysteryChest(initialRarity) }
        catch (_: android.database.sqlite.SQLiteException) {
            error = "Data maintenance is running. Please try again in a moment."
            return
        }
        if (granted == null) {
            error = "This mystery box has already been opened or isn't available yet."
            return
        }
        rarityName = ChestRarity.entries.first { it.title == granted.rarityTitle }.name
        rewardGold = granted.gold
        rewardFreezes = if (granted.streakFreezeAwarded) granted.freezesCount else 0
        rewardBonus = granted.bonusGoldInsteadOfFreeze
        rewardXp = granted.xp
        openedAt = SystemClock.elapsedRealtime()
        error = null
        GamificationHaptics.playLight(context)
        if (reduceMotion || sceneFailed) revealed = true
    }
    LaunchedEffect(opening, sceneReady, sceneFailed, reduceMotion) {
        if (opening && (sceneReady || sceneFailed)) {
            if (!reduceMotion && !sceneFailed) {
                val remaining = SmokePawMotion.durationMs(rarity) -
                    (SystemClock.elapsedRealtime() - openedAt).coerceAtLeast(0L)
                delay(remaining.coerceAtLeast(0L))
            }
            revealed = true
            if (rarity == ChestRarity.LEGENDARY) GamificationHaptics.playCelebration(context)
            else GamificationHaptics.playSuccess(context)
        }
    }
    // Renderer failures must never trap an already-granted reward behind an animation.
    LaunchedEffect(opening, sceneReady) {
        if (opening && !sceneReady) { delay(2500); sceneFailed = true }
    }
    Dialog(
        onDismissRequest = { if (!opening) { if (reward != null) finish() else onDismiss() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color(0xFF0C1015))
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("run", color = Color(0xFFEEDFC9), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (reward != null) finish() else onDismiss() }, enabled = !opening) {
                    Text("Close", color = if (opening) Color(0xFF55545B) else Color(0xFFB7ADA6))
                }
            }
            Text(
                text = "${rarity.name} / ${rarity.title}", color = rarity.primaryColor,
                fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                if (revealed) "A little victory." else "A little mystery.",
                color = Color(0xFFF1E2CC), style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (!sceneFailed) {
                    SmokePawChestScene(
                        rarity = rarity,
                        mode = if (revealed) 2 else if (opening) 1 else 0,
                        elapsedMs = if (opening) (SystemClock.elapsedRealtime() - openedAt).coerceAtLeast(0L) else 0L,
                        reduceMotion = reduceMotion,
                        onReady = { sceneReady = true }, onFailure = { sceneFailed = true },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    DuolingoChest3DGraphic(rarity, Modifier.size(180.dp))
                }
            }
            Column(
                Modifier.fillMaxWidth().height(120.dp).verticalScroll(rememberScrollState())
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (revealed && reward != null) {
                    Text("REWARD UNLOCKED", color = rarity.primaryColor, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text("+${reward.xp} XP", color = Color(0xFFF1DFC4), fontSize = 32.sp, fontWeight = FontWeight.Medium)
                    Text("+${reward.gold} coins", color = Color(0xFFC8B29F), fontSize = 16.sp)
                    if (reward.streakFreezeAwarded) Text(
                        "+${reward.freezesCount} streak freeze${if (reward.freezesCount > 1) "s" else ""}",
                        color = Color(0xFF9BD7CB), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                    if (reward.bonusGoldInsteadOfFreeze > 0) Text(
                        "Includes ${reward.bonusGoldInsteadOfFreeze} bonus coins for full freeze slots",
                        color = Color(0xFFB7ADA6), fontSize = 12.sp, textAlign = TextAlign.Center
                    )
                } else Text(
                    if (opening) "Smoke. Scarf. One clean strike." else "A small surprise is waiting.",
                    color = Color(0xFFB5A69D), fontSize = 13.sp, textAlign = TextAlign.Center
                )
                error?.let { Text(it, color = Color(0xFFFFAAA5), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) }
            }
            Button(
                onClick = { if (revealed) finish() else open() },
                enabled = !opening && !busy && !delivered,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp).heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFECDED0), contentColor = Color(0xFF1C1318))
            ) { Text(if (revealed) "Collect reward" else if (opening) "Opening…" else "Open mystery", fontWeight = FontWeight.Bold) }
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                if (opening) TextButton(onClick = { revealed = true }) { Text("Skip animation", color = Color(0xFFB3A59F), fontSize = 12.sp) }
            }
        }
    }
}
