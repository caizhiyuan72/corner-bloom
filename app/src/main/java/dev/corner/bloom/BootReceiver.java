package dev.corner.bloom;

import android.content.*;
import android.provider.Settings;
import android.util.Log;

/** Receives the post-unlock boot broadcast; does not open any activity on boot. */
public final class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        String action=intent==null?null:intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(action))return;
        Prefs prefs=new Prefs(context);
        if(!prefs.get("start_on_boot",false))return;
        boolean allowed=Settings.canDrawOverlays(context);
        if(!StartupPolicy.shouldStart(action,true,allowed)){prefs.set("boot_failed",true);return;}
        try{context.startForegroundService(new Intent(context,OverlayService.class));}
        catch(RuntimeException e){
            prefs.set("boot_failed",true);
            Log.w("CornerBloom","Unable to restore corner launcher at boot",e);
        }
    }
}
