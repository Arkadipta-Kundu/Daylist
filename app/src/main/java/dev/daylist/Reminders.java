package dev.daylist;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.Build;
import org.json.*;
import java.time.*;

public class Reminders extends BroadcastReceiver {
    static final String CHANNEL = "task_reminders";
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("tasks", 0); }
    static JSONArray tasks(Context c) throws JSONException { return new JSONArray(prefs(c).getString("list","[]")); }
    static boolean exact(Context c) {
        return Build.VERSION.SDK_INT < 31 || c.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }
    static NotificationManager notifications(Context c) {
        NotificationManager n = c.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL,"Task alarms",NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
        channel.enableVibration(true);
        n.createNotificationChannel(channel);
        return n;
    }
    static boolean enabled(Context c) {
        NotificationManager n = notifications(c);
        return n.areNotificationsEnabled() && n.getNotificationChannel(CHANNEL).getImportance()!=0;
    }
    static LocalDate next(JSONObject t, LocalDate start, LocalDate floor) {
        LocalDate first = floor.isAfter(start) ? floor : start;
        int step = Math.max(1,t.optInt("interval",1));
        int anchor=t.optInt("monthDay",start.getDayOfMonth());
        switch(t.optString("repeat")) {
            case "months":
                LocalDate monthly=first.withDayOfMonth(Math.min(anchor,first.lengthOfMonth()));
                if(monthly.isBefore(first)){LocalDate m=first.plusMonths(1);monthly=m.withDayOfMonth(Math.min(t.optInt("monthDay",start.getDayOfMonth()),m.lengthOfMonth()));}
                return monthly;
            case "days":
                long distance = first.toEpochDay()-start.toEpochDay();
                return first.plusDays((step-distance%step)%step);
            case "weeks":
                int mask=t.optInt("mask");
                for(int i=0;i<7;i++) {
                    LocalDate d=first.plusDays(i);
                    if((mask & (1<<(d.getDayOfWeek().getValue()-1)))!=0) return d;
                }
                return null;
            default: return first.equals(start)?start:null;
        }
    }
    static synchronized void reconcile(Context c) {
        try {
            JSONArray list=tasks(c);
            long earliest=Long.MAX_VALUE, now=System.currentTimeMillis();
            LocalDate today=LocalDate.now();
            boolean allowed=enabled(c), changed=false;
            for(int i=0;i<list.length();i++) {
                JSONObject t=list.getJSONObject(i);
                String alarmTime=t.optString("reminder");
                if(alarmTime.isEmpty() && prefs(c).getBoolean("notifyAtDue",true))alarmTime=t.optString("time");
                if(t.optBoolean("done") || alarmTime.isEmpty() || t.optString("date").isEmpty()) continue;
                LocalDate start=LocalDate.parse(t.getString("date"));
                String last=t.optString("alerted");
                LocalDate floor=last.isEmpty()?start:LocalDate.parse(last).plusDays(1);
                LocalDate d=next(t,start,floor);
                long snooze=t.optLong("snooze",0);
                if(snooze>now)earliest=Math.min(earliest,snooze);
                boolean repeatAlert=snooze>0&&snooze<=now&&allowed;
                LocalDate nextRegular=d;
                if(repeatAlert && (d==null || instant(d,LocalTime.parse(alarmTime))>now))d=LocalDate.parse(last);
                else repeatAlert=false;
                if(d==null) continue;
                LocalTime time=LocalTime.parse(alarmTime);
                if(instant(d,time)<=now && allowed) {
                    // Coalesce missed occurrences, retaining today's future occurrence.
                    LocalDate limit=instant(today,time)<=now?today:today.minusDays(1);
                    if(!repeatAlert){if("days".equals(t.optString("repeat"))) {
                        int step=Math.max(1,t.optInt("interval",1));
                        d=start.plusDays((limit.toEpochDay()-start.toEpochDay())/step*step);
                    } else if("months".equals(t.optString("repeat"))){
                        LocalDate m=limit.withDayOfMonth(Math.min(t.optInt("monthDay",start.getDayOfMonth()),limit.lengthOfMonth()));
                        if(m.isAfter(limit)){m=m.minusMonths(1);m=m.withDayOfMonth(Math.min(t.optInt("monthDay",start.getDayOfMonth()),m.lengthOfMonth()));}
                        d=m;
                    } else if("weeks".equals(t.optString("repeat"))) {
                        for(int j=0;j<7;j++) {
                            LocalDate candidate=limit.minusDays(j);
                            if(!candidate.isBefore(d) && candidate.equals(next(t,start,candidate))) { d=candidate; break; }
                        }
                    }
                    }
                    t.remove("snooze");String id=t.getString("id");
                    Intent open=new Intent(c,Main.class).putExtra("task",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    open.setData(android.net.Uri.parse("daylist:task/"+id));
                    PendingIntent tap=PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    Intent complete=new Intent(c,Reminders.class).setAction("dev.daylist.COMPLETE").setData(android.net.Uri.parse("daylist:done/"+id+"/"+d)).putExtra("id",id).putExtra("day",d.toString()).putExtra("start",t.getString("date"));
                    PendingIntent done=PendingIntent.getBroadcast(c,0,complete,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    Intent delay=new Intent(complete).setAction("dev.daylist.SNOOZE");
                    PendingIntent later=PendingIntent.getBroadcast(c,0,delay,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
                    Notification alert=new Notification.Builder(c,CHANNEL).setSmallIcon(R.drawable.alert_mark)
                        .addAction(R.drawable.alert_mark,"Complete",done).addAction(R.drawable.alert_mark,"Snooze 10 min",later).setCategory(Notification.CATEGORY_ALARM).setContentTitle(t.getString("title"))
                        .setContentText(d+" · "+time+"  "+t.optString("description")).setAutoCancel(true).setContentIntent(tap).build();
                    notifications(c).notify(id,1,alert);
                    t.put("alerted",d.toString()); changed=true;
                    d=repeatAlert?nextRegular:next(t,start,d.plusDays(1));
                }
                if(d!=null && instant(d,time)>now) earliest=Math.min(earliest,instant(d,time));
            }
            if(changed && !prefs(c).edit().putString("list",list.toString()).commit()) throw new IllegalStateException("Storage unavailable");
            AlarmManager am=c.getSystemService(AlarmManager.class);
            PendingIntent tick=PendingIntent.getBroadcast(c,0,new Intent(c,Reminders.class),PendingIntent.FLAG_IMMUTABLE);
            if(earliest==Long.MAX_VALUE) am.cancel(tick);
            else {
                try { if(exact(c)) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,earliest,tick); return; } }
                catch(SecurityException accessChanged) { }
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,earliest,tick);
            }
        } catch(JSONException e) { throw new IllegalStateException(e); }
    }
    static long instant(LocalDate date,LocalTime time) {
        return date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    @Override public void onReceive(Context c,Intent intent) { if("dev.daylist.COMPLETE".equals(intent.getAction())||"dev.daylist.SNOOZE".equals(intent.getAction())){
        synchronized(Reminders.class){try{
            JSONArray list=tasks(c);for(int i=0;i<list.length();i++){JSONObject t=list.getJSONObject(i);
                if(!t.getString("id").equals(intent.getStringExtra("id"))||t.optBoolean("done")||!t.optString("alerted").equals(intent.getStringExtra("day"))||!t.optString("date").equals(intent.getStringExtra("start")))continue;
                LocalDate start=LocalDate.parse(t.getString("date"));
                LocalDate following=next(t,start,LocalDate.now().plusDays(1));
                if("dev.daylist.SNOOZE".equals(intent.getAction()))t.put("snooze",System.currentTimeMillis()+600000L);
                else if(t.optString("repeat").isEmpty()||following==null)t.put("done",true);
                else {if(t.optString("repeat").equals("months"))t.put("monthDay",t.optInt("monthDay",start.getDayOfMonth()));t.put("date",following.toString());JSONArray subs=t.getJSONArray("subs");for(int j=0;j<subs.length();j++)subs.getJSONObject(j).put("done",false);}
                if(prefs(c).edit().putString("list",list.toString()).commit()){notifications(c).cancel(t.getString("id"),1);Cloud.enqueue(c,false);}break;}
        }catch(JSONException e){throw new IllegalStateException(e);}}
    }reconcile(c); }
}
