package dev.corner.bloom;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** A permanent, small input window. Its bounds never change during a gesture. */
final class CornerHandleView extends View {
    private final Prefs prefs;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    CornerHandleView(Context context,Prefs prefs){
        super(context);this.prefs=prefs;density=getResources().getDisplayMetrics().density;
        setContentDescription("从这里向上滑动打开角落绽放");setClickable(true);
    }
    @Override protected void onDraw(Canvas canvas){
        if(!prefs.get("guide",true))return;
        paint.setColor(Color.argb(180,182,161,255));
        float x=getWidth()-8*density,y=getHeight()/2f;
        canvas.drawRoundRect(x-2*density,y-17*density,x+2*density,y+17*density,2*density,2*density,paint);
    }
}
