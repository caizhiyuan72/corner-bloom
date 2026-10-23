package dev.corner.bloom;

/** Unsaved editor state. Leaving the editor does not change stored preferences. */
final class ArrangementDraft {
    final float screenW,screenH,width,height;
    final int[] order;
    int right,bottom;
    ArrangementDraft(float screenW,float screenH,float width,float height,int right,int bottom,int count){
        this.screenW=screenW;this.screenH=screenH;this.width=width;this.height=height;
        this.right=right;this.bottom=bottom;order=new int[count];
        for(int i=0;i<count;i++)order[i]=i;
        place(screenW-right,screenH-bottom);
    }
    EntryPlacement bounds(){return EntryPlacement.resolve(screenW,screenH,width,height,right,bottom);}
    void place(float requestedRight,float requestedBottom){
        EntryPlacement p=EntryPlacement.resolve(screenW,screenH,width,height,screenW-requestedRight,screenH-requestedBottom);
        right=Math.round(screenW-p.right);bottom=Math.round(screenH-p.bottom);
    }
    void swap(int from,int to){
        if(from<0||to<0||from>=order.length||to>=order.length)return;
        int value=order[from];order[from]=order[to];order[to]=value;
    }
    void resetPosition(){place(screenW,screenH-36);}
}
