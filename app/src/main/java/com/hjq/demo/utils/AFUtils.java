package com.hjq.demo.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import com.szanfu.sdk.api.util.DataConversionUtils;

import org.apache.commons.lang3.StringUtils;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AFUtils {
    private static final String TAG = "AFUtils";
    public static boolean isDebug = false;//调试模式，打印Log信息

    public static void showLog(String tag, String msg) {
        if (isDebug) {
            Log.d(tag, msg);
        }
    }

    public static void showLogD(String tag, String msg) {
        if (isDebug) {
            Log.d(tag, msg);
        }
    }

    public static void showLogI(String tag, String msg) {
        if (isDebug) {
            Log.i(tag, msg);
        }
    }

    public static void showLogW(String tag, String msg) {
        if (isDebug) {
            Log.w(tag, msg);
        }
    }

    public static void showLogE(String tag, String msg) {
        if (isDebug) {
            Log.e(tag, msg);
        }
    }

    public static void showLogV(String tag, String msg) {
        if (isDebug) {
            Log.v(tag, msg);
        }
    }

    public static char[] StringToASCIIArray(String s) {
        if (s == null) {
            return new char[0];
        }
        return s.toCharArray();
    }

    /**
     * 字符串转16进制字符串 3030 >>>>> 33303330
     *
     * @param s
     * @return
     */
    public static byte[] StringToByteArray(String s) {
        int sl = s.length();
        byte[] charArray = new byte[sl];
        for (int i = 0; i < sl; i++) {
            char charElement = s.charAt(i);
            charArray[i] = (byte) charElement;
        }
        return charArray;
    }

    /**
     * @param s
     * @return
     */
    public static char[] ByteToCharArray(byte[] bs) {
        int bsl = bs.length;
        char[] charArray = new char[bsl];
        for (int i = 0; i < bsl; i++) {
            charArray[i] = (char) (((char) bs[i]) & 0x00FF);
        }
        return charArray;
    }

    /**
     * 将byte类型变量转为char类型
     *
     * @param b
     * @return
     */
    public static char byte2char(byte b) {
        return (char) (((char) b) & 0x00FF);
    }

    /**
     * 将16进制字符串转换为byte[]
     *
     * @param bs
     * @return
     */
    public static byte[] hexString2ByteArray(String bs) {
        int bsLength = bs.length();
        if (bsLength % 2 != 0) {
            return null;
        }
        byte[] cs = new byte[bsLength / 2];
        String st = "";
        for (int i = 0; i < bsLength; i = i + 2) {
            st = bs.substring(i, i + 2);
            cs[i / 2] = (byte) Integer.parseInt(st, 16);
        }
        return cs;
    }

    /**
     * 将byte数组以16进制字符显示
     *
     * @param b
     */
    public static String showResult16Str(byte[] b) {
        if (b == null) {
            return "";
        }
        String rs = "";
        int bl = b.length;
        byte bt;
        String bts = "";
        int btsl;
        for (int i = 0; i < bl; i++) {
            bt = b[i];
            bts = Integer.toHexString(bt);
            btsl = bts.length();
            if (btsl > 2) {
                bts = bts.substring(btsl - 2).toUpperCase();
            } else if (btsl == 1) {
                bts = "0" + bts.toUpperCase();
            } else {
                bts = bts.toUpperCase();
            }
//			System.out.println("i::"+i+">>>bts::"+bts);
            rs += bts;
        }
        return rs;
    }


    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 待转换的字节数组
     * @return 十六进制字符串，若输入为空则返回空字符串
     */
    public static String showPrintResult16Str(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            LogUtils.w("showResult16Str: 输入字节数组为空");
            return "";
        }

        StringBuilder hexString = new StringBuilder(bytes.length * 2);

        for (byte b : bytes) {
            // 防止负数转换为负数的十六进制字符串，使用 b & 0xFF 转换为无符号整型
            String hex = Integer.toHexString(b & 0xFF).toUpperCase();

            // 保证每个字节都是两位十六进制，位数不足补零
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        String result = hexString.toString();
        LogUtils.d("showResult16Str: 输入字节长度：" + bytes.length + "，转换结果：" + result);
        return result;
    }


    /**
     * 将byte数组以16进制0x样式字符显示
     *
     * @param b
     */
    public static String showResult0xStr(byte[] b) {
        String rs = "";
        int bl = b.length;
        byte bt;
        String bts = "";
        int btsl;
        for (int i = 0; i < bl; i++) {
            bt = b[i];
            bts = Integer.toHexString(bt);
            btsl = bts.length();
            if (btsl > 2) {
                bts = "0x" + bts.substring(btsl - 2);
            } else if (btsl == 1) {
                bts = "0x0" + bts;
            } else {
                bts = "0x" + bts;
            }
//			System.out.println("i::"+i+">>>bts::"+bts);
            rs += (bts + " ");
        }
//		System.out.println("rs::"+rs);
        return rs;
    }

    /**
     * 21算法添加校验位,直接将字符串转为char，得到ascII码，然后处理
     *
     * @param str
     * @return 如果字符串全是数字则返回原始字符串加上最后一位校验位组成的新字符串，否则返回ERROR
     */
    public static String enc21Int(String str) {
        try {
            int len = str.length();
            int endi = 0;
            int sum = 0;
            char[] cs = str.toCharArray();
            for (int i = 0; i < len; i++) {
                sum = sum + cs[i] * (i % 2 == 0 ? 2 : 1);
            }
//				System.out.println("enc21Int>>>sum::"+sum);
            endi = (10 - (sum % 10)) % 10;
            return (str + endi);
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * 对接收到的字符串进行21校验
     *
     * @param str
     * @return 如果字符串长度大于3并且校验通过则直接返回正确的信息，否则返回ERROR
     */
    public static String checkOutStr_21(String str) {
        try {
            int len = str.length();
            int endi = Integer.parseInt(str.substring(len - 1, len));
            int sum = 0;
            char[] cs = str.substring(0, len - 1).toCharArray();
            for (int i = 0; i < len - 1; i++) {
                sum = sum + cs[i] * (i % 2 == 0 ? 2 : 1);
            }
//			System.out.println("checkOutStr_21>>>sum::"+sum);
            if (endi == (10 - (sum % 10)) % 10) {
                return str.substring(0, len - 1);
            }
        } catch (Exception e) {
            return "ERROR";
        }
        return "ERROR";
    }

    /**
     * 累加和算法添加校验位,直接将字符串转为char，得到ascII码，然后处理
     *
     * @param str
     * @return 如果字符串全是数字则返回原始字符串加上最后一位校验位组成的新字符串，否则返回ERROR
     */
    public static String encSumInt(String str) {
        try {
            int len = str.length();
            int sum = 0;
            char[] cs = str.toCharArray();
            for (int i = 0; i < len; i++) {
                sum = sum + cs[i];
            }
            String sum_str = Integer.toHexString(sum);
//			System.out.println("sum_str::"+sum_str);
            int ssl = sum_str.length();
            if (ssl < 2) {
                char nc = (char) sum;
                return (str + nc);
            }
            String sum_str_sub = sum_str.substring(ssl - 2);
            int ci = Integer.decode("0x" + sum_str_sub);
            char c = (char) ci;
            return (str + c);
        } catch (Exception e) {
            return "ERROR";
        }
    }

    /**
     * 对接收到的字符串进行累加和校验
     *
     * @param str
     * @return 如果长度大于3并且校验通过则直接返回正确的信息，否则返回ERROR
     */
    public static String checkOutStr_sum(String str) {
        try {
            int len = str.length();
            if (len < 3) {
                return "ERROR";
            }
            String str_int = str.substring(0, len - 1);
            String str_sub = str.substring(len - 1, len);
            int sum = 0;
            char[] cs = str_int.toCharArray();
            for (int i = 0; i < len - 1; i++) {
                sum = sum + cs[i];
            }
            String sum_str = Integer.toHexString(sum);
//				System.out.println("checkOutStr_sum>>>sum_str::"+sum_str);
            int ssl = sum_str.length();
            if (ssl < 2) {
                if (str_sub.charAt(0) == sum) {
                    return str_int;
                }
            } else {
                String sum_str_sub = sum_str.substring(ssl - 2);
                if (str_sub.charAt(0) == Integer.decode("0x" + sum_str_sub)) {
                    return str_int;
                }
            }
        } catch (Exception e) {
            return "ERROR";
        }
        return "ERROR";
    }

    /**
     * 对字符串str添加LRC校验位
     *
     * @param str
     */
    public static char encLRCInt(String str) {
        int len = str.length();
        int sum = 0;
        char[] cs = str.toCharArray();
        for (int i = 0; i < len; i++) {
            sum = sum + cs[i];
        }
        String sum_str = Integer.toHexString(sum);
//		System.out.println("sum_str::"+sum_str);
        int ssl = sum_str.length();
        if (ssl > 2) {
            sum_str = sum_str.substring(ssl - 2);
        }
        int ci = Integer.decode("0x" + sum_str);
        char c = (char) ci;
        char cF = 0xFF;
        char c1 = 0x01;
        char nc = (char) (cF - c);
        char ncLrc = (char) ((char) (nc + c1) & 0x00FF);
//		System.out.println("ncLrc::"+ncLrc+">>>int(ncLrc)::"+(int)ncLrc);
        return ncLrc;
    }

    /**
     * 通过LRC校验算法检测字符串str是否可用
     *
     * @param str 返回一个布尔值，校验通过则返回true，否则，返回false
     */
    public static boolean checkOutStr_LRC(String str) {
        int len = str.length();
        if (len < 3) {
            return false;
        }
        String str_int = str.substring(0, len - 1);
        String str_sub = str.substring(len - 1, len);
        int sum = 0;
        char[] cs = str_int.toCharArray();
        for (int i = 0; i < len - 1; i++) {
            sum = sum + cs[i];
        }
        String sum_str = Integer.toHexString(sum);
//			System.out.println("checkOutStr_sum>>>sum_str::"+sum_str);
        int ssl = sum_str.length();
        if (ssl > 2) {
            sum_str = sum_str.substring(ssl - 2);
        }
        int ci = Integer.decode("0x" + sum_str);
        char c = (char) ci;
        char cF = 0xFF;
        char c1 = 0x01;
        char nc = (char) (cF - c);
        char ncLrc = (char) ((char) (nc + c1) & 0x00FF);
//			System.out.println("checkOutStr_sum>>>ncLrc::"+(int)ncLrc);
        if (str_sub.charAt(0) == ncLrc) {
            return true;
        }
        return false;
    }

    /**
     * 异或校验
     *
     * @param str
     */
    public static char encXORInt(byte[] str) {
        int len = str.length;
        char tor = 0;
        for (int i = 0; i < len; i++) {
            tor = (char) (tor ^ AFUtils.byte2char(str[i]));
        }
//		System.out.println("tor::"+tor+">>>int(tor)::"+(int)tor);
        return tor;
    }

    /**
     * @param str
     */
    public static boolean checkOutStr_XOR(String str) {
        int len = str.length();
        if (len < 3) {
            return false;
        }
        String str_int = str.substring(0, len - 1);//去掉了LRC
        String str_sub = str.substring(len - 1, len);//去除了LRC
        char tor = 0;
        char[] cs = str_int.toCharArray();
        for (int i = 0; i < len - 2; i++) {//总长度减2去掉了包尾和LRC
            tor = (char) (tor ^ cs[i]);
        }
//		System.out.println("tor::"+tor+">>>int(tor)::"+(int)tor);
        char a = str_sub.charAt(0);
        if (str_sub.charAt(0) == tor) {
            return true;
        }
        return false;
    }

    /**
     * 检测字符串是否全由数字组成
     *
     * @param str
     * @return
     */
    public static boolean checkNum(String str) {
        String regEx = "[^0-9]";
        Pattern p = Pattern.compile(regEx);
        Matcher m;
        boolean isNum = true;
        char[] pns = str.toCharArray();
        int pl = pns.length;
        for (int i = 0; i < pl; i++) {
            m = p.matcher("" + pns[i]);
            if (m.find()) {
                isNum = false;
                break;
            }
        }
        return isNum;
    }

    /**
     * 检测字符串是否全由数字和.组成
     *
     * @param str
     * @return
     */
    public static boolean checkNumIsMoney(String str) {
        String regEx = "[^0-9.]";
        Pattern p = Pattern.compile(regEx);
        Matcher m;
        boolean isNum = true;
        char[] pns = str.toCharArray();
        int pl = pns.length;
        for (int i = 0; i < pl; i++) {
            m = p.matcher("" + pns[i]);
            if (m.find()) {
                isNum = false;
                break;
            }
        }
        return isNum;
    }

    /**
     * 检测数字或者字符
     *
     * @param str
     * @return
     */
    public static boolean checkNumOrZ(String str) {
        String regEx = "[^(0-9a-zA-Z)]";
        Pattern p = Pattern.compile(regEx);
        Matcher m;
        boolean isNum = true;
        char[] pns = str.toCharArray();
        int pl = pns.length;
        for (int i = 0; i < pl; i++) {
            m = p.matcher("" + pns[i]);
            if (m.find()) {
                isNum = false;
                break;
            }
        }
        return isNum;
    }

    /**
     * 检测字符串是否是合法的十六进制展开
     *
     * @param str
     * @return
     */
    public static boolean checkHexStr(String str) {
        String regEx = "[^(0-9a-fA-F)]";
        Pattern p = Pattern.compile(regEx);
        Matcher m;
        boolean isNum = true;
        char[] pns = str.toCharArray();
        int pl = pns.length;
        for (int i = 0; i < pl; i++) {
            m = p.matcher("" + pns[i]);
            if (m.find()) {
                isNum = false;
                break;
            }
        }
        return isNum;
    }

    /**
     * 检测手机网络是否可用
     *
     * @param context
     * @return
     */
    public static boolean isConnect(Context context) {
        // 获取手机所有连接管理对象（包括对wi-fi,net等连接的管理）
        try {
            ConnectivityManager connectivity = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);
            if (connectivity != null) {
                // 获取网络连接管理的对象
                NetworkInfo info = connectivity.getActiveNetworkInfo();
                if (info != null && info.isConnected()) {
                    // 判断当前网络是否已经连接
                    if (info.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            Log.v("error", e.toString());
        }
        return false;
    }


    /**
     * 检测系统蓝牙版本,，如果是4.0返回true，否则返回false
     */
    @SuppressWarnings("unused")
    public static boolean checkBleVer() {
        try {
            @SuppressWarnings("rawtypes")
            Class cls = Class.forName("android.bluetooth.BluetoothAdapter$LeScanCallback");
            if (cls != null) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 将数组倒置
     *
     * @param byteArray
     * @return
     */
    public static byte[] reverse(byte[] byteArray) {
        byte[] rev = new byte[byteArray.length];
        for (int i = 0; i < byteArray.length; i++) {
            rev[byteArray.length - (i + 1)] = byteArray[i];
        }
        return rev;
    }

    public static String toHexCode(byte[] data, int offset, int length) {
        final StringBuilder stringBuilder = new StringBuilder(data.length * 2);
        for (int i = offset; i < offset + length; i++) {
            byte byteChar = data[i];
            stringBuilder.append(String.format("%02X", byteChar).toUpperCase());
        }
        return stringBuilder.toString();
    }

    /**
     * 将特殊格式字符串转为为TLV数据对象，必须是标准的TLV格式字符串，否则返回NULL
     *
     * @param str
     * @return
     */
    public static ArrayList<TLVModel> praseTLVStr(byte[] str) {
        try {
            ArrayList<TLVModel> tList = new ArrayList<TLVModel>();
            int curIndex = 0;
            int strLen = str.length;
            int tLen = 1;
            char t = 0;
            char t2 = 0;
            int l = 0;
            String v = "";
            TLVModel tlvModel;
            while (curIndex < strLen) {

                t = AFUtils.byte2char(str[curIndex++]);
                if (t == 0x5F || t == 0x9F || t == 0xDF) {
                    t2 = AFUtils.byte2char(str[curIndex++]);
                    tLen = 2;
                } else {
                    tLen = 1;
                }
                if (curIndex >= strLen) {
                    AFUtils.showLogE(TAG, "strLen::" + strLen + ">>>curIndex::" + curIndex);
                    return null;
                }
                l = AFUtils.byte2char(str[curIndex++]);
                if (l == 0x0081) {//数据长度在128-255之间
                    l = AFUtils.byte2char(str[curIndex++]);
                } else if (l == 0x0082) {//数据长度在256以上
                    int l1 = AFUtils.byte2char(str[curIndex++]);
                    int l2 = AFUtils.byte2char(str[curIndex++]);
                    l = (l1 << 8) + l2;
                }
                AFUtils.showLogD(TAG, "t::" + Integer.toHexString(t) + ">>>l::" + l);
                if (curIndex + l > strLen) {
                    AFUtils.showLogE(TAG, "strLen::" + strLen + ">>>curIndex::" + curIndex + ">>>len::" + l);
                    return null;
                }
                byte[] tb = new byte[l];
                System.arraycopy(str, curIndex, tb, 0, l);
                v = AFUtils.showResult16Str(tb);
                AFUtils.showLogD(TAG, "v::" + v);
                curIndex += l;
                tlvModel = new TLVModel();
                tlvModel.setTLen(tLen);
                tlvModel.setTag(t);
                tlvModel.setTag2(t2);
                tlvModel.setLen(l);
                tlvModel.setValue(v);
                tList.add(tlvModel);
            }
            return tList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * 处理52域信息，个人标识码数据
     *
     * @return 将字符串转换为BINARY编码格式的字符串
     */
    public static String dealStr2BINARY(String str) {
        StringBuffer tempDcBuf = new StringBuffer();
        int pswLen = str.length();
        char dc;
        char tt;
        int tj;
        for (int j = 0; j < pswLen; j++) {
            dc = str.charAt(j);
            for (int k = 7; k >= 0; k--) {
                tt = (char) ((1 << k) & 0x00FF);
                tj = dc & tt;
                if (tj != 0) {
                    tempDcBuf.append("1");
                } else {
                    tempDcBuf.append("0");
                }
            }
        }
        return tempDcBuf.toString();
    }

    /**
     * @param list
     * @param vType
     * @return
     */
    public static Map<String, String> list2Map(ArrayList<TLVModel> list, int vType) {
        if (list == null) {
            return null;
        }
        Map<String, String> resMap = new HashMap<String, String>();
        TLVModel tlvModel = null;
        int tLen;
        char t;
        char t2;
        int l;
        String v;
        int ll = list.size();
        for (int i = 0; i < ll; i++) {
            tlvModel = list.get(i);
            tLen = tlvModel.getTLen();
            t = tlvModel.getTag();
            t2 = tlvModel.getTag2();
            l = tlvModel.getLen();
            if (vType == 0) {
                v = new String(AFUtils.hexString2ByteArray(tlvModel.getValue()));
            } else if (vType == 1) {
                v = new String(AFUtils.ByteToCharArray(AFUtils.hexString2ByteArray(tlvModel.getValue())));
            } else {
                v = tlvModel.getValue();
            }
            AFUtils.showLogD(
                    TAG,
                    "tLen::"
                            + tLen
                            + ">>>T::"
                            + Integer.toHexString(t)
                            + ">>>T2::"
                            + Integer.toHexString(t2)
                            + ">>>L::"
                            + l
                            + ">>>V::"
                            + v);
            if (tLen == 1) {
                resMap.put(AFUtils.showResult16Str(AFUtils.StringToByteArray(("" + t))), v);
            } else if (tLen == 2) {
                resMap.put(AFUtils.showResult16Str(AFUtils.StringToByteArray(("" + t + t2))), v);
            }
        }
        return resMap;
    }


    public static boolean verifyIP(String ipStr) {
        if (ipStr == null || "".equals(ipStr)) {
            return false;
        }
        String[] ips = ipStr.split("\\.");
        int ipsl = ips.length;
        if (ipsl != 4) {
            return false;
        }
        String ipch = "";
        for (int i = 0; i < ipsl; i++) {
            ipch = ips[i];
            if (!checkNum(ipch) || ipch.length() > 3 || ipch.length() < 1) {
                return false;
            }
        }
        return true;
    }

    /**
     * 交易金额组装为12位，不包含小数点，只包含数字
     *
     * @param money
     * @return
     */
    public static String dealMoneyStr(String money) {
        String resDealSpend = "";
        String[] ms = money.split("\\.");
        int msl = ms.length;
        StringBuffer sf = new StringBuffer();
        if (msl == 1) {
            int ml = 12 - money.length();
            for (int i = 0; i < ml; i++) {
                sf.append("0");
            }
            sf.append(ms[0]);
            //sf.append("00");
            resDealSpend = sf.toString();
        } else if (msl == 2) {
            int ml = 10 - ms[0].length();
            for (int i = 0; i < ml; i++) {
                sf.append("0");
            }
            sf.append(ms[0]);
            int msl1 = ms[1].length();
            String radix = "";
            if (msl1 > 2) {
                radix = ms[1].substring(0, 2);
            } else if (msl1 == 1) {
                radix = ms[1] + "0";
            } else if (msl1 == 2) {
                radix = ms[1];
            }
            sf.append(radix);
            resDealSpend = sf.toString();
        }
        return resDealSpend;
    }

    /**
     * 交易金额组装为13位，包含小数点
     *
     * @param money
     * @return
     */
    public static String dealMoneyStr1(String amount) {
        String resDealSpend = "";
        String[] ms = amount.split("\\.");
        int msl = ms.length;
        StringBuffer sf = new StringBuffer();
        if (msl == 1) {
            int ml = 13 - amount.length();
            for (int i = 0; i < ml; i++) {
                sf.append("0");
            }
            sf.append(ms[0]);
            //sf.append("00");
            resDealSpend = sf.toString();
        } else if (msl == 2) {
            int ml = 10 - ms[0].length();
            for (int i = 0; i < ml; i++) {
                sf.append("0");
            }
            sf.append(ms[0]);
            sf.append(".");
            int msl1 = ms[1].length();
            String radix = "";
            if (msl1 > 2) {
                radix = ms[1].substring(0, 2);
            } else if (msl1 == 1) {
                radix = ms[1] + "0";
            } else if (msl1 == 2) {
                radix = ms[1];
            }
            sf.append(radix);
            resDealSpend = sf.toString();
        }
        return resDealSpend;
    }


    /**
     * 59域定制内容
     *
     * @param t
     * @param v
     * @param lenType 数据长度处理
     *                true :长度/2
     *                false 实际字符串长度
     * @return
     */
    public static String field59DataTLV(String t, String v, boolean lenType) {
       /* A1 000
        A2 051
        01 002 02
        02 026 00005002200670999000001668
        05 008 20083003
        A3 000
        A4 000
        A5 005 P0926*/
        StringBuffer sbField59Data = new StringBuffer();
        sbField59Data.setLength(0);
        String tag = showResult16Str(StringToByteArray(t));
        sbField59Data.append(tag);
        String values = null;
        String len = null;
        //数据为空默认000
        if (StringUtils.isEmpty(v)) {
            sbField59Data.append("303030");
        } else {
            if (lenType) {
                len = StringUtils.leftPad(String.valueOf(v.length() / 2), 3, "0");
            } else {
                len = StringUtils.leftPad(String.valueOf(v.length()), 3, "0");
            }
            sbField59Data.append(showResult16Str(StringToByteArray(len)));
            sbField59Data.append(showResult16Str(StringToByteArray(v)));
        }
        return sbField59Data.toString();
    }

    /**
     * 封装TLV格式数据
     *
     * @param t
     * @param v
     * @return
     */
    public static String packSendTLV(String t, String v) {
        int l = v.length();
        String tlvStr = "";
        if (l <= 127 * 2) {
            tlvStr = t + AFUtils.showResult16Str(AFUtils.StringToByteArray("" + (char) (l / 2))) + v;
        } else if (128 * 2 <= l && l <= 255 * 2) {
            tlvStr = t + "81" + AFUtils.showResult16Str(AFUtils.StringToByteArray("" + (char) (l / 2))) + v;
        } else {
            int l1 = l / 2 >> 8;
            int l2 = (l / 2 & 0xFF);
            tlvStr = t + "82" +
                    AFUtils.showResult16Str(AFUtils.StringToByteArray("" + (char) (l1)))
                    + AFUtils.showResult16Str(AFUtils.StringToByteArray("" + (char) (l2)))
                    + v;
        }
        return tlvStr;
    }


    /**
     * 六进制字符串转换为ASCII字符
     */
    public static String hexToAscii(String hexString) {
        if (hexString.length() % 2 != 0) {
            Log.e(TAG, "hexToAscii: Invalid hex string length");
            return "";
        }

        StringBuilder asciiString = new StringBuilder();
        asciiString.setLength(0);

        for (int i = 0; i < hexString.length(); i += 2) {
            String hexByte = hexString.substring(i, i + 2);
            char asciiChar = (char) Integer.parseInt(hexByte, 16);
            asciiString.append(asciiChar);
        }
        return asciiString.toString();
    }


    /**
     * 字符串转换为16进制字符串。
     *
     * @param input
     * @return
     */
    public static String stringToHex(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder hexString = new StringBuilder();
        for (char c : input.toCharArray()) {
            // 将每个字符转换为对应的16进制值，并拼接到hexString中
            hexString.append(String.format("%02X", (int) c));
        }
        return hexString.toString();
    }

    public static String hexGBK2String(String src) {

        return hexString2String(src, "GBK", "UTF-8");
    }

    private static String hexString2String(String src, String oldchartype, String chartype) {
        byte[] bts = hexString2Bytes(src);
        try {
            if (oldchartype.equals(chartype))
                return new String(bts, oldchartype);
            else
                return new String(new String(bts, oldchartype).getBytes(), chartype);
        } catch (Exception e) {

            return "";
        }
    }

    public static byte[] hexString2Bytes(String src) {
        int l = src.length() / 2;
        byte[] ret = new byte[l];
        for (int i = 0; i < l; i++) {
            ret[i] = Integer.valueOf(src.substring(i * 2, i * 2 + 2), 16).byteValue();
        }
        return ret;
    }

    public static ArrayList<TLVModel> praseTLVStrX(byte[] str) {
        try {
            ArrayList<TLVModel> tList = new ArrayList<TLVModel>();
            int curIndex = 0;
            int strLen = str.length;
            int tLen = 1;
            char t = 0;
            char t2 = 0;
            int l = 0;
            String v = "";
            TLVModel tlvModel;
            while (curIndex < strLen) {
                t = AFUtils.byte2char(str[curIndex++]);
                if (t == 0x5F || t == 0x9F || t == 0xDF || t == 0xBF) {
                    t2 = AFUtils.byte2char(str[curIndex++]);
                    tLen = 2;
                } else {
                    tLen = 1;
                }
                if (curIndex >= strLen) {
                    AFUtils.showLogE(TAG, "strLen::" + strLen + ">>>curIndex::" + curIndex);
                    return null;
                }
                l = AFUtils.byte2char(str[curIndex++]);
                if (l == 0x0081) {//数据长度在128-255之间
                    l = AFUtils.byte2char(str[curIndex++]);
                } else if (l == 0x0082) {//数据长度在256以上
                    int l1 = AFUtils.byte2char(str[curIndex++]);
                    int l2 = AFUtils.byte2char(str[curIndex++]);
                    l = (l1 << 8) + l2;
                }
                AFUtils.showLogD(TAG, "t::" + Integer.toHexString(t) + ">>>l::" + l);
                if (curIndex + l > strLen) {
                    AFUtils.showLogE(TAG, "strLen::" + strLen + ">>>curIndex::" + curIndex + ">>>len::" + l);
                    return null;
                }
                byte[] tb = new byte[l];
                System.arraycopy(str, curIndex, tb, 0, l);
                v = AFUtils.showResult16Str(tb);
                AFUtils.showLogD(TAG, "v::" + v);
                curIndex += l;
                tlvModel = new TLVModel();
                tlvModel.setTLen(tLen);
                tlvModel.setTag(t);
                tlvModel.setTag2(t2);
                tlvModel.setLen(l);
                tlvModel.setValue(v);
                tList.add(tlvModel);
            }
            return tList;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static byte[] StrToBCDBytes(String s) {

        if (s.length() % 2 != 0) {
            s = "0" + s;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        char[] cs = s.toCharArray();
        for (int i = 0; i < cs.length; i += 2) {
            int high = cs[i] - 48;
            int low = cs[i + 1] - 48;
            baos.write(high << 4 | low);
        }
        return baos.toByteArray();
    }

    /**
     * 将BCD码转成String
     *
     * @param b
     * @return
     */
    public static String bcdToString(byte[] b) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            int h = ((b[i] & 0xff) >> 4) + 48;
            sb.append((char) h);
            int l = (b[i] & 0x0f) + 48;
            sb.append((char) l);
        }
        return sb.toString();
    }

    public static String getPlatformType(int platformType) {
        String tempPlatformType = null;
        String[] tempPlatformTypeArray = new String[]{"银联主扫", "银联被扫", "支付宝主扫", "支付宝被扫", "微信主扫", " 微信被扫"};
        return tempPlatformTypeArray[platformType - 1];
    }

    public static String getRandom() {
        Random random = new Random();
        StringBuffer sbRandom = new StringBuffer();
        sbRandom.setLength(0);
        int num = 32;
        for (int i = 0; i < num; i++) {
            int randomNumber = random.nextInt(16);
            switch (randomNumber) {
                case 10:
                    sbRandom.append("A");
                    break;
                case 11:
                    sbRandom.append("B");
                    break;
                case 12:
                    sbRandom.append("C");
                    break;
                case 13:
                    sbRandom.append("D");
                    break;
                case 14:
                    sbRandom.append("E");
                    break;
                case 15:
                    sbRandom.append("F");
                    break;
                default:
                    sbRandom.append(String.valueOf(randomNumber));
                    break;
            }
        }
        return sbRandom.toString();
    }

    /**
     * 下载主密钥随机数RSA运算
     *
     * @return
     */
    public static String getTag02Data() {
        byte[] randomData_01 = hexString2ByteArray("0002");
        byte[] randomData_03 = hexString2ByteArray("00");

        String strRandom = getRandom();

        new SPTransUtil().setTmkRandom(strRandom);

        //String sbRandom = "CB4E3EA400309DAB656D8DBFE4B93F35";
        byte[] randomData_04 = StringToByteArray(strRandom);


        int dataLen = randomData_01.length + randomData_03.length + randomData_04.length;
        byte[] randomData_02 = initByte(dataLen);

        byte[] tag02Data = new byte[256];
        System.arraycopy(randomData_01, 0, tag02Data, 0, randomData_01.length);
        System.arraycopy(randomData_02, 0, tag02Data, randomData_01.length, randomData_02.length);
        System.arraycopy(randomData_03, 0, tag02Data, randomData_01.length + randomData_02.length, randomData_03.length);
        System.arraycopy(randomData_04, 0, tag02Data, randomData_01.length + randomData_02.length + randomData_03.length, randomData_04.length);

        return showResult16Str(tag02Data);
    }

    private static byte[] initByte(int arrayLength) {
        // 创建byte数组并初始化为0xFF（全F）
        byte[] resByte = new byte[(512 - arrayLength * 2) / 2];
        Arrays.fill(resByte, (byte) 0xFF); // 使用0xFF（十六进制）来填充整个数组
        return resByte;
    }


    /**
     * 将时间戳（以秒为单位）格式化为指定的日期时间格式
     *
     * @param timestamp 时间戳（以秒为单位）
     * @return 格式化后的日期时间字符串
     */
    public static String formatTimestamp(long timestamp) {

        // 创建 SimpleDateFormat 对象并设置日期格式
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        // 将时间戳转换为 Date 对象
        Date date = new Date(timestamp);

        // 格式化 Date 对象并返回
        return sdf.format(date);
    }

    /**
     * 移除hex ,.
     *
     * @param hexString
     * @return
     */
    public static String removeAllHexPrefixes(String hexString) {
        String delimiter = hexString.contains(",") ? "," : " ";
        String[] hexArray = hexString.split(delimiter);
        for (int i = 0; i < hexArray.length; i++) {
            if (hexArray[i].startsWith("0x")) {
                hexArray[i] = hexArray[i].substring(2);
            }
        }
        return String.join("", hexArray);
    }


    public static String filterNonAscii(String input) {
        StringBuilder filteredString = new StringBuilder();

        for (char c : input.toCharArray()) {
            if (c >= 32 && c <= 126) {
                filteredString.append(c);
            }
        }

        return filteredString.toString();
    }

    /**
     * 获取系统版本，使用AFSystemCommonInterface
     *
     * @return 系统版本号
     */
    public static String getSystemVersion() {
        //SZANFU_AF930_V1.32_02_20240329_A2_userdebug
        Log.i(TAG, "getSystemVersion: " + Build.DISPLAY);
        String[] fmtSystem = Build.DISPLAY.split("_");
        if (fmtSystem.length >= 3) {
            Log.i(TAG, "系统版本:" + fmtSystem[2]);
            return fmtSystem[2];
        }
        return "";
    }


    /**
     * 获取自己应用内部的版本名
     */
    public static String getVersionName(Context context) {
        PackageManager manager = context.getPackageManager();
        String name = "";
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            name = "V" + info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return name;
    }

    /**
     * 后补空格
     *
     * @param str         源数据
     * @param targetBytes 数据长度
     * @return 补空格后的数据
     */
    public static String padStringToBytes(String str, int targetBytes) {
        int byteLength = str.getBytes().length;
        if (byteLength < targetBytes) {
            int spacesNeeded = targetBytes - byteLength;
            StringBuilder sb = new StringBuilder(str);
            for (int i = 0; i < spacesNeeded; i++) {
                sb.append(" ");
            }
            return sb.toString();
        }
        return str;
    }


    /**
     * 获取客户ID
     *
     * @return 客户编号
     */
    public static int getCustomerVersion() {
        String[] fmtSystem = Build.DISPLAY.split("_");
        for (int i = 0; i < fmtSystem.length; i++) {
            Log.e(TAG, "fmtSystem: " + i + " \t " + fmtSystem[i]);
        }
        if (fmtSystem.length >= 4) {
            Log.i(TAG, "getCustomerVersion 客户编号:" + fmtSystem[3]);
            try {
                return Integer.parseInt(fmtSystem[3]);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        return 0;
    }


    /**
     * 设备型号
     *
     * @return 型号描述
     */
    public static String getDeviceModel() {
        String[] fmtSystem = Build.DISPLAY.split("_");
        return fmtSystem[1];
    }


    /**
     * 通用的“设备是否在支持列表中”检查
     */
    private static boolean checkDeviceSupported(String... supportedTypes) {
        String customerType = DataConversionUtils.getCustomerType();
        boolean isSupported = false;

        if (customerType != null && supportedTypes != null) {
            for (String type : supportedTypes) {
                if (customerType.equals(type)) {
                    isSupported = true;
                    break;
                }
            }
        }

        LogUtils.e("customerType = " + customerType +
                " , isLcdSupported: " + isSupported + "\t" +
                (isSupported ? "支持LCD" : "不支持LCD"));

        return isSupported;
    }

    /**
     * 判断当前设备是否支持 LCD
     */
    public static boolean checkLcdSupported() {
        return checkDeviceSupported(
                DeviceType.A8,
                DeviceType.A11,
                DeviceType.A12,
                DeviceType.A13
        );
    }
}
