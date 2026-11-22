package dev.daylist;

import android.app.*;
import android.os.*;
import android.content.*;
import android.net.Uri;
import android.provider.Settings;
import android.webkit.*;
import org.json.*;

public class Main extends androidx.activity.ComponentActivity {
    Runnable redraw; boolean ready; WebView engine; Transfers transfer;
    @Override public void onActivityResult(int code,int result,Intent data){super.onActivityResult(code,result,data);transfer.result(code,result,data);}
    static final java.util.List<org.commonmark.Extension> extensions=java.util.Arrays.asList(org.commonmark.ext.gfm.tables.TablesExtension.create(),org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),org.commonmark.ext.task.list.items.TaskListItemsExtension.create());
    static final org.commonmark.parser.Parser parser=org.commonmark.parser.Parser.builder().extensions(extensions).build();
    static final org.commonmark.renderer.html.HtmlRenderer html=org.commonmark.renderer.html.HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();
    String selected="";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);transfer=new Transfers(this);
        Repair.ensure(this);
        selected=getIntent().getStringExtra("task");
        engine=new WebView(this);
        engine.getSettings().setJavaScriptEnabled(true);
        engine.getSettings().setBlockNetworkLoads(true);
        engine.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){ready=true;refresh();}
        });
        engine.loadUrl("file:///android_asset/parser.html");
        DaylistUiKt.mount(this);
    }
    @Override protected void onDestroy(){redraw=null;engine.destroy();super.onDestroy();}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);selected=i.getStringExtra("task");refresh();}
    void refresh(){if(redraw!=null)redraw.run();}
    @Override public void onResume(){super.onResume();Reminders.reconcile(this);refresh();}
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){
        super.onRequestPermissionsResult(code,p,g);Reminders.reconcile(this);refresh();
    }
    public class Bridge {
        public String draftText(){return getPreferences(0).getString("draft","");}
        public void saveDraft(String s){getPreferences(0).edit().putString("draft",s).apply();}
        public String markdown(String s){return html.render(parser.parse(s));}
        public boolean pref(String key){return Reminders.prefs(Main.this).getBoolean(key,key.equals("notifyAtDue"));}
        public void setting(String key,boolean v){if(!key.equals("enter")&&!key.equals("notifyAtDue"))return;Reminders.prefs(Main.this).edit().putBoolean(key,v).apply();Reminders.reconcile(Main.this);Cloud.enqueue(Main.this,false);}
        public String cloudStatus(){android.content.SharedPreferences p=Cloud.state(Main.this);return (p.getBoolean("enabled",false)?"Connected · ":"Disconnected · ")+p.getString("status","No verified backup")+" "+p.getString("last","");}
        public void backup(String op){runOnUiThread(()->{switch(op){
          case "export":transfer.file(true);break;case "import":transfer.file(false);break;
          case "restore":transfer.connect(true);break;case "connect":transfer.connect(false);break;
          case "now":if(Cloud.state(Main.this).getBoolean("enabled",false))Cloud.enqueue(Main.this,true);else transfer.connect(false);break;
          case "off":Cloud.state(Main.this).edit().putBoolean("enabled",false).apply();androidx.work.WorkManager.getInstance(Main.this).cancelUniqueWork("drive-snapshot");break;}refresh();});}

        public String selection(){String id=selected;selected="";return id==null?"":id;}
        public String list(){return Reminders.prefs(Main.this).getString("list","[]");}
        public boolean exact(){return Reminders.exact(Main.this);}
        public boolean alerts(){return Reminders.enabled(Main.this);}
        public void settings(boolean alarm){
            runOnUiThread(()->{
                Intent intent;
                if(alarm && Build.VERSION.SDK_INT>=31)
                    intent=new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName()));
                else if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=0 &&
                    !getPreferences(0).getBoolean("asked",false)){
                    getPreferences(0).edit().putBoolean("asked",true).apply();
                    requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},1);return;
                } else intent=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                try {startActivity(intent);} catch(ActivityNotFoundException e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
            });
        }
        public boolean write(String raw,boolean remove){
            synchronized(Reminders.class){
                try{
                    JSONObject t=new JSONObject(raw);String id=t.getString("id");
                    JSONArray list=Reminders.tasks(Main.this);
                    for(int i=0;i<list.length();i++){
                        JSONObject old=list.getJSONObject(i);
                        if(!id.equals(old.getString("id"))) continue;
                        boolean same=true;
                        for(String key:new String[]{"date","time","repeat","mask","interval","reminder"})
                            same &= old.optString(key).equals(t.optString(key));
                        if(same)t.put("alerted",old.optString("alerted"));else {t.remove("alerted");t.remove("snooze");}
                        list.remove(i);break;
                    }
                    if(!remove)list.put(t);
                    if(!Reminders.prefs(Main.this).edit().putString("list",list.toString()).commit())return false;
                    Reminders.notifications(Main.this).cancel(id,1);
                    Reminders.reconcile(Main.this);Cloud.enqueue(Main.this,false);return true;
                } catch(Exception e){return false;}
            }
        }
    }
}
