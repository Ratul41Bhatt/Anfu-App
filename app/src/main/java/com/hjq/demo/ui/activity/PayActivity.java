package com.hjq.demo.ui.activity;

import static android.graphics.Bitmap.createBitmap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Settings;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;
import com.hjq.demo.model.AFConstant;
import com.hjq.demo.ans8583.OfflinePacket8583;
import com.hjq.demo.ans8583.OnlineHost;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.utils.PasswordPrompt;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.hmscorescan.DefinedActivity;
import com.hjq.demo.qrscancode.ScanServiceClient;
import com.hjq.demo.qrscancode.ZXScannerActivity;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.ui.dialog.TipsDialog;
import com.hjq.demo.ui.dialog.WaitDialog;
import com.hjq.demo.utils.AFUtils;
import com.hjq.demo.utils.AidManager;
import com.hjq.demo.utils.AppConfig;
import com.hjq.demo.utils.AppExitMonitor;
import com.hjq.demo.utils.AppPreferences;
import com.hjq.demo.utils.DateUtil;
import com.hjq.demo.utils.DeviceType;
import com.hjq.demo.ans8583.ResponseCode;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PermissionInterceptor;
import com.hjq.demo.utils.PlatformUtils;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.ShareUtil;
import com.hjq.demo.utils.TouchHelper;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.emv.IEmvBinderService;
import com.szanfu.sdk.api.error.ErrorCode;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.system.ISystemBinderService;
import com.szanfu.sdk.api.util.DataConversionUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

public final class PayActivity extends AppActivity implements View.OnClickListener {
    private static final String TAG = "PayActivity";
    //    private RelativeLayout gridLayout;
//    private FrameLayout myTermainlTab;//我的终端
//    private FrameLayout myTradeTab;//我的交易
//    private FrameLayout myPaymentTab;//扫码支付
//    private FrameLayout moreAppTab;//更多应用
    private String ErrMsg = "";
    private String InitiaState = "";
    public Intent intent;
    public ShareUtil myShare = null;
    /**
     * 扫描跳转Activity RequestCode
     */
    /**
     * 扫描跳转Activity RequestCode
     */
    public static final int REQUEST_CODE_SECOND_ACTIVITY = 1;
    private BaseDialog mWaitDialog;
    AppPreferences mPreferences;
    private IPrinterBinderService mPrinterBinderService;
    private IEmvBinderService mEmvBinderService;
    private ISystemBinderService mSystemBinderService;
    private IPinPadBinderService mPinPadBinderService;
    private AidManager aidManager;
    private volatile com.szanfu.sdk.api.card.ICardBinderService mSlotSensor;
    private volatile boolean mHomeResumed;
    private final java.util.concurrent.atomic.AtomicInteger mHomeSensorEpoch =
            new java.util.concurrent.atomic.AtomicInteger();
    private boolean mAmountFromSensor;
    private boolean mHomeCardPresent;
    public static final String EXTRA_WAIT_FOR_CARD_REMOVAL = "WAIT_FOR_CARD_REMOVAL";
    private volatile boolean mWaitForCardRemoval;


    /** Amount keypad opened by an explicit transaction selection. */
    private android.app.Dialog mAmountDialog;
    /**
     * Which transaction the keypad on screen belongs to.
     *
     * <p>The keypad captures its type when it is built and hands that type to
     * {@code launchTxn} on confirm. A keypad opened for one transaction can
     * therefore never start another - which is a problem when something else
     * opened it first. See {@link #showAmountKeypad(String)}.</p>
     */
    private String mAmountDialogType;
    /** UI handler for the reversal-status banner. */
    private final android.os.Handler mReversalHandler = new android.os.Handler();

    /**
     * Re-reads the reversal slot while the home screen is in front.
     *
     * <p>{@code onResume} alone was not enough. A reversal can be delivered by
     * a background thread that is still finishing after this screen has come
     * back - the immediate retry loop in {@code OnlineHost} can be several
     * seconds long - so the banner was drawn from a slot that was about to be
     * cleared, and then nothing redrew it. Once every two seconds costs
     * nothing and means the banner disappears when the 0410 lands rather than
     * at the next screen change.</p>
     */
    private static final long REVERSAL_WATCH_MS = 2000L;
    private final Runnable mReversalWatch = new Runnable() {
        @Override
        public void run() {
            showReversalStatus();
            mReversalHandler.postDelayed(this, REVERSAL_WATCH_MS);
        }
    };
    private static final int REQUEST_CODE_SCAN_ONE = 0X01;
    public static final String RESULT = "SCAN_RESULT";
    private static final int REQUEST_CODE_DEFINE = 0X0111;
    private static final int REQUEST_CODE_SCANBYHW = 0X0112;
    private static final int REQUEST_CODE_BY_GOOGLE_MLKIT = 0X113;
    /** Held so the tiles can be re-hidden after Menu Control is edited. */
    private ViewPager mMenuPager;
    /** Scanning a "Nexus (KonaMoney)" QR for a sale. */
    private static final int REQ_KONA_QR = 0X114;
    /** The amount already keyed, held while the operator scans the QR. */
    private String mKonaAmount = "";
    private static final int ERROR_LOW_BATTERY = -3006;
    private static final int ERROR_PRINTER_OUT_OF_PAPER = -4002;
    private static final int ERROR_PRINTER_OVER_TEMPERATUR = -4005;


    //设备类型 820
    private boolean keyboardDevice;
    private String customerType;

    private ImageView imagePos;
    private EditText mAmountInput;
    private int scannerOpenResult = -1;

    private static final long AMOUNT_LOWER_LIMIT = 100;
    private static final long AMOUNT_UPPER_LIMIT = 99999999;

    private SpeechRecognizer mSpeech;
    private boolean mMicWanted = false;
    private boolean mMicUnavailableToasted = false;

    private Model mVoskModel;
    private SpeechService mVoskService;

    private ScanServiceClient scanClient = new ScanServiceClient();

    /**
     * 获取布局 ID
     */
    @Override
    protected int getLayoutId() {
        Timber.tag(TAG).d("<<<<<<<<<<<<<<< execute getLayoutId >>>>>>>>>>>>>>>>>>>");
        return R.layout.activity_pay1;
    }

    /**
     * 初始化控件
     */
    @Override
    protected void initView() {
        myShare = new ShareUtil(getApplicationContext());
//        int scrWidth = myShare.getInt(Constant.SCREEN_WIDTH, 1080);
//        gridLayout = (RelativeLayout) findViewById(R.id.main_grid_layout);
//        gridLayout.setLayoutParams(new LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, scrWidth));
//        myTermainlTab = (FrameLayout) findViewById(R.id.main_my_termainl_tab);
//        myTradeTab = (FrameLayout) findViewById(R.id.main_my_trade_tab);
//        myPaymentTab = (FrameLayout) findViewById(R.id.main_my_payment_tab);
//        moreAppTab = (FrameLayout) findViewById(R.id.main_more_app_tab);

        Timber.tag(TAG).d("<<<<<<<<<<<<<<< execute initView >>>>>>>>>>>>>>>>>>>");
        imagePos = findViewById(R.id.main_posview);
        if (imagePos != null) {
            imagePos.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    voiceSelfTest();
                    return true;
                }
            });
        }

        setupMenuPager();

        mAmountInput = findViewById(R.id.main_amount_input);
        mAmountInput.setShowSoftInputOnFocus(false);
        mAmountInput.setFocusable(false);
        mAmountInput.setOnClickListener(this);
        findViewById(R.id.main_amount_enter).setOnClickListener(this);

        setupMenuQr();

        customerType = DataConversionUtils.getCustomerType();
        keyboardDevice = PlatformUtils.isKeyboardDevice(customerType);
        LogUtils.e("设备类型:" + (keyboardDevice ? "820" : "930"));

//        myTermainlTab.setOnClickListener(this);
//        myTradeTab.setOnClickListener(this);
//        myPaymentTab.setOnClickListener(this);
//        moreAppTab.setOnClickListener(this);

        initPermission();
        //扫码库
        //ZXingLibrary.initDisplayOpinion(this);
        bindService();
//        getWindow().getDecorView().clearFocus();
        getWindow().getDecorView().requestFocus();
        LogUtils.e("initView: 渲染..... ");
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int screenWidth = getResources().getDisplayMetrics().widthPixels;
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                float centerX = screenWidth / 2;
                float centerY = screenHeight * 0.1f;
                TouchHelper.simulateFullTouch(PayActivity.this, centerX, centerY);

                LogUtils.e("run: 触摸事件");
                if (imagePos != null) {
                    imagePos.setFocusable(true);
                    imagePos.setFocusableInTouchMode(true);
                    imagePos.requestFocus();
                }
            }
        }, 200);

    }


    @Override
    protected void onDestroy() {
        stopHomeSlotSensor();
        super.onDestroy();
        stopScanQRCode();
        stopVosk();
        if (mVoskModel != null) {
            try { mVoskModel.close(); } catch (Exception ignore) {}
            mVoskModel = null;
        }
        if (mSpeech != null) {
            try { mSpeech.destroy(); } catch (Exception ignore) {}
            mSpeech = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_WAIT_FOR_CARD_REMOVAL, false)) {
            mWaitForCardRemoval = true;
            getIntent().removeExtra(EXTRA_WAIT_FOR_CARD_REMOVAL);
        }
        // The home screen is the one the operator sits on, so the kiosk lock is
        // re-applied here too - the same reason as HomeActivity.onResume.
        com.hjq.demo.utils.TerminalLockdown.onAppResumed();
        com.hjq.demo.utils.TerminalLockdown.lockNavBar();
        startHomeSlotSensor();
        // Menu Control may have been edited in the admin panel while this
        // screen was in the background. The pager keeps its pages alive, so
        // without this a tile switched off stayed on screen until the app was
        // restarted - which is what "we are disabling it but it still shows"
        // was. applyMenuVisibility only ever ran when a page was first built.
        refreshMenuVisibility();
        restoreAudio();
        startVoice();
        playWelcomeOnce();
        showBuildStamp();
        showReversalStatus();
        warnIfSettleOverdue();
        mReversalHandler.removeCallbacks(mReversalWatch);
        mReversalHandler.postDelayed(mReversalWatch, REVERSAL_WATCH_MS);
    }

    /**
     * The reversal banner under the logo.
     *
     * <p>A pending reversal is the one terminal state that can cost the
     * cardholder real money without anyone doing anything wrong: the host may
     * have authorised a sale the terminal then reported as failed, and until
     * the 0400 is acknowledged nobody knows which. It also blocks settlement.
     * Both of those used to be discoverable only from Admin - Clear, which is
     * three screens in and behind a password.</p>
     *
     * <p>Refreshed on every resume rather than once at startup, because the
     * transaction that armed it finishes on another activity and comes back
     * here - and because clearing it in the admin panel has to take the banner
     * away without a restart.</p>
     */
    private void showReversalStatus() {
        TextView v = findViewById(R.id.tv_reversal_status);
        if (v == null) {
            return;
        }
        com.hjq.demo.ans8583.BatchStore batch =
                com.hjq.demo.ans8583.BatchStore.get(this);
        if (!batch.hasPendingReversal()) {
            if (batch.hasPendingTips()) {
                v.setText("TIP ADJUST UPLOAD PENDING"); v.setVisibility(android.view.View.VISIBLE); return;
            }
            v.setVisibility(android.view.View.GONE);
            return;
        }
        // One word, and nothing else on the screen changes. The home screen
        // stays exactly as it is - amount box, tiles, QR - and this is only a
        // line under the logo saying the terminal is carrying an unacknowledged
        // reversal. Invoice, amount and attempt count are on the Clear page in
        // the admin panel for whoever asks.
        v.setText("REVERSAL");
        v.setVisibility(android.view.View.VISIBLE);
    }


    /**
     * Append the build stamp to the QR caption.
     *
     * <p>The stamp is the same timestamp as the one in the APK filename
     * ({@code DBBL-POS_v1.1.0_debug_<stamp>.apk}), so the terminal can be
     * matched to the build that produced it at a glance.</p>
     */
    private void showBuildStamp() {
        TextView qrText = findViewById(R.id.menu_qr_text);
        if (qrText == null) {
            return;
        }
        String stamp = "BUILD " + com.hjq.demo.BuildConfig.VERSION_NAME
                + " / " + com.hjq.demo.BuildConfig.BUILD_TIME;
        String current = qrText.getText() == null ? "" : qrText.getText().toString();
        if (current.contains("BUILD ")) {
            return;
        }
        qrText.setText(current + "\n" + stamp);
    }

    /**
     * Settlement: hand over to the review screen, which shows the batch totals
     * and requires a confirmation before anything goes to the host - the same
     * shape as the PAX terminal's settle screen.
     */
    private void confirmSettlement() {
        startActivity(new Intent(getActivity(), SettleActivity.class));
    }

    /**
     * Warn once per screen when "Settle Require Days" has been passed, so the
     * batch does not sit open indefinitely.
     */
    private boolean mSettleWarned;

    private void warnIfSettleOverdue() {
        if (mSettleWarned) {
            return;
        }
        TerminalConfig tc = TerminalConfig.get(getActivity());
        if (!tc.settleOverdue()
                || com.hjq.demo.ans8583.BatchStore.get(getActivity()).totals().isZero()) {
            return;
        }
        mSettleWarned = true;
        int days = tc.daysSinceSettle();
        new MessageDialog.Builder(getActivity())
                .setTitle("SETTLEMENT DUE")
                .setMessage("Batch " + tc.batchNo() + " has not been settled "
                        + (days < 0 ? "yet" : "for " + days + " day(s)")
                        + ".\nSettle Require Days is set to "
                        + tc.settleRequireDays() + ".")
                .setConfirm(getString(R.string.common_confirm))
                .setCancel(null)
                .show();
    }

    @Override
    protected void onPause() {
        stopHomeSlotSensor();
        super.onPause();
        stopVoice();
        mReversalHandler.removeCallbacks(mReversalWatch);
    }

    /**
     * Wait for a card on the idle home screen, the way Anfu say to.
     *
     * <p>This used to poll {@code isCardInserted()} on a loop and it never saw
     * anything, which is why the keypad stopped appearing. On this reader the
     * slot switch does not report from an idle screen - it only answers once a
     * card session is open. Polling it with nothing armed asks a question
     * nobody is listening to.</p>
     *
     * <p>Anfu's instruction was: <i>"Please initiate a new card search
     * operation after completing the current one."</i> So insertion is
     * detected by {@code openFindCardForEMV}, which BLOCKS and returns the
     * moment a card is presented, and a fresh search is armed only after the
     * previous one has returned. Two searches never overlap - that is what
     * left the reader unable to see any card at all.</p>
     *
     * <p>Removal is the other half, and only then is the switch read: by that
     * point the search has completed and a card session is open, so the switch
     * answers. That is the same order the Nexgo reference uses, where
     * {@code isCardExist} is polled after a transaction and never during one.
     * Reads go through {@link CardReaderAccess} so they cannot collide with a
     * transaction that has taken the reader.</p>
     */
    private void startHomeSlotSensor() {
        mHomeResumed = true;
        final int epoch = mHomeSensorEpoch.incrementAndGet();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    homeCardLoop(epoch);
                } catch (Throwable e) {
                    LogUtils.e("HOME_CARD watch died: " + e);
                }
            }
        }, "HomeCardWatch").start();
    }

    /**
     * How long each idle search waits before it is re-armed. SECONDS.
     *
     * <p>The idle screen has to open a real chip search to see a card: on this
     * firmware isCardInserted() only reports the slot once a search has
     * detected the card, so a slot-switch poll on its own never saw a card
     * inserted at the home screen (field log 05-10: card in the slot, no
     * keypad). The open search holds the reader, and closeFindCardForEMV does
     * not end it early, so a sale started from the menu waits for the current
     * slice to run out. One second keeps that wait below anything a person
     * notices; a card inserted at any moment is still seen at once, because
     * the search returns the instant the chip goes in.</p>
     */
    private static final int HOME_FIND_TIMEOUT_SEC = 1;
    /** openFindCardForEMV: the search was cancelled from under us. */
    private static final int HOME_ERR_CANCELLED = -2012;
    /** openFindCardForEMV: timed out with nothing presented. */
    private static final int HOME_ERR_TIMEOUT = -2003;
    /** openFindCardForEMV: "detect card timeout" (V2.1 4.8.1). */
    private static final int HOME_DETECT_TIMEOUT = 8;
    /** How often the slot is re-read once a card session is open. */
    private static final long HOME_POLL_MS = 250L;

    // ------------------------------------------------ chip probe (no search)
    //
    // Field log 05-10 14:16: the SDK raises every openFindCardForEMV timeout
    // below 10 s to 10 s ("Timeout value is too low, setting to 10 seconds"),
    // so the "1 second" idle search was really an 11 second one, and
    // closeFindCardForEMV does not end it - its poll ran on to the full 11 s.
    // A sale started from the menu therefore waited up to 10 s on the card
    // screen before its own search could open (14:16:39.5 -> 14:16:49.7).
    //
    // So the idle screen no longer opens a search at all. It powers the
    // contact slot for a moment (apiDalSciPowerOn): a card answers with an
    // ATR, an empty slot does not, and the reader is free again a fraction of
    // a second later. If that call is missing, refused, or slow on a firmware,
    // the watch falls back to the old search for the rest of the app's life.

    private static final int PROBE_ABSENT = 0;
    private static final int PROBE_PRESENT = 1;
    /** Reader taken by someone else, or the screen went away. Try again. */
    private static final int PROBE_BUSY = -1;
    /** The probe cannot be used on this firmware. */
    private static final int PROBE_UNUSABLE = -2;
    /** 0 untested, 1 working, -1 unusable (search fallback). */
    private static volatile int sProbeState = 0;
    private static int sProbeLogs = 0;
    /** A probe slower than this would hold up a sale - use the search instead. */
    private static final long PROBE_MAX_MS = 1500L;
    private static final long HOME_PROBE_IDLE_MS = 400L;
    private static final long HOME_PROBE_PRESENT_MS = 700L;

    private int probeChip(com.szanfu.sdk.api.card.ICardBinderService sensor, int epoch) {
        if (!com.hjq.demo.utils.CardReaderAccess.beginTransaction(300L,
                com.hjq.demo.utils.CardReaderAccess.HOME_SESSION, "home chip probe")) {
            return PROBE_BUSY;
        }
        try {
            if (!homeSensorAlive(epoch)) {
                return PROBE_BUSY;
            }
            long t0 = android.os.SystemClock.uptimeMillis();
            com.szanfu.sdk.api.card.ICardDataResult r;
            try {
                r = sensor.apiDalSciPowerOn(0);
            } catch (Throwable e) {
                LogUtils.e("HOMEPROBE apiDalSciPowerOn refused: " + e);
                return PROBE_UNUSABLE;
            }
            long dt = android.os.SystemClock.uptimeMillis() - t0;
            int ret = r == null ? Integer.MIN_VALUE : r.getRet();
            byte[] atr = r == null ? null : r.getCardData();
            // A real answer-to-reset starts with TS = 3B (direct) or 3F
            // (inverse). Requiring it means a firmware that returns 0 for an
            // empty slot can never conjure a card out of nothing.
            boolean atrOk = atr != null && atr.length >= 2
                    && ((atr[0] & 0xFF) == 0x3B || (atr[0] & 0xFF) == 0x3F);
            boolean present = atrOk && ret >= 0;
            if (atrOk || ret >= 0) {
                try {
                    sensor.apiDalSciPowerOff();
                } catch (Throwable ignore) {
                    // the next search powers the slot itself
                }
            }
            if (sProbeLogs < 6 || present) {
                sProbeLogs++;
                LogUtils.i("HOMEPROBE ret=" + ret + " atr="
                        + (atr == null ? "null" : atr.length + "B") + " present=" + present
                        + " in " + dt + " ms");
            }
            if (dt > PROBE_MAX_MS) {
                LogUtils.e("HOMEPROBE took " + dt + " ms - too slow for the idle screen");
                return PROBE_UNUSABLE;
            }
            return present ? PROBE_PRESENT : PROBE_ABSENT;
        } finally {
            com.hjq.demo.utils.CardReaderAccess.endTransaction();
        }
    }

    /** Wait, by probing, until the chip leaves the slot. Two empty reads in a row. */
    private boolean waitForProbeRemoval(com.szanfu.sdk.api.card.ICardBinderService sensor,
                                        int epoch) {
        int empty = 0;
        while (homeSensorAlive(epoch)) {
            sleepHome(HOME_PROBE_PRESENT_MS);
            int p = probeChip(sensor, epoch);
            if (p == PROBE_UNUSABLE) {
                sProbeState = -1;
                return false;
            }
            if (p == PROBE_ABSENT) {
                if (++empty >= 2) {
                    return true;
                }
            } else if (p == PROBE_PRESENT) {
                empty = 0;
            }
        }
        return false;
    }

    private void homeCardLoop(final int epoch) {
        LogUtils.i("HOME_CARD watch started (epoch " + epoch + ")");
        while (homeSensorAlive(epoch)) {
            // A card screen that closed with its card thread stuck inside the
            // card service leaves the reader dead for every screen after it.
            // Restart here, on the idle screen, before the next sale finds out.
            if (com.hjq.demo.utils.ReaderRecovery.recoverIfWedged(this, "home screen")) {
                return;
            }
            com.szanfu.sdk.api.card.ICardBinderService sensor = homeSensor();
            if (sensor == null
                    || !TerminalConfig.get(getApplicationContext()).entryChip()) {
                sleepHome(500L);
                continue;
            }

            if (sProbeState >= 0) {
                int p = probeChip(sensor, epoch);
                if (p == PROBE_UNUSABLE) {
                    sProbeState = -1;
                    LogUtils.e("HOME_CARD chip probe unusable on this firmware -"
                            + " falling back to the idle card search");
                    continue;
                }
                if (p == PROBE_BUSY) {
                    sleepHome(300L);
                    continue;
                }
                sProbeState = 1;
                if (p == PROBE_ABSENT) {
                    sleepHome(HOME_PROBE_IDLE_MS);
                    continue;
                }
                if (mWaitForCardRemoval) {
                    LogUtils.i("HOME_CARD card still inserted from the last"
                            + " transaction - waiting for it to be removed");
                } else {
                    LogUtils.i("HOME_CARD card inserted (probe) - opening the amount keypad");
                    postHomePresence(epoch, true);
                }
                boolean removed = waitForProbeRemoval(sensor, epoch);
                if (!homeSensorAlive(epoch)) {
                    return;
                }
                if (removed) {
                    mWaitForCardRemoval = false;
                    LogUtils.i("HOME_CARD card removed");
                    postHomePresence(epoch, false);
                    sleepHome(400L);
                }
                continue;
            }

            // Take the reader the same way a transaction does, so the idle
            // search can never be open at the same time as a sale's search -
            // V2.1 -2001, "card service interface already in progress".
            if (!com.hjq.demo.utils.CardReaderAccess.beginTransaction(300L,
                    com.hjq.demo.utils.CardReaderAccess.HOME_SESSION, "home idle search")) {
                sleepHome(500L);
                continue;
            }
            int type;
            try {
                // Checked again under the lock: stopHomeSlotSensor() may have
                // run between the loop test and here, and a search armed now
                // would outlive this screen.
                if (!homeSensorAlive(epoch)) {
                    return;
                }
                try {
                    // BLOCKING. Returns as soon as a card is presented, or when
                    // the timeout expires with an empty slot.
                    type = sensor.openFindCardForEMV(
                            com.hjq.demo.utils.CardSearchType.IC_CARD,
                            HOME_FIND_TIMEOUT_SEC);
                } catch (Throwable e) {
                    LogUtils.e("HOME_CARD openFindCardForEMV failed: " + e);
                    type = Integer.MIN_VALUE;
                }
                if (!homeSensorAlive(epoch)) {
                    return;                  // the screen went away mid-search
                }
                if (type == HOME_ERR_CANCELLED) {
                    // A close that landed between two slices. The screen is
                    // still up (checked just above), so this is not the end
                    // of the watch - ending it here is what left the keypad
                    // never popping for an inserted card until the screen was
                    // left and re-entered. Re-arm.
                    LogUtils.i("HOME_CARD search cancelled - re-arming");
                }
                if (type != com.hjq.demo.utils.CardSearchType.IC_CARD) {
                    // Empty timeout, busy service, or not a chip. Close while
                    // still holding the reader, so this close can never land on
                    // a card screen's search.
                    closeHomeFind(sensor);
                }
            } finally {
                com.hjq.demo.utils.CardReaderAccess.endTransaction();
            }
            if (type != com.hjq.demo.utils.CardSearchType.IC_CARD) {
                boolean empty = type == HOME_ERR_TIMEOUT || type == HOME_DETECT_TIMEOUT
                        || type == HOME_ERR_CANCELLED;
                if (!empty) {
                    // -2001 and friends come back at once. Without a pause this
                    // loop hammers a card service that is already in trouble.
                    LogUtils.w("HOME_CARD search returned " + type + " - waiting before re-arming");
                    sleepHome(1000L);
                }
                continue;
            }

            // A card is in the slot.
            //
            // Not necessarily a NEW one: coming back from a transaction with
            // the same card still inserted must not offer another sale. The
            // keypad waits until the slot has genuinely been emptied.
            if (mWaitForCardRemoval) {
                LogUtils.i("HOME_CARD card still inserted from the last"
                        + " transaction - waiting for it to be removed");
            } else {
                LogUtils.i("HOME_CARD card inserted - opening the amount keypad");
                postHomePresence(epoch, true);
            }

            waitForHomeCardRemoval(sensor, epoch);
            if (!homeSensorAlive(epoch)) {
                return;
            }
            mWaitForCardRemoval = false;
            LogUtils.i("HOME_CARD card removed");
            postHomePresence(epoch, false);

            closeHomeFindIfAlive(sensor, epoch);
            sleepHome(400L);                 // let the reader settle, then re-arm
        }
        LogUtils.i("HOME_CARD watch stopped (epoch " + epoch + ")");
    }

    /**
     * Block until the card leaves the slot.
     *
     * <p>Safe to read the switch here and nowhere else in this class: the
     * search has returned, so nothing is competing for the card service.</p>
     */
    private void waitForHomeCardRemoval(
            final com.szanfu.sdk.api.card.ICardBinderService sensor, int epoch) {
        com.hjq.demo.utils.CardPresenceTracker tracker =
                new com.hjq.demo.utils.CardPresenceTracker(HOME_POLL_MS + 50L);
        int unreadable = 0;
        while (homeSensorAlive(epoch)) {
            Boolean present;
            try {
                present = com.hjq.demo.utils.CardReaderAccess.observe(sensor::isCardInserted);
            } catch (Throwable e) {
                LogUtils.e("HOME_CARD isCardInserted failed: " + e);
                return;
            }
            if (present == null) {
                // A transaction holds the reader. Not our business now.
                if (++unreadable > 8) {
                    return;
                }
                sleepHome(HOME_POLL_MS);
                continue;
            }
            unreadable = 0;
            Boolean changed = tracker.sample(present, android.os.SystemClock.uptimeMillis());
            if (changed != null && !changed) {
                return;                      // debounced removal
            }
            if (!present && changed == null && !tracker.waitingForRemoval()) {
                // first empty reading - keep sampling until it is stable
            }
            sleepHome(HOME_POLL_MS);
        }
    }

    private com.szanfu.sdk.api.card.ICardBinderService homeSensor() {
        com.szanfu.sdk.api.card.ICardBinderService sensor = mSlotSensor;
        if (sensor == null && mSdkServiceManager != null) {
            try {
                mSlotSensor = com.szanfu.sdk.api.card.ICardBinderService.Stub.asInterface(
                        mSdkServiceManager.getService(ServiceID.SERVICE_ID_CARD));
                sensor = mSlotSensor;
            } catch (Exception ignore) {
                // service not up yet; the loop will ask again
            }
        }
        return sensor;
    }

    /**
     * Close the idle search only if this screen is still the one watching, and
     * only while holding the reader - so it cannot cancel a search that a card
     * screen opened in the meantime.
     */
    private void closeHomeFindIfAlive(
            final com.szanfu.sdk.api.card.ICardBinderService sensor, final int epoch) {
        try {
            com.hjq.demo.utils.CardReaderAccess.observe(() -> {
                if (homeSensorAlive(epoch)) {
                    closeHomeFind(sensor);
                }
                return true;
            });
        } catch (Throwable e) {
            LogUtils.e("HOME_CARD close after removal failed: " + e);
        }
    }

    /** Release the reader so the next search - ours or a transaction's - is clean. */
    private void closeHomeFind(com.szanfu.sdk.api.card.ICardBinderService sensor) {
        try {
            if (sensor != null) {
                sensor.closeFindCardForEMV();
            }
        } catch (Throwable e) {
            LogUtils.e("HOME_CARD closeFindCardForEMV failed: " + e);
        }
    }

    private void postHomePresence(final int epoch, final boolean present) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (homeSensorAlive(epoch)) {
                    onHomeCardPresence(present, epoch);
                }
            }
        });
    }

    private static void sleepHome(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean homeSensorAlive(int epoch) {
        return mHomeResumed && mHomeSensorEpoch.get() == epoch && !isFinishing();
    }

    private void stopHomeSlotSensor() {
        mHomeResumed = false;
        // Invalidate anything still in flight BEFORE breaking the search, so a
        // thread that wakes up inside closeFindCardForEMV already knows it has
        // been retired and will not touch the next screen's reader.
        mHomeSensorEpoch.incrementAndGet();
        mHomeCardPresent = false;
        mHomeCardPromptPending = false;
        if (mAmountFromSensor && mAmountDialog != null) mAmountDialog.dismiss();
        mAmountFromSensor = false;
        // Break the blocking search. Without this the idle search is still
        // holding the reader when FindCardActivity arms its own, and two
        // overlapping openFindCardForEMV calls leave it unable to see any card.
        // But never once a card screen owns the reader: this runs from
        // onPause/onDestroy too, which can land after the card screen has
        // opened its own search.
        int holder = com.hjq.demo.utils.CardReaderAccess.holderSession();
        if (holder == 0 || holder == com.hjq.demo.utils.CardReaderAccess.HOME_SESSION) {
            closeHomeFind(mSlotSensor);
        }
    }

    private boolean mHomeCardPromptPending;

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && mHomeCardPromptPending && mHomeCardPresent && mHomeResumed) {
            final int epoch = mHomeSensorEpoch.get();
            getWindow().getDecorView().post(() -> {
                if (homeSensorAlive(epoch) && mHomeCardPromptPending && mHomeCardPresent)
                    offerHomeAmount(epoch);
            });
        }
    }

    private void onHomeCardPresence(boolean present, int epoch) {
        mHomeCardPresent = present;
        mHomeCardPromptPending = present;
        if (!present) {
            if (mAmountFromSensor && mAmountDialog != null) mAmountDialog.dismiss();
            mAmountFromSensor = false;
            return;
        }
        offerHomeAmount(epoch);
    }

    private void offerHomeAmount(int epoch) {
        if (!homeSensorAlive(epoch) || !mHomeCardPresent) return;
        mHomeCardPromptPending = false;
        if ((mAmountDialog != null && mAmountDialog.isShowing())
                || !com.hjq.demo.ans8583.MenuControl.get(this).isAllowed("SALE")) return;
        guardMenu("SALE", () -> {
            if (!homeSensorAlive(epoch) || !mHomeCardPresent) return;
            showAmountKeypad("SALE");
            mAmountFromSensor = true;
            LogUtils.i("HOME_SLOT amount keypad opened");
        });
    }

    private boolean mWelcomePlayed = false;

    private void playWelcomeOnce() {
        if (mWelcomePlayed) return;
        mWelcomePlayed = true;
        try {
            int resId = getResources().getIdentifier("voice_welcome", "raw", getPackageName());
            if (resId != 0) {
                android.media.MediaPlayer mp = android.media.MediaPlayer.create(this, resId);
                if (mp != null) {
                    mp.setOnCompletionListener(new android.media.MediaPlayer.OnCompletionListener() {
                        @Override public void onCompletion(android.media.MediaPlayer m) { try { m.release(); } catch (Exception ignore) {} }
                    });
                    mp.start();
                }
            }
        } catch (Exception ignore) {}
    }

    /**
     * 初始化数据
     */
    /**
     * The home menu never closes itself.
     *
     * <p>The idle timeout exists to get the terminal back to this screen. There
     * is nothing behind it, so expiring here would either do nothing or drop
     * the operator out of the app entirely.</p>
     */
    @Override
    protected boolean idleTimeoutEnabled() {
        return false;
    }

    @Override
    protected void initData() {
        Timber.tag(TAG).d("<<<<<<<<<<<<<<< execute initData >>>>>>>>>>>>>>>>>>>");

        AppExitMonitor.init(getApplication(), new AppExitMonitor.AppExitListener() {
            @Override
            public void onAppForeground() {
            }

            @Override
            public void onAppBackground() {
            }

            @Override
            public void onAppExit() {
                Log.i(TAG, "******* onAppExit: ");
                stopScanQRCode();
            }
        });
    }


    public void stopScanQRCode() {
        Log.i(TAG, "stopScanQRCode: ");
        if (isScanning.compareAndSet(true, false)) {
            if (scanThread != null) scanThread.interrupt();
            try {
                mSystemBinderService.scannerClose();
            } catch (Exception ignore) {
            }
            Log.i(TAG, "Scan stopped and scanner closed.");
        }
    }


    /**
     * 初始化权限事件
     */
    /*private void initPermission() {
        XXPermissions.with(this)
                .permission(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ?
                        new String[]{Permission.READ_MEDIA_IMAGES, Permission.READ_MEDIA_VIDEO, Permission.READ_MEDIA_AUDIO,Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE} :
                        Permission.Group.STORAGE)
                .permission(Permission.ACCESS_COARSE_LOCATION)
                .permission(Permission.ACCESS_FINE_LOCATION)
                .permission(Permission.CAMERA)
                // 设置权限请求拦截器（局部设置）
                .interceptor(new PermissionInterceptor())
                // 设置不触发错误检测机制（局部设置）
                //.unchecked()
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                        if (!allGranted) {
                            for (String permission : permissions) {
                                toast(String.format(getString(R.string.common_permission_fail_3), permission));
                            }
                            //toast("获取部分权限成功，但部分权限未正常授予");
                            //return;
                        }
                        //toast("获取权限成功");
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        if (doNotAskAgain) {
                            //toast("被永久拒绝授权，请手动授予权限");
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(getContext(), permissions);
                        } else {
                            //toast("获取权限失败");
                        }
                    }
                });


    }*/
    private void initPermission() {
        String[] storagePermissions;
        Log.e(TAG, "initPermission: SDK_INT->" + Build.VERSION.SDK_INT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 13 (API 33) 及以上
            storagePermissions = new String[]{
                    Permission.READ_MEDIA_IMAGES,
                    Permission.READ_MEDIA_VIDEO,
                    Permission.READ_MEDIA_AUDIO
            };
        } else {
            // Android 9 (API 28) 及以下
            storagePermissions = Permission.Group.STORAGE;
        }

        XXPermissions.with(this)
                .permission(storagePermissions)
                .permission(Permission.ACCESS_COARSE_LOCATION)
                .permission(Permission.ACCESS_FINE_LOCATION)
                .permission(Permission.CAMERA)
                .interceptor(new PermissionInterceptor())
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(@NonNull List<String> permissions, boolean allGranted) {
                        if (!allGranted) {
                            for (String permission : permissions) {
                                toast(String.format(getString(R.string.common_permission_fail_3), permission));
                            }
                        }
                    }

                    @Override
                    public void onDenied(@NonNull List<String> permissions, boolean doNotAskAgain) {
                        if (doNotAskAgain) {
                            XXPermissions.startPermissionActivity(getContext(), permissions);
                        }
                    }
                });
        if (AFUtils.checkLcdSupported() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }

    }


    @SuppressLint("MissingSuperCall")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // A scanned "Nexus (KonaMoney)" QR. Checked here rather than in
        // FindCardActivity so a mis-scan costs the operator a re-scan instead
        // of a started transaction that has to be cancelled.
        if (requestCode == REQ_KONA_QR) {
            if (resultCode != RESULT_OK || data == null) {
                toast("Scan cancelled");
                return;
            }
            String scanned = data.getStringExtra(QrScanActivity.SCAN_RESULT);
            com.hjq.demo.ans8583.KonaMoney.Qr qr =
                    com.hjq.demo.ans8583.KonaMoney.parseQr(scanned);
            if (!qr.ok()) {
                new MessageDialog.Builder(getActivity())
                        .setTitle("KONAMONEY QR")
                        .setMessage("That code is not a KonaMoney payment code.\n\n"
                                + qr.error
                                + "\n\nAsk the customer to open the KonaMoney app and"
                                + " show the payment code again.")
                        .setConfirm(getString(R.string.common_confirm))
                        .setCancel(null)
                        .show();
                return;
            }
            Intent i = new Intent(getActivity(), FindCardActivity.class);
            i.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, mKonaAmount);
            i.putExtra(FindCardActivity.EXTRA_TRANS_TYPE, "SALE");
            i.putExtra(FindCardActivity.EXTRA_KONA_QR, qr.track2 + qr.dcvvMd);
            startActivity(i);
            mKonaAmount = "";
            return;
        }
        /**
         * 处理二维码扫描结果(Zxing)
         */
        if (requestCode == REQUEST_CODE_SECOND_ACTIVITY && 0 == AppConfig.buildType) {
            //处理扫描结果（在界面上显示）
            if (null != data) {
                Bundle bundle = data.getExtras();
                if (bundle == null) {
                    return;
                }
                if (requestCode == REQUEST_CODE_SECOND_ACTIVITY) {
                    if (resultCode == Activity.RESULT_OK) {
//                        String result = bundle.getString(ScanActivity.EXTRA_RESULT_DATA);
                        String result = bundle.getString(ZXScannerActivity.EXTRA_RESULT_DATA);
                        LogUtils.i("解析结果:" + result);
                        showScanCode(getString(R.string.scan_code_result));
                    }
                    // } else if (bundle.getInt(CodeUtils.RESULT_TYPE) == CodeUtils.RESULT_FAILED) {
                } else {
                    showScanCode(getString(R.string.scan_code_err));
                }
            }
        }

        //Default View 华为扫码功能  Default View Mode
        //Scan Kit处理
        //Scan Kit提供
        //相机扫码、导入图片扫码。
        if (requestCode == REQUEST_CODE_SCAN_ONE && 1 == AppConfig.buildType) {
            if (null != data) {
                HmsScan obj = data.getParcelableExtra(ScanUtil.RESULT);
                if (obj != null) {
                    LogUtils.e("onActivityResult: --->>>" + obj.getOriginalValue());
                    LogUtils.i("解析结果:" + obj.getOriginalValue());
                    showScanCode(getString(R.string.scan_code_result));
//                Intent intent = new Intent(this, DisPlayActivity.class);
//                intent.putExtra(RESULT, obj);
//                startActivity(intent);
                }
            }
            //MultiProcessor & Bitmap
        }

        //Default View 华为扫码功能
//        Customized View Mode
//        Scan Kit处理
//        您自行定义
//        相机扫码（可以叠加Bitmap Mode增加导入图片扫码功能）。
        if (requestCode == REQUEST_CODE_DEFINE && 1 == AppConfig.buildType) {
            if (null != data) {
                HmsScan obj = data.getParcelableExtra(DefinedActivity.SCAN_RESULT);
                if (obj != null) {
                    LogUtils.e("onActivityResult: --->>>" + obj.getOriginalValue());
                    LogUtils.i("解析结果:" + obj.getOriginalValue());
                    showScanCode(getString(R.string.scan_code_result));
//                Intent intent = new Intent(this, DisPlayActivity.class);
//                intent.putExtra(RESULT, obj);
//                startActivity(intent);
                }
                //MultiProcessor & Bitmap
            }
        }

        //自定义相机，相机使用定焦，获取到相机图片后调用华为扫码接口解析
        if (requestCode == REQUEST_CODE_SCANBYHW && 1 == AppConfig.buildType) {
            if (null != data) {
                HmsScan obj = data.getParcelableExtra(ScanByHWActivity.SCAN_RESULT);
                if (obj != null) {
                    LogUtils.e("onActivityResult: --->>>" + obj.getOriginalValue());
                    LogUtils.i("解析结果:" + obj.getOriginalValue());
                    showScanCode(getString(R.string.scan_code_result));
                }
            }
        }

        //谷歌扫码功能
        if (requestCode == REQUEST_CODE_BY_GOOGLE_MLKIT && 1 == AppConfig.buildType) {
            if (null != data) {
                String obj = data.getStringExtra(QrScanActivity.SCAN_RESULT);
                long time = data.getLongExtra(QrScanActivity.SCAN_TIME, -1);
                String strCodeFormat = data.getStringExtra(QrScanActivity.SCAN_FORMAT);
                if (obj != null) {
                    LogUtils.e("onActivityResult: --->>>" + obj);
                    LogUtils.i("解析结果:" + obj);
                    showScanCode(getString(R.string.scan_code_result) + obj);
                }
            }
        }

    }

    private void showScanCode(String codeResult) {
        new MessageDialog.Builder(getActivity())
                // 标题可以不用填写
                //.setTitle(R.string.scan_code_result)
                // 内容必须要填写
                .setMessage(codeResult)
                // 确定按钮文本
                .setConfirm(getString(R.string.common_confirm))
                // 设置 null 表示不显示取消按钮
                .setCancel(null)
                // 设置点击按钮后不关闭对话框
                //.setAutoDismiss(false)
                .setCanceledOnTouchOutside(false)
                .setListener(new MessageDialog.OnListener() {

                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        //toast("确定");
                        mHandler.sendEmptyMessage(8002);
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        //toast("取消");
                    }
                })
                .show();
    }


    @Override
    public void onClick(View view) {
        super.onClick(view);
        int vid = view.getId();
        if (isScanning()) {
            toast(getString(R.string.scanning_qrcode));
            return;
        }
        if (vid == R.id.main_amount_input) {
            showAmountKeypad("SALE");
        } else if (vid == R.id.main_amount_enter) {
            startTxn("SALE");
        } else if (vid == R.id.menu_void) {
            startTxn("VOID");
        } else if (vid == R.id.menu_refund) {
            startTxn("REFUND");
        } else if (vid == R.id.menu_preauth) {
            startTxn("PREAUTH");
        } else if (vid == R.id.menu_preauth_void) {
            startTxn("PREAUTH_VOID");
        } else if (vid == R.id.menu_salecomp) {
            startTxn("SALE_COMPLETE");
        } else if (vid == R.id.menu_emi_sale) {
            startTxn("EMI_SALE");
        } else if (vid == R.id.menu_emi_void) {
            startTxn("EMI_VOID");
        } else if (vid == R.id.menu_offline) {
            startTxn("OFFLINE");
        } else if (vid == R.id.menu_adjust) {
            startTxn("TIP_ADJUST");
        } else if (vid == R.id.menu_cashwithdraw) {
            startTxn("CASH_WITHDRAW");
        } else if (vid == R.id.menu_settle) {
            startTxn("SETTLEMENT");
        } else if (vid == R.id.menu_reports) {
            guardMenu("REPORTS", new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(getActivity(), ReportsActivity.class));
                }
            });
        } else if (vid == R.id.menu_qr_sale) {
            startTxn("QR_SALE");
        } else if (vid == R.id.menu_kona_qr) {
            startTxn("KONA_QR");
        } else if (vid == R.id.menu_app_info) {
            guardMenu("APP_INFO", new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(getActivity(), AppInfoActivity.class));
                }
            });
        } else if (vid == R.id.menu_logon) {
            guardMenu("LOGON", new Runnable() {
                @Override
                public void run() {
                    runLogon();
                }
            });
        } else if (vid == R.id.menu_manage) {
            guardAdmin(new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(getActivity(), AdminActivity.class));
                }
            });
        }
    }

    private void startTxn(final String type) {
        if (mAmountFromSensor && mAmountDialog != null) mAmountDialog.dismiss();
        mAmountFromSensor = false;
        // Transaction Control: the tile stays visible and the operator is told
        // why, which is what the PAX terminal does - MainActivity answers a
        // disallowed transaction with err_trans_not_allowed rather than
        // removing the tile.
        if (!com.hjq.demo.ans8583.MenuControl.get(this).isAllowed(type)) {
            new MessageDialog.Builder(getActivity())
                    .setTitle(type.replace('_', ' '))
                    .setMessage("Transaction not allowed on this terminal.\n\n"
                            + "It can be enabled in Parameters, Menu Control.")
                    .setConfirm(getString(R.string.common_confirm))
                    .setCancel(null)
                    .show();
            return;
        }
        guardMenu(type, new Runnable() {
            @Override
            public void run() {
                startTxnBody(type);
            }
        });
    }

    /**
     * Menu Hide: drop the tiles the merchant has switched off.
     *
     * <p>Applied after each page is inflated, because the pager rebuilds pages
     * as they scroll into view.</p>
     */
    private void applyMenuVisibility(android.view.ViewGroup grid) {
        com.hjq.demo.ans8583.MenuControl mc =
                com.hjq.demo.ans8583.MenuControl.get(this);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View tile = grid.getChildAt(i);
            String key = menuKeyOf(tile.getId());
            if (key != null) {
                tile.setVisibility(mc.isVisible(key) && (!"TIP_ADJUST".equals(key)
                        || TerminalConfig.get(this).tipAdjustEnabled()) ? View.VISIBLE : View.GONE);
            }
        }
    }

    /**
     * Re-apply Menu Control to every page the pager currently holds.
     *
     * <p>Walks the live views rather than rebuilding the adapter: rebuilding
     * would work too, but it drops the pager back to page one and the operator
     * loses their place for no reason.</p>
     */
    private void refreshMenuVisibility() {
        if (mMenuPager == null) {
            return;
        }
        for (int i = 0; i < mMenuPager.getChildCount(); i++) {
            View page = mMenuPager.getChildAt(i);
            View grid = page == null ? null : page.findViewById(R.id.pax_menu_grid);
            if (grid instanceof android.view.ViewGroup) {
                applyMenuVisibility((android.view.ViewGroup) grid);
            }
        }
    }

    /** Map a tile to its menu key. */
    private String menuKeyOf(int id) {
        if (id == R.id.menu_void) return "VOID";
        if (id == R.id.menu_refund) return "REFUND";
        if (id == R.id.menu_preauth) return "PREAUTH";
        if (id == R.id.menu_preauth_void) return "PREAUTH_VOID";
        if (id == R.id.menu_salecomp) return "SALE_COMPLETE";
        if (id == R.id.menu_emi_sale) return "EMI_SALE";
        if (id == R.id.menu_emi_void) return "EMI_VOID";
        if (id == R.id.menu_offline) return "OFFLINE";
        if (id == R.id.menu_adjust) return "TIP_ADJUST";
        if (id == R.id.menu_cashwithdraw) return "CASH_WITHDRAW";
        if (id == R.id.menu_settle) return "SETTLEMENT";
        if (id == R.id.menu_reports) return "REPORTS";
        if (id == R.id.menu_app_info) return "APP_INFO";
        if (id == R.id.menu_qr_sale) return "QR_SALE";
        if (id == R.id.menu_kona_qr) return "KONA_QR";
        if (id == R.id.menu_logon) return "LOGON";
        return null;
    }

    private void guardMenu(String key, final Runnable action) {
        TerminalConfig tc = TerminalConfig.get(this);
        // VOID authenticates inside its own activity, including direct entry and recreation.
        if (TerminalConfig.isVoidMenu(key)) { action.run(); return; }
        String pw = tc.menuPw(key);
        if (pw == null || pw.isEmpty()) {
            action.run();
            return;
        }
        PasswordPrompt.ask(this, key + " password", pw, new PasswordPrompt.OnOk() {
            @Override
            public void ok() {
                action.run();
            }
        });
    }

    private void guardAdmin(final Runnable action) {
        PasswordPrompt.ask(this, "Admin password", TerminalConfig.get(this).adminPw(),
                new PasswordPrompt.OnOk() {
                    @Override
                    public void ok() {
                        action.run();
                    }
                });
    }

    @Override
    public void onBackPressed() {
        PasswordPrompt.ask(this, "Exit password", TerminalConfig.get(this).exitPw(),
                new PasswordPrompt.OnOk() {
                    @Override
                    public void ok() {
                        finishAffinity();
                    }
                });
    }

    private void startTxnBody(String type) {
        if ("QR_SALE".equals(type)) {
            startActivity(new Intent(this, QrSaleActivity.class)); return;
        }
        if ("TIP_ADJUST".equals(type)) {
            if (!TerminalConfig.get(this).tipAdjustEnabled()) { toast("Enable Tip Adjust in Parameters > Tip"); return; }
            Intent tip = new Intent(this, TipActivity.class);
            tip.putExtra(TipActivity.ADJUST, true);
            startActivity(tip); return;
        }
        if ("SETTLEMENT".equals(type)) {
            confirmSettlement();
            return;
        }
        if ("EMI_SALE".equals(type)) {
            // Amount and tenure are settled before the card is read, because
            // the plan goes out in DE48 with the authorisation.
            startActivity(new Intent(getActivity(), EmiSaleActivity.class));
            return;
        }
        if ("EMI_VOID".equals(type)) {
            Intent i = new Intent(getActivity(), VoidActivity.class);
            i.putExtra(VoidActivity.EXTRA_EMI_ONLY, true);
            startActivity(i);
            return;
        }
        if ("SALE_COMPLETE".equals(type)) {
            // A completion settles a hold that already exists, so it starts
            // from the pre-auth's invoice number - not from the amount keypad,
            // which has no way to say which authorisation is being completed.
            Intent i = new Intent(getActivity(), VoidActivity.class);
            i.putExtra(VoidActivity.EXTRA_COMPLETE_ONLY, true);
            startActivity(i);
            return;
        }
        if ("PREAUTH_VOID".equals(type)) {
            // Cancelling a pre-auth starts from the authorisation it releases,
            // so it uses the same invoice-lookup screen as a void - restricted
            // to pre-auths, because the message it sends is an 0400 reversal
            // rather than an 0200 void.
            Intent i = new Intent(getActivity(), VoidActivity.class);
            i.putExtra(VoidActivity.EXTRA_PREAUTH_ONLY, true);
            startActivity(i);
            return;
        }
        if ("VOID".equals(type)) {
            // A void cancels a transaction already in the batch, so it starts
            // from the invoice number rather than from an amount or a card.
            // Its own screen, the way PAX does it - not the amount keypad.
            startActivity(new Intent(getActivity(), VoidActivity.class));
            return;
        }
        String amt = normalizeAmount(mAmountInput == null ? "" : mAmountInput.getText().toString());
        if (amt == null) {
            showAmountKeypad(type);
            return;
        }
        launchTxn(type, amt);
    }

    private void runLogon() {
        toast("Logon: contacting host...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final OnlineHost.HostResult r = OnlineHost.logon(getApplicationContext(), mPinPadBinderService);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        printSlipLines(buildLogonSlip(r));
                        if (r.approved) {
                            toast(r.online ? "LOGON SUCCESSFUL" : "Logon OK (offline fallback)");
                        } else {
                            toast("Logon declined: " + r.responseCode);
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * The logon slip: the terminal's letterhead, then the verdict.
     *
     * <p>This used to print the request and response packets in full, on every
     * logon, whether or not Debug was on - and then a one-line verdict at the
     * bottom. A merchant reading it learns nothing from three hundred
     * characters of hex; anyone else who picks the paper up learns the
     * terminal's keys and configuration. The dump is a certification aid, so
     * it now appears only behind {@link TerminalConfig#printPacketTrace}, the
     * same flag that gates it on a sale slip.</p>
     *
     * <p>A failed logon carries the spec section 8 text for its response code
     * rather than the bare code, for the same reason a declined sale does: the
     * operator has to decide what to do next, and "BAD TERMINAL ID." tells
     * them, where "89" does not.</p>
     */
    private java.util.List<com.hjq.demo.utils.Slip.Line> buildLogonSlip(
            OnlineHost.HostResult r) {
        boolean ok = ResponseCode.isApproved(r.responseCode);
        // On success the outcome is the whole slip - a "LOGON" heading above
        // "LOGON SUCCESSFUL" says the same word twice.
        java.util.List<com.hjq.demo.utils.Slip.Line> o =
                com.hjq.demo.utils.TxnSlip.notice(getApplicationContext(),
                        ok ? "LOGON SUCCESSFUL" : "LOGON FAILED",
                        ok ? "" : ResponseCode.displayText(r.responseCode, "", r.hostText),
                        ok ? "" : "RESP CODE : " + nzText(r.responseCode));
        if (!ok && !nzText(r.error).isEmpty()) {
            o.add(new com.hjq.demo.utils.Slip.Line(r.error, "",
                    com.hjq.demo.utils.Slip.SMALL, com.hjq.demo.utils.Slip.CENTER));
        }
        if (TerminalConfig.get(this).printPacketTrace()) {
            addTraceLines(o, "PENDING TRANSACTIONS", r.precedingTrace);
            addTraceLines(o, "SENDING PACKET", r.requestDump);
            addTraceLines(o, "RECEIVING PACKET", r.responseDump);
        }
        return o;
    }

    /** The packet dump, only ever reached with Debug on. */
    private void addTraceLines(java.util.List<com.hjq.demo.utils.Slip.Line> o,
                               String title, String dump) {
        if (dump == null || dump.trim().isEmpty()) {
            return;
        }
        o.add(com.hjq.demo.utils.Slip.rule());
        o.add(com.hjq.demo.utils.Slip.text(title + ":"));
        for (String line : dump.split("\n")) {
            o.add(com.hjq.demo.utils.Slip.text(line));
        }
    }

    private static String nzText(String s) {
        return s == null ? "" : s.trim();
    }

    /** Print a composed slip, the same way every other screen does. */
    private void printSlipLines(final java.util.List<com.hjq.demo.utils.Slip.Line> lines) {
        if (mPrinterBinderService == null) {
            toast("Printer not ready");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    com.hjq.demo.utils.Slip.useFont(getApplicationContext());
                    TerminalConfig tc = TerminalConfig.get(getApplicationContext());
                    android.graphics.Bitmap logo =
                            tc.printBankLogo() || tc.printMerchantLogo()
                                    ? com.hjq.demo.utils.Slip.bankLogo(
                                            getApplicationContext(), 340) : null;
                    android.graphics.Bitmap bmp =
                            com.hjq.demo.utils.Slip.toBitmap(lines, logo);
                    // Short jobs - a long one loses its tail (see SlipPrinter).
                    com.hjq.demo.utils.SlipPrinter.print(mPrinterBinderService, bmp,
                            (ok, msg) -> {
                                if (!ok) LogUtils.e("slip print failed: " + msg);
                            });
                } catch (Exception e) {
                    LogUtils.e("slip print failed: " + e.getMessage());
                }
            }
        }).start();
    }


    private void launchTxn(String type, String amt) {
        if ("SALE".equals(type) && TerminalConfig.get(this).saleWithTip()) {
            mWaitForCardRemoval = true;
            Intent tip = new Intent(this, TipActivity.class);
            tip.putExtra(TipActivity.BASE_AMOUNT, TerminalConfig.parseMajorToMinor(amt));
            if (mAmountInput != null) mAmountInput.setText("");
            startActivity(tip);
            return;
        }
        if ("KONA_QR".equals(type)) {
            // Amount first, then the code. The KonaMoney QR is regenerated by
            // the phone for each transaction and carries a dCVV that goes stale,
            // so it is scanned as late as possible - and the amount has to be
            // known before the terminal can go online with it anyway.
            mKonaAmount = amt;
            try {
                startActivityForResult(
                        new Intent(getActivity(), QrScanActivity.class), REQ_KONA_QR);
            } catch (Exception e) {
                toast("Scanner not available: " + e.getMessage());
            }
            if (mAmountInput != null) {
                mAmountInput.setText("");
            }
            return;
        }
        // The transaction screen owns the reader from here. Hand it over
        // explicitly rather than waiting for onPause, which lands a moment
        // later - and that moment is long enough for the two searches to
        // overlap. A card already in the slot must not re-offer a sale when
        // this screen comes back, either.
        mWaitForCardRemoval = true;
        stopHomeSlotSensor();
        Intent i = new Intent(getActivity(), FindCardActivity.class);
        i.putExtra(AFConstant.CURRENT_TRANS_AMOUNT, amt);
        i.putExtra(FindCardActivity.EXTRA_TRANS_TYPE, type);
        startActivity(i);
        if (mAmountInput != null) {
            mAmountInput.setText("");
        }
    }

    private void showAmountKeypad(final String type) {
        if (isFinishing() || isDestroyed()) return;
        if (mAmountDialog != null && mAmountDialog.isShowing() && type.equals(mAmountDialogType)) return;
        mAmountFromSensor = false;
        // Keep the amount dialog associated with the selected transaction type.
        if (mAmountDialog != null && mAmountDialog.isShowing()
                && !type.equals(mAmountDialogType)) {
            LogUtils.i("amount keypad was open for " + mAmountDialogType
                    + " - replacing it with " + type);
            try {
                mAmountDialog.dismiss();
            } catch (Throwable ignored) {
                // already gone
            }
        }
        mAmountDialogType = type;

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_amount_keypad, null);
        final TextView disp = view.findViewById(R.id.kp_amount);
        // The header used to be hardcoded "SALE", so every transaction type
        // looked like a sale on the amount screen.
        TextView kpTitle = view.findViewById(R.id.kp_title);
        if (kpTitle != null) {
            kpTitle.setText(type.replace('_', ' '));
        }
        com.hjq.demo.ui.view.KeyBoardView kb = view.findViewById(R.id.kp_keyboard);
        kb.styleAmountActions();

        final android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        // Track the dialog so a different transaction can replace its amount entry.
        mAmountDialog = dialog;
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(android.content.DialogInterface d) {
                if (mAmountDialog == d) {
                    mAmountDialog = null;
                    mAmountFromSensor = false;
                    mAmountDialogType = null;
                }
            }
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(android.view.Gravity.BOTTOM);
        }

        final StringBuilder digits = new StringBuilder();
        disp.setText("TK. 0.00");
        if (mAmountInput != null) {
            mAmountInput.setText("0.00");
        }
        kb.setIKetBoardListener(new com.hjq.demo.ui.view.KeyBoardView.IKeyBoardListener() {
            void updateAmt() {
                String amt = formatMinor(digits.toString());
                disp.setText("TK. " + amt);
                if (mAmountInput != null) {
                    mAmountInput.setText(amt);
                }
            }

            @Override
            public void getNumber(String s) {
                if (digits.length() < 8) {
                    digits.append(s);
                }
                updateAmt();
            }

            @Override
            public void clear() {
                digits.setLength(0);
                updateAmt();
            }

            @Override
            public void cancel() {
                if (digits.length() > 0) {
                    digits.deleteCharAt(digits.length() - 1);
                }
                updateAmt();
            }

            @Override
            public void confirm() {
                long minor;
                try {
                    minor = digits.length() == 0 ? 0 : Long.parseLong(digits.toString());
                } catch (NumberFormatException e) {
                    minor = 0;
                }
                if (minor < AMOUNT_LOWER_LIMIT) {
                    toast("Minimum amount is TK " + formatMinor(String.valueOf(AMOUNT_LOWER_LIMIT)));
                    return;
                }
                if (minor > AMOUNT_UPPER_LIMIT) {
                    toast("Maximum amount is TK " + formatMinor(String.valueOf(AMOUNT_UPPER_LIMIT)));
                    return;
                }
                String amt = formatMinor(digits.toString());
                if (mAmountInput != null) {
                    mAmountInput.setText(amt);
                }
                dialog.dismiss();
                launchTxn(type, amt);
            }
        });
        dialog.show();
    }

    private String formatMinor(String digits) {
        if (digits == null || digits.isEmpty()) {
            return "0.00";
        }
        long v;
        try {
            v = Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return "0.00";
        }
        return String.format(java.util.Locale.US, "%d.%02d", v / 100, v % 100);
    }

    private void setupMenuQr() {
        ImageView qr = findViewById(R.id.menu_qr_image);
        if (qr == null) {
            return;
        }
        Bitmap bmp = null;
        try {
            java.io.InputStream is = getAssets().open("BanglaQR.jpeg");
            bmp = android.graphics.BitmapFactory.decodeStream(is);
            is.close();
        } catch (Exception ignore) {
        }
        if (bmp == null) {
            bmp = generateQr("MERCHANT:LIMU BEDDING STORE DITF-2026|MID:8638197970|QRID:01097EFD", 480);
        }
        if (bmp != null) {
            qr.setImageBitmap(bmp);
        }
    }

    private Bitmap generateQr(String content, int size) {
        try {
            com.google.zxing.common.BitMatrix m = new com.google.zxing.MultiFormatWriter()
                    .encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size);
            int w = m.getWidth();
            int h = m.getHeight();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    bmp.setPixel(x, y, m.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private String prettyType(String type) {
        switch (type) {
            case "PREAUTH":       return "PRE-AUTH";
            case "PREAUTH_VOID":  return "PRE-AUTH VOID";
            case "SALE_COMPLETE": return "SALE COMPLETE";
            case "EMI_SALE":      return "EMI SALE";
            case "EMI_VOID":      return "EMI VOID";
            case "CASH_WITHDRAW": return "CASH WITHDRAW";
            default:               return type;
        }
    }

    private String normalizeAmount(String raw) {
        if (raw == null) {
            return null;
        }
        raw = raw.trim().replace(",", "");
        if (raw.isEmpty()) {
            return null;
        }
        try {
            double v = Double.parseDouble(raw);
            if (v <= 0) {
                return null;
            }
            return String.format(java.util.Locale.US, "%.2f", v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void restoreAudio() {
        try {
            android.media.AudioManager am =
                    (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
            if (am == null) {
                return;
            }
            int[] streams = {
                    android.media.AudioManager.STREAM_MUSIC,
                    android.media.AudioManager.STREAM_SYSTEM,
                    android.media.AudioManager.STREAM_NOTIFICATION,
                    android.media.AudioManager.STREAM_RING,
                    android.media.AudioManager.STREAM_ALARM
            };
            for (int st : streams) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(st, android.media.AudioManager.ADJUST_UNMUTE, 0);
                    } else {
                        am.setStreamMute(st, false);
                    }
                } catch (Exception ignore) {
                }
            }
            int max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
            if (am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == 0) {
                am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        Math.max(1, (int) (max * 0.7)), 0);
            }
        } catch (Exception ignore) {
        }
    }

    private void voiceSelfTest() {
        StringBuilder sb = new StringBuilder("VOICE SELF-TEST\n");
        try {
            boolean mic = getPackageManager()
                    .hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE);
            sb.append("Mic hardware: ").append(mic ? "YES" : "NO (no voice input possible)").append('\n');
        } catch (Exception e) {
            sb.append("Mic hardware: ?\n");
        }
        sb.append("Record permission: ")
                .append(XXPermissions.isGranted(this, Permission.RECORD_AUDIO) ? "granted" : "DENIED")
                .append('\n');
        try {
            sb.append("Google recognizer: ")
                    .append(SpeechRecognizer.isRecognitionAvailable(this) ? "yes" : "no").append('\n');
        } catch (Exception ignore) {
        }
        boolean hasVoskModel = false;
        try {
            String[] f = getAssets().list("model-en-us");
            hasVoskModel = f != null && f.length > 0;
        } catch (Exception ignore) {
        }
        sb.append("Vosk model in assets: ").append(hasVoskModel ? "yes" : "NO").append('\n');
        try {
            Intent it = new Intent(android.speech.tts.TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            int n = getPackageManager().queryIntentActivities(it, 0).size();
            sb.append("TTS engines installed: ").append(n).append('\n');
        } catch (Exception e) {
            sb.append("TTS engines: ?\n");
        }
        LogUtils.e(sb.toString());
        Toast.makeText(this, sb.toString(), Toast.LENGTH_LONG).show();
        playTestTone();
    }

    private void playTestTone() {
        try {
            final android.media.ToneGenerator tg =
                    new android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC, 100);
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP, 300);
            getWindow().getDecorView().postDelayed(new Runnable() {
                @Override
                public void run() { try { tg.release(); } catch (Exception ignore) {} }
            }, 500);
        } catch (Exception ignore) {
        }
    }

    private void setupMenuPager() {
        final ViewPager pager = findViewById(R.id.menu_pager);
        mMenuPager = pager;
        final LinearLayout dots = findViewById(R.id.menu_dots);
        final int[] pageLayouts = {
                R.layout.pay_menu_page1,
                R.layout.pay_menu_page2,
                R.layout.pay_menu_page3
        };
        if (pager == null) {
            return;
        }
        pager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return pageLayouts.length;
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return view == object;
            }

            @NonNull
            @Override
            public Object instantiateItem(@NonNull ViewGroup container, int position) {
                View page = LayoutInflater.from(container.getContext())
                        .inflate(pageLayouts[position], container, false);
                View gridView = page.findViewById(R.id.pax_menu_grid);
                if (gridView instanceof ViewGroup) {
                    ViewGroup grid = (ViewGroup) gridView;
                    for (int i = 0; i < grid.getChildCount(); i++) {
                        grid.getChildAt(i).setOnClickListener(PayActivity.this);
                    }
                    applyMenuVisibility(grid);
                }
                container.addView(page);
                return page;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
                container.removeView((View) object);
            }
        });

        buildDots(dots, pageLayouts.length, 0);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                buildDots(dots, pageLayouts.length, position);
            }
        });
    }

    private void buildDots(LinearLayout dots, int count, int selected) {
        if (dots == null) {
            return;
        }
        dots.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        int size = (int) (9 * d);
        int margin = (int) (5 * d);
        for (int i = 0; i < count; i++) {
            View dot = new View(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.leftMargin = margin;
            lp.rightMargin = margin;
            dot.setLayoutParams(lp);
            dot.setBackgroundResource(i == selected
                    ? R.drawable.dot_selected : R.drawable.dot_unselected);
            dots.addView(dot);
        }
    }

    private void setupMic() {
        if (mSpeech != null) {
            return;
        }
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                return;
            }
            mSpeech = SpeechRecognizer.createSpeechRecognizer(this);
            mSpeech.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) { restoreAudio(); }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { restoreAudio(); }
                @Override public void onError(int error) { restoreAudio(); restartMic(); }
                @Override public void onResults(android.os.Bundle results) {
                    restoreAudio();
                    handleSpeech(results);
                    restartMic();
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) { }
                @Override public void onEvent(int eventType, android.os.Bundle params) { }
            });
        } catch (Exception e) {
            LogUtils.e("mic setup error: " + e.getMessage());
        }
    }

    private void startMic() {
        mMicWanted = true;
        if (!XXPermissions.isGranted(this, Permission.RECORD_AUDIO)) {
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            if (!mMicUnavailableToasted) {
                mMicUnavailableToasted = true;
                toast("Voice input unavailable on this device (no speech recognizer). Use the keypad.");
            }
            return;
        }
        if (mSpeech == null) {
            setupMic();
        }
        if (mSpeech == null) {
            return;
        }
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            }
            restoreAudio();
            mSpeech.startListening(i);
        } catch (Exception e) {
            LogUtils.e("mic start error: " + e.getMessage());
        }
    }

    private void restartMic() {
        if (!mMicWanted) {
            return;
        }
        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mMicWanted && mSpeech != null) {
                    try { mSpeech.cancel(); } catch (Exception ignore) {}
                    startMic();
                }
            }
        }, 1500);
    }

    private void stopMic() {
        mMicWanted = false;
        if (mSpeech != null) {
            try {
                mSpeech.cancel();
                mSpeech.stopListening();
            } catch (Exception ignore) {
            }
        }
    }

    private void handleSpeech(android.os.Bundle results) {
        try {
            java.util.ArrayList<String> list =
                    results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (list != null && !list.isEmpty()) {
                acceptVoiceAmount(list.get(0));
            }
        } catch (Exception e) {
            LogUtils.e("voice parse error: " + e.getMessage());
        }
    }

    private void acceptVoiceAmount(String heard) {
        if (heard == null) {
            return;
        }
        String text = heard.toLowerCase(java.util.Locale.US);
        LogUtils.e("voice heard: " + text);
        if (!text.contains("taka") && !text.contains("tk") && !text.contains("amount")) {
            return;
        }
        long taka = parseSpokenAmount(text);
        if (taka <= 0) {
            return;
        }
        long minor = taka * 100;
        if (minor < AMOUNT_LOWER_LIMIT || minor > AMOUNT_UPPER_LIMIT) {
            runOnUiThread(new Runnable() {
                @Override public void run() { toast("Voice amount out of range"); }
            });
            return;
        }
        final String amt = String.format(java.util.Locale.US, "%d.00", taka);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mAmountInput != null) {
                    mAmountInput.setText(amt);
                }
                stopVoice();
                launchTxn("SALE", amt);
            }
        });
    }

    private String extractVoskText(String json) {
        if (json == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"(?:text|partial)\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private void startVosk() {
        try {
            if (mVoskService != null || mVoskModel == null) {
                return;
            }
            Recognizer rec = new Recognizer(mVoskModel, 16000.0f);
            mVoskService = new SpeechService(rec, 16000.0f);
            mVoskService.startListening(new org.vosk.android.RecognitionListener() {
                @Override public void onPartialResult(String hypothesis) { }
                @Override public void onResult(String hypothesis) { acceptVoiceAmount(extractVoskText(hypothesis)); }
                @Override public void onFinalResult(String hypothesis) { acceptVoiceAmount(extractVoskText(hypothesis)); }
                @Override public void onError(Exception e) { LogUtils.e("vosk error: " + e.getMessage()); }
                @Override public void onTimeout() { }
            });
            LogUtils.e("Vosk offline recognizer listening");
        } catch (Exception e) {
            LogUtils.e("startVosk error: " + e.getMessage());
        }
    }

    private void stopVosk() {
        if (mVoskService != null) {
            try {
                mVoskService.stop();
                mVoskService.shutdown();
            } catch (Exception ignore) {
            }
            mVoskService = null;
        }
    }

    private void startVoice() {
        mMicWanted = true;
        if (!XXPermissions.isGranted(this, Permission.RECORD_AUDIO)) {
            return;
        }
        boolean hasModel = false;
        try {
            String[] f = getAssets().list("model-en-us");
            hasModel = f != null && f.length > 0;
        } catch (Exception ignore) {
        }
        if (hasModel) {
            if (mVoskService != null) {
                return;
            }
            if (mVoskModel != null) {
                startVosk();
                return;
            }
            try {
                StorageService.unpack(this, "model-en-us", "vosk-model",
                        (model) -> {
                            mVoskModel = model;
                            startVosk();
                        },
                        (exception) -> LogUtils.e("Vosk unpack failed: " + exception.getMessage()));
            } catch (Exception e) {
                LogUtils.e("Vosk init error: " + e.getMessage());
            }
        } else {
            startMic();
        }
    }

    private void stopVoice() {
        mMicWanted = false;
        stopVosk();
        stopMic();
    }

    private long parseSpokenAmount(String text) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d[\\d,]*").matcher(text);
        if (m.find()) {
            try {
                return Long.parseLong(m.group().replace(",", ""));
            } catch (Exception ignore) {
            }
        }
        return wordsToNumber(text);
    }

    private long wordsToNumber(String text) {
        java.util.Map<String, Long> map = new java.util.HashMap<>();
        String[] ones = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight",
                "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
                "seventeen", "eighteen", "nineteen"};
        for (int i = 0; i < ones.length; i++) {
            map.put(ones[i], (long) i);
        }
        map.put("twenty", 20L); map.put("thirty", 30L); map.put("forty", 40L); map.put("fifty", 50L);
        map.put("sixty", 60L); map.put("seventy", 70L); map.put("eighty", 80L); map.put("ninety", 90L);
        long total = 0, current = 0;
        boolean found = false;
        for (String w : text.split("[^a-z]+")) {
            if (w.isEmpty()) {
                continue;
            }
            if (map.containsKey(w)) {
                current += map.get(w);
                found = true;
            } else if (w.equals("hundred")) {
                current = (current == 0 ? 1 : current) * 100;
                found = true;
            } else if (w.equals("thousand")) {
                total += (current == 0 ? 1 : current) * 1000;
                current = 0;
                found = true;
            } else if (w.equals("lakh") || w.equals("lac")) {
                total += (current == 0 ? 1 : current) * 100000;
                current = 0;
                found = true;
            } else if (w.equals("million")) {
                total += (current == 0 ? 1 : current) * 1000000;
                current = 0;
                found = true;
            }
        }
        return found ? total + current : 0;
    }

    private void startScan() {
        if (!scanClient.isReady()) {
            Toast.makeText(this, "SDK not ready (service not connected)", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            scanClient.openScan(
                    this,
                    2,          // cameraType: 建议默认2（camera2）
                    true,       // autoFixedFocal: 默认自动
                    1,          // singleOrRepeat: 1单次
                    0,          // cameraOrFront: 0后置
                    30,         // focalLength: cm（仅手动时有效）
                    7_000,      // timeOutMs：7秒
                    new ScanServiceClient.ScanResultListener() {
                        @Override
                        public void onSuccess(String result) {
                            Log.i(TAG, "SUCCESS: " + result);
                            showScanCode(getString(R.string.scan_code_result));
                        }

                        @Override
                        public void onFailed(String errorMsg) {
                            //showScanCode(getString(R.string.scan_code_err) + "\n errMsg:" + errorMsg);
                            Log.e(TAG, "FAILED: " + errorMsg);
                            Toast.makeText(PayActivity.this, getString(R.string.scan_code_err) + "\n errMsg:" + errorMsg, Toast.LENGTH_SHORT).show();

                        }
                    }
            );
        } catch (RemoteException e) {
            Log.e(TAG, "RemoteException: " + e.getMessage());
        }
    }


    private void startScanQRCodeX() {
        try {
            if (scannerOpenResult == 0) {
                // 成功打开扫码器，开始轮询获取数据
                new Thread(() -> {
                    try {
                        while (true) {
                            byte[] responseData = new byte[1024];
                            int scannerGetDataResult = mSystemBinderService.scannerGetData(responseData, 1024 * 4);

                            Log.i(TAG, "scannerGetDataResult result:: " + scannerGetDataResult);

                            if (scannerGetDataResult > 0) {
                                // 成功后关闭扫码器
                                int scannerCloseResult = mSystemBinderService.scannerClose();
                                Log.i(TAG, "scannerCloseResult  : " + scannerCloseResult);
                                //runOnUiThread(() -> toast("scannerCloseResult: " + scannerCloseResult));
                                mSystemBinderService.setBeepStatus(true, 9600, 9600, 200);
                                // 截取有效数据
                                byte[] validData = Arrays.copyOf(responseData, scannerGetDataResult);

                                runOnUiThread(() -> {
                                    String strValidData = AFUtils.showResult16Str(validData);
                                    Log.i(TAG, "scannerGetDataResult DataLen : " + scannerGetDataResult);
                                    Log.i(TAG, "scannerGetDataResult : " + AFUtils.showResult16Str(validData));
                                    Log.i(TAG, "strValidData : " + AFUtils.hexToAscii(strValidData));
                                    //runOnUiThread(() -> toast("Get QR ValidData Success"));
                                    showScanCode(getString(R.string.scan_code_result));
                                });

                                break;
                            }
                            // 每次查询间隔 500ms
                            Thread.sleep(500);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> toast("scannerGetData failed: " + e.getMessage()));
                    }
                }).start();
            } else {
                runOnUiThread(() -> toast("Error   Failed to start scanning .."));
            }

        } catch (Exception e) {
            e.printStackTrace();
            toast("scannerOpen failed: " + e.getMessage());
        }
    }

    private final AtomicBoolean isScanning = new AtomicBoolean(false);
    private Thread scanThread;

    // 对外提供查询/停止（可选）
    public boolean isScanning() {
        return isScanning.get();
    }

    private void startScanQRCode() {
        try {
            // 防止重复开启：只有从 false->true 才允许启动
            if (!isScanning.compareAndSet(false, true)) {
                runOnUiThread(() -> toast("Scanning QR code..."));
                return;
            }

            if (scannerOpenResult != 0) {
                isScanning.set(false); // 失败要复位
                runOnUiThread(() -> toast("Error   Failed to start scanning .."));
                return;
            }

            // 成功打开扫码器，开始轮询获取数据
            scanThread = new Thread(() -> {
                try {
                    byte[] responseData = new byte[1024 * 4];

                    while (isScanning.get() && !Thread.currentThread().isInterrupted()) {
                        int len = mSystemBinderService.scannerGetData(responseData, responseData.length);
                        Log.i(TAG, "scannerGetDataResult: " + len);

                        if (len > 0) {
                            // 截取有效数据
                            final byte[] validData = Arrays.copyOf(responseData, len);

                            // 蜂鸣提示
                            try {
                                //mSystemBinderService.setBeepStatus(true, 9600, 9600, 200);
                                mSystemBinderService.setBeepTone(true, 1, 200);
                            } catch (Exception ignore) {
                            }

                            // UI 更新
                            runOnUiThread(() -> {
                                String hex = AFUtils.showResult16Str(validData);
                                Log.i(TAG, "DataLen : " + len);
                                Log.i(TAG, "HEX     : " + hex);
                                Log.i(TAG, "ASCII   : " + AFUtils.hexToAscii(hex));
                                showScanCode(getString(R.string.scan_code_result));
                            });
                            break;
                        }

                        // 轮询间隔（500ms，可按需改小）
                        Thread.sleep(500);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    Log.w(TAG, "Scan thread interrupted.");
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> toast("scannerGetData failed: " + e.getMessage()));
                } finally {
                    try {
                        mSystemBinderService.scannerClose();
                    } catch (Exception ignore) {
                    }
                    isScanning.set(false);
                    scanThread = null;
                    Log.i(TAG, "Scan finished. scanner closed. isScanning=false");
                }
            }, "ScanQR-Thread");

            scanThread.start();

        } catch (Exception e) {
            e.printStackTrace();
            isScanning.set(false);
            toast("scannerOpen failed: " + e.getMessage());
        }
    }


    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            LogUtils.i("onServiceConnected: -------------------------------");
            mPrinterBinderService = IPrinterBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
            mEmvBinderService = IEmvBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_EMV));
            mSystemBinderService = ISystemBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_SYSTEM));
            // Hand the SDK to the lockdown helper and lock immediately: on a
            // cold boot this is the first moment setNavBarEnabled can work.
            com.hjq.demo.utils.TerminalLockdown.connect(this, sdkServiceManager);
            com.hjq.demo.utils.TerminalLockdown.lockNavBar(sdkServiceManager);
            mPinPadBinderService = IPinPadBinderService.Stub.asInterface(sdkServiceManager.getService(ServiceID.SERVICE_ID_PINPAD));
            mSlotSensor = com.szanfu.sdk.api.card.ICardBinderService.Stub.asInterface(
                    sdkServiceManager.getService(ServiceID.SERVICE_ID_CARD));
            aidManager = new AidManager(mEmvBinderService);
            // 获取AppPreferences实例
            mPreferences = AppPreferences.getInstance(getContext());
            // AID and CAPK were previously loaded only once, on first launch.
            // If app data is cleared, or the kernel loses its table, the
            // terminal silently ends up with no CA public keys - which makes
            // every chip card fail offline data authentication and answer the
            // first Generate AC with an AAC. Reload on every start until a load
            // is confirmed successful; it is cheap and idempotent.
            LogUtils.i("onServiceConnected: Syncing AID and CAPK parameters to EMV Kernel...");
            downloadInitialParameters();
            scanClient.onSdkServiceReady(mSystemBinderService);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private StringBuffer sbMsg = new StringBuffer();

    private String printMsg() {
        sbMsg.setLength(0);
        String id;
        String date_time;
        long currentDate = DateUtil.getTimeStamp(new Date(), 0);
        sbMsg.append("        Dutch Bangla Bank PLC\n");
        sbMsg.append("------------------------------------------------------\n");
        date_time = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT, 0);
        sbMsg.append(getString(R.string.print_date_time) + "\t\t\t" + date_time + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append("                          SALE");
        sbMsg.append("\n\n");
        sbMsg.append(getString(R.string.print_merchant_name) + "\n                              DBB PLC TEST POS\n");
        sbMsg.append(getString(R.string.print_terminal_no) + "\n                                              " + 48290000 + "\n");
        sbMsg.append(getString(R.string.print_operator_no) + "\n                                              00000001\n");
        sbMsg.append(getString(R.string.checkbalance_indent_num_str) + "\n                                     " + generateRandomNumber(12) + "\n");
        sbMsg.append(getString(R.string.print_txn_type) + "\n                                                      " + getString(R.string.print_txn_type_info) + "\n");
        id = DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4);
        sbMsg.append(getString(R.string.print_ref_no) + "\n                                     " + id + "\n");
        sbMsg.append("------------------------------------------------------\n");
        sbMsg.append(getString(R.string.print_amount) + "99.00\n");
        sbMsg.append("------------------------------------------------------\n\n");
        sbMsg.append(getString(R.string.sing_tip) + "\n\n");
//        sbMsg.append(getString(R.string.sign) + "\n\n");
        //savaTrans(id, date_time);
        LogUtils.i("printMsg: " + sbMsg.toString());
        myShare.put("trans_order", myShare.toString());
        LogUtils.i("SP-->: " + myShare.getString("trans_order", "xxx"));
        return sbMsg.toString();
    }

    private String printMsgS() {
        sbMsg.setLength(0);
        long currentDate = DateUtil.getTimeStamp(new Date(), 0);
        sbMsg.append("*************TEST*************\n\n");
        sbMsg.append(getString(R.string.print_merchant_name) + "82982415699005C\n\n");
        sbMsg.append(getString(R.string.print_terminal_no) + generateRandomNumber(8) + "\n\n");
        sbMsg.append(getString(R.string.print_operator_no) + "00000001\n\n");
        sbMsg.append(getString(R.string.checkbalance_indent_num_str) + generateRandomNumber(12) + "\n\n");
        sbMsg.append(getString(R.string.print_txn_type) + getString(R.string.print_txn_type_info) + "\n\n");
//        sbMsg.append(getString(R.string.print_exp) + "2023/08\n\n");
        sbMsg.append(getString(R.string.print_date_time) + DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT, 0) + "\n\n");
        sbMsg.append(getString(R.string.print_ref_no) + DateUtil.getTimeStampString(currentDate, DateUtil.DATE_TIME_FORMAT_NO, 0) + generateRandomNumber(4) + "\n\n");
        sbMsg.append(getString(R.string.print_amount) + "99\n\n");
        sbMsg.append(getString(R.string.sign) + "\n\n");
        return sbMsg.toString();
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
        return sb.toString();
    }

    private void printerTest() {
        Log.e(TAG, "printerTest: 开始打印->mPrinterBinderService == null \t" + (mPrinterBinderService == null));
        try {
            if (mPrinterBinderService == null) {
                toast("PrinterBinderService服务获取失败！");
                if (mWaitDialog != null && mWaitDialog.isShowing())
                    mWaitDialog.dismiss();
                return;
            }

            Bitmap bmp = creatTextBitmap(printMsg(), 384, 1100);
            LogUtils.i("bmp width " + bmp.getWidth() + "\n" + "bmp height " + bmp.getHeight() + "\n");
            int x = 0;
            int width = bmp.getWidth();
            int height = bmp.getHeight();
            int bitW = PrintUtil.getPaddingBitWidth(width);
            byte[] bitmapData = PrintUtil.getBitmapData(bmp);
            mPrinterBinderService.printBitmap(x, bitmapData, bitW, height, new IPrinterCallback.Stub() {
                @Override
                public void onResult(int errorCode, String errorMsg, int subErrorCode) throws RemoteException {
                    if (errorCode == ErrorCode.SUCCESS) {
                        mWaitDialog.dismiss();
                        LogUtils.i(getResources().getString(R.string.succeed) + "\n");
                    } else {
                        Message printErr = new Message();
                        printErr.what = 3000;
                        Bundle bundle = new Bundle();
                        /*if(subErrorCode == ERROR_LOW_BATTERY){
                            bundle.putCharSequence("print_err", getString(R.string.low_battery_power));
                        } else if(subErrorCode == ERROR_PRINTER_OUT_OF_PAPER) {
                            bundle.putCharSequence("print_err", getString(R.string.print_out_of_paper));
                        } else if(subErrorCode == ERROR_PRINTER_OVER_TEMPERATUR) {
                            bundle.putCharSequence("print_err", getString(R.string.print_over_temperature));
                        } else {
                            bundle.putCharSequence("print_err", "Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n");
                        }*/
                        bundle.putCharSequence("print_err", errorMsg);
                        printErr.setData(bundle);
                        mHandler.sendMessage(printErr);
                        LogUtils.i("Error Code: " + errorCode + "  Sub Error Code: " + subErrorCode + "\nError Msg: " + errorMsg + "\n");
                    }
                }
            });
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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

    private void disDialog() {
        if (mWaitDialog != null) {
            mWaitDialog.dismiss();
            mWaitDialog = null;
        }
    }

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
                            // 标题可以不用填写
                            .setTitle(R.string.pop_notice)
                            // 内容必须要填写
                            .setMessage(print_err_info)
                            // 确定按钮文本
                            .setConfirm(getString(R.string.common_confirm))
                            // 设置 null 表示不显示取消按钮
                            .setCancel(null)
                            // 设置点击按钮后不关闭对话框
                            //.setAutoDismiss(false)
                            .setListener(new MessageDialog.OnListener() {

                                @Override
                                public void onConfirm(BaseDialog dialog) {
                                    //toast("确定");
                                }

                                @Override
                                public void onCancel(BaseDialog dialog) {
                                    //toast("取消");
                                }
                            })
                            .show();
                    break;
                case 8002:
                    //交易处理中
                    mWaitDialog = new WaitDialog.Builder(getActivity())
                            // 消息文本可以不用填写
                            .setMessage(getString(R.string.trans_waiting))
                            .create();
                    mWaitDialog.show();
                    mHandler.sendEmptyMessageDelayed(8003, 1000);
                    break;
                case 8003:
                    mWaitDialog.dismiss();
                    if (DeviceType.A6.equals(customerType)) {
                        //printMsg();
                        int delay = 100;
                        LogUtils.e("delay: " + delay);
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                new MessageDialog.Builder(getActivity())
                                        .setTitle(R.string.title_result)
                                        .setMessage(R.string.trans_tip_successful)
                                        .setConfirm(getString(R.string.common_confirm))
                                        .setCancel(null)
                                        .setCancelable(false)
                                        .setCanceledOnTouchOutside(false)
                                        //.setAutoDismiss(false)
                                        .setListener(new MessageDialog.OnListener() {
                                            @Override
                                            public void onConfirm(BaseDialog dialog) {
                                                //toast("确认");
                                            }

                                            @Override
                                            public void onCancel(BaseDialog dialog) {
                                                //toast("取消");
                                            }
                                        })
                                        .show();
                            }
                        }, delay);
                    } else {
                        //打印小票
                        mWaitDialog = new WaitDialog.Builder(getActivity())
                                // 消息文本可以不用填写
                                .setMessage(getString(R.string.print_state))
                                .create();
                        mWaitDialog.show();
                        printerTest();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @NonNull
    @Override
    protected ImmersionBar createStatusBarConfig() {
        return super.createStatusBarConfig()
                // 默认状态栏字体颜色为亮色
                .statusBarDarkFont(false);
    }


    /**
     * 跳转ZXing扫码界面
     **/
    public void goPageZXScanner() {
        ActivityResultLauncher<Intent> intentActivityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        //此处是跳转的result回调方法
                        if (result.getData() != null && result.getResultCode() == Activity.RESULT_OK) {
                            if (0 == AppConfig.buildType) {
                                //处理扫描结果（在界面上显示）
                                Bundle bundle = result.getData().getExtras();
                                if (bundle == null) {
                                    return;
                                }
                                String resultcode = bundle.getString(ZXScannerActivity.EXTRA_RESULT_DATA);
                                LogUtils.i("解析结果:" + resultcode);
                                showScanCode(getString(R.string.scan_code_result));
                            }
                        } else {
                            showScanCode(getString(R.string.scan_code_err));
                        }
                    }
                });
        Intent intent = new Intent(PayActivity.this, ZXScannerActivity.class);
        intentActivityResultLauncher.launch(intent);
    }


    //首次安装应用，加载AID(参数）,公钥操作
    //Install the app for the first time, download AID, capk
    private void downloadInitialParameters() {
        // Optimized Priority AID list for Bangladesh (DBBL):
        // 1. NexusPay (KonaMoney / PureKernel contactless)
        // 2. DBBL Nexus Chip (7-byte)
        // 3. DBBL Nexus Debit (6-byte)
        // 4. TakaPay (NPSB National Scheme)
        // 5. Visa (Credit, Electron, Interlink, Generic)
        // 6. Mastercard (Standard, Maestro, Generic)
        // 7. Diners Club / Discover (DCI)
        // 8. JCB
        // 9. UnionPay (PBOC/qPBOC)
        // 10. RuPay
        String strAID_PRIORITY =
                // 1. NEXUSPAY / KonaMoney Full AID (A0000002514025260010)
                com.hjq.demo.ans8583.KonaMoney.aidParamContactless() + "\n"
                // 2. NEXUSPAY / KonaMoney Prefix RID (A000000251)
                + "9F0605A000000251DF0101009F08020020DF13050010000000DF1205FC60ACF800DF1105FC60242800DF14039F3704DF150400000000DF160100DF1701009F1B0400000000DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F6604B6000000\n"
                // 3. DBBL NEXUS EMV chip cards 7-byte (F0504442420110)
                + "9F0607F0504442420110DF0101009F08020020DF13050010000000DF1205FC60ACF800DF1105FC60242800DF14039F3704DF150400000000DF160100DF1701009F1B0400000000DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F6604B6000000\n"
                // 4. DBBL NEXUS Debit 6-byte (F05044420110)
                + "9F0606F05044420110DF0101009F08020020DF13050010000000DF1205FC60ACF800DF1105FC60242800DF14039F3704DF150400000000DF160100DF1701009F1B0400000000DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F6604B6000000\n"
                // 5. TAKAPAY (A00000091010)
                + "9F0606A00000091010DF0101009F08020020DF13050010000000DF1205FC60ACF800DF1105FC60242800DF14039F3704DF150400000000DF160100DF1701009F1B0400000000DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F6604B6000000\n"
                // 5. VISA Credit (A0000000031010)
                + "9F0607A0000000031010DF0101009F0902008CDF1105D84000A800DF1205D84004F800DF130500100000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 6. VISA Electron (A0000000032010)
                + "9F0607A0000000032010DF0101009F0902008CDF1105D84000A800DF1205D84004F800DF130500100000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801009F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 7. VISA Generic (A000000003)
                + "9f0605a0000000039f0902008c9f0106010203040506df14039f3704ff01069f02069f0306df13050010000000df1205d84004f800df1105d84000a8009f1c0832303034313231359f1b0400000000df150400000000df170400000000df1604000000009f150207429f160f4d65726368616e74494420202020205f2a0204105f3601009f3c0200009f3d0100df20069999999999999F660436000000\n"
                // 8. VISA Interlink (A0000000033010)
                + "9F0607A0000000033010DF0101009F0902008CDF1105D84000A800DF1205D84004F800DF130500100000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801009F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 9. VISA Plus / V-PAY (A0000000038010 / A0000000032020)
                + "9f0607a0000000038010DF0101009F08020020DF1105D84000A800DF1205D84004F800DF130500100000009F1B0400000001DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000000100000DF21060000005000009F660436000000\n"
                + "9f0607a0000000032020DF0101009F08020020DF1105D84000A800DF1205D84004F800DF130500100000009F1B0400000001DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000000100000DF21060000005000009F660436000000\n"
                // 10. MASTERCARD Credit/Debit (A0000000041010)
                + "9F0607A0000000041010DF0101009F09020002DF1105FC5080A000DF1205F85080F800DF130504000000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 11. MAESTRO (A0000000043060)
                + "9F0607A0000000043060DF0101009F09020002DF1105FC5058A000DF1205F85058F800DF130504000000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 12. MASTERCARD Generic (A000000004)
                + "9F0605A000000004DF0101009F09020002DF1105FC5080A000DF1205F85080F800DF130504000000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 13. DINERS CLUB INTERNATIONAL DCI (A0000001524010)
                + "9F0607A00000015240109f090200019f0106010203040506df14039f3704ff01069f02069f0306df13050010000000df1205fce09cf800df1105dc000020009f1c0832303034313231359f1b0400000000df150400000000df170400000000df1604000000009f150207429f160f4d65726368616e74494420202020205f2a0204105f3601009f3c0200009f3d0100df20069999999999999F660436000000\n"
                // 14. DINERS CLUB (A0000001523010)
                + "9F0607A00000015230109f090200019f0106010203040506df14039f3704ff01069f02069f0306df13050010000000df1205fce09cf800df1105dc000020009f1c0832303034313231359f1b0400000000df150400000000df170400000000df1604000000009f150207429f160f4d65726368616e74494420202020205f2a0204105f3601009f3c0200009f3d0100df20069999999999999F660436000000\n"
                // 15. JCB (A00000006510)
                + "9F0606A00000006510DF0101009F09020200DF1105FC6024A800DF1205FC60ACF800DF130500100000009F1B0400000000DF150400000000DF160199DF170199DF14039F3704DF1801009F7B06000000100000DF1906000000100000DF2006000999999999DF21060000005000009F660436000000\n"
                // 16. UNIONPAY PBOC/qPBOC (A000000333010101, A000000333010102, A000000333010103)
                + "9F0608A000000333010101DF0101009F08020020DF1105D84000A800DF1205D84004F800DF130500100000009F1B040000C350DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06100000000000DF1906100000000000DF2006100000000000DF21060000005000009F660436000000\n"
                + "9F0608A000000333010102DF0101009F08020020DF1105D84000A800DF1205D84004F800DF130500100000009F1B040000C350DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06100000000000DF1906100000000000DF2006100000000000DF21060000005000009F660436000000\n"
                + "9F0608A000000333010103DF0101009F08020020DF1105D84000A800DF1205D84004F800DF130500100000009F1B040000C350DF150400000000DF160199DF170199DF14039F3704DF1801019F7B06100000000000DF1906100000000000DF2006100000000000DF21060000005000009F660436000000\n"
                // 17. RUPAY (a0000005241010)
                + "9f0607a00000052410105F3601009F090200649F150212349F1B04000000009F1C0832303034313231359F1E0838333230314943439F3901059F3C0200009F3D01009F4005F000F0A0019F4E04616263649F6604360000009F1D080800800000000000DF010100DF1105FFFFFFFFFFDF1205FFFFFFFFFFDF13050010000000DF14039F3704DF150400001388DF160100DF170100DF1906000000005000DF2006000000200001DF2106000000500000DF7208F4F0F0FAAFFE8000DF730101DF74010F\n"
        ;

        String strAID_CONTACT = com.hjq.demo.ans8583.KonaMoney.aidParamContact() + "\n"
                + strAID_PRIORITY.substring(strAID_PRIORITY.indexOf('\n') + 1);
        String strAID_CONTACTLESS = strAID_PRIORITY;

        String packageName = getApplication().getPackageName();
        LogUtils.e("downloadInitialParameters: packageName-->" + packageName);

        // Save general AID, contact AID (type 2) and contactless AID (type 4)
        aidManager.saveDevParamAsync(strAID_CONTACTLESS, packageName, AidManager.PARAM_AID);

        boolean loadAidResult_CONTACT = aidManager.saveDevParamAsync(strAID_CONTACT, packageName, AidManager.PARAM_AID_CONTACT);
        LogUtils.e("downloadInitialParameters: loadAidResult_CONTACT-->" + loadAidResult_CONTACT);
        runOnUiThread(() -> {
            // 更新UI
            if (!loadAidResult_CONTACT) {
                new TipsDialog.Builder(getContext())
                        .setIcon(TipsDialog.ICON_ERROR)
                        .setMessage(getString(R.string.load_param_fail))
                        .show();
            }
        });
        if (!loadAidResult_CONTACT)
            return;

        boolean loadAidResultCONTACTLESS = aidManager.saveDevParamAsync(strAID_CONTACTLESS, packageName, AidManager.PARAM_AID_CONTACTLESS);
        LogUtils.e("downloadInitialParameters: loadAidResultCONTACTLESS-->" + loadAidResultCONTACTLESS);
        runOnUiThread(() -> {
            // 更新UI
            if (!loadAidResultCONTACTLESS) {
                new TipsDialog.Builder(getContext())
                        .setIcon(TipsDialog.ICON_ERROR)
                        .setMessage(getString(R.string.load_param_fail))
                        .show();
            }
        });
        if (!loadAidResultCONTACTLESS)
            return;


        //公钥
        String strCapk =
                "9F0605A0000003339F220101DF028180BBE9066D2517511D239C7BFA77884144AE20C7372F515147E8CE6537C54C0A6A4D45F8CA4D290870CDA59F1344EF71D17D3F35D92F3F06778D0D511EC2A7DC4FFEADF4FB1253CE37A7B2B5A3741227BEF72524DA7A2B7B1CB426BEE27BC513B0CB11AB99BC1BC61DF5AC6CC4D831D0848788CD74F6D543AD37C5A2B4C5D5A93BDF0314E881E390675D44C2DD81234DCE29C3F5AB2297A0DF040103DF05083230303931323331DF060101DF070101\n"
                        + "9F0605A0000003339F220102DF028190A3767ABD1B6AA69D7F3FBF28C092DE9ED1E658BA5F0909AF7A1CCD907373B7210FDEB16287BA8E78E1529F443976FD27F991EC67D95E5F4E96B127CAB2396A94D6E45CDA44CA4C4867570D6B07542F8D4BF9FF97975DB9891515E66F525D2B3CBEB6D662BFB6C3F338E93B02142BFC44173A3764C56AADD202075B26DC2F9F7D7AE74BD7D00FD05EE430032663D27A57DF031403BB335A8549A03B87AB089D006F60852E4B8060DF040103DF05083230323131323331DF060101DF070101\n"
                        + "9F0605A0000003339F220103DF0281B0B0627DEE87864F9C18C13B9A1F025448BF13C58380C91F4CEBA9F9BCB214FF8414E9B59D6ABA10F941C7331768F47B2127907D857FA39AAF8CE02045DD01619D689EE731C551159BE7EB2D51A372FF56B556E5CB2FDE36E23073A44CA215D6C26CA68847B388E39520E0026E62294B557D6470440CA0AEFC9438C923AEC9B2098D6D3A1AF5E8B1DE36F4B53040109D89B77CAFAF70C26C601ABDF59EEC0FDC8A99089140CD2E817E335175B03B7AA33DDF031487F0CD7C0E86F38F89A66F8C47071A8B88586F26DF040103DF05083230323431323331DF060101DF070101\n"
                        + "9F0605A0000003339F220104DF0281F8BC853E6B5365E89E7EE9317C94B02D0ABB0DBD91C05A224A2554AA29ED9FCB9D86EB9CCBB322A57811F86188AAC7351C72BD9EF196C5A01ACEF7A4EB0D2AD63D9E6AC2E7836547CB1595C68BCBAFD0F6728760F3A7CA7B97301B7E0220184EFC4F653008D93CE098C0D93B45201096D1ADFF4CF1F9FC02AF759DA27CD6DFD6D789B099F16F378B6100334E63F3D35F3251A5EC78693731F5233519CDB380F5AB8C0F02728E91D469ABD0EAE0D93B1CC66CE127B29C7D77441A49D09FCA5D6D9762FC74C31BB506C8BAE3C79AD6C2578775B95956B5370D1D0519E37906B384736233251E8F09AD79DFBE2C6ABFADAC8E4D8624318C27DAF1DF0314F527081CF371DD7E1FD4FA414A665036E0F5E6E5DF040103DF05083230323531323331DF060101DF070101\n"
                        + "9F0605A0000000039F220101DF028180C696034213D7D8546984579D1D0F0EA519CFF8DEFFC429354CF3A871A6F7183F1228DA5C7470C055387100CB935A712C4E2864DF5D64BA93FE7E63E71F25B1E5F5298575EBE1C63AA617706917911DC2A75AC28B251C7EF40F2365912490B939BCA2124A30A28F54402C34AECA331AB67E1E79B285DD5771B5D9FF79EA630B75DF0314D34A6A776011C7E7CE3AEC5F03AD2F8CFC5503CCDF040103DF05083230303931323331DF060101DF070101\n"
                        + "9F0605A0000000039F220107DF028190A89F25A56FA6DA258C8CA8B40427D927B4A1EB4D7EA326BBB12F97DED70AE5E4480FC9C5E8A972177110A1CC318D06D2F8F5C4844AC5FA79A4DC470BB11ED635699C17081B90F1B984F12E92C1C529276D8AF8EC7F28492097D8CD5BECEA16FE4088F6CFAB4A1B42328A1B996F9278B0B7E3311CA5EF856C2F888474B83612A82E4E00D0CD4069A6783140433D50725FDF0314B4BC56CC4E88324932CBC643D6898F6FE593B172DF040103DF05083230313231323331DF060101DF070101\n"
                        + "9F0605A0000000039F220108DF0281B0D9FD6ED75D51D0E30664BD157023EAA1FFA871E4DA65672B863D255E81E137A51DE4F72BCC9E44ACE12127F87E263D3AF9DD9CF35CA4A7B01E907000BA85D24954C2FCA3074825DDD4C0C8F186CB020F683E02F2DEAD3969133F06F7845166ACEB57CA0FC2603445469811D293BFEFBAFAB57631B3DD91E796BF850A25012F1AE38F05AA5C4D6D03B1DC2E568612785938BBC9B3CD3A910C1DA55A5A9218ACE0F7A21287752682F15832A678D6E1ED0BDF031420D213126955DE205ADC2FD2822BD22DE21CF9A8DF040103DF05083230313431323331DF060101DF070101\n"
                        + "9F0605A0000000039F220109DF0281F89D912248DE0A4E39C1A7DDE3F6D2588992C1A4095AFBD1824D1BA74847F2BC4926D2EFD904B4B54954CD189A54C5D1179654F8F9B0D2AB5F0357EB642FEDA95D3912C6576945FAB897E7062CAA44A4AA06B8FE6E3DBA18AF6AE3738E30429EE9BE03427C9D64F695FA8CAB4BFE376853EA34AD1D76BFCAD15908C077FFE6DC5521ECEF5D278A96E26F57359FFAEDA19434B937F1AD999DC5C41EB11935B44C18100E857F431A4A5A6BB65114F174C2D7B59FDF237D6BB1DD0916E644D709DED56481477C75D95CDD68254615F7740EC07F330AC5D67BCD75BF23D28A140826C026DBDE971A37CD3EF9B8DF644AC385010501EFC6509D7A41DF03141FF80A40173F52D7D27E0F26A146A1C8CCB29046DF040103DF05083230313631323331DF060101DF070101\n"
                        + "9F0605A0000000659F220109DF028180B72A8FEF5B27F2B550398FDCC256F714BAD497FF56094B7408328CB626AA6F0E6A9DF8388EB9887BC930170BCC1213E90FC070D52C8DCD0FF9E10FAD36801FE93FC998A721705091F18BC7C98241CADC15A2B9DA7FB963142C0AB640D5D0135E77EBAE95AF1B4FEFADCF9C012366BDDA0455C1564A68810D7127676D493890BDDF03144410C6D51C2F83ADFD92528FA6E38A32DF048D0ADF040103DF05083230303931323331DF060101DF070101\n"
                        + "9F0605A0000000659F220110DF02819099B63464EE0B4957E4FD23BF923D12B61469B8FFF8814346B2ED6A780F8988EA9CF0433BC1E655F05EFA66D0C98098F25B659D7A25B8478A36E489760D071F54CDF7416948ED733D816349DA2AADDA227EE45936203CBF628CD033AABA5E5A6E4AE37FBACB4611B4113ED427529C636F6C3304F8ABDD6D9AD660516AE87F7F2DDF1D2FA44C164727E56BBC9BA23C0285DF0314C75E5210CBE6E8F0594A0F1911B07418CADB5BABDF040103DF05083230323231323331DF060101DF070101\n"
                        + "9F0605A0000000659F220112DF0281B0ADF05CD4C5B490B087C3467B0F3043750438848461288BFEFD6198DD576DC3AD7A7CFA07DBA128C247A8EAB30DC3A30B02FCD7F1C8167965463626FEFF8AB1AA61A4B9AEF09EE12B009842A1ABA01ADB4A2B170668781EC92B60F605FD12B2B2A6F1FE734BE510F60DC5D189E401451B62B4E06851EC20EBFF4522AACC2E9CDC89BC5D8CDE5D633CFD77220FF6BBD4A9B441473CC3C6FEFC8D13E57C3DE97E1269FA19F655215B23563ED1D1860D8681DF0314874B379B7F607DC1CAF87A19E400B6A9E25163E8DF040103DF05083230323431323331DF060101DF070101\n"
                        + "9F0605A0000000659F220114DF0281F8AEED55B9EE00E1ECEB045F61D2DA9A66AB637B43FB5CDBDB22A2FBB25BE061E937E38244EE5132F530144A3F268907D8FD648863F5A96FED7E42089E93457ADC0E1BC89C58A0DB72675FBC47FEE9FF33C16ADE6D341936B06B6A6F5EF6F66A4EDD981DF75DA8399C3053F430ECA342437C23AF423A211AC9F58EAF09B0F837DE9D86C7109DB1646561AA5AF0289AF5514AC64BC2D9D36A179BB8A7971E2BFA03A9E4B847FD3D63524D43A0E8003547B94A8A75E519DF3177D0A60BC0B4BAB1EA59A2CBB4D2D62354E926E9C7D3BE4181E81BA60F8285A896D17DA8C3242481B6C405769A39D547C74ED9FF95A70A796046B5EFF36682DC29DF0314C0D15F6CD957E491DB56DCDD1CA87A03EBE06B7BDF040103DF05083230323631323331DF060101DF070101\n"
                        + "9F0605A0000000049F220103DF028180C2490747FE17EB0584C88D47B1602704150ADC88C5B998BD59CE043EDEBF0FFEE3093AC7956AD3B6AD4554C6DE19A178D6DA295BE15D5220645E3C8131666FA4BE5B84FE131EA44B039307638B9E74A8C42564F892A64DF1CB15712B736E3374F1BBB6819371602D8970E97B900793C7C2A89A4A1649A59BE680574DD0B60145DF03145ADDF21D09278661141179CBEFF272EA384B13BBDF040103DF05083230303931323331DF060101DF070101\n"
                        + "9F0605A0000000049F220104DF028190A6DA428387A502D7DDFB7A74D3F412BE762627197B25435B7A81716A700157DDD06F7CC99D6CA28C2470527E2C03616B9C59217357C2674F583B3BA5C7DCF2838692D023E3562420B4615C439CA97C44DC9A249CFCE7B3BFB22F68228C3AF13329AA4A613CF8DD853502373D62E49AB256D2BC17120E54AEDCED6D96A4287ACC5C04677D4A5A320DB8BEE2F775E5FEC5DF0314381A035DA58B482EE2AF75F4C3F2CA469BA4AA6CDF040103DF05083230313731323331DF060101DF070101\n"
                        + "9F0605A0000000049F220105DF0281B0B8048ABC30C90D976336543E3FD7091C8FE4800DF820ED55E7E94813ED00555B573FECA3D84AF6131A651D66CFF4284FB13B635EDD0EE40176D8BF04B7FD1C7BACF9AC7327DFAA8AA72D10DB3B8E70B2DDD811CB4196525EA386ACC33C0D9D4575916469C4E4F53E8E1C912CC618CB22DDE7C3568E90022E6BBA770202E4522A2DD623D180E215BD1D1507FE3DC90CA310D27B3EFCCD8F83DE3052CAD1E48938C68D095AAC91B5F37E28BB49EC7ED597DF0314EBFA0D5D06D8CE702DA3EAE890701D45E274C845DF040103DF05083230323331323331DF060101DF070101\n"
                        + "9f0605a0000003719f220105df05083230323831323331df060101df070101df0281B0AEAC5DB7F879F5C0DAB6CE358CBD6AA4213D13E7635BE3886CCE9304161D7A9E9FF3C26412957A8318C90602946A297BF5235F7FDD4137B3762D69A25C0889BF839A03C6392CBB325FBC87B892E9C49E8F005F3454D29CD9C545259405E909F040CC2877B6CE9DD1E02D79224C8C3312FB1B223B26414934D0618B7E329C25039C1D98683376637804A2B26F62301EEAD2531D5D3E62BC507EF88FFB15A0101E64A6EA209A72DDA08F4ADAF04E1C52D9DF031470690c8782d517f04d09c5bfd352f6e6043f3069df040103\n"
                        + "9f0605a0000003719f220106df05083230323831323331df060101df070101df0281F8D2DA0134B4DFC93A75EE8960C99896D50A91527B87BA7B16CDB77E5B6FDB750EB70B54026CADDA1D562C77A2C6DA541E94BC415D43E68489B16980F2E887C09E4CF90E2E639B179277BBA0E982CCD1F80521D1457209125B3ABCD309E1B92B5AEDA2EB1CBF933BEAD9CE7365E52B7D17FCB405AA28E5DE6AA3F08E764F745E70859ABCBA41E570A6E4367B3D6FECE723B73ABF3EB53DCDE3816E8A813460447021509D0DFDF2EEEE74CC35485FB55C26836EB3BF9C7DEBEE6C0B77B7BE059233801CF76B321FCA25FB1E63117AE1865E23161EC39D7B1FB84256C2BE72BF8EC771548DB9F00BEF77C509FADA15E2B53FF950D383F96211D3DF0314f5bab84ece5f8bd45511e5ca861b80c7e6c51f55df040103\n"
                        + "9f0605a0000005249f220103df028190e703a908ffae3730f82e550869a294c1ff1da25f2b53d2c8bb18f770dad505135d03d5ec8ee3926550051c3d4857f6fedb882c2889e0b25f389f78741f2931a92d45d3a47e62810d3253653ab0ab3570c35dfd08d3167b6db42ed28f765186f4287cdaf9d9bad20bce2c4ecfecdd218e50f1fcc718878882f3934a6feb502cfcad615a2b2e279a0868dda9489dfa9cd9df03144b93d1e1f57cfa16970501f17d3e06411043f1d5df040103df050420311222df060101df070101\n"
                        + "9f0605a0000005249f220104df0281b0ac0019624fc0a72270c6885cc0b3c9140c351fcfe6f8145881a27750393453d3265f69e7658132d8d253edf8991e2ba32b782d39ade1ff1fc8f211f5df51a0007c761ad9882587bd6a36aecd3abbf944307ac97a2d905fab489c3e1ccd76de9eb93ecfab2bb84f34e770119e356dc6372d8685da8eb92fcac7b53c0167100e4cdfb9830d1c45e787e44c9f6a42ec131a6a4cd66bbe4f93ca91fdf157c7b22fc7221a6348f0eda6151302a80ef77d6ca5df03146f843ce765b9144ce1a6bfea46bc37b65081ce7fdf040103df050420311222df060101df070101\n"
                        + "9f0605a0000005249f220105df0281f8c04e80180369898aaef6ee7741eded25239d765301614b5b41a0096a3009358d626d828bc5f1b1e04a2dc1367101266905d262003be747fd231c9b0011f2f2b21ba8e4c0f4ca5e93ed9dbb2e92abc450576a4eb59ad00dca59c8bf3230e4b19d43452871c6215d837663310df43caea1b9b08c1f500af1b550f62e18d70eee9e9475321bcd1799ab193e0bc849dace892a0e6a1f42fe0786db30345ae1a0e7e4c4b71640e03bfd2832c491a7d83f3b4ef4d388cddbb748c2fd1d9d4a9bf52fc856cba088d4b274846002c23cda722c5cff3b1f8218a1843b0426474bdc92f2f5e31fbf321cc17480ad069df55381f2e601d5cba7b871253fdf03147081df2a0c36360f24c122c574f0ad2e57893dd2df040103df050420311222df060101df070101\n"
                        + "9f0605a0000000259f220103df040103df060101df070101df03148708a3e3bbc1bb0be73ebd8d19d4e5d20166bf6cdf028180b0c2c6e2a6386933cd17c239496bf48c57e389164f2a96bff133439ae8a77b20498bd4dc6959ab0c2d05d0723af3668901937b674e5a2fa92ddd5e78ea9d75d79620173cc269b35f463b3d4aaff2794f92e6c7a3fb95325d8ab95960c3066be548087bcb6ce12688144a8b4a66228ae4659c634c99e36011584c095082a3a3e3\n"
                        + "9f0605a0000000259f22010edf040103df060101df070101df0314a7266abae64b42a3668851191d49856e17f8fbcddf028190aa94a8c6dad24f9ba56a27c09b01020819568b81a026be9fd0a3416ca9a71166ed5084ed91ced47dd457db7e6cbcd53e560bc5df48abc380993b6d549f5196cfa77dfb20a0296188e969a2772e8c4141665f8bb2516ba2c7b5fc91f8da04e8d512eb0f6411516fb86fc021ce7e969da94d33937909a53a57f907c40c22009da7532cb3be509ae173b39ad6a01ba5bb85\n"
                        + "9f0605a0000000259f22010fdf040103df060101df070101df0314a73472b3ab557493a9bc2179cc8014053b12bab4df0281b0c8d5ac27a5e1fb89978c7c6479af993ab3800eb243996fbb2ae26b67b23ac482c4b746005a51afa7d2d83e894f591a2357b30f85b85627ff15da12290f70f05766552ba11ad34b7109fa49de29dcb0109670875a17ea95549e92347b948aa1f045756de56b707e3863e59a6cbe99c1272ef65fb66cbb4cff070f36029dd76218b21242645b51ca752af37e70be1a84ff31079dc0048e928883ec4fadd497a719385c2bbbebc5a66aa5e5655d18034ec5\n"
                        + "9f0605a0000000259f220110df040103df060101df070101df0314c729cf2fd262394abc4cc173506502446aa9b9fddf0281f8cf98dfedb3d3727965ee7797723355e0751c81d2d3df4d18ebab9fb9d49f38c8c4a826b99dc9dea3f01043d4bf22ac3550e2962a59639b1332156422f788b9c16d40135efd1ba94147750575e636b6ebc618734c91c1d1bf3edc2a46a43901668e0ffc136774080e888044f6a1e65dc9aaa8928dacbeb0db55ea3514686c6a732cef55ee27cf877f110652694a0e3484c855d882ae191674e25c296205bbb599455176fdd7bbc549f27ba5fe35336f7e29e68d783973199436633c67ee5a680f05160ed12d1665ec83d1997f10fd05bbdbf9433e8f797aee3e9f02a34228ace927abe62b8b9281ad08d3df5c7379685045d7ba5fcde58637\n"
                        + "9f0605a0000000259f220167df040103df060101df070101df031452a2907300c8445bf54b970c894691feadf2d28edf0281b0c687adccf3d57d3360b174e471eda693aa555dfdc6c8cd394c74ba25ccdf8eabfd1f1ceadfbe2280c9e81f7a058998dc22b7f22576fe84713d0bdd3d34cfcd12fcd0d26901ba74103d075c664dabccaf57bf789494051c5ec303a2e1d784306d3db3eb665cd360a558f40b7c05c919b2f0282fe1ed9bf6261aa814648fbc263b14214491de426d242d65cd1fff0fbe4d4daff5cfacb2adc7131c9b147ee791956551076270696b75fd97373f1fd7804f\n";
        ;

        // Prefer the full table from assets/capk.json - the same file and the
        // same 78 keys the PAX reference loads. The hardcoded strCapk above
        // held about two dozen and was missing Mastercard index 06, which is
        // what current Mastercard cards ask for: without it offline data
        // authentication cannot run (TVR byte 1 bit 8), CDA fails, and the card
        // returns an AAC at GAC2 even after the issuer approved. Fall back to
        // the built-in list only if the asset cannot be read.
        String capkFromAsset = com.hjq.demo.utils.CapkTable.build(getApplicationContext());
        if (!capkFromAsset.isEmpty()) {
            strCapk = capkFromAsset;
            LogUtils.e("CAPK: using assets/capk.json ("
                    + com.hjq.demo.utils.CapkTable.count(getApplicationContext()) + " keys)");
        } else {
            LogUtils.e("CAPK: assets/capk.json unreadable - falling back to the built-in list");
        }

        boolean loadCapkResult = aidManager.saveDevParamAsync(strCapk, packageName, AidManager.PARAM_CAPK);
        LogUtils.e("downloadInitialParameters: loadCapkResult-->" + loadCapkResult);
        // Remember the outcome so a failed load is retried on the next start
        // instead of leaving the terminal permanently without CA public keys.
        mPreferences.setCapkLoaded(loadCapkResult);
        if (loadCapkResult && loadAidResult_CONTACT) {
            // The kernel now holds this build's AID tables, so stop reloading
            // them on every start until they are edited again.
            mPreferences.setAidParamVersionCurrent();
        }

        runOnUiThread(() -> {
            // 更新UI
            if (!loadCapkResult) {
                new TipsDialog.Builder(getContext())
                        .setIcon(TipsDialog.ICON_ERROR)
                        .setMessage(getString(R.string.load_param_fail))
                        .show();
            } else {
                // 设置首次启动标志为false
                mPreferences.setFirstLaunch(false);
            }
        });

    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyboardDevice) {
            LogUtils.i("--------onKeyUp: 820");
            LogUtils.i("--------onKeyUp: keyCode: " + keyCode);
            switch (keyCode) {
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (imagePos != null) {
                        imagePos.setFocusable(true);
                        imagePos.setFocusableInTouchMode(true);
                        imagePos.requestFocus();
                    }
                    LogUtils.d("Confirm button released");
                    return true;
                case KeyEvent.KEYCODE_CLEAR:
                    LogUtils.d("onKeyUp:  KEYCODE_CLEAR");
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    LogUtils.i("onKeyUp:  KEYCODE_DPAD_DOWN");
                    return true;
                default:
                    return super.onKeyUp(keyCode, event);
            }
        }
        LogUtils.e("-------onKeyUp: xxxxxx");
        return super.onKeyUp(keyCode, event);
    }

}
