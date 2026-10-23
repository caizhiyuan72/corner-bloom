package dev.corner.bloom;

import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import java.util.*;

final class AppEntry {
    final ComponentName component;
    final String name;
    final Bitmap icon;
    AppEntry(ComponentName component,String name,Bitmap icon){this.component=component;this.name=name;this.icon=icon;}
    String id(){return component.flattenToString();}
    static List<AppEntry> load(Context c){
        PackageManager pm=c.getPackageManager();
        Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppEntry> out=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        int size=(int)(52*c.getResources().getDisplayMetrics().density);
        for(ResolveInfo r:pm.queryIntentActivities(query,0)){
            if(r.activityInfo.packageName.equals(c.getPackageName()))continue;
            ComponentName component=new ComponentName(r.activityInfo.packageName,r.activityInfo.name);
            if(!seen.add(component.flattenToString()))continue;
            try{
                Drawable d=r.loadIcon(pm);
                Bitmap b=RoundedIcons.create(d,size);
                out.add(new AppEntry(component,r.loadLabel(pm).toString(),b));
            }catch(RuntimeException ignored){}
        }
        java.text.Collator collator=java.text.Collator.getInstance(Locale.CHINA);
        out.sort((a,b)->collator.compare(a.name,b.name));
        return out;
    }
    static List<AppEntry> favorites(List<AppEntry> all,Prefs p){
        if(!p.hasSelection()){
            List<String> initial=new ArrayList<>();
            for(int i=0;i<Math.min(12,all.size());i++)initial.add(all.get(i).id());
            p.selected(initial);
        }
        Map<String,AppEntry> map=new HashMap<>();for(AppEntry a:all)map.put(a.id(),a);
        List<AppEntry> result=new ArrayList<>();
        for(String id:p.selected())if(map.containsKey(id)&&result.size()<15)result.add(map.get(id));
        return result;
    }
}
