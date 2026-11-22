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
    private static class Markdown {
    static final java.util.List<org.commonmark.Extension> extensions=java.util.Arrays.asList(org.commonmark.ext.gfm.tables.TablesExtension.create(),org.commonmark.ext.gfm.strikethrough.StrikethroughExtension.create(),org.commonmark.ext.task.list.items.TaskListItemsExtension.create());
    static final org.commonmark.parser.Parser parser=org.commonmark.parser.Parser.builder().extensions(extensions).build();
    static final org.commonmark.renderer.html.HtmlRenderer html=org.commonmark.renderer.html.HtmlRenderer.builder().extensions(extensions).escapeHtml(true).sanitizeUrls(true).build();
    }
    String widgetRoute="";
    String selected="";
    @Override public void onCreate(Bundle state) {
        androidx.core.splashscreen.SplashScreen.installSplashScreen(this);
        super.onCreate(state);transfer=new Transfers(this);

        selected=state==null?getIntent().getStringExtra("task"):null;
        widgetRoute=state==null?getIntent().getStringExtra("route"):null;
        DaylistUiKt.mount(this);
    }
    void prepareParser(){
        if(engine!=null)return;
        engine=new WebView(this);
        engine.getSettings().setJavaScriptEnabled(true);
        engine.getSettings().setBlockNetworkLoads(true);
        engine.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView v,String url){ready=true;refresh();}
        });
        engine.loadUrl("file:///android_asset/parser.html");
    }
    @Override protected void onDestroy(){redraw=null;if(engine!=null)engine.destroy();super.onDestroy();}
    @Override public void onNewIntent(Intent i){super.onNewIntent(i);selected=i.getStringExtra("task");widgetRoute=i.getStringExtra("route");refresh();}
    void refresh(){if(redraw!=null)redraw.run();}
    @Override public void onResume(){super.onResume();TaskStore.maintain(getApplicationContext());refresh();}
    @Override public void onRequestPermissionsResult(int code,String[] p,int[] g){
        super.onRequestPermissionsResult(code,p,g);TaskStore.io.execute(()->Reminders.reconcile(this));refresh();
    }
    public class Bridge {
        public String draftText(){return getPreferences(0).getString("draft","");}
        public void saveDraft(String s){getPreferences(0).edit().putString("draft",s).apply();}
        public String markdown(String s){return Markdown.html.render(Markdown.parser.parse(s));}
        public boolean pref(String key){return Reminders.prefs(Main.this).getBoolean(key,key.equals("notifyAtDue"));}
        public void setting(String key,boolean v){if(!key.equals("enter")&&!key.equals("notifyAtDue"))return;Reminders.prefs(Main.this).edit().putBoolean(key,v).apply();TaskStore.io.execute(()->Reminders.reconcile(Main.this));}
        public void backup(String op){runOnUiThread(()->transfer.file(op.equals("export")));}
        public String selection(){String id=selected;selected="";return id==null?"":id;}
        public String list(){return Reminders.prefs(Main.this).getString("list","[]");}
        public boolean exact(){return Reminders.exact(Main.this);}
        public void testReminder(){
            Context c=getApplicationContext();
            TaskStore.io.execute(()->{synchronized(Reminders.class){
                Reminders.prefs(c).edit().putLong("testAt",System.currentTimeMillis()+60000L).apply();
                Reminders.reconcile(c);
            }});
        }
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
            return TaskStore.write(getApplicationContext(),raw,remove);
        }
    }
}
