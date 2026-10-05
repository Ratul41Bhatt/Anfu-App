package com.hjq.demo.model;

import org.litepal.crud.LitePalSupport;

/**
 * 订单记录
 */
public class TransOrder extends LitePalSupport {
    //订单ID
    private String order_id;
    //交易日期
    private String order_date_time;
    //交易金额
    private String order_amount;

    //交易银行卡号
    private String order_cardno;

    //交易银行卡有效期
    private String order_expdate;

    //交易方式
    private int order_method;   //1 磁条     2 插卡    4 非接     8 手输卡号

    //授权码
    private String order_apprCode;

    //电子签名
    private String order_signature;

    public String getOrder_signature() {
        return order_signature;
    }

    public void setOrder_signature(String order_signature) {
        this.order_signature = order_signature;
    }

    public String getOrder_cardno() {
        return order_cardno;
    }

    public void setOrder_cardno(String order_cardno) {
        this.order_cardno = order_cardno;
    }

    public String getOrder_expdate() {
        return order_expdate;
    }

    public void setOrder_expdate(String order_expdate) {
        this.order_expdate = order_expdate;
    }

    public String getOrder_id() {
        return order_id;
    }

    public void setOrder_id(String order_id) {
        this.order_id = order_id;
    }

    public String getOrder_date_time() {
        return order_date_time;
    }

    public void setOrder_date_time(String order_date_time) {
        this.order_date_time = order_date_time;
    }

    public String getOrder_amount() {
        return order_amount;
    }

    public void setOrder_amount(String order_amount) {
        this.order_amount = order_amount;
    }

    public int getOrder_method() {
        return order_method;
    }

    public void setOrder_method(int order_method) {
        this.order_method = order_method;
    }

    public String getOrder_apprCode() {
        return order_apprCode;
    }

    public void setOrder_apprCode(String order_apprCode) {
        this.order_apprCode = order_apprCode;
    }

    @Override
    public String toString() {
        return "Order{" +
                "order_id='" + order_id + '\'' +
                ", order_date_time='" + order_date_time + '\'' +
                ", order_amount='" + order_amount + '\'' +
                ", order_cardno='" + order_cardno + '\'' +
                ", order_order_expdate='" + order_expdate + '\'' +
                ", order_method='" + order_method + '\'' +
                ", order_apprCode='" + order_apprCode + '\'' +
                '}';
    }
}
