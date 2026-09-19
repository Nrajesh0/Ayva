package com.focusbyrj.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.focusbyrj.app.ui.screens.BlockActivity
import com.focusbyrj.app.util.FocusQuotes
import com.focusbyrj.app.util.TemporaryUnlockManager

object BlockOverlayManager {
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentPackageName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private var timeLeft = 10
    private var countdownRunnable: Runnable? = null
    var isShowing = false
        private set

    fun showBlockScreen(context: Context, packageName: String, quote: String, mode: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { showBlockScreen(context, packageName, quote, mode) }
            return
        }

        if (isShowing && currentPackageName == packageName) return

        com.focusbyrj.app.util.FocusStatsManager.addInterception(context)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
            val success = tryShowOverlay(context, packageName, quote, mode)
            if (success) return
        }

        launchBlockActivity(context, packageName, quote, mode)
    }

    private fun launchBlockActivity(context: Context, packageName: String, quote: String, mode: String) {
        try {
            val intent = Intent(context, BlockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("package_name", packageName)
                putExtra("quote", quote)
                putExtra("mode", mode)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun tryShowOverlay(context: Context, packageName: String, quote: String, mode: String): Boolean {
        hideOverlay() // Ensure previous overlay is cleaned up

        return try {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                },
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.FILL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val isHardMode = mode.equals("HARD", ignoreCase = true)

            val prefs = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
            val savedModeId = prefs.getString("overlay_theme_mode", "system") ?: "system"
            val systemDarkMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDarkMode = when (savedModeId) {
                "dark" -> true
                "light" -> false
                else -> systemDarkMode
            }
            val bgColor = if (isDarkMode) Color.parseColor("#07090E") else Color.parseColor("#F8FAFC")
            val cardBgColor = if (isDarkMode) Color.parseColor("#08FFFFFF") else Color.parseColor("#FFFFFF")
            val cardStrokeColor = if (isDarkMode) Color.parseColor("#1AFFFFFF") else Color.parseColor("#E2E8F0")
            val iconBgColor = if (isDarkMode) Color.parseColor("#141620") else Color.parseColor("#F1F5F9")
            val iconStrokeColor = if (isDarkMode) Color.parseColor("#26FFFFFF") else Color.parseColor("#E2E8F0")
            val primaryTextColor = if (isDarkMode) Color.parseColor("#E2E8F0") else Color.parseColor("#1E293B")
            val secondaryTextColor = if (isDarkMode) Color.parseColor("#CBD5E1") else Color.parseColor("#475569")
            val tertiaryTextColor = if (isDarkMode) Color.parseColor("#94A3B8") else Color.parseColor("#64748B")
            val outlineBtnTextColor = if (isDarkMode) Color.WHITE else Color.parseColor("#0F172A")
            val outlineBtnStrokeColor = if (isDarkMode) Color.parseColor("#26FFFFFF") else Color.parseColor("#CBD5E1")
            val filledBtnBgColor = if (isDarkMode) Color.WHITE else Color.parseColor("#0F172A")
            val filledBtnTextColor = if (isDarkMode) Color.parseColor("#08090E") else Color.WHITE

            var appName = packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            var appIcon: Drawable? = null
            try {
                val pm = context.packageManager
                val info = pm.getApplicationInfo(packageName, 0)
                appName = pm.getApplicationLabel(info).toString()
                appIcon = pm.getApplicationIcon(info)
            } catch (e: Exception) {
            }

            val totalSoftLockSeconds = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                .getInt("soft_lock_duration", 10)
            val unlockMins = context.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)
                .getInt("soft_unlock_duration", 5)

            val displayedQuote = FocusQuotes.getQuoteOrDefault(quote)
            timeLeft = if (isHardMode) 0 else totalSoftLockSeconds

            val scrollView = object : ScrollView(context) {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                        if (event.action == android.view.KeyEvent.ACTION_UP) {
                            goHome(context, packageName)
                        }
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                fitsSystemWindows = false
                @Suppress("DEPRECATION")
                systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or (if (!isDarkMode) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
                )
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                isFillViewport = true
                background = GradientDrawable().apply {
                    setColor(bgColor)
                }
            }

            val centerContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(64, 80, 64, 80)
            }
            scrollView.addView(centerContainer)

            val singleCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = 80f
                    setStroke(2, cardStrokeColor)
                }
                setPadding(64, 96, 64, 96)
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            centerContainer.addView(singleCard, cardParams)

            if (appIcon != null) {
                val iconView = ImageView(context).apply {
                    setImageDrawable(appIcon)
                    background = GradientDrawable().apply {
                        setColor(iconBgColor)
                        cornerRadius = 48f
                        setStroke(2, iconStrokeColor)
                    }
                    setPadding(32, 32, 32, 32)
                }
                val iconParams = LinearLayout.LayoutParams(200, 200).apply {
                    setMargins(0, 0, 0, 64)
                }
                singleCard.addView(iconView, iconParams)
            }

            val nameText = TextView(context).apply {
                text = appName
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryTextColor)
                gravity = Gravity.CENTER
            }
            singleCard.addView(nameText)

            val titleText = TextView(context).apply {
                text = if (isHardMode) "Focus Shielded" else "Mindful Pause"
                textSize = 24f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isHardMode) Color.parseColor("#F43F5E") else secondaryTextColor)
                gravity = Gravity.CENTER
                setPadding(0, 8, 0, 48)
            }
            singleCard.addView(titleText)

            val quoteText = TextView(context).apply {
                text = "“$displayedQuote”"
                textSize = 16f
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
                setTextColor(secondaryTextColor)
                gravity = Gravity.CENTER
                setLineSpacing(6f, 1f)
                setPadding(8, 0, 8, 48)
            }
            singleCard.addView(quoteText)

            val dynamicActionLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }
            singleCard.addView(dynamicActionLayout, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            if (isHardMode) {
                val descText = TextView(context).apply {
                    text = "This app is strictly locked to honor your focus commitment."
                    textSize = 14f
                    setTextColor(tertiaryTextColor)
                    gravity = Gravity.CENTER
                    setLineSpacing(5f, 1f)
                    setPadding(0, 0, 0, 48)
                }
                dynamicActionLayout.addView(descText)

                val exitBtn = Button(context).apply {
                    text = "Exit to Home"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(Color.parseColor("#F43F5E"))
                        cornerRadius = 80f
                    }
                    setPadding(32, 22, 32, 22)
                    setOnClickListener { goHome(context, packageName) }
                }
                dynamicActionLayout.addView(exitBtn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
            } else {
                val timeNum = TextView(context).apply {
                    text = if (timeLeft < 10) "00:0$timeLeft" else "00:$timeLeft"
                    textSize = 42f
                    typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                    setTextColor(secondaryTextColor)
                    gravity = Gravity.CENTER
                    letterSpacing = 0.12f
                }
                dynamicActionLayout.addView(timeNum)

                val progressBar = View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(if (isDarkMode) Color.WHITE else Color.parseColor("#334155"))
                        cornerRadius = 8f
                    }
                }
                val progressParams = LinearLayout.LayoutParams(240, 6).apply {
                    setMargins(0, 24, 0, 16)
                }
                dynamicActionLayout.addView(progressBar, progressParams)

                val timeSub = TextView(context).apply {
                    text = "Mindful pause in progress"
                    textSize = 13f
                    setTextColor(tertiaryTextColor)
                    gravity = Gravity.CENTER
                }
                val timeSubParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 48)
                }
                dynamicActionLayout.addView(timeSub, timeSubParams)

                val exitInitialBtn = Button(context).apply {
                    text = "Exit to Home"
                    setTextColor(outlineBtnTextColor)
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    background = GradientDrawable().apply {
                        setColor(Color.TRANSPARENT)
                        setStroke(2, outlineBtnStrokeColor)
                        cornerRadius = 80f
                    }
                    setPadding(32, 20, 32, 20)
                    setOnClickListener { goHome(context, packageName) }
                }
                dynamicActionLayout.addView(exitInitialBtn, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))

                countdownRunnable = object : Runnable {
                    override fun run() {
                        if (timeLeft > 1) {
                            timeLeft--
                            timeNum.text = if (timeLeft < 10) "00:0$timeLeft" else "00:$timeLeft"
                            handler.postDelayed(this, 1000)
                        } else {
                            timeLeft = 0
                            dynamicActionLayout.removeAllViews()

                            val pauseCompletedTitle = TextView(context).apply {
                                text = "Pause Completed"
                                textSize = 18f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(secondaryTextColor)
                                gravity = Gravity.CENTER
                                letterSpacing = 0.04f
                            }
                            val badgeParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 0, 0, 16)
                            }
                            dynamicActionLayout.addView(pauseCompletedTitle, badgeParams)

                            val askPrompt = TextView(context).apply {
                                text = "Would you like to open $appName for $unlockMins minutes or exit?"
                                textSize = 14f
                                setTextColor(tertiaryTextColor)
                                gravity = Gravity.CENTER
                                setLineSpacing(5f, 1f)
                                setPadding(0, 0, 0, 48)
                            }
                            dynamicActionLayout.addView(askPrompt)

                            val openBtn = Button(context).apply {
                                text = "Open for $unlockMins Minutes"
                                textSize = 15f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(filledBtnTextColor)
                                background = GradientDrawable().apply {
                                    setColor(filledBtnBgColor)
                                    cornerRadius = 80f
                                }
                                setPadding(32, 22, 32, 22)
                                setOnClickListener {
                                    TemporaryUnlockManager.grantUnlock(context, packageName, unlockMins)
                                    hideOverlay()
                                }
                            }
                            dynamicActionLayout.addView(openBtn, LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ))

                            val exitFinalBtn = Button(context).apply {
                                text = "Exit to Home"
                                textSize = 15f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(outlineBtnTextColor)
                                background = GradientDrawable().apply {
                                    setColor(Color.TRANSPARENT)
                                    setStroke(2, outlineBtnStrokeColor)
                                    cornerRadius = 80f
                                }
                                setPadding(32, 20, 32, 20)
                                setOnClickListener { goHome(context, packageName) }
                            }
                            val exitFinalParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                setMargins(0, 12, 0, 0)
                            }
                            dynamicActionLayout.addView(exitFinalBtn, exitFinalParams)
                        }
                    }
                }
                handler.postDelayed(countdownRunnable!!, 1000)
            }

            overlayView = scrollView
            currentPackageName = packageName
            isShowing = true
            windowManager?.addView(overlayView, params)
            true
        } catch (e: Exception) {
            hideOverlay()
            false
        }
    }

    fun hideOverlay(context: Context? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { hideOverlay(context) }
            return
        }

        if (!isShowing && overlayView == null) {
            // If it's the Activity fallback, broadcast the close signal
            if (context != null) {
                val intent = Intent("com.focusbyrj.app.CLOSE_BLOCK_SCREEN").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(intent)
            }
            return
        }
        
        try {
            countdownRunnable?.let { handler.removeCallbacks(it) }
            countdownRunnable = null
            val viewToRemove = overlayView
            if (viewToRemove != null && viewToRemove.isAttachedToWindow) {
                windowManager?.removeView(viewToRemove)
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            overlayView = null
            currentPackageName = null
            isShowing = false
        }
        
        // Also send broadcast just in case the Activity fallback was somehow active simultaneously
        if (context != null) {
            val intent = Intent("com.focusbyrj.app.CLOSE_BLOCK_SCREEN").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(intent)
        }
    }

    private fun goHome(context: Context, pkgName: String? = null) {
        FocusExitTracker.notifyExited(pkgName)
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            }
            context.startActivity(homeIntent)
        } catch (e: Exception) {
            // Ignore
        }
        hideOverlay(context)
    }
}
