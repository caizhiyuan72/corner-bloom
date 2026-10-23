package dev.corner.bloom;

/** Classifies the entire path, so an outward-and-back swipe never becomes a tap. */
public final class TapClassifier {
    private final float slop;
    private float x,y,maxDistance;
    private long start;
    private boolean eligible;
    public TapClassifier(float slop){this.slop=slop;}
    public void begin(float x,float y,long time){this.x=x;this.y=y;start=time;maxDistance=0;eligible=true;}
    public void move(float x,float y){maxDistance=Math.max(maxDistance,(float)Math.hypot(x-this.x,y-this.y));}
    public void cancel(){eligible=false;}
    public boolean finish(float x,float y,long time){
        move(x,y);boolean tap=eligible&&time>=start&&time-start<=350&&maxDistance<=slop;
        eligible=false;return tap;
    }
}
