package dev.corner.bloom;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.Toast;
import java.util.*;
import java.util.concurrent.*;

public final class OverlayService extends Service {
    static final String STOP="dev.corner.bloom.STOP";
    static volatile boolean running;
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private BloomView view;
    private Prefs prefs;
    private boolean added,handleAdded;
    private CornerHandleView handle;
    private WindowManager.LayoutParams handleParams;
    private TapClassifier tapClassifier;
    private DeleteHold deleteHold;
    private boolean replaying;
    private int replayGeneration;
    private int screenW,screenH;
    private RectF trigger;
    private List<AppEntry> all=new ArrayList<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService loader=Executors.newSingleThreadExecutor();
    private int generation;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes=(sp,key)->{
        if("hide_recents".equals(key)||"start_on_boot".equals(key)||"boot_failed".equals(key))return;
        if(view==null)return;
        if(deleteHold!=null)deleteHold.cancel();
        cancelTapReplay();
        view.reset();
        view.setApps(AppEntry.favorites(all,prefs));measure();layoutWindows();showExpanded(false);
    };
    private final BroadcastReceiver screen=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){
            if(view==null)return;
            if(deleteHold!=null)deleteHold.cancel();
            cancelTapReplay();
            view.reset();showExpanded(false);
            KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
            boolean visible=!Intent.ACTION_SCREEN_OFF.equals(i.getAction())&&!keyguard.isKeyguardLocked();
            view.setVisibility(visible?View.VISIBLE:View.GONE);
            if(handle!=null)handle.setVisibility(visible?View.VISIBLE:View.GONE);
        }
    };
    @Override public void onCreate(){
        super.onCreate();prefs=new Prefs(this);prefs.migrateInputV3();prefs.recoverTapMode();wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        tapClassifier=new TapClassifier(Math.max(dp(8),ViewConfiguration.get(this).getScaledTouchSlop()));
        deleteHold=new DeleteHold((action,delay)->main.postDelayed(action,delay),
                ViewConfiguration.getLongPressTimeout(),80,Math.max(dp(8),ViewConfiguration.get(this).getScaledTouchSlop()));
        TapAccessibilityService.setStateListener(()->main.post(()->{
            if(view==null||!added)return;
            deleteHold.cancel();
            cancelTapReplay();view.reset();showExpanded(false);layoutWindows();
        }));
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel("gesture","角落手势",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示角落入口的运行状态，随时可以停止");channel.setShowBadge(false);nm.createNotificationChannel(channel);
        PendingIntent settings=PendingIntent.getActivity(this,1,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop=PendingIntent.getService(this,2,new Intent(this,OverlayService.class).setAction(STOP),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"gesture").setSmallIcon(dev.corner.bloom.R.drawable.ic_bloom)
                .setContentTitle("角落绽放已开启").setContentText("从右下角上滑，滑到应用后松手打开")
                .setContentIntent(settings).setOngoing(true).addAction(new Notification.Action.Builder(null,"停止",stop).build()).build();
        startForeground(7,notification);
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SCREEN_ON);filter.addAction(Intent.ACTION_USER_PRESENT);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(screen,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(screen,filter);
        getSharedPreferences("bloom",MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changes);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(!Settings.canDrawOverlays(this)){stopSelf();return START_NOT_STICKY;}
        if(view==null){
            view=new BloomView(this,prefs,true,new BloomView.Host(){
                public void expand(){showExpanded(true);}
                public void collapse(){showExpanded(false);}
                public void launch(AppEntry app){
                    view.reset();showExpanded(false);
                    Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                            .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                    try{startActivity(launch);}catch(RuntimeException e){Toast.makeText(OverlayService.this,"暂时无法打开，请重新选择这个应用",Toast.LENGTH_SHORT).show();}
                }
            });
            // Only the permanent corner handle owns the original gesture. While the
            // launcher is visible, consume independent background taps in this canvas.
            view.setOnTouchListener((v,event)->true);
            measure();
            params=new WindowManager.LayoutParams(1,1,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                            |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            // Combined with NOT_FOCUSABLE, ALT_FOCUSABLE_IM layers this window BELOW
            // the input method. Keyboard keys own touches even inside the handle bounds.
            params.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            params.windowAnimations=0;
            params.gravity=Gravity.TOP|Gravity.LEFT;params.setTitle("角落绽放动画画布");
            if(Build.VERSION.SDK_INT>=28)params.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if(Build.VERSION.SDK_INT>=30)params.setFitInsetsTypes(0);
            params.x=0;params.y=0;params.width=screenW;params.height=screenH;params.alpha=0;
            handle=new CornerHandleView(this,prefs);
            handle.setOnTouchListener((v,event)->forwardHandleTouch(event));
            handleParams=new WindowManager.LayoutParams((int)trigger.width(),(int)trigger.height(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                            |WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            handleParams.gravity=Gravity.TOP|Gravity.LEFT;handleParams.windowAnimations=0;
            handleParams.x=(int)trigger.left;handleParams.y=(int)trigger.top;
            handleParams.softInputMode=WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
            handleParams.setTitle("角落绽放固定手势入口");
            if(Build.VERSION.SDK_INT>=28)handleParams.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            if(Build.VERSION.SDK_INT>=30)handleParams.setFitInsetsTypes(0);
            try{
                wm.addView(view,params);added=true;
                wm.addView(handle,handleParams);handleAdded=true;running=true;
                prefs.set("boot_failed",false);
                layoutWindows();
            }
            catch(RuntimeException e){Toast.makeText(this,"入口未能显示，请检查悬浮窗权限",Toast.LENGTH_LONG).show();stopSelf();return START_NOT_STICKY;}
            loadApps();
        }
        return START_NOT_STICKY;
    }
    private void loadApps(){
        final int request=++generation;
        loader.execute(()->{
            List<AppEntry> loaded=AppEntry.load(this);
            main.post(()->{if(request!=generation||view==null)return;all=loaded;view.setApps(AppEntry.favorites(all,prefs));});
        });
    }
    private float dp(float n){return n*getResources().getDisplayMetrics().density;}
    private boolean tapThroughReady(){return prefs.get("tap_through",false)&&TapAccessibilityService.available();}
    private boolean forwardHandleTouch(MotionEvent event){
        if(replaying)return true;
        int action=event.getActionMasked();float x=event.getRawX(),y=event.getRawY();
        if(action==MotionEvent.ACTION_DOWN)deleteHold.down(x,y,deleteSource(x,y));
        else if(action==MotionEvent.ACTION_MOVE){
            for(int h=0;h<event.getHistorySize();h++)
                deleteHold.move(x+event.getHistoricalX(h)-event.getX(),y+event.getHistoricalY(h)-event.getY());
            deleteHold.move(x,y);
        }else if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL)deleteHold.cancel();
        boolean deleted=deleteHold.claimed();
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)deleted=deleteHold.release();
        if(deleted&&(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL))
            TapAccessibilityService.deleteStatus("已停止发送删除键");
        if(deleted){tapClassifier.cancel();view.cancelGesture();return true;}
        if(action==MotionEvent.ACTION_DOWN)tapClassifier.begin(x,y,event.getEventTime());
        else if(action==MotionEvent.ACTION_MOVE){
            // Include coalesced samples, including a swipe which returned to its start.
            for(int h=0;h<event.getHistorySize();h++)
                tapClassifier.move(x+event.getHistoricalX(h)-event.getX(),y+event.getHistoricalY(h)-event.getY());
            tapClassifier.move(x,y);
        }else if(action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_POINTER_DOWN)tapClassifier.cancel();
        boolean tap=action==MotionEvent.ACTION_UP&&tapClassifier.finish(x,y,event.getEventTime())&&!view.gestureActive();
        boolean consumed=view.onTouchEvent(event);
        if(view.gestureActive())deleteHold.cancel();
        if(tap&&tapThroughReady())replayTap(x,y);
        return consumed;
    }
    private DeleteHold.Source deleteSource(float x,float y){
        if(!deletePositionReady(x,y))return null;
        DeleteHold.Source editor=TapAccessibilityService.captureDelete();
        if(editor==null)return null;
        return new DeleteHold.Source(){
            public boolean valid(){return deletePositionReady(x,y)&&editor.valid();}
            public boolean erase(){return editor.erase();}
        };
    }
    private boolean deletePositionReady(float x,float y){
        if(Build.VERSION.SDK_INT<33||!prefs.get("hold_delete",false)||!tapThroughReady())return false;
        if(prefs.get("delete_w",0)!=screenW||prefs.get("delete_h",0)!=screenH){TapAccessibilityService.deleteStatus("请重新标记删除键");return false;}
        if(Math.abs(x-prefs.get("delete_x",-1000))>dp(36)||Math.abs(y-prefs.get("delete_y",-1000))>dp(24)){
            TapAccessibilityService.deleteStatus("当前按下位置不在删除键标记范围");return false;
        }
        try{
            WindowMetrics metrics=wm.getCurrentWindowMetrics();
            WindowInsets insets=metrics.getWindowInsets();
            if(!insets.isVisible(WindowInsets.Type.ime())){TapAccessibilityService.deleteStatus("未检测到键盘");return false;}
            int keyboardHeight=insets.getInsets(WindowInsets.Type.ime()).bottom;
            boolean matches=keyboardHeight>0&&Math.abs(keyboardHeight-prefs.get("delete_ime_h",0))<=dp(24)
                    &&y>=metrics.getBounds().bottom-keyboardHeight;
            if(!matches)TapAccessibilityService.deleteStatus("键盘布局变化，请重新标记");
            return matches;
        }catch(RuntimeException e){TapAccessibilityService.deleteStatus("暂时无法检测键盘");return false;}
    }
    private void replayTap(float x,float y){
        if(!handleAdded||replaying)return;
        replaying=true;final int request=++replayGeneration;
        // Make the actual WINDOW transparent and non-touchable before injecting.
        // View alpha alone would still leave an obscuring input window on Android 12+.
        handleParams.alpha=0;handleParams.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try{wm.updateViewLayout(handle,handleParams);}catch(RuntimeException e){cancelTapReplay();return;}
        Runnable restore=()->{if(request==replayGeneration)cancelTapReplay();};
        main.postDelayed(()->{
            if(request!=replayGeneration||!replaying)return;
            if(!tapThroughReady()){restore.run();return;}
            try{if(!TapAccessibilityService.tap(x,y,restore))restore.run();}
            catch(RuntimeException e){restore.run();}
        },32);
        // Also restore if a vendor accessibility implementation omits its callback.
        main.postDelayed(restore,800);
    }
    private void cancelTapReplay(){
        replayGeneration++;replaying=false;
        if(tapClassifier!=null)tapClassifier.cancel();
        if(handleParams==null)return;
        handleParams.alpha=1;handleParams.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if(handleAdded){try{wm.updateViewLayout(handle,handleParams);}catch(RuntimeException e){stopSelf();}}
    }
    private void measure(){
        DisplayMetrics metrics=new DisplayMetrics();wm.getDefaultDisplay().getRealMetrics(metrics);
        screenW=metrics.widthPixels;screenH=metrics.heightPixels;
        float width=dp(prefs.get("width",16)),height=dp(prefs.get("height",88));
        float right=Math.max(dp(175),screenW-dp(prefs.get("right",0)));
        float bottom=Math.min(screenH-dp(24),Math.max(dp(300),screenH-dp(prefs.get("bottom",36))));
        trigger=new RectF(Math.max(0,right-width),Math.max(0,bottom-height),right,bottom);
        view.overlayBounds(screenW,screenH,trigger);
    }
    // Called only for configuration/preferences. Gesture callbacks NEVER resize or move
    // either window: the input handle and the rendering surface have independent bounds.
    private void layoutWindows(){
        if(view==null||params==null)return;
        // Above the IME only when replaying taps is actually available; otherwise
        // keep the keyboard-first behavior of the previous release.
        if(tapThroughReady())params.flags&=~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        else params.flags|=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        params.x=0;params.y=0;params.width=screenW;params.height=screenH;
        if(handleParams!=null){
            if(tapThroughReady())handleParams.flags&=~WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            else handleParams.flags|=WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
            handleParams.x=(int)trigger.left;handleParams.y=(int)trigger.top;
            handleParams.width=(int)trigger.width();handleParams.height=(int)trigger.height();
        }
        try{
            if(added)wm.updateViewLayout(view,params);
            if(handleAdded)wm.updateViewLayout(handle,handleParams);
        }catch(RuntimeException e){stopSelf();}
        if(handle!=null){
            handle.invalidate();
            if(Build.VERSION.SDK_INT>=29)handle.setSystemGestureExclusionRects(Collections.singletonList(
                    new Rect(0,0,(int)trigger.width(),(int)trigger.height())));
        }
    }
    private void showExpanded(boolean expanded){
        if(view==null||params==null)return;
        // Active launcher is a touchable panel, so it can render at full opacity.
        // Idle canvas is transparent AND non-touchable. Bounds remain unchanged.
        params.alpha=expanded?1f:0f;
        if(expanded)params.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        else params.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        if(handle!=null)handle.setAlpha(expanded?0f:1f);
        if(added){try{wm.updateViewLayout(view,params);}catch(RuntimeException e){stopSelf();}}
    }
    @Override public void onConfigurationChanged(Configuration c){
        super.onConfigurationChanged(c);if(view==null)return;deleteHold.cancel();cancelTapReplay();view.reset();measure();layoutWindows();showExpanded(false);
    }
    @Override public void onDestroy(){
        if(deleteHold!=null)deleteHold.cancel();
        running=false;generation++;replayGeneration++;TapAccessibilityService.setStateListener(null);loader.shutdownNow();main.removeCallbacksAndMessages(null);
        getSharedPreferences("bloom",MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changes);
        try{unregisterReceiver(screen);}catch(IllegalArgumentException ignored){}
        if(added&&view!=null){view.reset();try{wm.removeViewImmediate(view);}catch(RuntimeException ignored){}}
        if(handleAdded&&handle!=null){try{wm.removeViewImmediate(handle);}catch(RuntimeException ignored){}}
        handleAdded=false;handle=null;added=false;view=null;stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();
    }
    @Override public IBinder onBind(Intent i){return null;}
}
