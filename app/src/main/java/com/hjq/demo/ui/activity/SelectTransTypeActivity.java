package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.annotation.NonNull;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.TouchHelper;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.system.ISystemBinderService;

public final class SelectTransTypeActivity extends AppActivity implements View.OnClickListener {
    private static final String TAG = "SelectTransTypeActivity";
    private ISystemBinderService mSystemBinderService;

    private Button trans_type_confirm;

    private RadioGroup radioGroup;
    private RadioButton rbMode1, rbMode2, rbMode3, rbMode4;
    private LinearLayout llMode1, llMode2, llMode3, llMode4;

    private String currentAmount = "";


    @Override
    protected int getLayoutId() {

     /*   View myView = findViewById(R.layout.trans_type_activity);

        TouchSimulator.simulateTouch(myView, 50f, 50f);
*/
        return R.layout.trans_type_activity;
    }

    @Override
    protected void initView() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.2f;
                float centerY = screenHeight / 1.8f;
                // 在屏幕中心位置模拟点击
                TouchHelper.simulateFullTouch(SelectTransTypeActivity.this, centerX, centerY);
            }
        }, 200);

        Intent intent = getIntent();
        currentAmount = intent.getStringExtra(AFConstant.CURRENT_TRANS_AMOUNT);
        LogUtils.e( "current_trans_amount-->" + currentAmount);

        trans_type_confirm = findViewById(R.id.btn_confirm);
        trans_type_confirm.setOnClickListener(this);

        radioGroup = findViewById(R.id.transaction_mode_group);

        rbMode1 = findViewById(R.id.rb_mode_1);
        rbMode2 = findViewById(R.id.rb_mode_2);
        rbMode3 = findViewById(R.id.rb_mode_3);
        rbMode4 = findViewById(R.id.rb_mode_4);

        llMode1 = findViewById(R.id.ll_mode_1);
        llMode2 = findViewById(R.id.ll_mode_2);
        llMode3 = findViewById(R.id.ll_mode_3);
        llMode4 = findViewById(R.id.ll_mode_4);

        rbMode1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectMode1();
            }
        });
        rbMode2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectMode2();
            }
        });
        rbMode3.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectMode3();
            }
        });
        rbMode4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectMode4();
            }
        });

        llMode1.setOnClickListener(v -> {
            selectMode1();
        });
        llMode2.setOnClickListener(v -> {
            selectMode2();
        });
        llMode3.setOnClickListener(v -> {
            selectMode3();
        });
        llMode4.setOnClickListener(v -> {
            selectMode4();
        });


        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                LogUtils.e( "onCheckedChanged: 选择");
                switch (checkedId) {
                    case R.id.rb_mode_1:
                        // 用户选择了模式 1
                        selectMode1();
                        LogUtils.e( "onCheckedChanged: 用户选择了模式 1 ");
                        break;
                    case R.id.rb_mode_2:
                        // 用户选择了模式 2
                        selectMode2();
                        LogUtils.e( "onCheckedChanged: 用户选择了模式 2 ");
                        break;
                    case R.id.rb_mode_3:
                        // 用户选择了模式 3
                        selectMode3();
                        LogUtils.e( "onCheckedChanged: 用户选择了模式 3 ");
                        break;
                    case R.id.rb_mode_4:
                        // 用户选择了模式 4
                        selectMode4();
                        LogUtils.e( "onCheckedChanged: 用户选择了模式 4 ");
                        break;
                }
            }
        });
    }

    @Override
    protected void initData() {

    }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        Log.d(TAG, "点击了屏幕->" + ev.toString());
        return super.dispatchTouchEvent(ev);
    }


    private void selectMode1() {
        rbMode1.setChecked(true);
        rbMode2.setChecked(false);
        rbMode3.setChecked(false);
        rbMode4.setChecked(false);
    }

    private void selectMode2() {
        rbMode1.setChecked(false);
        rbMode2.setChecked(true);
        rbMode3.setChecked(false);
        rbMode4.setChecked(false);
    }

    private void selectMode3() {
        rbMode1.setChecked(false);
        rbMode2.setChecked(false);
        rbMode3.setChecked(true);
        rbMode4.setChecked(false);
    }

    private void selectMode4() {
        rbMode1.setChecked(false);
        rbMode2.setChecked(false);
        rbMode3.setChecked(false);
        rbMode4.setChecked(true);
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_confirm:
                LogUtils.e( "onClick: trans_type_confirm->" + currentAmount);
                Intent intent = new Intent(SelectTransTypeActivity.this, FindCardActivity.class);
                intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, currentAmount);
                startActivity(intent);
                break;
            default:
                break;
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
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private Handler handler = new Handler();
    private int retryCount = 0;
    // 尝试获取5次
    private static final int MAX_RETRY_COUNT = 5;
    // 每次延迟2秒
    private static final int RETRY_DELAY = 2000;

    private BaseDialog mWaitDialog;


    @Override
    protected void onStop() {
        super.onStop();
        // 停止位置更新
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
            // 在这里处理确认键按下的事件
            Log.i(TAG, "dispatchKeyEvent: 按下.....");
            Intent intent = new Intent(SelectTransTypeActivity.this, FindCardActivity.class);
            intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, currentAmount);
            startActivity(intent);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }


}