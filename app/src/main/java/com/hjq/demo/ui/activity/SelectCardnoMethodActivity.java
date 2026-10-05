package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.hjq.bar.TitleBar;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.TouchHelper;
import com.hjq.widget.layout.SettingBar;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.system.ISystemBinderService;

public class SelectCardnoMethodActivity extends AppActivity implements View.OnClickListener{
    private static final String TAG = "SelectCardnoMethodActivity";

    private ISystemBinderService mSystemBinderService;

    private String currentAmount = "";

    private SettingBar sbInsertWave;
    private SettingBar sbManual;
    private TextView tvAmount;
    private TitleBar leftIcon;

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
        }catch (RemoteException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.trans_cardnumber_method;
    }

    private Handler mHandler = new Handler();
    @Override
    protected void initView() {

        leftIcon = findViewById(R.id.amount_title);

        Intent intent = getIntent();
        currentAmount = intent.getStringExtra(AFConstant.CURRENT_TRANS_AMOUNT);
        Log.e(TAG, "current_trans_amount-->" + currentAmount);

        tvAmount = findViewById(R.id.tv_cardnumber_amount);
        sbInsertWave = findViewById(R.id.sb_cardnumber_insert);
        sbManual = findViewById(R.id.sb_cardnumber_manual);

        String setAmount = getString(R.string.card_number_amount) + " " + currentAmount;
        tvAmount.setText(setAmount);

        sbInsertWave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setInsertOrWave();
            }
        });

        sbManual.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setManual();
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 2;
                float centerY = screenHeight * 0.1f;
                TouchHelper.simulateFullTouch(SelectCardnoMethodActivity.this, centerX, centerY);
                LogUtils.e( "run: 触摸事件");
                if (leftIcon != null) {
                    leftIcon.setFocusable(true);
                    leftIcon.setFocusableInTouchMode(true);
                    leftIcon.requestFocus();
                }
            }
        }, 200);
    }

    //有卡模式
    private void setInsertOrWave()
    {
        Intent intent = new Intent(SelectCardnoMethodActivity.this, FindCardActivity.class);
        intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, currentAmount);
        startActivity(intent);
    }

    //无卡模式
    private void setManual()
    {
        Intent intent = new Intent(SelectCardnoMethodActivity.this, ManualPunchActivity.class);
        intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, currentAmount);
        startActivity(intent);
    }

    @Override
    protected void initData() {

    }
}
