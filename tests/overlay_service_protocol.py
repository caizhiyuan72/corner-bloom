#!/usr/bin/env python3
"""Run the production service against deterministic window/lifecycle stand-ins.

This checks our lifecycle protocol, not Android rendering, vendor policy or IME behavior.
Requires a JDK (or BLOOM_ECJ_JAR).
"""
import os
from pathlib import Path
import shutil
import subprocess

project = Path(__file__).resolve().parents[1]
work = project / "app/build/overlay-protocol"
shutil.rmtree(work, ignore_errors=True)
sources = {
"android/content/Context.java": """
package android.content;
import android.app.*;import android.os.*;import android.view.*;import android.content.res.*;
public class Context {
 public static final int MODE_PRIVATE=0,RECEIVER_NOT_EXPORTED=4;
 public static final String WINDOW_SERVICE="window",POWER_SERVICE="power",KEYGUARD_SERVICE="keyguard",NOTIFICATION_SERVICE="notification";
 public final WindowManager windows=new WindowManager();public final PowerManager power=new PowerManager();
 public final KeyguardManager keyguard=new KeyguardManager();public final NotificationManager notifications=new NotificationManager();
 public final SharedPreferences preferences=new SharedPreferences();
 public Object getSystemService(String s){if(s.equals(WINDOW_SERVICE))return windows;if(s.equals(POWER_SERVICE))return power;if(s.equals(KEYGUARD_SERVICE))return keyguard;return notifications;}
 public SharedPreferences getSharedPreferences(String s,int m){return preferences;}
 public Resources getResources(){return new Resources();}
 public void registerReceiver(BroadcastReceiver r,IntentFilter f){}public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){}
 public void unregisterReceiver(BroadcastReceiver r){}public void startActivity(Intent i){}
}
""",
"android/content/SharedPreferences.java": """
package android.content;public class SharedPreferences {
 public interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String key);}
 public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
 public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){}
}
""",
"android/content/Intent.java": """
package android.content;public class Intent {
 public static final String ACTION_SCREEN_OFF="off",ACTION_SCREEN_ON="on",ACTION_USER_PRESENT="present",ACTION_MAIN="main",CATEGORY_LAUNCHER="launcher";
 public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_RESET_TASK_IF_NEEDED=2;private String action;
 public Intent(){}public Intent(String a){action=a;}public Intent(Context c,Class<?> t){}
 public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}
 public Intent addCategory(String s){return this;}public Intent setComponent(Object o){return this;}public Intent addFlags(int flags){return this;}
}
""",
"android/content/IntentFilter.java": "package android.content;public class IntentFilter {public void addAction(String s){}}",
"android/content/BroadcastReceiver.java": "package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}",
"android/content/res/Configuration.java": "package android.content.res;public class Configuration {}",
"android/content/res/Resources.java": "package android.content.res;public class Resources {public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}",
"android/util/DisplayMetrics.java": "package android.util;public class DisplayMetrics {public float density=1;public int widthPixels=1080,heightPixels=2400;}",
"android/util/Log.java": "package android.util;public class Log {public static int w(String t,String s,Throwable e){return 0;}}",
"android/app/Service.java": """
package android.app;import android.content.*;import android.content.res.*;import android.os.*;
public class Service extends Context {
 public static final int START_NOT_STICKY=2,START_STICKY=1,STOP_FOREGROUND_REMOVE=1;public boolean stopped;
 public void onCreate(){}public int onStartCommand(Intent i,int f,int s){return 0;}public void onConfigurationChanged(Configuration c){}
 public void onDestroy(){}public IBinder onBind(Intent i){return null;}public void stopSelf(){stopped=true;}
 public void startForeground(int id,Notification n){}public void stopForeground(int flags){}
}
""",
"android/app/KeyguardManager.java": "package android.app;public class KeyguardManager {public boolean locked;public boolean isKeyguardLocked(){return locked;}}",
"android/app/NotificationManager.java": "package android.app;public class NotificationManager {public static final int IMPORTANCE_LOW=2;public void createNotificationChannel(NotificationChannel c){}public void notify(int id,Notification n){}}",
"android/app/NotificationChannel.java": "package android.app;public class NotificationChannel {public NotificationChannel(String a,String b,int c){}public void setDescription(String s){}public void setShowBadge(boolean b){}}",
"android/app/PendingIntent.java": "package android.app;import android.content.*;public class PendingIntent {public static final int FLAG_IMMUTABLE=1,FLAG_UPDATE_CURRENT=2;public static PendingIntent getActivity(Context c,int id,Intent i,int f){return new PendingIntent();}public static PendingIntent getService(Context c,int id,Intent i,int f){return new PendingIntent();}}",
"android/app/Notification.java": """
package android.app;import android.content.*;public class Notification {
 public static class Builder {public Builder(Context c,String s){}public Builder setSmallIcon(int i){return this;}
 public Builder setContentTitle(String s){return this;}public Builder setContentText(String s){return this;}
 public Builder setOnlyAlertOnce(boolean b){return this;}public Builder setContentIntent(PendingIntent p){return this;}
 public Builder setOngoing(boolean b){return this;}public Builder addAction(Action a){return this;}public Notification build(){return new Notification();}}
 public static class Action {public static class Builder {public Builder(Object o,String s,PendingIntent p){}public Action build(){return new Action();}}}
}
""",
"android/os/IBinder.java": "package android.os;public interface IBinder {}",
"android/os/Build.java": "package android.os;public class Build {public static class VERSION {public static int SDK_INT=35;}}",
"android/os/PowerManager.java": "package android.os;public class PowerManager {public boolean interactive=true;public boolean isInteractive(){return interactive;}}",
"android/os/Looper.java": "package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}",
"android/os/SystemClock.java": "package android.os;public class SystemClock {public static long uptimeMillis(){return Handler.now;}}",
"android/os/Handler.java": """
package android.os;import java.util.*;public class Handler {
 public static long now;private static long seq;
 static class Task {Handler owner;Runnable run;long at,id;Task(Handler h,Runnable r,long t){owner=h;run=r;at=t;id=seq++;}}
 private static final List<Task> tasks=new ArrayList<>();public Handler(Looper l){}
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long d){synchronized(tasks){tasks.add(new Task(this,r,now+d));}return true;}
 public void removeCallbacks(Runnable r){synchronized(tasks){tasks.removeIf(t->t.owner==this&&t.run==r);}}
 public void removeCallbacksAndMessages(Object o){synchronized(tasks){tasks.removeIf(t->t.owner==this);}}
 public static void advance(long ms){long end=now+ms;for(int n=0;n<10000;n++){
  Task next=null;synchronized(tasks){for(Task t:tasks)if(t.at<=end&&(next==null||t.at<next.at||t.at==next.at&&t.id<next.id))next=t;if(next!=null)tasks.remove(next);}
  if(next==null){now=end;return;}now=next.at;next.run.run();}throw new AssertionError("runaway handler");}
 public static int pending(){synchronized(tasks){return tasks.size();}}
}
""",
"android/provider/Settings.java": "package android.provider;import android.content.*;public class Settings {public static boolean allowed=true;public static boolean canDrawOverlays(Context c){return allowed;}}",
"android/graphics/RectF.java": "package android.graphics;public class RectF {public float left,top,right,bottom;public RectF(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}public float width(){return right-left;}public float height(){return bottom-top;}}",
"android/graphics/Rect.java": "package android.graphics;public class Rect {public int bottom=2400;public Rect(){}public Rect(int a,int b,int c,int d){}}",
"android/graphics/PixelFormat.java": "package android.graphics;public class PixelFormat {public static final int TRANSLUCENT=1;}",
"android/view/View.java": """
package android.view;import android.content.*;import java.util.*;public class View {
 public static final int VISIBLE=0,GONE=8;public boolean attached;private int visibility;private float alpha=1;public OnTouchListener listener;
 public View(Context c){}public interface OnTouchListener {boolean onTouch(View v,MotionEvent e);}
 public void setOnTouchListener(OnTouchListener l){listener=l;}public boolean isAttachedToWindow(){return attached;}
 public int getVisibility(){return visibility;}public void setVisibility(int v){visibility=v;}
 public float getAlpha(){return alpha;}public void setAlpha(float a){alpha=a;}
 public void invalidate(){}public void setSystemGestureExclusionRects(List<android.graphics.Rect> r){}
}
""",
"android/view/MotionEvent.java": """
package android.view;public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 private int action;private long time;public MotionEvent(int a,long t){action=a;time=t;}
 public int getActionMasked(){return action;}public float getRawX(){return 1075;}public float getRawY(){return 2320;}
 public int getHistorySize(){return 0;}public float getHistoricalX(int h){return 0;}public float getHistoricalY(int h){return 0;}
 public float getX(){return getRawX();}public float getY(){return getRawY();}public long getEventTime(){return time;}
}
""",
"android/view/ViewConfiguration.java": "package android.view;import android.content.*;public class ViewConfiguration {public static ViewConfiguration get(Context c){return new ViewConfiguration();}public int getScaledTouchSlop(){return 8;}public static int getLongPressTimeout(){return 500;}}",
"android/view/Gravity.java": "package android.view;public class Gravity {public static final int TOP=1,LEFT=2;}",
"android/view/Display.java": "package android.view;public class Display {public void getRealMetrics(android.util.DisplayMetrics d){}}",
"android/view/WindowInsets.java": "package android.view;public class WindowInsets {public static class Type {public static int ime(){return 1;}}public boolean isVisible(int t){return false;}public Insets getInsets(int t){return new Insets();}public static class Insets {public int bottom;}}",
"android/view/WindowMetrics.java": "package android.view;public class WindowMetrics {public WindowInsets getWindowInsets(){return new WindowInsets();}public android.graphics.Rect getBounds(){return new android.graphics.Rect();}}",
"android/view/WindowManager.java": """
package android.view;import java.util.*;public class WindowManager {
 public final List<View> attached=new ArrayList<>();public int adds,removes,updates,failAdds,failUpdates,failOnAdd;
 public Display getDefaultDisplay(){return new Display();}public WindowMetrics getCurrentWindowMetrics(){return new WindowMetrics();}
 public void addView(View v,LayoutParams p){adds++;if(failAdds-->0||adds==failOnAdd)throw new IllegalStateException("add failed");if(v.attached)throw new IllegalStateException("duplicate");attached.add(v);v.attached=true;}
 public void removeViewImmediate(View v){removes++;attached.remove(v);v.attached=false;}
 public void updateViewLayout(View v,LayoutParams p){updates++;if(failUpdates-->0||!v.attached)throw new IllegalArgumentException("lost root");}
 public static class LayoutParams {
  public static final int TYPE_APPLICATION_OVERLAY=2038,FLAG_NOT_FOCUSABLE=1,FLAG_NOT_TOUCHABLE=2,FLAG_ALT_FOCUSABLE_IM=4,FLAG_NOT_TOUCH_MODAL=8,FLAG_LAYOUT_IN_SCREEN=16,SOFT_INPUT_ADJUST_NOTHING=32,LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES=1;
  public int flags,x,y,width,height,gravity,windowAnimations,softInputMode,layoutInDisplayCutoutMode;public float alpha=1;
  public LayoutParams(int w,int h,int t,int f,int p){width=w;height=h;flags=f;}
  public void setTitle(String s){}public void setFitInsetsTypes(int t){}
 }
}
""",
"android/widget/Toast.java": "package android.widget;import android.content.*;public class Toast {public static final int LENGTH_SHORT=0,LENGTH_LONG=1;public static Toast makeText(Context c,String s,int d){return new Toast();}public void show(){}}",
"dev/corner/bloom/R.java": "package dev.corner.bloom;public class R {public static class drawable {public static final int ic_bloom=1;}}",
"dev/corner/bloom/MainActivity.java": "package dev.corner.bloom;public class MainActivity {}",
"dev/corner/bloom/Prefs.java": """
package dev.corner.bloom;import android.content.*;import java.util.*;final class Prefs {
 public static final Map<String,Object> data=new HashMap<>();Prefs(Context c){}
 boolean get(String k,boolean f){return (boolean)data.getOrDefault(k,f);}int get(String k,int f){return (int)data.getOrDefault(k,f);}
 void set(String k,boolean v){data.put(k,v);}void migrateInputV3(){}void recoverTapMode(){}
}
""",
"dev/corner/bloom/AppEntry.java": "package dev.corner.bloom;import android.content.*;import java.util.*;final class AppEntry {Object component;static List<AppEntry> load(Context c){return new ArrayList<>();}static List<AppEntry> favorites(List<AppEntry> a,Prefs p){return a;}}",
"dev/corner/bloom/BloomView.java": """
package dev.corner.bloom;import android.content.*;import android.view.*;import android.graphics.*;import java.util.*;
final class BloomView extends View {
 interface Host {void expand();void collapse();void launch(AppEntry e);}boolean busy;int resets;
 BloomView(Context c,Prefs p,boolean o,Host h){super(c);}void reset(){busy=false;resets++;}
 void setApps(List<AppEntry> a){}void overlayBounds(int w,int h,RectF r){}
 boolean interactionActive(){return busy;}boolean gestureActive(){return false;}void cancelGesture(){busy=false;}
 boolean onTouchEvent(MotionEvent e){busy=e.getActionMasked()!=MotionEvent.ACTION_UP&&e.getActionMasked()!=MotionEvent.ACTION_CANCEL;return true;}
}
""",
"dev/corner/bloom/CornerHandleView.java": "package dev.corner.bloom;import android.content.*;import android.view.*;final class CornerHandleView extends View {CornerHandleView(Context c,Prefs p){super(c);}}",
"dev/corner/bloom/TapAccessibilityService.java": """
package dev.corner.bloom;import android.os.*;final class TapAccessibilityService {
 static boolean available(){return true;}static void setStateListener(Runnable r){}static DeleteHold.Source captureDelete(){return null;}
 static void deleteStatus(String s){}static boolean tap(float x,float y,Runnable done){new Handler(Looper.getMainLooper()).postDelayed(done,24);return true;}
}
""",
"dev/corner/bloom/OverlayServiceProtocolTest.java": """
package dev.corner.bloom;
import android.app.*;import android.content.*;import android.os.*;import android.provider.*;import android.view.*;import java.lang.reflect.*;
public final class OverlayServiceProtocolTest {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static Object field(Object o,String n)throws Exception{Field f=OverlayService.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void checkNow(OverlayService s)throws Exception{Method m=OverlayService.class.getDeclaredMethod("checkWindows");m.setAccessible(true);m.invoke(s);}
 static void screen(OverlayService s,String action)throws Exception{((BroadcastReceiver)field(s,"screen")).onReceive(s,new Intent(action));}
 static OverlayService start()throws Exception{OverlayService s=new OverlayService();s.onCreate();check(s.onStartCommand(new Intent(),0,1)==Service.START_STICKY,"request normal sticky recovery");return s;}
 static void touch(OverlayService s,int a,long t)throws Exception{View h=(View)field(s,"handle");h.listener.onTouch(h,new MotionEvent(a,t));}
 public static void main(String[] args)throws Exception{
  Prefs.data.clear();OverlayService s=start();
  check(s.windows.attached.size()==2&&OverlayService.running,"initial pair attached");
  for(int i=0;i<20;i++)checkNow(s);
  check(s.windows.adds==2,"healthy checks never reorder windows");
  s.power.interactive=false;screen(s,Intent.ACTION_SCREEN_OFF);
  check(((View)field(s,"handle")).getVisibility()==View.GONE,"screen off hides handle");
  s.power.interactive=true;s.keyguard.locked=true;screen(s,Intent.ACTION_SCREEN_ON);screen(s,Intent.ACTION_USER_PRESENT);
  check(((View)field(s,"handle")).getVisibility()==View.GONE,"early USER_PRESENT cannot show lockscreen overlay");
  s.keyguard.locked=false;Handler.advance(2500);
  check(((View)field(s,"handle")).getVisibility()==View.VISIBLE,"unlock recovers without another broadcast");
  check(s.windows.adds==2,"visibility recovery does not replace healthy roots");

  View handle=(View)field(s,"handle");handle.attached=false;s.windows.attached.remove(handle);checkNow(s);
  check(s.windows.attached.size()==2&&handle.attached,"repair a lost handle even when old added flag is true");
  int before=s.windows.adds;
  touch(s,MotionEvent.ACTION_DOWN,10);int resets=((BloomView)field(s,"view")).resets;
  s.onStartCommand(new Intent().setAction(OverlayService.RESTORE),0,2);checkNow(s);
  check(s.windows.adds==before&&((BloomView)field(s,"view")).resets==resets,"manual restore defers until physical release");
  touch(s,MotionEvent.ACTION_UP,100);Handler.advance(2500);
  check(s.windows.adds==before+2&&s.windows.attached.size()==2,"deferred restore replaces exactly one pair");

  Prefs.data.put("tap_through",true);before=s.windows.adds;
  touch(s,MotionEvent.ACTION_DOWN,200);touch(s,MotionEvent.ACTION_UP,250);
  check((boolean)field(s,"replaying"),"tap replay has begun");
  s.onStartCommand(new Intent().setAction(OverlayService.RESTORE),0,3);
  check(s.windows.adds==before,"repair never reattaches during injected tap");
  Handler.advance(2500);handle=(View)field(s,"handle");
  WindowManager.LayoutParams hp=(WindowManager.LayoutParams)field(s,"handleParams");
  check(!(boolean)field(s,"replaying")&&hp.alpha==1&&handle.getAlpha()==1,"tap restores opacity and touchability");
  check((hp.flags&WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)==0&&s.windows.adds==before+2,"deferred repair preserves tap restoration");

  s.windows.failUpdates=1;
  s.onConfigurationChanged(new android.content.res.Configuration());Handler.advance(500);
  check(!s.stopped&&OverlayService.running&&OverlayService.statusHint.contains("已挂载"),"one failed layout update does not stop the service");
  s.onDestroy();Handler.advance(1000);check(s.windows.attached.isEmpty(),"destroy removes both windows");
  check(Handler.pending()==0,"destroy cancels health and tap callbacks");
  // Simulate sticky process recreation: only persisted enabled state survives.
  OverlayService restarted=new OverlayService();restarted.onCreate();
  check(restarted.onStartCommand(null,0,4)==Service.START_STICKY&&restarted.windows.attached.size()==2,"null intent restores after process loss");
  check(restarted.onStartCommand(new Intent().setAction(OverlayService.STOP),0,5)==Service.START_NOT_STICKY,"explicit stop disables sticky recovery");
  check(!(boolean)Prefs.data.get("overlay_enabled")&&!OverlayService.running,"stop clears requested state immediately");
  restarted.onDestroy();
  OverlayService stopped=new OverlayService();stopped.onCreate();
  check(stopped.onStartCommand(null,0,6)==Service.START_NOT_STICKY&&stopped.windows.adds==0,"late null intent cannot undo explicit stop");stopped.onDestroy();
  OverlayService stale=new OverlayService();stale.onCreate();
  check(stale.onStartCommand(new Intent().setAction(OverlayService.CHECK),0,6)==Service.START_NOT_STICKY&&stale.windows.adds==0,"queued settings check cannot undo explicit stop");stale.onDestroy();

  Prefs.data.clear();OverlayService partial=new OverlayService();partial.windows.failOnAdd=2;partial.onCreate();partial.onStartCommand(new Intent(),0,7);
  check(partial.windows.attached.isEmpty()&&!OverlayService.statusHint.contains("已挂载"),"half-attached pair is cleaned and reported as retry");
  Handler.advance(1100);check(partial.windows.attached.size()==2,"partial attachment recovers without duplicate roots");partial.onDestroy();

  OverlayService failed=new OverlayService();failed.windows.failAdds=100;failed.onCreate();failed.onStartCommand(new Intent(),0,8);Handler.advance(20000);
  check(failed.windows.adds==3&&OverlayService.statusHint.contains("恢复失败"),"bounded failures are reported, not shown as enabled");
  Handler.advance(60000);check(failed.windows.adds==3,"no repeated window tug of war after failure");
  failed.windows.failAdds=0;failed.onStartCommand(new Intent().setAction(OverlayService.RESTORE),0,9);
  check(failed.windows.attached.size()==2,"manual action can retry after bounded failure");
  Settings.allowed=false;checkNow(failed);check(failed.stopped&&!OverlayService.running&&failed.windows.attached.isEmpty(),"permission revocation stops service and removes windows immediately");failed.onDestroy();Settings.allowed=true;
  System.out.println("Production OverlayService: delayed unlock, lost root, deferred repair, tap replay, failed relayout, sticky restart, explicit stop, partial attachment, retry limits and revoked permission passed (host fixtures).");
 }
}
""",
}
paths = []
for name, source in sources.items():
    path = work / "src" / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source)
    paths.append(str(path))
for name in ["OverlayService.java", "OverlayRecovery.java", "DeleteHold.java", "TapClassifier.java"]:
    paths.append(str(project / "app/src/main/java/dev/corner/bloom" / name))
compiler = (["java", "-jar", os.environ["BLOOM_ECJ_JAR"], "-17"] if os.environ.get("BLOOM_ECJ_JAR")
            else ["java", "com.sun.tools.javac.Main"])
subprocess.run([*compiler, "-d", str(work / "classes"), *paths], check=True)
subprocess.run(["java", "-cp", str(work / "classes"), "dev.corner.bloom.OverlayServiceProtocolTest"], check=True)
