package com.hjq.demo.entity;

import android.util.Log;

import com.blankj.utilcode.util.SPUtils;

public class MessageEvent{
    public final int printResultCode;
    public final String message;

    private SPUtils spUtils = SPUtils.getInstance("PrintResult");

    public MessageEvent(int printResultCode, String message) {
        Log.e("MessageEvent", "-----------printResultCode:" + printResultCode + " " + message);

        this.printResultCode = printResultCode;
        this.message = message;

        spUtils.clear();
        spUtils.put("PrintCode", printResultCode);
        spUtils.put("PrintMessage", message);
    }

    @Override
    public String toString() {
        return "MessageEvent{" +
                "printResultCode=" + printResultCode +
                ", message='" + message + '\'' +
                '}';
    }
}
