package com.focusbyrj.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.focusbyrj.app.R
import com.focusbyrj.app.ui.screens.TaskReminderPopupActivity
import com.focusbyrj.app.util.TaskReminderHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TaskReminderOverlayManager {
    private const val TAG = "TaskReminderOverlay"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var currentTaskId: Long = -1L
    private val handler = Handler(Looper.getMainLooper())
    var isShowing = false
        private set

    fun showReminderOverlay(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDetails: String,
        taskDueDate: Long,
        taskTypeStr: String,
        taskRecurrenceStr: String,
        isPersistent: Boolean,
        openRescheduleInitially: Boolean = false
    ) {
        UnifiedOverlayCoordinator.enqueueTask(
            context = context,
            taskId = taskId,
            taskTitle = taskTitle,
            taskDetails = taskDetails,
            taskDueDate = taskDueDate,
            taskTypeStr = taskTypeStr,
            taskRecurrenceStr = taskRecurrenceStr,
            isPersistent = isPersistent,
            openRescheduleInitially = openRescheduleInitially
        )
    }

    internal fun showReminderOverlayDirect(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDetails: String,
        taskDueDate: Long,
        taskTypeStr: String,
        taskRecurrenceStr: String,
        isPersistent: Boolean,
        openRescheduleInitially: Boolean = false
    ) {
        val appContext = context.applicationContext ?: context
        Handler(Looper.getMainLooper()).post {
            try {
                // If already showing this task, update view if needed
                if (isShowing && currentTaskId == taskId) {
                    return@post
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(appContext)) {
                    val success = tryShowOverlay(
                        context = appContext,
                        taskId = taskId,
                        taskTitle = taskTitle,
                        taskDetails = taskDetails,
                        taskDueDate = taskDueDate,
                        taskTypeStr = taskTypeStr,
                        taskRecurrenceStr = taskRecurrenceStr,
                        isPersistent = isPersistent,
                        openRescheduleInitially = openRescheduleInitially
                    )
                    if (success) {
                        Log.d(TAG, "Floating reminder overlay displayed successfully for task $taskId")
                        return@post
                    }
                } else {
                    Log.w(TAG, "Overlay permission not granted. Attempting activity launch.")
                }

                // Notify coordinator that overlay slot is released before fallback activity launch
                UnifiedOverlayCoordinator.onOverlayDismissed(appContext)

                // Fallback to Activity launch if overlay cannot be drawn
                launchPopupActivity(
                    context = appContext,
                    taskId = taskId,
                    taskTitle = taskTitle,
                    taskDetails = taskDetails,
                    taskDueDate = taskDueDate,
                    taskTypeStr = taskTypeStr,
                    taskRecurrenceStr = taskRecurrenceStr,
                    isPersistent = isPersistent,
                    openReschedule = openRescheduleInitially
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in showReminderOverlay", e)
                UnifiedOverlayCoordinator.onOverlayDismissed(appContext)
            }
        }
    }

    private fun launchPopupActivity(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDetails: String,
        taskDueDate: Long,
        taskTypeStr: String,
        taskRecurrenceStr: String,
        isPersistent: Boolean,
        openReschedule: Boolean
    ) {
        try {
            val intent = Intent(context, TaskReminderPopupActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_ID, taskId)
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_TITLE, taskTitle)
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_DETAILS, taskDetails)
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_DUE_DATE, taskDueDate)
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_TYPE, taskTypeStr)
                putExtra(TaskReminderPopupActivity.EXTRA_TASK_RECURRENCE, taskRecurrenceStr)
                putExtra(TaskReminderPopupActivity.EXTRA_IS_PERSISTENT, isPersistent)
                putExtra(TaskReminderPopupActivity.EXTRA_OPEN_RESCHEDULE, openReschedule)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch fallback popup activity", e)
        }
    }

    private fun tryShowOverlay(
        context: Context,
        taskId: Long,
        taskTitle: String,
        taskDetails: String,
        taskDueDate: Long,
        taskTypeStr: String,
        taskRecurrenceStr: String,
        isPersistent: Boolean,
        openRescheduleInitially: Boolean
    ): Boolean {
        hideOverlayDirect()

        return try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
            windowManager = wm

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
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.FILL
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }

            val isDarkMode = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

            // Colors - Minimalist and Professional
            val dimOverlayColor = if (isDarkMode) Color.parseColor("#A6000000") else Color.parseColor("#66000000")
            val cardBgColor = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#FFFFFF")
            val cardStrokeColor = if (isDarkMode) Color.parseColor("#262626") else Color.parseColor("#EAEAEA")
            
            val primaryTextColor = if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#121212")
            val secondaryTextColor = if (isDarkMode) Color.parseColor("#A3A3A3") else Color.parseColor("#737373")
            
            val detailBoxBg = if (isDarkMode) Color.parseColor("#1A1A1A") else Color.parseColor("#FAFAFA")
            val detailBoxStroke = if (isDarkMode) Color.parseColor("#2E2E2E") else Color.parseColor("#F0F0F0")
            
            val btnBgColor = if (isDarkMode) Color.parseColor("#1E1E1E") else Color.parseColor("#F5F5F5")
            val btnStrokeColor = if (isDarkMode) Color.parseColor("#333333") else Color.parseColor("#E5E5E5")
            
            val primaryAccentBg = if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#171717")
            val primaryAccentText = if (isDarkMode) Color.parseColor("#121212") else Color.parseColor("#FFFFFF")
            
            val accentColor = if (isDarkMode) Color.parseColor("#A3A3A3") else Color.parseColor("#737373")

            val density = context.resources.displayMetrics.density
            fun dp(value: Int): Int = (value * density).toInt()

            // 1. Full Screen Backdrop Frame (Touch outside closes overlay)
            val rootLayout = object : FrameLayout(context) {
                override fun dispatchKeyEvent(event: android.view.KeyEvent?): Boolean {
                    if (event?.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                        hideOverlay()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }.apply {
                setBackgroundColor(dimOverlayColor)
                fitsSystemWindows = false
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    hideOverlay()
                }
            }

            // 2. Center Scroll Container for adaptive height
            val scrollWrapper = ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val centerContainer = FrameLayout(context).apply {
                setPadding(dp(20), dp(36), dp(20), dp(36))
            }
            scrollWrapper.addView(
                centerContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            rootLayout.addView(
                scrollWrapper,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )

            // 3. Main Floating Modal Card (Centered in Middle of Screen)
            val mainCard = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(cardBgColor)
                    cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), cardStrokeColor)
                }
                setPadding(dp(24), dp(24), dp(24), dp(24))
                isClickable = true
                isFocusable = true
                setOnClickListener { /* prevent dismissal when touching card */ }
                elevation = dp(24).toFloat()
            }

            val cardParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
            centerContainer.addView(mainCard, cardParams)

            // 4. TOP ROW: Title/Badge + Close Button
            val topRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            mainCard.addView(topRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            // App Logo (Top-Left)
            val logoIcon = ImageView(context).apply {
                var appLogoDrawable = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)
                if (appLogoDrawable == null) {
                    appLogoDrawable = ContextCompat.getDrawable(context, R.drawable.ic_app_logo)
                }
                if (appLogoDrawable != null) {
                    setImageDrawable(appLogoDrawable)
                }
            }
            topRow.addView(logoIcon, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                setMargins(0, 0, dp(12), 0)
            })

            // Header Text Column
            val headerTextCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            val appTitleText = TextView(context).apply {
                text = "Task Reminder"
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(secondaryTextColor)
                isAllCaps = true
                letterSpacing = 0.05f
            }
            headerTextCol.addView(appTitleText)

            val headerColParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            topRow.addView(headerTextCol, headerColParams)

            // Close button (Top-Right)
            val closeBtn = TextView(context).apply {
                text = "✕"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(secondaryTextColor)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    shape = GradientDrawable.OVAL
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    TaskReminderHelper.ignoreTask(context, taskId)
                    hideOverlay()
                }
            }
            topRow.addView(closeBtn, LinearLayout.LayoutParams(dp(24), dp(24)))

            // TASK TITLE
            val titleView = TextView(context).apply {
                text = taskTitle
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryTextColor)
                setLineSpacing(dp(2).toFloat(), 1.1f)
            }
            val titleParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(16), 0, 0)
            }
            mainCard.addView(titleView, titleParams)

            // METADATA (Due Time & Status)
            val timeFormatter = SimpleDateFormat("h:mm a", Locale.getDefault())
            val formattedTime = timeFormatter.format(Date(taskDueDate))
            var metaText = "Due at $formattedTime"
            if (taskRecurrenceStr != "NONE") {
                metaText += " • ${taskRecurrenceStr.lowercase().replaceFirstChar { it.uppercase() }}"
            }
            
            val metaView = TextView(context).apply {
                text = metaText
                textSize = 13f
                setTextColor(secondaryTextColor)
                setLineSpacing(dp(2).toFloat(), 1.1f)
            }
            val metaParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(4), 0, 0)
            }
            mainCard.addView(metaView, metaParams)

            // TASK DETAILS (If available)
            if (taskDetails.isNotBlank()) {
                val detailsBox = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(detailBoxBg)
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), detailBoxStroke)
                    }
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                }
                val detailsText = TextView(context).apply {
                    text = taskDetails
                    textSize = 14f
                    setTextColor(secondaryTextColor)
                    setLineSpacing(dp(4).toFloat(), 1.1f)
                }
                detailsBox.addView(detailsText)

                val detailsParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, dp(16), 0, 0)
                }
                mainCard.addView(detailsBox, detailsParams)
            }


            // INLINE RESCHEDULE CONTAINER (Expands in-place)
            val rescheduleContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(detailBoxBg)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), detailBoxStroke)
                }
                setPadding(dp(16), dp(16), dp(16), dp(16))
                visibility = if (openRescheduleInitially) View.VISIBLE else View.GONE
            }
            val reschParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(16), 0, 0)
            }
            mainCard.addView(rescheduleContainer, reschParams)

            val reschTitle = TextView(context).apply {
                text = "Reschedule Reminder"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(primaryTextColor)
            }
            rescheduleContainer.addView(reschTitle)

            fun executeReschedule(newDueDate: Long, label: String) {
                TaskReminderHelper.rescheduleTask(context, taskId, newDueDate) {
                    kotlin.runCatching {
                        Toast.makeText(context, "Rescheduled to $label", Toast.LENGTH_SHORT).show()
                    }
                }
                hideOverlay()
            }

            // Snooze Buttons Row 1: (+15m, +30m, +1h, +3h)
            val quickRow1 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
            }
            fun createSnoozeBtn(label: String, minutes: Int): TextView {
                return TextView(context).apply {
                    text = label
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(primaryTextColor)
                    background = GradientDrawable().apply {
                        setColor(btnBgColor)
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), btnStrokeColor)
                    }
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        val newTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
                        val formatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(newTime))
                        executeReschedule(newTime, formatted)
                    }
                }
            }

            val btn15m = createSnoozeBtn("+15m", 15)
            val btn30m = createSnoozeBtn("+30m", 30)
            val btn1h = createSnoozeBtn("+1 hr", 60)
            val btn3h = createSnoozeBtn("+3 hrs", 180)

            val chipParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            }
            quickRow1.addView(btn15m, chipParams)
            quickRow1.addView(btn30m, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            quickRow1.addView(btn1h, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            quickRow1.addView(btn3h, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            rescheduleContainer.addView(quickRow1)

            // Snooze Buttons Row 2: (Tonight 8 PM, Tomorrow 9 AM)
            val quickRow2 = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            val btnTonight = TextView(context).apply {
                text = "Tonight (8 PM)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                background = GradientDrawable().apply {
                    setColor(btnBgColor)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), btnStrokeColor)
                }
                setPadding(dp(10), dp(10), dp(10), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val cal = Calendar.getInstance().apply {
                        set(Calendar.HOUR_OF_DAY, 20)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        if (timeInMillis <= System.currentTimeMillis()) {
                            add(Calendar.DAY_OF_YEAR, 1)
                        }
                    }
                    executeReschedule(cal.timeInMillis, "Tonight 8:00 PM")
                }
            }
            val btnTomorrow = TextView(context).apply {
                text = "Tomorrow (9 AM)"
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                background = GradientDrawable().apply {
                    setColor(btnBgColor)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), btnStrokeColor)
                }
                setPadding(dp(10), dp(10), dp(10), dp(10))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    val cal = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, 1)
                        set(Calendar.HOUR_OF_DAY, 9)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }
                    executeReschedule(cal.timeInMillis, "Tomorrow 9:00 AM")
                }
            }
            quickRow2.addView(btnTonight, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            quickRow2.addView(btnTomorrow, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            rescheduleContainer.addView(quickRow2)

            // Smart Text Input for custom reschedule
            val smartInputContainer = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, 0)
                gravity = Gravity.CENTER_VERTICAL
            }
            val smartInput = android.widget.EditText(context).apply {
                hint = "e.g., 'in 2 hours', 'tomorrow at 5pm'"
                textSize = 13f
                setTextColor(primaryTextColor)
                setHintTextColor(secondaryTextColor)
                background = GradientDrawable().apply {
                    setColor(btnBgColor)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), btnStrokeColor)
                }
                setPadding(dp(12), dp(10), dp(12), dp(10))
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                maxLines = 1
                imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            }
            val smartSubmitBtn = android.widget.ImageView(context).apply {
                setImageResource(android.R.drawable.ic_menu_send)
                setColorFilter(primaryTextColor)
                background = GradientDrawable().apply {
                    setColor(btnBgColor)
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), btnStrokeColor)
                }
                setPadding(dp(10), dp(10), dp(10), dp(10))
                isClickable = true
                setOnClickListener {
                    val input = smartInput.text.toString().trim()
                    if (input.isNotEmpty()) {
                        val newTime = TaskReminderHelper.parseSmartDateTime(input)
                        if (newTime != null) {
                            val formatted = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(newTime))
                            executeReschedule(newTime, formatted)
                        } else {
                            Toast.makeText(context, "Couldn't understand time format", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            smartInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    smartSubmitBtn.performClick()
                    true
                } else {
                    false
                }
            }

            smartInputContainer.addView(smartInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(4), 0, dp(4), 0)
            })
            smartInputContainer.addView(smartSubmitBtn, LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                setMargins(dp(0), 0, dp(4), 0)
            })
            rescheduleContainer.addView(smartInputContainer)

            val parsedHintText = TextView(context).apply {
                text = ""
                textSize = 12f
                setTextColor(accentColor)
                visibility = View.GONE
                setPadding(dp(8), dp(6), dp(8), 0)
            }
            rescheduleContainer.addView(parsedHintText)

            smartInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val input = s?.toString()?.trim() ?: ""
                    if (input.isEmpty()) {
                        parsedHintText.visibility = View.GONE
                        return
                    }
                    val parsedTime = TaskReminderHelper.parseSmartDateTime(input)
                    if (parsedTime != null) {
                        val formatter = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
                        parsedHintText.text = "Reschedules to ${formatter.format(Date(parsedTime))}"
                        parsedHintText.visibility = View.VISIBLE
                    } else {
                        parsedHintText.visibility = View.GONE
                    }
                }
            })

            // 5. BOTTOM ACTION BUTTONS: [Ignore] [Reschedule] [Done]
            val actionsRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val actionsParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dp(24), 0, 0)
            }
            mainCard.addView(actionsRow, actionsParams)

            // 1. IGNORE BUTTON
            val ignoreBtn = TextView(context).apply {
                text = "Ignore"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(secondaryTextColor)
                background = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    cornerRadius = dp(10).toFloat()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    TaskReminderHelper.ignoreTask(context, taskId)
                    hideOverlay()
                }
            }
            val ignoreParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                setMargins(0, 0, dp(8), 0)
            }
            actionsRow.addView(ignoreBtn, ignoreParams)

            // 2. RESCHEDULE TOGGLE BUTTON
            val rescheduleBtn = TextView(context).apply {
                text = "Reschedule"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(primaryTextColor)
                background = GradientDrawable().apply {
                    setColor(btnBgColor)
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), btnStrokeColor)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (rescheduleContainer.visibility == View.VISIBLE) {
                        rescheduleContainer.visibility = View.GONE
                    } else {
                        rescheduleContainer.visibility = View.VISIBLE
                    }
                }
            }
            val reschBtnParams = LinearLayout.LayoutParams(0, dp(48), 1.25f).apply {
                setMargins(0, 0, dp(8), 0)
            }
            actionsRow.addView(rescheduleBtn, reschBtnParams)

            // 3. COMPLETE BUTTON (Primary action)
            val completeBtn = TextView(context).apply {
                text = "Done"
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(primaryAccentText)
                background = GradientDrawable().apply {
                    setColor(primaryAccentBg)
                    cornerRadius = dp(10).toFloat()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    TaskReminderHelper.completeTask(context, taskId) {
                        kotlin.runCatching {
                            Toast.makeText(context, "Task completed!", Toast.LENGTH_SHORT).show()
                        }
                    }
                    hideOverlay()
                }
            }
            val completeParams = LinearLayout.LayoutParams(0, dp(48), 1.25f)
            actionsRow.addView(completeBtn, completeParams)

            overlayView = rootLayout
            currentTaskId = taskId
            isShowing = true
            wm.addView(overlayView, params)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Exception in tryShowOverlay", e)
            hideOverlayDirect()
            false
        }
    }

    private fun hideOverlayDirect() {
        var appContext: Context? = null
        try {
            val view = overlayView
            if (view != null && windowManager != null) {
                appContext = view.context.applicationContext
                // Hide keyboard before removing the view to prevent ImeBackDispatcher errors
                val imm = view.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
                
                if (view.isAttachedToWindow) {
                    windowManager?.removeView(view)
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            overlayView = null
            currentTaskId = -1L
            isShowing = false
            appContext?.let { UnifiedOverlayCoordinator.onOverlayDismissed(it) }
        }
    }

    fun hideOverlay() {
        Handler(Looper.getMainLooper()).post {
            hideOverlayDirect()
        }
    }
}
