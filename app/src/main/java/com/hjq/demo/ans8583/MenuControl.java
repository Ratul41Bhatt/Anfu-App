package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Menu visibility and transaction permission, the two independent switches the
 * PAX terminal carries for every menu item.
 *
 * <ul>
 *   <li><b>Menu Hide</b> ({@code TRANS_HIDE_*}) - is the tile on the screen at
 *       all, and does the row appear on the settlement slip? Despite the name,
 *       PAX store <em>true = shown</em>: {@code SettleActivity} reads
 *       {@code TRANS_HIDE_SALE} and sets the row VISIBLE when it is true. The
 *       same flags gate the rows in {@code ReceiptTransTotal}.</li>
 *   <li><b>Transaction Control</b> ({@code TRANS_CONTROL_*}) - is the
 *       transaction allowed to run? PAX leave the tile visible and answer a
 *       press with "Transaction Not Allowed"
 *       ({@code err_trans_not_allowed}).</li>
 * </ul>
 *
 * <p>They are deliberately separate. Hiding a tile tidies the screen for a
 * merchant who never uses it; disallowing a transaction is an acquirer
 * decision, and leaving the tile visible means the operator gets told why
 * rather than wondering where it went.</p>
 */
public final class MenuControl {

    private static final String PREF = "menu_control";
    private static final String K_HIDE = "hide_";
    private static final String K_ALLOW = "allow_";

    private final SharedPreferences sp;
    private static MenuControl instance;

    private MenuControl(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized MenuControl get(Context c) {
        if (instance == null) {
            instance = new MenuControl(c);
        }
        return instance;
    }

    /**
     * Every menu item, in the order they appear, keyed the way the transaction
     * types already are so the two never drift apart.
     */
    public static Map<String, String> items() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("SALE", "Sale");
        m.put("VOID", "Void");
        m.put("REFUND", "Refund");
        m.put("PREAUTH", "Pre-Auth");
        m.put("PREAUTH_VOID", "Pre-Auth Void");
        m.put("SALE_COMPLETE", "Sale Complete");
        m.put("EMI_SALE", "EMI Sale");
        m.put("EMI_VOID", "EMI Void");
        m.put("OFFLINE", "Offline");
        m.put("TIP_ADJUST", "Tip Adjust");
        m.put("CASH_WITHDRAW", "Cash Withdraw");
        m.put("SETTLEMENT", "Settlement");
        m.put("REPORTS", "Reports");
        m.put("QR_SALE", "QR Sale");
        m.put("KONA_QR", "KonaMoney QR");
        m.put("APP_INFO", "App Information");
        m.put("LOGON", "Logon");
        return m;
    }

    /** Items that also appear as a row on the settlement slip. */
    public static String[] settlementRows() {
        return new String[] {"SALE", "VOID", "REFUND", "EMI_SALE", "CASH_WITHDRAW"};
    }

    // ---------------------------------------------------------------- visible

    /** Is the tile shown? Default yes, so nothing disappears without being asked. */
    public boolean isVisible(String key) {
        return sp.getBoolean(K_HIDE + key, true);
    }

    public void setVisible(String key, boolean v) {
        sp.edit().putBoolean(K_HIDE + key, v).apply();
    }

    // ---------------------------------------------------------------- allowed

    /**
     * Is the transaction permitted? Default yes.
     *
     * <p>Pre-Auth and Pre-Auth Void are on: both are built and both were
     * matched field by field against a Move/2500 capture on this acquirer -
     * 0100 with processing code 300000 and DE25 06, then 0400 echoing the
     * original's STAN, RRN, auth code and invoice.</p>
     *
     * <p>Sale Complete is on as well, but its layout is <b>inferred</b> rather
     * than captured - see {@code OnlineHost.saleComplete}. The roll supplied
     * for it turned out to hold a second pre-auth, not a completion. If DBBL
     * decline it, switch it off here rather than in code.</p>
     */
    public boolean isAllowed(String key) {
        return sp.getBoolean(K_ALLOW + key, true);
    }

    public void setAllowed(String key, boolean v) {
        sp.edit().putBoolean(K_ALLOW + key, v).apply();
    }

    /** Both switches at once, for the admin summary line. */
    public String describe(String key) {
        boolean vis = isVisible(key);
        boolean allow = isAllowed(key);
        if (vis && allow) {
            return "Shown, allowed";
        }
        if (vis) {
            return "Shown, NOT allowed";
        }
        return allow ? "Hidden" : "Hidden, not allowed";
    }
}
