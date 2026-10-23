package dev.corner.bloom;

public final class PageMotionTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        check(PageMotion.ease(0)==0&&PageMotion.ease(1)==1,"exact endpoints");
        check(Math.abs(PageMotion.ease(.2f)-.48f)<.02f,"reference Miuix pacing");
        for(int hz:new int[]{60,90,120,144}){
            float last=0;
            for(int frame=0;frame<=hz;frame++){
                float p=PageMotion.ease(frame/(float)hz);
                check(p>=last&&p<=1,"no bounce or overshoot");last=p;
                for(int width:new int[]{320,411,1080,1440}){
                    float x=PageMotion.frontX(width,p,false);
                    check(x>=0&&x<=width&&x==Math.round(x),"opaque page tracks device pixels");
                    check(PageMotion.frontX(width,p,true)==-x,"RTL mirrors slide");
                    check(Math.abs(PageMotion.backX(width,p,false)+width*p/4)<.001f,"quarter-width parallax");
                    check(PageMotion.backAlpha(p)>=.9f,"covered page only lightly fades");
                }
            }
        }
        System.out.println("Page motion: Miuix pacing, pixel-aligned slide, quarter parallax, RTL and monotone 60/90/120/144 Hz passed.");
    }
}
