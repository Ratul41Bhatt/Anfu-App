package com.hjq.demo.utils;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

public class MonitorService extends Service {
    private static final String TAG = "MonitorService";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e(TAG, "检测到用户划掉应用！");
        AppExitMonitor.notifyAppExit();
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

}
