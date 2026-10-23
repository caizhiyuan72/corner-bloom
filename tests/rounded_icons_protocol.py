#!/usr/bin/env python3
"""Production icon normalization with a software raster stand-in; not Android rendering."""
from pathlib import Path
import shutil,subprocess
project=Path(__file__).resolve().parents[1];work=project/'app/build/icon-protocol'
shutil.rmtree(work,ignore_errors=True)
sources={
'android/graphics/Bitmap.java':'''package android.graphics;public class Bitmap {
 public enum Config{ARGB_8888}public java.awt.image.BufferedImage image;Bitmap(int w,int h){image=new java.awt.image.BufferedImage(w,h,2);}
 public static Bitmap createBitmap(int w,int h,Config c){return new Bitmap(w,h);}public void recycle(){image=null;}
 public int getPixel(int x,int y){return image.getRGB(x,y);}}
''',
'android/graphics/Color.java':'package android.graphics;public class Color{public static final int WHITE=-1;}',
'android/graphics/Shader.java':'package android.graphics;public class Shader{public enum TileMode{CLAMP}}',
'android/graphics/BitmapShader.java':'package android.graphics;public class BitmapShader extends Shader{public Bitmap bitmap;public BitmapShader(Bitmap b,TileMode x,TileMode y){bitmap=b;}}',
'android/graphics/Paint.java':'''package android.graphics;public class Paint{
 public static final int ANTI_ALIAS_FLAG=1,FILTER_BITMAP_FLAG=2;public int color;public Shader shader;
 public Paint(int flags){}public void setColor(int c){color=c;}public void setShader(Shader s){shader=s;}}
''',
'android/graphics/Canvas.java':'''package android.graphics;import java.awt.*;import java.awt.geom.*;
public class Canvas{
 Bitmap bitmap;public Canvas(Bitmap b){bitmap=b;}Graphics2D graphics(Paint p){Graphics2D g=bitmap.image.createGraphics();
 g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
 if(p.shader instanceof BitmapShader){Bitmap b=((BitmapShader)p.shader).bitmap;g.setPaint(new TexturePaint(b.image,new Rectangle(0,0,b.image.getWidth(),b.image.getHeight())));}
 else g.setColor(new java.awt.Color(p.color,true));return g;}
 public void drawColor(int c){Paint p=new Paint(1);p.setColor(c);drawRect(0,0,bitmap.image.getWidth(),bitmap.image.getHeight(),p);}
 public void drawRect(float l,float t,float r,float b,Paint p){Graphics2D g=graphics(p);g.fill(new Rectangle2D.Float(l,t,r-l,b-t));g.dispose();}
 public void drawRoundRect(float l,float t,float r,float b,float rx,float ry,Paint p){Graphics2D g=graphics(p);g.fill(new RoundRectangle2D.Float(l,t,r-l,b-t,rx*2,ry*2));g.dispose();}
}
''',
'android/graphics/drawable/Drawable.java':'''package android.graphics.drawable;public abstract class Drawable{
 public int left,top,right,bottom;public void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 public abstract void draw(android.graphics.Canvas c);}
''',
'android/graphics/drawable/AdaptiveIconDrawable.java':'''package android.graphics.drawable;public class AdaptiveIconDrawable extends Drawable{
 Drawable back,front;public AdaptiveIconDrawable(Drawable b,Drawable f){back=b;front=f;}
 public static float getExtraInsetFraction(){return .25f;}public Drawable getBackground(){return back;}public Drawable getForeground(){return front;}
 public void draw(android.graphics.Canvas c){throw new AssertionError("must normalize layers rather than system circle mask");}}
''',
'dev/corner/bloom/RoundedIconsProtocolTest.java':'''package dev.corner.bloom;
import android.graphics.*;import android.graphics.drawable.*;
public class RoundedIconsProtocolTest{
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 static class Solid extends Drawable{
  int color;float inset;Solid(int c,float i){color=c;inset=i;}
  public void draw(Canvas c){Paint p=new Paint(1);p.setColor(color);float x=(right-left)*inset,y=(bottom-top)*inset;c.drawRect(left+x,top+y,right-x,bottom-y,p);}
 }
 public static void main(String[] args){
  for(int size:new int[]{32,52,156,208}){
   for(boolean adaptive:new boolean[]{false,true}){
    Solid background=new Solid(0xff3478f6,0),foreground=new Solid(0xffdf3a58,.35f);
    Bitmap icon=RoundedIcons.create(adaptive?new AdaptiveIconDrawable(background,foreground):new Solid(0xffdf3a58,0),size);
    for(int[] point:new int[][]{{0,0},{0,size-1},{size-1,0},{size-1,size-1}})
      check(icon.getPixel(point[0],point[1])>>>24==0,"all corners transparent after curved mask");
    check(icon.getPixel(size/2,size/2)==0xffdf3a58,"center artwork retained");
    check(icon.getPixel(size/2,0)>>>24>240,"rounded square keeps full top edge");
    check(icon.getPixel(0,size/2)>>>24>240,"rounded square keeps full side edge");
    int edgeColor=adaptive?0xff3478f6:0xffdf3a58;
    check(icon.getPixel(size/2,1)==edgeColor&&icon.getPixel(1,size/2)==edgeColor,"original edge color retained without white border or inset");
    if(adaptive)check(background.left<0&&foreground.left<0,"adaptive safe-zone crop retained");
   }
   Bitmap transparent=RoundedIcons.create(new Solid(0xffdf3a58,.25f),size);
   check(transparent.getPixel(size/2,size/2)==0xffdf3a58,"transparent legacy artwork retained");
   check(transparent.getPixel(0,size/2)>>>24==0&&transparent.getPixel(size/2,0)>>>24==0,"transparent margins stay transparent without a white plate");
   Bitmap partial=RoundedIcons.create(new Solid(0x80df3a58,0),size);
   check(partial.getPixel(size/2,size/2)>>>24==128,"original partial transparency retained");
   Bitmap noBackground=RoundedIcons.create(new AdaptiveIconDrawable(null,new Solid(0xffdf3a58,.35f)),size);
   check(noBackground.getPixel(0,size/2)>>>24==0&&noBackground.getPixel(size/2,size/2)==0xffdf3a58,"adaptive icon without a background stays transparent");
   Bitmap originalWhite=RoundedIcons.create(new Solid(0xffffffff,0),size);
   check(originalWhite.getPixel(size/2,size/2)==0xffffffff,"white belonging to original artwork is retained");
  }
  System.out.println("Production rounded icons: no added white border/inset, original transparency/colors, adaptive layers and curved corners passed at 4 raster sizes (software fixtures).");
 }
}
'''
}
paths=[]
for name,source in sources.items():
    path=work/'src'/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source);paths.append(str(path))
paths.append(str(project/'app/src/main/java/dev/corner/bloom/RoundedIcons.java'))
subprocess.run(['java','com.sun.tools.javac.Main','-d',str(work/'classes'),*paths],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(work/'classes'),'dev.corner.bloom.RoundedIconsProtocolTest'],check=True)
