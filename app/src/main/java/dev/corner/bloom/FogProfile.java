package dev.corner.bloom;

/** Radial opacity with a flat center and a smooth, fully transparent outer edge. */
public final class FogProfile {
    public static float opacity(float radius){
        float t=Math.max(0,Math.min(1,(radius-.22f)/.78f));
        return 1-t*t*(3-2*t);
    }
    public static float edgeOpacity(float distance,float feather){
        float t=Math.max(0,Math.min(1,distance/Math.max(1,feather)));
        return t*t*(3-2*t);
    }
}
