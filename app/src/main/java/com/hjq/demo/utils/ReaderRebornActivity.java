package com.hjq.demo.utils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.hjq.demo.ui.activity.SplashActivity;

/**
 * Runs in its own process ({@code :reborn}, see the manifest). Ends the main
 * app process - which is what releases the card service - then starts the app
 * again from the splash screen and goes away.
 */
public final class ReaderRebornActivity extends Activity {
    static final String EXTRA_MAIN_PID = "main_pid";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int mainPid = getIntent() == null ? 0 : getIntent().getIntExtra(EXTRA_MAIN_PID, 0);
        if (mainPid != 0 && mainPid != Process.myPid()) {
            Log.e("ReaderRecovery", "ending app process " + mainPid);
            Process.killProcess(mainPid);
        }
        try {
            Intent start = new Intent(this, SplashActivity.class);
            start.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(start);
        } catch (Throwable e) {
            Log.e("ReaderRecovery", "could not restart the app", e);
        }
        finish();
        Runtime.getRuntime().exit(0);
    }
}
