package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Calendar;

/**
 * Terminal / communication parameters, persisted in SharedPreferences.
 *
 * <p>These are the values edited from the admin panel (TID, MID, host IP/port,
 * NII, TPDU, timeout) and consumed by {@link OnlineHost} when it sends the
 * ISO-8583 packets. Defaults mirror the constants that used to live in
 * {@link OfflinePacket8583} so behaviour is unchanged until the operator edits
 * them.</p>
 */
public final class TerminalConfig {

    private static final String PREF = "terminal_config";

    private static final String K_TID = "tid";
    private static final String K_MID = "mid";
    private static final String K_IP = "host_ip";
    private static final String K_PORT = "host_port";
    private static final String K_NII = "nii";
    private static final String K_TPDU = "tpdu";
    private static final String K_TIMEOUT = "timeout";
    private static final String K_CURRENCY = "currency";
    private static final String K_MK_INDEX = "mk_index";
    private static final String K_STAN = "stan";
    private static final String K_BATCH = "batch";
    private static final String K_INVOICE = "invoice";
    private static final String K_PPID = "ppid";
    private static final String K_PP_SW = "pp_sw_version";
    private static final String K_WK_LEN = "wk_len";
    private static final String K_MAC_ON = "mac_enabled";
    private static final String K_TRACE_ON = "print_packet_trace";
    private static final String K_BLOCK_AAC = "block_offline_decline";
    private static final String K_EXPECTED_TPK_KCV = "expected_tpk_kcv";
    private static final String K_TMK_KCV = "tmk_kcv";
    private static final String K_TMK_CLEAR = "tmk_clear";
    private static final String K_LOGGED_ON = "logged_on";
    private static final String K_WK_KCV = "wk_kcv";
    private static final String K_MACK_KCV = "mack_kcv";
    private static final String K_RCPT2 = "receipt_line2";
    private static final String K_RCPT3 = "receipt_line3";
    private static final String K_HOST_MERCHANT = "host_merchant_name";
    private static final String K_CARD_MASK = "card_mask";
    private static final String K_SETTLE_DETAIL = "settle_detail_report";
    private static final String K_SETTLE_DAYS = "settle_require_days";
    private static final String K_LAST_SETTLE = "last_settle_time";
    private static final String K_CARD_RANGES = "card_ranges_json";
    private static final String K_EMI_CARD_RANGES = "emi_card_ranges_json";
    private static final String K_REVERSAL_TRIES = "reversal_max_tries";
    private static final String K_PINPAD_DYNAMIC = "pinpad_dynamic";
    private static final String K_EMI_ENABLED = "emi_enabled";
    private static final String K_EMI_NII = "emi_nii";
    private static final String K_EMI_TENURE = "emi_tenure_";
    private static final String K_EMI_PKG_PRINT = "emi_package_print";
    private static final String K_CHECK_EXPIRING_DATE = "check_expiring_date";
    private static final String K_CUSTOMER_COPY = "customer_copy_enabled";

    public static final String DEF_TID = "48290000";
    public static final String DEF_MID = "002948290000001";
    public static final String DEF_IP = "";
    public static final int    DEF_PORT = 5000;
    public static final String DEF_NII = "008";
    public static final String DEF_TPDU = "6000080000";
    public static final int    DEF_TIMEOUT = 30000;
    public static final String DEF_CURRENCY = "050";
    public static final int    DEF_MK_INDEX = 2;
    /** PIN pad serial number sent in DE60 on logon (spec 6.25.6). */
    public static final String DEF_PPID = "0000000000000000";
    /** PIN pad software version sent in DE60 on logon, 6 digits. */
    public static final String DEF_PP_SW = "000100";
    /**
     * Byte length of each key inside the DE62 logon response.
     *
     * <p><b>8, per Hypercom spec 6.27.2.</b> That section defines DE62 on an
     * 0810 as:</p>
     *
     * <pre>
     *   Length Attribute        n 3    2 bytes
     *   Encrypted Working Key   b 64   8 bytes   <- "b 64" is 64 BITS
     *   MAC Key                 b 64   8 bytes
     *   Receipt Line 2/3, Merchant Name          optional, 23 bytes each
     * </pre>
     *
     * <p>This used to default to 0, meaning "the whole field is the working
     * key". On a 16-byte DE62 that loaded {@code TPK||TAK} as a single
     * double-length PIN key. The pad then enciphered every PIN block under
     * 3DES(TPK,TAK) while the acquirer's HSM decrypted with the real 8-byte
     * TPK, so the recovered block was noise and its length nibble was random.
     * A Thales HSM reports that as command DA error 24, "PIN is fewer than 4
     * or more than 12 digits" - the PIN length error DBBL were seeing. Nothing
     * else in the message is affected, which is why the ARQC verified and the
     * issuer still returned an ARPC.</p>
     *
     * <p><b>0 - the whole field is the PIN key. Do not change this without
     * evidence.</b></p>
     *
     * <p>This is not a guess. The PAX terminal that DBBL certified on this same
     * TID takes the entire DE62 as the TPK and builds DE52 with it, and it is
     * approved. On a 16-byte DE62 that gives a double-length TPK, so the pad
     * 3DES-enciphers the PIN block. Spec 6.27.2 describes an 8-byte working key
     * plus an 8-byte MAC key because it was written for single-length DES;
     * an acquirer running double-length keys fills those two slots with the two
     * halves of one 16-byte key. The working terminal is the authority here,
     * not the 1990s text.</p>
     *
     * <p>8 forces the literal spec split, 16 forces 16+16. The admin panel
     * cycles 0 -> 8 -> 16; if a sale is failing, the answer is almost certainly
     * NOT here, because the pad's derived TPK KCV has been confirmed to match
     * our own. Look at the terminal master key instead.</p>
     */
    public static final int    DEF_WK_LEN = 0;

    private final SharedPreferences sp;
    private static TerminalConfig instance;

    private TerminalConfig(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized TerminalConfig get(Context c) {
        if (instance == null) {
            instance = new TerminalConfig(c);
        }
        return instance;
    }

    public String tid()      { return sp.getString(K_TID, DEF_TID); }
    public String mid()      { return sp.getString(K_MID, DEF_MID); }
    public String hostIp()   { return sp.getString(K_IP, DEF_IP); }
    public int    hostPort() { return sp.getInt(K_PORT, DEF_PORT); }
    public String nii()      { return sp.getString(K_NII, DEF_NII); }
    public String tpdu()     { return sp.getString(K_TPDU, DEF_TPDU); }
    public int    timeout()  { return sp.getInt(K_TIMEOUT, DEF_TIMEOUT); }
    public String currency() { return sp.getString(K_CURRENCY, DEF_CURRENCY); }
    public int    mkIndex()  { return sp.getInt(K_MK_INDEX, DEF_MK_INDEX); }

    public void setTid(String v)      { sp.edit().putString(K_TID, v).apply(); }
    public void setMid(String v)      { sp.edit().putString(K_MID, v).apply(); }
    public void setHostIp(String v)   { sp.edit().putString(K_IP, v).apply(); }
    public void setHostPort(int v)    { sp.edit().putInt(K_PORT, v).apply(); }
    public void setNii(String v)      { sp.edit().putString(K_NII, v).apply(); }
    public void setTpdu(String v)     { sp.edit().putString(K_TPDU, v).apply(); }
    public void setTimeout(int v)     { sp.edit().putInt(K_TIMEOUT, v).apply(); }
    public void setCurrency(String v) { sp.edit().putString(K_CURRENCY, v).apply(); }
    public void setMkIndex(int v)     { sp.edit().putInt(K_MK_INDEX, v).apply(); }

    // ------------------------------------------------------- logon / key state

    public String ppid()     { return sp.getString(K_PPID, DEF_PPID); }
    public String ppSwVer()  { return sp.getString(K_PP_SW, DEF_PP_SW); }
    /**
     * DE62 key length, with a one-time migration to the spec value.
     *
     * <p>Terminals provisioned before this fix may have 0 ("whole field")
     * written into preferences, which survives an app update and would keep
     * loading {@code TPK||TAK} as one double-length PIN key. Force the spec
     * value once; after that the admin panel's DE62 SPLIT button is free to
     * set whatever the acquirer needs.</p>
     */
    public int wkLen() {
        if (!sp.getBoolean(K_WK_LEN_MIGRATED, false)) {
            sp.edit().putBoolean(K_WK_LEN_MIGRATED, true)
                     .putInt(K_WK_LEN, DEF_WK_LEN).apply();
            return DEF_WK_LEN;
        }
        return sp.getInt(K_WK_LEN, DEF_WK_LEN);
    }

    // Bump the suffix whenever DEF_WK_LEN changes, so the new default reaches
    // terminals that already have a value written.
    // v4: force back to whole-field. The admin button cycles, so a diagnostic
    // press could have left the terminal on 8+8; this puts it on the reading the
    // certified PAX terminal uses.
    private static final String K_WK_LEN_MIGRATED = "wk_len_migrated_v4";

    /**
     * Everything the last logon did with the key, kept for the admin panel.
     *
     * <p>The DE62 content has never been in a log we could read, and every
     * remaining question - is it 8, 16 or 32 bytes, does the pad's derivation
     * match ours, did the write succeed - is answerable the moment it is. So
     * record it rather than asking for another capture.</p>
     */
    public String lastLogonReport() { return sp.getString(K_LOGON_REPORT, ""); }

    public void setLastLogonReport(String v) {
        sp.edit().putString(K_LOGON_REPORT, v == null ? "" : v).apply();
    }

    private static final String K_LOGON_REPORT = "last_logon_report";
    public boolean macEnabled() { return sp.getBoolean(K_MAC_ON, false); }

    /**
     * Print the full ISO-8583 request/response trace on the transaction slip.
     * On by default for host certification; turn it off for production, since
     * the trace contains track data and the PIN block.
     */
    public boolean printPacketTrace() { return sp.getBoolean(K_TRACE_ON, true); }

    public void setPrintPacketTrace(boolean v) { sp.edit().putBoolean(K_TRACE_ON, v).apply(); }

    /**
     * Stop the transaction when the card declines offline at the first Generate
     * AC (kernel status 0x20 / an AAC in tag 9F27), instead of going online
     * with a cryptogram the host can only reject.
     *
     * <p>Correct behaviour, but OFF by default while CA public keys are still
     * being commissioned - otherwise nothing reaches the host and there is
     * nothing to diagnose. Turn it on once chip transactions authenticate
     * cleanly (TVR byte 1 = 0x00).</p>
     */
    public boolean blockOfflineDecline() { return sp.getBoolean(K_BLOCK_AAC, false); }

    public void setBlockOfflineDecline(boolean v) { sp.edit().putBoolean(K_BLOCK_AAC, v).apply(); }

    /**
     * KCV the acquirer says the working key should have.
     *
     * <p>Set this from the value DBBL quote for the key they send in the DE62
     * logon response. Every logon then verifies the key that actually landed in
     * the PIN pad against it, so a key mismatch is reported at logon instead of
     * surfacing later as an unexplained "Invalid PIN Block" on a sale.</p>
     */
    /**
     * KCV of the terminal master key, captured the moment it was injected.
     *
     * <p>The TMK is injected once from the admin panel and must never change
     * again - no Hypercom message replaces it. Recording its KCV lets every
     * logon prove that afterwards.</p>
     */
    /**
     * The clear terminal master key, as injected.
     *
     * <p>Held so the logon can derive the working key from DE62 itself, with
     * {@link com.hjq.demo.utils.TriDES}, instead of relying on the PIN pad to
     * decrypt it. That derivation can be verified against the KCV the pad
     * reports, which turns "the bank says the PIN block is wrong" into a check
     * the terminal can make on its own.</p>
     *
     * <p><b>Clear the value before production.</b> A terminal master key must
     * not live outside the secure processor. This exists to get the key
     * exchange working; the admin panel can blank it.</p>
     */
    public String tmkClear() { return sp.getString(K_TMK_CLEAR, ""); }

    public void setTmkClear(String v) {
        sp.edit().putString(K_TMK_CLEAR, v == null ? ""
                : v.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.US)).apply();
    }

    public String tmkKcv() { return sp.getString(K_TMK_KCV, ""); }

    public void setTmkKcv(String v) {
        sp.edit().putString(K_TMK_KCV, v == null ? ""
                : v.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.US)).apply();
    }

    /**
     * The raw DE62 from the last successful logon, as hex.
     *
     * <p>Kept so the admin panel can re-derive the working key under every
     * interpretation of spec 6.27.2 without needing another logon - which is
     * how you find out which slice of the field the acquirer actually means by
     * "the PIN key".</p>
     */
    public String lastDe62() { return sp.getString(K_LAST_DE62, ""); }

    public void setLastDe62(String v) {
        sp.edit().putString(K_LAST_DE62, v == null ? ""
                : v.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.US)).apply();
    }

    private static final String K_LAST_DE62 = "last_de62";

    // ------------------------------------------------- contactless CVM limit

    private static final String K_CLESS_CVM_LIMIT = "cless_cvm_limit";

    /** Default contactless CVM Required Limit: 5000.00, held in minor units. */
    public static final long DEF_CLESS_CVM_LIMIT = 500000L;

    /**
     * Contactless CVM Required Limit, in minor units (paisa).
     *
     * <p>A contactless tap at or below this amount completes with no cardholder
     * verification; above it the terminal must take a PIN. This is the EMV
     * "Reader CVM Required Limit" - the rule that lets a small tap go through
     * without the customer touching the keypad, which is the entire point of
     * contactless.</p>
     *
     * <p>Held in minor units so the comparison is exact. Money must never be
     * compared as a double: 5000.00 is not representable in binary floating
     * point, so a tap of exactly the limit could fall on either side of it
     * depending on rounding.</p>
     */
    public long clessCvmLimit() {
        return sp.getLong(K_CLESS_CVM_LIMIT, DEF_CLESS_CVM_LIMIT);
    }

    public void setClessCvmLimit(long minorUnits) {
        sp.edit().putLong(K_CLESS_CVM_LIMIT, Math.max(0L, minorUnits)).apply();
    }

    /** The limit as a major-unit string for the admin field, e.g. "5000.00". */
    public String clessCvmLimitText() {
        long v = clessCvmLimit();
        return String.format(java.util.Locale.US, "%d.%02d", v / 100, v % 100);
    }

    // ----------------------------------------------------------- idle timeout

    private static final String K_IDLE_TIMEOUT = "idle_timeout_sec";

    /** Default idle timeout: 30 seconds. */
    public static final int DEF_IDLE_TIMEOUT = 30;

    /** The longest this parameter may be set to - 10 minutes. */
    public static final int MAX_IDLE_TIMEOUT = 600;

    /**
     * How long a screen may sit untouched before it closes itself, in seconds.
     * {@code 0} switches the timeout off.
     *
     * <p>A terminal is a shared device on a counter. Every screen that waits
     * for a person - an amount, a card, a confirmation - can be walked away
     * from, and when it is, the terminal is left holding a half-finished
     * transaction: the card reader is powered and polling, the batch has an
     * open trace number, and the next customer finds someone else's amount on
     * screen. Nothing in this app closed those screens. The card search had its
     * own timeout, but the confirmation dialog that follows it would wait
     * forever.</p>
     *
     * <p>Expiry is a cancel, never a confirm. A screen that times out must
     * leave the terminal in the state it would have been in had the operator
     * pressed cancel, because nobody was there to agree to anything.</p>
     */
    public int idleTimeoutSec() {
        int v = sp.getInt(K_IDLE_TIMEOUT, DEF_IDLE_TIMEOUT);
        if (v <= 0) {
            return 0;
        }
        return Math.min(v, MAX_IDLE_TIMEOUT);
    }

    public void setIdleTimeoutSec(int seconds) {
        int v = seconds <= 0 ? 0 : Math.min(seconds, MAX_IDLE_TIMEOUT);
        sp.edit().putInt(K_IDLE_TIMEOUT, v).apply();
    }

    /** The parameter as the admin screen shows it. */
    public String idleTimeoutText() {
        int v = idleTimeoutSec();
        return v == 0 ? "OFF" : String.valueOf(v);
    }

    /**
     * Parse a major-unit amount ("5000", "5000.00", "5,000.00") to minor units.
     *
     * @return the amount in minor units, or -1 if it cannot be parsed
     */
    public static long parseMajorToMinor(String text) {
        String s = text == null ? "" : text.replaceAll("[^0-9.]", "");
        if (s.isEmpty()) {
            return -1;
        }
        int dot = s.indexOf('.');
        String whole = dot < 0 ? s : s.substring(0, dot);
        String frac = dot < 0 ? "" : s.substring(dot + 1).replace(".", "");
        if (whole.isEmpty()) {
            whole = "0";
        }
        frac = (frac + "00").substring(0, 2);
        try {
            return Long.parseLong(whole) * 100L + Long.parseLong(frac);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public String expectedTpkKcv() { return sp.getString(K_EXPECTED_TPK_KCV, ""); }

    public void setExpectedTpkKcv(String v) {
        sp.edit().putString(K_EXPECTED_TPK_KCV, v == null ? ""
                : v.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(java.util.Locale.US)).apply();
    }

    public void setPpid(String v)      { sp.edit().putString(K_PPID, v).apply(); }
    public void setPpSwVer(String v)   { sp.edit().putString(K_PP_SW, v).apply(); }
    /** Also marks the migration done, so an explicit choice is never overwritten. */
    public void setWkLen(int v) {
        sp.edit().putBoolean(K_WK_LEN_MIGRATED, true).putInt(K_WK_LEN, v).apply();
    }
    public void setMacEnabled(boolean v) { sp.edit().putBoolean(K_MAC_ON, v).apply(); }

    /**
     * True once a logon has returned DE39='00' and the working key has been
     * written to the PIN pad. Online PIN-based sales must not run before this.
     */
    public boolean isLoggedOn() { return sp.getBoolean(K_LOGGED_ON, false); }

    public void setLoggedOn(boolean v) { sp.edit().putBoolean(K_LOGGED_ON, v).apply(); }

    /** KCVs read back from the PIN pad after the last successful key load. */
    public String wkKcv()   { return sp.getString(K_WK_KCV, ""); }
    public String macKcv()  { return sp.getString(K_MACK_KCV, ""); }

    public void setKeyKcvs(String wk, String mac) {
        sp.edit().putString(K_WK_KCV, wk == null ? "" : wk)
                 .putString(K_MACK_KCV, mac == null ? "" : mac).apply();
    }

    /** Receipt text the host may push down in the optional tail of DE62. */
    public String receiptLine2()     { return sp.getString(K_RCPT2, ""); }
    public String receiptLine3()     { return sp.getString(K_RCPT3, ""); }
    public String hostMerchantName() { return sp.getString(K_HOST_MERCHANT, ""); }

    // ================= receipt letterhead, logos, currency, PAN length ======
    //
    // All of this used to be constants in FindCardActivity, which meant the
    // reports could not see it - ReportsActivity carried its own copy of the
    // merchant name literal and no address at all. Putting it here gives the
    // sale slip and every report one source to read.

    private static final String K_MERCHANT_NAME = "merchant_name";
    private static final String K_ADDR = "merchant_addr_";     // + 1..4
    private static final String K_PRINT_BANK_LOGO = "print_bank_logo";
    private static final String K_PRINT_MERCHANT_LOGO = "print_merchant_logo";
    private static final String K_PAN_MIN_LEN = "pan_min_len";
    private static final String K_PAN_MAX_LEN = "pan_max_len";

    /** How many merchant address lines a slip can carry. */
    public static final int ADDR_LINES = 4;

    public static final String DEF_MERCHANT_NAME = "LIMU BEDDING STORE DITF-2026";
    public static final String DEF_ADDR_1 = "DHAKA INTL TRADE FAIR, PURBACHAL";
    public static final String DEF_ADDR_2 = "DHAKA-1229, BANGLADESH";

    /**
     * Merchant name for every slip and report.
     *
     * <p>The host's DE62 logon tail wins - the acquirer's record is what a slip
     * must say. Below that is the operator's entry on the admin PARAMETER page,
     * and only if both are empty does the built-in default appear.</p>
     */
    public String merchantName() {
        String host = hostMerchantName();
        if (!host.isEmpty()) {
            return host;
        }
        // A name the operator has saved wins even when it is empty.
        //
        // This used to fall back to the built-in default whenever the stored
        // value was blank, which meant the line could never be turned off: a
        // terminal whose logo already carries the bank's name printed it again
        // underneath, and clearing the field in Parameters brought back
        // DEF_MERCHANT_NAME instead. "Never set" and "deliberately cleared"
        // are different answers, and only the key's presence can tell them
        // apart.
        if (sp.contains(K_MERCHANT_NAME)) {
            return sp.getString(K_MERCHANT_NAME, "");
        }
        return DEF_MERCHANT_NAME;
    }

    /** The operator's entry, without the host override - for the admin field. */
    public String merchantNameLocal() {
        String v = sp.getString(K_MERCHANT_NAME, "");
        return v.isEmpty() ? DEF_MERCHANT_NAME : v;
    }

    public void setMerchantName(String v) {
        sp.edit().putString(K_MERCHANT_NAME, v == null ? "" : v.trim()).apply();
    }

    /**
     * Merchant address line {@code n} (1-based), "" when not configured.
     *
     * <p>Lines 1 and 2 accept the host's DE62 receipt lines when it sent them.
     * Lines 3 and 4 have no DE62 equivalent - the spec's tail carries only two -
     * so they are operator-entered only. An empty line is not printed at all
     * rather than printed blank, so a two-line address gives a two-line header
     * instead of two lines and two gaps.</p>
     */
    public String merchantAddr(int n) {
        if (n == 1) {
            String host = receiptLine2();
            if (!host.isEmpty()) {
                return host;
            }
        } else if (n == 2) {
            String host = receiptLine3();
            if (!host.isEmpty()) {
                return host;
            }
        }
        return merchantAddrLocal(n);
    }

    /** The operator's entry for line {@code n}, without the host override. */
    public String merchantAddrLocal(int n) {
        if (n < 1 || n > ADDR_LINES) {
            return "";
        }
        String v = sp.getString(K_ADDR + n, null);
        if (v != null) {
            return v;
        }
        // Never configured: seed 1 and 2 from the old hard-coded header so an
        // upgraded terminal keeps printing the address it printed yesterday.
        if (n == 1) {
            return DEF_ADDR_1;
        }
        if (n == 2) {
            return DEF_ADDR_2;
        }
        return "";
    }

    public void setMerchantAddr(int n, String v) {
        if (n < 1 || n > ADDR_LINES) {
            return;
        }
        sp.edit().putString(K_ADDR + n, v == null ? "" : v.trim()).apply();
    }

    /** Bank logo at the top of a slip. On by default. */
    public boolean printBankLogo() {
        return sp.getBoolean(K_PRINT_BANK_LOGO, true);
    }

    public void setPrintBankLogo(boolean v) {
        sp.edit().putBoolean(K_PRINT_BANK_LOGO, v).apply();
    }

    /**
     * Merchant logo beside the bank logo.
     *
     * <p>Off by default: the merchant logo asset is site-specific, and a
     * terminal that was never given one would otherwise print a placeholder.
     * When on, the header switches to the half-and-half pair - bank logo left,
     * merchant logo right - which is what the half assets exist for. They are
     * never used alone.</p>
     */
    public boolean printMerchantLogo() {
        return sp.getBoolean(K_PRINT_MERCHANT_LOGO, false);
    }

    public void setPrintMerchantLogo(boolean v) {
        sp.edit().putBoolean(K_PRINT_MERCHANT_LOGO, v).apply();
    }

    /**
     * Whether to offer a customer copy prompt after a successful transaction.
     * If true, terminal asks "Print Customer Copy?".
     * If false, terminal prints only 1 single copy for every transaction.
     */
    public boolean customerCopyEnabled() {
        return sp.getBoolean(K_CUSTOMER_COPY, true);
    }

    public void setCustomerCopyEnabled(boolean v) {
        sp.edit().putBoolean(K_CUSTOMER_COPY, v).apply();
    }

    /**
     * What to print in front of an amount.
     *
     * <p>Derived from the DE49 currency code the terminal already sends, rather
     * than the string literal "TK" that was repeated in eight places across five
     * files. A terminal transacting in one currency and printing the symbol of
     * another is worse than printing none; an unknown code prints the numeric
     * code itself, which looks odd on paper on purpose.</p>
     */
    public String currencySymbol() {
        String c = currency() == null ? "" : currency().trim();
        switch (c) {
            case "050": return "TK";     // Bangladeshi taka
            case "840": return "USD";
            case "978": return "EUR";
            case "826": return "GBP";
            case "356": return "INR";
            case "702": return "SGD";
            case "784": return "AED";
            case "966": return "SAR";
            case "392": return "JPY";
            case "156": return "CNY";
            case "586": return "PKR";
            case "144": return "LKR";
            case "524": return "NPR";
            default:    return c.isEmpty() ? "TK" : c;
        }
    }

    /**
     * Shortest PAN the terminal accepts. 11, not the ISO 7812 minimum of 13:
     * DBBL issue cards down to 11 digits, and a terminal that refuses them at
     * manual entry - or silently fails to build a PIN block for them - looks
     * broken to the cardholder at the counter.
     */
    public static final int DEF_PAN_MIN_LEN = 11;
    /** Longest PAN accepted. 19 is the ISO/IEC 7812 maximum. */
    public static final int DEF_PAN_MAX_LEN = 19;
    /** Hard floor on the configurable minimum - below this it is not a card. */
    public static final int PAN_LEN_FLOOR = 8;
    /** Hard ceiling. ISO/IEC 7812 allows no more, and DE2 is n..19. */
    public static final int PAN_LEN_CEILING = 19;

    public int panMinLen() {
        return clampPanLen(sp.getInt(K_PAN_MIN_LEN, DEF_PAN_MIN_LEN), DEF_PAN_MIN_LEN);
    }

    public int panMaxLen() {
        int max = clampPanLen(sp.getInt(K_PAN_MAX_LEN, DEF_PAN_MAX_LEN), DEF_PAN_MAX_LEN);
        // A max below the min would reject every card. Read defensively rather
        // than trusting that whoever wrote it validated.
        return Math.max(max, panMinLen());
    }

    /** True when {@code len} is a card length this terminal accepts. */
    public boolean isAcceptablePanLength(int len) {
        return len >= panMinLen() && len <= panMaxLen();
    }

    /** "11-19", for toasts and admin status text. */
    public String panLenRangeText() {
        return panMinLen() + "-" + panMaxLen();
    }

    public void setPanLenRange(int min, int max) {
        int lo = clampPanLen(min, DEF_PAN_MIN_LEN);
        int hi = clampPanLen(max, DEF_PAN_MAX_LEN);
        if (lo > hi) {
            int t = lo;
            lo = hi;
            hi = t;
        }
        sp.edit().putInt(K_PAN_MIN_LEN, lo).putInt(K_PAN_MAX_LEN, hi).apply();
    }

    // ------------------------------------------------- accepted entry modes
    //
    // PAX exposes these as payment.type.chip / .nfc / .fallback and a family of
    // payment.type.manual.entry.* parameters. Same idea: what the terminal is
    // willing to accept is a site decision, not a build-time one. A merchant
    // who must not take keyed transactions can turn manual off and the option
    // disappears; one whose contactless antenna has failed can turn tap off
    // rather than have every cardholder try it first.

    private static final String K_ENTRY_CHIP = "entry_chip";
    private static final String K_ENTRY_CTLS = "entry_contactless";
    private static final String K_ENTRY_SWIPE = "entry_swipe";
    private static final String K_ENTRY_FALLBACK = "entry_fallback";
    private static final String K_ENTRY_MANUAL = "entry_manual";

    /** Chip (insert). On by default - it is the primary way a card is read. */
    public boolean entryChip() { return sp.getBoolean(K_ENTRY_CHIP, true); }

    /** Contactless (tap). On by default. */
    public boolean entryContactless() { return sp.getBoolean(K_ENTRY_CTLS, true); }

    /** Magnetic stripe (swipe). On by default. */
    public boolean entrySwipe() { return sp.getBoolean(K_ENTRY_SWIPE, true); }

    /**
     * Magstripe fallback for a chip card whose chip could not be read.
     *
     * <p>Separate from plain swipe because it is a different risk: the card
     * says it has a chip and the chip did not work, which is also what a cloned
     * magstripe looks like. Schemes charge the fallback liability differently.</p>
     */
    public boolean entryFallback() { return sp.getBoolean(K_ENTRY_FALLBACK, true); }

    /**
     * Manual (keyed) entry of the card number.
     *
     * <p>Off by default. A keyed transaction has no chip cryptogram and no
     * track, so the merchant carries the fraud liability - it should be an
     * explicit decision by whoever configures the terminal, not something a
     * fresh install does silently.</p>
     */
    public boolean entryManual() { return sp.getBoolean(K_ENTRY_MANUAL, false); }

    public void setEntryChip(boolean v) { sp.edit().putBoolean(K_ENTRY_CHIP, v).apply(); }
    public void setEntryContactless(boolean v) { sp.edit().putBoolean(K_ENTRY_CTLS, v).apply(); }
    public void setEntrySwipe(boolean v) { sp.edit().putBoolean(K_ENTRY_SWIPE, v).apply(); }
    public void setEntryFallback(boolean v) { sp.edit().putBoolean(K_ENTRY_FALLBACK, v).apply(); }
    public void setEntryManual(boolean v) { sp.edit().putBoolean(K_ENTRY_MANUAL, v).apply(); }

    /**
     * The reader mask to open card search with.
     *
     * <p>{@code CardSearchType} is a bit set - 1 magstripe, 2 chip, 4
     * contactless - so the enabled modes translate straight into what the
     * hardware is told to listen for. A mode that is off is never armed, which
     * is stronger than accepting the read and rejecting it afterwards.</p>
     *
     * @return the mask, or 0 when every card-reading mode is off
     */
    public int cardSearchMask() {
        int mask = 0;
        if (entrySwipe() || entryFallback()) {
            mask |= 1;   // CardSearchType.MAGNETIC_STRIPE_CARD
        }
        if (entryChip()) {
            mask |= 2;   // CardSearchType.IC_CARD
        }
        if (entryContactless()) {
            mask |= 4;   // CardSearchType.RF_CARD
        }
        return mask;
    }

    /** True when nothing at all is enabled - no reader and no keyed entry. */
    public boolean noEntryModeEnabled() {
        return cardSearchMask() == 0 && !entryManual();
    }

    private static int clampPanLen(int v, int fallback) {
        if (v < PAN_LEN_FLOOR || v > PAN_LEN_CEILING) {
            return fallback;
        }
        return v;
    }

    public void setHostReceiptText(String line2, String line3, String merchant) {
        SharedPreferences.Editor e = sp.edit();
        if (line2 != null && !line2.isEmpty()) {
            e.putString(K_RCPT2, line2);
        }
        if (line3 != null && !line3.isEmpty()) {
            e.putString(K_RCPT3, line3);
        }
        if (merchant != null && !merchant.isEmpty()) {
            e.putString(K_HOST_MERCHANT, merchant);
        }
        e.apply();
    }

    /** True when a host IP has been configured, i.e. an online attempt is meaningful. */
    public boolean hasHost() {
        String ip = hostIp();
        return ip != null && ip.trim().length() > 0 && hostPort() > 0;
    }

    /** Rolling 6-digit System Trace Audit Number. */
    public synchronized String nextStan() {
        int s = sp.getInt(K_STAN, 1);
        int n = s + 1;
        if (n > 999999) {
            n = 1;
        }
        if (!sp.edit().putInt(K_STAN, n).commit()) throw new IllegalStateException("Could not reserve transaction trace number");
        return String.format(java.util.Locale.US, "%06d", s);
    }

    /** Current 6-digit batch number. */
    public String batchNo() {
        return String.format(java.util.Locale.US, "%06d", sp.getInt(K_BATCH, 1));
    }

    public String qrNii() { return qrNiiOverride(); }
    public String qrNiiOverride() { return sp.getString("qr_nii", ""); }
    public String qrTpdu() { return sp.getString("qr_tpdu", "6000050000"); }
    public String qrMcc() { return sp.getString("qr_mcc", ""); }
    public String qrSid() { return sp.getString("qr_sid", ""); }
    public void setQrSid(String sid) {
        sp.edit().putString("qr_sid", sid == null ? "" : sid.trim()).apply();
    }
    public String qrCity() {
        String[] address = new String[ADDR_LINES];
        for (int i = 0; i < address.length; i++) address[i] = merchantAddr(i + 1);
        String city = DbblQr.merchantCity(address);
        return city;
    }
    public String qrMerchant() {
        String m = DbblQr.merchantLabel(merchantName());
        return m;
    }
    public int qrTimeoutSec() { return sp.getInt("qr_timeout_sec", 60); }
    public void setQrTimeoutSec(int seconds) {
        if (seconds < 10 || seconds > 600) throw new IllegalArgumentException("QR timeout must be 10 to 600 seconds");
        int old = qrTimeoutSec();
        if (!sp.edit().putInt("qr_timeout_sec", seconds).commit()) {
            sp.edit().putInt("qr_timeout_sec", old).commit();
            throw new IllegalStateException("Could not save QR timeout");
        }
    }
    public void setQrSettings(String nii, String tpdu, String mcc) {
        setQrParameters(nii, tpdu, mcc, qrTimeoutSec(), qrSid());
    }
    /** Parameter editing is independent of merchant header completeness; generation validates it. */
    public synchronized void setQrParameters(String nii, String tpdu, String mcc, int seconds) {
        setQrParameters(nii, tpdu, mcc, seconds, qrSid());
    }
    public synchronized void setQrParameters(String nii, String tpdu, String mcc, int seconds, String sid) {
        nii = nii == null ? "" : nii.trim();
        tpdu = tpdu == null ? "" : tpdu.trim().toUpperCase(java.util.Locale.US);
        mcc = mcc == null ? "" : mcc.trim();
        sid = sid == null ? "" : sid.trim();
        if (tpdu.isEmpty()) tpdu = "6000050000";
        if (seconds <= 0) seconds = 60;
        if (!nii.isEmpty() && !nii.matches("[0-9]{3}")) throw new IllegalArgumentException("QR NII must be blank or 3 digits");
        if (!tpdu.matches("60[0-9A-F]{8}")) throw new IllegalArgumentException("QR TPDU must be 10 hex characters starting 60");
        if (!mcc.isEmpty() && !mcc.matches("[0-9]{4}")) throw new IllegalArgumentException("MCC must be blank or 4 digits");
        if (seconds < 10 || seconds > 600) throw new IllegalArgumentException("QR timeout must be 10 to 600 seconds");
        if (!sid.matches("[ -~]{0,25}")) throw new IllegalArgumentException("Store label must be up to 25 English characters");
        String oldNii = qrNiiOverride(), oldTpdu = qrTpdu(), oldMcc = sp.getString("qr_mcc", ""), oldSid = qrSid();
        int oldTimeout = qrTimeoutSec();
        if (!sp.edit().putString("qr_nii", nii).putString("qr_tpdu", tpdu)
                .putString("qr_mcc", mcc).putString("qr_sid", sid).putInt("qr_timeout_sec", seconds).commit()) {
            sp.edit().putString("qr_nii", oldNii).putString("qr_tpdu", oldTpdu)
                    .putString("qr_mcc", oldMcc).putString("qr_sid", oldSid).putInt("qr_timeout_sec", oldTimeout).commit();
            throw new IllegalStateException("Could not save QR settings");
        }
    }

    /** Rolling 6-digit invoice number. */
    public synchronized String nextInvoice() {
        int s = sp.getInt(K_INVOICE, 1);
        int n = s + 1;
        if (n > 999999) {
            n = 1;
        }
        if (!sp.edit().putInt(K_INVOICE, n).commit()) throw new IllegalStateException("Could not reserve invoice number");
        return String.format(java.util.Locale.US, "%06d", s);
    }

    // ------------------------------------------------- invoice / batch / STAN

    public int stan() {
        return sp.getInt(K_STAN, 1);
    }

    public void setStan(int v) {
        sp.edit().putInt(K_STAN, Math.max(0, v)).apply();
    }

    public int batch() {
        return sp.getInt(K_BATCH, 1);
    }

    public void setBatch(int v) {
        sp.edit().putInt(K_BATCH, Math.max(0, v)).apply();
    }

    public int invoice() {
        return sp.getInt(K_INVOICE, 1);
    }

    public void setInvoice(int v) {
        sp.edit().putInt(K_INVOICE, Math.max(0, v)).apply();
    }

    /** Current invoice number as a 6-digit string (used in DE62). */
    public String invoiceNo() {
        return String.format(java.util.Locale.US, "%06d", invoice());
    }

    // ------------------------------------------------------------------ EMI

    /** Every term PAX exposes as a parameter: 3 to 60 months in steps of 3. */
    public static final int[] EMI_TENURES = {3, 6, 9, 12, 15, 18, 21, 24, 27, 30,
            33, 36, 39, 42, 45, 48, 51, 54, 57, 60};

    /**
     * NII for EMI transactions, DE24.
     *
     * <p>PAX routes ZIP and Want2Buy to NII 295 rather than the acquirer NII,
     * which is how processing code 92 00 00 can mean an EMI sale there and a
     * settlement on the normal acquirer route - different NII, different host
     * application. Configurable because it is a DBBL routing decision, not
     * something the Hypercom spec fixes.</p>
     */
    public static final String DEF_EMI_NII = "295";

    public boolean emiEnabled() { return sp.getBoolean(K_EMI_ENABLED, false); }

    public void setEmiEnabled(boolean v) { sp.edit().putBoolean(K_EMI_ENABLED, v).apply(); }

    public String emiNii() { return sp.getString(K_EMI_NII, DEF_EMI_NII); }

    public void setEmiNii(String v) {
        sp.edit().putString(K_EMI_NII, v == null || v.trim().isEmpty()
                ? DEF_EMI_NII : v.trim()).apply();
    }

    /**
     * Whether this term is offered at the till - PAX's twenty
     * {@code EMI_CONTROL_TENURE_n} parameters.
     *
     * <p>Default on, so a package the host sends is usable without having to
     * switch its term on by hand first.</p>
     */
    public boolean emiTenureEnabled(int months) {
        return sp.getBoolean(K_EMI_TENURE + months, true);
    }

    public void setEmiTenureEnabled(int months, boolean v) {
        sp.edit().putBoolean(K_EMI_TENURE + months, v).apply();
    }

    /** Print the EMI package list after a download - PAX's PARAM_EMI_PACKAGE_PRINT. */
    public boolean emiPackagePrint() { return sp.getBoolean(K_EMI_PKG_PRINT, true); }

    public void setEmiPackagePrint(boolean v) {
        sp.edit().putBoolean(K_EMI_PKG_PRINT, v).apply();
    }

    /**
     * PIN pad key layout.
     *
     * <p><b>Static</b> lays the digits out 1-2-3 / 4-5-6 / 7-8-9, where a
     * cardholder expects them. <b>Dynamic</b> shuffles them on every entry, so
     * someone watching the hand cannot read the PIN from the finger positions.
     * The trade is speed and mis-keying: a scrambled pad is slower and older
     * cardholders in particular struggle with it.</p>
     *
     * <p>Static by default, matching the PAX terminal.</p>
     */
    public boolean pinPadDynamic() { return sp.getBoolean(K_PINPAD_DYNAMIC, false); }

    public void setPinPadDynamic(boolean v) {
        sp.edit().putBoolean(K_PINPAD_DYNAMIC, v).apply();
    }

    /**
     * The card range table as edited in the admin panel, JSON, empty when the
     * terminal is still using the shipped {@code assets/card_range.json}.
     *
     * <p>Kept here rather than rewriting the asset so "restore defaults" is
     * always available: clearing this falls straight back to the shipped
     * table.</p>
     */
    public String cardRangesJson() { return sp.getString(K_CARD_RANGES, ""); }

    public void setCardRangesJson(String v) {
        sp.edit().putString(K_CARD_RANGES, v == null ? "" : v).apply();
    }

    public void clearCardRanges() { sp.edit().remove(K_CARD_RANGES).apply(); }

    /**
     * The EMI card range table, JSON, empty when the operator has not entered
     * one.
     *
     * <p>Separate from {@link #cardRangesJson()} on purpose. The ordinary table
     * says which cards this terminal acquires at all; this one says which of
     * them the bank offers instalments on, and the two lists are given out by
     * different people at different times. Folding EMI into a flag on the
     * ordinary rows would mean re-cutting the acquiring table every time the
     * EMI BIN list changed.</p>
     *
     * <p>There is no shipped asset behind it, so empty means "not configured"
     * rather than "no card qualifies" - see
     * {@code CardRangeTable.emiAllowed}.</p>
     */
    public String emiCardRangesJson() { return sp.getString(K_EMI_CARD_RANGES, ""); }

    public void setEmiCardRangesJson(String v) {
        sp.edit().putString(K_EMI_CARD_RANGES, v == null ? "" : v).apply();
    }

    public void clearEmiCardRanges() { sp.edit().remove(K_EMI_CARD_RANGES).apply(); }

    // -------------------------------------------------------------- reversal

    /** How many 0400 attempts the terminal makes before it stops on its own. */
    public static final int DEF_REVERSAL_TRIES = 3;

    /**
     * How many times an unacknowledged reversal is retried before the terminal
     * gives up and leaves it for someone to escalate.
     *
     * <p>Spec 7.35 says a reversal must be sent "persistently until a valid
     * response to the reversal is received", and taken literally that is an
     * unbounded loop: a host that never answers would block every future
     * transaction behind a reversal that can never be delivered. So there is a
     * ceiling, and it is a parameter rather than a constant because the number
     * that is right depends on the link.</p>
     *
     * <p>Attempts are thirty seconds apart, and when they run out the queued
     * reversal is DROPPED - the slot is cleared, the REVERSAL banner goes and
     * settlement unblocks. That is what DBBL asked for. It also means a
     * reversal the host never acknowledged leaves no trace on the terminal
     * except the log and the Error TRXN Report, so the number here is the
     * number of chances the acquirer gets before the terminal forgets.</p>
     *
     * <p>Clamped to 1..20. Zero would mean a reversal is armed and never sent,
     * which is the one thing this must not be able to express.</p>
     */
    public int reversalMaxTries() {
        int v = sp.getInt(K_REVERSAL_TRIES, DEF_REVERSAL_TRIES);
        return v < 1 ? 1 : (v > 20 ? 20 : v);
    }

    public void setReversalMaxTries(int v) {
        int c = v < 1 ? 1 : (v > 20 ? 20 : v);
        sp.edit().putInt(K_REVERSAL_TRIES, c).apply();
    }

    public boolean saleWithTip() { return sp.getBoolean("sale_with_tip", false); }
    public boolean tipAdjustEnabled() { return sp.getBoolean("tip_adjust_enabled", false); }
    public int tipPercentage() { return sp.getInt("tip_percentage", 50); }
    public void setTipSettings(boolean sale, boolean adjust, int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Tip percentage must be 0 to 100");
        boolean previousSale = saleWithTip(), previousAdjust = tipAdjustEnabled();
        int previousPercent = tipPercentage();
        if (!sp.edit().putBoolean("sale_with_tip", sale).putBoolean("tip_adjust_enabled", adjust)
                .putInt("tip_percentage", percent).commit()) {
            sp.edit().putBoolean("sale_with_tip", previousSale).putBoolean("tip_adjust_enabled", previousAdjust)
                    .putInt("tip_percentage", previousPercent).commit();
            throw new IllegalStateException("Could not save Tip settings");
        }
    }

    // ------------------------------------------------------------- passwords

    // ------------------------------------------------------- pre-auth limit

    /** Default Pre Auth Percentage: a completion may be up to 125% of the hold. */
    public static final int DEF_PREAUTH_PERCENT = 125;

    /**
     * Pre Auth Percentage - the largest Sale Completion allowed, as a
     * percentage of the amount the pre-authorisation was approved for. 125
     * means a completion can be up to 25% above the pre-auth amount.
     */
    public int preAuthPercentage() {
        int v = sp.getInt("preauth_percentage", DEF_PREAUTH_PERCENT);
        return v < 100 ? DEF_PREAUTH_PERCENT : v;
    }

    public void setPreAuthPercentage(int percent) {
        if (percent < 100 || percent > 999) {
            throw new IllegalArgumentException("Pre Auth Percentage must be 100 to 999");
        }
        if (!sp.edit().putInt("preauth_percentage", percent).commit()) {
            throw new IllegalStateException("Could not save Pre Auth Percentage");
        }
    }

    /** The largest completion allowed for a pre-auth of {@code authorisedMinor}. */
    public long maxCompletionMinor(long authorisedMinor) {
        return Math.abs(authorisedMinor) * preAuthPercentage() / 100L;
    }

    // --------------------------------------------------- masking / settlement

    /**
     * How card numbers are masked wherever they are shown - the confirmation
     * prompt, the receipt, the logs. Defaults to
     * {@link com.hjq.demo.utils.CardMask#MIDDLE}, which keeps the first six and
     * last four digits, matching the PAX reference and what PCI DSS permits to
     * be displayed.
     */
    public int cardMask() {
        return sp.getInt(K_CARD_MASK, com.hjq.demo.utils.CardMask.MIDDLE);
    }

    public void setCardMask(int v) { sp.edit().putInt(K_CARD_MASK, v).apply(); }

    /**
     * Print the transaction detail report before the settlement slip.
     *
     * <p>PAX's "Settle Details Report" parameter. When on, the terminal prints
     * a line per transaction in the batch first, so the merchant can check the
     * detail against the totals the host accepted.</p>
     */
    public boolean settleDetailReport() { return sp.getBoolean(K_SETTLE_DETAIL, true); }

    public void setSettleDetailReport(boolean v) {
        sp.edit().putBoolean(K_SETTLE_DETAIL, v).apply();
    }

    /**
     * Force a settlement after this many days, 0 to never prompt.
     *
     * <p>PAX's "Settle Require Days". The batch is checked against the last
     * successful settlement; once the limit is passed the terminal warns the
     * merchant that the batch must be closed.</p>
     */
    public int settleRequireDays() { return sp.getInt(K_SETTLE_DAYS, 0); }

    public void setSettleRequireDays(int v) {
        sp.edit().putInt(K_SETTLE_DAYS, Math.max(0, Math.min(99, v))).apply();
    }

    /** Epoch millis of the last approved settlement, 0 when there has been none. */
    public long lastSettleTime() { return sp.getLong(K_LAST_SETTLE, 0L); }

    public void setLastSettleTime(long v) { sp.edit().putLong(K_LAST_SETTLE, v).apply(); }

    /** Whole days since the last approved settlement, -1 when there never was one. */
    public int daysSinceSettle() {
        long last = lastSettleTime();
        if (last <= 0) {
            return -1;
        }
        long ms = System.currentTimeMillis() - last;
        return ms <= 0 ? 0 : (int) (ms / (24L * 60L * 60L * 1000L));
    }

    /**
     * True when "Settle Require Days" has been passed and the merchant should
     * be told to close the batch.
     */
    public boolean settleOverdue() {
        int limit = settleRequireDays();
        if (limit <= 0) {
            return false;
        }
        int days = daysSinceSettle();
        return days < 0 || days >= limit;
    }

    // ------------------------------------------------------------- passwords

    private static final String K_ADMIN_PW = "admin_pw";
    private static final String K_EXIT_PW = "exit_pw";
    private static final String K_MENU_PW_PREFIX = "pw_menu_";
    public static final String DEFAULT_PW = "0000";

    public String settingsPw() { return sp.getString("settings_pw", "1235"); }
    public void setSettingsPw(String value) {
        if (value == null || !value.matches("[0-9]{4,12}"))
            throw new IllegalArgumentException("Settings password must be 4 to 12 digits");
        String old = settingsPw();
        if (!sp.edit().putString("settings_pw", value).commit()) {
            sp.edit().putString("settings_pw", old).commit();
            throw new IllegalStateException("Could not save Settings password");
        }
    }

    public String adminPw() {
        return sp.getString(K_ADMIN_PW, DEFAULT_PW);
    }

    public void setAdminPw(String v) {
        sp.edit().putString(K_ADMIN_PW, (v == null || v.isEmpty()) ? DEFAULT_PW : v).apply();
    }

    public String exitPw() {
        return sp.getString(K_EXIT_PW, DEFAULT_PW);
    }

    public void setExitPw(String v) {
        sp.edit().putString(K_EXIT_PW, (v == null || v.isEmpty()) ? DEFAULT_PW : v).apply();
    }

    /** Per-menu password ("" = no password required for that menu item). */
    public String menuPw(String key) {
        String value = sp.getString(K_MENU_PW_PREFIX + key, "");
        if (isVoidMenu(key) && value.isEmpty()) return adminPw();
        return value;
    }

    public static boolean isVoidMenu(String key) {
        return "VOID".equals(key) || "EMI_VOID".equals(key) || "PREAUTH_VOID".equals(key);
    }

    public void setMenuPw(String key, String v) {
        sp.edit().putString(K_MENU_PW_PREFIX + key, v == null ? "" : v).apply();
    }

    public void clearMenuPw(String key) {
        sp.edit().remove(K_MENU_PW_PREFIX + key).apply();
    }

    public boolean hasMenuPw(String key) {
        String p = menuPw(key);
        return p != null && !p.isEmpty();
    }

    public boolean checkExpiringDate() {
        return sp.getBoolean(K_CHECK_EXPIRING_DATE, true);
    }

    public void setCheckExpiringDate(boolean v) {
        sp.edit().putBoolean(K_CHECK_EXPIRING_DATE, v).apply();
    }

    /**
     * Check if a card is expired.
     *
     * <p>Card expiry dates are supplied in YYMM or YYMMDD format.
     * In payment systems, an expiry of YYMM means the card is valid through
     * the end of month MM. If YYMMDD is given and DD is non-zero, it expires
     * after day DD.</p>
     *
     * @param expiry Expiry date string (e.g. "2612", "261231", "2508", etc.)
     * @return true if card is expired, false otherwise.
     */
    public static boolean isCardExpired(String expiry) {
        return isCardExpired(expiry, Calendar.getInstance());
    }

    public static boolean isCardExpired(String expiry, Calendar now) {
        if (expiry == null || now == null) {
            return false;
        }
        String clean = expiry.replaceAll("[^0-9]", "");
        if (clean.length() < 4) {
            return false;
        }
        try {
            int expYear = Integer.parseInt(clean.substring(0, 2));
            int expMonth = Integer.parseInt(clean.substring(2, 4));
            if (expMonth < 1 || expMonth > 12) {
                return false;
            }

            int curYear = now.get(Calendar.YEAR) % 100;
            int curMonth = now.get(Calendar.MONTH) + 1; // 1-12
            int curDay = now.get(Calendar.DAY_OF_MONTH);

            int yearDiff = expYear - curYear;
            if (yearDiff < -50) yearDiff += 100;
            else if (yearDiff > 50) yearDiff -= 100;

            if (yearDiff < 0) return true;
            if (yearDiff > 0) return false;

            // Same year:
            if (expMonth < curMonth) return true;
            if (expMonth > curMonth) return false;

            // Same month and year:
            if (clean.length() >= 6) {
                int expDay = Integer.parseInt(clean.substring(4, 6));
                if (expDay >= 1 && expDay <= 31) {
                    return curDay > expDay;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
