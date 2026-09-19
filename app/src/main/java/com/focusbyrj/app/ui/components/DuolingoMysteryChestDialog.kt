package com.focusbyrj.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.GamificationHaptics
import com.focusbyrj.app.util.MysteryReward
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
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

private data class AmbientParticle(
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float,
    val speed: Float
)

private data class SparkBurstParticle(
    val startX: Float,
    val startY: Float,
    val angle: Double,
    val distance: Float,
    val color: Color,
    val size: Float
)

@Composable
fun DuolingoMysteryChestDialog(
    initialRarity: ChestRarity = ChestRarity.COMMON,
    onDismiss: () -> Unit,
    onClaimed: ((MysteryReward) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isRevealed by remember { mutableStateOf(false) }
    var isOpening by remember { mutableStateOf(false) }
    var claimedReward by remember { mutableStateOf<MysteryReward?>(null) }
    
    var wonRarity by remember { mutableStateOf(initialRarity) }
    var displayedRarity by remember { mutableStateOf(initialRarity) }

    // Ambient floating particles
    val ambientParticles = remember {
        List(28) {
            AmbientParticle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                size = Random.nextFloat() * 3f + 1.5f,
                alpha = Random.nextFloat() * 0.5f + 0.2f,
                speed = Random.nextFloat() * 0.0003f + 0.0001f
            )
        }
    }

    // Dynamic Spark burst particles generated on detonation
    val sparkParticles = remember(wonRarity) {
        val particleCount = when (wonRarity) {
            ChestRarity.LEGENDARY -> 70
            ChestRarity.EPIC -> 50
            ChestRarity.RARE -> 38
            ChestRarity.COMMON -> 28
        }
        List(particleCount) {
            val angle = Random.nextDouble(0.0, Math.PI * 2)
            val dist = Random.nextFloat() * 240f + 60f
            val col = when (wonRarity) {
                ChestRarity.LEGENDARY -> listOf(Color(0xFFFFD700), Color(0xFFFBBF24), Color(0xFFFFF59D), Color.White).random()
                ChestRarity.EPIC -> listOf(Color(0xFFE879F9), Color(0xFFC084FC), Color(0xFFF472B6), Color.White).random()
                ChestRarity.RARE -> listOf(Color(0xFF818CF8), Color(0xFF4F46E5), Color(0xFFA5B4FC), Color.White).random()
                ChestRarity.COMMON -> listOf(Color(0xFF00F0FF), Color(0xFF0284C7), Color(0xFF38BDF8), Color.White).random()
            }
            SparkBurstParticle(
                startX = 0.5f,
                startY = 0.45f,
                angle = angle,
                distance = dist,
                color = col,
                size = Random.nextFloat() * 4f + 2f
            )
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "vault_ambient")
    val hoverOffset by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 10f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "hover"
    )

    val gyroRotX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (wonRarity == ChestRarity.LEGENDARY) 4500 else 7500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gyroX"
    )

    val gyroRotY by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (wonRarity == ChestRarity.LEGENDARY) 5500 else 9500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gyroY"
    )

    val rayRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ray_rot"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Unboxing physics
    val boxScale = remember { Animatable(1f) }
    val lidElevation = remember { Animatable(0f) }
    val flashAlpha = remember { Animatable(0f) }
    val shockwaveProgress = remember { Animatable(0f) }
    val shockwaveAlpha = remember { Animatable(0f) }
    val burstProgress = remember { Animatable(0f) }
    val cardScale = remember { Animatable(0.8f) }
    val cardOffsetY = remember { Animatable(80f) }

    fun openVault() {
        if (isOpening || isRevealed) return
        isOpening = true

        coroutineScope.launch {
            GamificationHaptics.playLight(context)

            val finalRarity = ChestRarity.rollRarity(initialRarity)
            wonRarity = finalRarity

            // 1. Anticipation squash & energy resonance
            launch {
                boxScale.animateTo(
                    targetValue = 0.9f,
                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                )
            }
            
            launch {
                val allTiers = listOf(ChestRarity.COMMON, ChestRarity.RARE, ChestRarity.EPIC, ChestRarity.LEGENDARY)
                var elapsed = 0
                var cycleIdx = 0
                while (elapsed < 500) {
                    if (elapsed % 80 == 0) {
                        displayedRarity = allTiers[cycleIdx % allTiers.size]
                        cycleIdx++
                        GamificationHaptics.playLight(context)
                    }
                    delay(40)
                    elapsed += 40
                }
                displayedRarity = finalRarity
            }
            
            delay(520)

            // 2. Detonation, Lid Burst & Shockwave
            if (finalRarity == ChestRarity.LEGENDARY) {
                GamificationHaptics.playCelebration(context)
            } else {
                GamificationHaptics.playSuccess(context)
            }

            launch {
                lidElevation.animateTo(1f, animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow))
                boxScale.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
            }

            launch {
                flashAlpha.snapTo(0.85f)
                flashAlpha.animateTo(0f, animationSpec = tween(600, easing = FastOutSlowInEasing))
            }

            launch {
                shockwaveProgress.snapTo(0f)
                shockwaveAlpha.snapTo(1f)
                shockwaveProgress.animateTo(1f, animationSpec = tween(650, easing = FastOutSlowInEasing))
                shockwaveAlpha.animateTo(0f, animationSpec = tween(350, easing = LinearEasing))
            }

            launch {
                burstProgress.snapTo(0f)
                burstProgress.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
            }

            // 3. Rewards Calculation
            val currentFreezes = AptitudeManager.getStreakFreezesCount()
            val (baseXp, baseGold, freezeChance, maxFreezes) = when (finalRarity) {
                ChestRarity.COMMON -> {
                    val xp = Random.nextInt(120, 260)
                    val gold = Random.nextInt(150, 320)
                    Tuple4(xp, gold, 25, 1)
                }
                ChestRarity.RARE -> {
                    val xp = Random.nextInt(320, 650)
                    val gold = Random.nextInt(400, 800)
                    Tuple4(xp, gold, 60, 1)
                }
                ChestRarity.EPIC -> {
                    val xp = Random.nextInt(800, 1600)
                    val gold = Random.nextInt(1000, 2200)
                    Tuple4(xp, gold, 100, 1)
                }
                ChestRarity.LEGENDARY -> {
                    val xp = Random.nextInt(2500, 5000)
                    val gold = Random.nextInt(3000, 6000)
                    Tuple4(xp, gold, 100, 2)
                }
            }

            var freezesAwarded = 0
            var bonusGold = 0
            val wonFreeze = Random.nextInt(1, 101) <= freezeChance

            if (wonFreeze) {
                val availableSlots = (3 - currentFreezes).coerceAtLeast(0)
                if (availableSlots >= maxFreezes) {
                    freezesAwarded = maxFreezes
                    AptitudeManager.addStreakFreezes(freezesAwarded)
                } else if (availableSlots > 0) {
                    freezesAwarded = availableSlots
                    AptitudeManager.addStreakFreezes(freezesAwarded)
                    val overflow = maxFreezes - availableSlots
                    bonusGold = overflow * when (finalRarity) {
                        ChestRarity.LEGENDARY -> 300
                        ChestRarity.EPIC -> 250
                        ChestRarity.RARE -> 175
                        ChestRarity.COMMON -> 100
                    }
                } else {
                    bonusGold = maxFreezes * when (finalRarity) {
                        ChestRarity.LEGENDARY -> 300
                        ChestRarity.EPIC -> 250
                        ChestRarity.RARE -> 175
                        ChestRarity.COMMON -> 100
                    }
                }
            }

            val totalGold = baseGold + bonusGold
            FocusEconomyManager.addExactRewards(exactXp = baseXp, exactGold = totalGold)
            DailyQuestManager.claimMysteryChest()

            val reward = MysteryReward(
                xp = baseXp,
                gold = totalGold,
                streakFreezeAwarded = freezesAwarded > 0,
                bonusGoldInsteadOfFreeze = bonusGold,
                rarityTitle = finalRarity.title,
                freezesCount = if (freezesAwarded > 0) freezesAwarded else 1
            )
            claimedReward = reward

            delay(200)
            isRevealed = true

            // 4. Smooth Card Entrance with Spring Physics
            launch {
                cardScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
            launch {
                cardOffsetY.animateTo(0f, animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (isRevealed) {
                claimedReward?.let { onClaimed?.invoke(it) }
                onDismiss()
            } else if (!isOpening) {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val activeRarity = if (isOpening || isRevealed) wonRarity else displayedRarity

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            activeRarity.bgTopColor,
                            activeRarity.bgBottomColor,
                            Color(0xFF030712)
                        ),
                        center = Offset(200f, 300f),
                        radius = 1200f
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (!isOpening && !isRevealed) {
                            openVault()
                        }
                    }
                )
        ) {
            // -----------------------------------------------------------------
            // 0. TIER-SPECIFIC BACKGROUND PHENOMENA (God Rays / Aurora / Nebulas)
            // -----------------------------------------------------------------
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val cy = h * 0.42f

                // Ambient drifting background stars
                ambientParticles.forEach { p ->
                    val curY = ((p.y + p.speed * System.currentTimeMillis()) % 1f) * h
                    val curX = p.x * w
                    drawCircle(
                        color = Color.White.copy(alpha = p.alpha),
                        radius = p.size.dp.toPx(),
                        center = Offset(curX, curY)
                    )
                }

                // Tier-Specific Luxury Backdrops
                when (activeRarity) {
                    ChestRarity.LEGENDARY -> {
                        rotate(degrees = rayRotation, pivot = Offset(cx, cy)) {
                            val rayCount = 12
                            for (i in 0 until rayCount) {
                                val rayAngle = (i * (360f / rayCount)) * (Math.PI / 180.0)
                                val rayPath = Path().apply {
                                    moveTo(cx, cy)
                                    val r1 = rayAngle - 0.12
                                    val r2 = rayAngle + 0.12
                                    val rayDist = w * 1.5f
                                    lineTo((cx + cos(r1) * rayDist).toFloat(), (cy + sin(r1) * rayDist).toFloat())
                                    lineTo((cx + cos(r2) * rayDist).toFloat(), (cy + sin(r2) * rayDist).toFloat())
                                    close()
                                }
                                drawPath(
                                    path = rayPath,
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color(0xFFFFD700).copy(alpha = 0.22f),
                                            Color(0xFFF59E0B).copy(alpha = 0.08f),
                                            Color.Transparent
                                        ),
                                        center = Offset(cx, cy),
                                        radius = w * 0.9f
                                    )
                                )
                            }
                        }
                    }
                    ChestRarity.EPIC -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFFE879F9).copy(alpha = 0.28f * corePulse),
                                    Color(0xFF7E22CE).copy(alpha = 0.15f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = w * 0.8f
                            ),
                            radius = w * 0.8f,
                            center = Offset(cx, cy)
                        )
                    }
                    ChestRarity.RARE -> {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color(0xFF818CF8).copy(alpha = 0.18f),
                                    Color.Transparent
                                )
                            ),
                            topLeft = Offset(0f, cy - h * 0.2f),
                            size = Size(w, h * 0.4f)
                        )
                    }
                    ChestRarity.COMMON -> {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00F0FF).copy(alpha = 0.16f),
                                    Color.Transparent
                                ),
                                center = Offset(cx, cy),
                                radius = w * 0.65f
                            ),
                            radius = w * 0.65f,
                            center = Offset(cx, cy)
                        )
                    }
                }

                // Shockwave on detonation
                if (shockwaveAlpha.value > 0f) {
                    val maxRadius = w * 0.95f
                    val currentRadius = maxRadius * shockwaveProgress.value
                    drawCircle(
                        color = activeRarity.glowColor.copy(alpha = shockwaveAlpha.value * 0.8f),
                        radius = currentRadius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 8.dp.toPx() * (1f - shockwaveProgress.value))
                    )
                }

                // Particle explosion burst on fracture
                if (burstProgress.value > 0f) {
                    sparkParticles.forEach { p ->
                        val currDist = p.distance * burstProgress.value
                        val px = (cx + cos(p.angle) * currDist).toFloat()
                        val py = (cy + sin(p.angle) * currDist).toFloat()
                        val pAlpha = (1f - burstProgress.value).coerceIn(0f, 1f)
                        drawCircle(
                            color = p.color.copy(alpha = pAlpha),
                            radius = p.size * (1f - burstProgress.value * 0.5f),
                            center = Offset(px, py)
                        )
                    }
                }
            }

            // Whiteout Flash on detonation
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(flashAlpha.value)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.White,
                                    activeRarity.primaryColor.copy(alpha = 0.6f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }

            // -----------------------------------------------------------------
            // STAGE 1: 3D ISOMETRIC QUANTUM VAULT (Interactive Chest)
            // -----------------------------------------------------------------
            if (!isRevealed) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Header Subtitle
                    Text(
                        text = if (isOpening) "⚡ DECRYPTING VAULT..." else "MYSTERY QUANTUM VAULT",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 2.4.sp,
                            fontSize = 11.5.sp
                        ),
                        color = activeRarity.primaryColor
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isOpening) "Rolling Odds & Tier Buffs" else "Tap To Unbox Core Rewards",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp,
                            fontSize = 22.sp,
                            textAlign = TextAlign.Center
                        ),
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dynamic Rarity & Odds Badge
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = activeRarity.primaryColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.2.dp, activeRarity.primaryColor.copy(alpha = 0.6f)),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(text = activeRarity.iconSymbol, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = activeRarity.badgeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    fontSize = 10.5.sp
                                ),
                                color = activeRarity.primaryColor
                            )
                        }
                    }

                    // 3D Vault Box Container with Smooth Physics Hover
                    Box(
                        modifier = Modifier
                            .offset(
                                y = (hoverOffset + if (isOpening) 8f else 0f).dp
                            )
                            .scale(boxScale.value)
                            .size(210.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height
                            val cx = w / 2
                            val cy = h / 2

                            // 1. Dynamic Ground Pedestal Shadow
                            val shadowScale = (1f - (hoverOffset / 20f)).coerceIn(0.7f, 1.3f)
                            drawOval(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        activeRarity.primaryColor.copy(alpha = 0.35f * shadowScale),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy + h * 0.48f),
                                    radius = w * 0.45f * shadowScale
                                ),
                                topLeft = Offset(cx - w * 0.38f * shadowScale, cy + h * 0.42f),
                                size = Size(w * 0.76f * shadowScale, h * 0.18f * shadowScale)
                            )

                            // 2. Outer Kinetic Gyro Rings
                            rotate(degrees = gyroRotX, pivot = Offset(cx, cy)) {
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            activeRarity.primaryColor.copy(alpha = 0.85f),
                                            Color.Transparent,
                                            activeRarity.secondaryColor.copy(alpha = 0.6f),
                                            activeRarity.primaryColor.copy(alpha = 0.85f)
                                        )
                                    ),
                                    topLeft = Offset(cx - w * 0.48f, cy - h * 0.26f),
                                    size = Size(w * 0.96f, h * 0.52f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }

                            rotate(degrees = gyroRotY, pivot = Offset(cx, cy)) {
                                drawOval(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(
                                            activeRarity.secondaryColor.copy(alpha = 0.8f),
                                            Color.Transparent,
                                            activeRarity.primaryColor.copy(alpha = 0.5f),
                                            activeRarity.secondaryColor.copy(alpha = 0.8f)
                                        )
                                    ),
                                    topLeft = Offset(cx - w * 0.28f, cy - h * 0.46f),
                                    size = Size(w * 0.56f, h * 0.92f),
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }

                            // 3. Central Ambient Flare
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        activeRarity.primaryColor.copy(alpha = 0.45f * corePulse),
                                        Color.Transparent
                                    ),
                                    center = Offset(cx, cy),
                                    radius = w * 0.48f
                                ),
                                radius = w * 0.48f,
                                center = Offset(cx, cy)
                            )

                            // 4. 3D Isometric Obsidian Vault Body
                            val lidOffsetPx = lidElevation.value * 35.dp.toPx()

                            val topPath = Path().apply {
                                moveTo(cx, cy - h * 0.38f - lidOffsetPx)
                                lineTo(cx + w * 0.38f, cy - h * 0.14f - lidOffsetPx)
                                lineTo(cx, cy + h * 0.10f - lidOffsetPx)
                                lineTo(cx - w * 0.38f, cy - h * 0.14f - lidOffsetPx)
                                close()
                            }

                            val leftPath = Path().apply {
                                moveTo(cx - w * 0.38f, cy - h * 0.14f)
                                lineTo(cx, cy + h * 0.10f)
                                lineTo(cx, cy + h * 0.44f)
                                lineTo(cx - w * 0.38f, cy + h * 0.20f)
                                close()
                            }

                            val rightPath = Path().apply {
                                moveTo(cx, cy + h * 0.10f)
                                lineTo(cx + w * 0.38f, cy - h * 0.14f)
                                lineTo(cx + w * 0.38f, cy + h * 0.20f)
                                lineTo(cx, cy + h * 0.44f)
                                close()
                            }

                            // Facet Gradient Fills
                            drawPath(
                                topPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(activeRarity.primaryColor, activeRarity.secondaryColor)
                                )
                            )
                            drawPath(
                                leftPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(activeRarity.secondaryColor, Color(0xFF0B132B))
                                )
                            )
                            drawPath(
                                rightPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF0F172A), Color(0xFF020617))
                                )
                            )

                            // High-Tech Cybernetic Wireframe Highlights
                            drawPath(topPath, Color.White.copy(alpha = 0.75f), style = Stroke(width = 1.8.dp.toPx()))
                            drawPath(leftPath, Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.4.dp.toPx()))
                            drawPath(rightPath, Color.White.copy(alpha = 0.4f), style = Stroke(width = 1.4.dp.toPx()))

                            // Laser Seam Lines
                            drawLine(
                                color = activeRarity.primaryColor,
                                start = Offset(cx, cy - h * 0.14f - lidOffsetPx),
                                end = Offset(cx, cy + h * 0.10f - lidOffsetPx),
                                strokeWidth = 2.dp.toPx()
                            )

                            // Glowing Reactor Node at center
                            drawCircle(
                                color = Color.White,
                                radius = 3.5.dp.toPx(),
                                center = Offset(cx, cy + h * 0.10f - lidOffsetPx)
                            )
                            drawCircle(
                                color = activeRarity.primaryColor,
                                radius = 8.dp.toPx() * corePulse,
                                center = Offset(cx, cy + h * 0.10f - lidOffsetPx),
                                style = Stroke(width = 1.8.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Bottom Call-to-action
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = if (isOpening) "DECRYPTING QUANTUM CORE..." else "TAP ANYWHERE TO UNBOX",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                fontSize = 13.5.sp
                            ),
                            color = if (isOpening) activeRarity.primaryColor else Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Common 55% • Rare 28% • Epic 13% • Legendary 4%",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.3.sp,
                                textAlign = TextAlign.Center
                            ),
                            color = Color(0xFF94A3B8)
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // STAGE 2: VAULT REVEALED - SCROLLABLE LUXURY GLASS REWARD PEDESTAL
            // -----------------------------------------------------------------
            AnimatedVisibility(
                visible = isRevealed,
                enter = fadeIn(animationSpec = tween(350)),
                exit = fadeOut(animationSpec = tween(150)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                claimedReward?.let { reward ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .padding(vertical = 16.dp)
                            .offset(y = cardOffsetY.value.dp)
                            .scale(cardScale.value),
                        shape = RoundedCornerShape(26.dp),
                        color = Color(0xFF0C1322),
                        border = BorderStroke(
                            1.5.dp,
                            Brush.sweepGradient(
                                colors = listOf(
                                    wonRarity.primaryColor.copy(alpha = 0.9f),
                                    wonRarity.secondaryColor.copy(alpha = 0.5f),
                                    Color.White.copy(alpha = 0.8f),
                                    wonRarity.primaryColor.copy(alpha = 0.9f)
                                )
                            )
                        ),
                        shadowElevation = 24.dp
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFF131D36),
                                            Color(0xFF090E1A)
                                        )
                                    )
                                )
                                .padding(horizontal = 20.dp, vertical = 22.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Decrypted Rarity Badge
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = wonRarity.primaryColor.copy(alpha = 0.15f),
                                    border = BorderStroke(1.2.dp, wonRarity.primaryColor.copy(alpha = 0.6f))
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    ) {
                                        Text(text = wonRarity.iconSymbol, fontSize = 13.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = wonRarity.badgeLabel,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.2.sp,
                                                fontSize = 10.5.sp
                                            ),
                                            color = wonRarity.primaryColor
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = if (wonRarity == ChestRarity.LEGENDARY) "Legendary Jackpot!" else "Rewards Claimed!",
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        fontWeight = FontWeight.Black,
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.5).sp,
                                        textAlign = TextAlign.Center
                                    ),
                                    color = if (wonRarity == ChestRarity.LEGENDARY) Color(0xFFFFD700) else Color.White
                                )

                                Spacer(modifier = Modifier.height(18.dp))

                                // Dual Stat Cards (XP & Gold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ModernRewardCard(
                                        modifier = Modifier.weight(1f),
                                        label = "XP GAINED",
                                        value = "+${reward.xp}",
                                        accentColor = Color(0xFF818CF8),
                                        iconEmoji = "⚡"
                                    )

                                    ModernRewardCard(
                                        modifier = Modifier.weight(1f),
                                        label = "GOLD COINS",
                                        value = "+${reward.gold}",
                                        accentColor = Color(0xFFFBBF24),
                                        iconEmoji = "🪙"
                                    )
                                }

                                // Streak Freeze Drop
                                if (reward.streakFreezeAwarded) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                text = if (reward.freezesCount > 1) "👑" else "🛡️",
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (reward.freezesCount > 1) "+${reward.freezesCount} STREAK FREEZES (JACKPOT!)" else "+1 STREAK FREEZE",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 0.8.sp,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = Color(0xFF34D399)
                                                )
                                                Text(
                                                    text = "Protects your streak from missed days",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                } else if (reward.bonusGoldInsteadOfFreeze > 0) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(text = "💎", style = MaterialTheme.typography.titleMedium)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "+${reward.bonusGoldInsteadOfFreeze} MAX VAULT BONUS",
                                                    style = MaterialTheme.typography.labelMedium.copy(
                                                        fontWeight = FontWeight.Black,
                                                        letterSpacing = 0.8.sp,
                                                        fontSize = 11.sp
                                                    ),
                                                    color = Color(0xFFFBBF24)
                                                )
                                                Text(
                                                    text = "Streak freezes full (3/3) - Converted to gold",
                                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                                    color = Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                // Claim & Continue Action Button
                                Button(
                                    onClick = {
                                        GamificationHaptics.playLight(context)
                                        claimedReward?.let { onClaimed?.invoke(it) }
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.Transparent
                                    ),
                                    contentPadding = PaddingValues()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        wonRarity.primaryColor,
                                                        wonRarity.secondaryColor
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "COLLECT ALL REWARDS",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontWeight = FontWeight.Black,
                                                letterSpacing = 1.sp,
                                                fontSize = 12.5.sp
                                            ),
                                            color = Color.White
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

private data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ModernRewardCard(
    label: String,
    value: String,
    accentColor: Color,
    iconEmoji: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F182A),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = iconEmoji, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                    fontSize = 18.sp
                ),
                color = accentColor
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = 9.5.sp
                ),
                color = Color(0xFF94A3B8)
            )
        }
    }
}
