package dev.daylist

import android.content.pm.PackageManager
import android.app.NotificationManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import org.json.*
import java.util.UUID

class OfflineReminderTest {
    @Test fun offlineReminderAndLegacyImport(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(PackageManager.PERMISSION_DENIED,c.checkSelfPermission(android.Manifest.permission.INTERNET))
        val day=LocalDate.now()
        val t=JSONObject().put("id",UUID.randomUUID().toString()).put("title","Offline fixture").put("description","Notes")
            .put("date","").put("time","").put("repeat","").put("interval",1).put("mask",0).put("done",false)
            .put("reminder","18:00").put("reminderDate",day.plusDays(1).toString()).put("subs",JSONArray().put(JSONObject().put("text","Original").put("done",true)))
        try{
            assertTrue(TaskStore.write(c,t.toString(),false))
            val saved=Snapshot.validate(Snapshot.make(c)).getJSONArray("tasks").rows().single{it.str("id")==t.str("id")}
            assertEquals("Inbox",saved.group(day.toString()));assertTrue(saved.getJSONArray("subs").getJSONObject(0).getBoolean("done"))
            val late=t.copyTask().put("reminderDate",day.minusDays(1).toString())
            assertTrue(TaskStore.write(c,late.toString(),false))
            val manager=c.getSystemService(NotificationManager::class.java)
            if(Reminders.enabled(c))assertTrue(manager.activeNotifications.any{it.tag==t.str("id")})
            assertTrue(TaskStore.write(c,toggle(late).toString(),false))
            assertFalse(manager.activeNotifications.any{it.tag==t.str("id")})
        }finally{TaskStore.write(c,t.toString(),true)}
    }
    @Test fun widgetTapIsIdempotent(){
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        val day=LocalDate.now().toString()
        val t=JSONObject().put("id",UUID.randomUUID().toString()).put("title","Practice").put("date",day)
            .put("repeat","days").put("interval",2).put("reminderDate",LocalDate.now().plusDays(1).toString()).put("reminder","08:00").put("subs",JSONArray())
        try{assertTrue(TaskStore.write(c,t.toString(),false));assertTrue(finishOccurrence(c,t.str("id"),day));assertFalse(finishOccurrence(c,t.str("id"),day))}
        finally{TaskStore.write(c,t.toString(),true)}
    }
}
