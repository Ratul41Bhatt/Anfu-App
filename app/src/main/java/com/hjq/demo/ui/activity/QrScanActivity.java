package com.hjq.demo.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.media.Image;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.hjq.demo.R;
import com.hjq.demo.utils.LogUtils;

import java.util.List;
import java.util.concurrent.ExecutionException;

import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;

public class QrScanActivity extends AppCompatActivity implements EasyPermissions.PermissionCallbacks{

    private PreviewView previewView;
//    private TextView resultTextView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private BarcodeScanner barcodeScanner;

    private long startTime = 0;
    public static final String SCAN_RESULT = "scan_result";
    public static final String SCAN_TIME = "scan_time";
    public static final String SCAN_FORMAT = "scan_format";
    boolean isDecodeSuccess = false;
    Animation mTop2Bottom, mBottom2Top;
    private ImageView mIvScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startTime = System.currentTimeMillis();
        setContentView(R.layout.activity_qrscan);
        request();
        setupCamera();
        initAnimationView();
    }

    private void setupCamera() {
        previewView = findViewById(R.id.preview_view);
//        resultTextView = findViewById(R.id.result_text_view);
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);

                // 创建BarcodeScanner实例
                barcodeScanner = BarcodeScanning.getClient();

                // 创建ImageAnalysis实例
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // 设置ImageAnalysis分析器
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new ImageAnalysis.Analyzer() {
                    @Override
                    @OptIn(markerClass = ExperimentalGetImage.class)
                    public void analyze(@NonNull  ImageProxy image) {
                        LogUtils.d( "******* analyze is executeing ");
                        if(isDecodeSuccess) return;
                        // 获取ImageProxy对象
                        Image mediaImage = image.getImage();
                        if (mediaImage != null) {
                            // 创建InputImage对象
                            InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());
                            // 使用BarcodeScanner识别二维码
                            barcodeScanner.process(inputImage)
                                    .addOnSuccessListener(barcodes -> {
                                        for (Barcode barcode : barcodes) {
                                            String codeFormat = null;
                                            if(Barcode.FORMAT_UNKNOWN  == barcode.getFormat()){
                                                codeFormat = "unknown";
                                            }else if(Barcode.FORMAT_CODE_128 == barcode.getFormat()){
                                                codeFormat = "FORMAT_CODE_128";
                                            }else if(Barcode.FORMAT_CODE_39 == barcode.getFormat()){
                                                codeFormat = "FORMAT_CODE_39";
                                            }else if(Barcode.FORMAT_CODE_93 == barcode.getFormat()){
                                                codeFormat = "FORMAT_CODE_93";
                                            }else if(Barcode.FORMAT_CODABAR == barcode.getFormat()){
                                                codeFormat = "FORMAT_CODABAR";
                                            }else if(Barcode.FORMAT_DATA_MATRIX == barcode.getFormat()){
                                                codeFormat = "FORMAT_DATA_MATRIX";
                                            }else if(Barcode.FORMAT_EAN_13 == barcode.getFormat()){
                                                codeFormat = "FORMAT_EAN_13";
                                            }else if(Barcode.FORMAT_EAN_8 == barcode.getFormat()){
                                                codeFormat = "FORMAT_EAN_8";
                                            }else if(Barcode.FORMAT_ITF == barcode.getFormat()){
                                                codeFormat = "FORMAT_ITF";
                                            }else if(Barcode.FORMAT_QR_CODE == barcode.getFormat()){
                                                codeFormat = "FORMAT_QR_CODE";
                                            }else if(Barcode.FORMAT_UPC_A == barcode.getFormat()){
                                                codeFormat = "FORMAT_UPC_A";
                                            }else if(Barcode.FORMAT_UPC_E == barcode.getFormat()){
                                                codeFormat = "FORMAT_UPC_E";
                                            }else if(Barcode.FORMAT_PDF417 == barcode.getFormat()){
                                                codeFormat = "FORMAT_PDF417";
                                            }else if(Barcode.FORMAT_AZTEC == barcode.getFormat()){
                                                codeFormat = "FORMAT_AZTEC";
                                            }

                                            String qrCodeValue = barcode.getRawValue();
                                            if (!TextUtils.isEmpty(qrCodeValue)) {
                                                isDecodeSuccess = true;
                                                // 在UI线程中更新TextView
                                                long endTime = System.currentTimeMillis() - startTime;
                                                String finalCodeFormat = codeFormat;
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
//                                                        Toast.makeText(QrScanActivity.this, "EndTime is: " + endTime, Toast.LENGTH_LONG).show();
                                                        LogUtils.d( "******* EndTime is: " + endTime);
                                                        Intent intent = new Intent();
                                                        intent.putExtra(SCAN_RESULT, qrCodeValue);
                                                        intent.putExtra(SCAN_TIME,endTime);
                                                        intent.putExtra(SCAN_FORMAT, finalCodeFormat);
                                                        QrScanActivity.this.setResult(RESULT_OK,intent);
                                                        QrScanActivity.this.finish();
                                                    }
                                                });
                                                break;
                                            }
                                        }
                                    })
                                    .addOnFailureListener(e -> {
                                        // 处理扫描失败的情况
                                        LogUtils.e( "Failed to scan barcode"+ e);
                                    })
                                    .addOnCompleteListener(task -> image.close());
                        } else {
                            image.close();
                        }
                    }
                });

                // 绑定ImageAnalysis实例到相机生命周期中
                cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                LogUtils.e( "Failed to initialize camera"+ e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void request() {
        String[] permissions = new String[]{
                Manifest.permission.CAMERA,
                //可以添加其他的权限，用来判断
        };
        //判断有没有权限
        if (EasyPermissions.hasPermissions(this, permissions)) {
            // 有权限，需要做什么
        } else {
            // 没有权限, 申请权限
            EasyPermissions.requestPermissions(
                    this,
                    "摄像机需要用户允许才能调用，请开启相关权限（理由）",
                    1,
                    permissions
            );
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        for (String temp : perms) {
            if (temp.equals(Manifest.permission.CAMERA)) {
                LogUtils.e( "CAMERA onPermissionsGranted");
            }
        }

    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        LogUtils.d( "onPermissionsDenied:" + requestCode + ":" + perms.size());
        String permissions = Manifest.permission.CAMERA;
        if (perms.contains(permissions)) {
            if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
                new AppSettingsDialog.Builder(this)
                        .setTitle("The permission has been denied by you")
                        .setRationale("If you do not open the permission, you cannot use this function, click OK to open the permission")
                        .setRequestCode(12345)
                        .build()
                        .show();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIvScan.clearAnimation();
    }

    private void initAnimationView(){
        mIvScan = findViewById(R.id.scan_line);

        /**
         * x：从0点到0点
         * y:从20%到80%
         */
        mTop2Bottom = new TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0.0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 1.0f);

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
        mTop2Bottom.setDuration(2000);
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

