package com.focusbyrj.app.ui.speech

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

/** Respect system reduced-motion changes in both the recorder and simulated previews. */
@Composable
internal fun rememberVoiceMotionAllowed(): Boolean {
    val context = LocalContext.current
    var motion by remember { mutableStateOf(true) }
    DisposableEffect(context) {
        fun readMotion() { motion = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }.getOrDefault(true) }
        readMotion()
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = readMotion()
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return motion
}

/** One bounded native Canvas, no assets/downloads/bitmaps. Only the visible style gets a frame loop. */
@Composable
fun VoiceVisualization(style: VoiceStyle, amplitude: Float, active: Boolean, modifier: Modifier = Modifier) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val motion = rememberVoiceMotionAllowed()
    DisposableEffect(lifecycle) {
        val listener = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        lifecycle.addObserver(listener)
        onDispose { lifecycle.removeObserver(listener) }
    }
    val running = active && foreground && motion
    val level = remember { mutableFloatStateOf(0f) }
    val phase = remember { mutableFloatStateOf(0f) }
    val target = if (running) amplitude.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f else 0f
    val latestTarget by rememberUpdatedState(target)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect // hold the current shape; the surface fades gently
        var previous = 0L
        while (isActive) {
            withFrameNanos { frame ->
                if (previous != 0L) {
                    val dt = ((frame - previous) / 1_000_000_000f).coerceIn(0f, .08f)
                    val smoothing = 1f - exp(-dt * if (latestTarget > level.floatValue) 13f else 5f)
                    level.floatValue += (latestTarget - level.floatValue) * smoothing
                    phase.floatValue = (phase.floatValue + dt * (.12f + level.floatValue * 1.7f)) % (2f * PI.toFloat() * 100f)
                }
                previous = frame
            }
            delay(16) // cap rendering near 30 fps; no animation job when paused or backgrounded
        }
    }
    val emphasis by animateFloatAsState(if (active) 1f else .38f, tween(250), label = "voice-state")
    val primary = MaterialTheme.colorScheme.primary
    val light = lerp(primary, MaterialTheme.colorScheme.onSurface, .24f)
    Canvas(modifier.semantics { contentDescription = style.title + if (active) ", listening visual" else ", inactive visual" }) {
        val t = phase.floatValue
        val a = level.floatValue
        val c = center
        // Give every style a clear common voice-driven expansion. Idle movement
        // stays restrained; RMS supplies the energy rather than a simulated beat.
        val r = min(size.width, size.height) * .34f * (.88f + a * .26f)
        val ink = primary.copy(alpha = emphasis)
        val shine = light.copy(alpha = emphasis)
        fun organic(radius: Float, lobes: Int, depth: Float, time: Float, flatten: Float = 1f): Path = Path().apply {
            repeat(121) { i ->
                val angle = i * 2f * PI.toFloat() / 120
                val rad = radius * (1 + depth * sin(lobes * angle + time) + depth * .38f * cos(3 * angle - time * .7f))
                val x = c.x + cos(angle) * rad
                val y = c.y + sin(angle) * rad * flatten
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }; close()
        }
        when (style) {
            VoiceStyle.LIQUID -> {
                val shell = organic(r * (1 + a * .09f), 3, .035f + a * .065f, t)
                drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .12f * emphasis), Color.Transparent), c, r * 1.4f), r * 1.4f, c)
                clipPath(shell) {
                    val pearl = lerp(primary, Color.White, .8f)
                    val deep = lerp(primary, Color.Black, .68f)
                    drawRect(Brush.radialGradient(listOf(pearl.copy(alpha = emphasis), primary.copy(alpha = emphasis), deep.copy(alpha = emphasis)), c - Offset(r * .4f, r * .5f), r * 1.7f))
                    repeat(3) { band ->
                        rotate(t * 11f + band * 103f, c) {
                            val flow = Path().apply {
                                moveTo(c.x - r, c.y + r * .35f)
                                cubicTo(c.x + r * .6f, c.y + r * .8f, c.x - r * .7f, c.y - r, c.x + r, c.y - r * .5f)
                                cubicTo(c.x - r * .35f, c.y - r * .72f, c.x + r * .75f, c.y + r * (.5f + a * .2f), c.x - r, c.y + r * .6f)
                                close()
                            }
                            drawPath(flow, Brush.linearGradient(listOf(deep.copy(alpha = .5f * emphasis), pearl.copy(alpha = .8f * emphasis), primary.copy(alpha = .12f * emphasis)), c - Offset(r, r), c + Offset(r, r)))
                        }
                    }
                    repeat(4) { i ->
                        val q = t * (.3f + i * .08f) + i * 1.8f
                        val p = c + Offset(cos(q) * r * .45f, sin(q * 1.3f) * r * .5f)
                        drawCircle(Brush.radialGradient(listOf(shine.copy(alpha = .16f * emphasis), ink.copy(alpha = .05f * emphasis), Color.Transparent), p, r * .9f), r * .9f, p)
                        drawPath(organic(r * (.62f + i * .1f), 2 + i, .12f + a * .08f, -q), shine.copy(alpha = .22f * emphasis), style = Stroke(r * .015f))
                    }
                }
                drawPath(shell, shine.copy(alpha = .65f * emphasis), style = Stroke(1.2f))
            }
            VoiceStyle.HALO -> repeat(5) { i ->
                drawPath(organic(r * (1 + i * .024f), 5, .025f + a * .11f, t + i * .23f), ink.copy(alpha = (.85f - i * .15f) * emphasis), style = Stroke(if (i == 0) 2.5f else 1.1f))
            }
            VoiceStyle.RIBBON, VoiceStyle.AURORA -> {
                val aurora = style == VoiceStyle.AURORA
                val width = size.width * .83f
                repeat(if (aurora) 6 else 9) { i ->
                    val p = Path()
                    repeat(101) { j ->
                        val u = j / 100f
                        val x = c.x - width / 2 + u * width
                        val env = sin(u * PI.toFloat())
                        val y = c.y + sin(u * PI.toFloat() * (if (aurora) 2.3f else 3.5f) + t + i * .18f) * r * (.22f + a * .65f) * env + (i - 3) * r * .045f
                        if (j == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    if (aurora) {
                        p.lineTo(c.x + width / 2, c.y + r * .5f)
                        p.lineTo(c.x - width / 2, c.y + r * .5f); p.close()
                        drawPath(p, Brush.verticalGradient(listOf(shine.copy(alpha = .17f * emphasis), ink.copy(alpha = .05f * emphasis), Color.Transparent), c.y - r, c.y + r))
                    } else drawPath(p, lerp(ink, shine, i / 9f).copy(alpha = (.25f + .06f * i) * emphasis), style = Stroke(1.3f + a * .7f))
                }
            }
            VoiceStyle.PARTICLES -> {
                repeat(96) { i ->
                    val theta = i * 2.399963f + t * .12f
                    val spread = sqrt((i + .5f) / 96f)
                    val radius = r * spread * (.65f + a * .55f + .035f * sin(t + i))
                    val p = c + Offset(cos(theta) * radius, sin(theta) * radius * .86f)
                    val dot = (1f + ((i * 17) % 7) * .3f) * (1 + a * .25f)
                    drawCircle(lerp(ink, shine, (i % 5) / 5f).copy(alpha = (.25f + (1 - spread) * .7f) * emphasis), dot, p)
                }
                drawCircle(Brush.radialGradient(listOf(ink.copy(alpha = .25f * emphasis), Color.Transparent), c, r * .6f), r * .6f, c)
            }
            VoiceStyle.PETALS -> repeat(5) { i ->
                rotate(i * 72f + t * 6f, c) {
                    val p = Path().apply {
                        moveTo(c.x, c.y + r * .22f)
                        cubicTo(c.x - r * .85f, c.y - r * .7f, c.x + r * .6f, c.y - r * (1.15f + a * .35f), c.x + r * .38f, c.y - r * .12f)
                        cubicTo(c.x + r * .3f, c.y + r * .4f, c.x - r * .15f, c.y + r * .4f, c.x, c.y + r * .22f); close()
                    }
                    drawPath(p, Brush.radialGradient(listOf(ink.copy(alpha = .06f * emphasis), shine.copy(alpha = .42f * emphasis), ink.copy(alpha = .1f * emphasis)), c, r * 1.3f))
                    drawPath(p, shine.copy(alpha = .45f * emphasis), style = Stroke(1f))
                }
            }
            VoiceStyle.EQUALIZER -> {
                val w = min(size.width * .07f, r * .29f)
                repeat(7) { i ->
                    val h = r * (.23f + a * (1.7f - abs(i - 3) * .22f) * (.65f + .35f * sin(t * 3 + i)))
                    drawRoundRect(Brush.verticalGradient(listOf(shine, ink)), Offset(c.x + (i - 3) * w * 1.65f - w / 2, c.y - h / 2), Size(w, h), CornerRadius(w / 2))
                }
            }
            VoiceStyle.RIPPLES -> repeat(4) { i ->
                val cycle = ((t * .16f + i * .25f) % 1f)
                drawPath(organic(r * (.3f + cycle * .9f), 4, .015f + a * .045f, t - i, .65f), ink.copy(alpha = (1 - cycle) * .75f * emphasis), style = Stroke(1.4f + a))
            }
            VoiceStyle.ORBITS -> repeat(3) { i ->
                rotate(-35f + i * 53f + sin(t * .35f) * 8, c) {
                    val rx = r * (1 + a * .15f)
                    val ry = r * (.32f + i * .09f)
                    drawOval(ink.copy(alpha = (.55f + i * .12f) * emphasis), c - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(1.3f))
                    val q = t * .6f + i * 2.1f
                    val p = c + Offset(cos(q) * rx, sin(q) * ry)
                    drawCircle(Brush.radialGradient(listOf(shine.copy(alpha = .6f * emphasis), Color.Transparent), p, 9f), 9f, p)
                    drawCircle(shine, 2.5f + a * 1.8f, p)
                }
            }
            VoiceStyle.FIELD -> {
                val spacing = min(size.width / 18, r * .25f)
                repeat(6) { row -> repeat(15) { col ->
                    val distance = abs(col - 7) / 7f
                    val signal = (.5f + .5f * sin(col * .5f - t * 2 + row * .55f)) * (1 - distance * .7f)
                    val h = spacing * (.17f + a * signal * .65f)
                    val w = spacing * .28f
                    val p = c + Offset((col - 7) * spacing, (row - 2.5f) * spacing * .8f - h / 2)
                    drawRoundRect(ink.copy(alpha = (.15f + signal * (.3f + a * .5f)) * emphasis), p, Size(w, h.coerceAtLeast(w)), CornerRadius(w / 2))
                } }
            }
        }
    }
}
