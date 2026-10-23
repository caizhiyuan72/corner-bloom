import dev.corner.bloom.FanGeometry;

public final class GeometryTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int scenarios=0;
        for(int n=1;n<=15;n++)for(float width:new float[]{320,360,411,480,800})for(float scale:new float[]{.8f,1f,1.15f}){
            FanGeometry.Point[] points=FanGeometry.layout(n,width,800,width-16,740,scale);
            check(points.length==n,"count");
            float[] xs=new float[n],ys=new float[n];
            for(int i=0;i<n;i++){
                FanGeometry.Point p=points[i];xs[i]=p.x;ys[i]=p.y;
                check(p.x>=28&&p.x<=width-28&&p.y>=28&&p.y<=772,"off-screen icon");
                FanGeometry.Point repeat=FanGeometry.layout(n,width,800,width-16,740,scale)[i];
                check(p.x==repeat.x&&p.y==repeat.y,"unstable application positions");
                for(int j=0;j<i;j++)check(Math.hypot(p.x-xs[j],p.y-ys[j])>29,"overlapping icon centers");
            }
            for(int i=0;i<n;i++)check(FanGeometry.hit(xs[i],ys[i],xs,ys,n,25,-1)==i,"unreachable target");
            check(FanGeometry.hit(width-16,740,xs,ys,n,30,-1)==-1,"cancel area selects app");
            scenarios++;
        }
        check(FanGeometry.layout(100,411,800,395,740,1).length==15,"count cap");
        check(FanGeometry.layout(0,411,800,395,740,1).length==0,"empty selection");
        float[] x={10,50},y={10,10};
        check(FanGeometry.hit(28,10,x,y,2,20,0)==0,"selection hysteresis");
        System.out.println("PASS: "+scenarios+" layout/selection scenarios, count limits, cancel zone and hysteresis");
    }
}
