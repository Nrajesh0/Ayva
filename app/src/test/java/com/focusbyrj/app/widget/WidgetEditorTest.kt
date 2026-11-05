package com.focusbyrj.app.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.components.SettingsSearchCatalog
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = Application::class, qualifiers = "w360dp-h740dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetEditorTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: NoteDatabase
    private val instance = NoteDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previous: Any? = null
    private lateinit var view: View

    @Before fun prepare() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        previous = instance.get(null)
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).allowMainThreadQueries().build()
        instance.set(null, db)
    }
    @After fun cleanup() {
        compose.activity.setContentView(android.widget.FrameLayout(compose.activity))
        compose.waitForIdle()
        instance.set(null, previous)
        db.close()
    }
    private fun choose(row: String, choice: String) {
        compose.onNodeWithText(row).performScrollTo().performClick()
        compose.onNodeWithTag("widget_editor_choices").performScrollToNode(hasText(choice))
        compose.onNodeWithText(choice).performClick()
    }
    private fun capture(name: String) {
        compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.rootView.width, view.rootView.height, Bitmap.Config.ARGB_8888)
            view.rootView.draw(Canvas(bitmap))
            val output = File("build/reports/widget-editors/$name.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test @Config(sdk = [31, 36])
    fun taskEditorGroupsControlsAndRetainsDraftAcrossRestoration() {
        val initial = WidgetConfig(WidgetTheme.OLED, WidgetAccent.GOLD, 73, 21, WidgetTextSize.SIZE_20)
        var saved: WidgetConfig? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { view = LocalView.current; MaterialTheme(colorScheme = darkColorScheme()) {
            WidgetConfigScreen(initial, { saved = it }, {})
        } }
        compose.onNodeWithText("Font size").assertIsDisplayed()
        compose.onNodeWithText("Layout").assertDoesNotExist()
        compose.onNodeWithText("Deep work focus block").assertDoesNotExist()
        capture("tasks-style")
        choose("Theme", "Clean Light")
        choose("Font size", "26 sp")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(81f) }
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[1]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(14f) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Font size").assertIsDisplayed()
        compose.onNodeWithText("26 sp").assertIsDisplayed()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial.copy(theme = WidgetTheme.LIGHT, textSize = WidgetTextSize.SIZE_26, opacityPercent = 81, cornerRadiusDp = 14), saved)
    }

    @Test fun noteEditorKeepsAllOptionsAndAppliesOnlyOnSave() {
        val initial = NoteWidgetConfig(theme = WidgetTheme.WARM, accent = WidgetAccent.CORAL,
            opacityPercent = 66, cornerRadiusDp = 23, matchNoteColor = false,
            filterMode = NoteWidgetFilterMode.PINNED, sortBy = NoteWidgetSortBy.ALPHABETICAL,
            textSize = NoteWidgetTextSize.SIZE_22, padding = NoteWidgetPadding.TIGHT,
            showTitle = false, showQuickAddBar = false, showActionButtons = false,
            showNavHeader = false, adaptiveLayout = false)
        var saved: NoteWidgetConfig? = null
        compose.setContent { view = LocalView.current; MaterialTheme(colorScheme = darkColorScheme()) {
            NoteWidgetConfigScreen(initial, { saved = it }, {})
        } }
        compose.onNodeWithText("Theme").assertDoesNotExist()
        capture("notes-content")
        choose("Show", "Checklists")
        compose.onNodeWithText("Style").performClick()
        compose.onNodeWithText("Match note color").assertIsDisplayed()
        compose.onNodeWithText("Layout").performClick()
        choose("Font size", "36 sp")
        compose.onNodeWithText("Voice & new note buttons").performScrollTo().performClick()
        assertNull(saved)
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial.copy(filterMode = NoteWidgetFilterMode.CHECKLISTS,
            textSize = NoteWidgetTextSize.SIZE_36, showActionButtons = true), saved)
    }

    @Test @Config(sdk = [31, 36])
    fun singleNoteSelectionSurvivesTabSwitchingAndRestoration() {
        val id = kotlinx.coroutines.runBlocking { db.noteDao().insertNote(NoteEntity(title = "Travel checklist", content = "Pack pens")) }
        var saved: NoteWidgetConfig? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { NoteWidgetConfigScreen(NoteWidgetConfig(), { saved = it }, {}) } }
        choose("Show", "Single Note")
        compose.waitForIdle()
        compose.onNodeWithText("Selected note").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Travel checklist").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("widget_editor_choices").performScrollToNode(hasText("Travel checklist"))
        compose.onNode(hasText("Travel checklist") and hasAnyAncestor(hasTestTag("widget_editor_choices"))).performClick()
        compose.onNodeWithText("Layout").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Content").performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(NoteWidgetFilterMode.SPECIFIC, saved!!.filterMode)
        assertEquals(id, saved!!.specificNoteId)
    }

    @Test fun countdownRestoresInvalidDraftThenValidatesAndPreservesOtherOptions() {
        val initial = ExamCountdown(name = "RRB JE", subtitle = "Bring admit card", date = "2026-12-26", time = "10:30",
            zone = "Asia/Kolkata", startDate = "2026-11-01", palette = "CUSTOM", background = "#102030",
            foreground = "#FAFAFA", accent = "#FFAA00", opacity = 74, radius = 31, textSize = 47,
            align = "END", showDate = false, showProgress = false, countUp = false, endedText = "You are ready")
        var saved: ExamCountdown? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent { view = LocalView.current; MaterialTheme(colorScheme = darkColorScheme()) {
            ExamCountdownConfigScreen(initial, emptyList(), { saved = it }, {})
        } }
        capture("countdown-content")
        compose.onNodeWithTag("exam_entry_primary").performScrollTo().performClick()
        compose.onNodeWithText("Exam name").performScrollTo().performTextClearance()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Apply").performClick()
        compose.onNodeWithText("Enter an exam name.").assertIsDisplayed()
        assertNull(saved)
        compose.onNodeWithTag("exam_entry_primary").performScrollTo().performClick()
        compose.onNodeWithText("Exam name").performScrollTo().performTextInput("IBPS exam")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Fine-tune appearance").performScrollTo().performClick()
        compose.onNodeWithText("Background · #RRGGBB").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Text",substring=false).performClick()
        compose.onNodeWithText("Visible details",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Exam date").performScrollTo().performClick()
        compose.onNodeWithText("Done",substring=false).performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial.copy(name = "IBPS exam", showDate = true), saved)
    }

    @Test fun cancelledEditorDoesNotApplyAndBatteryProviderIsRemoved() {
        var saved = false
        var cancelled = false
        compose.setContent { MaterialTheme { WidgetConfigScreen(WidgetConfig(), { saved = true }, { cancelled = true }) } }
        choose("Accent", "Rose Pink")
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertTrue(cancelled)
        assertFalse(saved)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val providers = context.packageManager.queryBroadcastReceivers(Intent("android.appwidget.action.APPWIDGET_UPDATE").setPackage(context.packageName), PackageManager.GET_META_DATA)
        assertEquals(setOf(TodoWidgetProvider::class.java.name, NoteWidgetProvider::class.java.name,
            ExamCountdownProvider::class.java.name), providers.map { it.activityInfo.name }.toSet())
        assertFalse(SettingsSearchCatalog.entries.any { it.id == "widgets.battery" })
    }
}
