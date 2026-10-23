import dev.corner.bloom.OverlayLayout;

public final class OverlayLayoutTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(float density:new float[]{1,2,2.75f,3}){
            float width=16*density,height=88*density;
            check(!OverlayLayout.compactReady(411*density,890*density,width,height),"full-screen frame draws compact handle");
            check(!OverlayLayout.compactReady(width,890*density,width,height),"half-applied resize draws handle");
            check(!OverlayLayout.compactReady(411*density,height,width,height),"stale horizontal frame draws handle");
            check(OverlayLayout.compactReady(Math.round(width),Math.round(height),width,height),"compact frame rejected");
        }
        System.out.println("PASS: compact handle never draws in stale full-screen/partial resize frames; ready after compact layout");
    }
}
