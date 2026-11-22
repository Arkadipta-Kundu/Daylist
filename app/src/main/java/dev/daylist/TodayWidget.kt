package dev.daylist

import android.app.*
import android.appwidget.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.widget.*
import android.widget.RemoteViewsService.RemoteViewsFactory
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.*

internal data class WidgetItem(val id:String,val title:String,val date:String,val detail:String)
internal data class WidgetList(val rows:List<WidgetItem>,val done:Int)
internal fun widgetList(raw:String,day:String):WidgetList {
    val tasks=JSONArray(raw).rows()
    val rows=tasks.filter{it.group(day)=="Today"}.sortedWith(compareBy({it.str("date")},{it.str("time")},{it.str("title")})).map{
        val meta=listOf(if(it.str("date")<day)"Overdue" else "",it.str("time"),it.str("priority")).filter(String::isNotBlank)
        WidgetItem(it.str("id"),it.str("title"),it.str("date"),meta.joinToString(" · "))
    }
    return WidgetList(rows,tasks.count{it.optBoolean("done")&&it.str("date")==day})
}

private object WidgetCache {
    private var last:String?=null
    private var date=""
    private var data=WidgetList(emptyList(),0)
    @Synchronized fun get(c:Context):WidgetList{
        val raw=Reminders.prefs(c).getString("list","[]")?:"[]"
        val today=LocalDate.now().toString()
        if(last!=raw||today!=date){data=widgetList(raw,today);last=raw;date=today}
        return data
    }
}
internal fun finishOccurrence(c:Context,id:String,date:String):Boolean=synchronized(Reminders::class.java){
    val t=Reminders.tasks(c).rows().find{it.str("id")==id}?:return@synchronized false
    if(t.str("date")!=date||t.group(LocalDate.now().toString())!="Today")return@synchronized false
    TaskStore.write(c,toggle(t).toString(),false)
}

class TodayWidget:AppWidgetProvider(){
    override fun onReceive(c:Context,msg:Intent){
        if(msg.action==ROW&&msg.getStringExtra("op")=="open"){
            c.startActivity(launch(c,"task").putExtra("task",msg.getStringExtra("id")));return
        }
        val pending=goAsync()
        TaskStore.io.execute{
            try{if(msg.action!=ROW || msg.getStringExtra("op")!="complete" || !finishOccurrence(c,msg.getStringExtra("id").orEmpty(),msg.getStringExtra("date").orEmpty()))changed(c)}
            finally{pending.finish()}
        }
    }
    companion object{
        private const val ROW="dev.daylist.WIDGET_ACTION"
        private fun launch(c:Context,route:String)=Intent(c,Main::class.java).setData(Uri.parse("daylist:widget/"+route)).putExtra("route",route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        private fun nav(c:Context,route:String)=PendingIntent.getActivity(c,0,launch(c,route),PendingIntent.FLAG_IMMUTABLE)
        @JvmStatic fun changed(c:Context){
            val manager=AppWidgetManager.getInstance(c)
            val ids=manager.getAppWidgetIds(ComponentName(c,TodayWidget::class.java))
            val alarm=c.getSystemService(AlarmManager::class.java)
            val tick=PendingIntent.getBroadcast(c,0,Intent(c,TodayWidget::class.java).setAction("dev.daylist.MIDNIGHT"),PendingIntent.FLAG_IMMUTABLE)
            if(ids.isEmpty()){alarm.cancel(tick);return}
            val data=WidgetCache.get(c)
            val day=LocalDate.now()
            for(id in ids){
                val small=manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)<200
                val v=RemoteViews(c.packageName,R.layout.today_widget)
                val heading=if(small)"Today" else "Today · "+day.format(DateTimeFormatter.ofPattern("EEE, d MMM",Locale.getDefault()))
                v.setTextViewText(R.id.heading,heading)
                v.setTextViewText(R.id.progress,if(small)"${data.rows.size} remaining" else "${data.rows.size} remaining · ${data.done} completed due today")
                v.setOnClickPendingIntent(R.id.heading,nav(c,"today"));v.setOnClickPendingIntent(R.id.addTask,nav(c,"add"))
                v.setRemoteAdapter(R.id.tasks,Intent(c,TodayRows::class.java))
                v.setEmptyView(R.id.tasks,R.id.empty)
                val flags=if(Build.VERSION.SDK_INT>=31)PendingIntent.FLAG_MUTABLE else 0
                v.setPendingIntentTemplate(R.id.tasks,PendingIntent.getBroadcast(c,id,Intent(c,TodayWidget::class.java).setAction(ROW),flags or PendingIntent.FLAG_UPDATE_CURRENT))
                manager.updateAppWidget(id,v)
            }
            manager.notifyAppWidgetViewDataChanged(ids,R.id.tasks)
            alarm.set(AlarmManager.RTC,day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),tick)
        }
    }
}
class TodayRows:RemoteViewsService(){
    override fun onGetViewFactory(intent:Intent):RemoteViewsFactory=Rows(applicationContext)
    private class Rows(val c:Context):RemoteViewsFactory{
        private var rows=emptyList<WidgetItem>()
        override fun onCreate(){onDataSetChanged()}
        override fun onDataSetChanged(){rows=WidgetCache.get(c).rows}
        override fun onDestroy(){rows=emptyList()}
        override fun getCount()=rows.size
        override fun getViewTypeCount()=1
        override fun getLoadingView():RemoteViews?=null
        override fun hasStableIds()=false
        override fun getItemId(position:Int)=position.toLong()
        override fun getViewAt(position:Int):RemoteViews?{
            val t=rows.getOrNull(position)?:return null
            return RemoteViews(c.packageName,R.layout.widget_row).apply{
                setTextViewText(R.id.taskTitle,if(t.detail.isEmpty())t.title else t.title+"\n"+t.detail)
                setContentDescription(R.id.finish,"Complete "+t.title)
                fun action(op:String)=Intent().putExtra("id",t.id).putExtra("date",t.date).putExtra("op",op)
                setOnClickFillInIntent(R.id.taskTitle,action("open"))
                setOnClickFillInIntent(R.id.finish,action("complete"))
            }
        }
    }
}
