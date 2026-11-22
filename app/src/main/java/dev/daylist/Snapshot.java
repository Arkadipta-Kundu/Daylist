package dev.daylist;
import android.content.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

final class Snapshot {
 static String read(InputStream input)throws IOException{
    try(InputStream in=input;ByteArrayOutputStream out=new ByteArrayOutputStream()){
        byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>16000000)throw new IOException("Backup exceeds 16 MB");out.write(buf,0,n);}
        return out.toString("UTF-8");
    }
 }
 static String hash(String s)throws Exception{
    byte[] bytes=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    StringBuilder out=new StringBuilder();for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();
 }
 static synchronized String make(Context c)throws Exception{
    JSONObject data=new JSONObject().put("tasks",Reminders.tasks(c)).put("notifyAtDue",Reminders.prefs(c).getBoolean("notifyAtDue",true))
        .put("enter",Reminders.prefs(c).getBoolean("enter",false));
    String payload=data.toString();
    return new JSONObject().put("format","Daylist").put("version",1).put("appVersion","1.1").put("created",Instant.now().toString())
        .put("sha256",hash(payload)).put("payload",payload).toString();
 }
 static JSONObject validate(String raw)throws Exception{
    JSONObject e=new JSONObject(raw);String s=e.getString("payload");
    if(!"Daylist".equals(e.getString("format"))||e.getInt("version")!=1||!hash(s).equals(e.getString("sha256")))throw new IOException("Unsupported or damaged backup");
    JSONObject data=new JSONObject(s);JSONArray a=data.getJSONArray("tasks");Set<String> ids=new HashSet<>();
    for(int i=0;i<a.length();i++){
        JSONObject t=a.getJSONObject(i);if(t.getString("id").isEmpty()||!ids.add(t.getString("id"))||t.getString("title").trim().isEmpty())throw new IOException("Invalid task");
        String date=t.getString("date"), time=t.getString("time"),rem=t.optString("reminder"),rule=t.getString("repeat");
        if(!date.isEmpty())LocalDate.parse(date); if(!time.isEmpty())LocalTime.parse(time);if(!rem.isEmpty())LocalTime.parse(rem);
        if(date.isEmpty()&&!(time+rem+rule).isEmpty()||!Arrays.asList("","days","weeks","months").contains(rule)||t.getInt("interval")<1||t.getInt("interval")>3650||rule.equals("weeks")&&(t.getInt("mask")<1||t.getInt("mask")>127))throw new IOException("Invalid schedule");
        if(!t.optString("alerted").isEmpty())LocalDate.parse(t.getString("alerted"));
        if(t.has("monthDay")&&(t.getInt("monthDay")<1||t.getInt("monthDay")>31))throw new IOException("Invalid monthly date");
        if(t.optLong("snooze")>0&&t.optString("alerted").isEmpty())throw new IOException("Missing snooze occurrence");
        t.getBoolean("done");t.getString("description");JSONArray subs=t.getJSONArray("subs");
        for(int k=0;k<subs.length();k++){subs.getJSONObject(k).getString("text");subs.getJSONObject(k).getBoolean("done");}
        if(t.has("tags")){JSONArray tags=t.getJSONArray("tags");for(int k=0;k<tags.length();k++)tags.getString(k);}
    }return data;
 }
 static void restore(Context c,JSONObject data)throws Exception{
    synchronized(Reminders.class){
        if(!Reminders.prefs(c).edit().putString("list",data.getJSONArray("tasks").toString()).putBoolean("enter",data.optBoolean("enter"))
            .putBoolean("notifyAtDue",data.optBoolean("notifyAtDue",true)).commit())throw new IOException("Storage unavailable");
        Reminders.notifications(c).cancelAll();Reminders.reconcile(c);
    }
 }
}
