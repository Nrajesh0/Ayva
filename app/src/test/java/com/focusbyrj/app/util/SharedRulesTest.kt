package com.focusbyrj.app.util

import com.focusbyrj.app.data.HabitIntervalRules
import com.focusbyrj.app.service.AptitudeReminderHelper
import com.focusbyrj.app.ui.screens.BubbleSettingsHelper
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedRulesTest {
    @Test
    fun reminderFormatsPreserveEveryMinuteOfTheDay() {
        for (hour in 0..23) {
            for (minute in 0..59) {
                val clockHour = if (hour % 12 == 0) 12 else hour % 12
                val suffix = if (hour < 12) "AM" else "PM"
                for (text in listOf("$hour:$minute", " $clockHour:$minute $suffix ")) {
                    assertEquals(hour to minute, BubbleSettingsHelper.parseTimeSafe(text))
                    assertEquals(hour to minute, AptitudeReminderHelper.parseHourAndMinute(text))
                }
            }
        }
    }

    @Test
    fun malformedTimesKeepCallerDefaultsAndExistingBounds() {
        for (text in listOf("", "not a time", "12", "12:00PM", "12:xx PM")) {
            assertEquals(8 to 0, BubbleSettingsHelper.parseTimeSafe(text))
            assertEquals(18 to 0, AptitudeReminderHelper.parseHourAndMinute(text))
            assertEquals(23 to 0, AptitudeReminderHelper.parseHourAndMinute(text, 50, -5))
        }
        assertEquals(23 to 59, BubbleSettingsHelper.parseTimeSafe("25:99 PM"))
        assertEquals(0 to 0, AptitudeReminderHelper.parseHourAndMinute("-1:-2"))
    }

    @Test
    fun editDistancePreservesTyposCaseAndUtf16Semantics() {
        for ((lhs, rhs, expected) in
            listOf(
                Triple("", "", 0),
                Triple("", "abc", 3),
                Triple("kitten", "sitting", 3),
                Triple("reminder", "remidner", 2),
                Triple("focus", "Focus", 1),
                Triple("é", "e", 1),
                Triple("😀", "", 2),
            )) {
            assertEquals(expected, editDistance(lhs, rhs))
            assertEquals(expected, editDistance(rhs, lhs))
            assertEquals(expected, OfflineNluEngine.levenshtein(lhs, rhs))
        }
    }

    @Test
    fun habitWindowsHandleOvernightFullDayAndShortSelections() {
        assertEquals(720, HabitIntervalRules.windowMinutes(8, 0, 20, 0))
        assertEquals(480, HabitIntervalRules.windowMinutes(22, 0, 6, 0))
        assertEquals(1440, HabitIntervalRules.windowMinutes(8, 0, 8, 0))
        assertEquals(30, HabitIntervalRules.windowMinutes(8, 0, 8, 1))
        assertEquals(30, HabitIntervalRules.windowMinutes(23, 55, 0, 5))
        assertEquals(120, HabitIntervalRules.intervalForTarget(720, 6))
        assertEquals(5, HabitIntervalRules.intervalForTarget(30, 24))
    }

    @Test
    fun chatRetainsItsShortWindowRulesWhileIntervalSteppingCannotCrash() {
        val chat = HabitIntervalRules.WindowPolicy.CHAT
        assertEquals(5, HabitIntervalRules.windowMinutes(8, 0, 8, 5, chat))
        assertEquals(24, HabitIntervalRules.windowMinutes(8, 0, 8, 24, chat))
        assertEquals(15, HabitIntervalRules.windowMinutes(23, 55, 0, 5, chat))
        assertEquals(1440, HabitIntervalRules.windowMinutes(8, 0, 8, 0, chat))
        assertEquals(5, HabitIntervalRules.intervalForTarget(5, 1))
        assertEquals(15, HabitIntervalRules.stepInterval(5, 1, 5))
        assertEquals(1, HabitIntervalRules.targetForInterval(5, 15))
    }

    @Test
    fun intervalSteppingKeepsExistingTransitionsAndNeverInvertsBounds() {
        assertEquals(90, HabitIntervalRules.stepInterval(75, 1, 720))
        assertEquals(105, HabitIntervalRules.stepInterval(90, 1, 720))
        assertEquals(120, HabitIntervalRules.stepInterval(105, 1, 720))
        assertEquals(90, HabitIntervalRules.stepInterval(105, -1, 720))
        assertEquals(15, HabitIntervalRules.stepInterval(15, -1, 720))
        assertEquals(15, HabitIntervalRules.stepInterval(120, 1, 1))
        assertEquals(6, HabitIntervalRules.targetForInterval(720, 120))
        assertEquals(24, HabitIntervalRules.targetForInterval(1440, 15))
    }
}
