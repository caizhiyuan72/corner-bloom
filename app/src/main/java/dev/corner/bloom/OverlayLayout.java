package dev.corner.bloom;

/** Guards drawing while Android asynchronously changes the window dimensions. */
public final class OverlayLayout {
    public static boolean compactReady(float width,float height,float targetWidth,float targetHeight){
        return Math.abs(width-targetWidth)<=2&&Math.abs(height-targetHeight)<=2;
    }
}
