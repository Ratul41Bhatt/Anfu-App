package com.hjq.demo.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hjq.demo.ui.activity.AboutActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class WatermarkUtils {
    private String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/sdcard/Android/DeviceMSG.txt";
    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100; // 请求码，唯一即可

    /**
     * 添加水印
     *
     * @param activity  Activity
     * @param text      水印文字
     * @param textColor 文字颜色
     * @param textSize  文字大小
     * @param rotation  文字旋转角度
     * @param repeat    是否重复填满屏幕
     * @param position  水印位置 (1:左上角 2:右上角 3:左下角 4:右下角)
     */
    public static void addWatermark(Activity activity, String text, int textColor, int textSize,
                                    int rotation, boolean repeat, int position) {


        String libVersion = "";
        // 检查权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_STORAGE_PERMISSION);
        } else {
            String strMsg = readFile();
            try {
                JsonArray jsonArray = JsonParser.parseString(strMsg).getAsJsonArray();
                JsonObject firstObject = jsonArray.get(0).getAsJsonObject();

                libVersion = firstObject.get("Lib_Version").getAsString();
                LogUtils.e("libVersion :" + libVersion);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 读取版本并安全解析前缀（下划线前部分）
        String coreRaw = getVersionName(activity, "com.szanfu.core.server");
        String sdkRaw  = getVersionName(activity, "com.szanfu.sdk.service");
        String appRaw  = AFUtils.getVersionName(activity);

        String strSystemCoreVersionCode = parseVersionPrefix(coreRaw);
        String strSDKServiceVersionCode = parseVersionPrefix(sdkRaw);
        String strSDKTestVersionCode    = parseVersionPrefix(appRaw);

        String strShow = "VCore:" + strSystemCoreVersionCode +
                "; VSDK:" + strSDKServiceVersionCode +
                "; VApp:" + strSDKTestVersionCode;


        WatermarkView watermarkView = new WatermarkView(activity, strShow + text, textColor, 16, rotation, repeat, position);
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        rootView.addView(watermarkView);
    }


    /**
     * 解析版本前缀：
     * 1. null 或空字符串 -> 返回 "unknown"
     * 2. 包含下划线 -> 返回第一个下划线前的部分
     * 3. 不包含下划线 -> 原样返回
     */
    private static String parseVersionPrefix(String raw) {
        if (raw == null || raw.length() == 0) {
            return "unknown";
        }

        int index = raw.indexOf('_');
        if (index > 0) {
            return raw.substring(0, index);
        } else {
            // 没有下划线，直接用完整版本号
            return raw;
        }
    }


    /**
     * 移除水印
     */
    public static void removeWatermark(Activity activity) {
        ViewGroup rootView = activity.findViewById(android.R.id.content);
        for (int i = 0; i < rootView.getChildCount(); i++) {
            View view = rootView.getChildAt(i);
            if (view instanceof WatermarkView) {
                rootView.removeView(view);
                i--;
            }
        }
    }

    private static class WatermarkView extends View {
        private String mText;
        private int mTextColor;
        private int mTextSize;
        private int mRotation;
        private boolean mRepeat;
        private int mPosition;
        private Paint mPaint;

        public WatermarkView(Activity activity, String text, int textColor, int textSize,
                             int rotation, boolean repeat, int position) {
            super(activity);
            this.mText = text;
            this.mTextColor = textColor;
            this.mTextSize = textSize;
            this.mRotation = rotation;
            this.mRepeat = repeat;
            this.mPosition = position;

            init();
        }

        private void init() {
            mPaint = new Paint();
            mPaint.setColor(mTextColor);
            mPaint.setTextSize(mTextSize);
            mPaint.setAntiAlias(true);
            mPaint.setAlpha(80); // 设置透明度，可根据需要调整
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            int width = getWidth();
            int height = getHeight();

            if (mRepeat) {
                // 重复填满水印
                drawRepeatedWatermark(canvas, width, height);
            } else {
                // 单个水印
                drawSingleWatermark(canvas, width, height);
            }
        }

        private void drawSingleWatermark(Canvas canvas, int width, int height) {
            canvas.save();

            // 计算水印位置
            Rect textBounds = new Rect();
            mPaint.getTextBounds(mText, 0, mText.length(), textBounds);
            int textWidth = textBounds.width();
            int textHeight = textBounds.height();

            // 根据位置参数设置水印位置
            int x = 0;
            int y = 0;
            int padding = 3; // 边距

            switch (mPosition) {
                case 1: // 左上角
                    x = padding;
                    y = padding + textHeight;
                    break;
                case 2: // 右上角
                    x = width - textWidth - padding;
                    y = padding + textHeight;
                    break;
                case 3: // 左下角
                    x = padding;
                    y = height - padding;
                    break;
                case 4: // 右下角
                    x = width - textWidth - padding;
                    y = height - padding;
                    break;
            }

            // 旋转画布
            canvas.rotate(mRotation, x + textWidth / 2f, y - textHeight / 2f);
            canvas.drawText(mText, x, y, mPaint);
            canvas.restore();
        }

        private void drawRepeatedWatermark(Canvas canvas, int width, int height) {
            canvas.save();

            Rect textBounds = new Rect();
            mPaint.getTextBounds(mText, 0, mText.length(), textBounds);
            int textWidth = textBounds.width();
            int textHeight = textBounds.height();

            // 计算水印间距
            int spacingX = textWidth * 2;
            int spacingY = textHeight * 3;

            // 计算开始位置
            int startX = 0;
            int startY = 0;

            // 如果需要考虑旋转后的位置，可以在这里调整

            // 绘制重复水印
            for (int y = startY; y < height; y += spacingY) {
                for (int x = startX; x < width; x += spacingX) {
                    canvas.save();
                    // 旋转每个水印
                    canvas.rotate(mRotation, x + textWidth / 2f, y + textHeight / 2f);
                    canvas.drawText(mText, x, y + textHeight, mPaint);
                    canvas.restore();
                }
            }

            canvas.restore();
        }
    }


    public static String getVersionName(Context context, String packageName) {
        try {
            PackageInfo pInfo = context.getPackageManager().getPackageInfo(packageName, 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String readFile() {
        File file = new File(Environment.getExternalStorageDirectory() + "/Android/DeviceMSG.txt");
        if (!file.exists())
            return "";
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LogUtils.e("stringBuilder.toString() :" + stringBuilder.toString());
        return stringBuilder.toString();
    }
}
