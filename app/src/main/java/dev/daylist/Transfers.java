package dev.daylist;
import android.app.*;
import android.content.*;
import com.google.android.gms.auth.api.identity.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

class Transfers {
 final Main a;final ExecutorService io=Executors.newSingleThreadExecutor();boolean listing; String dialogTitle="", dialogBody="";String[] choices;Runnable confirm;java.util.function.IntConsumer choose;
 void dismiss(){dialogTitle="";dialogBody="";choices=null;confirm=null;choose=null;a.refresh();}
 void dialog(String title,String body,String[] items,Runnable positive,java.util.function.IntConsumer selected){
    a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;dialogTitle=title;dialogBody=body;choices=items;confirm=positive;choose=selected;a.refresh();});
 }
 Transfers(Main a){this.a=a;}
 void message(String s){dialog("Backup and restore",s,null,null,null);}
 void file(boolean save){Intent i=new Intent(save?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType(save?"application/json":"*/*").addCategory(Intent.CATEGORY_OPENABLE);
 if(save)i.putExtra(Intent.EXTRA_TITLE,"Daylist-"+java.time.LocalDate.now()+".json");a.startActivityForResult(i,save?81:82);}
 void connect(boolean restore){listing=restore;
 Identity.getAuthorizationClient(a).authorize(Cloud.request()).addOnSuccessListener(r->{try{if(r.hasResolution())a.startIntentSenderForResult(r.getPendingIntent().getIntentSender(),83,null,0,0,0);else authorized(r);}catch(Exception e){message(e.toString());}}).addOnFailureListener(e->message("Drive authorization failed: "+e.getMessage()));}
 void authorized(AuthorizationResult r){
 if(r.getAccessToken()==null){message("Google authorization required");return;}
 if(!listing){final String accountToken=r.getAccessToken();
 Cloud.state(a).edit().remove("email").apply();
 io.execute(()->{try{String result=Cloud.http("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)","GET",accountToken,null,null);
 Cloud.state(a).edit().putString("email",new JSONObject(result).getJSONObject("user").getString("emailAddress")).apply();}catch(Exception ignored){}});
 Cloud.state(a).edit().putBoolean("enabled",true).apply();Cloud.enqueue(a,true);a.refresh();return;}
 final String token=r.getAccessToken();io.execute(()->{try{
 JSONArray files=Cloud.list(token);String[] labels=new String[files.length()];for(int i=0;i<labels.length;i++){JSONObject f=files.getJSONObject(i);String file=f.getString("name");String count=file.substring(file.lastIndexOf('-')+1).replace(".json","");
 String when=java.time.Instant.parse(f.getString("createdTime")).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.MEDIUM,java.time.format.FormatStyle.SHORT));
 labels[i]=when+" · "+count+" tasks";}
 if(labels.length==0){message("No Daylist backups found for this account.");return;}
 dialog("Restore your tasks","",labels,null,k->io.execute(()->{try{preview(Cloud.download(token,files.getJSONObject(k).getString("id")));}catch(Exception e){message(e.toString());}}));
 }catch(Exception e){message(e.toString());}});}
 void preview(String raw)throws Exception{JSONObject d=Snapshot.validate(raw);
 String summary="Replace local tasks with "+d.getJSONArray("tasks").length()+" tasks from "+new JSONObject(raw).getString("created")+"? This also restores reminder settings.";
 dialog("Restore backup?",summary,null,()->{try{Snapshot.restore(a,d);a.refresh();}catch(Exception e){message(e.toString()); }},null);}
 void result(int code,int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return;
 if(code==83){try{authorized(Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data));}catch(Exception e){message(e.toString());}return;}
 if(code!=81&&code!=82)return;io.execute(()->{try{
 if(code==81){String s=Snapshot.make(a);try(OutputStream out=a.getContentResolver().openOutputStream(data.getData(),"wt")){out.write(s.getBytes(StandardCharsets.UTF_8));}message("Backup exported.");}
 else preview(Snapshot.read(a.getContentResolver().openInputStream(data.getData())));
 }catch(Exception e){message("Backup operation failed: "+e.getMessage());}});}
}
