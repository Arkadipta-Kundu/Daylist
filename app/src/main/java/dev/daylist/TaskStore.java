package dev.daylist;
import android.content.Context;
import org.json.*;
import java.util.concurrent.*;
final class TaskStore {
    static final ExecutorService io=Executors.newSingleThreadExecutor();
    static void maintain(Context c){
        io.execute(()->{
            android.content.SharedPreferences migration=c.getSharedPreferences("upgrades",0);
            if(!migration.getBoolean("localOnly",false)){
                android.app.job.JobScheduler scheduler=c.getSystemService(android.app.job.JobScheduler.class);
                for(android.app.job.JobInfo job:scheduler.getAllPendingJobs()){
                    if(job.getService().getClassName().equals("androidx.work.impl.background.systemjob.SystemJobService"))
                        scheduler.cancel(job.getId());
                }
                c.deleteSharedPreferences("drive");
                migration.edit().putBoolean("localOnly",true).apply();
            }
            Repair.ensure(c);Reminders.reconcile(c);
        });
    }
    static boolean write(Context c,String raw,boolean remove){
            synchronized(Reminders.class){
                try{
                    JSONObject t=new JSONObject(raw);String id=t.getString("id");
                    JSONArray list=Reminders.tasks(c);
                    for(int i=0;i<list.length();i++){
                        JSONObject old=list.getJSONObject(i);
                        if(!id.equals(old.getString("id"))) continue;
                        boolean same=true;
                        for(String key:new String[]{"date","time","repeat","mask","interval","reminder","reminderDate"})
                            same &= old.optString(key).equals(t.optString(key));
                        if(!same && old.optString("repeat").isEmpty() && t.optString("repeat").isEmpty()
                           && !t.optString("reminderDate").isEmpty()
                           && old.optString("reminderDate").equals(t.optString("reminderDate"))
                           && old.optString("reminder").equals(t.optString("reminder")))same=true;
                        if(same){t.put("alerted",old.optString("alerted"));if(old.has("snooze"))t.put("snooze",old.getLong("snooze"));}else {t.remove("alerted");t.remove("snooze");}
                        list.remove(i);break;
                    }
                    if(!remove)list.put(t);
                    if(!Reminders.prefs(c).edit().putString("list",list.toString()).commit())return false;
                    Reminders.notifications(c).cancel(id,1);
                    Reminders.reconcile(c);TodayWidget.changed(c);return true;
                } catch(Exception e){return false;}
            }
    }
}
