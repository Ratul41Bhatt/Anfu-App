package com.hjq.demo.app;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.hjq.base.BaseActivity;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.action.TitleBarAction;
import com.hjq.demo.action.ToastAction;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.entity.LcdInfo;
import com.hjq.demo.http.model.HttpData;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.AppConfig;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LcdUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.MonitorService;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.WatermarkUtils;
import com.hjq.http.listener.OnHttpListener;
import com.hjq.language.MultiLanguages;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.util.DataConversionUtils;

import java.io.File;

import okhttp3.Call;

/**
 * author : Android 轮子哥
 * github : https://github.com/getActivity/AndroidProject
 * time   : 2018/10/18
 * desc   : Activity 业务基类
 */
public abstract class AppActivity extends BaseActivity
        implements ToastAction, TitleBarAction, OnHttpListener<Object> {

    private static final String TAG = "AppActivity";
    /**
     * 标题栏对象
     */
    private TitleBar mTitleBar;
    /**
     * 状态栏沉浸
     */
    private ImmersionBar mImmersionBar;

    /**
     * 加载对话框
     */
    private BaseDialog mDialog;
    /**
     * 对话框数量
     */
    private int mDialogCount;

    protected ISdkServiceManager mSdkServiceManager = null;

    public ISdkServiceManager getSdkServiceManager() {
        return mSdkServiceManager;
    }

    boolean isServiceBound = false;
    private LcdUtils lcdUtils;
    private boolean isLcdSupported;

    LcdInfo lcdInfo = null;

    /**
     * 绑定SDK binder服务，调用SDK接口的前提。
     */
    public boolean bindService() {
        try {
            Intent intent = new Intent();
            intent.setPackage("com.szanfu.sdk.service");
            intent.setAction("com.szanfu.sdk.service.SDK_SERVICE");
            boolean bindResult = bindService(intent, conn, Context.BIND_AUTO_CREATE);
            if (!bindResult) {
                toast(getString(R.string.service_bind_failed));
            }
            return bindResult;
        } catch (SecurityException e) {
            // 处理安全异常，可能权限不足
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // 处理非法参数异常
            e.printStackTrace();
        } catch (Exception e) {
            // 处理其他异常
            e.printStackTrace();
        }
        return false;
    }

    private void unbindService() {
        LogUtils.i("--------- 注销服务 Unbind from Service ------------");
        if (isServiceBound) {
            unbindService(conn);
            Log.i(TAG, "unbindService: 解绑了... isLcdSupported->>>" + isLcdSupported);
            if (lcdInfo != null && isLcdSupported) {
                switch (lcdInfo.getType()) {
                    case 1:
                        Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_welcome), false, 16);
                        //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                        lcdUtils.lcdDrawPicture(bitmap);
                        break;
                    case 2:
                        File bmpFile = new File(FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                        if (!bmpFile.exists()) {
                            lcdUtils.displayBmp(getContext(), "Color_screen_welcome.bmp", getString(R.string.lcd_tip_welcome), 48);
                            Log.e("readFile", "BMP file not found, creating new BMP file: " + FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                        } else {
                            Log.i("readFile", "BMP file already exists, using existing file: " + FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                            lcdUtils.displayBinOnLcd(FILE_PATH_AF980_COLOR_SCREEN_LOGO, lcdInfo.getWidth(), lcdInfo.getHeight());
                        }
                        break;
                }
            }
        }
    }

    public ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            isServiceBound = false;
            mSdkServiceManager = null;
            LogUtils.i("SDK binder service disconnected.");
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isServiceBound = true;
            mSdkServiceManager = ISdkServiceManager.Stub.asInterface(service);
            if (mSdkServiceManager != null) {
                try {
                    /**
                     * 监听SDK binder服务的死亡状态，如果挂掉，则重新进行绑定。
                     */
                    mSdkServiceManager.asBinder().linkToDeath(new IBinder.DeathRecipient() {
                        @Override
                        public void binderDied() {
                            LogUtils.i("SDK binder service binderDied.");
                            bindService();
                        }
                    }, 0);
                    if (isLcdSupported) {
                        ISystemBinderService mSystemBinderService = ISystemBinderService.Stub.asInterface(mSdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
                        LcdUtils.getInstance().init(mSystemBinderService);
                        lcdUtils = LcdUtils.getInstance();
                        lcdInfo = lcdUtils.lcdGetDevLcdStatus();
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                com.hjq.demo.utils.TerminalLockdown.connect(getApplicationContext(), mSdkServiceManager);
                AppActivity.this.onServiceConnected(mSdkServiceManager);
            }
        }
    };

    public abstract void onServiceConnected(ISdkServiceManager sdkServiceManager);

    /**
     * 当前加载对话框是否在显示中
     */
    public boolean isShowDialog() {
        return mDialog != null && mDialog.isShowing();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 添加不重复的单个水印（右下角）
        if (1 == AppConfig.buildWatermarkType) {
            WatermarkUtils.addWatermark(this,
                    "",
                    Color.BLUE, 24, 0, false, 3);
        }
        isLcdSupported = AFUtils.checkLcdSupported();
//        isLcdSupported = DeviceType.A8.equals(DataConversionUtils.getCustomerType());
        LogUtils.e("isLcdSupported:" + isLcdSupported + "\t" + (isLcdSupported ? "支持LCD" : "不支持LCD"));


    }




    @Override
    protected void onResume() {
        super.onResume();
        com.hjq.demo.utils.TerminalLockdown.onAppResumed();
        applyKioskFlags();
        com.hjq.demo.utils.TerminalKiosk.enter(this);
        com.hjq.demo.utils.TerminalLockdown.lockNavBar(mSdkServiceManager);
        bindService();
        LogUtils.i("注册服务---》onResume()----------");

        startService(new Intent(this, MonitorService.class));
        resetIdleTimeout();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyKioskFlags();
            com.hjq.demo.utils.TerminalLockdown.lockNavBar(mSdkServiceManager);
        }
    }

    private void applyKioskFlags() {
        try {
            android.view.Window window = getWindow();
            if (window != null) {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
                View decor = window.getDecorView();
                if (decor != null) {
                    decor.setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    );
                }
            }
        } catch (Exception ignore) {}
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (!com.hjq.demo.utils.TerminalLockdown.isSettingsSession() && event != null) {
            int code = event.getKeyCode();
            if (code == android.view.KeyEvent.KEYCODE_HOME
                    || code == android.view.KeyEvent.KEYCODE_APP_SWITCH
                    || code == 187) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (!com.hjq.demo.utils.TerminalLockdown.isSettingsSession()) {
            try {
                android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    am.moveTaskToFront(getTaskId(), android.app.ActivityManager.MOVE_TASK_WITH_HOME);
                }
            } catch (Exception ignore) {}
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // A screen that is not in front cannot be idled out - the clock would
        // otherwise keep running behind whatever the operator moved on to.
        stopIdleTimeout();
    }

    // ------------------------------------------------------------ idle timeout

    /**
     * Close a screen that has been left untouched.
     *
     * <p>A terminal sits on a counter and is shared. Every screen that waits
     * for a person can be walked away from, and when one is, the terminal is
     * left holding a half-finished transaction with the next customer's
     * operator looking at someone else's amount. Nothing in this app closed
     * those screens - the card search had a timeout of its own, but the
     * confirmation dialog that follows it waited indefinitely.</p>
     *
     * <p>The clock is restarted by {@link #onUserInteraction()}, which Android
     * calls for every touch and key press dispatched to the activity, so a
     * screen someone is actually using never expires under them.</p>
     *
     * <p>Expiry is always a cancel and never a confirm: nobody was there to
     * agree to anything.</p>
     */
    private final android.os.Handler mIdleHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private final Runnable mIdleExpired = new Runnable() {
        @Override
        public void run() {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            LogUtils.i("idle timeout on " + getClass().getSimpleName() + " - closing");
            onIdleTimeout();
        }
    };

    /**
     * Whether this screen closes itself when left alone.
     *
     * <p>Screens that run an operation the operator cannot hurry along - a card
     * read, a host settlement - return false, because on those the absence of a
     * touch means the terminal is busy, not that it has been abandoned. The
     * home menu returns false too: closing it would put the operator nowhere.</p>
     */
    protected boolean idleTimeoutEnabled() {
        return true;
    }

    /** What expiry does. Override to unwind whatever this screen was holding. */
    protected void onIdleTimeout() {
        finish();
    }

    protected int idleTimeoutSeconds() { return TerminalConfig.get(this).idleTimeoutSec(); }

    /** Restart the clock. */
    protected void resetIdleTimeout() {
        mIdleHandler.removeCallbacks(mIdleExpired);
        if (!idleTimeoutEnabled() || isFinishing() || isDestroyed()) {
            return;
        }
        int seconds;
        try {
            seconds = idleTimeoutSeconds();
        } catch (Exception e) {
            // Parameters unreadable this early; no timeout is the safe answer.
            return;
        }
        if (seconds <= 0) {
            return;
        }
        mIdleHandler.postDelayed(mIdleExpired, seconds * 1000L);
    }

    /** Stop the clock - for a screen that has become busy, or is going away. */
    protected void stopIdleTimeout() {
        mIdleHandler.removeCallbacks(mIdleExpired);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        resetIdleTimeout();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // 绑定语种
        super.attachBaseContext(MultiLanguages.attach(newBase));
    }

    /**
     * 显示加载对话框
     */
    public void showDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        mDialogCount++;
        postDelayed(() -> {
            if (mDialogCount <= 0 || isFinishing() || isDestroyed()) {
                return;
            }

            if (mDialog == null) {
                mDialog = new WaitDialog.Builder(this)
                        .setCancelable(false)
                        .create();
            }
            if (!mDialog.isShowing()) {
                mDialog.show();
            }
        }, 300);
    }

    /**
     * 隐藏加载对话框
     */
    public void hideDialog() {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (mDialogCount > 0) {
            mDialogCount--;
        }

        if (mDialogCount != 0 || mDialog == null || !mDialog.isShowing()) {
            return;
        }

        mDialog.dismiss();
    }

    @Override
    protected void initLayout() {
        super.initLayout();

        if (getTitleBar() != null) {
            getTitleBar().setOnTitleBarListener(this);
        }

        // 初始化沉浸式状态栏
        if (isStatusBarEnabled()) {
            getStatusBarConfig().init();

            // 设置标题栏沉浸
            if (getTitleBar() != null) {
                ImmersionBar.setTitleBar(this, getTitleBar());
            }
        }
    }

    /**
     * 是否使用沉浸式状态栏
     */
    protected boolean isStatusBarEnabled() {
        return true;
    }

    /**
     * 状态栏字体深色模式
     */
    protected boolean isStatusBarDarkFont() {
        return true;
    }

    /**
     * 获取状态栏沉浸的配置对象
     */
    @NonNull
    public ImmersionBar getStatusBarConfig() {
        if (mImmersionBar == null) {
            mImmersionBar = createStatusBarConfig();
        }
        return mImmersionBar;
    }

    /**
     * 初始化沉浸式状态栏
     */
    @NonNull
    protected ImmersionBar createStatusBarConfig() {
        return ImmersionBar.with(this)
                // 默认状态栏字体颜色为黑色
                .statusBarDarkFont(isStatusBarDarkFont())
                // 隐藏导航栏（Recent apps 和 Home）
                .hideBar(com.gyf.immersionbar.BarHide.FLAG_HIDE_NAVIGATION_BAR)
                // 状态栏字体和导航栏内容自动变色，必须指定状态栏颜色和导航栏颜色才可以自动变色
                .autoDarkModeEnable(true, 0.2f);
    }

    /**
     * 设置标题栏的标题
     */
    @Override
    public void setTitle(@StringRes int id) {
        setTitle(getString(id));
    }

    /**
     * 设置标题栏的标题
     */
    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (getTitleBar() != null) {
            getTitleBar().setTitle(title);
        }
    }

    @Override
    @Nullable
    public TitleBar getTitleBar() {
        if (mTitleBar == null) {
            mTitleBar = obtainTitleBar(getContentView());
        }
        return mTitleBar;
    }

    @Override
    public void onLeftClick(View view) {
        onBackPressed();
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        super.startActivityForResult(intent, requestCode, options);
        overridePendingTransition(R.anim.right_in_activity, R.anim.right_out_activity);
    }

    public static final String FILE_PATH_AF980_COLOR_SCREEN_LOGO = Environment.getExternalStorageDirectory().getPath() + File.separator + "af980_color_screen_logo.bin";
    public static final String FILE_PATH_AF980_COLOR_SCREEN_NFC_LOGO = Environment.getExternalStorageDirectory().getPath() + File.separator + "980_color_nfc_log.bin";
    public static final String FILE_PATH_AF980_BLACK_NFC_LOGO = Environment.getExternalStorageDirectory().getPath() + File.separator + "980_black_white_nfc_log.bin";
//    public static final String FILE_PATH_AF980_COLOR_SCREEN_LOGO = Environment.getExternalStorageDirectory().getPath() + File.separator + "af980_color_screen_logo.bmp";

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(R.anim.left_in_activity, R.anim.left_out_activity);
        Log.i(TAG, "finish: 关闭....");
        String activityName = this.getClass().getSimpleName();
        Log.i(TAG, "当前关闭的 Activity: " + activityName + "\t isLcdSupported:" + isLcdSupported);
        if (isLcdSupported && !"FindCardActivity".equals(activityName)) {
            Log.i(TAG, "显示待机页面");
            if (lcdInfo != null) {
                LogUtils.e("lcdGetDevLcdStatus: " + lcdInfo.toString());
                switch (lcdInfo.getType()) {
                    case 1:
                        Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_welcome), false, 16);
                        //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                        lcdUtils.lcdDrawPicture(bitmap);
                        break;
                    case 2:
                       /* String bmpPath = getContext().getFilesDir() + "/Color_screen_welcome.bmp";
                        Bitmap bitmap_color = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_welcome), true, 48);
                        //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap_color);
                        lcdUtils.saveBitmapAsBmpFile(bitmap_color, bmpPath);
                        lcdUtils.displayBmpOnLcd(bmpPath);*/
                        File bmpFile = new File(FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                        if (!bmpFile.exists()) {
                            lcdUtils.displayBmp(getContext(), "Color_screen_welcome.bmp", getString(R.string.lcd_tip_welcome), 48);
                            Log.e("readFile", "BMP file not found, creating new BMP file: " + FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                        } else {
                            Log.i("readFile", "BMP file already exists, using existing file: " + FILE_PATH_AF980_COLOR_SCREEN_LOGO);
                            lcdUtils.displayBinOnLcd(FILE_PATH_AF980_COLOR_SCREEN_LOGO, lcdInfo.getWidth(), lcdInfo.getHeight());
                        }
                        break;
                }
            }
        }

    }

    /**
     * {@link OnHttpListener}
     */

    @Override
    public void onStart(Call call) {
        showDialog();
    }

    @Override
    public void onSucceed(Object result) {
        if (result instanceof HttpData) {
            toast(((HttpData<?>) result).getMessage());
        }
    }

    @Override
    public void onFail(Exception e) {
        toast(e.getMessage());
    }

    @Override
    public void onEnd(Call call) {
        hideDialog();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopIdleTimeout();
        if (isShowDialog()) {
            hideDialog();
        }
        mDialog = null;

        if (isServiceBound) {
            unbindService();
        }

        if (1 == AppConfig.buildWatermarkType) {
            WatermarkUtils.removeWatermark(this);
        }
    }


    private static final int REQUEST_CODE_STORAGE_PERMISSION = 100;

    private void requestStoragePermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.MANAGE_EXTERNAL_STORAGE"}, REQUEST_CODE_STORAGE_PERMISSION);
        } else {
            Log.i(TAG, "requestStoragePermission: 权限已授予，可以直接读取文件");
            // 权限已授予，可以直接读取文件
            //readBinFile("/storage/emulated/0/af980_color_screen_logo.bin");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //readBinFile("/storage/emulated/0/af980_color_screen_logo.bin");
            } else {
                Log.e(TAG, "Storage permission denied");
            }
        }
    }

}
