package com.hjq.demo.ui.activity;

import static android.graphics.Bitmap.createBitmap;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.TimeUtils;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.LcdInfo;
import com.hjq.demo.entity.TransReceiptPrintEntity;
import com.hjq.demo.entity.WorkingKeyEntity;
import com.hjq.demo.ilistener.MainListener;
import com.hjq.demo.inter.AFSwipeControllerListener;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.model.MainModel;
import com.hjq.demo.model.MainModelImpl;
import com.hjq.demo.model.SaleDataModel;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.ui.dialog.PayPasswordDialog;
import com.hjq.demo.ui.dialog.SignatureDialog;
import com.hjq.demo.ui.dialog.TipsDialog;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.ui.view.KeyBoardView.IKeyBoardListener;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.AidChoiceDialog;
import com.hjq.demo.utils.AppPreferences;
import com.hjq.demo.utils.BitmapUtil;
import com.hjq.demo.utils.CardSearchType;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.IntToBytesUtils;
import com.hjq.demo.utils.LcdUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.PowerReceiver;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.TLVModel;
import com.hjq.demo.utils.TouchHelper;
import com.hjq.demo.widget.DynamicPinPad;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.card.ICardBinderService;
import com.szanfu.sdk.api.card.ICardTestCallback;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.emv.EmvGetKernelProgressResult;
import com.szanfu.sdk.api.emv.IEmvBinderService;
import com.szanfu.sdk.api.emv.IKernelEmvCallback;
import com.szanfu.sdk.api.emv.IKernelEmvLicenseCallback;
import com.szanfu.sdk.api.emv.IKernelEmvSystemTimerCallback;
import com.szanfu.sdk.api.emv.IKernelEmvlcdPrintExCallback;
import com.szanfu.sdk.api.emv.ISdkKernelEmvAppCallback;
import com.szanfu.sdk.api.emv.ISdkKernelEmvInputPINCallback;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.IPinPadCallback;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.KeyInfoParam;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.ISystemEncDecBinderService;
import com.szanfu.sdk.api.system.KeypadValueCallback;
import com.szanfu.sdk.api.util.DataConversionUtils;
import com.szanfu.sdk.api.util.HexUtil;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AmountActivity extends AppActivity {
    private static final String TAG = "AmountActivity";

    /**
     * Accepted PIN length, ISO 9564: 4 to 12 digits. See the matching constants
     * in {@link FindCardActivity} - both screens must agree, or a PIN entered on
     * one path produces a block the host accepts and the other does not.
     */
    private static final int PIN_MIN_LEN = 4;
    private static final int PIN_MAX_LEN = 12;
    /** PIN entry timeout in seconds. SDK range is 5..180. */
    private static final int PIN_TIMEOUT_SEC = 60;

    private TextView tvType;
    private EditText etAmount;
    private KeyBoardView keyBoardView;
    private TextView tvTitle;
    private LinearLayout amount_pin_type;
    private TitleBar leftIcon;

    private int consumptionType;
    public static final int FIND_CARD_TIMEOUT = 30;
    private ICardBinderService mCardBinderService;
    private IEmvBinderService mEmvBinderService;
    private static ISystemBinderService mSystemBinderService;
    private IPinPadBinderService mPinPadBinderService;
    private IPrinterBinderService mPrinterBinderService;
    private ISystemEncDecBinderService mISystemEncDecBinderService;
    private static final int MSG_TRANS_MSR_SUCCESS = 0;
    private static final int MSG_TRANS_IC_RF_SUCCESS = 1;
    private static final int MSG_TRANS_FAil = 2;
    private static final int MSG_SHOW_SIGNATURE_DIALOG = 3;
    protected ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //当前卡号
    private static String currentCardNumber;
    //Tip Card Number
    private static String tipCardNumber;
    //cardValid
    private String cardValid;
    //当前金额
    private static String currentAmount;
    private MainModel mainModel;
    public static final int MAX_BIT_WIDTH = 300;
    public static final int MAX_BIT_HEIGHT = 200;
    public static final long MIN_SIGNATURE_LIMIT = 20000;
    public static final long MAX_SIGNATURE_LIMIT = 200000;
    StringBuffer sbMsg = new StringBuffer();

    private static SimpleDateFormat SDF_TAG_9A_YYDD = new SimpleDateFormat("yyMMdd");//交易日期
    private static SimpleDateFormat SDF_TAG_9A_HMS = new SimpleDateFormat("HHmmss");//交易时间
    private static SimpleDateFormat SDF_TAG_9A = new SimpleDateFormat("yyMMddHHmmss");//交易日期

    private static SimpleDateFormat SDF_DEVICES_TEST = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //设备是否需要签到
    private SPUtils spUtils = SPUtils.getInstance("SP_SignTime");
    //初始金额
    private static final String INITIAL_AMOUNT_OF_MONEY = "0.00";

    private SaleDataModel saleDataModel = null;

    PowerReceiver mPowerReceiver = null;

    PayPasswordDialog.Builder payPasswordDialog;
    SignatureDialog.Builder signatureDialog;

    private Bitmap signatureBmp;

    private String icCardNumber;

    private boolean isSearchingCard = false;
    private static final int ERROR_LOW_BATTERY = -3006;
    private static final int ERROR_PRINT_OUT_OF_PAPER = -4002;
    private static final int ERROR_PRINTER_OVER_TEMPERATUR = -4005;

    /**
     * 交易模式
     * Trading mode
     * true:联机交易 false 脱机交易
     * <p>
     * true :online trading  false :offline trading,
     */
    public static final boolean FORMAL_TRANSACTION = false;
    /**
     * 今日签到状态
     */
    private static boolean signedStatus = false;


    /**
     * 交易金额
     * Amount of the transaction
     */
    private String transAmount;
    /**
     * 重启内核
     * Transaction Kernel Restart
     */
    public static final int ERR_KERNEL_RESTART = -8;

    /**
     * 交易小票数据
     * Transaction receipt information
     */
    private TransReceiptPrintEntity printEntity = null;

    /**
     * 当前交易卡类型
     */
    private int cardType;
    private boolean mCardConfirmed = false;

    //确认键事件类型
    private int KEYCODE_ENTER_ACTION = -1;
    //发起寻卡交易
    private static final int KEYCODE_ENTER_START_SEARCH_CARD = 0;
    //输入PIN
    private static final int KEYCODE_ENTER_INPUT_PIN = 1;


    //设备类型 820
    private boolean keyboardDevice;

    private String customerType;


    private boolean isLcdSupported;

    private LcdUtils lcdUtils;
    private LcdInfo lcdInfo;

    private boolean mInternalAmountUpdate = false;

    // 金额以“分”为单位，避免字符串拼接造成的临时错误格式
    private long amountCents = 0;

    // LCD 刷新节流
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable lcdUpdateTask;
    private String pendingLcdAmount = null;

    private void appendDigit(int digit) {
        if (digit < 0 || digit > 9) return;

        // 可选：限制最大金额，防止无限输入（这里例子：最多 99999999.99）
        if (amountCents > 9999999999L) return;

        amountCents = amountCents * 10 + digit;
        updateAmountViews();
    }

    private void backspaceDigit() {
        amountCents = amountCents / 10;
        updateAmountViews();
    }

    private void clearAmount() {
        amountCents = 0;
        updateAmountViews();
    }

    private void updateAmountViews() {
        String show = String.format(Locale.US, "%.2f", amountCents / 100.0);
        setEditText(show);
        requestLcdUpdate(show);
    }


    private void requestLcdUpdate(String amountStr) {
        if (!isLcdSupported || lcdInfo == null) return;

        pendingLcdAmount = amountStr;

        if (lcdUpdateTask == null) {
            lcdUpdateTask = new Runnable() {
                @Override
                public void run() {
                    if (pendingLcdAmount == null) return;
                    try {
                        displayTransactionAmountASCII(Double.parseDouble(pendingLcdAmount));
                    } catch (Exception ignore) {
                    }
                }
            };
        }

        uiHandler.removeCallbacks(lcdUpdateTask);
        uiHandler.postDelayed(lcdUpdateTask, 16); // 16ms 合并刷新
    }


    private void unRegisterBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unregisterReceiver(mPowerReceiver);
            mPowerReceiver = null;
        }

        LogUtils.i("onDestroy: isSearchingCard->" + (isSearchingCard ? "正在寻卡" : "空闲状态"));
        /*if (!isSearchingCard) {
            try {
                mCardBinderService.closeFindCardForEMV();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }*/

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        LogUtils.e("**************************【onDestroy】************************** ");
        try {
            mSystemBinderService.closeGetKeypadValue();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        unRegisterBroadCastReceiver();

    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LogUtils.e("**************************【onRestart】**************************  ");
        getKeyCode();
        lcdClearScreen();
        SPUtils spUtils = SPUtils.getInstance("PrintResult");
        if (spUtils.getBoolean("PrintFinish", false)) {
            spUtils.clear();
            setEditText(INITIAL_AMOUNT_OF_MONEY);
            displayTransactionAmountASCII(Double.valueOf(INITIAL_AMOUNT_OF_MONEY));
        } else {
            LogUtils.e("Amount:\t" + etAmount.getText().toString());
            displayTransactionAmountASCII(Double.valueOf(etAmount.getText().toString()));
        }

        SPUtils errSp = SPUtils.getInstance("ErrorResult");
        if (errSp.contains("errCode")) {
            int errCode = errSp.getInt("errCode");
            Log.e(TAG, "errCode: " + errCode);
            switch (errCode) {
                case -5011:
                    devicesSign();
                    break;
                default:
                    break;
            }
            errSp.clear();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.e("**************************【onStop】************************** ");
        try {
            mSystemBinderService.closeGetKeypadValue();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        unRegisterBroadCastReceiver();
    }


    private void registerBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unRegisterBroadCastReceiver();
        }
        mPowerReceiver = new PowerReceiver(new PowerReceiver.DeviceStatusMonitoring() {
            @Override
            public void onShutDown() {
                LogUtils.i("onShutDown: --->Phone off.....");
                //spUtils.put(END_TEST_TIME, SDF_DEVICES_TEST.format(new Date()));
            }
        });
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        intentFilter.addAction("android.intent.action.REBOOT");
        registerReceiver(mPowerReceiver, intentFilter);
        LogUtils.i("registerBroadCastReceiver: 已经注册广播...");
    }

    public static ISystemBinderService getSystemSingleton() {
        return mSystemBinderService;
    }

    private String printMsg() {
        LogUtils.e("printMsg: The transaction is completed and the receipt is printed");
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
        sbMsg.append(getString(R.string.print_merchant_name) + "\n                            DBB PLC TEST \n");
        sbMsg.append(getString(R.string.print_terminal_no) + "\n                                              " + 48290000 + "\n");
        sbMsg.append(getString(R.string.print_operator_no) + "\n                                              00000001\n");
        sbMsg.append(getString(R.string.print_card_no) + "\n                       " + tipCardNumber + "\n");
        sbMsg.append(getString(R.string.print_txn_type) + "\n                                                      " + getString(R.string.print_txn_type_info) + "\n");
        sbMsg.append(getString(R.string.print_exp) + "\n                                                " + cardValid + "\n");
        id = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);
        sbMsg.append(getString(R.string.print_ref_no) + "\n                                     " + id + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_amount) + getAmount() + "\n");
        sbMsg.append("------------------------------------------------------\n\n");
        sbMsg.append(getString(R.string.sing_tip) + "\n\n");
        sbMsg.append(getString(R.string.sign) + "\n\n");
        savaTrans(id, date_time);
        LogUtils.i("printMsg: " + sbMsg.toString());
        return sbMsg.toString();
    }

    private void savaTrans(String id, String date_time) {
        TransOrder order = new TransOrder();
        order.setOrder_amount(currentAmount);
        order.setOrder_id(id);
        order.setOrder_date_time(date_time);
        order.setOrder_cardno(tipCardNumber);
        order.setOrder_expdate(cardValid);
        LogUtils.i("savaTrans: id" + order.toString());
        boolean saveRes = order.save();
        LogUtils.i("savaTrans: " + saveRes);
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
        LogUtils.i("generateRandomNumber: " + sb.toString());
        return sb.toString();
    }

    /**
     * 等待对话框
     */
    private BaseDialog mWaitDialog;
    private Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 3000:
                    disDialog();
                    LogUtils.i("stopPinPad: Success");
                    Bundle printErr = msg.getData();
                    String print_err_info = printErr.get("print_err").toString();
                    new MessageDialog.Builder(getActivity())
                            .setTitle(R.string.pop_notice)
                            .setMessage(print_err_info)
                            .setConfirm(getString(R.string.common_confirm))
                            .setCancel(null)
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    toast("确定");
                                    keyBoardView.setButtonClickBle(true);
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
                                }
                            })
                            .show();
                    break;
                case 3001:
                    disDialog();
                    //寻卡超时
                    resetSP();
                    keyBoardView.setButtonClickBle(true);
                    break;
                case 8000:
                    //开始寻卡
                    isSearchingCard = true;
                    showDialog(getString(R.string.common_loading));
                    keyBoardView.setButtonClickBle(true);
                    break;
                case 8001:
                    //磁条卡
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.swipe_card))
                            .create();
                    mWaitDialog.show();
                    break;
                case 8002:
                    //交易处理中
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.trans_waiting))
                            .create();
                    mWaitDialog.show();
                    mHandler.sendEmptyMessageDelayed(8003, 1000);
                    break;
                case 8003:
                    LogUtils.i("打印小票...");
                    if (mWaitDialog != null) {
                        mWaitDialog.dismiss();
                    }
                    //打印小票
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.print_state))
                            .create();
                    mWaitDialog.show();
                    keyBoardView.setButtonClickBle(true);
                    printerTest();
                    break;
                case 8004:
                    //寻卡失败
                    isSearchingCard = false;
                    disDialog();
                    break;
                case 8005:
                    //读卡中
                    showDialog(getString(R.string.reading_card_wait));
                    break;
                case MSG_TRANS_MSR_SUCCESS:
                    //磁条卡
                    isSearchingCard = false;
                    disDialog();
                    Bundle cardData = msg.getData();
                    String cardNumber = cardData.get("track2Data").toString();

                    if (cardNumber.equals("Read Card Error")) {
                        toast(getString(R.string.card_number_read_error));
                        return;
                    }

                    if (cardNumber.contains("F")) {
                        cardNumber.replaceAll("F", "");
                    }
                    //6228480018292192277F
                    // 消息对话框
                    new MessageDialog.Builder(getActivity())
                            .setTitle(R.string.trans_success)
                            .setCancelable(false)
                            .setMessage(cardNumber)
                            .setConfirm(getString(R.string.common_confirm))
                            .setCancel(getString(R.string.trans_canceled))
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {
                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    showPinPad(true, currentCardNumber, 1);
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    dialog.dismiss();
                                    toast(getString(R.string.trans_canceled));
                                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                                    getKeyCode();
                                }
                            })
                            .show();
                    break;
                case MSG_TRANS_IC_RF_SUCCESS:
                    LogUtils.e("handleMessage: MSG_TRANS_IC_RF_SUCCESS");
                    //ic卡
                    isSearchingCard = false;
                    disDialog();
                    Bundle cardDataBundle = msg.getData();
                    icCardNumber = cardDataBundle.get("cardData").toString();
                    //int cardType = Integer.parseInt(cardDataBundle.get("cardType").toString());
                    LogUtils.i("icCardNumber:" + icCardNumber);
                    //LogUtil.i(TAG, "cardType:" + cardType);
                    int icCardNumberLen = icCardNumber.length();
                    if (icCardNumber.contains("F")) {
                        icCardNumber = icCardNumber.replaceAll("F", "");
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 10).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    } else {
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 9).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    }
                    LogUtils.i("icCardTip:" + currentCardNumber);
                    //mHandler.sendEmptyMessageDelayed(8003, 1000);
//                    enterPinBlock();
                    //$1 $2 表示正则表达式里面的第一个和第二个，也就是括号里面的内容
                    // 消息对话框
                    new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_success)
                            //设置按下返回键不能消失
                            .setCancelable(false)
                            // 内容必须要填写
                            .setMessage(icCardNumber)
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(getString(R.string.trans_canceled))
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    //toast("确定");
                                    showPinPad(true, icCardNumber, 1);
//                                    showCustomizePinPad();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    dialog.dismiss();
                                    toast(getString(R.string.trans_canceled));
//                                    etAmount.setText("￥0.00");
                                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                                    //keyBoardView.setButtonClickBle(true);
                                }
                            }).show();
                    break;
                case MSG_TRANS_FAil:
                    disDialog();
                    resetSP();
                    keyBoardView.setButtonClickBle(true);
                    Bundle cardDataFail = msg.getData();
                    // 消息对话框
                    new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_fail)
                            // 内容必须要填写
                            .setMessage(cardDataFail.get("errInfo").toString())
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(null)
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    // toast(R.string.amount_confirm);
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    // toast(R.string.amount_clear);
                                }
                            }).show();
                    break;
                case MSG_SHOW_SIGNATURE_DIALOG:
                    showSignaturePad();
                    break;
                default:
                    break;
            }
        }
    };


    /**
     * 用户输入密码弹窗
     *
     * @param keyIndex               The key index allows users to operate 9 groups. The valid index value ranges from 2 to 10
     *                               秘钥索引,可供用户操作9组,合法索引值为2-10
     * @param setPasswordLengthLimit Set PinPad parameters, including whether an empty password is supported and whether the password length can be entered. The value is 0, 6,6. The first digit 0 cannot be pressed directly
     *                               设置PinPad参数，是否支持空密码和支持输入密码长度位数,有效值[0, 6,6] 第一位0不允许直接按OK 1：允许直接按OK
     * @param hasCancelButton        Whether to CANCEL is required true: Yes false: No [Valid only for 930 devices]
     *                               是否需要CANCEL按键 true：需要 false:不需要 【只对930设备生效】
     * @param amount                 Transaction amount
     *                               交易金额
     * @param cardNumber             Bank card number, between 12 and 19 in length
     *                               银行卡号,长度在12-19之间
     * @param encryptionMode         Encryption mode 0:DES/3DES 1:SM4
     *                               加密模式 0:DES/3DES 1:SM4
     * @param inputPwdTimeout        The maximum timeout period for entering a password is 3 minutes and the minimum timeout period is 5 seconds. The timeout period starts when the password keyboard is displayed
     *                               输入密码超时时间，单位S，最长不应超过3分钟,最短不建议少于5秒，从密码键盘显示开始计时
     * @param ucPinType              0.脱机PIN 1.联机PIN
     * @param pinPadCallback         Enter a password-related listening callback
     *                               输入密码相关监听回调
     *                               <p>
     *                               <p>
     *                               param keyIndex               The key index allows users to operate 9 groups. The valid index value ranges from 2 to 10
     *                               param setPasswordLengthLimit   Set PinPad parameters, including whether an empty password is supported and whether the password length can be entered. The value is 0, 6,6. The first digit 0 cannot be pressed directly
     *                               param hasCancelButton   Whether to CANCEL is required true: Yes false: No [Valid only for 930 devices]
     *                               param amount          Transaction amount
     *                               param cardNumber      Bank card number, between 12 and 19 in length
     *                               param encryptionMode  Encryption mode 0:DES/3DES 1:SM4
     *                               param inputPwdTimeout The maximum timeout period for entering a password is 3 minutes and the minimum timeout period is 5 seconds. The timeout period starts when the password keyboard is displayed
     *                               param pinPadCallback  Enter a password-related listening callback
     */

    private void showPinPad(boolean hasCancel, String backCardNumber, int ucPinType) {
        LogUtils.i("showPinPad: backCardNumber===>" + backCardNumber);
        KEYCODE_ENTER_ACTION = KEYCODE_ENTER_INPUT_PIN;
        try {
            // The first element of setPasswordLengthLimit MUST be 0.
            //
            // It used to be 1, which tells the pad an EMPTY PIN is acceptable
            // and lets the cardholder press OK on nothing. The pad then returns
            // an ISO 9564 format 0 block whose length nibble is 0, and the
            // acquirer's HSM rejects it with Thales command DA error 24,
            // "PIN is fewer than 4 or more than 12 digits in length". That is
            // the PIN length error.
            //
            // The range is also no longer pinned at 4,4 - see the notes in
            // FindCardActivity.showPinPad and in DynamicPinPad. The key index
            // comes from the terminal config rather than the literal 2, so it
            // always matches the slot the logon wrote the DE62 working key to.
            int keyIndex = TerminalConfig.get(getActivity()).mkIndex();
            DynamicPinPad.show(AmountActivity.this, mPinPadBinderService,
                    keyIndex, PIN_MIN_LEN, PIN_MAX_LEN, hasCancel, currentAmount,
                    backCardNumber, 0, PIN_TIMEOUT_SEC, ucPinType, new IPinPadCallback.Stub() {
                @Override
                public void onGetPinBlock(boolean isPasswordFree, String onlinePin) throws RemoteException {
                    //免密支付
                    if (isPasswordFree) {
                        LogUtils.i("Password-free payment \n");
                    } else {
                        LogUtils.i("onlinePin: " + onlinePin + "\n");
                        saleDataModel.setPinBlock(onlinePin);
                    }
                    if (cardType == CardSearchType.MAGNETIC_STRIPE_CARD || cardType == CardSearchType.RF_CARD) {
                        toast(getString(R.string.start_trans));
                        mHandler.sendEmptyMessageDelayed(8003, 1000);
                    }
                }

                @Override
                public void onPinPadError(int errCode) throws RemoteException {
                    LogUtils.i("onPinPadError: " + errCode + "\n");
                    toast("onPinPadError" + errCode);
                }


                @Override
                public void onTimeout() throws RemoteException {
                    LogUtils.i("onTimeout: \n");
                    toast("Input Pin Timeout");
                }

                @Override
                public void onCancelPasswordEntry() throws RemoteException {
                    LogUtils.i("onCancelPasswordEntry: \n");
                    //toast("CancelPassword");
                    toast(getString(R.string.trans_canceled));
                }

            });
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e("pin exception :" + e.getMessage());
            setEditText(INITIAL_AMOUNT_OF_MONEY);
            toast(getString(R.string.show_pinpad_error));
        }

    }


    /**
     * 自定义弹密码键盘
     * Custom pop-up password input keyboard
     */
    private void showSignaturePad() {
        LogUtils.e("showSignaturePad: ");
        signatureDialog = new SignatureDialog.Builder(this)
                .setListener(new SignatureDialog.OnListener() {
                    @Override
                    public void onCancel(BaseDialog dialog) {
                        setEditText(INITIAL_AMOUNT_OF_MONEY);
                        toast(getString(R.string.trans_canceled));
                    }

                    @Override
                    public void onConfirmClick(BaseDialog dialog, byte[] byteArray) {
                        if (byteArray == null || byteArray.length == 0) {
                            toast(getString(R.string.signature_cannot_be_empty));
                            return;
                        }

                        long countBits = countBits(byteArray);
                        if (countBits <= MIN_SIGNATURE_LIMIT) {
                            toast(getString(R.string.signature_too_simple));
                            signatureDialog.redo();
                        } else if (countBits >= MAX_SIGNATURE_LIMIT) {
                            toast(getString(R.string.signature_too_complex));
                            signatureDialog.redo();
                        } else {
                            signatureBmp = getSignatureBmp(byteArray);
                            mHandler.sendEmptyMessage(8002);
                            dialog.dismiss();
                        }
                    }
                });
        signatureDialog.setOnKeyListener(new BaseDialog.OnKeyListener() {
            @Override
            public boolean onKey(BaseDialog dialog, KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();
                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                    toast(getString(R.string.trans_canceled));
                }
                return false;
            }
        });

        signatureDialog.show();
    }

   /* private Bitmap getSignatureBmp(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) return null;
        final Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        //bitmap宽高比为1.5:1，防止bitmap图片变形
        Bitmap imgBmp = BitmapUtil.createScaleBitmap(bitmap, MAX_BIT_WIDTH, MAX_BIT_HEIGHT);
        return imgBmp;
    }*/


    /**
     * 根据字节数组生成缩放后的签名 Bitmap
     *
     * @param byteArray 签名数据字节数组
     * @return 缩放后的 Bitmap；若解码失败或输入无效，则返回 null
     */
    private Bitmap getSignatureBmp(byte[] byteArray) {
        LogUtils.i("getSignatureBmp: 开始生成签名图片");

        if (byteArray == null || byteArray.length == 0) {
            LogUtils.w("getSignatureBmp: 输入字节数组为空或长度为 0");
            return null;
        }

        Bitmap bitmap = null;
        Bitmap scaledBitmap = null;

        try {
            bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            if (bitmap == null) {
                LogUtils.e("getSignatureBmp: Bitmap 解码失败");
                return null;
            }

            LogUtils.d("getSignatureBmp: 原始 Bitmap 尺寸 - 宽：" + bitmap.getWidth() + " 高：" + bitmap.getHeight());

            // 调整 Bitmap 尺寸，保持宽高比
            scaledBitmap = BitmapUtil.createScaleBitmap(bitmap, MAX_BIT_WIDTH, MAX_BIT_HEIGHT);
            LogUtils.d("getSignatureBmp: 缩放后的 Bitmap 尺寸 - 宽：" + scaledBitmap.getWidth() + " 高：" + scaledBitmap.getHeight());

        } catch (Exception e) {
            LogUtils.e("getSignatureBmp: 解码或缩放 Bitmap 失败 - " + e.getMessage());
        } finally {
            // 释放原始 Bitmap，避免内存泄漏
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                LogUtils.d("getSignatureBmp: 原始 Bitmap 已回收");
            }
        }

        LogUtils.i("getSignatureBmp: 签名图片生成完成");
        return scaledBitmap;
    }


    private void printerTest() {
        try {
            Bitmap printBmp;
            if (signatureBmp != null) {
                Bitmap textBmp = creatTextBitmap(printMsg(), 384, 850);
                //两张bitmap拼接成一张bitmap
                printBmp = BitmapUtil.addBitmap(textBmp, signatureBmp, true);
            } else {
                printBmp = creatTextBitmap(printMsg(), 384, 1200);
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
                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                    if (errorCode == ErrorCode.SUCCESS) {
                        disDialog();
                        LogUtils.i("打印完成....");
                    } else {
                        Message printErr = new Message();
                        printErr.what = 3000;
                        Bundle bundle = new Bundle();
                        bundle.putCharSequence("print_err", errorMsg);
                        printErr.setData(bundle);
                        mHandler.sendMessage(printErr);
                        LogUtils.i("Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n");
                    }
                    KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                    getKeyCode();
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
        // String familyName ="宋体";
        // Typeface font = Typeface.create(familyName,Typeface.BOLD);
        // textPaint.setTypeface(font);
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

    /**
     * 获取布局 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.activity_amount1;
    }

    /**
     * 初始化控件
     */
    @Override
    protected void initView() {
        leftIcon = findViewById(R.id.amount_title);
        tvType = (TextView) findViewById(R.id.am_type);
        etAmount = (EditText) findViewById(R.id.am_amount);
        amount_pin_type = findViewById(R.id.amount_pin_type);
        keyBoardView = (KeyBoardView) findViewById(R.id.am_kb_view);
        keyBoardView.setButtonClickBle(false);
        etAmount.requestFocus();
        etAmount.setTypeface(etAmount.getTypeface(), Typeface.BOLD);
        setEditText(INITIAL_AMOUNT_OF_MONEY);
        bindService();

        registerBroadCastReceiver();
        customerType = DataConversionUtils.getCustomerType();
        Log.i(TAG, "initView: customerType ==>" + customerType);
        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
        LogUtils.e("是否支持键盘输入:" + keyboardDevice);

        initEvent();

//        isLcdSupported = DeviceType.A8.equals(DataConversionUtils.getCustomerType());
        isLcdSupported = AFUtils.checkLcdSupported();
        LogUtils.e("isLcdSupported:" + isLcdSupported + "\t" + (isLcdSupported ? "支持LCD" : "不支持LCD"));

        if (keyboardDevice) {
            amount_pin_type.setVisibility(View.GONE);
        } else {
            amount_pin_type.setVisibility(View.VISIBLE);
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 2;
                float centerY = screenHeight * 0.1f;
                TouchHelper.simulateFullTouch(AmountActivity.this, centerX, centerY);
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
    protected void onResume() {
        super.onResume();
        LogUtils.e("onResume:  ");
    }

    private void initEvent() {
        // TODO Auto-generated method stub
        if (!keyboardDevice) {
            keyBoardView.setIKetBoardListener(new IKeyBoardListener() {

                @Override
                public void getNumber(String s) {
                    // TODO Auto-generated method stub
                    String str = getAmount();
                    Log.i(TAG, "getNumber: " + str);
                    if (str.equals("0.00")) {
                        lcdClearScreen();
                        str = "0.0" + s;
//                    etAmount.setText(str);
                        setEditText(str);
                    } else {
                        etAmount.append(s);
                        setEditText(getAmount());
                    }

                }

                @Override
                public void clear() {
                    // TODO Auto-generated method stub
//                etAmount.setText("￥0.00");
                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                }

                @Override
                public void confirm() {
                    // TODO Auto-generated method stub
                    currentAmount = etAmount.getText().toString();
                    if ("0.00".equals(getAmount())) {
                        toast(getString(R.string.amount_cannot_empty));
                    } else {
                        //Intent intent = new Intent(AmountActivity.this, SelectTransTypeActivity.class);

//                    Intent intent = new Intent(AmountActivity.this, SelectCardnoMethodActivity.class);

                        Intent intent = new Intent(AmountActivity.this, FindCardActivity.class);

                        intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, getAmount());
                        startActivity(intent);
                        //startActivity(SelectTransTypeActivity.class);
                        try {
                            mSystemBinderService.closeGetKeypadValue();
//                        lcdDrawPicture(new String[]{getString(R.string.lcd_tip_transaction_in)});

                            //displayTransaction(getString(R.string.lcd_tip_transaction_in));


                            if (lcdInfo != null) {
                                LogUtils.e("lcdGetDevLcdStatus: " + lcdInfo.toString());
                                switch (lcdInfo.getType()) {
                                    case 1:
                                        Log.i(TAG, "confirm: 黑白屏幕");

                                        File bmpBackFile = new File(FILE_PATH_AF980_BLACK_NFC_LOGO);
                                        Log.i(TAG, "confirm ==>bmpBackFile: " + bmpBackFile.exists());

                                        if (!bmpBackFile.exists()) {
                                            Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_transaction_in), false, 16);
//                                        PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                                            lcdUtils.lcdDrawPicture(bitmap);
                                            //lcdUtils.displayBmp(getContext(), "black_nfc_logo.bmp", getString(R.string.lcd_tip_transaction_in), 16);
                                            Log.e(TAG, "BMP file not found, creating new BMP file: " + FILE_PATH_AF980_BLACK_NFC_LOGO);
                                        } else {
                                            Log.i(TAG, "BMP file already exists, using existing file: " + FILE_PATH_AF980_BLACK_NFC_LOGO);
                                            lcdUtils.displayBinOnLcd(FILE_PATH_AF980_BLACK_NFC_LOGO, lcdInfo.getWidth(), lcdInfo.getHeight());
                                        }
                                        break;
                                    case 2:
                                        Log.i(TAG, "confirm: 彩屏");
                                        //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap_color);
                                   /* Bitmap bitmap_color = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_transaction_in), true, 48);

                                    String bmpPath = getContext().getFilesDir() + "/Color_screen_transaction_in.bmp";
                                    lcdUtils.saveBitmapAsBmpFile(bitmap_color, bmpPath);
                                    lcdUtils.displayBmpOnLcd(bmpPath);*/

                                        File bmpFile = new File(FILE_PATH_AF980_COLOR_SCREEN_NFC_LOGO);
                                        if (!bmpFile.exists()) {
                                            lcdUtils.displayBmp(getContext(), "color_screen_nfc_logo.bmp", getString(R.string.lcd_tip_transaction_in), 48);
                                            Log.e(TAG, "BMP file not found, creating new BMP file: " + FILE_PATH_AF980_COLOR_SCREEN_NFC_LOGO);
                                        } else {
                                            Log.i(TAG, "BMP file already exists, using existing file: " + FILE_PATH_AF980_COLOR_SCREEN_NFC_LOGO);
                                            lcdUtils.displayBinOnLcd(FILE_PATH_AF980_COLOR_SCREEN_NFC_LOGO, lcdInfo.getWidth(), lcdInfo.getHeight());
                                        }

                                        //lcdUtils.displayBmp(getContext(), "Color_screen_transaction_in.bmp", getString(R.string.lcd_tip_transaction_in), 48);
                                        break;
                                }
                            }
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void cancel() {
                    // TODO Auto-generated method stub
                    int start = etAmount.length() - 1;
                    if (start >= 3) {
                        etAmount.getText().delete(start, start + 1);
                        setEditText(etAmount.getText().toString());
                    }
                }
            });
        }


        etAmount.addTextChangedListener(new TextWatcher() {
            String numberStr;

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // TODO Auto-generated method stub
                if (mInternalAmountUpdate) return;
                if (TextUtils.isEmpty(getAmount())) {// 如果金额为空，重新设置金额，光标移到最后
                    numberStr = "0.00";
                    setAmount(numberStr);
                    moveCursorEnd(numberStr.length());
                }

                if (s.toString().equals("0.00")) {
                    keyBoardView.setButtonClickBle(false);
                } else {
                    keyBoardView.setButtonClickBle(true);
                }

                if (isChanged()) {// 如果金额发生改变，格式化金额
                    numberStr = getMoneyStr2(s.toString());
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count,
                                          int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mInternalAmountUpdate) return;
                // TODO Auto-generated method stub
                String _etStr = getAmount();
                // UtilLogs.e(_etStr + "====" + numberStr);
                // 判断输入框的值是否等于金额的值，如果不相同则赋值，如果不断判断TextWatch将会死循环
                if (isChanged()) {// 重要：如果文字沒有改变，不再为EditText重新赋值
                    setAmount(numberStr);// 为EditText赋值
                    moveCursorEnd(numberStr.length());// 将光标定位到结尾
                }
            }

            private boolean isChanged() {
                String _etStr = getAmount();
                return !TextUtils.isEmpty(getAmount()) && !_etStr.equals(numberStr);
            }

        });

    }


    /* 获得金额，此方法做了格式化金额操作，移动小数点，删0，补0 */
    private String getMoneyStr(String pMoney) {
        // 定义一个StringBuilder，便于操作字符串
        StringBuilder _moneyBuf = new StringBuilder(pMoney);

        // ①删除小数点（如果有）
        int _dotIndex = _moneyBuf.indexOf(".");
        if (!(_dotIndex < 0)) {
            _moneyBuf.deleteCharAt(_dotIndex);
        }

        // ②判断字符串长度，>4,第一个字符为"￥"，删除掉；<4,前面补0，保持至少四位数
        int _len = _moneyBuf.length();
        if (_len > 4 && "0".equals(_moneyBuf.substring(1, 2))) {
            _moneyBuf.deleteCharAt(1);
        } else {
            for (int i = 0; i < 4 - _len; i++) {
                _moneyBuf.insert(1, "0");
            }
        }
        // ③将小数点插入倒数第二位和第三位之间，保证两位小数
        _moneyBuf.insert(_moneyBuf.length() - 2, ".");

        return _moneyBuf.toString();
    }

    /* 获得金额，此方法做了格式化金额操作，移动小数点，删0，补0 */
    private String getMoneyStr2(String pMoney) {
        // 定义一个StringBuilder，便于操作字符串
        StringBuilder _moneyBuf = new StringBuilder(pMoney);

        // ①删除小数点（如果有）
        int _dotIndex = _moneyBuf.indexOf(".");
        if (!(_dotIndex < 0)) {
            _moneyBuf.deleteCharAt(_dotIndex);
        }

        // ②判断字符串长度，>= 4时,如果第一个字符为"0"，删除掉
        int _len = _moneyBuf.length();
        if (_len >= 4 && "0".equals(_moneyBuf.substring(0, 1))) {
            _moneyBuf.deleteCharAt(0);
        } else {
            for (int i = 0; i < 3 - _len; i++) {
                _moneyBuf.insert(0, "0");
            }
        }
        // ③将小数点插入倒数第二位和第三位之间，保证两位小数
        _moneyBuf.insert(_moneyBuf.length() - 2, ".");

        return _moneyBuf.toString();
    }

    /* 获得输入框中的金额 */
    private String getAmount() {
        return etAmount.getText().toString();
    }

    /* 输入框设置金额 */
    private void setAmount(String pAmount) {
//        etAmount.setText(pAmount);
        setEditText(pAmount);
    }

    /* 将光标移到最后 */
    private void moveCursorEnd(int pEnd) {
        etAmount.setSelection(pEnd);
    }

    // private void reportError(String infoStr) {
    // new AlertDialog.Builder(AmountActivity.this).setTitle("提示")
    // .setMessage(infoStr).setPositiveButton("确定", null).show();
    // }


    /**
     * 取消交易
     */
    private void canCleTransaction() {
        if (mCardBinderService == null || mEmvBinderService == null) return;

        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    mCardBinderService.closeFindCardForEMV();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * 开启寻卡交易
     * Initiate a card search transaction
     *
     * @param cardSearchType 卡类型，参考CardType类定义
     *                       Card type reference CardType class definition
     */
    private void startSearchCard(int cardSearchType) {
        closeFindCard();
        mHandler.sendEmptyMessage(8000);
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mCardBinderService == null) {
                        toast(getString(R.string.service_acquisition_failed));
                        mHandler.sendEmptyMessage(8004);
                        return;
                    }
                    cardType = mCardBinderService.openFindCardForEMV(cardSearchType, FIND_CARD_TIMEOUT);
                    LogUtils.i(getResources().getString(R.string.open_emv_card_search_results) + cardType);
                    disDialog();
                    LogUtils.e("cardType：\t" + cardType);
                    switch (cardType) {
                        case CardSearchType.MAGNETIC_STRIPE_CARD:
//                            toast("Magnetic stripe card detected");
                            //磁条卡
                            msrCardTest();
                            break;
                        case CardSearchType.IC_CARD:
                            mHandler.sendEmptyMessage(8005);
//                            toast("IC card detected");
                            //接触式IC卡
                            emvKernelProcess();
                            break;
                        case CardSearchType.RF_CARD:
                            //非接触式卡
                            mHandler.sendEmptyMessage(8005);
//                            toast(" Contactless card detected");
                            emvKernelProcess();
                            break;
                        default:
                            toast(getString(R.string.card_search_failed_ret));
                            closeFindCard();
                            mHandler.sendEmptyMessage(8004);
                            break;
                    }
                    //LogUtils.i( "mCardBinderService: 关闭寻卡.....");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }


    /**
     * 获取当时设备是否已经签到
     *
     * @return true : 已签到
     * false: 未签到
     */
    private boolean getCurrentDateSign() {
        if (spUtils.contains(AFConstant.DEVICES_LAST_SIGN)) {
            String strLastSign = spUtils.getString(AFConstant.DEVICES_LAST_SIGN);
            Log.e(TAG, "getCurrentDateSign: " + strLastSign);
            return TimeUtils.isToday(strLastSign);
        }
        return false;
    }

    /**
     * 设备签到 - DISABLED. Do not write keys from the transaction flow.
     *
     * <p>This used to be the Anfu demo key bootstrap. It called
     * {@link #setPOSMasterKey()}, which writes the hard-coded demo master key
     * {@code 000000000000000000000000000000008CA64DE9C1B123A7} to index 2 with
     * {@code dstKeyType 0x02}, and then wrote three hard-coded working keys on
     * top of it.</p>
     *
     * <p>This is why the injected terminal master key kept disappearing.
     * {@code devicesSign()} runs from {@code onServiceConnected} whenever
     * {@code getCurrentDateSign()} is false, from {@code onResume} on PIN pad
     * error -5011, and again after a master key update. AmountActivity is
     * re-entered after every sale (TransactionResultActivity -> AmountActivity,
     * and FindCardActivity -> AmountActivity), so a sale or a logon was followed
     * by the demo key overwriting the TMK at the same index. The KCV then read
     * back as 8CA64D - the KCV of an all-zeros key - and every working key that
     * had been decrypted under the real TMK became meaningless, which is what
     * produced the invalid PIN block at the acquirer.</p>
     *
     * <p>The same bootstrap was already disabled in {@code FindCardActivity}.
     * Keys in this application come from exactly two places: the terminal master
     * key is injected once from the admin panel, and the working key arrives
     * from the host in DE62 at logon (Hypercom spec 6.27.2). Nothing in the
     * transaction flow may write a key.</p>
     */
    private void devicesSign() {
        LogUtils.i("devicesSign: skipped - keys come from admin injection (TMK) "
                + "and the DE62 logon response (TPK). The demo key bootstrap is disabled.");
        // Mark the terminal as "signed" for the day so the legacy callers that
        // gate on getCurrentDateSign() stop re-triggering this on every entry.
        spUtils.put(AFConstant.DEVICES_LAST_SIGN, AFConstant.SDF_DEVICES_SIGN.format(new Date()));
        signedStatus = true;
        disDialog();
    }

    private void updateWorkKeyFail() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                disDialog();
                toast(getString(R.string.checkbalance_toast_loadworkkey_fail));
            }
        }, 2000);
    }


    /**
     * 提示框
     *
     * @param msgTip 提示语
     */
//    public void showDialog(String msgTip) {
//        if (mWaitDialog != null) {
//            LogUtils.i( "showDialog: mWaitDialog != null");
//            mWaitDialog.dismiss();
//        }
////        if (isRun) {
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                mWaitDialog = new WaitDialog.Builder(getActivity())
//                        .setMessage(msgTip)
//                        .setCancelable(false)
//                        .create();
//                mWaitDialog.show();
//            }
//        });
////        }
//    }

    /**
     * 提示框
     *
     * @param msgTip 提示语
     */
    public void showDialog(String msgTip) {
        if (mWaitDialog != null) {
            LogUtils.i("showDialog: mWaitDialog != null");
            mWaitDialog.dismiss();
        }
        mWaitDialog = new WaitDialog.Builder(AmountActivity.this)
                .setMessage(msgTip)
                .setCancelable(false)
                .create();
        mWaitDialog.show();
        if (mWaitDialog != null) {
            mWaitDialog.setOnKeyListener(new BaseDialog.OnKeyListener() {
                @Override
                public boolean onKey(BaseDialog dialog, KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        if (isSearchingCard && dialog.isShowing()) {
                            canCleTransaction();
                            isSearchingCard = false;
                            dialog.dismiss();
                            setEditText(INITIAL_AMOUNT_OF_MONEY);
                            toast(getString(R.string.trans_canceled));
                        }

                    }
                    return false;
                }
            });

        } else {
            LogUtils.e("showDialog: 初始化异常...");
        }

    }

    private void disDialog() {
        LogUtils.i("disDialog: 调用关闭Dialog");

        if (mWaitDialog != null && mWaitDialog.isShowing()) {
            try {
                mWaitDialog.dismiss();
                LogUtils.i("mWaitDialog.dismiss() 成功");
            } catch (Exception e) {
                LogUtils.e("disDialog: 关闭 Dialog 异常：" + e.getMessage());
            } finally {
                mWaitDialog = null;
            }
        } else {
            LogUtils.i("mWaitDialog == null 或者未显示");
        }
    }


    /**
     * 初始化数据
     */
    @Override
    protected void initData() {

    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mCardBinderService = ICardBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_CARD));
            mEmvBinderService = IEmvBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_EMV));
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            mPinPadBinderService = IPinPadBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PINPAD));
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
            mISystemEncDecBinderService = ISystemEncDecBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM_ENCDEC));
            AppPreferences mPreferences = AppPreferences.getInstance(getContext());
            if (mPreferences.isFirstLaunch()) {
                new TipsDialog.Builder(getContext())
                        .setIcon(TipsDialog.ICON_ERROR)
                        .setMessage(getString(R.string.load_param_fail))
                        .show();
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mainModel = new MainModelImpl(new MainListener() {
            @Override
            public void setViewLog(String log) {
                LogUtils.i("setViewLog: " + log);
            }
        }, mPinPadBinderService, mISystemEncDecBinderService);

        MyControllerListener listener = new MyControllerListener();
        mainModel.initTrans(listener);

   /*     try {
            mSystemBinderService.getKeypadValue(new KeypadValueCallback.Stub() {
                @Override
                public void onKeypadValueReceived(int keyCode) throws RemoteException {
                    KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                    LogUtils.e( "onKeypadValueReceived.....-> " + keyCode);
                    String str = getAmount();
                    if (str.equals("0.00")) {
                        str = "0.0" + String.valueOf(keyCode);
                        setEditText(str);
                    } else {
                        etAmount.append(String.valueOf(keyCode));
                        setEditText(getAmount());
                    }
                }

                @Override
                public void onKeypadValueFail(int errorCord) throws RemoteException {
                    LogUtils.e( "onKeypadValueFail.....errorCord -> " + errorCord);
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/

        if (keyboardDevice) {
            getKeyCode();
        }

        LogUtils.e("onServiceConnected: " + (getCurrentDateSign() ? "已签到Y" : "未签到..."));

        if (!getCurrentDateSign()) {
            devicesSign();
        } else {
            signedStatus = true;
        }

        try {
            int emvRet = mEmvBinderService.init();
            LogUtils.e("emvRet: " + emvRet);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if (FORMAL_TRANSACTION) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mainModel.connectSocket();
                }
            });
        }

        if (isLcdSupported) {
            LcdUtils.getInstance().init(mSystemBinderService);
            lcdUtils = LcdUtils.getInstance();
            lcdInfo = lcdUtils.lcdGetDevLcdStatus();

            if (lcdInfo != null) {
                LogUtils.i(lcdInfo.toString());
                //displayTransaction(getString(R.string.lcd_tip_amount));

                if (lcdInfo != null) {
                    LogUtils.e("lcdGetDevLcdStatus: " + lcdInfo.toString());
                    switch (lcdInfo.getType()) {
                        case 1:
                            Bitmap bitmap = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_amount), false, 16);
                            //PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap);
                            lcdUtils.lcdDrawPicture(bitmap);
                            break;
                        case 2:
                          /*  String bmpPath = getContext().getFilesDir() + "/Color_screen_welcome.bmp";
                            Bitmap bitmap_color = lcdUtils.createTextBitmap(getString(R.string.lcd_tip_amount), true, 24);
                            PrintUtil.saveBitmapToInternalStorage(getContext(), bitmap_color);
                            lcdUtils.saveBitmapAsBmpFile(bitmap_color, bmpPath);
                            lcdUtils.displayBmpOnLcd(bmpPath);*/
                            //lcdUtils.displayBmp(getContext(), "Color_screen_inputAmount.bmp", getString(R.string.lcd_tip_amount), 36);
                            lcdUtils.displayInputAmountsBmp(getContext(), "Color_screen_inputAmount.bmp", getString(R.string.lcd_tip_amount), 36);
                            break;
                    }
                }
            }
        }
    }


    /**
     * 获取键值
     */
    /*private void getKeyCode() {
        if (keyboardDevice) {
            LogUtils.e(" =================== getKeyCode: 获取键值....===================");
            try {
                mSystemBinderService.getKeypadValue(new KeypadValueCallback.Stub() {
                    @Override
                    public void onKeypadValueReceived(int keyCode) throws RemoteException {
                        KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                        LogUtils.e("=============返回键值=============");
                        LogUtils.e("onKeypadValueReceived.....-> " + keyCode);
                        String str = getAmount();
                        if (str.equals("0.00")) {
                            str = "0.0" + String.valueOf(keyCode);
                            setEditText(str);
                            LogUtils.e("首次赋值");
                        } else {
                           *//* etAmount.append(String.valueOf(keyCode));
                            setEditText(getAmount());
                            LogUtils.e("非首次赋值:" + getAmount());*//*

                            String curr = getAmount();
                            if ("0.00".equals(curr)) {
                                setEditText("0.0" + keyCode);
                            } else {
                                setEditText(curr + keyCode);
                            }

                        }
                    }

                    @Override
                    public void onKeypadValueFail(int errorCord) throws RemoteException {
                        LogUtils.e("onKeypadValueFail.....errorCord -> " + errorCord);
                        toast("onKeypadValueFail->" + errorCord);
                    }
                });
            } catch (RemoteException e) {
                LogUtils.e("getKeyCode: 获取键值异常...");
                e.printStackTrace();
            }
        }
    }*/
    private void getKeyCode() {
        if (!keyboardDevice) return;

        LogUtils.e(" =================== getKeyCode: 获取键值....===================");
        try {
            mSystemBinderService.getKeypadValue(new KeypadValueCallback.Stub() {
                @Override
                public void onKeypadValueReceived(int keyCode) throws RemoteException {
                    KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                    LogUtils.e("onKeypadValueReceived.....-> " + keyCode);
                    if (keyCode >= 0 && keyCode <= 9) {
                        uiHandler.post(() -> appendDigit(keyCode));
                    } else {
                        LogUtils.w("Ignore non-digit keypad value: " + keyCode);
                    }
                }

                @Override
                public void onKeypadValueFail(int errorCord) throws RemoteException {
                    LogUtils.e("onKeypadValueFail.....errorCord -> " + errorCord);
                    uiHandler.post(() -> toast("onKeypadValueFail->" + errorCord));
                }
            });
        } catch (RemoteException e) {
            LogUtils.e("getKeyCode: 获取键值异常...");
            e.printStackTrace();
        }
    }


    class MyControllerListener implements AFSwipeControllerListener {

        /**
         * Return card information
         *
         * @param cardInfo
         */
        @Override
        public void onTransactionResult(Map<String, String> responseData) {
            LogUtils.i("onTransactionResult() IC.RF.MSC.交易回调");
            setNumberPadState(true);
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            setEditText(INITIAL_AMOUNT_OF_MONEY);
            //交易成功
            if (responseData.get(AFConstant.TRADE_MSG_TAG).equals(AFConstant.TRADE_VALUE_SUCCESS)) {
                Iterator<Map.Entry<String, String>> iterator = responseData.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    Log.d(TAG, "key:" + entry.getKey() + "\tvalues:" + entry.getValue());
                }
               /*
                key:BF50	values:http://39.96.48 电子签名
                key:BF04	values:48290000 收单机构编号，下载并打印小票上
                key:BF03	values:10052482 收单终端号，下载并打印小票上
                key:BF36	values:1
                key:BF02	values:82982415699005C 收单商户号，下载并打印小票上
                key:BF01	values:XXXX 发卡行名称(中文)，下载并打印小票上
                key:BF40	values:XXX  交易商户简称(无下传值时打印终端配置商户名称)
                */
                toast("Successful transaction ：" + responseData.get("BF02"));
                printEntity = new TransReceiptPrintEntity();
                // 商户名称 ->XXXX
                printEntity.setBusinessName(responseData.get("BF40"));
                // 商户号 ->82982415699005C
                printEntity.setMerchantName(responseData.get("BF02"));
                //收单终端编号 ->10052482
                printEntity.setAcquirer(responseData.get("BF03"));
                //操作员号
                printEntity.setOperatorNo("01");
                //发卡行 ->中国银行
                printEntity.setIssuer(responseData.get("BF01"));
                //收单行 ->48290000
                printEntity.setAcquirerId(responseData.get("BF04"));
                //批次号
                printEntity.setBatchNumber("001001");
                //凭证号
                printEntity.setVoucherNumber(responseData.get(AFConstant.TRANS_VOUCHER_NUMBER));
                //37域交易参考号
                printEntity.setRefNo(responseData.get(AFConstant.TRANS_REF_NUMBER));
                printEntity.setCardNo(currentCardNumber);
                printEntity.setTransType("SALE");
                printEntity.setTransDateTime(responseData.get(AFConstant.TRANS_DATE_TIME));
                printEntity.setTransAmount(currentAmount);
                LogUtils.i("onTransactionResult: \n" + printEntity.toString());
                printEntity = null;
            } else {
                LogUtils.e("onTransactionResult: " + "交易失败:" + responseData.get(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP));
                toast("Transaction failed:" + responseData.get(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP));
            }
        }

        /**
         * 二维码支付主扫|被扫，服务器返回的响应
         */
        @Override
        public void onQRCodePaymentResult(Map<String, String> responseData, int platformType) {
        }

        /**
         * 设备签到结果
         * Device sign-in result
         *
         * @param importWKResult 导入秘钥结果 Updated the working key Result
         *                       true:更新工作秘钥成功 Updated the working key successfully
         *                       false:更新工作秘钥失败 Failed to update the working key
         */
        @Override
        public void onDeviceSignIn(boolean importWKResult) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            //签到成功，更新签到时间
            if (importWKResult) {
                spUtils.put(AFConstant.DEVICES_LAST_SIGN, AFConstant.SDF_DEVICES_SIGN.format(new Date()));
                signedStatus = true;
            } else {
                signedStatus = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mainModel.AFUpdatePubKey();
                    }
                });
            }
            toast("Sign in " + (importWKResult ? "successfully!" : "failed,Request to update the master key!"));
        }

        /**
         * 更新设备参数
         * Update device parameters
         *
         * @param paramType    1:设备参数  Device parameters
         *                     2:更新公钥 Public key data
         *                     3:主密钥  Master Key
         * @param updateResult 更新结果 true：成功 false:失败
         */
        @Override
        public void updateDevParam(int paramType, boolean updateResult) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            switch (paramType) {
                case 1:
                    toast(updateResult ? "Terminal parameters updated successfully" : "Terminal parameter update failed");
                    break;
                case 2:
                    toast(updateResult ? "Public key updated successfully" : "Public key update failed");
                    break;
                case 3:
                    toast(updateResult ? "The master key was updated successfully ,Re-sign in" : "Master key update failed, data abnormality!");
                    if (updateResult) {
                        //Re-sign in
                        devicesSign();
                    }
                    break;
                default:
                    break;
            }
        }

        /**
         * Make a request to the server
         */
        @Override
        public void onSendData(int msgType) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            String tempTip = null;
            switch (msgType) {
                case AFConstant.SEND_PKG_MSG_TYPE_SIGN_IN:
                    tempTip = getString(R.string.pkg_msg_type_sign_in);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_SALE:
                    tempTip = getString(R.string.pkg_msg_type_sale);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UNION_PAY:
                    tempTip = getString(R.string.pkg_msg_type_union_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_ALI_PAY:
                    tempTip = getString(R.string.pkg_msg_type_ali_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_WECHAT_PAY:
                    tempTip = getString(R.string.pkg_msg_type_wechat_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_DEV_PARAM:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_param);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_PUB_KEY:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_pub_key);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_MASTER_KEY:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_tm_key);
                    break;
                default:
                    tempTip = "";
                    break;
            }
            sendDataTip(tempTip + getString(R.string.trans_send_data));
        }

        private void sendDataTip(String tip) {
            LogUtils.i("sendDataTip: " + System.currentTimeMillis() + "\tTip:\t" + tip);
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWaitDialog = new WaitDialog.Builder(AmountActivity.this)
                            .setMessage(tip)
                            .setCancelable(false).create();
                    if (mWaitDialog != null) {
                        mWaitDialog.show();
                    }
                }
            });
        }

        /**
         * Receiving data from the server
         */
        @Override
        public void onDataReceiving() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            mWaitDialog = new WaitDialog.Builder(getActivity()).setMessage(getString(R.string.trans_data_receiving))//选择寻卡类型
                    .setCancelable(false).create();
            mWaitDialog.show();
        }

        /**
         * Connection failure
         */
        @Override
        public void onConnectServerFail() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            toast("can not reach server");
            setNumberPadState(true);
        }

        /**
         * The server responded abnormally. Please try to sign in again.
         */
        @Override
        public void onServerUnresponsive() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            //toast("服务器响应异常，请尝试重新签到!");
            setNumberPadState(true);
            new MessageDialog.Builder(getActivity())
                    // 标题可以不用填写
                    .setTitle(R.string.main_title_friendly)
                    .setCancelable(false)
                    // 内容必须要填写
                    .setMessage("The server responded abnormally.\n Please try to sign in again.!")
                    // 确定按钮文本
                    .setConfirm(getString(R.string.common_confirm))
                    // 设置 null 表示不显示取消按钮
                    .setCancel(getString(R.string.common_cancel))
                    .setListener(new MessageDialog.OnListener() {

                        @Override
                        public void onConfirm(BaseDialog dialog) {
                            // toast(R.string.amount_confirm);
                            devicesSign();
                        }

                        @Override
                        public void onCancel(BaseDialog dialog) {
                            // toast(R.string.amount_clear);
                        }
                    }).show();
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


    /**
     * 更新工作密钥
     * Update working key
     */
    private boolean importWriteWorkingKey(WorkingKeyEntity workingKeyEntity) {
        try {
            int mode = 0x01;
            int keyLen = 0x10; // 8 或者  16   或24
            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            kcvInfoParam.setCheckMode(1);
            String kcvLen = Integer.toHexString(AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()).length);
            LogUtils.e("importWriteWorkingKey: kcvLen--->" + kcvLen);
            String formattedString = String.format("%02d", Integer.parseInt(kcvLen));

            LogUtils.e("importWriteWorkingKey: kcvLen--->" + formattedString);
            byte[] checkValueData = mergeByteArrays(AFUtils.hexString2ByteArray(formattedString), AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()));
            kcvInfoParam.setCheckBuf(AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()));
            int mKeyNo = 0x02;
            LogUtils.e("importWriteWorkingKey: -----start-----》》》");
            //获取秘钥ID
            int usKeyType = workingKeyEntity.getKeyID();
            //获取秘钥数据
            byte[] keyData = HexUtil.hexStringToByte(workingKeyEntity.getKeyData());

            int ret = mPinPadBinderService.posWriteWorkingKey(usKeyType, keyLen, keyData, kcvInfoParam, mode, mKeyNo);
            LogUtils.e("importWriteWorkingKey: -----end-----》》》" + ret);

            LogUtils.e("workingKeyEntity : \n" + workingKeyEntity.toString());
            if (ret == 0) {
                return true;
               /* try {
                    LogUtils.e( "mIPinPadBinderService.posGetKcv: ------------start---------->>");
                    PosGetKcvResult posGetKcvResult = mPinPadBinderService.posGetKcv(mKeyNo, keyId);
                    LogUtils.e( "mIPinPadBinderService.posGetKcv: ------------end---------->>" + posGetKcvResult.getRet());
                    int getKcvResultRet = posGetKcvResult.getRet();
                    if (posGetKcvResult.getRet() == 0) {
                        kcvInfoParam.getCheckBuf();
                        byte[] checkRes = posGetKcvResult.getKcvInfo();
                        LogUtils.e( "checkRes：\t" + AFUtils.showResult0xStr(checkRes));
                        String strKecResult = AFUtils.showResult16Str(posGetKcvResult.getKcvInfo());
                        LogUtils.e( "importWriteWorkingKey: ----------strKecResult---->" + strKecResult);
                        LogUtils.e( "DAL_POSGetKcv:" + getKcvResultRet);
                        LogUtils.e( "DAL_POSGetKcv结果：" + strKecResult);
                        strKecResult = strKecResult.substring(8, 16);
                        //00080000 173D5428
                        if (strKecResult.equals(workingKeyEntity.getKeyCheckValues().substring(0, 8))) {
                            LogUtils.e( "DAL_POSGetKcv：校验通过");
                            return true;
                        }
                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }*/
            } else {
                return false;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static byte[] mergeByteArrays(byte[] array1, byte[] array2) {
        byte[] mergedArray = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, mergedArray, 0, array1.length);
        System.arraycopy(array2, 0, mergedArray, array1.length, array2.length);
        return mergedArray;
    }

    /**
     * msrCardTest
     */
    private void msrCardTest() {
        try {
            saleDataModel = new SaleDataModel();
            LogUtils.i("----msrCardTest()");
            mCardBinderService.getMSRCardInfo(FIND_CARD_TIMEOUT, new ICardTestCallbackProxy());
        } catch (RemoteException e) {
            e.printStackTrace();
            LogUtils.e(e.getMessage());
        }
    }


    /**
     * 根据TAG值获取数据
     *
     * @param tag 数据类型
     * @return 相应域的数据
     */
    private String getTagValue(int tag) {
        byte[] getTLV = null;
        try {
            getTLV = mEmvBinderService.apiKernelEmvGetTlv(tag);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return (getTLV == null ? "" : HexUtil.bcd2str(getTLV));
    }

    private String getTagValueSafe(int tag) {
        try {
            return getTagValue(tag);
        } catch (Exception e) {
            return "";
        }
    }

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    private static String stripPadF(String s) {
        return s == null ? "" : s.replaceAll("[Ff]+$", "");
    }

    private static boolean isPlausiblePan(String s) {
        return s != null && s.length() >= 12 && s.length() <= 19;
    }

    private static String panFromTrack2(String track2) {
        if (track2 == null || track2.isEmpty()) {
            return "";
        }
        String t = track2.toUpperCase().replace('=', 'D');
        int d = t.indexOf('D');
        String raw = d >= 0 ? t.substring(0, d) : t;
        return digitsOnly(stripPadF(raw));
    }

    private static String panFromTrack1(String track1) {
        if (track1 == null || track1.isEmpty()) {
            return "";
        }
        String t = track1.trim();
        if (t.startsWith("B") || t.startsWith("b")) {
            t = t.substring(1);
        }
        int caret = t.indexOf('^');
        String raw = caret >= 0 ? t.substring(0, caret) : t;
        return digitsOnly(stripPadF(raw));
    }

    private static String panFromKernel(byte[] cadNo) {
        if (cadNo == null || cadNo.length == 0) {
            return "";
        }
        String hex = AFUtils.showResult16Str(cadNo);
        String ascii = digitsOnly(stripPadF(AFUtils.filterNonAscii(AFUtils.hexToAscii(hex))));
        String bcd = digitsOnly(stripPadF(hex));
        if (isPlausiblePan(ascii)) {
            return ascii;
        }
        if (isPlausiblePan(bcd)) {
            return bcd;
        }
        return ascii.length() >= bcd.length() ? ascii : bcd;
    }

    private String bestKnownPan(String preferred) {
        String p = digitsOnly(stripPadF(preferred));
        if (!p.isEmpty()) {
            return p;
        }
        p = digitsOnly(stripPadF(currentCardNumber));
        if (!p.isEmpty()) {
            return p;
        }
        String tag5A = getTagValueSafe(0x5A);
        p = digitsOnly(stripPadF(tag5A));
        if (!p.isEmpty()) {
            return p;
        }
        String tag57 = getTagValueSafe(0x57);
        p = digitsOnly(panFromTrack2(tag57));
        if (!p.isEmpty()) {
            return p;
        }
        String tag9F6B = getTagValueSafe(0x9F6B);
        p = digitsOnly(panFromTrack2(tag9F6B));
        if (!p.isEmpty()) {
            return p;
        }
        String tag56 = getTagValueSafe(0x56);
        p = digitsOnly(panFromTrack1(tag56));
        if (!p.isEmpty()) {
            return p;
        }
        if (saleDataModel != null) {
            p = digitsOnly(stripPadF(saleDataModel.getCardNo()));
            if (!p.isEmpty()) {
                return p;
            }
            String t2 = saleDataModel.getTrack2data();
            p = digitsOnly(panFromTrack2(t2));
            if (!p.isEmpty()) {
                return p;
            }
        }
        return "";
    }

    /**
     * 设置交易参数
     *
     * @param tag   数据类型
     * @param value 数据的值
     * @return
     */
    private boolean setTagValue(int tag, String value) {
        byte[] tempValue = AFUtils.hexString2ByteArray(value);
        LogUtils.i("setTagValue: " + AFUtils.showResult16Str(tempValue));
        LogUtils.i("setTagValue Len: " + tempValue.length);
//        LogUtils.i( "setTagValue: " + AFUtils.showResult16Str(AFUtils.StringToByteArray(value)));
        try {
            int res = mCardBinderService.posBscSetTLV(tag, tempValue, tempValue.length);
            LogUtils.i("setTagValue res: " + res);
            return res == 0;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取55域内容
     * Get 55 domain data
     *
     * @param tags tag list
     */
    private String getICData(int[] tags) {
        //55域内容
        StringBuffer sbIcData = new StringBuffer();
        sbIcData.setLength(0);
        //缺失TAG
        StringBuffer sbMissingTAG = new StringBuffer();
        sbMissingTAG.setLength(0);

        for (int i = 0; i < tags.length; i++) {
            try {
                int currTag = tags[i];
                byte[] currData = mEmvBinderService.apiKernelEmvGetTlv(currTag);
                String tag = Integer.toHexString(currTag);
                if (currData != null) {
                    String value = HexUtil.bcd2str(currData);
                    String len = Integer.toHexString(value.length() / 2);
                    LogUtils.i("TAG: " + tag + "\t\tLen: " + len + "\t\tValue: " + value);
                    sbIcData.append(AFUtils.packSendTLV(tag, value));
                } else {
                    //没有数据的TAG处理
                    sbMissingTAG.append(tag + "00");
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        String icData = sbIcData.toString() + sbMissingTAG.toString();
        LogUtils.i("55域内容: " + icData + "\n");
        LogUtils.i("缺失的TAG: " + sbMissingTAG.toString() + "\n");
        return icData;
    }


    /**
     * 弹窗显示卡号
     *
     * @param cardNo   卡号
     * @param cardType 卡类型
     *                 0：IC/插卡
     *                 1:RF/挥卡
     *                 2：MSC/刷卡
     */
    private void showReadCard(String cardNo, int cardType) {
        Message cardMsg = new Message();
        if (TextUtils.isEmpty(cardNo)) {
            String errInfo = getResources().getString(R.string.card_search_failed_ret);
            cardMsg.what = MSG_TRANS_FAil;
            Bundle cardBundle = new Bundle();
            cardBundle.putCharSequence("errInfo", errInfo);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        } else {
            cardMsg.what = MSG_TRANS_IC_RF_SUCCESS;
            Bundle cardBundle = new Bundle();
            cardBundle.putCharSequence("cardData", cardNo);
            cardBundle.putInt("cardType", cardType);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        }
    }

    /**
     * 读卡结果
     *
     * @param resultCode:结果码，0：成功，非0：失败
     * @param resultMsg：结果输出信息
     * @param cardNo：银行卡号
     * @param cardType：卡片类型
     */
    private void showReadCardResult(int resultCode, String resultMsg, String cardNo, int cardType) {
        Message cardMsg = new Message();
        Bundle cardBundle = new Bundle();
        if (resultCode == 0) {
            cardMsg.what = MSG_TRANS_IC_RF_SUCCESS;
            cardBundle.putCharSequence("cardData", cardNo);
            cardBundle.putInt("cardType", cardType);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        } else {
            String errInfo = resultMsg;
            if (TextUtils.isEmpty(errInfo)) {
                errInfo = getResources().getString(R.string.card_search_failed_ret);
            }
            cardMsg.what = MSG_TRANS_FAil;
            cardBundle.putCharSequence("errInfo", errInfo);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        }
    }

    private class ICardTestCallbackProxy extends ICardTestCallback.Stub {

        @Override
        public void onFindCardStart() throws RemoteException {
            LogUtils.d("开始寻卡...");
        }

        @Override
        public void onFindCardSuccess(int cardType) throws RemoteException {
            LogUtils.d("寻卡成功  Card Type: " + cardType);
        }

        @Override
        public void onTrackDataRead(byte[] track1Data, byte[] track2Data, byte[] track3Data) throws RemoteException {
            Message cardMsg = new Message();
            Bundle cardBundle = new Bundle();
            LogUtils.i("磁条卡 二磁道信息 : " + AFUtils.showResult16Str(track2Data));
            String strTrack2Data = new String(track2Data).trim();
            if (TextUtils.isEmpty(strTrack2Data)) {
                String errInfo = "Read Card Error,CardNo is empty";
                LogUtils.d(errInfo);
                cardMsg.what = MSG_TRANS_FAil;
                cardBundle.putCharSequence("errInfo", errInfo);
                cardMsg.setData(cardBundle);
                mHandler.sendMessage(cardMsg);
                return;
            }

            cardMsg.what = MSG_TRANS_MSR_SUCCESS;
            if (strTrack2Data.contains("=")) {
                cardBundle.putCharSequence("track2Data", strTrack2Data.substring(0, strTrack2Data.indexOf("=")));
                currentCardNumber = strTrack2Data.substring(0, strTrack2Data.indexOf("="));
                tipCardNumber = currentCardNumber;
            } else {
                cardBundle.putCharSequence("track2Data", strTrack2Data);
            }
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
            LogUtils.d("Track2 Data: " + strTrack2Data);


            //卡有效期
            if (strTrack2Data.contains("=")) {
                int tempDIndex = strTrack2Data.indexOf("=");
                //磁条卡获取卡号有效期 ---> 6225768751482231=200720115882103
                cardValid = strTrack2Data.substring(tempDIndex + 1, tempDIndex + 5);
                LogUtils.i("磁条卡有效期: " + cardValid);
            }
        }

        @Override
        public void onCardDataRead(byte[] cardData) throws RemoteException {
            LogUtils.d("Card Data: " + (cardData == null ? "null" : HexUtil.bcd2str(cardData)));
        }

        @Override
        public void onFindSamCardStart() throws RemoteException {
            LogUtils.e("onFindSamCardStart: ===========================================");
        }

        @Override
        public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
            if (errorCode == ErrorCode.SUCCESS) {
                LogUtils.d("成功");
            } else {
                String errInfo = "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n";
                LogUtils.d(errInfo);
                Message cardMsg = new Message();
                cardMsg.what = MSG_TRANS_FAil;
                Bundle cardBundle = new Bundle();
                cardBundle.putCharSequence("errInfo", errInfo);
                cardMsg.setData(cardBundle);
                mHandler.sendMessage(cardMsg);
            }
        }
    }


    /**
     * 重置SP
     */
    private void resetSP() {
      /*  try {
            int ret = mSystemBinderService.resetSP();
            LogUtil.i(TAG, "重置SP结果:\t" + (ret == 0 ? "成功" : "失败"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
    }


    /**
     * 写主密钥 DAL_POSWriteKey - DISABLED.
     *
     * <p>Wrote the Anfu demo master key to index 2. The terminal master key is
     * injected once from the admin panel and must never be replaced by anything
     * in the transaction flow - no Hypercom message replaces it either
     * (spec 6.27.2 replaces the working key, 6.19 the PIN and MAC keys). Kept
     * only so the call sites still compile; it now refuses and says so.</p>
     */
    private boolean setPOSMasterKey() {
        LogUtils.e("setPOSMasterKey: REFUSED - the terminal master key may only be "
                + "written from the admin panel. Writing the demo key here is what "
                + "erased the injected TMK after every sale.");
        return false;
    }

    /**
     * 统计byte数组中1的个数
     *
     * @param bytes
     * @return
     */
    private long countBits(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }

        long count = 0;
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                if ((bytes[i] & (1 << j)) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }

    private SpannableString getSpannableString(String string) {
        if (StringUtils.isEmpty(string))
            return null;

        SpannableString spanString = new SpannableString(string);
        //设置字体为粗体
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 0, spanString.length(), 0);
        return spanString;
    }

    private void setEditText(final String str) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setEditText(str);
                }
            });
            return;
        }

        SpannableString spannableString = getSpannableString(str);
        if (spannableString != null && spannableString.length() > 0) {
            if (!spannableString.toString().equals(etAmount.getText().toString())) {
                etAmount.setText(spannableString);
                try {
                    double amount = Double.parseDouble(spannableString.toString());
                    displayTransactionAmountASCII(amount);
                } catch (NumberFormatException e) {
                    LogUtils.e("setEditText: 金额格式异常: ");
                }
            }
        }
    }

    /**
     * 配置交易参数
     * Configure EMV transaction parameters
     * 卡片类型 0：IC卡 1：非接卡
     */
    private void emvKernelProcess() {
        saleDataModel = new SaleDataModel();
        mCardConfirmed = false;
        try {
            //emv init
            mEmvBinderService.emvKernelInit();

            //Debug EMV log switch ：1->on 0->off
            mEmvBinderService.apiEmvKernelJniPbocLogswitch(1);

            Map<String, String> map = new LinkedHashMap<>();
            // 流程类型  0 -EMV标准，1 -EMV简易，2 -Quick标准，3 -Quick简易（目前终端仅支持接触式EMV，非接触式EMV预留，Quick仅支持非接）
            //Flow Type 0 - EMV Standard, 1 - EMV Simple, 2 - Quick Standard, 3 - Quick Simple (Currently the terminal only supports contact EMV, contactless EMV is reserved, Quick only supports contactless)
            map.put("procType", "0x00");
            // 通道类型  0x00  0 -接触式，1 -非接触式  0x01
            //Channel Type 0 - Contact, 1 - Contactless
            map.put("channelType", (cardType == CardSearchType.IC_CARD ? "0x00" : "0x01"));
            // 脱机数据认证标识，仅适用于EMV标准模式 0 -不进行脱机数据认证（虚拟），1 -进行脱机数据认证
            //Offline data authentication flag, only applicable to EMV standard mode
            // 0-no offline data authentication (virtual/contactless/prepaid)
            // 1-offline data authentication
            map.put("odaFlag", "0x01");
            // 支持电子现金交易标识，仅适用于EMV标准模式 0 -不支持电子现金交易，1 -支持电子现金交易
            //Support electronic cash transaction identification, only applicable to EMV standard mode
            // 0 - does not support electronic cash transactions
            // 1 - supports electronic cash transactions
            map.put("ecFlag", "0x00");
            // 强制联机  0 -不强制，1 -强制联机
            //Force connection 0 - not forced, 1 - forced connection
            map.put("forceOnline", "0x01");
            // 强制联机PIN，仅适用于EMV标准模式  0 -不强制，1 -强制联机PIN
            //Force online PIN, only applicable to EMV standard mode 0 - not forced, 1 - force online PIN
            map.put("forceOnlinePin", (cardType == CardSearchType.IC_CARD ? "0x01" : "0x00"));
            // 卡号确认，仅适用于EMV标准模式  0 -不执行卡号确认，1 -执行卡号确认
            //Card number confirmation, only applicable to EMV standard mode
            // 0 - do not perform card number confirmation, 1 - perform card number confirmation
            map.put("cardNoConfirm", "0x00");
            // 内核重启标识  0 -内核第一次启动，1 -内核重启
            //Kernel restart flag 0 - kernel first boot, 1 - kernel restart
            map.put("restartFlag", "0x00");
            // 交易类型  n2  9C
            //Transaction type n2 9C
            map.put("transType", "0x00");
            // 交易货币指数 n1  5F36
            //Trading CurrencyIndex n1 5F36
            map.put("currencyExp", "0x00");
            // 商户名称长度  b 1B
            //Merchant name length b 1B
            map.put("merNameLen", "0x07");
            //商户名称 ans Var.最大长度未知 9F4E
            //Merchant Name ans Var. Maximum length unknown 9F4E
            String tempMerName = AFUtils.padStringToBytes("Barkey", 40);
            map.put("merName", AFUtils.showResult0xStr(AFUtils.StringToByteArray(tempMerName)));
            //交易流水号 n6  9F41
            //Transaction serial number n6 9F41
            map.put("posSer", "0x00,0x00,0x01");
            byte[] amount = AFUtils.hexString2ByteArray(AFUtils.dealMoneyStr(currentAmount));
            String tempAmount = AFUtils.showResult0xStr(amount);
            transAmount = AFUtils.showResult16Str(amount);
            LogUtils.e("tempAmount" + tempAmount);
            //交易金额 n12  9F02
            //Transaction amount n12 9F02
            map.put("transAmt", tempAmount);
            // 其他金额 n12  9F03
            //Other amount n12 9F03
            map.put("otherAmt", "0x00,0x00,0x00,0x00,0x00,0x00");
            // 交易货币代码 n3  5F2A
            //Transaction currency code n3 5F2A
            map.put("currencyCode", "0x00,0x50");
            byte[] date = AFUtils.hexString2ByteArray(SDF_TAG_9A_YYDD.format(new Date()));
            LogUtils.e("emvKernelProcess: date" + AFUtils.showResult0xStr(date));
            // 交易日期 n6  YYMMDD  9A
            //Transaction date n6 YYMMDD 9A
            map.put("transDate", AFUtils.showResult0xStr(date));
            byte[] time = AFUtils.hexString2ByteArray(SDF_TAG_9A_HMS.format(new Date()));
            // 交易时间 n6  hhmmss    9F21
            //Transaction time n6 hhmmss 9F21
            map.put("transTime", AFUtils.showResult0xStr(time));
            // 商户号 ans15  9F16
            //Merchant number ans15 9F16
            map.put("merId", AFUtils.showResult0xStr(AFUtils.StringToByteArray(String.format(java.util.Locale.US, "%-15.15s", TerminalConfig.get(getApplicationContext()).mid()))));
            // 终端号 an8  9F1C
            //Terminal number an8 9F1C
            map.put("termId", AFUtils.showResult0xStr(AFUtils.StringToByteArray(String.format(java.util.Locale.US, "%-8.8s", TerminalConfig.get(getApplicationContext()).tid()))));
            // é™„åŠ æ•°æ®å®žé™…æœ‰æ•ˆé•¿åº¦ (total emvParamByte must be exactly 256 bytes: 96 bytes header + 160 bytes addition)
            byte[] additionByte = new byte[160];
            String strAddition;
            if (cardType == CardSearchType.IC_CARD) {
                map.put("additionLen", "0x24");
                strAddition = "FF6001009F3501229F3303E0F0C89F40056000F0E0019F1A0200505F2A020050DF690100";
            } else {
                map.put("additionLen", "0x24");
                strAddition = "FF6001009F3501229F3303E0F0C89F40056000F0E0019F1A0200505F2A020050DF690100";
            }
            // TTQ (9F66) belongs to the selected AID profile: Visa uses its own
            // 36000000 profile, while NexusPay uses B6000000. Never override all taps.
            byte[] tlvRaw = AFUtils.hexString2ByteArray(strAddition);
            System.arraycopy(tlvRaw, 0, additionByte, 0, tlvRaw.length);
            LogUtils.i("additionByte:  16Str " + AFUtils.showResult16Str(additionByte));
            LogUtils.i("additionByte: 0xStr" + AFUtils.showResult0xStr(additionByte));
            // é™„åŠ æ•°æ®ï¼Œé™„åŠ æ•°æ®ä»¥TLVæ ¼å¼å­˜åœ¨
            //Additional data, which exists in TLV format
            map.put("addition", AFUtils.showResult0xStr(additionByte));

            StringBuilder sbEmvParam = new StringBuilder();
            sbEmvParam.setLength(0);

            for (Map.Entry<String, String> itemTAG : map.entrySet()) {
                LogUtils.i("emvParam key->: " + itemTAG.getKey() + "\t data-->" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()) + "\t 字节数:" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()).length() / 2);
                sbEmvParam.append(AFUtils.removeAllHexPrefixes(itemTAG.getValue()));
            }

            LogUtils.e("apiEmvKernelProcess Data--> " + sbEmvParam.toString());

            LogUtils.e("apiEmvKernelProcess Data Length--> " + sbEmvParam.toString().length() / 2);

            byte[] emvParamByte = AFUtils.hexString2ByteArray(sbEmvParam.toString());
            LogUtils.e("emvParam Len : " + emvParamByte.length);



                        //User enters PIN
            mEmvBinderService.apiSDKEmvInPutConfirmCallBack(new ISdkKernelEmvInputPINCallback.Stub() {
                @Override
                public void onEmvInputPIN(byte ucPinType, byte[] pucPinBlock, byte ucLastTimes) throws RemoteException {
                  /*  ucPinType     - PIN类型   0 脱机PIN   1 联机PIN
                                    -PIN Type  0 Offline PIN 1 Online PIN
                    ucLastTimes     - PIN重试次数
                                    -PIN retry count
                    */
                    LogUtils.e("ucPinType：" + AFUtils.showResult0xStr(new byte[]{ucPinType}));
                    LogUtils.e("ucLastTimes：" + AFUtils.showResult0xStr(new byte[]{ucLastTimes}));

                    currentCardNumber = getTagValue(0x5A);

                    LogUtils.i("onEmvInputPIN:currentCardNumber-> " + currentCardNumber);
                    showPinPad(true, currentCardNumber, ucPinType & 0xFF);
                    // PIN输入回调：返回值  返回错误码：0成功；-4是超时退出；-1031是未输入PIN直接确认跳过了；-1029是直接取消
                    disDialog();
                }
            });

            //Setting system counters
            mEmvBinderService.apiSDKGetEmvSystemTimerCall(new IKernelEmvSystemTimerCallback.Stub() {
                @Override
                public long emvGetSysTimerCallFun() throws RemoteException {
                    long currentTimeMillis = System.currentTimeMillis();
                    LogUtils.e("currentTimeMillis: " + currentTimeMillis);
                    LogUtils.e("currentTimeMillis: formatTimestamp--->>\t" + AFUtils.formatTimestamp(currentTimeMillis));
                    return currentTimeMillis;
                }
            });
            mEmvBinderService.apiSetEmvCardNoConfirmCallBack(new IKernelEmvCallback.Stub() {
                //卡号确认回调
                //Card number confirmation callback
                @Override
                public int EmvCardNoConfirm(byte[] cadNo) throws RemoteException {
                    LogUtils.e("EmvCardNoConfirm  : " + AFUtils.showResult16Str(cadNo));
                    currentCardNumber = panFromKernel(cadNo);
                    if (currentCardNumber == null || currentCardNumber.isEmpty()) {
                        currentCardNumber = bestKnownPan("");
                    }
                    LogUtils.e("EmvCardNoConfirm auto-confirmed PAN: " + currentCardNumber);
                    tipCardNumber = currentCardNumber;
                    mCardConfirmed = true;
                    return 0; // Auto-confirm without popping up confirmation dialog!
                }
            });

            /**
             * Cardholder ID verification(持卡人认证确认)
             * 注意：只有PBOC的才有持卡人认证确认
             */
            mEmvBinderService.apiSetEmvLicenseConfirmCallBack(new IKernelEmvLicenseCallback.Stub() {
                @Override
                public int EmvLicenseConfirm(byte uclicenseType, byte[] pszlicense) throws RemoteException {
                    LogUtils.e("IKernelEmvLicenseCallback ucLicenseType：" + AFUtils.showResult0xStr(new byte[]{uclicenseType}));
                    LogUtils.e("IKernelEmvLicenseCallback pszLicense：" + AFUtils.hexToAscii(AFUtils.showResult16Str(pszlicense)));

                    String strUclicenseType = AFUtils.showResult0xStr(new byte[]{uclicenseType}).trim();
                    String strLicenseValue = AFUtils.hexToAscii(AFUtils.showResult16Str(pszlicense));
                    StringBuilder strLicencesBuilder = new StringBuilder();
                    if ("0x00".equals(strUclicenseType)) {  //    0x00 身份证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Id_Card));
                    } else if ("0x01".equals(strUclicenseType)) { //    0x01 军官证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Officer_Certificate));
                    } else if ("0x02".equals(strUclicenseType)) {  //    0x02 护照
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Passport));

                    } else if ("0x03".equals(strUclicenseType)) {  //    0x03 入境证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_EntryPermit));

                    } else if ("0x04".equals(strUclicenseType)) {  //    0x04 临时身份证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_TemporaryIDcard));

                    } else if ("0x05".equals(strUclicenseType)) {  //    0x05其他
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Others));
                    }

                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] resultHolder = new int[1];

                    strLicencesBuilder.append(strLicenseValue);
                    showLicenceSelectedConfirmDialog(strLicencesBuilder.toString(), new LicenceSeclectedCallback() {
                        @Override
                        public void onResult(int result) {
                            resultHolder[0] = result;
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return resultHolder[0];
                }
            });

            // 应用选择 Application Selection(弹框显示AID，供选择)
            mEmvBinderService.apiSetEmvAppConfirmCallBack(new ISdkKernelEmvAppCallback.Stub() {
                @Override
                public int onEmvAppConfirm(byte ucAppNum, byte[] pucAppData, int uiDataLen, byte[] pucIndex) throws RemoteException {
                    ArrayList<TLVModel> tlvModelArrayList = AFUtils.praseTLVStrX(pucAppData);
                    for (int i = 0; i < tlvModelArrayList.size(); i++) {
                        LogUtils.e("tlvModelArrayList : " + tlvModelArrayList.get(i));
                    }
                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] resultHolder = new int[1];
                    showApplicationSelectedDialog("AID  ", tlvModelArrayList, new ApplicationSeclectedCallback() {
                        @Override
                        public void onResult(int result) {
                            resultHolder[0] = result;
                            byte[] tempPucIndex = IntToBytesUtils.toLittleEndian(result);
                            System.arraycopy(tempPucIndex, 0, pucIndex, 0, tempPucIndex.length);
                            latch.countDown();
                        }
                    });

                    try {
                        latch.await();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return resultHolder[0];
                }
            });

            //screen display
            mEmvBinderService.apiSDKSetEmvlcdPrintfExCallFun(new IKernelEmvlcdPrintExCallback.Stub() {
                @Override
                public int emvlcdPrintfExCallFun(byte column, byte row, byte mode, byte ucMsgID, byte[] ucDisStr) throws RemoteException {
                    LogUtils.e("emvlcdPrintfExCallFun: " + AFUtils.hexToAscii(AFUtils.showResult16Str(ucDisStr)) + "\n");
                    return 0;
                }

               /* @Override
                public int emvlcdPrintfExCallFun(byte column, byte row, byte mode, byte[] ucDisStr) throws RemoteException {
                    return 0;
                }*/
            });

            int kernelProcessResult = mEmvBinderService.apiEmvKernelProcess(emvParamByte);
            LogUtils.e("kernelProcessResult-------> " + kernelProcessResult);

            //只在认证的时候使用,第三方集成请忽略此步骤
           /* if (kernelProcessResult == ERR_KERNEL_RESTART) {
                toast(getString(R.string.err_kernel_restart));
                //重启内核 KERNEL RESTART
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startSearchCard(CardSearchType.RF_CARD);
                    }
                }, 1500);
                return;
            }*/

            apiEmvGetKernelProcessResult(0);

            //用户输入密码/免密/非接交易.继续执行获取EMV执行结果
            //User enters password/password-free/contactless transaction. Continue to execute and obtain EMV execution result
          /*  if (inputPinResult == -1 || inputPinResult == -1031 || cardType == 1) {
                //Contactless card execution results
                LogUtils.e( "start apiEmvGetKernelProcessResult... ");

            } else {
                closeFindCard();
                LogUtils.e( "emvKernelProcess: PIN pad Result -->" + inputPinResult);
            }*/


        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public interface ConfirmationCallback {
        void onResult(int result);
    }

    public interface ApplicationSeclectedCallback {
        void onResult(int result);
    }

    public interface LicenceSeclectedCallback {
        void onResult(int result);
    }


    public void showLicenceSelectedConfirmDialog(String ucLicenseTypePszLicense, LicenceSeclectedCallback callback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disDialog();
                new MessageDialog.Builder(getActivity())
                        .setTitle(R.string.card_number_confirmation_license)
                        .setMessage(TextUtils.isEmpty(ucLicenseTypePszLicense) ? "Licence Number Error" : ucLicenseTypePszLicense)
                        .setConfirm(keyboardDevice ? getString(R.string.common_cancel) : getString(R.string.common_confirm))
                        .setCancel(keyboardDevice ? getString(R.string.common_confirm) : getString(R.string.common_cancel))
                        .setCanceledOnTouchOutside(false)
                        .setListener(new MessageDialog.OnListener() {

                            @Override
                            public void onConfirm(BaseDialog dialog) {
                                dialog.dismiss();
                                if (keyboardDevice) {
                                    toast(getString(R.string.trans_canceled));
                                    callback.onResult(-1);
                                } else {
                                    if (TextUtils.isEmpty(tipCardNumber)) {
                                        callback.onResult(-1);
                                    } else {
                                        callback.onResult(0);
                                    }
                                }
                            }

                            @Override
                            public void onCancel(BaseDialog dialog) {
                                if (keyboardDevice) {
                                    if (TextUtils.isEmpty(tipCardNumber)) {
                                        callback.onResult(-1);
                                        canCleTransaction();
                                    } else {
                                        callback.onResult(0);
                                    }
                                } else {
                                    toast(getString(R.string.trans_canceled));
                                    callback.onResult(-1);
                                }

                            }
                        })
                        .show();
            }
        });
    }

    public void showApplicationSelectedDialog(String dialogType, ArrayList<TLVModel> tlvModelArrayList, ApplicationSeclectedCallback callback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AidChoiceDialog.showDialog(AmountActivity.this, dialogType, tlvModelArrayList,
                        new AidChoiceDialog.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(int position) {
                                callback.onResult(position);
                            }
                        });
            }
        });
    }

    public void showConfirmationDialog(String tipCardNumber, ConfirmationCallback callback) {
        final String pan = bestKnownPan(tipCardNumber);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disDialog();
                if (pan.isEmpty()) {
                    LogUtils.e("card confirmation: no card number on any path"
                            + " - tip=" + tipCardNumber
                            + " current=" + currentCardNumber);
                    // Do not abort if in contactless flow - allow callback to proceed
                    callback.onResult(0);
                    return;
                }
                if (cardValid == null || cardValid.isEmpty()) {
                    String t2 = getTagValueSafe(0x57);
                    if (t2.isEmpty()) {
                        t2 = getTagValueSafe(0x9F6B);
                    }
                    if (!t2.isEmpty()) {
                        String t2Upper = t2.toUpperCase().replace("=", "D");
                        int dIndex = t2Upper.indexOf('D');
                        if (dIndex >= 0 && t2Upper.length() >= dIndex + 5) {
                            cardValid = t2Upper.substring(dIndex + 1, dIndex + 5);
                        }
                    }
                }
                new MessageDialog.Builder(getActivity())
                        .setTitle(R.string.card_number_confirmation_tip)
                        .setMessage(pan)
                        .setConfirm(keyboardDevice ? getString(R.string.common_cancel) : getString(R.string.common_confirm))
                        .setCancel(keyboardDevice ? getString(R.string.common_confirm) : getString(R.string.common_cancel))
                        .setCanceledOnTouchOutside(false)
                        .setListener(new MessageDialog.OnListener() {

                            @Override
                            public void onConfirm(BaseDialog dialog) {
                                dialog.dismiss();
                                if (keyboardDevice) {
                                    toast(getString(R.string.trans_canceled));
                                    canCleTransaction();
                                    callback.onResult(-1);
                                } else {
                                    callback.onResult(0);
                                }
                            }

                            @Override
                            public void onCancel(BaseDialog dialog) {
                                if (keyboardDevice) {
                                    callback.onResult(0);
                                } else {
                                    toast(getString(R.string.trans_canceled));
                                    canCleTransaction();
                                    callback.onResult(-1);
                                }

                            }
                        })
                        .show();
            }
        });
    }


    /**
     * 是否需要发起联机交易
     * Check whether a connection transaction needs to be initiated
     *
     * @param type 0:GAC1 1: GAC2
     */
    private void apiEmvGetKernelProcessResult(int type) {
        try {

            LogUtils.i("apiEmvGetKernelProcessResult: ----->" + (type == 0 ? "===GAC1===" : "===GAC2==="));

            disDialog();


            EmvGetKernelProgressResult emvGetKernelProgressResult = mEmvBinderService.emvGetKernelProgressResult();

            if (emvGetKernelProgressResult == null) {
                toast("The kernel returns an abnormal result...");
                LogUtils.e("apiEmvGetKernelProcessResult: 获取内核处理结果异常...");
                return;
            }

            int ret = emvGetKernelProgressResult.getRet();
            byte[] resultData = emvGetKernelProgressResult.getTagData();
            LogUtils.i("EmvCompletion ret :" + ret);
            if (ret != 0 && resultData == null) {
                toast("emvGetKernelProgressResult Error");
                return;
            }

            LogUtils.i("EmvCompletion bOutData :" + AFUtils.showResult0xStr(resultData));
            for (int i = 0; i < resultData.length; i++) {
                LogUtils.i("EmvCompletion : ITEM ->" + i + "\t" + AFUtils.showResult16Str(new byte[]{resultData[i]}));
            }
            String ucStatus = AFUtils.showResult16Str(new byte[]{resultData[1]});
            LogUtils.i("Kernel processing results:" + ucStatus);

            if ("40".equals(ucStatus)) {
                // For Contactless (RF_CARD) or MSD mode, proceed immediately to startTrans()
                if (cardType == CardSearchType.RF_CARD || type == 1) {
                    LogUtils.i("Kernel status is 0x40 on RF_CARD - proceeding to startTrans()");
                    startTrans();
                    return;
                }
                String tag5A = getTagValueSafe(0x5A);
                String tag57 = getTagValueSafe(0x57);
                String tag9F6B = getTagValueSafe(0x9F6B);
                if (!tag5A.isEmpty() || !tag57.isEmpty() || !tag9F6B.isEmpty()) {
                    LogUtils.i("Kernel status is 0x40 but card data is present (MSD mode) - proceeding to startTrans()");
                    startTrans();
                    return;
                }
                KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                getKeyCode();
                if (type == 0) {
                    toast(getString(R.string.trans_canceled));
                } else {
                    toast(getString(R.string.gac1_end_application));
                }
                canCleTransaction();
            } else {
                switch (type) {
                    case 0:
                        //IC
                        startTrans();
                        break;
                    case 1:
                        startTrans();
                        //RF
                        break;
                }
            }

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    /**
     * Initiate online transaction
     */
    private void startTrans() {
        LogUtils.i("----------------startTrans----------------");
        currentCardNumber = getTagValue(0x5A);

        if (currentCardNumber == null || currentCardNumber.isEmpty()) {
            String t2 = getTagValue(0x57);
            if (t2 != null && !t2.isEmpty()) {
                currentCardNumber = panFromTrack2(t2);
            }
        }
        if (currentCardNumber == null || currentCardNumber.isEmpty()) {
            String t2_9f6b = getTagValue(0x9F6B);
            if (t2_9f6b != null && !t2_9f6b.isEmpty()) {
                currentCardNumber = panFromTrack2(t2_9f6b);
            }
        }
        if (currentCardNumber == null || currentCardNumber.isEmpty()) {
            String t1 = getTagValue(0x56);
            if (t1 != null && !t1.isEmpty()) {
                currentCardNumber = panFromTrack1(t1);
            }
        }
        if (currentCardNumber == null || currentCardNumber.isEmpty()) {
            currentCardNumber = bestKnownPan("");
        }

        if (currentCardNumber == null || currentCardNumber.isEmpty()) {
            showReadCard("Card reading abnormality, transaction terminated", 0);
            return;
        }

        LogUtils.i("currentCardNumber: " + currentCardNumber + "\n");

        tipCardNumber = stripPadF(currentCardNumber);

        LogUtils.e("startTrans: tipCardNumber---->" + tipCardNumber);
        LogUtils.e("startTrans: cardType--->" + cardType);

        String cardAID = getTagValue(0x4F);
        if (cardAID == null || cardAID.isEmpty()) {
            cardAID = getTagValue(0x84);
        }
        LogUtils.i("CardAID: " + cardAID + "\n");
        String track2data = getTagValue(0x57);
        if (track2data == null || track2data.isEmpty()) {
            track2data = getTagValue(0x9F6B);
        }
        LogUtils.i("Track2data: " + track2data + "\n");
        String cardSerialNumber = getTagValue(0x5F34);
        LogUtils.i("CardSerialNumber: " + cardSerialNumber + "\n");
        cardValid = getTagValue(0x5F24);
        if ((cardValid == null || cardValid.isEmpty()) && track2data != null) {
            String t2Upper = track2data.toUpperCase().replace("=", "D");
            int dIndex = t2Upper.indexOf('D');
            if (dIndex >= 0 && t2Upper.length() >= dIndex + 5) {
                cardValid = t2Upper.substring(dIndex + 1, dIndex + 5);
            }
        }
        LogUtils.i("0x5F24 卡有效期 CardValid: " + cardValid + "\n");
        String terminalCountryCode = getTagValue(0x9F1A);
        LogUtils.i("Terminal country code: " + terminalCountryCode + "\n");

        LogUtils.i("------------【Get IC DATA】------------");
        int[] icDataTAGList = new int[]{
                0X5F2A, 0X82, 0X84, 0X95, 0X9A,
                0X9C, 0X9F02, 0X9F03, 0X9F09, 0X9F10,
                0X9F1A, 0X9F26, 0X9F27, 0X9F33, 0X9F34,
                0X9F35, 0X9F36, 0X9F37, 0X9F41, 0X5F34};

        int[] rfDataTAGList = new int[]{
                0X5F2A, 0X82, 0X84, 0X95, 0X9A,
                0X9C, 0X9F02, 0X9F03, 0X9F09, 0X9F10,
                0X9F1A, 0X9F26, 0X9F27, 0X9F33, 0X9F34,
                0X9F35, 0X9F36, 0X9F37, 0X9F41, 0X5F34};

        String icData = (cardType == 0 ? getICData(icDataTAGList) : getICData(rfDataTAGList));

        saleDataModel.setCardNo(currentCardNumber);
        saleDataModel.setCardSerialNumber(cardSerialNumber);
        saleDataModel.setCardAID(cardAID);
        saleDataModel.setTrack2data(track2data);
        saleDataModel.setCardValid(cardValid);

        saleDataModel.setIcData(icData);

        LogUtils.i("IC Data : \n" + saleDataModel.getIcData());
        saleDataModel.setTransAmount(transAmount);
        saleDataModel.setTerminalCountryCode(terminalCountryCode);

        LogUtils.i("saleDataModel:" + saleDataModel.toString());

        if (cardType == CardSearchType.IC_CARD) {
            // Contact IC card: Card confirmation was already performed before PIN entry in EmvCardNoConfirm.
            // If the kernel skipped EmvCardNoConfirm for any reason, show it now as a fallback.
            if (mCardConfirmed) {
                toast(getString(R.string.start_trans));
                mHandler.sendEmptyMessageDelayed(8003, 1000);
            } else {
                showConfirmationDialog(tipCardNumber, new ConfirmationCallback() {
                    @Override
                    public void onResult(int result) {
                        if (result != 0) {
                            canCleTransaction();
                            return;
                        }
                        mCardConfirmed = true;
                        toast(getString(R.string.start_trans));
                        mHandler.sendEmptyMessageDelayed(8003, 1000);
                    }
                });
            }
        } else if (cardType == CardSearchType.RF_CARD) {
            // Contactless card (RF_CARD): Card reading is successful.
            // Show Card Confirmation Box FIRST before checking CVM limit or requesting PIN!
            showConfirmationDialog(tipCardNumber, new ConfirmationCallback() {
                @Override
                public void onResult(int result) {
                    if (result != 0) {
                        canCleTransaction();
                        return;
                    }
                    mCardConfirmed = true;
                    TerminalConfig tc = TerminalConfig.get(getActivity());
                    long amt = TerminalConfig.parseMajorToMinor(transAmount);
                    long limit = tc.clessCvmLimit();
                    if (amt >= 0 && amt <= limit) {
                        LogUtils.i("contactless " + transAmount + " is within CVM limit "
                                + tc.clessCvmLimitText() + " - no PIN required");
                        toast(getString(R.string.start_trans));
                        mHandler.sendEmptyMessageDelayed(8003, 1000);
                        return;
                    }
                    showPinPad(true, tipCardNumber, 1);
                }
            });
        } else {
            toast(getString(R.string.start_trans));
            mHandler.sendEmptyMessageDelayed(8003, 1000);
        }
    }

    private int rfCardConfirm(String tipCardNumber) {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] resultHolder = new int[1];
        showConfirmationDialog(tipCardNumber, new ConfirmationCallback() {
            @Override
            public void onResult(int result) {
                if (result == 0) {
                    resultHolder[0] = 0;
                } else if (result == -1) {
                    // Handle cancel
                    resultHolder[0] = -1;
                }
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return resultHolder[0];

    }

    /**
     * 关闭EMV寻卡交易
     * Disable EMV card search
     */
    private void closeFindCard() {
        LogUtils.e("==========【closeFindCardForEMV】==========");
        try {
            disDialog();
            mCardBinderService.closeFindCardForEMV();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.i("onKeyDown: 820");
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
                   /* switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                            setEditText(INITIAL_AMOUNT_OF_MONEY);
                            break;
                        default:
                            LogUtils.i( "暂不处理1.....");
                            break;
                    }*/
                    finish();
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    // CLEAR
                   /* Log.d(TAG, "Confirm button KEYCODE_DEL");
                    switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                            int start = etAmount.length() - 1;
                            LogUtils.i("onKeyUp: start---> " + start);
                            if (start >= 3) {
                                LogUtils.i("回退键....");
                                etAmount.getText().delete(start, start + 1);
                                setEditText(etAmount.getText().toString());
                            }
                            break;
                        default:
                            LogUtils.i("暂不处理2.....");
                            break;
                    }*/
                    Log.d(TAG, "DEL pressed");
                    if (KEYCODE_ENTER_ACTION == KEYCODE_ENTER_START_SEARCH_CARD) {
                        uiHandler.post(this::backspaceDigit);
                    }
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }

        LogUtils.e("onKeyDown: xxxxxx");
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.i("onKeyUp: 820");
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (leftIcon != null) {
                        leftIcon.setFocusable(true);
                        leftIcon.setFocusableInTouchMode(true);
                        leftIcon.requestFocus();
                    }
                    switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                            LogUtils.i("发起交易.....");
                            if ("0.00".equals(getAmount())) {
                                toast(getString(R.string.amount_cannot_empty));
                            } else {
                                //Intent intent = new Intent(AmountActivity.this, SelectTransTypeActivity.class);
//                                Intent intent = new Intent(AmountActivity.this, SelectCardnoMethodActivity.class);
                                Intent intent = new Intent(AmountActivity.this, FindCardActivity.class);
                                intent.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, getAmount());
                                startActivity(intent);
                                //startActivity(SelectTransTypeActivity.class);
                                try {
                                    mSystemBinderService.closeGetKeypadValue();
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case KEYCODE_ENTER_INPUT_PIN:
                            LogUtils.i("PIN输入.....");
                            break;
                        default:
                            LogUtils.i("KeyEvent  default---> " + getAmount());
                            if ("0.00".equals(getAmount())) {
                                toast(getString(R.string.amount_cannot_empty));
                            }
                            break;
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


    /**
     * 980 LCD屏幕显示
     *
     * @param amount
     */
    private void displayTransactionAmountASCIIX(double amount) {
        // 格式化金额：$0000123.45
        String formattedAmount = String.format("$%08.2f", amount);
//        String formattedAmount = "HELLO";
        Log.i(TAG, "formattedAmount: " + formattedAmount);

        lcdInfo.getWidth();
        lcdInfo.getHeight();

        // 居中计算
        int fontSize = 24;
        int charWidth = 12;
        int totalWidth = formattedAmount.length() * charWidth;
        int startX = (128 - totalWidth) / 2;
        int yPos = 30;

        try {
            lcdClearScreen();
            // 显示金额字符串
            int result = mSystemBinderService.lcdDisplayString(5, 5, formattedAmount, fontSize, 0xFFFF, 0x0000);
            if (result != 0) {
                Log.e(TAG, "Display error: " + result);
                return;
            }
            Log.e(TAG, "lcdDisplayString Result: " + result);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdDisplayString Error : " + e.getMessage());
        }
    }


    private void displayTransactionAmountASCII(double amount) {

        if (isLcdSupported && lcdInfo != null) {
            // 格式化金额：$0000123.45
            String formattedAmount = String.format("$%08.2f", amount);
            Log.i(TAG, "formattedAmount: " + formattedAmount);

            try {
                int screenWidth = lcdInfo.getWidth();
                int screenHeight = lcdInfo.getHeight();
                int screenType = lcdInfo.getType();
                Log.i(TAG, "screenWidth: " + screenWidth);
                Log.i(TAG, "screenHeight: " + screenHeight);
                int fontSize = calculateOptimalFontSize(formattedAmount, screenWidth, screenHeight);
                Log.e(TAG, "fontSize: " + fontSize);
                // 重新计算居中起始位置
                int charWidth = fontSize / 2;
                int totalWidth = formattedAmount.length() * charWidth;
                int startX = (screenWidth - totalWidth) / 2;
                int startY = (screenHeight - fontSize) / 2; // 居中显示

                Log.i(TAG, "startX: " + startX);
                Log.i(TAG, "startY: " + startY);

//                lcdClearScreen();

                // 设置字体颜色和背景色（根据屏幕类型区分）
              /*  int fontColor = (screenType == 1) ? 0xFFFF : 0x0000;  // 黑白屏：白字，彩屏：黑字
                int bgColor = (screenType == 1) ? 0x0000 : 0xFFFF;    // 黑白屏：黑底，彩屏：白底*/

                int fontColor;
                int bgColor;
                if (screenType == 1) {
                    // 黑白屏
                    fontColor = 0xFFFF; // 白字
                    bgColor = 0x0000;   // 黑底
                } else {
                    // 彩屏
                    fontColor = 0xFFFF; // 白字
                    bgColor = 0x0000;   // 黑底
//                    bgColor = 0xFF414241;   // 黑底
                }


                int result = mSystemBinderService.lcdDisplayString(startX, startY, formattedAmount, fontSize, fontColor, bgColor);
                if (result != 0) {
                    Log.e(TAG, "Display error: " + result);
                }
                Log.i(TAG, "lcdDisplayString Result: " + result);
            } catch (RemoteException e) {
                e.printStackTrace();
                Log.e(TAG, "lcdDisplayString Error: " + e.getMessage());
            }
        }
    }


    /**
     * 根据屏幕宽高和文本长度计算最佳字体大小（仅限 12, 16, 24, 48, 64）
     *
     * @param text         文本内容
     * @param screenWidth  屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 最佳字体大小
     */
    private int calculateOptimalFontSize(String text, int screenWidth, int screenHeight) {
        int[] allowedFontSizes = {64, 48, 24, 16, 12};

        for (int fontSize : allowedFontSizes) {
            int charWidth = fontSize / 2; // 计算字符宽度，假设宽度为字体大小的一半
            int totalWidth = text.length() * charWidth;
            int totalHeight = fontSize;

            if (totalWidth <= screenWidth && totalHeight <= screenHeight) {
                Log.i(TAG, "Selected Font Size: " + fontSize);
                return fontSize;
            }
        }

        // 如果所有尺寸均不满足，选择最小字体 12
        Log.w(TAG, "Text too large, fallback to minimum font size 12");
        return 12;
    }


    private void displayTransaction(String tip) {
        try {

            int screenWidth = lcdInfo.getWidth();
            int screenHeight = lcdInfo.getHeight();
            int fontSize = calculateOptimalFontSize(tip, screenWidth, screenHeight);
            Log.e(TAG, "fontSize: " + fontSize);
            // 重新计算居中起始位置
            int charWidth = fontSize / 2;
            int totalWidth = tip.length() * charWidth;
            int startX = (screenWidth - totalWidth) / 2;
            int startY = (screenHeight - fontSize) / 2; // 居中显示

            lcdClearScreen();
            int result = mSystemBinderService.lcdDisplayString(startX, startY, tip, fontSize, 0xFFFF, 0x0000);
            if (result != 0) {
                Log.e(TAG, "Display error: " + result);
            }
            Log.i(TAG, "lcdDisplayString Result: " + result);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "lcdDisplayString Error: " + e.getMessage());
        }
    }


    private void lcdDrawPicture(String[] msg) {
        try {
            Bitmap printBmp;
            printBmp = createLcdBitmap(printLcdMsg(msg), lcdInfo.getWidth(), lcdInfo.getHeight());
            Log.e(TAG, "bmp width: " + printBmp.getWidth() + "." + "\n" + "bmp height: " + printBmp.getHeight() + "." + "\n");
            int x = 0;
            int width = printBmp.getWidth();
            int height = printBmp.getHeight();
            byte[] bmpData = PrintUtil.getBitmapData(printBmp);
            LogUtils.d("width:" + width);
            LogUtils.d("height:" + height);
            LogUtils.d("bmpData:" + AFUtils.showResult16Str(bmpData));
            int result = mSystemBinderService.lcdDrawPicture(5, 5, bmpData, bmpData.length, width, height);
            Log.e(TAG, "lcdDrawPicture Result: " + result);

        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

    private void lcdRefreshScreen() {
        try {
            int result = mSystemBinderService.lcdRefreshScreen();
            Log.e(TAG, "lcdRefreshScreen Result: " + result);
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }


    /**
     * 将文字 生成 文字图片 的Bitmap, 打印的位图需要 黑字 白底(A Bitmap that generates text into text images. Printed bitmaps need black text on a white background)
     */
    public Bitmap createLcdBitmap(String text, int width, int height) {
        Bitmap newBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(newBitmap, 0, 0, null);
        TextPaint textPaint = new TextPaint();
       /* if (isBold) {
            String familyName = "宋体";
            Typeface font = Typeface.create(familyName, Typeface.BOLD);
            textPaint.setTypeface(font);
        }*/
        textPaint.setAntiAlias(true);
        textPaint.setFilterBitmap(true);//对位图进行滤波处理
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(16);
        // "在Android开发中，Canvas.drawText不会换行，即使一个很长的字符串也只会显示一行，超出部分会隐藏在屏幕之外.StaticLayout是android中处理文字的一个工具类，StaticLayout 处理了文字换行的问题";
        StaticLayout sl = new StaticLayout(text, textPaint, newBitmap.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        textPaint.setStyle(Paint.Style.FILL);
        canvas.translate(0, height / 10);
        sl.draw(canvas);
        return newBitmap;
    }

    private StringBuffer sbLcdMsg = new StringBuffer();

    private String printLcdMsg(String[] msg) {
        sbLcdMsg.setLength(0);
        for (String item : msg) {
            sbLcdMsg.append(item);
        }
        LogUtils.i("sbLcdMsg: " + sbLcdMsg.toString());
        return sbLcdMsg.toString();
    }


    private void lcdClearScreen() {
        try {
            if (lcdInfo != null) {
//                int fontColor = (lcdInfo.getType() == 1) ? 0x0000 : 0xFFFF;  // 黑白屏：白字，彩屏：黑字
                int fontColor = (lcdInfo.getType() == 1) ? 0x0000 : 0x0000;  // 黑白屏：白字，彩屏：黑字
                int result = mSystemBinderService.lcdClearScreen(fontColor);
                Log.e(TAG, "lcdClearScreen Result: " + result);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, e.getMessage());
        }
    }

}
