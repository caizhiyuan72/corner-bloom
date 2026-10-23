package dev.corner.bloom;

/** Pure geometry, shared by preview and overlay. Coordinates are density-independent. */
public final class FanGeometry {
    public static final class Point {
        public final float x, y, rotation, delay;
        Point(float x, float y, float rotation, float delay) {
            this.x=x; this.y=y; this.rotation=rotation; this.delay=delay;
        }
    }
    public static float clamp(float n, float lo, float hi) { return Math.max(lo,Math.min(hi,n)); }
    public static Point[] layout(int count, float width, float height, float ox, float oy, float requestedScale) {
        count=Math.max(0,Math.min(15,count));
        Point[] out=new Point[count];
        if(count==0) return out;
        // Three asymmetric arcs: 3/4/5 for twelve apps, 4/5/6 for fifteen.
        int inner=count<=3?count:count<=6?2:count<=9?3:count<=12?3:4;
        int middle=count<=3?0:count<=6?count-inner:count<=9?3:count<=12?4:5;
        int outer=count-inner-middle;
        float scale=Math.min(requestedScale,Math.min((ox-31f)/277f,(oy-35f)/277f));
        scale=clamp(scale,.38f,1.35f);
        int index=0;
        int[] counts={inner,middle,outer};
        for(int ring=0;ring<3;ring++) {
            int n=counts[ring];
            for(int j=0;j<n;j++) {
                float angle=n==1?47f:12f+(74f*j/(n-1));
                angle+=ring==1?-2f:ring==2?1f:0f;
                float radius=(132f+70f*ring)*scale;
                // Fixed offsets feel scattered without reshuffling between invocations.
                float jitter=((index*17)%9-4)*1.1f;
                double rad=Math.toRadians(angle);
                float x=ox-(float)Math.cos(rad)*(radius+jitter);
                float y=oy-(float)Math.sin(rad)*(radius+jitter);
                out[index]=new Point(clamp(x,28,width-28),clamp(y,28,height-28),
                        ((index*19)%23-11)*.47f,ring*.045f+j*.012f);
                index++;
            }
        }
        return out;
    }
    public static int hit(float x,float y,float[] xs,float[] ys,int count,float radius,int previous) {
        if(previous>=0 && previous<count && Math.hypot(x-xs[previous],y-ys[previous])<radius*1.16f) return previous;
        int best=-1; double distance=radius;
        for(int i=0;i<count;i++) {
            double d=Math.hypot(x-xs[i],y-ys[i]);
            if(d<distance){best=i;distance=d;}
        }
        return best;
    }
}
