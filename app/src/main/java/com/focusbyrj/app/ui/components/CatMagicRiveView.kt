package com.focusbyrj.app.ui.components

import android.annotation.SuppressLint
import android.graphics.PointF
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.core.Alignment as RiveAlignment
import app.rive.runtime.kotlin.core.File as RiveFile
import app.rive.runtime.kotlin.core.Fit
import app.rive.runtime.kotlin.core.Helpers
import app.rive.runtime.kotlin.core.Loop
import app.rive.runtime.kotlin.core.StateMachineInstance
import app.rive.runtime.kotlin.renderers.PointerEvents
import com.focusbyrj.app.R

data class RiveCatAsset(
    val assetName: String,
    val displayName: String,
    val artboardName: String? = null,
    val stateMachineName: String? = "State Machine 1",
    val contentScale: Float = 1.0f,
    val offsetYRatio: Float = 0.0f
)

val WELCOME_CAT_RIVE_ASSETS = listOf(
    RiveCatAsset(assetName = "cat_awesome_morning.riv", displayName = "Awesome Morning Cat", artboardName = "main cat", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_awesome_morning.riv", displayName = "Day & Night Cat Scene", artboardName = "Main", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_blunng.riv", displayName = "Blunng Cat", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_luna.riv", displayName = "Luna Cat", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_gold_fish.riv", displayName = "Goldfish Aquarium Cat", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_googlyeyes.riv", displayName = "Googly Eyes Cat", stateMachineName = "State Machine 1"),
    RiveCatAsset(assetName = "cat_wildfairy.riv", displayName = "Wild Fairy Cat", stateMachineName = null)
)

/**
 * Metadata discovered from the loaded Rive file so we only manipulate inputs that actually exist.
 */
private class RiveInteractivityDescriptor {
    val numberInputs = mutableListOf<String>()
    val booleanInputs = mutableListOf<String>()
    val triggerInputs = mutableListOf<String>()
    val booleanStateMap = mutableMapOf<String, Boolean>()
    val numberStateMap = mutableMapOf<String, Float>()
    var activeStateMachineName: String? = null
    var activeArtboardBounds: RectF? = null
    var isResolved: Boolean = false

    fun resolveStateMachine(view: RiveAnimationView): String? {
        if (isResolved && activeStateMachineName != null) return activeStateMachineName
        try {
            val sm = view.controller.stateMachines.firstOrNull()
            if (sm != null) {
                activeStateMachineName = sm.name
                activeArtboardBounds = view.controller.artboardBounds
                numberInputs.clear()
                booleanInputs.clear()
                triggerInputs.clear()
                for (input in sm.inputs) {
                    when {
                        input.isNumber -> if (!numberInputs.contains(input.name)) numberInputs.add(input.name)
                        input.isBoolean -> if (!booleanInputs.contains(input.name)) booleanInputs.add(input.name)
                        input.isTrigger -> if (!triggerInputs.contains(input.name)) triggerInputs.add(input.name)
                    }
                }
                isResolved = true
                return sm.name
            }
        } catch (_: Throwable) {}
        return activeStateMachineName
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CatWelcomeRiveView(
    modifier: Modifier = Modifier,
    messageId: String? = null
) {
    val initialIndex = remember(messageId) {
        if (WELCOME_CAT_RIVE_ASSETS.isNotEmpty()) {
            if (messageId != null) {
                kotlin.math.abs(messageId.hashCode()) % WELCOME_CAT_RIVE_ASSETS.size
            } else {
                kotlin.random.Random.nextInt(WELCOME_CAT_RIVE_ASSETS.size)
            }
        } else 0
    }
    var currentCatIndex by remember(messageId) { mutableIntStateOf(initialIndex) }
    val currentCat = WELCOME_CAT_RIVE_ASSETS.getOrElse(currentCatIndex) { WELCOME_CAT_RIVE_ASSETS[0] }
    var isNightMode by remember(currentCatIndex) { mutableStateOf(false) }

    var riveViewRef: RiveAnimationView? = null

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val boxWidth = maxWidth
            val boxHeight = maxHeight
            val scaleFactor = if (boxWidth > 0.dp && boxHeight > 0.dp) {
                maxOf(boxWidth / 500.dp, boxHeight / 500.dp)
            } else 1f
            val contentW = 500.dp * scaleFactor
            val contentH = 500.dp * scaleFactor
            val offsetX = (boxWidth - contentW) / 2
            val offsetY = (boxHeight - contentH) / 2
            val switchLeft = offsetX + (contentW * 0.128f)
            val switchTop = offsetY + (contentH * 0.084f)
            val switchWidth = (84.dp * scaleFactor).coerceIn(70.dp, 120.dp)
            val switchHeight = (54.dp * scaleFactor).coerceIn(46.dp, 80.dp)

            key("${currentCat.assetName}_${currentCat.artboardName ?: "default"}") {
                val isSpecialExpanded = currentCat.contentScale != 1.0f
                AndroidView(
                    modifier = if (isSpecialExpanded) {
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = currentCat.contentScale
                                scaleY = currentCat.contentScale
                                translationY = size.height * currentCat.offsetYRatio
                            }
                    } else {
                        Modifier.fillMaxSize()
                    },
                    factory = { ctx ->
                        try {
                            try {
                                app.rive.runtime.kotlin.core.Rive.init(ctx.applicationContext)
                            } catch (_: Throwable) {}

                            RiveAnimationView(ctx).apply {
                                riveViewRef = this
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                fit = Fit.COVER
                                alignment = RiveAlignment.CENTER
                                isClickable = true
                                isFocusable = true

                                val descriptor = RiveInteractivityDescriptor()

                                var loaded = false
                                try {
                                    val bytes = ctx.assets.open(currentCat.assetName).readBytes()
                                    setRiveBytes(
                                        bytes = bytes,
                                        artboardName = currentCat.artboardName,
                                        stateMachineName = currentCat.stateMachineName,
                                        autoplay = true,
                                        fit = Fit.COVER,
                                        alignment = RiveAlignment.CENTER
                                    )
                                    loaded = true

                                    // Discover active artboard & state machine inputs from controller
                                    try {
                                        descriptor.activeArtboardBounds = controller.artboardBounds
                                        val smInstance = controller.stateMachines.firstOrNull()
                                        if (smInstance != null) {
                                            descriptor.activeStateMachineName = smInstance.name
                                            for (input in smInstance.inputs) {
                                                when {
                                                    input.isNumber -> descriptor.numberInputs.add(input.name)
                                                    input.isBoolean -> descriptor.booleanInputs.add(input.name)
                                                    input.isTrigger -> descriptor.triggerInputs.add(input.name)
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}
                                } catch (ex: Throwable) {
                                    ex.printStackTrace()
                                }

                                if (!loaded) {
                                    val fallbackAssets = listOf("cat_awesome_morning.riv", "cat_blunng.riv", "cat_luna.riv", "cat_gold_fish.riv", "cat_googlyeyes.riv", "cat_wildfairy.riv")
                                    for (fallback in fallbackAssets) {
                                        try {
                                            val bytes = ctx.assets.open(fallback).readBytes()
                                            setRiveBytes(
                                                bytes = bytes,
                                                autoplay = true,
                                                fit = Fit.COVER,
                                                alignment = RiveAlignment.CENTER
                                            )
                                            play()
                                            break
                                        } catch (_: Throwable) {}
                                    }
                                }

                                // Touch tracking with full coordinate coverage across entire box
                                var downX = 0f
                                var downY = 0f
                                val touchSlop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

                                setOnTouchListener { v, event ->
                                    if (event == null) return@setOnTouchListener false
                                    val smName = descriptor.resolveStateMachine(this)
                                    val smInstance = controller.stateMachines.firstOrNull()

                                    val viewW = if (v.width > 0) v.width.toFloat() else 500f
                                    val viewH = if (v.height > 0) v.height.toFloat() else 500f
                                    val artBounds = descriptor.activeArtboardBounds ?: controller.artboardBounds ?: RectF(0f, 0f, 500f, 500f)
                                    val artW = if (artBounds.width() > 0f) artBounds.width() else 500f
                                    val artH = if (artBounds.height() > 0f) artBounds.height() else 500f

                                    val scale = maxOf(viewW / artW, viewH / artH)
                                    val contentW = artW * scale
                                    val contentH = artH * scale
                                    val offsetX = (viewW - contentW) / 2f
                                    val offsetY = (viewH - contentH) / 2f
                                    val artX = (event.x - offsetX) / scale
                                    val artY = (event.y - offsetY) / scale

                                    if (smInstance != null) {
                                        try {
                                            when (event.actionMasked) {
                                                MotionEvent.ACTION_DOWN -> smInstance.pointerDown(artX, artY)
                                                MotionEvent.ACTION_MOVE -> smInstance.pointerMove(artX, artY)
                                                MotionEvent.ACTION_UP -> smInstance.pointerUp(artX, artY)
                                            }
                                        } catch (_: Throwable) {}
                                    }

                                    try {
                                        controller.targetBounds = RectF(0f, 0f, viewW, viewH)
                                        when (event.actionMasked) {
                                            MotionEvent.ACTION_DOWN -> controller.pointerEvent(PointerEvents.POINTER_DOWN, event.x, event.y)
                                            MotionEvent.ACTION_MOVE -> controller.pointerEvent(PointerEvents.POINTER_MOVE, event.x, event.y)
                                            MotionEvent.ACTION_UP -> controller.pointerEvent(PointerEvents.POINTER_UP, event.x, event.y)
                                        }
                                    } catch (_: Throwable) {}

                                    when (event.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)
                                            downX = event.x
                                            downY = event.y

                                            // If file has tracking boolean (e.g. IsTracking)
                                            if (smName != null) {
                                                for (bInput in descriptor.booleanInputs) {
                                                    if (bInput.equals("IsTracking", ignoreCase = true) ||
                                                        bInput.contains("track", ignoreCase = true) ||
                                                        bInput.contains("hover", ignoreCase = true)) {
                                                        try {
                                                            setBooleanState(smName, bInput, true)
                                                        } catch (_: Throwable) {}
                                                    }
                                                }
                                            }

                                            // Apply artboard normalized coordinates for tracking
                                            updateCoordinates(event.x, event.y, v.width, v.height, descriptor)
                                            true
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(true)

                                            // Update continuous coordinate inputs (EYES X/Y, cursor, etc.)
                                            updateCoordinates(event.x, event.y, v.width, v.height, descriptor)
                                            true
                                        }
                                        MotionEvent.ACTION_UP -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(false)

                                            val dx = kotlin.math.abs(event.x - downX)
                                            val dy = kotlin.math.abs(event.y - downY)
                                            val isTap = dx < touchSlop && dy < touchSlop

                                            if (isTap) {
                                                if (artX in 0f..180f && artY in 0f..120f) {
                                                    isNightMode = !isNightMode
                                                    toggleDayNightScene(isNightMode)
                                                } else {
                                                    handleTap(smName, descriptor)
                                                }
                                            }

                                            // Reset tracking boolean on release
                                            if (smName != null) {
                                                for (bInput in descriptor.booleanInputs) {
                                                    if (bInput.equals("IsTracking", ignoreCase = true) ||
                                                        bInput.contains("track", ignoreCase = true) ||
                                                        bInput.contains("hover", ignoreCase = true)) {
                                                        try {
                                                            setBooleanState(smName, bInput, false)
                                                        } catch (_: Throwable) {}
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        MotionEvent.ACTION_CANCEL -> {
                                            v.parent?.requestDisallowInterceptTouchEvent(false)
                                            if (smName != null) {
                                                for (bInput in descriptor.booleanInputs) {
                                                    if (bInput.equals("IsTracking", ignoreCase = true) ||
                                                        bInput.contains("track", ignoreCase = true) ||
                                                        bInput.contains("hover", ignoreCase = true)) {
                                                        try {
                                                            setBooleanState(smName, bInput, false)
                                                        } catch (_: Throwable) {}
                                                    }
                                                }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                }
                            }
                        } catch (t: Throwable) {
                            android.view.View(ctx)
                        }
                    },
                    update = { view ->
                        if (view is RiveAnimationView) {
                            riveViewRef = view
                            try {
                                if (!view.isPlaying) {
                                    view.play()
                                }
                            } catch (e: Throwable) {}
                        }
                    },
                    onRelease = { view ->
                        if (view is RiveAnimationView) {
                            try {
                                view.pause()
                            } catch (_: Throwable) {}
                        }
                    }
                )
            }

            // Direct touch target overlay over the top-left toggle switch on Day & Night scene
            if (currentCat.displayName.contains("Day & Night", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = switchLeft, y = switchTop)
                        .size(width = switchWidth, height = switchHeight)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            isNightMode = !isNightMode
                            riveViewRef?.toggleDayNightScene(isNightMode)
                        }
                )
            }
        }

        // Companion tag placed BELOW the animation
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Cat Companion badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .clickable {
                        currentCatIndex = (currentCatIndex + 1) % WELCOME_CAT_RIVE_ASSETS.size
                    }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ ${currentCat.displayName}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "• Switch Cat",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                )
            }
        }
    }
}

@Composable
fun CatMagicRiveView(
    modifier: Modifier = Modifier,
    messageId: String? = null
) {
    CatWelcomeRiveView(modifier = modifier, messageId = messageId)
}

/**
 * Calculates normalized coordinate positions across the view bounds and maps them
 * to the discovered Rive number inputs (e.g. EYES X, EYES Y, mouseX, mouseY).
 */
private fun RiveAnimationView.updateCoordinates(
    touchX: Float,
    touchY: Float,
    viewWidth: Int,
    viewHeight: Int,
    descriptor: RiveInteractivityDescriptor
) {
    val smName = descriptor.activeStateMachineName ?: descriptor.resolveStateMachine(this) ?: return
    if (descriptor.numberInputs.isEmpty() || viewWidth <= 0 || viewHeight <= 0) return

    val normX = (touchX / viewWidth.toFloat()).coerceIn(0f, 1f)
    val normY = (touchY / viewHeight.toFloat()).coerceIn(0f, 1f)

    val bounds = descriptor.activeArtboardBounds ?: try { controller.artboardBounds } catch (_: Throwable) { null }
    val artboardWidth = bounds?.width()?.takeIf { it > 0f } ?: viewWidth.toFloat()
    val artboardHeight = bounds?.height()?.takeIf { it > 0f } ?: viewHeight.toFloat()

    val artboardX = normX * artboardWidth
    val artboardY = normY * artboardHeight

    for (numInput in descriptor.numberInputs) {
        try {
            val lower = numInput.lowercase()
            when {
                lower.contains("eye") && (lower.endsWith("x") || lower.contains(" x")) -> {
                    // Coordinates scaled to 0..100% or artboard coordinate range
                    setNumberState(smName, numInput, normX * 100f)
                }
                lower.contains("eye") && (lower.endsWith("y") || lower.contains(" y")) -> {
                    setNumberState(smName, numInput, normY * 100f)
                }
                lower.contains("x") || lower.contains("posx") || lower.contains("cursor") -> {
                    setNumberState(smName, numInput, artboardX)
                }
                lower.contains("y") || lower.contains("posy") -> {
                    setNumberState(smName, numInput, artboardY)
                }
                else -> {
                    // Unknown numeric input, do not guess to avoid crashing
                }
            }
        } catch (_: Throwable) {}
    }
}

/**
 * Triggers the toggle on the Day & Night scene ("Main" artboard).
 * Uses Rive's direct Artboard pointer methods (`pointerDown(64f, 42f)` and `pointerUp(64f, 42f)`)
 * to activate Rive's internal Listener (Target 2 / 3) seamlessly, and also advances the frame.
 */
fun RiveAnimationView.toggleDayNightScene(isNightTarget: Boolean? = null) {
    try {
        val smInstance = controller.stateMachines.firstOrNull()

        val w = if (width > 0) width.toFloat() else 500f
        val h = if (height > 0) height.toFloat() else 500f
        val artW = 500f
        val artH = 500f
        val scale = maxOf(w / artW, h / artH)
        val contentW = artW * scale
        val contentH = artH * scale
        val offsetX = (w - contentW) / 2f
        val offsetY = (h - contentH) / 2f

        controller.targetBounds = RectF(0f, 0f, w, h)

        // 1. Direct hit-test on all target positions of the switch button inside Rive artboard space
        val artCoords = listOf(
            PointF(86f, 42f),
            PointF(64f, 42f),
            PointF(75f, 42f),
            PointF(95f, 42f)
        )

        if (smInstance != null) {
            for (pt in artCoords) {
                smInstance.pointerDown(pt.x, pt.y)
                smInstance.pointerUp(pt.x, pt.y)
            }
        }

        // 2. Dispatch via RiveFileController with accurate screen space coordinates that account for letterboxing
        for (pt in artCoords) {
            val screenX = offsetX + pt.x * scale
            val screenY = offsetY + pt.y * scale
            controller.pointerEvent(PointerEvents.POINTER_DOWN, screenX, screenY)
            controller.pointerEvent(PointerEvents.POINTER_UP, screenX, screenY)
        }

        if (!isPlaying) {
            play()
        }
    } catch (_: Throwable) {}
}

/**
 * Fires triggers or switches boolean states when user taps.
 * Only touches inputs that are confirmed to exist on the active state machine,
 * and also safely fires nested artboard inputs (e.g. Instance/cat clicked).
 */
private fun RiveAnimationView.handleTap(
    smName: String?,
    descriptor: RiveInteractivityDescriptor
) {
    if (smName != null) {
        // Coordinated handler for Day & Night scene (cat_awesome_morning.riv / Main)
        if (descriptor.numberInputs.isEmpty() && descriptor.booleanInputs.isEmpty()) {
            toggleDayNightScene()
            return
        }

        // Fire any valid trigger inputs
        for (trig in descriptor.triggerInputs) {
            try {
                fireState(smName, trig)
            } catch (_: Throwable) {}
        }

        // Generic primary boolean toggle
        val primaryBool = descriptor.booleanInputs.firstOrNull { 
            it.equals("switch", ignoreCase = true) || it.equals("toggle", ignoreCase = true)
        } ?: if (descriptor.booleanInputs.size == 1) descriptor.booleanInputs.firstOrNull() else null

        if (primaryBool != null) {
            try {
                val smInstance = controller.stateMachines.firstOrNull()
                val boolObj = smInstance?.inputs?.filterIsInstance<app.rive.runtime.kotlin.core.SMIBoolean>()?.firstOrNull {
                    it.name.equals(primaryBool, ignoreCase = true)
                }
                if (boolObj != null) {
                    val current = boolObj.value
                    val next = !current
                    descriptor.booleanStateMap[primaryBool] = next
                    setBooleanState(smName, primaryBool, next)
                }
            } catch (_: Throwable) {}
        }

        // Safely cycle any interactive numeric inputs (e.g. bounce, jump, action)
        for (numInput in descriptor.numberInputs) {
            val lower = numInput.lowercase()
            if (lower.contains("bounce") || lower.contains("jump") || lower.contains("action") || lower.contains("click")) {
                try {
                    val current = descriptor.numberStateMap[numInput] ?: 0f
                    val next = if (current <= 0f) 1f else 0f
                    descriptor.numberStateMap[numInput] = next
                    setNumberState(smName, numInput, next)
                } catch (_: Throwable) {}
            }
        }
    }

    try {
        if (!isPlaying) {
            play()
        }
    } catch (_: Throwable) {}
}

