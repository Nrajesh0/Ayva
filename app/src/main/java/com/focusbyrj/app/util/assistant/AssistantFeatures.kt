package com.focusbyrj.app.util.assistant

import android.content.Context
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.FocusEconomyManager
import com.focusbyrj.app.util.TalkAction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AssistantFeatures {
    fun appList(context: Context): FeatureTurn.Reply {
        val apps = AssistantFeatureLanguage.apps(context)
        return FeatureTurn.Reply(if (apps.isEmpty()) "No eligible installed apps are visible to Ayva. You can supply an installed app's exact package name."
            else "Installed apps visible to Ayva (first 40):\n" + apps.take(40).joinToString("\n") { "• ${it.second} — ${it.first}" })
    }
    suspend fun answer(context: Context, app: FocusApplication, state: AssistantSession, text: String): FeatureTurn? {
        if (text.equals("show apps", true)) return appList(context)
        AssistantRoutineDialogue.answer(context, app, state, text)?.let { return it }
        AssistantDeviceFeatures.answer(context, app, state, text)?.let { return it }
        AssistantVocabularyFeatures.answer(app, state, text)?.let { return it }
        AssistantLearningFeatures.answer(context, state, text)?.let { return it }
        if (text.lowercase().trimEnd('.', '!', '?') in setOf("what can i claim", "explain my rewards", "my reward balance", "show my rewards", "what rewards can i claim")) {
            val p = context.getSharedPreferences("daily_learning_quests_prefs", 0)
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val current = p.getString("quest_date", "") == today
            val drills = if (current) p.getInt("quest_drills_completed", 0) else 0
            val claimed = current && p.getBoolean("quest_reward_claimed", false)
            fun chest(key: String, claim: String): String = when {
                current && p.getBoolean(claim, false) -> "already claimed today"
                p.getString(key, "") == today -> "earned and ready to claim in the chest window"
                else -> "not earned today"
            }
            val wallet = FocusEconomyManager.profileFlow.value
            state.switchTopic("rewards")
            return FeatureTurn.Reply("Available balance: ${wallet.gold} gold, ${wallet.xp} focus XP (level ${wallet.level}).\nPending focus rewards: ${wallet.pendingGold} gold, ${wallet.pendingXp} XP; pending rewards are not spendable yet. Learning XP is tracked separately.\nDaily test quest: $drills completed today; ${if (claimed) "100 learning XP + 50 gold already awarded" else if (drills >= 1) "the quest manager will settle 100 learning XP + 50 gold when refreshed" else "complete a test to earn 100 learning XP + 50 gold"}.\nEarly Bird chest: ${chest("quest_early_bird_earned_date", "quest_morning_chest_claimed")}.\nNight Owl chest: ${chest("quest_early_bird_earned_date_night", "quest_evening_chest_claimed")}.\nOpen the existing rewards controls to claim or spend; they recheck eligibility. This report does not claim rewards or spend gold.", listOf(TalkAction.AskQuery("/quests", "Open quests"), TalkAction.AskQuery("/box", "Open chests")))
        }
        return null
    }
}
