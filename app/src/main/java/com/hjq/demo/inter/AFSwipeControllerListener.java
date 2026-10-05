package com.hjq.demo.inter;

import java.util.Map;

/**
 * 操作监听回调
 */
public abstract interface AFSwipeControllerListener {

    /**
     * 交易结果，服务器返回响应
     *
     * @param responseData 交易处理结果
     */
    public void onTransactionResult(Map<String, String> responseData);

    /**
     * 二维码支付主扫|被扫，服务器返回的响应
     *
     * @param responseData 响应数据
     * @param platformType 平台类型
     *                     1:银联二维码主扫
     *                     2:银联二维码被扫
     *                     3:支付宝主扫
     *                     4:支付宝被扫
     *                     5:微信主扫
     *                     6:微信被扫
     */
    void onQRCodePaymentResult(Map<String, String> responseData, int platformType);

    /**
     * 设备签到结果
     *
     * @param importWKResult 导入秘钥结果 -》
     *                       true:更新工作秘钥成功
     *                       false:更新工作秘钥失败
     */
    public void onDeviceSignIn(boolean importWKResult);

    // public  void onDataAssembly(); 正在组包

    /**
     * 更新设备参数
     * @param paramType 1:设备参数
     *                  2:更新公钥
     *                  3:主密钥
     * @param updateResult 更新结果 true：成功 false:失败
     */
    public void updateDevParam(int paramType,boolean updateResult);


    /**
     * 数据发送中
     *
     * @param msgType 消息类型/签到/消费
     */
    public void onSendData(int msgType);

    /**
     * 数据接收中
     */
    public void onDataReceiving();

    /**
     * 连接服务器失败
     */
    void onConnectServerFail();

    /**
     * 服务器响应异常，请尝试重新签到
     */
    void onServerUnresponsive();

}
