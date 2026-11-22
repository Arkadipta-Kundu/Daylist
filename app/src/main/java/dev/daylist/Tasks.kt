package dev.daylist

import org.json.JSONArray
import org.json.JSONObject
import java.time.*
import java.time.format.*
import java.util.Locale

internal fun JSONArray.rows()=(0 until length()).map{getJSONObject(it)}
internal fun JSONObject.copyTask()=JSONObject(toString())
internal fun JSONObject.str(key:String)=optString(key,"")
internal fun JSONObject.group(today:String)=when{
    optBoolean("done")->"Completed"
    str("date").isEmpty()->"Inbox"
    str("date")<=today->"Today"
    else->"Upcoming"
}
internal fun prettyDay(iso:String):String{
    val day=LocalDate.parse(iso)
    return when(day){LocalDate.now()->"Today";LocalDate.now().plusDays(1)->"Tomorrow";else->day.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))}
}
internal fun prettyTime(raw:String):String=LocalTime.parse(raw).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
internal fun toggle(t:JSONObject):JSONObject=t.copyTask().apply {
    if(str("repeat").isEmpty() || optBoolean("done"))put("done",!optBoolean("done"))
    else {
        val date=LocalDate.parse(str("date"))
        if(str("repeat")=="months")put("monthDay",optInt("monthDay",date.dayOfMonth))
        val next=Reminders.next(this,date,maxOf(LocalDate.now(),date).plusDays(1))
        if(str("reminderDate").isNotEmpty())put("reminderDate",LocalDate.parse(str("reminderDate")).plusDays(next.toEpochDay()-date.toEpochDay()).toString())
        put("date",next.toString());remove("alerted");remove("snooze")
        getJSONArray("subs").rows().forEach{it.put("done",false)}
    }
}
internal fun JSONObject.issue():String?=when{
    str("title").isBlank()->"Enter a task name."
    str("reminderError").isNotEmpty()->str("reminderError")
    str("reminderDate").isNotEmpty() && str("reminder").isEmpty()->"Choose a reminder time."
    str("reminder").isNotEmpty() && str("reminderDate").isEmpty() && str("date").isEmpty()->"Choose a reminder date."
    str("date").isEmpty() && listOf("time","repeat").any{str(it).isNotEmpty()}->"Choose a date for this schedule."
    str("repeat")=="weeks" && optInt("mask")==0->"Choose at least one weekday."
    str("repeat")=="days" && optInt("interval") !in 1..3650->"Choose an interval from 1 to 3650 days."
    else->null
}
internal fun normalized(t:JSONObject):JSONObject=t.copyTask().apply{
    val clause=str("reminderClause")
    put("title",if(clause.isNotBlank() && str("reminderError").isEmpty())str("title").replaceFirst(clause,"").trim() else str("title").trim())
    remove("reminderClause");remove("reminderError")
    put("interval",optInt("interval",1).coerceIn(1,3650))
    val entries=optJSONArray("subs")?.rows().orEmpty().filter{it.str("text").isNotBlank()}
    put("subs",JSONArray(entries))
    if(str("repeat")=="weeks"){val d=LocalDate.parse(str("date"));put("date",Reminders.next(this,d,d).toString())}
    if(str("repeat")=="months" && !has("monthDay"))put("monthDay",LocalDate.parse(str("date")).dayOfMonth)
}

internal fun JSONObject.futureReminderIssue():String? {
    if(str("reminder").isEmpty())return null
    val day=str("reminderDate").ifEmpty{str("date")}
    if(day.isEmpty())return "Choose a reminder date."
    val instant=LocalDate.parse(day).atTime(LocalTime.parse(str("reminder"))).atZone(ZoneId.systemDefault()).toInstant()
    return if(instant.isAfter(Instant.now()))null else "Choose a future reminder time."
}
