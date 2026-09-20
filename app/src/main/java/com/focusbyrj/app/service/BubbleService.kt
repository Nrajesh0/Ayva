package com.focusbyrj.app.service

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.screens.BubbleChatActivity
import com.focusbyrj.app.util.BubbleChatManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var displayManager: DisplayManager
    private var bubbleView: View? = null
    private var badgeView: TextView? = null
    private var glowRingView: View? = null
    private var closeView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var closeLayoutParams: WindowManager.LayoutParams? = null

    // Message Preview Pill (Facebook Messenger style preview merged to bubble)
    private var previewPillView: View? = null
    private var previewLayoutParams: WindowManager.LayoutParams? = null
    private val previewDismissHandler = Handler(Looper.getMainLooper())
    private val previewDismissRunnable = Runnable { dismissPreviewPill(animated = true) }
    private var lastPreviewedMessageId: String? = null

    private var lastX = 0
    private var lastY = 300

    private var lastIsLandscape: Boolean? = null
    private var lastScreenWidth: Int = 0
    private var lastScreenHeight: Int = 0

    private val hideHandler = Handler(Looper.getMainLooper())
    private var isPeeking = false
    private var hideRunnable = Runnable { peekBubble() }
    private var peekAnimator: android.animation.ValueAnimator? = null
    private var springXAnim: SpringAnimation? = null

    // Snooze and Auto-hide on permission dialog states
    private var isHiddenForPermission = false
    private var isBubbleAdded = false
    private var isCloseViewAdded = false
    private val snoozeHandler = Handler(Looper.getMainLooper())
    private val snoozeExpiredRunnable = Runnable {
        clearSnooze(this@BubbleService)
        resumeBubble()
    }

    private var taskObserverJob: Job? = null
    private var latestOverdueCount: Int = 0
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            updateLandscapeVisibility(force = false)
        }
    }

    companion object {
        @Volatile
        var isRunning = false
        var isChatOpen = false
        const val ACTION_SETTINGS_CHANGED = "com.focusbyrj.app.BUBBLE_SETTINGS_CHANGED"
        const val ACTION_HIDE_FOR_PERMISSION = "com.focusbyrj.app.HIDE_FOR_PERMISSION"
        const val ACTION_RESTORE_FROM_PERMISSION = "com.focusbyrj.app.RESTORE_FROM_PERMISSION"
        const val ACTION_SNOOZE_BUBBLE = "com.focusbyrj.app.SNOOZE_BUBBLE"
        const val ACTION_RESUME_BUBBLE = "com.focusbyrj.app.RESUME_BUBBLE"
        const val ACTION_SHOW_ALERT_PREVIEW = "com.focusbyrj.app.SHOW_ALERT_PREVIEW"
        const val EXTRA_ALERT_TEXT = "extra_alert_text"

        const val PREFS_KEY_SNOOZED_UNTIL = "bubble_snoozed_until"
        const val DEFAULT_SNOOZE_DURATION_MS = 10 * 60 * 1000L // 10 minutes

        fun isSnoozed(context: Context): Boolean {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            val snoozedUntil = prefs.getLong(PREFS_KEY_SNOOZED_UNTIL, 0L)
            return System.currentTimeMillis() < snoozedUntil
        }

        fun snooze(context: Context, durationMs: Long = DEFAULT_SNOOZE_DURATION_MS) {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            prefs.edit().putLong(PREFS_KEY_SNOOZED_UNTIL, System.currentTimeMillis() + durationMs).apply()
        }

        fun clearSnooze(context: Context) {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            prefs.edit().remove(PREFS_KEY_SNOOZED_UNTIL).apply()
        }
        
        fun startIfEnabled(context: Context, ignoreSnooze: Boolean = false) {
            val prefs = context.getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("bubble_enabled", false) && android.provider.Settings.canDrawOverlays(context)) {
                if (!ignoreSnooze && isSnoozed(context)) {
                    return
                }
                val intent = Intent(context, BubbleService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "com.focusbyrj.app.CHAT_CLOSED" -> {
                    isChatOpen = false
                    addBubbleToWindowManager()
                    bubbleView?.visibility = android.view.View.VISIBLE
                    layoutParams?.x = lastX
                    layoutParams?.y = lastY
                    bubbleView?.let { bv ->
                        try { windowManager.updateViewLayout(bv, layoutParams) } catch (_: Exception) {}
                    }
                    resetHideTimer()
                    updateBadgeCount()
                }
                "com.focusbyrj.app.CHAT_OPENED" -> {
                    isChatOpen = true
                    addBubbleToWindowManager()
                    bubbleView?.visibility = android.view.View.VISIBLE
                    hideHandler.removeCallbacks(hideRunnable)
                    dismissPreviewPill(animated = false)
                    unpeekBubble(animate = false)
                    lastX = layoutParams?.x ?: 0
                    lastY = layoutParams?.y ?: 0
                    layoutParams?.x = (16 * resources.displayMetrics.density).toInt()
                    layoutParams?.y = (48 * resources.displayMetrics.density).toInt()
                    bubbleView?.let { bv ->
                        try { windowManager.updateViewLayout(bv, layoutParams) } catch (_: Exception) {}
                    }
                    updateBadgeCount(0)
                }
                "com.focusbyrj.app.HIDE_BUBBLE" -> {
                    removeBubbleFromWindowManager()
                }
                "com.focusbyrj.app.SHOW_BUBBLE" -> {
                    clearSnooze(this@BubbleService)
                    addBubbleToWindowManager()
                }
                ACTION_HIDE_FOR_PERMISSION -> {
                    hideForPermission()
                }
                ACTION_RESTORE_FROM_PERMISSION -> {
                    restoreFromPermission()
                }
                ACTION_SNOOZE_BUBBLE -> {
                    snoozeBubble()
                }
                ACTION_RESUME_BUBBLE -> {
                    resumeBubble()
                }
                BubbleChatManager.ACTION_UNREAD_COUNT_CHANGED -> {
                    updateBadgeCount()
                }
                ACTION_SETTINGS_CHANGED -> {
                    applyBubbleStyleSettings()
                }
                ACTION_SHOW_ALERT_PREVIEW -> {
                    val alertText = intent?.getStringExtra(EXTRA_ALERT_TEXT)
                    if (!alertText.isNullOrBlank()) {
                        clearSnooze(this@BubbleService)
                        addBubbleToWindowManager()
                        unpeekBubble(animate = false)
                        showNotificationPreviewPill(alertText)
                    }
                }
                Intent.ACTION_USER_PRESENT, Intent.ACTION_SCREEN_ON -> {
                    if (!isChatOpen && !isSnoozed(this@BubbleService) && !isHiddenForPermission) {
                        addBubbleToWindowManager()
                        unpeekBubble(animate = false)
                        resetHideTimer()
                    }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE_BUBBLE -> snoozeBubble()
            ACTION_RESUME_BUBBLE -> resumeBubble()
            ACTION_HIDE_FOR_PERMISSION -> hideForPermission()
            ACTION_RESTORE_FROM_PERMISSION -> restoreFromPermission()
            ACTION_SHOW_ALERT_PREVIEW -> {
                val alertText = intent.getStringExtra(EXTRA_ALERT_TEXT)
                if (!alertText.isNullOrBlank()) {
                    clearSnooze(this)
                    addBubbleToWindowManager()
                    unpeekBubble(animate = false)
                    showNotificationPreviewPill(alertText)
                }
            }
        }
        return START_STICKY
    }
    
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        updateNotification()

        val filter = IntentFilter().apply {
            addAction("com.focusbyrj.app.CHAT_CLOSED")
            addAction("com.focusbyrj.app.CHAT_OPENED")
            addAction("com.focusbyrj.app.HIDE_BUBBLE")
            addAction("com.focusbyrj.app.SHOW_BUBBLE")
            addAction(ACTION_HIDE_FOR_PERMISSION)
            addAction(ACTION_RESTORE_FROM_PERMISSION)
            addAction(ACTION_SNOOZE_BUBBLE)
            addAction(ACTION_RESUME_BUBBLE)
            addAction(ACTION_SHOW_ALERT_PREVIEW)
            addAction(BubbleChatManager.ACTION_UNREAD_COUNT_CHANGED)
            addAction(ACTION_SETTINGS_CHANGED)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        displayManager.registerDisplayListener(displayListener, Handler(Looper.getMainLooper()))
        
        setupBubble()
        startObservingTasks()
        resetHideTimer()
    }

    private fun showCloseView() {
        val cv = closeView ?: return
        val clp = closeLayoutParams ?: return
        if (!isCloseViewAdded && cv.windowToken == null && !cv.isAttachedToWindow) {
            try {
                cv.visibility = View.VISIBLE
                cv.scaleX = 1.0f
                cv.scaleY = 1.0f
                cv.alpha = 1.0f
                (cv.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#88000000"))
                windowManager.addView(cv, clp)
                isCloseViewAdded = true
            } catch (_: Exception) {}
        } else {
            cv.visibility = View.VISIBLE
        }
    }

    private fun hideCloseView() {
        val cv = closeView ?: return
        try {
            cv.visibility = View.GONE
            if (isCloseViewAdded || cv.windowToken != null || cv.isAttachedToWindow) {
                windowManager.removeView(cv)
            }
        } catch (_: Exception) {
        } finally {
            isCloseViewAdded = false
        }
    }

    @Synchronized
    private fun removeBubbleFromWindowManager() {
        dismissPreviewPill(animated = false)
        hideCloseView()
        try {
            bubbleView?.let { bv ->
                if (isBubbleAdded || bv.windowToken != null || bv.isAttachedToWindow) {
                    windowManager.removeView(bv)
                }
            }
        } catch (_: Exception) {
        } finally {
            isBubbleAdded = false
        }
    }

    @Synchronized
    private fun addBubbleToWindowManager() {
        if (isBubbleAdded) return
        if (isSnoozed(this)) return
        if (isHiddenForPermission) return
        val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("bubble_enabled", false)) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val hideInLandscape = prefs.getBoolean("hide_in_landscape", true)
        if (hideInLandscape && isLandscapeMode()) return

        try {
            bubbleView?.let { bv ->
                val lp = layoutParams ?: return
                if (!isBubbleAdded && bv.windowToken == null && !bv.isAttachedToWindow) {
                    bv.visibility = View.VISIBLE
                    bv.scaleX = 1f
                    bv.scaleY = 1f
                    bv.alpha = 1f
                    windowManager.addView(bv, lp)
                    isBubbleAdded = true
                    updateBadgeCount()
                    resetHideTimer()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "Error adding bubble view", e)
        }
    }

    private fun hideForPermission() {
        if (isHiddenForPermission) return
        isHiddenForPermission = true
        removeBubbleFromWindowManager()
    }

    private fun restoreFromPermission() {
        if (!isHiddenForPermission) return
        isHiddenForPermission = false
        if (!isSnoozed(this)) {
            addBubbleToWindowManager()
        }
    }

    fun snoozeBubble(durationMs: Long = DEFAULT_SNOOZE_DURATION_MS) {
        snooze(this, durationMs)
        snoozeHandler.removeCallbacks(snoozeExpiredRunnable)
        snoozeHandler.postDelayed(snoozeExpiredRunnable, durationMs)
        removeBubbleFromWindowManager()
        updateNotification()
        try {
            Toast.makeText(this, "Ayva snoozed for 10 min. Tap notification to resume.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    fun resumeBubble() {
        clearSnooze(this)
        snoozeHandler.removeCallbacks(snoozeExpiredRunnable)
        addBubbleToWindowManager()
        updateNotification()
        try {
            Toast.makeText(this, "Ayva resumed.", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {}
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateLandscapeVisibility(force = true)
    }

    private fun isLandscapeMode(): Boolean {
        val config = resources.configuration
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) return true
        val metrics = resources.displayMetrics
        if (metrics.widthPixels > metrics.heightPixels) return true
        return false
    }

    private fun updateLandscapeVisibility(force: Boolean = false) {
        val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        val hideInLandscape = prefs.getBoolean("hide_in_landscape", true)
        val isLandscape = isLandscapeMode()
        val displayMetrics = resources.displayMetrics
        val currentWidth = displayMetrics.widthPixels
        val currentHeight = displayMetrics.heightPixels

        val orientationChanged = lastIsLandscape != isLandscape
        val dimensionsChanged = lastScreenWidth != currentWidth || lastScreenHeight != currentHeight

        if (!force && !orientationChanged && !dimensionsChanged) {
            // Display refresh rate, HDR, or surface changes during video calls/streaming:
            // Do NOT unhide the bubble or interfere with the hide timer!
            return
        }

        lastIsLandscape = isLandscape
        lastScreenWidth = currentWidth
        lastScreenHeight = currentHeight

        if (hideInLandscape && isLandscape) {
            hideHandler.removeCallbacks(hideRunnable)
            removeBubbleFromWindowManager()
            if (isChatOpen) {
                sendBroadcast(Intent("com.focusbyrj.app.CLOSE_CHAT").setPackage(packageName))
            }
        } else {
            val isEnabled = prefs.getBoolean("bubble_enabled", false)
            if (isEnabled && !isSnoozed(this) && !isHiddenForPermission) {
                addBubbleToWindowManager()
                if (isPeeking) {
                    peekBubble(force = true)
                } else {
                    snapToEdge()
                    resetHideTimer()
                }
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val size = (60 * resources.displayMetrics.density).toInt()
        
        val imageView = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble_launcher_icon)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(size, size)
            elevation = 10f
        }

        // Subtle accent edge ring for peek & hide state
        val glowRing = View(this).apply {
            val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
            val accentColorStr = prefs.getString("bubble_accent_color", "#4ADE80") ?: "#4ADE80"
            val glowIntensity = prefs.getInt("bubble_glow_intensity", 65) / 100f
            val strokeWidthDp = (1.5f + (glowIntensity * 1.5f)).coerceIn(1.2f, 3.0f)
            
            val glowDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.TRANSPARENT)
                try {
                    setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor(accentColorStr))
                } catch (_: Exception) {
                    setStroke((1.5f * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#4ADE80"))
                }
            }
            background = glowDrawable
            layoutParams = FrameLayout.LayoutParams(size, size)
            alpha = 0f
            elevation = 11f
        }
        glowRingView = glowRing

        val badgeSize = (22 * resources.displayMetrics.density).toInt()
        val badgeBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(android.graphics.Color.parseColor("#E53935")) // Red badge
            setStroke((1.5f * resources.displayMetrics.density).toInt(), android.graphics.Color.WHITE)
        }

        val badge = TextView(this).apply {
            background = badgeBackground
            setTextColor(android.graphics.Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding((4 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt(), 0)
            minWidth = badgeSize
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                badgeSize
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = 0
                topMargin = 0
            }
            elevation = 16f
            visibility = View.GONE
        }
        badgeView = badge

        val container = FrameLayout(this).apply {
            clipChildren = false
            clipToPadding = false
            addView(imageView)
            addView(glowRing)
            addView(badge)
        }
        
        bubbleView = container

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            size,
            size,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        val closeSize = (56 * resources.displayMetrics.density).toInt()
        closeView = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#88000000"))
            }
            layoutParams = FrameLayout.LayoutParams(closeSize, closeSize)
            elevation = 10f
            
            addView(TextView(this@BubbleService).apply {
                text = "✕"
                setTextColor(android.graphics.Color.WHITE)
                textSize = 24f
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
            visibility = View.GONE
        }

        val closeLayoutParams = WindowManager.LayoutParams(
            closeSize,
            closeSize,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (40 * resources.displayMetrics.density).toInt()
        }
        this.closeLayoutParams = closeLayoutParams

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop

        bubbleView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    hideHandler.removeCallbacks(hideRunnable)
                    dismissPreviewPill(animated = false)
                    bubbleView?.animate()?.cancel()
                    peekAnimator?.cancel()
                    if (isPeeking) {
                        unpeekBubble(animate = false)
                    }
                    initialX = layoutParams!!.x
                    initialY = layoutParams!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    showCloseView()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isChatOpen) return@setOnTouchListener true // don't drag if chat open
                    
                    peekAnimator?.cancel()
                    
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                        isMoved = true
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        val bubbleSize = (60 * displayMetrics.density).toInt()

                        layoutParams!!.x = (initialX + dx.toInt()).coerceIn(0, screenWidth - bubbleSize)
                        layoutParams!!.y = (initialY + dy.toInt()).coerceIn(0, screenHeight - bubbleSize)
                        windowManager.updateViewLayout(bubbleView, layoutParams)
                        
                        val cSize = (56 * displayMetrics.density).toInt()
                        val bubbleCenterX = layoutParams!!.x + bubbleSize / 2
                        val bubbleCenterY = layoutParams!!.y + bubbleSize / 2
                        val closeCenterX = screenWidth / 2
                        val closeCenterY = screenHeight - (40 * displayMetrics.density).toInt() - cSize / 2
                        
                        val dist = Math.hypot((bubbleCenterX - closeCenterX).toDouble(), (bubbleCenterY - closeCenterY).toDouble())
                        
                        if (dist < cSize * 2.0) {
                            (closeView?.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#FF5252"))
                            closeView?.scaleX = 1.15f
                            closeView?.scaleY = 1.15f
                        } else {
                            (closeView?.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#88000000"))
                            closeView?.scaleX = 1.0f
                            closeView?.scaleY = 1.0f
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    hideCloseView()
                    (closeView?.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#88000000"))
                    closeView?.scaleX = 1.0f
                    closeView?.scaleY = 1.0f

                    if (!isMoved) {
                        view.performClick()
                        if (isChatOpen) {
                            sendBroadcast(Intent("com.focusbyrj.app.CLOSE_CHAT").setPackage(packageName))
                        } else {
                            openChatWindow()
                        }
                    } else if (!isChatOpen) {
                        val displayMetrics = resources.displayMetrics
                        val screenWidth = displayMetrics.widthPixels
                        val screenHeight = displayMetrics.heightPixels
                        val cSize = (56 * displayMetrics.density).toInt()
                        val bubbleSize = (60 * displayMetrics.density).toInt()
                        
                        val bubbleCenterX = layoutParams!!.x + bubbleSize / 2
                        val bubbleCenterY = layoutParams!!.y + bubbleSize / 2
                        val closeCenterX = screenWidth / 2
                        val closeCenterY = screenHeight - (40 * displayMetrics.density).toInt() - cSize / 2
                        
                        val dist = Math.hypot((bubbleCenterX - closeCenterX).toDouble(), (bubbleCenterY - closeCenterY).toDouble())
                        
                        if (dist < cSize * 2.0) {
                            // Dragged to dismiss with animated exit
                            dismissBubbleWithAnimation()
                            return@setOnTouchListener true
                        }

                        val dxTotal = event.rawX - initialTouchX
                        val isLeft = (layoutParams!!.x + bubbleSize / 2) < screenWidth / 2
                        updateBadgePosition(isLeft)
                        
                        if (isLeft && dxTotal < -(20 * displayMetrics.density)) {
                            peekBubble(force = true)
                        } else if (!isLeft && dxTotal > (20 * displayMetrics.density)) {
                            peekBubble(force = true)
                        } else {
                            snapToEdge()
                        }
                    }
                    if (isChatOpen || !isPeeking) {
                        resetHideTimer()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    hideCloseView()
                    (closeView?.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#88000000"))
                    closeView?.scaleX = 1.0f
                    closeView?.scaleY = 1.0f
                    true
                }
                else -> false
            }
        }

        try {
            updateLandscapeVisibility(force = true)
            updateBadgeCount()
        } catch (e: Exception) {
            android.util.Log.e("BubbleService", "Error setting up bubble view", e)
        }
    }

    private fun updateBadgeCount(count: Int = BubbleChatManager.getUnreadCount(this)) {
        val bv = badgeView ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val isLeft = ((layoutParams?.x ?: 0) + (30 * resources.displayMetrics.density)) < screenWidth / 2
        updateBadgePosition(isLeft)
        if (count > 0) {
            // Unread chat messages takes priority (Red)
            (bv.background as? GradientDrawable)?.setColor(android.graphics.Color.parseColor("#E53935"))
            bv.text = if (count > 99) "99+" else count.toString()
            bv.visibility = View.VISIBLE
            if (isPeeking) {
                unpeekBubble(animate = true)
            }

            // Check if there's a new unread message to show in the Messenger-style preview pill
            val latestMsg = BubbleChatManager.getMessages(this).lastOrNull { !it.isUser }
            if (latestMsg != null && latestMsg.id != lastPreviewedMessageId && !isChatOpen) {
                lastPreviewedMessageId = latestMsg.id
                val previewText = latestMsg.text.trim()
                if (previewText.isNotEmpty()) {
                    val category = com.focusbyrj.app.util.AyvaAlertCategory.infer(
                        text = previewText,
                        isMorning = latestMsg.isMorningBrief,
                        isEvening = latestMsg.isEveningBrief,
                        isDrill = latestMsg.isArithmetic || latestMsg.isDrillSummary,
                        isStreakPrompt = latestMsg.isStreakPrompt,
                        messageId = latestMsg.id
                    )
                    showNotificationPreviewPill(previewText, category)
                }
            }
            return
        }

        // When unread messages are cleared, dismiss any preview pill
        dismissPreviewPill(animated = true)

        // Hide badge when there are no unread chat messages
        bv.visibility = View.GONE
    }

    private fun updateBadgePosition(isLeft: Boolean) {
        val bv = badgeView ?: return
        val lp = bv.layoutParams as? FrameLayout.LayoutParams ?: return
        val desiredGravity = if (isLeft) (Gravity.TOP or Gravity.END) else (Gravity.TOP or Gravity.START)
        if (lp.gravity != desiredGravity) {
            lp.gravity = desiredGravity
            bv.layoutParams = lp
        }
    }

    private fun showNotificationPreviewPill(rawText: String, category: com.focusbyrj.app.util.AyvaAlertCategory = com.focusbyrj.app.util.AyvaAlertCategory.infer(rawText)) {
        if (isChatOpen) return
        if (!category.isEnabled(this)) return
        val currentBubbleView = bubbleView ?: return
        val currentLayoutParams = layoutParams ?: return

        // Clean up markdown / bullet markers for a clean single-line notification pill
        val cleanText = rawText
            .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
            .replace(Regex("\\*(.*?)\\*"), "$1")
            .replace(Regex("^#+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[\\s*\\-•]+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("\n+"), " ")
            .trim()

        val displayText = if (cleanText.length > 140) cleanText.take(137) + "…" else cleanText
        if (displayText.isEmpty()) return

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        val bubbleSize = (60 * density).toInt()

        if (isPeeking) {
            unpeekBubble(animate = false)
        }

        val isLeft = (currentLayoutParams.x + bubbleSize / 2) < screenWidth / 2
        val bubbleX = if (isLeft) 0 else (screenWidth - bubbleSize)
        val bubbleY = currentLayoutParams.y
        val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        val calloutView = (previewPillView as? MessengerBubbleNotificationView) ?: MessengerBubbleNotificationView(this).also {
            previewPillView = it
            it.setOnClickListener {
                dismissPreviewPill(animated = false)
                openChatWindow()
            }
        }
        calloutView.bind(displayText, isLeft, isDark, category)

        val maxCalloutWidth = (screenWidth - bubbleSize - (24 * density).toInt()).coerceIn((170 * density).toInt(), (270 * density).toInt())
        calloutView.measure(
            View.MeasureSpec.makeMeasureSpec(maxCalloutWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pillWidth = calloutView.measuredWidth
        val pillHeight = calloutView.measuredHeight

        // Perfectly merge the speech beak tip with the chat head circle rim
        val pillX = if (isLeft) {
            bubbleX + bubbleSize - (2 * density).toInt()
        } else {
            bubbleX - pillWidth + (2 * density).toInt()
        }.coerceIn((4 * density).toInt(), screenWidth - pillWidth - (4 * density).toInt())

        val pillY = (bubbleY + (bubbleSize - pillHeight) / 2)
            .coerceIn((20 * density).toInt(), screenHeight - pillHeight - (20 * density).toInt())

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val pillParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = pillX
            y = pillY
        }
        previewLayoutParams = pillParams

        // Add to WindowManager if not already attached
        if (calloutView.windowToken == null) {
            try {
                windowManager.addView(calloutView, pillParams)
            } catch (_: Exception) {
                return
            }
        } else {
            try {
                windowManager.updateViewLayout(calloutView, pillParams)
            } catch (_: Exception) {}
        }

        // Messenger-style entrance: pop out from the beak anchor tip touching the chat avatar
        calloutView.animate().cancel()
        calloutView.alpha = 0f
        calloutView.pivotX = if (isLeft) 0f else pillWidth.toFloat()
        calloutView.pivotY = pillHeight / 2f
        calloutView.scaleX = 0.15f
        calloutView.scaleY = 0.15f
        calloutView.visibility = View.VISIBLE

        calloutView.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(280)
            .setInterpolator(android.view.animation.OvershootInterpolator(1.25f))
            .start()

        // Auto-dismiss after 8.5 seconds for comfortable reading
        previewDismissHandler.removeCallbacks(previewDismissRunnable)
        previewDismissHandler.postDelayed(previewDismissRunnable, 8500L)
    }

    private fun dismissPreviewPill(animated: Boolean) {
        previewDismissHandler.removeCallbacks(previewDismissRunnable)
        val pill = previewPillView ?: return
        if (pill.windowToken == null || pill.visibility != View.VISIBLE) return

        if (animated) {
            val isLeft = ((layoutParams?.x ?: 0) + (30 * resources.displayMetrics.density)) < resources.displayMetrics.widthPixels / 2
            pill.pivotX = if (isLeft) 0f else pill.width.toFloat()
            pill.pivotY = pill.height / 2f
            pill.animate()
                .alpha(0f)
                .scaleX(0.15f)
                .scaleY(0.15f)
                .setDuration(200)
                .withEndAction {
                    pill.visibility = View.GONE
                    try { windowManager.removeView(pill) } catch (_: Exception) {}
                    previewPillView = null
                }
                .start()
        } else {
            pill.animate().cancel()
            pill.visibility = View.GONE
            try { windowManager.removeView(pill) } catch (_: Exception) {}
            previewPillView = null
        }
    }

    private fun applyBubbleStyleSettings() {
        val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("bubble_enabled", false)
        if (!isEnabled || isSnoozed(this) || isHiddenForPermission) {
            removeBubbleFromWindowManager()
            return
        } else {
            addBubbleToWindowManager()
        }

        val accentColorStr = prefs.getString("bubble_accent_color", "#4ADE80") ?: "#4ADE80"
        val glowIntensity = (prefs.getInt("bubble_glow_intensity", 65) / 100f).coerceIn(0f, 1f)
        val hiddenOpacity = (prefs.getInt("bubble_hidden_opacity", 85) / 100f).coerceIn(0.1f, 1f)
        val hiddenAmountRatio = (prefs.getInt("bubble_hidden_amount", 60) / 100f).coerceIn(0.2f, 0.9f)
        val strokeWidthDp = (1.5f + (glowIntensity * 1.5f)).coerceIn(1.2f, 3.0f)

        glowRingView?.let { gView ->
            val glowDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.TRANSPARENT)
                try {
                    setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor(accentColorStr))
                } catch (_: Exception) {
                    setStroke((1.5f * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#4ADE80"))
                }
            }
            gView.background = glowDrawable
        }

        if (isPeeking) {
            peekBubble(force = true)
        }

        resetHideTimer()
    }

    private fun peekBubble(force: Boolean = false) {
        if (isChatOpen) return
        if (!force && isPeeking) return
        val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        if (!force && !prefs.getBoolean("auto_hide_enabled", false)) return

        isPeeking = true
        val displayMetrics = resources.displayMetrics
        val size = (60 * displayMetrics.density).toInt()
        val screenWidth = displayMetrics.widthPixels
        
        val hiddenAmountRatio = (prefs.getInt("bubble_hidden_amount", 60) / 100f).coerceIn(0.2f, 0.9f)
        val hiddenOpacity = (prefs.getInt("bubble_hidden_opacity", 85) / 100f).coerceIn(0.1f, 1f)
        val glowIntensity = (prefs.getInt("bubble_glow_intensity", 65) / 100f).coerceIn(0f, 1f)
        val accentColorStr = prefs.getString("bubble_accent_color", "#4ADE80") ?: "#4ADE80"
        val strokeWidthDp = (1.5f + (glowIntensity * 1.5f)).coerceIn(1.2f, 3.0f)

        // Ensure stroke and color are up to date
        glowRingView?.let { gView ->
            val glowDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(android.graphics.Color.TRANSPARENT)
                try {
                    setStroke((strokeWidthDp * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor(accentColorStr))
                } catch (_: Exception) {
                    setStroke((1.5f * resources.displayMetrics.density).toInt(), android.graphics.Color.parseColor("#4ADE80"))
                }
            }
            gView.background = glowDrawable
        }

        val isLeft = (layoutParams?.x ?: 0) < screenWidth / 2
        updateBadgePosition(isLeft)
        val hideOffset = (size * hiddenAmountRatio).toInt()
        val targetX = if (isLeft) -hideOffset else (screenWidth - size + hideOffset)
        
        dismissPreviewPill(animated = true)

        glowRingView?.animate()
            ?.alpha(glowIntensity)
            ?.setDuration(300)
            ?.start()

        bubbleView?.animate()
            ?.translationX(0f)
            ?.alpha(hiddenOpacity)
            ?.setDuration(300)
            ?.start()
            
        peekAnimator?.cancel()
        val currentX = layoutParams?.x ?: 0
        peekAnimator = android.animation.ValueAnimator.ofInt(currentX, targetX).apply {
            duration = 300
            addUpdateListener { anim ->
                layoutParams?.x = anim.animatedValue as Int
                try { windowManager.updateViewLayout(bubbleView, layoutParams) } catch (e: Exception) {}
            }
            start()
        }
    }

    private fun unpeekBubble(animate: Boolean) {
        if (!isPeeking) return
        isPeeking = false
        
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val size = (60 * displayMetrics.density).toInt()
        val isLeft = (layoutParams?.x ?: 0) < screenWidth / 2
        val targetX = if (isLeft) 0 else (screenWidth - size)
        
        peekAnimator?.cancel()
        
        if (animate) {
            glowRingView?.animate()
                ?.alpha(0f)
                ?.setDuration(200)
                ?.start()
            bubbleView?.animate()
                ?.translationX(0f)
                ?.alpha(1.0f)
                ?.setDuration(250)
                ?.start()
                
            val currentX = layoutParams?.x ?: 0
            peekAnimator = android.animation.ValueAnimator.ofInt(currentX, targetX).apply {
                duration = 250
                addUpdateListener { anim ->
                    layoutParams?.x = anim.animatedValue as Int
                    try { windowManager.updateViewLayout(bubbleView, layoutParams) } catch (e: Exception) {}
                }
                start()
            }
        } else {
            glowRingView?.animate()?.cancel()
            glowRingView?.alpha = 0f
            bubbleView?.animate()?.cancel()
            bubbleView?.translationX = 0f
            bubbleView?.alpha = 1.0f
            
            layoutParams?.x = targetX
            try { windowManager.updateViewLayout(bubbleView, layoutParams) } catch (e: Exception) {}
        }
    }

    private fun resetHideTimer() {
        hideHandler.removeCallbacks(hideRunnable)
        if (isChatOpen) return
        val prefs = getSharedPreferences("bubble_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("auto_hide_enabled", false)) {
            val durationSecs = prefs.getInt("auto_hide_duration_sec", 3)
            hideHandler.postDelayed(hideRunnable, durationSecs * 1000L)
        }
    }

    private fun startObservingTasks() {
        taskObserverJob?.cancel()
        taskObserverJob = serviceScope.launch {
            try {
                val app = application as? FocusApplication ?: return@launch
                app.taskRepository.allTasks.collectLatest { tasks ->
                    val now = System.currentTimeMillis()
                    latestOverdueCount = tasks.count { !it.isCompleted && it.dueDate != null && it.dueDate < now }
                    updateBadgeCount()
                }
            } catch (_: Exception) {}
        }
    }

    private fun snapToEdge() {
        isPeeking = false
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val size = (60 * displayMetrics.density).toInt()
        val centerX = screenWidth / 2

        val currentX = layoutParams?.x ?: 0
        val isLeft = currentX < centerX
        updateBadgePosition(isLeft)
        val targetX = if (isLeft) 0 else (screenWidth - size)
        glowRingView?.animate()?.cancel()
        glowRingView?.alpha = 0f
        bubbleView?.animate()?.cancel()
        bubbleView?.translationX = 0f
        bubbleView?.alpha = 1.0f
        
        peekAnimator?.cancel()
        springXAnim?.cancel()

        val dummyView = bubbleView ?: return
        val floatProp = object : FloatPropertyCompat<View>("bubbleLayoutX") {
            override fun getValue(v: View): Float = (layoutParams?.x ?: 0).toFloat()
            override fun setValue(v: View, value: Float) {
                layoutParams?.x = value.toInt()
                try {
                    windowManager.updateViewLayout(bubbleView, layoutParams)
                } catch (_: Exception) {}
            }
        }

        springXAnim = SpringAnimation(dummyView, floatProp, targetX.toFloat()).apply {
            spring = SpringForce(targetX.toFloat()).apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_LOW
            }
            start()
        }
    }

    private fun dismissBubbleWithAnimation() {
        dismissPreviewPill(animated = true)
        closeView?.animate()
            ?.scaleX(0f)
            ?.scaleY(0f)
            ?.alpha(0f)
            ?.setDuration(180)
            ?.withEndAction {
                hideCloseView()
            }
            ?.start()

        bubbleView?.animate()
            ?.scaleX(0f)
            ?.scaleY(0f)
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                snoozeBubble()
            }
            ?.start()
    }

    private fun openChatWindow() {
        clearSnooze(this)
        dismissPreviewPill(animated = false)
        BubbleChatManager.clearUnread(this)
        updateBadgeCount(0)
        val intent = Intent(this, BubbleChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (isSnoozed(this)) return
        // Ensure the service restarts if the app task was swiped away from recent apps
        val restartServiceIntent = Intent(applicationContext, BubbleService::class.java).also {
            it.setPackage(packageName)
        }
        val restartServicePendingIntent = android.app.PendingIntent.getService(this, 1, restartServiceIntent, android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
        alarmService?.set(android.app.AlarmManager.ELAPSED_REALTIME, android.os.SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent)
    }

    private fun updateNotification() {
        val channelId = "ayva_bubble_fg_channel"
        val channelName = "Ayva Floating Bubble"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps Ayva floating bubble active across all apps"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val mainIntent = Intent(this, com.focusbyrj.app.MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE)

        val isSnoozedCurrently = isSnoozed(this)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)

        if (isSnoozedCurrently) {
            builder.setContentTitle("Ayva is snoozed (10m)")
                .setContentText("Tap Resume to show the floating bubble again")

            val resumeIntent = Intent(this, BubbleService::class.java).apply {
                action = ACTION_RESUME_BUBBLE
            }
            val resumePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, 101, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getService(this, 101, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
        } else {
            builder.setContentTitle("Ayva is active")
                .setContentText("Tap to open RuN")

            val snoozeIntent = Intent(this, BubbleService::class.java).apply {
                action = ACTION_SNOOZE_BUBBLE
            }
            val snoozePendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(this, 102, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            } else {
                PendingIntent.getService(this, 102, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            builder.addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze (10m)", snoozePendingIntent)
        }

        val notification = builder.build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(2001, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(2001, notification)
            }
        } catch (_: Exception) {
            startForeground(2001, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        taskObserverJob?.cancel()
        springXAnim?.cancel()
        hideHandler.removeCallbacks(hideRunnable)
        snoozeHandler.removeCallbacks(snoozeExpiredRunnable)
        previewDismissHandler.removeCallbacks(previewDismissRunnable)
        kotlin.runCatching { unregisterReceiver(receiver) }
        try {
            displayManager.unregisterDisplayListener(displayListener)
        } catch (_: Exception) {}
        previewPillView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        bubbleView?.let {
            try {
                if (isBubbleAdded || it.windowToken != null || it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {}
            isBubbleAdded = false
        }
        closeView?.let {
            try {
                if (isCloseViewAdded || it.windowToken != null || it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            } catch (e: Exception) {}
            isCloseViewAdded = false
        }
    }
}

class MessengerCalloutDrawable(
    var isLeft: Boolean,
    var backgroundColor: Int,
    var strokeColor: Int,
    var strokeWidth: Float,
    var cornerRadius: Float,
    var tailWidth: Float,
    var tailHeight: Float
) : android.graphics.drawable.Drawable() {

    private val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    private val strokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
    }
    private val path = android.graphics.Path()

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        bgPaint.color = backgroundColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = strokeWidth

        path.reset()
        val cy = b.exactCenterY()
        val halfStroke = strokeWidth / 2f
        val cr = cornerRadius.coerceAtMost((b.height() - strokeWidth) / 2f)

        if (isLeft) {
            // Chat head is on the left; pointer tail on left edge pointing left
            val l = b.left.toFloat() + tailWidth + halfStroke
            val r = b.right.toFloat() - halfStroke
            val t = b.top.toFloat() + halfStroke
            val bot = b.bottom.toFloat() - halfStroke

            path.moveTo(l + cr, t)
            path.lineTo(r - cr, t)
            path.arcTo(android.graphics.RectF(r - 2 * cr, t, r, t + 2 * cr), 270f, 90f, false)
            path.lineTo(r, bot - cr)
            path.arcTo(android.graphics.RectF(r - 2 * cr, bot - 2 * cr, r, bot), 0f, 90f, false)
            path.lineTo(l + cr, bot)
            path.arcTo(android.graphics.RectF(l, bot - 2 * cr, l + 2 * cr, bot), 90f, 90f, false)
            path.lineTo(l, cy + tailHeight / 2f)
            path.lineTo(b.left.toFloat() + halfStroke, cy) // Pointer tip docked to avatar
            path.lineTo(l, cy - tailHeight / 2f)
            path.lineTo(l, t + cr)
            path.arcTo(android.graphics.RectF(l, t, l + 2 * cr, t + 2 * cr), 180f, 90f, false)
            path.close()
        } else {
            // Chat head is on the right; pointer tail on right edge pointing right
            val l = b.left.toFloat() + halfStroke
            val r = b.right.toFloat() - tailWidth - halfStroke
            val t = b.top.toFloat() + halfStroke
            val bot = b.bottom.toFloat() - halfStroke

            path.moveTo(l + cr, t)
            path.lineTo(r - cr, t)
            path.arcTo(android.graphics.RectF(r - 2 * cr, t, r, t + 2 * cr), 270f, 90f, false)
            path.lineTo(r, cy - tailHeight / 2f)
            path.lineTo(b.right.toFloat() - halfStroke, cy) // Pointer tip docked to avatar
            path.lineTo(r, cy + tailHeight / 2f)
            path.lineTo(r, bot - cr)
            path.arcTo(android.graphics.RectF(r - 2 * cr, bot - 2 * cr, r, bot), 0f, 90f, false)
            path.lineTo(l + cr, bot)
            path.arcTo(android.graphics.RectF(l, bot - 2 * cr, l + 2 * cr, bot), 90f, 90f, false)
            path.lineTo(l, t + cr)
            path.arcTo(android.graphics.RectF(l, t, l + 2 * cr, t + 2 * cr), 180f, 90f, false)
            path.close()
        }

        canvas.drawPath(path, bgPaint)
        if (strokeWidth > 0f) {
            canvas.drawPath(path, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        bgPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

class MessengerBubbleNotificationView(context: Context) : FrameLayout(context) {

    private val contentLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
    }

    private val headerTextView = TextView(context).apply {
        textSize = 11.5f
        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        text = "Ayva"
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private val bodyTextView = TextView(context).apply {
        textSize = 13.5f
        typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
        maxLines = 3
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    init {
        elevation = 14f * resources.displayMetrics.density
        clipChildren = false
        clipToPadding = false

        contentLayout.addView(headerTextView)
        contentLayout.addView(bodyTextView)
        addView(contentLayout)
    }

    fun bind(
        text: String, 
        isLeft: Boolean, 
        isDark: Boolean,
        category: com.focusbyrj.app.util.AyvaAlertCategory = com.focusbyrj.app.util.AyvaAlertCategory.infer(text)
    ) {
        val density = resources.displayMetrics.density
        bodyTextView.text = text
        headerTextView.text = category.defaultTitle

        // Apply categorical aesthetic colors with high contrast that never drown text
        val bgColor = category.getParsedBgColor(context)
        val strokeColor = category.getParsedStrokeColor(context)
        val headerColor = category.getParsedHeaderColor(context)
        val textColor = category.getParsedBodyColor(context)

        headerTextView.setTextColor(headerColor)
        bodyTextView.setTextColor(textColor)

        val tailW = 8f * density
        val tailH = 12f * density
        val radius = 16f * density
        val sWidth = 1.2f * density

        background = MessengerCalloutDrawable(
            isLeft = isLeft,
            backgroundColor = bgColor,
            strokeColor = strokeColor,
            strokeWidth = sWidth,
            cornerRadius = radius,
            tailWidth = tailW,
            tailHeight = tailH
        )

        val padLeft = ((if (isLeft) 18 else 12) * density).toInt()
        val padRight = ((if (isLeft) 12 else 18) * density).toInt()
        val padTop = (8 * density).toInt()
        val padBottom = (8 * density).toInt()

        contentLayout.setPadding(padLeft, padTop, padRight, padBottom)
    }
}

