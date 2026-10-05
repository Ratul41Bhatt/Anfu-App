package com.hjq.demo.model;

/**
 * 用于组包消费8583报文实体类
 * @author yd
 */
public class SaleDataModel {
    //卡号
    private String cardNo;
    //卡AID
    private String cardAID;
    //二磁道数据
    private String track2data;
    //卡序列号
    private String cardSerialNumber;
    //卡有效期
    private String cardValid;
    //55域
    private String icData;
    //交易金额
    private String transAmount;
    //终端国家代码
    private String terminalCountryCode;
    //用户银行卡密码
    private String pinBlock;
    //授权码 手输
    private String approvalCode;

    public SaleDataModel() {
    }

    public SaleDataModel(String cardNo, String cardAID, String track2data, String cardSerialNumber, String cardValid, String icData, String transAmount, String terminalCountryCode, String pinBlock) {
        this.cardNo = cardNo;
        this.cardAID = cardAID;
        this.track2data = track2data;
        this.cardSerialNumber = cardSerialNumber;
        this.cardValid = cardValid;
        this.icData = icData;
        this.transAmount = transAmount;
        this.terminalCountryCode = terminalCountryCode;
        this.pinBlock = pinBlock;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getCardAID() {
        return cardAID;
    }

    public void setCardAID(String cardAID) {
        this.cardAID = cardAID;
    }

    public String getTrack2data() {
        return track2data;
    }

    public void setTrack2data(String track2data) {
        this.track2data = track2data;
    }

    public String getCardSerialNumber() {
        return cardSerialNumber;
    }

    public void setCardSerialNumber(String cardSerialNumber) {
        this.cardSerialNumber = cardSerialNumber;
    }

    public String getCardValid() {
        return cardValid;
    }

    public void setCardValid(String cardValid) {
        this.cardValid = cardValid;
    }

    public String getIcData() {
        return icData;
    }

    public void setIcData(String icData) {
        this.icData = icData;
    }

    public String getTransAmount() {
        return transAmount;
    }

    public void setTransAmount(String transAmount) {
        this.transAmount = transAmount;
    }

    public String getTerminalCountryCode() {
        return terminalCountryCode;
    }

    public void setTerminalCountryCode(String terminalCountryCode) {
        this.terminalCountryCode = terminalCountryCode;
    }

    public String getPinBlock() {
        return pinBlock;
    }

    public void setPinBlock(String pinBlock) {
        this.pinBlock = pinBlock;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    public void setApprovalCode(String approvalCode) {
        this.approvalCode = approvalCode;
    }

    @Override
    public String toString() {
        return "SaleDataModel{" +
                " \n  cardNo='" + cardNo + '\'' +
                ",\n  cardAID='" + cardAID + '\'' +
                ",\n  track2data='" + track2data + '\'' +
                ",\n  cardSerialNumber='" + cardSerialNumber + '\'' +
                ",\n  cardValid='" + cardValid + '\'' +
                ",\n  icData='" + icData + '\'' +
                ",\n  transAmount='" + transAmount + '\'' +
                ",\n  terminalCountryCode='" + terminalCountryCode + '\'' +
                ",\n  pinBlock='" + pinBlock + '\'' +
                '}';
    }
}
