package com.hjq.demo.ans8583;

import android.os.RemoteException;
import android.util.Log;

import com.hjq.demo.model.MainModelImpl;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.LogUtils;
import com.szanfu.sdk.api.pinpad.PosGetMacResult;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

import static com.hjq.demo.utils.DesUtil.DES_encrypt;
import static java.lang.System.arraycopy;

public class Easy8583Ans {

    private static final String TAG = " Easy8583Ans";
    private static String macKey = "da910898a794e0fe2a0bf090cc0bdab5"; //工作秘钥

    public static void setMacKey(String macKey) {
        Easy8583Ans.macKey = macKey;
    }

    public static String getMacKey() {
        return macKey;
    }

    /**
     * 定义8583的报文协议包结构 Len+Tpdu+Head+MsgType+BitMap+Data
     */
    public class Pack {

        public byte[] len;
        public byte[] tpdu;
        public byte[] head;
        //附加报文头32字节，固定值
        public byte[] additionalHead;
        public byte[] msgType;
        public byte[] bitMap;

        public byte[] txBuffer;
        public int txLen;

        public Pack() {
            len = new byte[2];
            tpdu = new byte[]{0x60, 0x05, 0x01, 0x00, 0x00};
            head = new byte[]{0x36, 0x30, 0x32, 0x30, 0x41, 0x30, 0x31, 0x30, 0x30, 0x30};//这几项一般固定，不会变动
            additionalHead = new byte[32];
            msgType = new byte[2];
            bitMap = new byte[8];
            txBuffer = new byte[1024];
            txLen = 0;
        }


        @Override
        public String toString() {
            return "Pack{" +
                    "len=" + bytesToHexString(len) +
                    ", tpdu=" + bytesToHexString(tpdu) +
                    ", head=" + bytesToHexString(head) +
                    ", additionalHead=" + bytesToHexString(additionalHead) +
                    ", msgType=" + bytesToHexString(msgType) +
                    ", bitMap=" + bytesToHexString(bitMap) +
                    ", txLen=" + txLen +
                    ", txBuffer=" + bytesToHexString(txBuffer) +
                    '}';
        }
    }

    /**
     * 域定义
     */
    public class __8583Fields {
        int ishave;   /*是否存在*/
        int type;    /*类型(取值范围0-2，组帧时0:表示不计长度 1:表示长度占1字节，2:表示2长度占2字节)*/
        int len;     /*域长度（需根据各个域的定义要求正确取值）*/
        byte[] data; /*域内容*/

        __8583Fields() {
            ishave = 0;
            type = 0;
            len = 0;
            data = null;
        }
    }

    public __8583Fields[] fieldsSend; //发送的域
    public __8583Fields[] fieldsRecv; //接收的域
    public Pack pack;

    public Easy8583Ans() {
        fieldsSend = new __8583Fields[64];
        fieldsRecv = new __8583Fields[64];
        pack = new Pack();
        init8583(fieldsSend);
        init8583(fieldsRecv);
        init8583Fields(fieldsSend);
        init8583Fields(fieldsRecv);
    }

    /**
     * 各个域的配置，初始化
     *
     * @param field
     */
    private void init8583(__8583Fields[] field) {
        for (int i = 0; i < 64; i++) {
            field[i] = new __8583Fields();
        }
    }

    public void init8583Fields(__8583Fields[] field) {

        for (int i = 0; i < 64; i++) {
            field[i].ishave = 0;
        }
        field[0].type = 0;
        field[1].type = 1;//LLVAR

        field[2].type = 0;
        field[2].len = 3;

        field[3].type = 0;
        field[3].len = 6;

        field[10].type = 0;
        field[10].len = 3;

        field[11].type = 0;
        field[11].len = 3;

        field[12].type = 0;
        field[12].len = 2;

        field[13].type = 0;
        field[13].len = 2;

        field[14].type = 0;
        field[14].len = 2;

        field[16].type = 2;

        field[17].type = 0;
        field[17].len = 2;

        field[21].type = 0;
        field[21].len = 2;
        field[22].type = 0;
        field[22].len = 2;

        field[24].type = 0;
        field[24].len = 1;
        field[25].type = 0;
        field[25].len = 1;

        field[31].type = 1;//LLVAR

        field[34].type = 1;//LLVAR

        field[36].type = 0;
        field[36].len = 12;

        field[37].type = 0;
        field[37].len = 6;
        field[38].type = 0;
        field[38].len = 2;

        field[39].type = 1;

        field[40].type = 0;
        field[40].len = 12;
        field[41].type = 0;
        field[41].len = 15;

        field[43].type = 1;

        field[44].type = 2;

        field[47].type = 2;
        field[48].type = 0;
        field[48].len = 3;
        field[51].type = 0;
        field[51].len = 8;
        field[52].type = 0;
        field[52].len = 8;

        field[54].type = 2;//LLLVAR
        field[58].type = 2;

        field[59].type = 2;
        field[60].type = 2;
        field[61].type = 2;
        field[62].type = 2;

        field[63].type = 0;
        field[63].len = 8;
    }

    /**
     * 该方法不需要外部调用，M该方法自动完成各个域的组包和Bitap的形成及报文长度的计算
     * 该方法最终组织各个域中的内容到 Pack的TxBuffer中，形成一完整报文
     *
     * @param field
     * @param pk
     */
    public void pack8583Fields(__8583Fields[] field, Pack pk) {
        LogUtils.i( "pk.bitMap\t:" + AFUtils.showResult16Str(pk.bitMap));
        int j = 0;
        int len = 59;
        int tmplen = 0;
        int seat = 0x80;

        Arrays.fill(pack.txBuffer, (byte) 0);

        for (int i = 0; i < 64; i++) {
            seat = (seat >> 1);
            if ((i % 8) == 0) {
                j++;
                seat = 0x80;
            }
            if (field[i].ishave == 1) {
                pk.bitMap[j - 1] |= seat;//根据每个filed中的ishave是否为1，自动计算BitMap
                if (field[i].type == 0) {
                    LogUtils.i( (i + 1) + "域 [type 0]\t\t 数据:" + AFUtils.showResult16Str(field[i].data));
                    //根据每个域的数据类型，自动截取长度组包
                    //System.out.println("i = "+i);
                    arraycopy(field[i].data, 0, pk.txBuffer, len, field[i].len);//数据
                    len += field[i].len;
                    LogUtils.i( (i + 1) + "域组包完成-->:" + AFUtils.showResult16Str(pk.txBuffer));
                } else if (field[i].type == 1) {
                    LogUtils.i( (i + 1) + "域 [type 1]\t\t 数据:" + AFUtils.showResult16Str(field[i].data));
                    //域长度
                    pk.txBuffer[len] = (byte) field[i].len;
                    tmplen = Integer.parseInt(String.format("%02x", pk.txBuffer[len]), 10);
                    LogUtils.i( (i + 1) + "Len -->:" + tmplen);
                    //域数据
                    if ((i == 1) || (i == 31) || (i == 34) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 1;
                    arraycopy(field[i].data, 0, pk.txBuffer, len, tmplen);//数据
                    len += tmplen;
                    LogUtils.i( (i + 1) + "域组包完成-->:" + AFUtils.showResult16Str(pk.txBuffer));
                } else if (field[i].type == 2) {
                    LogUtils.i( (i + 1) + "域 [type 2]\t\t 数据:" + AFUtils.showResult16Str(field[i].data));
                    pk.txBuffer[len] = (byte) (field[i].len >> 8);
                    LogUtils.i( (i + 1) + "域 [type Len 2]\t\t 数据:" + AFUtils.showResult16Str(new byte[]{pk.txBuffer[len]}));
                    pk.txBuffer[len + 1] = (byte) field[i].len;
                    LogUtils.i( (i + 1) + "域 [type Len+1 2]\t\t 数据:" + AFUtils.showResult16Str(new byte[]{pk.txBuffer[len + 1]}));

                    String temp = String.format("%02x%02x", pk.txBuffer[len], pk.txBuffer[len + 1]);
                    LogUtils.i( "temp: " + temp);
                    if (i == 54 || i == 44) {
                        tmplen = Integer.valueOf(temp, 16);
                    } else {
                        tmplen = Integer.parseInt(temp, 10);
                    }
                    LogUtils.i( (i + 1) + "域 [type Len+1 2]\t\t tmplen:" + tmplen);

                    if ((i == 1) || (i == 31) || (i == 34) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 2;
                    arraycopy(field[i].data, 0, pk.txBuffer, len, tmplen);//数据
                    len += tmplen;

                    LogUtils.i( (i + 1) + "域组包完成-->:" + AFUtils.showResult16Str(pk.txBuffer));

                }

            }
        }

        byte[] curr = pk.txBuffer;
        LogUtils.i( "xxxx->1:" + AFUtils.showResult16Str(curr));
        if (field[63].ishave == 1) {
            //len = len + 8;
            LogUtils.i( "存在64域长度增加8字节");
        } else {
            LogUtils.i( "不存在64域");
        }
        //64域+8字节特殊处理
        pk.txLen = len;
        LogUtils.i( "txLen:" + pk.txLen);
        pk.len[0] = (byte) (((len - 2) & 0xffff) >> 8);
        LogUtils.i( " pk.len[0]:" + AFUtils.showResult16Str(new byte[]{pk.len[0]}));
        pk.len[1] = (byte) (len - 2);
        LogUtils.i( " pk.len[1]:" + AFUtils.showResult16Str(new byte[]{pk.len[1]}));
        arraycopy(pk.len, 0, pk.txBuffer, 0, 2);

        LogUtils.i( "数据长度:" + AFUtils.showResult16Str(pk.txBuffer));
        arraycopy(pk.tpdu, 0, pk.txBuffer, 2, 5);
        arraycopy(pk.head, 0, pk.txBuffer, 7, 10);
        arraycopy(pk.additionalHead, 0, pk.txBuffer, 17, 32);
        arraycopy(pk.msgType, 0, pk.txBuffer, 49, 2);
        arraycopy(pk.bitMap, 0, pk.txBuffer, 51, 8);

       /* arraycopy(pk.msgType, 0, pk.txBuffer, 17, 2);
        arraycopy(pk.bitMap, 0, pk.txBuffer, 19, 8);*/

        byte[] additio = pk.additionalHead;
        LogUtils.i( "pack8583Fields: " + AFUtils.showResult16Str(additio));


        //如果64域存在，自动计算MAC并填充
        if (field[63].ishave == 1) {
            //参与计算MAC的数据，从MsgType开始-到64域之前数据结束
            String strCalcMac = AFUtils.showResult16Str(pk.txBuffer);
            String strCalcMacLen = strCalcMac.substring(0, 4);
            LogUtils.i( "64域 strCalcMacLen->: " + strCalcMacLen);
            //-12 64域数据还没有，不参与运算
            int dataLen = (Integer.parseInt(strCalcMacLen, 16) * 2) - 12;
            LogUtils.i( "64域 dataLen->: " + dataLen);
            //长度98:附加报文头之前数据 -》临时+20MsgType+Bitmap
//            int headLen = 98 + 20;
            int headLen = 98;
            //12
            strCalcMac = dataFormat(strCalcMac.substring(headLen, dataLen));
            LogUtils.i( "计算MAC数据1->: " + strCalcMac);
            //byte[] mac = upGetMac(pk.txBuffer, 13, len - 13 - 8, hexStringToBytes(macKey));
            byte[] mac = setPOSGetMac(AFUtils.hexString2ByteArray(strCalcMac));
            LogUtils.i( "计算MAC结果->: " + AFUtils.showResult0xStr(mac));
        /*field[63].ishave = 1;
        field[63].len = 0x14;
        field[63].data = new byte[7];*/
            arraycopy(mac, 0, pk.txBuffer, len - 8, 8);
            LogUtils.i( "拼接64域内容: " + AFUtils.showResult0xStr(pk.txBuffer));
            //arraycopy(mac, 0, field[63].data, 0, 8);
        }
        LogUtils.i( "最终报文: " + AFUtils.showResult16Str(pk.txBuffer));
    }

    /**
     * 数据格式化，补成8的倍数
     *
     * @param pkgData 要处理的数据
     */
    public static String dataFormat(String pkgData) {
        LogUtils.i( "数据处理8的倍数 dataFormat: " + pkgData);
        int pkgDataLen = pkgData.length();
        LogUtils.i( "pkgDataLen: " + pkgDataLen);
        int missingLen = pkgDataLen % 16;
        if (missingLen != 0) {
            missingLen = 16 - missingLen;
        }
        LogUtils.i( "需要补0的位数:" + missingLen);
        String rightPadRest = StringUtils.rightPad(pkgData, pkgDataLen + missingLen, "0");
        LogUtils.i("rightPadRest:" + rightPadRest);

        return rightPadRest;
    }

    /**
     * 计算MAC DAL_POSGetMac    --64域
     */
    public static byte[] setPOSGetMac(byte[] data) {
        LogUtils.i( "setPOSGetMac:\t" + AFUtils.showResult16Str(data));
        try {
            int MAC_KEY_ID = 0X02;
            int dataInLen = data.length;
            LogUtils.i( "dataInLen:\t" + dataInLen);
            byte[] macOut = null;
            int mode = 0x01;

            PosGetMacResult posGetMacResult = MainModelImpl.getPinPadSingleton().posGetMac(MAC_KEY_ID, data, dataInLen, mode);

            if (posGetMacResult.getRet() == 0) {
                String strMac = AFUtils.showResult16Str(posGetMacResult.getMac());
                LogUtils.i( "计算MAC成功:\t" + strMac);
                strMac = strMac.substring(0, 16);
                LogUtils.i( "计算MAC结果:\t" + strMac);
                return AFUtils.hexString2ByteArray(strMac);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 解析8583报文，解析成功后，可在fieldRecv中查看各个域
     *
     * @param rxbuf
     * @param rxlen
     * @param fieldrcv
     * @return
     */
    public int ans8583FieldsX(byte[] rxbuf, int rxlen, __8583Fields[] fieldrcv) {
        int len = 0;
        int tmplen = 0;
        long buf = 0, seat = 1;
        byte[] bitMap = new byte[8];
        init8583Fields(fieldsRecv);
        //报文长度[2 字节]+TPDU[5 字节]+ 报文头[10 字节]+附加报文头[32 字节]+ 报文内容[不定长]
        arraycopy(rxbuf, 51, bitMap, 0, 8);
        arraycopy(rxbuf, 0, pack.len, 0, 2);
        arraycopy(rxbuf, 7, pack.head, 0, 6);
        arraycopy(rxbuf, 17, pack.additionalHead, 0, 32);
        arraycopy(rxbuf, 49, pack.msgType, 0, 2);
        arraycopy(rxbuf, 51, pack.bitMap, 0, 8);
//        arraycopy(rxbuf, 59, pack.txBuffer, 0, rxlen);
        LogUtils.i( "bitMap:1\t " + AFUtils.showResult16Str(pack.bitMap));
        LogUtils.i( "pack:\t " + pack.toString());
        len += 59;
        for (int i = 0; i < 8; i++) {
            buf = ((buf << 8) | (bitMap[i] & 0xff));
        }
        for (int i = 0; i < 64; i++) {
            if ((buf & (seat << (63 - i))) > 0) {
                fieldrcv[i].ishave = 1;
                LogUtils.i( "[" + (i + 1) + "]域");
                if (fieldrcv[i].type == 0) {
                    fieldrcv[i].data = new byte[fieldrcv[i].len];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, fieldrcv[i].len);
                    len += fieldrcv[i].len;
                    LogUtils.i( "data: type 0 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 1) {
                    fieldrcv[i].len = rxbuf[len];
                    tmplen = Integer.parseInt(String.format("%02x", rxbuf[len]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 1;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 1 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 2) {
                    fieldrcv[i].len = ((rxbuf[len] << 8) | rxbuf[len + 1]);
                    tmplen = Integer.parseInt(String.format("%02x%02x", rxbuf[len], rxbuf[len + 1]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 2;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 2 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                }

            }
        }
        LogUtils.i( "长度:\t" + String.format("len: %d rxLen %d", len, rxlen));
        if (len > rxlen) {
            return 1;
        }
        return 0;
    }

    /**
     * 设备更新参数解析
     */
    public int ans8583FieldsUpdateDevParam(byte[] rxbuf, int rxlen, __8583Fields[] fieldrcv) {
        int len = 0;
        int tmplen = 0;
        long buf = 0, seat = 1;
        byte[] bitMap = new byte[8];
        init8583Fields(fieldsRecv);
        //报文长度[2 字节]+TPDU[5 字节]+ 报文头[10 字节]+附加报文头[32 字节]+ 报文内容[不定长]
        arraycopy(rxbuf, 19, bitMap, 0, 8);
        arraycopy(rxbuf, 0, pack.len, 0, 2);
        arraycopy(rxbuf, 7, pack.head, 0, 6);
//        arraycopy(rxbuf, 17, pack.additionalHead, 0, 32);
        arraycopy(rxbuf, 17, pack.msgType, 0, 2);
        arraycopy(rxbuf, 19, pack.bitMap, 0, 8);
//        arraycopy(rxbuf, 59, pack.txBuffer, 0, rxlen);
        LogUtils.i( "bitMap:1\t " + AFUtils.showResult16Str(pack.bitMap));
        LogUtils.i( "pack:\t " + pack.toString());
        len += 27;
        for (int i = 0; i < 8; i++) {
            buf = ((buf << 8) | (bitMap[i] & 0xff));
        }
        for (int i = 0; i < 64; i++) {
            if ((buf & (seat << (63 - i))) > 0) {
                fieldrcv[i].ishave = 1;
                LogUtils.i( "[" + (i + 1) + "]域");
                if (fieldrcv[i].type == 0) {
                    fieldrcv[i].data = new byte[fieldrcv[i].len];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, fieldrcv[i].len);
                    len += fieldrcv[i].len;
                    LogUtils.i( "data: type 0 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 1) {
                    fieldrcv[i].len = rxbuf[len];
                    tmplen = Integer.parseInt(String.format("%02x", rxbuf[len]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 1;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 1 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 2) {
                    fieldrcv[i].len = ((rxbuf[len] << 8) | rxbuf[len + 1]);
                    tmplen = Integer.parseInt(String.format("%02x%02x", rxbuf[len], rxbuf[len + 1]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 2;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 2 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                }

            }
        }
        LogUtils.i( "长度:\t" + String.format("len: %d rxLen %d", len, rxlen));
        if (len > rxlen) {
            return 1;
        }
        return 0;
    }

    /**
     * 解析8583报文，解析成功后，可在fieldRecv中查看各个域
     *
     * @param rxbuf
     * @param rxlen
     * @param fieldrcv
     * @return
     */
    public int ans8583Fields(byte[] rxbuf, int rxlen, __8583Fields[] fieldrcv) {
        int len = 0;
        int tmplen = 0;
        long buf = 0, seat = 1;
        byte[] bitMap = new byte[8];
        init8583Fields(fieldsRecv);
        //603039353536303230413031303030313030303034303838333235008E00000000000000000000000000000000000008102038000002D000142000010000011557450508303031303030303430383833323531303030303030303031303334313908BDBBD2D7B3C9B9A600140000100000000300728F53D669A93571B87D3BE9D24263DC178FF38A5E0EAB3BBD6EAA5FCB7E3DF74B34680381DBBD830B2807D38F1B356E779C5AFF9DC1E2391A5A9745F54BF827BEFC38D8C651937798
        //srcArray 表示原数组；srcIndex 表示原数组中的起始索引；
        // destArray 表示目标数组；destIndex 表示目标数组中的起始索引；length 表示要复制的数组长度
        arraycopy(rxbuf, 49, bitMap, 0, 8);
        arraycopy(rxbuf, 0, pack.len, 0, 2);
        arraycopy(rxbuf, 7, pack.head, 0, 6);
        arraycopy(rxbuf, 13, pack.additionalHead, 0, 32);
        arraycopy(rxbuf, 47, pack.msgType, 0, 2);
        arraycopy(rxbuf, 49, pack.bitMap, 0, 8);
        LogUtils.i( "bitMap:1\t " + AFUtils.showResult16Str(pack.bitMap));
        LogUtils.i( "pack:\t " + pack.toString());
        //len += 23;
        len += 57;
        for (int i = 0; i < 8; i++) {
            buf = ((buf << 8) | (bitMap[i] & 0xff));
        }
        for (int i = 0; i < 64; i++) {
            if ((buf & (seat << (63 - i))) > 0) {
                fieldrcv[i].ishave = 1;
                LogUtils.i( "[" + (i + 1) + "]域");
                if (fieldrcv[i].type == 0) {
                    fieldrcv[i].data = new byte[fieldrcv[i].len];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, fieldrcv[i].len);
                    len += fieldrcv[i].len;
                    LogUtils.i( "data: type 0 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 1) {
                    fieldrcv[i].len = rxbuf[len];
                    tmplen = Integer.parseInt(String.format("%02x", rxbuf[len]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 1;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 1 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                } else if (fieldrcv[i].type == 2) {
                    fieldrcv[i].len = ((rxbuf[len] << 8) | rxbuf[len + 1]);
                    tmplen = Integer.parseInt(String.format("%02x%02x", rxbuf[len], rxbuf[len + 1]), 10);
                    if ((i == 1) || (i == 31) || (i == 47) || (i == 59) || (i == 60)) {
                        tmplen = ((tmplen / 2) + (tmplen % 2));
                    }
                    len += 2;
                    fieldrcv[i].data = new byte[tmplen];
                    arraycopy(rxbuf, len, fieldrcv[i].data, 0, tmplen);
                    len += tmplen;
                    LogUtils.i( "data: type 2 : \t " + AFUtils.showResult16Str(fieldrcv[i].data));
                }

            }
        }
        if (len > rxlen) {
            return 1;
        }
        return 0;
    }

    public String getFields(__8583Fields[] field) {

        StringBuffer str = new StringBuffer();
        str.append(String.format("Len:\t%s\n", bytesToHexString(pack.len)));
        str.append(String.format("TPDU:\t%s\n", bytesToHexString(pack.tpdu)));
        str.append(String.format("Head:\t%s\n", bytesToHexString(pack.head)));
        str.append(String.format("MsgType:\t%s\n", bytesToHexString(pack.msgType)));
        str.append(String.format("BitMap:\t%s\n", bytesToHexString(pack.bitMap)));
        str.append("-------------------------------------------------\n");
        for (int i = 0; i < 64; i++) {
            if (field[i].ishave == 1) {
                str.append(String.format("[field:%d] ", i + 1));
                if (field[i].type == 1) {
                    str.append(String.format("[len:%02x] ", field[i].len));
                } else if (field[i].type == 2) {
                    str.append(String.format("[len:%04x] ", field[i].len));
                }
                str.append(String.format("[%s]", bytesToHexString(field[i].data)));
                str.append("\n-------------------------------------------------\n");
            }
        }
        return str.toString();

    }

    private static void dataXor1(byte[] in, byte[] out, int len) {
        for (int i = 0; i < len; i++) {
            out[i] = (byte) ((out[i] & 0xff) ^ (in[i] & 0xff));
        }
    }

    private static void dataXor(byte[] source, byte[] dest, int size, byte[] out) {
        for (int i = 0; i < size; i++) {
            out[i] = (byte) ((dest[i] & 0xff) ^ (source[i] & 0xff));
        }
    }

    /**
     * 计算通信的MAC
     *
     * @param buf
     * @param datasize
     * @param mackey
     * @return
     */
    public byte[] upGetMac(byte[] buf, int seat, int datasize, byte[] mackey) {

        int x = datasize / 8;        //计算有多少个完整的块
        int n = datasize % 8;
        byte[] val = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        byte[] block = new byte[1024];
        Arrays.fill(block, (byte) 0);//清零
        arraycopy(buf, seat, block, 0, datasize);
        //y非0,则在其后补上0x00...
        if (n != 0) {
            x += 1;                                    //将补上的这一块加上去
        }
        byte[] tmp = new byte[8];
        for (int i = 0, j = 0; i < x; i++) {
            arraycopy(block, j, tmp, 0, 8);
            dataXor1(tmp, val, 8);
            j += 8;
        }
        String Bbuf = String.format("%02x%02x%02x%02x%02x%02x%02x%02x", val[0] & 0xff, val[1] & 0xff,
                val[2] & 0xff, val[3] & 0xff, val[4] & 0xff, val[5] & 0xff, val[6] & 0xff, val[7] & 0xff);
        byte[] bbuf = Bbuf.getBytes();
        byte[] b1 = new byte[8];
        byte[] b2 = new byte[8];
        arraycopy(bbuf, 0, b1, 0, 8);
        arraycopy(bbuf, 8, b2, 0, 8);
        byte[] tmpmac;
        tmpmac = DES_encrypt(b1, mackey);
        byte[] Abuf = new byte[8];
        dataXor(tmpmac, b2, 8, Abuf);
        tmpmac = DES_encrypt(Abuf, mackey);

        String str1 = String.format("%02x%02x%02x%02x%02x%02x%02x%02x", tmpmac[0] & 0xff, tmpmac[1] & 0xff, tmpmac[2] & 0xff
                , tmpmac[3] & 0xff, tmpmac[4] & 0xff, tmpmac[5] & 0xff, tmpmac[6] & 0xff, tmpmac[7] & 0xff);

        byte[] mac = new byte[8];
        arraycopy(str1.getBytes(), 0, mac, 0, 8);
        return mac;
    }

    private static byte charToByte(char c) {
        return (byte) "0123456789ABCDEF".indexOf(c);
    }

    public static String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public static byte[] hexStringToBytes(String hexString) {
        if (hexString == null || hexString.equals("")) {
            return null;
        }
        hexString = hexString.toUpperCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] d = new byte[length];
        for (int i = 0; i < length; i++) {
            int pos = i * 2;
            d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));

        }
        return d;
    }


}
