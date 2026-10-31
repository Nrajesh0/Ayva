package com.focusbyrj.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Handler
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("DEPRECATION")
internal data class VizagArtwork(val drawable: Drawable? = null, val movie: Movie? = null)

@Suppress("DEPRECATION")
internal fun decodeVizagArtwork(context: Context, scene: VizagScene): VizagArtwork =
    if (Build.VERSION.SDK_INT >= 28) {
        VizagArtwork(drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.resources, scene.animation)))
    } else {
        VizagArtwork(movie = context.resources.openRawResource(scene.animation).use { Movie.decodeStream(it) })
    }

internal const val VIZAG_GITHUB_URL = "https://github.com/Nrajesh0/Ayva"

internal enum class VizagScene(val placeName: String, @RawRes val animation: Int, @DrawableRes val poster: Int) {
    RK_BEACH("RK Beach promenade", R.raw.vizag_rk_beach, R.drawable.vizag_rk_beach_poster),
    KAILASAGIRI("Kailasagiri", R.raw.vizag_kailasagiri, R.drawable.vizag_kailasagiri_poster),
    DOLPHINS_NOSE("Dolphin’s Nose Lighthouse", R.raw.vizag_dolphins_nose, R.drawable.vizag_dolphins_nose_poster),
    FISHING_HARBOUR("Vizag Fishing Harbour", R.raw.vizag_fishing_harbour, R.drawable.vizag_fishing_harbour_poster),
    INS_KURSURA("INS Kursura · S20", R.raw.vizag_ins_kursura, R.drawable.vizag_ins_kursura_poster),
    TENNETI_PARK("Tenneti Park · MV Maa", R.raw.vizag_tenneti_park, R.drawable.vizag_tenneti_park_poster),
    RUSHIKONDA("Rushikonda", R.raw.vizag_rushikonda, R.drawable.vizag_rushikonda_poster),
    VIZAG_PORT("Vizag Port", R.raw.vizag_vizag_port, R.drawable.vizag_vizag_port_poster),
    SIMHACHALAM("Simhachalam", R.raw.vizag_simhachalam, R.drawable.vizag_simhachalam_poster)
}

/** A navigation visit claims one scene. Its saved index survives theme/configuration changes. */
internal object VizagSceneRotation {
    private const val KEY = "next_scene"

    @Synchronized
    fun claimNext(context: Context): Int {
        val prefs = context.getSharedPreferences("vizag_footer", Context.MODE_PRIVATE)
        val index = Math.floorMod(prefs.getInt(KEY, 0), VizagScene.entries.size)
        prefs.edit().putInt(KEY, (index + 1) % VizagScene.entries.size).apply()
        return index
    }
}

// Equivalent to the approved preview's invert(1) hue-rotate(180deg). Alpha stays
// untouched, so the transparent artwork sits on the actual light/dark/OLED surface.
internal val VizagLightMatrix = floatArrayOf(
    .574f, -1.43f, -.144f, 0f, 255f,
    -.426f, -.43f, -.144f, 0f, 255f,
    -.426f, -1.43f, .856f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f
)
private val SignatureFont = FontFamily(Font(R.font.vizag_signature))

@Composable
internal fun rememberVizagScene(navController: NavController? = null): VizagScene {
    val context = LocalContext.current
    var index by rememberSaveable { mutableIntStateOf(VizagSceneRotation.claimNext(context)) }
    var awaitingReturn by rememberSaveable { mutableStateOf(false) }
    // A child Settings page is another visit when the user comes back. Save the
    // departure flag with this back-stack entry; rotation/recomposition isn't a visit.
    DisposableEffect(navController) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == Screen.PreferencesHub.route) {
                if (awaitingReturn) {
                    index = VizagSceneRotation.claimNext(context)
                    awaitingReturn = false
                }
            } else awaitingReturn = true
        }
        navController?.addOnDestinationChangedListener(listener)
        onDispose { navController?.removeOnDestinationChangedListener(listener) }
    }
    return VizagScene.entries[index]
}

@Composable
internal fun SettingsVizagFooter(
    scene: VizagScene,
    modifier: Modifier = Modifier,
    onOpenGithub: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lightSurface = MaterialTheme.colorScheme.background.luminance() > .5f
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    var visible by remember { mutableStateOf(false) }
    var motionAllowed by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f) }
    var playDespiteReducedMotion by remember(scene.animation) { mutableStateOf(false) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                motionAllowed = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    @Suppress("DEPRECATION")
    val artwork by produceState<VizagArtwork?>(null, scene.animation) {
        value = null
        value = withContext(Dispatchers.IO) {
            decodeVizagArtwork(context, scene)
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 14.dp).testTag("settings_vizag_footer"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.widthIn(max = 328.dp).fillMaxWidth().height(106.dp)
                .onGloballyPositioned { coordinates ->
                    val rect = coordinates.boundsInWindow()
                    val root = coordinates.findRootCoordinates().boundsInWindow()
                    visible = rect.width > 0 && rect.height > 0 && rect.overlaps(root)
                },
            contentAlignment = Alignment.Center
        ) {
            if (artwork?.drawable == null && artwork?.movie == null) {
                Image(
                    painterResource(scene.poster), contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                    colorFilter = if (lightSurface) ColorFilter.colorMatrix(ColorMatrix(VizagLightMatrix)) else null
                )
            } else {
                AndroidView(
                    factory = { VizagAnimationView(it) },
                    modifier = Modifier.fillMaxSize(),
                    update = {
                        it.setArtwork(artwork)
                        it.setLightSurface(lightSurface)
                        it.setPlaybackEnabled(foreground && visible, playDespiteReducedMotion)
                    },
                    onRelease = { it.release() }
                )
            }
            if (!motionAllowed && !playDespiteReducedMotion) {
                IconButton(
                    onClick = { playDespiteReducedMotion = true },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play animation", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            scene.placeName,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = .35.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp).testTag("settings_vizag_place")
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Made with ❤️ in Vizag",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, letterSpacing = .15.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Text(
            "By Rj",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = SignatureFont, fontSize = 32.sp, lineHeight = 40.sp,
                fontWeight = FontWeight.Normal, letterSpacing = 0.sp
            ),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp).heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = "Open Ayva on GitHub") {
                    if (onOpenGithub != null) onOpenGithub() else {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(VIZAG_GITHUB_URL)))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, "No app available to open GitHub", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.padding(horizontal = 24.dp, vertical = 2.dp).testTag("settings_vizag_signature")
        )
    }
}

/** Native, dependency-free GIF playback, compatible with Android 7 and newer. */
@Suppress("DEPRECATION")
internal class VizagAnimationView(context: Context) : View(context) {
    private var movie: Movie? = null
    private var drawable: Drawable? = null
    private var artwork: VizagArtwork? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var playbackEnabled = false
    private var reducedMotionOverride = false
    private var running = false
    private var startedAt = 0L
    private var elapsed = 0L
    private var lightSurface = false
    private var observing = false
    private val motionObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { updatePlayback(); invalidate() }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setArtwork(value: VizagArtwork?) {
        if (artwork === value) return
        stopDrawable()
        drawable?.callback = null
        artwork = value
        movie = value?.movie
        drawable = value?.drawable
        // Only the legacy Movie decoder requires software rendering. Caching the
        // modern drawable in a software View layer can leave Compose displaying
        // the first frame even while the drawable reports that it is running.
        setLayerType(if (movie != null) LAYER_TYPE_SOFTWARE else LAYER_TYPE_NONE, null)
        drawable?.callback = this
        drawable?.colorFilter = paint.colorFilter
        if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
        running = false
        elapsed = 0L
        startedAt = SystemClock.uptimeMillis()
        updatePlayback()
        invalidate()
    }

    fun setLightSurface(value: Boolean) {
        if (lightSurface == value) return
        lightSurface = value
        paint.colorFilter = if (value) android.graphics.ColorMatrixColorFilter(VizagLightMatrix) else null
        drawable?.colorFilter = paint.colorFilter
        invalidate()
    }

    fun setPlaybackEnabled(value: Boolean, overrideReducedMotion: Boolean = false) {
        playbackEnabled = value
        reducedMotionOverride = overrideReducedMotion
        updatePlayback()
    }

    private fun animationsEnabled(): Boolean =
        reducedMotionOverride || Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun updatePlayback() {
        val shouldRun = playbackEnabled && (movie != null || drawable != null) && isAttachedToWindow &&
            windowVisibility == VISIBLE && isShown && animationsEnabled()
        if (shouldRun == running) return
        if (shouldRun) startedAt = SystemClock.uptimeMillis()
        else elapsed += SystemClock.uptimeMillis() - startedAt
        running = shouldRun
        if (Build.VERSION.SDK_INT >= 28) {
            (drawable as? AnimatedImageDrawable)?.let { if (running) it.start() else it.stop() }
        }
        invalidate()
    }

    private fun stopDrawable() {
        if (Build.VERSION.SDK_INT >= 28) (drawable as? AnimatedImageDrawable)?.stop()
    }

    override fun verifyDrawable(who: Drawable): Boolean = who === drawable || super.verifyDrawable(who)

    override fun invalidateDrawable(who: Drawable) {
        // The drawable uses source-image bounds, while this View fits/scales and
        // centers that image. Its dirty rectangle is not in View coordinates.
        if (who === drawable) invalidate() else super.invalidateDrawable(who)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!observing) {
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, motionObserver
            )
            observing = true
        }
        updatePlayback()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updatePlayback()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        updatePlayback()
    }

    override fun onDetachedFromWindow() {
        stopObserving()
        super.onDetachedFromWindow()
    }

    private fun stopObserving() {
        if (running) elapsed += SystemClock.uptimeMillis() - startedAt
        running = false
        stopDrawable()
        if (observing) context.contentResolver.unregisterContentObserver(motionObserver)
        observing = false
    }

    fun release() {
        stopObserving()
        drawable?.callback = null
        drawable = null
        movie = null
        artwork = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawable?.let { image ->
            if (image.intrinsicWidth <= 0 || image.intrinsicHeight <= 0) return
            val scale = minOf(width.toFloat() / image.intrinsicWidth, height.toFloat() / image.intrinsicHeight)
            val checkpoint = canvas.save()
            canvas.translate((width - image.intrinsicWidth * scale) / 2, (height - image.intrinsicHeight * scale) / 2)
            canvas.scale(scale, scale)
            image.setBounds(0, 0, image.intrinsicWidth, image.intrinsicHeight)
            image.draw(canvas)
            canvas.restoreToCount(checkpoint)
            // Explicit full-view ticks also keep AndroidView's Compose host in
            // sync on devices whose drawable callback only updates its own layer.
            if (running) postInvalidateDelayed(80L)
            return
        }
        val animation = movie ?: return
        if (animation.width() <= 0 || animation.height() <= 0) return
        val scale = minOf(width.toFloat() / animation.width(), height.toFloat() / animation.height())
        val duration = animation.duration().takeIf { it > 0 } ?: 8000
        val time = if (animationsEnabled()) elapsed + if (running) SystemClock.uptimeMillis() - startedAt else 0L else 0L
        animation.setTime((time % duration).toInt())
        val checkpoint = canvas.save()
        canvas.translate((width - animation.width() * scale) / 2, (height - animation.height() * scale) / 2)
        canvas.scale(scale, scale)
        animation.draw(canvas, 0f, 0f, paint)
        canvas.restoreToCount(checkpoint)
        if (running) postInvalidateDelayed(80L)
    }
}
