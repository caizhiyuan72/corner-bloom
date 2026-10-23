package dev.corner.bloom;

public final class OverlayMotion {
    /** A released/cancelled gesture must always approach zero, never rebound outward. */
    public static float close(float progress,float dt){
        return Math.max(0,progress)*(float)Math.exp(-24*Math.max(0,dt));
    }
}
