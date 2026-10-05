package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.TimeUtils;
import com.gyf.immersionbar.ImmersionBar;
import com.hjq.bar.TitleBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.entity.LcdInfo;
import com.hjq.demo.entity.MessageEvent;
import com.hjq.demo.entity.TransReceiptPrintEntity;
import com.hjq.demo.entity.WorkingKeyEntity;
import com.hjq.demo.ilistener.MainListener;
import com.hjq.demo.inter.AFSwipeControllerListener;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.ans8583.BatchStore;
import com.hjq.demo.ans8583.Emv55;
import com.hjq.demo.ans8583.Iso8583;
import com.hjq.demo.ans8583.OfflinePacket8583;
import com.hjq.demo.ans8583.OnlineHost;
import com.hjq.demo.ans8583.ResponseCode;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.utils.CardMask;
import com.hjq.demo.utils.CardRangeTable;
import com.hjq.demo.ans8583.TxnRecord;
import android.speech.tts.TextToSpeech;
import com.hjq.demo.model.MainModel;
import com.hjq.demo.model.MainModelImpl;
import com.hjq.demo.model.SaleDataModel;
import com.hjq.demo.model.TransOrder;
import com.hjq.demo.ui.dialog.InputDialog;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.ui.dialog.PayPasswordDialog;
import com.hjq.demo.ui.dialog.SignatureDialog;
import com.hjq.demo.ui.dialog.TipsDialog;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.ui.view.KeyBoardView;
import com.hjq.demo.widget.DynamicPinPad;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.AidChoiceDialog;
import com.hjq.demo.utils.AppPreferences;
import com.hjq.demo.utils.BitmapUtil;
import com.hjq.demo.utils.CardSearchType;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.utils.IntToBytesUtils;
import com.hjq.demo.utils.LcdUtils;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.PowerReceiver;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.TLVModel;
import com.hjq.demo.utils.TouchHelper;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.card.ICardBinderService;
import com.szanfu.sdk.api.card.ICardTestCallback;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.emv.EmvGetKernelProgressResult;
import com.szanfu.sdk.api.emv.IEmvBinderService;
import com.szanfu.sdk.api.emv.IKernelEmvCallback;
import com.szanfu.sdk.api.emv.IKernelEmvLicenseCallback;
import com.szanfu.sdk.api.emv.IKernelEmvSystemTimerCallback;
import com.szanfu.sdk.api.emv.IKernelEmvlcdPrintExCallback;
import com.szanfu.sdk.api.emv.ISdkKernelEmvAppCallback;
import com.szanfu.sdk.api.emv.ISdkKernelEmvInputPINCallback;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.IPinPadCallback;
import com.szanfu.sdk.api.pinpad.KcvInfoParam;
import com.szanfu.sdk.api.pinpad.KeyInfoParam;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.system.ISystemEncDecBinderService;
import com.szanfu.sdk.api.system.KeypadValueCallback;
import com.szanfu.sdk.api.util.DataConversionUtils;
import com.szanfu.sdk.api.util.HexUtil;

import org.apache.commons.lang3.StringUtils;
import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static android.graphics.Bitmap.createBitmap;

public final class FindCardActivity extends AppActivity {
    private static final String TAG = "FindCardActivity";
    /**
     * One card search, in SECONDS.
     *
     * <p>openFindCardForEMV takes seconds, not milliseconds: the Anfu demo
     * passes 30, and the home screen's search armed with 30 came back after
     * 30 019 ms in the field log. This was 30 * 1000 - a search of more than
     * eight hours - and closeFindCardForEMV does not break a search that is
     * already open on this firmware. So a cancelled card screen left its search
     * running, holding the reader, and the next sale sat on "Waiting for card"
     * until the app was restarted.</p>
     *
     * <p>Short on purpose (was 8). While the screen is up an empty search is re-armed
     * quietly (see the timeout branch in startSearchCardInternal), so the
     * cardholder sees no difference; but a search left behind by a screen that
     * has gone ends within this many seconds and gives the reader back.</p>
     */
    public static final int FIND_CARD_TIMEOUT = 5;
    /** Preserve the independent getMSRCardInfo API's existing timeout (seconds). */
    private static final int MSR_CARD_TIMEOUT = 30;
    private int mCardReadAttempt;
    private ICardBinderService mCardBinderService;
    private IEmvBinderService mEmvBinderService;
    private static ISystemBinderService mSystemBinderService;
    private IPinPadBinderService mPinPadBinderService;
    private IPrinterBinderService mPrinterBinderService;
    private com.hjq.demo.ui.dialog.ReceiptPreviewDialog mReceiptPreview;
    private final com.hjq.demo.utils.ReceiptFlow mReceiptFlow = new com.hjq.demo.utils.ReceiptFlow();
    private boolean mReceiptFlowFinished;
    private volatile boolean mShowingTxnStage;
    private final Runnable mGoHomeRunnable = new Runnable() {
        @Override
        public void run() {
            goHome();
        }
    };
    private ISystemEncDecBinderService mISystemEncDecBinderService;
    private static final int MSG_TRANS_MSR_SUCCESS = 0;
    private static final int MSG_TRANS_IC_RF_SUCCESS = 1;
    private static final int MSG_TRANS_FAil = 2;
    private static final int MSG_SHOW_SIGNATURE_DIALOG = 3;

    public static final String EXTRA_TRANS_TYPE = "TRANS_TYPE";
    /** Invoice number of the transaction a VOID is cancelling. */
    public static final String EXTRA_VOID_INVOICE = "VOID_INVOICE";
    /**
     * A scanned "Nexus (KonaMoney)" QR: 40 digits, 37 of track-2 plus a 3-digit
     * dCVV_MD. When this is present there is no card to look for - the
     * credential arrived on the customer's phone screen - so the reader is
     * never armed and the transaction goes straight online.
     */
    public static final String EXTRA_KONA_QR = "KONA_QR";
    /** EMI term in months, chosen before the card is read. */
    public static final String EXTRA_EMI_TENURE = "EMI_TENURE";
    /** EMI product code (DE48 tag E01) for the chosen plan. */
    public static final String EXTRA_EMI_PRODUCT = "EMI_PRODUCT";

    /**
     * Which EMI product the operator chose: 0 = 0% Insta Pay, 1 = Easy Buy.
     * It is the first digit of DE61, so it travels with the tenure.
     */
    public static final String EXTRA_EMI_TYPE = "EMI_TYPE";
    private String transType = "SALE";
    private TextToSpeech mTts;
    private boolean mTtsReady = false;
    private String mPendingSpeak = null;

    /** How long the "card not supported" message stays on screen. */
    private static final long UNSUPPORTED_CARD_MS = 3000L;
    private static final int SLIP_W = 32;
    private static final String SLIP_SEP = "--------------------------------";
    /** Rule of asterisks, used around the build block on reports. */
    private static final String STAR_SEP = "********************************";
    private static final int F_BOLD = 1, F_CENTER = 2, F_SEP = 4, F_BLANK = 8, F_LOGO = 16;
    private static final int R_BIG = 40, R_MID = 30, R_SMALL = 25;
    // The merchant letterhead now lives in TerminalConfig, where the reports
    // can see it too - see addLetterhead(). The defaults it falls back to are
    // TerminalConfig.DEF_MERCHANT_NAME / DEF_ADDR_1 / DEF_ADDR_2, so an
    // unconfigured terminal still prints what it printed before.
    private static final String TVR = "0000000000";
    private static final String TSI = "0000";

    private static class RLine {
        final String left, right;
        final int size, flags;
        RLine(String left, String right, int size, int flags) {
            this.left = left == null ? "" : left;
            this.right = right == null ? "" : right;
            this.size = size;
            this.flags = flags;
        }
    }

    private final java.util.List<RLine> receiptLines = new java.util.ArrayList<>();
    private String mDate, mTime, mId, mApprCode, mInvoice, mBatch, mPan, mIssuer, mAid, mExp,
            mTitle, mOrigRef, mInstallment, mStan, mRref, mArqc, mAppl, mEntry, mCardMasked;
    /** Cardholder name, EMV tag 5F20, printed under the signature line. */
    private String mCardholder = "";
    /**
     * Remaining prepaid balance from DE54, already formatted for printing.
     *
     * <p>Only a prepaid scheme sends one. Kona Money's spec 2.2 requires it on
     * the screen and the receipt; every other card leaves it empty and the
     * line is skipped.</p>
     */
    private String mBalance = "";
    /** The scanned KonaMoney QR, split into track-2 and dCVV_MD. Null otherwise. */
    private com.hjq.demo.ans8583.KonaMoney.Qr mKonaQr;
    /** "TC", "ARQC" or "AAC" - what the cryptogram in 9F26 actually is. */
    private String mAcLabel = "TC";
    private int mTenure;
    private boolean mHostOnline;

    // ---- receipt state derived from the 0210 response
    /** The exchange this receipt describes. */
    private OnlineHost.HostResult mHostResult;
    /** DE39 as returned, empty when the host never answered. */
    private String mRespCode = "";
    /** Text to print: spec section 8 table, or host DE63 table '22' when >= 20 chars. */
    private String mRespText = "";
    private boolean mApproved;
    private boolean mReversed;
    /** EMV values taken from the DE55 actually sent. */
    private String mTvr = "", mTsi = "", mAtc = "", mCvm = "";
    private boolean mIssuerAuth;
    private boolean mScriptRun;
    /** The chip refused at the second Generate AC, whatever the host said. */
    private boolean mCardDeclined;

    /**
     * The chip returned an AAC at GAC2, whatever the host said.
     *
     * <p>Separate from {@link #mCardDeclined}, which now means only the one
     * case that is genuinely the card's doing. When the host declines, the
     * terminal hands that decision to the chip and the chip answers AAC -
     * correctly, because it was told the issuer said no. Treating that as a
     * card decline replaced "ISSUER INOPERATIVE" or "DO NOT HONOUR" on the
     * screen and the slip with "CARD DECLINED TRANSACTION", which sends the
     * merchant to the wrong place: they change the card instead of retrying,
     * and the response code that would have told them what happened never
     * gets shown.</p>
     */
    private boolean mGac2Refused;
    /**
     * The chip refused at GAC2 <em>after</em> the host approved - the case
     * where the issuer will settle a sale the merchant was told failed. Kept
     * separate from {@link #mCardDeclined} because only this one warrants a
     * reversal and the "verify with bank" warning on the slip.
     */
    private boolean mHostApprovedCardRefused;
    /** Why the second Generate AC did not complete - printed under Debug. */
    private String mGac2Reason = "";
    /** CA public key index (tag 8F) the card asked for - the ODA diagnostic. */
    private String mCapkIndex = "";
    /** True when the host-approved, card-declined transaction was reversed. */
    private boolean mCardDeclineReversed;

    /** True when the card left the slot while the host was answering. */
    private volatile boolean mCardLeftMidFlight;
    /** True when the PIN just entered was an ONLINE PIN, so DE52 should carry it. */
    private boolean mOnlinePin = true;

    /**
     * The EMV kernel's own CVM decision for this transaction, from byte 3 of
     * the GAC1 result. Empty when the kernel did not report one.
     */
    private volatile String mKernelCvm = "";

    /** Readable name for the kernel's CVM verdict, for the log. */
    private static String kernelCvmName(String uc) {
        if (uc == null) {
            return "none";
        }
        switch (uc) {
            case "00": return "no CVM required";
            case "10": return "signature";
            case "20": return "ONLINE PIN";
            case "30": return "confirmation code verified";
            case "F0": return "N/A";
            default:   return "unknown 0x" + uc;
        }
    }
    /** Invoice of the entry a VOID is cancelling; set by the void flow. */
    private String mVoidOrigInvoice;
    /** Details of the transaction a void is cancelling, for the slip. */
    private String mOrigRrn = "", mOrigAuth = "", mOrigDateTime = "";
    /** EMI plan chosen on the previous screen, 0 / null for an ordinary sale. */
    private int mEmiTenure;
    private String mEmiProduct;
    private int mEmiType;
    /** EMI plan name as authorised, appended to the title line on the slip. */
    private String mSlipEmiPlan = "";
    /** The entry a void is cancelling, kept so the slip can read its EMI plan. */
    private TxnRecord mVoidOrigRecord;
    private String mHostRc, mHostAuth, mHostRrn, mPinBlockHex;
    protected ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
    //当前卡号
    private static String currentCardNumber;
    //Tip Card Number
    private static String tipCardNumber;
    //cardValid
    private String cardValid;
    //当前金额
    private static String currentAmount;
    private MainModel mainModel;
    public static final int MAX_BIT_WIDTH = 300;
    public static final int MAX_BIT_HEIGHT = 200;
    public static final long MIN_SIGNATURE_LIMIT = 20000;
    public static final long MAX_SIGNATURE_LIMIT = 200000;
    StringBuffer sbMsg = new StringBuffer();

    private static SimpleDateFormat SDF_TAG_9A_YYDD = new SimpleDateFormat("yyMMdd");//交易日期
    private static SimpleDateFormat SDF_TAG_9A_HMS = new SimpleDateFormat("HHmmss");//交易时间
    private static SimpleDateFormat SDF_TAG_9A = new SimpleDateFormat("yyMMddHHmmss");//交易日期

    private static SimpleDateFormat SDF_DEVICES_TEST = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    //设备是否需要签到
    private SPUtils spUtils = SPUtils.getInstance("SP_SignTime");
    //初始金额
    private static final String INITIAL_AMOUNT_OF_MONEY = "0.00";

    private SaleDataModel saleDataModel = null;

    PowerReceiver mPowerReceiver = null;

    PayPasswordDialog.Builder payPasswordDialog;
    SignatureDialog.Builder signatureDialog;

    private Bitmap signatureBmp;
    private String signature;

    private String icCardNumber;

    private boolean isSearchingCard = false;
    private static final int ERROR_LOW_BATTERY = -3006;
    private static final int ERROR_PRINT_OUT_OF_PAPER = -4002;
    private static final int ERROR_PRINTER_OVER_TEMPERATUR = -4005;

    /**
     * 交易模式
     * Trading mode
     * true:联机交易 false 脱机交易
     * <p>
     * true :online trading  false :offline trading,
     */
    public static final boolean FORMAL_TRANSACTION = false;
    /**
     * 今日签到状态
     */
    private static boolean signedStatus = false;


    /**
     * 交易金额
     * Amount of the transaction
     */
    private String transAmount;
    private long mTipBaseMinor = -1, mTipMinor;
    /**
     * 重启内核
     * Transaction Kernel Restart
     */
    public static final int ERR_KERNEL_RESTART = -8;

    /**
     * 交易小票数据
     * Transaction receipt information
     */
    private TransReceiptPrintEntity printEntity = null;

    /**
     * 当前交易卡类型
     */
    private int cardType;

    //确认键事件类型
    private int KEYCODE_ENTER_ACTION = -1;
    //发起寻卡交易
    private static final int KEYCODE_ENTER_START_SEARCH_CARD = 0;
    //输入PIN
    private static final int KEYCODE_ENTER_INPUT_PIN = 1;

    //设备类型 820
    private boolean keyboardDevice;

    private String customerType;


    private volatile boolean stopFindCard = false;  //停止寻卡
    private boolean isShowCardNo = false;   //如果已经显示卡号，stop 之后重新打开页面不调用 startSearchCard

    private SPUtils spPrint = SPUtils.getInstance("PrintResult");

    private boolean isLcdSupported;
    private LcdUtils lcdUtils;
    private LcdInfo lcdInfo;

    private void unRegisterBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unregisterReceiver(mPowerReceiver);
            mPowerReceiver = null;
        }

        LogUtils.i("onDestroy: isSearchingCard->" + (isSearchingCard ? "正在寻卡" : "空闲状态"));
        stopFindCard = true;
        mCardSearchQueue.cancel();
        stopChipSlotWatch();
        mChipDialogUp = false;
        isSearchingCard = false;
        // Only close the reader while THIS screen still owns it. Once a newer
        // card screen has taken it, a close from here - onDestroy can land
        // seconds after the next sale has started - cancels the new screen's
        // search and leaves it on PRESENT CARD reading nothing.
        int holder = com.hjq.demo.utils.CardReaderAccess.holderSession();
        if (holder == 0 || holder == mReaderSession) {
            LogUtils.e("unRegisterBroadCastReceiver: 关闭EMV寻卡");
            closeReaderForExit("screen closing");
        } else {
            LogUtils.i("unRegisterBroadCastReceiver: reader now belongs to session "
                    + holder + " - not closing it");
        }

    }


    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mGoHomeRunnable);
        if (mReceiptPreview != null) mReceiptPreview.dismiss();
        super.onDestroy();
        // Looping animators hold a reference to the views they drive, and the
        // views hold the activity. Cancel them or the whole screen leaks.
        stopCardPrompt();
        // A watchdog that fires after the screen has gone would cancel a
        // transaction that is no longer this screen's to cancel.
        stopPinWatchdogOnExit();
        LogUtils.e("**************************【onDestroy】************************** ");
      /*  try {
            mSystemBinderService.closeGetKeypadValue();
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
        unRegisterBroadCastReceiver();
        releaseKernelPinWait("screen destroyed");
        com.hjq.demo.utils.CardReaderAccess.sessionEnded(mReaderSession);
        if (mTts != null) {
            try { mTts.stop(); mTts.shutdown(); } catch (Exception ignore) {}
            mTts = null;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        LogUtils.e("**************************【onRestart】**************************  ");
//        getKeyCode();
        LogUtils.e("---------isShowCardNo: " + isShowCardNo);
        if (!isShowCardNo) {     //还没到显示卡号页面，重新寻卡
            stopFindCard = false;
            startSearchCard(enabledSearchMask());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogUtils.e("**************************【onStop】************************** ");
        // A dialog still showing when the activity stops is a leaked window.
        dismissCardReading();
       /* try {
            mSystemBinderService.closeGetKeypadValue();
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
        unRegisterBroadCastReceiver();
    }

    private void registerBroadCastReceiver() {
        if (mPowerReceiver != null) {
            unRegisterBroadCastReceiver();
        }
        mPowerReceiver = new PowerReceiver(new PowerReceiver.DeviceStatusMonitoring() {
            @Override
            public void onShutDown() {
                LogUtils.i("onShutDown: --->Phone off.....");
                //spUtils.put(END_TEST_TIME, SDF_DEVICES_TEST.format(new Date()));
            }
        });
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.intent.action.ACTION_SHUTDOWN");
        intentFilter.addAction("android.intent.action.REBOOT");
        registerReceiver(mPowerReceiver, intentFilter);
        LogUtils.i("registerBroadCastReceiver: 已经注册广播...");
    }

    public static ISystemBinderService getSystemSingleton() {
        return mSystemBinderService;
    }

    /**
     * Populate the receipt fields from what actually happened.
     *
     * <p>Everything here comes from the message the terminal sent
     * ({@code mHostResult.record}) or the 0210 it got back. Nothing is invented:
     * a value the host did not supply prints as blank or is omitted, so the slip
     * can never show an approval code the issuer never gave.</p>
     */
    private void prepareTxnData() {
        long currentDate = DateUtil.getTimeStamp(new Date(), 0);
        String dateTime = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT, 0);
        // dd-MM-yyyy, matching the reference slip and matching what
        // TxnSlip.of() rebuilds from DE13 for a reprint. The two have to agree
        // or a reprint is a visibly different document.
        mDate = com.hjq.demo.utils.TxnSlip.todayForSlip();
        mTime = dateTime.length() > 11 ? dateTime.substring(11)
                : com.hjq.demo.utils.TxnSlip.nowForSlip();
        mId = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);

        OnlineHost.HostResult r = mHostResult;
        TxnRecord rec = r == null ? null : r.record;
        TerminalConfig tc = TerminalConfig.get(getActivity());

        // ---- identifiers as sent / as returned
        mStan = rec != null && !rec.stan.isEmpty() ? rec.stan : "";
        mInvoice = rec != null && !rec.invoice.isEmpty() ? rec.invoice : "";
        mBatch = tc.batchNo();
        mApprCode = r != null ? r.authCode : "";      // DE38, blank if none
        mRref = r != null ? r.rrn : "";               // DE37, blank if none

        // ---- host verdict, per spec section 8
        mRespCode = r != null ? r.responseCode : "";
        mReversed = r != null && r.needsReversal;
        if (mCardDeclined) {
            // The host said yes but the chip said no at GAC2, so this is not an
            // approval however good DE39 looked.
            mApproved = false;
            mRespText = "CARD DECLINED TRANSACTION";
            mApprCode = "";
        } else if (r != null && r.needsReversal) {
            mApproved = false;
            mRespText = r.reversalAcknowledged ? "REVERSAL ACKNOWLEDGED" : "REVERSAL PENDING";
            mApprCode = "";
        } else if (r != null && !r.online && !mRespText.isEmpty()) {
            // Never reached the host - keep the reason runOnlineTxn already set.
            mApproved = false;
        } else {
            mApproved = r != null && r.approved;
            mRespText = ResponseCode.displayText(mRespCode, mApprCode,
                    r == null ? null : r.hostText);
        }

        // ---- card data
        String pan = rec != null && !rec.pan.isEmpty() ? rec.pan : panFromTrack2(
                rec == null ? null : rec.track2);
        if (pan == null || pan.isEmpty()) {
            pan = tipCardNumber == null ? "" : tipCardNumber;
        }
        mPan = pan;
        mCardMasked = maskPan(mPan);
        // Name the card from the card range table, so TAKAPAY and NEXUSPAY
        // print as themselves. detectIssuer() only guesses from the first
        // digit, which cannot tell a national scheme from whatever else shares
        // its leading digit. Falls back to the guess when no range matches.
        mIssuer = CardRangeTable.nameFor(getApplicationContext(), mPan);
        if (mIssuer == null || mIssuer.isEmpty()) {
            mIssuer = detectIssuer(mPan);
        }
        mEntry = entryLabel(rec == null ? "" : rec.entryMode);
        mExp = formatExpYyMm(rec == null ? expiryYyMm() : rec.expiry);

        // ---- EMV data lifted from the DE55 we actually sent (spec 6.23)
        java.util.Map<String, String> icc = Emv55.parse(rec == null ? null : rec.iccHex);
        mAid = orBlank(icc.get("84"));
        if (mAid.isEmpty()) {
            mAid = detectAid(mPan);
        }
        mArqc = orBlank(icc.get("9F26"));             // Application Cryptogram
        mTvr = orBlank(icc.get("95"));                // Terminal Verification Result
        mAtc = orBlank(icc.get("9F36"));              // Application Transaction Counter
        mTsi = getTagValueSafe(0x9B);                 // Transaction Status Information
        mAppl = appLabelFromCard();
        mCvm = cvmLabel(icc.get("9F34"), rec != null && rec.pinUsed);
        mCardholder = asciiTag(0x5F20);               // Cardholder Name
        // Name the cryptogram for what it is rather than calling every one an
        // ARQC. Cryptogram Information Data (9F27) bits 7-8: 00 = AAC (card
        // declined), 01 = TC (completed), 10 = ARQC (went online). A slip that
        // labels a TC as an ARQC tells the acquirer's dispute team the wrong
        // thing about how the transaction was authorised.
        mAcLabel = acLabel(orBlank(icc.get("9F27")).isEmpty()
                ? getTagValueSafe(0x9F27) : orBlank(icc.get("9F27")));

        // ---- issuer scripts / issuer authentication from the response DE55
        java.util.Map<String, String> respIcc =
                Emv55.parse(r == null ? null : r.responseIcc);
        mIssuerAuth = respIcc.containsKey("91");
        mScriptRun = respIcc.containsKey("71") || respIcc.containsKey("72");

        // Prepaid balance, when the host sent one (Kona spec 2.2).
        mBalance = r == null ? "" : com.hjq.demo.ans8583.KonaMoney.balanceText(
                getApplicationContext(), r.additionalAmounts);

        mTitle = slipTitle(transType);
        boolean isVoid = transType != null && transType.contains("VOID");
        // A completion names the authorisation it settles for the same reason a
        // void names the sale it cancels: our invoice number is a fresh one, so
        // without these lines nothing on the paper ties the two together - and
        // on a completion that is the whole question, because the hold and the
        // charge are two entries against one card.
        boolean citesOriginal = isVoid || "SALE_COMPLETE".equals(transType);
        mOrigRef = citesOriginal ? mVoidOrigInvoice : null;
        mOrigRrn = "";
        mOrigAuth = "";
        mOrigDateTime = "";
        if (citesOriginal && mVoidOrigInvoice != null) {
            TxnRecord ov = BatchStore.get(getApplicationContext()).findByInvoice(mVoidOrigInvoice);
            if (ov == null) {
                // already marked voided by a successful void - look through all
                for (TxnRecord t : BatchStore.get(getApplicationContext()).all()) {
                    if (mVoidOrigInvoice.equals(t.invoice)) {
                        ov = t;
                        break;
                    }
                }
            }
            if (ov != null) {
                mOrigRrn = ov.rrn;
                mOrigAuth = ov.authCode;
                mVoidOrigRecord = ov;
                mOrigDateTime = ov.date.length() == 4 && ov.time.length() == 6
                        ? ov.date.substring(0, 2) + "/" + ov.date.substring(2)
                          + "  " + ov.time.substring(0, 2) + ":" + ov.time.substring(2, 4)
                        : "";
            }
        }

        // EMI figures come from the record the host actually authorised, not
        // from a hardcoded 6 months.
        boolean isEmi = rec != null && rec.isEmi();
        mTenure = isEmi ? rec.tenure : 0;
        // The host's record is authoritative, but a record that came back with
        // no term must not silently drop TENURE off the slip - the operator
        // chose one, and it is the thing that makes the sale an EMI.
        if (mTenure <= 0 && mEmiTenure > 0) {
            mTenure = mEmiTenure;
        }
        // The plan name goes in the title line - "EMI SALE : INSTA PAY" - so a
        // cardholder can see which offer they were put on. Read from the record
        // rather than the intent: what the host authorised is what happened.
        //
        // Through TxnSlip.emiPlanName, NOT raw. TxnRecord.emiProduct holds DE61
        // verbatim - [type][tenure] - so a nine-month Easy Buy is stored as
        // "109", and printing the field as-is put "EMI SALE : 109" on the
        // paper. This line is the reason fixing TxnSlip alone did not help:
        // TxnSlip.from(TxnRecord) decodes it correctly, and then this path
        // overwrote d.emiPlan with the raw code on its way to the printer.
        // One decoder, called from both.
        mSlipEmiPlan = isEmi ? com.hjq.demo.utils.TxnSlip.emiPlanName(rec.emiProduct) : "";

        // A void's own record is not an EMI sale, so the block above just zeroed
        // the term and plan - they belong to the transaction being cancelled.
        // Read them back off the original, or a VOID EMI SALE slip names no
        // plan and no term, which is most of what made it an EMI.
        if (isVoid && mVoidOrigRecord != null && mVoidOrigRecord.isEmi()) {
            mTenure = mVoidOrigRecord.tenure;
            mSlipEmiPlan = com.hjq.demo.utils.TxnSlip.emiPlanName(
                    mVoidOrigRecord.emiProduct);
        }
        mInstallment = isEmi && rec.installment() > 0
                ? String.format(java.util.Locale.US, "%d.%02d",
                        rec.installment() / 100, Math.abs(rec.installment() % 100))
                : null;
        if (isEmi) {
            try {
                double v = Double.parseDouble((currentAmount == null ? "0" : currentAmount).replace(",", "").trim());
                mInstallment = String.format(java.util.Locale.US, "%.2f", v / mTenure);
            } catch (Exception e) { mInstallment = currentAmount; }
        }
        savaTrans(mId, dateTime);
    }

    private static String orBlank(String s) {
        return s == null ? "" : s;
    }

    /**
     * Resolve a clean PAN for the PIN block.
     *
     * <p>The PAX reference builds the X9.8 PAN block as
     * {@code "0000" + pan.substring(len - 13, len - 1)} and hands it to
     * {@code getPinBlock(..., EPinBlockMode.ISO9564_0, ...)}. Our pad does that
     * internally, but only if it is given the real PAN. Tries, in order: the
     * PAN captured for this sale, EMV tag 5A, the track 2 image, then whatever
     * the caller passed. Non-digits are stripped, which removes both the
     * display mask and tag 5A's trailing 'F' padding.</p>
     */
    private String panForPinBlock(String candidate) {
        String[] sources = {
                candidate,
                currentCardNumber,
                tipCardNumber,
                saleDataModel == null ? null : saleDataModel.getCardNo(),
                getTagValue(0x5A),
                getTagValue(0x57),
                getTagValue(0x9F6B),
                panFromTrack2(saleDataModel == null ? null : saleDataModel.getTrack2data()),
                icCardNumber,
        };
        for (String s : sources) {
            if (s == null || s.isEmpty()) continue;
            String d = digitsOnly(stripPadF(s));
            if (s.contains("D") || s.contains("d") || s.contains("=")) {
                d = digitsOnly(panFromTrack2(s));
            }
            if (TerminalConfig.get(getApplicationContext()).isAcceptablePanLength(d.length())) {
                return d;
            }
        }
        return digitsOnly(stripPadF(candidate));
    }

    private static String digitsOnly(String s) {
        return s == null ? "" : s.replaceAll("[^0-9]", "");
    }

    /**
     * Print the ISO-8583 request and response exactly as sent and received, in
     * the same field-by-field hex layout the reference DBBL terminal uses, so
     * the two slips can be compared line for line during certification.
     *
     * <p>Controlled by {@link TerminalConfig#printPacketTrace()} - on for test
     * and UAT, off for production where card data must not appear on paper.</p>
     */
    private void addPacketTrace(java.util.List<RLine> o) {
        if (!TerminalConfig.get(getActivity()).printPacketTrace()) {
            return;
        }
        OnlineHost.HostResult r = mHostResult;
        if (r == null) {
            return;
        }
        addTraceBlock(o, r.packetTrace());
        if (!r.error.isEmpty()) {
            o.add(new RLine("ERR: " + r.error, "", R_SMALL, 0));
        }
        if (mGac2Reason != null && !mGac2Reason.isEmpty()) {
            o.add(new RLine("GAC2: " + mGac2Reason, "", R_SMALL, 0));
        }
        // The CA public key index the card asked for. When TVR byte 1 says
        // offline data authentication was not performed, this is the number
        // that has to be loaded for this RID - it turns "the card declined"
        // into a specific, actionable key to install.
        if (mCapkIndex != null && !mCapkIndex.isEmpty()) {
            o.add(new RLine("CAPK IDX (8F): " + mCapkIndex,
                    odaNotPerformed() ? "NO KEY LOADED" : "", R_SMALL, 0));
        }
        o.add(new RLine("", "", 0, F_BLANK));
    }

    private void addTraceBlock(java.util.List<RLine> o, String dump) {
        if (dump == null || dump.isEmpty()) {
            return;
        }
        for (String line : dump.split("\n")) {
            o.add(new RLine(line, "", R_SMALL, 0));
        }
        o.add(new RLine("", "", 0, F_BLANK));
    }

    /** PAN out of a track 2 image, when DE2 was not sent. */
    private static String panFromTrack2(String track2) {
        if (track2 == null || track2.isEmpty()) {
            return "";
        }
        int i = track2.indexOf('=');
        if (i < 0) {
            i = track2.toUpperCase(java.util.Locale.US).indexOf('D');
        }
        return i > 0 ? track2.substring(0, i) : track2;
    }

    /** Turn the DE22 we sent back into something a cardholder can read. */
    private static String entryLabel(String de22) {
        // One implementation, in DetailReport - this one had drifted a second
        // time (no NFC case at all) and both were reading DE22 wrongly.
        return com.hjq.demo.utils.DetailReport.entryLabel(de22);
    }

    /** YYMM from DE14 shown as MM/YY; blank when the field was not sent. */
    private static String formatExpYyMm(String yymm) {
        if (yymm == null) {
            return "";
        }
        String d = yymm.replaceAll("[^0-9]", "");
        if (d.length() < 4) {
            return "";
        }
        return d.substring(2, 4) + "/" + d.substring(0, 2);
    }

    /**
     * Cardholder verification actually performed. Reads EMV tag 9F34 byte 1
     * when the chip supplied it, otherwise falls back to whether a PIN block
     * was sent.
     */
    private static String cvmLabel(String cvmResults, boolean pinUsed) {
        if (cvmResults != null && cvmResults.length() >= 2) {
            int method = Integer.parseInt(cvmResults.substring(0, 2), 16) & 0x3F;
            switch (method) {
                case 0x01: case 0x04: return "PIN VERIFIED";           // offline plaintext PIN
                case 0x02: return "PIN VERIFIED";                       // online PIN
                case 0x1E: return "SIGNATURE VERIFIED";
                case 0x03: case 0x05: return "PIN + SIGNATURE";
                case 0x1F: return "NO CVM REQUIRED";
                default:   break;
            }
        }
        return pinUsed ? "PIN VERIFIED" : "NO CVM REQUIRED";
    }

    /** Application label / preferred name from the card, tag 50 then 9F12. */
    private String appLabelFromCard() {
        String hex = getTagValueSafe(0x50);
        if (hex.isEmpty()) {
            hex = getTagValueSafe(0x9F12);
        }
        if (!hex.isEmpty()) {
            String txt = Iso8583.hexToAscii(hex).trim();
            if (!txt.isEmpty() && txt.indexOf('.') < 0) {
                return txt;
            }
        }
        return applLabel(mIssuer);
    }

    /** getTagValue without throwing when the EMV service is absent (magstripe). */
    /**
     * Log the offline data authentication outcome after a Generate AC.
     *
     * <p>Diagnostic for the AAC problem: if the terminal has no CA public key
     * matching the card's RID and index (tag 8F), the kernel cannot perform
     * offline data authentication, sets TVR byte 1 bit 8 and the CDA-failed
     * bit, and the card answers the first Generate AC with an AAC (9F27 = 00)
     * instead of an ARQC (9F27 = 80). The host then rejects the request,
     * because it is being asked to authorise an already-declined cryptogram.</p>
     */
    private void logOfflineDataAuth(int type) {
        String tvr = getTagValueSafe(0x95);
        String cid = getTagValueSafe(0x9F27);
        String capkIdx = getTagValueSafe(0x8F);
        String aid = getTagValueSafe(0x84);
        mCapkIndex = capkIdx;
        Log.i(TAG, "GAC" + (type + 1) + " ODA: TVR=" + tvr + " 9F27=" + cid
                + " CAPK index(8F)=" + capkIdx + " AID(84)=" + aid);

        if (cid.length() >= 2) {
            int ac = (Integer.parseInt(cid.substring(0, 2), 16) >> 6) & 0x03;
            String name = ac == 0 ? "AAC (DECLINE)" : ac == 1 ? "TC (offline approve)"
                    : ac == 2 ? "ARQC (go online)" : "RFU";
            Log.i(TAG, "  cryptogram type: " + name);
            if (ac == 0) {
                Log.e(TAG, "  CARD GENERATED AN AAC - it declined before going online.");
            }
        }
        if (tvr.length() >= 2) {
            int b1 = Integer.parseInt(tvr.substring(0, 2), 16);
            if ((b1 & 0x80) != 0) {
                Log.e(TAG, "  TVR b8: offline data authentication was NOT performed"
                        + " - no CA public key loaded for RID " + rid(aid)
                        + " index " + capkIdx);
            }
            if ((b1 & 0x04) != 0) {
                Log.e(TAG, "  TVR b3: CDA FAILED");
            }
            if ((b1 & 0x20) != 0) {
                Log.e(TAG, "  TVR b6: ICC data missing");
            }
        }
    }

    /** First five bytes of an AID are the Registered Application Provider Id. */
    private static String rid(String aid) {
        String a = orBlank(aid).replaceAll("[^0-9A-Fa-f]", "");
        return a.length() >= 10 ? a.substring(0, 10) : a;
    }

    /** A tag whose value is ASCII text, e.g. 5F20 cardholder name. */
    private String asciiTag(int tag) {
        String hex = getTagValueSafe(tag);
        if (hex == null || hex.isEmpty()) {
            return "";
        }
        try {
            return Iso8583.hexToAscii(hex).trim();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Name the application cryptogram from the Cryptogram Information Data.
     *
     * <p>9F27 bits 7-8: 00 = AAC, 01 = TC, 10 = ARQC. Anything unreadable
     * defaults to TC, which is the right reading for a completed transaction
     * and the one the reference slip prints.</p>
     */
    private static String acLabel(String cid) {
        String c = cid == null ? "" : cid.replaceAll("[^0-9A-Fa-f]", "");
        if (c.length() < 2) {
            return "TC";
        }
        try {
            int v = (Integer.parseInt(c.substring(0, 2), 16) >> 6) & 0x03;
            if (v == 0) {
                return "AAC";
            }
            if (v == 2) {
                return "ARQC";
            }
            return "TC";
        } catch (Exception e) {
            return "TC";
        }
    }

    private String getTagValueSafe(int tag) {
        if (mEmvBinderService == null) {
            return "";
        }
        try {
            return orBlank(getTagValue(tag));
        } catch (Exception e) {
            return "";
        }
    }

    private java.util.List<RLine> buildCopy(String copyLabel) {
        java.util.List<RLine> o = new java.util.ArrayList<>();
        addCopyLines(o, copyLabel);
        return o;
    }

    /**
     * The slip for a transaction that did not go through.
     *
     * <p>Header, then what went wrong and the code it went wrong with, both
     * centred. Nothing else: a failed transaction has no approval code to
     * quote, nothing for the cardholder to sign and no amount that was taken,
     * so a full sale slip with those parts blanked out is a longer way of
     * saying less.</p>
     *
     * <p>The message is the spec section 8 text for the response code -
     * "DO NOT HONOUR", "EXPIRED CARD", "BAD TERMINAL ID." - or the host's own
     * DE63 table '22' text when it sent one of at least twenty characters,
     * which is the rule the spec sets. The operator has to decide what to do
     * next, and the code alone does not tell them.</p>
     *
     * <p>One copy, and it says nothing about whose copy it is. The merchant
     * and customer copies of a sale exist because two parties each keep a
     * record of a payment; nothing was paid here, so there is one piece of
     * paper and no second party to hand it to.</p>
     */
    private java.util.List<RLine> buildFailureSlip() {
        boolean reversalAccepted = mHostResult != null && mHostResult.reversalAcknowledged;
        java.util.List<com.hjq.demo.utils.Slip.Line> s =
                com.hjq.demo.utils.TxnSlip.notice(getApplicationContext(),
                        mReversed ? (reversalAccepted ? "TRANSACTION REVERSED" : "REVERSAL PENDING")
                                : "TRANSACTION FAILED",
                        orBlank(mRespText),
                        orBlank(mRespCode).isEmpty() ? "" : "RESP CODE : " + mRespCode);
        if (mReversed) {
            s.add(new com.hjq.demo.utils.Slip.Line(reversalAccepted
                    ? "REVERSAL ACKNOWLEDGED" : "VERIFY WITH BANK BEFORE RETRY", "",
                    com.hjq.demo.utils.Slip.SMALL,
                    com.hjq.demo.utils.Slip.BOLD | com.hjq.demo.utils.Slip.CENTER));
        } else if (mCardLeftMidFlight) {
            // The cardholder pulled the card while the host was answering.
            // Printing "card refused" here would blame the chip for something
            // it was never asked, and the merchant needs to know which of the
            // two happened - one is a card fault, the other is the customer.
            s.add(new com.hjq.demo.utils.Slip.Line("CARD REMOVED DURING TXN", "",
                    com.hjq.demo.utils.Slip.SMALL, com.hjq.demo.utils.Slip.CENTER));
            s.add(new com.hjq.demo.utils.Slip.Line(mCardDeclineReversed
                    ? "REVERSED - CARDHOLDER NOT CHARGED"
                    : "REVERSAL SENT - VERIFY WITH BANK", "",
                    com.hjq.demo.utils.Slip.SMALL,
                    com.hjq.demo.utils.Slip.BOLD | com.hjq.demo.utils.Slip.CENTER));
        } else if (mHostApprovedCardRefused) {
            // ONLY when the host actually said 00. Without this the merchant
            // cannot tell an issuer decline from a card decline, and cannot
            // tell whether the cardholder was charged.
            s.add(new com.hjq.demo.utils.Slip.Line("HOST APPROVED - CARD REFUSED", "",
                    com.hjq.demo.utils.Slip.SMALL, com.hjq.demo.utils.Slip.CENTER));
            s.add(new com.hjq.demo.utils.Slip.Line(mCardDeclineReversed
                    ? "REVERSED - CARDHOLDER NOT CHARGED"
                    : "REVERSAL QUEUED - VERIFY WITH BANK", "",
                    com.hjq.demo.utils.Slip.SMALL,
                    com.hjq.demo.utils.Slip.BOLD | com.hjq.demo.utils.Slip.CENTER));
        } else if (mGac2Refused) {
            // Printed under the response code, not instead of it. On a host
            // decline this is a footnote; the header already says why.
            s.add(new com.hjq.demo.utils.Slip.Line("CARD REFUSED AT GAC2", "",
                    com.hjq.demo.utils.Slip.SMALL, com.hjq.demo.utils.Slip.CENTER));
            s.add(new com.hjq.demo.utils.Slip.Line("CARDHOLDER NOT CHARGED", "",
                    com.hjq.demo.utils.Slip.SMALL,
                    com.hjq.demo.utils.Slip.BOLD | com.hjq.demo.utils.Slip.CENTER));
        }
        java.util.List<RLine> o = new java.util.ArrayList<>();
        // The packet trace stays gated on Debug, exactly as on a sale slip.
        addPacketTrace(o);
        for (com.hjq.demo.utils.Slip.Line ln : s) {
            o.add(new RLine(ln.left, ln.right, ln.size, ln.flags));
        }
        return o;
    }

    /**
     * Logo block and merchant letterhead, shared by every slip this file prints.
     *
     * <p>Was copied out three times - twice with two address lines, once with
     * none - so the detail report quietly lost the address the sale slip
     * carried. One method, one source of truth: the name and lines 1 to 4 come
     * from {@link TerminalConfig}, which prefers whatever the host pushed in
     * the DE62 logon tail over the operator's entry.</p>
     *
     * <p>Empty address lines are skipped rather than printed blank, so a
     * merchant with a two-line address gets a two-line header.</p>
     */
    /**
     * The shared document header - letterhead, date and time, MID and TID,
     * batch - converted into this file's line type.
     */
    private void addSharedHeader(java.util.List<RLine> o, String batch, String trace) {
        java.util.List<com.hjq.demo.utils.Slip.Line> head =
                com.hjq.demo.utils.Slip.list();
        com.hjq.demo.utils.TxnSlip.header(getApplicationContext(), head, batch, trace);
        for (com.hjq.demo.utils.Slip.Line ln : head) {
            o.add(new RLine(ln.left, ln.right, ln.size, ln.flags));
        }
    }

    private void addLetterhead(java.util.List<RLine> o) {
        java.util.List<com.hjq.demo.utils.Slip.Line> head =
                com.hjq.demo.utils.Slip.list();
        com.hjq.demo.utils.TxnSlip.letterhead(head,
                TerminalConfig.get(getApplicationContext()));
        for (com.hjq.demo.utils.Slip.Line ln : head) {
            o.add(new RLine(ln.left, ln.right, ln.size, ln.flags));
        }
    }

    /**
     * An amount as it appears on a slip: "TK 1.00", or "- TK 1.00" on a void.
     *
     * <p>The sign leads the whole figure rather than sitting inside it, which
     * is how the reference DBBL slip prints it and what a merchant totalling a
     * day's paper scans down the right-hand edge for. The symbol follows the
     * terminal's configured DE49 currency instead of being the literal "TK"
     * this file used to repeat.</p>
     *
     * <p>Printed text only. DE4 is always positive - the transaction code is
     * what tells the host this reverses an earlier sale - so nothing here may
     * reach the packet builder.</p>
     */
    private String slipAmount(String amount) {
        String a = amount == null ? "" : amount.trim();
        if (a.startsWith("-")) {
            a = a.substring(1).trim();
        }
        if (a.isEmpty()) {
            return "";
        }
        boolean isVoid = transType != null && transType.contains("VOID");
        return (isVoid ? "- " : "")
                + TerminalConfig.get(getApplicationContext()).currencySymbol() + " " + a;
    }

    private String slipTitle(String type) {
        if (type == null) return "SALE";
        switch (type) {
            case "VOID": return "VOID SALE";
            case "REFUND": return "REFUND";
            case "PREAUTH": return "PRE-AUTH";
            case "PREAUTH_VOID": return "PRE-AUTH VOID";
            case "SALE_COMPLETE": return "SALE COMPLETION";
            case "EMI_SALE": return "EMI SALE";
            case "EMI_VOID": return "VOID EMI SALE";
            case "OFFLINE": return "OFFLINE SALE";
            case "ADJUST": return "TIP ADJUST";
            case "CASH_WITHDRAW": return "CASH WITHDRAW";
            default: return "SALE";
        }
    }

    /**
     * Build one copy of the transaction slip from the host response.
     *
     * <p>Layout follows the response rather than a fixed template:</p>
     * <ul>
     *   <li>the merchant header uses the name and receipt lines the host pushed
     *       down in the optional tail of DE62 at logon (spec 6.27.2), falling
     *       back to the built-in constants,</li>
     *   <li>DE38 approval code and DE37 retrieval reference print only when the
     *       host actually sent them,</li>
     *   <li>the result line is the spec section 8 text for DE39, or the host's
     *       own DE63 table '22' text when it is at least 20 characters,</li>
     *   <li>EMV lines appear only for a chip transaction, using the real TVR,
     *       ARQC and ATC,</li>
     *   <li>a declined or reversed transaction prints a clear banner and drops
     *       the cardholder agreement and signature block.</li>
     * </ul>
     */
    private void addCopyLines(java.util.List<RLine> o, String copyLabel) {
        // Packet trace first, matching the reference terminal's slip layout.
        // It is a certification aid, not part of the document, so it sits
        // outside the shared builder and never reaches a reprint.
        addPacketTrace(o);
        for (com.hjq.demo.utils.Slip.Line ln : com.hjq.demo.utils.TxnSlip.body(
                getApplicationContext(), slipData(), copyLabel, false)) {
            o.add(new RLine(ln.left, ln.right, ln.size, ln.flags));
        }
    }

    /**
     * This transaction, in the form the shared slip builder reads.
     *
     * <p>Filling a form and handing it over, rather than composing the slip
     * here, is what lets a reprint be the same document: {@code ReportsActivity}
     * fills the same form out of the batch and calls the same builder. The
     * layout lived in this file for as long as there was only one thing
     * printing it, and the reprint drifted every time this one changed.</p>
     */
    private com.hjq.demo.utils.TxnSlip.Data slipData() {
        TerminalConfig tc = TerminalConfig.get(getActivity());
        com.hjq.demo.utils.TxnSlip.Data d = new com.hjq.demo.utils.TxnSlip.Data();
        d.date = orBlank(mDate);
        d.time = orBlank(mTime);
        d.mid = tc.mid();
        d.tid = tc.tid();
        d.batch = orBlank(mBatch);
        d.invoice = orBlank(mInvoice);
        d.stan = orBlank(mStan);
        d.hostOnline = mHostOnline;

        d.title = orBlank(mTitle);
        d.emiPlan = orBlank(mSlipEmiPlan);
        d.tenure = mTenure;

        d.cardMasked = orBlank(mCardMasked);
        d.entry = orBlank(mEntry);
        d.issuer = orBlank(mIssuer);
        d.onUs = CardRangeTable.onUsLabel(getApplicationContext(), orBlank(mPan));

        d.aid = orBlank(mAid);
        d.appl = orBlank(mAppl);

        // A hold prints no tip and no total - see TxnSlip's amount block.
        d.isPreAuth = "PREAUTH".equals(transType);
        d.isCompletion = "SALE_COMPLETE".equals(transType);
        d.origInvoice = orBlank(mOrigRef);
        d.origRrn = orBlank(mOrigRrn);
        d.origAuth = orBlank(mOrigAuth);
        d.origDateTime = orBlank(mOrigDateTime);

        d.apprCode = orBlank(mApprCode);
        d.rref = orBlank(mRref);
        d.respCode = orBlank(mRespCode);

        d.tvr = orBlank(mTvr);
        d.tsi = orBlank(mTsi);
        d.acLabel = orBlank(mAcLabel);
        d.arqc = orBlank(mArqc);
        d.atc = orBlank(mAtc);
        d.issuerAuth = mIssuerAuth;
        d.scriptRun = mScriptRun;

        d.amount = orBlank(currentAmount);
        if (mTipBaseMinor >= 0) {
            d.baseAmount = com.hjq.demo.ans8583.TipAmounts.major(mTipBaseMinor);
            d.tipAmount = com.hjq.demo.ans8583.TipAmounts.major(mTipMinor);
        } else if (mHostResult != null && mHostResult.record != null) {
            d.baseAmount = com.hjq.demo.ans8583.TipAmounts.major(mHostResult.record.baseAmount());
            d.tipAmount = mHostResult.record.baseAmountMinor.isEmpty() && !mHostResult.record.tipAdjusted
                    ? "" : com.hjq.demo.ans8583.TipAmounts.major(mHostResult.record.tip());
        }
        d.installment = orBlank(mInstallment);
        d.balance = orBlank(mBalance);
        d.isVoid = transType != null && transType.contains("VOID");

        d.approved = mApproved;
        d.reversed = mReversed;
        d.cvm = orBlank(mCvm);
        d.respText = orBlank(mRespText);
        d.cardholder = orBlank(mCardholder);
        d.hostApprovedCardRefused = mHostApprovedCardRefused;
        d.cardDeclineReversed = mCardDeclineReversed;
        d.cardDeclined = mCardDeclined;
        return d;
    }

    /**
     * Keep on the batch entry the parts of this slip that cannot be worked out
     * again later.
     *
     * <p>The cardholder name and TSI are read from the card and never travel
     * in DE55; the CVM text is the kernel's verdict. Without them a reprint
     * would be missing the signature line's name and the verification result -
     * a different document from the one the cardholder signed.</p>
     */
    private void saveReceiptSnapshot() {
        OnlineHost.HostResult r = mHostResult;
        TxnRecord rec = r == null ? null : r.record;
        if (rec == null || rec.invoice == null || rec.invoice.isEmpty()) {
            return;
        }
        rec.cardholder = orBlank(mCardholder);
        rec.tsi = orBlank(mTsi);
        rec.cvm = orBlank(mCvm);
        rec.appLabel = orBlank(mAppl);
        rec.issuer = orBlank(mIssuer);
        rec.respCode = orBlank(mRespCode);
        rec.onUs = CardRangeTable.isOnUs(getApplicationContext(), orBlank(mPan));
        rec.balance = orBlank(mBalance);
        rec.hostOnline = mHostOnline;
        rec.issuerAuth = mIssuerAuth;
        rec.scriptRun = mScriptRun;
        rec.origInvoice = orBlank(mOrigRef);
        rec.origRrn = orBlank(mOrigRrn);
        rec.origAuth = orBlank(mOrigAuth);
        rec.origDateTime = orBlank(mOrigDateTime);
        BatchStore.get(getApplicationContext()).updateReceipt(rec);
    }

    /**
     * TVR byte 1 bit 8: offline data authentication was not performed, i.e. no
     * CA public key was available for this RID and index. Parsed defensively -
     * a malformed TVR must not take the receipt down with it.
     */
    private boolean odaNotPerformed() {
        try {
            return mTvr != null && mTvr.length() >= 2
                    && (Integer.parseInt(mTvr.substring(0, 2), 16) & 0x80) != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Refuse a card this terminal does not acquire, before anything is sent.
     *
     * <p>Mirrors the PAX reference, which matches every PAN against
     * {@code card_range.json} and ends the transaction with
     * {@code ERR_CARD_UNSUPPORTED} / "Unsupported Card" when nothing covers it.
     * DBBL do not acquire American Express, so an Amex card is stopped here
     * with a clear message instead of being sent to the host and coming back
     * as an unexplained decline.</p>
     *
     * @return true when the card was rejected and the caller must stop
     */
    private boolean rejectUnsupportedCard(String pan) {
        if (pan == null || pan.replaceAll("[^0-9]", "").length() < 6) {
            return false;
        }
        if (!CardRangeTable.isSupported(getApplicationContext(), pan)) {
            String scheme = CardRangeTable.schemeOf(pan);
            LogUtils.e("card not acquired here: " + maskPan(pan)
                    + (scheme.isEmpty() ? "" : " (" + scheme + ")")
                    + " - no matching range in card_range.json");
            showUnsupportedCard(scheme);
            return true;
        }
        // Second, narrower gate, and only for an EMI sale. Being acquired here
        // does not mean the card carries an instalment programme: the bank
        // issues the EMI BIN list separately, and it is kept in its own table
        // in the admin panel. A card missing from it is refused for EMI alone -
        // the same card runs an ordinary sale perfectly well, which is why this
        // says something different from "card not supported".
        //
        // An empty EMI table means the terminal was never given the list, and
        // is treated as no restriction rather than as a list that matches
        // nothing. See CardRangeTable.emiAllowed.
        if ("EMI_SALE".equals(transType)
                && !CardRangeTable.emiAllowed(getApplicationContext(), pan)) {
            LogUtils.e("card not in the EMI table: " + maskPan(pan)
                    + " - EMI refused, ordinary sale would still be accepted");
            showNoEmiCard();
            return true;
        }
        return false;
    }

    /** Three seconds on screen, then back, the same as an unsupported card. */
    private void showNoEmiCard() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    disDialog();
                } catch (Exception ignore) {
                    // dialog may not be showing
                }
                speak("This card does not support E M I");
                final com.hjq.base.BaseDialog d =
                        new com.hjq.demo.ui.dialog.MessageDialog.Builder(getActivity())
                        .setTitle("EMI NOT SUPPORTED")
                        .setMessage("THIS CARD DOES NOT SUPPORT EMI\n\n"
                                + "It can still be used for an ordinary sale.")
                        .setConfirm(null)
                        .setCancel(null)
                        .setCancelable(false)
                        .create();
                d.show();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            d.dismiss();
                        } catch (Exception ignore) {
                            // already gone
                        }
                        canCleTransaction();
                    }
                }, UNSUPPORTED_CARD_MS);
            }
        });
    }

    /** Three seconds on screen, then back to the amount. */
    private void showUnsupportedCard(final String scheme) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    disDialog();
                } catch (Exception ignore) {
                    // dialog may not be showing
                }
                speak("Card not supported");
                final com.hjq.base.BaseDialog d =
                        new com.hjq.demo.ui.dialog.MessageDialog.Builder(getActivity())
                        .setTitle("CARD NOT SUPPORTED")
                        .setMessage((scheme.isEmpty() ? "This card" : scheme)
                                + " is not accepted on this terminal.\n\n"
                                + "Please try with another card.")
                        .setConfirm(null)
                        .setCancel(null)
                        .setCancelable(false)
                        .create();
                d.show();
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            d.dismiss();
                        } catch (Exception ignore) {
                            // already gone
                        }
                        canCleTransaction();
                    }
                }, UNSUPPORTED_CARD_MS);
            }
        });
    }

    /** Masked PAN for the receipt, using the configured Card Masking mode. */
    /**
     * The card number as the slip prints it: masked, then grouped in fours.
     *
     * <p>A sixteen-digit run with asterisks through the middle is unreadable
     * held against the card it is meant to identify. The reference slip groups
     * it - "1000 02** **** 7684" - and so does the detail report.</p>
     */
    private String maskPan(String pan) {
        return CardMask.maskGrouped(pan, TerminalConfig.get(getActivity()).cardMask());
    }

    private String applLabel(String issuer) {
        switch (issuer) {
            case "TAKAPAY": return "TakaPay";
            case "NEXUSPAY": case "NEXUS": return "NexusPay";
            case "MASTER": return "MasterCard";
            case "VISA": return "Visa Credit";
            case "MASTERCARD": return "MasterCard";
            case "UNIONPAY": return "UnionPay";
            case "AMEX": return "American Express";
            default: return issuer;
        }
    }

    private String entryModeStr() {
        if (cardType == CardSearchType.MAGNETIC_STRIPE_CARD) return "SWIPE";
        if (cardType == CardSearchType.RF_CARD) return "NFC";
        return "CHIP";
    }

    private String randomHex(int n) {
        java.util.Random r = new java.util.Random();
        String hex = "0123456789ABCDEF";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(hex.charAt(r.nextInt(16)));
        return b.toString();
    }

    private String detectIssuer(String pan) {
        String p = pan == null ? "" : pan.replaceAll("[^0-9]", "");
        char c = p.isEmpty() ? ' ' : p.charAt(0);
        switch (c) {
            case '4': return "VISA";
            case '5': return "MASTERCARD";
            case '6': return "UNIONPAY";
            case '3': return "AMEX";
            default: return "CARD";
        }
    }

    private String detectAid(String pan) {
        String issuer = detectIssuer(pan);
        switch (issuer) {
            case "VISA": return "A0000000031010";
            case "MASTERCARD": return "A0000000041010";
            case "UNIONPAY": return "A000000333010101";
            case "AMEX": return "A0000000250109";
            default: return "A0000000031010";
        }
    }

    private String formatExp(String v) {
        if (v == null) return "";
        String d = v.replaceAll("[^0-9]", "");
        if (d.length() >= 4) return d.substring(2, 4) + "/" + d.substring(0, 2);
        return v;
    }

    private String slipCenter(String t) {
        if (t == null) t = "";
        if (t.length() >= SLIP_W) return t;
        int pad = (SLIP_W - t.length()) / 2;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < pad; i++) b.append(' ');
        return b.append(t).toString();
    }

    private String slipLR(String l, String r) {
        if (l == null) l = "";
        if (r == null) r = "";
        int gap = SLIP_W - l.length() - r.length();
        StringBuilder b = new StringBuilder(l);
        if (gap < 1) b.append(' ');
        else for (int i = 0; i < gap; i++) b.append(' ');
        return b.append(r).toString();
    }

    private String groupPan(String pan) {
        if (pan == null) return "";
        String p = pan.replace(" ", "");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < p.length(); i++) {
            if (i > 0 && i % 4 == 0) b.append(' ');
            b.append(p.charAt(i));
        }
        return b.toString();
    }

    private String renderText(java.util.List<RLine> lines) {
        StringBuilder s = new StringBuilder();
        for (RLine ln : lines) {
            if ((ln.flags & F_LOGO) != 0) s.append(slipCenter("DUTCH BANGLA BANK")).append('\n');
            else if ((ln.flags & F_SEP) != 0) s.append(SLIP_SEP).append('\n');
            else if ((ln.flags & F_BLANK) != 0) s.append('\n');
            else if ((ln.flags & F_CENTER) != 0) s.append(slipCenter(ln.left)).append('\n');
            else if (!ln.right.isEmpty()) s.append(slipLR(ln.left, ln.right)).append('\n');
            else s.append(ln.left).append('\n');
        }
        return s.toString();
    }

    private Bitmap loadLogoBitmap(int maxWidth) {
        Bitmap src = null;
        try {
            java.io.InputStream is = getAssets().open("bank_logo_full.jpg");
            src = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
        } catch (Exception ignore) {}
        if (src == null) {
            try { src = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.bank_logo); } catch (Exception ignore) {}
        }
        if (src == null) return null;
        // Scale to the target, up as well as down. Math.min() here meant an
        // asset narrower than the target printed at its own size however much
        // paper there was - see Slip.scaled().
        int w = Math.max(1, maxWidth);
        int h = Math.round(src.getHeight() * (w / (float) src.getWidth()));
        return Bitmap.createScaledBitmap(src, w, h, true);
    }

    /**
     * Paint for every line of a slip. Everything prints bold.
     *
     * <p>Bold was already being asked for, but the slip still came out thin.
     * The reason is the 1-bit conversion in {@code PrintUtil.getBitmapData}:
     * a pixel prints only when its blue channel is below 128. With
     * anti-aliasing on, the outer pixels of every glyph are mid-grey, land
     * above that threshold and are dropped - so each character loses its edges
     * and what reaches the paper is a thinner letter than was drawn.</p>
     *
     * <p>So: anti-aliasing off, which makes every glyph pixel pure black and
     * survives the threshold intact, plus a light stroke on top of the bold
     * typeface to thicken the stems. Thermal paper has no use for smooth edges
     * at this resolution anyway.</p>
     */
    /**
     * One paint for the whole app's printing - see {@link Slip#paint}.
     *
     * <p>This file used to build its own, and the two drifted: both applied
     * synthetic bolding and a stroke on top of an already-SemiBold face, which
     * filled the counters and printed as blobs. There is now one definition of
     * what a printed character looks like.</p>
     */
    private TextPaint slipPaint(int size, boolean bold) {
        com.hjq.demo.utils.Slip.useFont(getApplicationContext());
        return com.hjq.demo.utils.Slip.paint(size);
    }

    /**
     * Blank pixel rows added below every slip so it feeds past the tear bar.
     *
     * <p>The print head sits well above the cutter, so the last line of a slip
     * stops under the mechanism and cannot be torn off cleanly. 120 rows at this
     * resolution is roughly 15 mm - enough to clear it without wasting paper.</p>
     */
    /**
     * Blank paper fed after the slip so the last line clears the tear bar.
     *
     * <p>Cut to 48 while the slip was being shortened, which was too far - the
     * head sits above the tear bar and the last line was ending up inside the
     * cut. Back to a feed that clears it.</p>
     */
    private static final int TEAR_FEED = 120;

    private Bitmap renderReceiptBitmap(java.util.List<RLine> lines) {
        final int width = 384;
        final int pad = 10;
        final int contentW = width - pad * 2;
        Bitmap logo = loadLogoBitmap(com.hjq.demo.utils.Slip.LOGO_W);
        int total = pad;
        for (RLine ln : lines) {
            if ((ln.flags & F_LOGO) != 0) total += (logo != null ? logo.getHeight() : 0) + 12;
            else if ((ln.flags & F_BLANK) != 0) total += com.hjq.demo.utils.Slip.BLANK_HEIGHT;
            else total += textLineHeight(fitSize(ln, contentW));
        }
        total += pad;
        // Blank paper under the slip so it clears the tear bar. Without it the
        // last printed line sits right on the cutter and the merchant tears
        // through the signature line or the response code.
        total += TEAR_FEED;
        if (total < 120) total = 120;
        Bitmap bmp = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(Color.WHITE);
        int y = pad;
        for (RLine ln : lines) {
            if ((ln.flags & F_LOGO) != 0) {
                if (logo != null) {
                    c.drawBitmap(logo, (width - logo.getWidth()) / 2f, y, null);
                    y += logo.getHeight() + 12;
                }
                continue;
            }
            if ((ln.flags & F_BLANK) != 0) { y += com.hjq.demo.utils.Slip.BLANK_HEIGHT; continue; }
            boolean bold = (ln.flags & F_BOLD) != 0;
            int size = fitSize(ln, contentW);
            TextPaint p = slipPaint(size, bold);
            Paint.FontMetrics fm = p.getFontMetrics();
            float baseline = y - fm.ascent;
            String[] col = slipColumns(ln.left);
            if ((ln.flags & F_SEP) != 0) {
                c.drawText(com.hjq.demo.utils.Slip.rule(p, contentW), pad, baseline, p);
            } else if (col != null) {
                drawSlipColumns(c, col, p, pad, contentW, baseline);
            } else if ((ln.flags & F_CENTER) != 0) {
                float tw = p.measureText(ln.left);
                c.drawText(ln.left, (width - tw) / 2f, baseline, p);
            } else if (!ln.right.isEmpty()) {
                c.drawText(ln.left, pad, baseline, p);
                float rw = p.measureText(ln.right);
                c.drawText(ln.right, width - pad - rw, baseline, p);
            } else {
                c.drawText(ln.left, pad, baseline, p);
            }
            y += textLineHeight(size);
        }
        return bmp;
    }

    private int fitSize(RLine ln, int contentW) {
        int base = ln.size == 0 ? R_SMALL : ln.size;
        boolean bold = (ln.flags & F_BOLD) != 0;
        TextPaint p = slipPaint(base, bold);
        float need;
        String[] col = slipColumns(ln.left);
        if (col != null) {
            need = p.measureText(col[0]) + p.measureText(col[1]) + p.measureText(col[2]) + 20;
        } else if (!ln.right.isEmpty()) {
            need = p.measureText(ln.left) + p.measureText(ln.right) + 14;
        } else {
            need = p.measureText(ln.left);
        }
        if (need <= contentW) return base;
        return Math.max(13, (int) Math.floor(base * (contentW / need)));
    }

    /** One line's spacing, from Slip so both renderers agree to the pixel. */
    private int textLineHeight(int size) {
        return com.hjq.demo.utils.Slip.lineHeightOf(size);
    }

    /**
     * Settlement slip, laid out like the PAX reference report.
     *
     * <p>The printed counts and amounts are the same {@link BatchStore.Totals}
     * object that produced DE63, so the merchant's copy and the host's copy of
     * the reconciliation can never drift apart.</p>
     */
    private java.util.List<RLine> buildSettlementSlip(OnlineHost.SettleResult res) {
        TerminalConfig tc = TerminalConfig.get(getActivity());
        BatchStore.Totals t = res.totals;

        java.util.List<RLine> o = new java.util.ArrayList<>();

        // Packet trace first, same position as on a sale slip, gated by Debug.
        if (tc.printPacketTrace()) {
            addTraceBlock(o, res.settle == null ? null : res.settle.precedingTrace);
            addTraceBlock(o, res.settle == null ? null : res.settle.requestDump);
            addTraceBlock(o, res.settle == null ? null : res.settle.responseDump);
            if (res.trailer != null) {
                addTraceBlock(o, res.trailer.requestDump);
                addTraceBlock(o, res.trailer.responseDump);
            }
        }

        // ---- header, the same block every other document on this terminal
        // starts with. It used to spell out TERMINAL ID / MERCHANT ID / DATE /
        // TIME as five separate rows in its own labelling.
        addSharedHeader(o, res.batchNo, "");
        o.add(new RLine("SETTLEMENT REPORT", "", R_BIG, F_BOLD | F_CENTER));
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        o.add(new RLine("HOST", tc.nii(), R_SMALL, 0));
        OnlineHost.HostResult last = res.lastExchange();
        if (last != null && !last.stan.isEmpty()) {
            o.add(new RLine("STAN: " + last.stan,
                    last.rrn.isEmpty() ? "" : "RREF: " + last.rrn, R_SMALL, 0));
        }
        if (res.uploaded) {
            o.add(new RLine("BATCH UPLOADED: " + res.uploadSent + "/" + res.batch.size(),
                    "", R_SMALL, 0));
        }
        o.add(new RLine("", "", 0, F_BLANK));

        // ---- totals table
        //
        // Rendered as ONE fixed-width monospace string per row, not as a
        // left/right pair. A two-column line right-aligns the whole "001  TK
        // 10.00" blob, so the counts and the amounts never lined up with each
        // other and the table was unreadable on paper. Every row is exactly
        // SLIP_W characters, which also stops the auto-shrink in fitSize()
        // from scaling rows differently and breaking the columns.
        o.add(new RLine(row3("TRANSACTION", "COUNT", "AMOUNT"), "", R_SMALL, F_BOLD));
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        // Rows follow Menu Hide, the way PAX's ReceiptTransTotal does - a
        // merchant who never refunds should not have a REFUND 000 line on
        // every slip. EMI is its own row and the SALE row excludes it, so the
        // two do not double count; the host still gets EMI inside the sale
        // totals in DE63.
        com.hjq.demo.ans8583.MenuControl mc =
                com.hjq.demo.ans8583.MenuControl.get(getApplicationContext());
        if (mc.isVisible("SALE")) {
            o.add(new RLine(row3("SALE", count3(t.plainSaleCount()),
                    money(t.plainSaleAmt())), "", R_SMALL, 0));
        }
        if (mc.isVisible("EMI_SALE") && t.emiCount > 0) {
            o.add(new RLine(row3("EMI SALE", count3(t.emiCount), money(t.emiAmt)),
                    "", R_SMALL, 0));
        }
        if (mc.isVisible("VOID")) {
            o.add(new RLine(row3("VOID", count3(t.voidCount), money(-t.voidAmt)),
                    "", R_SMALL, 0));
        }
        if (mc.isVisible("REFUND")) {
            o.add(new RLine(row3("REFUND", count3(t.refundCount), money(-t.refundAmt)),
                    "", R_SMALL, 0));
        }
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        o.add(new RLine(row3("TOTAL", count3(t.netCount()), money(t.netAmt())),
                "", R_SMALL, F_BOLD));
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        o.add(new RLine("", "", 0, F_BLANK));

        // ---- verdict
        if (res.approved) {
            o.add(new RLine("SETTLEMENT SUCCESS", "", R_MID, F_BOLD | F_CENTER));
        } else {
            o.add(new RLine("*** SETTLEMENT FAILED ***", "", R_MID, F_BOLD | F_CENTER));
            o.add(new RLine(res.displayMessage(), "", R_SMALL, F_BOLD | F_CENTER));
            if (!res.responseCode().isEmpty()) {
                o.add(new RLine("RESP CODE: " + res.responseCode(), "", R_SMALL, F_CENTER));
            }
            o.add(new RLine("BATCH NOT CLOSED - RETRY", "", R_SMALL, F_BOLD | F_CENTER));
        }
        addBuildBlock(o);
        return o;
    }

    /**
     * The build block a report carries between two asterisk rules.
     *
     * <p>Every line is read off the device and the installed package at print
     * time - model, Android release, version, install date. Nothing is a
     * constant: this is what support reads back when a merchant phones about a
     * bad print, so a stale value here is worse than none.</p>
     *
     * <p>OS version is {@code Build.VERSION.RELEASE} ("Android 13"), not
     * {@code Build.DISPLAY}, which on this terminal is a vendor build id that
     * means nothing to whoever is holding the paper.</p>
     */
    private void addBuildBlock(java.util.List<RLine> o) {
        o.add(new RLine("", "", 0, F_BLANK));
        o.add(new RLine(STAR_SEP, "", R_SMALL, 0));
        o.add(new RLine("Terminal Model :  " + android.os.Build.MODEL, "", R_SMALL, 0));
        o.add(new RLine("OS Version     :  Android " + android.os.Build.VERSION.RELEASE,
                "", R_SMALL, 0));
        o.add(new RLine("Appln Version  :  " + appVersionName(), "", R_SMALL, 0));
        String released = appInstallDate();
        if (!released.isEmpty()) {
            o.add(new RLine("Appln Release  :  " + released, "", R_SMALL, 0));
        }
        o.add(new RLine(STAR_SEP, "", R_SMALL, 0));
    }

    /** When this build was installed, for the report's build block. */
    private String appInstallDate() {
        try {
            android.content.pm.PackageInfo pi =
                    getPackageManager().getPackageInfo(getPackageName(), 0);
            return new java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US)
                    .format(new java.util.Date(pi.lastUpdateTime));
        } catch (Exception e) {
            // A build that cannot read its own package info still prints.
            return "";
        }
    }

    /**
     * The per-transaction detail report PAX prints before the settlement slip,
     * gated by the "Settle Details Report" parameter.
     */
    private java.util.List<RLine> buildDetailReport(OnlineHost.SettleResult res) {
        TerminalConfig tc = TerminalConfig.get(getActivity());
        java.util.List<RLine> o = new java.util.ArrayList<>();
        addSharedHeader(o, res.batchNo, "");
        o.add(new RLine("DETAIL REPORT", "", R_MID, F_BOLD | F_CENTER));
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));

        // Five lines per transaction, sharing DetailReport with the copy the
        // reports menu prints so the two cannot drift. The previous three-column
        // form had to truncate the card number and had nowhere to put the
        // expiry, the transaction's own timestamp or how the card was read.
        for (String l : com.hjq.demo.utils.DetailReport.legendLines()) {
            o.add(new RLine(l, "", R_SMALL, 0));
        }
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        int n = com.hjq.demo.utils.DetailReport.totalCount(res.batch);
        for (TxnRecord r : res.batch) {
            for (String l : com.hjq.demo.utils.DetailReport.blockLines(
                    getApplicationContext(), r)) {
                o.add(new RLine(l, "", R_SMALL, 0));
            }
            o.add(new RLine("", "", 0, F_BLANK));
        }
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        o.add(new RLine(com.hjq.demo.utils.DetailReport.row(
                "TOTAL   " + com.hjq.demo.utils.DetailReport.count(n),
                tc.currencySymbol() + " "
                        + money(com.hjq.demo.utils.DetailReport.net(res.batch))),
                "", R_SMALL, F_BOLD));
        o.add(new RLine(SLIP_SEP, "", R_SMALL, F_SEP));
        o.add(new RLine("", "", 0, F_BLANK));
        o.add(new RLine(com.hjq.demo.utils.DetailReport.END, "",
                R_SMALL, F_CENTER));
        o.add(new RLine("", "", 0, F_BLANK));
        o.add(new RLine("", "", 0, F_BLANK));
        return o;
    }

    /**
     * One table row, exactly {@link #SLIP_W} characters: label left, count
     * centred-ish, amount right. The printer paint is monospace, so fixed
     * character columns are what actually line up on paper.
     *
     * <pre>
     * TRANSACTION   COUNT       AMOUNT
     * SALE            001        10.00
     * </pre>
     */
    /**
     * Split a three-column row, or null when this is an ordinary line.
     *
     * <p>Same encoding {@code Slip} uses, because both renderers draw the same
     * lines and a row that aligned on the reports but not on the sale slip
     * would be the third time that particular difference had to be found by
     * holding two pieces of paper together.</p>
     */
    private static String[] slipColumns(String s) {
        if (s == null) {
            return null;
        }
        char sep = s.indexOf(com.hjq.demo.utils.Slip.COL_R) >= 0
                ? com.hjq.demo.utils.Slip.COL_R
                : (s.indexOf(com.hjq.demo.utils.Slip.COL_L) >= 0
                        ? com.hjq.demo.utils.Slip.COL_L : 0);
        if (sep == 0) {
            return null;
        }
        String[] parts = s.split(String.valueOf(sep), -1);
        return parts.length == 3
                ? new String[] {parts[0], parts[1], parts[2], String.valueOf(sep)} : null;
    }

    private static void drawSlipColumns(Canvas c, String[] col, TextPaint p,
                                        int pad, int contentW, float baseline) {
        c.drawText(col[0], pad, baseline, p);
        if (!col[2].isEmpty()) {
            c.drawText(col[2], pad + contentW - p.measureText(col[2]), baseline, p);
        }
        if (col[1].isEmpty()) {
            return;
        }
        boolean rightAligned =
                String.valueOf(com.hjq.demo.utils.Slip.COL_R).equals(col[3]);
        float x = rightAligned
                ? pad + contentW * 0.66f - p.measureText(col[1])
                : pad + contentW * 0.38f;
        float floor = pad + p.measureText(col[0]) + 8;
        c.drawText(col[1], Math.max(floor, x), baseline, p);
    }

    private static String row3(String label, String middle, String amount) {
        return com.hjq.demo.utils.Slip.row3(label, middle, amount);
    }

    private static String count3(int v) {
        return String.format(java.util.Locale.US, "%03d", Math.max(0, v));
    }

    /**
     * Money for a report column, grouped in thousands: "28,254.77".
     *
     * <p>A settlement total is read by eye off a narrow roll and checked
     * against another figure on another slip. Without the grouping, six- and
     * seven-digit takings are simply miscounted.</p>
     */
    private static String money(long minor) {
        long a = Math.abs(minor);
        String whole = String.valueOf(a / 100);
        StringBuilder g = new StringBuilder();
        for (int i = 0; i < whole.length(); i++) {
            if (i > 0 && (whole.length() - i) % 3 == 0) {
                g.append(',');
            }
            g.append(whole.charAt(i));
        }
        return (minor < 0 ? "-" : "") + g + "."
                + String.format(java.util.Locale.US, "%02d", a % 100);
    }

    private String panOfTrack(String track2) {
        if (track2 == null) {
            return "";
        }
        int i = track2.indexOf('=');
        return i > 0 ? track2.substring(0, i) : track2;
    }

    private String appVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Run a settlement and print what the host decided.
     *
     * <p>Nothing is faked and nothing is cleared optimistically: the batch is
     * snapshotted first, the slip is built from the result the host actually
     * returned, and a failed settlement leaves the batch open so it can be
     * retried.</p>
     */
    private final java.util.concurrent.atomic.AtomicBoolean mFinancialStarted = new java.util.concurrent.atomic.AtomicBoolean();

    private void runSettlement() {
        if (!mFinancialStarted.compareAndSet(false, true)) return;
        final com.szanfu.sdk.api.pinpad.IPinPadBinderService _pp = mPinPadBinderService;
        applyCardlessLayout();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final OnlineHost.SettleResult res = OnlineHost.settle(
                        getApplicationContext(), _pp, new OnlineHost.Progress() {
                            @Override
                            public void step(final String text) {
                                // Progress goes on the panel, not into a dialog
                                // that is dismissed and recreated every step.
                                setCardlessStatus(text);
                            }
                        });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disDialog();
                        setCardlessStatus("Printing...");
                        finishSettlement(res);
                    }
                });
            }
        }).start();
    }

    private void finishSettlement(OnlineHost.SettleResult res) {
        sbMsg.setLength(0);
        mApproved = res != null && res.approved;
        mRespText = res != null ? res.message : "";

        // Nothing to settle is not a printed slip - PAX just says so.
        if (res.noTrans || res.reversalPending) {
            toast(res.message);
            sbMsg.append(res.message).append('\n');
            showSlipResult();
            return;
        }

        TerminalConfig tc = TerminalConfig.get(getActivity());

        // ONE bitmap, ONE print call. printBitmap() hands the job to the
        // printer service and returns immediately, so printing the detail
        // report and then the settlement slip as two calls raced and the
        // printer overlaid them on the same paper - which is why the slip came
        // out unreadable. Concatenating the models guarantees the order.
        java.util.List<RLine> model = new java.util.ArrayList<>();
        if (tc.settleDetailReport() && !res.batch.isEmpty()) {
            model.addAll(buildDetailReport(res));
        }
        model.addAll(buildSettlementSlip(res));

        sbMsg.append(renderText(model));
        previewReceipt("SETTLEMENT", model, () -> showSlipResult());
    }

    private void showSlipResult() {
        // Settlement's preview (or its no-transactions notice) has already finished.
        finishReceiptFlow();
    }

    /** Send the void for the invoice chosen on the previous screen. */
    private void runVoidTxn() {
        if (!mFinancialStarted.compareAndSet(false, true)) return;
        final String invoice = getIntent() == null ? null
                : getIntent().getStringExtra(EXTRA_VOID_INVOICE);
        final TxnRecord orig = BatchStore.get(getApplicationContext()).findByInvoice(invoice);
        if (orig == null) {
            toast("Original transaction not found");
            canCleTransaction();
            return;
        }
        mVoidOrigInvoice = orig.invoice;
        // A void never passes through the amount screen, so currentAmount is
        // null and the slip printed "AMOUNT: TK null". The amount being voided
        // is the original's, which is also what goes in DE4.
        currentAmount = String.format(java.util.Locale.US, "%d.%02d",
                orig.amount() / 100, Math.abs(orig.amount() % 100));
        // A completion may settle for less than was held - fuel, a tab, a
        // hotel folio - so the slip and the screen must show what is actually
        // being charged, not what was authorised.
        final String completionAmount = getIntent() == null ? null
                : getIntent().getStringExtra(
                        com.hjq.demo.ui.activity.VoidActivity.EXTRA_FINAL_AMOUNT);
        if ("SALE_COMPLETE".equals(transType)
                && completionAmount != null && !completionAmount.trim().isEmpty()) {
            currentAmount = completionAmount.trim();
        }
        if ("SALE_COMPLETE".equals(transType)) {
            // Pre Auth Percentage, checked again here so nothing above the
            // limit can be sent whichever screen started the completion.
            TerminalConfig ptc = TerminalConfig.get(getApplicationContext());
            long max = ptc.maxCompletionMinor(orig.amount());
            long want;
            try {
                want = com.hjq.demo.ans8583.TipAmounts.parse(currentAmount);
            } catch (RuntimeException e) {
                want = -1;
            }
            if (want <= 0 || want > max) {
                LogUtils.e("SALE_COMPLETE refused: " + currentAmount + " is above "
                        + ptc.preAuthPercentage() + "% of the pre-auth (" + orig.amount() + " minor)");
                toast("Completion amount exceeds " + ptc.preAuthPercentage() + "% of the pre-auth");
                mFinancialStarted.set(false);
                canCleTransaction();
                return;
            }
        }
        final String pinBlock = mPinBlockHex;
        applyCardlessLayout();
        setCardlessStatus("EMI_VOID".equals(transType)
                ? "Sending EMI void to host..."
                : "PREAUTH_VOID".equals(transType)
                        ? "Cancelling pre-authorisation..."
                : "SALE_COMPLETE".equals(transType)
                        ? "Completing pre-authorisation..."
                        : "Sending void to host...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                // An EMI void has to be cancelled on the same host application
                // that authorised it, so it goes out on the EMI NII. Sending it
                // on the acquirer NII would reach a host with no record of it.
                final OnlineHost.HostResult r;
                if ("SALE_COMPLETE".equals(transType)) {
                    // The final amount comes from the completion screen; it can
                    // differ from the amount that was authorised.
                    // currentAmount already resolved to the completion amount,
                    // falling back to the authorised one.
                    r = OnlineHost.saleComplete(getApplicationContext(), orig,
                            currentAmount, mPinPadBinderService);
                } else if ("PREAUTH_VOID".equals(transType)) {
                    // A pre-auth is released with an 0400 that repeats the
                    // authorisation's own STAN, RRN and approval code - not
                    // with the 0200 that voids a sale.
                    r = OnlineHost.preAuthVoid(getApplicationContext(), orig,
                            mPinPadBinderService);
                } else if ("EMI_VOID".equals(transType)) {
                    r = OnlineHost.emiVoid(getApplicationContext(), orig,
                            mPinPadBinderService);
                } else {
                    r = OnlineHost.voidSale(getApplicationContext(), orig, pinBlock,
                            mPinPadBinderService);
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disDialog();
                        mHostResult = r;
                        mHostOnline = r.online;
                        mHostRc = r.responseCode;
                        mHostAuth = r.authCode;
                        mHostRrn = r.rrn;
                        if (r.record != null) {
                            mInvoice = r.record.invoice;
                        }
                        LogUtils.i("VOID RESULT: "
                                + ResponseCode.displayText(r.responseCode, r.authCode, r.hostText)
                                + (r.error.isEmpty() ? "" : " | " + r.error));
                        if (r.approved && !r.needsReversal) {
                            // Flag the original as cancelled (spec 6.28.1 rule 9).
                            // Nothing called markVoided anywhere in the app, so a
                            // voided sale stayed "live" in the batch and settlement
                            // still counted it - the merchant was reconciling
                            // against money the host had already given back.
                            boolean marked = BatchStore.get(getApplicationContext())
                                    .markVoided(orig.invoice);
                            LogUtils.i("VOID: original invoice " + orig.invoice
                                    + (marked ? " marked voided in batch"
                                              : " NOT found in batch to mark"));
                        } else {
                            speak("Transaction failed");
                            toast(r.needsReversal ? (r.reversalAcknowledged
                                    ? "Void failed - reversal acknowledged" : "Void failed - reversal pending")
                                    : ResponseCode.displayText(r.responseCode, r.authCode, r.hostText));
                        }
                        mHandler.sendEmptyMessage(8003);   // print the slip either way
                    }
                });
            }
        }).start();
    }

    /**
     * Send the online financial request (spec 7.36) and drive what follows.
     *
     * <p>Everything the spec's SALE layout needs is gathered here rather than
     * being dropped: the full track 2 image for DE35 (condition C03), the
     * expiry for DE14 (C02), the EMV data for DE55 (C07) and the real entry
     * mode for DE22 (C05). On the way back, an approved chip transaction is
     * completed with a second Generate AC before the receipt prints.</p>
     */
    private void runOnlineTxn() {
        if (txnAborted("runOnlineTxn entry")) {
            return;
        }
        if (chipCardGone("runOnlineTxn entry")) {
            return;
        }
        if (!mExpiryWarningConfirmed && isExpiredCardCheckNeeded()) {
            showExpiredCardDialog(new Runnable() {
                @Override
                public void run() {
                    mExpiryWarningConfirmed = true;
                    runOnlineTxn();
                }
            });
            return;
        }
        mExpiryWarningConfirmed = true;
        if (!mFinancialStarted.compareAndSet(false, true)) return;
        // The fallback ladder is over - this card was read. Leaving its watcher
        // running would keep polling the card service for the whole host round
        // trip, competing with the slot sampling that actually matters now.
        stopChipSlotWatch();
        showProcessingScreen("Processing Transaction...", "Connecting to host, please wait...");

        // Clear last transaction's verdict so nothing leaks onto this receipt.
        mHostResult = null;
        mCardDeclined = false;
        mGac2Refused = false;
        mCardLeftMidFlight = false;
        mHostApprovedCardRefused = false;
        mGac2Reason = "";
        mCapkIndex = "";
        mCardDeclineReversed = false;
        mRespCode = "";
        mRespText = "";
        mApproved = false;
        mReversed = false;
        mBalance = "";
        mTvr = "";
        mTsi = "";
        mAtc = "";
        mCvm = "";
        mKernelCvm = "";
        mIssuerAuth = false;
        mScriptRun = false;
        // Cleared with the rest of the receipt state so an EMI plan from the
        // last void cannot appear in the heading of the next transaction.
        mVoidOrigRecord = null;
        mSlipEmiPlan = "";

        final String type = transType;
        final String amt = currentAmount;
        final String pinBlock = mPinBlockHex;

        // Prefer the PAN the chip or track actually gave us over the display copy.
        String panSrc = saleDataModel != null && saleDataModel.getCardNo() != null
                && !saleDataModel.getCardNo().isEmpty()
                ? saleDataModel.getCardNo() : tipCardNumber;
        final String pan = panSrc;

        final String track2 = saleDataModel == null ? null : saleDataModel.getTrack2data();
        final String iccData = saleDataModel == null ? null : saleDataModel.getIcData();
        final String expiry = expiryYyMm();
        // mMagFallback, not a hardcoded false. This argument was pinned to
        // false, so EntryMode.FALLBACK could never be produced: a chip card
        // taken on its stripe after the chip failed went out as an ordinary
        // swipe (0021), hiding the fallback from the issuer and putting the
        // liability in the wrong place. DBBL's reference terminal sends 0801
        // for exactly this case.
        final OnlineHost.EntryMode entry = mManualEntry
                ? OnlineHost.EntryMode.MANUAL
                : OnlineHost.entryModeFor(cardType, mMagFallback,
                        saleDataModel == null ? null : saleDataModel.getCardAID(), track2);
        // Default account (spec 6.2.1). Wire an account-selection prompt here if
        // DBBL wants savings / checking selection on the pad.
        final String accountSel = "00";

        // The wait dialog here is right - the terminal really is busy talking
        // to the host and the operator can do nothing but wait. The MESSAGE was
        // wrong: common_loading reads "SWIPE/INSERT/TAP CARD", which is what
        // the terminal wanted a moment ago, not what it is doing now.
        showDialog(getString(R.string.trans_waiting));

        new Thread(new Runnable() {
            @Override
            public void run() {
                // Last gate before the socket. The dialog above takes a frame
                // to draw and this thread takes another to start, which is
                // easily enough time for a cancel to land in between.
                if (txnAborted("send thread") || chipCardGone("send thread")) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            disDialog();
                        }
                    });
                    return;
                }
                // An EMI sale carries the chosen plan in DE48 and goes out on
                // the EMI NII, so it takes a different builder. Everything
                // after this point - GAC2, batch, slip - is identical.
                final OnlineHost.HostResult r;
                if (mKonaQr != null) {
                    // KonaMoney: the 37-digit track goes in DE35 and the
                    // 3-digit dCVV_MD in DE48 sub-element 31 (spec 2.1).
                    r = OnlineHost.konaSale(getApplicationContext(), amt,
                            mKonaQr.track2, mKonaQr.dcvvMd, null,
                            OnlineHost.EntryMode.QR, pinBlock, mPinPadBinderService);
                } else if ("EMI_SALE".equals(type) && mEmiTenure > 0) {
                    // The plan is only needed to work out an Easy Buy
                    // instalment for the receipt. DE61 carries the product and
                    // the term on its own, so a null plan is not an error.
                    com.hjq.demo.ans8583.EmiPackage plan =
                            com.hjq.demo.ans8583.EmiStore.get(getApplicationContext())
                                    .byProductCode(mEmiProduct);
                    if (plan == null) {
                        plan = com.hjq.demo.ans8583.EmiStore.get(getApplicationContext())
                                .byTenure(mEmiTenure);
                    }
                    r = OnlineHost.emiSale(getApplicationContext(), amt, pan, pinBlock,
                            track2, expiry, iccData, entry, plan, mEmiType, mEmiTenure,
                            mPinPadBinderService);
                } else {
                    r = OnlineHost.transaction(
                            getApplicationContext(), type, amt, pan, pinBlock,
                            track2, expiry, iccData, entry, accountSel, mPinPadBinderService,
                            mTipBaseMinor >= 0 ? new OnlineHost.SaleTip(mTipBaseMinor, mTipMinor) : null);
                }

                // Did the card survive the round trip?
                //
                // Between the 0200 going out and the 0410 coming back the
                // cardholder can simply pull the card, and at that point the
                // transaction cannot be completed: there is no chip left to
                // give a TC, and whatever the issuer said, the terminal must
                // not tell the merchant this sale happened. The request is
                // already in the network, so the answer is a reversal.
                //
                //
                // Checked explicitly rather than relying on GAC2 failing,
                // because "the kernel returned an error" and "the card is not
                // there any more" are not the same fact and only one of them
                // is certain.
                if (mCardLeftMidFlight) {
                    LogUtils.e("CARD REMOVED while the host was answering -"
                            + " the transaction is void and will be reversed");
                }

                // Chip cards need the issuer's answer fed back before we can
                // claim the transaction completed. Do it off the UI thread.
                final boolean gac2Ok = completeEmvOnline(r);
                // Complete persistence and any reversal on this worker. The
                // printer/UI cannot run until the final card result is known.
                boolean reversalAccepted = false;
                if (r.approved && !gac2Ok && r.record != null) {
                    r.reversalDecision = "HOST APPROVED / CHIP COMPLETION FAILED - REVERSAL REQUIRED";
                    runOnUiThread(() -> {
                        if (!isFinishing() && !isDestroyed()) showDialog("REVERSING TRANSACTION...");
                    });
                    reversalAccepted = OnlineHost.reverseCardDecline(
                            getApplicationContext(), r, mPinPadBinderService).approved;
                } else if (r.approved && gac2Ok) {
                    try {
                        OnlineHost.completeCardApproval(getApplicationContext(), r);
                        r.reversalDecision = "TRANSACTION COMPLETED - NO REVERSAL REQUIRED";
                    } catch (RuntimeException e) {
                        r.reversalDecision = "COMPLETION NOT SAVED - REVERSAL REQUIRED";
                        r.reversalAcknowledged = OnlineHost.reverseCardDecline(
                                getApplicationContext(), r, mPinPadBinderService).approved;
                        r.approved = false;
                        r.needsReversal = true;
                        r.error = "Could not save completed transaction; recovery requested";
                    }
                } else if (r.needsReversal) {
                    r.reversalDecision = "NO VALID TRANSACTION RESPONSE - REVERSAL REQUIRED";
                } else {
                    r.reversalDecision = r.online ? "HOST DECLINED - NO REVERSAL REQUIRED"
                            : "TRANSACTION NOT SENT - NO REVERSAL REQUIRED";
                }
                LogUtils.i("REVERSAL DECISION: " + r.reversalDecision
                        + " | hostRc=" + r.responseCode + " cardType=" + cardType
                        + " GAC2=" + gac2Ok + " acknowledged=" + r.reversalAcknowledged);
                final boolean cardDeclineReversed = reversalAccepted;

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        disDialog();
                        mHostResult = r;
                        mHostOnline = r.online;
                        mHostRc = r.responseCode;
                        mHostAuth = r.authCode;
                        mHostRrn = r.rrn;
                        if (r.record != null) {
                            mInvoice = r.record.invoice;
                        }
                        String text = ResponseCode.displayText(r.responseCode, r.authCode, r.hostText);
                        LogUtils.i("ONLINE RESULT: " + text
                                + (r.error.isEmpty() ? "" : " | " + r.error));

                        if (r.approved && gac2Ok) {
                            mHandler.sendEmptyMessage(8003);
                            return;
                        }
                        speak("Transaction failed");
                        if (!gac2Ok) {
                            // Host approved but the chip refused at GAC2.
                            //
                            // The issuer has authorised the amount and will
                            // settle it. Showing the merchant a decline and
                            // doing nothing else charges the cardholder for a
                            // sale the merchant was told did not happen, and
                            // leaves the entry in the batch to be settled on
                            // top. Reverse it (spec 7.35).
                            mGac2Refused = true;
                            // Only an approval leaves money at risk. A host
                            // decline followed by an AAC is an ordinary
                            // decline: nothing to reverse, nothing to verify -
                            // and nothing to blame the card for either. The
                            // chip was told the issuer refused and answered
                            // accordingly, so what the merchant needs to read
                            // is the issuer's reason, not "card declined".
                            mCardDeclined = r.approved;
                            mHostApprovedCardRefused = r.approved;
                            mCardDeclineReversed = cardDeclineReversed;
                            if (mCardLeftMidFlight) {
                                // Not a decline, and the operator must not be
                                // told it was - the cardholder pulled the card
                                // while the host was answering.
                                toast(r.approved && mCardDeclineReversed
                                        ? "Card removed - transaction reversed"
                                        : "Card removed - transaction cancelled");
                            } else if (!r.approved) {
                                // The host is the one that said no. Show its
                                // reason - "Issuer inoperative", "Do not
                                // honour", whatever DE39 carried.
                                toast(text);
                            } else {
                                toast(mCardDeclineReversed
                                        ? "Card declined - transaction reversed"
                                        : "Card declined the transaction");
                            }
                        } else if (r.needsReversal) {
                            // The request may have reached the host, so a 0400 is
                            // queued. Tell the operator plainly.
                            toast(r.reversalAcknowledged ? "Transaction failed - reversal acknowledged"
                                    : "Transaction failed - reversal pending");
                        } else if (!r.online) {
                            // Never got out: no working key, no host configured,
                            // pack error. r.error carries the real reason - show
                            // it rather than blaming the card.
                            mRespText = r.error.isEmpty() ? "TRANSACTION NOT SENT"
                                    : r.error.toUpperCase(java.util.Locale.US);
                            toast(r.error.isEmpty() ? "Transaction not sent" : r.error);
                        } else {
                            toast(text);
                        }
                        // Still print a slip: a declined or reversed transaction
                        // needs a record the merchant and cardholder can see.
                        mHandler.sendEmptyMessage(8003);
                    }
                });
            }
        }).start();
    }

    /**
     * EMV second Generate AC (GAC2).
     *
     * <p>After going online the kernel must be told what the issuer said, so it
     * can decide between a TC (accept) and an AAC (decline) and run any issuer
     * scripts. The payload is built from DE55 of the 0210 - tags 91, 71 and 72 -
     * plus tag 8A derived from DE39 (spec 6.23).</p>
     *
     * @return true when the card accepted, or when this is not a chip
     *         transaction and there is nothing to do
     */
    private boolean completeEmvOnline(OnlineHost.HostResult r) {
        // CONTACTLESS HAS NO SECOND GENERATE AC.
        //
        // A tap completes at the FIRST Generate AC - the card is out of the
        // field long before the host answers, so there is nothing left to talk
        // to. Running GAC2 anyway makes apiEmvKernelAfterOnlineProcess fail,
        // this method return false, and the caller then treats gac2Ok=false as
        // "the card declined". That is why a contactless sale showed as failed
        // even when the host had returned DE39 = 00.
        //
        // Contact ICC is different: the card is still inserted and must be told
        // what the issuer said so it can issue a TC or an AAC.
        if (cardType == CardSearchType.RF_CARD) {
            LogUtils.i("contactless: no GAC2 - the card completed at the first "
                    + "Generate AC and is out of the field. Host DE39 decides.");
            return true;
        }
        boolean isChip = cardType == CardSearchType.IC_CARD;
        if (!isChip) {
            return true;
        }
        if (!r.online || r.needsReversal || r.responseCode.isEmpty()) {
            // Never reached the host, so there is no issuer answer to complete
            // against. This is NOT a card decline - report it as true so the
            // real host-side error is what surfaces to the operator.
            LogUtils.e("GAC2 skipped: no host response to complete against");
            return true;
        }
        if (mCardLeftMidFlight || mEmvBinderService == null) {
            mGac2Reason = mCardLeftMidFlight ? "Card removed during authorisation"
                    : "EMV service unavailable during completion";
            return false;
        }
        try {
            byte[] issuerResp = Emv55.issuerResponse(r.responseIcc, r.responseCode);
            LogUtils.i("GAC2 issuer response: " + Iso8583.bytesToHex(issuerResp));
            int ret = mEmvBinderService.apiEmvKernelAfterOnlineProcess(issuerResp);
            LogUtils.i("apiEmvKernelAfterOnlineProcess ret=" + ret);
            if (ret != 0) {
                mGac2Reason = "apiEmvKernelAfterOnlineProcess ret=" + ret;
                LogUtils.e("GAC2 failed, ret=" + ret);
                return false;
            }
            EmvGetKernelProgressResult res = mEmvBinderService.emvGetKernelProgressResult();
            if (res == null || res.getTagData() == null || res.getTagData().length < 2) {
                mGac2Reason = "kernel returned no GAC2 result";
                LogUtils.e("GAC2 returned no kernel result");
                return false;
            }
            // Second byte is ucStatus: 0x10 approved, 0x20 declined by the card.
            String ucStatus = AFUtils.showResult16Str(new byte[]{res.getTagData()[1]});
            LogUtils.i("GAC2 kernel status: " + ucStatus);
            if (!"10".equals(ucStatus)) {
                // 0x20 is a real AAC: the card itself refused after seeing the
                // issuer response. Anything else is a kernel problem, not a
                // cardholder decision - say which, because they need different
                // fixes and the slip used to blame the card for both.
                mGac2Reason = "20".equals(ucStatus)
                        ? "card returned AAC at GAC2 (issuer auth or CDA failed)"
                        : "unexpected GAC2 kernel status 0x" + ucStatus;
            }
            if ("10".equals(ucStatus) && r.approved && r.record != null) {
                // Store the TC separately: reversal must retain the original ARQC.
                try {
                    String completed = getICData(new int[]{0x5F2A, 0x82, 0x84, 0x95, 0x9A,
                            0x9C, 0x9F02, 0x9F03, 0x9F09, 0x9F10, 0x9F1A, 0x9F26, 0x9F27,
                            0x9F33, 0x9F34, 0x9F35, 0x9F36, 0x9F37, 0x9F41, 0x5F34});
                    java.util.Map<String, String> tags = Emv55.parse(completed);
                    if ("40".equals(tags.get("9F27")) && tags.containsKey("9F26"))
                        r.record.completedIccHex = completed;
                } catch (RuntimeException e) { LogUtils.e("Final chip data unavailable: " + e.getMessage()); }
            }
            return "10".equals(ucStatus);
        } catch (Exception e) {
            mGac2Reason = "GAC2 exception: " + e.getMessage();
            LogUtils.e("GAC2 error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Expiry as YYMM for DE14. The chip gives tag 5F24 as YYMMDD, the magnetic
     * stripe path already yields YYMM.
     */
    private String expiryYyMm() {
        String v = resolveCardExpiry();
        v = v.replaceAll("[^0-9]", "");
        if (v.length() >= 4) {
            return v.substring(0, 4);
        }
        return "";
    }

    private String resolveCardExpiry() {
        String v = saleDataModel == null ? null : saleDataModel.getCardValid();
        if (v == null || v.isEmpty()) {
            v = cardValid;
        }
        if (v == null || v.isEmpty()) {
            v = getExpiryFromKernelTags();
            if (!TextUtils.isEmpty(v)) {
                cardValid = v;
            }
        }
        return v == null ? "" : v;
    }

    private String getExpiryFromKernelTags() {
        String exp = getTagValueSafe(0x5F24);
        if (!TextUtils.isEmpty(exp)) {
            String digits = exp.replaceAll("[^0-9]", "");
            if (digits.length() >= 4) {
                return digits;
            }
        }
        String t2 = getTagValueSafe(0x57);
        if (TextUtils.isEmpty(t2)) {
            t2 = getTagValueSafe(0x9F6B);
        }
        if (!TextUtils.isEmpty(t2)) {
            String upper = t2.toUpperCase(java.util.Locale.US);
            int sep = upper.indexOf('D');
            if (sep < 0) sep = upper.indexOf('=');
            if (sep >= 0 && upper.length() >= sep + 5) {
                String sub = upper.substring(sep + 1, sep + 5).replaceAll("[^0-9]", "");
                if (sub.length() == 4) {
                    return sub;
                }
            }
        }
        return "";
    }

    private boolean isExpiredCardCheckNeeded() {
        if (mExpiryWarningConfirmed) {
            return false;
        }
        TerminalConfig tc = TerminalConfig.get(this);
        if (!tc.checkExpiringDate()) {
            return false;
        }
        String exp = resolveCardExpiry();
        LogUtils.i("isExpiredCardCheckNeeded: resolved expiry='" + exp + "'");
        return TerminalConfig.isCardExpired(exp);
    }

    private void showExpiredCardDialog(final Runnable onContinue) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                disDialog();
                new MessageDialog.Builder(getActivity())
                        .setTitle("CARD EXPIRED")
                        .setMessage("Card expired. Want to continue or cancel the transaction?")
                        .setConfirm("CONTINUE")
                        .setCancel("CANCEL")
                        .setCancelable(false)
                        .setListener(new MessageDialog.OnListener() {
                            @Override
                            public void onConfirm(BaseDialog dialog) {
                                mExpiryWarningConfirmed = true;
                                if (onContinue != null) {
                                    onContinue.run();
                                }
                            }

                            @Override
                            public void onCancel(BaseDialog dialog) {
                                toast(getString(R.string.trans_canceled));
                                canCleTransaction();
                            }
                        })
                        .show();
            }
        });
    }

    private void printCopyFlow() {
        if (isFinishing() || isDestroyed() || !mReceiptFlow.start()) return;
        try {
            printCopyFlowInner();
        } catch (Throwable t) {
            // Whatever broke while building or drawing the slip must not take
            // the app down after the host has already approved the money.
            // Log it in full, print a plain record of the result instead, and
            // finish the transaction normally.
            Log.e(TAG, "SLIP FAILED for " + transType + " - printing a plain record instead", t);
            printFallbackSlip(t);
        }
    }

    /** The minimum a merchant needs on paper when the full slip could not be drawn. */
    private void printFallbackSlip(Throwable cause) {
        try {
            OnlineHost.HostResult r = mHostResult;
            boolean ok = r != null && r.approved && !r.needsReversal && !mCardDeclined;
            String amt = TerminalConfig.get(getApplicationContext()).currencySymbol()
                    + " " + orBlank(currentAmount);
            java.util.List<com.hjq.demo.utils.Slip.Line> s =
                    com.hjq.demo.utils.TxnSlip.notice(getApplicationContext(),
                            slipTitle(transType) + (ok ? " APPROVED" : " NOT APPROVED"),
                            "AMOUNT : " + amt,
                            r == null || orBlank(r.authCode).isEmpty() ? "" : "APPR CODE : " + r.authCode,
                            r == null || orBlank(r.rrn).isEmpty() ? "" : "RRN : " + r.rrn,
                            r == null || r.record == null ? "" : "INVOICE NO : " + r.record.invoice,
                            "REPRINT THE FULL SLIP FROM REPORTS");
            java.util.List<RLine> o = new java.util.ArrayList<>();
            for (com.hjq.demo.utils.Slip.Line ln : s) {
                o.add(new RLine(ln.left, ln.right, ln.size, ln.flags));
            }
            mApproved = ok;
            previewReceipt("TRANSACTION RECORD", o, this::finishReceiptFlow);
        } catch (Throwable t2) {
            Log.e(TAG, "fallback slip failed too", t2);
            toast("Receipt could not be printed - reprint from Reports");
            finishReceiptFlow();
        }
    }

    private void printCopyFlowInner() {
        spPrint.clear();
        prepareTxnData();
        // The slip is where the cardholder name, TSI and CVM verdict first
        // exist; the batch entry was written before the request went out. Keep
        // them now, so the reprint has the same document to work from.
        saveReceiptSnapshot();
        if (mApproved && !mReversed) {
            speak("Transaction successful. Amount " + currentAmount + " taka");
        } else if (mReversed) {
            speak(mHostResult != null && mHostResult.reversalAcknowledged
                    ? "Transaction failed. Reversal acknowledged"
                    : "Transaction failed. Reversal pending");
        } else {
            speak("Transaction declined");
        }
        // A transaction that did not complete gets the short failure slip, not
        // a sale slip with its approval code, signature line and totals left
        // blank.
        final boolean isSuccessful = mApproved && !mReversed;
        final boolean customerEnabled = com.hjq.demo.ans8583.TerminalConfig.get(getApplicationContext()).customerCopyEnabled();
        final boolean offerCustomer = isSuccessful && customerEnabled;

        LogUtils.i("SLIP " + transType + ": approved=" + mApproved + " reversed=" + mReversed
                + " rc=" + mRespCode + " cardDeclined=" + mCardDeclined
                + " -> " + (isSuccessful ? "full merchant copy" : "short failure slip"));
        java.util.List<RLine> merchant = isSuccessful
                ? buildCopy("MERCHANT COPY") : buildFailureSlip();
        sbMsg.setLength(0);
        sbMsg.append(renderText(merchant));
        previewReceipt(isSuccessful ? "MERCHANT COPY" : "TRANSACTION RESULT", merchant, new Runnable() {
            @Override
            public void run() {
                if (!mReceiptFlow.merchantDone(offerCustomer)) return;
                if (offerCustomer && mReceiptFlow.stage() == com.hjq.demo.utils.ReceiptFlow.Stage.CUSTOMER_CHOICE) {
                    askCustomerCopy();
                } else {
                    finishReceiptFlow();
                }
            }
        });
    }

    private void askCustomerCopy() {
        if (isFinishing() || isDestroyed()) return;
        new MessageDialog.Builder(getActivity())
                .setTitle("Print Customer Copy?")
                .setMessage("Print a customer copy?")
                .setConfirm("YES")
                .setCancel("NO")
                .setCancelable(false)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        if (!mReceiptFlow.chooseCustomerCopy(true)) return;
                        try {
                            java.util.List<RLine> customer = buildCopy("CUSTOMER COPY");
                            previewReceipt("CUSTOMER COPY", customer, () -> {
                                if (mReceiptFlow.customerDone()) finishReceiptFlow();
                            });
                        } catch (Throwable t) {
                            Log.e(TAG, "SLIP FAILED (customer copy) for " + transType, t);
                            toast("Customer copy could not be printed - reprint from Reports");
                            finishReceiptFlow();
                        }
                    }
                    @Override
                    public void onCancel(BaseDialog dialog) {
                        if (mReceiptFlow.chooseCustomerCopy(false)) finishReceiptFlow();
                    }
                })
                .show();
    }

    private void finishReceiptFlow() {
        if (mReceiptFlowFinished || isFinishing() || isDestroyed()) return;
        mReceiptFlowFinished = true;
        stopFindCard = true;
        mCardSearchQueue.cancel();
        stopChipSlotWatch();
        mHandler.removeMessages(8003);
        spPrint.put("PrintFinish", true);

        boolean success = mApproved && !mReversed;
        boolean isSettle = "SETTLE".equals(transType) || "SETTLEMENT".equals(transType);
        String title;
        String msg;
        if (isSettle) {
            title = success ? "SETTLEMENT SUCCESS" : "SETTLEMENT FAILED";
            msg = mRespText != null && !mRespText.isEmpty() ? mRespText
                    : (success ? "Batch settled successfully." : "Settlement failed.");
        } else if (success) {
            title = "TRANSACTION APPROVED";
            String auth = mHostAuth != null && !mHostAuth.isEmpty() ? mHostAuth
                    : (mApprCode != null && !mApprCode.isEmpty() ? mApprCode : "");
            String rrn = mHostRrn != null && !mHostRrn.isEmpty() ? mHostRrn
                    : (mRref != null && !mRref.isEmpty() ? mRref : "");
            StringBuilder sb = new StringBuilder();
            if (!auth.isEmpty()) sb.append("AUTH CODE: ").append(auth);
            if (!rrn.isEmpty()) {
                if (sb.length() > 0) sb.append("   ");
                sb.append("RRN: ").append(rrn);
            }
            if (sb.length() == 0) sb.append("Transaction completed successfully");
            msg = sb.toString();
        } else if (mReversed) {
            title = "TRANSACTION REVERSED";
            msg = (mHostResult != null && mHostResult.reversalAcknowledged)
                    ? "Transaction reversed and acknowledged by host."
                    : "Transaction reversed. Pending reversal sync.";
        } else {
            title = "TRANSACTION DECLINED";
            String code = mRespCode != null && !mRespCode.isEmpty() ? "[" + mRespCode + "] " : "";
            String text = mRespText != null && !mRespText.isEmpty() ? mRespText : "DECLINED";
            msg = code + text;
        }
        showTxnResult(success, title, msg);
    }

    private void showProcessingScreen(final String title, final String message) {
        mShowingTxnStage = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                stopCardPrompt();
                disDialog();
                View cardPanel = findViewById(R.id.card_prompt_panel);
                if (cardPanel != null) cardPanel.setVisibility(View.GONE);
                View noCard = findViewById(R.id.no_card_panel);
                if (noCard != null) noCard.setVisibility(View.GONE);

                View resultPanel = findViewById(R.id.txn_result_panel);
                if (resultPanel != null) resultPanel.setVisibility(View.VISIBLE);

                TextView tvType = findViewById(R.id.tv_result_txn_type);
                if (tvType != null) {
                    String term = emiTenureText();
                    tvType.setText(term.isEmpty() ? slipTitle(transType)
                            : slipTitle(transType) + "  -  " + term);
                }

                TextView tvAmt = findViewById(R.id.tv_result_amount);
                if (tvAmt != null) {
                    TerminalConfig tc = TerminalConfig.get(getApplicationContext());
                    String a = currentAmount == null ? "" : currentAmount.trim();
                    tvAmt.setText(a.isEmpty() ? "" : tc.currencySymbol() + " " + a);
                }

                ImageView icon = findViewById(R.id.iv_result_icon);
                if (icon != null) icon.setVisibility(View.GONE);

                ProgressBar pb = findViewById(R.id.pb_result_progress);
                if (pb != null) pb.setVisibility(View.VISIBLE);

                TextView tvTitle = findViewById(R.id.tv_result_title);
                if (tvTitle != null) {
                    tvTitle.setTextColor(Color.parseColor("#100567"));
                    tvTitle.setText(title != null ? title : "Processing Transaction...");
                }

                TextView tvMsg = findViewById(R.id.tv_result_message);
                if (tvMsg != null) {
                    tvMsg.setText(message != null ? message : "Please wait...");
                }

                TextView tvTimer = findViewById(R.id.tv_result_timer);
                if (tvTimer != null) tvTimer.setText("");
            }
        });
    }

    private void showTxnResult(final boolean success, final String title, final String message) {
        mShowingTxnStage = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                stopCardPrompt();
                disDialog();
                View cardPanel = findViewById(R.id.card_prompt_panel);
                if (cardPanel != null) cardPanel.setVisibility(View.GONE);
                View noCard = findViewById(R.id.no_card_panel);
                if (noCard != null) noCard.setVisibility(View.GONE);

                View resultPanel = findViewById(R.id.txn_result_panel);
                if (resultPanel != null) resultPanel.setVisibility(View.VISIBLE);

                TextView tvType = findViewById(R.id.tv_result_txn_type);
                if (tvType != null) {
                    String term = emiTenureText();
                    tvType.setText(term.isEmpty() ? slipTitle(transType)
                            : slipTitle(transType) + "  -  " + term);
                }

                TextView tvAmt = findViewById(R.id.tv_result_amount);
                if (tvAmt != null) {
                    TerminalConfig tc = TerminalConfig.get(getApplicationContext());
                    String a = currentAmount == null ? "" : currentAmount.trim();
                    tvAmt.setText(a.isEmpty() ? "" : tc.currencySymbol() + " " + a);
                }

                ProgressBar pb = findViewById(R.id.pb_result_progress);
                if (pb != null) pb.setVisibility(View.GONE);

                ImageView icon = findViewById(R.id.iv_result_icon);
                if (icon != null) {
                    icon.setImageResource(success ? R.drawable.ic_txn_success : R.drawable.ic_txn_failed);
                    icon.setVisibility(View.VISIBLE);
                }

                TextView tvTitle = findViewById(R.id.tv_result_title);
                if (tvTitle != null) {
                    tvTitle.setTextColor(Color.parseColor(success ? "#2E7D32" : "#D32F2F"));
                    tvTitle.setText(title != null && !title.isEmpty() ? title
                            : (success ? "TRANSACTION APPROVED" : "TRANSACTION FAILED"));
                }

                TextView tvMsg = findViewById(R.id.tv_result_message);
                if (tvMsg != null) {
                    tvMsg.setText(message != null ? message : "");
                }

                TextView tvTimer = findViewById(R.id.tv_result_timer);
                if (tvTimer != null) {
                    tvTimer.setText("Returning to Home in 2s...");
                }

                mHandler.removeCallbacks(mGoHomeRunnable);
                mHandler.postDelayed(mGoHomeRunnable, 2000);
            }
        });
    }

    private void abortTransaction(final boolean showResult, final String title, final String reason) {
        mTxnAborted = true;
        stopFindCard = true;
        mCardSearchQueue.cancel();
        stopChipSlotWatch();
        // A kernel left waiting for a PIN pad that will now never open would
        // keep the card service - and every later card screen - blocked.
        releaseKernelPinWait("transaction aborted: " + reason);
        disDialog();
        LogUtils.e("关闭EMV寻卡....");
        closeReaderForExit("abort");
        if (showResult) {
            showTxnResult(false, title, reason);
        } else {
            runOnUiThread(this::goHome);
        }
    }

    private void previewReceipt(String title, java.util.List<RLine> lines, Runnable onDone) {
        if (isFinishing() || isDestroyed()) return;
        if (mReceiptPreview != null && mReceiptPreview.isShowing()) return;
        mReceiptPreview = new com.hjq.demo.ui.dialog.ReceiptPreviewDialog(this, title,
                renderReceiptBitmap(lines), DeviceType.A6.equals(customerType) ? null
                : mPrinterBinderService, onDone, true);
        mReceiptPreview.show();
    }

    private void speak(final String text) {
        if (text == null) return;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                restoreAudio();
                if (playClip(text)) return;
                if (mTts != null && mTtsReady) {
                    try { mTts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pax_tts"); return; } catch (Exception ignore) {}
                }
                toneBeep();
            }
        });
    }

    private boolean playClip(String text) {
        try {
            String t = text.toLowerCase(java.util.Locale.US);
            String raw = null;
            if (t.contains("insert") || t.contains("tap")) raw = "voice_insert_card";
            else if (t.contains("pin")) raw = "voice_enter_pin";
            else if (t.contains("successful") || t.contains("approved")) raw = "voice_success";
            else if (t.contains("failed")) raw = "voice_failed";
            int resId = raw == null ? 0 : getResources().getIdentifier(raw, "raw", getPackageName());
            if (resId != 0) {
                android.media.MediaPlayer mp = android.media.MediaPlayer.create(this, resId);
                if (mp != null) {
                    mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                        @Override public void onCompletion(android.media.MediaPlayer m) { try { m.release(); } catch (Exception ignore) {} }
                    });
                    mp.start();
                    return true;
                }
            }
        } catch (Exception ignore) {}
        return false;
    }

    private void toneBeep() {
        try {
            final android.media.ToneGenerator tg = new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 90);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 200);
            getWindow().getDecorView().postDelayed(new Runnable() { @Override public void run() { try { tg.release(); } catch (Exception ignore) {} } }, 400);
        } catch (Exception ignore) {}
    }

    private void restoreAudio() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am == null) return;
            int[] streams = { android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.STREAM_SYSTEM, android.media.AudioManager.STREAM_NOTIFICATION };
            for (int st : streams) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) am.adjustStreamVolume(st, android.media.AudioManager.ADJUST_UNMUTE, 0);
                    else am.setStreamMute(st, false);
                } catch (Exception ignore) {}
            }
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0)
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.max(1, (int) (max * 0.7)), 0);
        } catch (Exception ignore) {}
    }

    private void initTts() {
        if (mTts != null) return;
        try {
            mTts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS) {
                        int r = TextToSpeech.LANG_NOT_SUPPORTED;
                        try {
                            r = mTts.setLanguage(java.util.Locale.US);
                            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = mTts.setLanguage(java.util.Locale.ENGLISH);
                            if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) r = mTts.setLanguage(java.util.Locale.getDefault());
                        } catch (Exception ignore) {}
                        mTtsReady = (r >= TextToSpeech.LANG_AVAILABLE);
                        if (mTtsReady && mPendingSpeak != null) { String p = mPendingSpeak; mPendingSpeak = null; speak(p); }
                    }
                }
            });
        } catch (Exception e) { LogUtils.e("TTS init error: " + e.getMessage()); }
    }

    private void savaTrans(String id, String date_time) {
        String method = null;
        TransOrder order = new TransOrder();
        order.setOrder_amount(currentAmount);
        order.setOrder_id(id);
        order.setOrder_date_time(date_time);
        order.setOrder_cardno(tipCardNumber);
        order.setOrder_expdate(cardValid);
        order.setOrder_method(cardType);
        if (signature != null) {
            order.setOrder_signature(signature);
        }
        LogUtils.i("savaTrans: id" + order.toString());
        boolean saveRes = order.save();
        LogUtils.i("savaTrans: " + saveRes);
    }


    /**
     * 生成随机数
     *
     * @param length 生成的长度
     * @return 随机数
     */
    public static String generateRandomNumber(int length) {
        Random random = new Random();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        LogUtils.i("generateRandomNumber: " + sb.toString());
        return sb.toString();
    }

    /**
     * 等待对话框
     */
    private BaseDialog mWaitDialog;
    private Handler mHandler = new Handler(Looper.myLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 3000:
                    disDialog();
                    LogUtils.i("stopPinPad: Success");
                    Bundle printErr = msg.getData();
                    String print_err_info = printErr.get("print_err").toString();
                    new MessageDialog.Builder(getActivity())
                            .setTitle(R.string.pop_notice)
                            .setMessage(print_err_info)
                            .setConfirm(getString(R.string.common_confirm))
                            .setCancel(null)
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    toast("确定");
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
                                }
                            })
                            .show();
                    break;
                case 3001:
                    disDialog();
                    //寻卡超时
                    resetSP();
                    break;
                case 8000:
                    //开始寻卡
                    //
                    // This raised the wait dialog with R.string.common_loading,
                    // which on this build is the text "SWIPE/INSERT/TAP CARD" -
                    // a prompt, not a progress message. It went up the instant
                    // card search started and sat in the middle of the screen
                    // whose entire job is to show, in three animated tiles, how
                    // to swipe, insert and tap. The screen says it better than
                    // the box covering it did.
                    isSearchingCard = true;
                    setCardStatus(getString(R.string.common_loading));
                    break;
                case 8001:
                    //磁条卡
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.swipe_card))
                            .create();
                    mWaitDialog.show();
                    break;
                case 8002:
                    // No "Starting transaction" toast. runOnlineTxn puts up a
                    // proper wait dialog a line later, so the toast was a
                    // second, briefer notice saying the same thing - and it
                    // outlived a cancelled transaction, because a toast cannot
                    // be taken back once it is queued.
                    runOnlineTxn();
                    /*
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.trans_waiting))
                            .create();
                    mWaitDialog.show();
                    mHandler.sendEmptyMessageDelayed(8003, 1500);
                     */
                    break;
                case 8003:
                    LogUtils.i("打印小票...");
                    if (mWaitDialog != null) {
                        mWaitDialog.dismiss();
                    }
                    setSimulateTouch();
                    mHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            printCopyFlow();
                        }
                    }, 200);


                  /*  //打印小票
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.print_state))
                            .create();
                    mWaitDialog.show();*/
                    break;
                case 8004:
                    //寻卡失败
                    isSearchingCard = false;
                    disDialog();
                    break;
                case 8005:
                    if (mChipDialogUp || mRetryDialogUp || mTxnAborted || isFinishing()) break;
                    // Reading the card.
                    //
                    // This used to raise the wait dialog, whose text is
                    // "SWIPE/INSERT/TAP CARD" - a prompt, not a progress
                    // message - directly on top of the screen that already
                    // shows the three ways to present a card, with the
                    // artwork for each. The status line under the tiles says
                    // it instead.
                    setCardStatus("Reading card - hold it still");
                    showCardReading();
                    break;
                case MSG_TRANS_MSR_SUCCESS:
                    //磁条卡
                    LogUtils.e("handleMessage: MSG_TRANS_MSR_SUCCESS");
                    isSearchingCard = false;
                    closeFindCard();
                    isShowCardNo = true;
                    disDialog();
                    Bundle cardData = msg.getData();
                    String cardNumber = cardData.get("track2Data").toString();

                    if (cardNumber.equals("Read Card Error")) {
                        toast(getString(R.string.card_number_read_error));
                        return;
                    }

                    if (cardNumber.contains("F")) {
                        cardNumber.replaceAll("F", "");
                    }
                    mCardReadOk = true;
                    stopChipSlotWatch();
                    // The same card box chip and contactless get.
                    //
                    // This was the one read method still using a plain message
                    // dialog with the RAW PAN as its body - unmasked, in a box
                    // titled "transaction successful" before anything had been
                    // sent, with the confirm and cancel captions swapped on
                    // keyboard units so the button reading CANCEL was the one
                    // that went ahead. showConfirmationDialog draws the card,
                    // masks the number to the parameter, colours the buttons
                    // red-left green-right on every unit, and counts itself
                    // down. There is no reason a swipe should look different
                    // from an insert.
                    showConfirmationDialog(cardNumber, new FindCardActivity.ConfirmationCallback() {
                        @Override
                        public void onResult(int result) {
                            if (result != 0) {
                                return;   // the dialog has already ended it
                            }
                            showPinPad(true, currentCardNumber, 1);
                        }
                    });
                    break;
                case MSG_TRANS_IC_RF_SUCCESS:
                    LogUtils.e("handleMessage: MSG_TRANS_IC_RF_SUCCESS");
                    //ic卡
                    isSearchingCard = false;
                    closeFindCard();
                    disDialog();
                    Bundle cardDataBundle = msg.getData();
                    icCardNumber = cardDataBundle.get("cardData").toString();
                    //int cardType = Integer.parseInt(cardDataBundle.get("cardType").toString());
                    LogUtils.i("icCardNumber:" + icCardNumber);
                    //LogUtil.i(TAG, "cardType:" + cardType);
                    int icCardNumberLen = icCardNumber.length();
                    if (icCardNumber.contains("F")) {
                        icCardNumber = icCardNumber.replaceAll("F", "");
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 10).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    } else {
                        currentCardNumber = icCardNumber.substring(icCardNumberLen - 9).replaceAll("\\d{5}(\\d{4})", "*****$1");
                    }
                    LogUtils.i("icCardTip:" + currentCardNumber);
                    //mHandler.sendEmptyMessageDelayed(8003, 1000);
//                    enterPinBlock();
                    //$1 $2 表示正则表达式里面的第一个和第二个，也就是括号里面的内容
                    // 消息对话框
                    new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_success)
                            //设置按下返回键不能消失
                            .setCancelable(false)
                            // 内容必须要填写
                            .setMessage(icCardNumber)
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(getString(R.string.trans_canceled))
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    //toast("确定");
                                    showPinPad(true, icCardNumber, 1);
//                                    showCustomizePinPad();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    dialog.dismiss();
                                    toast(getString(R.string.trans_canceled));
//                                    etAmount.setText("￥0.00");

                                }
                            }).show();
                    break;
                case MSG_TRANS_FAil:
                    disDialog();
                    resetSP();
                    speak("Transaction failed");
                    Bundle cardDataFail = msg.getData();
                    // 消息对话框
                    new MessageDialog.Builder(getActivity())
                            // 标题可以不用填写
                            .setTitle(R.string.trans_fail)
                            // 内容必须要填写
                            .setMessage(cardDataFail.get("errInfo").toString())
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(null)
                            // 设置空白区域不可取消
                            .setCanceledOnTouchOutside(false)
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    // toast(R.string.amount_confirm);
                                    canCleTransaction();
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    // toast(R.string.amount_clear);
                                }
                            }).show();
                    break;
                case MSG_SHOW_SIGNATURE_DIALOG:
                    mHandler.sendEmptyMessage(8002);
                    break;
                default:
                    break;
            }
        }
    };
    private SPUtils spError = SPUtils.getInstance("ErrorResult");

    /**
     * Accepted PIN length, ISO 9564: 4 to 12 digits.
     *
     * <p>The PIN block carries its own length in nibble 1, so this is the range
     * the acquirer's HSM will accept. A block whose length nibble falls outside
     * it is rejected with Thales command DA error 24, "PIN is fewer than 4 or
     * more than 12 digits in length" - the PIN length error that was being
     * reported. {@link DynamicPinPad} shows {@code PIN_MIN_LEN} boxes to start
     * with and adds one per digit up to {@code PIN_MAX_LEN}.</p>
     */
    private static final int PIN_MIN_LEN = 4;
    private static final int PIN_MAX_LEN = 12;
    /**
     * PIN entry timeout, in seconds - the menu timeout from Parameters.
     *
     * <p>It was a hard-coded 60. The PIN screen is the same kind of unattended
     * prompt the card screen is, so it waits the same configured time and then
     * goes home. Clamped to the SDK's documented 5..180 range: a menu timeout
     * outside that would be rejected by the pad, and a terminal that cannot
     * raise a PIN pad is worse than one that waits a little longer.</p>
     */
    private int pinTimeoutSec() {
        int v = TerminalConfig.get(getApplicationContext()).idleTimeoutSec();
        if (v <= 0) {
            v = 60;                       // timeout disabled: the pad still needs one
        }
        return v < 5 ? 5 : (v > 180 ? 180 : v);
    }

    /**
     * How long after the pad's own timeout the terminal waits before giving up
     * on it. The SDK owns the pad and should report the timeout itself; this
     * is the backstop for when it does not.
     */
    private static final int PIN_WATCHDOG_GRACE_SEC = 10;

    /**
     * One terminal answer per PIN session, whichever arrives first.
     *
     * <p>A PIN pad can report more than once - a timeout followed by a stale
     * result, an error followed by a cancel. Whichever lands first decides the
     * transaction; the rest are dropped. Without this a late
     * {@code onGetPinBlock} could restart a sale the terminal had already
     * cancelled.</p>
     */
    private final java.util.concurrent.atomic.AtomicBoolean mPinDone =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /** Fires if the pad produces no answer at all. */
    private Runnable mPinWatchdog;

    /**
     * Which DE62 reading the PIN pad enciphered the last block under, as
     * identified by {@link OnlineHost#identifyPinKey}. Printed on the slip when
     * the packet trace is on, so a declined sale carries the evidence with it.
     */
    private String mPinKeyReport = "";

    /**
     * 用户输入密码弹窗
     *
     * @param keyIndex               The key index allows users to operate 9 groups. The valid index value ranges from 2 to 10
     *                               秘钥索引,可供用户操作9组,合法索引值为2-10
     * @param setPasswordLengthLimit Set PinPad parameters, including whether an empty password is supported and whether the password length can be entered. The value is 0, 6,6. The first digit 0 cannot be pressed directly
     *                               设置PinPad参数，是否支持空密码和支持输入密码长度位数,有效值[0, 6,6] 第一位0不允许直接按OK 1：允许直接按OK
     * @param hasCancelButton        Whether to CANCEL is required true: Yes false: No [Valid only for 930 devices]
     *                               是否需要CANCEL按键 true：需要 false:不需要 【只对930设备生效】
     * @param amount                 Transaction amount
     *                               交易金额
     * @param cardNumber             Bank card number, between 12 and 19 in length
     *                               银行卡号,长度在12-19之间
     * @param encryptionMode         Encryption mode 0:DES/3DES 1:SM4
     *                               加密模式 0:DES/3DES 1:SM4
     * @param inputPwdTimeout        The maximum timeout period for entering a password is 3 minutes and the minimum timeout period is 5 seconds. The timeout period starts when the password keyboard is displayed
     *                               输入密码超时时间，单位S，最长不应超过3分钟,最短不建议少于5秒，从密码键盘显示开始计时
     * @param ucPinType              0.脱机PIN 1.联机PIN
     * @param pinPadCallback         Enter a password-related listening callback
     *                               输入密码相关监听回调
     *                               <p>
     *                               <p>
     *                               param keyIndex               The key index allows users to operate 9 groups. The valid index value ranges from 2 to 10
     *                               param setPasswordLengthLimit   Set PinPad parameters, including whether an empty password is supported and whether the password length can be entered. The value is 0, 6,6. The first digit 0 cannot be pressed directly
     *                               param hasCancelButton   Whether to CANCEL is required true: Yes false: No [Valid only for 930 devices]
     *                               param amount          Transaction amount
     *                               param cardNumber      Bank card number, between 12 and 19 in length
     *                               param encryptionMode  Encryption mode 0:DES/3DES 1:SM4
     *                               param inputPwdTimeout The maximum timeout period for entering a password is 3 minutes and the minimum timeout period is 5 seconds. The timeout period starts when the password keyboard is displayed
     *                               param pinPadCallback  Enter a password-related listening callback
     */

    /**
     * Start a PIN session: no answer yet, and a clock running on it.
     */
    /**
     * Set the moment this transaction is abandoned, and never cleared.
     *
     * <p>Cancelling on the PIN pad used to end at {@code canCleTransaction()},
     * which closes the reader and walks back to the menu - but the EMV kernel
     * and the message handler both have their own routes into
     * {@link #runOnlineTxn()}, and one of those was still firing after the
     * cancel had been accepted. The terminal was assembling and sending a 0200
     * for a transaction the cardholder had already stopped.</p>
     *
     * <p>So there is a hard latch as well. It is checked at the top of
     * {@code runOnlineTxn}, again on the sending thread immediately before the
     * socket, and in {@code continueAfterPin}. Volatile because it is written
     * on a binder thread (the pad's cancel callback) and read on the UI thread
     * and on the sending thread. Once true it stays true: this activity
     * instance is finished with, and a new sale is a new instance.</p>
     */
    private boolean mExpiryWarningConfirmed = false;
    private volatile boolean mTxnAborted = false;

    /**
     * Has the contact chip left the slot? Reports, and does nothing else.
     *
     * <p>This used to CANCEL the transaction and send nothing, and that was
     * wrong once the PIN had been entered. By the time the online request is
     * being built the kernel has already produced the ARQC and the PIN block
     * exists - the transaction is committed, and EMV says it goes online.
     * Pulling the card at that point does not un-commit it. Suppressing the
     * 0200 only meant the terminal quietly dropped a transaction it was
     * required to complete one way or the other, which is why removing the
     * card straight after the PIN produced "Card removed - transaction
     * cancelled" and no reversal at all: the request the reversal would have
     * reversed was never sent.</p>
     *
     * <p>Restored after a failed experiment. Polling the slot through the host
     * round trip and reversing on any empty reading looked right on paper and
     * was wrong on the device: one transient false from isCardInserted() during
     * the EMV online step reversed a perfectly good sale. The PAX reference
     * does not ask this question anywhere in its online path - the EMV kernel's
     * GAC2 result is what decides - so neither do we.</p>
     *
     * <p>Only contact chip. A tap is over in a few hundred milliseconds and a
     * swipe has nothing left in the reader by definition, so asking about
     * either would flag perfectly good transactions.</p>
     */
    private boolean chipCardGone(String where) {
        if (cardType != CardSearchType.IC_CARD) {
            return false;
        }
        boolean gone;
        try {
            gone = mCardBinderService != null && !mCardBinderService.isCardInserted();
        } catch (Throwable e) {
            // A reader that cannot be asked is not evidence the card left.
            LogUtils.e("isCardInserted failed at " + where + ": " + e);
            return false;
        }
        if (!gone) {
            return false;
        }
        LogUtils.e("CARD REMOVED before the transaction went online (" + where
                + ") - cancelling, nothing will be sent");
        mTxnAborted = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disDialog();
                toast("Card removed - transaction cancelled");
                canCleTransaction();
            }
        });
        return true;
    }


    /** True when this transaction must not touch the host again. */
    private boolean txnAborted(String where) {
        if (mTxnAborted) {
            LogUtils.e("ABORTED transaction still tried to go online at "
                    + where + " - blocked");
            return true;
        }
        return false;
    }

    private void armPinSession() {
        mPinDone.set(false);
        cancelPinWatchdog();
        final int secs = pinTimeoutSec() + PIN_WATCHDOG_GRACE_SEC;
        mPinWatchdog = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !mPinDone.compareAndSet(false, true)) {
                    return;
                }
                // Nothing came back from the pad at all. End the sale here.
                // The alternative - carrying on and hoping - is how an
                // unattended terminal sends a transaction nobody authorised.
                Log.e(TAG, "PIN watchdog: no result after " + secs + "s."
                        + " Cancelling the transaction.");
                toast("PIN entry timed out");
                canCleTransaction();      // closes the reader and goes home
            }
        };
        mHandler.postDelayed(mPinWatchdog, secs * 1000L);
    }

    private void cancelPinWatchdog() {
        if (mPinWatchdog != null) {
            mHandler.removeCallbacks(mPinWatchdog);
            mPinWatchdog = null;
        }
    }

    /**
     * Claim this PIN session's single answer.
     *
     * @return true if this caller owns the outcome, false if something already
     *         decided it and this result must be ignored
     */
    private boolean claimPinResult(String who) {
        if (!mPinDone.compareAndSet(false, true)) {
            Log.e(TAG, "PIN result '" + who + "' arrived after the session was"
                    + " already settled - ignoring it");
            return false;
        }
        cancelPinWatchdog();
        return true;
    }

    /**
     * Confirm a no-PIN sale with the operator, and give up if nobody answers.
     *
     * <p>The dialog closes itself after the terminal's idle timeout and
     * cancels the transaction. That is the whole point: the danger is a
     * terminal standing unattended on this screen, so the safe outcome has to
     * be the one that needs nobody present.</p>
     */
    private void confirmNoPin() {
        final long idleMs =
                TerminalConfig.get(getActivity()).idleTimeoutSec() * 1000L;
        final java.util.concurrent.atomic.AtomicBoolean answered =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable[] giveUp = new Runnable[1];

        final com.hjq.base.BaseDialog dialog =
                new com.hjq.demo.ui.dialog.MessageDialog.Builder(getActivity())
                        .setTitle("NO PIN ENTERED")
                        .setMessage("No PIN was entered for this card.\n\n"
                                + "Continue without a PIN?")
                        .setConfirm("CONTINUE")
                        .setCancel(getString(R.string.common_cancel))
                        .setListener(new com.hjq.demo.ui.dialog.MessageDialog.OnListener() {
                            @Override
                            public void onConfirm(com.hjq.base.BaseDialog d) {
                                if (!answered.compareAndSet(false, true)) {
                                    return;
                                }
                                mHandler.removeCallbacks(giveUp[0]);
                                LogUtils.i("operator confirmed a no-PIN sale");
                                showProcessingScreen("Processing Transaction...", "Connecting to host, please wait...");
                                continueAfterPin();
                            }

                            @Override
                            public void onCancel(com.hjq.base.BaseDialog d) {
                                if (!answered.compareAndSet(false, true)) {
                                    return;
                                }
                                mHandler.removeCallbacks(giveUp[0]);
                                LogUtils.i("no-PIN sale declined by the operator");
                                toast(getString(R.string.trans_canceled));
                                abortTransaction(true, "TRANSACTION CANCELLED", "No-PIN entry cancelled");
                                mHandler.sendEmptyMessage(8004);
                            }
                        })
                        .create();

        giveUp[0] = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || !answered.compareAndSet(false, true)) {
                    return;
                }
                Log.e(TAG, "no-PIN confirmation went unanswered for "
                        + (idleMs / 1000) + "s - cancelling the transaction");
                try {
                    if (dialog.isShowing()) {
                        dialog.dismiss();
                    }
                } catch (Throwable ignore) {
                    // the screen may already be gone
                }
                toast("Timed out - transaction cancelled");
                abortTransaction(true, "TRANSACTION TIMED OUT", "PIN confirmation timed out");
                mHandler.sendEmptyMessage(8004);
            }
        };
        mHandler.postDelayed(giveUp[0], idleMs > 0 ? idleMs : 60000L);
        dialog.show();
    }

    /**
     * The step that used to sit at the end of {@code onGetPinBlock}: hand a
     * stripe or contactless sale on to the signature screen.
     */
    private void continueAfterPin() {
        if (txnAborted("continueAfterPin")) {
            return;
        }
        LogUtils.e("continueAfterPin: cardType--->" + cardType);
        if (cardType == CardSearchType.MAGNETIC_STRIPE_CARD
                || cardType == CardSearchType.RF_CARD) {
            mHandler.sendEmptyMessageDelayed(MSG_SHOW_SIGNATURE_DIALOG, 1000);
        }
    }

    private void showPinPad(final boolean hasCancel, final String backCardNumber, final int ucPinType) {
        if (isExpiredCardCheckNeeded()) {
            dismissCardReading();
            showExpiredCardDialog(new Runnable() {
                @Override
                public void run() {
                    mExpiryWarningConfirmed = true;
                    showPinPadInternal(hasCancel, backCardNumber, ucPinType);
                }
            });
            return;
        }
        mExpiryWarningConfirmed = true;
        showPinPadInternal(hasCancel, backCardNumber, ucPinType);
    }

    private void showPinPadInternal(boolean hasCancel, String backCardNumber, int ucPinType) {
        if (mTxnAborted || isFinishing() || isDestroyed()) {
            // Cancelled while this was on its way (a dialog's CONTINUE racing
            // a cancel). No pad of any kind for a transaction that is over.
            LogUtils.e("showPinPad: transaction already ended - no PIN pad");
            releaseKernelPinWait("PIN pad requested after the transaction ended");
            return;
        }
        // The read is over the moment the pad goes up.
        dismissCardReading();
        // The PAN drives the ISO 9564-0 PIN block: the pad XORs the PIN with the
        // rightmost 12 digits of the PAN excluding the check digit. Callers pass
        // several different sources here, some of which are masked for display
        // ("*****1234") or F-padded from EMV tag 5A - either would produce a PIN
        // block the issuer cannot verify. Resolve a clean PAN first.
        // ISO 9564 format 0. The Anfu SDK has no PIN block format parameter -
        // the format follows from the card number: supply a valid PAN and the
        // pad builds a format 0 block, XORing the PIN field with
        // "0000" + the rightmost 12 PAN digits excluding the check digit. That
        // is the same block PAX builds explicitly via
        // PanUtils.getPanBlock(pan, X9_8_WITH_PAN) before calling
        // getPinBlock(..., EPinBlockMode.ISO9564_0, ...).
        String pan = panForPinBlock(backCardNumber);
        LogUtils.i("showPinPad: PAN for PIN block ===> " + maskPan(pan)
                + " (len " + pan.length() + ")");

        // EMI gate at the PIN funnel.
        //
        // rejectUnsupportedCard() already checks this, but it only sits on the
        // chip startTrans and the swipe path. The card-confirm dialog and the
        // manual-entry path reach the pad without passing either, which is how
        // a card outside the EMI table still ended up at PIN entry. Every PIN
        // entry in this app goes through here, so this is the one place the
        // check cannot be walked around.
        if ("EMI_SALE".equals(transType)
                && !CardRangeTable.emiAllowed(getApplicationContext(), pan)) {
            LogUtils.e("showPinPad: " + maskPan(pan) + " is not in the EMI card"
                    + " range table - refusing the EMI sale before PIN entry");
            showNoEmiCard();
            return;
        }
        if (!TerminalConfig.get(getApplicationContext()).isAcceptablePanLength(pan.length())) {
            LogUtils.e("showPinPad: no usable PAN for the PIN block - got '"
                    + backCardNumber + "'. The PIN block would be invalid.");
            toast("Cannot read card number - retry the card");
            canCleTransaction();
            return;
        }
        // ucPinType: 0 = offline PIN (verified by the CARD), 1 = online PIN
        // (enciphered under the TPK and sent to the host in DE52). An offline
        // PIN block must never go into DE52 - the acquirer's HSM would decrypt
        // it with the TPK and find garbage, which is what
        // "HSM DA Error 20 / Invalid PIN Block" means.
        mOnlinePin = (ucPinType == 1);
        LogUtils.i("showPinPad: pinType=" + ucPinType
                + (mOnlinePin ? " (ONLINE - goes in DE52)" : " (OFFLINE - card verifies, no DE52)"));

        // The PIN block is only meaningful if the working key is - and the
        // working key is only meaningful if the TMK it was decrypted under is
        // still the one that was injected. Check before taking a PIN, so a wiped
        // master key shows up here rather than as an "invalid PIN block" from
        // the acquirer after the cardholder has already typed it in.
        if (mOnlinePin) {
            String tmkFault = OnlineHost.tmkFault(getActivity(), mPinPadBinderService);
            if (!tmkFault.isEmpty()) {
                LogUtils.e("showPinPad: " + tmkFault);
                toast(tmkFault);
                canCleTransaction();
                return;
            }
        }

        KEYCODE_ENTER_ACTION = KEYCODE_ENTER_INPUT_PIN;
        speak("Please enter your PIN");
        showProcessingScreen("Enter PIN", "Please enter PIN on the keypad...");
        // setSimulateTouch() used to run here. It posts TouchHelper
        // .simulateFullTouch 200 ms later, which dispatches a hundred
        // ACTION_MOVE events in a circle of radius 100 and then a full
        // ACTION_DOWN/ACTION_UP at (screenWidth / 1.2, screenHeight / 1.2) -
        // the lower right of the screen, which is exactly where the ENTER key
        // sits on the PIN pad. That is a synthetic ENTER press, fired by the
        // terminal itself a fifth of a second after the pad opens, and it is
        // why a transaction went online with a PIN the cardholder never
        // confirmed. It is left over from the auto-test harness and has no
        // business anywhere near PIN entry.
        try {
            //第一位0不允许直接按OK
            // keyIndex is the master key group [2..10]. The pad takes that
            // group's TPK. It must be the same index the logon wrote the DE62
            // working key under. Passing a separate slot (3) gave
            // onPinPadError-5011 - that index holds no key set.
            // encryptionMode 0 = DES/3DES.
            int keyIndex = TerminalConfig.get(getActivity()).mkIndex();
            // {allowEmptyPin, minLength, maxLength}.
            //
            // The leading element MUST be 0. With 1 the pad accepts an empty
            // PIN and returns an ISO 9564 format 0 block whose length nibble is
            // 0. The acquirer reads that as "PIN is fewer than 4 or more than
            // 12 digits" and rejects it - Thales command DA error 24.
            //
            // The range is now the full ISO 9564 4-12. It used to be clamped to
            // {0,4,4} because {0,4,6} made the SDK's built-in pad draw SIX boxes
            // for a four digit PIN, and the host reported a PIN length error:
            // that pad always lays out maxLength boxes and there is no parameter
            // to change it.
            //
            // DynamicPinPad solves that properly. It draws the keypad itself and
            // uses showUserPinPad, so the box row starts at PIN_MIN_LEN and grows
            // one box per digit up to PIN_MAX_LEN. The SDK still captures the
            // touches and builds the PIN block - see DynamicPinPad's header - so
            // nothing about PIN security changes. If the SDK rejects the key
            // coordinates on this hardware it falls back to the built-in pad by
            // itself, and the sale still completes.
            // One session, one answer, and a clock on it.
            armPinSession();
            // From here the pad owns the kernel's PIN wait: its confirm,
            // cancel and timeout all answer the kernel.
            mPinPadOpened = true;
            mKernelPinWait = false;
            mActivePad = null;
            mActivePad = DynamicPinPad.show(FindCardActivity.this, mPinPadBinderService,
                    keyIndex, PIN_MIN_LEN, PIN_MAX_LEN, hasCancel, currentAmount, pan,
                    0, pinTimeoutSec(), ucPinType, slipTitle(transType), mEntry,
                    emiTenureText(), new IPinPadCallback.Stub() {
                @Override
                public void onGetPinBlock(boolean isPasswordFree, String onlinePin) throws RemoteException {
                    LogUtils.e("onGetPinBlock: 获取PIN。。。。");
                    if (!claimPinResult("onGetPinBlock")) {
                        return;
                    }
                    if (mCardRemoved) {
                        // The PIN arrived for a chip that has left the slot
                        // (seen by the watch while the pad was up - no read
                        // here, the kernel is busy with the card again).
                        // Nothing goes to the host.
                        mPinBlockHex = null;
                        abortCardRemoved("after PIN entry");
                        return;
                    }
                    //免密支付
                    if (isPasswordFree) {
                        // "No PIN" is a decision, so a person has to make it.
                        //
                        // This used to go straight on to the signature and the
                        // host. That is correct when the operator has pressed
                        // ENTER on a card that carries no PIN - and it is also
                        // exactly what a pad reports when it gives up on its
                        // own, which meant a terminal left alone on the PIN
                        // screen would authorise a sale by itself once the
                        // clock ran out. The two are indistinguishable here,
                        // so ask.
                        LogUtils.e("pad reported password-free - asking the operator"
                                + " to confirm before anything goes to the host");
                        mPinBlockHex = null;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                confirmNoPin();
                            }
                        });
                        return;
                    } else if (!mOnlinePin) {
                        // Offline PIN: the card checks it. Nothing goes to the
                        // host, and DE52 must stay absent.
                        LogUtils.i("offline PIN verified by card - DE52 not sent\n");
                        mPinBlockHex = null;
                    } else {
                        // Log exactly what the pad returned. DE52 must be the
                        // 8-byte block enciphered under the TPK that came from
                        // the DE62 logon response - nothing more, nothing less.
                        // If this string is not 16 hex characters, the substring
                        // taken further downstream is cutting the wrong bytes.
                        String raw = onlinePin == null ? "" : onlinePin.trim();
                        LogUtils.i("onGetPinBlock raw='" + raw + "' len=" + raw.length()
                                + " (ISO 9564 format 0, expect 16 hex chars = 8 bytes)");
                        if (raw.length() != 16) {
                            LogUtils.e("PIN BLOCK LENGTH UNEXPECTED - DE52 will be wrong."
                                    + " Pad returned " + raw.length() + " chars.");
                        }
                        if (raw.isEmpty()) {
                            // An empty block means the pad handed back nothing:
                            // the entry was cleared away, or bypassed. Sending it
                            // would give a format 0 block with a zero length
                            // nibble, which the acquirer rejects with DA error 24.
                            //
                            // This used to just `return`. claimPinResult had
                            // already taken the one answer this PIN session gets,
                            // so nothing else could ever fire: no host send, no
                            // cancel, no screen change. The terminal sat on the
                            // PIN screen with the sale half done and the card
                            // still in the reader, and the only way out was the
                            // idle timeout. An empty block is a PIN that was
                            // never given, so end the transaction like a cancel.
                            LogUtils.e("empty PIN block - no PIN captured, cancelling"
                                    + " the transaction instead of going online");
                            mPinBlockHex = null;
                            toast("PIN not entered - transaction cancelled");
                            abortTransaction(true, "TRANSACTION CANCELLED", "PIN not entered");
                            return;
                        }
                        saleDataModel.setPinBlock(raw);
                        mPinBlockHex = raw;

                        // Identify WHICH reading of DE62 the pad enciphered this
                        // under. An ISO-0 field only decrypts to a well-formed
                        // shape under the right key, so this names the key that
                        // is actually loaded - the one thing the KCVs alone
                        // cannot tell us. If a sale is then rejected for the PIN
                        // block, this line says which reading to stop trying.
                        mPinKeyReport = OnlineHost.identifyPinKey(
                                getActivity(), raw, pan);
                        if (!mPinKeyReport.isEmpty()) {
                            LogUtils.i(mPinKeyReport);
                        }
                    }
                    showProcessingScreen("Processing Transaction...", "Connecting to host, please wait...");
                    continueAfterPin();
                }

                @Override
                public void onPinPadError(int errCode) throws RemoteException {
                    LogUtils.i("onPinPadError: " + errCode + "\n");
                    if (!claimPinResult("onPinPadError " + errCode)) {
                        return;
                    }
                    String errText = (errCode == com.hjq.demo.widget.DynamicPinPad.ERR_UNCONFIRMED_PIN)
                            ? "PIN not confirmed - transaction cancelled"
                            : "PIN pad error: " + errCode;
                    toast(errText);
                    spError.put("errCode", errCode);
                    abortTransaction(true, "TRANSACTION FAILED", errText);
                }


                @Override
                public void onTimeout() throws RemoteException {
                    LogUtils.i("onTimeout: \n");
                    if (!claimPinResult("onTimeout")) {
                        return;
                    }
                    toast("PIN entry timed out");
                    abortTransaction(true, "TRANSACTION TIMED OUT", "PIN entry timed out");
                    mHandler.sendEmptyMessage(8004);
                }

                @Override
                public void onCancelPasswordEntry() throws RemoteException {
                    LogUtils.i("onCancelPasswordEntry: \n");
                    if (!claimPinResult("onCancelPasswordEntry")) {
                        return;
                    }
                    toast(getString(R.string.trans_canceled));
                    abortTransaction(true, "TRANSACTION CANCELLED", "PIN entry cancelled");
                }

            });
            startPinRemovalWatch();
        } catch (Exception e) {
            e.printStackTrace();
            LogUtils.e("pin exception :" + e.getMessage());

            toast(getString(R.string.show_pinpad_error));
            abortTransaction(true, "PIN PAD ERROR", getString(R.string.show_pinpad_error));
        }
    }


    /**
     * Format a decimal string as the SDK's "0xAA,0xBB,..." BCD parameter form.
     *
     * <p>Used for EMV tags declared n6 and similar, where the value is packed
     * two digits per byte. The string is left padded with zeros to an even
     * length first, so "766" becomes {@code 0x00,0x07,0x66}.</p>
     */
    private static String bcdTagValue(String digits) {
        String d = digits == null ? "" : digits.replaceAll("[^0-9]", "");
        if (d.isEmpty()) {
            d = "0";
        }
        if (d.length() % 2 != 0) {
            d = "0" + d;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i += 2) {
            if (b.length() > 0) {
                b.append(',');
            }
            b.append("0x").append(d, i, i + 2);
        }
        return b.toString();
    }

    private void setSimulateTouch() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 1.2f;
                float centerY = screenHeight / 1.2f;
                TouchHelper.simulateFullTouch(FindCardActivity.this, centerX, centerY);
            }
        }, 200);
    }


    /**
     * 自定义弹密码键盘
     * Custom pop-up password input keyboard
     */
    private void showSignaturePad() {
        LogUtils.e("showSignaturePad: ");
        signatureDialog = new SignatureDialog.Builder(this)
                .setListener(new SignatureDialog.OnListener() {
                    @Override
                    public void onCancel(BaseDialog dialog) {
                        toast(getString(R.string.trans_canceled));
                        canCleTransaction();
                    }

                    @Override
                    public void onConfirmClick(BaseDialog dialog, byte[] byteArray) {
                        if (byteArray == null || byteArray.length == 0) {
                            toast(getString(R.string.signature_cannot_be_empty));
                            return;
                        }

                        long countBits = countBits(byteArray);
                        if (countBits <= MIN_SIGNATURE_LIMIT) {
                            toast(getString(R.string.signature_too_simple));
                            signatureDialog.redo();
                        } else if (countBits >= MAX_SIGNATURE_LIMIT) {
                            toast(getString(R.string.signature_too_complex));
                            signatureDialog.redo();
                        } else {
                            signatureBmp = getSignatureBmp(byteArray);
                            signature = AFUtils.showPrintResult16Str(byteArray);
                            mHandler.sendEmptyMessage(8002);
                            dialog.dismiss();
                        }
                    }
                });
        signatureDialog.setOnKeyListener(new BaseDialog.OnKeyListener() {
            @Override
            public boolean onKey(BaseDialog dialog, KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    dialog.dismiss();

                    toast(getString(R.string.trans_canceled));
                    canCleTransaction();
                }
                return false;
            }
        });

        signatureDialog.show();
    }

   /* private Bitmap getSignatureBmp(byte[] byteArray) {
        if (byteArray == null || byteArray.length == 0) return null;
        final Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
        //bitmap宽高比为1.5:1，防止bitmap图片变形
        Bitmap imgBmp = BitmapUtil.createScaleBitmap(bitmap, MAX_BIT_WIDTH, MAX_BIT_HEIGHT);
        return imgBmp;
    }*/

    /**
     * 根据字节数组生成缩放后的签名 Bitmap
     *
     * @param byteArray 签名数据字节数组
     * @return 缩放后的 Bitmap；若解码失败或输入无效，则返回 null
     */
    private Bitmap getSignatureBmp(byte[] byteArray) {
        LogUtils.i("getSignatureBmp: 开始生成签名图片");

        if (byteArray == null || byteArray.length == 0) {
            LogUtils.w("getSignatureBmp: 输入字节数组为空或长度为 0");
            return null;
        }

        Bitmap bitmap = null;
        Bitmap scaledBitmap = null;

        try {
            bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
            if (bitmap == null) {
                LogUtils.e("getSignatureBmp: Bitmap 解码失败");
                return null;
            }

            LogUtils.d("getSignatureBmp: 原始 Bitmap 尺寸 - 宽：" + bitmap.getWidth() + " 高：" + bitmap.getHeight());

            // 调整 Bitmap 尺寸，保持宽高比
            scaledBitmap = BitmapUtil.createScaleBitmap(bitmap, MAX_BIT_WIDTH, MAX_BIT_HEIGHT);
            LogUtils.d("getSignatureBmp: 缩放后的 Bitmap 尺寸 - 宽：" + scaledBitmap.getWidth() + " 高：" + scaledBitmap.getHeight());

        } catch (Exception e) {
            LogUtils.e("getSignatureBmp: 解码或缩放 Bitmap 失败 - " + e.getMessage());
        } finally {
            // 释放原始 Bitmap，避免内存泄漏
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
                LogUtils.d("getSignatureBmp: 原始 Bitmap 已回收");
            }
        }

        LogUtils.i("getSignatureBmp: 签名图片生成完成");
        return scaledBitmap;
    }


    /**
     * 将文字 生成 文字图片 的Bitmap, 打印的位图需要 黑字 白底(A Bitmap that generates text into text images. Printed bitmaps need black text on a white background)
     */
    public Bitmap creatTextBitmap(String text, int width, int height) {
        Bitmap newBitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(newBitmap);
        canvas.drawColor(Color.WHITE);

        canvas.drawBitmap(newBitmap, 0, 0, null);
        TextPaint textPaint = new TextPaint();
        // String familyName ="宋体";
        // Typeface font = Typeface.create(familyName,Typeface.BOLD);
        // textPaint.setTypeface(font);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(24);
        textPaint.setColor(Color.BLACK);
        // "在Android开发中，Canvas.drawText不会换行，即使一个很长的字符串也只会显示一行，超出部分会隐藏在屏幕之外.StaticLayout是android中处理文字的一个工具类，StaticLayout 处理了文字换行的问题";
        StaticLayout sl = new StaticLayout(text, textPaint, newBitmap.getWidth(), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        textPaint.setStyle(Paint.Style.FILL);
        canvas.translate(0, height / 10);
        sl.draw(canvas);
        return newBitmap;
    }

    /**
     * 获取布局 ID
     */
    @Override
    protected int getLayoutId() {
        return R.layout.find_card_activity;
    }

    /**
     * 初始化控件
     */
    @Override
    protected void initView() {
        // Load the slip typeface up front. Doing it lazily inside slipPaint
        // works, but the first call then lands on the printer thread with the
        // paper already moving.
        com.hjq.demo.utils.Slip.useFont(getApplicationContext());
        bindService();
        registerBroadCastReceiver();
        Intent intent = getIntent();
        currentAmount = intent.getStringExtra(AFConstant.CURRENT_TRANS_AMOUNT);
        mTipBaseMinor = intent.getLongExtra(TipActivity.BASE_AMOUNT, -1);
        mTipMinor = intent.getLongExtra(TipActivity.TIP_AMOUNT, 0);
        LogUtils.e("current_trans_amount-->" + currentAmount);
        String tt = intent.getStringExtra(EXTRA_TRANS_TYPE);
        transType = (tt == null || tt.isEmpty()) ? "SALE" : tt;
        mEmiTenure = intent.getIntExtra(EXTRA_EMI_TENURE, 0);
        mEmiProduct = intent.getStringExtra(EXTRA_EMI_PRODUCT);
        mEmiType = intent.getIntExtra(EXTRA_EMI_TYPE,
                com.hjq.demo.ans8583.OnlineHost.EMI_TYPE_INSTA_PAY);
        String konaQr = intent.getStringExtra(EXTRA_KONA_QR);
        if (konaQr != null && !konaQr.isEmpty()) {
            mKonaQr = com.hjq.demo.ans8583.KonaMoney.parseQr(konaQr);
        }
        applyCardlessLayout();
        initTts();

        customerType = DataConversionUtils.getCustomerType();
        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
        LogUtils.e("设备类型:" + (keyboardDevice ? "820" : "930"));
//        isLcdSupported = DeviceType.A8.equals(DataConversionUtils.getCustomerType());
        isLcdSupported = AFUtils.checkLcdSupported();
        LogUtils.e("isLcdSupported:" + isLcdSupported + "\t" + (isLcdSupported ? "支持LCD" : "不支持LCD"));

        // The per-device icon switch that used to sit here set ic_a3 / rf_a3 /
        // ms_a3 and their A6 and A8 variants - small line drawings picked by
        // hardware model, held in img_ic / img_rf / img_ms and never read
        // again. The screen now draws each method as a reader with a card
        // moving through it, the same on every unit, so there is nothing left
        // to choose between and nothing to hold.
        setUpCardPrompt();
    }

    /**
     * 取消交易
     */
    /**
     * End the transaction and go back to the main menu.
     *
     * <p>This used to start {@code AmountActivity} - the SDK demo's own "Card
     * consumption" keypad. It is not part of any flow this app builds: sales
     * start from the menu tile, which takes the amount on its own keypad and
     * goes straight to card search. So every cancelled, declined or backed-out
     * transaction dropped the operator on an orphan screen with a Confirm
     * button that leads nowhere they wanted to go.</p>
     *
     * <p>CLEAR_TOP with SINGLE_TOP, so the menu already on the stack comes
     * forward instead of a second copy being stacked on the first - otherwise
     * a day of transactions leaves a pile of dead menu screens behind the
     * live one.</p>
     *
     * <p>The reader is closed first, and closing it is no longer a
     * precondition for leaving: the guard at the top returned early when the
     * card service was not bound, which left the operator stuck on the card
     * screen precisely when something had already gone wrong.</p>
     */
    /** The host approved this transaction (and it is not being reversed). */
    private boolean hostApproved() {
        OnlineHost.HostResult r = mHostResult;
        return r != null && r.approved && !r.needsReversal && !mCardDeclined;
    }

    /**
     * BACK / CANCEL once the transaction is past the point of no return.
     *
     * <p>BACK used to call canCleTransaction() whatever stage the screen was
     * at, so pressing it on the result screen of an APPROVED sale painted
     * "TRANSACTION CANCELLED" over it - while the money had been taken and the
     * slip printed. Now:</p>
     * <ul>
     *   <li>result already shown: BACK just goes home, nothing is cancelled;</li>
     *   <li>request with the host / approved and printing: BACK is ignored
     *       with "please wait" - it cannot be cancelled from here.</li>
     * </ul>
     *
     * @return true when the key was handled here and must not cancel
     */
    private boolean backAfterCommit() {
        if (mReceiptFlowFinished) {
            mHandler.removeCallbacks(mGoHomeRunnable);
            goHome();
            return true;
        }
        if (hostApproved() || mFinancialStarted.get()) {
            LogUtils.i("BACK ignored - the transaction is with the host or already approved");
            toast("Please wait - transaction in progress");
            return true;
        }
        return false;
    }

    private void canCleTransaction() {
        // Never turn a completed or approved transaction into "cancelled",
        // whichever button or timer asked for it.
        if (mReceiptFlowFinished) {
            mHandler.removeCallbacks(mGoHomeRunnable);
            runOnUiThread(this::goHome);
            return;
        }
        if (hostApproved()) {
            LogUtils.e("cancel ignored - the host approved this transaction; the receipt flow ends it");
            return;
        }
        if (mShowingTxnStage) {
            abortTransaction(true, "TRANSACTION CANCELLED", "Transaction was cancelled");
        } else {
            abortTransaction(false, null, null);
        }
    }

    /** Back to the main menu, whatever happened. */
    private void goHome() {
        // This screen is leaving. If its card thread still holds the reader
        // from here on, nobody is left to release it - ReaderRecovery watches
        // for exactly that.
        com.hjq.demo.utils.CardReaderAccess.sessionEnded(mReaderSession);
        Intent intent = new Intent(getContext(), PayActivity.class);
        intent.putExtra(PayActivity.EXTRA_WAIT_FOR_CARD_REMOVAL, true);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }


    /**
     * 开启寻卡交易
     * Initiate a card search transaction
     *
     * @param cardSearchType 卡类型，参考CardType类定义
     *                       Card type reference CardType class definition
     */
    /**
     * SETTLEMENT and VOID are built from the journal and never read a card, so
     * showing "Swipe / tap / insert your card" was wrong and confusing.
     */
    private boolean isCardless() {
        return "SETTLEMENT".equals(transType)
                || "VOID".equals(transType)
                || "EMI_VOID".equals(transType)
                // Cancelling a pre-auth is an 0400 built entirely from the
                // authorisation in the journal - STAN, RRN, approval code,
                // invoice and the ICC data the card gave at the time. Asking
                // for the card again would gain nothing and could produce a
                // different cryptogram than the one being reversed.
                || "PREAUTH_VOID".equals(transType)
                // A completion settles an authorisation already in the journal.
                // The card is not needed and must not be re-read: the ICC data
                // that goes out is the cryptogram the issuer approved.
                || "SALE_COMPLETE".equals(transType);
    }

    /** Swap the card prompts for a progress panel on the cardless flows. */
    /** Animators for the three card tiles, so they can be stopped on the way out. */
    private final java.util.List<android.animation.Animator> mPromptAnims =
            new java.util.ArrayList<>();

    /**
     * The popup shown while the card is actually being read.
     *
     * <p>The wait dialog that used to cover this screen was removed because
     * its text was the prompt "SWIPE/INSERT/TAP CARD", which is what the
     * screen behind it already says. Removing it left nothing at all between
     * "present your card" and either the card-confirm dialog or the PIN pad,
     * so on a slow read the terminal looked dead. This is the replacement:
     * the same artwork, showing only the method the reader actually used.</p>
     */
    private android.app.Dialog mReadingDialog;

    /** Animators owned by {@link #mReadingDialog}, cancelled when it closes. */
    private final java.util.List<android.animation.Animator> mReadAnims =
            new java.util.ArrayList<>();

    /**
     * One card session at a time.
     *
     * <p>{@code openFindCardForEMV} is a blocking call into the card service.
     * Nothing used to stop a second one being queued on the pool while the
     * first was still waiting - a re-arm after a failed read racing the
     * re-arm from {@code onRestart}, say - and a card service with two
     * overlapping searches against it stops answering. Once it does, no card
     * of any type reads until the terminal is restarted.</p>
     */
    private final com.hjq.demo.utils.CardSearchQueue mCardSearchQueue =
            new com.hjq.demo.utils.CardSearchQueue(command ->
                    new Thread(command, "TransactionCardSearch").start());
    private volatile boolean mRetryDialogUp;
    private volatile boolean mChipSeenDuringSearch;
    private final java.util.concurrent.atomic.AtomicInteger mChipWatchEpoch =
            new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicBoolean mSearchInFlight =
            new java.util.concurrent.atomic.AtomicBoolean(false);

    /** This screen's id as an owner of the card reader. */
    private final int mReaderSession = com.hjq.demo.utils.CardReaderAccess.newSession();

    /** True only while openFindCardForEMV is actually blocked in the SDK. */
    private volatile boolean mOpenInFlight;

    /**
     * The kernel has asked for the PIN (onEmvInputPIN) and is now waiting for
     * the PIN pad to finish. Until a pad is opened nothing else can release it.
     */
    private volatile boolean mKernelPinWait;
    private volatile int mKernelPinType = 1;
    private volatile boolean mPinPadOpened;

    /** The operator confirmed the card in EmvCardNoConfirm, inside the kernel. */
    private volatile boolean mKernelCardConfirmed;
    /**
     * EmvCardNoConfirm refused this card for a reason that already has its own
     * screen (not acquired here, not in the EMI table, expired and cancelled).
     * The kernel's non-zero return that follows is expected, not a bad read.
     */
    private volatile boolean mKernelRefusedCard;
    /** uptimeMillis when the reader reported the card, for the CARDTIME log. */
    private volatile long mCardSeenAtMs;
    /** The slot switch read "present" right after the chip was detected. */
    private volatile boolean mChipSeenPresent;
    /** The chip has been seen leaving the slot during this transaction. */
    private volatile boolean mCardRemoved;
    /** The PIN pad on screen, so a removed card can be announced on it. */
    private volatile DynamicPinPad mActivePad;

    /** Searches cancelled by something other than this screen, in a row. */
    private int mForeignCancels;
    /** -2001 "card service busy" answers, in a row. */
    private int mBusyRetries;
    /** When this screen started waiting for the reader lock, 0 if not waiting. */
    private volatile long mLockWaitSinceMs;
    /** The next arming is the re-arm of an empty search slice: no voice prompt. */
    private volatile boolean mQuietRearm;
    /**
     * How long the reader may stay with someone else before the screen says it
     * is busy. Every search is at most FIND_CARD_TIMEOUT seconds, so anything
     * past this is no longer a search finishing.
     */
    private static final long READER_BUSY_NOTICE_MS = (FIND_CARD_TIMEOUT + 4) * 1000L;

    private static final int MAX_FOREIGN_CANCELS = 5;
    private static final int MAX_BUSY_RETRIES = 10;
    /** openFindCardForEMV "detect card timeout" (AFSDKService V2.1, 4.8.1). */
    private static final int EMV_FIND_DETECT_TIMEOUT = 8;
    /** openFindCardForEMV: card service interface already in use (V2.1). */
    private static final int EMV_ERR_SERVICE_BUSY = -2001;

    /**
     * What {@code apiEmvKernelProcess} returns for Book B's
     * {@code EndApplicatonOtherCard}.
     *
     * <p>It is not a read failure. It is the contactless entry point saying
     * this application cannot be completed over this interface and the
     * cardholder should be asked for another one - which is why the kernel
     * log leading up to it reads
     * {@code ERROR AppSelectCTLDir ... Return ERR_OTHER_INTERFACE}. It comes
     * up when the card's PPSE entry names a kernel in tag 9F2A that this
     * kernel build does not carry: the AID matches, the candidate list is
     * built, and selection then has nowhere to go.</p>
     */
    private static final int EMV_ERR_OTHER_INTERFACE = -1001;

    /**
     * What {@code openFindCardForEMV} returns when the search it was running
     * is closed underneath it.
     *
     * <p>The SDK logs {@code InterruptedException} and hands back -2012. It is
     * not a card the reader failed to read - it is a search the terminal
     * cancelled on purpose, which happens on every ordinary teardown: the
     * operator cancels, the transaction ends, the activity finishes. Counting
     * it as a missed read spent a retry and put "Please present your card
     * again" on screen at the exact moment the sale was being cancelled.</p>
     */
    private static final int EMV_ERR_SEARCH_CANCELLED = -2012;

    /**
     * What {@code openFindCardForEMV} returns when its own timer runs out.
     *
     * Empty timeouts do not count toward fallback. Some readers also time out
     * with an unreadable chip inserted, so observed insertion history is used
     * only after this completed result to distinguish those attempts.
     */
    private static final int EMV_ERR_SEARCH_TIMEOUT = -2003;

    /**
     * A card put into the slot the wrong way round.
     *
     * <p>Anfu, on the -2011 code: "ErrorCode = -2011 indicates reverse card
     * insertion". This is the signal the fallback ladder was missing. It is
     * the reader itself saying a card WAS presented and could NOT be read,
     * which is exactly and only what a fallback strike is supposed to count -
     * the same role {@code SdkResult.Emv_FallBack} plays in the Nexgo
     * reference, where the app keeps no counter of its own at all.</p>
     *
     * <p>It replaces guesswork. {@code isCardInserted()} could only ever say
     * "something is in the slot", which is true of a good card too, so every
     * strike had to be inferred from timers and grace periods. This is a fact,
     * it arrives when the search returns, and it needs no sensor.</p>
     */
    private static final int EMV_ERR_REVERSE_INSERTION = -2011;

    /**
     * What {@code apiEmvKernelProcess} returns when the kernel finishes and
     * shuts itself down.
     *
     * <p>The contactless kernels do not report success the way the contact
     * path does. PayPass runs the entire transaction - candidate list, GPO,
     * READ RECORD, CDA, terminal action analysis, GENERATE AC - and then tears
     * itself down, which the SDK logs as {@code EXIT MASTER Kernel!} followed
     * by {@code Return ERR_KERNEL_EXIT} and hands back -9. The cryptogram is
     * already computed and sitting in the kernel's tag space by then. -9 is
     * the kernel saying "I am finished, come and collect it".</p>
     *
     * <p>Reading it as a failure is what made contactless look broken. A
     * Mastercard tap that passed CDA and produced a valid ARQC - 9F27 = 80,
     * 9F26 present, 9F36 incrementing - was thrown away, the reader was torn
     * down, and the cardholder was asked to present the card again. The next
     * tap produced another perfectly good transaction, and it was thrown away
     * too. The card was never the problem.</p>
     *
     * <p>Nothing is assumed from the code alone: the result is fetched first,
     * and only a kernel that actually published one is completed. If the
     * kernel exited with nothing to show, this falls through to the ordinary
     * bounded retry.</p>
     */
    private static final int EMV_ERR_KERNEL_EXIT = -9;

    /**
     * What {@code apiEmvKernelProcess} returns when the card leaves the field
     * part-way through.
     *
     * <p>It follows an APDU that never got an answer - {@code DAL_cscIsoCmd
     * ERROR!}, {@code CmdSendRecvApdu = -1009} - and means the kernel wants to
     * begin again from selection. That is a real miss, but a specific one: the
     * card was there and moved. "Hold the card on the reader" is the only
     * instruction that helps. "Please present your card again" describes a
     * different mistake and sends the cardholder to do the same thing the same
     * way.</p>
     */
    private static final int EMV_ERR_KERNEL_RESTART = -8;

    /**
     * What {@code apiEmvKernelProcess} returns when the card answers the PPSE
     * with something that is not a payment directory.
     *
     * <p>The kernel logs {@code AppSelectCTLDir ... Return ERR_OTHER_CARD}.
     * In the captures behind this it followed
     * {@code PPSE Resp Code : 6985} - "conditions of use not satisfied" - and
     * once {@code 6A82}, "application not found". Both are a card that is
     * powered, in the field and answering every APDU, and declining to present
     * a payment application.</p>
     *
     * <p>On a phone wallet that is the ordinary not-ready state: the card was
     * never selected in the app, the activation window has closed, or the
     * screen is locked. Nothing is wrong with the reader and nothing is wrong
     * with the card, so "please present your card again" sends the cardholder
     * to repeat the one thing that will keep failing. What they need to do is
     * wake the card first.</p>
     */
    private static final int EMV_ERR_OTHER_CARD = -1000;

    /** How many times to ask the SDK for the kernel result before giving up. */
    private static final int KERNEL_RESULT_FETCHES = 3;

    /** Gap between those attempts. */
    private static final long KERNEL_RESULT_RETRY_MS = 60L;

    /** So the contact fallback below is offered once per transaction, not in a loop. */
    private boolean mCtlsFellBack;

    /**
     * Consecutive contact-chip read failures in this transaction.
     *
     * <p>EMV requires a terminal to stop trying the chip and offer the stripe
     * after a run of failed contact reads, and to flag that transaction as a
     * FALLBACK so the issuer knows a chip card was taken on its magnetic
     * stripe - liability turns on it. Three is the usual number and the one
     * DBBL's reference terminal uses.</p>
     */
    private volatile int mChipFailures;

    /** True once this transaction has dropped from chip to stripe. */
    private boolean mMagFallback;
    private boolean mChipRequired;

    /** Consecutive chip failures before the stripe is offered. */
    private static final int CHIP_FAILS_BEFORE_FALLBACK = 3;

    /**
     * A kernel result already fetched, waiting to be consumed.
     *
     * <p>{@code emvGetKernelProgressResult} is a one-shot read on some SDK
     * builds. The ERR_KERNEL_EXIT path has to fetch the result to decide
     * whether the kernel finished at all, so it parks it here rather than
     * fetch it twice and risk the second call coming back empty.</p>
     */
    private EmvGetKernelProgressResult mPendingKernelResult;

    /**
     * Fill in the present-card screen and set the tiles moving.
     *
     * <p>Three things happen here. The amount and transaction type go at the
     * top, because the cardholder is looking at the terminal to check what
     * they are about to pay and the screen did not say. The tiles for methods
     * the terminal will not accept are removed - {@link TerminalConfig} already
     * knows, and offering contactless on a terminal with contactless switched
     * off wastes a tap and a question. What is left animates, so it is obvious
     * the reader is live rather than the screen being a static diagram.</p>
     *
     * <p>Each tile moves differently, in the direction of the gesture it is
     * asking for: the tap pulses toward the reader, the swipe slides across,
     * the insert slides down into the slot. They are also started out of step
     * with each other - three tiles moving in lockstep look like one
     * mechanism.</p>
     */
    /**
     * The instalment term for whatever is on screen, or 0.
     *
     * <p>An EMI sale carries it in the intent; an EMI void carries it on the
     * record being cancelled. Both are read here so the term is the same on the
     * card prompt, the PIN pad, the void screens and the slip - it used to be
     * read from the authorised record only, which is after all four of those
     * have already been drawn.</p>
     */
    private int emiTenure() {
        if (mEmiTenure > 0) {
            return mEmiTenure;
        }
        if (mVoidOrigRecord != null && mVoidOrigRecord.isEmi()) {
            return mVoidOrigRecord.tenure;
        }
        if (mTenure > 0) {
            return mTenure;
        }
        return 0;
    }

    /** "12 MONTHS", or blank when this is not an instalment transaction. */
    private String emiTenureText() {
        int t = emiTenure();
        return t <= 0 ? "" : t + (t == 1 ? " MONTH" : " MONTHS");
    }

    private void setUpCardPrompt() {
        if (mShowingTxnStage) return;
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());

        TextView type = findViewById(R.id.fc_txn_type);
        if (type != null) {
            // The term belongs next to the name of the transaction. A
            // cardholder presenting a card for a 12 month plan should be able
            // to see that before they present it, not first on the slip.
            String term = emiTenureText();
            type.setText(term.isEmpty()
                    ? slipTitle(transType)
                    : slipTitle(transType) + "  -  " + term);
        }
        TextView amt = findViewById(R.id.fc_amount);
        if (amt != null) {
            String a = currentAmount == null ? "" : currentAmount.trim();
            amt.setText(a.isEmpty() ? "" : tc.currencySymbol() + " " + a);
        }

        // Which tiles are shown, and therefore which animations run.
        //
        // Once a transaction has fallen back to the stripe the chip and the
        // contactless tiles are wrong twice over: the terminal is no longer
        // listening on either, and the animations are still inviting the
        // cardholder to use them. Fallback shows the stripe alone.
        boolean rf = !mMagFallback && tc.entryContactless();
        boolean ic = !mMagFallback && tc.entryChip();
        boolean ms = mMagFallback || tc.entrySwipe() || tc.entryFallback();

        showTile(findViewById(R.id.tile_rf), rf);
        showTile(findViewById(R.id.tile_msg), ms);
        showTile(findViewById(R.id.tile_ic), ic);

        // The instruction goes up with the tiles. The layout's own text is
        // "Waiting for card", which on its own reads as a terminal that has
        // stopped - never leave it showing.
        setCardStatus(mMagFallback ? "PLEASE SWIPE THE CARD"
                : mChipRequired ? "PLEASE INSERT THE CARD" : "INSERT, SWIPE OR TAP CARD");

        if (mCardScreenAtMs == 0) {
            mCardScreenAtMs = SystemClock.uptimeMillis();
        }
        armCardScreenTimeout();
        // Watch the slot switch for insertions the reader never reports.
        startChipSlotWatch();

        // Only animate what is on screen. A running animator on a GONE view is
        // a timer redrawing nothing.
        if (rf) {
            startTapAnimation();
        }
        if (ms) {
            startSwipeAnimation();
        }
        if (ic) {
            startInsertAnimation();
        }
    }

    private void showTile(View tile, boolean enabled) {
        if (tile != null) {
            tile.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    // ------------------------------------------------- the three animations
    //
    // These used to be three ObjectAnimators nudging a static illustration a
    // few pixels back and forth. A picture that wobbles is not an instruction:
    // it tells an operator who has never used the terminal nothing about which
    // way the card goes or how far. Each tile is now a reader with a card
    // moving through it - the card is behind the reader in the layout, so it
    // genuinely disappears into the slot rather than sliding over the top of
    // it - and each motion is the motion being asked for.

    /** A frame of an animation, expressed as a position from 0 to 1. */
    private interface PromptFrame {
        void at(float fraction);
    }

    /**
     * Run a frame function on a loop until the screen goes away.
     *
     * <p>A ValueAnimator driving a callback rather than an ObjectAnimator per
     * property: these motions move two or three properties of two views
     * together, and keeping them in one function is what stops them drifting
     * apart. RESTART, not REVERSE - a card being inserted and then pulled back
     * out is not the instruction.</p>
     */
    private void loopFrames(long durationMs, long startDelayMs, final PromptFrame frame) {
        loopFrames(durationMs, startDelayMs, frame, mPromptAnims);
    }

    /**
     * As above, but the animator is handed to {@code sink} instead of the
     * screen's own list - the reading popup has to stop its animations when
     * it closes, not when the activity does.
     */
    private void loopFrames(long durationMs, long startDelayMs, final PromptFrame frame,
                            final java.util.List<android.animation.Animator> sink) {
        android.animation.ValueAnimator a = android.animation.ValueAnimator.ofFloat(0f, 1f);
        a.setDuration(durationMs);
        a.setStartDelay(startDelayMs);
        a.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        a.setRepeatMode(android.animation.ValueAnimator.RESTART);
        a.setInterpolator(new android.view.animation.LinearInterpolator());
        a.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(android.animation.ValueAnimator v) {
                try {
                    frame.at((Float) v.getAnimatedValue());
                } catch (Exception ignore) {
                    // A view can be gone mid-frame while the activity finishes.
                }
            }
        });
        a.start();
        sink.add(a);
    }

    private float dpF(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** The card leans in while the contactless field radiates from the reader. */
    private void startTapAnimation() {
        final View card = findViewById(R.id.rf_card);
        final View nfc = findViewById(R.id.rf_nfc);
        if (card == null || nfc == null) {
            return;
        }
        // The field grows out of the reader, so the pivot is the bottom centre
        // of the arcs and not the middle of the view.
        nfc.post(new Runnable() {
            @Override
            public void run() {
                nfc.setPivotX(nfc.getWidth() / 2f);
                nfc.setPivotY(nfc.getHeight());
            }
        });
        final float bob = dpF(7f);
        loopFrames(1500, 0, new PromptFrame() {
            @Override
            public void at(float f) {
                // A cosine so the card eases at both ends instead of snapping
                // back when the loop restarts.
                card.setTranslationY(bob * (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * f)));
                float scale = 0.72f + 0.5f * f;
                nfc.setScaleX(scale);
                nfc.setScaleY(scale);
                nfc.setAlpha(1f - 0.85f * f);
            }
        });
    }

    /** The card runs through the groove, left to right, once per cycle. */
    private void startSwipeAnimation() {
        final View card = findViewById(R.id.msg_card);
        if (card == null) {
            return;
        }
        final float travel = dpF(34f);
        loopFrames(1400, 300, new PromptFrame() {
            @Override
            public void at(float f) {
                card.setTranslationX(-travel + 2f * travel * f);
                // Fade at both ends so the card arrives and leaves rather than
                // popping into existence at the edge of the tile.
                card.setAlpha(Math.min(1f, Math.min(f, 1f - f) * 6f));
            }
        });
    }

    /** The card is pushed down into the slot, held there, then starts again. */
    private void startInsertAnimation() {
        final View card = findViewById(R.id.ic_card);
        if (card == null) {
            return;
        }
        final float from = -dpF(34f);
        final float to = dpF(8f);
        loopFrames(1700, 600, new PromptFrame() {
            @Override
            public void at(float f) {
                // Home by 70% of the cycle, then held - the pause is what
                // makes it read as "and leave it there".
                float g = Math.min(1f, f / 0.7f);
                g = 1f - (1f - g) * (1f - g) * (1f - g);   // ease out: pushed, not dropped
                card.setTranslationY(from + (to - from) * g);
                float in = Math.min(1f, f * 6f);
                float out = f > 0.94f ? (1f - f) / 0.06f : 1f;
                card.setAlpha(in * out);
            }
        });
    }

    /** Stop the tiles. Left running they keep the activity's views alive. */
    private void stopCardPrompt() {
        for (android.animation.Animator a : mPromptAnims) {
            try {
                a.cancel();
            } catch (Exception ignore) {
                // Nothing useful to do; the activity is going away regardless.
            }
        }
        mPromptAnims.clear();
        stopReadAnims();
        hideReaderPreparing();
    }

    // --------------------------------------------------- the reading popup

    /**
     * Raise the reading popup for the method the reader actually used.
     *
     * <p>{@code cardType} is set by the SDK before this runs, so by the time
     * the card is being read there is no longer a choice between tap, swipe
     * and insert - two of the three tiles are answering a question that has
     * been settled. Exactly one is shown, and it carries the same artwork at
     * the same size and layering as the tile on the screen behind, so the
     * picture does not jump when the popup appears.</p>
     *
     * <p>Everything here is defensive. It is reached from binder callback
     * threads, and a dialog that throws while the card is in the field would
     * take the read down with it.</p>
     */
    private void showCardReading() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isFinishing() || isDestroyed() || mShowingTxnStage) {
                        return;
                    }
                    dismissCardReading();

                    View v = getLayoutInflater().inflate(R.layout.dialog_card_reading, null);
                    android.app.Dialog d = new android.app.Dialog(FindCardActivity.this);
                    d.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
                    d.setContentView(v);
                    d.setCancelable(false);
                    d.setCanceledOnTouchOutside(false);
                    if (d.getWindow() != null) {
                        d.getWindow().setBackgroundDrawable(
                                new android.graphics.drawable.ColorDrawable(
                                        android.graphics.Color.TRANSPARENT));
                    }

                    View tap = v.findViewById(R.id.rd_tap);
                    View swipe = v.findViewById(R.id.rd_swipe);
                    View ic = v.findViewById(R.id.rd_ic);
                    TextView msg = v.findViewById(R.id.rd_message);

                    if (cardType == CardSearchType.RF_CARD) {
                        if (tap != null) {
                            tap.setVisibility(View.VISIBLE);
                        }
                        if (msg != null) {
                            msg.setText("Hold the card on the reader");
                        }
                        animateReadTap(v.findViewById(R.id.rd_tap_card),
                                v.findViewById(R.id.rd_tap_nfc));
                    } else if (cardType == CardSearchType.MAGNETIC_STRIPE_CARD) {
                        if (swipe != null) {
                            swipe.setVisibility(View.VISIBLE);
                        }
                        if (msg != null) {
                            msg.setText("Reading the magnetic stripe");
                        }
                        animateReadSwipe(v.findViewById(R.id.rd_swipe_card));
                    } else {
                        if (ic != null) {
                            ic.setVisibility(View.VISIBLE);
                        }
                        if (msg != null) {
                            msg.setText("Leave the card in the slot");
                        }
                        animateReadInsert(v.findViewById(R.id.rd_ic_card));
                    }

                    d.show();
                    mReadingDialog = d;
                } catch (Exception e) {
                    // A popup is not worth losing a card read over.
                    LogUtils.e("showCardReading failed: " + e);
                }
            }
        });
    }

    /**
     * Close the reading popup.
     *
     * <p>Called from {@link #disDialog()} and {@link #closeFindCard()}, which
     * between them cover every way out of a read: the card confirm dialog,
     * the PIN pad, a cancel, a timeout and a failure.</p>
     */
    private void dismissCardReading() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    dismissCardReading();
                }
            });
            return;
        }
        stopReadAnims();
        android.app.Dialog d = mReadingDialog;
        mReadingDialog = null;
        if (d == null) {
            return;
        }
        try {
            if (d.isShowing()) {
                d.dismiss();
            }
        } catch (Exception ignore) {
            // The window can already be gone if the activity is finishing.
        }
    }

    private void stopReadAnims() {
        for (android.animation.Animator a : mReadAnims) {
            try {
                a.cancel();
            } catch (Exception ignore) {
                // Nothing useful to do.
            }
        }
        mReadAnims.clear();
    }

    /** Same motion as the tap tile, driven into the popup's views. */
    private void animateReadTap(final View card, final View nfc) {
        if (card == null || nfc == null) {
            return;
        }
        nfc.post(new Runnable() {
            @Override
            public void run() {
                nfc.setPivotX(nfc.getWidth() / 2f);
                nfc.setPivotY(nfc.getHeight());
            }
        });
        final float bob = dpF(7f);
        loopFrames(1500, 0, new PromptFrame() {
            @Override
            public void at(float f) {
                card.setTranslationY(bob * (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * f)));
                float scale = 0.72f + 0.5f * f;
                nfc.setScaleX(scale);
                nfc.setScaleY(scale);
                nfc.setAlpha(1f - 0.85f * f);
            }
        }, mReadAnims);
    }

    /** Same motion as the swipe tile. */
    private void animateReadSwipe(final View card) {
        if (card == null) {
            return;
        }
        final float travel = dpF(34f);
        loopFrames(1400, 0, new PromptFrame() {
            @Override
            public void at(float f) {
                card.setTranslationX(-travel + 2f * travel * f);
                card.setAlpha(Math.min(1f, Math.min(f, 1f - f) * 6f));
            }
        }, mReadAnims);
    }

    /** Same motion as the insert tile, held at the bottom of each cycle. */
    private void animateReadInsert(final View card) {
        if (card == null) {
            return;
        }
        final float from = -dpF(34f);
        final float to = dpF(8f);
        loopFrames(1700, 0, new PromptFrame() {
            @Override
            public void at(float f) {
                float g = Math.min(1f, f / 0.7f);
                g = 1f - (1f - g) * (1f - g) * (1f - g);
                card.setTranslationY(from + (to - from) * g);
                float in = Math.min(1f, f * 6f);
                float out = f > 0.94f ? (1f - f) / 0.06f : 1f;
                card.setAlpha(in * out);
            }
        }, mReadAnims);
    }

    /** The line under the tiles - what the terminal is doing right now. */
    private void setCardStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView v = findViewById(R.id.fc_status);
                if (v != null) {
                    v.setText(text == null ? "" : text);
                }
            }
        });
    }

    private void applyCardlessLayout() {
        View cardPanel = findViewById(R.id.card_prompt_panel);
        View noCard = findViewById(R.id.no_card_panel);
        View resultPanel = findViewById(R.id.txn_result_panel);
        if (cardPanel == null || noCard == null) {
            return;
        }
        if (mShowingTxnStage) {
            cardPanel.setVisibility(View.GONE);
            noCard.setVisibility(View.GONE);
            if (resultPanel != null) resultPanel.setVisibility(View.VISIBLE);
            return;
        }
        boolean cardless = isCardless();
        cardPanel.setVisibility(cardless ? View.GONE : View.VISIBLE);
        noCard.setVisibility(cardless ? View.VISIBLE : View.GONE);
        if (resultPanel != null) resultPanel.setVisibility(View.GONE);
        if (cardless) {
            TextView t = findViewById(R.id.no_card_title);
            if (t != null) {
                t.setText("EMI_VOID".equals(transType) ? "EMI VOID"
                        : "PREAUTH_VOID".equals(transType) ? "PRE-AUTH VOID"
                        : "SALE_COMPLETE".equals(transType) ? "SALE COMPLETE"
                        : "VOID".equals(transType) ? "VOID SALE" : "SETTLEMENT");
            }
        }
    }

    /**
     * One line of progress on the cardless panel. Safe to call from the
     * settlement worker thread.
     */
    private void setCardlessStatus(final String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView v = findViewById(R.id.no_card_status);
                if (v != null) {
                    v.setText(text);
                }
            }
        });
    }

    /** True once the operator has keyed the card in for this transaction. */
    private boolean mManualEntry;

    /**
     * The readers to arm, from the entry modes enabled in the parameters.
     *
     * <p>A mode that is off is never armed, rather than read and rejected
     * afterwards. If only manual entry is enabled there is nothing to arm at
     * all, so the key-in prompt opens instead of a card search that could never
     * succeed.</p>
     */
    private int enabledSearchMask() {
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (mMagFallback) return tc.entryFallback() ? CardSearchType.MAGNETIC_STRIPE_CARD : 0;
        if (mChipRequired) return tc.entryChip() ? CardSearchType.IC_CARD : 0;
        int mask = tc.cardSearchMask();
        if (mask == 0) {
            if (tc.entryManual()) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        promptManualEntry();
                    }
                });
            } else {
                toast("No entry mode is enabled - see Admin, Parameter");
            }
        }
        return mask;
    }

    /**
     * Key the card in by hand, when the parameters allow it.
     *
     * <p>The PAN is checked against the configured length range and the expiry
     * against a real month, because a keyed transaction has no track and no
     * chip to catch a typo - the first thing that would notice is the issuer,
     * by which point the cardholder is waiting.</p>
     *
     * <p>Entry mode goes out as DE22 '01' and no DE55 is built: there is no
     * cryptogram, so the merchant carries the liability. That is why the mode
     * is off by default.</p>
     */
    private void promptManualEntry() {
        final TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (!tc.entryManual()) {
            toast("Manual entry is not enabled - see Admin, Parameter");
            return;
        }
        stopFindCard = true;
        closeFindCard();
        new InputDialog.Builder(getActivity())
                .setTitle("MANUAL ENTRY")
                .setHint("Card number")
                .setInputRegex("[0-9]*")
                .setCancelable(false)
                .setListener(new InputDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog, String content) {
                        String pan = content == null ? "" : content.replaceAll("[^0-9]", "");
                        if (!tc.isAcceptablePanLength(pan.length())) {
                            toast("Card number must be " + tc.panLenRangeText() + " digits");
                            promptManualEntry();
                            return;
                        }
                        promptManualExpiry(pan);
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        canCleTransaction();
                    }
                })
                .show();
    }

    private void promptManualExpiry(final String pan) {
        new InputDialog.Builder(getActivity())
                .setTitle("MANUAL ENTRY")
                .setHint("Expiry YYMM")
                .setInputRegex("[0-9]*")
                .setCancelable(false)
                .setListener(new InputDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog, String content) {
                        String exp = content == null ? "" : content.replaceAll("[^0-9]", "");
                        if (exp.length() != 4 || !isValidYyMm(exp)) {
                            toast("Enter the expiry as YYMM, e.g. 2812");
                            promptManualExpiry(pan);
                            return;
                        }
                        startManualTxn(pan, exp);
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        canCleTransaction();
                    }
                })
                .show();
    }

    /** Four digits, YYMM, month 01-12. */
    private static boolean isValidYyMm(String yymm) {
        if (yymm == null || yymm.length() != 4 || !yymm.matches("\\d{4}")) {
            return false;
        }
        int mm = Integer.parseInt(yymm.substring(2));
        return mm >= 1 && mm <= 12;
    }

    /**
     * Run the transaction from a keyed card number.
     *
     * <p>Feeds the same online path a read card takes - only the source of the
     * PAN differs. No track 2 and no ICC data, because there are none; the
     * entry mode tells the host that.</p>
     */
    private void startManualTxn(String pan, String expiry) {
        mManualEntry = true;
        cardType = 0;
        isShowCardNo = true;
        currentCardNumber = pan;
        tipCardNumber = pan;
        cardValid = expiry;
        if (saleDataModel != null) {
            saleDataModel.setCardNo(pan);
            saleDataModel.setCardValid(expiry);
            saleDataModel.setTrack2data("");
            saleDataModel.setIcData("");
        }
        LogUtils.i("MANUAL ENTRY: pan len " + pan.length() + ", expiry " + expiry);
        showConfirmationDialog(tipCardNumber, new FindCardActivity.ConfirmationCallback() {
            @Override
            public void onResult(int result) {
                if (result != 0) {
                    canCleTransaction();
                    return;
                }
                runOnlineTxn();
            }
        });
    }

    /**
     * A "Nexus (KonaMoney)" sale from a scanned QR.
     *
     * <p>Same shape as the keyed path: put the card in front of the operator,
     * let them confirm it, then go online. What is confirmed here is the PAN
     * out of the track the phone displayed - not something typed beside it -
     * so what the operator approves is what the issuer will see.</p>
     */
    private void startKonaQrTxn() {
        if (mKonaQr == null || !mKonaQr.ok()) {
            String why = mKonaQr == null ? "no QR data" : mKonaQr.error;
            LogUtils.e("KONA QR: " + why);
            showUnsupportedCard("KONAMONEY QR");
            return;
        }
        com.hjq.demo.ans8583.KonaMoney.Track2 t2 = mKonaQr.parsed;
        cardType = 0;
        isShowCardNo = true;
        currentCardNumber = t2.pan;
        tipCardNumber = t2.pan;
        cardValid = t2.expiry;
        if (saleDataModel != null) {
            saleDataModel.setCardNo(t2.pan);
            saleDataModel.setCardValid(t2.expiry);
            saleDataModel.setTrack2data(t2.raw);
            saleDataModel.setIcData("");
        }
        LogUtils.i("KONA QR: " + t2);
        showConfirmationDialog(tipCardNumber, new FindCardActivity.ConfirmationCallback() {
            @Override
            public void onResult(int result) {
                if (result != 0) {
                    canCleTransaction();
                    return;
                }
                runOnlineTxn();
            }
        });
    }

    private void startSearchCard(int cardSearchType) {
        // A KonaMoney QR is the card. Arming the reader here would leave the
        // terminal waiting for a tap that is never coming, on a transaction
        // whose credential is already in hand.
        if (mKonaQr != null) {
            startKonaQrTxn();
            return;
        }
        LogUtils.i("startSearchCard: 开始寻卡");
        // A real card read is starting, so this is no longer a keyed
        // transaction. Without this the MANUAL entry mode would stick and the
        // next chip sale would go out as DE22 '01'.
        mManualEntry = false;
        mCardReadAttempt = 0;
        mCtlsFellBack = false;
        mQuietRearm = false;
        // A result left over from the previous transaction must never be
        // consumed by this one.
        mPendingKernelResult = null;
        // Static: the last transaction's card must not leak into this one.
        currentCardNumber = "";
        tipCardNumber = "";
        startSearchCardInternal(cardSearchType);
    }

    // ------------------------------------------------ chip slot watcher (SDK)

    /** When the card screen was put up, for the idle limit above. */
    private volatile long mCardScreenAtMs;

    /**
     * Ends the card screen when nobody presents a card.
     *
     * <p>The screen used to rely on {@link #cardSearchExpired()}, which is only
     * consulted when a search returns or a retry is armed. With a blocking
     * search that is not a clock - if nothing is presented the check simply
     * does not run, and the prompt can sit there well past the configured
     * limit. This fires on the main looper at exactly the menu timeout,
     * whatever the reader is doing.</p>
     */
    private Runnable mCardScreenTimeout;

    /** Menu timeout, in seconds, from Parameters. 0 switches it off. */
    private int menuTimeoutSec() {
        return TerminalConfig.get(getApplicationContext()).idleTimeoutSec();
    }

    /** Start (or restart) the card-screen clock. */
    private void armCardScreenTimeout() {
        cancelCardScreenTimeout();
        final int secs = menuTimeoutSec();
        if (secs <= 0) {
            return;                       // timeout disabled in Parameters
        }
        mCardScreenTimeout = new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || mTxnAborted || mCardReadOk) {
                    return;
                }
                Log.e(TAG, "no card presented within the " + secs
                        + "s menu timeout - returning to the home screen");
                toast(getString(R.string.card_search_failed_ret));
                canCleTransaction();      // closes the reader and goes home
            }
        };
        mHandler.postDelayed(mCardScreenTimeout, secs * 1000L);
    }

    private void cancelCardScreenTimeout() {
        if (mCardScreenTimeout != null) {
            mHandler.removeCallbacks(mCardScreenTimeout);
            mCardScreenTimeout = null;
        }
    }

    /** True while a CHIP READ COUNTER / mag-card dialog is waiting for an answer. */
    private volatile boolean mChipDialogUp;

    /** Last openFindCardForEMV outcome, for the CARDDIAG readout. */
    private volatile int mLastFindResult = Integer.MIN_VALUE;
    private volatile long mLastFindMs;


    /** True while the slot watcher thread should run. */
    private volatile boolean mChipSlotWatch;
    /** Set as soon as any card has been read successfully this transaction. */
    private volatile boolean mCardReadOk;
    /** Read-only slot observation interval; this never starts a card search. */
    private static final long SLOT_POLL_MS = 100L;
    /** Continuous empty reading before the card counts as having left. */
    private static final long SLOT_EMPTY_MS = 600L;
    /** Observe insertion history only; the SDK result decides success or failure. */
    private synchronized void startChipSlotWatch() {
        // Off. This read the slot switch ten times a second for the whole
        // search, and every one of those reads is a call into the same card
        // service that is polling the contactless field - which is why a tap
        // took a moment to be picked up. A chip that is in the slot but not
        // reported is still caught: the slot is read once when the search is
        // armed and again when a search slice ends (chipFailureCandidate).
        if (SLOT_WATCH_DURING_SEARCH) {
            startChipSlotWatchNow();
        }
    }

    /** See startChipSlotWatch. */
    private static final boolean SLOT_WATCH_DURING_SEARCH = false;

    private synchronized void startChipSlotWatchNow() {
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (!tc.entryChip() || !tc.entryFallback() || mMagFallback || mChipSlotWatch) {
            return;
        }
        mChipSlotWatch = true;
        final int epoch = mChipWatchEpoch.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    chipSlotWatchLoop(epoch);
                } catch (Throwable e) {
                    LogUtils.e("chip slot watch died: " + e);
                } finally {
                    if (mChipWatchEpoch.get() == epoch) mChipSlotWatch = false;
                }
            }
        }, "ChipSlotWatch").start();
    }

    private synchronized void stopChipSlotWatch() {
        mChipSlotWatch = false;
        mChipWatchEpoch.incrementAndGet();
    }

    /**
     * The slot switch: TRUE card in, FALSE empty, null if it could not be read.
     * An unreadable switch is never taken to mean "removed".
     */
    private Boolean slotState() {
        try {
            return mCardBinderService == null ? null : mCardBinderService.isCardInserted();
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * True when the contact chip has been pulled out of the slot.
     *
     * <p>Only for a chip transaction, only if the switch was seen reading
     * "present" right after detection (so a firmware that does not report the
     * switch can never cancel a good sale), and only on two empty readings in
     * a row. Called only while the kernel is waiting on the application - the
     * confirm callback, the PIN callback, the PIN pad - never while it is
     * exchanging commands with the card.</p>
     */
    private boolean chipRemoved() {
        if (mCardRemoved) {
            return true;
        }
        if (cardType != CardSearchType.IC_CARD || !mChipSeenPresent) {
            return false;
        }
        if (!Boolean.FALSE.equals(slotState())) {
            return false;
        }
        sleepQuiet(150L);
        if (!Boolean.FALSE.equals(slotState())) {
            return false;
        }
        mCardRemoved = true;
        LogUtils.e("CARD REMOVED - the chip left the slot during the transaction");
        return true;
    }

    /** End the sale because the chip was pulled out. */
    private void abortCardRemoved(String where) {
        LogUtils.e("ending the transaction: card removed (" + where + ")");
        speak("Card removed");
        toast("Card removed - transaction cancelled");
        abortTransaction(true, "CARD REMOVED", "The card was removed before the transaction finished");
    }

    /**
     * While the PIN is being typed, watch the slot. The kernel is idle, waiting
     * for the pad, so the switch can be read safely.
     */
    private void startPinRemovalWatch() {
        if (cardType != CardSearchType.IC_CARD || !mChipSeenPresent) {
            return;
        }
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                while (!mPinDone.get() && !mTxnAborted && !isFinishing()) {
                    sleepQuiet(400L);
                    if (mPinDone.get() || mTxnAborted) {
                        return;
                    }
                    if (chipRemoved()) {
                        speak("Card removed. Press cancel");
                        DynamicPinPad pad = mActivePad;
                        if (pad != null) {
                            pad.showNotice("CARD REMOVED - PRESS CANCEL");
                        }
                        return;
                    }
                }
            }
        });
    }

    /** Read the slot switch without opening another card search. */
    private boolean slotOccupied() {
        try {
            return mCardBinderService != null && mCardBinderService.isCardInserted();
        } catch (Throwable e) {
            return false;
        }
    }


    private void clearChipStrikes() {
        mChipFailures = 0;
    }

    private void chipSlotWatchLoop(int epoch) {
        while (mChipSlotWatch && mChipWatchEpoch.get() == epoch
                && !isFinishing() && !mMagFallback) {
            if (mSearchInFlight.get()) {
                boolean occupied = slotOccupied();
                if (mChipWatchEpoch.get() != epoch) return;
                if (occupied) mChipSeenDuringSearch = true;
            }
            sleepQuiet(SLOT_POLL_MS);
        }
    }

    /** A successful reader result or completed card-data read. */
    private boolean chipTaken() {
        return mCardReadOk || cardType == CardSearchType.IC_CARD;
    }

    // ------------------------------------------------ reader warm-up spinner

    /** How long after openFindCardForEMV the reader is taken to be listening. */
    private static final long READER_WARMUP_MS = 250L;
    private volatile long mPrepStartMs;
    private View mReaderPrep;
    private final Runnable mHidePrep = this::hideReaderPreparing;

    /**
     * "PREPARING CARD READER" with a spinner, over the card prompt, from the
     * moment the screen asks for a card until the reader is armed. It does not
     * take touches, so CANCEL behind it still works.
     */
    private void showReaderPreparing() {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                if (mReaderPrep == null) {
                    LinearLayout box = new LinearLayout(this);
                    box.setOrientation(LinearLayout.VERTICAL);
                    box.setGravity(android.view.Gravity.CENTER);
                    box.setBackgroundColor(0xF2FFFFFF);
                    int side = Math.round(getResources().getDisplayMetrics().widthPixels * 0.22f);
                    ProgressBar pb = new ProgressBar(this, null, android.R.attr.progressBarStyleLarge);
                    pb.setIndeterminate(true);
                    box.addView(pb, new LinearLayout.LayoutParams(side, side));
                    TextView tv = new TextView(this);
                    tv.setText("PREPARING CARD READER...");
                    tv.setTextColor(0xFF2D3242);
                    tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                    tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22);
                    tv.setGravity(android.view.Gravity.CENTER);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.topMargin = side / 3;
                    box.addView(tv, lp);
                    addContentView(box, new android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT));
                    mReaderPrep = box;
                }
                mReaderPrep.setVisibility(View.VISIBLE);
                mReaderPrep.bringToFront();
            } catch (Throwable t) {
                Log.w(TAG, "reader spinner not shown: " + t);
            }
        });
    }

    private void hideReaderPreparing() {
        runOnUiThread(() -> {
            if (mReaderPrep != null) {
                mReaderPrep.setVisibility(View.GONE);
            }
        });
    }

    /** Milliseconds since the reader reported the current card. */
    private long sinceCardMs() {
        long at = mCardSeenAtMs;
        return at == 0L ? -1L : SystemClock.uptimeMillis() - at;
    }

    private static void sleepQuiet(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean chipFailureCandidate() {
        if (mMagFallback) {
            return false;                     // already fallen back
        }
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (!tc.entryChip() || !tc.entryFallback()) {
            return false;
        }
        boolean observed = mChipSeenDuringSearch;
        if (cardType == EMV_ERR_SEARCH_TIMEOUT || cardType == -2009) observed |= slotOccupied();
        return com.hjq.demo.utils.ChipFallbackPolicy.failedChipAttempt(cardType, observed);
    }

    private boolean chipAttemptFailed() {
        return noteChipStrike("cardType=" + cardType);
    }

    /**
     * Record one failed contact-chip attempt, and fall back on the third.
     *
     * Called after a failed search or kernel result; never from a timer.
     */
    private synchronized boolean noteChipStrike(String why) {
        if (mMagFallback || mChipDialogUp) {
            return true;             // the existing prompt owns the retry
        }
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (!tc.entryChip() || !tc.entryFallback()) {
            return false;
        }
        mChipFailures++;
        final int n = mChipFailures;
        LogUtils.w("CHIP READ COUNTER " + n + " of " + CHIP_FAILS_BEFORE_FALLBACK
                + " (" + why + ")");
        mChipDialogUp = true;
        // Called only after the search/kernel has returned a failure.
        final int epoch = mCardSearchQueue.generation();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!searchAlive(epoch)) return;
                if (n < CHIP_FAILS_BEFORE_FALLBACK) {
                    showChipReadCounter(n);
                } else {
                    askUseMagCard();
                }
            }
        });
        // The operator acknowledges the failure before another search is queued.
        return true;
    }

    /** Queue one retry; the worker waits for the old call and card removal. */
    private void rearmWhenFree(final int mask, final String prompt) {
        if (stopFindCard || isFinishing() || mTxnAborted) return;
        speak(prompt);
        startSearchCardInternal(mask, true);
    }

    private void showChipReadCounter(final int counter) {
        final int epoch = mCardSearchQueue.generation();
        disDialog();
        speak("Chip read counter " + counter + ". Remove the card and press any key");
        new MessageDialog.Builder(getActivity())
                .setTitle("CHIP READ")
                .setMessage("COUNTER " + counter
                        + "\nREMOVE CARD\nPRESS ANY KEY")
                .setConfirm(getString(R.string.common_confirm))
                .setCancel(null)
                .setCancelable(false)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        mChipDialogUp = false;
                        if (!searchAlive(epoch)) return;
                        // Someone pressed a key, so the terminal is not idle.
                        mCardScreenAtMs = SystemClock.uptimeMillis();
                        armCardScreenTimeout();
                        mCardReadAttempt = 0;
                        // Re-arm only now. The reader waits for the operator,
                        // the way the reference does.
                        rearmWhenFree(enabledSearchMask(),
                                "Insert, swipe or tap the card");
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        mChipDialogUp = false;
                    }
                })
                .show();
    }

    /**
     * The third failure: offer the stripe, and let the operator decide.
     *
     * <pre>
     *   Please Use Mag Card
     *   Remove Card And
     *   Cancel To Stop
     *   Ok To Proceed
     * </pre>
     *
     * <p>Not automatic. The reference asks, because taking a chip card on its
     * stripe moves the fraud liability onto the merchant - that is a decision,
     * and the person standing at the counter is the one who gets to make it.
     * Cancel ends the transaction; Ok arms the stripe alone and the sale goes
     * out flagged as a fallback, DE22 80x.</p>
     */
    private void askUseMagCard() {
        final int epoch = mCardSearchQueue.generation();
        disDialog();
        speak("Please use mag card. Remove the card. Cancel to stop, okay to proceed");
        new MessageDialog.Builder(getActivity())
                .setTitle(slipTitle(transType))
                .setMessage("Please Use Mag Card\nRemove Card And\n"
                        + "Cancel To Stop\nOk To Proceed")
                .setConfirm("OK")
                .setCancel("CANCEL")
                .setCancelable(false)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        mChipDialogUp = false;
                        if (!searchAlive(epoch)) return;
                        beginMagFallback();
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        mChipDialogUp = false;
                        if (!searchAlive(epoch)) return;
                        LogUtils.e("operator cancelled at the mag card prompt"
                                + " - ending the transaction");
                        toast(getString(R.string.trans_canceled));
                        canCleTransaction();
                    }
                })
                .show();
    }

    /** Stripe only from here, and the message flagged as a fallback. */
    private void beginMagFallback() {
        TerminalConfig tc = TerminalConfig.get(getApplicationContext());
        if (!tc.entryFallback()) {
            LogUtils.e("no stripe reader enabled - cannot fall back");
            canCleTransaction();
            return;
        }
        mMagFallback = true;
        mPinBlockHex = null;
        mPinKeyReport = "";
        // The stripe must never inherit a PIN or cryptogram from the failed chip.
        if (saleDataModel != null) {
            saleDataModel.setIcData("");
            saleDataModel.setTrack2data("");
            saleDataModel.setCardAID("");
            saleDataModel.setCardSerialNumber("");
        }
        mCardReadAttempt = 0;         // the stripe gets its own retry budget
        mChipFailures = 0;
        stopChipSlotWatch();          // nothing left to watch the chip for
        // Restart the idle clock.
        //
        // mCardScreenAtMs is measured from when the card screen first appeared,
        // and the chip ladder can easily eat the whole idle allowance before it
        // finishes - three insertions, three dialogs, three searches. The first
        // re-arm after falling back then found the limit already passed and
        // cancelled the transaction on the spot, which is why the swipe state
        // never stayed. The operator has just answered a prompt, so they are
        // demonstrably still there: give the stripe the full window.
        mCardScreenAtMs = SystemClock.uptimeMillis();
                        armCardScreenTimeout();
        LogUtils.e("falling back to the magnetic stripe after "
                + CHIP_FAILS_BEFORE_FALLBACK + " failed chip reads;"
                + " DE22 will be sent as 80x");
        // Redraw so the chip and contactless tiles go and only the swipe
        // animation is left running.
        setUpCardPrompt();
        setCardStatus("PLEASE SWIPE THE CARD");
        rearmWhenFree(CardSearchType.MAGNETIC_STRIPE_CARD,
                "Please SWIPE the card");
    }

    private void retrySearchCard(final int cardSearchType) {
        retrySearchCard(cardSearchType, "Please present your card again");
    }

    /** Evaluate the completed result before allowing a new search. */
    private void retrySearchCard(final int cardSearchType, final String prompt) {
        final int epoch = mCardSearchQueue.generation();
        if (!searchAlive(epoch) || mChipDialogUp || mRetryDialogUp) return;
        if (chipFailureCandidate() && chipAttemptFailed()) return;
        mCardReadAttempt++;
        if (cardSearchExpired()) return;
        if (cardType == EMV_ERR_SEARCH_TIMEOUT) {
            // An empty search can wait again. An inserted chip was handled above.
            speak(prompt);
            startSearchCardInternal(cardSearchType, true);
            return;
        }
        showCardRetryPrompt(cardSearchType, prompt, epoch);
        // Removed SDK polling from delayed UI tasks:
        // mHandler.postDelayed(() -> startSearchCardInternal(cardSearchType), 150L);
    }

    private void showCardRetryPrompt(final int cardSearchType, final String prompt, final int epoch) {
        if (!searchAlive(epoch) || mRetryDialogUp || mChipDialogUp) return;
        // A failed read needs a new presentation, not a rapid loop on the same card.
        mRetryDialogUp = true;
        runOnUiThread(() -> {
            if (!searchAlive(epoch)) { mRetryDialogUp = false; return; }
            new MessageDialog.Builder(getActivity())
                    .setTitle("CARD READ")
                    .setMessage(prompt + "\nRemove the card, then press RETRY.")
                    .setConfirm("RETRY").setCancel("CANCEL").setCancelable(false)
                    .setListener(new MessageDialog.OnListener() {
                        @Override public void onConfirm(BaseDialog dialog) {
                            mRetryDialogUp = false;
                            if (!searchAlive(epoch)) return;
                            mCardScreenAtMs = SystemClock.uptimeMillis();
                        armCardScreenTimeout();
                            rearmWhenFree(cardSearchType, prompt);
                        }
                        @Override public void onCancel(BaseDialog dialog) {
                            mRetryDialogUp = false;
                            if (searchAlive(epoch)) canCleTransaction();
                        }
                    }).show();
        });
    }

    private boolean searchAlive(int epoch) {
        return mCardSearchQueue.isCurrent(epoch) && !stopFindCard
                && !mTxnAborted && !isFinishing();
    }

    /**
     * The reader lock was not free. Work out why, and do something about it.
     *
     * @return true if the app is restarting and the search must not be re-queued
     */
    private boolean onReaderLockBusy(final int epoch) {
        long now = SystemClock.uptimeMillis();
        final boolean firstMiss = mLockWaitSinceMs == 0L;
        if (firstMiss) {
            mLockWaitSinceMs = now;
        }
        final long waited = now - mLockWaitSinceMs;
        Log.w(TAG, "card reader busy, waited " + waited + " ms - "
                + com.hjq.demo.utils.CardReaderAccess.describeHolder());
        if (com.hjq.demo.utils.CardReaderAccess.holderSession()
                == com.hjq.demo.utils.CardReaderAccess.HOME_SESSION) {
            // The idle home-screen search outlived its screen. It is short
            // (a few seconds) and ends by itself; one close is enough. Closing
            // again on every miss only leaves a cancel pending for THIS
            // screen's own search, which then comes back -2012.
            if (firstMiss) {
                try {
                    if (mCardBinderService != null) {
                        mCardBinderService.closeFindCardForEMV();
                    }
                } catch (Exception ignore) {
                    // it times out on its own
                }
            }
            return false;
        }
        if (com.hjq.demo.utils.ReaderRecovery.recoverIfWedged(this, "card screen")) {
            runOnUiThread(() -> setCardStatus("RESETTING CARD READER..."));
            return true;
        }
        // Until then the card prompt stays up. The search that has the reader
        // ends within FIND_CARD_TIMEOUT seconds; a card presented meanwhile is
        // still in the field or the slot when this screen's search opens.
        if (waited >= READER_BUSY_NOTICE_MS) {
            runOnUiThread(() -> {
                if (searchAlive(epoch)) setCardStatus("PLEASE WAIT - CARD READER BUSY");
            });
        }
        return false;
    }

    /**
     * Close the reader on the way out, and keep closing it while a search is
     * still blocked in the SDK.
     *
     * <p>A close that lands a moment BEFORE the worker's openFindCardForEMV
     * cancels nothing, and the open that follows then blocks for its full
     * timeout holding the reader - which the next card screen inherits as a
     * reader that never answers. Re-closing until the open has come back
     * removes that window.</p>
     */
    private void closeReaderForExit(final String why) {
        final com.szanfu.sdk.api.card.ICardBinderService card = mCardBinderService;
        if (card == null) return;
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < 12; i++) {
                    try {
                        card.closeFindCardForEMV();
                    } catch (Exception e) {
                        LogUtils.e("closeFindCardForEMV (" + why + ") failed: " + e.getMessage());
                        return;
                    }
                    if (!mOpenInFlight) {
                        return;
                    }
                    sleepQuiet(250L);
                }
                Log.w(TAG, "search still open after repeated closes (" + why + ")");
            }
        });
    }

    /**
     * The kernel asked for a PIN and no PIN pad was ever opened for it.
     *
     * <p>{@code onEmvInputPIN} returns nothing: after it the kernel simply
     * waits inside {@code apiEmvKernelProcess} for the SDK PIN pad to finish.
     * If the transaction is abandoned before the pad opens - the card refused
     * at the confirm screen, an expired card cancelled, the EMI range check -
     * nothing ever finishes it, and the card thread stays blocked inside the
     * kernel holding the reader.</p>
     *
     * <p>This used to open the SDK's own {@code showPinPad} with a one second
     * timeout. On this firmware that timeout is not honoured: the system
     * "Secure Pinpad" came up over the home screen after every cancel and
     * stayed until someone pressed its cancel key - and even then the kernel
     * did not always come back.</p>
     *
     * <p>{@code skipShowPinPad} with {@code iSkipShowPinPad = true} ends the
     * kernel's PIN step WITHOUT putting a pad on the screen. Nothing it
     * reports is acted on - this screen is already gone. If the call itself is
     * refused, NO visible pad is opened as a fallback: ReaderRecovery is the
     * last resort for a kernel that never returns. Most refusals no longer
     * get here at all - the range, EMI and expiry checks run in
     * EmvCardNoConfirm, where a "no" ends the kernel cleanly.</p>
     */
    private void releaseKernelPinWait(final String why) {
        if (!mKernelPinWait || mPinPadOpened) {
            return;
        }
        mPinPadOpened = true;
        mKernelPinWait = false;
        final com.szanfu.sdk.api.pinpad.IPinPadBinderService pad = mPinPadBinderService;
        LogUtils.e("kernel is waiting for a PIN but no pad was opened (" + why
                + ") - skipping the PIN step silently (no pad on screen)");
        if (pad == null) {
            return;
        }
        final int pinType = mKernelPinType;
        final long askedAt = SystemClock.uptimeMillis();
        cachedThreadPool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String pan = "";
                    try {
                        pan = panForPinBlock(currentCardNumber);
                    } catch (Throwable ignore) {
                        // kernel tag space may be gone; the call needs a PAN shape only
                    }
                    if (pan.length() < 13) pan = "0000000000000000";
                    pad.skipShowPinPad(TerminalConfig.get(getApplicationContext()).mkIndex(),
                            new int[]{0, PIN_MIN_LEN, PIN_MAX_LEN}, true, currentAmount,
                            pan, 0, 1, pinType, true, new IPinPadCallback.Stub() {
                                @Override
                                public void onGetPinBlock(boolean free, String block) {
                                    LogUtils.i("kernel PIN step skipped: pad answered (ignored) after "
                                            + (SystemClock.uptimeMillis() - askedAt) + " ms");
                                }

                                @Override
                                public void onPinPadError(int errCode) {
                                    LogUtils.i("kernel PIN step skipped: pad error " + errCode
                                            + " (ignored)");
                                }

                                @Override
                                public void onTimeout() {
                                    LogUtils.i("kernel PIN step skipped: pad timed out (ignored)");
                                }

                                @Override
                                public void onCancelPasswordEntry() {
                                    LogUtils.i("kernel PIN step skipped: pad cancelled (ignored) after "
                                            + (SystemClock.uptimeMillis() - askedAt) + " ms");
                                }
                            });
                    LogUtils.i("skipShowPinPad accepted - waiting for the kernel to return");
                } catch (Throwable e) {
                    // Deliberately no showPinPad fallback: that is the popup.
                    LogUtils.e("skipShowPinPad refused (" + e + ") - no pad will be shown;"
                            + " ReaderRecovery frees the reader if the kernel never returns");
                }
            }
        });
    }

    /**
     * Wait on a binder thread for the operator's answer to something the
     * kernel asked, without ever waiting for ever.
     *
     * <p>The kernel is blocked inside this callback, and the card thread is
     * blocked inside the kernel holding the reader. A plain {@code
     * latch.await()} with no way out is how one unanswered dialog becomes a
     * terminal that needs restarting.</p>
     *
     * @return {@code true} if the latch was released by an answer
     */
    private boolean awaitKernelAnswer(CountDownLatch latch, String what) {
        int idle = TerminalConfig.get(getApplicationContext()).idleTimeoutSec();
        long capMs = (idle > 0 ? Math.min(idle, 300) + 15 : 120) * 1000L;
        long start = SystemClock.uptimeMillis();
        try {
            while (!latch.await(250L, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                if (mTxnAborted || isFinishing() || isDestroyed()) {
                    LogUtils.e(what + ": transaction ended while the kernel was waiting"
                            + " - answering cancel");
                    return false;
                }
                if (SystemClock.uptimeMillis() - start > capMs) {
                    LogUtils.e(what + ": no answer in " + (capMs / 1000) + " s"
                            + " - answering cancel and ending the transaction");
                    runOnUiThread(this::canCleTransaction);
                    return false;
                }
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean cardSearchExpired() {
        long limitMs = TerminalConfig.get(getApplicationContext()).idleTimeoutSec() * 1000L;
        if (mCardScreenAtMs > 0 && limitMs > 0
                && SystemClock.uptimeMillis() - mCardScreenAtMs >= limitMs) {
            toast(getString(R.string.card_search_failed_ret));
            canCleTransaction();
            return true;
        }
        return false;
    }

    /** Wait on the worker for a stable removal before requesting another search. */
    private boolean awaitCardRemoval(int epoch) throws RemoteException {
        long emptySince = 0;
        boolean prompted = false;
        while (searchAlive(epoch)) {
            if (cardSearchExpired()) return false;
            if (mCardBinderService.isCardInserted()) {
                emptySince = 0;
                if (!prompted) {
                    prompted = true;
                    speak("Please remove the card");
                    runOnUiThread(() -> { if (searchAlive(epoch)) setCardStatus("PLEASE REMOVE THE CARD"); });
                }
            } else {
                long now = SystemClock.uptimeMillis();
                if (emptySince == 0) emptySince = now;
                if (now - emptySince >= SLOT_EMPTY_MS) return true;
            }
            sleepQuiet(SLOT_POLL_MS);
        }
        return false;
    }

    private void startSearchCardInternal(int cardSearchType) {
        startSearchCardInternal(cardSearchType, false);
    }

    private void startSearchCardInternal(int cardSearchType, final boolean waitForRemoval) {
        mExpiryWarningConfirmed = false;
        final int epoch = mCardSearchQueue.generation();
        if (!searchAlive(epoch)) return;
        if ("SETTLEMENT".equals(transType)) {
            runSettlement();
            return;
        }
        if ("VOID".equals(transType) || "EMI_VOID".equals(transType)
                || "PREAUTH_VOID".equals(transType)
                || "SALE_COMPLETE".equals(transType)) {
            // These need no card: they are built from the journal entry the
            // invoice number identified on the previous screen.
            runVoidTxn();
            return;
        }
        final int requestedMask = cardSearchType;
        if (!waitForRemoval && !mMagFallback) {
            // Ask for the card NOW, not once the reader is ours. The prompt
            // used to be set only after the lock was taken, so while an earlier
            // search was still finishing the screen showed the layout's
            // "Waiting for card" with no instruction - the dead state in the
            // field log.
            final String ask = mChipRequired ? "PLEASE INSERT THE CARD" : "INSERT, SWIPE OR TAP CARD";
            runOnUiThread(() -> {
                if (searchAlive(epoch) && !mChipDialogUp && !mRetryDialogUp) setCardStatus(ask);
            });
            // Until the reader is actually listening, say so with a spinner
            // rather than a prompt the reader cannot answer yet. Not on the
            // silent re-arm of an empty slice: the reader was listening a
            // moment ago and will be again in a fraction of a second.
            if (!mQuietRearm) {
                mPrepStartMs = SystemClock.uptimeMillis();
                showReaderPreparing();
            }
        }
        mCardSearchQueue.submit(epoch, () -> {
            if (!searchAlive(epoch) || mChipDialogUp || mRetryDialogUp) {
                hideReaderPreparing();
                return;
            }
            final long lockAskedAt = SystemClock.uptimeMillis();
            // (2.0.13 closed the home search here first. Field log 05-10
            // shows closeFindCardForEMV does not end an IC search early, so
            // that close bought nothing; the home screen no longer keeps a
            // search open at all - see PayActivity.probeChip.)
            if (!com.hjq.demo.utils.CardReaderAccess.beginTransaction(1000,
                    mReaderSession, "card screen " + transType)) {
                // Somebody else still has the reader. Say who, break it if it
                // is only the idle home search, and restart the app if it is a
                // screen that has already closed - that is the reader that
                // used to stay stuck until the app was killed by hand.
                if (onReaderLockBusy(epoch)) return;
                if (searchAlive(epoch) && !cardSearchExpired()) startSearchCardInternal(requestedMask, waitForRemoval);
                return;
            }
            mLockWaitSinceMs = 0L;
            mSearchInFlight.set(true);
            try {
                if (!searchAlive(epoch)) return;
                if (mCardBinderService == null) {
                    toast(getString(R.string.service_acquisition_failed));
                    mHandler.sendEmptyMessage(8004);
                    return;
                }
                if (waitForRemoval) {
                    // The previous SDK operation has returned and this worker owns
                    // the reader. Release its latched search before the next presentation.
                    mCardBinderService.closeFindCardForEMV();
                    if (!awaitCardRemoval(epoch)) return;
                } else {
                    // Every search starts by closing the last one - the order the
                    // Anfu demo uses (closeFindCard() before openFindCardForEMV).
                    // The idle home-screen search can still be open here: it is
                    // closed when the home screen pauses, and a search it armed a
                    // moment after that close would otherwise still own the
                    // reader when this one asks for it.
                    // Not before a silent re-arm: our own slice has just
                    // ended, nothing is open, and the open below closes
                    // anything left over itself ("repeat close" in the
                    // reader log). One fewer round trip to the reader.
                    try {
                        if (!mQuietRearm) mCardBinderService.closeFindCardForEMV();
                    } catch (Exception e) {
                        Log.w(TAG, "pre-search closeFindCardForEMV: " + e);
                    }
                }
                if (!searchAlive(epoch) || cardSearchExpired()) return;
                if (mMagFallback) {
                    try {
                        if (mCardBinderService != null) {
                            mCardBinderService.closeFindCardForEMV();
                        }
                    } catch (Exception ignore) {}
                    speak("Please swipe your card");
                    runOnUiThread(() -> {
                        if (searchAlive(epoch)) setCardStatus("PLEASE SWIPE THE CARD");
                    });
                    mHandler.sendEmptyMessage(8000);
                    hideReaderPreparing();
                    Log.i(TAG, "Fallback mode: armed direct MSR with timeout=" + MSR_CARD_TIMEOUT + "s");
                    msrCardTest();
                    return;
                }

                final int searchMask = mChipRequired ? CardSearchType.IC_CARD : requestedMask;
                if (searchMask == 0) {
                    toast("No card entry mode enabled");
                    canCleTransaction();
                    return;
                }
                cardType = Integer.MIN_VALUE;
                mChipSeenDuringSearch = slotOccupied();
                startChipSlotWatch();
                final boolean quiet = mQuietRearm;
                mQuietRearm = false;
                if (!quiet) {
                    speak(mChipRequired ? "Please insert your chip card" : "Please insert or tap your card");
                }
                runOnUiThread(() -> {
                    if (searchAlive(epoch)) setCardStatus(mChipRequired ? "PLEASE INSERT THE CARD" : "INSERT, SWIPE OR TAP CARD");
                });
                mHandler.sendEmptyMessage(8000);
                Log.i(TAG, "openFindCardForEMV: arming mask=" + searchMask
                        + " timeoutSec=" + FIND_CARD_TIMEOUT + " attempt=" + (mCardReadAttempt + 1)
                        + (quiet ? " (re-arm)" : ""));
                long started = SystemClock.uptimeMillis();
                if (!quiet) {
                    Log.i(TAG, "CARDTIME reader armed +" + (started - mPrepStartMs)
                            + " ms after the card prompt (waited " + (started - lockAskedAt)
                            + " ms for the reader)");
                }
                // The SDK takes ~0.2 s to switch the field and slot on after
                // this call (log 05-10: findCardAsyncOpen 180 ms). Clear the
                // spinner once that has passed, not before.
                mHandler.removeCallbacks(mHidePrep);
                mHandler.postDelayed(mHidePrep, READER_WARMUP_MS);
                int result;
                mOpenInFlight = true;
                try {
                    result = mCardBinderService.openFindCardForEMV(searchMask, FIND_CARD_TIMEOUT);
                } finally {
                    mOpenInFlight = false;
                    stopChipSlotWatch();
                    hideReaderPreparing();
                }
                if (!searchAlive(epoch)) return;
                if (result == EMV_FIND_DETECT_TIMEOUT) {
                    // V2.1 4.8.1: 8 = "detect card timeout". Nothing was
                    // presented; it is the same answer as -2003 and must be
                    // treated the same way, not as an unreadable card.
                    Log.i(TAG, "openFindCardForEMV returned 8 (detect card timeout)"
                            + " - treating as an empty search");
                    result = EMV_ERR_SEARCH_TIMEOUT;
                }
                if (result == CardSearchType.IC_CARD || result == CardSearchType.RF_CARD
                        || result == CardSearchType.MAGNETIC_STRIPE_CARD) {
                    mForeignCancels = 0;
                    mBusyRetries = 0;
                }
                cardType = result;
                mLastFindResult = result;
                mLastFindMs = SystemClock.uptimeMillis() - started;
                mCardSeenAtMs = SystemClock.uptimeMillis();
                String outcome = result == EMV_ERR_SEARCH_TIMEOUT ? "TIMEOUT"
                        : result == EMV_ERR_SEARCH_CANCELLED ? "CANCELLED"
                        : (result == CardSearchType.IC_CARD || result == CardSearchType.RF_CARD
                        || result == CardSearchType.MAGNETIC_STRIPE_CARD) ? "SUCCESS" : "FAILED";
                Log.i(TAG, "CARDDIAG openFindCardForEMV mask=" + searchMask + " result=" + result
                        + " outcome=" + outcome + " elapsedMs=" + mLastFindMs);
                if (!searchAlive(epoch)) return;
                disDialog();
                // A successful read consumes this search. Discard duplicate start requests.
                if ("SUCCESS".equals(outcome)) mCardSearchQueue.clearPending();
                if (mMagFallback && (result == CardSearchType.IC_CARD || result == CardSearchType.RF_CARD)) {
                    retrySearchCard(CardSearchType.MAGNETIC_STRIPE_CARD, "Please SWIPE the card");
                    return;
                }
                    // A card is here: the "nobody presented a card" clock has
                    // done its job. It must stop NOW and not merely when the
                    // read completes - a chip transaction can easily outlast
                    // the menu timeout, and this timer cancels transactions.
                    if (cardType == CardSearchType.MAGNETIC_STRIPE_CARD
                            || cardType == CardSearchType.IC_CARD
                            || cardType == CardSearchType.RF_CARD) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                cancelCardScreenTimeout();
                            }
                        });
                    }
                    switch (cardType) {
                        case CardSearchType.MAGNETIC_STRIPE_CARD:
//                            toast("Magnetic stripe card detected");
                            //磁条卡
                            // The stripe read gets the same "reading card"
                            // popup as chip and contactless. Without this the
                            // swipe path was the one method that showed
                            // nothing between the prompt and the PIN pad.
                            mHandler.sendEmptyMessage(8005);
                            msrCardTest();
                            break;
                        case CardSearchType.IC_CARD:
                            mHandler.sendEmptyMessage(8005);
//                            toast("IC card detected");
                            //接触式IC卡
                            // One reading of the slot switch while nothing else
                            // is talking to the reader. Only if it says "card
                            // in" is a later "card out" believed - see
                            // chipRemoved().
                            mChipSeenPresent = Boolean.TRUE.equals(slotState());
                            mCardRemoved = false;
                            Log.i(TAG, "chip detected - slot switch reads "
                                    + (mChipSeenPresent ? "present" : "not present (removal checks off)"));
                            emvKernelProcess();
                            break;
                        case CardSearchType.RF_CARD:
                            //非接触式卡
                            mHandler.sendEmptyMessage(8005);
//                            toast(" Contactless card detected");
                            emvKernelProcess();
                            break;
                        default:
                            if (cardType == EMV_ERR_SEARCH_TIMEOUT && !chipFailureCandidate()) {
                                // The slice ran out with nothing presented. The
                                // screen is still up and its own menu timeout
                                // decides when to give up, so arm the next
                                // slice silently - no voice, no dialog, no
                                // removal wait. A chip sitting unread in the
                                // slot is not this case: chipFailureCandidate
                                // sends that to the strike ladder below.
                                if (searchAlive(epoch) && !cardSearchExpired()) {
                                    mQuietRearm = true;
                                    startSearchCardInternal(mCtlsFellBack ? enabledSearchMask() : searchMask, false);
                                }
                                break;
                            }
                            if (cardType == EMV_ERR_SERVICE_BUSY) {
                                // V2.1: -2001 = the card service is still busy with
                                // an earlier call. That is not a bad read, so it
                                // gets no "remove the card and press RETRY"
                                // dialog: close, wait a moment, ask again.
                                mBusyRetries++;
                                Log.w(TAG, "openFindCardForEMV returned -2001 (card service busy),"
                                        + " try " + mBusyRetries + " of " + MAX_BUSY_RETRIES
                                        + " - reader " + com.hjq.demo.utils.CardReaderAccess.describeHolder());
                                try {
                                    if (mCardBinderService != null) {
                                        mCardBinderService.closeFindCardForEMV();
                                    }
                                } catch (Exception ignore) {}
                                if (mBusyRetries <= MAX_BUSY_RETRIES) {
                                    SystemClock.sleep(300);
                                    if (searchAlive(epoch) && !cardSearchExpired()) {
                                        startSearchCardInternal(mCtlsFellBack ? enabledSearchMask() : searchMask, false);
                                    }
                                    break;
                                }
                                mBusyRetries = 0;
                                retrySearchCard(mCtlsFellBack ? enabledSearchMask() : searchMask);
                                break;
                            }
                            // A cancelled search is not a missed card. See
                            // EMV_ERR_SEARCH_CANCELLED - it is what the SDK
                            // returns when closeFindCardForEMV lands while the
                            // search is still open, which is every normal way
                            // out of this screen.
                            if (cardType == EMV_ERR_SEARCH_CANCELLED) {
                                mCardSearchQueue.clearPending();
                                // Every cancel this screen makes itself sets
                                // stopFindCard / mTxnAborted / a new generation
                                // first, so searchAlive() is false for those and
                                // they never get this far. A cancel that DOES get
                                // here came from somewhere else - the home screen's
                                // idle search, or the previous card screen closing
                                // the reader on its way out - and used to leave
                                // this screen on PRESENT CARD with no search open,
                                // reading nothing, until the menu timeout. Re-arm.
                                //
                                // It never gives up while the screen is up: the
                                // old "stop after 5" left the prompt on screen
                                // with no search behind it. After the first few
                                // it just pauses longer; the menu timeout still
                                // ends the screen.
                                if (searchAlive(epoch) && !cardSearchExpired()) {
                                    mForeignCancels++;
                                    Log.w(TAG, "card search cancelled from outside this screen ("
                                            + mForeignCancels + ") - re-arming");
                                    SystemClock.sleep(mForeignCancels <= MAX_FOREIGN_CANCELS ? 200 : 1000);
                                    if (searchAlive(epoch)) {
                                        mQuietRearm = true;
                                        startSearchCardInternal(mCtlsFellBack ? enabledSearchMask() : searchMask, false);
                                    }
                                    break;
                                }
                                mHandler.sendEmptyMessage(8004);
                                Log.e(TAG, "card search cancelled (" + cardType
                                        + ") - not counting it as a missed read");
                                break;
                            }
                            // Not a usable read. Re-arm and ask for the card
                            // again instead of ending the transaction.
                            //
                            // If this arm was the contact-only fallback, go
                            // back to the full set of entry modes now. The
                            // wallet the kernel refused is one card; leaving
                            // contactless switched off for the rest of the
                            // transaction would refuse the next card too,
                            // which looks exactly like contactless breaking.
                            retrySearchCard(mCtlsFellBack ? enabledSearchMask() : searchMask);
                            break;
                    }
            } catch (RemoteException | RuntimeException e) {
                Log.e(TAG, "card search failed", e);
                if (searchAlive(epoch)) {
                    cardType = Integer.MIN_VALUE;
                    showCardRetryPrompt(requestedMask, "Card reader error. Please try again", epoch);
                }
            } finally {
                // Hold ownership through result handling and EMV processing.
                mSearchInFlight.set(false);
                com.hjq.demo.utils.CardReaderAccess.endTransaction();
            }
        });
    }

    /**
     * 获取当时设备是否已经签到
     *
     * @return true : 已签到
     * false: 未签到
     */
    private boolean getCurrentDateSign() {
        if (spUtils.contains(AFConstant.DEVICES_LAST_SIGN)) {
            String strLastSign = spUtils.getString(AFConstant.DEVICES_LAST_SIGN);
            return TimeUtils.isToday(strLastSign);
        }
        return false;
    }

    /**
     * Device sign-in - DISABLED.
     *
     * <p>This was the Anfu demo's key bootstrap. With
     * {@code FORMAL_TRANSACTION = false} it ran {@code setPOSMasterKey()},
     * which writes a <b>16-byte all-zero master key</b> taken from the front of
     * {@code "000000000000000000000000000000008CA64DE9C1B123A7"} (dstKeyLen is
     * 16, so only the leading zeros are used), and then three hardcoded demo
     * working keys with KCVs 4481F582, 5BB56FBE and 20727651.</p>
     *
     * <p>It is called from three points in the card flow, so every transaction
     * wiped the terminal master key injected from the admin panel and replaced
     * the PIN key with a demo key. That is why the pad reported TMK KCV
     * {@code 8CA64D} (the KCV of sixteen zero bytes), TPK {@code 4481F5} and
     * TAK {@code 5BB56F}, and why no PIN block could ever verify at DBBL - the
     * PIN was enciphered under an Anfu demo key.</p>
     *
     * <p>Keys in this application come from exactly two places: the terminal
     * master key is injected once from the admin panel, and the working key
     * arrives from the host in DE62 at logon. Nothing in the transaction flow
     * may write a key.</p>
     */
    private void devicesSign() {
        LogUtils.i("devicesSign: skipped - keys come from admin injection (TMK) "
                + "and the DE62 logon response (TPK). The demo key bootstrap is disabled.");
    }

    private void updateWorkKeyFail() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                disDialog();
                toast(getString(R.string.checkbalance_toast_loadworkkey_fail));
            }
        }, 2000);
    }


    /**
     * 提示框
     *
     * @param msgTip 提示语
     */
//    public void showDialog(String msgTip) {
//        if (mWaitDialog != null) {
//            Log.i(TAG, "showDialog: mWaitDialog != null");
//            mWaitDialog.dismiss();
//        }
////        if (isRun) {
//        mHandler.post(new Runnable() {
//            @Override
//            public void run() {
//                mWaitDialog = new WaitDialog.Builder(getActivity())
//                        .setMessage(msgTip)
//                        .setCancelable(false)
//                        .create();
//                mWaitDialog.show();
//            }
//        });
////        }
//    }

    /**
     * 提示框
     *
     * @param msgTip 提示语
     */
    public void showDialog(String msgTip) {
        if (mWaitDialog != null) {
            LogUtils.i("showDialog: mWaitDialog != null");
            mWaitDialog.dismiss();
        }
        mWaitDialog = new WaitDialog.Builder(FindCardActivity.this)
                .setMessage(msgTip)
                .setCancelable(false)
                .create();
        if (!isFinishing() && !isDestroyed()) {
            // 这里放置对话框显示逻辑
            mWaitDialog.show();
        } else {
            LogUtils.e("showDialog: 异常了");
        }
        if (mWaitDialog != null) {
            mWaitDialog.setOnKeyListener(new BaseDialog.OnKeyListener() {
                @Override
                public boolean onKey(BaseDialog dialog, KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                        if (isSearchingCard && dialog.isShowing()) {
                            canCleTransaction();
                            isSearchingCard = false;
                            dialog.dismiss();
                            toast(getString(R.string.trans_canceled));
                        }

                    }
                    return false;
                }
            });

        } else {
            LogUtils.e("showDialog: 初始化异常...");
        }

    }


    private void disDialog() {
        LogUtils.i("disDialog: 调用关闭Dialog");
        // First, and outside the early return below: the reading popup has to
        // come down whether or not a wait dialog happens to be up.
        dismissCardReading();
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
            mWaitDialog = null;
            LogUtils.i(" mWaitDialog != null");
            return;
        }
        LogUtils.i(" mWaitDialog == null");
    }


    /**
     * 初始化数据
     */
    @Override
    protected void initData() {

    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mCardBinderService = ICardBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_CARD));
            mEmvBinderService = IEmvBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_EMV));
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            mPinPadBinderService = IPinPadBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PINPAD));
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
            mISystemEncDecBinderService = ISystemEncDecBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM_ENCDEC));
            AppPreferences mPreferences = AppPreferences.getInstance(getContext());
            if (mPreferences.isFirstLaunch()) {
                new TipsDialog.Builder(getContext())
                        .setIcon(TipsDialog.ICON_ERROR)
                        .setMessage(getString(R.string.load_param_fail))
                        .show();
            }
            startSearchCard(enabledSearchMask());


            if (isLcdSupported) {
                LcdUtils.getInstance().init(mSystemBinderService);
                lcdUtils = LcdUtils.getInstance();
                lcdInfo = lcdUtils.lcdGetDevLcdStatus();
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        mainModel = new MainModelImpl(new MainListener() {
            @Override
            public void setViewLog(String log) {
                Log.i(TAG, "setViewLog: " + log);
            }
        }, mPinPadBinderService, mISystemEncDecBinderService);


        FindCardActivity.MyControllerListener listener = new FindCardActivity.MyControllerListener();
        mainModel.initTrans(listener);

        getKeyCode();

        Log.e(TAG, "onServiceConnected: " + (getCurrentDateSign() ? "已签到Y" : "未签到..."));

        if (!getCurrentDateSign()) {
            devicesSign();
        } else {
            signedStatus = true;
        }

        try {
            int emvRet = mEmvBinderService.init();
            Log.e(TAG, "emvRet: " + emvRet);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        if (FORMAL_TRANSACTION) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mainModel.connectSocket();
                }
            });
        }

    }


    /**
     * 获取键值
     */
    private void getKeyCode() {
        //Log.e(TAG, " =================== getKeyCode: 获取键值....===================");
        /*try {
            mSystemBinderService.getKeypadValue(new KeypadValueCallback.Stub() {
                @Override
                public void onKeypadValueReceived(int keyCode) throws RemoteException {
                    KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                    Log.e(TAG, "=============返回键值=============");
                    Log.e(TAG, "onKeypadValueReceived.....-> " + keyCode);
                }

                @Override
                public void onKeypadValueFail(int errorCord) throws RemoteException {
                    Log.e(TAG, "onKeypadValueFail.....errorCord -> " + errorCord);
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
    }


    class MyControllerListener implements AFSwipeControllerListener {

        /**
         * Return card information
         *
         * @param cardInfo
         */
        @Override
        public void onTransactionResult(Map<String, String> responseData) {
            Log.i(TAG, "onTransactionResult() IC.RF.MSC.交易回调");
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }

            //交易成功
            if (responseData.get(AFConstant.TRADE_MSG_TAG).equals(AFConstant.TRADE_VALUE_SUCCESS)) {
                Iterator<Map.Entry<String, String>> iterator = responseData.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<String, String> entry = iterator.next();
                    Log.d(TAG, "key:" + entry.getKey() + "\tvalues:" + entry.getValue());
                }
               /*
                key:BF50	values:http://39.96.48 电子签名
                key:BF04	values:48290000 收单机构编号，下载并打印小票上
                key:BF03	values:10052482 收单终端号，下载并打印小票上
                key:BF36	values:1
                key:BF02	values:82982415699005C 收单商户号，下载并打印小票上
                key:BF01	values:XXXX 发卡行名称(中文)，下载并打印小票上
                key:BF40	values:XXX  交易商户简称(无下传值时打印终端配置商户名称)
                */
                toast("Successful transaction ：" + responseData.get("BF02"));
                printEntity = new TransReceiptPrintEntity();
                // 商户名称 ->XXXX
                printEntity.setBusinessName(responseData.get("BF40"));
                // 商户号 ->82982415699005C
                printEntity.setMerchantName(responseData.get("BF02"));
                //收单终端编号 ->10052482
                printEntity.setAcquirer(responseData.get("BF03"));
                //操作员号
                printEntity.setOperatorNo("01");
                //发卡行 ->中国银行
                printEntity.setIssuer(responseData.get("BF01"));
                //收单行 ->48290000
                printEntity.setAcquirerId(responseData.get("BF04"));
                //批次号
                printEntity.setBatchNumber("001001");
                //凭证号
                printEntity.setVoucherNumber(responseData.get(AFConstant.TRANS_VOUCHER_NUMBER));
                //37域交易参考号
                printEntity.setRefNo(responseData.get(AFConstant.TRANS_REF_NUMBER));
                printEntity.setCardNo(currentCardNumber);
                printEntity.setTransType("SALE");
                printEntity.setTransDateTime(responseData.get(AFConstant.TRANS_DATE_TIME));
                printEntity.setTransAmount(currentAmount);
                Log.i(TAG, "onTransactionResult: \n" + printEntity.toString());
                printEntity = null;
            } else {
                Log.e(TAG, "onTransactionResult: " + "交易失败:" + responseData.get(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP));
                toast("Transaction failed:" + responseData.get(AFConstant.TRADE_VALUE_EXCEPTION_RESPONSE_CODE_ERROR_TIP));
            }
        }

        /**
         * 二维码支付主扫|被扫，服务器返回的响应
         */
        @Override
        public void onQRCodePaymentResult(Map<String, String> responseData, int platformType) {
        }

        /**
         * 设备签到结果
         * Device sign-in result
         *
         * @param importWKResult 导入秘钥结果 Updated the working key Result
         *                       true:更新工作秘钥成功 Updated the working key successfully
         *                       false:更新工作秘钥失败 Failed to update the working key
         */
        @Override
        public void onDeviceSignIn(boolean importWKResult) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            //签到成功，更新签到时间
            if (importWKResult) {
                spUtils.put(AFConstant.DEVICES_LAST_SIGN, AFConstant.SDF_DEVICES_SIGN.format(new Date()));
                signedStatus = true;
            } else {
                signedStatus = false;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mainModel.AFUpdatePubKey();
                    }
                });
            }
            toast("Sign in " + (importWKResult ? "successfully!" : "failed,Request to update the master key!"));
        }

        /**
         * 更新设备参数
         * Update device parameters
         *
         * @param paramType    1:设备参数  Device parameters
         *                     2:更新公钥 Public key data
         *                     3:主密钥  Master Key
         * @param updateResult 更新结果 true：成功 false:失败
         */
        @Override
        public void updateDevParam(int paramType, boolean updateResult) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            switch (paramType) {
                case 1:
                    toast(updateResult ? "Terminal parameters updated successfully" : "Terminal parameter update failed");
                    break;
                case 2:
                    toast(updateResult ? "Public key updated successfully" : "Public key update failed");
                    break;
                case 3:
                    toast(updateResult ? "The master key was updated successfully ,Re-sign in" : "Master key update failed, data abnormality!");
                    if (updateResult) {
                        //Re-sign in
                        devicesSign();
                    }
                    break;
                default:
                    break;
            }
        }

        /**
         * Make a request to the server
         */
        @Override
        public void onSendData(int msgType) {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            String tempTip = null;
            switch (msgType) {
                case AFConstant.SEND_PKG_MSG_TYPE_SIGN_IN:
                    tempTip = getString(R.string.pkg_msg_type_sign_in);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_SALE:
                    tempTip = getString(R.string.pkg_msg_type_sale);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UNION_PAY:
                    tempTip = getString(R.string.pkg_msg_type_union_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_ALI_PAY:
                    tempTip = getString(R.string.pkg_msg_type_ali_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_WECHAT_PAY:
                    tempTip = getString(R.string.pkg_msg_type_wechat_pay);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_DEV_PARAM:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_param);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_PUB_KEY:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_pub_key);
                    break;
                case AFConstant.SEND_PKG_MSG_TYPE_UPDATE_MASTER_KEY:
                    tempTip = getString(R.string.pkg_msg_type_update_dev_tm_key);
                    break;
                default:
                    tempTip = "";
                    break;
            }
            sendDataTip(tempTip + getString(R.string.trans_send_data));
        }

        private void sendDataTip(String tip) {
            Log.i(TAG, "sendDataTip: " + System.currentTimeMillis() + "\tTip:\t" + tip);
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mWaitDialog = new WaitDialog.Builder(FindCardActivity.this)
                            .setMessage(tip)
                            .setCancelable(false).create();
                    if (mWaitDialog != null) {
                        mWaitDialog.show();
                    }
                }
            });
        }

        /**
         * Receiving data from the server
         */
        @Override
        public void onDataReceiving() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            mWaitDialog = new WaitDialog.Builder(getActivity()).setMessage(getString(R.string.trans_data_receiving))//选择寻卡类型
                    .setCancelable(false).create();
            mWaitDialog.show();
        }

        /**
         * Connection failure
         */
        @Override
        public void onConnectServerFail() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            toast("can not reach server");
        }

        /**
         * The server responded abnormally. Please try to sign in again.
         */
        @Override
        public void onServerUnresponsive() {
            if (mWaitDialog != null) {
                mWaitDialog.dismiss();
            }
            //toast("服务器响应异常，请尝试重新签到!");
            new MessageDialog.Builder(getActivity())
                    // 标题可以不用填写
                    .setTitle(R.string.main_title_friendly)
                    .setCancelable(false)
                    // 内容必须要填写
                    .setMessage("The server responded abnormally.\n Please try to sign in again.!")
                    // 确定按钮文本
                    .setConfirm(getString(R.string.common_confirm))
                    // 设置 null 表示不显示取消按钮
                    .setCancel(getString(R.string.common_cancel))
                    .setListener(new MessageDialog.OnListener() {

                        @Override
                        public void onConfirm(BaseDialog dialog) {
                            // toast(R.string.amount_confirm);
                            devicesSign();
                        }

                        @Override
                        public void onCancel(BaseDialog dialog) {
                            // toast(R.string.amount_clear);
                        }
                    }).show();
        }
    }


    /**
     * 更新工作密钥
     * Update working key
     */
    private boolean importWriteWorkingKey(WorkingKeyEntity workingKeyEntity) {
        try {
            int mode = 0x01;
            int keyLen = 0x10; // 8 或者  16   或24
            KcvInfoParam kcvInfoParam = new KcvInfoParam();
            kcvInfoParam.setCheckMode(1);
            String kcvLen = Integer.toHexString(AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()).length);
            Log.e(TAG, "importWriteWorkingKey: kcvLen--->" + kcvLen);
            String formattedString = String.format("%02d", Integer.parseInt(kcvLen));

            Log.e(TAG, "importWriteWorkingKey: kcvLen--->" + formattedString);
            byte[] checkValueData = mergeByteArrays(AFUtils.hexString2ByteArray(formattedString), AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()));
            kcvInfoParam.setCheckBuf(AFUtils.hexString2ByteArray(workingKeyEntity.getKeyCheckValues()));
            int mKeyNo = 0x02;
            Log.e(TAG, "importWriteWorkingKey: -----start-----》》》");
            //获取秘钥ID
            int usKeyType = workingKeyEntity.getKeyID();
            //获取秘钥数据
            byte[] keyData = HexUtil.hexStringToByte(workingKeyEntity.getKeyData());

            int ret = mPinPadBinderService.posWriteWorkingKey(usKeyType, keyLen, keyData, kcvInfoParam, mode, mKeyNo);
            Log.e(TAG, "importWriteWorkingKey: -----end-----》》》" + ret);

            Log.e(TAG, "workingKeyEntity : \n" + workingKeyEntity.toString());
            if (ret == 0) {
                return true;
               /* try {
                    Log.e(TAG, "mIPinPadBinderService.posGetKcv: ------------start---------->>");
                    PosGetKcvResult posGetKcvResult = mPinPadBinderService.posGetKcv(mKeyNo, keyId);
                    Log.e(TAG, "mIPinPadBinderService.posGetKcv: ------------end---------->>" + posGetKcvResult.getRet());
                    int getKcvResultRet = posGetKcvResult.getRet();
                    if (posGetKcvResult.getRet() == 0) {
                        kcvInfoParam.getCheckBuf();
                        byte[] checkRes = posGetKcvResult.getKcvInfo();
                        Log.e(TAG, "checkRes：\t" + AFUtils.showResult0xStr(checkRes));
                        String strKecResult = AFUtils.showResult16Str(posGetKcvResult.getKcvInfo());
                        Log.e(TAG, "importWriteWorkingKey: ----------strKecResult---->" + strKecResult);
                        Log.e(TAG, "DAL_POSGetKcv:" + getKcvResultRet);
                        Log.e(TAG, "DAL_POSGetKcv结果：" + strKecResult);
                        strKecResult = strKecResult.substring(8, 16);
                        //00080000 173D5428
                        if (strKecResult.equals(workingKeyEntity.getKeyCheckValues().substring(0, 8))) {
                            Log.e(TAG, "DAL_POSGetKcv：校验通过");
                            return true;
                        }
                    }

                } catch (RemoteException e) {
                    e.printStackTrace();
                }*/
            } else {
                return false;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static byte[] mergeByteArrays(byte[] array1, byte[] array2) {
        byte[] mergedArray = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, mergedArray, 0, array1.length);
        System.arraycopy(array2, 0, mergedArray, array1.length, array2.length);
        return mergedArray;
    }

    /**
     * msrCardTest
     */
    private void msrCardTest() {
        try {
            saleDataModel = new SaleDataModel();
            LogUtils.i("----msrCardTest()");
            mCardBinderService.getMSRCardInfo(MSR_CARD_TIMEOUT, new FindCardActivity.ICardTestCallbackProxy());
        } catch (RemoteException e) {
            e.printStackTrace();
            LogUtils.e(e.getMessage());
        }
    }


    /**
     * 根据TAG值获取数据
     *
     * @param tag 数据类型
     * @return 相应域的数据
     */
    private String getTagValue(int tag) {
        byte[] getTLV = null;
        try {
            getTLV = mEmvBinderService.apiKernelEmvGetTlv(tag);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        return (getTLV == null ? "" : HexUtil.bcd2str(getTLV));
    }

    /**
     * 设置交易参数
     *
     * @param tag   数据类型
     * @param value 数据的值
     * @return
     */
    private boolean setTagValue(int tag, String value) {
        byte[] tempValue = AFUtils.hexString2ByteArray(value);
        Log.i(TAG, "setTagValue: " + AFUtils.showResult16Str(tempValue));
        Log.i(TAG, "setTagValue Len: " + tempValue.length);
//        Log.i(TAG, "setTagValue: " + AFUtils.showResult16Str(AFUtils.StringToByteArray(value)));
        try {
            int res = mCardBinderService.posBscSetTLV(tag, tempValue, tempValue.length);
            Log.i(TAG, "setTagValue res: " + res);
            return res == 0;
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 获取55域内容
     * Get 55 domain data
     *
     * @param tags tag list
     */
    private String getICData(int[] tags) {
        //55域内容
        StringBuffer sbIcData = new StringBuffer();
        sbIcData.setLength(0);
        //缺失TAG
        StringBuffer sbMissingTAG = new StringBuffer();
        sbMissingTAG.setLength(0);

        for (int i = 0; i < tags.length; i++) {
            try {
                int currTag = tags[i];
                byte[] currData = mEmvBinderService.apiKernelEmvGetTlv(currTag);
                String tag = Integer.toHexString(currTag);
                if (currData != null) {
                    String value = HexUtil.bcd2str(currData);
                    String len = Integer.toHexString(value.length() / 2);
                    LogUtils.i("TAG: " + tag + "\t\tLen: " + len + "\t\tValue: " + value);
                    sbIcData.append(AFUtils.packSendTLV(tag, value));
                } else {
                    //没有数据的TAG处理
                    sbMissingTAG.append(tag + "00");
                }

            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        String icData = sbIcData.toString() + sbMissingTAG.toString();
        LogUtils.i("55域内容: " + icData + "\n");
        LogUtils.i("缺失的TAG: " + sbMissingTAG.toString() + "\n");
        /*ArrayList<TLVModel> tlvModels = AFUtils.praseTLVStr(AFUtils.hexString2ByteArray(icData));
        Map<String, String> tm = AFUtils.list2Map(tlvModels, 3);
        for (Map.Entry<String, String> entry : tm.entrySet()) {
            LogUtil.i(TAG, "key=" + entry.getKey() + "  value=" + entry.getValue());
        }*/

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
        private final int searchEpoch = mCardSearchQueue.generation();

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
            if (!searchAlive(searchEpoch)) return;
            Message cardMsg = new Message();
            Bundle cardBundle = new Bundle();
            Log.i(TAG, "磁条卡 二磁道信息 : " + AFUtils.showResult16Str(track2Data));
            String strTrack2Data = track2Data == null ? "" : new String(track2Data).trim();
            strTrack2Data = strTrack2Data.replace('D', '=').replace('d', '=');
            int trackSeparator = strTrack2Data.indexOf('=');
            if (trackSeparator < 12 || strTrack2Data.length() < trackSeparator + 8) {
                String errInfo = "Read Card Error,CardNo is empty";
                LogUtils.d(errInfo);
                cardMsg.what = MSG_TRANS_FAil;
                cardBundle.putCharSequence("errInfo", errInfo);
                cardMsg.setData(cardBundle);
                mHandler.sendMessage(cardMsg);
                return;
            }

            if (!mMagFallback && com.hjq.demo.ans8583.TransactionPolicy.chipServiceCode(strTrack2Data)) {
                runOnUiThread(() -> {
                    if (!searchAlive(searchEpoch)) return;
                    disDialog();
                    TerminalConfig config = TerminalConfig.get(getApplicationContext());
                    if (!config.entryChip()) {
                        toast("Chip card requires chip entry; enable CHIP in Parameters");
                        canCleTransaction();
                        return;
                    }
                    mCardReadOk = false;
                    mChipRequired = true;
                    cardType = EMV_ERR_SEARCH_CANCELLED; // redirect is not a failed chip read
                    speak("Please insert the chip card");
                    rearmWhenFree(CardSearchType.IC_CARD, "CHIP CARD - PLEASE INSERT");
                });
                return;
            }

            TerminalConfig swipeConfig = TerminalConfig.get(getApplicationContext());
            if (!(mMagFallback ? swipeConfig.entryFallback() : swipeConfig.entrySwipe())) {
                runOnUiThread(() -> {
                    toast("Swipe entry is disabled");
                    canCleTransaction();
                });
                return;
            }

            cardMsg.what = MSG_TRANS_MSR_SUCCESS;
            // Keep the FULL track 2 image - DE35 needs the whole thing, not just
            // the PAN (spec 6.11: track 2 excluding sentinels and LRC).
            if (saleDataModel != null) {
                saleDataModel.setTrack2data(strTrack2Data);
                saleDataModel.setIcData("");
                saleDataModel.setCardNo(strTrack2Data.substring(0, trackSeparator));
                cardValid = strTrack2Data.substring(trackSeparator + 1, trackSeparator + 5);
                saleDataModel.setCardValid(cardValid);
            }
            if (strTrack2Data.contains("=")) {
                cardBundle.putCharSequence("track2Data", strTrack2Data.substring(0, strTrack2Data.indexOf("=")));
                currentCardNumber = strTrack2Data.substring(0, strTrack2Data.indexOf("="));
                tipCardNumber = currentCardNumber;
                // Same range check on the swipe path.
                if (rejectUnsupportedCard(tipCardNumber)) {
                    return;
                }
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
            if (!searchAlive(searchEpoch)) return;
            if (errorCode == ErrorCode.SUCCESS) {
                LogUtils.d("成功");
            } else {
                String errInfo = "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n";
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
            LogUtil.i(TAG, "重置SP结果:\t" + (ret == 0 ? "成功" : "失败"));
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/
    }


    /**
     * DISABLED - the Anfu demo's master key writer.
     *
     * <p>It wrote {@code dstKeyLen = 16} bytes taken from the front of
     * {@code "000000000000000000000000000000008CA64DE9C1B123A7"} - i.e. a master
     * key of <b>sixteen zero bytes</b>, whose KCV is {@code 8CA64D}. Running it
     * destroyed the terminal master key injected from the admin panel, and with
     * it every working key the host had sent.</p>
     *
     * <p>The terminal master key is injected once, by hand, from key ceremony
     * components. It must never be written by application code.</p>
     */
    private boolean setPOSMasterKey() {
        Log.e(TAG, "setPOSMasterKey() is disabled - it wrote an all-zero master key "
                + "and destroyed the injected TMK. Inject the master key from the admin panel.");
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
        //设置字体下划线
        //spanString.setSpan(new UnderlineSpan(), 0, spanString.length(), 0);
        //设置字体为斜体
        //spanString.setSpan(new StyleSpan(Typeface.ITALIC), 0, spanString.length(), 0);

        return spanString;
    }


    /**
     * 统计byte数组中1的个数
     *
     * @param bytes
     * @return
     */
    private long countBits(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return 0;
        }

        long count = 0;
        for (int i = 0; i < bytes.length; i++) {
            for (int j = 0; j < 8; j++) {
                if ((bytes[i] & (1 << j)) != 0) {
                    count++;
                }
            }
        }

        return count;
    }


    /**
     * 配置交易参数
     * Configure EMV transaction parameters
     * 卡片类型 0：IC卡 1：非接卡
     */
    /**
     * The EMV kernel's parameter block is a FIXED 256 bytes.
     *
     * <p>{@code apiEmvKernelProcess} does not negotiate this. Hand it any other
     * length and it rejects the call outright - it returns an error and never
     * powers the card, so there is no PPSE, no SELECT, an empty select space
     * and every tag missing. Downstream that looks exactly like a card that
     * could not be read, which is what sent this bug hunting in the wrong
     * place: the log showed the reader finding the card, then no PAN.</p>
     *
     * <p>The header fields assembled into the map below come to 98 bytes:
     * eleven single-byte flags, a 40-byte merchant name, 3-byte invoice,
     * 6-byte amount, 6-byte other amount, 2-byte currency, 3-byte date, 3-byte
     * time, 15-byte merchant id, 8-byte terminal id and the 1-byte addition
     * length. So the addition field that follows is the remaining 158.</p>
     *
     * <p>v1.8.8 got this right by accident, with a 158-byte pre-padded string
     * literal. Rewriting it as allocate-and-pad kept the contents and changed
     * the size, which is the whole of this defect.</p>
     */
    private static final int EMV_PARAM_LEN = 256;

    /** Everything put into the parameter map before {@code addition}. */
    private static final int EMV_HEADER_LEN = 98;

    /** So the zero-padded addition field is exactly this long. */
    private static final int EMV_ADDITION_LEN = EMV_PARAM_LEN - EMV_HEADER_LEN;

    /**
     * First argument to {@code apiDalCscIsoCmd}.
     *
     * <p>The SDK header does not name it. The kernel's own log line -
     * {@code DAL_cscIsoCmd fast picc ppse type:4} - puts a 4 there on a
     * contactless card, and 4 is {@link CardSearchType#RF_CARD}, so that is
     * what is tried first. The rest are a fallback ladder in case the mapping
     * is something else on another build; whichever value answers is
     * remembered and reused for the rest of the transaction.</p>
     */
    private static final int[] CSC_ISO_ARGS = {CardSearchType.RF_CARD, 0, 1, 2};

    /** The value from {@link #CSC_ISO_ARGS} that worked, or -1 before the first call. */
    private int mCscIsoArg = -1;

    /**
     * Send one APDU to the contactless card and return the response as hex,
     * status word included.
     *
     * @return the response, or "" if the card did not answer
     */
    private String cscApdu(String apduHex) throws RemoteException {
        byte[] cmd = AFUtils.hexString2ByteArray(apduHex);
        int[] tries = (mCscIsoArg >= 0) ? new int[]{mCscIsoArg} : CSC_ISO_ARGS;
        for (int arg : tries) {
            com.szanfu.sdk.api.card.ICardCscIsoCmdResult r =
                    mCardBinderService.apiDalCscIsoCmd(arg, cmd, cmd.length);
            if (r == null) {
                Log.e(TAG, "CSC APDU arg=" + arg + " -> null result");
                continue;
            }
            byte[] data = r.getCardData();
            int outLen = r.getOutLen();
            String resp = "";
            if (data != null && data.length > 0) {
                byte[] used = (outLen > 0 && outLen <= data.length)
                        ? java.util.Arrays.copyOf(data, outLen) : data;
                resp = AFUtils.showResult16Str(used).replace(" ", "");
            }
            Log.e(TAG, "CSC APDU arg=" + arg + " > " + apduHex
                    + "  ret=" + r.getRet() + " outLen=" + outLen + " < " + resp);
            if (r.getRet() == 0 && resp.length() >= 4) {
                mCscIsoArg = arg;
                return resp;
            }
        }
        return "";
    }

    /** An APDU response minus its two status bytes. */
    private static String apduBody(String resp) {
        return (resp == null || resp.length() < 4) ? "" : resp.substring(0, resp.length() - 4);
    }

    /** True when the card answered 9000. */
    private static boolean apduOk(String resp) {
        return resp != null && resp.length() >= 4 && resp.toUpperCase(java.util.Locale.US).endsWith("9000");
    }

    /**
     * Find one tag in a response, descending into the templates that wrap it.
     *
     * <p>{@link Emv55#parse} walks one level. An FCI buries the PDOL two deep -
     * 6F then A5 - and a GPO response puts the AFL inside 77.</p>
     */
    private String tagFrom(String bodyHex, String tag) {
        if (bodyHex == null || bodyHex.isEmpty()) {
            return "";
        }
        java.util.Map<String, String> t = com.hjq.demo.ans8583.Emv55.parse(bodyHex);
        if (t == null) {
            return "";
        }
        String v = t.get(tag);
        if (v != null && !v.isEmpty()) {
            return v;
        }
        for (String wrapper : new String[]{"6F", "A5", "BF0C", "61", "77", "70", "80"}) {
            String inner = t.get(wrapper);
            if (inner != null && !inner.isEmpty()) {
                String found = tagFrom(inner, tag);
                if (!found.isEmpty()) {
                    return found;
                }
            }
        }
        return "";
    }

    /**
     * Run a KonaMoney contactless sale over raw APDUs, if this is one.
     *
     * <p>Kona's terminal specification, section 2.2: SELECT PPSE, SELECT the
     * KonaMoney AID, GET PROCESSING OPTIONS with the PDOL the FCI asked for,
     * and READ RECORD when the GPO response did not already carry the track.
     * Then go online. There is nothing else - the card generates no
     * cryptogram and verifies no cardholder, which is why the kernel's
     * elaborate machinery is not only failing here but unnecessary.</p>
     *
     * @return true when this took over the transaction; false to let the
     *         kernel run, which is what happens for every other card
     */
    private boolean tryKonaMoneyContactless() {
        if (cardType != CardSearchType.RF_CARD || mCardBinderService == null) {
            return false;
        }
        try {
            String ppse = cscApdu(com.hjq.demo.ans8583.KonaMoney.selectPpse());
            if (!apduOk(ppse)) {
                Log.i(TAG, "KONA CL: PPSE did not answer 9000 - leaving this card"
                        + " to the kernel");
                return false;
            }
            if (!apduBody(ppse).toUpperCase(java.util.Locale.US)
                    .contains(com.hjq.demo.ans8583.KonaMoney.AID)) {
                Log.i(TAG, "KONA CL: PPSE does not name the KonaMoney AID - this is"
                        + " a different card, leaving it to the kernel");
                return false;
            }
            Log.e(TAG, "KONA CL: KonaMoney card, driving the transaction directly");

            String fci = cscApdu(com.hjq.demo.ans8583.KonaMoney.selectKona());
            if (!apduOk(fci)) {
                Log.e(TAG, "KONA CL: SELECT AID failed - " + fci);
                return false;
            }
            String pdol = tagFrom(apduBody(fci), "9F38");
            Log.e(TAG, "KONA CL: PDOL=" + (pdol.isEmpty() ? "(none)" : pdol));

            long amountMinor = TerminalConfig.parseMajorToMinor(currentAmount);
            String yymmdd = SDF_TAG_9A_YYDD.format(new Date());
            String terminalRandom = konaTerminalRandom();
            String country = "0050";
            String currency = "0050";

            String dol = com.hjq.demo.ans8583.KonaMoney.buildDol(pdol,
                    com.hjq.demo.ans8583.KonaMoney.pdolValues(amountMinor, 0,
                            country, currency, yymmdd, "00", terminalRandom));
            String gpo = cscApdu(com.hjq.demo.ans8583.KonaMoney.getProcessingOptions(
                    com.hjq.demo.ans8583.KonaMoney.gpoData(dol)));
            if (!apduOk(gpo)) {
                Log.e(TAG, "KONA CL: GPO failed - " + gpo);
                return false;
            }

            // Everything the card hands over, gathered as it arrives. The
            // GPO response carries the AIP; the records carry CDOL1 and,
            // usually, the track.
            java.util.Map<String, String> cardTags = new java.util.LinkedHashMap<>();
            collectTags(apduBody(gpo), cardTags);

            String track2 = com.hjq.demo.ans8583.KonaMoney.track2From(apduBody(gpo));
            String afl = tagFrom(apduBody(gpo), "94");
            for (com.hjq.demo.ans8583.KonaMoney.Afl a
                    : com.hjq.demo.ans8583.KonaMoney.parseAfl(afl)) {
                for (int rec = a.firstRecord; rec <= a.lastRecord; rec++) {
                    String rr = cscApdu(com.hjq.demo.ans8583.KonaMoney.readRecord(a.sfi, rec));
                    if (!apduOk(rr)) {
                        continue;
                    }
                    collectTags(apduBody(rr), cardTags);
                    if (track2.isEmpty()) {
                        track2 = com.hjq.demo.ans8583.KonaMoney.track2From(apduBody(rr));
                    }
                }
            }
            if (track2.isEmpty()) {
                Log.e(TAG, "KONA CL: no track 2 anywhere - cannot go online");
                return false;
            }

            // GENERATE AC.
            //
            // The 2017 KonaMoney specification describes contactless as MSD
            // only - track 2 and go online, no cryptogram. The card in front
            // of us is newer than that document and does more. A working
            // terminal on this same card and this same host sends a full
            // DE55: 9F26 = 614DD105CB683123, 9F27 = 80 (ARQC), 9F36 = 0007,
            // 9F10, 82 = 00C0. So the card generates a cryptogram, the host
            // expects one, and reading the track and stopping is not enough.
            //
            // CDOL1 comes off the card, so this does not need to know what
            // the card will ask for - buildDol fills in what the terminal has
            // and zero-fills the rest, which is what EMV Book 3 5.4 requires.
            String cdol1 = cardTags.get("8C");
            if (cdol1 == null || cdol1.isEmpty()) {
                Log.e(TAG, "KONA CL: no CDOL1 (8C) on the card - going online without"
                        + " a cryptogram, which the host may refuse");
            } else {
                String acData = com.hjq.demo.ans8583.KonaMoney.buildDol(cdol1,
                        konaDolValues(amountMinor, country, currency, yymmdd,
                                terminalRandom));
                String genAc = cscApdu("80AE8000"
                        + String.format(java.util.Locale.US, "%02X", acData.length() / 2)
                        + acData + "00");
                if (apduOk(genAc)) {
                    harvestGenerateAc(apduBody(genAc), cardTags);
                } else {
                    Log.e(TAG, "KONA CL: GENERATE AC failed - " + genAc);
                }
            }

            startKonaMoneyClTxn(track2, cardTags, amountMinor, country, currency,
                    yymmdd, terminalRandom);
            return true;
        } catch (RemoteException e) {
            Log.e(TAG, "KONA CL: card service failed", e);
            return false;
        } catch (RuntimeException e) {
            // Anything unexpected falls back to the kernel rather than
            // stranding the sale on a half-driven card.
            Log.e(TAG, "KONA CL: aborted, falling back to the kernel", e);
            return false;
        }
    }

    /**
     * Terminal values mirrored from a terminal that this host accepts.
     *
     * <p>These are declarative - the terminal says what it is and what it did
     * - so there is no way to derive them from the card. They are taken from a
     * capture of a working transaction on the same card, at the same host, on
     * the same merchant: {@code 9F09 = 0001}, {@code 9F34 = 3F0000}
     * (no CVM performed), {@code 95 = 8000008000} (offline data authentication
     * was not performed, cardholder verification was not successful).</p>
     *
     * <p>{@code 9F09} in particular is per-application. This terminal's
     * configured value is {@code 0002}, set for the contact path; the working
     * terminal sends {@code 0001} for the KonaMoney application. Worth
     * confirming with Kona rather than leaving on trust.</p>
     */
    private static final String KONA_APP_VERSION = "0001";
    /**
     * Terminal capabilities. This is the terminal's real configured value.
     * The working capture sends {@code 000000}, which declares nothing at all
     * - if the host ever objects to this field, that is the one line to
     * change, and the difference is deliberate rather than overlooked.
     */
    private static final String KONA_TERM_CAPS = "E0F0C8";
    private static final String KONA_CVM_RESULTS = "3F0000";
    private static final String KONA_TVR = "8000008000";

    /**
     * The values a KonaMoney CDOL1 can ask for.
     *
     * <p>Whatever the card does not ask for is simply never emitted, and
     * anything asked for that is not here is zero-filled to the right length.
     * The same values go into DE55, so what the issuer recomputes the
     * cryptogram over is what the terminal actually sent.</p>
     */
    private java.util.Map<String, String> konaDolValues(long amountMinor, String country,
                                                        String currency, String yymmdd,
                                                        String terminalRandom) {
        java.util.Map<String, String> v = new java.util.LinkedHashMap<>();
        v.put("9F02", String.format(java.util.Locale.US, "%012d", amountMinor));
        v.put("9F03", "000000000000");
        v.put("9F1A", country);
        v.put("5F2A", currency);
        v.put("95", KONA_TVR);
        v.put("9A", yymmdd);
        v.put("9C", "00");
        v.put("9F37", terminalRandom);
        v.put("9F35", "22");
        v.put("9F33", KONA_TERM_CAPS);
        v.put("9F34", KONA_CVM_RESULTS);
        v.put("9F09", KONA_APP_VERSION);
        v.put("9F21", SDF_TAG_9A_HMS.format(new Date()));
        v.put("9F41", "00000000");
        return v;
    }

    /**
     * Merge every tag in a response into {@code into}, descending templates.
     *
     * <p>First value wins: a record read later must not quietly replace
     * something the GPO already established.</p>
     */
    private void collectTags(String bodyHex, java.util.Map<String, String> into) {
        if (bodyHex == null || bodyHex.isEmpty()) {
            return;
        }
        java.util.Map<String, String> t = com.hjq.demo.ans8583.Emv55.parse(bodyHex);
        if (t == null) {
            return;
        }
        for (java.util.Map.Entry<String, String> e : t.entrySet()) {
            String tag = e.getKey();
            String val = e.getValue();
            if (val == null || val.isEmpty()) {
                continue;
            }
            if ("6F".equals(tag) || "A5".equals(tag) || "BF0C".equals(tag)
                    || "61".equals(tag) || "77".equals(tag) || "70".equals(tag)) {
                collectTags(val, into);
                continue;
            }
            if (!into.containsKey(tag)) {
                into.put(tag, val);
            }
        }
    }

    /**
     * Pull the cryptogram out of a GENERATE AC response.
     *
     * <p>Two shapes are legal. Template 77 is ordinary TLV. Template 80 is
     * "format 1": the values run together with no tags at all, in a fixed
     * order - CID, ATC, the cryptogram, then whatever issuer application data
     * is left - and have to be cut by length.</p>
     */
    private void harvestGenerateAc(String bodyHex, java.util.Map<String, String> into) {
        if (bodyHex == null || bodyHex.length() < 4) {
            return;
        }
        String tag = bodyHex.substring(0, 2);
        if ("77".equals(tag)) {
            collectTags(bodyHex, into);
        } else if ("80".equals(tag)) {
            int len = Integer.parseInt(bodyHex.substring(2, 4), 16);
            String v = bodyHex.substring(4, Math.min(4 + len * 2, bodyHex.length()));
            if (v.length() >= 22) {
                into.put("9F27", v.substring(0, 2));
                into.put("9F36", v.substring(2, 6));
                into.put("9F26", v.substring(6, 22));
                if (v.length() > 22) {
                    into.put("9F10", v.substring(22));
                }
            }
        }
        Log.e(TAG, "KONA CL: after GENERATE AC 9F27=" + into.get("9F27")
                + " 9F36=" + into.get("9F36") + " 9F26=" + into.get("9F26")
                + " 9F10=" + into.get("9F10"));
    }

    /** One TLV, or nothing at all when there is no value. */
    private static void putTlv(StringBuilder sb, String tag, String value) {
        if (value == null) {
            return;
        }
        String v = value.replace(" ", "").toUpperCase(java.util.Locale.US);
        if (v.isEmpty()) {
            // A zero-length tag is not an empty answer, it is a malformed
            // message. Sending 9F26 00 and 9F36 00 is what the host rejected
            // with response code 30.
            return;
        }
        if ((v.length() % 2) != 0) {
            v = v + "0";
        }
        sb.append(tag)
                .append(String.format(java.util.Locale.US, "%02X", v.length() / 2))
                .append(v);
    }

    /**
     * DE55 for a KonaMoney contactless authorisation.
     *
     * <p>Tag order and content follow a capture of a terminal this host
     * accepts. Terminal-side values are the terminal's own; everything else -
     * AIP, the cryptogram, the ATC, the issuer application data - comes off
     * the card and is forwarded unaltered.</p>
     */
    private String buildKonaDe55(java.util.Map<String, String> cardTags, long amountMinor,
                                 String country, String currency, String yymmdd,
                                 String terminalRandom) {
        java.util.Map<String, String> c = (cardTags == null)
                ? new java.util.LinkedHashMap<String, String>() : cardTags;
        StringBuilder sb = new StringBuilder();
        putTlv(sb, "82", c.get("82"));
        putTlv(sb, "84", com.hjq.demo.ans8583.KonaMoney.AID);
        putTlv(sb, "95", KONA_TVR);
        putTlv(sb, "9A", yymmdd);
        putTlv(sb, "9C", "00");
        putTlv(sb, "5F2A", currency);
        putTlv(sb, "9F02", String.format(java.util.Locale.US, "%012d", amountMinor));
        putTlv(sb, "9F03", "000000000000");
        putTlv(sb, "9F09", KONA_APP_VERSION);
        putTlv(sb, "9F10", c.get("9F10"));
        putTlv(sb, "9F1A", country);
        putTlv(sb, "9F26", c.get("9F26"));
        putTlv(sb, "9F27", c.get("9F27"));
        putTlv(sb, "9F33", KONA_TERM_CAPS);
        putTlv(sb, "9F34", KONA_CVM_RESULTS);
        putTlv(sb, "9F35", "22");
        putTlv(sb, "9F36", c.get("9F36"));
        putTlv(sb, "9F37", terminalRandom);
        return sb.toString();
    }

    /** Tag 9F37: four unpredictable bytes, the same value at GPO and online. */
    private String konaTerminalRandom() {
        byte[] r = new byte[4];
        new java.security.SecureRandom().nextBytes(r);
        return AFUtils.showResult16Str(r).replace(" ", "");
    }

    /**
     * Finish a KonaMoney contactless sale: confirm the card, then go online.
     *
     * <p>The same shape as the QR and keyed paths. What differs is only where
     * the track came from.</p>
     */
    private void startKonaMoneyClTxn(String track2Hex,
                                     java.util.Map<String, String> cardTags,
                                     long amountMinor,
                                     String country, String currency,
                                     String yymmdd, String terminalRandom) {
        com.hjq.demo.ans8583.KonaMoney.Track2 t2 =
                com.hjq.demo.ans8583.KonaMoney.parseTrack2(track2Hex);
        if (!t2.ok()) {
            Log.e(TAG, "KONA CL: track 2 did not parse - " + t2.error);
            showUnsupportedCard("KONAMONEY");
            return;
        }
        Log.i(TAG, "KONA CL: " + t2);

        // The reader has the data it came for. Let the card go before a
        // dialog goes up, so the cardholder is not asked to hold a phone
        // against the terminal while someone reads a screen.
        stopFindCard = true;
        closeFindCard();

        isShowCardNo = true;
        currentCardNumber = t2.pan;
        tipCardNumber = t2.pan;
        cardValid = t2.expiry;
        transAmount = AFUtils.showResult16Str(
                AFUtils.hexString2ByteArray(AFUtils.dealMoneyStr(currentAmount)))
                .replace(" ", "");

        String de55 = buildKonaDe55(cardTags, amountMinor, country, currency,
                yymmdd, terminalRandom);
        Log.e(TAG, "KONA CL: DE55=" + de55);

        if (saleDataModel != null) {
            saleDataModel.setCardNo(t2.pan);
            saleDataModel.setCardValid(t2.expiry);
            saleDataModel.setTrack2data(t2.raw);
            saleDataModel.setCardAID(com.hjq.demo.ans8583.KonaMoney.AID);
            saleDataModel.setIcData(de55);
            saleDataModel.setTransAmount(transAmount);
        }

        showConfirmationDialog(tipCardNumber, new FindCardActivity.ConfirmationCallback() {
            @Override
            public void onResult(int result) {
                if (result != 0) {
                    return;
                }
                runOnlineTxn();
            }
        });
    }

    private void emvKernelProcess() {
        final int searchEpoch = mCardSearchQueue.generation();
        if (!searchAlive(searchEpoch)) return;
        saleDataModel = new SaleDataModel();
        // Per-kernel-run state. A retry after a failed read starts clean.
        //
        // currentCardNumber and tipCardNumber are STATIC, so without this the
        // previous card's number - from the last sale, or from the card that
        // just failed - is still there for the confirm screen and the PIN
        // block to fall back on if this card's read does not set it.
        currentCardNumber = "";
        tipCardNumber = "";
        mKernelCardConfirmed = false;
        mKernelRefusedCard = false;
        mKernelPinWait = false;
        mPinPadOpened = false;
        mCardRemoved = false;

        // A KonaMoney tap never reaches the kernel.
        //
        // The card asks for kernel 9F2A=02, the SDK resolves that to PURE, and
        // PURE rejects the data at STATE_S2 without ever sending a GPO. That
        // is a vendor problem and it is being chased separately. It is also
        // not one this terminal has to wait for: Kona's own terminal
        // specification describes the whole contactless transaction as four
        // APDUs in MSD mode - no offline authentication, no cardholder
        // verification by the card, no cryptogram - and the card service
        // exposes apiDalCscIsoCmd, which sends APDUs to the card directly.
        //
        // So the transaction is driven here instead. Every other card is
        // untouched: the probe below only takes over when the PPSE actually
        // names the KonaMoney AID, and returns false for anything else, which
        // falls straight through to the kernel exactly as before.
        if (tryKonaMoneyContactless()) {
            return;
        }

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
            // 0-no offline data authentication (virtual)
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
            map.put("forceOnlinePin", (cardType == CardSearchType.IC_CARD ? "0x01" : "0x00"));
            // 卡号确认，仅适用于EMV标准模式  0 -不执行卡号确认，1 -执行卡号确认
            //Card number confirmation, only applicable to EMV standard mode
            // 0 - do not perform card number confirmation, 1 - perform card number confirmation
            //
            // Contact chip: ON. The kernel then calls EmvCardNoConfirm, waits
            // for the operator's answer there, and a refusal (or a card pulled
            // out meanwhile) ends the kernel cleanly. With this OFF the only
            // place left to confirm was onEmvInputPIN - and a CANCEL there left
            // the kernel waiting for a PIN that never came: the "Secure Pinpad"
            // that popped up over the home screen after a cancel, the reader
            // that stayed held, and the app restart the next sale ran into.
            // Contactless stays OFF: the tap is long over by then.
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
            //map.put("merName", "0x30,0x31,0x32,0x33,0x34,0x35,0x36,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20,0x20");
            String tempMerName = AFUtils.padStringToBytes("Barkey", 40);
            Log.e(TAG, "merName Len : " + tempMerName.length());
            Log.e(TAG, "merName: " + AFUtils.showResult0xStr(AFUtils.StringToByteArray(tempMerName)));
            map.put("merName", AFUtils.showResult0xStr(AFUtils.StringToByteArray(tempMerName)));
            //交易流水号 n6  9F41
            //Transaction serial number n6 9F41
            //
            // This was hard-coded to 000001, so every single transaction this
            // terminal has ever sent carried 9F41 = 00000001 while the ATC
            // (9F36) climbed normally. A working terminal on the same card and
            // TID sent 9F41 = 00000766, tracking its transaction counter.
            //
            // 9F41 is not part of CDOL1, so it does not feed the ARQC and this
            // cannot break the cryptogram. It does feed issuer-side velocity and
            // duplicate checks, and a counter frozen at 1 across hundreds of
            // sales is exactly the shape of a replay.
            map.put("posSer", bcdTagValue(TerminalConfig.get(getActivity()).invoiceNo()));
            byte[] amount = AFUtils.hexString2ByteArray(AFUtils.dealMoneyStr(currentAmount));
            String tempAmount = AFUtils.showResult0xStr(amount);
            transAmount = AFUtils.showResult16Str(amount);
            Log.e(TAG, "tempAmount" + tempAmount);
            //交易金额 n12  9F02
            //Transaction amount n12 9F02
            map.put("transAmt", tempAmount);
            // 其他金额 n12  9F03
            //Other amount n12 9F03
            map.put("otherAmt", "0x00,0x00,0x00,0x00,0x00,0x00");
            // 交易货币代码 n3  5F2A
            //Transaction currency code n3 5F2A
            // 0050 = BDT (ISO 4217). This was 0156 (CNY), which put the wrong
            // currency into EMV tag 5F2A and made DBBL's host reject the sale.
            map.put("currencyCode", "0x00,0x50");
            byte[] date = AFUtils.hexString2ByteArray(SDF_TAG_9A_YYDD.format(new Date()));
            Log.e(TAG, "emvKernelProcess: date" + AFUtils.showResult0xStr(date));
            // 交易日期 n6  YYMMDD  9A
            //Transaction date n6 YYMMDD 9A
            map.put("transDate", AFUtils.showResult0xStr(date));
            byte[] time = AFUtils.hexString2ByteArray(SDF_TAG_9A_HMS.format(new Date()));
            // 交易时间 n6  hhmmss    9F21
            //Transaction time n6 hhmmss 9F21
            map.put("transTime", AFUtils.showResult0xStr(time));
            // 商户号 ans15  9F16
            //Merchant number ans15 9F16
            map.put("merId", AFUtils.showResult0xStr(AFUtils.StringToByteArray(String.format(java.util.Locale.US, "%-15.15s", TerminalConfig.get(getApplicationContext()).mid()))));
            // ç»ˆç«¯å· an8  9F1C
            //Terminal number an8 9F1C
            map.put("termId", AFUtils.showResult0xStr(AFUtils.StringToByteArray(String.format(java.util.Locale.US, "%-8.8s", TerminalConfig.get(getApplicationContext()).tid()))));
            // é™„åŠ æ•°æ®å®žé™…æœ‰æ•ˆé•¿åº¦ (total emvParamByte must be exactly 256 bytes: 96 bytes header + 160 bytes addition)
            byte[] additionByte = new byte[EMV_ADDITION_LEN];
            String strAddition;
            if (cardType == CardSearchType.IC_CARD) {
                map.put("additionLen", "0x24");
                strAddition = "FF6001009F3501229F3303E0F0C89F40056000F0E0019F1A0200505F2A020050DF690100";
            } else {
                map.put("additionLen", "0x24");
                strAddition = "FF6001009F3501229F3303E0F0C89F40056000F0E0019F1A0200505F2A020050DF690100";
            }
            // TTQ (9F66) belongs to the selected AID profile: Visa uses its own
            // 36000000 profile, while NexusPay uses B6000000. Never override all taps.
            byte[] tlvRaw = AFUtils.hexString2ByteArray(strAddition);
            System.arraycopy(tlvRaw, 0, additionByte, 0, tlvRaw.length);
            Log.i(TAG, "additionByte:  16Str " + AFUtils.showResult16Str(additionByte));
            Log.i(TAG, "additionByte: 0xStr" + AFUtils.showResult0xStr(additionByte));
            // é™„åŠ æ•°æ®ï¼Œé™„åŠ æ•°æ®ä»¥TLVæ ¼å¼å­˜åœ¨
            //Additional data, which exists in TLV format
            map.put("addition", AFUtils.showResult0xStr(additionByte));


            StringBuilder sbEmvParam = new StringBuilder();
            sbEmvParam.setLength(0);

            for (Map.Entry<String, String> itemTAG : map.entrySet()) {
                Log.i(TAG, "emvParam key->: " + itemTAG.getKey() + "\t data-->" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()) + "\t 字节数:" + AFUtils.removeAllHexPrefixes(itemTAG.getValue()).length() / 2);
                sbEmvParam.append(AFUtils.removeAllHexPrefixes(itemTAG.getValue()));
            }

            Log.e(TAG, "apiEmvKernelProcess Data--> " + sbEmvParam.toString());

            Log.e(TAG, "apiEmvKernelProcess Data Length--> " + sbEmvParam.toString().length() / 2);

            byte[] emvParamByte = AFUtils.hexString2ByteArray(sbEmvParam.toString());
            Log.e(TAG, "emvParam Len : " + emvParamByte.length);

            // Say so here rather than let the kernel refuse the call and leave
            // the operator looking at "card could not be read". If this ever
            // fires, one of the map fields above changed length.
            if (emvParamByte.length != EMV_PARAM_LEN) {
                Log.e(TAG, "emvParam is " + emvParamByte.length + " bytes, the kernel"
                        + " takes exactly " + EMV_PARAM_LEN + " - the call will be"
                        + " rejected. Header should be " + EMV_HEADER_LEN
                        + ", addition " + EMV_ADDITION_LEN + ".");
            }


            //User enters PIN
            mEmvBinderService.apiSDKEmvInPutConfirmCallBack(new ISdkKernelEmvInputPINCallback.Stub() {
                @Override
                public void onEmvInputPIN(byte ucPinType, byte[] pucPinBlock, byte ucLastTimes) throws RemoteException {
                  /*  ucPinType     - PIN类型   0 脱机PIN   1 联机PIN
                                    -PIN Type  0 Offline PIN 1 Online PIN
                    ucLastTimes     - PIN重试次数
                                    -PIN retry count
                    */
                    LogUtils.e("ucPinType：" + AFUtils.showResult0xStr(new byte[]{ucPinType}));
                    LogUtils.e("ucLastTimes：" + AFUtils.showResult0xStr(new byte[]{ucLastTimes}));

                    currentCardNumber = getPanFromKernelTags();
                    if (TextUtils.isEmpty(currentCardNumber) && !TextUtils.isEmpty(tipCardNumber)) {
                        currentCardNumber = tipCardNumber;
                    }
                    tipCardNumber = currentCardNumber;
                    if (TextUtils.isEmpty(cardValid)) {
                        cardValid = resolveCardExpiry();
                    }

                    LogUtils.i("onEmvInputPIN:currentCardNumber-> " + currentCardNumber);
                    LogUtils.i("CARDTIME onEmvInputPIN +" + sinceCardMs() + " ms after the card was detected");
                    final int pinType = ucPinType & 0xFF;
                    // From here the kernel is blocked inside apiEmvKernelProcess
                    // until the SDK PIN pad finishes. Only the pad can release
                    // it - see releaseKernelPinWait().
                    mKernelPinType = pinType;
                    mPinPadOpened = false;       // each PIN request is a new wait
                    mKernelPinWait = true;
                    if (mTxnAborted || isFinishing()) {
                        releaseKernelPinWait("transaction already ended");
                        return;
                    }
                    if (chipRemoved()) {
                        // Pulled out between the confirm screen and the PIN
                        // request. No pad for a card that is not there.
                        runOnUiThread(() -> abortCardRemoved("before PIN entry"));
                        return;
                    }
                    if (mKernelCardConfirmed) {
                        // The card was confirmed in EmvCardNoConfirm, where the
                        // kernel itself waits for the answer and a "no" ends it
                        // cleanly. Straight to the pad.
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                showPinPad(true, currentCardNumber, pinType);
                            }
                        });
                        return;
                    }
                    // The kernel did not ask for a card confirmation on this
                    // card. Confirm it here instead - but a "no" here leaves
                    // the kernel waiting for a PIN pad that will never open,
                    // which is what froze the reader after a cancel on this
                    // screen. abortTransaction() now releases that wait; see
                    // releaseKernelPinWait().
                    showConfirmationDialog(currentCardNumber,
                            new FindCardActivity.ConfirmationCallback() {
                                @Override
                                public void onResult(int result) {
                                    if (result != 0) {
                                        releaseKernelPinWait("card refused at the confirm screen");
                                        return;
                                    }
                                    if (chipRemoved()) {
                                        abortCardRemoved("at the confirm screen");
                                        return;
                                    }
                                    showPinPad(true, currentCardNumber, pinType);
                                }
                            });
                    // PIN输入回调：返回值  返回错误码：0成功；-4是超时退出；-1031是未输入PIN直接确认跳过了；-1029是直接取消
                }
            });

            //Setting system counters
            mEmvBinderService.apiSDKGetEmvSystemTimerCall(new IKernelEmvSystemTimerCallback.Stub() {
                @Override
                public long emvGetSysTimerCallFun() throws RemoteException {
                    // Called by the kernel many times per card read, from
                    // inside the read. It used to write two log lines (each
                    // walking the stack) per call; that time came straight
                    // out of the chip read.
                    return System.currentTimeMillis();
                }
            });
            mEmvBinderService.apiSetEmvCardNoConfirmCallBack(new IKernelEmvCallback.Stub() {
                //卡号确认回调
                //Card number confirmation callback
                @Override
                public int EmvCardNoConfirm(byte[] cadNo) throws RemoteException {
                    LogUtils.e("EmvCardNoConfirm  : " + AFUtils.showResult16Str(cadNo));
                    currentCardNumber = panFromKernel(cadNo);
                    if (currentCardNumber == null || currentCardNumber.isEmpty()) {
                        currentCardNumber = bestKnownPan("");
                    }
                    tipCardNumber = currentCardNumber;
                    LogUtils.i("CARDTIME EmvCardNoConfirm reached +" + sinceCardMs() + " ms after the card was detected");
                    if (mTxnAborted || isFinishing()) {
                        return -1;
                    }
                    // Range and EMI checks HERE, while a "no" still ends the
                    // kernel cleanly. Made at the PIN request instead, the
                    // refusal left the kernel waiting for a PIN, and freeing it
                    // is what put the system "Secure Pinpad" on the screen
                    // after an EMI card was refused.
                    if (rejectUnsupportedCard(currentCardNumber)) {
                        mKernelRefusedCard = true;
                        LogUtils.e("EmvCardNoConfirm: card refused by the range/EMI check"
                                + " - returning -1 so the kernel ends without a PIN step");
                        return -1;
                    }
                    if (cardType != CardSearchType.IC_CARD) {
                        // Contactless: the tap is over in a fraction of a
                        // second and the card is confirmed after the kernel
                        // (startTrans). Holding the kernel here would ask the
                        // cardholder to keep the card in the field.
                        LogUtils.e("EmvCardNoConfirm auto-confirmed PAN (contactless): " + currentCardNumber);
                        return 0;
                    }
                    // Contact chip: confirm the card HERE, where Anfu's demo does
                    // it. The kernel waits for this return value, and a non-zero
                    // answer ends the kernel cleanly. Asking later, inside
                    // onEmvInputPIN, left the kernel waiting for a PIN pad that a
                    // "no" never opened - the frozen reader in the video.
                    if (mTxnAborted || isFinishing()) {
                        return -1;
                    }
                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] answer = {-1};
                    showConfirmationDialog(currentCardNumber,
                            new FindCardActivity.ConfirmationCallback() {
                                @Override
                                public void onResult(int result) {
                                    answer[0] = result == 0 ? 0 : -1;
                                    latch.countDown();
                                }
                            });
                    boolean answered = awaitKernelAnswer(latch, "EmvCardNoConfirm");
                    int ret = answered ? answer[0] : -1;
                    if (ret == 0 && chipRemoved()) {
                        // Confirmed, but the card is no longer in the slot.
                        // Refusing here ends the kernel cleanly; going on
                        // would put up a PIN pad for a card that is gone.
                        ret = -1;
                        runOnUiThread(() -> abortCardRemoved("at the confirm screen"));
                    }
                    if (ret == 0 && !mExpiryWarningConfirmed && isExpiredCardCheckNeeded()) {
                        // The expired-card question, asked while the kernel
                        // can still be told "no". CANCEL on it ends the
                        // transaction (showExpiredCardDialog), which the
                        // wait below sees as "no answer" -> -1.
                        LogUtils.e("EmvCardNoConfirm: card is expired - asking before the PIN step");
                        final CountDownLatch expLatch = new CountDownLatch(1);
                        dismissCardReading();
                        showExpiredCardDialog(expLatch::countDown);
                        if (!awaitKernelAnswer(expLatch, "EmvCardNoConfirm expiry")) {
                            ret = -1;
                            mKernelRefusedCard = true;
                        }
                    }
                    mKernelCardConfirmed = ret == 0;
                    LogUtils.e("EmvCardNoConfirm: operator " + (ret == 0 ? "confirmed" : "refused")
                            + " the card - returning " + ret + " to the kernel");
                    return ret;
                }
            });

            /**
             * Cardholder ID verification(持卡人认证确认)
             * 注意：只有PBOC的才有持卡人认证确认
             */
            mEmvBinderService.apiSetEmvLicenseConfirmCallBack(new IKernelEmvLicenseCallback.Stub() {
                @Override
                public int EmvLicenseConfirm(byte uclicenseType, byte[] pszlicense) throws RemoteException {
                    LogUtils.e("IKernelEmvLicenseCallback ucLicenseType：" + AFUtils.showResult0xStr(new byte[]{uclicenseType}));
                    LogUtils.e("IKernelEmvLicenseCallback pszLicense：" + AFUtils.hexToAscii(AFUtils.showResult16Str(pszlicense)));

                    String strUclicenseType = AFUtils.showResult0xStr(new byte[]{uclicenseType}).trim();
                    String strLicenseValue = AFUtils.hexToAscii(AFUtils.showResult16Str(pszlicense));
                    StringBuilder strLicencesBuilder = new StringBuilder();
                    if ("0x00".equals(strUclicenseType)) {  //    0x00 身份证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Id_Card));
                    } else if ("0x01".equals(strUclicenseType)) { //    0x01 军官证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Officer_Certificate));
                    } else if ("0x02".equals(strUclicenseType)) {  //    0x02 护照
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Passport));

                    } else if ("0x03".equals(strUclicenseType)) {  //    0x03 入境证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_EntryPermit));

                    } else if ("0x04".equals(strUclicenseType)) {  //    0x04 临时身份证
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_TemporaryIDcard));

                    } else if ("0x05".equals(strUclicenseType)) {  //    0x05其他
                        strLicencesBuilder.append(getResources().getString(R.string.license_check_Others));
                    }

                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] resultHolder = new int[1];

                    strLicencesBuilder.append(strLicenseValue);
                    showLicenceSelectedConfirmDialog(strLicencesBuilder.toString(), new FindCardActivity.LicenceSeclectedCallback() {
                        @Override
                        public void onResult(int result) {
                            resultHolder[0] = result;
                            latch.countDown();
                        }
                    });

                    if (!awaitKernelAnswer(latch, "EmvLicenseConfirm")) {
                        return -1;
                    }
                    return resultHolder[0];
                }
            });

            // 应用选择 Application Selection(弹框显示AID，供选择)
            mEmvBinderService.apiSetEmvAppConfirmCallBack(new ISdkKernelEmvAppCallback.Stub() {
                @Override
                public int onEmvAppConfirm(byte ucAppNum, byte[] pucAppData, int uiDataLen, byte[] pucIndex) throws RemoteException {
                    ArrayList<TLVModel> tlvModelArrayList = AFUtils.praseTLVStrX(pucAppData);
                    for (int i = 0; i < tlvModelArrayList.size(); i++) {
                        LogUtils.e("tlvModelArrayList : " + tlvModelArrayList.get(i));
                    }
                    final CountDownLatch latch = new CountDownLatch(1);
                    final int[] resultHolder = new int[1];
                    showApplicationSelectedDialog("AID  ", tlvModelArrayList, new FindCardActivity.ApplicationSeclectedCallback() {
                        @Override
                        public void onResult(int result) {
                            byte[] tempPucIndex = IntToBytesUtils.toLittleEndian(result);
                            for (int i = 0; i < tempPucIndex.length; i++) {
                                pucIndex[i] = tempPucIndex[i];
                            }
                            resultHolder[0] = result;
                            latch.countDown();
                        }
                    });

                    if (!awaitKernelAnswer(latch, "onEmvAppConfirm")) {
                        return -1;
                    }
                    return resultHolder[0];
                }
            });

            //screen display
            mEmvBinderService.apiSDKSetEmvlcdPrintfExCallFun(new IKernelEmvlcdPrintExCallback.Stub() {
                @Override
                public int emvlcdPrintfExCallFun(byte column, byte row, byte mode, byte ucMsgID, byte[] ucDisStr) throws RemoteException {
                    LogUtils.e("emvlcdPrintfExCallFun: " + AFUtils.hexToAscii(AFUtils.showResult16Str(ucDisStr)) + "\n");
                    return 0;
                }

               /* @Override
                public int emvlcdPrintfExCallFun(byte column, byte row, byte mode, byte[] ucDisStr) throws RemoteException {
                    return 0;
                }*/
            });

            int kernelProcessResult = mEmvBinderService.apiEmvKernelProcess(emvParamByte);
            if (!searchAlive(searchEpoch)) {
                Log.w(TAG, "kernel returned " + kernelProcessResult
                        + " after this transaction had ended - reader released");
                return;
            }
            Log.e(TAG, "kernelProcessResult-------> " + kernelProcessResult);
            Log.i(TAG, "CARDTIME kernel returned +" + sinceCardMs() + " ms after the card was detected");
            if (kernelProcessResult != 0 && mKernelRefusedCard) {
                // Refused in EmvCardNoConfirm; that screen ends the sale.
                Log.i(TAG, "kernel ended after the card was refused at confirm - nothing more to do");
                return;
            }

            if (kernelProcessResult != 0) {
                // "failed" was the wrong word for every one of these. Two of
                // them are the kernel reporting on a transaction it actually
                // ran, and one of those is a completed sale.
                Log.e(TAG, "apiEmvKernelProcess returned " + kernelProcessResult);

                // ERR_KERNEL_EXIT is how the contactless kernels sign off, and
                // it is checked before anything is torn down. See
                // EMV_ERR_KERNEL_EXIT: the kernel has run the whole
                // transaction and is telling the terminal to come and collect
                // the result. Ask for it, and if it is there, finish the sale
                // exactly the way the contact path finishes - same tag
                // harvest, same online authorisation, same receipt.
                if (kernelProcessResult == EMV_ERR_KERNEL_EXIT) {
                    EmvGetKernelProgressResult exitResult = fetchKernelProgressResult();
                    if (exitResult != null) {
                        // A published result is not the same as a completed
                        // sale. ERR_KERNEL_EXIT is how the kernel signs off
                        // both after it has produced a cryptogram and after it
                        // has given up on finding an application at all, and
                        // it publishes a result in both cases. So say what the
                        // status actually is here, and let the result path -
                        // which checks the status and the tag space before it
                        // commits to anything - decide what it means.
                        byte[] exitData = exitResult.getTagData();
                        String exitStatus = (exitData != null && exitData.length > 1)
                                ? AFUtils.showResult16Str(new byte[]{exitData[1]})
                                : "unknown";
                        Log.e(TAG, "kernel exited (ERR_KERNEL_EXIT) with a published"
                                + " result, status 0x" + exitStatus + " - handing it to"
                                + " the result path rather than counting the tap as a miss");
                        mPendingKernelResult = exitResult;
                        apiEmvGetKernelProcessResult(0);
                        return;
                    }
                    Log.e(TAG, "kernel exited (ERR_KERNEL_EXIT) with no result published"
                            + " - treating this one as a miss");
                }

                // ERR_OTHER_CARD is the card declining to present a payment
                // application at all. See EMV_ERR_OTHER_CARD - it is a state
                // the cardholder can fix, and only if we tell them which one.
                if (kernelProcessResult == EMV_ERR_OTHER_CARD) {
                    Log.e(TAG, "card answered but offered no payment directory"
                            + " (ERR_OTHER_CARD) - the application is not being"
                            + " presented; see the PPSE response code above");
                    closeFindCard();
                    stopFindCard = false;
                    retrySearchCard(enabledSearchMask(),
                            "Card not ready - open your wallet app, then tap again");
                    return;
                }

                // ERR_KERNEL_RESTART means the card was in the field and left
                // it: an APDU went unanswered mid-selection. Re-arming is the
                // right move, but the cardholder needs to be told the thing
                // that actually helps.
                if (kernelProcessResult == EMV_ERR_KERNEL_RESTART) {
                    Log.e(TAG, "card left the field mid-transaction"
                            + " (ERR_KERNEL_RESTART) - re-arming and asking the"
                            + " cardholder to hold the card still");
                    closeFindCard();
                    stopFindCard = false;
                    retrySearchCard(enabledSearchMask(),
                            "Hold your card on the reader until it beeps");
                    return;
                }
                // This was startSearchCard(), which resets the attempt
                // counter to zero every time. A card the kernel refuses
                // outright - a wallet whose 9F2A names a kernel this build
                // does not carry, for one - fails identically on every
                // attempt, so the terminal re-armed, saw the same card still
                // sitting in the field, failed again, and went round without
                // any limit. The reader was opened and closed as fast as the
                // SDK would allow it, and a card service driven like that
                // stops answering: after it does, no card of any kind reads
                // until the terminal is restarted. retrySearchCard is the
                // retry path waits for the operator before another presentation.
                closeFindCard();
                stopFindCard = false;

                // "Use another interface", not "try again". Re-arming
                // contactless here asks the cardholder to repeat the one
                // thing that cannot work, and on a card that always fails
                // this way that is every attempt. Drop contactless from the
                // mask and ask for chip or stripe instead, which is what the
                // kernel is telling the terminal to do. Once only - if the
                // contact interface fails too, the ordinary bounded retry
                // takes it from there.
                if (kernelProcessResult == EMV_ERR_OTHER_INTERFACE && cardType == CardSearchType.RF_CARD && !mCtlsFellBack) {
                    mCtlsFellBack = true;
                    final int contactMask = enabledSearchMask() & ~CardSearchType.RF_CARD;
                    // Name the card. Two different products failing the same
                    // way look identical from the toast alone, and that is how
                    // a wallet tap and a bank card tap end up being reported
                    // as one fault.
                    String refusedAid = "";
                    try {
                        refusedAid = getTagValue(0x4F);
                    } catch (Exception ignore) {
                        // The kernel space may already be torn down.
                    }
                    Log.e(TAG, "kernel ended the contactless application"
                            + " (ERR_OTHER_INTERFACE): this build has no kernel for"
                            + " the card's 9F2A. AID="
                            + (TextUtils.isEmpty(refusedAid) ? "unknown" : refusedAid)
                            + ". Falling back to mask=" + contactMask);
                    if (contactMask != 0) {
                        toast("Contactless not available for this card - please insert or swipe");
                        speak("Please insert or swipe your card");
                        mCardReadAttempt = 0;
                        startSearchCardInternal(contactMask, true);
                        return;
                    }
                    toast("Contactless not available for this card");
                    canCleTransaction();
                    mHandler.sendEmptyMessage(8004);
                    return;
                }

                // Say that the card was READ and REFUSED, rather than falling
                // back to "please present your card again".
                //
                // Those two are not the same event and they were impossible to
                // tell apart: a card that is never detected and a card the
                // kernel runs and rejects both ended here, both re-armed the
                // search, and both showed the same prompt. From the counter it
                // looks like the reader is broken, which sent us hunting
                // through reader parameters for a fault that was a refusal.
                String refusedAt = "";
                try {
                    refusedAt = getTagValue(0x4F);
                } catch (Exception ignore) {
                    // kernel space may already be gone
                }
                Log.e(TAG, "CARD WAS READ AND REFUSED BY THE KERNEL."
                        + " apiEmvKernelProcess=" + kernelProcessResult
                        + " interface=" + (cardType == CardSearchType.RF_CARD
                                ? "contactless" : "contact")
                        + " amount=" + currentAmount
                        + " AID=" + (TextUtils.isEmpty(refusedAt) ? "unknown" : refusedAt)
                        + " - this is NOT a missed tap. Check the AID limits"
                        + " (DF19/DF20/DF21), DF18 online-PIN capability and the"
                        + " CA public key for this AID.");
                toast("Card read but not accepted (code " + kernelProcessResult + ")");
                retrySearchCard(enabledSearchMask(),
                        "Card not accepted - present again or insert");
                return;
            }

            apiEmvGetKernelProcessResult(0);

            //用户输入密码/免密/非接交易.继续执行获取EMV执行结果
            //User enters password/password-free/contactless transaction. Continue to execute and obtain EMV execution result
          /*  if (inputPinResult == -1 || inputPinResult == -1031 || cardType == 1) {
                //Contactless card execution results
                Log.e(TAG, "start apiEmvGetKernelProcessResult... ");

            } else {
                closeFindCard();
                Log.e(TAG, "emvKernelProcess: PIN pad Result -->" + inputPinResult);
            }*/


        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public interface ConfirmationCallback {
        void onResult(int result);
    }

    public interface ApplicationSeclectedCallback {
        void onResult(int result);
    }

    public interface LicenceSeclectedCallback {
        void onResult(int result);
    }

    public void showLicenceSelectedConfirmDialog(String ucLicenseTypePszLicense, FindCardActivity.LicenceSeclectedCallback callback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disDialog();
                new MessageDialog.Builder(getActivity())
                        .setTitle(R.string.card_number_confirmation_license)
                        .setMessage(TextUtils.isEmpty(ucLicenseTypePszLicense) ? "Licence Number Error" : ucLicenseTypePszLicense)
                        .setConfirm(keyboardDevice ? getString(R.string.common_cancel) : getString(R.string.common_confirm))
                        .setCancel(keyboardDevice ? getString(R.string.common_confirm) : getString(R.string.common_cancel))
                        .setCanceledOnTouchOutside(false)
                        .setListener(new MessageDialog.OnListener() {

                            @Override
                            public void onConfirm(BaseDialog dialog) {
                                dialog.dismiss();
                                if (keyboardDevice) {
                                    toast(getString(R.string.trans_canceled));
                                    callback.onResult(-1);
                                } else {
                                    if (TextUtils.isEmpty(tipCardNumber)) {
                                        callback.onResult(-1);
                                    } else {
                                        callback.onResult(0);
                                    }
                                }
                            }

                            @Override
                            public void onCancel(BaseDialog dialog) {
                                if (keyboardDevice) {
                                    if (TextUtils.isEmpty(tipCardNumber)) {
                                        callback.onResult(-1);
                                        canCleTransaction();
                                    } else {
                                        callback.onResult(0);
                                    }
                                } else {
                                    toast(getString(R.string.trans_canceled));
                                    callback.onResult(-1);
                                }

                            }
                        })
                        .show();
            }
        });
    }

    public void showApplicationSelectedDialog(String dialogType, ArrayList<TLVModel> tlvModelArrayList, FindCardActivity.ApplicationSeclectedCallback callback) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AidChoiceDialog.showDialog(FindCardActivity.this, dialogType, tlvModelArrayList,
                        new AidChoiceDialog.OnItemSelectedListener() {
                            @Override
                            public void onItemSelected(int selectedIndex) {
                                LogUtils.e("selectedIndex  position : " + selectedIndex);
                                callback.onResult(selectedIndex);
                            }
                        });
            }
        });
    }

    // ------------------------------------------------- card number decoding

    /**
     * The card number out of whatever the kernel handed over.
     *
     * <p>The EMV kernel returns the PAN as raw bytes and the encoding is not
     * fixed. Some kernels send the digits as ASCII text; others send them
     * BCD-packed, two digits to a byte. This callback decoded as ASCII
     * unconditionally, so a BCD PAN came back as mojibake, every character was
     * dropped as non-numeric, and the confirmation dialog was handed an empty
     * string - which is exactly what "CARD NUMBER ERROR" on that dialog
     * was reporting.</p>
     *
     * <p>Both readings are taken and the plausible one wins. The two cannot
     * both be plausible: a card number is 12 to 19 digits (ISO/IEC 7812), and
     * reading an ASCII PAN as BCD doubles its length to 24-38 while reading a
     * BCD PAN as ASCII collapses it to a handful of stray printable
     * characters. If neither reads as a card number the longer of the two is
     * returned, so the log and the screen show what actually arrived instead
     * of a blank.</p>
     */
    static String panFromKernel(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        String hex = AFUtils.showResult16Str(raw);
        // Reading one: the bytes are ASCII text.
        String ascii = digitsOnly(AFUtils.filterNonAscii(AFUtils.hexToAscii(hex)));
        // Reading two: the bytes are BCD, so the hex dump is already the
        // digits, with F nibbles padding the tail.
        String bcd = digitsOnly(stripPadF(hex));
        if (isPlausiblePan(ascii)) {
            return ascii;
        }
        if (isPlausiblePan(bcd)) {
            return bcd;
        }
        LogUtils.e("panFromKernel: neither reading is a card number"
                + " (ascii=" + ascii.length() + " digits, bcd=" + bcd.length() + " digits)");
        return ascii.length() >= bcd.length() ? ascii : bcd;
    }

    /**
     * Drop the run of F nibbles that pads a BCD field out to its length.
     *
     * <p>Not {@code substring(0, lastIndexOf("F"))}, which is what this was.
     * That cuts before the last F and leaves every earlier one attached to the
     * number, so a 16-digit PAN in a 10-byte field came out as
     * {@code ...5908FFF}.</p>
     */
    static String stripPadF(String s) {
        return s == null ? "" : s.replaceAll("[Ff]+$", "");
    }

    /** 12 to 19 digits, per ISO/IEC 7812. */
    private static boolean isPlausiblePan(String s) {
        return s != null && s.length() >= 12 && s.length() <= 19;
    }

    private static String panFromTrack1(String track1) {
        if (track1 == null || track1.isEmpty()) {
            return "";
        }
        String t = track1.trim();
        if (t.startsWith("B") || t.startsWith("b")) {
            t = t.substring(1);
        }
        int caret = t.indexOf('^');
        String raw = caret >= 0 ? t.substring(0, caret) : t;
        return digitsOnly(stripPadF(raw));
    }

    private String getPanFromKernelTags() {
        String pan = getTagValue(0x5A);
        if (pan != null && !pan.isEmpty()) {
            pan = digitsOnly(stripPadF(pan));
            if (isPlausiblePan(pan)) {
                return pan;
            }
        }
        String t2 = getTagValue(0x57);
        if (t2 != null && !t2.isEmpty()) {
            t2 = t2.toUpperCase(java.util.Locale.US);
            int sep = t2.indexOf('D');
            if (sep < 0) sep = t2.indexOf('=');
            if (sep > 0) {
                String p = digitsOnly(t2.substring(0, sep));
                if (isPlausiblePan(p)) {
                    return p;
                }
            }
        }
        String t2cl = getTagValue(0x9F6B);
        if (t2cl != null && !t2cl.isEmpty()) {
            t2cl = t2cl.toUpperCase(java.util.Locale.US);
            int sep = t2cl.indexOf('D');
            if (sep < 0) sep = t2cl.indexOf('=');
            if (sep > 0) {
                String p = digitsOnly(t2cl.substring(0, sep));
                if (isPlausiblePan(p)) {
                    return p;
                }
            }
        }
        String t1 = getTagValue(0x56);
        if (t1 != null && !t1.isEmpty()) {
            String p = panFromTrack1(t1);
            if (isPlausiblePan(p)) {
                return p;
            }
        }
        return "";
    }

    /**
     * The best card number this transaction knows about.
     *
     * <p>The confirmation dialog is asked to show a number that has come down
     * one of five paths - chip, contactless, swipe, keyed, QR - and any of them
     * can hand over a blank if the read went wrong. Rather than draw a card
     * with an error where the number goes, look at the other places this
     * transaction has already recorded the PAN.</p>
     */
    private String bestKnownPan(String preferred) {
        String p = digitsOnly(stripPadF(preferred));
        if (!p.isEmpty()) {
            return p;
        }
        p = digitsOnly(stripPadF(currentCardNumber));
        if (!p.isEmpty()) {
            return p;
        }
        if (saleDataModel != null) {
            p = digitsOnly(stripPadF(saleDataModel.getCardNo()));
            if (!p.isEmpty()) {
                return p;
            }
            String t2 = saleDataModel.getTrack2data();
            if (t2 != null && t2.contains("=")) {
                p = digitsOnly(t2.substring(0, t2.indexOf('=')));
                if (!p.isEmpty()) {
                    return p;
                }
            }
        }
        return "";
    }

    /**
     * The transaction screen does not close itself.
     *
     * <p>On this screen a long silence means the terminal is busy, not that it
     * has been abandoned: the card reader is polling, or the host has the
     * message and has not answered yet. Cutting either short would leave a
     * transaction the batch and the host disagree about.</p>
     *
     * <p>What the operator can actually get stuck on here is the card
     * confirmation dialog, and that counts itself down - see
     * {@link com.hjq.demo.ui.dialog.CardConfirmDialog}. Card search already has
     * its own timeout in the kernel.</p>
     */
    @Override
    protected boolean idleTimeoutEnabled() {
        return false;
    }

    /**
     * Ask the operator to confirm the card, by drawing the card.
     *
     * <p>This was a message box whose body was {@code tipCardNumber} - the raw
     * PAN, unmasked. Every slip, report and log in this app goes through
     * {@link CardMask}; this dialog was the one screen that did not, and it is
     * the one held up at the counter with a customer standing in front of it.
     * {@link com.hjq.demo.ui.dialog.CardConfirmDialog} masks it to the
     * parameter and lays the number out like the card it is asking about.</p>
     *
     * <p>The keyboard-device caption swap went with it. On those units the
     * confirm and cancel captions were exchanged so the physical green key
     * landed on the logical confirm - which meant the on-screen button reading
     * "CANCEL" was the one that went ahead with the transaction. The buttons
     * are now coloured and positioned, red left and green right, and mean what
     * they say on every unit.</p>
     */
    public void showConfirmationDialog(String tipCardNumber, FindCardActivity.ConfirmationCallback callback) {
        final String pan = bestKnownPan(tipCardNumber);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                disDialog();
                // Nothing to confirm. Drawing a card with an error message
                // where the number goes asks the operator to approve a card
                // this terminal cannot identify, and the only honest answers
                // to that question are no and no. End it here instead.
                if (pan.isEmpty()) {
                    LogUtils.e("card confirmation: no card number on any path"
                            + " - tip=" + tipCardNumber
                            + " current=" + currentCardNumber);
                    toast(getString(R.string.card_number_read_error));
                    canCleTransaction();
                    callback.onResult(-1);
                    return;
                }
                com.hjq.demo.ui.dialog.CardConfirmDialog.show(FindCardActivity.this,
                        pan, cardValid, mEntry,
                        TerminalConfig.get(FindCardActivity.this).idleTimeoutSec(),
                        new com.hjq.demo.ui.dialog.CardConfirmDialog.OnResult() {
                            @Override
                            public void onResult(int result) {
                                if (result != 0) {
                                    toast(getString(R.string.trans_canceled));
                                    canCleTransaction();
                                    callback.onResult(-1);
                                    return;
                                }
                                callback.onResult(0);
                            }
                        });
            }
        });
    }

    /**
     * Ask the SDK for the kernel's result, with a little patience.
     *
     * <p>A null is the service saying it has nothing yet, and on contactless
     * that is usually a race: the tap is over in a few hundred milliseconds
     * and the fetch can land before the result has been published. Ask again
     * before concluding there is nothing there.</p>
     *
     * <p>Returns whatever was parked in {@link #mPendingKernelResult} first.
     * The ERR_KERNEL_EXIT path fetches the result to find out whether the
     * kernel completed, and on SDK builds where the read is one-shot a second
     * fetch would come back empty.</p>
     *
     * @return the result, or null if the kernel published none.
     */
    private EmvGetKernelProgressResult fetchKernelProgressResult() throws RemoteException {
        if (mPendingKernelResult != null) {
            EmvGetKernelProgressResult parked = mPendingKernelResult;
            mPendingKernelResult = null;
            Log.i(TAG, "fetchKernelProgressResult: using the result already collected");
            return parked;
        }
        EmvGetKernelProgressResult result = null;
        for (int attempt = 1; attempt <= KERNEL_RESULT_FETCHES; attempt++) {
            result = mEmvBinderService.emvGetKernelProgressResult();
            if (result != null) {
                if (attempt > 1) {
                    Log.e(TAG, "emvGetKernelProgressResult arrived on attempt " + attempt);
                }
                break;
            }
            Log.e(TAG, "emvGetKernelProgressResult returned null (attempt "
                    + attempt + "/" + KERNEL_RESULT_FETCHES + ")");
            if (attempt < KERNEL_RESULT_FETCHES
                    && android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                try {
                    Thread.sleep(KERNEL_RESULT_RETRY_MS);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return result;
    }

    /**
     * 是否需要发起联机交易
     * Check whether a connection transaction needs to be initiated
     *
     * @param type 0:GAC1 1: GAC2
     */
    private boolean retryFailedChipRead(int stage, String reason) {
        if (!com.hjq.demo.utils.ChipFallbackPolicy.retryIncompleteKernelRead(
                stage, cardType, mHostResult != null || mFinancialStarted.get(), mTxnAborted)) return false;
        closeFindCard();
        stopFindCard = false;
        retrySearchCard(enabledSearchMask(), reason + " - remove and insert the card again");
        return true;
    }

    private void apiEmvGetKernelProcessResult(int type) {
        try {

            Log.i(TAG, "apiEmvGetKernelProcessResult: ----->" + (type == 0 ? "===GAC1===" : "===GAC2==="));

            disDialog();


            EmvGetKernelProgressResult emvGetKernelProgressResult = fetchKernelProgressResult();

            if (emvGetKernelProgressResult == null) {
                if (retryFailedChipRead(type, "Chip returned no read result")) return;
                // Say which read this was. Without it the toast is the only
                // evidence and it names neither the interface nor the stage,
                // so a contactless GAC1 failure and a chip GAC2 failure look
                // identical from the outside.
                Log.e(TAG, "apiEmvGetKernelProcessResult: the kernel published no result"
                        + " - stage=" + (type == 0 ? "GAC1" : "GAC2")
                        + " cardType=" + cardType
                        + " (" + (cardType == CardSearchType.RF_CARD ? "contactless"
                                : cardType == CardSearchType.IC_CARD ? "chip" : "stripe") + ")"
                        + " entry=" + mEntry
                        + " pan=" + (TextUtils.isEmpty(tipCardNumber) ? "none" : "present"));
                toast("The kernel returns an abnormal result...");
                // Was a bare return, which left the screen up with the reader
                // shut and no way forward but the back key. End it properly so
                // the operator can start again.
                canCleTransaction();
                mHandler.sendEmptyMessage(8004);
                return;
            }

            int ret = emvGetKernelProgressResult.getRet();
            byte[] resultData = emvGetKernelProgressResult.getTagData();
            Log.i(TAG, "EmvCompletion ret :" + ret);
            if (ret != 0 || resultData == null || resultData.length < 2) {
                if (retryFailedChipRead(type, "Chip read result incomplete")) return;
                toast("Card read result unavailable");
                canCleTransaction();
                return;
            }

            Log.i(TAG, "EmvCompletion bOutData :" + AFUtils.showResult0xStr(resultData));
            for (int i = 0; i < resultData.length; i++) {
                Log.i(TAG, "EmvCompletion : ITEM ->" + i + "\t" + AFUtils.showResult16Str(new byte[]{resultData[i]}));
            }
            String ucStatus = AFUtils.showResult16Str(new byte[]{resultData[1]});
            Log.i(TAG, "Kernel processing results:" + ucStatus);

            // Byte 3 is the kernel's CVM verdict, and it was being thrown away.
            //
            //   00 no CVM required   10 signature   20 ONLINE PIN
            //   30 confirmation code   F0 not applicable
            //
            // For a tap this is the authoritative answer: the kernel has the
            // card's CVM list, the TTQ and the Reader CVM Required Limit from
            // the AID record, and it applies the scheme's rules to all three.
            // The app was instead comparing the amount to its own parameter and
            // ignoring the kernel entirely, which is how a contactless sale
            // over the limit went through with no PIN.
            if (resultData.length > 3) {
                mKernelCvm = AFUtils.showResult16Str(new byte[]{resultData[3]});
                Log.i(TAG, "Kernel CVM verdict (ucCvm): " + mKernelCvm
                        + " (" + kernelCvmName(mKernelCvm) + ")");
            } else {
                mKernelCvm = "";
            }

            logOfflineDataAuth(type);

            // 0x20 = the card declined offline (it generated an AAC). Going
            // online with an AAC can only ever be rejected by the host, so
            // strictly the terminal should stop here.
            //
            // That is gated behind a setting and OFF by default, because while
            // the CA public key problem is still being sorted out, blocking
            // here stops the transaction reaching the host at all and leaves
            // nothing to diagnose from. With it off we log loudly and carry on,
            // which is the behaviour that produced the '**' captures.
            if ("20".equals(ucStatus)) {
                Log.e(TAG, "GAC" + (type + 1) + " returned DECLINED (0x20)"
                        + " - the card generated an AAC. This transaction cannot be"
                        + " approved; load the correct CA public key for this card.");
                if (TerminalConfig.get(getActivity()).blockOfflineDecline()) {
                    mCardDeclined = true;
                    mRespText = "CARD DECLINED - OFFLINE AUTH FAILED";
                    KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                    toast("Card declined - offline authentication failed");
                    canCleTransaction();
                    return;
                }
            }

            /*if (!"30".equals(ucStatus)) {
                closeFindCard();
            }*/

            if ("40".equals(ucStatus)) {
                // GAC2 is reporting on a transaction that already ran and
                // already has its card data. Nothing to check.
                if (type == 1) {
                    LogUtils.i("Kernel status is 0x40 at GAC2 - proceeding to startTrans()");
                    startTrans();
                    return;
                }
                // Look for card data first - contactless included.
                //
                // This used to send RF_CARD straight to startTrans() without
                // looking, on the assumption that 0x40 after a tap meant an
                // MSD read that had simply not filled in the usual tags.
                // That is one thing 0x40 means. It is not the only one.
                // 0x40 is the kernel ending the application, and it ends the
                // application just as readily when it never found one: a
                // PPSE that answers 6A82, an explicit AID list where every
                // SELECT is refused. The kernel publishes a result either
                // way, and only one of those results has a card in it.
                //
                // Taking the shortcut meant an empty read was announced as
                // "proceeding to startTrans()", walked one step further into
                // the sale, and died there on a missing PAN - with the log
                // line claiming success sitting just above the failure.
                String tag5A = getTagValue(0x5A);
                String tag57 = getTagValue(0x57);
                String tag9F6B = getTagValue(0x9F6B);
                if (!tag5A.isEmpty() || !tag57.isEmpty() || !tag9F6B.isEmpty()) {
                    LogUtils.i("Kernel status is 0x40 with card data present"
                            + " (MSD or equivalent) - proceeding to startTrans()");
                    startTrans();
                    return;
                }
                if (cardType == CardSearchType.RF_CARD) {
                    // Nothing was read. Say so here, where the kernel's own
                    // result block is still in hand - its trailing status word
                    // is the difference between "this card carries no payment
                    // application" and "the card stopped answering", and those
                    // are two different things to tell the cardholder.
                    Log.e(TAG, "kernel ended the contactless application with no"
                            + " card data (status 0x40). Kernel result block: "
                            + AFUtils.showResult16Str(resultData)
                            + " - byte 1 is the kernel's own error code, bytes 9-10"
                            + " the last status word from the card. 6A82 there means the"
                            + " card offered no payment application; 0000 means the kernel"
                            + " rejected the data it already had before asking for more."
                            + " Nothing was read; re-arming.");
                    stopFindCard = false;
                    retrySearchCard(enabledSearchMask(),
                            "Card not readable - try again or use another card");
                    return;
                }
                if (retryFailedChipRead(type, "Chip application could not be read")) return;
                KEYCODE_ENTER_ACTION = KEYCODE_ENTER_START_SEARCH_CARD;
                getKeyCode();
                if (type == 0) {
                    toast(getString(R.string.trans_canceled));
                } else {
                    toast(getString(R.string.gac1_end_application));
                }
                canCleTransaction();
            } else {
                switch (type) {
                    case 0:
                        //IC
                        startTrans();
                        break;
                    case 1:
                        startTrans();
                        //RF
                        break;
                }
            }


            /**
             * 根据返回数据内容第二字节：ucStatus的值做后续操作，定义如下
             * Perform subsequent operations based on the value of the second byte of the returned data: ucStatus, which is defined as follows
             * #define OPS_STATUS_APPROVED                 0x10 // 批准 // Approve
             * #define OPS_STATUS_DECLINED                 0x20 // 拒绝 // Deny
             * #define OPS_STATUS_ONLINE_REQUEST           0x30 // 联机请求// Online request
             * #define OPS_STATUS_END_APPLICATION          0x40 // 结束应用// End application
             * #define OPS_STATUS_SELECT_NEXT              0x50 // 选择下一个// Select next
             * #define OPS_STATUS_TRY_ANOTHER_INTERFACE    0x60 // 尝试其他界面// Try another interface
             * #define OPS_STATUS_TRY_AGAIN                0x70 // 重试// Retry
             * #define OPS_STATUS_REVERSAL                 0x80 // 冲正 (RUPAY专用) // Reverse (for RUPAY only)
             * #define OPS_STATUS_NA                       0xF0 // N/A // N/A
             */
          /*  if ("10".equals(ucStatus)) {
                toast(getString(R.string.gac1_approve));
                Log.e(TAG, getString(R.string.gac1_approve));
            } else if ("20".equals(ucStatus)) {
                toast(getString(R.string.gac1_deny));
                Log.e(TAG, getString(R.string.gac1_deny));
            } else if ("30".equals(ucStatus)) {
                //toast(getString(R.string.gac1_online_request));
                Log.e(TAG, getString(R.string.gac1_online_request));
                switch (type) {
                    case 0:
                        //IC
                        startTrans();
                        break;
                    case 1:
                        startTrans();
                        //RF
                        break;
                }
                *//**
             * 如果第二字节提示联机请求，需要在判断第四字节 ucCvm,看是否需要执行打印小票签名行或调用联机PIN
             * If the second byte indicates an online request, you need to check the fourth byte ucCvm to see if you need to print the receipt signature line or call the online PIN.
             * #define OPS_CVM_NO_CVM                  0x00 // 无需CVM   No CVM required
             * #define OPS_CVM_SIGNATURE               0x10 // 签名   Signature
             * #define OPS_CVM_ONLINE_PIN              0x20 // 联机PIN   Online PIN
             * #define OPS_CVM_CONFIRM_CODE_VERIFIED   0x30 // 验证确认码   Verification confirmation code
             * #define OPS_CVM_NA                      0xF0 // CVM：N/A   CVM: N/A
             *//*
                String ucCvm = AFUtils.showResult16Str(new byte[]{resultData[3]});
                Log.i(TAG, "ucCvm:" + ucCvm);
                switch (ucCvm) {
                    case "00":
                        Log.i(TAG, "ucCvm Result: No CVM required");
                        break;
                    case "10":
                        Log.i(TAG, "ucCvm Result: Signature");
                        break;
                    case "20":
                        Log.i(TAG, "ucCvm Result: Online PIN");
                        toast("RF Online PIN...");
                        break;
                    case "30":
                        Log.i(TAG, "ucCvm Result: Verification confirmation code");
                        break;
                    case "F0":
                        Log.i(TAG, "ucCvm Result: N/A");
                        break;
                }

            } else if ("40".equals(ucStatus)) {
                if (type == 0) {
                    toast("CancelPassword");
                } else {
                    toast(getString(R.string.gac1_end_application));
                }
                Log.e(TAG, getString(R.string.gac1_end_application));
            } else if ("50".equals(ucStatus)) {
                toast(getString(R.string.gac1_select_next));
                Log.e(TAG, getString(R.string.gac1_select_next));
            } else if ("60".equals(ucStatus)) {
                toast(getString(R.string.gac1_try_another_interface));
                Log.e(TAG, getString(R.string.gac1_try_another_interface));
            } else if ("70".equals(ucStatus)) {
                toast(getString(R.string.gac1_retry));
                Log.e(TAG, getString(R.string.gac1_retry));
            } else if ("80".equals(ucStatus)) {
                toast(getString(R.string.gac1_reverse));
                Log.e(TAG, getString(R.string.gac1_reverse));
            } else if ("F0".equals(ucStatus)) {
                toast(getString(R.string.gac1_na));
                Log.e(TAG, getString(R.string.gac1_na));
            }

            if (type == 1) {
                mHandler.sendEmptyMessageDelayed(8003, 1000);
            }

            */

        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    /**
     * Initiate online transaction
     */
    private void startTrans() {
        LogUtils.i("----------------startTrans----------------");
        // A card has been read. Removing it from here on is the end-of-
        // transaction removal, not a failed insertion.
        mCardReadOk = true;
        stopChipSlotWatch();
        currentCardNumber = getPanFromKernelTags();
        if (TextUtils.isEmpty(currentCardNumber) && !TextUtils.isEmpty(tipCardNumber)) {
            currentCardNumber = tipCardNumber;
        }
        tipCardNumber = currentCardNumber;

        if (TextUtils.isEmpty(currentCardNumber)) {
            Log.e(TAG, "startTrans: empty PAN, retrying card search");
            // Bounded, for the same reason as the kernel-failure path above:
            // a card that reads but yields no PAN yields no PAN every time.
            stopFindCard = false;
            retrySearchCard(enabledSearchMask());
            return;
        }

        if (cardType == CardSearchType.IC_CARD) clearChipStrikes();
        LogUtils.i("currentCardNumber: " + currentCardNumber + "\n");

        // Card range check before anything else touches this transaction.
        if (rejectUnsupportedCard(tipCardNumber)) {
            return;
        }

        String cardAID = getTagValue(0x4F);
        LogUtils.i("CardAID: " + cardAID + "\n");
        String track2data = getTagValue(0x57);
        if (TextUtils.isEmpty(track2data)) {
            track2data = getTagValue(0x9F6B);
        }
        LogUtils.i("Track2data: " + track2data + "\n");
        String cardSerialNumber = getTagValue(0x5F34);
        LogUtils.i("CardSerialNumber: " + cardSerialNumber + "\n");
        cardValid = getTagValue(0x5F24);
        if (TextUtils.isEmpty(cardValid) && !TextUtils.isEmpty(track2data)) {
            String t2 = track2data.toUpperCase(java.util.Locale.US);
            int sep = t2.indexOf('D');
            if (sep < 0) sep = t2.indexOf('=');
            if (sep >= 0 && t2.length() >= sep + 5) {
                cardValid = t2.substring(sep + 1, sep + 5);
            }
        }
        LogUtils.i("0x5F24 卡有效期 CardValid: " + cardValid + "\n");
        String terminalCountryCode = getTagValue(0x9F1A);
        LogUtils.i("Terminal country code: " + terminalCountryCode + "\n");

        LogUtils.i("------------【Get IC DATA】------------");
        int[] icDataTAGList = new int[]{
                0X5F2A, 0X82, 0X84, 0X95, 0X9A,
                0X9C, 0X9F02, 0X9F03, 0X9F09, 0X9F10,
                0X9F1A, 0X9F26, 0X9F27, 0X9F33, 0X9F34,
                0X9F35, 0X9F36, 0X9F37, 0X9F41, 0X5F34};

        int[] rfDataTAGList = new int[]{
                0X5F2A, 0X82, 0X84, 0X95, 0X9A,
                0X9C, 0X9F02, 0X9F03, 0X9F09, 0X9F10,
                0X9F1A, 0X9F26, 0X9F27, 0X9F33, 0X9F34,
                0X9F35, 0X9F36, 0X9F37, 0X9F41, 0X5F34};


        String icData = (cardType == 0 ? getICData(icDataTAGList) : getICData(rfDataTAGList));

        saleDataModel.setCardNo(currentCardNumber);
        saleDataModel.setCardSerialNumber(cardSerialNumber);
        saleDataModel.setCardAID(cardAID);
        saleDataModel.setTrack2data(track2data);
        saleDataModel.setCardValid(cardValid);

        saleDataModel.setIcData(icData);

        Log.i(TAG, "IC Data : \n" + saleDataModel.getIcData());
        saleDataModel.setTransAmount(transAmount);
        saleDataModel.setTerminalCountryCode(terminalCountryCode);

        Log.i(TAG, "saleDataModel:" + saleDataModel.toString());

        if (cardType == CardSearchType.RF_CARD) {
            // Confirm the card before this transaction goes anywhere.
            //
            // Every other entry mode already asks: swipe through
            // MSG_TRANS_MSR_SUCCESS, keyed through startManualTxn, QR through
            // startKonaQrTxn, chip just before the PIN pad. Contactless was
            // the one that did not. A tap went from the reading popup
            // straight to the signature dialog, so nothing on screen ever
            // named the card that was about to be charged.
            //
            // On a phone wallet that is not a nicety. The wallet holds
            // several cards and presents whichever one it is currently set
            // to; the cardholder cannot see which, and neither could the
            // operator. Confirmation is the only point at which a wrong card
            // can still be stopped, and once the transaction is online it
            // cannot be stopped at all.
            //
            // showConfirmationDialog takes the reading popup down itself and
            // cancels the transaction on a "no", so a refusal needs nothing
            // more here.
            showConfirmationDialog(currentCardNumber,
                    new FindCardActivity.ConfirmationCallback() {
                        @Override
                        public void onResult(int result) {
                            if (result != 0) {
                                return;
                            }
                            continueContactlessAfterConfirm();
                        }
                    });
            return;
        } else {
            // Contact chip or magstripe: proceed directly to signature/online
            mHandler.sendEmptyMessageDelayed(MSG_SHOW_SIGNATURE_DIALOG, 1000);
        }
    }

    /**
     * The rest of the contactless path, once the card has been confirmed.
     *
     * <p>Split out of {@link #startTrans()} so the confirmation can be asked
     * asynchronously. {@code startTrans} runs on the card-search thread and
     * blocking it to wait for an answer would hold the reader open behind a
     * dialog.</p>
     */
    private void continueContactlessAfterConfirm() {
        TerminalConfig tc = TerminalConfig.get(getActivity());
        long amt = TerminalConfig.parseMajorToMinor(currentAmount);
        long limit = tc.clessCvmLimit();

        // ONE rule, and it is the amount against the contactless CVM limit:
        //
        //     amount <= limit   ->  no PIN
        //     amount >  limit   ->  PIN
        //
        // A tap of exactly the limit is PIN-free. 5000.00 with a 5000.00 limit
        // does not ask; 5000.01 does.
        //
        // Deliberately scheme-independent. This runs for every contactless
        // card the terminal reads - Visa, Mastercard, UnionPay, Diners,
        // TakaPay, NexusPay, whatever the AID - because it is keyed on the
        // amount and nothing else. Putting the rule in the per-AID records was
        // tried and it was wrong twice over: the field chosen (DF20) turned out
        // to be the contactless TRANSACTION limit, so the card stopped being
        // read entirely above it, and it would have had to be maintained
        // separately for every scheme in the table. One rule here covers all of
        // them and can only ever decide the PIN, never whether a card reads.
        if (limit > 0 && amt >= 0) {
            if (amt <= limit) {
                LogUtils.i("contactless " + currentAmount + " is within the CVM limit "
                        + tc.clessCvmLimitText() + " - no PIN"
                        + (mKernelCvm == null || mKernelCvm.isEmpty()
                                ? "" : " (kernel said " + mKernelCvm + ")"));
                noPinForThisTap();
                return;
            }
            LogUtils.i("contactless " + currentAmount + " is above the CVM limit "
                    + tc.clessCvmLimitText() + " - PIN required"
                    + (mKernelCvm == null || mKernelCvm.isEmpty()
                            ? "" : " (kernel said " + mKernelCvm + ")"));
            showPinPad(true, currentCardNumber, 1);
            return;
        }

        // No usable limit configured. Fall back to the kernel's own verdict
        // rather than letting every tap through unverified.
        LogUtils.w("no contactless CVM limit configured - using the kernel verdict '"
                + mKernelCvm + "' (" + kernelCvmName(mKernelCvm) + ")");
        if ("20".equals(mKernelCvm)) {
            showPinPad(true, currentCardNumber, 1);
            return;
        }
        noPinForThisTap();
    }

    /** No cardholder verification on this tap: go straight on to the slip. */
    private void noPinForThisTap() {
        mOnlinePin = false;
        mPinBlockHex = null;
        showProcessingScreen("Processing Transaction...", "Connecting to host, please wait...");
        mHandler.sendEmptyMessageDelayed(MSG_SHOW_SIGNATURE_DIALOG, 1000);
    }

    private int rfCardConfirm(String tipCardNumber) {
        final CountDownLatch latch = new CountDownLatch(1);
        final int[] resultHolder = new int[1];
        showConfirmationDialog(tipCardNumber, new FindCardActivity.ConfirmationCallback() {
            @Override
            public void onResult(int result) {
                if (result == 0) {
                    resultHolder[0] = 0;
                } else if (result == -1) {
                    // Handle cancel
                    resultHolder[0] = -1;
                }
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return resultHolder[0];

    }

    /**
     * 关闭EMV寻卡交易
     * Disable EMV card search
     */
    /** Nothing should fire on a screen that has gone. */
    private void stopPinWatchdogOnExit() {
        cancelPinWatchdog();
        mPinDone.set(true);
    }

    private void closeFindCard() {
        Log.e(TAG, "==========【closeFindCardForEMV】==========");
        try {
            disDialog();
            mCardBinderService.closeFindCardForEMV();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            Log.i(TAG, "onKeyDown: 820");
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                // 确认键被按下
                Log.d(TAG, "Confirm (Enter/OK) button pressed");
                return true;
            }
            // 检查按键的键值
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                    Log.d(TAG, "确认键");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_UP:
                    // 处理音量增大按键
                    Log.d(TAG, "Volume up button pressed");
                    return true;
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    // 处理音量减少按键
                    Log.d(TAG, "Volume down button pressed");
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    // 处理清除键->直接退出页面
                    Log.d(TAG, "Back button pressed");
                   /* switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                           
                            break;
                        default:
                            Log.i(TAG, "暂不处理1.....");
                            break;
                    }*/
                    // Was a bare finish(), which left the card reader armed and
                    // popped back to whatever happened to be underneath.
                    // canCleTransaction closes the reader and returns to the
                    // menu, which is what every other cancel path does.
                    if (backAfterCommit()) return true;
                    toast(getString(R.string.trans_canceled));
                    canCleTransaction();
                    return true;
                case KeyEvent.KEYCODE_DEL:
                    // CLEAR
                    Log.d(TAG, "Confirm button KEYCODE_DEL");
                    switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                            break;
                        default:
                            Log.i(TAG, "暂不处理2.....");
                            break;
                    }
                    return true;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }

        Log.e(TAG, "onKeyDown: xxxxxx");
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Back, on a touch unit.
     *
     * <p>While a card was being looked for, back used to be caught by the wait
     * dialog's own key listener, which cancelled the transaction and closed the
     * reader. That dialog no longer covers the card screen, so the handling
     * moves here - otherwise back would fall through to the default finish()
     * and leave the reader armed behind a dead activity. The keyboard units
     * take the same route through onKeyDown above.</p>
     */
    @Override
    public void onBackPressed() {
        if (backAfterCommit()) return;
        isSearchingCard = false;
        toast(getString(R.string.trans_canceled));
        canCleTransaction();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            Log.i(TAG, "onKeyUp: 820");
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    switch (KEYCODE_ENTER_ACTION) {
                        case KEYCODE_ENTER_START_SEARCH_CARD:
                            Log.i(TAG, "发起交易.....");
                            //startActivity(SelectTransTypeActivity.class);
                            break;
                        case KEYCODE_ENTER_INPUT_PIN:
                            Log.i(TAG, "PIN输入.....");
                            break;
                        default:
                            Log.i(TAG, "KeyEvent  default---> ");
                            break;
                    }
                    Log.d(TAG, "Confirm button released");
                    return true;
                case KeyEvent.KEYCODE_CLEAR:
                    Log.d(TAG, "onKeyUp:  KEYCODE_CLEAR");
                    return true;
                default:
                    return super.onKeyUp(keyCode, event);
            }
        }
        Log.e(TAG, "onKeyUp: xxxxxx");
        return super.onKeyUp(keyCode, event);
    }

}
