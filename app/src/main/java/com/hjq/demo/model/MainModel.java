package com.hjq.demo.model;

import android.util.ArrayMap;

import com.hjq.demo.inter.AFSwipeControllerListener;

import io.reactivex.Observable;

public interface MainModel {

    /**
     * 初始化交易
     *
     * @param afSwipeControllerListener 操作回调
     */
    void initTrans(AFSwipeControllerListener afSwipeControllerListener);

    //连接服务器
    void connectSocket();

    //设备签到
    void AFPosSignIn();

    /**
     * 更新终端公钥
     */
    void AFUpdatePubKey();

    /**
     * 更新主密钥
     */
    void AFUpdateMasterKey(String data);

    /**
     * 消费报文
     * @param saleDataModel 卡数据
     * @param cardType 卡类型 0：IC/插卡 1:RF/挥卡 2：MSC/刷卡
     */
    void AFPosSale(SaleDataModel saleDataModel,int cardType);

    /**
     * 批结算
     */
    void postBatchData();
}
