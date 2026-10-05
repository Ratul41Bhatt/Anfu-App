package com.hjq.demo.ui.activity;

import android.os.Handler;
import android.text.TextUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.utils.TouchHelper;
import com.szanfu.sdk.api.ISdkServiceManager;

import org.litepal.LitePal;

public class MyTransactionActivity extends AppActivity {
    private static final String TAG = "MyTransactionActivity";

    private TextView txtTransDate;
    private TextView txtRefNo;
    private TextView txtCardNo;
    private TextView txtTransAmount;
    private TextView txtExpDate;
    private Handler handler = new Handler();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_my_transaction;
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
                TouchHelper.simulateFullTouch(MyTransactionActivity.this, centerX, centerY);
            }
        }, 200);
        txtTransDate = findViewById(R.id.txt_transdate);
        txtRefNo = findViewById(R.id.txt_refno);
        txtCardNo = findViewById(R.id.txt_cardno);
        txtTransAmount = findViewById(R.id.txt_transamount);
        txtExpDate = findViewById(R.id.txt_expdate);
    }

    @Override
    protected void initData() {
        TransOrder transOrder = LitePal.findLast(TransOrder.class); // 查询最后一条数据
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