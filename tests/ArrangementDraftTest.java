package dev.corner.bloom;

import java.util.Arrays;

public final class ArrangementDraftTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static boolean near(float a,float b){return Math.abs(a-b)<.01f;}
    public static void main(String[] args){
        for(float density:new float[]{1,2.625f,3,4}){
            ArrangementDraft d=new ArrangementDraft(411,914,16,88,0,36,15);
            EntryPlacement p=d.bounds();check(near(p.right,411)&&near(p.bottom,878),"initial handle position");
            // Dragging a scaled preview and saving must map to the exact real bounds.
            d.place(p.right-74,p.bottom-133);p=d.bounds();
            check(d.right==74&&d.bottom==169,"drag yields right and bottom offsets");
            check(near(p.left*density,(411-74-16)*density)&&near(p.top*density,(914-169-88)*density),"preview dp resolves to physical overlay pixels");
            d.place(-100,-100);p=d.bounds();
            check(near(p.right,175)&&near(p.bottom,300)&&p.left>=0&&p.top>=0,"drag clamps to space needed by the launcher");
            d.place(10000,10000);p=d.bounds();
            check(d.right==0&&d.bottom==24&&p.right<=411&&p.bottom<=914-24,"cannot drag outside screen or onto bottom system area");
            for(int from=0;from<15;from++)for(int to=0;to<15;to++)d.swap(from,to);
            int[] sorted=d.order.clone();Arrays.sort(sorted);
            for(int i=0;i<15;i++)check(sorted[i]==i,"drag swapping cannot duplicate or lose an app");
            int[] before=d.order.clone();d.resetPosition();
            check(d.right==0&&d.bottom==36&&Arrays.equals(before,d.order),"reset entry preserves chosen app order");
            d.swap(-1,0);d.swap(0,99);check(Arrays.equals(before,d.order),"invalid hover target does not change app order");
        }
        ArrangementDraft rotated=new ArrangementDraft(800,360,16,88,180,260,3);
        check(rotated.bottom==60&&near(rotated.bounds().bottom,300),"old portrait offsets normalize in landscape");
        ArrangementDraft small=new ArrangementDraft(150,200,100,180,999,999,1);
        EntryPlacement b=small.bounds();check(b.left>=0&&b.top>=0&&b.right<=150&&b.bottom<=176,"small-screen bounds stay visible");
        System.out.println("Editor draft: scaled preview mapping, bounds, density, app permutation, position-only reset and rotation passed.");
    }
}
