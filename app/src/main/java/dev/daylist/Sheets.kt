@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.daylist

import android.graphics.Color as AndroidColor
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.*
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

private val fields=linkedMapOf("Date" to Icons.Outlined.Event,"Reminder" to Icons.Outlined.Notifications,"Priority" to Icons.Outlined.Flag,"Description" to Icons.Outlined.Notes,"Subtasks" to Icons.Outlined.Checklist,"Recurrence" to Icons.Outlined.Repeat,"Tags" to Icons.Outlined.Label)
@Composable internal fun TaskSheet(initial:String,first:String,adding:Boolean,close:()->Unit,save:(JSONObject)->Unit,delete:(JSONObject)->Unit,complete:(JSONObject)->Unit,bridge:Main.Bridge){
    var raw by rememberSaveable(initial){mutableStateOf(initial)}
    val t=remember(raw){JSONObject(raw)}
    var active by rememberSaveable(initial){mutableStateOf(first)}
    var error by rememberSaveable{mutableStateOf("")}
    var picker by rememberSaveable{mutableStateOf("")}
    var confirm by remember{mutableStateOf(false)}
    var reading by rememberSaveable{mutableStateOf(!adding)}
    fun set(k:String,v:Any){val copy=t.copyTask().put(k,v);if(k=="date")copy.remove("monthDay");raw=copy.toString();error=""}
    ModalBottomSheet(onDismissRequest=close,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(horizontal=16.dp)){
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text(if(adding)"Task details" else "Edit task",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                TextButton(onClick=close){Text("Cancel")}
                Button(onClick={val issue=if(adding&&t.str("title").isBlank())t.copyTask().put("title","draft").issue() else t.issue();if(issue==null)save(t)else error=issue}){Text(if(adding)"Apply" else "Save")}
            }
            if(error.isNotBlank())Text(error,color=MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=16.dp)){
                if(!adding)Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(t.optBoolean("done"),{if(t.issue()==null)complete(normalized(t)) else error=t.issue().orEmpty()},Modifier.semantics{contentDescription="Toggle task completion"})
                    TextField(t.str("title"),{set("title",it)},label={Text("Task name")},modifier=Modifier.fillMaxWidth())
                }
                fields.forEach{(name,icon)->
                    val subs=t.optJSONArray("subs")?:JSONArray()
                    val summary=when(name){
                        "Date"->listOf(t.str("date").takeIf{it.isNotEmpty()}?.let(::prettyDay),t.str("time").takeIf{it.isNotEmpty()}?.let(::prettyTime)).filterNotNull().joinToString(" · ")
                        "Description"->t.str("description").lineSequence().first().take(65)
                        "Subtasks"->if(subs.length()>0)"${subs.rows().count{it.optBoolean("done")}} of ${subs.length()} complete" else ""
                        else->labels(t).filter{it.first==name}.joinToString(" · "){it.second}
                    }
                    ListItem(headlineContent={Text(name)},supportingContent=if(summary.isBlank())null else {{Text(summary)}},leadingContent={Icon(icon,null)},trailingContent={Icon(if(active==name)Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,null)},modifier=Modifier.clickable{active=if(active==name)"" else name})
                    if(active==name)Column(Modifier.fillMaxWidth().padding(start=12.dp,end=8.dp,bottom=12.dp)){
                        when(name){
                            "Date","Reminder"->{for((key,label) in if(name=="Date")listOf("date" to "Due date","time" to "Due time")else listOf("reminder" to "Remind at")){
                                Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={picker=key}){Text(label+" · "+t.str(key).ifEmpty{"Choose"})}
                                if(t.str(key).isNotEmpty())IconButton(onClick={set(key,"")}){Icon(Icons.Outlined.Close,"Clear "+label)}}
                            }}
                            "Priority"->Choices(listOf("Normal","Low","High","Urgent"),t.str("priority").ifEmpty{"Normal"}){set("priority",if(it=="Normal")"" else it)}
                            "Recurrence"->{
                                val rules=mapOf("Once" to "","Days" to "days","Weeks" to "weeks","Months" to "months")
                                Choices(rules.keys.toList(),rules.keys.firstOrNull{rules[it]==t.str("repeat")} ?: "Once"){set("repeat",rules.getValue(it))}
                                if(t.str("repeat")=="days")OutlinedTextField(t.str("interval"),{if(it.length<=4&&it.all(Char::isDigit))set("interval",it)},label={Text("Every N days")},singleLine=true)
                                if(t.str("repeat")=="weeks")Row(Modifier.horizontalScroll(rememberScrollState())){DayOfWeek.values().forEach{day->
                                    val bit=1 shl (day.value-1);FilterChip((t.optInt("mask") and bit)!=0,{set("mask",t.optInt("mask") xor bit)},label={Text(day.getDisplayName(TextStyle.SHORT,Locale.getDefault()))})
                                }}
                            }
                            "Tags"->{var tags by rememberSaveable{mutableStateOf((t.optJSONArray("tags")?:JSONArray()).let{a->(0 until a.length()).joinToString(", "){a.getString(it)}})}
                                OutlinedTextField(tags,{tags=it;set("tags",JSONArray(it.split(',',' ').map{v->v.trim().removePrefix("#")}.filter{v->v.isNotBlank()}.distinct()))},label={Text("Tags, separated by commas")})}
                            "Description"->{TextButton(onClick={reading=!reading}){Text(if(reading)"Edit Markdown" else "Preview Markdown")}
                                if(reading&&t.str("description").isNotEmpty())Markdown(bridge.markdown(t.str("description")))
                                else OutlinedTextField(t.str("description"),{set("description",it)},label={Text("Markdown description")},minLines=2,modifier=Modifier.fillMaxWidth())}
                            "Subtasks"->{
                                fun change(block:(MutableList<JSONObject>)->Unit){val rows=subs.rows().map{it.copyTask()}.toMutableList();block(rows);set("subs",JSONArray(rows))}
                                subs.rows().forEachIndexed{i,item->
                                    Row(verticalAlignment=Alignment.CenterVertically){
                                        Checkbox(item.optBoolean("done"),{value->change{it[i].put("done",value)}},Modifier.semantics{contentDescription="Complete subtask "+(i+1)})
                                        TextField(item.str("text"),{value->change{it[i].put("text",value)}},placeholder={Text("Subtask")},modifier=Modifier.weight(1f))
                                        if(i>0)IconButton(onClick={change{it.add(i-1,it.removeAt(i))}}){Icon(Icons.Outlined.ArrowUpward,"Move subtask up")}
                                        IconButton(onClick={change{it.removeAt(i)}}){Icon(Icons.Outlined.Close,"Remove subtask")}
                                    }
                                }
                                TextButton(onClick={change{it.add(JSONObject().put("text","").put("done",false))}}){Text("Add subtask")}
                            }
                        }
                    }
                }
                if(!adding)TextButton(onClick={confirm=true}){Text("Delete task",color=MaterialTheme.colorScheme.error)}
            }
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("Delete this task?")},text={Text("Its subtasks will also be deleted.")},confirmButton={TextButton(onClick={delete(t)}){Text("Delete")}},dismissButton={TextButton(onClick={confirm=false}){Text("Cancel")}})
    if(picker=="date"){
        val date=t.str("date").takeIf{it.isNotEmpty()}?.let{LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()}
        val state=rememberDatePickerState(initialSelectedDateMillis=date)
        DatePickerDialog(onDismissRequest={picker=""},confirmButton={TextButton(onClick={state.selectedDateMillis?.let{set("date",Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())};picker=""}){Text("OK")}},dismissButton={TextButton(onClick={picker=""}){Text("Cancel")}}){DatePicker(state)}
    }else if(picker.isNotEmpty()){
        val time=t.str(picker).takeIf{it.isNotEmpty()}?.let(LocalTime::parse)?:LocalTime.now()
        val state=rememberTimePickerState(time.hour,time.minute,android.text.format.DateFormat.is24HourFormat(LocalContext.current))
        AlertDialog(onDismissRequest={picker=""},title={Text(if(picker=="time")"Due time" else "Reminder time")},text={TimeInput(state)},confirmButton={TextButton(onClick={set(picker,LocalTime.of(state.hour,state.minute).toString());picker=""}){Text("OK")}},dismissButton={TextButton(onClick={picker=""}){Text("Cancel")}})
    }
}

@Composable internal fun Choices(labels:List<String>,selected:String,choose:(String)->Unit){
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
        labels.forEach{FilterChip(it==selected,{choose(it)},label={Text(it)})}
    }
}
@Composable private fun Markdown(html:String){
    val colors=MaterialTheme.colorScheme
    val ink=String.format("#%06x",colors.onSurface.toArgb() and 0xffffff)
    val body="<meta name='viewport' content='width=device-width'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\"><style>body{font:1em/1.5 sans-serif;color:$ink;overflow-wrap:anywhere}pre{white-space:pre-wrap}table{border-collapse:collapse}td,th{padding:.3em;border:1px solid currentColor}</style>"+html
    AndroidView(factory={ctx->WebView(ctx).apply{settings.blockNetworkLoads=true;settings.allowFileAccess=false;settings.allowContentAccess=false;setBackgroundColor(AndroidColor.TRANSPARENT);webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest)=true}}},update={if(it.tag!=body){it.tag=body;it.loadDataWithBaseURL(null,body,"text/html","utf-8",null)}},modifier=Modifier.fillMaxWidth().heightIn(min=64.dp,max=320.dp))
}
