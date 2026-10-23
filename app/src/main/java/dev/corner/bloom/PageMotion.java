package dev.corner.bloom;

/** Native View implementation of the navigation geometry used by XGesture's MiuixDefault.
 * Reference: compose-miuix-ui/miuix v0.9.4, NavTransitions and NavProgrammaticEasing.
 * Pages stay opaque and at their original size; one progress value drives both layers.
 */
final class PageMotion {
    static final int DURATION_MS=500;
    private static final double OMEGA=2*Math.PI/.8;
    private static final double DECAY=.95*OMEGA;
    private static final double FREQUENCY=OMEGA*Math.sqrt(1-.95*.95);
    static float ease(float fraction){
        if(fraction<=0)return 0;if(fraction>=1)return 1;
        double t=fraction;
        return (float)(1-Math.exp(-DECAY*t)*(Math.cos(FREQUENCY*t)+DECAY/FREQUENCY*Math.sin(FREQUENCY*t)));
    }
    static float frontX(float width,float progress,boolean rtl){return Math.round(width*(1-progress))*(rtl?-1:1);}
    static float backX(float width,float progress,boolean rtl){return width*.25f*progress*(rtl?1:-1);}
    static float backAlpha(float progress){return 1-.1f*progress;}
}
