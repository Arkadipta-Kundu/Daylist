package dev.daylist

import android.os.Build
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.*
import java.time.format.*
import org.json.JSONObject

@Composable internal fun Preferences(a:Main,bridge:Main.Bridge,mode:String,theme:(String)->Unit,revision:Int){
    var guide by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
        Heading("Appearance")
        ListItem(headlineContent={Text("Color theme")},supportingContent={Choices(listOf("System","Light","Dark"),mode,theme)})
        Heading("Reminder reliability")
        val enabled=bridge.alerts();val precise=bridge.exact()
        ListItem(headlineContent={Text(if(!enabled)"Notifications disabled" else if(precise)"Ready" else "Limited precision")},supportingContent={Text(if(!enabled)"Enable task notifications to receive reminders." else if(!precise)"Android may deliver reminders late." else "Precise scheduling is available.")})
        Preference("Task notifications",if(bridge.alerts())"Allowed" else "Turn on to receive reminders"){bridge.settings(false)}
        if(Build.VERSION.SDK_INT>=31)Preference("Precise alarms",if(bridge.exact())"Allowed" else "Enable for on-time reminders"){bridge.settings(true)}
        val probe=Reminders.prefs(a)
        val scheduled=probe.getLong("testScheduled",0)
        val handled=probe.getLong("testHandled",0)
        fun clock(ms:Long)=java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss"))
        val pending=probe.getLong("testAt",0)
        val result=when{
            pending>0->"Scheduled for "+clock(pending)
            handled>0->"Scheduled "+clock(scheduled)+" · "+(if(probe.getBoolean("testPosted",false))"posted " else "notifications blocked at ")+clock(handled)
            else->"Schedule a test in one minute, then lock your phone."
        }
        Preference("Test reminder",result){if(enabled)bridge.testReminder() else bridge.settings(false)}
        Toggle("Notify at the deadline",bridge.pref("notifyAtDue")){bridge.setting("notifyAtDue",it);a.refresh()}
        Heading("Quick capture")
        Toggle("Enter sends a task",bridge.pref("enter")){bridge.setting("enter",it);a.refresh()}
        Preference("Quick syntax guide"){guide=!guide}
        if(guide) Text(SYNTAX_GUIDE,Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodyMedium)
        Heading("Data management")
        Preference("Export backup","Save a copy to a location you choose"){bridge.backup("export")}
        Preference("Import backup","Preview a file before replacing tasks"){bridge.backup("import")}
        Heading("About")
        ListItem(headlineContent={Text("Daylist")},supportingContent={Text("Version 1.4.1")})
    }
}
@Composable private fun Preference(title:String,summary:String="",click:()->Unit){
    ListItem(headlineContent={Text(title)},supportingContent=if(summary.isEmpty())null else {{Text(summary)}},modifier=Modifier.clickable(onClick=click))
}
@Composable private fun Toggle(title:String,value:Boolean,change:(Boolean)->Unit){
    ListItem(headlineContent={Text(title)},trailingContent={Switch(value,onCheckedChange=null)},modifier=Modifier.toggleable(value=value,role=Role.Switch,onValueChange=change))
}

private const val SYNTAX_GUIDE = """Buy milk
Study Java today
Call Rahul tomorrow
Finish report tomorrow by 5 pm
Call mom remind at 8 pm
Take medicine remind in 30 minutes
Buy groceries !
Finish project !!
Submit assignment !!!
Study Java "Revise Collections"
Clean room () Sweep floor
Study SQL #learning
Workout every Monday"""
