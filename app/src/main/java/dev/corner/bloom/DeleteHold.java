package dev.corner.bloom;

/** Owns one physical press. Every queued pulse is invalidated on release/cancel. */
public final class DeleteHold {
    public interface Timer {void later(Runnable action,long delay);}
    public interface Source {boolean valid();boolean erase();}
    private final Timer timer;
    private final long delay,interval;
    private final float slop;
    private int generation;
    private Source source;
    private float x,y;
    private boolean claimed;
    public DeleteHold(Timer timer,long delay,long interval,float slop){this.timer=timer;this.delay=delay;this.interval=interval;this.slop=slop;}
    public void down(float x,float y,Source source){
        cancel();claimed=false;this.x=x;this.y=y;this.source=source;
        if(source==null)return;
        final int request=generation;
        timer.later(()->pulse(request),delay);
    }
    public void move(float x,float y){
        if(Math.hypot(x-this.x,y-this.y)>slop)cancel();
    }
    private void pulse(int request){
        if(request!=generation||source==null)return;
        try{
            if(!source.valid()){cancel();return;}
            // Claim before sending: release must never turn this hold into a tap.
            claimed=true;
            if(!source.erase()){cancel();return;}
        }catch(RuntimeException e){cancel();return;}
        timer.later(()->pulse(request),interval);
    }
    public void cancel(){generation++;source=null;}
    public boolean claimed(){return claimed;}
    public boolean release(){boolean result=claimed;cancel();claimed=false;return result;}
}
