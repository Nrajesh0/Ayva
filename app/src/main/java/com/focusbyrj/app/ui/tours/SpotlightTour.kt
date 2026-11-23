@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.focusbyrj.app.ui.tours

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal val LocalToursAllowed = compositionLocalOf { true }

internal class SpotlightState(val tour: AppTour) {
    internal val anchors = mutableStateMapOf<String, Rect>()
    internal val requesters = mutableStateMapOf<String, BringIntoViewRequester>()
    internal var interacted by mutableStateOf(false)
}

@Composable
internal fun rememberSpotlightTour(tour: AppTour): SpotlightState {
    val serial = rememberTourPreferences().replaySerial
    return remember(tour.id, serial) { SpotlightState(tour) }
}

/** Observe without consuming gestures. Never interrupt someone who has already started. */
internal fun Modifier.pauseTourOnInteraction(state: SpotlightState): Modifier =
    pointerInput(state) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed && !it.previousPressed }) state.interacted = true
            }
        }
    }

/** Measure the actual control, including clipping and its window's screen offset. */
internal fun Modifier.spotlightTarget(state: SpotlightState?, id: String): Modifier =
    if (state == null) this
    else
        composed {
            val view = LocalView.current
            val requester = remember(state, id) { BringIntoViewRequester() }
            DisposableEffect(state, id, requester) {
                state.requesters[id] = requester
                onDispose {
                    if (state.requesters[id] === requester) {
                        state.requesters.remove(id)
                        state.anchors.remove(id)
                    }
                }
            }
            this.bringIntoViewRequester(requester)
                .pauseTourOnInteraction(state)
                .onGloballyPositioned { coordinates ->
                    val screen = IntArray(2)
                    val window = IntArray(2)
                    view.getLocationOnScreen(screen)
                    view.getLocationInWindow(window)
                    state.anchors[id] =
                        coordinates
                            .boundsInWindow()
                            .translate(
                                Offset(
                                    (screen[0] - window[0]).toFloat(),
                                    (screen[1] - window[1]).toFloat(),
                                )
                            )
                }
        }

internal data class TourCardSpace(val below: Boolean, val maxHeight: Int)

internal fun tourCardSpace(
    target: Rect,
    height: Int,
    safeTop: Int,
    safeBottom: Int,
    gap: Int,
): TourCardSpace {
    val above = max(0, target.top.roundToInt() - safeTop - gap)
    val below = max(0, height - safeBottom - target.bottom.roundToInt() - gap)
    return TourCardSpace(below >= above, max(above, below))
}

/** A modal explanation: touching the spotlight never performs an underlying action. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SpotlightTour(
    state: SpotlightState,
    enabled: Boolean = true,
    steps: List<TourStep> = state.tour.steps,
) {
    val preferences = rememberTourPreferences()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0
    val replaying = preferences.replayId == state.tour.id
    val eligible =
        LocalToursAllowed.current &&
            enabled &&
            (!state.interacted || replaying) &&
            preferences.eligible(state.tour) &&
            lifecycleState == Lifecycle.State.RESUMED &&
            !keyboardOpen
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(eligible) {
        settled = false
        if (eligible) {
            delay(650)
            settled = true
        }
    }
    var index by rememberSaveable(state.tour.id, preferences.replaySerial) { mutableIntStateOf(0) }
    val step = steps.getOrNull(index) ?: return
    val requester = state.requesters[step.target]
    val anchor = state.anchors[step.target]
    val parentView = LocalView.current
    val visibleFrame = android.graphics.Rect()
    parentView.getWindowVisibleDisplayFrame(visibleFrame)
    val viewport =
        Rect(
            visibleFrame.left.toFloat(),
            visibleFrame.top.toFloat(),
            visibleFrame.right.toFloat(),
            visibleFrame.bottom.toFloat(),
        )
    val needsScroll =
        anchor == null ||
            anchor.width < 12 ||
            anchor.height < 12 ||
            anchor.intersect(viewport).isEmpty
    var seeking by remember { mutableStateOf(false) }
    LaunchedEffect(eligible, requester, index, preferences.replaySerial) {
        seeking = eligible && (replaying || index > 0) && requester != null && needsScroll
        try {
            if (seeking) {
                if (index == 0) delay(650)
                requester?.bringIntoView()
                withFrameNanos {}
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: IllegalStateException) {
            /* Detached targets wait for the next layout. */
        } finally {
            seeking = false
        }
    }
    if (anchor == null) return
    if (!eligible || !settled || seeking || anchor.width < 12 || anchor.height < 12) return
    val visibleAnchor = anchor.intersect(viewport)
    if (visibleAnchor.isEmpty) return
    val available =
        tourCardSpace(
            visibleAnchor.translate(-viewport.topLeft),
            viewport.height.roundToInt(),
            0,
            0,
            with(density) { 28.dp.roundToPx() },
        )
    // Wait for a usable layout rather than showing a card whose dismissal cannot fit.
    if (
        available.maxHeight <
            with(density) { (64 + 56 * fontScale.coerceAtLeast(1f)).dp.roundToPx() }
    )
        return

    Dialog(
        onDismissRequest = { preferences.finish(state.tour) },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnClickOutside = false,
            ),
    ) {
        val view = LocalView.current
        SideEffect { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
        var origin by remember { mutableStateOf<Offset?>(null) }
        var cardBounds by remember(step.target) { mutableStateOf<Rect?>(null) }
        val safeTop = WindowInsets.safeDrawing.getTop(density)
        val safeBottom = WindowInsets.safeDrawing.getBottom(density)
        val direction = LocalLayoutDirection.current
        val safeLeft = WindowInsets.safeDrawing.getLeft(density, direction)
        val safeRight = WindowInsets.safeDrawing.getRight(density, direction)
        val gap = with(density) { 12.dp.roundToPx() }
        val margin = with(density) { 16.dp.roundToPx() }
        val accent = MaterialTheme.colorScheme.primary
        Box(
            Modifier.fillMaxSize().testTag("spotlight_overlay").onGloballyPositioned { coordinates
                ->
                val screen = IntArray(2)
                val window = IntArray(2)
                view.getLocationOnScreen(screen)
                view.getLocationInWindow(window)
                origin =
                    coordinates.boundsInWindow().topLeft +
                        Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
            }
        ) {
            val localAnchor = origin?.let { anchor.translate(-it) }
            Canvas(Modifier.fillMaxSize()) {
                val visible = localAnchor?.intersect(Rect(Offset.Zero, size))
                val scrim =
                    Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(Rect(Offset.Zero, size))
                        if (visible != null && !visible.isEmpty)
                            addRoundRect(RoundRect(visible, CornerRadius(12.dp.toPx())))
                    }
                drawPath(scrim, Color.Black.copy(alpha = .68f))
                if (visible != null && !visible.isEmpty) {
                    drawRoundRect(
                        accent,
                        visible.topLeft,
                        visible.size,
                        CornerRadius(12.dp.toPx()),
                        style = Stroke(2.dp.toPx()),
                    )
                    cardBounds
                        ?.takeIf { it.width >= 48.dp.toPx() }
                        ?.let { card ->
                            val below = card.top >= visible.bottom
                            val x =
                                visible.center.x.coerceIn(
                                    card.left + 24.dp.toPx(),
                                    card.right - 24.dp.toPx(),
                                )
                            val edge = if (below) card.top else card.bottom
                            val direction = if (below) -1 else 1
                            val pointer =
                                Path().apply {
                                    moveTo(x - 8.dp.toPx(), edge)
                                    lineTo(x, edge + direction * 8.dp.toPx())
                                    lineTo(x + 8.dp.toPx(), edge)
                                }
                            drawPath(pointer, accent, style = Stroke(1.dp.toPx()))
                        }
                }
            }
            if (localAnchor != null)
                Layout(
                    content = {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, accent.copy(alpha = .5f)),
                            modifier =
                                Modifier.testTag("spotlight_card")
                                    .onGloballyPositioned { coordinates ->
                                        val screen = IntArray(2)
                                        val window = IntArray(2)
                                        view.getLocationOnScreen(screen)
                                        view.getLocationInWindow(window)
                                        cardBounds =
                                            origin?.let {
                                                coordinates
                                                    .boundsInWindow()
                                                    .translate(
                                                        Offset(
                                                            (screen[0] - window[0]).toFloat(),
                                                            (screen[1] - window[1]).toFloat(),
                                                        ) - it
                                                    )
                                            }
                                    }
                                    .semantics {
                                        paneTitle = "App tour"
                                        dismiss {
                                            preferences.finish(state.tour)
                                            true
                                        }
                                        liveRegion = LiveRegionMode.Polite
                                    },
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    Modifier.weight(1f, fill = false)
                                        .verticalScroll(
                                            key(step.target, preferences.replaySerial) {
                                                rememberScrollState()
                                            }
                                        ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    if (steps.size > 1)
                                        Text(
                                            "${index + 1} of ${steps.size}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    Text(
                                        step.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.semantics { heading() },
                                    )
                                    Text(
                                        step.explanation,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                                FlowRow(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement =
                                        Arrangement.spacedBy(8.dp, Alignment.End),
                                ) {
                                    TextButton(
                                        onClick = { preferences.finish(state.tour) },
                                        modifier = Modifier.testTag("spotlight_skip"),
                                    ) {
                                        Text("Skip tour")
                                    }
                                    Button(
                                        onClick = {
                                            val next =
                                                ((index + 1)..steps.lastIndex).firstOrNull {
                                                    nextIndex ->
                                                    state.requesters.containsKey(
                                                        steps[nextIndex].target
                                                    )
                                                }
                                            if (next == null) preferences.finish(state.tour)
                                            else index = next
                                        },
                                        modifier = Modifier.testTag("spotlight_next"),
                                    ) {
                                        Text(if (index == steps.lastIndex) "Got it" else "Next")
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) { measurables, constraints ->
                    val visible =
                        localAnchor.intersect(
                            Rect(
                                0f,
                                0f,
                                constraints.maxWidth.toFloat(),
                                constraints.maxHeight.toFloat(),
                            )
                        )
                    val space =
                        tourCardSpace(
                            visible,
                            constraints.maxHeight,
                            safeTop + margin,
                            safeBottom + margin,
                            gap,
                        )
                    val maxWidth =
                        (constraints.maxWidth - safeLeft - safeRight - margin * 2).coerceAtLeast(1)
                    val card =
                        measurables
                            .single()
                            .measure(
                                Constraints(
                                    maxWidth = min(maxWidth, 360.dp.roundToPx()),
                                    maxHeight = space.maxHeight.coerceAtLeast(1),
                                )
                            )
                    val x =
                        (visible.center.x - card.width / 2f)
                            .roundToInt()
                            .coerceIn(
                                safeLeft + margin,
                                max(
                                    safeLeft + margin,
                                    constraints.maxWidth - safeRight - margin - card.width,
                                ),
                            )
                    val y =
                        if (space.below) visible.bottom.roundToInt() + gap
                        else visible.top.roundToInt() - gap - card.height
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        card.place(
                            x,
                            y.coerceIn(
                                safeTop,
                                max(safeTop, constraints.maxHeight - safeBottom - card.height),
                            ),
                        )
                    }
                }
        }
    }
}
