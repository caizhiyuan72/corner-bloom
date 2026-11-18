import dev.corner.bloom.TapClassifier;

public final class TapClassifierTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        TapClassifier t=new TapClassifier(8,500);
        t.begin(100,200,1000);check(t.finish(100,200,1080),"short tap rejected");
        check(!t.finish(100,200,1090),"same tap replayed twice");
        t.begin(100,200,1200);t.move(104,201);check(t.finish(102,202,1350),"tap jitter rejected");
        check(t.downX()==100&&t.downY()==200,"release jitter changes target key");
        t.begin(100,200,1400);check(t.finish(100,200,1899),"slower press below system long-press timeout lost");
        t.begin(100,200,1400);t.move(100,160);check(!t.finish(100,200,1500),"upward-and-back swipe replayed as tap");
        t.begin(100,200,1600);t.move(130,200);check(!t.finish(130,200,1650),"horizontal swipe replayed");
        t.begin(100,200,1700);check(!t.finish(100,200,2200),"long press replayed");
        t.begin(100,200,2300);t.cancel();check(!t.finish(100,200,2400),"cancelled/multitouch gesture replayed");
        t.begin(100,200,2500);check(t.finish(100,200,2550),"next independent tap rejected");
        t.begin(100,200,2700);check(!t.finish(100,200,2600),"invalid timestamp accepted");
        TapClassifier extended=new TapClassifier(8,800);
        extended.begin(10,20,1000);check(extended.finish(10,20,1799),"system long-press preference ignored");
        extended.begin(10,20,2000);check(!extended.finish(10,20,2800),"actual long press replayed");
        System.out.println("PASS: tap/jitter accepted once; swipes, outward-and-back paths, holds, cancellations and invalid events rejected");
    }
}
