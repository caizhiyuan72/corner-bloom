#!/usr/bin/env python3
"""Production navigation lifecycle with host stand-ins, not Android rendering."""
from pathlib import Path
import shutil,subprocess
project=Path(__file__).resolve().parents[1]
work=project/'app/build/page-protocol'
shutil.rmtree(work,ignore_errors=True)
sources={
'android/content/Context.java':'package android.content;public class Context {}',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent {}',
'android/animation/TimeInterpolator.java':'package android.animation;public interface TimeInterpolator{float getInterpolation(float p);}',
'android/animation/Animator.java':'package android.animation;public class Animator {}',
'android/animation/AnimatorListenerAdapter.java':'package android.animation;public class AnimatorListenerAdapter{public void onAnimationEnd(Animator a){}}',
'android/animation/ValueAnimator.java':'''package android.animation;import java.util.*;
public class ValueAnimator extends Animator{
 public interface AnimatorUpdateListener{void onAnimationUpdate(ValueAnimator a);}
 public static boolean enabled=true;static List<ValueAnimator> all=new ArrayList<>();
 float from,to,value;boolean running;TimeInterpolator curve=p->p;AnimatorUpdateListener update;AnimatorListenerAdapter listener;
 public static ValueAnimator ofFloat(float... v){ValueAnimator a=new ValueAnimator();a.from=v[0];a.to=v[1];all.add(a);return a;}
 public static boolean areAnimatorsEnabled(){return enabled;}public void setDuration(long d){}public void setInterpolator(TimeInterpolator i){curve=i;}
 public void addUpdateListener(AnimatorUpdateListener l){update=l;}public void addListener(AnimatorListenerAdapter l){listener=l;}
 public Object getAnimatedValue(){return value;}public void start(){running=true;step(0);}
 void step(float f){value=from+(to-from)*curve.getInterpolation(f);update.onAnimationUpdate(this);if(f>=1){running=false;listener.onAnimationEnd(this);}}
 public void cancel(){if(running){running=false;listener.onAnimationEnd(this);}}
 public static void advance(float f){for(ValueAnimator a:new ArrayList<>(all))if(a.running)a.step(f);}
}
''',
'android/view/View.java':'''package android.view;import android.content.*;import java.util.*;
public class View{
 public static final int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_NO=2,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4,IMPORTANT_FOR_ACCESSIBILITY_AUTO=0;
 public static final int LAYER_TYPE_NONE=0,LAYER_TYPE_HARDWARE=2,LAYOUT_DIRECTION_RTL=1;
 public float x,alpha=1;public int visibility=0,layer,accessibility,width=1000,dir=0;public Object parent;
 static List<Runnable> pending=new ArrayList<>();
 public View(Context c){}public void setBackgroundColor(int c){}public void setClickable(boolean b){}
 public void setImportantForAccessibility(int v){accessibility=v;}public void setVisibility(int v){visibility=v;}
 public void setFocusableInTouchMode(boolean b){}public boolean requestFocus(){return true;}
 public void postOnAnimation(Runnable r){pending.add(r);}public boolean removeCallbacks(Runnable r){return pending.remove(r);}
 public static void frame(){List<Runnable> jobs=new ArrayList<>(pending);pending.clear();for(Runnable r:jobs)r.run();}
 public void setLayerType(int v,Object p){}public void setTranslationX(float v){x=v;}public void setAlpha(float v){alpha=v;}
 public int getLayoutDirection(){return dir;}public int getWidth(){return width;}
 protected void onSizeChanged(int w,int h,int ow,int oh){}protected void onDetachedFromWindow(){}
 public boolean onTouchEvent(MotionEvent e){return false;}
}
''',
'android/widget/FrameLayout.java':'''package android.widget;import android.content.*;import android.view.*;import java.util.*;
public class FrameLayout extends View{
 public static class LayoutParams{public LayoutParams(int w,int h){}}
 public List<View> children=new ArrayList<>();public FrameLayout(Context c){super(c);}public void setClipChildren(boolean b){}
 public void addView(View v,LayoutParams p){addView(v,children.size(),p);}
 public void addView(View v,int at,LayoutParams p){if(v.parent!=null)throw new IllegalStateException("already parented");children.add(at,v);v.parent=this;}
 public void removeView(View v){children.remove(v);v.parent=null;}public int getChildCount(){return children.size();}
 public boolean onInterceptTouchEvent(MotionEvent e){return false;}
}
''',
'dev/corner/bloom/PageHostProtocolTest.java':'''package dev.corner.bloom;
import android.content.*;import android.view.*;import android.animation.*;
public final class PageHostProtocolTest{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static boolean near(float a,float b){return Math.abs(a-b)<.2f;}
 static void finish(){View.frame();ValueAnimator.advance(1);}
 public static void main(String[] args){
  Context c=new Context();PageHost host=new PageHost(c,0);View home=new View(c);host.setRoot(home);int[] closed={0};
  View detail=new View(c);host.push(detail,p->closed[0]++);
  check(detail.x==1000&&detail.alpha==1,"destination starts outside viewport and opaque");
  check(!host.canPush()&&host.push(new View(c),null)==null,"rapid duplicate navigation ignored");
  check(host.onInterceptTouchEvent(new MotionEvent()),"moving pages cannot leak taps");
  View.frame();ValueAnimator.advance(.2f);
  check(detail.x>0&&detail.x<1000&&detail.alpha==1,"page slides without fade");
  check(near(home.x,-(1000-detail.x)/4),"quarter parallax shares progress");
  float at=detail.x;check(host.pop(),"back during entry accepted");
  check(near(detail.x,at),"reversal does not jump");
  check(host.pop(),"repeated back consumed");ValueAnimator.advance(1);
  check(closed[0]==1&&home.x==0&&home.alpha==1&&home.visibility==View.VISIBLE,"back restores home exactly once");
  check(detail.parent==null&&!host.isTransitioning()&&!host.pop(),"root cannot be popped");
  View settings=new View(c);host.push(settings,p->closed[0]++);finish();
  View editor=new View(c);host.push(editor,p->closed[0]++);finish();
  check(home.visibility==View.GONE&&settings.visibility==View.GONE,"hidden parents retained");
  host.pop();finish();check(settings.visibility==View.VISIBLE&&settings.x==0&&home.visibility==View.GONE,"nested back restores previous page");
  check(closed[0]==2,"only nested cleanup fires");
  host.push(new View(c),p->closed[0]++);View.frame();ValueAnimator.advance(.3f);host.finishTransition();
  check(!host.isTransitioning(),"pause settles incomplete entry");
  host.dispose();host.dispose();View.frame();ValueAnimator.advance(1);
  check(closed[0]==4&&host.getChildCount()==1,"destruction clears stale callbacks exactly once");
  check(host.push(new View(c),null)==null,"disposed host cannot reopen");
  PageHost instant=new PageHost(c,0);instant.setRoot(new View(c));ValueAnimator.enabled=false;
  View noMotion=new View(c);PageHost.Page quick=instant.push(noMotion,p->closed[0]++);View.frame();
  check(!instant.isTransitioning()&&noMotion.x==0&&noMotion.alpha==1,"system animation-off lands immediately");
  quick.closeImmediately();check(!instant.isTransitioning()&&closed[0]==5,"immediate editor close");ValueAnimator.enabled=true;
  PageHost early=new PageHost(c,0);early.setRoot(new View(c));early.push(new View(c),p->closed[0]++);early.pop();finish();
  check(closed[0]==6&&early.getChildCount()==1,"back before first frame cancels pending entry");
  early.push(new View(c),p->closed[0]++);early.onDetachedFromWindow();finish();
  check(!early.isTransitioning(),"detach removes animation callbacks");early.dispose();check(closed[0]==7,"detached cleanup once");
  System.out.println("Production PageHost: opaque slide, nested/reversed back, rapid taps, animation-off, pause, detach and cleanup passed (host fixtures).");
 }
}
'''
}
paths=[]
for name,source in sources.items():
    path=work/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);paths.append(str(path))
paths += [str(project/'app/src/main/java/dev/corner/bloom'/name) for name in ['PageHost.java','PageMotion.java']]
subprocess.run(['java','com.sun.tools.javac.Main','-d',str(work/'classes'),*paths],check=True)
subprocess.run(['java','-cp',str(work/'classes'),'dev.corner.bloom.PageHostProtocolTest'],check=True)
