package com.hjq.demo.utils;

import android.util.Log;

import com.blankj.utilcode.util.SPUtils;

/**
 * 交易参数记录表
 */
public class SPTransUtil {
    private static final String TAG = "SPTransUtil";
    //记录交易流水号
    private static SPUtils spUtils;
    //交易流水号固定键值
    private static final String SERIAL_NUMBER = "TransSerialNumber";
    //随机数
    private static final String CURRENT_RANDOM = "CurrentRandom";

    //初始交易序列号
    private static final int FIRST_SERIAL_NUMBER = 2000;

    public SPTransUtil() {
        spUtils = SPUtils.getInstance("SP_SerialNumber");
    }


    /**
     * 公钥加密随机数
     */
    public void setTmkRandom(String strRandom) {
        spUtils.put(CURRENT_RANDOM, strRandom);
    }

    /**
     * 获取当前公钥加密随机数
     *
     * @param strRandom
     */
    public String getTmkRandom() {
        return spUtils.getString(CURRENT_RANDOM, "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
    }

    /**
     * 某个事务完成后，设置交易流水号累加1
     */
    public void setTransSerialNumber() {
        LogUtils.i( "设置交易流水号 ");
        //设备存在流水号记录
        if (spUtils.contains(SERIAL_NUMBER)) {
            LogUtils.i( "已存储流水号 ");
            int currentSn = Integer.parseInt(spUtils.getString(SERIAL_NUMBER));
            spUtils.put(SERIAL_NUMBER, String.format("%06d", (currentSn + 1)));
        } else {
            spUtils.put(SERIAL_NUMBER, String.format("%06d", FIRST_SERIAL_NUMBER));
            LogUtils.i( "未存储流水号【首次】写流水号:");
        }
        LogUtils.i( "当前设备交易流水号:" + spUtils.getString(SERIAL_NUMBER));
    }

    /**
     * 获取设备目前交易流水号
     */
    public String getTransSerialNumber() {
        LogUtils.i( "获取设备目前交易流水号 ");
        //设备存在流水号记录
        if (spUtils.contains(SERIAL_NUMBER)) {
            LogUtils.i( "已存储流水号 ");
            int currentSn = Integer.parseInt(spUtils.getString(SERIAL_NUMBER));
            return String.format("%06d", currentSn);
        } else {
            LogUtils.i( "未存储流水号 ");
            return String.format("%06d", FIRST_SERIAL_NUMBER);
        }
    }

}
