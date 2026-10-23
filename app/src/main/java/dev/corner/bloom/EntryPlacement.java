package dev.corner.bloom;

/** Shared screen coordinates in dp for the real handle and its in-app editor. */
public final class EntryPlacement {
    public final float left,top,right,bottom;
    private EntryPlacement(float left,float top,float right,float bottom){
        this.left=left;this.top=top;this.right=right;this.bottom=bottom;
    }
    public static EntryPlacement resolve(float screenW,float screenH,float width,float height,float rightInset,float bottomInset){
        float right=FanGeometry.clamp(screenW-rightInset,Math.min(175,screenW),screenW);
        float maxBottom=Math.max(0,screenH-24);
        float bottom=FanGeometry.clamp(screenH-bottomInset,Math.min(300,maxBottom),maxBottom);
        return new EntryPlacement(Math.max(0,right-width),Math.max(0,bottom-height),right,bottom);
    }
    public float width(){return right-left;}
    public float height(){return bottom-top;}
}
