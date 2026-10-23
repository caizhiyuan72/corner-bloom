package dev.corner.bloom;

public final class OverlayRecoveryTest {
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static final class Windows implements OverlayRecovery.Host {
        boolean permitted=true,unlocked=true,busy,healthy=true,accept=true;
        int attempts,hidden,reattachments;
        public boolean permitted(){return permitted;}
        public boolean unlocked(){return unlocked;}
        public boolean busy(){return busy;}
        public boolean healthy(){return healthy;}
        public void hide(){hidden++;healthy=false;busy=false;}
        public boolean restore(boolean reattach){attempts++;if(reattach)reattachments++;healthy=accept;return accept;}
    }
    public static void main(String[] args){
        Windows windows=new Windows();OverlayRecovery recovery=new OverlayRecovery();
        for(int i=0;i<100;i++)check(recovery.check(i*5000,windows)==OverlayRecovery.State.READY,"healthy window stays ready");
        check(windows.attempts==0,"periodic checks must not raise healthy windows over other apps");

        windows.unlocked=false;
        check(recovery.check(0,windows)==OverlayRecovery.State.HIDDEN,"hide on screen off");
        check(windows.attempts==0,"never mount on lock screen");
        // USER_PRESENT can arrive before keyguard changes, or be missing entirely.
        recovery.request(false);recovery.check(250,windows);
        windows.unlocked=true;
        check(recovery.check(2250,windows)==OverlayRecovery.State.READY&&windows.attempts==1,"later check restores after delayed unlock without another broadcast");

        windows.busy=true;recovery.request(true);
        check(recovery.check(3000,windows)==OverlayRecovery.State.BUSY&&windows.reattachments==0,"manual repair waits for a physical press or animation");
        windows.unlocked=false;recovery.check(3100,windows);
        check(windows.reattachments==0,"deferred repair cannot raise windows on lockscreen");
        windows.unlocked=true;
        check(recovery.check(5100,windows)==OverlayRecovery.State.READY&&windows.reattachments==1,"deferred manual repair survives lock/unlock");
        recovery.check(10100,windows);check(windows.reattachments==1,"manual repair happens once");

        windows.healthy=false;windows.accept=false;int baseline=windows.attempts;
        check(recovery.check(11000,windows)==OverlayRecovery.State.RETRY,"failed attachment is not ready");
        recovery.check(11999,windows);check(windows.attempts==baseline+1,"first retry respects backoff");
        recovery.check(12000,windows);recovery.check(16999,windows);
        check(windows.attempts==baseline+2,"second retry respects longer backoff");
        check(recovery.check(17000,windows)==OverlayRecovery.State.FAILED,"three failures require user action or a lifecycle change");
        for(int i=0;i<100;i++)recovery.check(20000+i*5000,windows);
        check(windows.attempts==baseline+3,"failed windows do not enter an endless add/remove loop");
        windows.accept=true;recovery.request(true);
        check(recovery.check(900000,windows)==OverlayRecovery.State.READY,"manual repair can retry after failure");
        windows.permitted=false;recovery.request(true);baseline=windows.attempts;
        check(recovery.check(900001,windows)==OverlayRecovery.State.PERMISSION&&windows.attempts==baseline,"revoked permission never causes an attachment attempt");
        System.out.println("Overlay recovery: delayed unlock, deferred manual repair, healthy windows, bounded failure retries and permission loss passed.");
    }
}
