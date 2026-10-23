import dev.corner.bloom.SwipeGate;

public final class SwipeGateTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        SwipeGate gate=new SwipeGate(20);
        check(!gate.move(0,0),"tap activates");
        for(int i=0;i<120;i++)check(!gate.move(0,0),"stationary hold activates");
        float[][] jitter={{2,1},{-3,-2},{4,5},{0,2},{-2,0}};
        for(float[] p:jitter)check(!gate.move(p[0],p[1]),"finger jitter activates");
        gate.reset();check(!gate.move(90,12),"horizontal swipe activates");
        gate.reset();check(!gate.move(-90,22),"mostly horizontal swipe activates");
        gate.reset();check(!gate.move(0,-80),"downward swipe activates");
        gate.reset();check(!gate.move(0,19),"sub-threshold movement activates");
        check(gate.move(0,24),"upward swipe fails");
        check(gate.move(-120,5),"selection sweep loses activation");
        gate.reset();check(!gate.move(0,0),"next tap inherits activation");
        check(gate.move(-35,35),"diagonal upward swipe fails");
        gate.reset();check(!gate.move(0,0),"cancel fails to reset");
        System.out.println("PASS: tap, hold, jitter, horizontal/downward rejection, upward/diagonal activation, selection continuation, reset");
    }
}
