package dev.daylist

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TaskUiStateTest {
    @Test fun undatedLegacyTaskIsInInbox(){
        val t=JSONObject().put("title","Review notes")
        assertEquals("Inbox",t.group("2026-11-22"))
        assertNull(t.issue())
        assertFalse(t.has("priority"))
    }
    @Test fun missingDatePreventsTimedReminder(){
        val task=JSONObject().put("title","Review notes").put("reminder","18:00")
        assertNotNull(task.issue())
    }
    @Test fun futureWeeklyTaskKeepsFirstSelectedWeekday(){
        val task=JSONObject().put("title","Walk").put("repeat","weeks").put("date","2026-11-22").put("mask",1)
        assertEquals("2026-11-23",normalized(task).str("date"))
        assertEquals("2026-11-22",task.str("date"))
    }
    @Test fun completedTasksWinOverOverdueGrouping(){
        val task=JSONObject().put("done",true).put("date","2020-01-01")
        assertEquals("Completed",task.group("2026-11-22"))
    }
}
