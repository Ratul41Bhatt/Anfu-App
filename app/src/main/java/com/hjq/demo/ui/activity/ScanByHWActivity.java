package com.hjq.demo.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.TextureView;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.camera2.CameraHelperHW;
import com.hjq.demo.utils.LogUtils;
import com.huawei.hms.ml.scan.HmsScan;
import com.szanfu.sdk.api.ISdkServiceManager;

import java.lang.ref.WeakReference;

public class ScanByHWActivity extends AppActivity {
    private static final String TAG = ScanByHWActivity.class.getSimpleName();
    TextureView textureView;
    CameraHelperHW cameraHelper;
    static Handler handler;
    private ImageView mIvScan;
    Animation mTop2Bottom, mBottom2Top;
    public static final String SCAN_RESULT = "scanResult";
    public static final String BUNDLE_KEY = "hms_scan";
    private static final int ACTION_REQUEST_PERMISSIONS = 1;
    private static final String[] NEEDED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA
    };

    private static long startTime;
    public static final String SCAN_TIME = "time";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        startTime = System.currentTimeMillis();
        super.onCreate(savedInstanceState);
    }

    private boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(this, neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == ACTION_REQUEST_PERMISSIONS) {
            boolean isAllGranted = true;
            for (int grantResult : grantResults) {
                isAllGranted &= (grantResult == PackageManager.PERMISSION_GRANTED);
            }
            if (isAllGranted) {
                initCamera();
            } else {
                Toast.makeText(this, "权限被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_scan_by_hw;
    }

    @Override
    protected void initView() {
        mIvScan = findViewById(R.id.scan_line);
        textureView = findViewById(R.id.texture_preview_camera);
        initAnimationView();
    }
    @Override
    protected void initData() {
        handler = new MyHandler(this);
        if (!checkPermissions(NEEDED_PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, NEEDED_PERMISSIONS, ACTION_REQUEST_PERMISSIONS);
        } else {
            initCamera();
        }
    }

    private void initCamera() {
        Point max = new Point(1280, 720);
        Point min = new Point(1280, 720);
        cameraHelper = new CameraHelperHW(this,textureView, max,min,getWindowManager().getDefaultDisplay().getRotation());
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {

    }

    @Override
    public void onLeftClick(View view) {
        toast("交易取消");
        super.onLeftClick(view);
    }

    @Override
    public void onBackPressed() {
        toast("交易取消");
        super.onBackPressed();//注释掉这行,back键不退出activity
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false)
                //隐藏导航栏
                .hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraHelper.destory();
    }

    public static void send_message(Message message){
        if(message != null){
            handler.sendMessage(message);
        }
    }

    private static class MyHandler extends Handler {
        private final WeakReference<ScanByHWActivity> scanbyhuaweiActivity;
        public MyHandler(ScanByHWActivity mineActivity){
            scanbyhuaweiActivity = new WeakReference<>(mineActivity);
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            ScanByHWActivity activity = scanbyhuaweiActivity.get();
            if(activity != null){
                switch(msg.what){
                    case 1000:
                        Bundle bundle = msg.getData();
                        HmsScan hmsScan = bundle.getParcelable(BUNDLE_KEY);
                        if (!TextUtils.isEmpty(hmsScan.getOriginalValue())) {
                            LogUtils.i("----- 解析结果：" + hmsScan.getOriginalValue());
                            long endTime = System.currentTimeMillis() - startTime;
                            LogUtils.i("----- 扫码时间花费：" + endTime);
                            Intent intent = new Intent();
                            intent.putExtra(SCAN_RESULT, hmsScan);
                            intent.putExtra(SCAN_TIME,endTime);
                            scanbyhuaweiActivity.get().setResult(RESULT_OK,intent);
                            scanbyhuaweiActivity.get().finish();
                        }
                        break;
                }
            }
        }
    }

    private void initAnimationView(){
        /**
         * x：从0点到0点
         * y:从20%到80%
         */
        mTop2Bottom = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0.2f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0.6f);

        /**
         * x：从0点到0点
         * y:从80%到20%
         */
        mBottom2Top = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0.9f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0.1f);

        mBottom2Top.setRepeatMode(Animation.RESTART);
        mBottom2Top.setInterpolator(new LinearInterpolator());
        mBottom2Top.setDuration(1500);
        mBottom2Top.setFillEnabled(true);//使其可以填充效果从而不回到原地
        mBottom2Top.setFillAfter(true);//不回到起始位置
        //如果不添加setFillEnabled和setFillAfter则动画执行结束后会自动回到远点
        mBottom2Top.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}

            @Override
            public void onAnimationEnd(Animation animation) {
                mIvScan.startAnimation(mTop2Bottom);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });

        mTop2Bottom.setRepeatMode(Animation.RESTART);
        mTop2Bottom.setInterpolator(new LinearInterpolator());
        mTop2Bottom.setDuration(3000);
        mTop2Bottom.setFillEnabled(true);
        mTop2Bottom.setFillAfter(true);
        mTop2Bottom.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                mIvScan.startAnimation(mTop2Bottom);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        mIvScan.startAnimation(mTop2Bottom);
    }
}
