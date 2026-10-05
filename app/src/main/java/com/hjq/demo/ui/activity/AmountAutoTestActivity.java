package com.hjq.demo.ui.activity;

import static android.graphics.Bitmap.createBitmap;

import android.content.IntentFilter;
import android.graphics.Bitmap;
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
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.TimeUtils;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.WorkingKeyEntity;
import com.hjq.demo.ilistener.MainListener;
import com.hjq.demo.model.MainModel;
import com.hjq.demo.model.MainModelImpl;
import com.hjq.demo.model.SaleDataModel;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.ui.dialog.PayPasswordDialog;
import com.hjq.demo.ui.dialog.SelectDialog;
import com.hjq.demo.ui.dialog.TipsDialog;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.ui.view.KeyBoardView.IKeyBoardListener;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.DateDifferentExample;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PowerReceiver;
import com.hjq.demo.utils.PrintUtil;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.card.ICardBinderService;
import com.szanfu.sdk.api.card.ICardTestCallback;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.emv.CommonPPSEStatus;
import com.szanfu.sdk.api.emv.EmvTermParam;
import com.szanfu.sdk.api.emv.GetAllAmtResult;
import com.szanfu.sdk.api.emv.GetHolderPwdResult;
import com.szanfu.sdk.api.emv.IEmvBinderService;
import com.szanfu.sdk.api.emv.IPayPassBinderService;
import com.szanfu.sdk.api.emv.IPayWaveBinderService;
import com.szanfu.sdk.api.emv.IQpbocBinderCallback;
import com.szanfu.sdk.api.emv.IQpbocBinderService;
import com.szanfu.sdk.api.emv.InputAmtResult;
import com.szanfu.sdk.api.emv.NeedOnlineParam;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.KeyInfoParam;
import com.szanfu.sdk.api.pinpad.PinGetPinBlockResult;
import com.szanfu.sdk.api.pinpad.PosGetKcvResult;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.ISystemEncDecBinderService;
import com.szanfu.sdk.api.util.HexUtil;

import org.apache.commons.lang3.StringUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 该页面为做交易自动化测试用的
 * 1.进入页面 ---> 点击”测试“按钮 ---> 自动输入金额（5秒） ---> 显示卡号弹框自动消失（1秒） ---> 输入密码弹框自动消失(1秒) --->交易成功打印（此过程死循环，不需要人工介入），如需手动停止测试，可点击”终止“按钮
 * 2.统计消费次数（消费开始到关机）
 * 3.统计消费前的电量到关机前的电量
 * 4.统计开始消费到关机前时长
 **/
public class AmountAutoTestActivity extends AppActivity {
    private static final String TAG = "AmountAutoTestActivity";
    private TextView tvType;
    private EditText etAmount;
    private KeyBoardView keyBoardView;
    private TextView tvTitle;
    public static final int FIND_CARD_TIMEOUT = 30;
    private ISdkServiceManager mSdkServiceManager = null;
    private ICardBinderService mCardBinderService;
    private IEmvBinderService mEmvBinderService;
    private IQpbocBinderService mQpbocBinderService;
    private IPayPassBinderService mPayPassBinderService;
    private IPayWaveBinderService mPayWaveBinderService;
    private static ISystemBinderService mSystemBinderService;
    private IPinPadBinderService mPinPadBinderService;
    private ISystemEncDecBinderService mISystemEncDecBinderService;
    private IPrinterBinderService mPrinterBinderService;
    private static final int MSG_TRANS_MSR_SUCCESS = 0;
    private static final int MSG_TRANS_IC_RF_SUCCESS = 1;
    private static final int MSG_TRANS_FAil = 2;
    protected ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //当前卡号
    private static String currentCardNumber;
    //当前金额
    private static String currentAmount;
    private MainModel mainModel;
    public static final int MAX_BIT_WIDTH = 384;
    StringBuffer sbMsg = new StringBuffer();

    private static SimpleDateFormat SDF_TAG_9A_YYDD = new SimpleDateFormat("yyMMdd");//交易日期
    private static SimpleDateFormat SDF_TAG_9A = new SimpleDateFormat("yyMMddHHmmss");//交易日期

    private static SimpleDateFormat SDF_DEVICES_TEST = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //记录测试次数
    private SPUtils spUtils = SPUtils.getInstance("SP_Test");
    //设备最后一次测试日期
    private static final String DEVICES_LAST_TEST = "DevicesLastTest";
    //测试次数
    private static final String DEVICES_TRANS_COUNT = "devices_trans_count";
    private static final String START_TEST_TIME = "start_test_time";
    private static final String END_TEST_TIME = "end_test_time";
    //初始钱数
    private static final String INITIAL_AMOUNT_OF_MONEY = "0.00";

    private SaleDataModel saleDataModel = null;

    PowerReceiver mPowerReceiver = null;

    PayPasswordDialog.Builder payPasswordDialog;
    MessageDialog.Builder messageDialog;

    private void unRegisterBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unregisterReceiver(mPowerReceiver);
            mPowerReceiver = null;
        }
    }

    private void registerBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unRegisterBroadCastReceiver();
        }
        mPowerReceiver = new PowerReceiver(new PowerReceiver.DeviceStatusMonitoring() {
            @Override
            public void onShutDown() {
                LogUtils.i( "onShutDown: --->我关机了.....");
                spUtils.put(END_TEST_TIME, SDF_DEVICES_TEST.format(new Date()));
            }
        });
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        intentFilter.addAction("android.intent.action.REBOOT");
        registerReceiver(mPowerReceiver, intentFilter);
        LogUtils.i( "registerBroadCastReceiver: 已经注册广播...");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unRegisterBroadCastReceiver();
        mHandler.removeCallbacksAndMessages(null);
    }

    public static ISystemBinderService getSystemSingleton() {
        return mSystemBinderService;
    }

    private String printMsg() {
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
        sbMsg.append(getString(R.string.print_merchant_name) + "\n                               DBB PLC TEST POS\n");
        sbMsg.append(getString(R.string.print_terminal_no) + "\n                                              " + generateRandomNumber(8) + "\n");
        sbMsg.append(getString(R.string.print_operator_no) + "\n                                              00000001\n");
        sbMsg.append(getString(R.string.print_card_no) + "\n                                              " + currentCardNumber + "\n");
        sbMsg.append(getString(R.string.print_txn_type) + "\n                                                      " + getString(R.string.print_txn_type_info) + "\n");
        sbMsg.append(getString(R.string.print_exp) + "\n                                                2023/08" + "\n");
        id = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);
        sbMsg.append(getString(R.string.print_ref_no) + "\n                                     " + id + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_amount) + getAmount() + "\n");
        sbMsg.append("------------------------------------------------------\n\n");
        sbMsg.append(getString(R.string.sing_tip) + "\n\n");
        sbMsg.append(getString(R.string.sign) + "\n\n");
        savaTrans(id, date_time);
        LogUtils.i( "printMsg: " + sbMsg.toString());
        return sbMsg.toString();
    }

    private void savaTrans(String id, String date_time) {
        TransOrder order = new TransOrder();
        order.setOrder_amount(currentAmount);
        order.setOrder_id(id);
        order.setOrder_date_time(date_time);
        LogUtils.i( "savaTrans: id" + order.toString());
        boolean saveRes = order.save();
        LogUtils.i( "savaTrans: " + saveRes);
        order = null;
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
        LogUtils.i( "generateRandomNumber: " + sb.toString());
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
                    LogUtils.i( "stopPinPad: Success");
                    Bundle printErr = msg.getData();
                    String print_err_info = printErr.get("print_err").toString();
                    new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.pop_notice)
                            // 内容必须要填写
                            .setMessage(print_err_info)
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(null)
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    //toast("确定");
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
                    showDialog1("Find Card...");
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
                    if (payPasswordDialog != null) {
                        payPasswordDialog.dismiss();
                    }
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.trans_waiting))
                            .create();
                    mWaitDialog.show();
                    mHandler.sendEmptyMessageDelayed(8003, 1000);
                    break;
                case 8003:
                    LogUtils.i( "打印小票...");
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
                    disDialog();
                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                    break;
                case 8005:
                    if (messageDialog != null) {
                        messageDialog.dismiss();
                    }
                    showPinPad();
                    break;
                case MSG_TRANS_MSR_SUCCESS:
                    disDialog();
                    Bundle cardData = msg.getData();
                    String cardNumber = cardData.get("track2Data").toString();
                    if (cardNumber.contains("F")) {
                        cardNumber.replaceAll("F", "");
                    }
                    int cardLen = cardNumber.length();
                    currentCardNumber = cardNumber.substring(cardLen - 9).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    //String tipCard = cardNumber.replaceAll("(\\d{4})\\d{8}(\\d{4})", "$1********$2");
                    LogUtils.i( "tipCard:" + currentCardNumber);

                    //6228480018292192277F
                    // 消息对话框
                    messageDialog = new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_success)
                            .setCancelable(false)
                            // 内容必须要填写
                            .setMessage(currentCardNumber)
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
                                    showPinPad();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
//                                    etAmount.setText("￥0.00");
                                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                                    //keyBoardView.setButtonClickBle(true);
                                }
                            });
                    messageDialog.show();
                    mHandler.sendEmptyMessageDelayed(8005, 1000);
                    break;
                case MSG_TRANS_IC_RF_SUCCESS:
                    disDialog();
                    Bundle cardDataBundle = msg.getData();
                    String icCardNumber = cardDataBundle.get("cardData").toString();
                    //int cardType = Integer.parseInt(cardDataBundle.get("cardType").toString());
                    LogUtils.i( "icCardNumber:" + icCardNumber);
                    //LogUtil.i(TAG, "cardType:" + cardType);
                    int icCardNumberLen = icCardNumber.length();
                    if (icCardNumber.contains("F")) {
                        icCardNumber = icCardNumber.replaceAll("F", "");
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 10).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    } else {
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 9).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    }
                    LogUtils.i( "icCardTip:" + currentCardNumber);

                    //$1 $2 表示正则表达式里面的第一个和第二个，也就是括号里面的内容
                    // 消息对话框
                    messageDialog = new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_success)
                            .setCancelable(false)
                            // 内容必须要填写
                            .setMessage(currentCardNumber)
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
                                    showPinPad();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
//                                    etAmount.setText("￥0.00");
                                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                                    //keyBoardView.setButtonClickBle(true);
                                }
                            });
                    messageDialog.show();
                    mHandler.sendEmptyMessageDelayed(8005, 1000);
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
                default:
                    break;
            }
        }
    };

    private void showPinPad() {
        payPasswordDialog = new PayPasswordDialog.Builder(this)
                .setTitle(getString(R.string.pay_title))
                .setMoney(getString(R.string.print_amount) + currentAmount)
                .setListener(new PayPasswordDialog.OnListener() {
                    @Override
                    public void onCompleted(BaseDialog dialog, String password) {
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                    }
                });

        payPasswordDialog.show();
        mHandler.sendEmptyMessageDelayed(8002, 1000);
    }

    private void printerTest() {
        try {
            Bitmap bmp = creatTextBitmap(printMsg(), 384, 1200);
            LogUtils.i( "bmp width " + bmp.getWidth() + "\n" + "bmp height " + bmp.getHeight() + "\n");
            int x = 0;
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            int bitW = PrintUtil.getPaddingBitWidth(width);
            byte[] bitmapData = PrintUtil.getBitmapData(bmp);

            mPrinterBinderService.printBitmap(x, bitmapData, bitW, height, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
                    setEditText(INITIAL_AMOUNT_OF_MONEY);
                    if (errorCode == ErrorCode.SUCCESS) {
                        disDialog();
                        LogUtils.i( getResources().getString(R.string.succeed) + "\n");
                        //继续自动交易
                        enterAmount();
                    } else {
                        Message printErr = new Message();
                        printErr.what = 3000;
                        Bundle bundle = new Bundle();
                        bundle.putCharSequence("print_err", errorMsg);
                        //bundle.putCharSequence("print_err", "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n");
                        printErr.setData(bundle);
                        mHandler.sendMessage(printErr);
                        LogUtils.i( "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n");
                    }
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
        return R.layout.activity_amount_autotest;
    }

    /**
     * 初始化控件
     */
    @Override
    protected void initView() {
        tvType = (TextView) findViewById(R.id.am_type);
        etAmount = (EditText) findViewById(R.id.am_amount);
        keyBoardView = (KeyBoardView) findViewById(R.id.am_kb_view);
        keyBoardView.setButtonClickBle(false);
        keyBoardView.setTv_cancel("测试");
        keyBoardView.setTv_clear("终止");
        keyBoardView.setTv_confirm("交易次数");
        etAmount.requestFocus();
        setEditText(INITIAL_AMOUNT_OF_MONEY);
        bindService();
        initEvent();
        registerBroadCastReceiver();
    }

    /**
     * 设备签到
     */
    private void devicesSign() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                //导入主密钥
                //setPOSWriteKey();
                //导入工作秘钥
                mainModel.AFPosSignIn();
            }
        });
    }

    private void initEvent() {
        // TODO Auto-generated method stub
        keyBoardView.setIKetBoardListener(new IKeyBoardListener() {
            @Override
            public void getNumber(String s) {
                // TODO Auto-generated method stub
                String str = getAmount();
                if (str.equals("0.00")) {
                    str = "0.0" + s;
                    setEditText(str);
                } else {
                    etAmount.append(s);
                    setEditText(getAmount());
                }
            }

            @Override
            public void clear() {
                // TODO Auto-generated method stub
                setEditText(INITIAL_AMOUNT_OF_MONEY);
                stopTest();
            }

            @Override
            public void confirm() {
                // TODO Auto-generated method stub
                toast(getTransInfo());
            }

            @Override
            public void cancel() {
                // TODO Auto-generated method stub
//                int start = etAmount.length() - 1;
//                if (start >= 3) {
//                    etAmount.getText().delete(start, start + 1);
//                    setEditText(etAmount.getText().toString());
//                }
                //开始测试
                enterAmount();
            }
        });

        etAmount.addTextChangedListener(new TextWatcher() {
            String numberStr;

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // TODO Auto-generated method stub
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
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // TODO Auto-generated method stub
            }

            @Override
            public void afterTextChanged(Editable s) {
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

    //模拟刷卡
    private void openFindCardForEMVS(String moneyValue) {
        mHandler.sendEmptyMessage(8000);
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    if (mCardBinderService == null) {
                        toast("CardService服务获取失败！");
                        mHandler.sendEmptyMessage(8004);
                        return;
                    }

                    int cardType = mCardBinderService.openFindCardForEMV(7, FIND_CARD_TIMEOUT);
                    LogUtils.i( getResources().getString(R.string.open_emv_card_search_results) + cardType);
                    mCardBinderService.closeFindCardForEMV();
                    switch (cardType) {
                        case 1:
                            //磁条
                            Message cardMsg = new Message();
                            cardMsg.what = MSG_TRANS_MSR_SUCCESS;
                            Bundle cardBundle = new Bundle();
                            String track2Data = "6228480018292192277";
                            cardBundle.putInt("cardType", 2);
                            String strTrack2Data = TextUtils.isEmpty(new String(track2Data).trim()) ? "Read Card Error" : (new String(track2Data)).trim();
                            if (strTrack2Data.contains("=")) {
                                cardBundle.putCharSequence("track2Data", strTrack2Data.substring(0, strTrack2Data.indexOf("=")));
                            } else {
                                cardBundle.putCharSequence("track2Data", strTrack2Data);
                            }
                            cardMsg.setData(cardBundle);
                            mHandler.sendMessage(cardMsg);
                            break;
                        case 2:
                        case 4:
                            //IC
                            Message icCardMsg = new Message();
                            icCardMsg.what = MSG_TRANS_IC_RF_SUCCESS;
                            Bundle icCardBundle = new Bundle();
                            String icCardNo = "6228480018292192279";
                            icCardBundle.putCharSequence("cardData", icCardNo);
                            icCardMsg.setData(icCardBundle);
                            mHandler.sendMessage(icCardMsg);
                            break;
                        default:
                            toast(getString(R.string.card_search_failed_ret));
                            mHandler.sendEmptyMessage(8004);
                            break;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /* 1.输入金额界面----->自动输入金额（5秒）--------->自动输入密码（5秒）--------->交易成功打印（此过程死循环，不需要人工介入）
       2.统计消费次数（消费开始到关机）
       3.统计消费前的电量到关机前的电量
       4.统计开始消费到关机前时长    */

    /**
     * 获取测试记录
     *
     * @return 交易记录信息
     */
    private String getTransInfo() {
        StringBuffer sbTestInfo = new StringBuffer();
        sbTestInfo.setLength(0);
        //开始测试时间
        if (spUtils.contains(START_TEST_TIME)) {
            String tempStartTestTime = spUtils.getString(START_TEST_TIME);
            sbTestInfo.append("\n开始测试时间:" + tempStartTestTime);
        }
        //设备关机
        if (spUtils.contains(END_TEST_TIME)) {
            String tempEndTestTime = spUtils.getString(END_TEST_TIME);
            sbTestInfo.append("\n设备关机时间:" + tempEndTestTime);
        }
        //开始测试时间|设备关机时间|测试总次数
        if (spUtils.contains(DEVICES_TRANS_COUNT)) {
            int tempTransCount = spUtils.getInt(DEVICES_TRANS_COUNT, 0);
            sbTestInfo.append("\n测试总次数:" + tempTransCount);
        }
        if (spUtils.contains(START_TEST_TIME) && spUtils.contains(END_TEST_TIME)) {
            String tempStartTestTime = spUtils.getString(START_TEST_TIME);
            String tempEndTestTime = spUtils.getString(END_TEST_TIME);
            String timeDiff = DateDifferentExample.getTimeSpan(tempStartTestTime, tempEndTestTime);
            sbTestInfo.append("\n测试时长:" + timeDiff);
        }

        return sbTestInfo.toString();
    }

    /**
     * 设备交易次数
     *
     * @return 交易总次数
     */
    private int getTransCount() {
        return spUtils.getInt(DEVICES_TRANS_COUNT, 0);
    }

    /**
     * 保存测试记录
     */
    private void devicesTransSave() {
        if (spUtils.contains(DEVICES_TRANS_COUNT)) {
            int tempCount = spUtils.getInt(DEVICES_TRANS_COUNT);
            spUtils.put(DEVICES_TRANS_COUNT, tempCount + 1);
        } else {
            //默认设备首次安装先签到
            spUtils.put(DEVICES_TRANS_COUNT, 1);
            LogUtils.i( "首次安装APP，保存测试次数记录....");
            spUtils.put(START_TEST_TIME, SDF_DEVICES_TEST.format(new Date()));
        }
    }

    /**
     * 设备是否需要记录今天测试记录
     * 当天测试日期|开始测试时间|设备关机时间|测试总次数
     */
    private void initDevicesAutoTest() {
        //判断设备今天是否有记录
        if (spUtils.contains(DEVICES_LAST_TEST)) {
            String strLastSign = spUtils.getString(DEVICES_LAST_TEST);
            LogUtils.i( "当前设备最后一次自动测试时间:\t" + strLastSign);
            boolean isToday = TimeUtils.isToday(strLastSign);
            if (isToday) {
                //无需签到
                LogUtils.i( "设备今日已签到");
            } else {
                LogUtils.i( "设备今日未签到，开始签到....");
                //devicesSign();
            }
        } else {
            //默认设备首次安装先记录测试时间
            spUtils.put(DEVICES_LAST_TEST, SDF_DEVICES_TEST.format(new Date()));
            LogUtils.i( "首次安装APP，开始记录自动测试时间....");
            //devicesSign();
        }
    }

    /**
     * 是否正在自动测试
     */
    private boolean isRun = false;

    /**
     * 取消自动测试
     */
    private void stopTest() {
        isRun = false;
        mHandler.removeCallbacks(myTest);
        disDialog();
    }

    private Runnable myTest = new Runnable() {
        @Override
        public void run() {
            String tempAmount = generateRandomNumber(4);
            if ("0".equals(tempAmount.substring(0, 1))) {
                setAmount("9000");
            } else {
                setAmount(tempAmount);
            }
            currentAmount = etAmount.getText().toString();
            openFindCardForEMVS(tempAmount);
        }
    };

    /**
     * 提示框
     *
     * @param msgTip 提示语
     */
    public void showDialog(String msgTip) {
        if (mWaitDialog != null) {
            LogUtils.i( "showDialog: mWaitDialog != null");
            mWaitDialog.dismiss();
        }
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mWaitDialog = new WaitDialog.Builder(getActivity())
                        .setMessage(msgTip)
                        .setCancelable(false)
                        .create();
                mWaitDialog.show();
            }
        });
    }

    public void showDialog1(String msgTip) {
        if (mWaitDialog != null) {
            LogUtils.i( "showDialog: mWaitDialog != null");
            mWaitDialog.dismiss();
        }

        mWaitDialog = new WaitDialog.Builder(getActivity())
                .setMessage(msgTip)
                .setCancelable(false)
                .create();
        mWaitDialog.show();

    }

    private void disDialog() {
        LogUtils.i( "disDialog: 调用关闭Dialog");
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
            mWaitDialog = null;
            LogUtils.i( " mWaitDialog != null");
            return;
        }
        LogUtils.i( " mWaitDialog == null");
    }

    /**
     * 输入金额
     */
    private void enterAmount() {
        isRun = true;
        devicesTransSave();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showDialog("正在准备第" + getTransCount() + "次交易...");
            }
        }, 2 * 1000);
        mHandler.postDelayed(myTest, 5 * 1000);
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
            mQpbocBinderService = IQpbocBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_QPBOC));
            mPayPassBinderService = IPayPassBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PAYPASS));
            mPayWaveBinderService = IPayWaveBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PAYWAVE));
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            mPinPadBinderService = IPinPadBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PINPAD));
            mISystemEncDecBinderService = ISystemEncDecBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM_ENCDEC));
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
            mQpbocBinderService.setCallback(new IQpbocBinderCallback.Stub() {
                @Override
                public InputAmtResult onInputAmt() throws RemoteException {
                    LogUtils.i( "Callback => onInputAmt \n");
                    InputAmtResult inputAmtResult = new InputAmtResult();
                    inputAmtResult.setRet(0);
                    inputAmtResult.setAuthAmt(HexUtil.hexStringToByte("000000001000"));
                    inputAmtResult.setCashbackAmt(HexUtil.hexStringToByte("000000002000"));
                    return inputAmtResult;
                }

                @Override
                public void onAdviceProc() throws RemoteException {
                    LogUtils.i( "Callback => onAdviceProc \n");
                }

                @Override
                public GetHolderPwdResult onGetHolderPwd(int iTryFlag, int iRemainCnt) throws RemoteException {
                    LogUtils.i( "Callback => onGetHolderPwd");
                    LogUtils.i( "iTryFlag: " + iTryFlag + "\niRemainCnt: " + iRemainCnt + "\n");
                    GetHolderPwdResult getHolderPwdResult = new GetHolderPwdResult();
                    getHolderPwdResult.setRet(0);
                    getHolderPwdResult.setPszPin(HexUtil.hexStringToByte("313233343536"));
                    return getHolderPwdResult;
                }

                @Override
                public int onReferProc() throws RemoteException {
                    LogUtils.i( "Callback => onReferProc \n");
                    return 0;
                }

                @Override
                public GetAllAmtResult onGetAllAmt(byte[] panData, byte[] authAmt) throws RemoteException {
                    LogUtils.i( "Callback => onGetAllAmt");
                    LogUtils.i( "panData: " + (panData == null ? "null" : HexUtil.bcd2str(panData))
                            + "\nauthAmt: " + (authAmt == null ? "null" : HexUtil.bcd2str(authAmt)) + "\n");
                    GetAllAmtResult getAllAmtResult = new GetAllAmtResult();
                    getAllAmtResult.setBcdTotalAmt(HexUtil.hexStringToByte("000000001000"));
                    return getAllAmtResult;
                }

                @Override
                public int onGetSign() throws RemoteException {
                    LogUtils.i( "Callback => onGetSign" + "\n");
                    return 0;
                }

                @Override
                public int onWaitSelectApp(int tryCnt, byte[] appNameList, int num) throws RemoteException {
                    LogUtils.i( "Callback => onWaitSelectApp");
                    LogUtils.i( "tryCnt: " + tryCnt + "\nnum" + num);
                    LogUtils.i( "\nappNameList: " + (appNameList == null ? "null" : HexUtil.bcd2str(appNameList)) + "\n");
                    return 0;
                }

                @Override
                public byte[] onIoCtrl(byte ioName) throws RemoteException {
                    LogUtils.i( "Callback => onIoCtrl");
                    LogUtils.i( "ioName: " + ioName + "\n");
                    return new byte[0];
                }

                @Override
                public void onVfyPinOK() throws RemoteException {
                    LogUtils.i( "Callback => onVfyPinOK \n");
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mainModel = new MainModelImpl(new MainListener() {
            @Override
            public void setViewLog(String log) {
                LogUtils.i( "setViewLog: " + log);
            }
        }, mPinPadBinderService,mISystemEncDecBinderService);

    }

    /**
     * 重置SP
     */
    private void resetSP() {
        try {
            int ret = mSystemBinderService.resetSP();
            LogUtils.i( "重置SP结果:\t" + (ret == 0 ? "成功" : "失败"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    /**
     * 写主密钥               DAL_POSWriteKey
     *
     * @param itemName
     */
    private void setPOSWriteKey() {
        try {
            KeyInfoParam keyInfoParam = new KeyInfoParam();
            keyInfoParam.setDstKeyIdx((short) 2);
            keyInfoParam.setDstKeyLen(16);
            keyInfoParam.setDstKeyType((byte) 0x10);
            byte[] mainKey = HexUtil.hexStringToByte("01FDCF78B91BD0563EC2889490750FDA");
            keyInfoParam.setDstKeyValue(mainKey); //16字节hex，主密钥明文
            keyInfoParam.setSrcKeyIdx((short) 0);
            keyInfoParam.setSrcKeyType((byte) 0x01);
            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            //kcvInfoParam.setCheckBuf(//这个不用管);
            kcvInfoParam.setCheckMode(0);
            int ret = mPinPadBinderService.posMasterKey(keyInfoParam, kcvInfoParam);
            Log.d(TAG, "写入主秘钥结果:" + ret);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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
        //设置字体下划线
        //spanString.setSpan(new UnderlineSpan(), 0, spanString.length(), 0);
        //设置字体为斜体
        //spanString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spanString.length(), 0);

        return spanString;
    }

    private void setEditText(String str) {
        SpannableString spannableString = getSpannableString(str);
        if (!StringUtils.isEmpty(spannableString)) {
            etAmount.setText(spannableString);
        }
    }

    @Override
    public void onBackPressed() {
        disDialog();
        if(payPasswordDialog != null && payPasswordDialog.isShowing()){
            payPasswordDialog.dismiss();
            payPasswordDialog = null;
        }
        if(messageDialog != null && messageDialog.isShowing()){
            messageDialog.dismiss();
            messageDialog = null;
        }
        super.onBackPressed();
    }
}