package com.hjq.demo.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.hjq.demo.R;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.utils.CardMask;
import com.hjq.demo.utils.CardRangeTable;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.pinpad.IPinPadCallback;
import com.szanfu.sdk.api.pinpad.IPinPadResultListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * PIN entry with a box row that starts at four boxes and grows as the
 * cardholder types.
 *
 * <h3>Why this exists</h3>
 * <p>{@code IPinPadBinderService.showPinPad} draws the SDK's own keypad, and
 * that keypad always lays out {@code setPasswordLengthLimit[2]} boxes - the
 * maximum. So {@code {0,4,6}} showed six boxes for a four digit PIN and the
 * host reported a PIN length error, and clamping to {@code {0,4,4}} fixed the
 * display at four and locked out every six digit PIN. There is no parameter
 * that makes the built-in pad grow.</p>
 *
 * <h3>How it works</h3>
 * <p>{@code showUserPinPad} (AFSDKService doc 4.7.4) lets the application draw
 * the keypad and hand the SDK the screen rectangle of every key. The SDK still
 * owns touch capture, the PIN digits and the PIN block - the application only
 * supplies pixels and receives a keypress <em>count</em> through
 * {@link IPinPadResultListener}. No PIN digit ever reaches this class, so the
 * secure boundary is unchanged from {@code showPinPad}.</p>
 *
 * <p>The listener reports 1 for a digit, 10 for cancel, 11 for backspace and 12
 * for confirm. {@link PinBoxView} turns that into a growing row of boxes.</p>
 *
 * <h3>Three ways a PIN block can arrive that nobody asked for</h3>
 * <ol>
 *   <li><b>Stale key rectangles.</b> The SDK is handed one immutable set of
 *   rectangles. Rectangles captured on a layout pass that is later superseded
 *   are offset from what the cardholder sees, so a tap on a digit can land
 *   inside the stored ENTER rectangle. {@link #scheduleStart()} waits until two
 *   consecutive frames produce an identical coordinate string, and
 *   {@link #geometryFault()} refuses to start at all if any two rectangles
 *   overlap or any rectangle is empty.</li>
 *   <li><b>A synthetic touch.</b> FindCardActivity used to call
 *   {@code setSimulateTouch()} immediately before opening this pad, which fires
 *   a real ACTION_DOWN/ACTION_UP at {@code (width / 1.2, height / 1.2)} 200 ms
 *   later - the lower right of the screen, where ENTER sits. That call is gone;
 *   {@link #submitFault()} is the net under it, rejecting any confirm reported
 *   sooner than {@link #MIN_HUMAN_CONFIRM_MS} after the pad opened.</li>
 *   <li><b>The pad's own timeout.</b> The SDK is reported to return a PIN block
 *   rather than calling {@code onTimeout} when its clock runs out, which put an
 *   unattended terminal's sale on the wire. See
 *   {@link #OWN_TIMEOUT_LEAD_SEC}.</li>
 * </ol>
 *
 * <h3>Key coordinate format</h3>
 * <p>{@code virtualKeyCoordinates} is a hex string of 5 x 2-byte values per
 * key: {@code code, x1, y1, x2, y2}. Codes are {@code 0000}-{@code 0009} for
 * digits, {@code 000A} cancel, {@code 000B} backspace, {@code 000C} confirm.
 * Every key must be present. Coordinates here are absolute screen pixels taken
 * from {@link View#getLocationOnScreen}.</p>
 *
 * <h3>If it misbehaves on your hardware</h3>
 * <p>{@code onPinPadError(-5018)} means the SDK rejected the coordinate string.
 * The pad then falls back to {@code showPinPad} automatically, so a sale is
 * never blocked by this UI. Set {@link #setEnabled(boolean)} to false to skip
 * the custom pad entirely.</p>
 */
public final class DynamicPinPad extends Dialog {

    private static final String TAG = "DynamicPinPad";

    /** Special key codes understood by showUserPinPad. */
    private static final int KEY_CANCEL = 0x0A;
    private static final int KEY_CLEAR = 0x0B;
    private static final int KEY_CONFIRM = 0x0C;

    /** Real-time status values from IPinPadResultListener. */
    /**
     * The clear key's status on this terminal.
     *
     * <p>Documented as Cancel. Filmed at four frames a second with the
     * operator's finger on the amber key, the row counts <em>up</em> one dot
     * per press, so whatever this key sends was being read as a digit - and
     * the pad never cancelled, so it is not a cancel. A real cancel is not
     * read from here anyway: it arrives on
     * {@code IPinPadCallback.onCancelPasswordEntry} and closes the pad.</p>
     */
    /**
     * How clickPinPadResult numbers the keys.
     *
     * <p>Two readings are in play and the firmware has never told us which it
     * uses. Doc 4.7.4 says a digit reports its value plus one (so 0..9 -> 1..10)
     * with Cancel 10, Backspace 11, Confirm 12 - note that Cancel and digit 9
     * collide at 10 in the document's own table. The other reading is the
     * position of the key in the coordinate string we hand over, 1-based:
     * ten digits 1..10, then Cancel 11, Backspace 12, Confirm 13, which is the
     * order this class builds mKeys in.</p>
     *
     * <p>The ranges below are chosen to be correct under BOTH readings for the
     * two things that matter - a digit must add a dot and Backspace must take
     * one away:</p>
     *
     * <pre>
     *   1..10  digit        (both readings agree; 10 was previously read as
     *                        CLEAR, which is why pressing 0 REMOVED a dot)
     *   11,12  clear        (Backspace is 11 in the doc, 12 by position;
     *                        Cancel and Confirm also fall in here, and both of
     *                        those close the pad in the same breath, so a dot
     *                        disappearing as it dismisses is invisible)
     *   13     confirm      (by position; the doc has no 13)
     * </pre>
     *
     * <p>Set Debug on in the admin panel and the pad prints the raw number of
     * every key on its hint line. One press of the clear key settles which
     * reading this firmware uses, and then these can become exact.</p>
     */

    /**
     * Reported through {@code onPinPadError} when a PIN block arrives that the
     * cardholder did not confirm. See {@link #submitFault()}.
     */
    public static final int ERR_UNCONFIRMED_PIN = -9001;

    /** How many frames to wait for the layout to settle before giving up. */
    private static final int MAX_LAYOUT_SETTLE_TRIES = 12;
    private static final long LAYOUT_SETTLE_STEP_MS = 40L;

    /**
     * A confirm reported sooner than this after the pad opened did not come
     * from a person. Nobody reads an amount, types four digits and presses
     * ENTER inside four tenths of a second.
     */
    private static final long MIN_HUMAN_CONFIRM_MS = 400L;

    /**
     * Whether a PIN block with no confirm keypress behind it is refused.
     *
     * <p>Off, because this terminal's firmware does not report the confirm key
     * through {@link IPinPadResultListener} at all. It reports digits - the box
     * row fills in correctly - and says nothing when ENTER is pressed, so the
     * check refused every genuine PIN sale on this hardware.</p>
     *
     * <p>Turn it on only after a log has been seen with
     * {@code sawConfirm=true} on a real press. Until then the terminal is
     * protected by the three things that do work: the synthetic touch is gone
     * from {@code FindCardActivity}, {@link #MIN_HUMAN_CONFIRM_MS} rejects a
     * confirm too fast to be human, and {@link #OWN_TIMEOUT_LEAD_SEC} cancels
     * before the pad's own timeout can answer for the cardholder.</p>
     */
    private static final boolean REQUIRE_CONFIRM_EVENT = false;

    /**
     * How far ahead of the SDK's own deadline this class gives up.
     *
     * <p>{@code IPinPadBinderService} has no cancel entry point. Its methods are
     * showPinPad, showCustomPinPad, showUserPinPad, skipShowPinPad,
     * showPinPadWithRandomKeypad, autoTestShowPinPad, setOption and the key,
     * DES and MAC calls - none of them closes an open pad. So the pad cannot be
     * told to stop, and the only thing the application controls is whether it
     * still believes the answer when it arrives.</p>
     *
     * <p>The pad is reported to return a PIN block on its own timeout instead of
     * calling {@code onTimeout}, which is how a terminal left alone on the PIN
     * screen put a sale on the wire. This class therefore runs its own clock
     * five seconds short of the SDK's, cancels the transaction when it fires,
     * and discards everything the SDK says afterwards. The SDK's own timeout
     * still lands five seconds later and releases its touch capture, but by then
     * the transaction is already dead - here, and through
     * {@code claimPinResult} in FindCardActivity too.</p>
     */
    private static final int OWN_TIMEOUT_LEAD_SEC = 5;

    /**
     * THE KEYPAD SIZE KNOB. Change this one number and nothing else.
     *
     * <p>Fraction of the whole screen height the four keypad rows take. 0.33
     * means a third of the screen; 0.28 is a smaller pad, 0.40 a bigger one.
     * Each 0.01 is roughly 8 px on an 815 px screen, so 0.30 to 0.36 is the
     * useful range.</p>
     *
     * <p>The keypad used to be sized as "whatever the card and the PIN boxes
     * left over", which meant every change to the card above it resized the
     * keypad below it. It is now measured against the screen instead, so the
     * card can be redesigned freely and the keypad does not move. The leftover
     * space goes into a single gap between the PIN boxes and the pad, which
     * keeps the pad flat against the bottom edge.</p>
     */
    private static float sKeypadFraction = 0.33f;

    /** Change the keypad size at runtime, e.g. from a parameters screen. */
    public static void setKeypadFraction(float fraction) {
        sKeypadFraction = Math.max(0.20f, Math.min(0.50f, fraction));
    }

    /** Never smaller or larger than this, whatever the fraction works out to. */
    private static final int KEY_ROW_MIN_DP = 40;
    private static final int KEY_ROW_MAX_DP = 60;

    /**
     * Header sizes, as fractions of the screen height rather than in dp or sp.
     *
     * <p>This terminal does not report the density a phone would, so a size in
     * dp came out as a handful of pixels on an 815 px screen - which is why the
     * logo and the headings read as tiny. A fraction of the screen is the same
     * physical size whatever the device claims its density is, so these hold
     * their proportions on the AF930 and on anything else this is built for.</p>
     *
     * <p>The logo is three times the dp version. The three text sizes went to
     * three times as well and read as shouting - type does not scale with a
     * picture, and a heading three times too big just eats the screen the card
     * and the keypad need. They sit at roughly double instead, which is the
     * size they should have been all along. Adjust one number to resize one
     * thing.</p>
     */
    private static final float LOGO_H_FRACTION       = 0.132f;
    private static final float TITLE_TEXT_FRACTION   = 0.033f;
    private static final float AMOUNT_LABEL_FRACTION = 0.030f;
    private static final float AMOUNT_VALUE_FRACTION = 0.030f;

    /** Master switch. Turn off to fall straight back to the built-in pad. */
    private static boolean sEnabled = true;

    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public static boolean isEnabled() {
        return sEnabled;
    }

    // ------------------------------------------------------------------ entry

    /**
     * Show the PIN pad.
     *
     * <p>Drop-in for {@code pinpad.showPinPad(keyIndex, lengthLimit, hasCancel,
     * amount, pan, 0, timeout, ucPinType, callback)} - same arguments, same
     * callback contract. Falls back to that call if anything here fails.</p>
     *
     * @param minLen minimum PIN digits, 4 per ISO 9564
     * @param maxLen maximum PIN digits, 12 per ISO 9564
     * @return the dialog, or null when the built-in pad was used instead
     */
    public static DynamicPinPad show(Activity activity, IPinPadBinderService pinpad,
                                     int keyIndex, int minLen, int maxLen,
                                     boolean hasCancel, String amount, String pan,
                                     int encryptionMode, int timeoutSec, int ucPinType,
                                     IPinPadCallback callback) {
        return show(activity, pinpad, keyIndex, minLen, maxLen, hasCancel, amount, pan,
                encryptionMode, timeoutSec, ucPinType, null, callback);
    }

    /**
     * @param title what the cardholder is being asked to authorise - "SALE",
     *              "EMI SALE", "REFUND". Shown across the top of the card so a
     *              PIN is never typed into an unlabelled screen.
     */
    public static DynamicPinPad show(Activity activity, IPinPadBinderService pinpad,
                                     int keyIndex, int minLen, int maxLen,
                                     boolean hasCancel, String amount, String pan,
                                     int encryptionMode, int timeoutSec, int ucPinType,
                                     String title, IPinPadCallback callback) {
        return show(activity, pinpad, keyIndex, minLen, maxLen, hasCancel, amount, pan,
                encryptionMode, timeoutSec, ucPinType, title, null, callback);
    }

    /**
     * @param entry how the card was read - CHIP, NFC, SWIPE - for the bottom
     *              left of the card face, the same slot the confirmation
     *              dialog uses
     */
    public static DynamicPinPad show(Activity activity, IPinPadBinderService pinpad,
                                     int keyIndex, int minLen, int maxLen,
                                     boolean hasCancel, String amount, String pan,
                                     int encryptionMode, int timeoutSec, int ucPinType,
                                     String title, String entry, IPinPadCallback callback) {
        return show(activity, pinpad, keyIndex, minLen, maxLen, hasCancel, amount, pan,
                encryptionMode, timeoutSec, ucPinType, title, entry, null, callback);
    }

    /**
     * @param term the instalment term, "12 MONTHS", or null on an ordinary
     *             sale. A cardholder typing a PIN for a 12 month plan should be
     *             able to see the term at the moment they authorise it.
     */
    public static DynamicPinPad show(Activity activity, IPinPadBinderService pinpad,
                                     int keyIndex, int minLen, int maxLen,
                                     boolean hasCancel, String amount, String pan,
                                     int encryptionMode, int timeoutSec, int ucPinType,
                                     String title, String entry, String term,
                                     IPinPadCallback callback) {
        int[] limit = lengthLimit(minLen, maxLen);
        if (hostEnded(activity) && activity != null) {
            // The transaction ended before the pad could open. Never put the
            // system "Secure Pinpad" over the next screen for it.
            skipSilently(pinpad, keyIndex, limit, hasCancel, amount, pan,
                    encryptionMode, ucPinType, callback, "screen already closing");
            return null;
        }
        if (!sEnabled || activity == null || pinpad == null) {
            fallback(pinpad, keyIndex, limit, hasCancel, amount, pan,
                    encryptionMode, timeoutSec, ucPinType, callback);
            return null;
        }
        try {
            DynamicPinPad d = new DynamicPinPad(activity, pinpad, keyIndex, limit,
                    hasCancel, amount, pan, encryptionMode, timeoutSec, ucPinType,
                    title, entry, term, callback);
            d.show();
            return d;
        } catch (Throwable t) {
            Log.e(TAG, "custom PIN pad failed to start (" + t + ") - using the built-in pad");
            fallback(pinpad, keyIndex, limit, hasCancel, amount, pan,
                    encryptionMode, timeoutSec, ucPinType, callback);
            return null;
        }
    }

    /**
     * {@code {allowEmptyPin, min, max}}.
     *
     * <p>The first element MUST be 0. With 1 the pad accepts an empty PIN and
     * returns an ISO 9564 format 0 block whose length nibble is 0; the acquirer
     * reads that as "PIN is fewer than 4 or more than 12 digits" and rejects it
     * (Thales command DA, error 24). AmountActivity used to pass 1 here.</p>
     */
    public static int[] lengthLimit(int minLen, int maxLen) {
        int lo = Math.max(4, Math.min(12, minLen));
        int hi = Math.max(lo, Math.min(12, maxLen));
        return new int[]{0, lo, hi};
    }

    /** True once the screen that asked for the PIN is closing or gone. */
    private static boolean hostEnded(Activity host) {
        return host != null && (host.isFinishing() || host.isDestroyed());
    }

    /**
     * The built-in pad, but only while the transaction is still on screen.
     *
     * <p>The custom pad can give up on its own a moment after the sale was
     * cancelled - the layout settle retries run on a timer, and a late -5018
     * arrives from the SDK. Falling back then put the system "Secure Pinpad"
     * over the home screen with no transaction behind it.</p>
     */
    private static void fallbackUnlessEnded(Activity host, IPinPadBinderService pinpad,
                                            int keyIndex, int[] limit, boolean hasCancel,
                                            String amount, String pan, int encryptionMode,
                                            int timeoutSec, int ucPinType,
                                            IPinPadCallback callback) {
        if (hostEnded(host)) {
            skipSilently(pinpad, keyIndex, limit, hasCancel, amount, pan,
                    encryptionMode, ucPinType, callback, "transaction already ended");
            return;
        }
        fallback(pinpad, keyIndex, limit, hasCancel, amount, pan,
                encryptionMode, timeoutSec, ucPinType, callback);
    }

    /**
     * End the kernel's PIN step with no pad on screen.
     *
     * <p>{@code skipShowPinPad(..., iSkipShowPinPad = true, ...)} answers the
     * kernel without drawing anything. Whatever it reports goes to the host,
     * which has already ended the transaction and ignores it. If the SDK
     * refuses the call the host is told the entry was cancelled - still no
     * pad.</p>
     */
    private static void skipSilently(IPinPadBinderService pinpad, int keyIndex, int[] limit,
                                     boolean hasCancel, String amount, String pan,
                                     int encryptionMode, int ucPinType,
                                     IPinPadCallback callback, String why) {
        Log.w(TAG, "PIN pad not shown (" + why + ") - skipping the PIN step silently");
        try {
            if (pinpad != null) {
                pinpad.skipShowPinPad(keyIndex, limit, hasCancel,
                        amount == null ? "" : amount, pan == null ? "" : pan,
                        encryptionMode, 1, ucPinType, true, callback);
                return;
            }
        } catch (Throwable t) {
            Log.e(TAG, "skipShowPinPad refused (" + t + ") - reporting a cancel instead");
        }
        try {
            if (callback != null) {
                callback.onCancelPasswordEntry();
            }
        } catch (Throwable ignored) {
            // host is gone
        }
    }

    private static void fallback(IPinPadBinderService pinpad, int keyIndex, int[] limit,
                                 boolean hasCancel, String amount, String pan,
                                 int encryptionMode, int timeoutSec, int ucPinType,
                                 IPinPadCallback callback) {
        if (pinpad == null) {
            return;
        }
        try {
            pinpad.showPinPad(keyIndex, limit, hasCancel, amount, pan,
                    encryptionMode, timeoutSec, ucPinType, callback);
        } catch (RemoteException e) {
            Log.e(TAG, "built-in showPinPad also failed: " + e.getMessage());
            try {
                callback.onPinPadError(-1);
            } catch (RemoteException ignored) {
                // nothing more we can do
            }
        }
    }

    // ------------------------------------------------------------------ theme

    private static final int PAGE_BG        = 0xFFEEF1F6;
    private static final int KEY_FACE       = 0xFFFFFFFF;
    private static final int KEY_EDGE       = 0xFFD5DCE8;
    private static final int KEY_TEXT       = 0xFF16202F;
    private static final int ACT_CANCEL     = 0xFFE5484D;
    private static final int ACT_CLEAR      = 0xFFF59E0B;
    private static final int ACT_CONFIRM    = 0xFF1E8E3E;

    // ------------------------------------------------------------------ state

    private final IPinPadBinderService mPinPad;
    private final int mKeyIndex;
    private final int[] mLimit;
    private final boolean mHasCancel;
    private final String mAmount;
    private final String mPan;
    private final int mEncryptionMode;
    private final int mTimeoutSec;
    private final int mUcPinType;
    private final String mTitle;
    private final String mEntry;
    private final String mTerm;
    private final IPinPadCallback mOuter;

    private LinearLayout mRoot;
    private LinearLayout mPad;
    private int mPadH;
    private boolean mPadFitted;
    private PinBoxView mBoxes;
    private TextView mHint;
    /** A warning raised by the transaction (card removed); keeps the hint line. */
    private volatile String mNotice;
    private final List<KeyView> mKeys = new ArrayList<>();
    private boolean mStarted;
    private volatile boolean mFinished;

    /** Coordinate string from the previous frame, for the settle check. */
    private String mLastCoords;
    private int mSettleTries;
    /** The rectangles actually handed to the SDK, for the post-start audit. */
    private String mLiveCoords;

    /** True once the listener has reported an ENTER press. */
    private volatile boolean mSawConfirm;
    /** True once the listener has reported at least one digit. */
    private volatile boolean mSawDigit;
    /** uptimeMillis when the SDK was armed and when ENTER was reported. */
    private volatile long mArmedAtMs;
    private volatile long mConfirmAtMs;

    private final Handler mClock = new Handler(Looper.getMainLooper());
    /** The screen that asked for the PIN; a fallback never outlives it. */
    private final Activity mHost;
    private Runnable mOwnTimeout;
    private volatile boolean mExpired;

    private DynamicPinPad(Activity activity, IPinPadBinderService pinpad, int keyIndex,
                          int[] limit, boolean hasCancel, String amount, String pan,
                          int encryptionMode, int timeoutSec, int ucPinType,
                          String title, String entry, String term,
                          IPinPadCallback callback) {
        super(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        mHost = activity;
        mPinPad = pinpad;
        mKeyIndex = keyIndex;
        mLimit = limit;
        mHasCancel = hasCancel;
        mAmount = amount == null ? "" : amount;
        mPan = pan == null ? "" : pan;
        mEncryptionMode = encryptionMode;
        mTimeoutSec = timeoutSec;
        mUcPinType = ucPinType;
        mTitle = title == null || title.trim().isEmpty()
                ? "SALE" : title.trim().toUpperCase(Locale.US);
        mEntry = entry == null ? "" : entry.trim().toUpperCase(Locale.US);
        mTerm = term == null ? "" : term.trim().toUpperCase(Locale.US);
        mOuter = callback;
    }

    // ------------------------------------------------------------------- view

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0xFFFFFFFF));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
        }
        setContentView(buildUi(getContext()));

        // The SDK needs the on-screen rectangle of every key, so it can only be
        // started once layout has actually happened - and only once the layout
        // has stopped moving. See scheduleStart().
        final View root = findViewById(android.R.id.content);
        final View target = root != null ? root : mBoxes;
        target.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (mStarted) {
                            auditGeometryAfterStart();
                            return;
                        }
                        if (!keysLaidOut()) {
                            return;   // not laid out yet
                        }
                        scheduleStart();
                    }
                });
    }

    private View buildUi(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        TerminalConfig tc = TerminalConfig.get(c);

        final int screenH = c.getResources().getDisplayMetrics().heightPixels;

        mRoot = new LinearLayout(c);
        mRoot.setOrientation(LinearLayout.VERTICAL);
        mRoot.setBackgroundColor(0xFFFFFFFF);
        mRoot.setPadding(0, 0, 0, (int) (10 * d));

        // ==================================================================
        // 1. The bank's lockup, then what the cardholder is authorising.
        //
        // Same band as the EMI entry screen: the asset is 3289x621 with a white
        // ground, so it goes on white at a fixed 40dp height and wraps its own
        // width. This dialog has no fitsSystemWindows to hand it the status bar
        // inset, so the inset is read and added to the top padding instead.
        // ==================================================================
        int statusBar = 0;
        int barId = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (barId > 0) {
            statusBar = c.getResources().getDimensionPixelSize(barId);
        }

        LinearLayout band = new LinearLayout(c);
        band.setOrientation(LinearLayout.VERTICAL);
        band.setGravity(Gravity.CENTER);
        band.setBackgroundColor(0xFFFFFFFF);
        band.setPadding(0, statusBar + (int) (14 * d), 0, (int) (12 * d));

        ImageView logo = new ImageView(c);
        logo.setImageResource(R.drawable.bank_logo);
        logo.setAdjustViewBounds(true);
        logo.setMaxWidth(c.getResources().getDisplayMetrics().widthPixels
                - (int) (24 * d));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        logo.setContentDescription("Dutch-Bangla Bank");
        int logoH = Math.round(screenH * LOGO_H_FRACTION);
        band.addView(logo, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, logoH));

        TextView tvTitle = new TextView(c);
        tvTitle.setText(mTitle);
        tvTitle.setTextColor(0xFF4A6CF7);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX, screenH * TITLE_TEXT_FRACTION);
        tvTitle.getPaint().setFakeBoldText(true);
        tvTitle.setLetterSpacing(0.04f);
        band.addView(tvTitle, lp(LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, (int) (8 * d), 0, 0));

        mRoot.addView(band, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Everything below the band is inset from the screen edge.
        LinearLayout content = new LinearLayout(c);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int) (14 * d), (int) (14 * d), (int) (14 * d), 0);
        mRoot.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // ==================================================================
        // 2. The card - the same markup the confirmation dialog draws.
        //
        // Inflated from view_card_face, which dialog_card_confirm includes, so
        // there is one copy of the card in the project and the two screens
        // cannot drift apart.
        // ==================================================================
        View cardFace = LayoutInflater.from(c).inflate(R.layout.view_card_face, content, false);
        fillCardFace(c, tc, cardFace);
        content.addView(cardFace, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, (int) (14 * d)));

        // ==================================================================
        // 3. Amount, then the PIN boxes.
        // ==================================================================
        LinearLayout amountRow = new LinearLayout(c);
        amountRow.setOrientation(LinearLayout.HORIZONTAL);
        amountRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView amountLabel = new TextView(c);
        amountLabel.setText("Amount :");
        amountLabel.setTextColor(0xFF44506A);
        amountLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                screenH * AMOUNT_LABEL_FRACTION);
        amountLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        amountLabel.getPaint().setFakeBoldText(true);
        amountLabel.setIncludeFontPadding(false);
        amountRow.addView(amountLabel, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView amountValue = new TextView(c);
        amountValue.setText(mAmount.isEmpty() ? "TK" : mAmount + " TK");
        amountValue.setTextColor(0xFF12203A);
        amountValue.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                screenH * AMOUNT_VALUE_FRACTION);
        amountValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        amountValue.getPaint().setFakeBoldText(true);
        amountValue.setIncludeFontPadding(false);
        amountValue.setGravity(Gravity.END);
        amountRow.addView(amountValue, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        content.addView(amountRow, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0,
                mTerm.isEmpty() ? (int) (12 * d) : (int) (4 * d)));

        // The instalment term, when there is one. Same row shape as the
        // amount, so the two read as one block: what is being paid, and over
        // how long.
        if (!mTerm.isEmpty()) {
            LinearLayout termRow = new LinearLayout(c);
            termRow.setOrientation(LinearLayout.HORIZONTAL);
            termRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView termLabel = new TextView(c);
            termLabel.setText("Tenure :");
            termLabel.setTextColor(0xFF44506A);
            termLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    screenH * AMOUNT_LABEL_FRACTION);
            termLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            termLabel.getPaint().setFakeBoldText(true);
            termLabel.setIncludeFontPadding(false);
            termRow.addView(termLabel, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView termValue = new TextView(c);
            termValue.setText(mTerm);
            termValue.setTextColor(0xFF12203A);
            termValue.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    screenH * AMOUNT_VALUE_FRACTION);
            termValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            termValue.getPaint().setFakeBoldText(true);
            termValue.setIncludeFontPadding(false);
            termValue.setGravity(Gravity.END);
            termRow.addView(termValue, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            content.addView(termRow, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, (int) (12 * d)));
        }

        mHint = new TextView(c);
        mHint.setText("ENTER PIN (" + mLimit[1] + "-" + mLimit[2] + " digits)");
        mHint.setTextColor(0xFF44506A);
        mHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        mHint.getPaint().setFakeBoldText(true);
        mHint.setLetterSpacing(0.04f);
        mHint.setGravity(Gravity.CENTER);
        content.addView(mHint, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, (int) (8 * d)));

        // No frame around the boxes. A full width bordered box with four small
        // squares floating in the middle of it was the worst thing on the
        // screen; the squares carry their own outline and read better alone.
        mBoxes = new PinBoxView(c);
        mBoxes.setMinBoxes(mLimit[1]);
        mBoxes.setMaxBoxes(mLimit[2]);
        content.addView(mBoxes, lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 0, 0, 0, (int) (10 * d)));

        // ==================================================================
        // 4. Keypad - digits left, actions right, sized against the screen
        //    rather than against what is left over. See sKeypadFraction.
        // ==================================================================
        // The keypad sits on a grey tray. White keys on a white page have no
        // edge to them, and this screen has no divider under the header, so the
        // tray is the only thing separating the two halves.
        View gap = new View(c);
        gap.setBackgroundColor(PAGE_BG);
        mRoot.addView(gap, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        int rowH = Math.round(screenH * sKeypadFraction / 4f);
        rowH = Math.max((int) (KEY_ROW_MIN_DP * d),
                Math.min((int) (KEY_ROW_MAX_DP * d), rowH));
        mPadH = rowH * 4;
        Log.i(TAG, "header: logoH=" + logoH + "px title="
                + Math.round(screenH * TITLE_TEXT_FRACTION) + "px amountLabel="
                + Math.round(screenH * AMOUNT_LABEL_FRACTION) + "px amountValue="
                + Math.round(screenH * AMOUNT_VALUE_FRACTION) + "px");
        Log.i(TAG, "keypad: screenH=" + screenH + "px density=" + d
                + " fraction=" + sKeypadFraction + " -> rowH=" + rowH
                + "px padH=" + mPadH + "px (" + Math.round(100f * mPadH / screenH)
                + "% of the screen)");

        mPad = new LinearLayout(c);
        mPad.setOrientation(LinearLayout.HORIZONTAL);
        mPad.setBackgroundColor(PAGE_BG);
        mPad.setPadding((int) (10 * d), 0, (int) (10 * d), 0);
        mRoot.addView(mPad, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, mPadH));

        boolean isDynamic = tc.pinPadDynamic();
        Log.i(TAG, "buildUi: pinPadDynamic = " + isDynamic
                + " -> layout: " + (isDynamic ? "DYNAMIC (shuffled)" : "STATIC (1-2-3 order)"));
        int[] n = digitOrder(isDynamic);

        LinearLayout digitsCol = new LinearLayout(c);
        digitsCol.setOrientation(LinearLayout.VERTICAL);
        mPad.addView(digitsCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 3f));

        digitsCol.addView(keyRow(c, new int[]{n[0], n[1], n[2]}), rowLp(1f));
        digitsCol.addView(keyRow(c, new int[]{n[3], n[4], n[5]}), rowLp(1f));
        digitsCol.addView(keyRow(c, new int[]{n[6], n[7], n[8]}), rowLp(1f));
        // The tenth digit sits in the middle column, the width of one key, so
        // the grid stays a grid. A full width key on the bottom row looked like
        // a mistake.
        digitsCol.addView(centredKeyRow(c, n[9]), rowLp(1f));

        LinearLayout actionCol = new LinearLayout(c);
        actionCol.setOrientation(LinearLayout.VERTICAL);
        mPad.addView(actionCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f));

        actionCol.addView(keyRow(c, new int[]{KEY_CANCEL}), rowLp(1f));
        actionCol.addView(keyRow(c, new int[]{KEY_CLEAR}), rowLp(1f));
        actionCol.addView(keyRow(c, new int[]{KEY_CONFIRM}), rowLp(2f));

        return mRoot;
    }

    /**
     * Fill the shared card face.
     *
     * <p>Same fields, same masking and same rules as
     * {@code CardConfirmDialog}: the PAN through {@link CardMask} at whatever
     * the masking parameter says, the expiry always {@code **}{@code /}{@code
     * **} because PAN plus expiry is most of a card-not-present transaction and
     * this screen is held up in front of a counter, and the scheme from the
     * card range table so TAKAPAY and NEXUSPAY show as themselves.</p>
     */
    private void fillCardFace(Context c, TerminalConfig tc, View card) {
        String digits = mPan.replaceAll("[^0-9]", "");

        TextView panView = card.findViewById(R.id.cc_pan);
        String shown = CardMask.maskGrouped(digits, tc.cardMask());
        panView.setText(shown);
        // A 19-digit PAN, grouped and letter-spaced, is wider than the card at
        // 21sp. Step the size down rather than let it clip.
        if (shown.length() > 22) {
            panView.setTextSize(16);
        } else if (shown.length() > 19) {
            panView.setTextSize(18);
        }

        ((TextView) card.findViewById(R.id.cc_expiry)).setText("**/**");
        ((TextView) card.findViewById(R.id.cc_issuer))
                .setText(tc.merchantName().toUpperCase(Locale.US));
        ((TextView) card.findViewById(R.id.cc_entry))
                .setText(mEntry.isEmpty()
                        ? (mUcPinType == 1 ? "ONLINE PIN" : "OFFLINE PIN") : mEntry);

        String scheme = digits.isEmpty() ? "" : CardRangeTable.nameFor(c, digits);
        TextView brand = card.findViewById(R.id.cc_brand);
        ImageView brandIcon = card.findViewById(R.id.cc_brand_icon);
        String name = scheme == null ? "" : scheme.trim();
        int id = 0;
        if (!name.isEmpty()) {
            try {
                id = c.getResources().getIdentifier("card_brand_"
                                + name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", ""),
                        "drawable", c.getPackageName());
            } catch (Exception ignore) {
                // getIdentifier is best-effort; the text fallback covers it.
            }
        }
        if (id != 0) {
            brandIcon.setImageResource(id);
            brandIcon.setVisibility(View.VISIBLE);
            brand.setText("");
        } else {
            brandIcon.setVisibility(View.GONE);
            brand.setText(name.toUpperCase(Locale.US));
        }
    }

    /**
     * Give back to the card whatever the keypad cannot have.
     *
     * <p>The keypad height is a fraction of the screen, chosen before anything
     * is measured. On a short screen, or with a long merchant name wrapping the
     * card onto an extra line, the total can come out taller than the display -
     * which would push the bottom row of keys off the edge. This runs after the
     * first layout, before the SDK is armed, and trims the keypad by exactly
     * the overflow.</p>
     *
     * @return true when it changed the layout, so the caller waits for another
     *         pass before capturing key rectangles
     */
    private boolean fitKeypad() {
        if (mPadFitted || mPad == null || mRoot == null || mRoot.getHeight() == 0) {
            return false;
        }
        int floor = mRoot.getHeight() - mRoot.getPaddingBottom();
        int overflow = mPad.getBottom() - floor;
        if (overflow <= 0) {
            mPadFitted = true;
            return false;
        }
        float d = mRoot.getResources().getDisplayMetrics().density;
        int min = (int) (4 * KEY_ROW_MIN_DP * d);
        int wanted = Math.max(min, mPadH - overflow);
        mPadFitted = true;
        if (wanted == mPadH) {
            Log.e(TAG, "the screen is " + overflow + "px short even with the keypad"
                    + " at its minimum - the card above it does not fit. Lower"
                    + " sKeypadFraction or shorten the merchant name.");
            return false;
        }
        Log.i(TAG, "keypad trimmed by " + (mPadH - wanted) + "px to fit the screen"
                + " (overflow was " + overflow + "px): padH " + mPadH + " -> " + wanted);
        mPadH = wanted;
        mPad.getLayoutParams().height = mPadH;
        mPad.requestLayout();
        return true;
    }

    /**
     * The ten digits in the order they are laid out.
     *
     * <p>Index 0-8 fill the 3x3 grid, index 9 goes in the centre slot of the
     * bottom row where 0 normally sits. Static returns 1..9,0. Dynamic returns
     * a shuffle of 0..9 from {@link java.security.SecureRandom} - a predictable
     * shuffle would defeat the point of scrambling.</p>
     */
    private static int[] digitOrder(boolean dynamic) {
        int[] order = {1, 2, 3, 4, 5, 6, 7, 8, 9, 0};
        if (!dynamic) {
            return order;
        }
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        for (int i = order.length - 1; i > 0; i--) {
            int j = rnd.nextInt(i + 1);
            int t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        return order;
    }

    private LinearLayout.LayoutParams rowLp(float weight) {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, weight);
    }

    private LinearLayout keyRow(Context c, int[] codes) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int code : codes) {
            row.addView(key(c, code), keyLp(c));
        }
        return row;
    }

    /** One key in the middle third of the row, blank either side. */
    private LinearLayout centredKeyRow(Context c, int code) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(new View(c), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        row.addView(key(c, code), keyLp(c));
        row.addView(new View(c), new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private LinearLayout.LayoutParams keyLp(Context c) {
        float d = c.getResources().getDisplayMetrics().density;
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        int m = (int) (4 * d);
        p.setMargins(m, m, m, m);
        return p;
    }

    private TextView key(Context c, int code) {
        float d = c.getResources().getDisplayMetrics().density;
        TextView t = new TextView(c);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, code <= 9 ? 27 : 14);
        t.getPaint().setFakeBoldText(true);
        t.setText(label(code));
        t.setIncludeFontPadding(false);
        if (code > 9) {
            t.setLetterSpacing(0.06f);
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(10 * d);
        bg.setColor(background(code));
        if (code <= 9) {
            bg.setStroke((int) (1 * d), KEY_EDGE);
        }
        t.setBackground(bg);
        t.setTextColor(foreground(code));

        // Touches are captured by the SDK, not by these views. Leaving them
        // unclickable makes it obvious that this class never sees a digit.
        t.setClickable(false);
        t.setFocusable(false);
        mKeys.add(new KeyView(code, t));
        return t;
    }

    private static String label(int code) {
        switch (code) {
            case KEY_CANCEL:    return "CANCEL";
            case KEY_CLEAR: return "CLEAR";
            case KEY_CONFIRM:   return "ENTER";
            default:            return String.valueOf(code);
        }
    }

    private static int background(int code) {
        switch (code) {
            case KEY_CANCEL:    return ACT_CANCEL;
            case KEY_CLEAR: return ACT_CLEAR;
            case KEY_CONFIRM:   return ACT_CONFIRM;
            default:            return KEY_FACE;
        }
    }

    private static int foreground(int code) {
        return code <= 9 ? KEY_TEXT : Color.WHITE;
    }

    private static LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(l, t, r, b);
        return p;
    }

    /** Show only the last four digits - a full PAN must not sit on screen. */
    private static String maskPan(String pan) {
        String digits = pan == null ? "" : pan.replaceAll("[^0-9]", "");
        if (digits.length() < 4) {
            return "";
        }
        return "**** **** **** " + digits.substring(digits.length() - 4);
    }

    // -------------------------------------------------------------- SDK start

    private boolean keysLaidOut() {
        if (mKeys.size() < 13) {
            return false;
        }
        for (KeyView k : mKeys) {
            if (k.view.getWidth() == 0 || k.view.getHeight() == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Start the SDK only after the coordinate string has been identical on two
     * consecutive frames.
     *
     * <p>{@code onGlobalLayout} fires on the first pass, which for a weighted
     * layout is not always the final pass. Handing the SDK first-pass
     * rectangles is what let a tap on a digit register inside the stored ENTER
     * rectangle, so the pad returned a PIN block the cardholder never
     * confirmed.</p>
     */
    private void scheduleStart() {
        if (mStarted || mFinished || mKeys.isEmpty()) {
            return;
        }
        final View v = mKeys.get(0).view;
        v.removeCallbacks(mSettleCheck);
        v.post(mSettleCheck);
    }

    private final Runnable mSettleCheck = new Runnable() {
        @Override
        public void run() {
            if (mStarted || mFinished) {
                return;
            }
            if (!keysLaidOut()) {
                retry();
                return;
            }
            if (fitKeypad()) {
                mLastCoords = null;   // the keys are about to move
                retry();
                return;
            }
            String coords = buildCoordinates();
            if (!coords.equals(mLastCoords)) {
                mLastCoords = coords;
                retry();
                return;
            }
            // Two identical frames - the layout has settled.
            String fault = geometryFault();
            if (fault != null) {
                Log.e(TAG, "key rectangles are unusable (" + fault
                        + ") - refusing to start the custom pad, using the built-in pad."
                        + " rects=" + describeRects());
                mStarted = true;
                finishWith();
                fallbackUnlessEnded(mHost, mPinPad, mKeyIndex, mLimit, mHasCancel, mAmount, mPan,
                        mEncryptionMode, mTimeoutSec, mUcPinType, mOuter);
                return;
            }
            mStarted = true;
            mLiveCoords = coords;
            startSdkPinPad(coords);
        }

        private void retry() {
            if (++mSettleTries > MAX_LAYOUT_SETTLE_TRIES) {
                Log.e(TAG, "layout never settled after " + MAX_LAYOUT_SETTLE_TRIES
                        + " frames - using the built-in pad rather than risking"
                        + " stale key rectangles. rects=" + describeRects());
                mStarted = true;
                finishWith();
                fallbackUnlessEnded(mHost, mPinPad, mKeyIndex, mLimit, mHasCancel, mAmount, mPan,
                        mEncryptionMode, mTimeoutSec, mUcPinType, mOuter);
                return;
            }
            if (!mKeys.isEmpty()) {
                mKeys.get(0).view.postDelayed(this, LAYOUT_SETTLE_STEP_MS);
            }
        }
    };

    /**
     * @return null when every key has a non-empty rectangle and no two
     *         rectangles intersect, otherwise a short description of the first
     *         problem found
     */
    private String geometryFault() {
        int[][] r = new int[mKeys.size()][4];
        int[] loc = new int[2];
        for (int i = 0; i < mKeys.size(); i++) {
            View v = mKeys.get(i).view;
            v.getLocationOnScreen(loc);
            r[i][0] = loc[0];
            r[i][1] = loc[1];
            r[i][2] = loc[0] + v.getWidth();
            r[i][3] = loc[1] + v.getHeight();
            if (r[i][2] <= r[i][0] || r[i][3] <= r[i][1]) {
                return "key " + label(mKeys.get(i).code) + " has an empty rectangle";
            }
        }
        for (int i = 0; i < r.length; i++) {
            for (int j = i + 1; j < r.length; j++) {
                boolean overlap = r[i][0] < r[j][2] && r[j][0] < r[i][2]
                        && r[i][1] < r[j][3] && r[j][1] < r[i][3];
                if (overlap) {
                    return "key " + label(mKeys.get(i).code) + " overlaps key "
                            + label(mKeys.get(j).code);
                }
            }
        }
        return null;
    }

    /** Re-check after the SDK has been started; the SDK cannot be re-armed. */
    private void auditGeometryAfterStart() {
        if (mFinished || mLiveCoords == null) {
            return;
        }
        String now = buildCoordinates();
        if (!now.equals(mLiveCoords)) {
            Log.e(TAG, "keys moved AFTER the SDK was armed - the rectangles the SDK"
                    + " holds no longer match the screen. armed=" + mLiveCoords
                    + " now=" + now);
        }
    }

    private String describeRects() {
        StringBuilder b = new StringBuilder();
        int[] loc = new int[2];
        for (KeyView k : mKeys) {
            k.view.getLocationOnScreen(loc);
            b.append(label(k.code)).append('[').append(loc[0]).append(',').append(loc[1])
                    .append('-').append(loc[0] + k.view.getWidth()).append(',')
                    .append(loc[1] + k.view.getHeight()).append("] ");
        }
        return b.toString();
    }

    private void startSdkPinPad(String coords) {
        Log.i(TAG, "showUserPinPad keyIndex=" + mKeyIndex
                + " limit={" + mLimit[0] + "," + mLimit[1] + "," + mLimit[2] + "}"
                + " ucPinType=" + mUcPinType + " settleFrames=" + mSettleTries
                + " rects=" + describeRects());
        mArmedAtMs = SystemClock.uptimeMillis();
        startOwnTimeout();
        try {
            mPinPad.showUserPinPad(coords, mListener, mKeyIndex, mLimit, mHasCancel,
                    mAmount, mPan, mEncryptionMode, mTimeoutSec, mUcPinType, mCallback);
        } catch (Throwable t) {
            Log.e(TAG, "showUserPinPad unavailable (" + t + ") - using the built-in pad");
            stopOwnTimeout();
            dismissQuietly();
            fallbackUnlessEnded(mHost, mPinPad, mKeyIndex, mLimit, mHasCancel, mAmount, mPan,
                    mEncryptionMode, mTimeoutSec, mUcPinType, mOuter);
        }
    }

    // ------------------------------------------------------------- own clock

    /** Cancel the sale a few seconds before the SDK's own deadline. */
    private void startOwnTimeout() {
        final int secs = Math.max(10, mTimeoutSec - OWN_TIMEOUT_LEAD_SEC);
        stopOwnTimeout();
        mOwnTimeout = new Runnable() {
            @Override
            public void run() {
                if (mFinished || mExpired) {
                    return;
                }
                mExpired = true;
                Log.e(TAG, "PIN entry timed out after " + secs + "s - cancelling now,"
                        + " ahead of the SDK's own deadline. Anything the pad reports"
                        + " from here on is discarded.");
                finishWith();
                try {
                    mOuter.onTimeout();
                } catch (RemoteException e) {
                    Log.e(TAG, "host refused the timeout: " + e.getMessage());
                }
            }
        };
        mClock.postDelayed(mOwnTimeout, secs * 1000L);
    }

    private void stopOwnTimeout() {
        if (mOwnTimeout != null) {
            mClock.removeCallbacks(mOwnTimeout);
            mOwnTimeout = null;
        }
    }

    /**
     * True when this pad has already given up, so a late answer from the SDK
     * must be dropped rather than acted on.
     */
    private boolean stale(String what) {
        if (mExpired) {
            Log.e(TAG, "ignoring " + what + " - this PIN session already timed out"
                    + " and the transaction was cancelled");
            return true;
        }
        return false;
    }

    /**
     * Build {@code virtualKeyCoordinates}: 20 hex characters per key -
     * {@code code, x1, y1, x2, y2}, each a 2-byte big-endian value.
     */
    private String buildCoordinates() {
        StringBuilder b = new StringBuilder();
        int[] loc = new int[2];
        for (KeyView k : mKeys) {
            k.view.getLocationOnScreen(loc);
            int x1 = loc[0];
            int y1 = loc[1];
            int x2 = x1 + k.view.getWidth();
            int y2 = y1 + k.view.getHeight();
            b.append(hex16(k.code)).append(hex16(x1)).append(hex16(y1))
                    .append(hex16(x2)).append(hex16(y2));
        }
        return b.toString();
    }

    private static String hex16(int v) {
        int clamped = Math.max(0, Math.min(0xFFFF, v));
        return String.format(Locale.US, "%04X", clamped);
    }

    // ---------------------------------------------------------- SDK callbacks

    /**
     * Real-time keypress feed. Digits are reported as a count only - the value
     * of each digit stays inside the SDK.
     */
    private final IPinPadResultListener mListener = new IPinPadResultListener.Stub() {
        @Override
        public void clickPinPadResult(final String clickResult) {
            final int status = parseInt(clickResult);
            Log.i(TAG, "clickPinPadResult=" + status + " at +"
                    + (SystemClock.uptimeMillis() - mArmedAtMs) + "ms");
            // Record what happened HERE, on the binder thread, before anything
            // is posted anywhere.
            //
            // This used to be done inside applyClick, which runs on the UI
            // thread via post(). onGetPinBlock arrives on a binder thread a
            // fraction of a millisecond after the confirm keypress, reads
            // mSawConfirm, and finds it still false because the posted runnable
            // has not been dispatched yet - so a PIN the cardholder really did
            // confirm was refused as unconfirmed. The display update can wait
            // for the UI thread; the audit flags cannot.
            noteKeypress(status);
            post(new Runnable() {
                @Override
                public void run() {
                    applyClick(status);
                }
            });
        }
    };

    /**
     * Record a keypress for the audit, synchronously on whatever thread the SDK
     * called in on. Draws nothing - see {@link #applyClick(int)}.
     */
    private void noteKeypress(int status) {
        // A length report of any kind means the pad is alive and the
        // cardholder is working it. This firmware never reports the confirm
        // key at all - see submitFault - so mSawConfirm is left for firmware
        // that does, and nothing here can set it from a length.
        if (status >= 0) {
            mSawDigit = true;
        }
    }

    /**
     * Redraw the box row from a keypress report.
     *
     * <p><b>{@code clickPinPadResult} reports the PIN LENGTH, not a key.</b>
     * That was settled by a single observation: pressing the clear key with an
     * empty buffer reports {@code 0}. Zero is not a key identifier under any
     * reading of doc 4.7.4 - digits there are 1..10, Cancel 10, Backspace 11,
     * Confirm 12 - but it is exactly what "there are now no digits" looks
     * like. Everything else follows from it and, for the first time, every
     * symptom this pad has ever shown is explained:</p>
     *
     * <ul>
     *   <li>digits appeared to work, because typing from empty reports
     *       1, 2, 3... and "increment" happens to produce the same row;</li>
     *   <li>the clear key <em>added</em> a dot, because clearing from 6 digits
     *       reports 5, and 5 was being read as a digit key and incremented;</li>
     *   <li>the last press before an empty buffer logged
     *       {@code key=0 UNKNOWN}, because 0 matched no key range;</li>
     *   <li>and the very first version of this method - the one that called
     *       {@code setCount(status)} - was right about the arithmetic and
     *       wrong only in the special case it wrapped around it.</li>
     * </ul>
     *
     * <p>So the report is used as what it is: assign it, never add to it. The
     * row cannot drift, because nothing here accumulates - every press
     * restates the whole count. A value above the configured maximum PIN
     * length cannot be a length, so it is left alone as a function key.</p>
     */
    private void applyClick(int status) {
        if (mBoxes == null || mExpired) {
            return;
        }
        int max = mLimit[2];
        if (status >= 0 && status <= max) {
            mBoxes.setCount(status);
        } else if (status > max) {
            // Longer than any PIN this pad accepts, so it is not a length.
            // Confirm and Cancel land here; both close the pad, and neither
            // should disturb the row on its way out.
            Log.i(TAG, "clickPinPadResult=" + status + " is above the "
                    + max + "-digit maximum - function key, row unchanged");
        } else {
            Log.w(TAG, "unparseable clickPinPadResult: " + status);
        }
        int shown = mBoxes.getCount();
        Log.i(TAG, "clickPinPadResult=" + status + " -> boxes now show "
                + shown + " digit" + (shown == 1 ? "" : "s"));
        if (mHint != null && mNotice == null) {
            String msg = shown < mLimit[1]
                    ? "ENTER PIN (" + mLimit[1] + "-" + mLimit[2] + " digits)"
                    : "PRESS ENTER TO CONFIRM";
            mHint.setText(msg);
        }
    }

    /**
     * A digit press.
     *
     * <p>Doc 4.7.4 says digits report the digit plus one, so 1 to 10, and that
     * the clear key reports 11. This terminal does not match that: 10 comes
     * from the clear key here, so the digit range stops at 9. The one case that
     * cannot be told apart is a real 9 on a firmware that does send value + 1 -
     * it would arrive as 10 and take a dot off. The log line above names the
     * raw status, so one capture settles it.</p>
     */
    /**
     * Why this PIN block must not be trusted, or null when it is fine.
     *
     * <p>Two ways a submit can be forged. Either no ENTER was ever reported -
     * checked only when the keypress feed is demonstrably alive, so firmware
     * that reports nothing at all cannot block a real sale - or an ENTER was
     * reported so soon after the pad opened that no person could have produced
     * it.</p>
     */
    /**
     * Wait a moment for a confirm that may still be in flight.
     *
     * <p>{@code clickPinPadResult} and {@code onGetPinBlock} are two separate
     * binder transactions. They are raised in order but there is no guarantee
     * they are <em>delivered</em> in order, so a confirm can still be a few
     * milliseconds behind the PIN block it produced. This is called from the
     * binder thread that carries onGetPinBlock, never from the UI thread.</p>
     */
    private void awaitConfirmEvent() {
        for (int i = 0; i < 20 && !mSawConfirm; i++) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private String submitFault() {
        if (!mSawConfirm) {
            awaitConfirmEvent();
        }
        if (!mSawConfirm) {
            // NOT a refusal. This firmware does not report the confirm key
            // through clickPinPadResult - it reports digits, which is why the
            // boxes fill in correctly, but nothing at all when ENTER is
            // pressed. Refusing on a missing confirm therefore cancelled every
            // genuine PIN sale on this hardware, twice, in production.
            //
            // The absence of a confirm event is not evidence that nobody
            // pressed ENTER, so it cannot be used as one. Logged loudly so the
            // question stays answerable, and so that if a later firmware does
            // start reporting confirms, REQUIRE_CONFIRM_EVENT can be turned
            // back on and this becomes a real check again.
            if (REQUIRE_CONFIRM_EVENT && mSawDigit) {
                return "no ENTER press was ever reported";
            }
            Log.w(TAG, "no confirm keypress was reported for this PIN block"
                    + " (digits=" + (mBoxes == null ? -1 : mBoxes.getCount())
                    + "). This firmware does not report key 12, so the ENTER"
                    + " check cannot run. The protections that do run: the"
                    + " synthetic touch is gone from FindCardActivity, the"
                    + " sub-" + MIN_HUMAN_CONFIRM_MS + "ms floor below, and the"
                    + " app-owned timeout.");
            return null;
        }
        long dt = mConfirmAtMs - mArmedAtMs;
        if (dt < MIN_HUMAN_CONFIRM_MS) {
            return "ENTER arrived " + dt + "ms after the pad opened, under the "
                    + MIN_HUMAN_CONFIRM_MS + "ms floor - not a human press";
        }
        return null;
    }

    /** Wraps the caller's callback so the dialog always closes with the pad. */
    private final IPinPadCallback mCallback = new IPinPadCallback.Stub() {
        @Override
        public void onGetPinBlock(boolean isPasswordFree, String onlinePin) throws RemoteException {
            if (stale("onGetPinBlock")) {
                return;
            }
            int typed = mBoxes == null ? -1 : mBoxes.getCount();
            long dt = SystemClock.uptimeMillis() - mArmedAtMs;
            String fault = submitFault();
            if (!isPasswordFree && fault != null) {
                Log.e(TAG, "REFUSING PIN block: " + fault + " (digits=" + typed
                        + ", sawConfirm=" + mSawConfirm + ", block at +" + dt
                        + "ms) - cancelling instead of going online.");
                finishWith();
                mOuter.onPinPadError(ERR_UNCONFIRMED_PIN);
                return;
            }
            Log.i(TAG, "onGetPinBlock passwordFree=" + isPasswordFree
                    + " digits=" + typed + " sawConfirm=" + mSawConfirm
                    + " confirmAt=+" + (mSawConfirm ? (mConfirmAtMs - mArmedAtMs) : -1)
                    + "ms blockAt=+" + dt + "ms");
            finishWith();
            mOuter.onGetPinBlock(isPasswordFree, onlinePin);
        }

        @Override
        public void onPinPadError(int errCode) throws RemoteException {
            if (stale("onPinPadError " + errCode)) {
                return;
            }
            // -5018 = the SDK rejected virtualKeyCoordinates. That is a layout
            // problem in this class, not a card or key problem, so retry on the
            // built-in pad rather than failing the sale.
            if (errCode == -5018 && !mFinished) {
                Log.e(TAG, "virtualKeyCoordinates rejected (-5018) - using the built-in pad");
                finishWith();
                fallbackUnlessEnded(mHost, mPinPad, mKeyIndex, mLimit, mHasCancel, mAmount, mPan,
                        mEncryptionMode, mTimeoutSec, mUcPinType, mOuter);
                return;
            }
            finishWith();
            mOuter.onPinPadError(errCode);
        }

        @Override
        public void onTimeout() throws RemoteException {
            if (stale("onTimeout")) {
                return;
            }
            finishWith();
            mOuter.onTimeout();
        }

        @Override
        public void onCancelPasswordEntry() throws RemoteException {
            if (stale("onCancelPasswordEntry")) {
                return;
            }
            finishWith();
            mOuter.onCancelPasswordEntry();
        }
    };

    /**
     * Put a warning on the hint line in red, and keep it there.
     *
     * <p>Used when the chip leaves the slot while the PIN is being entered.
     * The SDK pad cannot be closed from the application, so the cardholder is
     * told instead; the transaction itself refuses to go on with a PIN that
     * arrives for a card that is no longer there.</p>
     */
    public void showNotice(final String text) {
        mNotice = text;
        post(new Runnable() {
            @Override
            public void run() {
                if (mHint != null) {
                    mHint.setText(text);
                    mHint.setTextColor(0xFFD32F2F);
                }
            }
        });
    }

    private void finishWith() {
        mFinished = true;
        stopOwnTimeout();
        dismissQuietly();
    }

    private void dismissQuietly() {
        post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (isShowing()) {
                        dismiss();
                    }
                } catch (Throwable ignored) {
                    // the activity may already be gone
                }
            }
        });
    }

    private void post(Runnable r) {
        Context c = getContext();
        if (c instanceof Activity) {
            ((Activity) c).runOnUiThread(r);
        } else if (mBoxes != null) {
            mBoxes.post(r);
        } else {
            r.run();
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s == null ? "" : s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static final class KeyView {
        final int code;
        final View view;

        KeyView(int code, View view) {
            this.code = code;
            this.view = view;
        }
    }

    @Override
    public void dismiss() {
        try {
            super.dismiss();
        } catch (Throwable ignored) {
            // dialog leaked with a finishing activity
        }
    }
}
