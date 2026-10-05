package com.hjq.demo.entity;

/**
 * 银联主扫交易，查询交易结果所需数据
 */
public class PayQRCodeQueryEntity {
    //11域交易流水号
    private String serialNumber;
    //12域受卡方所在地时间
    private String transTime;
    //13域受卡方所在地日期
    private String transDate;
    //17域 BF08 U付订单号
    private String orderNumber;
    //交易金额
    private String amount;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getTransTime() {
        return transTime;
    }

    public void setTransTime(String transTime) {
        this.transTime = transTime;
    }

    public String getTransDate() {
        return transDate;
    }

    public void setTransDate(String transDate) {
        this.transDate = transDate;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }


    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "PayQRCodeQueryEntity{" +
                "serialNumber='" + serialNumber + '\'' +
                ", transTime='" + transTime + '\'' +
                ", transDate='" + transDate + '\'' +
                ", orderNumber='" + orderNumber + '\'' +
                ", amount='" + amount + '\'' +
                '}';
    }
}
