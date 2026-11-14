package com.focusbyrj.app.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.focusbyrj.app.FocusApplication
import com.focusbyrj.app.util.AyvaAlertCategory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=FocusApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AssistantUpdateSettingsUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun masterAndCategoryTogglesAreClearAndPreserveIndividualChoices() {
        val context=compose.activity
        context.getSharedPreferences(AyvaAlertCategory.PREFS_NAME,0).edit().clear().commit()
        context.getSharedPreferences("bubble_prefs",0).edit().clear().commit()
        compose.setContent { MaterialTheme { AyvaNotificationColorsDialog {} } }
        compose.onNodeWithText("Chat updates & colors").assertIsDisplayed()
        compose.onNodeWithTag("chat_category_briefing").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("chat_updates_master").performClick().assertIsOff()
        compose.onNodeWithTag("chat_category_briefing").assertIsNotEnabled()
        compose.onNodeWithTag("chat_updates_master").performClick().assertIsOn()
        compose.onNodeWithTag("chat_category_briefing").assertIsOff().assertIsEnabled()
        compose.onNodeWithTag("chat_update_categories").performScrollToNode(hasTestTag("chat_category_rewards"))
        compose.onNodeWithTag("chat_category_rewards").assertIsOn().performClick().assertIsOff()
        assertFalse(AyvaAlertCategory.REWARDS.isEnabled(context))
        AyvaAlertCategory.DRILL_PRACTICE.setEnabled(context,false)
        compose.onNodeWithTag("chat_updates_master").performClick().assertIsOff()
        compose.onNodeWithText("Reset All").performClick()
        compose.onNodeWithTag("chat_updates_master").assertIsOn()
        assertTrue(AyvaAlertCategory.entries.all { it.isEnabled(context) })
        assertTrue(context.getSharedPreferences("bubble_prefs",0).getBoolean("streak_notification_enabled",false))
        assertTrue(context.getSharedPreferences("bubble_prefs",0).getBoolean("random_drills_notification_enabled",false))
    }
}
