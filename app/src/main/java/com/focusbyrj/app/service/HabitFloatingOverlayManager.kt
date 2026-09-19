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

package com.focusbyrj.app.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.MainActivity
import com.focusbyrj.app.data.Habit
import com.focusbyrj.app.util.HabitAlarmScheduler
import com.focusbyrj.app.util.HabitMicroCopyProvider
import com.focusbyrj.app.util.StreakManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * Story-Card Style Floating Window for Focus by Rj Habits.
 *
 * Architecture:
 * - [LEFT]: Seamless Lottie Animation Stage (108x116dp) featuring smooth animated Lottie visuals.
 * - [RIGHT]: Habit Info Column with category tag, habit emoji badge + title, XP reward, and progress bar.
 * - [BELOW]: Streamlined 1-tap option to complete the habit + quick snooze button.
 */
object HabitFloatingOverlayManager {
    private const val TAG = "HabitFloatingOverlay"
    private var windowManager: WindowManager? = null
    private var overlayCard: View? = null
    private var currentHabitId: Long = -1L
    private val handler = Handler(Looper.getMainLooper())
    private var autoDismissRunnable: Runnable? = null
    private var activeLottieView: LottieAnimationView? = null
    private var pulseAnimator: ObjectAnimator? = null
    private var lastSelectedLottieAsset: String? = null

    val HABIT_LOTTIE_ASSETS = listOf(
        "habbitautumcat.lottie",
        "habbitboredcat.lottie",
        "habbitcatplayingbyobservingballs.lottie",
        "habbitcatssleeping.lottie",
        "habbitchrismascat.lottie",
        "habbitcutegirlcat.lottie",
        "habbitgojoawseomecat.lottie",
        "habbithappylaptopcat.lottie",
        "habbithollowencat.lottie",
        "habbitpopupcat.lottie",
        "habbitscalmcat.lottie",
        "habbitsppokycat.lottie"
    )

    internal fun getRandomHabitLottieAsset(): String {
        val candidates = if (HABIT_LOTTIE_ASSETS.size > 1 && lastSelectedLottieAsset != null) {
            HABIT_LOTTIE_ASSETS.filter { it != lastSelectedLottieAsset }
        } else {
            HABIT_LOTTIE_ASSETS
        }
        val chosen = candidates.random()
        lastSelectedLottieAsset = chosen
        return chosen
    }

    var isShowing: Boolean = false
        private set

    fun showHabitOverlay(
        context: Context,
        habit: Habit,
        completedCount: Int,
        targetCount: Int,
        currentStreak: Int = 0
    ) {
        UnifiedOverlayCoordinator.enqueueHabit(context, habit, completedCount, targetCount, currentStreak)
    }

    internal fun showHabitOverlayDirect(
        context: Context,
        habit: Habit,
        completedCount: Int,
        targetCount: Int,
        currentStreak: Int = 0
    ) {
        val appContext = context.applicationContext ?: context
        handler.post {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(appContext)) {
                    Log.d(TAG, "Overlay permission not granted; fallback to notification.")
                    UnifiedOverlayCoordinator.onOverlayDismissed(appContext)
                    return@post
                }

                if (isShowing) {
                    dismissOverlayInternal(animated = false)
                }

                currentHabitId = habit.id
                val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: run {
                    UnifiedOverlayCoordinator.onOverlayDismissed(appContext)
                    return@post
                }
                windowManager = wm

                val density = appContext.resources.displayMetrics.density
                val screenWidth = appContext.resources.displayMetrics.widthPixels

                // Story card width: 338dp (fits comfortably across mobile displays with margins)
                val desiredWidth = (338 * density).toInt()
                val cardWidth = min(screenWidth - (32 * density).toInt(), desiredWidth)

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_DIM_BEHIND

                val params = WindowManager.LayoutParams(
                    cardWidth,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                    dimAmount = 0.52f
                    windowAnimations = 0
                }

                val displayStreak = currentStreak

                val cardView = buildStoryModalCard(
                    context = appContext,
                    habit = habit,
                    completedCount = completedCount,
                    targetCount = targetCount,
                    currentStreak = displayStreak,
                    cardWidth = cardWidth,
                    density = density
                )

                overlayCard = cardView
                wm.addView(cardView, params)
                isShowing = true

                // Smooth Entrance
                cardView.alpha = 0f
                cardView.scaleX = 0.92f
                cardView.scaleY = 0.92f
                cardView.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator(1.4f))
                    .start()

                // Ensure any previous auto-dismiss is cleared; no automatic disappearance
                autoDismissRunnable?.let { handler.removeCallbacks(it) }
                autoDismissRunnable = null

            } catch (e: Exception) {
                Log.e(TAG, "Error displaying habit floating overlay", e)
                UnifiedOverlayCoordinator.onOverlayDismissed(appContext)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun buildStoryModalCard(
        context: Context,
        habit: Habit,
        completedCount: Int,
        targetCount: Int,
        currentStreak: Int,
        cardWidth: Int,
        density: Float
    ): View {
        val accentColor = try {
            Color.parseColor(habit.colorHex)
        } catch (_: Exception) {
            Color.parseColor("#38BDF8")
        }

        // --- ROOT STORY CARD CONTAINER ---
        val rootCard = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT)

            // Deep obsidian surface with subtle hairline glow
            val cornerRadius = 24f * density
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setCornerRadius(cornerRadius)
                colors = intArrayOf(
                    Color.parseColor("#161B24"),
                    Color.parseColor("#0F1218")
                )
                gradientType = GradientDrawable.LINEAR_GRADIENT
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                setStroke((1.2f * density).toInt(), Color.argb(55, 255, 255, 255))
            }
            background = bgDrawable
            elevation = 18f * density
            clipToOutline = true
        }

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            val pH = (16 * density).toInt()
            val pV = (22 * density).toInt()
            setPadding(pH, pV, pH, pV)
        }
        rootCard.addView(contentLayout)

        // --- STORY BODY (HORIZONTAL ROW: LEFT LOTTIE STAGE | RIGHT HABIT INFO) ---
        val storyBody = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Determine random animation asset ensuring no two in a row repeat
        val lottieAsset = getRandomHabitLottieAsset()
        val isWhiteBg = lottieAsset == "habbitcutegirlcat.lottie"

        // === [LEFT]: SEAMLESS LOTTIE ANIMATION STAGE ===
        val stageWidth = (118 * density).toInt()
        val stageHeight = (148 * density).toInt()

        val lottieStage = FrameLayout(context).apply {
            val lp = LinearLayout.LayoutParams(stageWidth, stageHeight)
            layoutParams = lp

            // Seamless frosted stage or crisp white background for habbitcutegirlcat
            val stageBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * density
                if (isWhiteBg) {
                    setColor(Color.WHITE)
                    setStroke(
                        (1.5f * density).toInt(),
                        Color.argb(120, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    )
                } else {
                    colors = intArrayOf(
                        Color.argb(130, 24, 32, 45),
                        Color.argb(190, 14, 18, 26)
                    )
                    gradientType = GradientDrawable.LINEAR_GRADIENT
                    orientation = GradientDrawable.Orientation.TL_BR
                    setStroke(
                        (1f * density).toInt(),
                        Color.argb(70, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor))
                    )
                }
            }
            background = stageBg
            clipToOutline = true
        }

        // Lottie Animation View (Seamlessly plays rich vector animations with AppCompat theme context)
        val themedContext = androidx.appcompat.view.ContextThemeWrapper(
            context,
            androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar
        )
        val lottieView = LottieAnimationView(themedContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = if (isWhiteBg) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            repeatCount = LottieDrawable.INFINITE
            repeatMode = LottieDrawable.RESTART
            speed = 1.15f
        }

        // Guarantee continuous infinite looping without stopping or pausing
        lottieView.addLottieOnCompositionLoadedListener {
            lottieView.repeatCount = LottieDrawable.INFINITE
            lottieView.repeatMode = LottieDrawable.RESTART
            lottieView.playAnimation()
        }
        lottieView.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                lottieView.playAnimation()
            }
        })

        try {
            lottieView.setAnimation(lottieAsset)
            lottieView.playAnimation()
        } catch (e: Exception) {
            Log.e(TAG, "Could not load Lottie animation: $lottieAsset, falling back to habbitpopupcat.lottie", e)
            try {
                lottieView.setAnimation("habbitpopupcat.lottie")
                lottieView.playAnimation()
            } catch (_: Exception) {}
        }
        activeLottieView = lottieView
        lottieStage.addView(lottieView)

        storyBody.addView(lottieStage)

        // === [RIGHT]: HABIT INFO & EMOJI COLUMN ===
        val infoColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (14 * density).toInt()
            }
            layoutParams = lp
        }

        // Top Sub-Row: Category Tag Pill + XP Pill + Close Button
        val topMetaRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Category Tag (e.g. "💧 HYDRATION")
        val categoryPill = TextView(context).apply {
            text = getTagTitle(habit)
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            letterSpacing = 0.05f

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(Color.argb(35, 255, 255, 255))
            }
            background = bg
            val pH = (6 * density).toInt()
            val pV = (2 * density).toInt()
            setPadding(pH, pV, pH, pV)
        }
        topMetaRow.addView(categoryPill)

        // Streak Flame Pill (Customized habit streak badge with category blue flame emoji)
        val streakBadge = HabitMicroCopyProvider.getStreakBadge(currentStreak, habit)
        val streakPill = TextView(context).apply {
            text = streakBadge.label
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
            val parsedColor = try {
                Color.parseColor(streakBadge.primaryColorHex)
            } catch (_: Exception) {
                Color.parseColor("#38BDF8")
            }
            val bgColor = Color.argb(streakBadge.bgAlphaInt, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor))
            val strokeColor = Color.argb(140, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor))
            val txtColor = parsedColor

            setTextColor(txtColor)
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(bgColor)
                setStroke((1f * density).toInt(), strokeColor)
            }
            background = bg
            val pH = (6 * density).toInt()
            val pV = (2 * density).toInt()
            setPadding(pH, pV, pH, pV)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = (5 * density).toInt()
            }
            layoutParams = lp
        }
        topMetaRow.addView(streakPill)

        // Spacer
        val metaSpacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
        }
        topMetaRow.addView(metaSpacer)
        infoColumn.addView(topMetaRow)

        // Habit Identity: Emoji Chip + Habit Title
        val identityRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (7 * density).toInt()
                bottomMargin = (3 * density).toInt()
            }
            layoutParams = lp
        }

        // Water/Habit Emoji badge on the right
        val emojiBadge = TextView(context).apply {
            text = habit.iconEmoji.ifBlank { "✨" }
            textSize = 18f
            gravity = Gravity.CENTER
            val badgeSize = (32 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                marginEnd = (7 * density).toInt()
            }
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(45, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)))
                setStroke((1f * density).toInt(), Color.argb(90, 255, 255, 255))
            }
            background = bg
        }
        identityRow.addView(emojiBadge)

        // Habit Title
        val habitTitle = TextView(context).apply {
            text = habit.title.ifBlank { "Daily Ritual" }
            textSize = 15.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F8FAFC"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        identityRow.addView(habitTitle)
        infoColumn.addView(identityRow)

        // Dynamic Micro-Copy Quote (Natural, conversational, contextual, non-repeating)
        val naturalQuote = HabitMicroCopyProvider.getNaturalQuote(
            context = context,
            habit = habit,
            completedCount = completedCount,
            targetCount = targetCount,
            streakDays = currentStreak
        )

        val quoteView = TextView(context).apply {
            text = "“$naturalQuote”"
            textSize = 11.5f
            setTextColor(Color.parseColor("#CBD5E1"))
            setLineSpacing(1.5f * density, 1f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (2 * density).toInt()
                bottomMargin = (4 * density).toInt()
            }
            layoutParams = lp
        }
        infoColumn.addView(quoteView)

        // Subtitle / Progress Counter
        val progressPercent = if (targetCount > 0) {
            ((completedCount.toFloat() / targetCount.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else 0

        val subtitleView = TextView(context).apply {
            val remaining = (targetCount - completedCount).coerceAtLeast(0)
            text = when {
                targetCount <= 1 -> "Daily focus quest • 1 tap"
                remaining == 0 -> "Completed today! 🔥"
                else -> "$completedCount of $targetCount done ($progressPercent%)"
            }
            textSize = 10.5f
            setTextColor(Color.parseColor("#94A3B8"))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        infoColumn.addView(subtitleView)

        // Hairline Progress Bar
        val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (3.5f * density).toInt()
            ).apply {
                topMargin = (5 * density).toInt()
            }
            layoutParams = lp
            max = if (targetCount > 0) targetCount else 1
            progress = completedCount
            progressTintList = ColorStateList.valueOf(accentColor)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(35, 255, 255, 255))
        }
        infoColumn.addView(progressBar)

        storyBody.addView(infoColumn)
        contentLayout.addView(storyBody)

        // === [JUST BELOW]: OPTION TO COMPLETE THE HABIT ===
        val actionRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (50 * density).toInt()
            ).apply {
                topMargin = (18 * density).toInt()
            }
            layoutParams = lp
        }

        // Primary Complete Button
        val primaryBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = (9 * density).toInt()
            }
            layoutParams = lp

            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(accentColor)
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(Color.argb(80, 255, 255, 255)),
                btnBg,
                null
            )
            background = ripple
            elevation = 4f * density
        }

        val actionText = getCompleteActionLabel(habit)
        val primaryBtnText = TextView(context).apply {
            text = actionText
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }
        primaryBtn.addView(primaryBtnText)
        actionRow.addView(primaryBtn)

        // Snooze Button
        val snoozeBtn = TextView(context).apply {
            text = "⏳ 30m"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            val lp = LinearLayout.LayoutParams(
                (72 * density).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 14f * density
                setColor(Color.parseColor("#1B202B"))
                setStroke((1f * density).toInt(), Color.argb(30, 255, 255, 255))
            }
            val ripple = RippleDrawable(
                ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                bg,
                null
            )
            background = ripple
        }
        actionRow.addView(snoozeBtn)
        contentLayout.addView(actionRow)

        // --- BUTTON ACTIONS ---
        primaryBtn.setOnClickListener {
            autoDismissRunnable?.let { handler.removeCallbacks(it) }
            pulseAnimator?.cancel()

            // Subtle Haptic feedback
            try {
                val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib?.vibrate(android.os.VibrationEffect.createOneShot(45L, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vib?.vibrate(45L)
                }
            } catch (_: Exception) {}

            val newCount = completedCount + 1
            progressBar.progress = newCount
            primaryBtnText.text = "✓ Logged!"
            subtitleView.text = if (newCount >= targetCount) "Goal reached today! 🔥" else "$newCount of $targetCount completed"
            subtitleView.setTextColor(Color.parseColor("#4ADE80"))
            quoteView.text = if (newCount >= targetCount) "✓ “Goal completed today! Solid work.”" else "✓ “Logged! Keep the momentum.”"
            quoteView.setTextColor(Color.parseColor("#86EFAC"))

            // Button micro-pulse feedback
            primaryBtn.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(100L)
                .withEndAction {
                    primaryBtn.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                }
                .start()

            // Update Database in background
            val app = context.applicationContext as? FocusApplication
            if (app != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val updatedLog = app.habitRepository.incrementHabitProgress(habit.id)
                        val isGoalMet = updatedLog.completedCount >= habit.targetPerDay
                        val xpReward = if (isGoalMet) 35 else 15
                        val goldReward = if (isGoalMet) 20 else 5
                        com.focusbyrj.app.util.FocusEconomyManager.addRewards(xpReward, goldReward)
                        HabitAlarmScheduler.scheduleHabitReminder(
                            context,
                            habit,
                            updatedLog.lastCompletedTimestamp,
                            isGoalCompletedToday = isGoalMet
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            handler.postDelayed({
                dismissOverlay()
            }, 220L)
        }

        snoozeBtn.setOnClickListener {
            autoDismissRunnable?.let { handler.removeCallbacks(it) }
            pulseAnimator?.cancel()
            primaryBtnText.text = "Snoozed"
            subtitleView.text = "Reminder snoozed for 30m"
            HabitAlarmScheduler.snoozeHabit(context, habit.id, 30)
            handler.postDelayed({
                dismissOverlay()
            }, 220L)
        }

        // --- TOP-RIGHT '✕' MARK ---
        // Prominently placed on the top-right corner so users can dismiss without logging.
        // Clicking it does NOT log the habit, ensuring next logging reminders continue as normal.
        val closeBtn = TextView(context).apply {
            text = "✕"
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "Close reminder without logging"

            val btnSize = (28 * density).toInt()
            val lp = FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (10 * density).toInt()
                marginEnd = (10 * density).toInt()
            }
            layoutParams = lp

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(215, 15, 23, 42)) // Sleek high-contrast dark slate
                setStroke((1.2f * density).toInt(), Color.argb(120, 255, 255, 255))
            }
            val ripple = RippleDrawable(ColorStateList.valueOf(Color.argb(80, 255, 255, 255)), bg, null)
            background = ripple
            elevation = 12f * density

            setOnClickListener {
                autoDismissRunnable?.let { handler.removeCallbacks(it) }
                pulseAnimator?.cancel()
                // Maintain next logging reminders as normal without logging progress
                HabitAlarmScheduler.scheduleHabitReminder(context, habit)
                dismissOverlay()
            }
        }
        rootCard.addView(closeBtn)

        // Dismissal is handled by action buttons (Log, Snooze, or ✕)
        return rootCard
    }

    private fun getTagTitle(habit: Habit): String {
        return when (HabitMicroCopyProvider.getCategory(habit)) {
            HabitMicroCopyProvider.Category.HYDRATION -> "HYDRATION"
            HabitMicroCopyProvider.Category.POSTURE_MOVEMENT -> "POSTURE"
            HabitMicroCopyProvider.Category.MINDFULNESS_BREATH -> "MINDFUL"
            HabitMicroCopyProvider.Category.READING_LEARNING -> "READING"
            HabitMicroCopyProvider.Category.FITNESS_WORKOUT -> "FITNESS"
            HabitMicroCopyProvider.Category.GENERAL_HABIT -> "HABIT"
        }
    }

    private fun getCompleteActionLabel(habit: Habit): String {
        val title = habit.title.lowercase()
        return when {
            habit.iconEmoji == "💧" || title.contains("water") || title.contains("hydrat") -> "✓ Drink & Log (+1)"
            habit.iconEmoji == "💊" || title.contains("med") || title.contains("vitamin") -> "✓ Taken (+1)"
            habit.iconEmoji == "📖" || title.contains("read") || title.contains("book") -> "✓ Completed (+1)"
            else -> "✓ Complete (+1)"
        }
    }

    fun dismissOverlay() {
        handler.post {
            dismissOverlayInternal(animated = true)
        }
    }

    private fun dismissOverlayInternal(animated: Boolean) {
        autoDismissRunnable?.let { handler.removeCallbacks(it) }
        autoDismissRunnable = null
        pulseAnimator?.cancel()
        pulseAnimator = null

        try {
            activeLottieView?.cancelAnimation()
            activeLottieView = null
        } catch (_: Exception) {}

        val card = overlayCard ?: return
        val wm = windowManager ?: return

        if (!isShowing) return

        if (animated) {
            card.animate()
                .alpha(0f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(180L)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        try {
                            if (card.isAttachedToWindow) {
                                wm.removeViewImmediate(card)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error removing overlay view", e)
                        } finally {
                            val ctx = card.context.applicationContext
                            overlayCard = null
                            isShowing = false
                            currentHabitId = -1L
                            UnifiedOverlayCoordinator.onOverlayDismissed(ctx)
                        }
                    }
                })
                .start()
        } else {
            try {
                if (card.isAttachedToWindow) {
                    wm.removeViewImmediate(card)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            } finally {
                val ctx = card.context.applicationContext
                overlayCard = null
                isShowing = false
                currentHabitId = -1L
                UnifiedOverlayCoordinator.onOverlayDismissed(ctx)
            }
        }
    }
}
