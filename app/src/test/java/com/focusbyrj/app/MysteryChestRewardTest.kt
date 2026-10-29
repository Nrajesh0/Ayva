package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.components.ChestRarity
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Necessary economy regression: animation callbacks must not award again or consume another chest. */
@RunWith(RobolectricTestRunner::class)
class MysteryChestRewardTest {
    private lateinit var context: Context
    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext<FocusApplication>()
        for (name in listOf("daily_learning_quests_prefs", "focus_economy_prefs", "aptitude_economy_prefs"))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        FocusEconomyManager.init(context)
        AptitudeManager.init(context)
        DailyQuestManager.init(context)
    }
    private fun earnBoth() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        context.getSharedPreferences("daily_learning_quests_prefs", Context.MODE_PRIVATE).edit()
            .putString("quest_date", today).putString("quest_early_bird_earned_date", today)
            .putString("quest_early_bird_earned_date_night", today).commit()
        DailyQuestManager.refreshState()
    }
    @Test fun sourceChestIsConsumedOnceEvenWhenTheRollUpgrades() {
        earnBoth()
        val before = FocusEconomyManager.profileFlow.value
        val morning = requireNotNull(DailyQuestManager.openMysteryChest(ChestRarity.COMMON))
        val afterMorning = FocusEconomyManager.profileFlow.value
        assertEquals(before.pendingXp + morning.xp, afterMorning.pendingXp)
        assertEquals(before.pendingGold + morning.gold, afterMorning.pendingGold)
        assertTrue(DailyQuestManager.stateFlow.value.morningChestClaimed)
        assertFalse(DailyQuestManager.stateFlow.value.eveningChestClaimed)
        assertNull(DailyQuestManager.openMysteryChest(ChestRarity.COMMON))
        assertEquals(afterMorning.pendingXp, FocusEconomyManager.profileFlow.value.pendingXp)
        assertEquals(afterMorning.pendingGold, FocusEconomyManager.profileFlow.value.pendingGold)
        val evening = requireNotNull(DailyQuestManager.openMysteryChest(ChestRarity.RARE))
        assertEquals(afterMorning.pendingXp + evening.xp, FocusEconomyManager.profileFlow.value.pendingXp)
        assertEquals(afterMorning.pendingGold + evening.gold, FocusEconomyManager.profileFlow.value.pendingGold)
        assertTrue(DailyQuestManager.stateFlow.value.eveningChestClaimed)
        assertNull(DailyQuestManager.openMysteryChest(ChestRarity.RARE))
    }
    @Test fun unavailableChestCannotGrantRewards() {
        val before = FocusEconomyManager.profileFlow.value
        assertNull(DailyQuestManager.openMysteryChest(ChestRarity.COMMON))
        assertNull(DailyQuestManager.openMysteryChest(ChestRarity.LEGENDARY))
        assertEquals(before.pendingXp, FocusEconomyManager.profileFlow.value.pendingXp)
        assertEquals(before.pendingGold, FocusEconomyManager.profileFlow.value.pendingGold)
    }
    @Test fun legendaryKeepsFreezeCapAndOverflowRewards() {
        earnBoth()
        AptitudeManager.addStreakFreezes(3)
        val reward = requireNotNull(DailyQuestManager.openMysteryChest(ChestRarity.LEGENDARY))
        assertEquals("SOLARIS", reward.rarityTitle)
        assertTrue(reward.xp in 2500..4999)
        assertTrue(reward.gold - reward.bonusGoldInsteadOfFreeze in 3000..5999)
        assertEquals(600, reward.bonusGoldInsteadOfFreeze)
        assertFalse(reward.streakFreezeAwarded)
        assertEquals(3, AptitudeManager.getStreakFreezesCount())
    }
}
