package dev.corner.bloom;

/** One layout and icon-size calculation for the overlay and all in-app previews. Units: dp. */
public final class LauncherGeometry {
    public static final int GRID=0,ARC=1,CLASSIC=2;
    public static final class Layout {
        public final FanGeometry.Point[] points;
        public final float iconSize,hitRadius;
        Layout(FanGeometry.Point[] points,float iconSize,float hitRadius){this.points=points;this.iconSize=iconSize;this.hitRadius=hitRadius;}
    }
    public static int style(int value){return value>=GRID&&value<=CLASSIC?value:GRID;}
    public static Layout layout(int count,float width,float height,float ox,float oy,float spread,int percent,int style){
        count=Math.max(0,Math.min(15,count));style=style(style);
        float requested=47*FanGeometry.clamp(percent,60,140)/100f;
        spread=FanGeometry.clamp(spread,.8f,1.15f);
        if(count==0)return new Layout(new FanGeometry.Point[0],requested,requested*.6f);
        if(style==GRID){
            int columns=count<=3?count:count==4?2:width>height*1.25f?Math.min(5,count):count>=10?4:3;
            int rows=(count+columns-1)/columns;
            float right=Math.min(width-12,ox-8),bottom=Math.min(height-16,oy-64);
            // Fit once at the standard spread. Moving the spread slider never resizes icons.
            float standardSpacing=1.42f;
            float size=Math.min(requested,Math.min((right-12)/(1.23f+(columns-1)*standardSpacing),
                    (bottom-12)/(1.23f+(rows-1)*standardSpacing)));
            size=Math.max(1,size);
            float maximumStep=Float.MAX_VALUE;
            if(columns>1)maximumStep=Math.min(maximumStep,(right-12-size*1.23f)/(columns-1));
            if(rows>1)maximumStep=Math.min(maximumStep,(bottom-12-size*1.23f)/(rows-1));
            float step=Math.min(size*1.42f*spread,maximumStep);
            float left=right-size*.615f-(columns-1)*step,top=bottom-size*.615f-(rows-1)*step;
            FanGeometry.Point[] points=new FanGeometry.Point[count];
            for(int i=0;i<count;i++)points[i]=new FanGeometry.Point(left+(i%columns)*step,top+(i/columns)*step,0,0);
            return new Layout(points,size,Math.min(step*.48f,Math.max(20,size*.65f)));
        }
        float scale=Math.max(1,requested/47);
        FanGeometry.Point[] reference=FanGeometry.layout(count,width,height,ox,oy,scale,style==ARC);
        float limit=requested;
        for(int i=0;i<count;i++)for(int j=0;j<i;j++){
            float dx=Math.abs(reference[i].x-reference[j].x),dy=Math.abs(reference[i].y-reference[j].y);
            limit=Math.min(limit,(Math.max(dx,dy)-5)/(style==CLASSIC?1.38f:1.115f));
        }
        float[] base=bounds(reference),extent;
        limit=Math.min(limit,Math.min((width-16-(base[1]-base[0]))/1.38f,(height-16-(base[3]-base[2]))/1.38f));
        float size=Math.max(1,limit),margin=size*.69f+8;
        FanGeometry.Point[] points=FanGeometry.layout(count,width,height,ox,oy,spread*scale,style==ARC);
        extent=bounds(points);
        // At the screen edge, stop expanding the positions rather than reducing icon size.
        float blend=1,baseW=base[1]-base[0],baseH=base[3]-base[2];
        float growthW=extent[1]-extent[0]-baseW,growthH=extent[3]-extent[2]-baseH;
        if(growthW>.001f)blend=Math.min(blend,(width-2*margin-baseW)/growthW);
        if(growthH>.001f)blend=Math.min(blend,(height-2*margin-baseH)/growthH);
        float minimumDistance=size*(style==CLASSIC?1.38f:1.115f);
        for(int i=0;i<count;i++)for(int j=0;j<i;j++){
            float before=Math.max(Math.abs(reference[i].x-reference[j].x),Math.abs(reference[i].y-reference[j].y));
            float after=Math.max(Math.abs(points[i].x-points[j].x),Math.abs(points[i].y-points[j].y));
            if(after<minimumDistance&&before>after+.001f)
                blend=Math.min(blend,(before-minimumDistance)/(before-after));
        }
        blend=FanGeometry.clamp(blend,0,1);
        for(int i=0;i<count;i++){
            FanGeometry.Point p=points[i],start=reference[i];
            points[i]=new FanGeometry.Point(start.x+(p.x-start.x)*blend,start.y+(p.y-start.y)*blend,p.rotation,p.delay);
        }
        extent=bounds(points);
        float minX=extent[0],maxX=extent[1],minY=extent[2],maxY=extent[3];
        float shiftX=minX<margin?margin-minX:maxX>width-margin?width-margin-maxX:0;
        float shiftY=minY<margin?margin-minY:maxY>height-margin?height-margin-maxY:0;
        for(int i=0;i<count;i++){
            FanGeometry.Point p=points[i];points[i]=new FanGeometry.Point(p.x+shiftX,p.y+shiftY,p.rotation,p.delay);
        }
        float closest=10000;
        for(int i=0;i<count;i++)for(int j=0;j<i;j++)
            closest=Math.min(closest,(float)Math.hypot(points[i].x-points[j].x,points[i].y-points[j].y));
        return new Layout(points,size,Math.min(closest*.48f,Math.max(20,size*.65f)));
    }
    private static float[] bounds(FanGeometry.Point[] points){
        float left=Float.MAX_VALUE,right=-Float.MAX_VALUE,top=Float.MAX_VALUE,bottom=-Float.MAX_VALUE;
        for(FanGeometry.Point p:points){left=Math.min(left,p.x);right=Math.max(right,p.x);top=Math.min(top,p.y);bottom=Math.max(bottom,p.y);}
        return new float[]{left,right,top,bottom};
    }
}
