package dev.daylist

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CalendarMonthTest {
    val feb = YearMonth.of(2028,2)
    private fun task(date:String) = JSONObject().put("date",date)
    @Test fun monthBoundariesAndReminderIndependence(){
        val leap = task("2028-02-29").put("done",true)
        val due = task("2028-02-29").put("reminderDate","2028-02-27")
        val tasks = List(10000){task("")} + listOf(task("").put("reminderDate","2028-02-29"),leap,due,task("2028-03-01"))
        val days = calendarMonth(tasks,feb)
        assertEquals(setOf(feb.atEndOfMonth()),days.keys)
        assertEquals(listOf(leap,due),days[feb.atEndOfMonth()])
        due.put("date","2028-03-02")
        assertEquals(listOf(leap),calendarMonth(tasks,feb)[feb.atEndOfMonth()])
    }
    @Test fun recurringDatesRespectExistingSchedule(){
        val monday = task("2025-12-01").put("repeat","weeks").put("mask",1)
        val monthly = task("2027-12-31").put("repeat","months").put("monthDay",31)
        val days = calendarMonth(listOf(monday,monthly),feb)
        assertEquals(listOf(7,14,21,28),days.filterValues{monday in it}.keys.map{it.dayOfMonth}.sorted())
        assertEquals(listOf(monthly),days[feb.atEndOfMonth()])
        monday.put("date","2028-02-21")
        assertNull(calendarMonth(listOf(monday),feb)[feb.atDay(14)])
    }
}
