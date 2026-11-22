package dev.daylist;
import android.content.*;
import androidx.work.*;
import com.google.android.gms.auth.api.identity.*;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Tasks;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.json.*;

public class Cloud extends Worker {
 static final String API="https://www.googleapis.com/drive/v3/files";
 static SharedPreferences state(Context c){return c.getSharedPreferences("drive",0);}
 static AuthorizationRequest request(){return AuthorizationRequest.builder().setRequestedScopes(Collections.singletonList(new Scope("https://www.googleapis.com/auth/drive.appdata"))).build();}
 static void enqueue(Context c,boolean now){
    if(!state(c).getBoolean("enabled",false))return;
    state(c).edit().putString("status","Pending").apply();
    OneTimeWorkRequest w=new OneTimeWorkRequest.Builder(Cloud.class).setInitialDelay(now?0:45,TimeUnit.SECONDS)
        .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
    WorkManager.getInstance(c).enqueueUniqueWork("drive-snapshot",ExistingWorkPolicy.REPLACE,w);
 }
 public Cloud(Context c,WorkerParameters p){super(c,p);}
 static String http(String url,String method,String token,String type,String data)throws Exception{
    HttpURLConnection h=(HttpURLConnection)new URL(url).openConnection();
    try{h.setConnectTimeout(15000);h.setReadTimeout(30000);h.setRequestMethod(method);h.setRequestProperty("Authorization","Bearer "+token);
        if(data!=null){h.setDoOutput(true);h.setRequestProperty("Content-Type",type);try(OutputStream out=h.getOutputStream()){out.write(data.getBytes(StandardCharsets.UTF_8));}}
        int code=h.getResponseCode();if(code<200||code>=300)throw new IOException("Drive HTTP "+code);
        return Snapshot.read(h.getInputStream());
    }finally{h.disconnect();}
 }
 static JSONArray list(String token)throws Exception {
    return new JSONObject(http(API+"?spaces=appDataFolder&q=trashed%3Dfalse%20and%20name%20contains%20%27Daylist-%27%20and%20appProperties%20has%20%7B%20key%3D%27verified%27%20and%20value%3D%27yes%27%20%7D&orderBy=createdTime%20desc&fields=files(id,name,createdTime,size)&pageSize=100","GET",token,null,null)).getJSONArray("files");
 }
 static String download(String token,String id)throws Exception{return http(API+"/"+id+"?alt=media","GET",token,null,null);}
 @Override public Result doWork(){
    Context c=getApplicationContext();if(!state(c).getBoolean("enabled",false))return Result.success();
    try{
        String snapshot=Snapshot.make(c);int count=Snapshot.validate(snapshot).getJSONArray("tasks").length();
        if(count==0){state(c).edit().putString("status","Empty collection · earlier backups retained").apply();return Result.success();}
        AuthorizationResult auth=Tasks.await(Identity.getAuthorizationClient(c).authorize(request()),45,TimeUnit.SECONDS);
        if(auth.hasResolution()||auth.getAccessToken()==null)throw new SecurityException("Reconnect Google Drive");
        String token=auth.getAccessToken(), boundary=UUID.randomUUID().toString();
        JSONObject meta=new JSONObject().put("name","Daylist-"+System.currentTimeMillis()+"-"+count+".json").put("parents",new JSONArray().put("appDataFolder"));
        String body="--"+boundary+"\r\nContent-Type: application/json\r\n\r\n"+meta+"\r\n--"+boundary+"\r\nContent-Type: application/json\r\n\r\n"+snapshot+"\r\n--"+boundary+"--";
        String id=new JSONObject(http("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart","POST",token,"multipart/related; boundary="+boundary,body)).getString("id");
        if(!snapshot.equals(download(token,id)))throw new IOException("Read-back verification failed");
        if(isStopped())return Result.success();
        http(API+"/"+id,"PATCH",token,"application/json",new JSONObject().put("appProperties",new JSONObject().put("verified","yes")).toString());
        state(c).edit().putString("last",java.time.Instant.now().toString()).putString("status","Verified").apply();
        JSONArray history=list(token);for(int i=5;i<history.length();i++)http(API+"/"+history.getJSONObject(i).getString("id"),"DELETE",token,null,null);
        return Result.success();
    }catch(Exception e){state(c).edit().putString("status",e instanceof SecurityException?"Reconnect Google Drive":"Backup failed · retry pending").apply();return e instanceof SecurityException?Result.failure():Result.retry();}
 }
}
