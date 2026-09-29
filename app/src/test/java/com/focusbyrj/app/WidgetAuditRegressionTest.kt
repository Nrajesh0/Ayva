/*
 * Copyright (C) 2024-2026 Focus by Rj
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.focusbyrj.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.focusbyrj.app.widget.NoteWidgetConfig
import com.focusbyrj.app.widget.NoteWidgetConfigHelper
import com.focusbyrj.app.widget.NoteWidgetDrawableGenerator
import com.focusbyrj.app.widget.NoteWidgetFilterMode
import com.focusbyrj.app.widget.NoteWidgetTextSize
import com.focusbyrj.app.widget.WidgetAccent
import com.focusbyrj.app.widget.WidgetConfig
import com.focusbyrj.app.widget.WidgetConfigHelper
import com.focusbyrj.app.widget.WidgetDrawableGenerator
import com.focusbyrj.app.widget.WidgetTextSize
import com.focusbyrj.app.widget.WidgetTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetAuditRegressionTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("todo_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("note_widget_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun testTaskWidgetDrawable_rendersCleanBitmapWithoutCrashing() {
        val config = WidgetConfig(
            theme = WidgetTheme.DARK,
            accent = WidgetAccent.BLUE,
            textSize = WidgetTextSize.SIZE_18
        )
        val bitmap = WidgetDrawableGenerator.createWidgetBackground(context, config)
        assertNotNull(bitmap)
        assertEquals(360, bitmap.width)
        assertEquals(240, bitmap.height)
    }

    @Test
    fun testNoteWidgetDrawable_rendersCleanBitmapWithoutCrashing() {
        val config = NoteWidgetConfig(
            theme = WidgetTheme.DARK,
            accent = WidgetAccent.BLUE,
            textSize = NoteWidgetTextSize.SIZE_20
        )
        val result = NoteWidgetDrawableGenerator.createWidgetBackground(
            context = context,
            config = config,
            noteColorKey = null,
            isSystemDark = true,
            targetWidthDp = 300,
            targetHeightDp = 200
        )
        assertNotNull(result.bitmap)
        assertTrue(result.bitmap.width > 0)
        assertTrue(result.bitmap.height > 0)
    }

    @Test
    fun testTaskWidgetConfig_textSizePersistenceAndIsolation() {
        val widget1Id = 101
        val widget2Id = 102

        // Initial default should be SIZE_14
        val initialConfig1 = WidgetConfigHelper.getConfig(context, widget1Id)
        assertEquals(WidgetTextSize.SIZE_14, initialConfig1.textSize)

        // Save custom font size for widget 1
        val customConfig1 = initialConfig1.copy(textSize = WidgetTextSize.SIZE_20)
        WidgetConfigHelper.saveConfig(context, widget1Id, customConfig1)

        // Verify widget 1 persisted SIZE_20
        val loadedConfig1 = WidgetConfigHelper.getConfig(context, widget1Id)
        assertEquals(WidgetTextSize.SIZE_20, loadedConfig1.textSize)

        // Verify widget 2 still defaults to SIZE_14
        val loadedConfig2 = WidgetConfigHelper.getConfig(context, widget2Id)
        assertEquals(WidgetTextSize.SIZE_14, loadedConfig2.textSize)

        // Save custom font size for widget 2
        val customConfig2 = loadedConfig2.copy(textSize = WidgetTextSize.SIZE_12)
        WidgetConfigHelper.saveConfig(context, widget2Id, customConfig2)

        // Re-verify both widgets kept their independent sizes
        assertEquals(WidgetTextSize.SIZE_20, WidgetConfigHelper.getConfig(context, widget1Id).textSize)
        assertEquals(WidgetTextSize.SIZE_12, WidgetConfigHelper.getConfig(context, widget2Id).textSize)
    }

    @Test
    fun testNoteWidgetConfig_textSizePersistenceAndFilterIsolation() {
        val widget1Id = 201
        val widget2Id = 202

        // Widget 1 configured with SIZE_26
        val config1 = NoteWidgetConfig(
            theme = WidgetTheme.OLED,
            accent = WidgetAccent.NEON_GREEN,
            textSize = NoteWidgetTextSize.SIZE_26
        )
        NoteWidgetConfigHelper.saveConfig(context, widget1Id, config1)

        // Widget 2 configured with SIZE_14
        val config2 = NoteWidgetConfig(
            theme = WidgetTheme.LIGHT,
            accent = WidgetAccent.PURPLE,
            textSize = NoteWidgetTextSize.SIZE_14
        )
        NoteWidgetConfigHelper.saveConfig(context, widget2Id, config2)

        // Verify both widgets read back their respective text sizes
        val read1 = NoteWidgetConfigHelper.getConfig(context, widget1Id)
        val read2 = NoteWidgetConfigHelper.getConfig(context, widget2Id)
        assertEquals(NoteWidgetTextSize.SIZE_26, read1.textSize)
        assertEquals(NoteWidgetTextSize.SIZE_14, read2.textSize)

        // Cycle filter mode on widget 1 - this must NOT reset widget 1's font size
        NoteWidgetConfigHelper.setFilterMode(context, widget1Id, NoteWidgetFilterMode.CHECKLISTS)
        val read1AfterFilter = NoteWidgetConfigHelper.getConfig(context, widget1Id)
        assertEquals(NoteWidgetFilterMode.CHECKLISTS, read1AfterFilter.filterMode)
        assertEquals(NoteWidgetTextSize.SIZE_26, read1AfterFilter.textSize)

        // Widget 2 must also remain completely unaffected
        val read2After = NoteWidgetConfigHelper.getConfig(context, widget2Id)
        assertEquals(NoteWidgetTextSize.SIZE_14, read2After.textSize)
    }

    @Test
    fun testPendingIntentRequestCodes_noCollisionsBetweenWidgetsAndActions() {
        val widgetA = 1
        val widgetB = 10

        // In TodoWidgetProvider:
        // Tab request codes: 100_000 + widgetId * 10 + i
        // Add request code: 200_000 + widgetId
        val todoTabA1 = 100_000 + widgetA * 10 + 1
        val todoTabB1 = 100_000 + widgetB * 10 + 1
        val todoAddA = 200_000 + widgetA
        val todoAddB = 200_000 + widgetB

        assertNotEquals(todoTabA1, todoAddA)
        assertNotEquals(todoTabB1, todoAddA)
        assertNotEquals(todoTabB1, todoAddB)

        // In NoteWidgetProvider:
        // Prev: 10_000_000 + widgetId
        // Next: 20_000_000 + widgetId
        // Filter: 30_000_000 + widgetId
        // Settings: 40_000_000 + widgetId
        // Header: 50_000_000 + widgetId
        // Empty: 56_000_000 + widgetId
        // Add item: 60_000_000 + widgetId
        // Notes tab: 70_000_000 + widgetId
        // Voice: 85_000_000 + widgetId
        // New note: 90_000_000 + widgetId
        // List template: 95_000_000 + widgetId
        val noteCodes = listOf(
            10_000_000 + widgetA,
            20_000_000 + widgetA,
            30_000_000 + widgetA,
            40_000_000 + widgetA,
            50_000_000 + widgetA,
            56_000_000 + widgetA,
            60_000_000 + widgetA,
            70_000_000 + widgetA,
            85_000_000 + widgetA,
            90_000_000 + widgetA,
            95_000_000 + widgetA
        )
        // All codes for widget A must be strictly distinct
        assertEquals(noteCodes.size, noteCodes.toSet().size)
    }
}
