package com.hjq.demo.ans8583;

import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.blankj.utilcode.util.EncodeUtils;
import com.blankj.utilcode.util.EncryptUtils;
import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.SPUtils;
import com.hjq.demo.entity.PayQRCodeQueryEntity;
import com.hjq.demo.entity.WorkingKeyEntity;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.model.MainModelImpl;
import com.hjq.demo.model.SaleDataModel;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.SPTransUtil;
import com.hjq.demo.utils.TLVModel;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.KeyInfoParam;
import com.szanfu.sdk.api.pinpad.PosGetKcvResult;
import com.szanfu.sdk.api.util.HexUtil;

import org.apache.commons.lang3.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static java.lang.System.arraycopy;

public class My8583Ans extends Easy8583Ans {

    private static final String TAG = " My8583Ans";

    //通信涉及到的一些参数,内部使用
    private static long commSn = 1; //通讯流水号
    private static byte[] licenceNum = {0x33, 0x30, 0x36, 0x30};//入网许可证编号
    private String upBinNumber;  //银行卡卡号
    private String cardSnNumber; //持卡序号
    private String cardValiteDate; //卡有效期
    private String field35Data; //二磁道数据

    //需要外部设置的参数有：商户号，终端号，主秘钥，TPDU(以下的为默认值，并提供set和get方法)
    //需要持久化存储这些参数，每次使用时加载

    //测试环境配置
  /*  //商户号
    private static String manNum = "100000000103419";
    //终端号
    private static String posNum = "100004088325";
    //设备TUSN号
    private static String devicesTUSN = "00005002689990000638"; */

    private static String mainKey = "31313131313131313131313131313131"; //主秘钥

    //正式环境测试
    //商户号
    private static String manNum = "110000003517177";
    //终端号
    private static String posNum = "100009855445";
    //设备TUSN号
    private static String devicesTUSN = "00005002689990000630";


    private static String TPDU = "6030393535";
    private static byte[] piciNum = new byte[3];//批次号
    private static long posSn = 1; //终端交易流水,每笔交易加一

    //记录交易流水号
    private SPUtils spUtils = SPUtils.getInstance("SP_SerialNumber");
    //交易流水号固定键值
    private static final String SERIAL_NUMBER = "TransSerialNumber";

    private SPTransUtil spTransUtil = new SPTransUtil();

    public My8583Ans() {
        //通过子类修改父类的配置
        pack.tpdu = hexStringToBytes(TPDU);
        upBinNumber = "";
        cardSnNumber = "";
        cardValiteDate = "";
        field35Data = "";
    }

    /**
     * 某个事务完成后，设置交易流水号累加1
     */
   /* private void setTransSerialNumber() {
        LogUtils.i( "设置交易流水号: ");
        //设备存在流水号记录
        if (spUtils.contains(SERIAL_NUMBER)) {
            LogUtils.i( "已存储流水号 ");
            int currentSn = Integer.parseInt(spUtils.getString(SERIAL_NUMBER));
            spUtils.put(SERIAL_NUMBER, String.format("%06d", (currentSn + 1)));
        } else {
            LogUtils.i( "未存储流水号 ");
            //000001开始
            spUtils.put(SERIAL_NUMBER, String.format("%06d", 5001));
            LogUtils.i( "【首次】写流水号:");
        }
        LogUtils.i( "当前设备交易流水号:" + spUtils.getString(SERIAL_NUMBER));
    }*/

    /**
     * 获取设备目前交易流水号
     */
    /*private String getTransSerialNumber() {
        //设备存在流水号记录
        if (spUtils.contains(SERIAL_NUMBER)) {
            int currentSn = Integer.parseInt(spUtils.getString(SERIAL_NUMBER));
            return String.format("%06d", currentSn);
        } else {
            //000001开始
            return String.format("%06d", 5000);
        }
    }*/


    /**
     * 签到报文组帧
     *
     * @param field
     * @param tx
     */
    public void frame8583QD(__8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);

       /* field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();*/
        //附加报文头组成-》12字节终端号与41域内容相同+原始报文长度+18字节保留域
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "frame8583QD:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;

        LogUtils.i( "frame8583QD: " + AFUtils.showResult16Str(additionalHeadByte));
        //消息类型
        tx.msgType[0] = 0x08;
        tx.msgType[1] = 0x00;
        tx.bitMap = new byte[]{0x20, 0x20, 0x00, 0x00, 0x00, (byte) 0xc0, 0x00, 0x10};
        //3域
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x20, 0x00, 0x01};
        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tmp);

        //17域
        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);
        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);


        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);


        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");


        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 12;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();
        //60域
        field[59].ishave = 1;
        field[59].len = 0x14;
        field[59].data = new byte[7];
        //交易类型
        field[59].data[0] = 0x00;
        //操作员号
        field[59].data[1] = 0x00;
        field[59].data[2] = 0x10;
        arraycopy(piciNum, 0, field[59].data, 3, 2);
        field[59].data[5] = 0x00;
        field[59].data[6] = 0x01;
        //0x00,0x10,0x00,0x06,0x00,0x03
/*
        field[59].ishave = 1;
        field[59].len = 0x11;
        field[59].data = new byte[6];
        field[59].data[0] = 0x00;
        arraycopy(piciNum, 0, field[59].data, 1, 3);
        field[59].data[4] = 0x00;
        field[59].data[5] = 0x30;
*/
        //62域
        field[61].ishave = 0;
        field[61].len = 0x25;
        field[61].data = new byte[25];
        String str = "Sequence No12";
        arraycopy(str.getBytes(), 0, field[61].data, 0, 13);
        arraycopy(licenceNum, 0, field[61].data, 13, 4);
        arraycopy(posNum.getBytes(), 0, field[61].data, 17, 8);
        //63域
        field[62].ishave = 0;
        field[62].len = 0x03;
        field[62].data = new byte[3];
        field[62].data[0] = 0x30;
        field[62].data[1] = 0x30;
        field[62].data[2] = 0x31;
        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 8583签到的响应报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public int ans8583QD(byte[] rxbuf, int rxlen) {
        LogUtils.i( "签到报文数据解析: " + AFUtils.showResult16Str(rxbuf));

        //签到成功，更新流水号
        spTransUtil.setTransSerialNumber();
        int ret = 0;
        ret = ans8583FieldsX(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        LogUtils.d( "解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x08) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            //resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            //resMap.put(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP, strTransResult);
            //return resMap;
            return 3;
        }

        byte[] payReceipt = fieldsRecv[16].data;
        String strPayReceipt = AFUtils.showResult16Str(payReceipt);
        LogUtils.d( "17域数据:" + strPayReceipt);
        ArrayList<TLVModel> tList = AFUtils.praseTLVStrX(payReceipt);
        Map<String, String> tm = AFUtils.list2Map(tList, 2);
        Iterator<Map.Entry<String, String>> iterator = tm.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            //resMap.put(entry.getKey(), AFUtils.hexGBK2String(entry.getValue()));
            LogUtils.d( "key:" + entry.getKey() + "\tvalues:" + AFUtils.hexGBK2String(entry.getValue()));
        }

        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            //return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            //return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            //return 6;
        }

        byte[] workingKey = fieldsRecv[61].data;
        String strWk = AFUtils.showResult16Str(workingKey);
        LogUtils.d( "秘钥信息:" + strWk);
        ArrayList<WorkingKeyEntity> workingKeyEntities = workingKeyFormat(strWk);
        for (WorkingKeyEntity workingKeyEntity : workingKeyEntities) {
            LogUtils.d( "导入秘钥信息:" + workingKeyEntity.toString());
            boolean importWkResult = setPOSWriteWK(workingKeyEntity);
            if (!importWkResult) {
                LogUtils.d( "*************导入秘钥异常，请检查主密钥*************");
                return 2;
            }
        }
        //签到成功
        return 0;
    }

    /**
     * 开始更新终端参数
     *
     * @param field
     * @param tx
     */
    public void updateDevParam_8583_send(__8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);

       /* field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();*/
        //附加报文头组成-》12字节终端号与41域内容相同+原始报文长度+18字节保留域
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "frame8583QD:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;

        LogUtils.i( "frame8583QD: " + AFUtils.showResult16Str(additionalHeadByte));

        //消息类型
        tx.msgType[0] = 0x08;
        tx.msgType[1] = 0x00;
        tx.bitMap = new byte[]{0x20, 0x20, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, 0x00};
        //3域
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x50, 0x00, 0x00};
        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tmp);

        //17域
        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String applicationTypeNumber = AFUtils.packSendTLV("BF18", "31303031");
        sbField17Data.append(applicationTypeNumber);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);


        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 终端参数更新响应
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public int updateDevParam_8583_recv(byte[] rxbuf, int rxlen) {
        LogUtils.i( "更新设备参数响应解析: " + AFUtils.showResult16Str(rxbuf));

        //签到成功，更新流水号
        spTransUtil.setTransSerialNumber();

        int ret = 0;
        ret = ans8583FieldsUpdateDevParam(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        LogUtils.d( "解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x08) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            //resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            //resMap.put(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP, strTransResult);
            //return resMap;
            return 3;
        }

        byte[] payReceipt = fieldsRecv[16].data;
        String strPayReceipt = AFUtils.showResult16Str(payReceipt);
        LogUtils.d( "17域数据:" + strPayReceipt);
        ArrayList<TLVModel> tList = AFUtils.praseTLVStrX(payReceipt);
        Map<String, String> tm = AFUtils.list2Map(tList, 2);
        Iterator<Map.Entry<String, String>> iterator = tm.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            //resMap.put(entry.getKey(), AFUtils.hexGBK2String(entry.getValue()));
            LogUtils.d( "key:" + entry.getKey() + "\tvalues:" + AFUtils.hexGBK2String(entry.getValue()));
        }

        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            //return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            //return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            //return 6;
        }

        //更新参数
        try {
            byte[] devInfo = fieldsRecv[44].data;
            String strDevInfo = AFUtils.showResult16Str(devInfo);
            LogUtils.i( "POS参数更新: " + strDevInfo);
            String[] arrDevInfo = AFUtils.hexGBK2String(strDevInfo).split(",");
            for (int i = 0; i < arrDevInfo.length; i++) {
                LogUtils.i( "参数【" + (i + 1) + "】:->" + arrDevInfo[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e( "updateDevParam_8583_recv: 转换POS参数异常！");
        }
        //操作成功
        return 0;
    }

    /**
     * 终端管理公钥下载
     *
     * @param field
     * @param tx
     */
    public void updatePubKey_8583_send(__8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);

       /* field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();*/
        //附加报文头组成-》12字节终端号与41域内容相同+原始报文长度+18字节保留域
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "updatePubKey_8583_send:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;

        LogUtils.i( "updatePubKey_8583_send: " + AFUtils.showResult16Str(additionalHeadByte));

        //消息类型
        tx.msgType[0] = 0x08;
        tx.msgType[1] = 0x00;
        tx.bitMap = new byte[]{0x20, 0x20, (byte) 0x80, 0x00, 0x00, (byte) 0xC0, 0x00, 0x00};
        //3域
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x51, 0x00, 0x00};
        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tmp);

        //17域
        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);

        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        String transactionType = AFUtils.packSendTLV("BF26", "30");
        sbField17Data.append(transactionType);

        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String pub_mast_key = AFUtils.packSendTLV("BF70", "41463937433533444534423030373638444136323332353531423830393737433645");
        sbField17Data.append(pub_mast_key);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);


        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 12;
        field[40].data = posNum.getBytes();

        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 终端管理公钥下载响应
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public Map<String, String> updatePubKey_8583_recv(byte[] rxbuf, int rxlen) {
        LogUtils.i( "终端管理公钥下载响应解析: " + AFUtils.showResult16Str(rxbuf));
        Map<String, String> updatePubKeyRes = new ArrayMap<>();
        //签到成功，更新流水号
        spTransUtil.setTransSerialNumber();
        int ret = 0;
        ret = ans8583FieldsX(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            updatePubKeyRes.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_ERROR);
            return updatePubKeyRes;
        }
        LogUtils.d( "解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x08) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            updatePubKeyRes.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_MSG_TYPE_ERROR);
            return updatePubKeyRes;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            //resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            updatePubKeyRes.put(AFConstant.TRADE_MSG_TAG, strTransResult);
            return updatePubKeyRes;
        }

        byte[] pubKey = fieldsRecv[44].data;
        String strPayReceipt = AFUtils.hexGBK2String(AFUtils.showResult16Str(pubKey));
        //4D49494243674B43415145417369394D32374B355A65456775525164497A39416A6A6D6839716E68794F6641506E49395973774F2B766D2B32447837577842686F545449494F4358437163324D45434B41513537696A784F484A523478337735387349615662327A6877384A68795264612B574F636C4A3157657530537545585A73397454676647694C516C65466F76774544534D566B687A734B6A4E5061742F55523368454C5977664144343270516B6B31666462772F7879757441587A7433786E4E573168652B7437516E373443612B4D504E757146595975597531566B34415270505A57326A70796C4E5669494F642F59456D38326B684D75497662486D6B456D33314655686E6979685A68366448584837364838462F5464694568552B496450756437444968426944587068434B44732B6476553148546A4C5762444141716F63355753365268744A5761614A6E6A665A4378776C356459424278392B51494441514142
        LogUtils.d( "45域数据:" + strPayReceipt);
        //3082010A0282010100B22F4CDBB2B965E120B9141D233F408E39A1F6A9E1C8E7C03E723D62CC0EFAF9BED83C7B5B1061A134C820E0970AA73630408A010E7B8A3C4E1C9478C77C39F2C21A55BDB3870F0987245D6BE58E72527559EBB44AE11766CF6D4E07C688B425785A2FC040D2315921CEC2A334F6ADFD44778442D8C1F003E36A50924D5F75BC3FC72BAD017CEDDF19CD5B585EFADED09FBE026BE30F36EA85618B98BB5564E004693D95B68E9CA535588839DFD8126F3692132E22F6C79A4126DF51548678B285987A7475C7EFA1FC17F4DD884854F8874FB9DEC32210620D7A6108A0ECF9DBD4D474E32D66C3000AA8739592E9186D25669A2678DF642C70979758041C7DF90203010001
        String fmtPubKeyBase64Decode = AFUtils.showResult16Str(EncodeUtils.base64Decode(strPayReceipt));
        LogUtils.i( "fmtPubKeyBase64Decode : \n" + fmtPubKeyBase64Decode);
        updatePubKeyRes.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_SUCCESS);
        updatePubKeyRes.put(AFConstant.TRANS_UPDATE_BASE64_PUB_KEY, fmtPubKeyBase64Decode);
        return updatePubKeyRes;
    }

    /**
     * 终端主密钥下载
     *
     * @param field
     * @param tx
     */
    public void updateMasterKey_8583_send(__8583Fields[] field, Pack tx, String field45Data) {

        init8583Fields(fieldsSend);

       /* field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();*/
        //附加报文头组成-》12字节终端号与41域内容相同+原始报文长度+18字节保留域
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "updateMasterKey_8583_send:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;

        LogUtils.i( "updateMasterKey_8583_send: " + AFUtils.showResult16Str(additionalHeadByte));

        //消息类型
        tx.msgType[0] = 0x08;
        tx.msgType[1] = 0x00;
        tx.bitMap = new byte[]{0x20, 0x20, (byte) 0x80, 0x00, 0x00, (byte) 0xC0, 0x00, 0x00};
        //3域
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x52, 0x00, 0x00};
        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tmp);

        //17域
        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);

        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        String transactionType = AFUtils.packSendTLV("BF26", "30");
        sbField17Data.append(transactionType);

        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String pub_mast_key = AFUtils.packSendTLV("BF70", "41463937433533444534423030373638444136323332353531423830393737433645");
        sbField17Data.append(pub_mast_key);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);


        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 12;
        field[40].data = posNum.getBytes();

        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        //45域

        String fmtField45DataLen = StringUtils.leftPad(String.valueOf(field45Data.length() / 2), 4, "0");
        LogUtils.i( "fmtField45DataLen: " + fmtField45DataLen + "\n");
        byte[] currentField45Data = AFUtils.hexString2ByteArray(fmtField45DataLen + field45Data);

        field[44].ishave = 1;
        field[44].type = 0;
        field[44].len = currentField45Data.length;
        field[44].data = currentField45Data;
        LogUtils.i( "currentField45Data: " + AFUtils.showResult16Str(field[44].data) + "\n");



        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 终端主密钥下载响应
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public int updateMasterKey_8583_recv(byte[] rxbuf, int rxlen) {
        LogUtils.i( "终端主密钥下载响应解析: " + AFUtils.showResult16Str(rxbuf));
        //签到成功，更新流水号
        spTransUtil.setTransSerialNumber();
        int ret = 0;
        ret = ans8583FieldsX(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        LogUtils.d( "解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x08) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            //resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            //resMap.put(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP, strTransResult);
            //return resMap;
            return 3;
        }


        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            //return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            //return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            //return 6;
        }

        byte[] tmk = fieldsRecv[61].data;
        String strTmk = AFUtils.showResult16Str(tmk);
        LogUtils.d( "主秘钥密文信息:" + strTmk);

        byte[] data = AFUtils.hexString2ByteArray(strTmk);
        String strRandom = spTransUtil.getTmkRandom();
        LogUtils.i( "随机数: " + strRandom);
        byte[] key = AFUtils.hexString2ByteArray(strRandom);
        LogUtils.i( "data: " + AFUtils.showResult16Str(data));
        LogUtils.i( "key: " + AFUtils.showResult16Str(key));
        byte[] byteTmk = EncryptUtils.decrypt3DES(data, key, "DESede/ECB/NoPadding", null);
        LogUtils.d( "主密钥明文信息:" + AFUtils.showResult16Str(byteTmk));

        boolean updateTmk = setPOSTmKey(AFUtils.showResult16Str(byteTmk).substring(0, 32));
        //更新主密钥成功
        return updateTmk ? 0 : -1;
    }

    public static void testDevicesSin() {
        String strWk = "EAFB3948FA6E7E29E9A05341DAC44D990597C2815D1055E2F7889615B79EBFB946B45E15495DAFA81835E7FE4DD44EE33D4E8114E92796ABE95B31C28A51C345ACFFABB6FB7E766F";
        LogUtils.d( "秘钥信息:" + strWk);
        ArrayList<WorkingKeyEntity> workingKeyEntities = workingKeyFormat(strWk);
        for (WorkingKeyEntity workingKeyEntity : workingKeyEntities) {
            LogUtils.d( "导入秘钥信息:" + workingKeyEntity.toString());
            boolean importWkResult = setPOSWriteWK(workingKeyEntity);
            if (!importWkResult) {
                LogUtils.d( "*************导入秘钥异常，请检查主密钥*************");
                break;
            }
        }
        testDevicesXor();
    }

    private static void testDevicesXor() {
        //String strCalcMac = dataFormat("0200702486c020c09a35196232086400002909390000001000000000100000032025020142bf1603413137bf170441463638bf190a32303233303432303032bf1a143030303035303032363839393930303030363338bf24023131bf260130bf3415343532337c3436307c30307c313238363132323332bf42083033333030313030bf580130bf600134bf61143839383630344132313932313830383438313531bf620f383630373231303639333138383036051000100012376232086400002909390d2502220100004870003130303030343038383332353130303030303030303130333431393135365e39e2b43427f85d260000000000000001599f3303e0f9c8950580800408009f1e085465726d696e616c9f1013071d0103a0a800010a010000000000e04029149f260831d97dbd7fa3f4f99f36020bde82027c009c01009f1a0201569a032305249f02060000000010005f2a0201569f03060000000000009f3501229f34030203009f37049e10d4499f2701809f4104000003209f6310303130343339303000000000000060008408a00000033301010100994131303030413230363930313030323032303230323030303030353030323638393939303030303633383033303036393039333930303430303834444433323842463035303038323330343230303241333030304134303030413530303550333039390014220010010010000167000001000000008000000032000000070800031c42404ec19c23201353ebff0260551720313c10319e7c71a665be144443d97fa4f4ea461eff0233922f0078a64c38a77f28b8c038293c74856aebf333260eea6af240f58012baa080ff02107395938d44e6327c26287cd7b046843f411d4dc9d356d54820b71680ff02cbab71e81bcb7eb00589f13880ff029b934ee71b27ca2ff714ff02fd0af9ce9dbf755047ff024f80ff02");
        String strCalcMac = dataFormat("0200702486c020c09a35196232086400002909390000001000000000100000032125020142bf1603413137bf170441463638bf190a32303233303432303032bf1a143030303035303032363839393930303030363338bf24023131bf260130bf3415343532337c3436307c30307c313238363132323432bf42083033333130313030bf580130bf600134bf61143839383630344132313932313830383438313531bf620f383630373231303639333138383036051000100012376232086400002909390d2502220100004870003130303030343038383332353130303030303030303130333431393135365e39e2b43427f85d260000000000000001599f3303e0f9c8950580800408009f1e085465726d696e616c9f1013071d0103a0a800010a01000000000049c950fe9f260895351eb0d49779a99f36020bdf82027c009c01009f1a0201569a032305249f02060000000010005f2a0201569f03060000000000009f3501229f34030203009f3704e24ea6889f2701809f4104000003219f6310303130343339303000000000000060008408a00000033301010100994131303030413230363930313030323032303230323030303030353030323638393939303030303633383033303036393039333930303430303834444433323842463035303038323330343230303241333030304134303030413530303550333039390014220010010010000198000001000000008000000032000000070800031ca94015b24ef420585d53ff022d60dc9ecca3aa1da7ff02a632fbbf92d21168e2189083d4896ece4780d95b96f82be2cafa68598380847286a5e6ff024e674f9b76f1c8bea996468b9f62259761cde2476f771f8724aa406f2fa6863ecc531676ff02146689b0e34a631843dcb38f250ef839394779fd9ff33f0f1767c44ca6b29b80ff0243f1e1104d06cee2cf53ca1bf037bb6aee813836ecf77a73b0366150ff02382617354adb6335b74680ff0238ff02");
        LogUtils.i( "Format MAC数据1->: " + strCalcMac);
        //byte[] mac = upGetMac(pk.txBuffer, 13, len - 13 - 8, hexStringToBytes(macKey));
        byte[] mac = setPOSGetMac(AFUtils.hexString2ByteArray(strCalcMac));
        LogUtils.i( "计算MAC结果->: " + AFUtils.showResult0xStr(mac));

    }


    /**
     * 格式化工作秘钥
     *
     * @param strWk 从服务器获取的工作秘钥数据
     * @return 格式化后的数据, 列如：
     * {keyID='2', keyData='6FABC136B3B545AEC761B209735C952F', keyCheckValues='D314D4A4CD6F7D61'}
     */
    private static ArrayList<WorkingKeyEntity> workingKeyFormat(String strWk) {
        int keyLen = strWk.length() / 3;
        System.out.println("每个秘钥长度:" + keyLen);
        StringBuffer sbWK = new StringBuffer();
        ArrayList<WorkingKeyEntity> workingKeyEntities = new ArrayList<WorkingKeyEntity>(3);
        workingKeyEntities.clear();
        //A414DBEB8A1397D6F7E039567D8A3AB0130F363D2B812DD0
        int keyDataLen = 32;
        int keyCheckValueLen = 16;
        int currentKeyIndex = 0;
        for (int i = 1; i < 4; i++) {
            WorkingKeyEntity keyEntity = new WorkingKeyEntity();
            keyEntity.setKeyID(i);
            //最后一个联动优势特殊处理，秘钥索引5
            if (i == 3) {
                keyEntity.setKeyID(i + 2);
            } else {
                keyEntity.setKeyID(i);
            }
            String currentKeyInfo = strWk.substring(currentKeyIndex, i * keyLen);
            keyEntity.setKeyData(currentKeyInfo.substring(0, keyDataLen));
            keyEntity.setKeyCheckValues(currentKeyInfo.substring(keyDataLen, currentKeyInfo.length()));
            workingKeyEntities.add(keyEntity);
            currentKeyIndex = i * keyLen;
        }
        return workingKeyEntities;
    }

    int TMK_KEY_ID = 0;
    int PIN_KEY_ID = 1;
    int MAC_KEY_ID = 2;
    int TDK_KEY_ID = 3;
    int TLK_KEY_ID = 4;
    int TCK_KEY_ID = 5;//datakey
    static int mode = 0x01;

    /**
     * 写工作密钥
     * DAL_POSWriteWK --下发的62域，PINKEY,MACKEY,dataKEY,TDK(支持磁道加密解析，不支持不解析)
     */
    private static boolean setPOSWriteWK(WorkingKeyEntity workingKeyEntity) {
        try {
            int keyNo = 0x02;
            int keyLen = 0x10; // 8 或者  16   或24
            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            kcvInfoParam.setCheckMode(0);
            int mKeyNo = 0x02;
            LogUtils.e( "setPOSWriteWK: -----start-----》》》");

            //获取秘钥ID
            int keyId = workingKeyEntity.getKeyID();
            //获取秘钥数据
            byte[] keyData = HexUtil.hexStringToByte(workingKeyEntity.getKeyData());

            int ret = MainModelImpl.getPinPadSingleton().posWriteWorkingKey(keyId, keyLen, keyData, kcvInfoParam, mode, mKeyNo);
            LogUtils.e( "setPOSWriteWK: -----end-----》》》" + ret);

            if (ret == 0) {
                try {
                    LogUtils.e( "mIPinPadBinderService.posGetKcv: ------------start---------->>");
                    PosGetKcvResult posGetKcvResult = MainModelImpl.getPinPadSingleton().posGetKcv(mKeyNo, keyId);
                    LogUtils.e( "mIPinPadBinderService.posGetKcv: ------------end---------->>" + posGetKcvResult.getRet());
                    int getKcvResultRet = posGetKcvResult.getRet();
                    if (posGetKcvResult.getRet() == 0) {
                        kcvInfoParam.getCheckBuf();
                        byte[] checkRes = posGetKcvResult.getKcvInfo();
                        LogUtils.e( "checkRes：\t" + AFUtils.showResult0xStr(checkRes));
                        String strKecResult = AFUtils.showResult16Str(posGetKcvResult.getKcvInfo());
                        LogUtils.e( "setPOSWriteWK: ----------strKecResult---->" + strKecResult);
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
                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 写主密钥               DAL_POSWriteKey
     *
     * @param itemName
     */
    private static boolean setPOSTmKey(String tmkData) {
        // DISABLED. This is the legacy (non-Hypercom) sign-in path: it takes a
        // master key out of field 61 of an 8583 response and writes it to index
        // 2, over the top of the TMK injected from the admin panel.
        //
        // Under the Hypercom 3.34 host there is no message that replaces the
        // terminal master key - 6.27.2 replaces the working key, 6.19 the PIN
        // and MAC keys - so any write here is a bug, and it is one of the paths
        // that erased the injected TMK after a logon.
        //
        // Delete the early return only if you deliberately go back to the
        // legacy remote-key-download host.
        if (true) {
            LogUtils.e("setPOSTmKey: REFUSED - the terminal master key may only be "
                    + "written from the admin panel (legacy DE61 key download ignored)");
            return false;
        }
        try {
            KeyInfoParam keyInfoParam = new KeyInfoParam();
            keyInfoParam.setDstKeyIdx((short) 2);
            keyInfoParam.setDstKeyLen(16);
            keyInfoParam.setDstKeyType((byte) 0x10);
            byte[] mainKey = HexUtil.hexStringToByte(tmkData);
            keyInfoParam.setDstKeyValue(mainKey); //16字节hex，主密钥明文
            keyInfoParam.setSrcKeyIdx((short) 0);
            keyInfoParam.setSrcKeyType((byte) 0x01);
            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            //kcvInfoParam.setCheckBuf(//这个不用管);
            kcvInfoParam.setCheckMode(0);
            int ret = MainModelImpl.getPinPadSingleton().posMasterKey(keyInfoParam, kcvInfoParam);
            LogUtils.d( "写入主秘钥结果:" + ret);
            return ret == 0;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 消费报文
     *
     * @param field
     * @param tx
     */
    public void frame8583Sale(__8583Fields[] field, Pack tx, SaleDataModel saleDataModel, int cardType) {
        //0：IC/插卡 1:RF/挥卡 2：MSC/刷卡->卡类型不同区分免密和密码
        switch (cardType) {
            case 0:
                LogUtils.i( "消费报文组包，卡类型: IC/插卡");
                break;
            case 1:
                LogUtils.i( "消费报文组包，卡类型: RF/挥卡");
                break;
            case 2:
                LogUtils.i( "消费报文组包，卡类型:  MSC/刷卡");
                break;
        }

        // test();
        init8583Fields(fieldsSend);

       /* field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();*/
        //附加报文头组成-》12字节终端号与41域内容相同+原始报文长度+18字节保留域
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x34};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "frame8583QD:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;

        LogUtils.i( "frame8583QD: " + AFUtils.showResult16Str(additionalHeadByte));
        //消息类型
        tx.msgType[0] = 0x02;
        tx.msgType[1] = 0x00;
//        tx.bitMap = new byte[]{0x70, 0x20, (byte) 0x86, (byte) 0x80, 0x20, (byte) 0xC0, (byte) 0x82, 0x35};
        tx.bitMap = new byte[]{0x70, 0x20, (byte) 0x86, (byte) 0x80, 0x20, (byte) 0xC0, (byte) 0x82, 0x31};

        //2域主账号
        field[1].ishave = 1;
        String cardNo = saleDataModel.getCardNo();
        LogUtils.i( "卡号数据: " + cardNo + "\n");
        byte[] cardNoLen = new byte[1];
        if (cardNo.contains("F")) {
            cardNo = cardNo.replace("F", "0");
            cardNoLen = AFUtils.hexString2ByteArray(String.valueOf(cardNo.length() - 1));
        } else {
            cardNoLen = AFUtils.hexString2ByteArray(String.valueOf(cardNo.length()));
        }
        LogUtils.i( "最终卡号: " + cardNo + "\n");
        LogUtils.i( "cardNoLen: " + AFUtils.showResult0xStr(cardNoLen) + "\n");
        byte[] byteCard = AFUtils.hexString2ByteArray(cardNo);
        LogUtils.i( "cardNo: " + AFUtils.showResult0xStr(byteCard) + "\n");
        field[1].data = new byte[cardNoLen.length + byteCard.length];
        System.arraycopy(cardNoLen, 0, field[1].data, 0, cardNoLen.length);
        System.arraycopy(byteCard, 0, field[1].data, cardNoLen.length, byteCard.length);
        LogUtils.i( "cardNo: " + AFUtils.showResult0xStr(field[1].data) + "\n");
        field[1].len = field[1].data.length;
        field[1].type = 0;

        //3域 处理码
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x00, 0x00, 0x10};


        //4域交易金额
        String amount = AFUtils.dealMoneyStr(saleDataModel.getTransAmount());
        field[3].ishave = 1;
        field[3].len = 6;
        field[3].data = AFUtils.hexString2ByteArray(amount);
        LogUtils.i( "amount: " + AFUtils.showResult0xStr(field[3].data) + "\n");

        //11域，受卡方系统跟踪号BCD 通讯流水
        field[10].ishave = 1;
        field[10].len = 3;
        //String tmp = String.format("%06d", commSn);
        String tempSerialNumber = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tempSerialNumber);
        LogUtils.i( "流水号 0X: " + AFUtils.showResult0xStr(field[10].data) + "\n");

        //磁条不上送卡有效期
        if (cardType != 2) {
            //14域，卡有效期 ->IC卡有效期长1字节
            byte[] cardValid = AFUtils.hexString2ByteArray(saleDataModel.getCardValid().substring(0, 4));
            field[13].ishave = 1;
            field[13].len = cardValid.length;
            field[13].data = cardValid;
            LogUtils.i( " 卡有效期 0X: " + AFUtils.showResult0xStr(field[13].data) + "\n");
        }

       /* 0102
        BF16 03  413136 厂商编号
        BF17 04  41463730 机具型号编号
        BF19 0C  565332303230303833303033 应用版本号
        BF1A 1A  3030303035303032323030363730393939303030303031363638 设备SN序列号 00005002200670999000001668
        BF24 02  3131 终端类型
        BF26 01  30 交易类型
        BF34 12  30343532337C3436307C307C34393739307C   终端当前基站定位 04523|460|0|49790|
        BF42 08  3030303830313030 单子签名交易上送的交易特征码 00080100
        BF58 01  30  是否VIP标识*/
        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);
        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        //0普通交易 1 双免交易
        String transactionType = "";
        switch (cardType) {
            case 0://IC
            case 2://磁条
                transactionType = AFUtils.packSendTLV("BF26", "30");
                String transactionCode = AFUtils.packSendTLV("BF42", getTransactionCode("001001", tempSerialNumber));
                sbField17Data.append(transactionCode);
                break;
            case 1://RF
                transactionType = AFUtils.packSendTLV("BF26", "31");
                break;
        }
        sbField17Data.append(transactionType);
        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);


        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        //22域 服务点输入方式码
        field[21].ishave = 1;
        field[21].len = 2;
        //卡类型 0：IC/插卡 1:RF/挥卡 2：MSC/刷卡->卡类型不同区分免密和密码
        switch (cardType) {
            case 0:
                field[21].data = AFUtils.hexString2ByteArray("0510");
                break;
            case 1:
                field[21].data = AFUtils.hexString2ByteArray("0720");
                break;
            case 2:
                field[21].data = AFUtils.hexString2ByteArray("0210");
                break;
        }
        LogUtils.i( " 服务点条件码 0X: " + AFUtils.showResult0xStr(field[21].data) + "\n");

        //26域 个人密码明文的最大长度
        //卡类型 0：IC/插卡 1:RF/挥卡 2：MSC/刷卡->卡类型不同区分免密和密码
        switch (cardType) {
            case 0:
            case 2:
                field[25].ishave = 1;
                field[25].len = 1;
                field[25].data = AFUtils.hexString2ByteArray("12");
                LogUtils.i( " 个人密码明文的最大长度 0X: " + AFUtils.showResult0xStr(field[25].data) + "\n");
                break;
        }


        //22域与52域关联
        switch (cardType) {
            case 0:
            case 2:
                field[51].ishave = 1;
                byte[] hexPinBlock = AFUtils.hexString2ByteArray(saleDataModel.getPinBlock());
                field[51].len = hexPinBlock.length;
                field[51].data = hexPinBlock;
                LogUtils.i( " 个人标识码 0X: " + AFUtils.showResult0xStr(field[51].data) + "\n");
                break;
           /* case 1:
                field[51].ishave = 0;
                field[51].data = AFUtils.hexString2ByteArray("0720");
                break;*/
        }

        //53域密码相关算法
        switch (cardType) {
            case 0:
            case 2:
                field[52].ishave = 1;
                byte[] hexPinBlock = AFUtils.hexString2ByteArray("2600000000000000");
                field[52].len = hexPinBlock.length;
                field[52].data = hexPinBlock;
                LogUtils.i( " 安全控制信息 0X: " + AFUtils.showResult0xStr(field[52].data) + "\n");
                break;
           /* case 1:
                field[51].ishave = 0;
                field[51].data = AFUtils.hexString2ByteArray("0720");
                break;*/
        }


       /* cardNo: 6212142600000000167F
        CardAID: A000000333010101
        Track2data: 6212142600000000167D2903220493903112
        CardSerialNumber: 00 ->卡序列号
        CardValid: 290331*/
        //23域 卡序列号 【23】:00 10

        String strCardSerialNumber = saleDataModel.getCardSerialNumber();
        strCardSerialNumber = StringUtils.leftPad(strCardSerialNumber, 3, "0");
        strCardSerialNumber = StringUtils.rightPad(strCardSerialNumber, 4, "0");
        byte[] cardSerialNumber = AFUtils.hexString2ByteArray(strCardSerialNumber);
        field[22].ishave = 1;
        field[22].len = cardSerialNumber.length;
        field[22].data = cardSerialNumber;
        LogUtils.i( " 卡序列号 0X: " + AFUtils.showResult0xStr(field[22].data) + "\n");

        //25域 服务点条件码
        field[24].ishave = 1;
        field[24].len = 1;
        field[24].data = AFUtils.hexString2ByteArray("00");
        LogUtils.i( " 服务点条件码 0X: " + AFUtils.showResult0xStr(field[24].data) + "\n");


       /* String amount = AFUtils.dealMoneyStr(saleDataModel.getTransAmount());
        field[3].ishave = 1;
        field[3].len = 6;
        field[3].data = AFUtils.hexString2ByteArray(amount);
        LogUtil.i(TAG, "amount: " + AFUtils.showResult0xStr(field[3].data) + "\n");*/

        String strTrack2data = saleDataModel.getTrack2data();
        LogUtils.i( "strTrack2data----------------: " + strTrack2data);
        int track2DataLen = strTrack2data.length();
        LogUtils.i( "track2DataLen----------------: " + track2DataLen);
        String strTrack2dataLen = "";
        if (track2DataLen >= 37) {
            //6232086400002909390D25022201000048700F->挥卡磁道信息
            strTrack2data = strTrack2data.replace("F", "0");
            LogUtils.i( "track2data ------- >= 37---------: " + strTrack2data);
            strTrack2dataLen = "37";
        } else {
            strTrack2dataLen = String.valueOf(track2DataLen);
        }

        int tract2dataRPad = strTrack2data.length() % 2;
        LogUtils.i( "strTrack2data.length() % 2: " + tract2dataRPad);
        if (tract2dataRPad != 0) {
            strTrack2data = StringUtils.rightPad(strTrack2data, strTrack2data.length() + tract2dataRPad, "0");
        }
        byte[] track2data = AFUtils.hexString2ByteArray(strTrack2dataLen + strTrack2data);

        LogUtils.i( "track2data----------------: " + AFUtils.showResult16Str(track2data));
        //35域 二磁道数据
        field[34].ishave = 1;
        field[34].len = track2data.length;
        field[34].type = 0;
        field[34].data = track2data;
        LogUtils.i( " 二磁道数据 0X: " + AFUtils.showResult0xStr(field[34].data) + "\n");

        //41域，终端号 100004088325
        byte[] field_41_terminalNumber = AFUtils.StringToByteArray(posNum);
        field[40].ishave = 1;
        field[40].len = field_41_terminalNumber.length;
        field[40].data = field_41_terminalNumber;
        LogUtils.i( " 终端号 0X: " + AFUtils.showResult0xStr(field[40].data) + "\n");

        //42域，商户号 100000000103419
        byte[] field_42_BusinesNumber = AFUtils.StringToByteArray(manNum);
        field[41].ishave = 1;
        field[41].len = field_42_BusinesNumber.length;
        field[41].data = field_42_BusinesNumber;
        LogUtils.i( " 商户号 0X: " + AFUtils.showResult0xStr(field[41].data) + "\n");


        //49域，交易货币代码
        String strTerminalCountryCode = saleDataModel.getTerminalCountryCode();
        LogUtils.i( " 交易货币代码 0X: " + strTerminalCountryCode + "\n");

        String strFmtCountryCode = String.valueOf(Integer.valueOf(strTerminalCountryCode));
        byte[] terminalCountryCode = AFUtils.StringToByteArray(strFmtCountryCode);
        field[48].ishave = 1;
        field[48].len = terminalCountryCode.length;
        field[48].data = terminalCountryCode;
        LogUtils.i( " 交易货币代码 0X: " + AFUtils.showResult0xStr(field[48].data) + "\n");


        switch (cardType) {
            case 0:
            case 1:
                //55域
                String strIcData = saleDataModel.getIcData();
                String strIcDataLen = StringUtils.leftPad(String.valueOf(strIcData.length() / 2), 4, "0");
                byte[] icData = AFUtils.hexString2ByteArray(strIcDataLen + strIcData);
//        byte[] icData = AFUtils.hexString2ByteArray(strIcData);
                field[54].ishave = 1;
                field[54].len = icData.length;
                field[54].type = 0;
                field[54].data = icData;
                LogUtils.i( " 55域 0X: " + AFUtils.showResult0xStr(field[54].data) + "\n");
                LogUtils.i( " 55域 Len 0X: " + field[54].len + "\n");
                break;
            case 2:
                byte[] mscIcData = AFUtils.hexString2ByteArray("0000");
                field[54].ishave = 1;
                field[54].len = mscIcData.length;
                field[54].type = 0;
                field[54].data = mscIcData;
                LogUtils.i( " 55域 0X: " + AFUtils.showResult0xStr(field[54].data) + "\n");
                LogUtils.i( " 55域 Len 0X: " + field[54].len + "\n");
                break;
        }
       /* 59域
        0081
        4131 303030
        4132 303531303130303230323032303236303030303530303232303036373039393930303030303136363830353030383230303833303033
        4133 303030
        4134 303030
        4135 3030355030393236
        //解析
        A1 000
        A2 051
         01 002 02
         02 026 00005002200670999000001668
         05 008 20083003
        A3 000
        A4 000
        A5 005 P0926
        */
        StringBuffer sbField59Data = new StringBuffer();
        String strA1 = AFUtils.field59DataTLV("A1", null, false);
        sbField59Data.append(strA1);
        //A2子域处理
        String strA2_01 = AFUtils.field59DataTLV("01", "02", false);
        String strA2_02 = AFUtils.field59DataTLV("02", devicesTUSN, false);
        //加密随机因子，2域卡号后六位
        String encryRandom = saleDataModel.getCardNo().replace("F", "");
        String strA2_03 = AFUtils.field59DataTLV("03", encryRandom.substring(encryRandom.length() - 6), false);
        String strA2_04 = AFUtils.field59DataTLV("04", "4DD328BF", false);
        String strA2_05 = AFUtils.field59DataTLV("05", "23042002", false);

        String strA2 = strA2_01 + strA2_02 + strA2_03 + strA2_04 + strA2_05;
        String strA2Len = AFUtils.showResult16Str(AFUtils.StringToByteArray(String.valueOf(strA2.length() / 2)));
        String fmtStrA2 = "4132" + StringUtils.leftPad(strA2Len, 6, "30") + strA2;
        LogUtils.i( "fmtStrA2: " + fmtStrA2 + "\n");
        sbField59Data.append(fmtStrA2);

        String strA3 = AFUtils.field59DataTLV("A3", null, false);
        LogUtils.i( "strA3: " + strA3 + "\n");
        sbField59Data.append(strA3);
        String strA4 = AFUtils.field59DataTLV("A4", null, false);
        LogUtils.i( "strA4: " + strA4 + "\n");
        sbField59Data.append(strA4);
        String strA5 = AFUtils.field59DataTLV("A5", "P3099", false);
        LogUtils.i( "strA5: " + strA5 + "\n");
        sbField59Data.append(strA5);

        String fmtField59Len = StringUtils.leftPad(String.valueOf(sbField59Data.length() / 2), 4, "0");

        LogUtils.i( "field59 Data: " + fmtField59Len + sbField59Data + "\n");


        byte[] field59Byte = AFUtils.hexString2ByteArray(fmtField59Len + sbField59Data);
        field[58].ishave = 1;
        field[58].len = field59Byte.length;
        field[58].data = field59Byte;
        field[58].type = 0;

        LogUtils.i( " field59 0X: " + AFUtils.showResult0xStr(field[58].data) + "\n");


        //60域
        field[59].ishave = 1;
        field[59].len = 0x14;
        field[59].data = new byte[7];

        //交易类型
        field[59].data[0] = 0x22;
        //操作员号
        field[59].data[1] = 0x00;
        field[59].data[2] = 0x10;
        //xxx
        field[59].data[3] = 0x01;
        field[59].data[4] = 0x00;
        //arraycopy(piciNum, 0, field[59].data, 3, 2);
        //LogUtil.i(TAG, " 批次号: " + AFUtils.showResult0xStr(piciNum) + "\n");
        field[59].data[5] = 0x10;
        field[59].data[6] = 0x00;

        byte[] field61Byte = AFUtils.hexString2ByteArray("0167000001000000008000000032000000070800031C42404EC19C23201353EBFF0260551720313C10319E7C71A665BE144443D97FA4F4EA461EFF0233922F0078A64C38A77F28B8C038293C74856AEBF333260EEA6AF240F58012BAA080FF02107395938D44E6327C26287CD7B046843F411D4DC9D356D54820B71680FF02CBAB71E81BCB7EB00589F13880FF029B934EE71B27CA2FF714FF02FD0AF9CE9DBF755047FF024F80FF02");
        field[61].ishave = 1;
        field[61].len = field61Byte.length;
        field[61].data = field61Byte;
        field[61].type = 0;

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        //MAC，64域
        field[63].ishave = 1;
        field[63].len = 0x08;
        field[63].data = new byte[8];
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一*/
        spTransUtil.setTransSerialNumber();
    }

    /**
     * 计算17域 BF42（电子签名交易上送的交易特征码）
     *
     * @param batchNumber  批次号
     * @param serialNumber 流水号
     * @return
     */
    private String getTransactionCode(String batchNumber, String serialNumber) {
        LogUtils.i( "计算17域交易特征码: " + String.format("批次号:%s \t 流水号:%s\t", batchNumber, serialNumber));
        /*
        批次号前6位+流水号前2位转换bcd码
              与
        流水号后4位+0000转换bcd码
            异或
        转成ASC11放到17域*/
//        String batchNumber = "001001";
//        String serialNumber = "000320";
        int serialNumberLen = serialNumber.length();
        byte[] str1 = AFUtils.StrToBCDBytes(batchNumber + serialNumber.substring(0, 2));
        LogUtils.i( "str1: " + AFUtils.showResult16Str(str1));
        byte[] str2 = AFUtils.StrToBCDBytes(StringUtils.rightPad(serialNumber.substring(serialNumberLen - 4, serialNumberLen), 8, "0"));
        LogUtils.i( "str2: " + AFUtils.showResult16Str(str2));
        byte[] xorResult = HexUtil.xor(str1, str2);
        LogUtils.i( "xorResult: " + AFUtils.showResult16Str(xorResult));
        byte[] xorResultHex = AFUtils.StringToByteArray(AFUtils.showResult16Str(xorResult));
        LogUtils.i( "xorResultHex: " + AFUtils.showResult16Str(xorResultHex));
        return AFUtils.showResult16Str(xorResultHex);
    }

    /**
     * 8583消费的响应报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public Map<String, String> ansframe8583Sale(byte[] rxbuf, int rxlen) {
        //获取交易结果成功，更新流水号,在组包完成后完成更新流水号，暂时屏蔽
        //spTransUtil.setTransSerialNumber();
        LogUtils.i( "8583消费的响应报文解析: " + AFUtils.showResult16Str(rxbuf));
        Map<String, String> resMap = new HashMap<>();
        int ret = 0;
        ret = ans8583FieldsX(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            LogUtils.d( "<-Er 解析失败！");
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_ERROR);
            return resMap;
        }
        LogUtils.d( "解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x02) || (pack.msgType[1] != 0x10)) {
            LogUtils.d( "消息类型错！");
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_MSG_TYPE_ERROR);
            return resMap;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( "应答码不正确！");
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            resMap.put(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP, strTransResult);
            return resMap;
        }

        byte[] transResult = fieldsRecv[43].data;
        LogUtils.d( "交易结果:" + AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult)));

        resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_SUCCESS);
        LogUtils.i( "ansframe8583Sale resMap size()1:\t" + resMap.size());
        /*
        【17】:0151
        BF50 52
        687474703A2F2F33392E39362E34382E3133393A383038392F6C646261636B73746167652F7369676E706F732F6765745369676E3F73657269616C3D313030303132393837363835267265736F7563653D30  电子签名
        BF04 08 3438323930303030 收单机构编号，下载并打印小票上
        BF03 08 3130303532343832 收单终端号，下载并打印小票上
        BF36 01 31 BF35 值为1时回传银行卡借贷记标志 00-借记卡 01-贷记卡
        BF02 0F 383239383234313536393930303543 收单商户号，下载并打印小票上
        BF01 08 D6D0B9FAD2F8D0D0 发卡行名称(中文)，下载并打印小票上
        BF40 08 B1B1BEA9C1AAB6AF 交易商户简称(无下传值时打印终端配置商户名称)*/
        //支付小票
        byte[] payReceipt = fieldsRecv[16].data;
        String strPayReceipt = AFUtils.showResult16Str(payReceipt);
        LogUtils.d( "17域数据:" + strPayReceipt);
        ArrayList<TLVModel> tList = AFUtils.praseTLVStrX(payReceipt);
        Map<String, String> tm = AFUtils.list2Map(tList, 2);
        Iterator<Map.Entry<String, String>> iterator = tm.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            resMap.put(entry.getKey(), AFUtils.hexGBK2String(entry.getValue()));
            //LogUtils.d( "key:" + entry.getKey() + "\tvalues:" + AFUtils.hexGBK2String(entry.getValue()));
        }


        String transDate = AFUtils.showResult16Str(fieldsRecv[12].data);
        String transTime = AFUtils.showResult16Str(fieldsRecv[11].data);
        resMap.put(AFConstant.TRANS_DATE_TIME, afSimpleDateFormat(transDate + transTime));


        byte[] voucherNumber = fieldsRecv[10].data;
        LogUtils.d( "凭证号:" + AFUtils.hexGBK2String(AFUtils.showResult16Str(voucherNumber)));
        resMap.put(AFConstant.TRANS_VOUCHER_NUMBER, AFUtils.hexGBK2String(AFUtils.showResult16Str(voucherNumber)));

        byte[] transRefNumber = fieldsRecv[36].data;
        LogUtils.d( "交易参考号:" + AFUtils.hexGBK2String(AFUtils.showResult16Str(transRefNumber)));
        resMap.put(AFConstant.TRANS_REF_NUMBER, AFUtils.hexGBK2String(AFUtils.showResult16Str(transRefNumber)));

        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            //return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            //return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            //return 6;
        }
        LogUtils.i( "ansframe8583Sale resMap size()2:\t" + resMap.size());
        return resMap;
    }

    /**
     * 格式化服务器响应后的交易时间
     *
     * @param dateTime 交易日期+交易时间
     * @return 打印显示的交易时间
     */
    private static String afSimpleDateFormat(String dateTime) {
        try {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy");
            String tempDate = simpleDateFormat.format(new Date());
            Date date = new SimpleDateFormat("yyyyMMddHHmmss").parse(tempDate + dateTime);
            simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            return simpleDateFormat.format(date);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
    }

    /**
     * 扫码支付报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @param platformType 平台类型
     *                     1:银联二维码主扫
     *                     2:银联二维码被扫
     *                     3:支付宝主扫
     *                     4:支付宝被扫
     *                     5:微信主扫
     *                     6:微信被扫
     * @return
     */
    public Map<String, String> ansframe8583Qrcode(byte[] rxbuf, int rxlen, int platformType) {
        //获取交易结果成功，更新流水号,在组包完成后完成更新流水号，暂时屏蔽
        //spTransUtil.setTransSerialNumber();
        LogUtils.i( " 平台类型：" + AFUtils.getPlatformType(platformType) + "->>\t响应报文解析: " + AFUtils.showResult16Str(rxbuf));
        Map<String, String> resMap = new HashMap<>();
        resMap.clear();
        int ret = 0;
        ret = ans8583FieldsX(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            LogUtils.d( "<-Er 解析失败！");
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_ERROR);
            return resMap;
        }
        String msgType = AFUtils.showResult16Str(new byte[]{pack.msgType[0], pack.msgType[1]});
        String tempCurrentMsgType = null;
        switch (platformType) {
            case 1:
                tempCurrentMsgType = "9310";
                break;
            case 3:
                //支付宝主扫下单接口，
                tempCurrentMsgType = "9210";
                break;
            case 6:
                tempCurrentMsgType = "9110";
                break;
        }
        //消息类型判断
        if (!tempCurrentMsgType.equals(msgType)) {
            LogUtils.d( "消息类型错！");
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_MSG_TYPE_ERROR);
            return resMap;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( "应答码不正确！");
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR);
            byte[] transResult = fieldsRecv[43].data;
            String strTransResult = AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult));
            LogUtils.d( "交易结果:" + strTransResult);
            resMap.put(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP, strTransResult);
            return resMap;
        }

        byte[] transResult = fieldsRecv[43].data;
        LogUtils.d( "交易结果:" + AFUtils.hexGBK2String(AFUtils.showResult16Str(transResult)));

        resMap.put(AFConstant.TRADE_MSG_TAG, AFConstant.TRADE_VALUE_SUCCESS);

        //支付小票
        byte[] payReceipt = fieldsRecv[16].data;
        String strPayReceipt = AFUtils.showResult16Str(payReceipt);
        LogUtils.d( "17域数据:" + strPayReceipt);
        ArrayList<TLVModel> tList = AFUtils.praseTLVStrX(payReceipt);
        Map<String, String> tm = AFUtils.list2Map(tList, 2);
        Iterator<Map.Entry<String, String>> iterator = tm.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, String> entry = iterator.next();
            resMap.put(entry.getKey(), AFUtils.hexGBK2String(entry.getValue()));
            LogUtils.d( "key:" + entry.getKey() + "\tvalues:" + AFUtils.hexGBK2String(entry.getValue()));
        }

        //交易处理码
        byte[] processingCode = fieldsRecv[2].data;
        String tempProcessingCode = AFUtils.showResult16Str(processingCode);
        LogUtils.d( "交易处理码:" + tempProcessingCode);

        //查询类
        if (!"910003".equals(tempProcessingCode)) {
            //查询交易结果接口
            PayQRCodeQueryEntity payQRCodeQuery = new PayQRCodeQueryEntity();
            //BF08	values:102755595839
            payQRCodeQuery.setOrderNumber(resMap.get("BF08"));
            //4域交易金额
            byte[] tempAmount = fieldsRecv[3].data;
            payQRCodeQuery.setAmount(AFUtils.showResult16Str(tempAmount));
            //11域交易流水号
            byte[] tempSNumber = fieldsRecv[10].data;
            payQRCodeQuery.setSerialNumber(AFUtils.showResult16Str(tempSNumber));
            //12域受卡方所在地时间
            byte[] tempTransTime = fieldsRecv[11].data;
            payQRCodeQuery.setTransTime(AFUtils.showResult16Str(tempTransTime));
            //13域受卡方所在地日期
            byte[] tempTransDate = fieldsRecv[12].data;
            payQRCodeQuery.setTransDate(AFUtils.showResult16Str(tempTransDate));

            String payQRCodeQuery2JSON = GsonUtils.toJson(payQRCodeQuery);
            LogUtils.i( "payQRCodeQuery2JSON:\t " + payQRCodeQuery2JSON);
            resMap.put(AFConstant.RESPONSE_PAY_QRCODE_QUERY, payQRCodeQuery2JSON);
        }

        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            //return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            //return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            //return 6;
        }
        return resMap;
    }


    /**
     * 银联二维码组包
     *
     * @param money
     * @param field
     * @param tx
     * @param type  平台类型
     *              1:银联二维码主扫
     *              2:银联二维码被扫
     *              3:支付宝主扫
     *              4:支付宝被扫
     *              5:微信主扫
     *              6:微信被扫
     */
    public void frame8583Qrcode(String money, __8583Fields[] field, Pack tx, int platformType) {
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "frame8583Qrcode:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;
        LogUtils.i( "frame8583Qrcode: " + AFUtils.showResult16Str(additionalHeadByte));

        init8583Fields(fieldsSend);


        //消息类型
        switch (platformType) {
            case 1:
            case 2:
                tx.msgType[0] = (byte) 0x93;
                tx.msgType[1] = 0x00;
                break;
            case 3:
            case 4:
                tx.msgType[0] = (byte) 0x92;
                tx.msgType[1] = 0x00;
                break;
            case 5:
                break;
            case 6:
                break;
        }

        tx.bitMap = new byte[]{0x31, 0x20, (byte) 0xC0, 0x00, 0x00, (byte) 0xC8, (byte) 0x80, 0x31};
        byte[] transCode = new byte[3];
        switch (platformType) {
            case 1:
            case 2:
            case 3:
                transCode = AFUtils.hexString2ByteArray("900820");
                break;
            case 4:
                transCode = AFUtils.hexString2ByteArray("910000");
                break;

        }
        //3域 交易处理码
        field[2].ishave = 1;
        field[2].len = transCode.length;
        field[2].data = transCode;

        //4域交易金额
        String amount = AFUtils.dealMoneyStr(money);
        field[3].ishave = 1;
        field[3].len = 6;
        field[3].data = AFUtils.hexString2ByteArray(amount);
        LogUtils.i( "amount: " + AFUtils.showResult0xStr(field[3].data) + "\n");

        //终端坐标
        field[7].ishave = 1;
        field[7].type = 1;
        field[7].len = 3;
        field[7].data = hexStringToBytes("302C30");

        //11域，pos终端交易流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tempSerialNumber = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tempSerialNumber);
        LogUtils.i( "流水号 0X: " + AFUtils.showResult0xStr(field[10].data) + "\n");

        StringBuffer sbField17Data = new StringBuffer();
        //BF07 12 323838353731303037333932373334363137 [X] ->288571007392734617
        switch (platformType) {
            case 4:
                //支付宝被扫支付，授权码
                String alipayAuthorizationCode = AFUtils.packSendTLV("BF07", "413137");
                break;
        }
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);
        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);

        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        byte[] addType = new byte[2];
        //业务类型
        switch (platformType) {
            case 1:
                //银联二维码支付-主扫模式下单
                addType = AFUtils.hexString2ByteArray("3605");
                break;
            case 2:
                // 订单支付银联二维码被扫(POS 扫手机)
                addType = AFUtils.hexString2ByteArray("3203");
                break;
            case 3:
                addType = AFUtils.hexString2ByteArray("3505");
                break;
        }
        /*0:银联二维码主扫
        1：银联二维码被扫*/
        field[17].ishave = 1;
        field[17].len = 2;
        field[17].type = 0;
        field[17].data = addType;

        //41域，终端号 100004088325
        byte[] field_41_terminalNumber = AFUtils.StringToByteArray(posNum);
        field[40].ishave = 1;
        field[40].len = field_41_terminalNumber.length;
        field[40].data = field_41_terminalNumber;
        LogUtils.i( " 终端号 0X: " + AFUtils.showResult0xStr(field[40].data) + "\n");

        //42域，商户号 100000000103419
        byte[] field_42_BusinesNumber = AFUtils.StringToByteArray(manNum);
        field[41].ishave = 1;
        field[41].len = field_42_BusinesNumber.length;
        field[41].data = field_42_BusinesNumber;
        LogUtils.i( " 商户号 0X: " + AFUtils.showResult0xStr(field[41].data) + "\n");


        //附加信息 -》购买产品描述信息若无则传”Goods
        field[44].ishave = 1;
        field[44].type = 2;
        field[44].len = 5;
        field[44].data = hexStringToBytes("476F6F6473");


        //49域，交易货币代码
        String strTerminalCountryCode = "156";
        LogUtils.i( " 交易货币代码 0X: " + strTerminalCountryCode + "\n");

        String strFmtCountryCode = String.valueOf(Integer.valueOf(strTerminalCountryCode));
        byte[] terminalCountryCode = AFUtils.StringToByteArray(strFmtCountryCode);
        field[48].ishave = 1;
        field[48].len = terminalCountryCode.length;
        field[48].data = terminalCountryCode;
        LogUtils.i( " 交易货币代码 0X: " + AFUtils.showResult0xStr(field[48].data) + "\n");

        StringBuffer sbField59Data = new StringBuffer();
        String strA1 = AFUtils.field59DataTLV("A1", null, false);
        sbField59Data.append(strA1);
        //A2子域处理
        String strA2_01 = AFUtils.field59DataTLV("01", "02", false);
        String strA2_02 = AFUtils.field59DataTLV("02", devicesTUSN, false);
        String strA2_03 = AFUtils.field59DataTLV("03", "000000", false);
        String strA2_04 = AFUtils.field59DataTLV("04", "4DD328BF", false);
        String strA2_05 = AFUtils.field59DataTLV("05", "23042002", false);

        String strA2 = strA2_01 + strA2_02 + strA2_03 + strA2_04 + strA2_05;
        String strA2Len = AFUtils.showResult16Str(AFUtils.StringToByteArray(String.valueOf(strA2.length() / 2)));
        String fmtStrA2 = "4132" + StringUtils.leftPad(strA2Len, 6, "30") + strA2;
        LogUtils.i( "fmtStrA2: " + fmtStrA2 + "\n");
        sbField59Data.append(fmtStrA2);

        String strA3 = AFUtils.field59DataTLV("A3", null, false);
        LogUtils.i( "strA3: " + strA3 + "\n");
        sbField59Data.append(strA3);
        String strA4 = AFUtils.field59DataTLV("A4", null, false);
        LogUtils.i( "strA4: " + strA4 + "\n");
        sbField59Data.append(strA4);
        String strA5 = AFUtils.field59DataTLV("A5", "P3099", false);
        LogUtils.i( "strA5: " + strA5 + "\n");
        sbField59Data.append(strA5);

        String fmtField59Len = StringUtils.leftPad(String.valueOf(sbField59Data.length() / 2), 4, "0");

        LogUtils.i( "field59 Data: " + fmtField59Len + sbField59Data + "\n");


        byte[] field59Byte = AFUtils.hexString2ByteArray(fmtField59Len + sbField59Data);
        field[58].ishave = 1;
        field[58].len = field59Byte.length;
        field[58].data = field59Byte;
        field[58].type = 0;

        LogUtils.i( " field59 0X: " + AFUtils.showResult0xStr(field[58].data) + "\n");


        //60域
        field[59].ishave = 1;
        field[59].len = 0x14;
        field[59].data = new byte[7];

        //交易类型
        field[59].data[0] = 0x00;
        //操作员号
        field[59].data[1] = 0x00;
        field[59].data[2] = 0x10;
        //xxx
        field[59].data[3] = 0x01;
        field[59].data[4] = 0x00;
        //arraycopy(piciNum, 0, field[59].data, 3, 2);
        //LogUtil.i(TAG, " 批次号: " + AFUtils.showResult0xStr(piciNum) + "\n");
        field[59].data[5] = 0x10;
        field[59].data[6] = 0x00;

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        //MAC，64域
        field[63].ishave = 1;
        field[63].len = 0x08;
        field[63].data = new byte[8];
        pack8583Fields(field, tx);

        spTransUtil.setTransSerialNumber();

        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }


    /**
     * 二维码支付->用户被扫
     *
     * @param money
     * @param field
     * @param tx
     * @param platformType
     * @param AuthorizationCode 交易授权码
     */
    public void frame8583ScannedUserQRCode(String money, __8583Fields[] field, Pack tx, int platformType, String authorizationCode) {
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];
        LogUtils.i( "frame8583ScannedUserQRCode:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;
        LogUtils.i( "frame8583ScannedUserQRCode: " + AFUtils.showResult16Str(additionalHeadByte));

        init8583Fields(fieldsSend);


        //消息类型
        switch (platformType) {
            case 1:
            case 2:
                tx.msgType[0] = (byte) 0x93;
                tx.msgType[1] = 0x00;
                break;
            case 3:
            case 4:
                tx.msgType[0] = (byte) 0x92;
                tx.msgType[1] = 0x00;
                break;
            case 5:
            case 6:
                tx.msgType[0] = (byte) 0x91;
                tx.msgType[1] = 0x00;
                break;
        }

        tx.bitMap = new byte[]{0x31, 0x20, (byte) 0xC0, 0x00, 0x00, (byte) 0xC8, (byte) 0x80, 0x31};
        byte[] transCode = new byte[3];
        switch (platformType) {
            case 1:
            case 2:
            case 3:
                transCode = AFUtils.hexString2ByteArray("900820");
                break;
            case 4:
            case 6:
                transCode = AFUtils.hexString2ByteArray("910000");
                break;
        }
        //3域 交易处理码
        field[2].ishave = 1;
        field[2].len = transCode.length;
        field[2].data = transCode;

        //4域交易金额
        String amount = AFUtils.dealMoneyStr(money);
        field[3].ishave = 1;
        field[3].len = 6;
        field[3].data = AFUtils.hexString2ByteArray(amount);
        LogUtils.i( "amount: " + AFUtils.showResult0xStr(field[3].data) + "\n");

        //终端坐标
        field[7].ishave = 1;
        field[7].type = 1;
        field[7].len = 3;
        field[7].data = hexStringToBytes("302C30");

        //11域，pos终端交易流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tempSerialNumber = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tempSerialNumber);
        LogUtils.i( "流水号 0X: " + AFUtils.showResult0xStr(field[10].data) + "\n");

        StringBuffer sbField17Data = new StringBuffer();
        //BF07 12 323838353731303037333932373334363137 [X] ->288571007392734617
        switch (platformType) {
            case 4:
            case 6:
                //支付宝被扫支付，授权码
                String alipayAuthorizationCode = AFUtils.packSendTLV("BF07", AFUtils.showResult16Str(AFUtils.StringToByteArray(authorizationCode)));
                sbField17Data.append(alipayAuthorizationCode);
                break;
        }
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);
        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323332");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);

        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        byte[] addType = new byte[2];
        //业务类型
        switch (platformType) {
            case 1:
                //银联二维码支付-主扫模式下单
                addType = AFUtils.hexString2ByteArray("3605");
                break;
            case 2:
                // 订单支付银联二维码被扫(POS 扫手机)
                addType = AFUtils.hexString2ByteArray("3203");
                break;
            case 3:
                addType = AFUtils.hexString2ByteArray("3505");
                break;
            case 4:
                addType = AFUtils.hexString2ByteArray("3501");
                break;
            case 6:
                addType = AFUtils.hexString2ByteArray("3301");
                break;
        }
        /*0:银联二维码主扫
        1：银联二维码被扫*/
        field[17].ishave = 1;
        field[17].len = 2;
        field[17].type = 0;
        field[17].data = addType;

        //41域，终端号 100004088325
        byte[] field_41_terminalNumber = AFUtils.StringToByteArray(posNum);
        field[40].ishave = 1;
        field[40].len = field_41_terminalNumber.length;
        field[40].data = field_41_terminalNumber;
        LogUtils.i( " 终端号 0X: " + AFUtils.showResult0xStr(field[40].data) + "\n");

        //42域，商户号 100000000103419
        byte[] field_42_BusinesNumber = AFUtils.StringToByteArray(manNum);
        field[41].ishave = 1;
        field[41].len = field_42_BusinesNumber.length;
        field[41].data = field_42_BusinesNumber;
        LogUtils.i( " 商户号 0X: " + AFUtils.showResult0xStr(field[41].data) + "\n");


        //附加信息 -》购买产品描述信息若无则传”Goods
        field[44].ishave = 1;
        field[44].type = 2;
        field[44].len = 5;
        field[44].data = hexStringToBytes("476F6F6473");


        //49域，交易货币代码
        String strTerminalCountryCode = "156";
        LogUtils.i( " 交易货币代码 0X: " + strTerminalCountryCode + "\n");

        String strFmtCountryCode = String.valueOf(Integer.valueOf(strTerminalCountryCode));
        byte[] terminalCountryCode = AFUtils.StringToByteArray(strFmtCountryCode);
        field[48].ishave = 1;
        field[48].len = terminalCountryCode.length;
        field[48].data = terminalCountryCode;
        LogUtils.i( " 交易货币代码 0X: " + AFUtils.showResult0xStr(field[48].data) + "\n");

        StringBuffer sbField59Data = new StringBuffer();
        String strA1 = AFUtils.field59DataTLV("A1", null, false);
        sbField59Data.append(strA1);
        //A2子域处理
        String strA2_01 = AFUtils.field59DataTLV("01", "02", false);
        String strA2_02 = AFUtils.field59DataTLV("02", devicesTUSN, false);
        String strA2_03 = AFUtils.field59DataTLV("03", "000000", false);
        String strA2_04 = AFUtils.field59DataTLV("04", "4DD328BF", false);
        String strA2_05 = AFUtils.field59DataTLV("05", "23042002", false);

        String strA2 = strA2_01 + strA2_02 + strA2_03 + strA2_04 + strA2_05;
        String strA2Len = AFUtils.showResult16Str(AFUtils.StringToByteArray(String.valueOf(strA2.length() / 2)));
        String fmtStrA2 = "4132" + StringUtils.leftPad(strA2Len, 6, "30") + strA2;
        LogUtils.i( "fmtStrA2: " + fmtStrA2 + "\n");
        sbField59Data.append(fmtStrA2);

        String strA3 = AFUtils.field59DataTLV("A3", null, false);
        LogUtils.i( "strA3: " + strA3 + "\n");
        sbField59Data.append(strA3);
        String strA4 = AFUtils.field59DataTLV("A4", null, false);
        LogUtils.i( "strA4: " + strA4 + "\n");
        sbField59Data.append(strA4);
        String strA5 = AFUtils.field59DataTLV("A5", "P3099", false);
        LogUtils.i( "strA5: " + strA5 + "\n");
        sbField59Data.append(strA5);

        String fmtField59Len = StringUtils.leftPad(String.valueOf(sbField59Data.length() / 2), 4, "0");

        LogUtils.i( "field59 Data: " + fmtField59Len + sbField59Data + "\n");


        byte[] field59Byte = AFUtils.hexString2ByteArray(fmtField59Len + sbField59Data);
        field[58].ishave = 1;
        field[58].len = field59Byte.length;
        field[58].data = field59Byte;
        field[58].type = 0;

        LogUtils.i( " field59 0X: " + AFUtils.showResult0xStr(field[58].data) + "\n");


        //60域
        field[59].ishave = 1;
        field[59].len = 0x14;
        field[59].data = new byte[7];

        //交易类型
        field[59].data[0] = 0x00;
        //操作员号
        field[59].data[1] = 0x00;
        field[59].data[2] = 0x10;
        //xxx
        field[59].data[3] = 0x01;
        field[59].data[4] = 0x00;
        //arraycopy(piciNum, 0, field[59].data, 3, 2);
        //LogUtil.i(TAG, " 批次号: " + AFUtils.showResult0xStr(piciNum) + "\n");
        field[59].data[5] = 0x10;
        field[59].data[6] = 0x00;

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        //MAC，64域
        field[63].ishave = 1;
        field[63].len = 0x08;
        field[63].data = new byte[8];
        pack8583Fields(field, tx);
        spTransUtil.setTransSerialNumber();

        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }


    /**
     * 扫码交易查询
     *
     * @param payQRCodeQuery
     * @param field
     * @param tx
     * @param type
     */
    public void frame8583QrcodeResultQuery(PayQRCodeQueryEntity payQRCodeQuery, __8583Fields[] field, Pack tx, int platformType) {
        byte[] terminalNumber = AFUtils.StringToByteArray(posNum);
        byte[] data8583Len = new byte[]{0x00, 0x00};
        byte[] reservedValue = new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

        byte[] additionalHeadByte = new byte[terminalNumber.length + data8583Len.length + reservedValue.length];

        LogUtils.i( "frame8583QrcodeResultQuery:" + additionalHeadByte.length);

        System.arraycopy(terminalNumber, 0, additionalHeadByte, 0, terminalNumber.length);
        System.arraycopy(data8583Len, 0, additionalHeadByte, terminalNumber.length, data8583Len.length);
        System.arraycopy(reservedValue, 0, additionalHeadByte, data8583Len.length + terminalNumber.length, reservedValue.length);
        tx.additionalHead = additionalHeadByte;
        LogUtils.i( "frame8583QrcodeResultQuery: " + AFUtils.showResult16Str(additionalHeadByte));

        init8583Fields(fieldsSend);
        switch (platformType) {
            case 1:
            case 2:
                //消息类型
                tx.msgType[0] = (byte) 0x93;
                tx.msgType[1] = 0x00;
                break;
            case 3:
                //消息类型
                tx.msgType[0] = (byte) 0x92;
                tx.msgType[1] = 0x00;
                break;
        }


        tx.bitMap = new byte[]{0x30, 0x20, (byte) 0xC0, 0x00, 0x00, (byte) 0xC8, (byte) 0x80, 0x19};
        //3域 交易处理码
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{(byte) 0x91, 0x00, 0x03};

        //4域交易金额
        String amount = payQRCodeQuery.getAmount();
        field[3].ishave = 1;
        field[3].len = 6;
        field[3].data = AFUtils.hexString2ByteArray(amount);
        LogUtils.i( "amount: " + AFUtils.showResult0xStr(field[3].data) + "\n");


        //11域，pos终端交易流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tempSerialNumber = spTransUtil.getTransSerialNumber();
        field[10].data = hexStringToBytes(tempSerialNumber);
        LogUtils.i( "流水号 0X: " + AFUtils.showResult0xStr(field[10].data) + "\n");

        StringBuffer sbField17Data = new StringBuffer();
        String mfNumber = AFUtils.packSendTLV("BF16", "413137");
        sbField17Data.append(mfNumber);
        String deviceModel = AFUtils.packSendTLV("BF17", "41463638");
        sbField17Data.append(deviceModel);
        String appVersionNumber = AFUtils.packSendTLV("BF19", "32303233303432303032");
        sbField17Data.append(appVersionNumber);
        //设备SN
        String deviceSNSerialNumber = AFUtils.packSendTLV("BF1A", AFUtils.showResult16Str(AFUtils.StringToByteArray(devicesTUSN)));
        sbField17Data.append(deviceSNSerialNumber);
        String terminalType = AFUtils.packSendTLV("BF24", "3131");
        sbField17Data.append(terminalType);

        String baseStation = AFUtils.packSendTLV("BF34", "343532337C3436307C30307C313238363132323432");
        sbField17Data.append(baseStation);


        String identificationCode = AFUtils.packSendTLV("BF58", "30");
        sbField17Data.append(identificationCode);

        //终端通讯方式 1-2G 3-WIFI 4-4G
        String terminal_communication_method = AFUtils.packSendTLV("BF60", "34");
        sbField17Data.append(terminal_communication_method);

        String phone_sim = AFUtils.packSendTLV("BF61", "3839383630344132313932313830383438313531");
        sbField17Data.append(phone_sim);

        String phone_imei = AFUtils.packSendTLV("BF62", "383630373231303639333138383036");
        sbField17Data.append(phone_imei);

        String field17Data = sbField17Data.toString();

        int field17DataLen = field17Data.length() / 2;
        LogUtils.i( "field17DataLen: " + field17DataLen + "\n");

        //补0操作
        String fmtField17DataLen = StringUtils.leftPad(String.valueOf(field17DataLen), 4, "0");
        LogUtils.i( "fmtField17DataLen: " + fmtField17DataLen + "\n");

        byte[] currentField17Data = AFUtils.hexString2ByteArray(fmtField17DataLen + sbField17Data);

        //17域
        field[16].ishave = 1;
        field[16].type = 0;
        field[16].len = currentField17Data.length;
        field[16].data = currentField17Data;
        LogUtils.i( "field17Data 0X: " + AFUtils.showResult0xStr(field[16].data) + "\n");

        byte[] addType = new byte[2];
        //业务类型
        switch (platformType) {
            case 1:
                //银联二维码支付-主扫模式下单
                addType = AFUtils.hexString2ByteArray("3611");
                break;
            case 2:
                // 订单支付银联二维码主扫(POS 扫手机)
                addType = AFUtils.hexString2ByteArray("3203");
                break;
            case 3:
                //支付宝二维码支付-主扫模式下单
                addType = AFUtils.hexString2ByteArray("3511");
                break;
        }
        /*0:银联二维码主扫
        1：银联二维码被扫*/
        field[17].ishave = 1;
        field[17].len = 2;
        field[17].type = 0;
        field[17].data = addType;

        //41域，终端号 100004088325
        byte[] field_41_terminalNumber = AFUtils.StringToByteArray(posNum);
        field[40].ishave = 1;
        field[40].len = field_41_terminalNumber.length;
        field[40].data = field_41_terminalNumber;
        LogUtils.i( " 终端号 0X: " + AFUtils.showResult0xStr(field[40].data) + "\n");

        //42域，商户号 100000000103419
        byte[] field_42_BusinesNumber = AFUtils.StringToByteArray(manNum);
        field[41].ishave = 1;
        field[41].len = field_42_BusinesNumber.length;
        field[41].data = field_42_BusinesNumber;
        LogUtils.i( " 商户号 0X: " + AFUtils.showResult0xStr(field[41].data) + "\n");


        //附加信息 -》购买产品描述信息若无则传”Goods
        //00 12 31 30 32 37 33 38 38 39 35 30 37 33
        String tempOrderNumber = AFUtils.showResult16Str(AFUtils.StringToByteArray(payQRCodeQuery.getOrderNumber()));
        String tempOrderNumberLen = StringUtils.leftPad(String.valueOf(tempOrderNumber.length() / 2), 4, "0");
        byte[] field_44_data = AFUtils.hexString2ByteArray(tempOrderNumberLen + tempOrderNumber);
        field[44].ishave = 1;
        field[44].type = 0;
        field[44].len = field_44_data.length;
        field[44].data = field_44_data;


        //49域，交易货币代码
        String strTerminalCountryCode = "156";
        LogUtils.i( " 交易货币代码 0X: " + strTerminalCountryCode + "\n");

        String strFmtCountryCode = String.valueOf(Integer.valueOf(strTerminalCountryCode));
        byte[] terminalCountryCode = AFUtils.StringToByteArray(strFmtCountryCode);
        field[48].ishave = 1;
        field[48].len = terminalCountryCode.length;
        field[48].data = terminalCountryCode;
        LogUtils.i( " 交易货币代码 0X: " + AFUtils.showResult0xStr(field[48].data) + "\n");

        //60域
        field[59].ishave = 1;
        field[59].len = 0x14;
        field[59].data = new byte[7];

        //交易类型
        field[59].data[0] = 0x00;
        //操作员号
        field[59].data[1] = 0x00;
        field[59].data[2] = 0x10;
        //xxx
        field[59].data[3] = 0x01;
        field[59].data[4] = 0x00;
        //arraycopy(piciNum, 0, field[59].data, 3, 2);
        //LogUtil.i(TAG, " 批次号: " + AFUtils.showResult0xStr(piciNum) + "\n");
        field[59].data[5] = 0x10;
        field[59].data[6] = 0x00;

        //61域【61】:00 22 00 10 01 00 05 51 06 08 17 40 35
        String tempSNumber = payQRCodeQuery.getSerialNumber();
        String tempTransDate = payQRCodeQuery.getTransDate();
        String tempTransTime = payQRCodeQuery.getTransTime();
        byte[] temp61Data = AFUtils.hexString2ByteArray("0022001001" + tempSNumber + tempTransDate + tempTransTime);
        field[60].ishave = 1;
        field[60].type = 0;
        field[60].len = temp61Data.length;
        field[60].data = temp61Data;


        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        //MAC，64域
        field[63].ishave = 1;
        field[63].len = 0x08;
        field[63].data = new byte[8];
        pack8583Fields(field, tx);

        spTransUtil.setTransSerialNumber();

        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    public int ans8583Qrcode(byte[] rxbuf, int rxlen) {

        int ret = 0;
        ret = ans8583Fields(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        //LogUtils.d("解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x02) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            System.out.println("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( "应答码不正确！");
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            return 3;
        }
        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            return 6;
        }
        //成功
        return 0;
    }

    /**
     * 从传入参数的TLV格式的数据源中解析出55域需要的数据
     *
     * @param src   需要解析的数据源(TLV格式)
     * @param money 交易金额
     * @param date  交易日期 格式如:"180707",必须跟卡指令中传的金额和日期一致
     * @return
     */
    byte[] getFieldData55(byte[] src, int money, String date) {

        return null;

    }

    int intToHex(int d) {
        int out = 0;
        out = ((d / 10) << 4);
        out |= (d % 10);
        return out;
    }

    /**
     * 银联小额双免组包
     *
     * @param carddata 卡上数据
     * @param money    交易金额(单位分)
     * @param date     日期（长度为6） 格式如：180707
     * @param field
     * @param tx
     */
    public void frameShuangMian(byte[] carddata, int money, String date, __8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);
        if (date.length() != 6) {
            return;
        }
        byte[] field55 = getFieldData55(carddata, money, date);
        if (upBinNumber.equals("") || field35Data.equals("")) {
            return;
        }
        //消息类型
        tx.msgType[0] = 0x02;
        tx.msgType[1] = 0x00;
        //2域 银行卡号
        field[1].ishave = 1;
        String strtemp = String.format("%02d", upBinNumber.length());
        byte[] tmp = hexStringToBytes(strtemp);
        field[1].len = tmp[0];
        strtemp = upBinNumber;
        if (upBinNumber.length() % 2 != 0) {
            strtemp += "0";
        }
        field[1].data = hexStringToBytes(strtemp);

        //3域 交易处理码
        field[2].ishave = 1;
        field[2].len = 3;
        field[2].data = new byte[]{0x00, 0x00, 0x00};
        //4域 交易金额
        field[3].ishave = 1;
        field[3].len = 6;
        String strtmp = String.format("%012d", money);
        field[3].data = hexStringToBytes(strtmp);

        //11域，pos终端交易流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp1 = String.format("%06d", posSn);
        field[10].data = hexStringToBytes(tmp1);

        //14域，卡有效期
        if (cardValiteDate.equals("")) {
        } else {
            field[13].ishave = 1;
            field[13].len = 2;
            field[13].data = hexStringToBytes(cardValiteDate);
        }
        //22域
        field[21].ishave = 1;
        field[21].len = 2;
        field[21].data = new byte[]{0x07, 0x20};

        //23域卡片序列号
        if (cardSnNumber.equals("")) {
            field[22].ishave = 1;
            field[22].len = 2;
            field[22].data = new byte[]{0x00, 0x00};
        } else {
            field[22].ishave = 1;
            field[22].len = 2;
            field[22].data = new byte[]{0x00, 0x00};
            if (cardSnNumber.equals("01")) {
                field[22].data[1] = 0x01;
            }
        }
        // 25域
        field[24].ishave = 1;
        field[24].len = 1;
        field[24].data = new byte[]{0x00};
        //35域，二磁道数据
        field[34].ishave = 1;
        int tmplen = field35Data.length();
        if (field35Data.charAt(tmplen - 1) == 'f') {
            tmplen = intToHex(tmplen);//转为HEX
            field[34].len = tmplen - 1;
        } else {
            tmplen = intToHex(tmplen);//转为HEX
            field[34].len = tmplen;
        }
        field[34].data = hexStringToBytes(field35Data);

        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 8;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        //49域 交易货币代码
        field[48].ishave = 1;
        field[48].len = 3;
        field[48].data = new byte[]{0x31, 0x35, 0x36};

        //55域，卡上TLV数据组包
        field[54].ishave = 1;
        //field[54].len = 3;
        String tmplen1 = String.format("%04d", field55.length);
        byte[] tmpby = hexStringToBytes(tmplen1);
        field[54].len = (((tmpby[0] & 0xff) << 8) | (tmpby[1] & 0xff));
        field[54].data = field55.clone();

        //60域
        field[59].ishave = 1;
        field[59].len = 0x13;
        field[59].data = new byte[7];
        field[59].data[0] = 0x22;
        arraycopy(piciNum, 0, field[59].data, 1, 3);
        field[59].data[4] = 0x00;
        field[59].data[5] = 0x06;
        field[59].data[6] = 0x00;
        //MAC，64域
        field[63].ishave = 1;
        field[63].len = 0x08;
        field[63].data = new byte[8];
        //这个域要求填MAC，只需按这样填，MAC的计算在pack8583Fields自动完成了
        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 双免收到的应答报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @return
     */
    public int ansShuangMian(byte[] rxbuf, int rxlen) {

        int ret = 0;
        ret = ans8583Fields(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        //LogUtils.d("解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x02) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            System.out.println("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( "应答码不正确！");
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            return 3;
        }
        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            return 6;
        }
        //成功
        return 0;
    }

    /**
     * 批结算组包，当后台报交易流水重复，响应码94时，批结算后允许交易流水从一开始
     *
     * @param field
     * @param tx
     */
    public void frame8583Batch(__8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);
        //消息类型
        tx.msgType[0] = 0x05;
        tx.msgType[1] = 0x00;

        //11域，通信流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = String.format("%06d", commSn);
        field[10].data = hexStringToBytes(tmp);
        //
        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 8;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        //48域
        field[47].ishave = 1;
        field[47].len = 0x62;
        field[47].data = new byte[31];
        Arrays.fill(field[47].data, (byte) 0);
        //49域 交易货币代码
        field[48].ishave = 1;
        field[48].len = 3;
        field[48].data = new byte[]{0x31, 0x35, 0x36};

        //60域
        field[59].ishave = 1;
        field[59].len = 0x11;
        field[59].data = new byte[6];
        field[59].data[0] = 0x00;
        arraycopy(piciNum, 0, field[59].data, 1, 3);
        field[59].data[4] = 0x20;
        field[59].data[5] = 0x10;

        //63域
        field[62].ishave = 1;
        field[62].len = 0x23;
        field[62].data = new byte[23];
        Arrays.fill(field[62].data, (byte) 'X');
        field[62].data[0] = 0x01;
        field[62].data[1] = 0x01;
        field[62].data[2] = 0x01;
        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 8583批结算响应报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @return 0，成功 非0，失败
     */
    public int ans8583Batch(byte[] rxbuf, int rxlen) {

        int ret = 0;
        ret = ans8583Fields(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        //LogUtils.d("解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x05) || (pack.msgType[1] != 0x10)) {
            //LogUtils.d("消息类型错！");
            return 2;
        }

        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            return 6;
        }
        //批结算成功
        return 0;
    }

    /**
     * 批结算结束报文组帧
     *
     * @param field
     * @param tx
     */
    public void frame8583BatchOver(__8583Fields[] field, Pack tx) {

        init8583Fields(fieldsSend);
        //消息类型
        tx.msgType[0] = 0x03;
        tx.msgType[1] = 0x20;

        //11域，通信流水
        field[10].ishave = 1;
        field[10].len = 3;
        String tmp = String.format("%06d", commSn);
        field[10].data = hexStringToBytes(tmp);
        //
        //41域，终端号
        field[40].ishave = 1;
        field[40].len = 8;
        field[40].data = posNum.getBytes();
        //42域，商户号
        field[41].ishave = 1;
        field[41].len = 15;
        field[41].data = manNum.getBytes();

        //48域
        field[47].ishave = 1;
        field[47].len = 0x04;
        field[47].data = new byte[2];
        Arrays.fill(field[47].data, (byte) 0);

        //60域
        field[59].ishave = 1;
        field[59].len = 0x11;
        field[59].data = new byte[6];
        field[59].data[0] = 0x00;
        arraycopy(piciNum, 0, field[59].data, 1, 3);
        field[59].data[4] = 0x20;
        field[59].data[5] = 0x70;

        /*报文组帧，自动组织这些域到Pack的TxBuffer中*/
        pack8583Fields(field, tx);
        commSn++; //通讯流水每次加一
    }

    /**
     * 批结结束应答报文解析
     *
     * @param rxbuf
     * @param rxlen
     * @return
     */
    public int ans8583BatchOver(byte[] rxbuf, int rxlen) {

        int ret = 0;
        ret = ans8583Fields(rxbuf, rxlen, fieldsRecv);
        if (ret != 0) {
            //LogUtils.d("解析失败！");
            System.out.println("<-Er 解析失败！");
            return ret;
        }
        //LogUtils.d("解析成功！");
        System.out.println("->ok 解析成功！");
        //消息类型判断
        if ((pack.msgType[0] != 0x03) || (pack.msgType[1] != 0x30)) {
            LogUtils.d( "消息类型错！");
            //System.out.println("消息类型错！");
            return 2;
        }
        //应答码判断
        if ((fieldsRecv[38].data[0] != 0x30) || (fieldsRecv[38].data[1] != 0x30)) {
            LogUtils.d( "应答码不正确！");
            LogUtils.d( String.format("应答码:%02x%02x", fieldsRecv[38].data[0], fieldsRecv[38].data[1]));
            return 3;
        }
        //跟踪号比较
        if (!Arrays.equals(fieldsSend[10].data, fieldsRecv[10].data)) {
            return 4;
        }
        //终端号比较
        if (!Arrays.equals(fieldsSend[40].data, fieldsRecv[40].data)) {
            return 5;
        }
        //商户号比较
        if (!Arrays.equals(fieldsSend[41].data, fieldsRecv[41].data)) {
            return 6;
        }
        //成功
        return 0;
    }

    public static void setManNum(String manNum) {
        My8583Ans.manNum = manNum;
    }

    public static void setPosNum(String posNum) {
        My8583Ans.posNum = posNum;
    }

    public static void setMainKey(String mainKey) {
        My8583Ans.mainKey = mainKey;
    }

    public static void setTPDU(String TPDU) {
        My8583Ans.TPDU = TPDU;
    }

    public static String getManNum() {
        return manNum;
    }

    public static String getPosNum() {
        return posNum;
    }

    public static String getMainKey() {
        return mainKey;
    }

    public static String getTPDU() {
        return TPDU;
    }

    /**
     * 调用demo
     *
     * @param args
     */
    public static void main(String[] args) {
        My8583Ans myans = new My8583Ans();
        //签到组包
        myans.frame8583QD(myans.fieldsSend, myans.pack);
        //打印出待发送的报文
        byte[] send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        System.out.println("->send:");
        System.out.println(My8583Ans.bytesToHexString(send));
        System.out.println(myans.pack.toString());
        System.out.println(myans.getFields(myans.fieldsSend));
        //接收解析,假如收到的报文在recv中
        String recvstr = "007960000001386131003111080810003800010AC0001450021122130107200800085500323231333031343931333239303039393939393930363030313433303137303131393939390011000005190030004046F161A743497B32EAC760DF5EA57DF5900ECCE3977731A7EA402DDF0000000000000000CFF1592A";
        System.out.println("->recv:" + recvstr);
        byte[] recv = My8583Ans.hexStringToBytes(recvstr);
        // mypack.ans8583Fields(bt,bt.length,mypack.fieldsRecv);
        //解析
        System.out.println("开始解析...");
        int ret = myans.ans8583QD(recv, recv.length);
        if (ret == 0) {
            //打印出解析成功的各个域
            System.out.println("签到成功!");
            System.out.println(myans.getFields(myans.fieldsRecv));
        }

        //二维码组包
        System.out.println("->二维码交易 demo");
        String money = "0.01"; //1分
        myans.frame8583Qrcode(money, myans.fieldsSend, myans.pack, 1);
        //打印出待发送的报文
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        System.out.println("->send:");
        System.out.println(My8583Ans.bytesToHexString(send));
        System.out.println(myans.pack.toString());
        System.out.println(myans.getFields(myans.fieldsSend));

        //双免组包测试
        System.out.println("->银行卡双免交易 demo");
        String cardData = "9f370400000001776882027C009F3602004E57136212264200008234827D25062204329991635F9F101307010103A00000010A01000000000079B281999F2608DE242036EBF1B10B5F3401019F6C0220009F5D060000000000005F20142020202020202020202020202020202020202020";

        byte[] hexdata = hexStringToBytes(cardData);
        myans.frameShuangMian(hexdata, 1, "180707", myans.fieldsSend, myans.pack);

        //打印出待发送的报文
        send = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send, 0, myans.pack.txLen);
        System.out.println("->send:");
        System.out.println(My8583Ans.bytesToHexString(send));
        System.out.println(myans.pack.toString());
        System.out.println(myans.getFields(myans.fieldsSend));


        //批结算组包
        System.out.println("->批结算 demo");
        myans.frame8583Batch(myans.fieldsSend, myans.pack);
        //打印出待发送的报文
        byte[] send2 = new byte[myans.pack.txLen];
        arraycopy(myans.pack.txBuffer, 0, send2, 0, myans.pack.txLen);
        System.out.println("->send:");
        System.out.println(My8583Ans.bytesToHexString(send2));
        System.out.println(myans.pack.toString());
        System.out.println(myans.getFields(myans.fieldsSend));

        //mac算法测试
        String mackey = "313215FDA2C4AB4C";
        String data = "0118600501000061310031110802007024068020C08211196210810840003424940000000000000000000100002027060720000100376210810840003424940D27062207001000100F373337383232313438393834313133343133313030313431353601649F2608ED1D6BA78357FB259F2701809F101307010103A00000010A0100000000003B1637329F3704591D37169F360201C0950500000000009A031807119C01009F02060000000000015F2A02015682027C009F1A0201569F03060000000000009F3303E0E1C89F34030200009F3501229F1E0830303030303030308408A0000003330101019F0902008C9F4104000000209F6310303130353030303000000000000000000013220007570006004237313333423537";
        byte[] hexdt = hexStringToBytes(data);
        System.out.println("->mac check:");
        byte[] mac = myans.upGetMac(hexdt, 13, hexdt.length - 13 - 8, hexStringToBytes(mackey));
        String strmac = bytesToHexString(mac);
        System.out.println("->mac is:" + strmac);
    }


}
