package com.focusbyrj.app.ui.components

import android.app.Application
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.focusbyrj.app.ui.screens.AddTaskDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class ProductivityNavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun plusOffersThreeActionsAndSavingTemplateDoesNotCreateTask() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        val db = androidx.room.Room.inMemoryDatabaseBuilder(app, com.focusbyrj.app.data.FocusDatabase::class.java).allowMainThreadQueries().build()
        val vm = com.focusbyrj.app.ui.viewmodels.TaskViewModel(com.focusbyrj.app.data.TaskRepository(db.taskDao()), app)
        compose.setContent { MaterialTheme { com.focusbyrj.app.ui.screens.TodosScreen(vm) } }
        compose.onNodeWithContentDescription("Add Task").performClick()
        compose.onNodeWithText("Create task").assertIsDisplayed()
        compose.onNodeWithText("Create template").assertIsDisplayed().performClick()
        compose.onNodeWithText("New Template").assertIsDisplayed()
        compose.onNodeWithText("Title").performTextInput("Pack for tomorrow")
        compose.onNodeWithText("Save template").performClick()
        var savedName = ""
        compose.runOnIdle {
            val saved = com.focusbyrj.app.data.ProductivityLibrary(app).entries("template").single().second
            savedName = saved.getString("name")
            org.junit.Assert.assertEquals(com.focusbyrj.app.util.SmartDateParser.parse("Pack for tomorrow")!!.cleanText, savedName)
            org.junit.Assert.assertNotNull(com.focusbyrj.app.data.ProductivityLibrary.instantiate(saved).dueDate)
            org.junit.Assert.assertTrue(kotlinx.coroutines.runBlocking { db.taskDao().getAllTasksList().isEmpty() })
        }
        compose.onNodeWithContentDescription("Add Task").performClick()
        compose.onNodeWithText("Existing templates").performClick()
        compose.onNodeWithText(savedName).assertExists()
    }
    @Test fun taskPlanningStartsHiddenAndMoreRevealsOrganizedActions() {
        compose.setContent { MaterialTheme { AddTaskDialog(onDismiss = {}, onSave = { _, _ -> }) } }
        compose.onNodeWithText("Plan work").assertDoesNotExist()
        compose.onNodeWithText("Waiting for").assertDoesNotExist()
        compose.onNodeWithText("Start showing on").assertDoesNotExist()
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Plan work").assertExists()
        compose.onNodeWithText("Waiting for").assertExists()
        compose.onNodeWithText("Start showing on").assertExists()
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Plan work").assertDoesNotExist()
    }
    @Test fun templateCreationOffersSameTaskSchedulingAndPlanning() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<Application>()
        EditorDraftStore(app, "task:0:0:TASK").save(org.json.JSONObject().put("title", "Unfinished task draft"))
        compose.setContent { MaterialTheme { AddTaskDialog(templateMode = true, onDismiss = {}, onSave = { _, _ -> }) } }
        compose.onNodeWithText("New Template").assertIsDisplayed()
        compose.onNodeWithText("Unfinished task draft").assertDoesNotExist()
        compose.onNodeWithText("Save template").assertExists()
        compose.onNodeWithText("Due Date & Time").assertExists()
        compose.onNodeWithText("Persistent Reminder").assertExists()
        compose.onNodeWithText("Recurrence").assertExists()
        compose.onNodeWithText("More").performScrollTo().performClick()
        compose.onNodeWithText("Plan work").assertExists()
        compose.onNodeWithText("Waiting for").assertExists()
        compose.onNodeWithText("Start showing on").assertExists()
    }
    @Test fun recoveryLandingOnlyShowsMainDestinationsAndStorageOpensSeparately() {
        compose.setContent { MaterialTheme { DataRecoveryTools(onSnapshots = {}, entry = { open -> Button(onClick = open) { Text("Open recovery") } }) } }
        compose.onNodeWithText("Open recovery").performClick()
        compose.onNodeWithText("Recovery").assertExists()
        compose.onNodeWithText("Verify a backup").assertExists()
        compose.onNodeWithText("Databases").assertDoesNotExist()
        compose.onNodeWithText("Storage", substring = false).performClick()
        compose.onNodeWithText("Databases").assertExists()
        compose.onNodeWithText("Expired temporary exports").assertExists()
    }
}
