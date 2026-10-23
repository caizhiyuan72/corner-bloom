package dev.corner.bloom;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

final class Prefs {
    private final SharedPreferences sp;
    Prefs(Context c){sp=c.getSharedPreferences("bloom",Context.MODE_PRIVATE);}
    int get(String key,int fallback){return sp.getInt(key,fallback);}
    boolean get(String key,boolean fallback){return sp.getBoolean(key,fallback);}
    void set(String key,int value){sp.edit().putInt(key,value).apply();}
    void set(String key,boolean value){sp.edit().putBoolean(key,value).apply();}
    void recoverTapMode(){
        if(get("tap_recovery_v1",false))return;
        // Once on upgrade, return to the user's working tap/swipe path.
        // Do not carry the broken hold experiment into the recovery release.
        sp.edit().putBoolean("hold_through",false).putBoolean("tap_recovery_v1",true).apply();
    }
    void migrateInputV3(){
        if(get("input_v3",false))return;
        // Replace the previous wide default; retain explicitly customized widths.
        if(get("width",48)==48)set("width",16);
        set("input_v3",true);
    }
    List<String> selected(){
        List<String> r=new ArrayList<>();
        String value=sp.getString("apps","");
        for(String s:value.split("\\|"))if(!s.isEmpty())r.add(s);
        return r;
    }
    boolean hasSelection(){return sp.contains("apps");}
    void selected(List<String> ids){sp.edit().putString("apps",String.join("|",ids)).apply();}
}
