package com.hjq.demo.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.MessageEvent;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.adapter.TextImageAdapter;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.BitmapUtil;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.toast.ToastUtils;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.util.DataConversionUtils;
import com.wushaohong.zxing.result.TextResult;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.Call;

import static android.graphics.Bitmap.createBitmap;

public class ShowTradeDetailsActivity extends AppActivity{
    private static final String TAG = "ShowTradeDetailsActivity";

    private IPrinterBinderService mPrinterBinderService;

    //private String tradeItems[] = new String[100];
    private List<TransOrder> transOrders = LitePal.findAll(TransOrder.class);
    private List<String> tradeItems = new ArrayList<>();

    StringBuffer sbMsg = new StringBuffer();

    private Bitmap signatureBmp = null;

    private int printIncHeight = 0;
    private String customerType;

    public static final int MAX_BIT_WIDTH = 300;
    public static final int MAX_BIT_HEIGHT = 200;

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
        }catch (RemoteException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    protected int getLayoutId() {
        return R.layout.activity_trade_details;
    }

    @Override
    protected void initData() {

    }

    private void initOrderData() {
        int i = 0;
        for(TransOrder item : transOrders)
        {
            //String str = "#" + (++i) + "                " + getString(R.string.print_total_sign) + item.getOrder_amount() + "\n" + item.getOrder_date_time();
            String str = "#" + (++i) + "                " + "￥ " + item.getOrder_amount() + "\n"
                    + item.getOrder_date_time();
            tradeItems.add(str);
        }
    }

    @Override
    protected void initView() {
        initOrderData();

        ListView testListView = (ListView) findViewById(R.id.lv_details);
        View emptyView = findViewById(R.id.emptyview);

        if(tradeItems.isEmpty()) {
            testListView.setEmptyView(emptyView);
            return;
        }

        customerType = DataConversionUtils.getCustomerType();

        /*
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_expandable_list_item_1, tradeItems);
         */
        //TextImageAdapter adapter = new TextImageAdapter(this, R.layout.trans_list_item, tradeItems);

        TextImageAdapter adapter = new TextImageAdapter(this, R.layout.trans_list_item, tradeItems, new TextImageAdapter.PrintCallback() {
            @Override
            public void printTest(int position) {
                LogUtils.e("---------printTest-------");
                if (DeviceType.A6.equals(customerType)) {

                }
                else {
                    toast(getString(R.string.deal_print));
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            printOrder(position);
                        }
                    }, 250);
                }
            }
        });

        testListView.setAdapter(adapter);
        testListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Intent intent = new Intent();
                intent.putExtra("OrderPosition", position);
                intent.setClass(ShowTradeDetailsActivity.this, ShowTradeOrderActivity.class);
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
        LogUtils.e( "onStart: 订阅消息");
    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        LogUtils.e( "onStop: 注销订阅消息");
    }

    private void showToast(String str)
    {
        Toast toast = Toast.makeText(ShowTradeDetailsActivity.this, str, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.CENTER, 0, 0);     //930、820验证无效，设备显示仍是靠下，没居中
        toast.show();
    }

    void printOrder(int position)
    {
        TransOrder transOrder = transOrders.get(position);
        String signature = null;
        signature = transOrder.getOrder_signature();
        try {
            Bitmap printBmp;
            if (signature != null)
            {
                signatureBmp = getSignatureBmp(AFUtils.hexString2ByteArray(signature));
                Bitmap textBmp = creatTextBitmap(printMsg(transOrder), 384, 860 + printIncHeight);
                //两张bitmap拼接成一张bitmap
                printBmp = BitmapUtil.addBitmap(textBmp, signatureBmp, true);
            } else {
                printBmp = creatTextBitmap(printMsg(transOrder), 384, 1210 + printIncHeight);
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

    private Bitmap getSignatureBmp(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) return null;
        final Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        //bitmap宽高比为1.5:1，防止bitmap图片变形
        Bitmap imgBmp = BitmapUtil.createScaleBitmap(bitmap, MAX_BIT_WIDTH, MAX_BIT_HEIGHT);
        return imgBmp;
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

    private String printMsg(TransOrder transOrder) {
        LogUtils.e( "printMsg: The transaction is completed and the receipt is printed");
        String method = null;
        if(transOrder.getOrder_method() == 1)
        {
            method = getString(R.string.print_method_swipe);
        }
        if(transOrder.getOrder_method() == 2)
        {
            method = getString(R.string.print_method_insert);
        }
        if(transOrder.getOrder_method() == 4)
        {
            method = getString(R.string.print_method_tap);
        }
        if(transOrder.getOrder_method() == 8)
        {
            method = getString(R.string.print_method_manual);
        }

        sbMsg.setLength(0);
        sbMsg.append("        Dutch Bangla Bank PLC\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_date_time) + "\t\t\t" + transOrder.getOrder_date_time() + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append("                          SALE");
        sbMsg.append("\n\n");
        sbMsg.append(getString(R.string.print_merchant_name) + "\n                              DBB PLC TEST POS\n");
        sbMsg.append(getString(R.string.print_terminal_no) + "\n                                              " + 48290000 + "\n");
        sbMsg.append(getString(R.string.print_operator_no) + "\n                                              00000001\n");
        sbMsg.append(getString(R.string.print_card_no) + "\n" + printFormat(transOrder.getOrder_cardno()) + "\n");
        sbMsg.append(getString(R.string.print_txn_type) + "\n" + printFormat(getString(R.string.print_txn_type_info)) + "\n");
        sbMsg.append(getString(R.string.print_method) + "\n" + printFormat(method)+ "\n");
        sbMsg.append(getString(R.string.print_exp) + "\n" + printFormat(transOrder.getOrder_expdate()) + "\n");
        sbMsg.append(getString(R.string.print_ref_no) + "\n                                     " + transOrder.getOrder_id() + "\n");
        if(!TextUtils.isEmpty(transOrder.getOrder_apprCode()))
        {
            printIncHeight += 30;
            sbMsg.append(getString(R.string.print_appr_code) + "\n                                                  " + transOrder.getOrder_apprCode() + "\n");
        }
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_amount) + transOrder.getOrder_amount() + "\n");
        sbMsg.append("------------------------------------------------------\n\n");
        sbMsg.append(getString(R.string.sing_tip) + "\n\n");
        sbMsg.append(getString(R.string.sign) + "\n\n");

        LogUtils.i( "printMsg: " + sbMsg.toString());
        return sbMsg.toString();
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


    // 订阅事件
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        LogUtils.e( "onMessageEvent: " + event.toString());
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
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                    }
                })
                .show();
    }
}
