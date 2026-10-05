package com.hjq.demo.model;

import android.os.Handler;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.hjq.demo.ans8583.Easy8583Ans;
import com.hjq.demo.ans8583.My8583Ans;
import com.hjq.demo.entity.SalePackageEntity;
import com.hjq.demo.ilistener.MainListener;
import com.hjq.demo.inter.AFSwipeControllerListener;
import com.hjq.demo.retrofit.ApiManager;
import com.hjq.demo.retrofit.DataBean;
import com.hjq.demo.retrofit.RetrofitInstance;
import com.hjq.demo.ui.activity.AmountActivity;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.MyUtil;
import com.hjq.demo.utils.SPTransUtil;
import com.hjq.demo.utils.TLVModel;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.PosCalcDesResult;
import com.szanfu.sdk.api.system.EncDecResult;
import com.szanfu.sdk.api.system.ISystemEncDecBinderService;
import com.szanfu.sdk.api.util.HexUtil;
import com.xuhao.didi.core.iocore.interfaces.IPulseSendable;
import com.xuhao.didi.core.iocore.interfaces.ISendable;
import com.xuhao.didi.core.pojo.OriginalData;
import com.xuhao.didi.core.protocol.IReaderProtocol;
import com.xuhao.didi.socket.client.sdk.OkSocket;
import com.xuhao.didi.socket.client.sdk.client.ConnectionInfo;
import com.xuhao.didi.socket.client.sdk.client.OkSocketOptions;
import com.xuhao.didi.socket.client.sdk.client.action.SocketActionAdapter;
import com.xuhao.didi.socket.client.sdk.client.connection.IConnectionManager;

import org.apache.commons.lang3.StringUtils;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;

import io.reactivex.Observable;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

import static java.lang.System.arraycopy;

public class MainModelImpl implements MainModel {
    private static final String TAG = "MainModelImpl";
    private SPTransUtil spTransUtil;
    private Observable<String> testObservable;
    private MainListener listener;
    Retrofit retrofit;
    ApiManager apiService;
    final My8583Ans myans = new My8583Ans();
    private ConnectionInfo info;
    private IConnectionManager mManager;
    StringBuffer strmsg = new StringBuffer();
    private static IPinPadBinderService myPinPadService;
    private ISystemEncDecBinderService mISystemEncDecBinderService;
    public AFSwipeControllerListener mSwipeControllerListener = null;
    private boolean responseStatus = false;
    /**
     * 消息类型：0810区分
     * 0：签到
     * 1：终端参数更新
     * 2：公钥下载
     * 3：主密钥下载
     */
    private int responseType = -1;

    @Override
    public void initTrans(AFSwipeControllerListener afSwipeControllerListener) {
        mSwipeControllerListener = afSwipeControllerListener;
    }

    public MainModelImpl() {
    }

    public MainModelImpl(MainListener listener, IPinPadBinderService pinpadService, ISystemEncDecBinderService encDecBinderService) {
        this.listener = listener;
        retrofit = RetrofitInstance.getInstance().getRetrofit();
        apiService = retrofit.create(ApiManager.class);
        myPinPadService = pinpadService;
        mISystemEncDecBinderService = encDecBinderService;
        spTransUtil = new SPTransUtil();
    }

    public static IPinPadBinderService getPinPadSingleton() {
        return myPinPadService;
    }


    private byte[] send = null;

    @Override
    public void AFPosSignIn() {
//        myans.testDevicesSin();
        mSwipeControllerListener.onSendData(AFConstant.SEND_PKG_MSG_TYPE_SIGN_IN);
        //签到组包
        strmsg.setLength(0);
        My8583Ans.setMainKey("B9257F2A4C317675709EEF89D9D54A89");
        myans.frame8583QD(myans.fieldsSend, myans.pack);
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        String strSendData = AFUtils.showResult16Str(send);
        SalePackageEntity salePackageEntity = saleFormatPackage(strSendData);
        salePackageEntity.setAttachHead_8583_pkg_len(String.format("%04x", (salePackageEntity.getPkgData().length() / 2)));
        byte[] signInSend = AFUtils.hexString2ByteArray(salePackageEntity.getSignInPackInfo());
        Log.i(TAG, "AFPosSignIn Send Data : " + AFUtils.showResult16Str(signInSend));
        //发送请求数据
        sendPkg(signInSend, AFConstant.SEND_PKG_MSG_TYPE_SIGN_IN);
        responseType = 0;
    }

    /**
     * 更新终端公钥
     */
    @Override
    public void AFUpdatePubKey() {
        mSwipeControllerListener.onSendData(AFConstant.SEND_PKG_MSG_TYPE_UPDATE_PUB_KEY);
        strmsg.setLength(0);
        myans.updatePubKey_8583_send(myans.fieldsSend, myans.pack);
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        String strSendData = AFUtils.showResult16Str(send);
        SalePackageEntity salePackageEntity = saleFormatPackage(strSendData);
        salePackageEntity.setAttachHead_8583_pkg_len(String.format("%04x", (salePackageEntity.getPkgData().length() / 2)));
        byte[] signInSend = AFUtils.hexString2ByteArray(salePackageEntity.getSignInPackInfo());
        Log.i(TAG, "AFUpdatePubKey Send Data : " + AFUtils.showResult16Str(signInSend));
        //发送请求数据
        sendPkg(signInSend, AFConstant.SEND_PKG_MSG_TYPE_UPDATE_PUB_KEY);
        responseType = 2;
    }

    /**
     * 更新主密钥
     */
    @Override
    public void AFUpdateMasterKey(String data) {
        mSwipeControllerListener.onSendData(AFConstant.SEND_PKG_MSG_TYPE_UPDATE_MASTER_KEY);
        strmsg.setLength(0);
        myans.updateMasterKey_8583_send(myans.fieldsSend, myans.pack, data);
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        String strSendData = AFUtils.showResult16Str(send);
        SalePackageEntity salePackageEntity = saleFormatPackage(strSendData);
        salePackageEntity.setAttachHead_8583_pkg_len(String.format("%04x", (salePackageEntity.getPkgData().length() / 2)));
        byte[] signInSend = AFUtils.hexString2ByteArray(salePackageEntity.getSignInPackInfo());
        Log.i(TAG, "AFUpdateMasterKey Send Data : " + AFUtils.showResult16Str(signInSend));
        //发送请求数据
        sendPkg(signInSend, AFConstant.SEND_PKG_MSG_TYPE_UPDATE_MASTER_KEY);
        responseType = 3;
    }

    Handler handler = new Handler();

    @Override
    public void AFPosSale(SaleDataModel saleDataModel, int cardType) {
        //消费
        Log.i(TAG, "AFPosSale: " + saleDataModel.toString());
        mSwipeControllerListener.onSendData(AFConstant.SEND_PKG_MSG_TYPE_SALE);
        strmsg.setLength(0);
        myans.frame8583Sale(myans.fieldsSend, myans.pack, saleDataModel, cardType);
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        String strSendData = AFUtils.showResult16Str(send);
        Log.i(TAG, "AFPosSale Send Data : " + strSendData);
        SalePackageEntity salePackageEntity = saleFormatPackage(strSendData);
        //加密报文
        Map<String, Object> pkgEncryptResMap = packageEncrypt(salePackageEntity.getPkgData());
        String pkgEncryptRes = pkgEncryptResMap.get(ENCRYPT_DATA_RESULT).toString();
        //加密报文成功
        if (!StringUtils.isEmpty(pkgEncryptRes)) {
            //重新赋值密文报文
            salePackageEntity.setPkgData(pkgEncryptRes);
            int encrypt_data_missing = Integer.parseInt(pkgEncryptResMap.get(ENCRYPT_DATA_MISSING).toString());
            Log.i(TAG, "补零位数:\t" + encrypt_data_missing);
            int currPkgLen = Integer.parseInt(salePackageEntity.getPkgLen(), 16);
            Log.i(TAG, "原数据长度:\t" + currPkgLen);
//            salePackageEntity.setPkgLen(String.format("%04x", (encrypt_data_missing / 2 + currPkgLen)));
            salePackageEntity.setPkgLen(String.format("%04x", (encrypt_data_missing / 2 + currPkgLen)));
            Log.i(TAG, "最后的长度:\t" + salePackageEntity.getPkgLen());
            StringBuffer sbSendSale = new StringBuffer();
            sbSendSale.setLength(0);
            sbSendSale.append(salePackageEntity.getPkgLen());
            sbSendSale.append(salePackageEntity.getTpdu());
            //消费报文特殊处理
            if (!salePackageEntity.getMsgType().equals("0800")) {
                salePackageEntity.setPkgHead("36303230413031313030");
            }
            sbSendSale.append(salePackageEntity.getPkgHead());

            //附加报文头处理----> 组成12字节终端号+2字节原始8583报文长度（getPkgData长度/2）
            salePackageEntity.setAttachHead_8583_pkg_len(String.format("%04x", ((pkgEncryptRes.length() - encrypt_data_missing) / 2)));
            sbSendSale.append(salePackageEntity.getAttachHead_terminal_number());
            sbSendSale.append(salePackageEntity.getAttachHead_8583_pkg_len());
            sbSendSale.append(salePackageEntity.getAttachHead_reserved_domain());
//            sbSendSale.append(salePackageEntity.getAttachPkgHead());
            sbSendSale.append(salePackageEntity.getPkgData());
            Log.i(TAG, salePackageEntity.toString());
            byte[] sendSale = AFUtils.hexString2ByteArray(sbSendSale.toString());
            Log.i(TAG, "向服务器发送【消费】报文:\t" + AFUtils.showResult16Str(sendSale));
            sendPkg(sendSale, AFConstant.SEND_PKG_MSG_TYPE_SALE);
        } else {
            Log.i(TAG, "加密报文接口返回异常~~~~~");
        }
    }

    //检查服务器是否连接
    private void checkConnect(byte[] sendSale) {
        if (!mManager.isConnect()) {
            Log.i(TAG, "************当前与服务器【已连接】************");
            /*OkSocket.open(info).send(new ISendable() {
                @Override
                public byte[] parse() {
                    return sendSale;
                }
            });*/
        } else {
            mManager.connect();//调用通道进行连接
            Log.i(TAG, "************当前与服务器未连接************");
           /* handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mManager.isConnect()) {
                        OkSocket.open(info).send(new ISendable() {
                            @Override
                            public byte[] parse() {
                                return sendSale;
                            }
                        });
                    }
                }
            }, 2000);//3秒后执行Runnable中的run方法*/
        }
    }


    SocketActionAdapter socketActionAdapter = new SocketActionAdapter() {
        @Override
        public void onSocketIOThreadStart(String action) {
            super.onSocketIOThreadStart(action);
        }

        @Override
        public void onSocketIOThreadShutdown(String action, Exception e) {
            super.onSocketIOThreadShutdown(action, e);
        }

        @Override
        public void onSocketDisconnection(ConnectionInfo info, String action, Exception e) {
            super.onSocketDisconnection(info, action, e);
            //断开连接
            Log.d(TAG, "断开服务器连接");
            mManager.disconnect();
            mManager.unRegisterReceiver(socketActionAdapter);
        }

        @Override
        public void onSocketConnectionSuccess(ConnectionInfo info, String action) {
            super.onSocketConnectionSuccess(info, action);
            Log.d(TAG, "客户端连接服务器成功");
        }

        @Override
        public void onSocketConnectionFailed(ConnectionInfo info, String action, Exception e) {
            super.onSocketConnectionFailed(info, action, e);
            Log.d(TAG, "客户端连接服务器失败");
            mSwipeControllerListener.onConnectServerFail();
        }

        @Override
        public void onSocketReadResponse(ConnectionInfo info, String action, OriginalData data) {
            // String str = new String(data.getBodyBytes(), Charset.forName("utf-8"));
            //解析
            strmsg.append("接收响应成功!\n");
            strmsg.append("<-begin ans:\n");
            responsePkgAnalyze(data);
            //清除响应监听事件
            handler.removeCallbacks(myRunnable);
            //断开服务器连接
            mManager.disconnect();
            super.onSocketReadResponse(info, action, data);
        }

        @Override
        public void onSocketWriteResponse(ConnectionInfo info, String action, ISendable data) {
            Log.d(TAG, "客户端发送数据回调:\n" + AFUtils.showResult16Str(data.parse()));
            super.onSocketWriteResponse(info, action, data);
        }

        @Override
        public void onPulseSend(ConnectionInfo info, IPulseSendable data) {
            Log.d(TAG, "客户端发送心跳包");
            super.onPulseSend(info, data);
        }
    };

    @Override
    public void connectSocket() {
        //连接参数设置(IP,端口号)
        //测试环境
        //info = new ConnectionInfo("111.205.18.100", 18555);
        switch (responseType) {
            case 1:
            case 2:
                Log.i(TAG, "更新设备参数相关.....连接服务器->>>> ");
                //更新设备参数->请求的IP和端口与其他交易不同..115.182.249.102:6120
                info = new ConnectionInfo("115.182.249.102", 6120);
                break;
            default:
                Log.i(TAG, "正式环境.....连接服务器->>>>");
                //正式环境 210.74.1.237:20009
                info = new ConnectionInfo("210.74.1.237", 20009);
                break;
        }
        //调用OkSocket,开启这次连接的通道,拿到通道Manager
        mManager = OkSocket.open(info);
        //注册Socket行为监听器
        mManager.registerReceiver(socketActionAdapter);
        //获得当前连接通道的参配对象
        OkSocketOptions options = mManager.getOption();
        //设置自定义解析头
        OkSocketOptions.Builder okOptionsBuilder = new OkSocketOptions.Builder(options);
        //AbsReconnectionManager
        okOptionsBuilder.setReaderProtocol(new IReaderProtocol() {
            @Override
            public int getHeaderLength() {
                return 2;
            }

            @Override
            public int getBodyLength(byte[] header, ByteOrder byteOrder) {

                Integer dataLen = Integer.parseInt(AFUtils.showResult16Str(header), 16);

                Log.i(TAG, "getBodyLength: " + dataLen);
                return dataLen;
            }
        });
        mManager.option(okOptionsBuilder.build());
        if (!mManager.isConnect()) {
            mManager.connect();//调用通道进行连接
        }
    }


    private void readLog() {
        boolean enable = false;
        try {
            int ret = AmountActivity.getSystemSingleton().setLightStatus(enable, enable, enable, enable);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    //报文加密结果
    public static final String ENCRYPT_DATA_RESULT = "encryptRet";
    //数据不够8整数需要补0位数
    public static final String ENCRYPT_DATA_MISSING = "missingLen";

    /**
     * 报文加密处理
     *
     * @param pkgData 报文数据
     */
    private Map<String, Object> packageEncrypt(String pkgData) {
        Log.i(TAG, "报文加密: " + pkgData);
        Map<String, Object> pkgEncryptMap = new ArrayMap<>();
        pkgEncryptMap.clear();
        // readLog();
        int pkgDataLen = pkgData.length();
        Log.i(TAG, "pkgDataLen: " + pkgDataLen);
        int missingLen = pkgDataLen % 16;
        if (missingLen != 0) {
            missingLen = 16 - missingLen;
        }
        Log.i(TAG, "需要补0的位数:" + missingLen);
        pkgEncryptMap.put(ENCRYPT_DATA_MISSING, missingLen);
        pkgData = StringUtils.rightPad(pkgData, pkgDataLen + missingLen, "0");
        Log.i(TAG, "pkgData:" + pkgData);
        try {
            int keyIdx = 0x02;
            int keyType = 0x06;
            int algIde = 0x02;
            int ecbCbcMode = 0x00;
//            byte[] dataIn = HexUtil.hexStringToByte("01020304050607080900010203040506");
            byte[] dataIn = HexUtil.hexStringToByte(pkgData);
            Log.i(TAG, "待加密数据dataIn:\t" + AFUtils.showResult0xStr(dataIn));
            int mode = 0x01;
            byte[] iv = null;
            PosCalcDesResult posCalcDesResult = myPinPadService.posCalcDes(keyIdx, keyType, algIde, ecbCbcMode, dataIn, mode, iv);
           /* Log.i(TAG, "keyIdx: " + keyIdx +
                    "\nkeyType: " + keyType +
                    "\nalgIde: " + algIde +
                    "\necbCbcMode: " + ecbCbcMode +
                    "\ndataIn: " + (dataIn == null ? "null" : HexUtil.bcd2str(dataIn)) +
                    "\nmode: " + mode +
                    "\ndataIn: " + (iv == null ? "null" : HexUtil.bcd2str(iv))
                    + "\nret: " + (posCalcDesResult == null ? "null" : posCalcDesResult.getRet())
                    + "\ndataOut: " + (posCalcDesResult == null ? "null" :
                    (posCalcDesResult.getDataOut() == null ? "null" : HexUtil.bcd2str(posCalcDesResult.getDataOut()))) + "\n");
*/
            if (posCalcDesResult.getRet() == 0) {
                String encryptRet = HexUtil.bcd2str(posCalcDesResult.getDataOut());
                pkgEncryptMap.put(ENCRYPT_DATA_RESULT, encryptRet);
                //解密验证数据
                packageDecrypt(encryptRet);
            } else {
                pkgEncryptMap.put(ENCRYPT_DATA_RESULT, null);
                Log.i(TAG, "报文加密失败: " + pkgData);
            }
            //readLog();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return pkgEncryptMap;
    }


    //报文解密结果
    public static final String DECRYPT_DATA_RESULT = "decryptRet";

    /**
     * 报文解密处理
     *
     * @param pkgData 报文数据
     */
    private Map<String, Object> packageDecrypt(String pkgData) {
        Log.i(TAG, "报文解密: " + pkgData);
        Map<String, Object> pkgDecryptMap = new ArrayMap<>();
        pkgDecryptMap.clear();
//        readLog();
        int pkgDataLen = pkgData.length();
        Log.i(TAG, "pkgDataLen: " + pkgDataLen);
        int missingLen = pkgDataLen % 16;
        if (missingLen != 0) {
            missingLen = 16 - missingLen;
        }
        Log.i(TAG, "需要补0的位数:" + missingLen);
        pkgData = StringUtils.rightPad(pkgData, pkgDataLen + missingLen, "0");
        Log.i(TAG, "pkgData:" + pkgData);
        try {
            int keyIdx = 0x02;
            int keyType = 0x06;
            int algIde = 0x2;
            int ecbCbcMode = 0x00;
//            byte[] dataIn = HexUtil.hexStringToByte("01020304050607080900010203040506");
            byte[] dataIn = HexUtil.hexStringToByte(pkgData);
            Log.i(TAG, "待解密数据dataIn:\t" + AFUtils.showResult0xStr(dataIn));
            //0 - 解密 1 - 加密
            int mode = 0x00;
            byte[] iv = null;
            PosCalcDesResult posCalcDesResult = myPinPadService.posCalcDes(keyIdx, keyType, algIde, ecbCbcMode, dataIn, mode, iv);
            if (posCalcDesResult.getRet() == 0) {
                pkgDecryptMap.put(DECRYPT_DATA_RESULT, HexUtil.bcd2str(posCalcDesResult.getDataOut()));
                Log.i(TAG, "解密成功:\t" + HexUtil.bcd2str(posCalcDesResult.getDataOut()));
            } else {
                pkgDecryptMap.put(DECRYPT_DATA_RESULT, null);
            }
//            readLog();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return pkgDecryptMap;
    }

    @Override
    public void postBatchData() {
        strmsg.setLength(0);
        //My8583Ans.setMainKey("B9257F2A4C317675709EEF89D9D54A89");
        myans.frame8583Batch(myans.fieldsSend, myans.pack);
        byte[] send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        String qdstr = My8583Ans.bytesToHexString(send);
        strmsg.append("->send:");
        strmsg.append(qdstr);
        Log.d("Send:", strmsg.toString());
        DataBean data = new DataBean(qdstr);
        Call<byte[]> call = apiService.postData(data.bodyhex);
        call.enqueue(new Callback<byte[]>() {
            @Override
            public void onResponse(Call<byte[]> call, Response<byte[]> response) {
                if (response.isSuccessful()) {
                    // do SomeThing
                    Log.d("AA", "成功");
                    Log.d("AA", response.raw().toString());
                    String strrecv = MyUtil.bytesToHexString(response.body());
                    Log.d("respondAA:", strrecv);
                    //解析
                    //System.out.println("开始解析...");
                    strmsg.append("接收响应成功!\n");
                    strmsg.append("<-begin ans:\n");
                    byte[] recv = My8583Ans.hexStringToBytes(strrecv);
                    int ret = myans.ans8583Batch(recv, recv.length);
                    strmsg.append(myans.getFields(myans.fieldsRecv));
                    if (ret == 0) {
                        //打印出解析成功的各个域
                        Log.d("respondAA OK:", "批结成功!");
                        strmsg.append("\n批结成功!\n");
                        //System.out.println("签到成功!");
                        //System.out.println(myans.getFields(myans.fieldsRecv));
                        Log.d("respondAA OK:", myans.getFields(myans.fieldsRecv));
                    }
                } else {
                    //直接操作UI 返回的respone被直接解析成你指定的modle
                    Log.d("AA", "失败");
                    strmsg.append("\n失败!\n");

                }
                listener.setViewLog(strmsg.toString());
                //RxBus.getInstance().post(new EventMsg<String>(Constant.LOG, strmsg.toString()));
            }

            @Override
            public void onFailure(Call<byte[]> call, Throwable t) {
                // do onFailure代码
                Log.d("AA", "失败", t);
                //RxBus.getInstance().post(new EventMsg<String>(Constant.LOG, strmsg.toString()));
                listener.setViewLog(strmsg.toString());
            }
        });
    }


    private void sendPkg(byte[] sendData, int msgType) {
        Log.i(TAG, "sendPkg: ->msgType:" + msgType);
        responseStatus = false;
        //mSwipeControllerListener.onSendData(msgType);
        handler.post(new Runnable() {
            @Override
            public void run() {
                //与服务器建立连接
                connectSocket();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeControllerListener.onDataReceiving();
                        if (mManager.isConnect()) {
                            sendDataListener();
                            OkSocket.open(info).send(new ISendable() {
                                @Override
                                public byte[] parse() {
                                    return sendData;
                                }
                            });
                        } else {
                            Log.i(TAG, "run: 服务器还未连接成功");
                            mSwipeControllerListener.onConnectServerFail();
                        }
                    }
                }, 2000);   //5秒
            }
        });
    }


    Runnable myRunnable = new Runnable() {
        @Override
        public void run() {
            Log.i(TAG, "监听客户发送请求结果：\t" + (responseStatus ? "成功" : "失败"));
            if (!responseStatus) {
                mSwipeControllerListener.onServerUnresponsive();
            }
        }
    };

    /**
     * 监听客户发送请求是否成功
     */
    private void sendDataListener() {
        //发送消息15S后不返回，默认通讯失败
        handler.postDelayed(myRunnable, 15 * 1000);
    }

    /**
     * 格式化消费报文
     *
     * @param salePackage 需要处理的报文
     */
    private SalePackageEntity saleFormatPackage(String salePkg) {
        //报文处理 报文长度[2 字节]+TPDU[5 字节]+ 报文头[10 字节]+附加报文头[32 字节]+ 报文内容[不定长]
        SalePackageEntity saleEntity = new SalePackageEntity();
        saleEntity.setPkgLen(salePkg.substring(0, 4));
        saleEntity.setTpdu(salePkg.substring(4, 14));
        saleEntity.setPkgHead(salePkg.substring(14, 34));
        saleEntity.setAttachPkgHead(salePkg.substring(34, 98));
        //附加报文头处理
        String attachPkgHead = saleEntity.getAttachPkgHead();
        saleEntity.setAttachHead_terminal_number(attachPkgHead.substring(0, 24));
        saleEntity.setAttachHead_8583_pkg_len(attachPkgHead.substring(24, 28));
        saleEntity.setAttachHead_reserved_domain(attachPkgHead.substring(28, attachPkgHead.length()));
        saleEntity.setPkgData(salePkg.substring(98, salePkg.length()));
        saleEntity.setMsgType(saleEntity.getPkgData().substring(0, 4));
        saleEntity.setBitMap(saleEntity.getPkgData().substring(4, 20));
        Log.i(TAG, "报文组装完成:\n" + saleEntity.toString());
        return saleEntity;
    }

    /**
     * 更新设备参数相关，特殊解析报文
     *
     * @param salePkg 响应报文内容
     * @return 格式化后的数据
     */
    private SalePackageEntity updateDevParamFormatPackage(String salePkg) {
        //报文处理 报文长度[2 字节]+TPDU[5 字节]+ 报文头[10 字节]+附加报文头[32 字节]+ 报文内容[不定长]
        SalePackageEntity saleEntity = new SalePackageEntity();
        saleEntity.setPkgLen(salePkg.substring(0, 4));
        saleEntity.setTpdu(salePkg.substring(4, 14));
        saleEntity.setPkgHead(salePkg.substring(14, 34));
//        saleEntity.setAttachPkgHead(salePkg.substring(34, 98));
        //附加报文头处理
       /* String attachPkgHead = saleEntity.getAttachPkgHead();
        saleEntity.setAttachHead_terminal_number(attachPkgHead.substring(0, 24));
        saleEntity.setAttachHead_8583_pkg_len(attachPkgHead.substring(24, 28));
        saleEntity.setAttachHead_reserved_domain(attachPkgHead.substring(28, attachPkgHead.length()));*/
        saleEntity.setPkgData(salePkg.substring(34, salePkg.length()));
        saleEntity.setMsgType(saleEntity.getPkgData().substring(0, 4));
        saleEntity.setBitMap(saleEntity.getPkgData().substring(4, 20));
        Log.i(TAG, "updateDevParamFormatPackage 报文组装完成:\n" + saleEntity.toString());
        return saleEntity;
    }

    private boolean pkgHeadAnalysis(String headData) {
        //36303230413031313030 ->6020A01100
        String fmtHead = AFUtils.hexGBK2String(headData);
        Log.i(TAG, "pkgHeadAnalysis ->>>>fmtHead:\t" + fmtHead);
        //是否加密tcp的8583报文[1字节] :0:不加密 1:加密 2:特殊加密
        String pkgIsEncrypt = fmtHead.substring(7, 8);
        Log.i(TAG, pkgIsEncrypt.equals("0") ? "报文未加密" : "报文加密");
        return !"0".equals(pkgIsEncrypt);

    }

    private void responsePkgAnalyze(OriginalData data) {
        Log.i(TAG, "******【responsePkgAnalyze】******");
        responseStatus = true;
        String strResponseData = AFUtils.showResult16Str(data.getBodyBytes());
        Log.d(TAG, "接收响服务器响应成功:\t" + strResponseData);

        byte[] dataLen = data.getHeadBytes();
        byte[] byteResponseData = Easy8583Ans.hexStringToBytes(strResponseData);
        byte[] responseAnalyze = new byte[byteResponseData.length + dataLen.length];
        arraycopy(dataLen, 0, responseAnalyze, 0, dataLen.length);//数据
        arraycopy(byteResponseData, 0, responseAnalyze, dataLen.length, byteResponseData.length);//数据
        Log.i(TAG, "响应数据组包:\t" + AFUtils.showResult16Str(responseAnalyze));

        //组装响应数据
        StringBuffer sbResponseData = new StringBuffer();
        sbResponseData.append(AFUtils.showResult16Str(dataLen));
        sbResponseData.append(AFUtils.showResult16Str(byteResponseData));

        SalePackageEntity fmtPackEntity = null;
        //数据解析
        switch (responseType) {
            case 1:
                fmtPackEntity = updateDevParamFormatPackage(sbResponseData.toString());
                break;
            default:
                fmtPackEntity = saleFormatPackage(sbResponseData.toString());
                break;
        }

        sbResponseData.setLength(0);
        boolean pkgIsEncrypt = pkgHeadAnalysis(fmtPackEntity.getPkgHead());
        //检查报文是否加密
        if (pkgIsEncrypt) {
            Map<String, Object> decryRes = packageDecrypt(fmtPackEntity.getPkgData());
            String decryData = decryRes.get(DECRYPT_DATA_RESULT).toString();
            fmtPackEntity.setPkgData(decryData);
            //解密之后格式化报文
            fmtPackEntity = saleFormatPackage(fmtPackEntity.getCurrentPackInfo());
        }
        //判断消息类型，区分签到/消费
        if ("0810".equals(fmtPackEntity.getMsgType())) {
           /*
            0：签到
            1：终端参数更新
            2：公钥下载
            3：主密钥下载*/
            switch (responseType) {
                case 0:
                    Log.i(TAG, "设备【签到】->服务器响应解析...");
                    int signInResult = myans.ans8583QD(responseAnalyze, responseAnalyze.length);
                    mSwipeControllerListener.onDeviceSignIn(signInResult == 0);
                    Log.i(TAG, "签到结果:\t" + signInResult);
                    break;
                case 1:
                    Log.i(TAG, "终端【参数】更新->服务器响应解析...");
                    int updateDevParamResult = myans.updateDevParam_8583_recv(responseAnalyze, responseAnalyze.length);
                    mSwipeControllerListener.updateDevParam(1, updateDevParamResult == 0);
                    Log.i(TAG, "updateDevParamResult:\t" + updateDevParamResult);
                    break;
                case 2:
                    Log.i(TAG, "终端【公钥】更新->服务器响应解析...");
                    Map<String, String> updatePubKeyResult = myans.updatePubKey_8583_recv(responseAnalyze, responseAnalyze.length);
                    String tempUpdateKeyRes = updatePubKeyResult.get(AFConstant.TRADE_MSG_TAG);
                    if (AFConstant.TRADE_VALUE_SUCCESS.equals(tempUpdateKeyRes)) {
                        String tempPubKey = updatePubKeyResult.get(AFConstant.TRANS_UPDATE_BASE64_PUB_KEY);
                        Log.i(TAG, "解析公钥成功: " + tempPubKey);

                        String tempHead = tempPubKey.substring(0, 18);
                        String tempBody = tempPubKey.substring(18, 18 + 512);
                        String tempBottom = tempPubKey.substring(18 + 512);

                        Log.i(TAG, "tempHead: " + tempHead);
                        Log.i(TAG, "tempBody: " + tempBody);
                        Log.i(TAG, "tempBottom: " + tempBottom);
                        ArrayList<TLVModel> tList = AFUtils.praseTLVStr(AFUtils.hexString2ByteArray(tempBottom));
                        Map<String, String> tm = AFUtils.list2Map(tList, 2);
                        String tempFmtBottom = StringUtils.leftPad(tm.get("02"), 8, "0");
                        Log.i(TAG, "tempFmtBottom: " + tempFmtBottom);
                        String random = AFUtils.getRandom();
                        Log.i(TAG, "random: " + random);
                        byte[] randomByte = AFUtils.StringToByteArray(random);
                        Log.i(TAG, "randomByte: " + AFUtils.showResult16Str(randomByte));

                        String tag02Data = AFUtils.getTag02Data();
                        boolean calcDataResult = dataCalcRsa(tempBody, tempFmtBottom, tag02Data);
                        mSwipeControllerListener.updateDevParam(2, calcDataResult);
                    } else {
                        Log.i(TAG, "异常: " + tempUpdateKeyRes);
                        mSwipeControllerListener.updateDevParam(2, false);
                    }
                    break;
                case 3:
                    Log.i(TAG, "终端【主密钥】更新->服务器响应解析...");
                    int updateMastKeyResult = myans.updateMasterKey_8583_recv(responseAnalyze, responseAnalyze.length);
                    mSwipeControllerListener.updateDevParam(3, updateMastKeyResult == 0);
                    Log.i(TAG, "updateMastKeyResult:\t" + updateMastKeyResult);
                    break;
            }
        } else if ("0210".equals(fmtPackEntity.getMsgType())) {
            Log.i(TAG, "消费报文解析");
            String currentInfo = fmtPackEntity.getCurrentPackInfo();
            byte[] saleData = AFUtils.hexString2ByteArray(currentInfo);
            Log.i(TAG, "消费报文解密后数据:\t" + AFUtils.showResult16Str(saleData));
            Map<String, String> ret = myans.ansframe8583Sale(saleData, saleData.length);
            mSwipeControllerListener.onTransactionResult(ret);
        } else if ("9310".equals(fmtPackEntity.getMsgType())) {
            Log.i(TAG, "银联报文解析");
            String currentInfo = fmtPackEntity.getCurrentPackInfo();
            byte[] saleData = AFUtils.hexString2ByteArray(currentInfo);
            Log.i(TAG, "银联报文解密后数据:\t" + AFUtils.showResult16Str(saleData));
            Map<String, String> ret = myans.ansframe8583Qrcode(saleData, saleData.length, 1);
            mSwipeControllerListener.onQRCodePaymentResult(ret, 1);
        } else if ("9210".equals(fmtPackEntity.getMsgType())) {
            Log.i(TAG, "支付宝报文解析");
            String currentInfo = fmtPackEntity.getCurrentPackInfo();
            byte[] saleData = AFUtils.hexString2ByteArray(currentInfo);
            Log.i(TAG, "支付宝报文解密后数据:\t" + AFUtils.showResult16Str(saleData));
            Map<String, String> ret = myans.ansframe8583Qrcode(saleData, saleData.length, 3);
           /* Iterator<Map.Entry<String, String>> iterator = ret.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                Log.d(TAG, "key:" + entry.getKey() + "\tvalues:" + entry.getValue());
            }*/
            mSwipeControllerListener.onQRCodePaymentResult(ret, 3);
        } else if ("9110".equals(fmtPackEntity.getMsgType())) {
            Log.i(TAG, "微信报文解析");
            String currentInfo = fmtPackEntity.getCurrentPackInfo();
            byte[] saleData = AFUtils.hexString2ByteArray(currentInfo);
            Log.i(TAG, "微信报文解密后数据:\t" + AFUtils.showResult16Str(saleData));
            Map<String, String> ret = myans.ansframe8583Qrcode(saleData, saleData.length, 6);
           /* Iterator<Map.Entry<String, String>> iterator = ret.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, String> entry = iterator.next();
                Log.d(TAG, "key:" + entry.getKey() + "\tvalues:" + entry.getValue());
            }*/
            mSwipeControllerListener.onQRCodePaymentResult(ret, 6);
        } else {
            //主扫，被扫未响应情况，重新签到处理
            Log.i(TAG, "报文解析还未处理");
            spTransUtil.setTransSerialNumber();
            handler.post(new Runnable() {
                @Override
                public void run() {
                    mSwipeControllerListener.onServerUnresponsive();
                }
            });
        }
    }



    /**
     * 数据计算
     *
     * @param model 公钥 tag 01
     * @param exp   E tag 02
     * @param data  随机数 tag03
     */
    private boolean dataCalcRsa(String model, String exp, String data) {
        try {
            byte[] byteModel = HexUtil.hexStringToByte(model);
            Log.i(TAG, "byteModel: " + AFUtils.showResult16Str(byteModel));
            byte[] byteExp = AFUtils.hexString2ByteArray(exp);
            Log.i(TAG, "byteExp: " + AFUtils.showResult16Str(byteExp));
            byte[] byteDataIn = HexUtil.hexStringToByte(data);
            Log.i(TAG, "byteDataIn: " + AFUtils.showResult16Str(byteDataIn));
            byte[] byteDataOut = new byte[256];
            EncDecResult encDecResult = mISystemEncDecBinderService.encDecResult(byteModel, byteModel.length, byteExp, byteExp.length, byteDataIn, byteDataOut);
            int retEncDec = encDecResult.getRet();
            String getResData = AFUtils.showResult16Str(encDecResult.getTagData());
            Log.i(TAG, "返回值:" + retEncDec);
            Log.i(TAG, "计算结果:" + getResData);
            String dataEncBase64 = Base64.getEncoder().encodeToString(AFUtils.hexString2ByteArray(getResData));
            Log.i(TAG, "dataEncBase64：\t" + dataEncBase64);
            String tempData = AFUtils.stringToHex(dataEncBase64);
            Log.i(TAG, "tempData：\t" + tempData);
            handler.post(new Runnable() {
                @Override
                public void run() {
                    AFUpdateMasterKey(tempData);
                }
            });
            return true;
        } catch (RemoteException e) {
            e.printStackTrace();
            return false;
        }
    }
}
