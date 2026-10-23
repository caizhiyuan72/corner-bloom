package dev.corner.bloom;

public final class EditorMotionTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static boolean near(float a,float b){return Math.abs(a-b)<.001f;}
    public static void main(String[] args){
        FanGeometry.Point[] points=FanGeometry.layout(3,411,914,403,870,1);
        int[] order={0,1,2};EditorMotion motion=new EditorMotion(3);motion.snap(order,points);
        check(!motion.retarget(order,points),"resting arrangement starts no animation");
        order=new int[]{2,1,0};check(motion.retarget(order,points),"exchange needs a transition");
        motion.advance(0);check(near(motion.x[0],points[0].x),"retarget starts at rendered position");
        motion.advance(.5f);float midway=motion.x[0];
        check(near(midway,points[0].x+(points[2].x-points[0].x)*.875f),"cubic ease follows elapsed fraction");
        order=new int[]{1,2,0};motion.retarget(order,points);motion.advance(0);
        check(near(motion.x[0],midway),"rapid exchange does not jump at retarget");
        motion.place(1,180,300,1.5f);motion.retarget(order,points);motion.advance(.3f);
        check(motion.size[1]>1&&motion.size[1]<1.5f,"released icon shrinks smoothly toward its slot");
        motion.advance(1);
        for(int slot=0;slot<order.length;slot++){
            int item=order[slot];check(near(motion.x[item],points[slot].x)&&near(motion.y[item],points[slot].y)&&near(motion.size[item],1),"finite transition settles exactly");
        }
        float[] finalX=motion.x.clone();motion.advance(2);
        for(int i=0;i<3;i++)check(near(finalX[i],motion.x[i]),"late frame cannot overshoot");
        for(int hz:new int[]{60,90,120}){
            motion.snap(new int[]{0,1,2},points);motion.retarget(order,points);
            for(int frame=0;frame<=hz;frame++)motion.advance(frame/(float)hz);
            for(int slot=0;slot<3;slot++)check(near(motion.x[order[slot]],points[slot].x),"same result at every frame rate");
        }
        System.out.println("Editor motion: identity, interrupted retarget, release scale, exact settling and 60/90/120 Hz passed.");
    }
}
