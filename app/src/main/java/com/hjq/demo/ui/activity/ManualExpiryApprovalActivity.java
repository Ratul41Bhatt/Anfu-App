package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

import com.blankj.utilcode.util.SPUtils;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.MessageEvent;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.model.SaleDataModel;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.utils.BitmapUtil;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.TouchHelper;
import com.hjq.widget.view.PasswordEditText;
import com.hjq.widget.view.RegexEditText;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.KeypadValueCallback;
import com.szanfu.sdk.api.util.DataConversionUtils;

import org.greenrobot.eventbus.EventBus;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import static android.graphics.Bitmap.createBitmap;

public class ManualExpiryApprovalActivity extends AppActivity implements View.OnClickListener {

    private static String TAG = "ManualExpiryApprovalActivity";

    private ISystemBinderService mSystemBinderService;
    private IPrinterBinderService mPrinterBinderService;

    private TextView tv_cardNo;
    private RegexEditText pet_expiry, pet_approvalCode;
    private AppCompatButton btn_pay;
    private LinearLayout expiry_pin_type;
    private KeyBoardView keyBoardView;

    private String curAmount;
    private String curExpiry;
    private String curCardno;
    private String curApprovalCode = "";
    StringBuffer sbMsg = new StringBuffer();
    private SaleDataModel saleDataModel = null;

    private int focusState = 0;    //聚焦所在编辑框
    private boolean keyboardDevice;
    private String customerType;

    private Bitmap signatureBmp;
    private boolean keyFlag = false;
    private boolean isBold = false;
    private int printHeight = 0;

    private SPUtils spPrint = SPUtils.getInstance("PrintResult");

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {

        try {
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
        }catch (RemoteException e)
        {
            e.printStackTrace();
        }

        //getKeyCode();
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_expiry_approval_code;
    }

    @Override
    protected void initView() {

        Intent intent = getIntent();
        curAmount = intent.getStringExtra(AFConstant.CURRENT_TRANS_AMOUNT);
        curCardno = intent.getStringExtra(AFConstant.CURRENT_TRANS_CARDNO);

        tv_cardNo = findViewById(R.id.tv_cardno);
        pet_expiry = findViewById(R.id.pet_expiry);
        pet_approvalCode = findViewById(R.id.pet_approval_code);
        btn_pay = findViewById(R.id.btn_manual_pay);
        expiry_pin_type = findViewById(R.id.expiry_pin_type);
        keyBoardView = findViewById(R.id.expiry_kb_view);
        keyBoardView.setButtonClickBle(false);

        customerType = DataConversionUtils.getCustomerType();
        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
        LogUtils.e("设备类型:" + (keyboardDevice ? "820" : "930"));
        if (keyboardDevice) {
            expiry_pin_type.setVisibility(View.GONE);
        } else {
            expiry_pin_type.setVisibility(View.VISIBLE);
        }

        String showCardno = getString(R.string.manual_card_no) + "   " + curCardno;
        tv_cardNo.setText(showCardno);

        btn_pay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onclickPay();
            }
        });

        pet_expiry.setShowSoftInputOnFocus(false);      //点击编辑框时不显示系统软键盘
        pet_expiry.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus) {
                    focusState = 1;
                    if(keyFlag == false) {
                        getKeyCode();
                        keyFlag = true;
                    }
                }
                else {
                    focusState = 0;
                }
            }
        });
        pet_expiry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s.toString())) {
                    keyBoardView.setButtonClickBle(false);
                } else {
                    keyBoardView.setButtonClickBle(true);
                }
                pet_expiry.setSelection(s.length());        //设置光标后移
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        pet_approvalCode.setShowSoftInputOnFocus(false);
        pet_approvalCode.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus)
                {
                    focusState = 2;
                    if(keyFlag == false) {
                        getKeyCode();
                        keyFlag = true;
                    }
                }
                else
                {
                    focusState = 0;
                }
            }
        });
        pet_approvalCode.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s.toString())) {
                    keyBoardView.setButtonClickBle(false);
                } else {
                    keyBoardView.setButtonClickBle(true);
                }
                pet_approvalCode.setSelection(s.length());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        pet_approvalCode.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if(actionId == EditorInfo.IME_ACTION_UNSPECIFIED)   //确认键不允响应
                {
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    protected void initData() {
        keyBoardView.setIKetBoardListener(new KeyBoardView.IKeyBoardListener() {

            @Override
            public void getNumber(String s) {
                // TODO Auto-generated method stub
                LogUtils.e("keyBoardView.....-> " + s);
                if (focusState == 1) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            pet_expiry.append(String.valueOf(s));
                        }
                    });
                }
                if (focusState == 2) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            pet_approvalCode.append(String.valueOf(s));
                        }
                    });
                }
            }

            @Override
            public void clear() {
                // TODO Auto-generated method stub
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (focusState == 1) {
                            pet_expiry.setText("");
                        }
                        if (focusState == 2) {
                            pet_approvalCode.setText("");
                        }
                    }
                });
            }

            @Override
            public void confirm() {
                // TODO Auto-generated method stub
                onclickPay();
            }

            @Override
            public void cancel() {
                // TODO Auto-generated method stub
                if(focusState == 1) {
                    int start = pet_expiry.length() -1;
                    LogUtils.i("keyBoardView: start---> " + start);
                    if (start > 0) {
                        pet_expiry.getText().delete(start, start + 1);
                    }
                }
                if(focusState == 2) {
                    int start = pet_approvalCode.length() -1;
                    LogUtils.i("keyBoardView: start---> " + start);
                    if (start >= 0) {
                        pet_approvalCode.getText().delete(start, start + 1);
                    }
                }
            }
        });
    }

    @Override
    protected void onRestart() {
        LogUtils.e("**************************【onRestart】**************************  ");
        super.onRestart();
        getKeyCode();
        keyFlag = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            mSystemBinderService.closeGetKeypadValue();
            keyFlag = false;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void onclickPay()
    {
        curExpiry = pet_expiry.getText().toString();
        curApprovalCode = pet_approvalCode.getText().toString();
        LogUtils.e("---------curExpiry： " + curExpiry);
        LogUtils.e("---------curApprovalCode： " + curApprovalCode);
        if(curExpiry.isEmpty())
        {
            toast(getString(R.string.expiry_cannot_empty));
            return;
        }
        if(curExpiry.length() != 4)
        {
            toast(getString(R.string.expiry_format_error));
            return;
        }

        if(curApprovalCode.isEmpty())
        {
            toast(getString(R.string.approval_cannot_empty));
            return;
        }
        if(curApprovalCode.length() != 6)
        {
            toast(getString(R.string.expiry_format_error));
            return;
        }

        saleDataModel = new SaleDataModel();
        saleDataModel.setTransAmount(curAmount);
        saleDataModel.setCardNo(curCardno);
        saleDataModel.setCardValid(curExpiry);
        saleDataModel.setApprovalCode(curApprovalCode);
        LogUtils.e("-------saleDataModel: " + saleDataModel.toString());
        mHandler.sendEmptyMessage(8002);
    }

    private BaseDialog mWaitDialog;
    private Handler mHandler = new Handler(Looper.myLooper())
    {
        @Override
        public void handleMessage(@NonNull Message msg) {
            LogUtils.e("----------msg.what: " + msg.what);
            switch (msg.what)
            {
                case 8002:
                    //交易处理中
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.trans_waiting))
                            .create();
                    mWaitDialog.show();
                    mHandler.sendEmptyMessageDelayed(8003, 1500);
                    break;
                case 8003:
                    LogUtils.i("打印小票...");
                    if (mWaitDialog != null) {
                        mWaitDialog.dismiss();
                    }
                    setSimulateTouch();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (DeviceType.A6.equals(customerType)) {
                                //printMsg(0);
                                printMsg();
                            }else {
                                printerTest();
                            }
                            startActivity(TransactionResultActivity.class);
                        }
                    }, 200);
                    break;
                default:
                    break;
            }
        }
    };

    private String getCardOrganization(String cardNo)
    {
        int cardNo1 = Integer.parseInt(cardNo.substring(0, 1));
        int cardNo2 = Integer.parseInt(cardNo.substring(0, 2));
        int cardNo4 = Integer.parseInt(cardNo.substring(0, 4));
        int cardNoLen = cardNo.length();

        LogUtils.e("-------cardNo1: " + cardNo1);
        LogUtils.e("-------cardNo2: " + cardNo2);
        LogUtils.e("-------cardNo4: " + cardNo4);

        //visa
        if(cardNo1 == 4 && (cardNoLen == 13 || cardNoLen == 16))
            return "VISA";
        //中国银联
        if(cardNo2 == 62 && (cardNoLen >=16 && cardNoLen <= 19))
            return "UnionPay";
        //美国运通
        if((cardNo2 == 34 || cardNo2 == 37) && cardNoLen == 15)
            return "America Express";
        //万事达
        if((cardNo2 >= 51 && cardNo2 <= 55) && cardNoLen == 16)
            return "MasterCard";
        //大来信用卡有限公司
        if((cardNo2 == 30 || cardNo2 == 36 || cardNo2 == 38 || cardNo2 == 39) && cardNoLen == 14)
            return "Diners Club";
        //JCB日本国际信用卡
        if((cardNo4 >= 3528 && cardNo4 <= 3589) && cardNoLen == 16)
            return "JCB";
        return " ";
    }


    /**
     *
     * @param mode
     * 0：  签购单标题  放大
     * 1：  商户信息
     * 2：  交易类型   放大
     * 3：  卡号信息
     * 4：  金额       放大
     * 5：  交易信息
     * 6：  空电子签名
     * 7:   签名确认
     * @return
     *
     */
    /*
    private String printMsg(int mode) {
        LogUtils.e("printMsg: The transaction is completed and the receipt is printed");
        String enCardno = "XXXXXXXXXXXXXXXXXXXX";
        String printCardno = enCardno.substring(0, curCardno.length() - 4) + curCardno.substring(curCardno.length() - 4, curCardno.length());
        String cardOrganization = getCardOrganization(curCardno);

        sbMsg.setLength(0);
        String refNo;
        String date_time;
        long currentDate = DateUtil.getTimeStamp(new Date(), 0);
        date_time = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT, 0);
        refNo = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);

        printHeight = 0;
        isBold = false;
        switch (mode)
        {

            case 0:     //签购单标题  放大
                sbMsg.append("                       Santander\n\n");
                isBold = true;
                printHeight = 1*50;
                break;
            case 1:     //商户信息
                sbMsg.append("               A Bank Of Your Ideas \n\n");
                sbMsg.append("                   merchant_name \n");
                sbMsg.append("                 merchant_address \n\n");
                sbMsg.append(getString(R.string.print_merchant_id) + "2001\n");
                sbMsg.append(getString(R.string.print_term) + "0101                " + getString(R.string.print_store_id) + "65\n");
                sbMsg.append("                          " + getString(R.string.print_ref) + refNo + "\n\n");
                printHeight = 9*32;
                break;
            case 2:     //交易类型   放大
                sbMsg.append("                          Offline\n\n");
                isBold = true;
                printHeight = 2*32;
                break;
            case 3:     //卡号信息
                sbMsg.append(printCardno + "\n");
                //sbMsg.append(cardOrganization + "                 " + getString(R.string.print_method) + "Manual\n\n");
                sbMsg.append(printFormat(cardOrganization, getString(R.string.print_method1) + "Manual\n\n"));
                printHeight = 3*32;
                break;
            case 4:     //金额       放大
                sbMsg.append(printFormat(getString(R.string.print_total), getString(R.string.print_total_sign) + curAmount) + "\n\n");
                isBold = true;
                printHeight = 2*32;
                break;
            case 5:     //交易信息
                sbMsg.append(date_time.substring(0, 10) + "                           " + date_time.substring(10, date_time.length()) + "\n");
                sbMsg.append(printFormat(getString(R.string.print_inv) + "5", getString(R.string.print_appr_code1) + curApprovalCode + "\n"));
                sbMsg.append(getString(R.string.print_transaction_id) + "5\n");
                sbMsg.append(getString(R.string.print_apprvd) + "Offline" + "              " + getString(R.string.print_batch) + "160\n");
                printHeight = 4*32;
                break;
            case 6:     //空电子签名
                sbMsg.append("\n\n\n\n");
                printHeight = 4*32;
                break;
            case 7:     //签名确认
                sbMsg.append(getString(R.string.print_sign_tip) + "\n");
                sbMsg.append("------------------------------------------------------\n");
                sbMsg.append("                   " + getString(R.string.print_merchant_copy) + "\n");
                sbMsg.append("                       " + getString(R.string.print_thank_you) + "\n");
                printHeight = 7*32;
                break;
            default:
                break;
        }

        if(mode == 0) {
            savaTrans(refNo, date_time);
        }
        LogUtils.i("-------printMsg: " + sbMsg.toString());
        LogUtils.i("-------printHeight: " + printHeight);
        return sbMsg.toString();
    }


    private void printerTest() {
        try {
            Bitmap printBmp;
            printBmp = creatTextBitmap(printMsg(0), 384, printHeight);
            printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(1), 384, printHeight), false);
            printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(2), 384, printHeight), false);
            printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(3), 384, printHeight), false);
            printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(4), 384, printHeight), false);
            printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(5), 384, printHeight), false);
            if (signatureBmp != null) {
                //Bitmap textBmp = creatTextBitmap(printMsg(), 384, 850);
                //两张bitmap拼接成一张bitmap
                //printBmp = BitmapUtil.addBitmap(textBmp, signatureBmp);
                printBmp = BitmapUtil.addBitmap(printBmp, signatureBmp, false);
                printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(7), 384, printHeight), true);
            } else {
                //printBmp = creatTextBitmap(printMsg(), 384, 1200);
                printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(6), 384, printHeight), false);
                printBmp = BitmapUtil.addBitmap(printBmp, creatTextBitmap(printMsg(7), 384, printHeight), true);
            }

            LogUtils.i("bmp width " + printBmp.getWidth() + "\n" + "bmp height " + printBmp.getHeight() + "\n");
            int x = 0;
            int width = printBmp.getWidth();
            int height = printBmp.getHeight();
            int bitW = PrintUtil.getPaddingBitWidth(width);
            byte[] bitmapData = PrintUtil.getBitmapData(printBmp);

            mPrinterBinderService.printBitmap(x, bitmapData, bitW, height, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
                    EventBus.getDefault().post(new MessageEvent(errorCode, errorMsg));
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
     */

    private String printMsg() {
        LogUtils.e( "printMsg: The transaction is completed and the receipt is printed");
        sbMsg.setLength(0);
        String id;
        String date_time;
        long currentDate = DateUtil.getTimeStamp(new Date(), 0);
        sbMsg.append("        Dutch Bangla Bank PLC\n");
        sbMsg.append("------------------------------------------------------\n");
        date_time = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT, 0);
        sbMsg.append(getString(R.string.print_date_time) + "\t\t\t" + date_time + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append("                          SALE");
        sbMsg.append("\n\n");
        sbMsg.append(getString(R.string.print_merchant_name) + "\n                              DBB PLC TEST POS\n");
        sbMsg.append(getString(R.string.print_terminal_no) + "\n                                              " + 48290000 + "\n");
        sbMsg.append(getString(R.string.print_operator_no) + "\n                                              00000001\n");
        sbMsg.append(getString(R.string.print_card_no) + "\n" + printFormat(curCardno) + "\n");
        sbMsg.append(getString(R.string.print_txn_type) + "\n" + printFormat(getString(R.string.print_txn_type_info)) + "\n");
        sbMsg.append(getString(R.string.print_method) + "\n" + printFormat(getString(R.string.print_method_manual))+ "\n");
        sbMsg.append(getString(R.string.print_exp) + "\n" + printFormat(curExpiry) + "\n");
        id = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);
        sbMsg.append(getString(R.string.print_ref_no) + "\n                                     " + id + "\n");
        sbMsg.append(getString(R.string.print_appr_code) + "\n                                                  " + curApprovalCode + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_amount) + curAmount + "\n");
        sbMsg.append("------------------------------------------------------\n\n");
        sbMsg.append(getString(R.string.sing_tip) + "\n\n");
        sbMsg.append(getString(R.string.sign) + "\n\n");
        savaTrans(id, date_time);
        LogUtils.i( "printMsg: " + sbMsg.toString());
        return sbMsg.toString();
    }

    private void printerTest() {
        spPrint.clear();
        try {
            Bitmap printBmp;
            if (signatureBmp != null) {
                Bitmap textBmp = creatTextBitmap(printMsg(), 384, 910);
                //两张bitmap拼接成一张bitmap
                printBmp = BitmapUtil.addBitmap(textBmp, signatureBmp, true);
            } else {
                printBmp = creatTextBitmap(printMsg(), 384, 1260);
            }

            LogUtils.i( "bmp width " + printBmp.getWidth() + "\n" + "bmp height " + printBmp.getHeight() + "\n");
            int x = 0;
            int width = printBmp.getWidth();
            int height = printBmp.getHeight();
            int bitW = PrintUtil.getPaddingBitWidth(width);
            byte[] bitmapData = PrintUtil.getBitmapData(printBmp);

            mPrinterBinderService.printBitmap(x, bitmapData, bitW, height, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
                    EventBus.getDefault().post(new MessageEvent(errorCode, errorMsg));
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将文字 生成 文字图片 的Bitmap, 打印的位图需要 黑字 白底(A Bitmap that generates text into text images. Printed bitmaps need black text on a white background)
     */
    public Bitmap creatTextBitmap(String text, int width, int height) {
        Bitmap newBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(Color.WHITE);

        canvas.drawBitmap(newBitmap, 0, 0, null);
        TextPaint textPaint = new TextPaint();

        if(isBold) {
            String familyName = "宋体";
            Typeface font = Typeface.create(familyName, Typeface.BOLD);
            textPaint.setTypeface(font);
        }

        textPaint.setAntiAlias(true);
        textPaint.setTextSize(24);
        textPaint.setColor(Color.BLACK);
        // "在Android开发中，Canvas.drawText不会换行，即使一个很长的字符串也只会显示一行，超出部分会隐藏在屏幕之外.StaticLayout是android中处理文字的一个工具类，StaticLayout 处理了文字换行的问题";
        StaticLayout sl = new StaticLayout(text, textPaint, newBitmap.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        textPaint.setStyle(Paint.Style.FILL);
        canvas.translate(0, height / 10);
        sl.draw(canvas);
        return newBitmap;
    }

    private String printFormat(String inputStr)
    {
        String whiteStr = "                                                                ";
        int subLen = 0;
        float instrLen = 0;
        int a[] = {0, 0, 0, 0};

        if (inputStr == null || inputStr.isEmpty()) {
            return null;
        }

        if(isPureChinese(inputStr)) {
            subLen = (int) (whiteStr.length() - (float) inputStr.length() * (64/16) - 1);
            LogUtils.e("------zh----strlen: " + inputStr + "  " + subLen);
        }
        else {
            instrLen = 0;
            for(int i =0; i<inputStr.length(); i++) {
                char c = inputStr.charAt(i);
                if (c == ' ' || c == '-' || c == ':' || c == '.' || c == 'i' || c == 'j' || c == 'l' || c == 'I')
                {
                    a[0]++;     //64
                }
                else if (c >= '0' && c <= '9')
                {
                    a[1]++;     //29
                }
                else if(c == 'm' || c == 'w' || c == 'M' || c == 'W' || c == '$')
                {
                    a[2]++;     //18
                }
                else
                {
                    a[3]++;     //24
                }
            }
            LogUtils.e("--------a[0]: " + a[0] + "   a[1]: " + a[1] + "   a[2]: " + a[2] + "   a[3]: " + a[3]);
            instrLen = ((float)64/64)*a[0] + ((float)64/29)*a[1] + ((float)64/18)*a[2] + ((float)64/24)*a[3];
            subLen = whiteStr.length() - (int)Math.ceil(instrLen);
            LogUtils.e("------en----strlen: " + inputStr + "  " + subLen + "  " + instrLen);
        }
        String retStr = whiteStr.substring(0, subLen) + inputStr;
        return retStr;
    }

    public  boolean isPureChinese(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        // 定义中文字符的 Unicode 范围
        String regex = "[\\u4E00-\\u9FA5]+";
        return str.matches(regex);
    }

    private Map<Character, Integer> countCharacter(String str)
    {
        Map<Character, Integer> charCount = new HashMap<>();
        charCount.put(' ', 0);
        charCount.put('a', 0);
        charCount.put('r', 0);
        charCount.put('m', 0);
        charCount.put('A', 0);
        charCount.put('￥', 0);
        if(str != null)
        {
            for(int i = 0; i<str.length(); i++)
            {
                char c = str.charAt(i);
                if(c == ' ' || c == '-' || c == ':' || c == '.'
                        || c == 'i' || c == 'j' || c == 'l' || c == 'I')
                {
                    charCount.put(' ', charCount.get(' ') + 1);     //64
                }
                else if(c == 'm' || c == 'w' || c == 'M' || c == 'W'
                        || c == '$') {
                    charCount.put('m', charCount.get('m') + 1);     //18
                }
                else if(c == '￥')
                {
                    charCount.put('￥', charCount.get('￥') + 1);     //10
                }
                else if((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c =='_')
                {
                    if(c == 'r' || c == 't' || c == 'f')
                        charCount.put('r', charCount.get('r') + 1);     //48
                    else
                        charCount.put('a', charCount.get('a') + 1);      //29
                }
                else if((c >= 'A' && c <= 'Z') || c == '#')
                {
                    charCount.put('A', charCount.get('A') + 1);     //24
                }
            }
        }

        Iterator<Map.Entry<Character, Integer>> iterator = charCount.entrySet().iterator();
        while(iterator.hasNext())
        {
            Map.Entry<Character, Integer> entry = iterator.next();
            LogUtils.e("-------key: " + entry.getKey() + ",  value: " + entry.getValue());
        }

        return charCount;
    }

    private String printFormat1(String str1, String str2)
    {
        String initData = "                                                              ";
        int lineLen = initData.length();
        int differLen;
        int str1Len = 0, str2Len = 0;
        StringBuilder newStr = new StringBuilder();
        float fcharLen = 0;
        int charLen = 0;

        if(str1 == null && str2 == null)
            return "";
        if(str2 != null)
            str2Len = str2.length();

        Map<Character, Integer> charCount1 = null;
        Map<Character, Integer> charCount2 = null;
        if(str1 != null)
        {
            str1Len = str1.length();
            charCount1 = countCharacter(str1);
        }
        if(str2 != null)
        {
            str2Len = str2.length();
            charCount2 = countCharacter(str2);
        }

        if(str2 == null)    //str1 居中
        {
            fcharLen = charCount1.get(' ')
                    + (((float)lineLen/29) * charCount1.get('a'))
                    + (((float)lineLen/48) * charCount1.get('r'))
                    + (((float)lineLen/18) * charCount1.get('m'))
                    + (((float)lineLen/24) * charCount1.get('A'))
                    + (((float)lineLen/10) * charCount1.get('￥'));

            if(fcharLen > lineLen)
            {
                return str1;
            }
            charLen = Math.round(fcharLen);
            differLen = (lineLen - charLen)/2;
            LogUtils.e("------0----lineLen: " + lineLen);
            LogUtils.e("----------charLen: " + charLen);
            LogUtils.e("----------differLen: " + differLen);
            newStr.append(initData.substring(0, differLen) + str1);
            LogUtils.e("----------newStr: " + newStr.toString());
            return newStr.toString();
        }

        //靠两边显示
        fcharLen = charCount1.get(' ')
                + (((float)lineLen/29) * charCount1.get('a'))
                + (((float)lineLen/48) * charCount1.get('r'))
                + (((float)lineLen/18) * charCount1.get('m'))
                + (((float)lineLen/24) * charCount1.get('A'))
                + (((float)lineLen/10) * charCount1.get('￥'))
                + charCount2.get(' ')
                + (((float)lineLen/29) * charCount2.get('a'))
                + (((float)lineLen/48) * charCount2.get('r'))
                + (((float)lineLen/18) * charCount2.get('m'))
                + (((float)lineLen/24) * charCount2.get('A'))
                + (((float)lineLen/10) * charCount2.get('￥'));

        if(fcharLen > lineLen)
            return str1 + str2;

        LogUtils.e("-------fcharLen: " + fcharLen);

        charLen = (int)fcharLen + 1;
        differLen = lineLen - charLen;
        LogUtils.e("----------lineLen: " + lineLen);
        LogUtils.e("----------charLen: " + charLen);
        LogUtils.e("----------differLen: " + differLen);
        newStr.append(str1 + initData.substring(0,differLen) + str2);
        LogUtils.e("----------newStr: " + newStr.toString());

        return newStr.toString();
    }

    /**
     * 生成随机数
     *
     * @param length 生成的长度
     * @return 随机数
     */
    public static String generateRandomNumber(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        LogUtils.e("generateRandomNumber: " + sb.toString());
        return sb.toString();
    }

    private void savaTrans(String id, String date_time) {
        TransOrder order = new TransOrder();
        order.setOrder_amount(curAmount);
        order.setOrder_id(id);
        order.setOrder_date_time(date_time);
        order.setOrder_cardno(curCardno);
        order.setOrder_expdate(curExpiry);
        order.setOrder_method(8);
        order.setOrder_apprCode(curApprovalCode);
        LogUtils.i("savaTrans: id" + order.toString());
        boolean saveRes = order.save();
        LogUtils.i("savaTrans: " + saveRes);
    }


    private void setSimulateTouch() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.2f;
                float centerY = screenHeight / 1.2f;
                TouchHelper.simulateFullTouch(ManualExpiryApprovalActivity.this, centerX, centerY);
            }
        }, 200);
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
                    if (focusState == 1) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                pet_expiry.append(String.valueOf(keyCode));
                            }
                        });
                    }
                    if (focusState == 2) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                pet_approvalCode.append(String.valueOf(keyCode));
                            }
                        });
                    }
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
                    if(focusState == 1) {
                        int start = pet_expiry.length() -1;
                        LogUtils.i("onKeyUp: start---> " + start);
                        if (start > 0) {
                            pet_expiry.getText().delete(start, start + 1);
                        }
                    }
                    if(focusState == 2) {
                        int start = pet_approvalCode.length() -1;
                        LogUtils.i("onKeyUp: start---> " + start);
                        if (start >= 0) {
                            pet_approvalCode.getText().delete(start, start + 1);
                        }
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
                    onclickPay();
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
