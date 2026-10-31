package com.focusbyrj.app.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.*
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.*
import com.focusbyrj.app.ui.screens.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsAndBoundariesTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val apps=listOf(AppRestriction("com.video","Video",true),AppRestriction("com.social","Social",true),AppRestriction("org.reader","Reader",false))

    @Test fun settingsSearchFindsControlsAcrossSectionsAndHandlesPluralQueries() {
        assertTrue(SettingsSearchCatalog.entries.any{it.title=="Persistent Reminder Interval"&&it.matches("reminders")})
        assertTrue(SettingsSearchCatalog.entries.any{it.route=="security"&&it.matches("privacy screenshot")})
        assertTrue(SettingsSearchCatalog.entries.any{it.title=="Hidden Opacity"})
        assertTrue(SettingsSearchCatalog.entries.any{it.title=="App Background Theme"&&it.matches("dark mode")})
        var chosen: SettingsSearchEntry?=null
        compose.setContent {MaterialTheme {SettingsSearchSheet({}, {chosen=it})}}
        compose.onNodeWithTag("settings_search_query").performTextInput("reminders")
        compose.onNodeWithText("Persistent Reminder Interval").performClick()
        compose.runOnIdle {assertEquals("settings",chosen!!.route)}
    }

    @Test fun selectingSearchResultNavigatesAndScrollsToTheActualSetting() {
        compose.setContent {MaterialTheme {
            val nav=rememberNavController()
            NavHost(nav,startDestination="preferences_hub") {
                composable("preferences_hub"){PreferencesHubScreen(nav)}
                composable("settings"){SettingsSearchDestination(nav){SettingsScreen(nav)}}
            }
        }}
        compose.onNodeWithContentDescription("Search settings").performClick()
        compose.onNodeWithTag("settings_search_query").performTextInput("default launch")
        compose.onNodeWithText("Default Launch Tab").performClick()
        compose.waitUntil(5000) {compose.onAllNodesWithText("Default Launch Tab").fetchSemanticsNodes().isNotEmpty()}
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Default Launch Tab").assertIsDisplayed()
    }

    @Test fun collapsedBubbleSettingsExpandForASearchTarget() {
        compose.setContent {MaterialTheme {
            val nav=rememberNavController()
            CompositionLocalProvider(LocalSettingsSearchTarget provides "Hidden Opacity") {BubbleSettingsScreen(nav)}
        }}
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("Hidden Opacity").assertIsDisplayed()
    }

    @Test fun boundarySearchSelectionConfirmationAndDeselectAll() {
        val state=BoundarySelection();var deleted=listOf<AppRestriction>();lateinit var view:View
        compose.setContent {view=LocalView.current;MaterialTheme(colorScheme=darkColorScheme(background=Color.Black)) {
            Column {BoundaryControls(state,apps){deleted=it}
                apps.filter(state::matches).forEach{app->AppRestrictionCard(app,{if(state.selecting)state.toggle(app.packageName)},state.selecting,app.packageName in state.selected,{state.toggle(app.packageName)})}
            }
        }}
        compose.onNodeWithTag("boundary_card_com.video").performTouchInput{longClick()}
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithTag("boundary_card_com.social").performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.onNodeWithText("Deselect all").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
        compose.onNodeWithText("Select all (3)").performClick()
        compose.onNodeWithContentDescription("Delete selected boundaries").performClick()
        compose.runOnIdle {assertTrue(deleted.isEmpty())}
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Cancel selection").performClick()
        compose.onNodeWithContentDescription("Search boundaries").performClick()
        compose.onNodeWithTag("boundary_search").performTextInput("com.video")
        compose.onNodeWithText("Reader").assertDoesNotExist()
        compose.onNodeWithContentDescription("Select boundaries").performClick()
        compose.onNodeWithText("Select all (1)").performClick()
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        capture(view,"boundary-selection")
        compose.onNodeWithContentDescription("Delete selected boundaries").performClick()
        compose.onNodeWithText("Delete",substring=false).performClick()
        compose.runOnIdle{assertEquals(listOf("com.video"),deleted.map{it.packageName})}
    }

    @Test fun realBoundaryCardsEnterSelectionWithoutChangingLocks() {
        var toggles=0
        compose.setContent {MaterialTheme(colorScheme=darkColorScheme()) {
            NormalDashboard(restrictions=apps,onToggle={toggles++},timeRemaining=1500,onToggleSession={},onSetTime={})
        }}
        compose.onNodeWithTag("boundary_card_com.video").performScrollTo().performTouchInput { longClick() }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
        compose.onNodeWithTag("boundary_card_com.social").performScrollTo().performClick()
        compose.onNodeWithText("2 selected").assertIsDisplayed()
        compose.runOnIdle {assertEquals(0,toggles)}
        compose.onNodeWithText("Select all (3)").performClick()
        compose.onNodeWithText("3 selected").assertIsDisplayed()
        compose.onNodeWithText("Deselect all").performClick()
        compose.onNodeWithText("0 selected").assertIsDisplayed()
    }

    @Test fun bulkDeleteRemovesSelectedAppsFromRulesAndRoutinesAndPreservesOtherData() = runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val db=Room.inMemoryDatabaseBuilder(app,FocusDatabase::class.java).allowMainThreadQueries().build()
        val repository=AppRepository(db)
        try {
            apps.forEach{repository.saveApp(it)}
            repository.insertSchedule(FocusSchedule(name="Work",startHour=9,startMinute=0,endHour=17,endMinute=0,daysOfWeek="2,3,4",appsToBlock="com.video|HARD,com.social|SOFT,org.reader|HARD"))
            repository.removeBoundaryPackages(setOf("com.video","com.social"))
            assertEquals(listOf("org.reader"),db.appRestrictionDao().getAllRestrictionsSync().map{it.packageName})
            val routine=db.scheduleDao().getAllSchedulesSync().single()
            assertEquals("org.reader|HARD",routine.appsToBlock)
            assertEquals("Work",routine.name)
            assertEquals(9,routine.startHour)
            repository.removeBoundaryPackages(emptySet())
            assertEquals(1,db.appRestrictionDao().getAllRestrictionsSync().size)
        } finally {db.close()}
    }
    private fun capture(view:View,name:String) {compose.runOnIdle {
        val bmp=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888);view.draw(Canvas(bmp))
        val f=File("build/reports/settings-boundaries/$name.png");f.parentFile!!.mkdirs();f.outputStream().use{bmp.compress(Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
    }}
}
