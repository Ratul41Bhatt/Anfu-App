package com.hjq.demo.entity;

/**
 * 交易小票数据
 */
public class TransReceiptPrintEntity {
    /**
     * 商户名称
     */
    private String businessName;
    /**
     * 商户号
     */
    private String merchantName;
    /**
     * 收单终端编号
     */
    private String acquirer;
    /**
     * 操作员号
     */
    private String operatorNo;
    /**
     * 发卡行
     */
    private String issuer;
    /**
     * 收单机构编号
     */
    private String acquirerId;
    /**
     * 批次号
     */
    private String batchNumber;
    /**
     * 凭证号
     */
    private String voucherNumber;
    /**
     * 交易参考号
     */
    private String refNo;
    /**
     * 银行卡号
     */
    private String cardNo;
    /**
     * 卡有效期
     */
    private String cardExp;

    /**
     * 交易类型
     */
    private String transType;

    /**
     * 交易日期
     */
    private String transDateTime;

    /**
     * 交易金额
     */
    private String transAmount;

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getAcquirer() {
        return acquirer;
    }

    public void setAcquirer(String acquirer) {
        this.acquirer = acquirer;
    }

    public String getOperatorNo() {
        return operatorNo;
    }

    public void setOperatorNo(String operatorNo) {
        this.operatorNo = operatorNo;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAcquirerId() {
        return acquirerId;
    }

    public void setAcquirerId(String acquirerId) {
        this.acquirerId = acquirerId;
    }

    public String getBatchNumber() {
        return batchNumber;
    }

    public void setBatchNumber(String batchNumber) {
        this.batchNumber = batchNumber;
    }

    public String getVoucherNumber() {
        return voucherNumber;
    }

    public void setVoucherNumber(String voucherNumber) {
        this.voucherNumber = voucherNumber;
    }

    public String getRefNo() {
        return refNo;
    }

    public void setRefNo(String refNo) {
        this.refNo = refNo;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getCardExp() {
        return cardExp;
    }

    public void setCardExp(String cardExp) {
        this.cardExp = cardExp;
    }

    public String getTransType() {
        return transType;
    }

    public void setTransType(String transType) {
        this.transType = transType;
    }

    public String getTransDateTime() {
        return transDateTime;
    }

    public void setTransDateTime(String transDateTime) {
        this.transDateTime = transDateTime;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    @Override
    public String toString() {
        return "TransReceiptPrintEntity{" +
                "businessName='" + businessName + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", acquirer='" + acquirer + '\'' +
                ", operatorNo='" + operatorNo + '\'' +
                ", issuer='" + issuer + '\'' +
                ", acquirerId='" + acquirerId + '\'' +
                ", batchNumber='" + batchNumber + '\'' +
                ", voucherNumber='" + voucherNumber + '\'' +
                ", refNo='" + refNo + '\'' +
                ", cardNo='" + cardNo + '\'' +
                ", cardExp='" + cardExp + '\'' +
                ", transType='" + transType + '\'' +
                ", transDateTime='" + transDateTime + '\'' +
                ", transAmount='" + transAmount + '\'' +
                '}';
    }
}
