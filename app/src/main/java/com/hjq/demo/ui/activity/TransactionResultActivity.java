package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.RemoteException;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.SPUtils;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.Target;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.LcdInfo;
import com.hjq.demo.entity.MessageEvent;
import com.hjq.demo.inter.AFSwipeControllerListener;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LcdUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.TouchHelper;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.util.DataConversionUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.litepal.LitePal;

public class TransactionResultActivity extends AppActivity {
    private static final String TAG = "TransactionResultActivity";

    private Handler handler = new Handler();
    private TextView tv_printing, tv_trans_result;
    private TextView tv_slip;
    public static final String EXTRA_SLIP_TEXT = "SLIP_TEXT";
    private CountDownTimer countDownTimer;
    private ImageView img_wait;
    private TitleBar leftIcon;

    private SPUtils spUtils = SPUtils.getInstance("PrintResult");

    private boolean keyboardDevice;

    private boolean isLcdSupported;

    private LcdUtils lcdUtils;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_transaction_result;
    }

    @Override
    protected void initView() {
        tv_printing = findViewById(R.id.tv_printing);
        tv_trans_result = findViewById(R.id.tv_trans_result);
        img_wait = findViewById(R.id.img_wait);
        leftIcon = findViewById(R.id.tb_trans_result);
        tv_slip = findViewById(R.id.tv_slip);

        // Show the printed PAX-style sale slip on screen (useful when there is no printer)
        String slip = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_SLIP_TEXT);
        if (tv_slip != null && slip != null && !slip.isEmpty()) {
            tv_slip.setText(slip);
            tv_slip.setVisibility(android.view.View.VISIBLE);
        }
        Glide.with(this)
                .asGif()
                .load(R.drawable.icon_please_wait)
                .override(130, 130)
                .into(img_wait);

        //spUtils.clear();


        String customerType = DataConversionUtils.getCustomerType();

        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
//        isLcdSupported = DeviceType.A8.equals(DataConversionUtils.getCustomerType());
        isLcdSupported = AFUtils.checkLcdSupported();
        LogUtils.e("isLcdSupported:" + isLcdSupported + "\t" + (isLcdSupported ? "支持LCD" : "不支持LCD"));

        // 创建倒计时器，设置60秒，每1秒更新一次
        countDownTimer = new CountDownTimer(60000, 1000) {

            @Override
            public void onTick(long millisUntilFinished) {
                // 更新TextView，每秒更新一次
                tv_printing.setText(millisUntilFinished / 1000 + "S");
            }

            @Override
            public void onFinish() {
                // 倒计时结束时的逻辑
                transCompleted();
            }
        };

        // 启动倒计时
        countDownTimer.start();
        LogUtils.e("initView: 绘制UI");
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
               /* int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.4f;
                float centerY = screenHeight / 1.2f;
                TouchHelper.simulateFullTouch(TransactionResultActivity.this, centerX, centerY);
                LogUtils.e( "点击.....");*/
                if (leftIcon != null) {
                    tv_printing.setFocusable(true);
                    tv_printing.setFocusableInTouchMode(true);
                    tv_printing.requestFocus();
                }
            }
        }, 200);


        if (DeviceType.A6.equals(customerType)) {
            int delay = (int) (Math.random() * 1000) + 3000;
            LogUtils.e("delay: " + delay);
            tv_trans_result.setText(R.string.please_wait_a_moment_for_transaction_processing);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    countDownTimer.cancel();
                    new MessageDialog.Builder(getActivity())
                            .setTitle(R.string.title_result)
                            .setMessage(R.string.trans_tip_successful)
                            .setConfirm(getString(R.string.common_confirm))
                            .setCancel(null)
                            .setCancelable(false)
                            .setCanceledOnTouchOutside(false)
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {
                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    transCompleted();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
                                }
                            })
                            .show();
                }
            }, delay);

        }

    }

    @Override
    protected void initData() {
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        if (isLcdSupported) {
            Log.i(TAG, "onServiceConnected: 绑定服务成功....");
            try {
                ISystemBinderService mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
                LcdUtils.getInstance().init(mSystemBinderService);
                lcdUtils = LcdUtils.getInstance();
                LcdInfo lcdInfo = lcdUtils.lcdGetDevLcdStatus();
                if (lcdInfo != null) {
                    Log.i(TAG, "lcdGetDevLcdStatus: " + lcdInfo.toString());
                    switch (lcdInfo.getType()) {
                        case 1:
                            Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_transaction_completed), true, 16);
                            //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                            lcdUtils.lcdDrawPicture(bitmap);
                            break;
                        case 2:
                            lcdUtils.displayInputAmountsBmp(getContext(), "lcd_tip_transaction_completed.bmp", getString(R.string.lcd_tip_transaction_completed), 32);
                            break;
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            transCompleted(); return true;
        }
        if (keyboardDevice) {
            LogUtils.e("onKeyDown: 820");
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                // 确认键被按下
                Log.d(TAG, "Confirm (Enter/OK) button pressed");
                return true;
            }
            // 检查按键的键值
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                    Log.d(TAG, "确认键");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                    // 处理音量增大按键
                    Log.d(TAG, "Volume up button pressed");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    // 处理音量减少按键
                    Log.d(TAG, "Volume down button pressed");
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    // 处理清除键->直接退出页面
                    Log.d(TAG, "Back button pressed");
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    // CLEAR
                    Log.d(TAG, "Confirm button KEYCODE_DEL");
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.e("onKeyUp: 820");
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (leftIcon != null) {
                        leftIcon.setFocusable(true);
                        leftIcon.setFocusableInTouchMode(true);
                        leftIcon.requestFocus();
                    }
                    Log.d(TAG, "Confirm button released");
                    return true;
                case KeyEvent.KEYCODE_CLEAR:
                    Log.d(TAG, "onKeyUp:  KEYCODE_CLEAR");
                    return true;
                default:
                    return super.onKeyUp(keyCode, event);
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Log.d(TAG, "点击了屏幕->" + ev.toString());
        return super.dispatchTouchEvent(ev);
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        LogUtils.e("onStart: 订阅消息");

        if (spUtils.contains("PrintCode") && spUtils.contains("PrintMessage")) {
            int errorCode = spUtils.getInt("PrintCode");
            String errorMsg = spUtils.getString("PrintMessage");
            LogUtils.e("-----------errorCode: " + errorCode + " ,errorMsg: " + errorMsg);
            EventBus.getDefault().post(new MessageEvent(errorCode, errorMsg));

           /* if (isLcdSupported && lcdUtils != null) {
                LcdInfo lcdInfo = lcdUtils.lcdGetDevLcdStatus();
                if (lcdInfo != null) {
                    LogUtils.e("lcdGetDevLcdStatus: " + lcdInfo.toString());
                    switch (lcdInfo.getType()) {
                        case 1:
                            Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_welcome), false, 16);
                            //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                            lcdUtils.lcdDrawPicture(bitmap);
                            break;
                        case 2:
                            lcdUtils.displayBmp(getContext(), errorCode + ".bmp", getString(R.string.lcd_tip_welcome), 12);
                            break;
                    }
                }
            }*/
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        LogUtils.e("onStop: 注销订阅消息");
    }

    // 订阅事件
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if (completed || isFinishing() || isDestroyed()) return;
        LogUtils.e("onMessageEvent: " + event.toString());
        tv_trans_result.setText(event.message);
        countDownTimer.cancel();
        spUtils.clear();
        new MessageDialog.Builder(getActivity())
                .setTitle(R.string.printing_results)
                .setMessage(event.message)
                .setConfirm(getString(R.string.common_confirm))
                .setCancel(null)
                .setCancelable(false)
                .setCanceledOnTouchOutside(false)
                //.setAutoDismiss(false)
                .setListener(new MessageDialog.OnListener() {

                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        transCompleted();
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        //toast("取消");
                    }
                })
                .show();
    }

    /**
     * Done - back to the main menu.
     *
     * <p>This went to {@code AmountActivity}, the SDK demo's own "Card
     * consumption" keypad, with the correct line sitting commented out just
     * above it. So every finished transaction - approved, declined or
     * cancelled - dropped the operator on a bare amount screen that belongs to
     * no flow in this app, instead of the menu they started from.</p>
     *
     * <p>SINGLE_TOP alongside CLEAR_TOP so the menu already on the stack is
     * brought forward rather than a second copy being built on top of it.</p>
     */
    private boolean completed;
    private void transCompleted() {
        if (completed || isFinishing()) return;
        completed = true;
        if (countDownTimer != null) countDownTimer.cancel();
        spUtils.put("PrintFinish", true);
        Intent intent = new Intent(getContext(), PayActivity.class);
        intent.putExtra(PayActivity.EXTRA_WAIT_FOR_CARD_REMOVAL, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        LogUtils.e("--------onBackPressed------");
        transCompleted();
    }
}
