package com.focusbyrj.app

import com.focusbyrj.app.data.RecurrencePattern
import com.focusbyrj.app.util.SmartDateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SmartDateParserTest {

    @Test
    fun testEveryMonthOn19th() {
        val result = SmartDateParser.parse("pay rent on 19 th every month")
        assertEquals("pay rent", result.cleanText)
        assertEquals(RecurrencePattern.MONTHLY, result.recurrence)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(19, cal.get(Calendar.DAY_OF_MONTH))
        
        val nowCal = Calendar.getInstance()
        if (nowCal.get(Calendar.DAY_OF_MONTH) > 19) {
            val expectedMonth = (nowCal.get(Calendar.MONTH) + 1) % 12
            assertEquals(expectedMonth, cal.get(Calendar.MONTH))
        }
    }

    @Test
    fun testEveryDayAt8pm() {
        val result = SmartDateParser.parse("call mom every day at 8pm")
        assertEquals("call mom", result.cleanText)
        assertEquals(RecurrencePattern.DAILY, result.recurrence)
        assertTrue(result.hasTime)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(20, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testEveryMondayAt10am() {
        val result = SmartDateParser.parse("team standup every monday at 10am")
        assertEquals("team standup", result.cleanText)
        assertEquals(RecurrencePattern.WEEKLY, result.recurrence)
        assertTrue(result.hasTime)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(Calendar.MONDAY, cal.get(Calendar.DAY_OF_WEEK))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun testEveryYearOnApril15th() {
        val result = SmartDateParser.parse("taxes on apr 15th every year")
        assertEquals("taxes", result.cleanText)
        assertEquals(RecurrencePattern.YEARLY, result.recurrence)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(Calendar.APRIL, cal.get(Calendar.MONTH))
        assertEquals(15, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testMay202033() {
        val result = SmartDateParser.parse("may 20 2033")
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2033, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testMay202033WithTaskTitleAndTime() {
        val result = SmartDateParser.parse("Renew passport May 20, 2033 at 4pm")
        assertEquals("Renew passport", result.cleanText)
        assertNotNull(result.timestamp)
        assertTrue(result.hasTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2033, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(16, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testDayMonthYear2033() {
        val result = SmartDateParser.parse("pay mortgage on 20th of May 2033")
        assertEquals("pay mortgage", result.cleanText)
        assertNotNull(result.timestamp)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2033, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testIsoDateYear() {
        val result = SmartDateParser.parse("Doctor appointment 2033-05-20 at 10:30am")
        assertEquals("Doctor appointment", result.cleanText)
        assertNotNull(result.timestamp)
        assertTrue(result.hasTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2033, cal.get(Calendar.YEAR))
        assertEquals(Calendar.MAY, cal.get(Calendar.MONTH))
        assertEquals(20, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testColonSeparatedTime930() {
        val result = SmartDateParser.parse("call client at 9:30")
        assertEquals("call client", result.cleanText)
        assertNotNull(result.timestamp)
        assertTrue(result.hasTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) in listOf(9, 21))
    }

    @Test
    fun testDotSeparatedTime930() {
        val result = SmartDateParser.parse("call client at 9.30")
        assertEquals("call client", result.cleanText)
        assertNotNull(result.timestamp)
        assertTrue(result.hasTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(30, cal.get(Calendar.MINUTE))
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) in listOf(9, 21))
    }

    @Test
    fun testColonSeparatedTime930AmPm() {
        val resultAm = SmartDateParser.parse("breakfast meeting at 9:30 am")
        assertEquals("breakfast meeting", resultAm.cleanText)
        assertNotNull(resultAm.timestamp)
        assertTrue(resultAm.hasTime)
        val calAm = Calendar.getInstance().apply { timeInMillis = resultAm.timestamp!! }
        assertEquals(9, calAm.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calAm.get(Calendar.MINUTE))

        val resultPm = SmartDateParser.parse("dinner at 9:30 pm")
        assertEquals("dinner", resultPm.cleanText)
        assertNotNull(resultPm.timestamp)
        assertTrue(resultPm.hasTime)
        val calPm = Calendar.getInstance().apply { timeInMillis = resultPm.timestamp!! }
        assertEquals(21, calPm.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calPm.get(Calendar.MINUTE))
    }

    @Test
    fun testDotSeparatedTime930AmPm() {
        val resultAm = SmartDateParser.parse("breakfast meeting at 9.30am")
        assertEquals("breakfast meeting", resultAm.cleanText)
        assertNotNull(resultAm.timestamp)
        assertTrue(resultAm.hasTime)
        val calAm = Calendar.getInstance().apply { timeInMillis = resultAm.timestamp!! }
        assertEquals(9, calAm.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calAm.get(Calendar.MINUTE))

        val resultPm = SmartDateParser.parse("dinner at 9.30pm")
        assertEquals("dinner", resultPm.cleanText)
        assertNotNull(resultPm.timestamp)
        assertTrue(resultPm.hasTime)
        val calPm = Calendar.getInstance().apply { timeInMillis = resultPm.timestamp!! }
        assertEquals(21, calPm.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calPm.get(Calendar.MINUTE))
    }

    @Test
    fun testRemindMePrefixAndColonTime() {
        val result = SmartDateParser.parse("remind me to check oven at 9:30")
        assertEquals("check oven", result.cleanText)
        assertNotNull(result.timestamp)
        assertTrue(result.hasTime)

        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(30, cal.get(Calendar.MINUTE))
    }

    @Test
    fun testFeb29InLeapYear2028() {
        val result = SmartDateParser.parse("Anniversary on Feb 29 2028")
        assertNotNull(result.timestamp)
        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2028, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(29, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testFeb29InNonLeapYearClampsToFeb28WithNote() {
        val result = SmartDateParser.parse("Anniversary on Feb 29 2027")
        assertNotNull(result.timestamp)
        val cal = Calendar.getInstance().apply { timeInMillis = result.timestamp!! }
        assertEquals(2027, cal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, cal.get(Calendar.MONTH))
        assertEquals(28, cal.get(Calendar.DAY_OF_MONTH))
        assertNotNull(result.note)
        assertTrue(result.note!!.contains("29"))
        assertTrue(result.note!!.contains("28"))
    }

    @Test
    fun testMonthlyRecurrenceClampingOn31st() {
        val baseCal = Calendar.getInstance().apply {
            val currentYear = get(Calendar.YEAR)
            set(currentYear + 1, Calendar.JANUARY, 31, 10, 0, 0)
        }
        val targetYear = baseCal.get(Calendar.YEAR)
        val task = com.focusbyrj.app.data.Task(
            title = "Pay bills",
            dueDate = baseCal.timeInMillis,
            recurrence = com.focusbyrj.app.data.RecurrencePattern.MONTHLY
        )
        val nextTask = com.focusbyrj.app.util.TaskReminderHelper.generateNextRecurringTask(task)
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTask.dueDate!! }
        assertEquals(targetYear, nextCal.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, nextCal.get(Calendar.MONTH))
        val expectedFebDays = if (com.focusbyrj.app.util.SmartDateParser.isLeapYear(targetYear)) 29 else 28
        assertEquals(expectedFebDays, nextCal.get(Calendar.DAY_OF_MONTH))
    }
}
