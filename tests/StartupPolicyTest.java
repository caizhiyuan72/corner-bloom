import dev.corner.bloom.StartupPolicy;

public final class StartupPolicyTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        String boot="android.intent.action.BOOT_COMPLETED";
        check(StartupPolicy.shouldStart(boot,true,true),"authorized boot restore rejected");
        check(!StartupPolicy.shouldStart(boot,false,true),"disabled autostart still runs");
        check(!StartupPolicy.shouldStart(boot,true,false),"missing overlay permission ignored");
        check(!StartupPolicy.shouldStart(boot,false,false),"disabled and missing permission still runs");
        for(String action:new String[]{null,"","android.intent.action.TIME_SET","android.intent.action.LOCKED_BOOT_COMPLETED"})
            check(!StartupPolicy.shouldStart(action,true,true),"unrelated or pre-unlock action runs");
        System.out.println("PASS: opt-in boot restore only with overlay permission; disabled, unrelated and pre-unlock events ignored");
    }
}
