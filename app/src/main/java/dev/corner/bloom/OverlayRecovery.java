package dev.corner.bloom;

/** Repairs our own windows only. A healthy window may still be hidden by Android. */
final class OverlayRecovery {
    enum State { READY, BUSY, HIDDEN, RETRY, FAILED, PERMISSION }
    interface Host {
        boolean permitted();
        boolean unlocked();
        boolean busy();
        boolean healthy();
        void hide();
        boolean restore(boolean reattach);
    }
    private int failures;
    private long retryAt;
    private boolean reattach;

    void request(boolean reattach){
        this.reattach|=reattach;failures=0;retryAt=0;
    }
    State check(long now,Host host){
        if(!host.permitted())return State.PERMISSION;
        if(!host.unlocked()){host.hide();return State.HIDDEN;}
        // A repair must not steal a swipe, a delete hold, or a tap replay.
        if(host.busy())return State.BUSY;
        if(!reattach&&host.healthy()){failures=0;retryAt=0;return State.READY;}
        if(failures>=3)return State.FAILED;
        if(now<retryAt)return State.RETRY;
        if(host.restore(reattach)){
            reattach=false;failures=0;retryAt=0;return State.READY;
        }
        failures++;
        retryAt=now+(failures==1?1000:5000);
        return failures>=3?State.FAILED:State.RETRY;
    }
}
