package com.hjq.demo.utils;

import android.os.RemoteException;
import android.util.Log;

import com.szanfu.sdk.api.emv.IEmvBinderService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class AidManager {

    private static final String TAG = "AidManager";
    private final IEmvBinderService mEmvBinderService;
    public static final int PARAM_AID = 0;
    public static final int PARAM_CAPK = 1;
    public static final int PARAM_AID_CONTACT = 2;
    public static final int PARAM_AID_CONTACTLESS = 4;

    public AidManager(IEmvBinderService service) {
        this.mEmvBinderService = service;
    }

    /**
     * 更新AID（应用标识符）和CAPK（密钥证书授权公钥）
     *
     * @param strAIDOrCapk AID参数或公钥参数
     * @param packageName  当前应用包名
     * @param type         0.AID 1.CAPK
     * @return true：加载成功 false：加载失败
     */
    public boolean saveDevParamAsync(String strAidOrCapk, String packageName, int type) {
        List<String> dataList = new ArrayList<>();
        if (strAidOrCapk != null) {
            for (String line : strAidOrCapk.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    dataList.add(trimmed);
                }
            }
        }

        LogUtils.e( "Processing  dataList.size(): " + dataList.size());

        ExecutorService executor = Executors.newSingleThreadExecutor();

        Future<Integer> future = executor.submit(new Callable<Integer>() {
            @Override
            public Integer call() {
                int ret = -1;
                try {
                    switch (type) {
                        case 0:
                            ret = mEmvBinderService.saveAid(dataList, packageName);
                            break;
                        case 1:
                            ret = mEmvBinderService.saveCapk(dataList, packageName);
                            break;
                        case 2:
                            ret = mEmvBinderService.saveAidMsg(2,dataList, packageName);
                            break;
                        case 4:
                            ret = mEmvBinderService.saveAidMsg(4,dataList, packageName);
                            break;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                    LogUtils.e( "RemoteException while saving AID"+ e);
                }
                return ret;
            }
        });

        try {
            int result = future.get();
            return result == 0;
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e( "Exception while executing saveAidAsync"+ e);
            return false;
        } finally {
            executor.shutdown();
        }
    }
}
