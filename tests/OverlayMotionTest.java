import dev.corner.bloom.OverlayMotion;

public final class OverlayMotionTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        for(float start:new float[]{0,.1f,.68f,1,1.17f})for(float hz:new float[]{30,60,90,120,144,240}){
            float p=start;
            for(int i=0;i<hz;i++){
                float next=OverlayMotion.close(p,1/hz);
                check(next<=p&&next>=0,"closing rebounds outward");p=next;
            }
            check(p<.006f,"closing fails to finish");
        }
        System.out.println("PASS: cancellation closes monotonically at 30–240 Hz, including an overshot opening");
    }
}
