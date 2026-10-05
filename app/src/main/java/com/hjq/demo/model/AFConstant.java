package com.hjq.demo.model;

import java.text.SimpleDateFormat;

public class AFConstant {
    /**
     * 默认试探性接收数据超时时间15s
     */
    public static final int TOTime = 30;
    //报文响应解析结果
    public static final String TRADE_MSG_TAG = "0000";
    //成功
    public static final String TRADE_VALUE_SUCCESS = "0000";
    //一般的数据错误,报文解析失败
    public static final String TRADE_VALUE_EXCEPTION_ERROR = "0001";
    //超时错误
    public static final String TRADE_VALUE_EXCEPTION_OUTTIME = "0002";
    //卡操作错误
    public static final String TRADE_VALUE_EXCEPTION_CARD = "0003";
    //设备未连接
    public static final String TRADE_VALUE_EXCEPTION_NOCONNECT = "0004";
    //插IC卡
    public static final String TRADE_VALUE_EXCEPTION_SWIPER_IC = "0005";
    //取消按键
    public static final String TRADE_VALUE_EXCEPTION_PRESS_CANCLE = "0006";
    //app取消交易，刷卡等待 或 密码输入等待
    public static final String TRADE_VALUE_EXCEPTION_APP_CANCLE_SWIPER = "0007";
    //报文解析，消息类型错误
    public static final String TRADE_VALUE_EXCEPTION_MSG_TYPE_ERROR = "0008";
    //报文解析，应答码错误
    public static final String TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR = "0009";
    //如果39域不等于00，44域找错误提示（例如：MAC校验错，请重新签到）
    public static final String TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP = "0010";


    /**
     * 主扫交易，查询结果需要的数据
     */
    public static final String RESPONSE_PAY_QRCODE_QUERY = "0011";

    /**
     * 刷卡交易，交易凭证号
     */
    public static final String TRANS_REF_NUMBER = "0012";
    /**
     * 交易参考号
     */
    public static final String TRANS_VOUCHER_NUMBER = "0013";
    /**
     * 交易日期
     */
    public static final String TRANS_DATE_TIME = "0014";

    /**
     * 公钥数据
     */
    public static final String TRANS_UPDATE_BASE64_PUB_KEY = "0015";


    /**
     * 向服务器发送报文标记
     */
    //签到
    public static final int SEND_PKG_MSG_TYPE_SIGN_IN = 2001;
    //消费
    public static final int SEND_PKG_MSG_TYPE_SALE = 2002;
    //银联云闪付
    public static final int SEND_PKG_MSG_TYPE_UNION_PAY = 2003;
    //支付宝支付
    public static final int SEND_PKG_MSG_TYPE_ALI_PAY = 2004;
    //微信支付
    public static final int SEND_PKG_MSG_TYPE_WECHAT_PAY = 2005;
    //更新终端参数
    public static final int SEND_PKG_MSG_TYPE_UPDATE_DEV_PARAM = 2006;
    //更新终端公钥
    public static final int SEND_PKG_MSG_TYPE_UPDATE_PUB_KEY = 2007;
    //更新主密钥
    public static final int SEND_PKG_MSG_TYPE_UPDATE_MASTER_KEY = 2008;


    //设备最后一次签到日期
    public static final String DEVICES_LAST_SIGN = "DevicesLastSign";
    //设备签到日期
    public static SimpleDateFormat SDF_DEVICES_SIGN = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //交易金额
    public static final String CURRENT_TRANS_AMOUNT = "trans_amount";

    //交易卡号
    public static final String CURRENT_TRANS_CARDNO = "trans_cardno";

}
