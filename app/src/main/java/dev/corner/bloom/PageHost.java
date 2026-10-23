package dev.corner.bloom;

import android.animation.*;
import android.content.Context;
import android.view.*;
import android.widget.FrameLayout;
import java.util.ArrayList;

/** A retained page stack in one window. No fullscreen dialogs or independent window fades. */
final class PageHost extends FrameLayout {
    interface Closed {void run(Page page);}
    final class Page {
        final View view;private final Closed closed;
        private boolean removed;
        private Page(View view,Closed closed){this.view=view;this.closed=closed;}
        void closeImmediately(){if(top()==this)pop(false);}
    }
    private final ArrayList<Page> stack=new ArrayList<>();
    private final View scrim;
    private ValueAnimator driver;
    private Runnable pending;
    private float progress=1;
    private int generation;
    private boolean moving,closing,disposed;
    PageHost(Context context,int color){
        super(context);setBackgroundColor(color);setClipChildren(true);
        scrim=new View(context);scrim.setBackgroundColor(0xff000000);scrim.setClickable(true);
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    boolean isTransitioning(){return moving;}
    boolean canPush(){return !disposed&&!moving;}
    private Page top(){return stack.isEmpty()?null:stack.get(stack.size()-1);}
    void setRoot(View root){
        if(!stack.isEmpty())throw new IllegalStateException("Root already set");
        stack.add(new Page(root,null));addView(root,new LayoutParams(-1,-1));
    }
    Page push(View view,Closed closed){
        if(!canPush()||stack.isEmpty())return null;
        Page previous=top(),page=new Page(view,closed);
        previous.view.setVisibility(VISIBLE);
        previous.view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addView(scrim,new LayoutParams(-1,-1));
        stack.add(page);addView(view,new LayoutParams(-1,-1));
        view.setFocusableInTouchMode(true);view.requestFocus();
        moving=true;closing=false;progress=0;render();
        // Allow the pressed row to draw and the complete destination to lay out first.
        pending=()->{pending=null;settle(false,true);};postOnAnimation(pending);
        return page;
    }
    boolean pop(){return pop(true);}
    private boolean pop(boolean animate){
        if(stack.size()<2||disposed)return false;
        if(closing){if(!animate)finishTransition();return true;}
        if(!moving){
            Page previous=stack.get(stack.size()-2);previous.view.setVisibility(VISIBLE);
            removeView(scrim);addView(scrim,getChildCount()-1,new LayoutParams(-1,-1));
            progress=1;
        }
        settle(true,animate);return true;
    }
    private void cancelDriver(){
        generation++;
        if(pending!=null){removeCallbacks(pending);pending=null;}
        if(driver!=null){driver.cancel();driver=null;}
    }
    private void settle(boolean back,boolean animate){
        cancelDriver();moving=true;closing=back;
        float target=back?0:1;
        if(!animate||!ValueAnimator.areAnimatorsEnabled()||Math.abs(progress-target)<.001f){finishTransition();return;}
        Page front=top(),behind=stack.get(stack.size()-2);
        front.view.setLayerType(View.LAYER_TYPE_HARDWARE,null);behind.view.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        final int token=generation;
        ValueAnimator animation=ValueAnimator.ofFloat(progress,target);driver=animation;
        animation.setDuration(Math.max(80,Math.round(PageMotion.DURATION_MS*Math.abs(progress-target))));
        animation.setInterpolator(PageMotion::ease);
        animation.addUpdateListener(a->{if(token==generation){progress=(float)a.getAnimatedValue();render();}});
        animation.addListener(new AnimatorListenerAdapter(){
            @Override public void onAnimationEnd(Animator a){if(token==generation)finishTransition();}
        });
        animation.start();
    }
    private void render(){
        if(stack.size()<2)return;
        boolean rtl=getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;
        View front=top().view,behind=stack.get(stack.size()-2).view;
        front.setTranslationX(PageMotion.frontX(getWidth(),progress,rtl));front.setAlpha(1);
        behind.setTranslationX(PageMotion.backX(getWidth(),progress,rtl));behind.setAlpha(PageMotion.backAlpha(progress));
        scrim.setAlpha(.5f*progress);
    }
    private void reset(View view){
        view.setTranslationX(0);view.setAlpha(1);view.setLayerType(View.LAYER_TYPE_NONE,null);
    }
    void finishTransition(){
        if(!moving)return;
        boolean remove=closing;cancelDriver();moving=false;closing=false;
        removeView(scrim);
        if(remove){Page page=stack.remove(stack.size()-1);removeView(page.view);reset(page.view);notifyClosed(page);}
        for(int i=0;i<stack.size();i++){
            View view=stack.get(i).view;reset(view);boolean active=i==stack.size()-1;
            view.setVisibility(active?VISIBLE:GONE);
            view.setImportantForAccessibility(active?View.IMPORTANT_FOR_ACCESSIBILITY_AUTO:View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        }
        progress=1;
    }
    private void notifyClosed(Page page){if(!page.removed){page.removed=true;if(page.closed!=null)page.closed.run(page);}}
    void dispose(){
        if(disposed)return;disposed=true;cancelDriver();moving=false;removeView(scrim);
        while(stack.size()>1){Page page=stack.remove(stack.size()-1);removeView(page.view);notifyClosed(page);}
        if(top()!=null)reset(top().view);
    }
    @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){
        super.onSizeChanged(w,h,oldW,oldH);if(moving)render();
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return moving||super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){return moving||super.onTouchEvent(event);}
    @Override protected void onDetachedFromWindow(){finishTransition();super.onDetachedFromWindow();}
}
