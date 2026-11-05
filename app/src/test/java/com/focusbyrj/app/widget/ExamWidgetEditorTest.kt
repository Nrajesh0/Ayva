package com.focusbyrj.app.widget

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.ui.theme.FocusByRjTheme
import com.focusbyrj.app.util.AppThemeManager
import com.focusbyrj.app.util.ThemeMode
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
@Config(sdk=[34], application=Application::class, qualifiers="w393dp-h852dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExamWidgetEditorTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val app get()=ApplicationProvider.getApplicationContext<Application>()
    private lateinit var view:android.view.View
    private fun initial()=ExamCountdown(name="Civil services",date="2027-06-06",time="09:00",zone="Asia/Kolkata",startDate="2026-11-01")
    @Before fun setup() {
        AppThemeManager.init(app);AppThemeManager.setThemeMode(app,ThemeMode.OLED)
        app.getSharedPreferences("exam_countdowns",0).edit().clear().commit()
        app.getSharedPreferences("exam_widget_bindings",0).edit().clear().commit()
        android.provider.Settings.Global.putFloat(app.contentResolver,android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,0f)
    }
    @Test fun addsMultipleExamsAndPreservesTheWholeDraftAcrossRotation() {
        var saved:ExamCountdown?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent { view=LocalView.current;FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Add exam").performScrollTo().performClick()
        compose.onNodeWithText("Exam name").performScrollTo().performTextClearance()
        compose.onNodeWithText("Exam name").performTextInput("Banking prelims")
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Bento").performScrollTo().performClick()
        compose.onNodeWithText("Fine-tune appearance").performScrollTo().performClick()
        compose.onNodeWithText("All colors").performScrollTo().performClick();compose.onNodeWithText("Ocean").performClick()
        compose.onNodeWithText("Background detail").performScrollTo().performClick()
        compose.onNode(hasText("Orbit") and hasAnyAncestor(hasTestTag("widget_editor_choices"))).performClick()
        compose.onNodeWithText("Typeface").performScrollTo().performClick();compose.onNodeWithText("Serif").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(2,saved!!.exams().size);assertEquals("Banking prelims",saved!!.extraExams.single().name)
        assertEquals("LIST",saved!!.displayMode);assertEquals("BENTO",saved!!.design)
        assertEquals("OCEAN",saved!!.palette);assertEquals("ORBIT",saved!!.decoration);assertEquals("SERIF",saved!!.font)
    }
    @Test fun cancellingChangesDoesNotWriteWidgetOrPresetData() {
        var saved=false;var cancelled=false
        compose.setContent { FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=true},{cancelled=true}) } }
        compose.onNodeWithText("Add exam").performScrollTo().performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        assertTrue(cancelled);assertFalse(saved);assertTrue(ExamCountdownStore(app).all().isEmpty())
    }
    @Test fun removingAnExamNeedsConfirmationAndAppliesOnlyToThisDraft() {
        var saved:ExamCountdown?=null
        compose.setContent { FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Add exam").performScrollTo().performClick()
        compose.onNodeWithText("Remove exam").performScrollTo().performClick()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Apply").performClick();assertEquals(2,saved!!.exams().size)
        compose.onNodeWithTag("exam_entry_${saved!!.extraExams.single().id}").performScrollTo().performClick()
        compose.onNodeWithText("Remove exam").performScrollTo().performClick()
        compose.onNodeWithText("Remove",substring=false).performClick()
        compose.onNodeWithText("Apply").performClick();assertEquals(1,saved!!.exams().size)
        assertEquals("Civil services",saved!!.name)
    }
    @Test fun managerListsIndependentWidgetsAndCanDuplicateACompleteConfiguration() {
        val manager=android.appwidget.AppWidgetManager.getInstance(app)
        val provider=android.content.ComponentName(app,ExamCountdownProvider::class.java)
        org.robolectric.Shadows.shadowOf(manager).addInstalledProvider(android.appwidget.AppWidgetProviderInfo().apply {this.provider=provider})
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(101,provider)
        org.robolectric.Shadows.shadowOf(manager).bindAppWidgetId(202,provider)
        val store=ExamCountdownStore(app);store.save(101,initial());store.save(202,initial().copy(name="Banking exam"))
        var duplicated:ExamCountdown?=null
        compose.setContent { view=LocalView.current;FocusByRjTheme { androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            ExamWidgetManagerDialog({}, {}, {duplicated=it})
        } } }
        compose.onNodeWithText("Civil services").assertExists();compose.onNodeWithText("Banking exam").assertExists()
        compose.onNodeWithContentDescription("Options for Banking exam").performClick()
        compose.onNodeWithText("Duplicate").performClick()
        assertEquals("Banking exam",duplicated!!.name)
    }
    @Test fun capturesEditorWithRealLauncherPreviewAndGroupedControls() {
        val config=initial().copy(widgetName="Exam season",displayMode="LIST",extraExams=listOf(
            ExamEvent("second","Banking prelims","Bring admit card","2027-05-14","10:30","Asia/Kolkata","2026-11-01","City examination centre","PRELIMS")),palette="OCEAN")
        compose.setContent { view=LocalView.current;FocusByRjTheme { ExamCountdownConfigScreen(config,emptyList(),{}, {}) } }
        capture("editor-exams")
        compose.onNodeWithTag("exam_preview").assertExists();capture("editor-preview")
        compose.onNodeWithText("Design").performClick();capture("editor-design")
        compose.onNodeWithText("Fine-tune appearance").performScrollTo().assertIsDisplayed();capture("editor-colors")
        compose.onNodeWithText("Details").performClick();capture("editor-details")

    }
    @Test fun simplifiedControlsStayOutOfTheWayAndDesignChangesPreserveExams() {
        var saved:ExamCountdown?=null
        compose.setContent { FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Exam name").assertDoesNotExist()
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Typeface").assertDoesNotExist()
        compose.onNodeWithText("Orbit",substring=false).performScrollTo().performClick()
        compose.onNodeWithText("Coral").performScrollTo().performClick()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial().withDesign("ORBITAL").copy(palette="CORAL"),saved)
    }
    @Test fun boldCategoryAppliesAndRestoresAnIndependentDesign() {
        var saved:ExamCountdown?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent { view=LocalView.current;FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Bold").performScrollTo().performClick()
        compose.onNodeWithText("Poster").performScrollTo().performClick()
        capture("editor-bold")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Poster").assertExists()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial().withDesign("POSTER"),saved)
    }
    @Test fun glassCategoryKeepsBackdropChoicesOutOfTheSavedWidget() {
        var saved:ExamCountdown?=null
        val restoration=StateRestorationTester(compose)
        restoration.setContent { view=LocalView.current;FocusByRjTheme { ExamCountdownConfigScreen(initial(),emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Glass").performScrollTo().performClick()
        compose.onNodeWithText("Frost").performScrollTo().performClick()
        compose.onNodeWithText("Sample wallpaper").performScrollTo().assertExists()
        compose.onNodeWithText("Light",substring=false).performScrollTo().performClick()
        capture("editor-glass")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Light",substring=false).performScrollTo().assertIsSelected()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(initial().withDesign("FROST"),saved)
    }
    @Test fun retiredLayoutsStayReadableWithoutReappearingInThePicker() {
        var saved:ExamCountdown?=null
        val legacy=initial().copy(design="TICKET",palette="CUSTOM",font="SERIF",align="END")
        compose.setContent { view=LocalView.current;FocusByRjTheme { ExamCountdownConfigScreen(legacy,emptyList(),{saved=it},{}) } }
        compose.onNodeWithText("Design").performClick()
        compose.onNodeWithText("Calm",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Colorblock",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Focus",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Fine-tune appearance").performScrollTo().performClick()
        compose.onNodeWithText("All layouts",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Apply").performClick()
        assertEquals(legacy,saved)
    }
    private fun capture(name:String) {
        compose.mainClock.advanceTimeBy(500);compose.waitForIdle()
        compose.runOnIdle {
            val root=view.rootView
            val bitmap=Bitmap.createBitmap(root.width,root.height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
            val file=File("build/reports/exam-widgets/$name.png").apply {parentFile!!.mkdirs()}
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
