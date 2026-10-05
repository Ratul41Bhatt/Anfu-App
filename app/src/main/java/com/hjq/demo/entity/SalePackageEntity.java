package com.hjq.demo.entity;

import android.util.Log;

import org.apache.commons.lang3.StringUtils;

/**
 * 消费交易报文实体类
 * 报文格式：
 * 报文长度[2 字节]+TPDU[5 字节]+ 报文头[10 字节]+附加报文头[32 字节]+ 报文内容[不定长]
 */
public class SalePackageEntity {

    private static final String TAG = "SalePackageEntity";

    /**
     * 报文长度
     */
    private String pkgLen;
    /**
     * TPDU
     */
    private String tpdu;
    /**
     * 报文头
     */
    private String pkgHead;
    /**
     * 附加报文头
     */
    private String attachPkgHead;
    /**
     * 附加报文头组成域-》12字节终端号
     */
    private String attachHead_terminal_number;
    /**
     * 附加报文头组成域-》2字节原始报文长度 （MsgType到所有域数据）
     */
    private String attachHead_8583_pkg_len;
    /**
     * 附加报文头组成域-》保留域18字节
     */
    private String attachHead_reserved_domain;
    /**
     * 报文内容
     */
    private String pkgData;
    /**
     * 消息类型
     */
    private String msgType;

    /**
     * 位图
     */
    private String bitMap;


    public String getPkgLen() {
        return pkgLen;
    }

    public void setPkgLen(String pkgLen) {
        this.pkgLen = pkgLen;
    }

    public String getTpdu() {
        return tpdu;
    }

    public void setTpdu(String tpdu) {
        this.tpdu = tpdu;
    }

    public String getPkgHead() {
        return pkgHead;
    }

    public void setPkgHead(String pkgHead) {
        this.pkgHead = pkgHead;
    }

    public String getAttachPkgHead() {
        return attachPkgHead;
    }

    public void setAttachPkgHead(String attachPkgHead) {
        this.attachPkgHead = attachPkgHead;
    }

    public String getAttachHead_terminal_number() {
        return attachHead_terminal_number;
    }

    public void setAttachHead_terminal_number(String attachHead_terminal_number) {
        this.attachHead_terminal_number = attachHead_terminal_number;
    }

    public String getAttachHead_8583_pkg_len() {
        return attachHead_8583_pkg_len;
    }

    public void setAttachHead_8583_pkg_len(String attachHead_8583_pkg_len) {
        this.attachHead_8583_pkg_len = attachHead_8583_pkg_len;
    }

    public String getAttachHead_reserved_domain() {
        return attachHead_reserved_domain;
    }

    public void setAttachHead_reserved_domain(String attachHead_reserved_domain) {
        this.attachHead_reserved_domain = attachHead_reserved_domain;
    }

    public String getPkgData() {
        return pkgData;
    }

    public void setPkgData(String pkgData) {
        this.pkgData = pkgData;
    }

    public String getMsgType() {
        return msgType;
    }

    public void setMsgType(String msgType) {
        this.msgType = msgType;
    }

    public SalePackageEntity() {
    }

    public String getBitMap() {
        return bitMap;
    }

    public void setBitMap(String bitMap) {
        this.bitMap = bitMap;
    }

    @Override
    public String toString() {
        return "SalePackageEntity{" +
                "\n   pkgLen='" + pkgLen + '\'' +
                ",\n  tpdu='" + tpdu + '\'' +
                ",\n  pkgHead='" + pkgHead + '\'' +
                ",\n  attachPkgHead='" + attachPkgHead + '\'' +
                ",\n  attachHead_terminal_number='" + attachHead_terminal_number + '\'' +
                ",\n  attachHead_8583_pkg_len='" + attachHead_8583_pkg_len + '\'' +
                ",\n  attachHead_reserved_domain='" + attachHead_reserved_domain + '\'' +
                ",\n  pkgData='" + pkgData + '\'' +
                ",\n  msgType='" + msgType + '\'' +
                ",\n  bitMap='" + bitMap + '\'' +
                '}';
    }

    /**
     * 获取当前报文数据
     *
     * @return
     */
    public String getCurrentPackInfo() {
        return pkgLen + tpdu + pkgHead + attachPkgHead + pkgData;
    }

    /**
     * 签到报文终端参数更新，没有附加报文头
     *
     * @return
     */
    public String getSignInPackInfo() {
        return pkgLen + tpdu + pkgHead + attachHead_terminal_number + attachHead_8583_pkg_len + attachHead_reserved_domain + pkgData;
    }

    /**
     * 特殊报文，
     *
     * @return
     */
    public String getUpdateDevParamPackInfo() {
        String tempData = tpdu + pkgHead + pkgData;
        String tempDataLen = StringUtils.leftPad(Integer.toHexString(tempData.length() / 2), 4, "0");
        Log.i(TAG, "tempDataLen: " + tempDataLen);
        return tempDataLen + tempData;
    }


    /**
     * 组装加密报文
     *
     * @return
     */
    public String getCurrentEncryptPackInfo() {
        return pkgLen + tpdu + pkgHead + attachPkgHead;
    }
}
