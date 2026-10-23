#!/usr/bin/env python3
"""Exercise the production in-app editor with host input/drawing stand-ins.
Not an Android device test. Also writes an SVG of actual editor draw calls for QA.
"""
from pathlib import Path
import shutil
import subprocess

project=Path(__file__).resolve().parents[1]
work=project/"app/build/editor-protocol"
shutil.rmtree(work,ignore_errors=True)
sources={
"android/animation/ValueAnimator.java":'''package android.animation;import java.util.*;public class ValueAnimator {
 public interface AnimatorUpdateListener{void onAnimationUpdate(ValueAnimator a);}
 static List<ValueAnimator> all=new ArrayList<>();public static boolean enabled=true;
 private AnimatorUpdateListener listener;private float fraction;private boolean running;
 public static ValueAnimator ofFloat(float... values){ValueAnimator a=new ValueAnimator();all.add(a);return a;}
 public void setDuration(long d){}public void setInterpolator(Object o){}public void addUpdateListener(AnimatorUpdateListener l){listener=l;}
 public static boolean areAnimatorsEnabled(){return enabled;}public float getAnimatedFraction(){return fraction;}
 public void start(){running=true;fraction=0;listener.onAnimationUpdate(this);}public void cancel(){running=false;}public boolean isRunning(){return running;}
 public static void advanceAll(float t){for(ValueAnimator a:all)if(a.running){a.fraction=t;a.listener.onAnimationUpdate(a);if(t>=1)a.running=false;}}}
''',
"android/view/animation/LinearInterpolator.java":'package android.view.animation;public class LinearInterpolator {}',
"android/content/Context.java":'''package android.content;public class Context {
 public float density=1;public android.content.res.Resources getResources(){return new android.content.res.Resources(density);}}
''',
"android/content/res/Resources.java":'''package android.content.res;public class Resources {
 float density;public Resources(float d){density=d;}public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics(density);}}
''',
"android/util/DisplayMetrics.java":'package android.util;public class DisplayMetrics {public float density;public DisplayMetrics(float d){density=d;}}',
"android/graphics/RectF.java":'''package android.graphics;public class RectF {
 public float left,top,right,bottom;public RectF(){}public RectF(float l,float t,float r,float b){set(l,t,r,b);}
 public void set(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;}
 public boolean contains(float x,float y){return x>=left&&x<=right&&y>=top&&y<=bottom;}}
''',
"android/graphics/Color.java":'package android.graphics;public class Color {public static final int WHITE=-1;}',
"android/graphics/Bitmap.java":'package android.graphics;public class Bitmap {public int id;public Bitmap(int i){id=i;}}',
"android/graphics/Paint.java":'''package android.graphics;public class Paint {
 public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public enum Style{FILL,STROKE}public enum Align{LEFT,CENTER,RIGHT}
 public int color=-1,alpha=255;public float width=1,textSize=12;public Style style=Style.FILL;public Align align=Align.LEFT;
 public Paint(int f){}public void setStyle(Style s){style=s;}public void setColor(int c){color=c;alpha=c>>>24;}
 public void setStrokeWidth(float w){width=w;}public void setAlpha(int a){alpha=a;}public void setTextSize(float s){textSize=s;}public void setTextAlign(Align a){align=a;}}
''',
"android/graphics/Canvas.java":'''package android.graphics;
import java.awt.geom.AffineTransform;import java.util.*;
public class Canvas {
 public StringBuilder svg=new StringBuilder();private AffineTransform matrix=new AffineTransform();private Stack<AffineTransform> stack=new Stack<>();
 String attrs(Paint p){double[] m=new double[6];matrix.getMatrix(m);String color=String.format("#%06x",p.color&0xffffff);
  return " transform='matrix("+m[0]+" "+m[1]+" "+m[2]+" "+m[3]+" "+m[4]+" "+m[5]+")' opacity='"+(p.alpha/255.0)+"' "+
   (p.style==Paint.Style.STROKE?"fill='none' stroke='"+color+"' stroke-width='"+p.width+"'":"fill='"+color+"'");}
 public void save(){stack.push(new AffineTransform(matrix));}public void restore(){matrix=stack.pop();}
 public void clipRect(RectF r){}public void translate(float x,float y){matrix.translate(x,y);}public void scale(float x,float y){matrix.scale(x,y);}
 public void drawRoundRect(RectF r,float x,float y,Paint p){drawRoundRect(r.left,r.top,r.right,r.bottom,x,y,p);}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){svg.append("<rect x='"+l+"' y='"+t+"' width='"+(r-l)+"' height='"+(b-t)+"' rx='"+rx+"' ry='"+ry+"'"+attrs(p)+"/>");}
 public void drawLine(float x,float y,float u,float v,Paint p){Paint.Style old=p.style;p.style=Paint.Style.STROKE;svg.append("<line x1='"+x+"' y1='"+y+"' x2='"+u+"' y2='"+v+"'"+attrs(p)+"/>");p.style=old;}
 public void drawCircle(float x,float y,float r,Paint p){svg.append("<circle cx='"+x+"' cy='"+y+"' r='"+r+"'"+attrs(p)+"/>");}
 public void drawBitmap(Bitmap b,Object src,RectF r,Paint p){int c=p.color;int a=p.alpha;p.setColor(new int[]{0xff3c82f6,0xfff28b52,0xff37aa83,0xffa97ee8,0xffe26185}[b.id%5]);drawRoundRect(r,10,10,p);p.color=c;p.alpha=a;}
 public void drawText(String s,float x,float y,Paint p){svg.append("<text x='"+x+"' y='"+y+"' font-family='sans-serif' font-size='"+p.textSize+"' text-anchor='"+(p.align==Paint.Align.CENTER?"middle":p.align==Paint.Align.RIGHT?"end":"start")+"'"+attrs(p)+">"+s+"</text>");}
}
''',
"android/view/View.java":'''package android.view;import android.content.*;public class View {
 private Context context;public int clicks;public View(Context c){context=c;}public android.content.res.Resources getResources(){return context.getResources();}
 public static class Parent {public void requestDisallowInterceptTouchEvent(boolean b){}}public Parent getParent(){return new Parent();}
 public void setContentDescription(String s){}public void setClickable(boolean v){}public void invalidate(){}
 protected void onSizeChanged(int w,int h,int ow,int oh){}protected void onDraw(android.graphics.Canvas c){}protected void onDetachedFromWindow(){}
 public boolean onTouchEvent(MotionEvent e){return false;}public boolean performClick(){clicks++;return true;}
 public void size(int w,int h){onSizeChanged(w,h,0,0);}}
''',
"android/view/MotionEvent.java":'''package android.view;public class MotionEvent {
 public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 private int action;private float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
 public int getActionMasked(){return action;}public float getX(){return x;}public float getY(){return y;}public float getX(int i){return x;}public float getY(int i){return y;}
 public int getPointerId(int i){return 0;}public int findPointerIndex(int p){return p==0?0:-1;}}
''',
"dev/corner/bloom/AppEntry.java":'''package dev.corner.bloom;final class AppEntry {android.graphics.Bitmap icon;AppEntry(int i){icon=new android.graphics.Bitmap(i);}}''',
"dev/corner/bloom/PositionEditorProtocolTest.java":'''
package dev.corner.bloom;
import android.content.*;import android.graphics.*;import android.view.*;import android.animation.ValueAnimator;import java.util.*;import java.lang.reflect.*;import java.nio.file.*;
public final class PositionEditorProtocolTest {
 static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
 static Object field(Object o,String n)throws Exception{Field f=PositionEditorView.class.getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static void touch(PositionEditorView v,int action,float x,float y)throws Exception{
  RectF s=(RectF)field(v,"screen");float scale=(float)field(v,"scale");v.onTouchEvent(new MotionEvent(action,s.left+x*scale,s.top+y*scale));}
 public static void main(String[] args)throws Exception{
  for(float density:new float[]{1,2.625f,3})for(int count:new int[]{1,9,15})for(int style=0;style<=2;style++)for(int percent:new int[]{60,100,140}){
   Context context=new Context();context.density=density;
   ArrangementDraft d=new ArrangementDraft(411,914,16,88,0,36,count);
   List<AppEntry> apps=new ArrayList<>();for(int i=0;i<count;i++)apps.add(new AppEntry(i));
   int[] changed={0};PositionEditorView v=new PositionEditorView(context,d,apps,1,percent,style,()->changed[0]++);v.size((int)(411*density),(int)(650*density));
   EntryPlacement b=d.bounds();float hx=b.right-8,hy=(b.top+b.bottom)/2;
   touch(v,0,hx,hy);touch(v,2,hx-40,hy-90);touch(v,1,hx-40,hy-90);
   check(d.right==40&&d.bottom==126,"physical preview drag updates handle draft correctly at every density");
   b=d.bounds();hx=b.right-8;hy=(b.top+b.bottom)/2;
   touch(v,0,hx,hy);touch(v,2,hx-20,hy-30);touch(v,3,hx-20,hy-30);
   check(d.right==40&&d.bottom==126,"CANCEL rolls back the active drag");
   touch(v,0,hx,hy);touch(v,2,hx+20,hy+30);touch(v,5,hx+20,hy+30);
   check(d.right==40&&d.bottom==126,"second pointer cancels and rolls back without saving");
   FanGeometry.Point[] points=(FanGeometry.Point[])field(v,"points");
   if(count>1){
    int last=count-1;touch(v,0,points[0].x,points[0].y);touch(v,2,points[last].x,points[last].y);touch(v,1,points[last].x,points[last].y);
    check(d.order[0]==last&&d.order[last]==0,"drag icon swaps visible slots");
    EditorMotion motion=(EditorMotion)field(v,"motion");ValueAnimator animator=(ValueAnimator)field(v,"settle");
    check(animator.isRunning(),"release starts a finite settle animation");
    check(Math.abs(motion.x[last]-points[last].x)<.01f,"exchanged icon begins at its rendered position");
    ValueAnimator.advanceAll(.5f);check(Math.abs(motion.x[last]-points[0].x)<Math.abs(points[last].x-points[0].x),"exchanged icon moves toward its destination");
    int[] pending=d.order.clone();float movingX=motion.x[last],movingY=motion.y[last];
    touch(v,0,movingX,movingY);check((int)field(v,"drag")==0,"moving icon can be picked at its rendered position");
    touch(v,1,movingX,movingY);check(Arrays.equals(d.order,pending),"tap during settling never exchanges slots");
    ValueAnimator.advanceAll(1);check(Math.abs(motion.x[last]-points[0].x)<.01f&&motion.size[0]==1,"icons settle to their actual saved slots");
    int[] before=d.order.clone();touch(v,0,points[0].x,points[0].y);touch(v,2,points[1].x,points[1].y);touch(v,3,points[1].x,points[1].y);
    check(Arrays.equals(d.order,before),"cancel icon drag restores its starting order");
    check(!animator.isRunning(),"cancel stops pending animation");
    ValueAnimator.enabled=false;
    touch(v,0,points[0].x,points[0].y);touch(v,2,points[1].x,points[1].y);touch(v,1,points[1].x,points[1].y);
    check(!animator.isRunning()&&motion.size[d.order[1]]==1,"system animation-off mode snaps without pending frames");
    ValueAnimator.enabled=true;
    touch(v,0,points[1].x,points[1].y);touch(v,1,points[1].x+5,points[1].y+5);
    check(animator.isRunning(),"small release still settles");v.onDetachedFromWindow();
    check(!animator.isRunning(),"detaching editor cancels animation immediately");
   }
   check(changed[0]>0,"editor publishes draft changes to its position caption");
   if(density==1&&count==15){
    v.resetPosition();Canvas canvas=new Canvas();v.onDraw(canvas);
    Files.writeString(Path.of(args[0].replace("editor.svg","editor-"+style+"-"+percent+".svg")),"<svg xmlns='http://www.w3.org/2000/svg' width='411' height='650'><rect width='411' height='650' fill='#f5f5f5'/>"+canvas.svg+"</svg>");
   }
  }
  System.out.println("Production position editor: 3 layouts x 3 sizes x 3 densities x 3 app counts; drag, exchange, cancellation, animation-off and detach passed (host fixtures).");
 }
}
'''
}
paths=[]
for name,source in sources.items():
    path=work/"src"/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);paths.append(str(path))
for name in ["PositionEditorView.java","EditorMotion.java","ArrangementDraft.java","EntryPlacement.java","FanGeometry.java","LauncherGeometry.java"]:
    paths.append(str(project/"app/src/main/java/dev/corner/bloom"/name))
subprocess.run(["java","com.sun.tools.javac.Main","-d",str(work/"classes"),*paths],check=True)
subprocess.run(["java","-cp",str(work/"classes"),"dev.corner.bloom.PositionEditorProtocolTest",str(work/"editor.svg")],check=True)
