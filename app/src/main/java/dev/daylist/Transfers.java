package dev.daylist;
import android.app.*;
import android.content.*;
import com.google.android.gms.auth.api.identity.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

class Transfers {
 final Main a;final ExecutorService io=Executors.newSingleThreadExecutor();boolean listing;
 Transfers(Main a){this.a=a;}
 void message(String s){a.runOnUiThread(()->{if(!a.isFinishing())new AlertDialog.Builder(a).setMessage(s).setPositiveButton("OK",null).show();a.refresh();});}
 void file(boolean save){Intent i=new Intent(save?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType(save?"application/json":"*/*").addCategory(Intent.CATEGORY_OPENABLE);
 if(save)i.putExtra(Intent.EXTRA_TITLE,"Daylist-"+java.time.LocalDate.now()+".json");a.startActivityForResult(i,save?81:82);}
 void connect(boolean restore){listing=restore;
 Identity.getAuthorizationClient(a).authorize(Cloud.request()).addOnSuccessListener(r->{try{if(r.hasResolution())a.startIntentSenderForResult(r.getPendingIntent().getIntentSender(),83,null,0,0,0);else authorized(r);}catch(Exception e){message(e.toString());}}).addOnFailureListener(e->message("Drive authorization failed: "+e.getMessage()));}
 void authorized(AuthorizationResult r){
 if(r.getAccessToken()==null){message("Google authorization required");return;}
 if(!listing){Cloud.state(a).edit().putBoolean("enabled",true).apply();Cloud.enqueue(a,true);a.refresh();return;}
 final String token=r.getAccessToken();io.execute(()->{try{
 JSONArray files=Cloud.list(token);String[] labels=new String[files.length()];for(int i=0;i<labels.length;i++){JSONObject f=files.getJSONObject(i);labels[i]=f.getString("createdTime")+" · "+f.getString("name")+" · "+f.optString("size")+" bytes";}
 if(labels.length==0){message("No Daylist backups found for this account.");return;}
 a.runOnUiThread(()->new AlertDialog.Builder(a).setTitle("Restore your tasks").setItems(labels,(dialog,k)->io.execute(()->{try{preview(Cloud.download(token,files.getJSONObject(k).getString("id")));}catch(Exception e){message(e.toString());}})).show());
 }catch(Exception e){message(e.toString());}});}
 void preview(String raw)throws Exception{JSONObject d=Snapshot.validate(raw);
 String summary="Replace local tasks with "+d.getJSONArray("tasks").length()+" tasks from "+new JSONObject(raw).getString("created")+"? This also restores reminder settings.";
 a.runOnUiThread(()->new AlertDialog.Builder(a).setTitle("Restore backup?").setMessage(summary).setNegativeButton("Cancel",null).setPositiveButton("Replace",(dialog,w)->{try{Snapshot.restore(a,d);a.web.reload();}catch(Exception e){message(e.toString());}}).show());}
 void result(int code,int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return;
 if(code==83){try{authorized(Identity.getAuthorizationClient(a).getAuthorizationResultFromIntent(data));}catch(Exception e){message(e.toString());}return;}
 if(code!=81&&code!=82)return;io.execute(()->{try{
 if(code==81){String s=Snapshot.make(a);try(OutputStream out=a.getContentResolver().openOutputStream(data.getData(),"wt")){out.write(s.getBytes(StandardCharsets.UTF_8));}message("Backup exported.");}
 else preview(Snapshot.read(a.getContentResolver().openInputStream(data.getData())));
 }catch(Exception e){message("Backup operation failed: "+e.getMessage());}});}
}
