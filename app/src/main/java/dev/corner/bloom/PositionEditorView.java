package dev.corner.bloom;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.*;
import android.view.*;
import android.view.animation.LinearInterpolator;
import java.util.*;

/** A scaled screen inside the Activity. It never creates an overlay or launches apps. */
final class PositionEditorView extends View {
    interface Listener {void changed();}
    private final ArrangementDraft draft;
    private final List<AppEntry> apps;
    private final Listener listener;
    private final float spread,density;
    private final int layoutStyle,iconScale;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF screen=new RectF(),iconBounds=new RectF();
    private final EditorMotion motion;
    private final ValueAnimator settle=ValueAnimator.ofFloat(0,1);
    private FanGeometry.Point[] points=new FanGeometry.Point[0];
    private float scale=1,iconSize=47,downX,downY,startRight,startBottom,fingerX,fingerY;
    private int pointer=-1,drag=-1; // -2: entry handle, >=0: icon slot
    private boolean movedIcon;
    private int[] gestureOrder;
    private int gestureRight,gestureBottom;
    PositionEditorView(Context context,ArrangementDraft draft,List<AppEntry> apps,float spread,Listener listener){
        this(context,draft,apps,spread,100,LauncherGeometry.GRID,listener);
    }
    PositionEditorView(Context context,ArrangementDraft draft,List<AppEntry> apps,float spread,int iconScale,int layoutStyle,Listener listener){
        super(context);this.draft=draft;this.apps=new ArrayList<>(apps);this.spread=spread;this.listener=listener;
        this.iconScale=iconScale;this.layoutStyle=layoutStyle;
        motion=new EditorMotion(apps.size());settle.setDuration(180);settle.setInterpolator(new LinearInterpolator());
        settle.addUpdateListener(animation->{motion.advance(animation.getAnimatedFraction());invalidate();});
        density=getResources().getDisplayMetrics().density;
        setContentDescription("位置与排列预览，拖动亮条移动入口，拖动图标交换位置");setClickable(true);
    }
    private float dp(float n){return n*density;}
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);
        scale=Math.max(.01f,Math.min((w-dp(24))/draft.screenW,(h-dp(16))/draft.screenH));
        float sw=draft.screenW*scale,sh=draft.screenH*scale;
        screen.set((w-sw)/2,(h-sh)/2,(w+sw)/2,(h+sh)/2);recalculate();
    }
    private void recalculate(){
        settle.cancel();
        EntryPlacement b=draft.bounds();
        LauncherGeometry.Layout layout=LauncherGeometry.layout(apps.size(),draft.screenW,draft.screenH,b.right-8,b.bottom-8,spread,iconScale,layoutStyle);
        points=layout.points;iconSize=layout.iconSize;motion.snap(draft.order,points);invalidate();
    }
    private void animateArrangement(){
        settle.cancel();
        if(!ValueAnimator.areAnimatorsEnabled())motion.snap(draft.order,points);
        else if(motion.retarget(draft.order,points))settle.start();
        invalidate();
    }
    void resetPosition(){cancelDrag();draft.resetPosition();recalculate();listener.changed();}
    void cancelDrag(){
        if(pointer>=0){
            draft.right=gestureRight;draft.bottom=gestureBottom;
            System.arraycopy(gestureOrder,0,draft.order,0,draft.order.length);
        }
        pointer=-1;drag=-1;
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
        recalculate();
    }
    private int pickedIconAt(float x,float y){
        int best=-1;double distance=Math.max(iconSize*.65f,dp(18)/scale);
        for(int slot=0;slot<draft.order.length;slot++){
            int item=draft.order[slot];double d=Math.hypot(x-motion.x[item],y-motion.y[item]);
            if(d<distance){best=slot;distance=d;}
        }
        return best;
    }
    private int iconAt(float x,float y){
        int best=-1;double distance=Math.max(iconSize*.65f,dp(18)/scale);
        for(int i=0;i<points.length;i++){
            double d=Math.hypot(x-points[i].x,y-points[i].y);
            if(d<distance){best=i;distance=d;}
        }
        return best;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!screen.contains(event.getX(),event.getY()))return false;
            float x=(event.getX()-screen.left)/scale,y=(event.getY()-screen.top)/scale;
            EntryPlacement b=draft.bounds();float reach=dp(22)/scale;
            boolean onHandle=Math.abs(x-(b.right-8))<=Math.max(b.width()/2,reach)
                    &&y>=b.top-reach*.35f&&y<=b.bottom+reach*.35f;
            int picked=pickedIconAt(x,y);
            boolean insideHandle=x>=b.left&&x<=b.right&&y>=b.top&&y<=b.bottom;
            drag=insideHandle?-2:picked>=0?picked:onHandle?-2:-1;
            if(drag==-1)return true;
            pointer=event.getPointerId(0);downX=fingerX=x;downY=fingerY=y;
            movedIcon=false;
            startRight=b.right;startBottom=b.bottom;gestureRight=draft.right;gestureBottom=draft.bottom;
            gestureOrder=draft.order.clone();
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(true);
            invalidate();return true;
        }
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancelDrag();listener.changed();return true;}
        if(pointer<0)return true;
        int index=event.findPointerIndex(pointer);
        if(index<0){cancelDrag();listener.changed();return true;}
        fingerX=(event.getX(index)-screen.left)/scale;fingerY=(event.getY(index)-screen.top)/scale;
        if(action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){
            if(drag==-2){
                int right=draft.right,bottom=draft.bottom;
                draft.place(startRight+fingerX-downX,startBottom+fingerY-downY);
                if(right!=draft.right||bottom!=draft.bottom)recalculate();
            }
            else{
                movedIcon|=Math.hypot(fingerX-downX,fingerY-downY)>=dp(6)/scale;
                int target=movedIcon?iconAt(fingerX,fingerY):-1;
                if(target>=0&&target!=drag){draft.swap(drag,target);drag=target;animateArrangement();}
                invalidate();
            }
            listener.changed();
        }
        if(action==MotionEvent.ACTION_UP){
            if(drag>=0){
                settle.cancel();motion.place(draft.order[drag],fingerX,fingerY,Math.max(1.2f,dp(42)/scale/iconSize));
            }
            pointer=-1;drag=-1;gestureOrder=null;
            if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
            animateArrangement();performClick();
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);paint.setColor(0xff080910);
        canvas.drawRoundRect(screen,dp(20),dp(20),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xff454355);
        canvas.drawRoundRect(screen,dp(20),dp(20),paint);paint.setStyle(Paint.Style.FILL);
        canvas.save();canvas.clipRect(screen);canvas.translate(screen.left,screen.top);canvas.scale(scale,scale);
        paint.setStrokeWidth(1/scale);paint.setColor(0xff202131);
        for(int i=1;i<4;i++){
            canvas.drawLine(draft.screenW*i/4,0,draft.screenW*i/4,draft.screenH,paint);
            canvas.drawLine(0,draft.screenH*i/4,draft.screenW,draft.screenH*i/4,paint);
        }
        paint.setColor(0xff59586b);
        canvas.drawRoundRect(draft.screenW*.38f,14,draft.screenW*.62f,20,3,3,paint);
        canvas.drawRoundRect(draft.screenW*.35f,draft.screenH-14,draft.screenW*.65f,draft.screenH-10,3,3,paint);
        for(int i=0;i<points.length;i++)if(i!=drag){
            int item=draft.order[i];drawIcon(canvas,i,motion.x[item],motion.y[item],iconSize*motion.size[item]);
        }
        EntryPlacement b=draft.bounds();
        paint.setColor(drag==-2?0x70b6a1ff:0x32b6a1ff);
        canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,5,5,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f/scale);paint.setColor(0xffb6a1ff);
        canvas.drawRoundRect(b.left,b.top,b.right,b.bottom,5,5,paint);paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(b.right-10,(b.top+b.bottom)/2-17,b.right-6,(b.top+b.bottom)/2+17,2,2,paint);
        if(drag>=0){
            paint.setColor(0x66b6a1ff);canvas.drawCircle(points[drag].x,points[drag].y,iconSize*.64f,paint);
            drawIcon(canvas,drag,fingerX,fingerY,Math.max(iconSize*1.2f,dp(42)/scale));
        }
        canvas.restore();
    }
    private void drawIcon(Canvas canvas,int slot,float x,float y,float size){
        paint.setColor(Color.WHITE);paint.setAlpha(255);
        iconBounds.set(x-size/2,y-size/2,x+size/2,y+size/2);
        canvas.drawBitmap(apps.get(draft.order[slot]).icon,null,iconBounds,paint);
        paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.min(12,size*.22f));
        paint.setColor(0xffc9c5da);canvas.drawText(String.valueOf(slot+1),x,y+size*.77f,paint);
    }
    @Override protected void onDetachedFromWindow(){settle.cancel();super.onDetachedFromWindow();}
}
