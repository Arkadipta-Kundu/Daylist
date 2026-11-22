package dev.daylist;

import android.app.job.*;
import android.content.*;

public class Repair extends JobService {
    static void ensure(Context context) {
        JobScheduler jobs=context.getSystemService(JobScheduler.class);
        if(jobs.getPendingJob(27)==null)
            jobs.schedule(new JobInfo.Builder(27,new ComponentName(context,Repair.class))
                .setPeriodic(15*60*1000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters parameters){
        TaskStore.io.execute(()->{try{Reminders.reconcile(this);}finally{jobFinished(parameters,false);}});
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){return true;}
}
