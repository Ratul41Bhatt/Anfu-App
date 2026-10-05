package com.hjq.demo.ui.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class ShutdownBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "ShutdownBroadcastReceiver";
    private static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";

    @Override
    public void onReceive(Context context, Intent intent) {  //即将关机时，要做的事情
        if (intent.getAction().equals(ACTION_SHUTDOWN)) {
            Log.i(TAG, "ShutdownBroadcastReceiver onReceive(), Do thing!");
        }
    }
}