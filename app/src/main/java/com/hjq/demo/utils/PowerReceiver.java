package com.hjq.demo.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PowerReceiver extends BroadcastReceiver {
    private static final String TAG = "PowerReceiver";
    private final String action_shutdown = Intent.ACTION_SHUTDOWN;
    private final String action_reboot = Intent.ACTION_REBOOT;
    private DeviceStatusMonitoring myListener;

    public PowerReceiver(DeviceStatusMonitoring deviceStatusMonitoring) {
        myListener = deviceStatusMonitoring;
    }

    /**
     * 设备状态监听
     */
    public interface DeviceStatusMonitoring {
        void onShutDown();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        LogUtils.d("action = " + action);
        if (action_shutdown.equals(action)) {
            //Power Off
            LogUtils.i("Power Off!");
            myListener.onShutDown();
        } else if (action_reboot.equals(action)) {
            //Power reboot
            LogUtils.i("Power Reboot!");
        }
    }

}