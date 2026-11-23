package com.focusbyrj.app.data

import java.util.Calendar
import org.junit.Assert.*
import org.junit.Test

class BlockingRulesTest {
    private val soft = BlockingRules.Rule("pkg", "Manual", "SOFT", "SIMPLE")
    private val hard = BlockingRules.Rule("pkg", "Routine", "HARD", "TIME_LIMIT", minutes = 30)

    @Test
    fun hardWinsOnlyWhenItsOwnLimitAppliesRegardlessOfOrder() {
        for (rules in listOf(listOf(soft, hard), listOf(hard, soft))) {
            assertEquals(soft, BlockingRules.resolve(rules, { 29 }, { 0 }))
            assertEquals(hard, BlockingRules.resolve(rules, { 30 }, { 0 }))
        }
    }

    @Test
    fun separateRoutineLimitsCannotMaskEachOther() {
        val opens = hard.copy(source = "Open limit", restrictionMode = "CLICK_LIMIT", clicks = 3)
        assertNull(BlockingRules.resolve(listOf(hard, opens), { 0 }, { 3 }))
        assertEquals(opens, BlockingRules.resolve(listOf(hard, opens), { 0 }, { 4 }))
        assertEquals(hard, BlockingRules.resolve(listOf(opens, hard), { 30 }, { 0 }))
    }

    @Test
    fun temporarySoftUnlockCannotBypassAnApplicableHardRoutine() {
        val rules = listOf(soft, hard)
        assertNull(BlockingRules.resolve(rules, { 29 }, { 0 }, temporarilyUnlocked = true))
        assertEquals(hard, BlockingRules.resolve(rules, { 30 }, { 0 }, temporarilyUnlocked = true))
        assertEquals(soft, BlockingRules.resolve(rules, { 29 }, { 0 }, temporarilyUnlocked = false))
    }

    @Test
    fun allOverlappingActiveRoutineRulesAreRetained() {
        val at = Calendar.getInstance().apply { set(2026, Calendar.NOVEMBER, 22, 9, 30) }
        val row =
            FocusSchedule(
                id = 1,
                name = "Soft",
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
                daysOfWeek = "1,2,3,4,5,6,7",
                mode = "SOFT",
                appsToBlock = "pkg|SOFT",
            )
        val rules =
            BlockingRules.routines(
                listOf(row, row.copy(id = 2, name = "Hard", appsToBlock = "pkg|HARD")),
                at,
            )
        assertEquals(2, rules.size)
        assertEquals("HARD", BlockingRules.resolve(rules, { 0 }, { 0 })?.mode)
    }
}
