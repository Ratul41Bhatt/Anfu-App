package com.hjq.demo.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.hjq.demo.ans8583.TerminalConfig;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.system.ISystemBinderService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** AFSDK V2.1 section 4.14. Return codes remain visible; immersive UI is not a lock. */
public final class TerminalLockdown {
    private static final ExecutorService worker = Executors.newSingleThreadExecutor();
    private static volatile ISdkServiceManager sdkManager;
    private static volatile ISystemBinderService system;
    private static volatile String status = "Waiting for terminal services";
    private static volatile boolean settingsSession = false;
    private TerminalLockdown() { }
    public static String status() { return status + "\nAndroid kiosk: " + TerminalKiosk.status(); }
    public static boolean isSettingsSession() { return settingsSession; }
    public static void onAppResumed() { settingsSession = false; }

    /** Bind even when no Activity exists yet; the SDK can start later than the boot broadcast. */
    public static void restoreOnStartup(Context context, Runnable completed) {
        Context app = context.getApplicationContext();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        class StartupBinding implements android.content.ServiceConnection, Runnable {
            boolean bound, finished;
            final long deadline = android.os.SystemClock.uptimeMillis() + 7000;
            void done() {
                if (finished) return;
                finished = true;
                main.removeCallbacks(this);
                if (bound) try { app.unbindService(this); } catch (RuntimeException ignored) { }
                completed.run();
            }
            @Override public void run() {
                if (finished) return;
                if (android.os.SystemClock.uptimeMillis() >= deadline) { done(); return; }
                if (!bound) try {
                    bound = app.bindService(new Intent("com.szanfu.sdk.service.SDK_SERVICE")
                            .setPackage("com.szanfu.sdk.service"), this, Context.BIND_AUTO_CREATE);
                } catch (RuntimeException e) { android.util.Log.w("TerminalLockdown", "SDK not ready at boot", e); }
                main.postDelayed(this, 500);
            }
            @Override public void onServiceConnected(android.content.ComponentName name, android.os.IBinder binder) {
                if (finished) return;
                configureAllLockdown(app, ISdkServiceManager.Stub.asInterface(binder), () -> main.post(this::done));
            }
            @Override public void onServiceDisconnected(android.content.ComponentName name) { system = null; sdkManager = null; }
        }
        main.post(new StartupBinding());
    }

    public static void connect(Context context, ISdkServiceManager sdk) {
        configureAllLockdown(context, sdk, null);
    }

    public static void configureAllLockdown(Context context, ISdkServiceManager sdk, Runnable onComplete) {
        if (sdk != null) {
            sdkManager = sdk;
        }
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            try {
                if (sdk != null) {
                    system = ISystemBinderService.Stub.asInterface(sdk.getService(ServiceID.SERVICE_ID_SYSTEM));
                } else if (system == null && sdkManager != null) {
                    system = ISystemBinderService.Stub.asInterface(sdkManager.getService(ServiceID.SERVICE_ID_SYSTEM));
                }
                if (system != null) {
                    apply(app);
                }
            } catch (Exception | LinkageError e) {
                status = "Device lock unavailable: " + e.getClass().getSimpleName();
                android.util.Log.e("TerminalLockdown", status, e);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private static void apply(Context app) {
        if (system == null || settingsSession) return;
        String authorization = call(() -> system.grantVendorSystemSdkPermission(app.getPackageName()));
        call(() -> system.grantVendorUserSdkPermission(app.getPackageName()));
        try { system.grantAllDangerousPermissions(app.getPackageName()); } catch (Exception | LinkageError ignore) {}
        String startup = call(() -> system.grantPersistentAlertWindowPermission(app.getPackageName()));
        try { system.grantWriteSettingsPermission(app.getPackageName()); } catch (Exception | LinkageError ignore) {}
        try { system.grantAppPowerManagerPermission(app.getPackageName(), true); } catch (Exception | LinkageError ignore) {}
        try { system.avoidAppLowMemoryKiller(app.getPackageName(), true); } catch (Exception | LinkageError ignore) {}
        String password = call(() -> system.setSettingsPwd(TerminalConfig.get(app).settingsPw()));
        try { system.setSettingsPwdEnabled(true); } catch (Exception | LinkageError ignore) {}
        try { system.setDevOptionsPwd(TerminalConfig.get(app).settingsPw()); } catch (Exception | LinkageError ignore) {}
        try { system.setDevOptionsPwdEnabled(true); } catch (Exception | LinkageError ignore) {}
        try { system.setFileExplorerPwd(TerminalConfig.get(app).settingsPw()); } catch (Exception | LinkageError ignore) {}
        try { system.setFileExplorerPwdEnabled(true); } catch (Exception | LinkageError ignore) {}
        String nav = call(() -> system.setNavBarEnabled(false));
        String bar = call(() -> system.setStatusBarEnabled(false));
        // What Anfu need to answer "why does setNavBarEnabled return 0 but the
        // bar stays": V2.1 section 2.1 - the call only takes effect on a system
        // image built with their customer-specific bar control. A 0 from the
        // service says the service took the request, not that the ROM obeyed.
        status = "Vendor navigation lock: " + nav
                + "\nStatus bar: " + bar + "\nSettings password: " + password
                + "\nSystem SDK authorization: " + authorization
                + "\nStartup authorization: " + startup
                + "\nSystem build: " + android.os.Build.DISPLAY
                + "\nDevice OS (SDK): " + text(() -> system.getDeviceosVersion())
                + "\nSDK service: " + text(() -> system.getAppVersionInfo("com.szanfu.sdk.service"))
                + "\nModel: " + text(() -> system.getRoProductModel())
                + "\nAFSDKInterface (app): 0.0.356";
        android.util.Log.i("TerminalLockdown", status);
    }

    private interface TextOperation { String run() throws android.os.RemoteException; }
    private static String text(TextOperation operation) {
        try {
            String v = operation.run();
            return v == null || v.isEmpty() ? "-" : v.trim();
        } catch (Exception | LinkageError e) {
            return "unavailable (" + e.getClass().getSimpleName() + ")";
        }
    }

    private static String result(int code) { return code == 0 ? "Applied" : "Not applied (" + code + ")"; }
    private interface Operation { int run() throws android.os.RemoteException; }
    private static String call(Operation operation) {
        try { return result(operation.run()); }
        catch (Exception | LinkageError e) { return "Unavailable (" + e.getClass().getSimpleName() + ")"; }
    }

    public static void lockNavBar(ISdkServiceManager sdk) {
        if (sdk != null) {
            sdkManager = sdk;
        }
        lockNavBar();
    }

    public static void lockNavBar() {
        worker.execute(() -> {
            try {
                if (settingsSession) return;
                if (system == null && sdkManager != null) {
                    system = ISystemBinderService.Stub.asInterface(sdkManager.getService(ServiceID.SERVICE_ID_SYSTEM));
                }
                if (system == null) return;
                int nav = system.setNavBarEnabled(false);
                int bar = system.setStatusBarEnabled(false);
                android.util.Log.d("TerminalLockdown", "lockNavBar applied: nav=" + nav + ", bar=" + bar);
            } catch (Exception | LinkageError e) {
                android.util.Log.w("TerminalLockdown", "lockNavBar failed: " + e.getMessage());
            }
        });
    }

    public static void refresh(Context context) {
        settingsSession = false;
        Context app = context.getApplicationContext();
        worker.execute(() -> {
            if (system == null && sdkManager != null) {
                try {
                    system = ISystemBinderService.Stub.asInterface(sdkManager.getService(ServiceID.SERVICE_ID_SYSTEM));
                } catch (Exception | LinkageError ignore) {}
            }
            if (system == null) return;
            try { apply(app); }
            catch (Exception | LinkageError e) { status = "Device lock unavailable: " + e.getClass().getSimpleName(); }
        });
    }

    /** Only called after the administrator has passed the Settings password prompt. */
    public static void openSettings(Activity activity) {
        settingsSession = true;
        worker.execute(() -> {
            try { if (system != null) system.setNavBarEnabled(true); }
            catch (Exception | LinkageError e) { android.util.Log.w("TerminalLockdown", "Settings navigation unavailable"); }
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) { refresh(activity); return; }
                try {
                    TerminalKiosk.leaveForSettings(activity);
                    activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
                }
                catch (RuntimeException e) {
                    refresh(activity);
                    TerminalKiosk.enter(activity);
                    android.widget.Toast.makeText(activity, "Settings unavailable", android.widget.Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}
