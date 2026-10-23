package dev.corner.bloom;

/** Preview coordinates follow app identity, so exchanging slots never teleports other icons. */
final class EditorMotion {
    final float[] x,y,size;
    private final float[] fromX,fromY,fromSize,toX,toY;
    EditorMotion(int count){
        x=new float[count];y=new float[count];size=new float[count];
        fromX=new float[count];fromY=new float[count];fromSize=new float[count];
        toX=new float[count];toY=new float[count];
    }
    void snap(int[] order,FanGeometry.Point[] points){
        for(int slot=0;slot<order.length;slot++)place(order[slot],points[slot].x,points[slot].y,1);
    }
    void place(int item,float px,float py,float scale){x[item]=px;y[item]=py;size[item]=scale;}
    boolean retarget(int[] order,FanGeometry.Point[] points){
        boolean moving=false;
        for(int slot=0;slot<order.length;slot++){
            int item=order[slot];fromX[item]=x[item];fromY[item]=y[item];fromSize[item]=size[item];
            toX[item]=points[slot].x;toY[item]=points[slot].y;
            moving|=Math.abs(x[item]-toX[item])>.01f||Math.abs(y[item]-toY[item])>.01f||Math.abs(size[item]-1)>.001f;
        }
        return moving;
    }
    void advance(float fraction){
        float t=Math.max(0,Math.min(1,fraction));float eased=1-(1-t)*(1-t)*(1-t);
        for(int item=0;item<x.length;item++){
            x[item]=fromX[item]+(toX[item]-fromX[item])*eased;
            y[item]=fromY[item]+(toY[item]-fromY[item])*eased;
            size[item]=fromSize[item]+(1-fromSize[item])*eased;
        }
    }
}
