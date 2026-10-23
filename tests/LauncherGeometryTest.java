package dev.corner.bloom;

public final class LauncherGeometryTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    public static void main(String[] args){
        int scenarios=0;
        for(float[] screen:new float[][]{{320,640},{411,914},{240,320},{914,411},{640,320}})
        for(int count=1;count<=15;count++)for(int style=0;style<=2;style++)
        for(int percent:new int[]{60,100,140})for(float spread:new float[]{.8f,1,1.15f})
        for(boolean moved:new boolean[]{false,true}){
            EntryPlacement b=EntryPlacement.resolve(screen[0],screen[1],16,88,moved?200:0,moved?500:36);
            float ox=b.right-8,oy=b.bottom-8;
            LauncherGeometry.Layout layout=LauncherGeometry.layout(count,screen[0],screen[1],ox,oy,spread,percent,style);
            LauncherGeometry.Layout reference=LauncherGeometry.layout(count,screen[0],screen[1],ox,oy,.8f,percent,style);
            check(Math.abs(layout.iconSize-reference.iconSize)<.001f,"spread must never change icon size in any style");
            float[] xs=new float[count],ys=new float[count];float half=layout.iconSize*.615f;
            check(layout.points.length==count&&layout.iconSize>0&&layout.iconSize<=47*percent/100f+.01f,"bounded size and count");
            for(int i=0;i<count;i++){
                FanGeometry.Point p=layout.points[i];xs[i]=p.x;ys[i]=p.y;
                check(p.x-half>=0&&p.x+half<=screen[0]+.1&&p.y-half>=0&&p.y+half<=screen[1]+.1,"focused icon stays on screen");
                check(Math.hypot(p.x-ox,p.y-oy)>56,"every center remains outside cancellation zone");
                if(style!=LauncherGeometry.CLASSIC)check(p.rotation==0&&p.delay==0,"orderly icons remain upright and synchronized");
                for(int j=0;j<i;j++)check(Math.max(Math.abs(p.x-xs[j]),Math.abs(p.y-ys[j]))>=layout.iconSize*1.115f-.01f,"selected icon does not overlap its neighbors");
            }
            for(int i=0;i<count;i++)check(FanGeometry.hit(xs[i],ys[i],xs,ys,count,layout.hitRadius,-1)==i,"rendered center selects correct app");
            if(style==LauncherGeometry.GRID&&count>=6){
                int columns=screen[0]>screen[1]*1.25f?Math.min(5,count):count>=10?4:3;
                check(Math.abs(ys[0]-ys[1])<.01f&&Math.abs(xs[0]-xs[columns])<.01f,"grid rows and columns line up");
            }
            scenarios++;
        }
        LauncherGeometry.Layout small=LauncherGeometry.layout(15,411,914,403,870,1,60,0),large=LauncherGeometry.layout(15,411,914,403,870,1,140,0);
        check(large.iconSize>small.iconSize*2,"icon scale changes actual rendered size");
        for(int style=0;style<=2;style++){
            LauncherGeometry.Layout tight=LauncherGeometry.layout(15,411,914,403,870,.8f,100,style);
            LauncherGeometry.Layout wide=LauncherGeometry.layout(15,411,914,403,870,1.15f,100,style);
            boolean moved=false;for(int i=0;i<15;i++)moved|=Math.hypot(tight.points[i].x-wide.points[i].x,tight.points[i].y-wide.points[i].y)>1;
            check(moved,"spread still changes positions when space is available");
            check(tight.iconSize==wide.iconSize,"spacing and icon scale are independent");
        }
        check(LauncherGeometry.layout(0,411,914,403,870,1,100,0).points.length==0,"empty selection");
        check(LauncherGeometry.layout(99,411,914,403,870,1,100,0).points.length==15,"app count capped");
        check(LauncherGeometry.style(-20)==LauncherGeometry.GRID,"invalid saved style falls back safely");
        System.out.println("Launcher geometry: "+scenarios+" layouts, size scaling, orderly rows/arcs, focused bounds, cancellation and selection passed.");
    }
}
