package com.focusbyrj.app.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

internal object SmokePawMotion {
    private val speeds = doubleArrayOf(1.42, 1.18, 1.02, .92)
    fun durationMs(rarity: ChestRarity): Long = (6200 / speeds[rarity.ordinal]).toLong()
}

/** Bounded reusable frame buffers; never recycle a bitmap that a render thread may still own. */
private class SmokePawFrames {
    private val lock = Mutex()
    private val buffers = arrayOfNulls<Bitmap>(4)
    private var next = 0
    suspend fun decode(result: String): ImageBitmap = lock.withLock {
        withContext(Dispatchers.Default) {
            require(result.length <= 2_000_000) { "Invalid animation frame" }
            val url = JSONTokener(result).nextValue() as? String ?: error("Missing animation frame")
            require(url.startsWith("data:image/jpeg;base64,")) { "Invalid animation frame format" }
            val bytes = Base64.decode(url.substringAfter(','), Base64.DEFAULT)
            val slot = next
            next = (next + 1) % buffers.size
            val options = BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inBitmap = buffers[slot]
            }
            val bitmap = try { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }
            catch (_: IllegalArgumentException) {
                options.inBitmap = null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } ?: error("Animation frame could not be decoded")
            require(bitmap.width == 780 && bitmap.height == 900) { "Invalid animation frame dimensions" }
            buffers[slot] = bitmap
            bitmap.asImageBitmap()
        }
    }
}

/** Private offline vector rasterizer. No browser view is attached to the app's windows. */
@Suppress("SetJavaScriptEnabled")
@Composable
internal fun SmokePawChestScene(
    rarity: ChestRarity,
    mode: Int,
    elapsedMs: Long,
    reduceMotion: Boolean,
    onReady: () -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val readyCallback by rememberUpdatedState(onReady)
    val failureCallback by rememberUpdatedState(onFailure)
    var engineReady by remember { mutableStateOf(false) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val frames = remember { SmokePawFrames() }
    val html = remember { context.assets.open("smoke_paw_chest.html").bufferedReader().use { it.readText() } }
    val engine = remember {
        runCatching {
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    blockNetworkLoads = true
                    domStorageEnabled = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setGeolocationEnabled(false)
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
                    @Deprecated("Legacy navigation is denied as well")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?) = true
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) =
                        WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript("typeof window.snapshotChest === 'function'") { result ->
                            if (result == "true") engineReady = true else failureCallback()
                        }
                    }
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                        if (request?.isForMainFrame == true) failureCallback()
                    }
                }
                // Offscreen canvas rendering does not depend on a window, Surface or dialog elevation.
                measure(android.view.View.MeasureSpec.makeMeasureSpec(780, android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY))
                layout(0, 0, 780, 900)
                loadDataWithBaseURL("https://run.invalid/", html, "text/html", "UTF-8", null)
            }
        }.getOrNull()
    }
    if (engine == null) {
        LaunchedEffect(Unit) { failureCallback() }
        return
    }
    DisposableEffect(engine, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> engine.onResume()
                Lifecycle.Event.ON_PAUSE -> engine.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            engine.stopLoading()
            engine.onPause()
            engine.destroy()
        }
    }
    LaunchedEffect(engineReady, rarity, mode, reduceMotion, lifecycle) {
        if (!engineReady) return@LaunchedEffect
        val epoch = SystemClock.elapsedRealtime() - elapsedMs.coerceAtLeast(0L)
        try {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var first = true
                while (isActive) {
                    val began = SystemClock.elapsedRealtime()
                    val duration = SmokePawMotion.durationMs(rarity)
                    val time = when {
                        mode == 0 -> 0L
                        mode == 2 || reduceMotion -> duration
                        else -> (began - epoch).coerceIn(0L, duration)
                    }
                    val result = suspendCancellableCoroutine<String> { continuation ->
                        engine.evaluateJavascript("window.snapshotChest(${rarity.ordinal},$time,$reduceMotion)") {
                            if (continuation.isActive) continuation.resume(it ?: "null")
                        }
                    }
                    image = frames.decode(result)
                    if (first) { first = false; readyCallback() }
                    if (mode != 1 || reduceMotion || time >= duration) break
                    val interval = if (Build.VERSION.SDK_INT <= 29) 33L else 16L
                    delay((interval - (SystemClock.elapsedRealtime() - began)).coerceAtLeast(1L))
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failureCallback() }
    }
    Box(modifier.semantics {
        contentDescription = "${rarity.name.lowercase()} mystery box, Smoke Paw ninja cat reveal"
    }) {
        image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}
