package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;

import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.other.AppConfig;
import com.hjq.demo.utils.TerminalLockdown;
import com.hjq.widget.view.SlantedTextView;
import com.szanfu.sdk.api.ISdkServiceManager;

/**
 * 闪屏界面 - 入口Activity
 * 首次安装打开或系统开机重启时，在此配置安福全套禁用API（状态栏/导航栏/Recent/Home/设置密码）及开机自启权限
 */
public final class SplashActivity extends AppActivity {

    private SlantedTextView mDebugView;
    private volatile boolean mSplashFinished;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mTimeoutRunnable = this::proceedToMain;

    @Override
    protected int getLayoutId() {
        return R.layout.splash_activity;
    }

    @Override
    protected void initView() {
        mDebugView = findViewById(R.id.iv_splash_debug);
    }

    @Override
    protected void initData() {
        if (mDebugView != null) {
            mDebugView.setText(AppConfig.getBuildType().toUpperCase());
            mDebugView.setVisibility(AppConfig.isDebug() ? View.VISIBLE : View.INVISIBLE);
        }

        // 兜底超时：如果SDK服务连接或配置超过2秒，直接进入主界面，避免卡屏
        mHandler.postDelayed(mTimeoutRunnable, 2000);

        // 如果在initData执行时SDK服务已被绑定，立即执行配置
        if (mSdkServiceManager != null) {
            configureTerminal(mSdkServiceManager);
        }
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 隐藏状态栏和导航栏
                .hideBar(BarHide.FLAG_HIDE_BAR);
    }

    @Override
    public void onBackPressed() {
        // 禁用返回键
    }

    @Override
    protected void initActivity() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (!isTaskRoot()) {
            Intent intent = getIntent();
            if (intent != null && (intent.hasCategory(Intent.CATEGORY_LAUNCHER) || intent.hasCategory(Intent.CATEGORY_HOME))
                    && Intent.ACTION_MAIN.equals(intent.getAction())) {
                finish();
                return;
            }
        }
        super.initActivity();
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        configureTerminal(sdkServiceManager);
    }

    private void configureTerminal(ISdkServiceManager sdkServiceManager) {
        if (sdkServiceManager == null) return;
        TerminalLockdown.configureAllLockdown(this, sdkServiceManager, () -> {
            mHandler.post(this::proceedToMain);
        });
    }

    private synchronized void proceedToMain() {
        mHandler.removeCallbacks(mTimeoutRunnable);
        if (mSplashFinished || isFinishing() || isDestroyed()) return;
        mSplashFinished = true;
        startActivity(PayActivity.class);
        finish();
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}