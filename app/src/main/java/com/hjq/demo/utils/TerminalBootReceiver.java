package com.hjq.demo.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.hjq.demo.ui.activity.SplashActivity;

/** Launches the payment app after boot or an in-place APK upgrade. */
public final class TerminalBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        android.os.UserManager users = (android.os.UserManager) context.getSystemService(Context.USER_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= 24 && users != null && !users.isUserUnlocked()) return;
        PendingResult pending = goAsync();
        TerminalLockdown.restoreOnStartup(context, () -> {
        try {
            Intent launch = new Intent(context, SplashActivity.class)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            context.startActivity(launch);
        } catch (Exception e) {
            android.util.Log.e("TerminalBoot", "Startup error on boot", e);
        } finally {
            pending.finish();
        }
        });
    }
}
