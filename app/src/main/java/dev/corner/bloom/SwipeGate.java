package dev.corner.bloom;

/** Confirms direction and distance before the window expands or any animation starts. */
public final class SwipeGate {
    private final float threshold;
    private boolean active;
    public SwipeGate(float threshold){this.threshold=threshold;}
    public void reset(){active=false;}
    public boolean move(float horizontal,float upward){
        if(!active&&upward>=threshold&&upward>=Math.abs(horizontal)*.7f)active=true;
        return active;
    }
}
