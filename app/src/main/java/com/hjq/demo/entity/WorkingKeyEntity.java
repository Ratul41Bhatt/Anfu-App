package com.hjq.demo.entity;

/**
 * 工作秘钥实体类
 */
public class WorkingKeyEntity {
    /**
     * 秘钥ID
     * #define POS_SNK 0x00 // 加解密终端  SN号需要使用
     * #define POS_TPK 0x03 // PinKey
     * #define POS_TAK 0x04 // MacKey
     * #define POS_TDK 0x05 // TDEA decrypt key
     * #define POS_TCK 0x06 // Communication key
     */
    private int keyID;
    /**
     * 秘钥数据
     */
    private String keyData;
    /**
     * 秘钥校验值
     */
    private String keyCheckValues;

    public int getKeyID() {
        return keyID;
    }

    public void setKeyID(int keyID) {
        this.keyID = keyID;
    }

    public String getKeyData() {
        return keyData;
    }

    public void setKeyData(String keyData) {
        this.keyData = keyData;
    }

    public String getKeyCheckValues() {
        return keyCheckValues;
    }

    public void setKeyCheckValues(String keyCheckValues) {
        this.keyCheckValues = keyCheckValues;
    }

    @Override
    public String toString() {
        //联动优势秘钥说明
        //服务器返回的秘钥顺序 1:PIN,2:MAC 5:DATEKey
        //5 ：联 动优 势 datekey使 用
        String keyInfo = "";
        switch (keyID) {
            case 1:
                keyInfo = "Pin 秘钥";
                break;
            case 2:
                keyInfo = "MAC 秘钥";
                break;
            case 5:
                keyInfo = "Date Key 秘钥";
                break;
        }
        return keyInfo + "\t{" +
                "keyID='" + keyID + '\'' +
                ", keyData='" + keyData + '\'' +
                ", keyCheckValues='" + keyCheckValues + '\'' +
                '}';
    }
}
