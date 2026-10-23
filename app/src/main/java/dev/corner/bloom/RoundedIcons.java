package dev.corner.bloom;

import android.graphics.*;
import android.graphics.drawable.*;

/** Normalize adaptive and legacy icons once while loading, never on an animation frame. */
final class RoundedIcons {
    static Bitmap create(Drawable drawable,int size){
        Bitmap source=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(source);
        if(drawable instanceof AdaptiveIconDrawable){
            AdaptiveIconDrawable adaptive=(AdaptiveIconDrawable)drawable;
            int extra=Math.round(size*AdaptiveIconDrawable.getExtraInsetFraction());
            for(Drawable layer:new Drawable[]{adaptive.getBackground(),adaptive.getForeground()})
                if(layer!=null){layer.setBounds(-extra,-extra,size+extra,size+extra);layer.draw(canvas);}
        }else{
            drawable.setBounds(0,0,size,size);drawable.draw(canvas);
        }
        Bitmap output=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        paint.setShader(new BitmapShader(source,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP));
        float radius=size*.24f;
        new Canvas(output).drawRoundRect(0,0,size,size,radius,radius,paint);
        source.recycle();return output;
    }
}
