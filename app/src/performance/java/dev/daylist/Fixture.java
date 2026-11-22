package dev.daylist;
import android.app.Activity;
import android.os.Bundle;
import org.json.*;
import java.time.LocalDate;
public class Fixture extends Activity{
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        try{
            JSONArray tasks=new JSONArray();
            for(int n=0;n<500;n++)tasks.put(new JSONObject().put("id","fixture-"+n).put("title","Review task "+n)
                .put("date",LocalDate.now().toString()).put("time","").put("repeat","").put("interval",1).put("mask",0)
                .put("done",false).put("description","").put("subs",new JSONArray()));
            if(!getSharedPreferences("tasks",0).edit().putString("list",tasks.toString()).commit())throw new IllegalStateException("Fixture storage failed");
        }catch(JSONException e){throw new IllegalStateException(e);}
        finish();
    }
}
