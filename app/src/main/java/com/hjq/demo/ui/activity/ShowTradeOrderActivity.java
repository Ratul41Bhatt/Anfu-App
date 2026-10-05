package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.TouchHelper;
import com.szanfu.sdk.api.ISdkServiceManager;

import org.litepal.LitePal;

import java.util.List;

public class ShowTradeOrderActivity extends AppActivity {
    private static final String TAG = "ShowTradeOrderActivity";

    private TextView txtTransDate;
    private TextView txtRefNo;
    private TextView txtCardNo;
    private TextView txtTransAmount;
    private TextView txtExpDate;
    private TextView txtApprCode;
    private TextView txtMethod;
    private RelativeLayout rlApprCode;
    private Handler handler = new Handler();

    private int orderIndex = -1;
    private List<TransOrder> transOrders = LitePal.findAll(TransOrder.class);

    @Override
    protected int getLayoutId() {
        return R.layout.activity_my_transaction1;
    }

    @Override
    protected void initView() {
        Intent intent = getIntent();
        orderIndex = intent.getIntExtra("OrderPosition", -1);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.2f;
                float centerY = screenHeight / 1.8f;
                // 在屏幕中心位置模拟点击
                TouchHelper.simulateFullTouch(ShowTradeOrderActivity.this, centerX, centerY);
            }
        }, 200);
        txtTransDate = findViewById(R.id.txt_transdate);
        txtRefNo = findViewById(R.id.txt_refno);
        txtCardNo = findViewById(R.id.txt_cardno);
        txtTransAmount = findViewById(R.id.txt_transamount);
        txtExpDate = findViewById(R.id.txt_expdate);
        txtMethod = findViewById(R.id.txt_method);
        txtApprCode = findViewById(R.id.txt_appr_code);
        rlApprCode = findViewById(R.id.rl10);
    }

    @Override
    protected void initData() {
        if(orderIndex == -1)
            return;

        TransOrder transOrder = transOrders.get(orderIndex);
        if (transOrder == null) {
            return;
        }

        String cardNo = transOrder.getOrder_cardno();
        if (!TextUtils.isEmpty(cardNo)) {
            txtCardNo.setText(cardNo);
        }

        String amount = transOrder.getOrder_amount();
        if (!TextUtils.isEmpty(amount)) {
            txtTransAmount.setText("￥：" + amount);
        }

        String refNo = transOrder.getOrder_id();
        if (!TextUtils.isEmpty(refNo)) {
            txtRefNo.setText(refNo);
        }

        String dataTime = transOrder.getOrder_date_time();
        if (!TextUtils.isEmpty(dataTime)) {
            txtTransDate.setText(dataTime);
        }

        String expDate = transOrder.getOrder_expdate();
        if (!TextUtils.isEmpty(expDate)) {
            txtExpDate.setText(expDate);
        }

        int method = transOrder.getOrder_method();
        String strMethod = null;
        if(transOrder.getOrder_method() == 1)
        {
            strMethod = "SWIPE CARD";
        }
        if(transOrder.getOrder_method() == 2)
        {
            strMethod = "INSERT CARD";
        }
        if(transOrder.getOrder_method() == 4)
        {
            strMethod = "TAP CARD";
        }
        if(transOrder.getOrder_method() == 8)
        {
            strMethod = "MANUAL";
        }
        if (TextUtils.isEmpty(strMethod)) {
            txtMethod.setVisibility(View.GONE);
        }
        else
        {
            txtMethod.setVisibility(View.VISIBLE);
            txtMethod.setText(strMethod);
        }

        String apprCode = transOrder.getOrder_apprCode();
        if (TextUtils.isEmpty(apprCode)) {
            rlApprCode.setVisibility(View.GONE);
        }
        else
        {
            rlApprCode.setVisibility(View.VISIBLE);
            txtApprCode.setText(apprCode);
        }
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }
}
