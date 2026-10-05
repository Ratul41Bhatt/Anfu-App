package com.hjq.demo.utils;

import android.os.RemoteException;
import android.util.Log;


import androidx.annotation.Keep;

import com.hjq.demo.entity.WorkingKeyEntity;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.KeyInfoParam;
import com.szanfu.sdk.api.pinpad.PosGetKcvResult;

import java.util.Arrays;
@Keep
public class KeyManager {

    private final String TAG = "KeyManager";
    final int MIN_MAINKEY_INDEX = 2;
    final int MAX_MAINKEY_INDEX = 10;
    public static final int mainkey_index = 2;

    /**
     * posWriteWorkingKey ucMode values (AFSDKService doc 4.4.3).
     *
     * <p>{@code 0x00} writes the key data verbatim - use it when the caller
     * already holds the CLEAR key. {@code 0x01} says the key data arrives
     * enciphered and the pad must DES-decrypt it under the source key (the TMK
     * named by the {@code keyIndex} argument) before storing it.</p>
     */
    public static final int MODE_WRITE_CLEAR = 0x00;
    public static final int MODE_DES_DECRYPT = 0x01;

    IPinPadBinderService mPinPadBinderService;

    public KeyManager(IPinPadBinderService pinPadBinderService) {
        mPinPadBinderService = pinPadBinderService;
    }

    /**
     * Anfu demo key bootstrap - REMOVED, now a no-op.
     *
     * <p>This used to write a hard-coded master key
     * ({@code 303132...3F}) and a hard-coded PIN key on start-up. Every call
     * overwrote the terminal master key injected from the admin panel, and the
     * PIN key it wrote was an Anfu demo value the acquirer knows nothing
     * about.</p>
     *
     * <p>The terminal master key now comes only from the admin panel, and the
     * working key only from the DE62 logon response (Hypercom spec 6.27.2).</p>
     *
     * @deprecated no longer loads anything. Inject the TMK from the admin panel
     *             and run a logon to receive the working key.
     */
    @Deprecated
    public int setupTerminalKeys() {
        Log.e(TAG, "setupTerminalKeys: REFUSED - demo key bootstrap removed. "
                + "Inject the TMK from the admin panel, then run a logon.");
        return -1;
    }

    /**
     * @param mode {@link #MODE_WRITE_CLEAR} when {@code workingKeyEntity} holds
     *             the clear key, {@link #MODE_DES_DECRYPT} when it holds the
     *             key enciphered under the TMK at {@code keyindex}.
     */
    private int pinpad_LoadWorkingKey(int keyindex, WorkingKeyEntity workingKeyEntity, int mode) {

        if(keyindex < MIN_MAINKEY_INDEX || keyindex > MAX_MAINKEY_INDEX) {
            Log.e(TAG, "invalid key index parameter " + keyindex);
            return -1;
        }

        int ret = -1;
        try {
            Log.e(TAG, "");
            Log.e(TAG, "   keyindex=" + keyindex);
            Log.e(TAG, "   key_enc=" + workingKeyEntity.getKeyData());
            Log.e(TAG, "   kcv=" + workingKeyEntity.getKeyCheckValues());
            //Log.e(TAG, "   keyId=" + workingKeyEntity.getKeyID() + " (" + workingKeyEntity.getKeyIDString() + ")");

            Log.e(TAG, "   mode=0x" + Integer.toHexString(mode)
                    + (mode == MODE_DES_DECRYPT ? " (key is enciphered under the TMK)"
                                                : " (key is clear, written verbatim)"));

            String key_str = workingKeyEntity.getKeyData();
            int keyLen = key_str.length()/2; // 8 or 16 or 24

            String kcv_str = workingKeyEntity.getKeyCheckValues();

            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            if(kcv_str == null || kcv_str.length() == 0) {
                kcvInfoParam.setCheckMode(0); //won't check KCV
            } else {
                kcvInfoParam.setCheckMode(1); //need check KCV
                kcvInfoParam.setCheckBuf(HexSupport.toBytesFromHex(kcv_str));
            }

            //get working key type
            int keyId = workingKeyEntity.getKeyID();

            //load working key
            /**
             * int posWriteWokerKey(int usKeyType, int ucKeyLen, in byte[] keyData, in KcvInfoParam kcvInfoParam, int ucMode, int usMKeyNo);
             *
             * 更新工作秘钥接口
             * @param usKeyType 目的密钥类型
             *              POS_TPK 0x03 // PinKey
             *              POS_TAK 0x04 // MacKey
             *              POS_TDK 0x05 // TDEA decrypt key
             *              POS_TCK 0x06 // account encryption or decryption key
             * @param ucKeyLen 秘钥数据长度
             * @param keyData 秘钥数据
             * @param kcvInfoParam
             * @param ucMode    加解密模式
             *                  0x00: 表示直接写入主密钥
             *                  0x01：使用DES算法解密
             *                  0x81: 使用DES算法加密
             *                  0x41: 使用SM4算法解密
             *                  0xC1: 使用SM4算法加密
             * @param usMKeyNo  源密钥索引
             *                   POS_TMK 0x02 // master key主密钥
             *                   POS_TPK 0x03 // PinKey
             *                   POS_TAK 0x04 // MacKey
             *                   POS_TDK 0x05 // TDEA decrypt key
             *                   POS_TCK 0x06 // Communication key
             */
            ret = mPinPadBinderService.posWriteWorkingKey(keyId,
                    keyLen,
                    HexSupport.toBytesFromHex(key_str),
                    kcvInfoParam,
                    mode,
                    keyindex);
            Log.e(TAG, "   ret=" + ret);

            //read out the kcv from pinpad to double confirm
            if (ret == 0) {
                try {

                    /**
                     *  PosGetKcvResult posGetKcv(int keyIdx, int keyType);
                     *
                     * 获取秘钥的Check Value 数据
                     * @param keyIdx 秘钥索引【2,10】
                     * @param keyType 秘钥类型
                     *                POS_TLK 0x01 // Loading Key传输秘钥
                     *                POS_TMK 0x02 // master key主密钥
                     *                POS_TPK 0x03 // PinKey
                     *                POS_TAK 0x04 // MacKey
                     *                POS_TDK 0x05 // TDEA decrypt key
                     *                POS_TCK 0x06 // Communication key
                     */
                    PosGetKcvResult posGetKcvResult = mPinPadBinderService.posGetKcv(keyindex, keyId);
                    int getKcvResultRet = posGetKcvResult.getRet();
                    Log.e(TAG, "posGetKcv()");
                    Log.e(TAG, "      keyindex=" + keyindex);
                    Log.e(TAG, "      keyId=" + keyId);
                    Log.e(TAG, "      ret=" + getKcvResultRet);

                    if (getKcvResultRet == 0) {
                        //kcvInfoParam.getCheckBuf();
                        //getkcv=00080000F26C1AD100000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000
                        byte[] getkcv = posGetKcvResult.getKcvInfo();
                        //getkcv_str=F26C1AD1  (it's the 4 bytes kcv got from pinpad
                        String getkcv_str = HexSupport.toHexFromBytesEx(getkcv, 4, 4);
                        Log.e(TAG, "      kcv=" + kcv_str);

                        //Optional:  you can compare the "kcv from pinpad" and the "kcv from host"
                        if(true) {
                            if (kcv_str == null || kcv_str.length() == 0) {
                                Log.e(TAG, "   kcv match (no kcv)");
                            } else if (kcv_str.length() <= 8) {
                                if (getkcv_str.toUpperCase().startsWith(kcv_str.toUpperCase()))
                                    Log.e(TAG, "   kcv match");
                                else
                                    Log.e(TAG, "   !!! kcv unmatch !!!");
                            } else { // >8
                                if (kcv_str.toUpperCase().startsWith(getkcv_str.toUpperCase()))
                                    Log.e(TAG, "   kcv match");
                                else
                                    Log.e(TAG, "   !!! kcv unmatch !!!");
                            }
                        }
                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

        } catch (RemoteException e) {
            e.printStackTrace();
            ret = -1;
        }
        return ret;
    }

    private int pinpad_LoadMasterKey(int keyindex, String mainkey_str) {

        if(keyindex < MIN_MAINKEY_INDEX || keyindex > MAX_MAINKEY_INDEX) {
            Log.e(TAG, "invalid index parameter" + keyindex);
            return -1;
        }
        if(mainkey_str == null || (mainkey_str.length() != 32 && mainkey_str.length() != 48)) {
            Log.e(TAG, "invalid (key str) parameter");
            return -1;
        }

        try {
            KeyInfoParam keyInfoParam = new KeyInfoParam();
            keyInfoParam.setDstKeyIdx((short) keyindex);
            keyInfoParam.setDstKeyLen(mainkey_str.length()/2);   //TDES
            keyInfoParam.setDstKeyType((byte) 0x02);  //Main Key, don't change this value
            keyInfoParam.setDstKeyValue(HexSupport.toBytesFromHex(mainkey_str)); //16 bytes plaintext main key
            keyInfoParam.setSrcKeyIdx((short) 0);   //don't change this value
            keyInfoParam.setSrcKeyType((byte) 0x01);    //don't change this value

            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            kcvInfoParam.setCheckMode(0);   //not check KCV

            int ret =  mPinPadBinderService.posMasterKey(keyInfoParam, kcvInfoParam);
            Log.e(TAG, "pinpad_LoadMasterKey"+ "   index=" + keyindex +"   Key=" + mainkey_str + "   ret=" + ret);
            return ret;

        } catch (RemoteException e) {
            e.printStackTrace();
            Log.e(TAG, "   ret=" + e.getMessage());
            return -1;
        }
    }

    /** Manual injection entry points used by the admin panel. */
    public int loadMasterKey(int index, String hex) {
        return pinpad_LoadMasterKey(index, hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", ""));
    }

    /**
     * Admin panel: inject a working key that is already in the CLEAR.
     *
     * <p>Written with mode 0x00, so the pad stores exactly the bytes given.</p>
     */
    public int loadWorkingKey(int index, int keyId, String keyHex, String kcv) {
        return loadWorkingKey(index, keyId, keyHex, kcv, MODE_WRITE_CLEAR);
    }

    /**
     * Inject a working key.
     *
     * @param mode {@link #MODE_WRITE_CLEAR} for a clear key,
     *             {@link #MODE_DES_DECRYPT} when {@code keyHex} is enciphered
     *             under the terminal master key at {@code index} - which is how
     *             the key in DE62 of the logon response arrives
     *             (Hypercom spec 6.27.2).
     */
    public int loadWorkingKey(int index, int keyId, String keyHex, String kcv, int mode) {
        WorkingKeyEntity e = new WorkingKeyEntity();
        e.setKeyID(keyId);
        e.setKeyData(keyHex == null ? "" : keyHex.replaceAll("[^0-9A-Fa-f]", ""));
        e.setKeyCheckValues(kcv == null ? "" : kcv.replaceAll("[^0-9A-Fa-f]", ""));
        return pinpad_LoadWorkingKey(index, e, mode);
    }

    public String readKcv(int index, int keyType) {
        try {
            PosGetKcvResult r = mPinPadBinderService.posGetKcv(index, keyType);
            if (r == null) {
                return "null";
            }
            if (r.getRet() != 0) {
                return "ret=" + r.getRet();
            }
            // 3 bytes / 6 hex chars - the standard KCV length, matching what PAX
            // reads back and the format on DBBL's key ceremony sheet (e.g. 65044C).
            return HexSupport.toHexFromBytesEx(r.getKcvInfo(), 4, 3);
        } catch (Exception e) {
            return "err:" + e.getMessage();
        }
    }

    /**
     * Diagnostic: recover the clear ISO 9564-0 PIN field from an encrypted PIN
     * block, given the clear PIN key.
     *
     * <p>The clear key used to be a hard-coded Anfu demo value, which made this
     * lie about every real transaction. Pass the working key you actually hold -
     * i.e. {@code 3DES-decrypt(DE62, TMK)} - or use
     * {@link com.hjq.demo.ans8583.OnlineHost#checkPinBlock} which asks the pad
     * to do it and needs no clear key at all.</p>
     *
     * @param pinKeyClear the clear PIN/working key as hex, 32 or 48 characters
     */
    public String decryptPinBlock(String pinblock_enc, String card_pan, String pinKeyClear) {
        Log.e(TAG, "DecryptPinBlock ===========================");

        if (pinKeyClear == null || pinKeyClear.isEmpty()) {
            Log.e(TAG, "decryptPinBlock: no clear PIN key supplied");
            return null;
        }
        try {
            byte[] pinblock_plain = TriDES.des3DecodeECB(HexSupport.toBytesFromHex(pinKeyClear), HexSupport.toBytesFromHex(pinblock_enc));

            int pan_len = card_pan.length();
            String pan_slice =card_pan.substring(pan_len-13, pan_len-1);

            byte pan_slice_hex[] = new byte[8];
            Arrays.fill(pan_slice_hex, (byte)0x00);
            System.arraycopy(HexSupport.toBytesFromHex(pan_slice), 0, pan_slice_hex, 2, 6);

            for(int i=0; i<8; i++)
                pinblock_plain[i] = (byte)(pinblock_plain[i] ^ pan_slice_hex[i]);

            return HexSupport.toHexFromBytes(pinblock_plain);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}
