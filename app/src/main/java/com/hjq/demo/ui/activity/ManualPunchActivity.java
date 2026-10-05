package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.RemoteException;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatButton;

import com.hjq.bar.TitleBar;
import com.hjq.demo.R;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.TouchHelper;
import com.hjq.widget.view.RegexEditText;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.KeypadValueCallback;
import com.szanfu.sdk.api.util.DataConversionUtils;

import org.apache.commons.lang3.StringUtils;

public class ManualPunchActivity extends AppActivity implements View.OnClickListener{

    private String TAG = "ManualPunchActivity";
    private ISystemBinderService mSystemBinderService;

    private LinearLayout cardNo_pin_type;
    private EditText et_cardNo;
    private AppCompatButton btn_customerPresent, btn_customerNotPresent;
    private AppCompatButton btn_cancel, btn_continue;
    private TitleBar leftIcon;
    private KeyBoardView keyBoardView;

    private boolean keyboardDevice;
    private String customerType;
    private String currentAmount;
    private String cardNo;

    private Handler mHandler = new Handler();

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
        } catch (RemoteException e)
        {
            e.printStackTrace();
        }

        getKeyCode();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_manual_cardnumber;
    }

    @Override
    protected void initView() {

        Intent intent = getIntent();
        currentAmount = intent.getStringExtra(AFConstant.CURRENT_TRANS_AMOUNT);

        cardNo_pin_type = findViewById(R.id.cardno_pin_type);
        et_cardNo = findViewById(R.id.et_card_number);
        btn_customerPresent = findViewById(R.id.btn_customer_present);
        btn_customerNotPresent = findViewById(R.id.btn_customer_not_present);
        btn_cancel = findViewById(R.id.btn_cardno_cancel);
        btn_continue = findViewById(R.id.btn_cardno_continue);
        leftIcon = findViewById(R.id.cardno_title);
        keyBoardView = findViewById(R.id.cardno_kb_view);
        keyBoardView.setButtonClickBle(false);

        btn_customerPresent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn_customerPresent.setFocusable(true);
                btn_customerPresent.setFocusableInTouchMode(true);
                btn_customerPresent.requestFocus();
            }
        });
        btn_customerNotPresent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                btn_customerNotPresent.setFocusable(true);
                btn_customerNotPresent.setFocusableInTouchMode(true);
                btn_customerNotPresent.requestFocus();
            }
        });

        btn_cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        btn_continue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectContinue();
            }
        });


        customerType = DataConversionUtils.getCustomerType();
        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
        LogUtils.e("设备类型:" + (keyboardDevice ? "820" : "930"));
        if (keyboardDevice) {
            cardNo_pin_type.setVisibility(View.GONE);
        } else {
            cardNo_pin_type.setVisibility(View.VISIBLE);
        }

        et_cardNo.setShowSoftInputOnFocus(false);   //点击编辑框时不显示系统键盘
        et_cardNo.addTextChangedListener(new TextWatcher() {
            String beforeStr;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                //beforeStr = s.toString().replace(" ", "");
                beforeStr = s.toString();
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                LogUtils.e("------onTextChanged---------");
                if (TextUtils.isEmpty(s.toString())) {
                    keyBoardView.setButtonClickBle(false);
                } else {
                    keyBoardView.setButtonClickBle(true);
                }

                if(!isChanged()) {
                    et_cardNo.setSelection(s.length());
                    return;
                }
                else {
                    formatCarNumber();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

            private boolean isChanged()
            {
                String _etStr = et_cardNo.getText().toString();
                LogUtils.e("--------beforeStr: " + beforeStr);
                LogUtils.e("--------_etStr: " + _etStr);
                return !TextUtils.isEmpty(_etStr) && !_etStr.equals(beforeStr);
            }

            private void formatCarNumber()
            {
                String input = et_cardNo.getText().toString().replace(" ", "");  //去掉空格
                StringBuilder formatted = new StringBuilder();

                for(int i =0; i<input.length(); i++)
                {
                    if(i > 0 && i % 4 == 0)
                    {
                        formatted.append(" ");
                    }
                    formatted.append(input.charAt(i));
                }

                LogUtils.e("----------formatted: " + formatted.toString());
                et_cardNo.setText(formatted.toString());
                et_cardNo.setSelection(formatted.length());
            }
        });
        et_cardNo.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                LogUtils.e("------onEditorAction: " + actionId);
                if (actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {    //确认键不允响应
                    return true;
                }
                return false;
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 2;
                float centerY = screenHeight * 0.1f;
                TouchHelper.simulateFullTouch(ManualPunchActivity.this, centerX, centerY);
                LogUtils.e("run: 触摸事件");
                if (leftIcon != null) {
                    leftIcon.setFocusable(true);
                    leftIcon.setFocusableInTouchMode(true);
                    leftIcon.requestFocus();
                }
            }
        }, 200);

    }


    @Override
    protected void initData() {
        keyBoardView.setIKetBoardListener(new KeyBoardView.IKeyBoardListener() {

            @Override
            public void getNumber(String s) {
                // TODO Auto-generated method stub
                LogUtils.e("keyBoardView.....-> " + s);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        et_cardNo.append(s);
                        cardNo = et_cardNo.getText().toString().replace(" ", "");
                        LogUtils.e("--------cardNo: " + cardNo);
                    }
                });
            }

            @Override
            public void clear() {
                // TODO Auto-generated method stub
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        et_cardNo.setText("");
                    }
                });
            }

            @Override
            public void confirm() {
                // TODO Auto-generated method stub
                selectContinue();
            }

            @Override
            public void cancel() {
                // TODO Auto-generated method stub
                int start = et_cardNo.length() - 1;
                LogUtils.i("keyBoardView: start---> " + start);
                if (start >= 0)
                {
                    et_cardNo.getText().delete(start, start + 1);
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        LogUtils.e("**************************【onRestart】**************************  ");
        super.onRestart();
        getKeyCode();
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            mSystemBinderService.closeGetKeypadValue();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void setNumberPadState(boolean able) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // Stuff that updates the UI
                keyBoardView.setNumberPadClickable(able);
            }
        });
    }

    void selectContinue()
    {
        if (leftIcon != null) {
            leftIcon.setFocusable(true);
            leftIcon.setFocusableInTouchMode(true);
            leftIcon.requestFocus();
        }

        cardNo = et_cardNo.getText().toString().replace(" ", "");
        if (cardNo.isEmpty()) {
            toast(getString(R.string.cardno_cannot_empty));
        }
        // The accepted range is an admin parameter, not 13-19: DBBL issue cards
        // from 11 digits, and a hard-coded ISO 7812 minimum turned those into a
        // refusal the operator could do nothing about. The toast names the
        // configured range so it is actionable.
        else if(!TerminalConfig.get(this).isAcceptablePanLength(cardNo.length()))
        {
            toast(getString(R.string.cardno_length_error)
                    + " (" + TerminalConfig.get(this).panLenRangeText() + " digits)");
        }
        else {
            Intent intent = new Intent(ManualPunchActivity.this, ManualExpiryApprovalActivity.class);
            intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, currentAmount);
            intent.putExtra(AFConstant.CURRENT_TRANS_CARDNO, cardNo);
            startActivity(intent);
            try {
                mSystemBinderService.closeGetKeypadValue();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取键值
     */
    private void getKeyCode() {
        LogUtils.e(" ------------ getKeyCode: 获取键值 -------------");
        try {
            mSystemBinderService.getKeypadValue(new KeypadValueCallback.Stub() {
                @Override
                public void onKeypadValueReceived(int keyCode) throws RemoteException {
                    LogUtils.e("onKeypadValueReceived.....-> " + keyCode);
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            et_cardNo.append(String.valueOf(keyCode));
                            cardNo = et_cardNo.getText().toString().replace(" ", "");
                            LogUtils.e("--------cardNo: " + cardNo);
                        }
                    });
                }

                @Override
                public void onKeypadValueFail(int errorCord) throws RemoteException {
                    LogUtils.e("onKeypadValueFail.....errorCord -> " + errorCord);
                }
            });
        } catch (RemoteException e) {
            LogUtils.e("-------getKeyCode: 获取键值异常");
            e.printStackTrace();
            getKeyCode();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.i("-----onKeyDown: 820");
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                // 确认键被按下
                LogUtils.d("Confirm (Enter/OK) button pressed");
                return true;
            }
            // 检查按键的键值
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                    LogUtils.d("确认键");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:    // 处理音量增大按键
                    LogUtils.d("Volume up button pressed");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:  // 处理音量减少按键
                    LogUtils.d("Volume down button pressed");
                    return true;
                case KeyEvent.KEYCODE_BACK: // 处理清除键->直接退出页面
                    LogUtils.d("Back button pressed");
                    finish();
                    return true;
                case KeyEvent.KEYCODE_DEL:  // CLEAR
                    LogUtils.d("Confirm button KEYCODE_DEL");
                    int start = et_cardNo.length() - 1;
                    LogUtils.i("onKeyUp: start---> " + start);
                    if (start >= 0)
                    {
                        et_cardNo.getText().delete(start, start + 1);
                    }
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }

        LogUtils.e("-------onKeyDown");
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.i("onKeyUp: 820");
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    selectContinue();
                    LogUtils.d("Confirm button released");
                    return true;
                case KeyEvent.KEYCODE_CLEAR:
                    LogUtils.d("onKeyUp: KEYCODE_CLEAR");
                    return true;
                default:
                    return super.onKeyUp(keyCode, event);
            }
        }
        LogUtils.e("----------onKeyUp");
        return super.onKeyUp(keyCode, event);
    }

}
