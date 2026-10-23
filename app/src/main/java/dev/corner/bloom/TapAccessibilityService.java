package dev.corner.bloom;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Region;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

/** Short tap replay and opt-in editor delete keys. No touch exploration. */
public final class TapAccessibilityService extends AccessibilityService {
    private static volatile TapAccessibilityService current;
    private static Runnable stateListener;
    private static String lastTap="最近补点：尚未触发";
    private static String lastDelete="长按删除：尚未触发";
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean ready;
    private String setup="正在恢复轻点服务…";
    static boolean available(){return current!=null&&current.ready;}
    static String connectionHint(){return current==null?"请在系统无障碍设置中开启「角落绽放 · 点击穿透」。":current.setup;}
    static String lastTapHint(){return lastTap;}
    static String lastDeleteHint(){return lastDelete;}
    static void deleteStatus(String value){lastDelete="长按删除："+value;}
    static void updateDeleteMode(boolean enabled){
        TapAccessibilityService service=current;if(service==null||Build.VERSION.SDK_INT<33)return;
        try{
            AccessibilityServiceInfo info=service.getServiceInfo();
            if(enabled)info.flags|=AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            else info.flags&=~AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            service.setServiceInfo(info);
        }catch(RuntimeException e){deleteStatus("输入编辑接口未就绪，请重新开启无障碍服务");}
    }
    static DeleteHold.Source captureDelete(){
        TapAccessibilityService service=current;
        if(service==null||!service.ready||Build.VERSION.SDK_INT<33)return null;
        DeleteHold.Source source;
        try{source=EditorDeleteBridge.capture(service);}catch(RuntimeException e){source=null;}
        if(source==null){lastDelete="长按删除：尚未连接输入框，请先调出键盘";return null;}
        final DeleteHold.Source captured=source;
        return new DeleteHold.Source(){
            public boolean valid(){return current==service&&service.ready&&captured.valid();}
            public boolean erase(){
                boolean sent=captured.erase();lastDelete=sent?"长按删除：已发送删除键":"长按删除：输入连接已结束";return sent;
            }
        };
    }
    static void setStateListener(Runnable value){stateListener=value;}
    private static void stateChanged(){if(stateListener!=null)stateListener.run();}
    @Override protected void onServiceConnected(){
        current=this;ready=false;setup="正在清理旧版触摸配置…";
        try{
            AccessibilityServiceInfo info=getServiceInfo();
            boolean hadExploration=(info.flags&AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)!=0;
            // A reconnect may retain dynamic service info from the previous process.
            // Clear it even though this process has no hold controller object.
            info.flags&=~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
            if(Build.VERSION.SDK_INT>=33){
                if(new Prefs(this).get("hold_delete",false))info.flags|=AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
                else info.flags&=~AccessibilityServiceInfo.FLAG_INPUT_METHOD_EDITOR;
            }
            info.eventTypes=0;
            setServiceInfo(info);
            if(Build.VERSION.SDK_INT>=30&&hadExploration)
                setTouchExplorationPassthroughRegion(Display.DEFAULT_DISPLAY,new Region());
            verifyTapMode(0);
        }catch(RuntimeException e){setup="轻点服务配置未恢复，请在系统无障碍设置中关闭再开启本服务。";stateChanged();}
    }
    private void verifyTapMode(int attempt){
        if(current!=this)return;
        AccessibilityServiceInfo info=getServiceInfo();
        AccessibilityManager manager=(AccessibilityManager)getSystemService(ACCESSIBILITY_SERVICE);
        boolean clean=(info.flags&AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)==0
                &&info.eventTypes==0&&!manager.isTouchExplorationEnabled();
        if(clean){ready=true;setup="轻点服务已就绪";stateChanged();return;}
        if(attempt<10){main.postDelayed(()->verifyTapMode(attempt+1),100);return;}
        setup="系统触摸探索仍在开启，轻点服务尚未就绪。请关闭再开启本服务；如有其他读屏服务，也需检查其触摸探索状态。";
        stateChanged();
    }
    @Override public boolean onUnbind(Intent intent){disconnect();return super.onUnbind(intent);}
    @Override public void onDestroy(){disconnect();super.onDestroy();}
    private void disconnect(){main.removeCallbacksAndMessages(null);ready=false;if(current==this)current=null;stateChanged();}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){}
    static boolean tap(float x,float y,Runnable finished){
        TapAccessibilityService service=current;if(service==null||!service.ready){lastTap="最近补点：服务未就绪";return false;}
        Path path=new Path();path.moveTo(x,y);
        GestureDescription gesture=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,24)).build();
        lastTap="最近补点：已请求";
        boolean accepted=service.dispatchGesture(gesture,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription gesture){lastTap="最近补点：系统已完成手势";finished.run();}
            @Override public void onCancelled(GestureDescription gesture){lastTap="最近补点：被系统取消";finished.run();}
        },null);
        if(!accepted)lastTap="最近补点：系统未接受请求";
        return accepted;
    }
}
