# -*- coding: utf-8 -*-
import os

path = r'd:\projectv1.85\projectv1\offlinepaydemo\app\src\main\java\com\hjq\demo\ui\activity\AmountActivity.java'
with open(path, 'r', encoding='utf-8') as f:
    content = f.read()

start_marker = "        String icData = sbIcData.toString() + sbMissingTAG.toString();"
end_marker = "            //User enters PIN"

start_idx = content.find(start_marker)
end_idx = content.find(end_marker)

if start_idx != -1 and end_idx != -1:
    clean_section = '''        String icData = sbIcData.toString() + sbMissingTAG.toString();
        LogUtils.i("55域内容: " + icData + "\\n");
        LogUtils.i("缺失的TAG: " + sbMissingTAG.toString() + "\\n");
        return icData;
    }


    /**
     * 弹窗显示卡号
     *
     * @param cardNo   卡号
     * @param cardType 卡类型
     *                 0：IC/插卡
     *                 1:RF/挥卡
     *                 2：MSC/刷卡
     */
    private void showReadCard(String cardNo, int cardType) {
        Message cardMsg = new Message();
        if (TextUtils.isEmpty(cardNo)) {
            String errInfo = getResources().getString(R.string.card_search_failed_ret);
            cardMsg.what = MSG_TRANS_FAil;
            Bundle cardBundle = new Bundle();
            cardBundle.putCharSequence("errInfo", errInfo);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        } else {
            cardMsg.what = MSG_TRANS_IC_RF_SUCCESS;
            Bundle cardBundle = new Bundle();
            cardBundle.putCharSequence("cardData", cardNo);
            cardBundle.putInt("cardType", cardType);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        }
    }

    /**
     * 读卡结果
     *
     * @param resultCode:结果码，0：成功，非0：失败
     * @param resultMsg：结果输出信息
     * @param cardNo：银行卡号
     * @param cardType：卡片类型
     */
    private void showReadCardResult(int resultCode, String resultMsg, String cardNo, int cardType) {
        Message cardMsg = new Message();
        Bundle cardBundle = new Bundle();
        if (resultCode == 0) {
            cardMsg.what = MSG_TRANS_IC_RF_SUCCESS;
            cardBundle.putCharSequence("cardData", cardNo);
            cardBundle.putInt("cardType", cardType);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        } else {
            String errInfo = resultMsg;
            if (TextUtils.isEmpty(errInfo)) {
                errInfo = getResources().getString(R.string.card_search_failed_ret);
            }
            cardMsg.what = MSG_TRANS_FAil;
            cardBundle.putCharSequence("errInfo", errInfo);
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
        }
    }

    private class ICardTestCallbackProxy extends ICardTestCallback.Stub {

        @Override
        public void onFindCardStart() throws RemoteException {
            LogUtils.d("开始寻卡...");
        }

        @Override
        public void onFindCardSuccess(int cardType) throws RemoteException {
            LogUtils.d("寻卡成功  Card Type: " + cardType);
        }

        @Override
        public void onTrackDataRead(byte[] track1Data, byte[] track2Data, byte[] track3Data) throws RemoteException {
            Message cardMsg = new Message();
            Bundle cardBundle = new Bundle();
            LogUtils.i("磁条卡 二磁道信息 : " + AFUtils.showResult16Str(track2Data));
            String strTrack2Data = new String(track2Data).trim();
            if (TextUtils.isEmpty(strTrack2Data)) {
                String errInfo = "Read Card Error,CardNo is empty";
                LogUtils.d(errInfo);
                cardMsg.what = MSG_TRANS_FAil;
                cardBundle.putCharSequence("errInfo", errInfo);
                cardMsg.setData(cardBundle);
                mHandler.sendMessage(cardMsg);
                return;
            }

            cardMsg.what = MSG_TRANS_MSR_SUCCESS;
            if (strTrack2Data.contains("=")) {
                cardBundle.putCharSequence("track2Data", strTrack2Data.substring(0, strTrack2Data.indexOf("=")));
                currentCardNumber = strTrack2Data.substring(0, strTrack2Data.indexOf("="));
                tipCardNumber = currentCardNumber;
            } else {
                cardBundle.putCharSequence("track2Data", strTrack2Data);
            }
            cardMsg.setData(cardBundle);
            mHandler.sendMessage(cardMsg);
            LogUtils.d("Track2 Data: " + strTrack2Data);


            //卡有效期
            if (strTrack2Data.contains("=")) {
                int tempDIndex = strTrack2Data.indexOf("=");
                //磁条卡获取卡号有效期 ---> 6225768751482231=200720115882103
                cardValid = strTrack2Data.substring(tempDIndex + 1, tempDIndex + 5);
                LogUtils.i("磁条卡有效期: " + cardValid);
            }
        }

        @Override
        public void onCardDataRead(byte[] cardData) throws RemoteException {
            LogUtils.d("Card Data: " + (cardData == null ? "null" : HexUtil.bcd2str(cardData)));
        }

        @Override
        public void onFindSamCardStart() throws RemoteException {
            LogUtils.e("onFindSamCardStart: ===========================================");
        }

        @Override
        public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
            if (errorCode == ErrorCode.SUCCESS) {
                LogUtils.d("成功");
            } else {
                String errInfo = "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\\nError Msg: " + errorMsg + "\\n";
                LogUtils.d(errInfo);
                Message cardMsg = new Message();
                cardMsg.what = MSG_TRANS_FAil;
                Bundle cardBundle = new Bundle();
                cardBundle.putCharSequence("errInfo", errInfo);
                cardMsg.setData(cardBundle);
                mHandler.sendMessage(cardMsg);
            }
        }
    }


    /**
     * 重置SP
     */
    private void resetSP() {
      /*  try {
            int ret = mSystemBinderService.resetSP();
            LogUtil.i(TAG, "重置SP结果:\\t" + (ret == 0 ? "成功" : "失败"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
    }


    /**
     * 写主密钥 DAL_POSWriteKey - DISABLED.
     *
     * <p>Wrote the Anfu demo master key to index 2. The terminal master key is
     * injected once from the admin panel and must never be replaced by anything
     * in the transaction flow - no Hypercom message replaces it either
     * (spec 6.27.2 replaces the working key, 6.19 the PIN and MAC keys). Kept
     * only so the call sites still compile; it now refuses and says so.</p>
     */
    private boolean setPOSMasterKey() {
        LogUtils.e("setPOSMasterKey: REFUSED - the terminal master key may only be "
                + "written from the admin panel. Writing the demo key here is what "
                + "erased the injected TMK after every sale.");
        return false;
    }

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }

    private SpannableString getSpannableString(String string) {
        if (StringUtils.isEmpty(string))
            return null;

        SpannableString spanString = new SpannableString(string);
        //设置字体为粗体
        spanString.setSpan(new StyleSpan(Typeface.BOLD), 0, spanString.length(), 0);
        return spanString;
    }

    private void setEditText(final String str) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setEditText(str);
                }
            });
            return;
        }

        SpannableString spannableString = getSpannableString(str);
        if (spannableString != null && spannableString.length() > 0) {
            if (!spannableString.toString().equals(etAmount.getText().toString())) {
                etAmount.setText(spannableString);
                try {
                    double amount = Double.parseDouble(spannableString.toString());
                    displayTransactionAmountASCII(amount);
                } catch (NumberFormatException e) {
                    LogUtils.e("setEditText: 金额格式异常: ");
                }
            }
        }
    }

    /**
     * 配置交易参数
     * Configure EMV transaction parameters
     * 卡片类型 0：IC卡 1：非接卡
     */
    private void emvKernelProcess() {
        saleDataModel = new SaleDataModel();
        mCardConfirmed = false;
        try {
            //emv init
            mEmvBinderService.emvKernelInit();

            //Debug EMV log switch ：1->on 0->off
            mEmvBinderService.apiEmvKernelJniPbocLogswitch(1);

            Map<String, String> map = new LinkedHashMap<>();
            // 流程类型  0 -EMV标准，1 -EMV简易，2 -Quick标准，3 -Quick简易（目前终端仅支持接触式EMV，非接触式EMV预留，Quick仅支持非接）
            //Flow Type 0 - EMV Standard, 1 - EMV Simple, 2 - Quick Standard, 3 - Quick Simple (Currently the terminal only supports contact EMV, contactless EMV is reserved, Quick only supports contactless)
            map.put("procType", "0x00");
            // 通道类型  0x00  0 -接触式，1 -非接触式  0x01
            //Channel Type 0 - Contact, 1 - Contactless
            map.put("channelType", (cardType == CardSearchType.IC_CARD ? "0x00" : "0x01"));
            // 脱机数据认证标识，仅适用于EMV标准模式 0 -不进行脱机数据认证（虚拟），1 -进行脱机数据认证
            //Offline data authentication flag, only applicable to EMV standard mode
            // 0-no offline data authentication (virtual/contactless/prepaid)
            // 1-offline data authentication
            map.put("odaFlag", "0x01");
            // 支持电子现金交易标识，仅适用于EMV标准模式 0 -不支持电子现金交易，1 -支持电子现金交易
            //Support electronic cash transaction identification, only applicable to EMV standard mode
            // 0 - does not support electronic cash transactions
            // 1 - supports electronic cash transactions
            map.put("ecFlag", "0x00");
            // 强制联机  0 -不强制，1 -强制联机
            //Force connection 0 - not forced, 1 - forced connection
            map.put("forceOnline", "0x01");
            // 强制联机PIN，仅适用于EMV标准模式  0 -不强制，1 -强制联机PIN
            //Force online PIN, only applicable to EMV standard mode 0 - not forced, 1 - force online PIN
            map.put("forceOnlinePin", "0x00");
            // 卡号确认，仅适用于EMV标准模式  0 -不执行卡号确认，1 -执行卡号确认
            //Card number confirmation, only applicable to EMV standard mode
            // 0 - do not perform card number confirmation, 1 - perform card number confirmation
            map.put("cardNoConfirm", (cardType == CardSearchType.IC_CARD ? "0x01" : "0x00"));
            // 内核重启标识  0 -内核第一次启动，1 -内核重启
            //Kernel restart flag 0 - kernel first boot, 1 - kernel restart
            map.put("restartFlag", "0x00");
            // 交易类型  n2  9C
            //Transaction type n2 9C
            map.put("transType", "0x00");
            // 交易货币指数 n1  5F36
            //Trading CurrencyIndex n1 5F36
            map.put("currencyExp", "0x00");
            // 商户名称长度  b 1B
            //Merchant name length b 1B
            map.put("merNameLen", "0x07");
            //商户名称 ans Var.最大长度未知 9F4E
            //Merchant Name ans Var. Maximum length unknown 9F4E
            String tempMerName = AFUtils.padStringToBytes("Barkey", 40);
            map.put("merName", AFUtils.showResult0xStr(AFUtils.StringToByteArray(tempMerName)));
            //交易流水号 n6  9F41
            //Transaction serial number n6 9F41
            map.put("posSer", "0x00,0x00,0x01");
            byte[] amount = AFUtils.hexString2ByteArray(AFUtils.dealMoneyStr(currentAmount));
            String tempAmount = AFUtils.showResult0xStr(amount);
            transAmount = AFUtils.showResult16Str(amount);
            LogUtils.e("tempAmount" + tempAmount);
            //交易金额 n12  9F02
            //Transaction amount n12 9F02
            map.put("transAmt", tempAmount);
            // 其他金额 n12  9F03
            //Other amount n12 9F03
            map.put("otherAmt", "0x00,0x00,0x00,0x00,0x00,0x00");
            // 交易货币代码 n3  5F2A
            //Transaction currency code n3 5F2A
            map.put("currencyCode", "0x00,0x50");
            byte[] date = AFUtils.hexString2ByteArray(SDF_TAG_9A_YYDD.format(new Date()));
            LogUtils.e("emvKernelProcess: date" + AFUtils.showResult0xStr(date));
            // 交易日期 n6  YYMMDD  9A
            //Transaction date n6 YYMMDD 9A
            map.put("transDate", AFUtils.showResult0xStr(date));
            byte[] time = AFUtils.hexString2ByteArray(SDF_TAG_9A_HMS.format(new Date()));
            // 交易时间 n6  hhmmss    9F21
            //Transaction time n6 hhmmss 9F21
            map.put("transTime", AFUtils.showResult0xStr(time));
            // 商户号 ans15  9F16
            //Merchant number ans15 9F16
            map.put("merId", AFUtils.showResult0xStr(AFUtils.StringToByteArray("110000003517177")));
            // 终端号 an8  9F1C
            //Terminal number an8 9F1C
            map.put("termId", AFUtils.showResult0xStr(AFUtils.StringToByteArray("10000985")));
            // 附加数据实际有效长度
            //Additional data length, actual valid data length
            map.put("additionLen", "0x24");
            String strAddition = "FF6001009F3501229F3303E0F0C89F40056000F0E0019F1A0200505F2A020050DF690100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
            byte[] additionByte = AFUtils.hexString2ByteArray(strAddition);
            LogUtils.i("additionByte:  16Str " + AFUtils.showResult16Str(additionByte));
            LogUtils.i("additionByte: 0xStr" + AFUtils.showResult0xStr(additionByte));
            // 附加数据，附加数据以TLV格式存在
            //Additional data, which exists in TLV format
            map.put("addition", AFUtils.showResult0xStr(additionByte));

            StringBuilder sbEmvParam = new StringBuilder();
            sbEmvParam.setLength(0);

            for (Map.Entry<String, String> itemTAG : map.entrySet()) {
                LogUtils.i("emvParam key->: " + itemTAG.getKey() + "\\t data-->" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()) + "\\t 字节数:" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()).length() / 2);
                sbEmvParam.append(AFUtils.removeAllHexPrefixes(itemTAG.getValue()));
            }

            LogUtils.e("apiEmvKernelProcess Data--> " + sbEmvParam.toString());

            LogUtils.e("apiEmvKernelProcess Data Length--> " + sbEmvParam.toString().length() / 2);

            byte[] emvParamByte = AFUtils.hexString2ByteArray(sbEmvParam.toString());
            LogUtils.e("emvParam Len : " + emvParamByte.length);


'''
    new_content = content[:start_idx] + clean_section + content[end_idx:]
    with open(path, 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Successfully replaced section!")
else:
    print(f"Indices not found: start_idx={start_idx}, end_idx={end_idx}")
