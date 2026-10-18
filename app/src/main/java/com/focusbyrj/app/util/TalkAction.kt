package com.focusbyrj.app.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.focusbyrj.app.MainActivity

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
                is RoutineToggle, is DirectPrefUpdate ->
                    com.focusbyrj.app.util.assistant.AssistantCapabilities.reviewedChip(this).execute(context)
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
                        if (intentAction == Settings.ACTION_APP_NOTIFICATION_SETTINGS) {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else if (packageUri) {
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
                is RoutineToggle -> com.focusbyrj.app.util.assistant.AssistantCapabilities.reviewedChip(this).execute(context)
                is DirectPrefUpdate -> {
                    com.focusbyrj.app.util.assistant.AssistantCapabilities.reviewedChip(this).execute(context)
                }
            }
        } catch (e: Exception) {
            false
        }
    }
}
