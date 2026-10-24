package com.focusbyrj.app.widget

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.R
import com.focusbyrj.app.data.note.NoteDatabase
import com.focusbyrj.app.data.note.NoteEntity
import com.focusbyrj.app.ui.screens.notes.NotesViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 36], application = Application::class)
class NoteWidgetTypographyTest {
    private lateinit var context: Context
    private lateinit var db: NoteDatabase
    private val instance = NoteDatabase::class.java.getDeclaredField("INSTANCE").apply { isAccessible = true }
    private var previousDb: Any? = null

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("note_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java).allowMainThreadQueries().build()
        previousDb = instance.get(null)
        instance.set(null, db)
        NotesViewModel.latestNotesCache.clear()
    }
    @After fun cleanup() {
        instance.set(null, previousDb)
        db.close()
        NotesViewModel.latestNotesCache.clear()
    }
    private fun pixels(sp: Float, owner: Context = context) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, owner.resources.displayMetrics)
    private fun inflate(views: RemoteViews): View = views.apply(context, FrameLayout(context))
    private fun assertSize(view: View, id: Int, sp: Float) {
        val text = view.findViewById<TextView>(id)
        assertEquals(pixels(sp), text.textSize, 0.01f)
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, text.autoSizeTextType)
    }

    @Test fun actualRowsKeepSizesAcrossRefreshRecyclingNoteChangesAndFactoryRecreation() = runBlocking {
        val firstId = db.noteDao().insertNote(NoteEntity(title = "Text", content = "One\nTwo"))
        val checklistId = db.noteDao().insertNote(NoteEntity(title = "List", isChecklist = true,
            checklistJson = """[{"id":"a","text":"A long checklist item that wraps","isChecked":false}]"""))
        val small = NoteWidgetConfig(textSize = NoteWidgetTextSize.SIZE_12, filterMode = NoteWidgetFilterMode.SPECIFIC, specificNoteId = firstId)
        val large = small.copy(textSize = NoteWidgetTextSize.SIZE_30, specificNoteId = checklistId)
        NoteWidgetConfigHelper.saveConfig(context, 101, small)
        NoteWidgetConfigHelper.saveConfig(context, 102, large)
        val textFactory = NoteWidgetRemoteViewsFactory(context, NoteWidgetTypography.adapterIntent(context, 101, small))
        val listFactory = NoteWidgetRemoteViewsFactory(context, NoteWidgetTypography.adapterIntent(context, 102, large))
        textFactory.onCreate(); listFactory.onCreate()
        val row = inflate(textFactory.getViewAt(0)!!)
        assertSize(row, R.id.widget_note_text_item_body, 12f)
        val checkRow = inflate(listFactory.getViewAt(0)!!)
        assertSize(checkRow, R.id.widget_note_item_text, 30f)
        repeat(3) {
            textFactory.onDataSetChanged(); listFactory.onDataSetChanged()
            textFactory.getViewAt(0)!!.reapply(context, row)
            listFactory.getViewAt(0)!!.reapply(context, checkRow)
            assertSize(row, R.id.widget_note_text_item_body, 12f)
            assertSize(checkRow, R.id.widget_note_item_text, 30f)
        }
        val revised = small.copy(textSize = NoteWidgetTextSize.SIZE_26)
        NoteWidgetConfigHelper.saveConfig(context, 101, revised)
        textFactory.onDataSetChanged()
        textFactory.getViewAt(0)!!.reapply(context, row)
        assertSize(row, R.id.widget_note_text_item_body, 26f)
        assertSize(inflate(textFactory.getLoadingView()), R.id.widget_note_text_item_body, 26f)
        NoteWidgetConfigHelper.saveConfig(context, 101, revised.copy(specificNoteId = checklistId))
        textFactory.onDataSetChanged()
        assertSize(inflate(textFactory.getViewAt(0)!!), R.id.widget_note_item_text, 26f)
        val restarted = NoteWidgetRemoteViewsFactory(context, NoteWidgetTypography.adapterIntent(context, 101, revised))
        restarted.onCreate()
        assertSize(inflate(restarted.getViewAt(0)!!), R.id.widget_note_item_text, 26f)
        assertEquals(NoteWidgetTextSize.SIZE_30, NoteWidgetConfigHelper.getConfig(context, 102).textSize)
    }

    @Test fun resizingAndReapplyingRowsKeepChosenSizeAndHeaderAlwaysUsesConfig() {
        val config = NoteWidgetConfig(textSize = NoteWidgetTextSize.SIZE_26)
        val views = RemoteViews(context.packageName, R.layout.widget_note_text_item)
        views.setTextViewText(R.id.widget_note_text_item_body, "A long line of text to fit into a tiny widget")
        NoteWidgetTypography.apply(context, views, R.id.widget_note_text_item_body, 26f)
        val root = inflate(views)
        val text = root.findViewById<TextView>(R.id.widget_note_text_item_body)
        assertEquals(TextView.AUTO_SIZE_TEXT_TYPE_NONE, text.autoSizeTextType)
        root.measure(View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(40, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 120, 40)
        views.reapply(context, root)
        assertSize(root, R.id.widget_note_text_item_body, 26f)
        val header = RemoteViews(context.packageName, R.layout.widget_note_layout)
        NoteWidgetTypography.applyHeader(context, header, config)
        val headerView = inflate(header)
        assertSize(headerView, R.id.widget_note_title, 28f)
        assertSize(headerView, R.id.widget_note_empty_title, 26f)
        header.setTextViewText(R.id.widget_note_title, "No Notes")
        header.reapply(context, headerView)
        assertSize(headerView, R.id.widget_note_title, 28f)
    }

    @Test fun adapterCacheIdentityChangesOnlyForTypographyAndWidgetIdentity() {
        val config = NoteWidgetConfig()
        val original = NoteWidgetTypography.adapterIntent(context, 101, config)
        assertTrue(original.filterEquals(NoteWidgetTypography.adapterIntent(context, 101, config.copy(showTitle = false))))
        assertFalse(original.filterEquals(NoteWidgetTypography.adapterIntent(context, 102, config)))
        assertFalse(original.filterEquals(NoteWidgetTypography.adapterIntent(context, 101, config.copy(textSize = NoteWidgetTextSize.SIZE_30))))
        val scaled = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.5f })
        assertFalse(original.filterEquals(NoteWidgetTypography.adapterIntent(scaled, 101, config)))
        val views = RemoteViews(context.packageName, R.layout.widget_note_text_item)
        NoteWidgetTypography.apply(scaled, views, R.id.widget_note_text_item_body, 20f)
        val rendered = inflate(views).findViewById<TextView>(R.id.widget_note_text_item_body)
        assertEquals(pixels(20f, scaled), rendered.textSize, 0.01f)
    }
}
