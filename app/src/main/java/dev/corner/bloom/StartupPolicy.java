package dev.corner.bloom;

public final class StartupPolicy {
    public static boolean shouldStart(String action,boolean enabled,boolean overlayAllowed){
        return enabled&&overlayAllowed&&"android.intent.action.BOOT_COMPLETED".equals(action);
    }
}
