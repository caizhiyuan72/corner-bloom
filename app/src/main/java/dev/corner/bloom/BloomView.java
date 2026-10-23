package dev.corner.bloom;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Gesture input and animated launcher drawing for the real overlay. */
final class BloomView extends View implements Choreographer.FrameCallback {
    interface Host { void expand(); void collapse(); void launch(AppEntry entry); }
    private final Prefs prefs;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final float density;
    private final SwipeGate swipeGate;
    private final Host host;
    private final boolean overlay;
    private List<AppEntry> apps=new ArrayList<>();
    private final RectF trigger=new RectF(),iconBounds=new RectF();
    private final Typeface labelTypeface=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private final float[] xs=new float[15],ys=new float[15],emphasis=new float[15];
    private FanGeometry.Point[] points=new FanGeometry.Point[0];
    private float fullWidth,fullHeight,originX,originY,downX,downY,fingerX,fingerY;
    private float progress,velocity,target;
    private float iconDp=47,hitDp=32;
    private boolean tracking,activated,scheduled,closing;
    private int pointerId=-1,selected=-1;
    private long previousFrame;
    private Shader fogShader;
    private int cachedFogStrength=-1,cachedFogFeather=-1;
    private float fogX,fogY,fogRadius;

    BloomView(Context c,Prefs prefs,boolean overlay,Host host){
        super(c);this.prefs=prefs;this.overlay=overlay;this.host=host;
        density=getResources().getDisplayMetrics().density;
        swipeGate=new SwipeGate(Math.max(20,ViewConfiguration.get(c).getScaledTouchSlop()*1.25f/density));
        setLayerType(View.LAYER_TYPE_HARDWARE,null);
        if(android.os.Build.VERSION.SDK_INT<28)setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        paint.setDither(true);
        setContentDescription("右下角上滑，不松手滑到应用，松手选择；滑回角落取消");
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    float dp(float n){return n*density;}
    boolean gestureActive(){return activated;}
    boolean interactionActive(){return tracking||closing||scheduled;}
    void setApps(List<AppEntry> value){apps=new ArrayList<>(value);recalculate();invalidate();}
    void overlayBounds(int w,int h,RectF bounds){fullWidth=w;fullHeight=h;trigger.set(bounds);recalculate();}
    void reset(){
        Choreographer.getInstance().removeFrameCallback(this);scheduled=false;previousFrame=0;
        progress=velocity=target=0;tracking=activated=closing=false;pointerId=-1;selected=-1;
        swipeGate.reset();
        Arrays.fill(emphasis,0);invalidate();
    }
    void cancelGesture(){
        getParent().requestDisallowInterceptTouchEvent(false);
        if(!activated){reset();return;}
        tracking=false;pointerId=-1;selected=-1;closing=true;target=0;kick();
    }
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){
        super.onSizeChanged(w,h,ow,oh);
        fogShader=null;
        if(!overlay){fullWidth=w;fullHeight=h;trigger.set(w-dp(64),h-dp(94),w-dp(10),h-dp(18));recalculate();}
    }
    private void recalculate(){
        fogShader=null;
        if(fullWidth==0||fullHeight==0)return;
        originX=trigger.right-dp(8);originY=trigger.bottom-dp(8);
        points=FanGeometry.layout(apps.size(),fullWidth/density,fullHeight/density,
                originX/density,originY/density,prefs.get("spread",100)/100f);
        float closest=1000;
        for(int i=0;i<points.length;i++)for(int j=0;j<i;j++)
            closest=Math.min(closest,(float)Math.hypot(points[i].x-points[j].x,points[i].y-points[j].y));
        iconDp=Math.max(25,Math.min(47,closest*.83f));hitDp=Math.min(32,closest*.52f);
    }
    private void kick(){if(!scheduled&&isAttachedToWindow()){scheduled=true;Choreographer.getInstance().postFrameCallback(this);}}
    @Override public void doFrame(long now){
        scheduled=false;
        float dt=previousFrame==0?1f/60:Math.min(.032f,(now-previousFrame)/1_000_000_000f);previousFrame=now;
        float strength=prefs.get("motion",100)/100f;
        float damping=strength<.05?30f:23f-4f*Math.min(strength,1.5f);
        if(closing){progress=OverlayMotion.close(progress,dt);velocity=0;}
        else{velocity+=(250f*(target-progress)-damping*velocity)*dt;progress+=velocity*dt;}
        progress=Math.max(0,Math.min(1.17f,progress));
        boolean moving=Math.abs(target-progress)>.0015f||Math.abs(velocity)>.012f;
        if(!moving){progress=target;velocity=0;}
        for(int i=0;i<apps.size();i++){
            float goal=i==selected?1:0;
            emphasis[i]+=(goal-emphasis[i])*Math.min(1,dt*19);
            if(Math.abs(goal-emphasis[i])>.005f)moving=true;
        }
        updatePositions();
        if(tracking&&activated)selectAtFinger();
        invalidate();
        if(closing&&progress<.006f&&Math.abs(velocity)<.1f){
            closing=false;activated=false;progress=0;previousFrame=0;host.collapse();return;
        }
        if(moving)kick();else previousFrame=0;
    }
    private void updatePositions(){
        float strength=prefs.get("motion",100)/100f;
        for(int i=0;i<points.length;i++){
            FanGeometry.Point p=points[i];
            float t=FanGeometry.clamp((progress-p.delay)/(1-p.delay),0,1.14f);
            float curl=(float)Math.sin(Math.PI*Math.min(t,1))*dp(17)*(i%2==0?1:-1)*strength;
            xs[i]=originX+(p.x*density-originX)*t+curl;
            ys[i]=originY+(p.y*density-originY)*t-curl*.4f;
        }
    }
    private void selectAtFinger(){
        int next=-1;
        if(progress>.55f&&Math.hypot(fingerX-originX,fingerY-originY)>dp(56))
            next=FanGeometry.hit(fingerX,fingerY,xs,ys,apps.size(),dp(hitDp),selected);
        if(next!=selected){
            selected=next;
            if(next>=0&&prefs.get("haptic",true))performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            kick();
        }
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            if(!overlay&&!trigger.contains(event.getX(),event.getY()))return false;
            if(closing)return true;
            pointerId=event.getPointerId(0);
            downX=fingerX=overlay?event.getRawX():event.getX();
            downY=fingerY=overlay?event.getRawY():event.getY();
            tracking=true;activated=false;selected=-1;target=0;
            swipeGate.reset();
            getParent().requestDisallowInterceptTouchEvent(true);
            // A press only arms tracking. Keep the small handle window and start no frames.
            return true;
        }
        if(!tracking)return true;
        if(action==MotionEvent.ACTION_POINTER_DOWN||action==MotionEvent.ACTION_CANCEL){cancelGesture();return true;}
        int index=event.findPointerIndex(pointerId);
        if(index<0){cancelGesture();return true;}
        // Only one pointer is accepted, so raw coordinates always belong to that pointer.
        fingerX=overlay?event.getRawX():event.getX(index);
        fingerY=overlay?event.getRawY():event.getY(index);
        float upward=downY-fingerY,travel=(float)Math.hypot(downX-fingerX,downY-fingerY);
        if(action==MotionEvent.ACTION_MOVE){
            if(!activated){
                if(!swipeGate.move((fingerX-downX)/density,upward/density))return true;
                activated=true;
                if(overlay)host.expand();
            }
            boolean pulledBack=Math.hypot(fingerX-originX,fingerY-originY)<dp(48);
            target=pulledBack?0:Math.max(.68f,FanGeometry.clamp(travel/dp(72),0,1));
            updatePositions();selectAtFinger();kick();return true;
        }
        if(action==MotionEvent.ACTION_UP){
            if(!activated){getParent().requestDisallowInterceptTouchEvent(false);reset();return true;}
            updatePositions();selectAtFinger();
            AppEntry choice=activated&&selected>=0?apps.get(selected):null;
            tracking=false;pointerId=-1;selected=-1;target=0;closing=true;kick();
            getParent().requestDisallowInterceptTouchEvent(false);
            if(choice!=null)host.launch(choice);
            performClick();return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(overlay&&getWidth()<=trigger.width()+2&&getHeight()<=trigger.height()+2)return;
        float visible=Math.min(1,progress);
        if(visible>.002f){
            drawFog(canvas,visible);
            updatePositions();
            float strength=prefs.get("motion",100)/100f;
            for(int i=0;i<apps.size();i++)if(i!=selected)drawIcon(canvas,i,strength);
            if(selected>=0)drawIcon(canvas,selected,strength);
            paint.setColor(Color.argb((int)(165*visible),216,209,246));
            canvas.drawCircle(originX,originY,dp(6)*(1-visible*.3f),paint);
            if(selected>=0)drawLabel(canvas,apps.get(selected).name,xs[selected],ys[selected]-dp(43));
        }
        // The real handle has its own permanent window. Never draw it in this canvas.
        if(!overlay&&progress<.6f)drawHandle(canvas,trigger);
        if(!overlay&&progress<.01f){
            paint.setColor(0xff88869e);paint.setTextSize(dp(13));paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("从右下角的亮条向上划",getWidth()/2f,dp(45),paint);
            canvas.drawText("保持按住，滑向图标后松手",getWidth()/2f,dp(68),paint);
        }
    }
    private void drawFog(Canvas canvas,float visible){
        int strength=prefs.get("fog_strength",22),feather=prefs.get("fog_feather",120);
        if(strength<=0||points.length==0)return;
        if(fogShader==null||cachedFogStrength!=strength||cachedFogFeather!=feather){
            float left=points[0].x,right=left,top=points[0].y,bottom=top;
            for(FanGeometry.Point p:points){left=Math.min(left,p.x);right=Math.max(right,p.x);top=Math.min(top,p.y);bottom=Math.max(bottom,p.y);}
            float cx=(left+right)/2,cy=(top+bottom)/2,reach=0;
            for(FanGeometry.Point p:points)reach=Math.max(reach,(float)Math.hypot(p.x-cx,p.y-cy));
            float radius=dp(reach+iconDp/2+Math.max(60,feather));
            int[] colors=new int[25];float[] stops=new float[25];
            for(int i=0;i<colors.length;i++){
                stops[i]=i/(float)(colors.length-1);
                int alpha=Math.round(255*FanGeometry.clamp(strength,0,50)/100f*FogProfile.opacity(stops[i]));
                colors[i]=Color.argb(alpha,26,23,39);
            }
            fogShader=new RadialGradient(dp(cx),dp(cy),radius,colors,stops,Shader.TileMode.CLAMP);
            // Fade against EVERY viewport edge as well as the circular falloff.
            // A circle extending beyond the left viewport must not be visibly clipped.
            float edge=Math.min(dp(Math.max(60,feather)),Math.min(getWidth(),getHeight())*.3f);
            int[] mask=new int[25];
            for(int i=0;i<mask.length;i++)mask[i]=Color.argb(Math.round(255*FogProfile.edgeOpacity(stops[i],1)),255,255,255);
            Shader[] edges={
                new LinearGradient(0,0,edge,0,mask,stops,Shader.TileMode.CLAMP),
                new LinearGradient(getWidth(),0,getWidth()-edge,0,mask,stops,Shader.TileMode.CLAMP),
                new LinearGradient(0,0,0,edge,mask,stops,Shader.TileMode.CLAMP),
                new LinearGradient(0,getHeight(),0,getHeight()-edge,mask,stops,Shader.TileMode.CLAMP)
            };
            for(Shader edgeMask:edges)fogShader=new ComposeShader(fogShader,edgeMask,PorterDuff.Mode.DST_IN);
            fogX=dp(cx);fogY=dp(cy);fogRadius=radius;
            cachedFogStrength=strength;cachedFogFeather=feather;
        }
        // Retain the established fog strength when removing the old whole-window fade.
        float fogScale=overlay&&android.os.Build.VERSION.SDK_INT>=31?.8f:1f;
        paint.setColor(Color.WHITE);paint.setAlpha((int)(255*visible*fogScale));paint.setShader(fogShader);
        canvas.drawCircle(fogX,fogY,fogRadius,paint);
        paint.setShader(null);paint.setAlpha(255);
    }
    private void drawHandle(Canvas canvas,RectF bounds){
        if(!prefs.get("guide",true))return;
        float alpha=1-Math.min(1,progress);
        paint.setColor(Color.argb((int)(180*alpha),182,161,255));
        float x=bounds.right-dp(8);
        canvas.drawRoundRect(x-dp(2),bounds.centerY()-dp(17),x+dp(2),bounds.centerY()+dp(17),dp(2),dp(2),paint);
    }
    private void drawIcon(Canvas canvas,int i,float strength){
        float t=FanGeometry.clamp((progress-points[i].delay)/(1-points[i].delay),0,1);
        if(t<.01f)return;
        float focus=emphasis[i],size=dp(iconDp)*(.28f+.72f*t)*(1+.23f*focus);
        float rotation=((i%2==0?1:-1)*(32+i*2)*(1-t)+points[i].rotation*t)*strength*(1-focus);
        canvas.save();canvas.translate(xs[i],ys[i]);canvas.rotate(rotation);
        paint.setAlpha((int)(255*t));
        if(focus>.01f){
            paint.setColor(Color.argb((int)(90*t*focus),191,171,255));
            canvas.drawRoundRect(-size*.62f,-size*.62f,size*.62f,size*.62f,dp(20),dp(20),paint);
        }
        paint.setColor(Color.WHITE);paint.setAlpha(255);
        iconBounds.set(-size/2,-size/2,size/2,size/2);
        canvas.drawBitmap(apps.get(i).icon,null,iconBounds,paint);
        paint.setAlpha(255);canvas.restore();
    }
    private void drawLabel(Canvas canvas,String text,float x,float y){
        paint.setTextSize(dp(13));paint.setTypeface(labelTypeface);
        if(text.length()>20)text=text.substring(0,19)+"…";
        float half=paint.measureText(text)/2+dp(12);
        x=FanGeometry.clamp(x,half+dp(6),getWidth()-half-dp(6));y=Math.max(dp(26),y);
        paint.setColor(0xee282535);canvas.drawRoundRect(x-half,y-dp(20),x+half,y+dp(9),dp(14),dp(14),paint);
        paint.setColor(Color.WHITE);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(text,x,y,paint);
        paint.setTypeface(Typeface.DEFAULT);
    }
    @Override protected void onDetachedFromWindow(){
        Choreographer.getInstance().removeFrameCallback(this);scheduled=false;previousFrame=0;super.onDetachedFromWindow();
    }
}
