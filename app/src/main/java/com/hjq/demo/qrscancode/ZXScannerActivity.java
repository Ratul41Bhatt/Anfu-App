package com.hjq.demo.qrscancode;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.client.result.ParsedResultType;
import com.hjq.demo.R;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LogUtils;
import com.szanfu.sdk.api.util.DataConversionUtils;
import com.wushaohong.zxing.ScannerView;
import com.wushaohong.zxing.camera.open.CameraFacing;
import com.wushaohong.zxing.decode.QRDecode;

import java.io.FileNotFoundException;

/**
 * author : wushaohong
 * e-mail : 576218811@qq.com
 * date   : 2019/08/28
 * desc   : 摄像头扫描二维码页面 Activity
 * version: 1.0
 */
public class ZXScannerActivity extends BaseScannerActivity {

    private static final int CHOOSE_PICTURE_REQUEST_CODE = 10;

    private ImageView mIvBack;
    private TextView mTvAlbum;
    /**
     * 扫描控件
     */
    private ScannerView mScannerView;

    /**
     * 闪光灯提示布局
     */
    private LinearLayout mLlWeak;
    private ImageView mIvFlashLamp;
    private TextView mTvWeakTips;
    public static final String EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"; // 用于传递结果数据的键

    private static final String TAG = "ZXScannerActivity";

    private BarcodeFormat[] supportedFormats  = null;

    public static void startActivity(Context context) {
        Intent intent = new Intent(context, ZXScannerActivity.class);
        context.startActivity(intent);
    }

    private void initSupportedQRData() {
        supportedFormats = new BarcodeFormat[]{
                BarcodeFormat.AZTEC,
                BarcodeFormat.CODABAR,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.CODE_128,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.EAN_8,
                BarcodeFormat.EAN_13,
                BarcodeFormat.ITF,
                BarcodeFormat.MAXICODE,
                BarcodeFormat.PDF_417,
                BarcodeFormat.QR_CODE,
                BarcodeFormat.RSS_14,
                BarcodeFormat.RSS_EXPANDED,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.UPC_EAN_EXTENSION
        };
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        // 屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//        // 透明状态栏
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        // 透明状态栏
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {//Android5.0以上
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(Color.TRANSPARENT);
        } else if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.KITKAT){//Android4.4以上,5.0以下
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        }

        setContentView(R.layout.activity_scanner);
        initSupportedQRData();
        initView();
    }

    private void initView() {

        mIvBack = (ImageView) findViewById(R.id.iv_back);
        mIvBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        mTvAlbum = (TextView) findViewById(R.id.tv_album);
        mTvAlbum.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                choosePicture();
            }
        });

        // 扫描框
        mScannerView = (ScannerView) findViewById(R.id.sv_scan);
        // 设置扫描回调
        mScannerView.setOnScannerCompletionListener(this);
        // 设置扫描成功的声音
        mScannerView.setMediaResId(R.raw.beep);
        // 扫描框下方文字
        mScannerView.setDrawText("放入框内，自动扫描", true);
        // 文字颜色
        mScannerView.setDrawTextColor(0xFF26CEFF);
        mScannerView.setScanMode(supportedFormats);
        mScannerView.isScanFullScreen(false);
        // 边框颜色
        mScannerView.setLaserFrameBoundColor(0xFF26CEFF);
        // 支付宝网格
        mScannerView.setLaserGridLineResId(R.mipmap.zfb_grid_scan_line);

        String customerType = DataConversionUtils.getCustomerType();
        if (DeviceType.A6.equals(customerType)) {
            mScannerView.setCameraFacing(CameraFacing.FRONT);    // 前置摄像头
        }
        else {
            mScannerView.setCameraFacing(CameraFacing.BACK);   // 后置摄像头
        }
        // 扫描框与屏幕上方距离
        mScannerView.setLaserFrameTopMargin(150);
        // 设置4角长度
        mScannerView.setLaserFrameCornerLength(25);
        // 设置扫描线高度
        mScannerView.setLaserLineHeight(10);
        // 扫描框四个边角宽度
        mScannerView.setLaserFrameCornerWidth(3);
        //设置扫码库宽高
//        mScannerView.setLaserFrameSize(280,280);

        // 设置扫描结果放大
//        mScannerView.setResultZoom(true);
        // 设置扫描结果放大后是否重置
//        mScannerView.setResultZoomReset(true);
        // 设置扫描结果方法后，重置毫秒数
//        mScannerView.setResultZoomResetDelay(2000);

        mLlWeak = (LinearLayout) findViewById(R.id.ll_weak_light);
        mIvFlashLamp = (ImageView) findViewById(R.id.iv_flash_lamp);
        mTvWeakTips = (TextView) findViewById(R.id.tv_weak_light_tips);

        mLlWeak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                controlFlashLamp();
            }
        });

    }


    @Override
    protected void onResume() {
        mScannerView.onResume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mScannerView.onPause();
        mLlWeak.setVisibility(View.GONE);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        supportedFormats = null;
    }

    @Override
    void onScanResultActivity(Result result, ParsedResultType type, Bundle bundle) {
        //Toast.makeText(this, result.getText(), Toast.LENGTH_LONG).show();
        // 2000毫秒后进入下一次扫描
        //mScannerView.restartPreviewAfterDelay(2000);
        Intent resultIntent = new Intent();
        resultIntent.putExtra(EXTRA_RESULT_DATA, result.getText());
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onCheckWeakLight(boolean isWeakLight) {
        super.onCheckWeakLight(isWeakLight);
        boolean isOpen = mScannerView.isLightStatus();
        // 如果是开着闪光灯，将不会自动隐藏
        if (isOpen) {
            mLlWeak.setVisibility(View.VISIBLE);
        } else {
            mLlWeak.setVisibility(isWeakLight ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        LogUtils.i( "onActivityResult: ");
        if (Activity.RESULT_OK == resultCode) {
            switch (requestCode) {
                case CHOOSE_PICTURE_REQUEST_CODE:
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            ContentResolver cr = this.getContentResolver();
                            try {
                                Bitmap bitmap = BitmapFactory.decodeStream(cr.openInputStream(uri));
                                QRDecode.decodeQR(bitmap, this);
                            } catch (FileNotFoundException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    break;
                default:
                    break;
            }
        }

    }

    /**
     * 选择图片
     */
    private void choosePicture() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, CHOOSE_PICTURE_REQUEST_CODE);
    }

    /**
     * 控制闪光灯
     */
    private void controlFlashLamp() {
        boolean isOpen = mScannerView.isLightStatus();
        isOpen = !isOpen;
        mIvFlashLamp.setImageDrawable(getDrawable(isOpen ?
                R.drawable.ic_flashlight_open : R.drawable.ic_flashlight_close));
        mScannerView.toggleLight(isOpen);
    }
}
