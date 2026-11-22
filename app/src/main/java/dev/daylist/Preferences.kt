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
    val drive=Cloud.state(a)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom=32.dp)){
        Heading("Appearance")
        ListItem(headlineContent={Text("Color theme")},supportingContent={Choices(listOf("System","Light","Dark"),mode,theme)})
        Heading("Notifications and reminders")
        Preference("Task notifications",if(bridge.alerts())"Allowed" else "Turn on to receive reminders"){bridge.settings(false)}
        if(Build.VERSION.SDK_INT>=31)Preference("Precise alarms",if(bridge.exact())"Allowed" else "Enable for on-time reminders"){bridge.settings(true)}
        Toggle("Notify at the deadline",bridge.pref("notifyAtDue")){bridge.setting("notifyAtDue",it);a.refresh()}
        Heading("Quick capture")
        Toggle("Enter sends a task",bridge.pref("enter")){bridge.setting("enter",it);a.refresh()}
        Heading("Backup and restore")
        val connected=drive.getBoolean("enabled",false)
        val last=drive.getString("last","").orEmpty().takeIf{it.isNotBlank()}?.let{runCatching{Instant.parse(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM,FormatStyle.SHORT))}.getOrNull()}
        Preference("Google Drive",if(connected)drive.getString("email","Connected") ?: "Connected" else "Connect an account"){bridge.backup("connect")}
        if(connected){
            Preference("Back up now",listOfNotNull(drive.getString("status","Pending"),last?.let{"Last verified: $it"}).joinToString("\n")){bridge.backup("now")}
            Preference("Disable automatic backups"){bridge.backup("off")}
        }
        Preference("Restore from Drive","Choose a saved snapshot"){bridge.backup("restore")}
        Heading("Data management")
        Preference("Export backup","Save a copy to a location you choose"){bridge.backup("export")}
        Preference("Import backup","Preview a file before replacing tasks"){bridge.backup("import")}
        Heading("About")
        ListItem(headlineContent={Text("Daylist")},supportingContent={Text("Version 1.2")})
    }
}
@Composable private fun Preference(title:String,summary:String="",click:()->Unit){
    ListItem(headlineContent={Text(title)},supportingContent=if(summary.isEmpty())null else {{Text(summary)}},modifier=Modifier.clickable(onClick=click))
}
@Composable private fun Toggle(title:String,value:Boolean,change:(Boolean)->Unit){
    ListItem(headlineContent={Text(title)},trailingContent={Switch(value,onCheckedChange=null)},modifier=Modifier.toggleable(value=value,role=Role.Switch,onValueChange=change))
}
