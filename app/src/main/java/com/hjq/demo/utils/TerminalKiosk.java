package com.hjq.demo.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

/**
 * Android system lock management.
 * On Anfu POS hardware, lockdown of navigation bar, status bar, and recent apps
 * is handled cleanly at the vendor OS level via TerminalLockdown (ISystemBinderService).
 * Android's native startLockTask() is only called if the app is an enrolled Device Owner;
 * it must NEVER be called as an un-permitted fallback because that triggers Android's
 * Screen Pinning dialog ("App is pinned... Touch & hold Back and Overview to unpin").
 */
public final class TerminalKiosk {
    private static volatile String status = "Not checked";
    private static java.lang.ref.WeakReference<Activity> lockStarter = new java.lang.ref.WeakReference<>(null);
    private TerminalKiosk() { }
    public static String status() { return status; }

    public static void enter(Activity activity) {
        if (Build.VERSION.SDK_INT < 23 || activity.isFinishing()) return;
        try {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            // If the activity was pinned (e.g. from previous session or accidental pinning), unpin immediately!
            if (manager != null && manager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_PINNED) {
                try {
                    activity.stopLockTask();
                } catch (Exception ignore) {}
            }

            DevicePolicyManager policy = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (policy == null) {
                status = "Device management unavailable";
                return;
            }
            String pkg = activity.getPackageName();
            if (policy.isDeviceOwnerApp(pkg)) {
                ComponentName admin = new ComponentName(activity, TerminalAdminReceiver.class);
                policy.setLockTaskPackages(admin, new String[]{pkg});
                if (Build.VERSION.SDK_INT >= 28) {
                    policy.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
                }
                if (policy.isLockTaskPermitted(pkg)) {
                    KeyguardManager keyguard = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
                    if (keyguard != null && keyguard.isKeyguardLocked()) {
                        status = "Waiting for device unlock";
                        return;
                    }
                    if (manager != null && manager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                        activity.startLockTask();
                        lockStarter = new java.lang.ref.WeakReference<>(activity);
                    }
                    status = manager != null && manager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_LOCKED
                            ? "Locked (Device Owner Kiosk)" : "Lock request pending";
                    return;
                }
            }

            // Not a provisioned Device Owner:
            // Do NOT call activity.startLockTask()! That triggers Android's "App is pinned" dialog.
            // On Anfu terminals, navigation bar and buttons are locked via Anfu vendor APIs in TerminalLockdown.
            status = "Vendor firmware lock active (Anfu SDK)";
        } catch (RuntimeException e) {
            status = "Not applied (" + e.getClass().getSimpleName() + ")";
            android.util.Log.w("TerminalKiosk", status, e);
        }
    }

    /** Called only from the password-verified administrator Settings action. */
    public static void leaveForSettings(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager != null && manager.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                DevicePolicyManager policy = (DevicePolicyManager) activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
                if (policy != null && policy.isDeviceOwnerApp(activity.getPackageName())) {
                    policy.setLockTaskPackages(new ComponentName(activity, TerminalAdminReceiver.class), new String[0]);
                } else {
                    Activity starter = lockStarter.get();
                    if (starter != null && !starter.isDestroyed()) {
                        starter.stopLockTask();
                    } else {
                        activity.stopLockTask();
                    }
                }
                status = "Administrator Settings session";
            }
        } catch (Exception ignore) {}
    }
}
