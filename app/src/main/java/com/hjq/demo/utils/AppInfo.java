package com.hjq.demo.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import com.hjq.demo.ans8583.TerminalConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * What this terminal is, and what is running on it.
 *
 * <p>The PAX reference answers this in two places - a Version item on the
 * terminal menu and a printed Application Info slip built by {@code
 * ReceiptAppInfo} - and they read the same facts. So do these: {@link
 * #entries(Context)} is the list, {@link AppInfoActivity} shows it and prints
 * it, and neither can say something the other does not.</p>
 *
 * <p>Everything is read from the device. The reason this screen exists is to
 * answer "which build is on this terminal, and what is it running on" during a
 * support call, and a hardcoded answer to that question is worse than none.</p>
 */
public final class AppInfo {

    /** Shown when the platform will not tell us - not the same as empty. */
    public static final String UNAVAILABLE = "NOT AVAILABLE";

    /** Shown while a value is still being fetched from the network. */
    public static final String PENDING = "CHECKING...";

    /**
     * Values that neither Android nor this app can work out on its own.
     *
     * <p>The terminal serial comes from the Anfu SDK and needs a bound service;
     * the SIM numbers come back from a USSD request and need the network. Both
     * arrive after the screen is already up, so they are cached here and read
     * by {@link #entries} - which means the screen and the printed slip show
     * the same values without either of them having to know where they came
     * from.</p>
     */
    private static String sSerial = "";
    private static final String[] sMsisdn = {"", ""};

    private AppInfo() { }

    /** Record the serial the SDK reported. */
    public static synchronized void setSerial(String sn) {
        sSerial = sn == null ? "" : sn.trim();
    }

    /** Record a SIM's own number, once the USSD reply comes back. */
    public static synchronized void setMsisdn(int slot, String number) {
        if (slot >= 0 && slot < sMsisdn.length) {
            sMsisdn[slot] = number == null ? "" : number.trim();
        }
    }

    public static synchronized String msisdn(int slot) {
        return slot >= 0 && slot < sMsisdn.length ? sMsisdn[slot] : "";
    }

    /** One labelled fact. */
    public static final class Item {
        public final String label;
        public final String value;

        public Item(String label, String value) {
            this.label = label;
            this.value = value == null || value.trim().isEmpty() ? "-" : value.trim();
        }
    }

    /**
     * Every fact, in the order the PAX slip prints them.
     *
     * <p>SIM details are last because they are the ones most likely to read
     * "No SIM" - a terminal on wi-fi is a normal terminal, and the block should
     * not lead with an absence.</p>
     */
    public static List<Item> entries(Context ctx) {
        List<Item> o = new ArrayList<>();
        o.add(new Item("NAME", "DUTCH-BANGLA BANK PLC"));
        o.add(new Item("VERSION", version(ctx)));
        o.add(new Item("RELEASE DATE", buildDate(ctx)));
        o.add(new Item("INSTALL DATE", installDate(ctx)));
        o.add(new Item("TERMINAL MODEL", Build.MODEL));
        o.add(new Item("OS VERSION", "Android " + Build.VERSION.RELEASE));
        o.add(new Item("POS SERIAL NO", serial()));
        o.add(new Item("SIM 1 OPERATOR", simOperator(ctx, 0)));
        o.add(new Item("SIM 1 NUMBER", msisdnOrLine1(ctx, 0)));
        o.add(new Item("SIM 1 SERIAL NO", simSerial(ctx, 0)));
        o.add(new Item("SIM 2 OPERATOR", simOperator(ctx, 1)));
        o.add(new Item("SIM 2 NUMBER", msisdnOrLine1(ctx, 1)));
        o.add(new Item("SIM 2 SERIAL NO", simSerial(ctx, 1)));
        return o;
    }

    /**
     * The printed slip: merchant letterhead, the terminal's own identifiers,
     * then the facts above.
     *
     * <p>Built through {@link TxnSlip#letterhead} for the same reason
     * everything else is - there is one letterhead on this terminal, and a
     * second copy of it here would be the third time that mistake was
     * made.</p>
     */
    public static List<Slip.Line> lines(Context ctx) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        List<Slip.Line> o = Slip.list();
        TxnSlip.letterhead(o, tc);
        o.add(Slip.blank());
        o.add(Slip.pair("DATE: " + today(), "TIME: " + now()));
        o.add(Slip.pair("MID: " + tc.mid(), "TID: " + tc.tid()));
        o.add(Slip.pair("BAT NUM: " + tc.batchNo(), ""));
        o.add(Slip.blank());
        o.add(Slip.centre("APPLICATION INFO", Slip.BIG));
        o.add(Slip.rule());
        for (Item it : entries(ctx)) {
            // One fixed-width row rather than a left/right pair: the renderer
            // shrinks any line too wide to fit, one line at a time, so a block
            // built from pairs prints with the values stepping in and out.
            o.add(Slip.text(DetailReport.row(it.label, it.value)));
        }
        o.add(Slip.rule());
        return o;
    }

    // ----------------------------------------------------------------- facts

    public static String version(Context ctx) {
        PackageInfo pi = pkg(ctx);
        if (pi == null) {
            return "";
        }
        return pi.versionName + " (" + versionCode(pi) + ")";
    }

    /** When this build was compiled, from the APK's own timestamp. */
    public static String buildDate(Context ctx) {
        PackageInfo pi = pkg(ctx);
        return pi == null ? "" : stamp(pi.firstInstallTime);
    }

    /** When this build was put on the terminal - the last update, not the first. */
    public static String installDate(Context ctx) {
        PackageInfo pi = pkg(ctx);
        return pi == null ? "" : stamp(pi.lastUpdateTime);
    }

    /**
     * The terminal's serial number.
     *
     * <p>Android stopped handing this out to ordinary apps in Oreo, so on this
     * device it usually needs the READ_PHONE_STATE permission and may still
     * refuse. Refusal is reported rather than dressed up as a blank - a
     * support call needs to know the difference between "no serial" and "the
     * platform would not say".</p>
     */
    public static String serial() {
        // The terminal's own serial, as the Anfu SDK reports it
        // (ISystemBinderService.getSN). That is the number printed on the back
        // of the unit and the one the acquirer's asset register holds.
        // AppInfoActivity fills it in when the service binds.
        if (!sSerial.isEmpty()) {
            return sSerial;
        }
        // Android's own serial, which since Oreo needs READ_PHONE_STATE and
        // usually refuses anyway. Only reached before the SDK has answered, or
        // on a build where the service is not running.
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                String s = Build.getSerial();
                return s == null || s.isEmpty() || Build.UNKNOWN.equals(s) ? UNAVAILABLE : s;
            }
            return Build.SERIAL;
        } catch (SecurityException e) {
            return UNAVAILABLE;
        } catch (Exception e) {
            return UNAVAILABLE;
        }
    }

    /** The carrier on a slot, or "No SIM". No permission needed for the name. */
    public static String simOperator(Context ctx, int slot) {
        try {
            SubscriptionInfo info = subscription(ctx, slot);
            if (info != null && info.getCarrierName() != null
                    && info.getCarrierName().length() > 0) {
                return info.getCarrierName().toString();
            }
            if (slot == 0) {
                TelephonyManager tm = (TelephonyManager)
                        ctx.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY) {
                    String n = tm.getSimOperatorName();
                    if (n != null && !n.isEmpty()) {
                        return n;
                    }
                }
            }
            return "No SIM";
        } catch (SecurityException e) {
            return UNAVAILABLE;
        } catch (Exception e) {
            return "No SIM";
        }
    }

    /**
     * The ICCID of a slot.
     *
     * <p>Android 10 restricted this to carrier-privileged apps, so on this
     * terminal it will normally come back {@link #UNAVAILABLE}. It is still
     * listed because the acquirer's own build may be privileged, and a missing
     * row would look like a bug rather than a platform rule.</p>
     */
    public static String simSerial(Context ctx, int slot) {
        try {
            SubscriptionInfo info = subscription(ctx, slot);
            if (info == null) {
                return "No SIM";
            }
            String iccid = info.getIccId();
            return iccid == null || iccid.isEmpty() ? UNAVAILABLE : iccid;
        } catch (SecurityException e) {
            return UNAVAILABLE;
        } catch (Exception e) {
            return UNAVAILABLE;
        }
    }

    /**
     * A SIM's own number.
     *
     * <p>Two sources, in order. {@code getLine1Number} is free and instant but
     * is only populated when the operator wrote the MSISDN to the SIM, which in
     * Bangladesh they generally do not. Failing that, the number cached from
     * the USSD reply - see {@link #requestMsisdn}.</p>
     */
    public static String msisdnOrLine1(Context ctx, int slot) {
        String cached = msisdn(slot);
        if (!cached.isEmpty()) {
            return cached;
        }
        try {
            SubscriptionInfo info = subscription(ctx, slot);
            if (info != null) {
                String n = info.getNumber();
                if (n != null && !n.trim().isEmpty()) {
                    return n.trim();
                }
            }
        } catch (SecurityException e) {
            return UNAVAILABLE;
        } catch (Exception ignore) {
            // fall through to "no SIM"
        }
        return subscription(ctx, slot) == null ? "No SIM" : UNAVAILABLE;
    }

    /**
     * Ask the network for this SIM's number, by running {@code *2#}.
     *
     * <p>That is the self-number code on the Bangladeshi operators. It is a
     * USSD session, so it goes out over the air and comes back as a line of
     * text - "Your number is 8801XXXXXXXXX" or similar, worded differently by
     * each operator - which is why the reply is scanned for anything shaped
     * like an MSISDN rather than parsed.</p>
     *
     * <p>Three things can stop it and all three are normal: the app needs
     * CALL_PHONE granted at runtime, the API needs Android 8, and the operator
     * has to answer. Each failure caches {@link #UNAVAILABLE} rather than
     * retrying, so a terminal on a network that does not support the code does
     * not send a USSD request every time someone opens this screen.</p>
     *
     * @param done run on the main thread when the answer, or the failure,
     *             is in
     */
    public static void requestMsisdn(final Context ctx, final int slot, final Runnable done) {
        if (!msisdn(slot).isEmpty()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            setMsisdn(slot, UNAVAILABLE);
            return;
        }
        SubscriptionInfo info = subscription(ctx, slot);
        if (info == null) {
            setMsisdn(slot, "No SIM");
            if (done != null) {
                done.run();
            }
            return;
        }
        try {
            TelephonyManager tm = (TelephonyManager)
                    ctx.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                setMsisdn(slot, UNAVAILABLE);
                return;
            }
            tm = tm.createForSubscriptionId(info.getSubscriptionId());
            setMsisdn(slot, PENDING);
            tm.sendUssdRequest(USSD_SELF_NUMBER,
                    new TelephonyManager.UssdResponseCallback() {
                        @Override
                        public void onReceiveUssdResponse(TelephonyManager t,
                                                          String request,
                                                          CharSequence response) {
                            String n = firstMsisdn(response == null ? "" : response.toString());
                            setMsisdn(slot, n.isEmpty() ? UNAVAILABLE : n);
                            if (done != null) {
                                done.run();
                            }
                        }

                        @Override
                        public void onReceiveUssdResponseFailed(TelephonyManager t,
                                                                String request,
                                                                int failureCode) {
                            LogUtils.e("AppInfo: USSD " + request + " failed on slot "
                                    + slot + ", code " + failureCode);
                            setMsisdn(slot, UNAVAILABLE);
                            if (done != null) {
                                done.run();
                            }
                        }
                    }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (SecurityException e) {
            // CALL_PHONE not granted. Say so rather than showing a blank.
            LogUtils.e("AppInfo: USSD needs CALL_PHONE - " + e.getMessage());
            setMsisdn(slot, "NEED CALL PERMISSION");
            if (done != null) {
                done.run();
            }
        } catch (Exception e) {
            LogUtils.e("AppInfo: USSD failed - " + e.getMessage());
            setMsisdn(slot, UNAVAILABLE);
            if (done != null) {
                done.run();
            }
        }
    }

    /** The self-number USSD code used by the Bangladeshi operators. */
    public static final String USSD_SELF_NUMBER = "*2#";

    /**
     * Pull the first thing shaped like a Bangladeshi mobile number out of a
     * USSD reply.
     *
     * <p>Each operator words the answer differently, so matching the shape is
     * the only thing that works across all of them: an optional 88 or +88
     * country code, then 01 and nine more digits.</p>
     */
    static String firstMsisdn(String text) {
        if (text == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:\\+?88)?(01\\d{9})")
                .matcher(text.replaceAll("[\\s-]", ""));
        return m.find() ? m.group(1) : "";
    }

    // --------------------------------------------------------------- helpers

    private static SubscriptionInfo subscription(Context ctx, int slot) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
                return null;
            }
            SubscriptionManager sm = (SubscriptionManager)
                    ctx.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            return sm == null ? null : sm.getActiveSubscriptionInfoForSimSlotIndex(slot);
        } catch (SecurityException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static PackageInfo pkg(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
        } catch (Exception e) {
            return null;
        }
    }

    private static long versionCode(PackageInfo pi) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return pi.getLongVersionCode();
            }
        } catch (Exception ignore) {
            // fall through to the deprecated field
        }
        return pi.versionCode;
    }

    private static String stamp(long millis) {
        if (millis <= 0) {
            return "";
        }
        return new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US).format(new Date(millis));
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
