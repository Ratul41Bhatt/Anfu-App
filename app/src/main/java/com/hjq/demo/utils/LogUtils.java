package com.hjq.demo.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import com.szanfu.sdk.api.BuildConfig;


public class LogUtils {
    private static boolean toPrintLog = true; // 日志开关 true ：输出日志 ；false :不输出日志
    private static final String TAG = "offlinepaydemo";
    private static Context mContext;
    private static String versionName = "unknown";
    private static int versionCode = 0;

    // 初始化方法（在Application中调用）
    public static void init(Context context) {
        mContext = context.getApplicationContext();
        try {
            PackageInfo packageInfo = mContext.getPackageManager()
                    .getPackageInfo(mContext.getPackageName(), 0);
            versionName = packageInfo.versionName;
            versionCode = packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void d(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, buildMessage(message));
        }
    }

    public static void i(String message) {
        Log.i(TAG, buildMessage(message));
    }

    public static void w(String message) {
        Log.w(TAG, buildMessage(message));
    }

    public static void e(String message) {
        Log.e(TAG, buildMessage(message));
    }

    private static String buildMessage(String message) {
        if (!toPrintLog){
            return "";
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StackTraceElement targetElement = stackTrace[4]; // 根据调用栈深度调整
        String className = targetElement.getClassName();
        String methodName = targetElement.getMethodName();
        int lineNumber = targetElement.getLineNumber();

        // 简化类名（去掉包名）
        if (className.contains(".")) {
            className = className.substring(className.lastIndexOf('.') + 1);
        }

        return String.format("[%s:%s():%d | v%s(%d)] %s",
                className,
                methodName,
                lineNumber,
                versionName,
                versionCode,
                message);
    }
}