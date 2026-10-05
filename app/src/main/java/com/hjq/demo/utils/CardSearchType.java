package com.hjq.demo.utils;
/**
 * 发起寻卡交易，卡类型选择
 */
public class CardSearchType {
    //磁条卡
    public static final int MAGNETIC_STRIPE_CARD = 1;
    //IC卡
    public static final int IC_CARD = 2;
    //非接卡
    public static final int RF_CARD = 4;
    //识别IC卡和非接卡
    public static final int RF_IC_CARD = 6;
    //识别所有的卡类型
    public static final int ALL_CARD = 7;

}

