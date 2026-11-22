@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.daylist

import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import org.json.*
import java.time.LocalDate
import java.util.UUID

fun mount(a:Main){
    a.enableEdgeToEdge()
    a.setContent {
        val appearance=remember{a.getSharedPreferences("appearance",0)}
        var mode by remember{mutableStateOf(appearance.getString("theme","System")?:"System")}
        DayTheme(a,mode){ App(a,mode){mode=it;appearance.edit().putString("theme",it).apply()} }
    }
}

@Composable private fun App(a:Main,mode:String,onMode:(String)->Unit){
    val bridge=remember{a.Bridge()}
    var revision by remember{mutableIntStateOf(0)}
    var dataRevision by remember{mutableIntStateOf(0)}
    DisposableEffect(a){
        a.redraw=Runnable{revision++}
        val listener=SharedPreferences.OnSharedPreferenceChangeListener{_,key->if(key=="list"||key==null)dataRevision++ else revision++}
        val stores=listOf(Reminders.prefs(a))
        stores.forEach{it.registerOnSharedPreferenceChangeListener(listener)}
        onDispose{a.redraw=null;stores.forEach{it.unregisterOnSharedPreferenceChangeListener(listener)}}
    }
    var loaded by remember{mutableStateOf<List<JSONObject>?>(null)}
    LaunchedEffect(dataRevision){loaded=withContext(Dispatchers.Default){JSONArray(bridge.list()).rows().sortedWith(compareBy({it.str("date")},{it.str("time")},{it.str("title").lowercase()}))}}
    val tasks=loaded.orEmpty()
    androidx.activity.compose.ReportDrawnWhen {loaded!=null}
    var dest by rememberSaveable{mutableStateOf("Today")}
    var settings by rememberSaveable{mutableStateOf(false)}
    var search by rememberSaveable{mutableStateOf(false)}
    var query by rememberSaveable{mutableStateOf("")}
    var menu by remember{mutableStateOf(false)}
    var text by remember{mutableStateOf(bridge.draftText())}
    var composing by rememberSaveable{mutableStateOf(text.isNotBlank())}
    val draftPrefs=remember{a.getPreferences(0)}
    var overrides by rememberSaveable{mutableStateOf(draftPrefs.getString("metadataDraft","{}")?:"{}")}
    LaunchedEffect(overrides){draftPrefs.edit().putString("metadataDraft",overrides).apply()}
    var parsed by remember{mutableStateOf<JSONObject?>(null)}
    var parsedText by remember{mutableStateOf<String?>(null)}
    val ready=a.ready
    LaunchedEffect(composing){if(composing)a.prepareParser()}
    LaunchedEffect(text,ready,composing){if(ready&&composing){
        if(text.isNotEmpty())delay(30)
        val input=text
        val result=suspendCancellableCoroutine<String>{c->a.engine.evaluateJavascript("capture("+JSONObject.quote(input)+")"){if(c.isActive)c.resumeWith(Result.success(it))}}
        parsed=runCatching{JSONObject(result)}.getOrNull();parsedText=input
    }}
    val draft=remember(parsed,overrides){parsed?.copyTask()?.apply{val edits=JSONObject(overrides);edits.keys().forEach{put(it,edits.get(it))}}}
    var edit by rememberSaveable{mutableStateOf<String?>(null)}
    var section by rememberSaveable{mutableStateOf("")}
    var attachment by rememberSaveable{mutableStateOf(false)}
    val scope=rememberCoroutineScope();val snack=remember{SnackbarHostState()}
    val haptic=LocalHapticFeedback.current
    fun message(s:String){scope.launch{snack.currentSnackbarData?.dismiss();snack.showSnackbar(s)}}
    val pending=remember{mutableStateMapOf<String,Boolean>()}
    val optimistic=remember{mutableStateMapOf<String,JSONObject>()}
    var sending by remember{mutableStateOf(false)}
    fun write(t:JSONObject,remove:Boolean=false,immediate:Boolean=false,after:()->Unit={}){
        val id=t.str("id");if(pending.containsKey(id))return
        pending[id]=true
        if(immediate)optimistic[id]=t
        val raw=t.toString()
        TaskStore.io.execute{
            val ok=bridge.write(raw,remove)
            a.runOnUiThread{
                pending.remove(id)
                if(ok){after()}else{sending=false;optimistic.remove(id);message("Couldn’t save changes. Try again.")}
            }
        }
    }
    LaunchedEffect(loaded,pending.size){
        optimistic.keys.toList().forEach{id->
            val stored=tasks.find{it.str("id")==id};val expected=optimistic[id]
            if(!pending.containsKey(id)&&stored!=null&&expected!=null&&stored.str("date")==expected.str("date")&&stored.optBoolean("done")==expected.optBoolean("done"))optimistic.remove(id)
        }
    }
    fun details(t:JSONObject,field:String="",adding:Boolean=false){edit=t.toString();section=field;attachment=adding}
    LaunchedEffect(revision,loaded){
        if(a.widgetRoute in listOf("today","add")){dest="Today";edit=null;search=false;query="";settings=false;composing=a.widgetRoute=="add";a.widgetRoute=""}
        if(loaded!=null){val id=bridge.selection();tasks.find{it.str("id")==id}?.let{settings=false;composing=false;details(it)}}
    }
    fun setText(value:String){text=value;bridge.saveDraft(value)}
    fun send(){
        if(sending || !composing || parsedText!=text || edit!=null)return
        val t=draft?.copyTask()?:return
        (t.issue()?:t.futureReminderIssue())?.let{message(it);return}
        val saved=normalized(t).put("id",UUID.randomUUID().toString())
        sending=true
        write(saved){
        sending=false
        dest=saved.group(LocalDate.now().toString());search=false;query="";setText("");overrides="{}";composing=false
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        scope.launch{snack.currentSnackbarData?.dismiss();if(snack.showSnackbar("Task added","Undo",duration=SnackbarDuration.Short)==SnackbarResult.ActionPerformed)write(saved,true)}
        }
    }
    fun back(){when{edit!=null->edit=null;settings->settings=false;composing->composing=false;search->{search=false;query=""};else->dest="Today"}}
    BackHandler(edit!=null||settings||composing||search||dest=="Completed"){back()}
    var today by remember{mutableStateOf(LocalDate.now().toString())}
    LaunchedEffect(revision){today=LocalDate.now().toString();while(true){delay(60000);today=LocalDate.now().toString()}}
    val projected=remember(tasks,optimistic.toMap()){if(optimistic.isEmpty())tasks else tasks.map{optimistic[it.str("id")]?:it}}
    val items=remember(projected,search,query,today,dest){projected.filter{if(search && query.isNotBlank())listOf(it.str("title"),it.str("description"),it.str("tags")).any{s->s.contains(query,true)} else it.group(today)==dest}}
    Scaffold(topBar={TopAppBar(title={
        if(search&&!settings)TextField(query,{query=it},singleLine=true,placeholder={Text("Search tasks")},modifier=Modifier.fillMaxWidth())
        else Text(if(settings)"Settings" else if(dest=="Completed")"Completed" else "Daylist",style=MaterialTheme.typography.headlineSmall)
    },navigationIcon={if(settings||search||dest=="Completed")IconButton(onClick={if(settings)settings=false else if(search){search=false;query=""}else dest="Today"}){Icon(Icons.Outlined.ArrowBack,"Back")}},
    actions={if(!settings){
        if(!search && tasks.isNotEmpty())IconButton(onClick={search=true}){Icon(Icons.Outlined.Search,"Search")}
        Box{IconButton(onClick={menu=true}){Icon(Icons.Outlined.MoreVert,"More options")};DropdownMenu(menu,{menu=false}){
            DropdownMenuItem({Text("Completed")},{menu=false;dest="Completed";composing=false;search=false;query=""})
            DropdownMenuItem({Text("Settings")},{menu=false;settings=true})
        }}
    }})},snackbarHost={SnackbarHost(snack)},floatingActionButton={if(!settings&&!composing)FloatingActionButton(onClick={composing=true}){Icon(Icons.Outlined.Add,"New task")}},
    bottomBar={AnimatedVisibility(composing&&!settings,enter=expandVertically()+fadeIn(),exit=shrinkVertically()+fadeOut()){
        Surface(color=MaterialTheme.colorScheme.surfaceContainer,shape=Metrics.panel,modifier=Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(8.dp)){
            Column(Modifier.padding(4.dp)){
                val focus=remember{FocusRequester()};val keyboard=LocalSoftwareKeyboardController.current
                LaunchedEffect(Unit){focus.requestFocus();keyboard?.show()}
                Row(verticalAlignment=Alignment.CenterVertically){
                    IconButton(enabled=draft!=null&&parsedText==text,onClick={draft?.let{details(it,"",true)}}){Icon(Icons.Outlined.AttachFile,"Add details")}
                    Box(Modifier.weight(1f).padding(vertical=12.dp)){
                        if(text.isEmpty())Text("What needs doing?",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(text,{setText(it)},enabled=!sending,modifier=Modifier.fillMaxWidth().focusRequester(focus).semantics{contentDescription="New task text"},maxLines=5,
                            textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions=KeyboardOptions(imeAction=if(bridge.pref("enter"))ImeAction.Send else ImeAction.Default),keyboardActions=KeyboardActions(onSend={send()}))
                    }
                    IconButton(enabled=draft!=null&&parsedText==text&&text.isNotBlank(),onClick={send()}){Icon(Icons.Outlined.Send,"Send task")}
                }
                if(draft!=null)Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    for((field,label) in labels(draft)) AssistChip(onClick={details(draft,field,true)},label={Text(label)})
                }
            }
        }
    }}){padding->
        Box(Modifier.fillMaxSize().padding(padding),contentAlignment=Alignment.TopCenter){
            Column(Modifier.widthIn(max=840.dp).fillMaxSize()){
                if(settings)Preferences(a,bridge,mode,onMode,revision)
                else{
                    if(dest!="Completed"&&!search){
                        val tabs=listOf("Inbox","Today","Upcoming")
                        TabRow(tabs.indexOf(dest).coerceAtLeast(0)){tabs.forEach{Tab(dest==it,{dest=it},text={Text(it)})}}
                    }
                    if(loaded==null)Spacer(Modifier.height(1.dp)) else if(items.isEmpty()) Column(Modifier.padding(horizontal=24.dp,vertical=32.dp)){
                        Text(when{search->"No matching tasks";dest=="Today"->"Nothing due today";dest=="Inbox"->"Your inbox is empty";dest=="Upcoming"->"No upcoming tasks";else->"No completed tasks"},style=MaterialTheme.typography.titleMedium)
                        if(!search&&dest=="Inbox")Text("Tap + to capture a task without a date.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)

                    }else LazyColumn(contentPadding=PaddingValues(bottom=88.dp)){
                        itemsIndexed(items,key={_,t->t.str("id")}){i,t->
                            Column(Modifier.animateItem()){
                                val date=t.str("date")
                                if(!search&&dest=="Upcoming"&&(i==0||items[i-1].str("date")!=date))Heading(prettyDay(date))
                                if(!search&&dest=="Today"&&(i==0||items[i-1].str("date")<today && date==today)&&items.first().str("date")<today)Heading(if(date<today)"Overdue" else "Today")
                                TaskRow(t,today,{details(t)},{write(toggle(t),immediate=true)},dest!="Upcoming"||search){write(it)}
                            }
                        }
                    }
                }
            }
        }
    }
    if(a.transfer.dialogTitle.isNotEmpty()) {
        val transfer=a.transfer
        AlertDialog(onDismissRequest={transfer.dismiss()}, title={Text(transfer.dialogTitle)},text={
            Text(transfer.dialogBody)
        },confirmButton={TextButton(onClick={val action=transfer.confirm;transfer.dismiss();action?.run()}){Text(if(transfer.confirm!=null)"Replace" else "Close")}},dismissButton={if(transfer.confirm!=null)TextButton(onClick={transfer.dismiss()}){Text("Cancel")}})
    }
    edit?.let{initial-> TaskSheet(initial,section,attachment,{edit=null},{edited->
        if(attachment){
            val diff=JSONObject(overrides);val before=JSONObject(initial)
            edited.keys().forEach{k->if(edited.opt(k).toString()!=before.opt(k).toString())diff.put(k,edited.get(k))}
            overrides=diff.toString();edit=null
        }else write(normalized(edited)){edit=null}
    },{t->write(t,true){edit=null}},{t->write(toggle(t)){edit=null}},bridge) }
}

internal fun labels(t:JSONObject):List<Pair<String,String>> = buildList{
    if(t.str("date").isNotEmpty())add("Date" to prettyDay(t.str("date")))
    if(t.str("time").isNotEmpty())add("Date" to prettyTime(t.str("time")))
    if(t.str("priority").isNotEmpty())add("Priority" to t.str("priority"))
    if(t.str("reminderError").isNotEmpty())add("Reminder" to "Fix reminder")
    if(t.str("reminder").isNotEmpty())add("Reminder" to ((t.str("reminderDate").ifEmpty{t.str("date")}).takeIf{it.isNotBlank()}?.let{prettyDay(it)+", "}.orEmpty()+prettyTime(t.str("reminder"))+" 🔔"))
    if(t.str("repeat").isNotEmpty())add("Recurrence" to when(t.str("repeat")){"weeks"->"Weekly";"months"->"Monthly";else->if(t.optInt("interval")==1)"Daily" else "Every "+t.optInt("interval")+" days"})
    if(t.str("description").isNotBlank())add("Description" to "Description")
    if((t.optJSONArray("subs")?.length()?:0)>0)add("Subtasks" to "Subtasks")
    t.optJSONArray("tags")?.let{for(i in 0 until it.length())add("Tags" to "#"+it.getString(i))}
}

@Composable private fun TaskRow(t:JSONObject,today:String,open:()->Unit,complete:()->Unit,showDate:Boolean,save:(JSONObject)->Unit){
    val done=t.optBoolean("done");val muted=MaterialTheme.colorScheme.onSurfaceVariant
    var expanded by rememberSaveable(t.str("id")){mutableStateOf(false)}
    val checklist=t.optJSONArray("subs")?.rows().orEmpty()
    Row(Modifier.fillMaxWidth().clickable(onClick=open).padding(start=4.dp,end=16.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically){
        Checkbox(done,{complete()},Modifier.semantics{contentDescription=(if(done)"Reopen " else "Complete ")+t.str("title")})
        Column(Modifier.weight(1f).padding(vertical=8.dp)){
            Text(t.str("title"),style=MaterialTheme.typography.bodyLarge,color=if(done)muted else MaterialTheme.colorScheme.onSurface,textDecoration=if(done)TextDecoration.LineThrough else null)
            val meta=buildList{
                val date=t.str("date")
                if(showDate && date.isNotEmpty())add((if(!done && date<today)"Overdue · " else "")+prettyDay(date))
                if(t.str("time").isNotEmpty())add(prettyTime(t.str("time")))
                if(t.str("priority").isNotBlank())add(t.str("priority")+" priority")
                val subs=t.optJSONArray("subs")?.rows().orEmpty();if(subs.isNotEmpty())add("${subs.count{it.optBoolean("done")}}/${subs.size} subtasks")
                labels(t).filter{it.first in listOf("Recurrence","Tags")}.forEach{add(it.second)}
            }
            if(meta.isNotEmpty())Text(meta.joinToString(" · "),color=muted,style=MaterialTheme.typography.bodySmall)
        }
        if(checklist.isNotEmpty())IconButton(onClick={expanded=!expanded}){Icon(if(expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,if(expanded)"Hide subtasks" else "Show subtasks")}
    }
    AnimatedVisibility(expanded){
        Column(Modifier.padding(start=40.dp,end=16.dp)){
            checklist.forEachIndexed{index,item->
                Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(item.optBoolean("done"),{value->val updated=t.copyTask();updated.getJSONArray("subs").getJSONObject(index).put("done",value);save(updated)},Modifier.semantics{contentDescription="Toggle "+item.str("text")})
                    Text(item.str("text"),style=MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(start=52.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
}
