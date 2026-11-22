package dev.daylist;
import android.app.*;
import android.content.*;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

class Transfers {
 final Main a;final ExecutorService io=Executors.newSingleThreadExecutor(); String dialogTitle="", dialogBody="";Runnable confirm;
 void dismiss(){dialogTitle="";dialogBody="";confirm=null;a.refresh();}
 void dialog(String title,String body,Runnable positive){
    a.runOnUiThread(()->{if(a.isFinishing()||a.isDestroyed())return;dialogTitle=title;dialogBody=body;confirm=positive;a.refresh();});
 }
 Transfers(Main a){this.a=a;}
 void message(String s){dialog("Local files",s,null);}
 void file(boolean save){Intent i=new Intent(save?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).setType(save?"application/json":"*/*").addCategory(Intent.CATEGORY_OPENABLE);
 if(save)i.putExtra(Intent.EXTRA_TITLE,"Daylist-"+java.time.LocalDate.now()+".json");a.startActivityForResult(i,save?81:82);}
 void preview(String raw)throws Exception{JSONObject d=Snapshot.validate(raw);
 String summary="Replace local tasks with "+d.getJSONArray("tasks").length()+" tasks from "+new JSONObject(raw).getString("created")+"? This also restores reminder settings.";
 dialog("Import tasks?",summary,()->{io.execute(()->{try{Snapshot.restore(a,d);a.runOnUiThread(a::refresh);}catch(Exception e){message(e.toString());}});});}
 void result(int code,int result,Intent data){if(result!=Activity.RESULT_OK||data==null)return;
 if(code!=81&&code!=82)return;io.execute(()->{try{
 if(code==81){String s=Snapshot.make(a);try(OutputStream out=a.getContentResolver().openOutputStream(data.getData(),"wt")){out.write(s.getBytes(StandardCharsets.UTF_8));}message("Backup exported.");}
 else preview(Snapshot.read(a.getContentResolver().openInputStream(data.getData())));
 }catch(Exception e){message("Backup operation failed: "+e.getMessage());}});}
}
