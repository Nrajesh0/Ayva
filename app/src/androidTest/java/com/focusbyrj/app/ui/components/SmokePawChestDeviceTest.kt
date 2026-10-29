package com.focusbyrj.app.ui.components

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.focusbyrj.app.util.AptitudeManager
import com.focusbyrj.app.util.DailyQuestManager
import com.focusbyrj.app.util.FocusEconomyManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SmokePawChestDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val artwork get() = compose.onNode(hasContentDescription("Smoke Paw ninja cat reveal", substring = true))
    private fun visibleArtwork(): Boolean = runCatching {
        val pixels = artwork.captureToImage().toPixelMap()
        var bright = 0
        for (y in 0 until pixels.height step 4) for (x in 0 until pixels.width step 4) {
            val color = pixels[x, y]
            if (color.red > .6f && color.green > .5f) bright++
        }
        bright > 5
    }.getOrDefault(false)

    @Test fun allTierArtworkRendersInNativeImages() {
        val ready = AtomicBoolean(false)
        val failed = AtomicBoolean(false)
        val tier = mutableStateOf(ChestRarity.COMMON)
        compose.setContent { MaterialTheme {
            SmokePawChestScene(tier.value, 2, 0, false, { ready.set(true) }, { failed.set(true) }, Modifier.fillMaxSize())
        } }
        for (rarity in ChestRarity.entries) {
            if (rarity != ChestRarity.COMMON) compose.runOnIdle { ready.set(false); tier.value = rarity }
            compose.waitUntil(30_000) { ready.get() || failed.get() }
            assertFalse("The offline rasterizer must produce a native frame", failed.get())
            compose.waitUntil(15_000) { visibleArtwork() }
        }
    }

    @Test fun restoreAndSkipKeepTheSameAwardAndCollectOnlyOnce() {
        val context = compose.activity.applicationContext
        val questPrefs = context.getSharedPreferences("daily_learning_quests_prefs", Context.MODE_PRIVATE)
        val economyPrefs = context.getSharedPreferences("focus_economy_prefs", Context.MODE_PRIVATE)
        val aptitudePrefs = context.getSharedPreferences("aptitude_economy_prefs", Context.MODE_PRIVATE)
        val oldQuest = questPrefs.all.toMap()
        val oldEconomy = economyPrefs.all.toMap()
        val oldAptitude = aptitudePrefs.all.toMap()
        fun restore(prefs: android.content.SharedPreferences, values: Map<String, *>) {
            val edit = prefs.edit().clear()
            for ((key, value) in values) when (value) {
                is String -> edit.putString(key, value)
                is Int -> edit.putInt(key, value)
                is Long -> edit.putLong(key, value)
                is Boolean -> edit.putBoolean(key, value)
                is Float -> edit.putFloat(key, value)
                is Set<*> -> { @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>) }
            }
            edit.commit()
        }
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            questPrefs.edit().putString("quest_date", today)
                .putString("quest_early_bird_earned_date", today)
                .putString("quest_early_bird_earned_date_night", today)
                .putBoolean("quest_morning_chest_claimed", false)
                .putBoolean("quest_evening_chest_claimed", false).commit()
            DailyQuestManager.init(context)
            FocusEconomyManager.init(context)
            AptitudeManager.init(context)
            val visible = mutableStateOf(true)
            var delivered = 0
            val restoration = StateRestorationTester(compose)
            restoration.setContent { MaterialTheme {
                if (visible.value) DuolingoMysteryChestDialog(ChestRarity.COMMON,
                    onDismiss = { visible.value = false }, onClaimed = { delivered++ })
            } }
            compose.waitUntil(30_000) { visibleArtwork() }
            compose.onNodeWithText("Open mystery").performClick()
            val awardedXp = FocusEconomyManager.profileFlow.value.pendingXp
            val awardedGold = FocusEconomyManager.profileFlow.value.pendingGold
            restoration.emulateSavedInstanceStateRestore()
            if (compose.onAllNodesWithText("Skip animation").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithText("Skip animation").performClick()
            compose.waitUntil(30_000) { visibleArtwork() }
            compose.onNodeWithText("A little victory.").assertIsDisplayed()
            compose.waitForIdle()
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(compose.activity.getExternalFilesDir(null), "smoke-paw-device.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            compose.onNodeWithText("Collect reward").performClick()
            compose.runOnIdle {
                assertEquals(1, delivered)
                assertEquals(awardedXp, FocusEconomyManager.profileFlow.value.pendingXp)
                assertEquals(awardedGold, FocusEconomyManager.profileFlow.value.pendingGold)
                assertTrue(DailyQuestManager.stateFlow.value.morningChestClaimed)
                assertFalse(DailyQuestManager.stateFlow.value.eveningChestClaimed)
            }
        } finally {
            restore(questPrefs, oldQuest); restore(economyPrefs, oldEconomy); restore(aptitudePrefs, oldAptitude)
            DailyQuestManager.refreshState(); FocusEconomyManager.init(context); AptitudeManager.init(context)
        }
    }
}
