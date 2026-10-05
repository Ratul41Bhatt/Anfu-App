package com.hjq.demo.qrscancode;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;

import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.ScanCallback;

/**
 * ScanServiceClient
 * 迪克扫码能力调用入口
 */
public class ScanServiceClient {

    public interface ScanResultListener {
        void onSuccess(String result);

        void onFailed(String errorMsg);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ISystemBinderService systemBinderService;

    /**
     * SDK Service 连接成功后调用
     */
    public void onSdkServiceReady(ISystemBinderService sdkServiceManager) throws RemoteException {
        systemBinderService = sdkServiceManager;
        if (systemBinderService == null) {
            throw new RemoteException("ISystemBinderService is null");
        }
    }

    public boolean isReady() {
        return systemBinderService != null;
    }

    /**
     * 打开扫码界面
     */
    public void openScan(
            Activity activity,
            int cameraType,
            boolean autoFixedFocal,
            int singleOrRepeat,
            int cameraOrFront,
            int focalLength,
            int timeOutMs,
            final ScanResultListener listener
    ) throws RemoteException {

        if (systemBinderService == null) {
            throw new RemoteException("ScanServiceClient not ready");
        }

        systemBinderService.scansOpenScancallback(
                cameraType,
                autoFixedFocal,
                singleOrRepeat,
                cameraOrFront,
                focalLength,
                timeOutMs,
                new ScanCallback.Stub() {

                    @Override
                    public void onScanSuccess(final String result) throws RemoteException {
                        mainHandler.post(() -> {
                            if (listener != null) listener.onSuccess(result);
                        });
                    }

                    @Override
                    public void onScanFailed(final String errorMsg) throws RemoteException {
                        mainHandler.post(() -> {
                            if (listener != null) listener.onFailed(errorMsg);
                        });
                    }
                }
        );
    }
}
