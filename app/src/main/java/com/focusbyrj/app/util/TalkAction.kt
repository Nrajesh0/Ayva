package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.focusbyrj.app.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Direct navigation action target from Ayva talk / guidance responses.
 */
sealed class TalkAction(
    val label: String,
    val emoji: String,
    val description: String? = null
) {
    data class NavigateAppScreen(
        val route: String,
        val buttonLabel: String,
        val iconEmoji: String = "🚀"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class OpenSystemSetting(
        val intentAction: String,
        val buttonLabel: String,
        val iconEmoji: String = "⚙️",
        val packageUri: Boolean = false
    ) : TalkAction(buttonLabel, iconEmoji)

    data class AskQuery(
        val query: String,
        val buttonLabel: String,
        val iconEmoji: String = "💬"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class DirectPrefUpdate(
        val prefKey: String,
        val prefType: String, // "int", "boolean", "string"
        val targetValue: String,
        val displayValue: String,
        val buttonLabel: String,
        val iconEmoji: String = "⚡"
    ) : TalkAction(buttonLabel, iconEmoji)

    data class RoutineToggle(
        val scheduleId: Int,
        val isEnabled: Boolean,
        val routineName: String,
        val buttonLabel: String,
        val iconEmoji: String = "📅"
    ) : TalkAction(buttonLabel, iconEmoji)

    suspend fun executeAsync(context: Context): Boolean {
        return try {
            when (this) {
                is RoutineToggle -> kotlinx.coroutines.withContext(Dispatchers.IO) {
                    com.focusbyrj.app.util.assistant.AssistantActionExecutor.execute(context,
                        com.focusbyrj.app.util.assistant.AssistantAction.SetRoutine(scheduleId, isEnabled))
                    true
                }
                is DirectPrefUpdate -> kotlinx.coroutines.withContext(Dispatchers.IO) { execute(context) }
                else -> execute(context)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            false
        }
    }

    fun execute(context: Context): Boolean {
        return try {
            when (this) {
                is NavigateAppScreen -> {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        putExtra("navigate_to", route)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    true
                }
                is OpenSystemSetting -> {
                    val intent = Intent(intentAction).apply {
                        if (packageUri) {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                is AskQuery -> {
                    val intent = Intent(context, com.focusbyrj.app.ui.screens.BubbleChatActivity::class.java).apply {
                        putExtra("prefill_query", query)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(intent)
                    true
                }
                is RoutineToggle -> false // Database mutations must be awaited through executeAsync.
                is DirectPrefUpdate -> {
                    com.focusbyrj.app.util.assistant.AssistantPreferences.apply(context, prefKey, prefType, targetValue)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
