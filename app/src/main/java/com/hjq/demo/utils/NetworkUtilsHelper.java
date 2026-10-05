package com.hjq.demo.utils;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.blankj.utilcode.util.NetworkUtils;
import com.blankj.utilcode.util.ThreadUtils;

/**
 * 获取网络是否可用
 */
public class NetworkUtilsHelper {
    private static final String TAG = "NetworkUtilsHelper";

    public interface OnNetworkCheckListener {
        void onResult(boolean isConnected);
    }

    /**
     * 检查当前网络是否可用
     *
     * @param listener 回调当前网络状态
     */
    public static void checkNetworkAvailability(final OnNetworkCheckListener listener) {
        final Handler handler = new Handler(Looper.getMainLooper());
        ThreadUtils.executeByCached(new ThreadUtils.SimpleTask<Boolean>() {
            @Override
            public Boolean doInBackground() throws Throwable {
                return NetworkUtils.isConnected();
            }

            @Override
            public void onSuccess(Boolean result) {
                if (listener != null) {
                    Log.i(TAG, "onSuccess: 响应成功....");
                    //网络异常
                    if (!result) {
                        handler.postDelayed(() -> {
                            if (listener != null) {
                                listener.onResult(NetworkUtils.isConnected());
                                Log.i(TAG, "网络超时---->:目前网络状态:" + NetworkUtils.isConnected());
                                handler.removeCallbacksAndMessages(null);
                            }
                        }, 3000);
                    } else {
                        Log.i(TAG, "网络正常....");
                        listener.onResult(result);
                    }

                }
            }
        });
    }
}
