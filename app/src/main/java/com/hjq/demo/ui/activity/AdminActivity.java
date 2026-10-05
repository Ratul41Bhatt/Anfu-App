package com.hjq.demo.ui.activity;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.RemoteException;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.ans8583.OnlineHost;
import com.hjq.demo.ans8583.EmiPackage;
import com.hjq.demo.ans8583.EmiStore;
import com.hjq.demo.ans8583.MenuControl;
import com.hjq.demo.ans8583.BatchStore;
import com.hjq.demo.ans8583.TxnRecord;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.utils.CardMask;
import com.hjq.demo.utils.CardRangeTable;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.utils.KeyManager;
import com.hjq.demo.utils.LogCollector;
import com.hjq.demo.utils.PrintUtil;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.pinpad.IPinPadBinderService;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;

/**
 * Option-wise admin panel (mirrors the PAX config tree): a menu of options, each
 * flipping to its own part — Communication, Key Injection, Password Manager, Logon.
 */
public final class AdminActivity extends AppActivity implements View.OnClickListener {

    /**
     * KCV of a key made entirely of zero bytes. The PIN pad reports this when a
     * key slot has never been written, so it means "no key", not "this key".
     */
    private static final String KCV_ALL_ZEROS = "8CA64D";

    private static final int PAGE_MENU = 0;
    private static final int PAGE_COMM = 1;
    private static final int PAGE_KEYS = 2;
    private static final int PAGE_PW = 3;
    private static final int PAGE_LOGON = 4;
    private static final int PAGE_COUNTERS = 5;
    private static final int PAGE_PARAM = 6;
    private static final int PAGE_RANGES = 7;
    private static final int PAGE_EMI = 8;
    private static final int PAGE_MENUCTL = 9;
    private static final int PAGE_CLEAR = 10;
    private static final int PAGE_EMI_RANGES = 11;

    private ViewFlipper flipper;
    private EditText etTid, etMid, etIp, etPort, etNii, etTpdu, etTimeout, etMkIndex;
    private TextView tvCardMaskValue, tvDebugValue, tvSettleDetailValue, tvPinPadValue;
    private android.widget.Switch swDebug, swSettleDetail, swPinPad;
    private EditText etSettleDays;
    private EditText etReversalTries;
    private EditText etRangeName, etRangeLow, etRangeHigh;
    private LinearLayout rangeRows;
    private EditText etEmiRangeName, etEmiRangeLow, etEmiRangeHigh;
    private LinearLayout emiRangeRows;
    private TextView tvEmiRangeValue;
    private LinearLayout emiPkgRows, emiTenureRows;
    private LinearLayout menuHideRows, transControlRows;
    private EditText etEmiNii, etEmiCode, etEmiTenure, etEmiRate, etEmiIndex, etEmiDesc;
    private EditText etMasterKey, etWkId, etWkKey, etWkKcv;
    private EditText etPwValue;
    private EditText etInvoice, etBatch, etStan;
    private EditText etClessLimit;
    private EditText etMerchantName;
    private EditText[] etAddr = new EditText[TerminalConfig.ADDR_LINES];
    private EditText etPanMinLen, etPanMaxLen;
    private EditText etIdleTimeout;
    private android.widget.CheckBox cbBankLogo, cbMerchantLogo;
    private android.widget.CheckBox cbEntryChip, cbEntryCtls, cbEntrySwipe,
            cbEntryFallback, cbEntryManual;
    private android.widget.CheckBox cbCheckExpiringDate;
    private android.widget.CheckBox cbCustomerCopy;
    private Spinner spTarget;
    private TextView tvStatus, tvLogonLog;
    private String[] pwLabels;
    private String[] pwKeys;

    private IPinPadBinderService mPinPad;
    private IPrinterBinderService mPrinter;
    private KeyManager mKeyManager;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_admin;
    }

    /**
     * The admin screen does not close itself.
     *
     * <p>Everything on here that takes time - a logon, a key injection, a
     * parameter download - runs with no touch on the screen while it happens,
     * and a key injection cut off half way leaves the terminal holding keys
     * that are neither the old ones nor the new. The idle timeout is for
     * screens a customer can be left standing in front of; this is not one.</p>
     */
    @Override
    protected boolean idleTimeoutEnabled() {
        return false;
    }

    @Override
    protected void initView() {
        // AppActivity runs an immersive, edge-to-edge status bar and only
        // pushes content down when the layout carries a TitleBar. This one has
        // none, so the first row sat underneath the clock and the battery.
        // Measured rather than a fixed dp, because the status bar is not the
        // same height on a device with a notch.
        View root = findViewById(R.id.admin_root);
        if (root != null) {
            root.setPadding(root.getPaddingLeft(),
                    root.getPaddingTop() + ImmersionBar.getStatusBarHeight(this),
                    root.getPaddingRight(), root.getPaddingBottom());
        }

        flipper = findViewById(R.id.admin_flipper);

        etTid = findViewById(R.id.et_tid);
        etMid = findViewById(R.id.et_mid);
        etIp = findViewById(R.id.et_ip);
        etPort = findViewById(R.id.et_port);
        etNii = findViewById(R.id.et_nii);
        etTpdu = findViewById(R.id.et_tpdu);
        etTimeout = findViewById(R.id.et_timeout);
        etMkIndex = findViewById(R.id.et_mkindex);
        tvCardMaskValue = findViewById(R.id.tv_card_mask_value);
        tvDebugValue = findViewById(R.id.tv_debug_value);
        tvSettleDetailValue = findViewById(R.id.tv_settle_detail_value);
        tvPinPadValue = findViewById(R.id.tv_pinpad_value);
        swDebug = findViewById(R.id.sw_debug);
        swSettleDetail = findViewById(R.id.sw_settle_detail);
        swPinPad = findViewById(R.id.sw_pinpad);
        etSettleDays = findViewById(R.id.et_settle_days);
        etReversalTries = findViewById(R.id.et_reversal_tries);
        etRangeName = findViewById(R.id.et_range_name);
        etRangeLow = findViewById(R.id.et_range_low);
        etRangeHigh = findViewById(R.id.et_range_high);
        rangeRows = findViewById(R.id.range_rows);
        etEmiRangeName = findViewById(R.id.et_emi_range_name);
        etEmiRangeLow = findViewById(R.id.et_emi_range_low);
        etEmiRangeHigh = findViewById(R.id.et_emi_range_high);
        emiRangeRows = findViewById(R.id.emi_range_rows);
        tvEmiRangeValue = findViewById(R.id.tv_emi_range_value);
        emiPkgRows = findViewById(R.id.emi_pkg_rows);
        emiTenureRows = findViewById(R.id.emi_tenure_rows);
        menuHideRows = findViewById(R.id.menu_hide_rows);
        transControlRows = findViewById(R.id.trans_control_rows);
        etEmiNii = findViewById(R.id.et_emi_nii);
        etEmiCode = findViewById(R.id.et_emi_code);
        etEmiTenure = findViewById(R.id.et_emi_tenure);
        etEmiRate = findViewById(R.id.et_emi_rate);
        etEmiIndex = findViewById(R.id.et_emi_index);
        etEmiDesc = findViewById(R.id.et_emi_desc);
        etMasterKey = findViewById(R.id.et_master_key);
        etWkId = findViewById(R.id.et_wk_id);
        etWkKey = findViewById(R.id.et_wk_key);
        etWkKcv = findViewById(R.id.et_wk_kcv);
        etPwValue = findViewById(R.id.et_pw_value);
        etInvoice = findViewById(R.id.et_invoice);
        etBatch = findViewById(R.id.et_batch);
        etStan = findViewById(R.id.et_stan);
        etClessLimit = findViewById(R.id.et_cless_limit);
        etMerchantName = findViewById(R.id.et_merchant_name);
        etAddr[0] = findViewById(R.id.et_addr1);
        etAddr[1] = findViewById(R.id.et_addr2);
        etAddr[2] = findViewById(R.id.et_addr3);
        etAddr[3] = findViewById(R.id.et_addr4);
        etPanMinLen = findViewById(R.id.et_pan_min_len);
        etIdleTimeout = findViewById(R.id.et_idle_timeout);
        etPanMaxLen = findViewById(R.id.et_pan_max_len);
        cbBankLogo = findViewById(R.id.cb_bank_logo);
        cbMerchantLogo = findViewById(R.id.cb_merchant_logo);
        cbEntryChip = findViewById(R.id.cb_entry_chip);
        cbEntryCtls = findViewById(R.id.cb_entry_ctls);
        cbEntrySwipe = findViewById(R.id.cb_entry_swipe);
        cbEntryFallback = findViewById(R.id.cb_entry_fallback);
        cbEntryManual = findViewById(R.id.cb_entry_manual);
        cbCheckExpiringDate = findViewById(R.id.cb_check_expiring_date);
        cbCustomerCopy = findViewById(R.id.cb_customer_copy);
        spTarget = findViewById(R.id.sp_pw_target);
        tvStatus = findViewById(R.id.tv_admin_status);
        tvLogonLog = findViewById(R.id.tv_logon_log);

        pwLabels = new String[]{"ADMIN PANEL", "EXIT APP", "SALE", "VOID", "REFUND", "PRE-AUTH",
                "PRE-AUTH VOID", "SALE COMPLETE", "EMI SALE", "EMI VOID", "OFFLINE", "TIP ADJUST",
                "CASH WITHDRAW", "SETTLEMENT", "REPORTS", "LOGON", "DEVICE SETTINGS"};
        pwKeys = new String[]{"__admin__", "__exit__", "SALE", "VOID", "REFUND", "PREAUTH",
                "PREAUTH_VOID", "SALE_COMPLETE", "EMI_SALE", "EMI_VOID", "OFFLINE", "TIP_ADJUST",
                "CASH_WITHDRAW", "SETTLEMENT", "REPORTS", "LOGON", "__settings__"};
        if (spTarget != null) {
            spTarget.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_dropdown_item, pwLabels));
        }

        bindClicks(R.id.btn_device_settings, R.id.btn_device_lock, R.id.btn_opt_comm, R.id.btn_opt_keys, R.id.btn_opt_pw, R.id.btn_opt_logon,
                R.id.btn_back_comm, R.id.btn_back_keys, R.id.btn_back_pw, R.id.btn_back_logon,
                R.id.btn_save_comm, R.id.btn_inject_master, R.id.btn_inject_working,
                R.id.btn_read_kcv, R.id.btn_print_kcv, R.id.btn_set_pw, R.id.btn_clear_pw,
                R.id.btn_run_logon, R.id.btn_opt_counters, R.id.btn_back_counters,
                R.id.btn_save_counters, R.id.btn_de62_diag, R.id.btn_print_logon,
                R.id.btn_opt_param, R.id.btn_back_param, R.id.btn_save_param, R.id.btn_save_tip, R.id.btn_save_qr,
                R.id.btn_card_mask, R.id.btn_debug, R.id.btn_settle_detail,
                R.id.btn_pinpad_mode,
                R.id.btn_opt_ranges, R.id.btn_back_ranges, R.id.btn_range_add,
                R.id.btn_range_reset,
                R.id.btn_opt_emi_ranges, R.id.btn_back_emi_ranges,
                R.id.btn_emi_range_add, R.id.btn_emi_range_clear,
                R.id.btn_opt_emi, R.id.btn_back_emi,
                R.id.btn_emi_enabled, R.id.btn_emi_pkg_print, R.id.btn_emi_add,
                R.id.btn_emi_print,
                R.id.btn_opt_menuctl, R.id.btn_back_menuctl,
                R.id.btn_opt_clear, R.id.btn_back_clear, R.id.btn_clear_batch,
                R.id.btn_clear_reversal, R.id.btn_clear_preauth,
                R.id.btn_clear_errors);

        TextView adminTitle = findViewById(R.id.tv_admin_title);
        if (adminTitle != null) {
            adminTitle.setText("TERMINAL   -   build "
                    + com.hjq.demo.BuildConfig.BUILD_TIME);
        }

        loadCommFields();
        refreshCommSummary();
        loadCounters();
        loadParams();
        refreshEmiRangeSummary();
        etWkId.setText("3");
        showPage(PAGE_MENU);
    }

    @Override
    protected void initData() {
        // Long-press READ KCV to cycle how a 16-byte DE62 logon block is split
        // into keys. Kept off the main layout because it is a diagnostic for
        // the "Invalid PIN Block" case, not a routine setting.
        View readKcv = findViewById(R.id.btn_read_kcv);
        if (readKcv != null) {
            readKcv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    cycleWorkingKeyLength();
                    return true;
                }
            });
        }
        // Long-press the status text to save + print the app log. Deliberately
        // reachable from every page: on site there is no PC to run logcat.
        if (tvStatus != null) {
            tvStatus.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    collectLog();
                    return true;
                }
            });
        }
        // Long-press PRINT KCV for the PIN block diagnostic.
        View printKcv = findViewById(R.id.btn_print_kcv);
        if (printKcv != null) {
            printKcv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    pinBlockTest();
                    return true;
                }
            });
        }
    }

    private void bindClicks(int... ids) {
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    /** Header title per flipper child, indexed by PAGE_*. */
    private static final String[] PAGE_TITLES = {
            "ADMIN PANEL", "COMMUNICATION", "KEY INJECTION", "PASSWORD MANAGER",
            "LOGON", "COUNTERS", "PARAMETERS", "CARD RANGE", "EMI",
            "MENU CONTROL", "CLEAR", "EMI CARD RANGE"};

    private void showPage(int page) {
        if (flipper != null) {
            flipper.setDisplayedChild(page);
        }
        TextView h = findViewById(R.id.tv_admin_header_title);
        if (h != null) {
            h.setText(page >= 0 && page < PAGE_TITLES.length
                    ? PAGE_TITLES[page] : "ADMIN PANEL");
        }
        TextView s = findViewById(R.id.tv_admin_header_sub);
        if (s != null) {
            s.setText(page == PAGE_MENU
                    ? "v" + com.hjq.demo.BuildConfig.VERSION_NAME
                            + "   TID " + TerminalConfig.get(this).tid()
                    : "ADMIN PANEL");
        }
    }

    @Override
    public void onBackPressed() {
        if (flipper != null && flipper.getDisplayedChild() != PAGE_MENU) {
            showPage(PAGE_MENU);
        } else {
            super.onBackPressed();
        }
    }

    private void loadCommFields() {
        TerminalConfig tc = TerminalConfig.get(this);
        etTid.setText(tc.tid());
        etMid.setText(tc.mid());
        etIp.setText(tc.hostIp());
        etPort.setText(String.valueOf(tc.hostPort()));
        etNii.setText(tc.nii());
        etTpdu.setText(tc.tpdu());
        etTimeout.setText(String.valueOf(tc.timeout()));
        etMkIndex.setText(String.valueOf(tc.mkIndex()));
        if (etSettleDays != null) {
            etSettleDays.setText(String.valueOf(tc.settleRequireDays()));
        }
        if (etReversalTries != null) {
            etReversalTries.setText(String.valueOf(tc.reversalMaxTries()));
        }
        refreshToggleButtons();
    }

    // ---------------------------------------------------------------- clear

    /**
     * Show what each Clear would actually destroy.
     *
     * <p>Every one of these makes the terminal forget something the host still
     * holds - none of them tell the bank anything. So each says what it will
     * delete before it is pressed, and asks again afterwards.</p>
     */
    private void showClear() {
        BatchStore batch = BatchStore.get(this);
        int txns = batch.all().size();
        BatchStore.Totals t = batch.totals();

        setText(R.id.tv_clear_value, txns == 0 && !batch.hasPendingReversal()
                ? "Nothing to clear"
                : txns + " transaction(s)"
                        + (batch.hasPendingReversal() ? ", reversal pending" : ""));

        setText(R.id.tv_clear_batch_value, txns == 0
                ? "Batch " + TerminalConfig.get(this).batchNo() + " is empty"
                : txns + " transaction(s), TK " + com.hjq.demo.utils.Slip.money(t.netAmt())
                        + " in batch " + TerminalConfig.get(this).batchNo());

        setText(R.id.tv_clear_reversal_value, batch.hasPendingReversal()
                ? "1 pending, " + batch.reversalTries() + " attempt(s) so far"
                : "No pending reversal");

        setText(R.id.tv_clear_preauth_value, preAuthCount() == 0
                ? "No pre-auth records"
                : preAuthCount() + " pre-auth record(s)");

        int errs = com.hjq.demo.ans8583.ReportStore.get(this).failed().size();
        setText(R.id.tv_clear_errors_value, errs == 0
                ? "No failed transactions logged" : errs + " entr(ies)");
    }

    private int preAuthCount() {
        int n = 0;
        for (TxnRecord r : BatchStore.get(this).all()) {
            if (r.type != null && r.type.startsWith("PREAUTH")) {
                n++;
            }
        }
        return n;
    }

    /**
     * Batch Clear. The batch holds transactions the host has authorised and
     * expects to reconcile, so wiping it without settling leaves the two out
     * of step - the merchant is paid for transactions the terminal can no
     * longer report, and the next settlement will not balance.
     */
    private void confirmClearBatch() {
        if (BatchStore.get(this).hasPendingTips()) { toast("Upload pending Tip Adjust transactions before clearing the batch"); return; }
        BatchStore batch = BatchStore.get(this);
        final int txns = batch.all().size();
        if (txns == 0) {
            status("BATCH ALREADY EMPTY\n\nNothing to clear.");
            return;
        }
        BatchStore.Totals t = batch.totals();
        new MessageDialog.Builder(this)
                .setTitle("CLEAR THE BATCH?")
                .setMessage("Batch " + TerminalConfig.get(this).batchNo() + " holds "
                        + txns + " transaction(s) worth TK "
                        + com.hjq.demo.utils.Slip.money(t.netAmt()) + ".\n\n"
                        + "The host still has them. Clearing does not tell the bank -"
                        + " it only makes this terminal forget, and the next"
                        + " settlement will not balance.\n\n"
                        + "Settle instead if you can.")
                .setConfirm("CLEAR ANYWAY")
                .setCancel(getString(R.string.common_cancel))
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(com.hjq.base.BaseDialog dialog) {
                        BatchStore.get(AdminActivity.this).clear();
                        showClear();
                        status("BATCH CLEARED\n\n" + txns + " transaction(s) removed.\n\n"
                                + "The batch number was NOT advanced - only a settlement\n"
                                + "closes a batch. Reconcile with the bank.");
                    }

                    @Override
                    public void onCancel(com.hjq.base.BaseDialog dialog) {
                    }
                })
                .show();
    }

    /**
     * Reversal Clear. The pending slot holds a transaction that was sent but
     * never acknowledged; spec 7.35 says keep retrying until the host answers.
     * Dropping it means a transaction the host may have approved is never
     * reversed, so the cardholder stays charged for a sale that failed.
     */
    private void confirmClearReversal() {
        final BatchStore batch = BatchStore.get(this);
        if (!batch.hasPendingReversal()) {
            status("NO PENDING REVERSAL\n\nNothing to clear.");
            return;
        }
        TxnRecord p = batch.pendingReversal();
        new MessageDialog.Builder(this)
                .setTitle("CLEAR THE PENDING REVERSAL?")
                .setMessage((p == null ? "A reversal" : p.type + " invoice " + p.invoice
                        + ", TK " + com.hjq.demo.utils.Slip.money(p.amount()))
                        + " is waiting to be reversed after "
                        + batch.reversalTries() + " attempt(s).\n\n"
                        + "The host may have approved it. Dropping the reversal means"
                        + " it is never cancelled, and the cardholder stays charged"
                        + " for a sale the terminal reported as failed.\n\n"
                        + "Only clear this if the bank has confirmed the transaction"
                        + " did not go through.")
                .setConfirm("CLEAR ANYWAY")
                .setCancel(getString(R.string.common_cancel))
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(com.hjq.base.BaseDialog dialog) {
                        batch.clearPendingReversal();
                        showClear();
                        status("PENDING REVERSAL CLEARED\n\n"
                                + "Nothing will be sent for it now.\n"
                                + "Confirm the outcome with the bank.");
                    }

                    @Override
                    public void onCancel(com.hjq.base.BaseDialog dialog) {
                    }
                })
                .show();
    }

    /**
     * Pre-Auth Clear. Pre-auth is not implemented yet, so this finds nothing
     * today - it is here so the option exists where operators expect it, and
     * it will work unchanged once pre-auth records start being written.
     */
    private void confirmClearPreAuth() {
        if (BatchStore.get(this).hasPendingTips()) { toast("Upload pending Tip Adjust transactions first"); return; }
        final int n = preAuthCount();
        if (n == 0) {
            status("NO PRE-AUTH RECORDS\n\nPre-auth is not implemented on this\n"
                    + "terminal yet, so there is nothing to clear.");
            return;
        }
        new MessageDialog.Builder(this)
                .setTitle("CLEAR PRE-AUTH RECORDS?")
                .setMessage(n + " pre-auth record(s) will be removed.\n\n"
                        + "Any of them still open with the issuer will stay open -"
                        + " the terminal simply forgets them.")
                .setConfirm("CLEAR ANYWAY")
                .setCancel(getString(R.string.common_cancel))
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(com.hjq.base.BaseDialog dialog) {
                        BatchStore b = BatchStore.get(AdminActivity.this);
                        java.util.List<TxnRecord> keep = new ArrayList<>();
                        for (TxnRecord r : b.all()) {
                            if (r.type == null || !r.type.startsWith("PREAUTH")) {
                                keep.add(r);
                            }
                        }
                        b.replaceAll(keep);
                        showClear();
                        status("PRE-AUTH CLEARED\n\n" + n + " record(s) removed.");
                    }

                    @Override
                    public void onCancel(com.hjq.base.BaseDialog dialog) {
                    }
                })
                .show();
    }

    /** The error log is a diagnostic only - nothing financial depends on it. */
    private void confirmClearErrors() {
        int n = com.hjq.demo.ans8583.ReportStore.get(this).failed().size();
        if (n == 0) {
            status("ERROR LOG ALREADY EMPTY");
            return;
        }
        com.hjq.demo.ans8583.ReportStore.get(this).clearFailed();
        showClear();
        status("ERROR LOG CLEARED\n\n" + n + " entr(ies) removed.\n\n"
                + "This is a diagnostic list only - no financial\n"
                + "record was affected.");
    }

    // --------------------------------------------------------- menu control

    /**
     * The two switch lists PAX carry per menu item: Menu Hide (is the tile on
     * screen) and Transaction Control (is the transaction permitted).
     */
    private void showMenuControl() {
        MenuControl mc = MenuControl.get(this);
        int hidden = 0;
        int blocked = 0;
        for (String key : MenuControl.items().keySet()) {
            if (!mc.isVisible(key)) {
                hidden++;
            }
            if (!mc.isAllowed(key)) {
                blocked++;
            }
        }
        setText(R.id.tv_menuctl_value, hidden == 0 && blocked == 0
                ? "All menu items shown and allowed"
                : hidden + " hidden, " + blocked + " not allowed");

        buildSwitchList(menuHideRows, true);
        buildSwitchList(transControlRows, false);
    }

    /**
     * @param visibility true to build the Menu Hide list, false for Transaction
     *                   Control. One builder because the rows are identical
     *                   apart from which flag they read and write.
     */
    private void buildSwitchList(LinearLayout host, final boolean visibility) {
        if (host == null) {
            return;
        }
        host.removeAllViews();
        final MenuControl mc = MenuControl.get(this);
        for (Map.Entry<String, String> e : MenuControl.items().entrySet()) {
            final String key = e.getKey();
            // Logon and Reports are not transactions, so they have nothing to
            // permit - only a tile to show or hide.
            boolean isTxn = !"LOGON".equals(key) && !"REPORTS".equals(key);
            if (!visibility && !isTxn) {
                continue;
            }

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(15), dp(10), dp(15), dp(10));
            row.setClickable(true);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView t = new TextView(this);
            t.setText(e.getValue());
            t.setTextColor(0xFF333333);
            t.setTextSize(15);
            left.addView(t);

            if (visibility) {
                for (String slipRow : MenuControl.settlementRows()) {
                    if (slipRow.equals(key)) {
                        TextView sub = new TextView(this);
                        sub.setText("also the settlement slip row");
                        sub.setTextColor(0xFF888888);
                        sub.setTextSize(11);
                        left.addView(sub);
                        break;
                    }
                }
            }
            row.addView(left);

            final android.widget.Switch sw = new android.widget.Switch(this);
            sw.setChecked(visibility ? mc.isVisible(key) : mc.isAllowed(key));
            sw.setClickable(false);
            sw.setFocusable(false);
            row.addView(sw);

            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (visibility) {
                        mc.setVisible(key, !mc.isVisible(key));
                    } else {
                        mc.setAllowed(key, !mc.isAllowed(key));
                    }
                    showMenuControl();
                }
            });
            host.addView(row);

            View sep = new View(this);
            sep.setBackgroundColor(0xFFE4E7F0);
            sep.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            host.addView(sep);
        }
    }

    // ------------------------------------------------------------------ EMI

    /** Draw the EMI page: controls, the packages held, and the tenure switches. */
    private void showEmi() {
        TerminalConfig tc = TerminalConfig.get(this);
        setText(R.id.tv_emi_enabled_value, tc.emiEnabled()
                ? "On - EMI SALE and EMI VOID are available"
                : "Off - the EMI tiles refuse to start");
        setChecked(R.id.sw_emi_enabled, tc.emiEnabled());
        setText(R.id.tv_emi_pkg_print_value, tc.emiPackagePrint()
                ? "On - the package list prints after a download" : "Off");
        setChecked(R.id.sw_emi_pkg_print, tc.emiPackagePrint());
        if (etEmiNii != null) {
            etEmiNii.setText(tc.emiNii());
        }

        java.util.List<EmiPackage> packages = EmiStore.get(this).all();
        setText(R.id.tv_emi_value, packages.isEmpty() ? "No packages"
                : packages.size() + " package(s)"
                        + (tc.emiEnabled() ? "" : "  (EMI off)"));

        if (emiPkgRows != null) {
            emiPkgRows.removeAllViews();
            if (packages.isEmpty()) {
                TextView none = new TextView(this);
                none.setText("None yet. Add the bank's EMI plans below.");
                none.setTextColor(0xFF888888);
                none.setTextSize(12);
                none.setPadding(0, dp(10), 0, dp(10));
                emiPkgRows.addView(none);
            }
            for (final EmiPackage p : packages) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(8), 0, dp(8));

                TextView t = new TextView(this);
                t.setText(p.tenure + " MONTHS   code " + p.productCode
                        + "\n" + p.interestWording + "   index " + p.interestIndex);
                t.setTextColor(0xFF111111);
                t.setTextSize(13);
                t.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(t);

                Button del = new Button(this);
                del.setText("DELETE");
                del.setTextSize(11);
                del.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        EmiStore.get(AdminActivity.this).remove(p.tenure);
                        showEmi();
                        toast("Removed the " + p.tenure + " month package");
                    }
                });
                row.addView(del);
                emiPkgRows.addView(row);

                View sep = new View(this);
                sep.setBackgroundColor(0xFFE4E7F0);
                sep.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                emiPkgRows.addView(sep);
            }
        }

        // The twenty tenure switches PAX exposes as EMI_CONTROL_TENURE_n.
        if (emiTenureRows != null) {
            emiTenureRows.removeAllViews();
            for (final int months : TerminalConfig.EMI_TENURES) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(15), dp(10), dp(15), dp(10));
                row.setClickable(true);

                TextView t = new TextView(this);
                // A package is no longer needed to sell this term. DE61
                // carries only the product and the months, and the host
                // applies the rate - so a term without a package is perfectly
                // sellable, it just cannot show an Easy Buy instalment on the
                // tenure screen. Saying "no package" in grey used to read as
                // "not available", which is why nobody switched these on.
                boolean have = EmiStore.get(this).byTenure(months) != null;
                t.setText(months + " MONTHS" + (have ? "" : "   (no rate set)"));
                t.setTextColor(0xFF111111);
                t.setTextSize(14);
                t.setLayoutParams(new LinearLayout.LayoutParams(0,
                        LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                row.addView(t);

                final android.widget.Switch sw = new android.widget.Switch(this);
                sw.setChecked(tc.emiTenureEnabled(months));
                sw.setClickable(false);
                sw.setFocusable(false);
                row.addView(sw);

                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        TerminalConfig c = TerminalConfig.get(AdminActivity.this);
                        c.setEmiTenureEnabled(months, !c.emiTenureEnabled(months));
                        showEmi();
                    }
                });
                emiTenureRows.addView(row);

                View sep = new View(this);
                sep.setBackgroundColor(0xFFE4E7F0);
                sep.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                emiTenureRows.addView(sep);
            }
        }
    }

    private void setText(int id, String text) {
        TextView v = findViewById(id);
        if (v != null) {
            v.setText(text);
        }
    }

    /**
     * Tick or untick a control by id.
     *
     * <p>Typed as {@code CompoundButton} rather than {@code Switch} because
     * this page uses both - the menu rows are switches, the parameter flags
     * are checkboxes - and a helper that silently did nothing for half of them
     * is a bug waiting to be written.</p>
     */
    private void setChecked(int id, boolean on) {
        View v = findViewById(id);
        if (v instanceof android.widget.CompoundButton) {
            ((android.widget.CompoundButton) v).setChecked(on);
        }
    }

    /** Is it ticked? False when the control is not on this page. */
    private boolean isChecked(int id) {
        View v = findViewById(id);
        return v instanceof android.widget.CompoundButton
                && ((android.widget.CompoundButton) v).isChecked();
    }

    private void saveEmiNii() {
        if (etEmiNii != null) {
            TerminalConfig.get(this).setEmiNii(str(etEmiNii));
        }
    }

    /** Add a package by hand, for a host that does not serve the download. */
    private void addEmiPackage() {
        String code = str(etEmiCode).trim();
        String tenureStr = str(etEmiTenure).trim();
        String rate = str(etEmiRate).trim();
        String index = str(etEmiIndex).trim();
        String desc = str(etEmiDesc).trim();

        if (code.isEmpty() || code.length() > 3) {
            toast("Product code is 1 to 3 characters");
            return;
        }
        int tenure;
        try {
            tenure = Integer.parseInt(tenureStr);
        } catch (Exception e) {
            toast("Enter the tenure in months");
            return;
        }
        if (tenure <= 0 || tenure > 60) {
            toast("Tenure must be between 1 and 60 months");
            return;
        }
        try {
            if (!rate.isEmpty()) {
                Double.parseDouble(rate);
            }
        } catch (Exception e) {
            toast("Rate must be a number, e.g. 0.10 for 10%");
            return;
        }
        EmiPackage p = new EmiPackage();
        p.productCode = code;
        p.productWording = rate.isEmpty() ? "0" : rate;
        p.interestIndex = index;
        p.interestWording = desc.isEmpty() ? tenure + " MONTHS" : desc;
        p.tenure = tenure;
        EmiStore.get(this).add(p);

        etEmiCode.setText("");
        etEmiTenure.setText("");
        etEmiRate.setText("");
        etEmiIndex.setText("");
        etEmiDesc.setText("");
        showEmi();
        status("EMI PACKAGE SAVED\n\n" + p.tenure + " months, product " + p.productCode
                + "\nrate " + p.rate() + "\n\nTK 10,000 over " + p.tenure
                + " months = TK " + com.hjq.demo.utils.Slip.money(p.installment(1000000))
                + " per month.");
    }

    /** Print the EMI package table - PAX's PARAM_EMI_PACKAGE_PRINT. */
    private void printEmiPackages() {
        final java.util.List<EmiPackage> packages = EmiStore.get(this).all();
        if (packages.isEmpty()) {
            status("No EMI packages to print");
            return;
        }
        TerminalConfig tc = TerminalConfig.get(this);
        final java.util.List<com.hjq.demo.utils.Slip.Line> o =
                com.hjq.demo.utils.Slip.list();
        o.add(com.hjq.demo.utils.Slip.centre("EMI PACKAGES", com.hjq.demo.utils.Slip.MID));
        o.add(com.hjq.demo.utils.Slip.blank());
        o.add(com.hjq.demo.utils.Slip.pair("MID: " + tc.mid(), "TID: " + tc.tid()));
        o.add(com.hjq.demo.utils.Slip.rule());
        o.add(new com.hjq.demo.utils.Slip.Line(
                com.hjq.demo.utils.Slip.row3("TENURE", "CODE", "RATE"), "",
                com.hjq.demo.utils.Slip.SMALL, com.hjq.demo.utils.Slip.BOLD));
        o.add(com.hjq.demo.utils.Slip.rule());
        for (EmiPackage p : packages) {
            o.add(new com.hjq.demo.utils.Slip.Line(
                    com.hjq.demo.utils.Slip.row3(p.tenure + " MONTHS", p.productCode,
                            String.format(java.util.Locale.US, "%.2f%%", p.rate() * 100)),
                    "", com.hjq.demo.utils.Slip.SMALL, 0));
            if (!p.interestWording.isEmpty()) {
                o.add(com.hjq.demo.utils.Slip.text("  " + p.interestWording));
            }
        }
        o.add(com.hjq.demo.utils.Slip.rule());
        // A worked example, so the merchant can sanity-check the rates.
        o.add(com.hjq.demo.utils.Slip.text("Example on TK 10,000.00:"));
        for (EmiPackage p : packages) {
            o.add(com.hjq.demo.utils.Slip.pair("  " + p.tenure + " months",
                    "TK " + com.hjq.demo.utils.Slip.money(p.installment(1000000)) + "/mo"));
        }
        o.add(com.hjq.demo.utils.Slip.rule());
        o.add(com.hjq.demo.utils.Slip.blank());
        o.add(com.hjq.demo.utils.Slip.blank());

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    android.graphics.Bitmap bmp =
                            com.hjq.demo.utils.Slip.toBitmap(o, null);
                    com.hjq.demo.utils.SlipPrinter.print(mPrinter, bmp, (ok, m) -> { });
                } catch (Exception e) {
                    com.hjq.demo.utils.LogUtils.e("EMI package print: " + e.getMessage());
                }
            }
        }).start();
        status("Printing " + packages.size() + " EMI package(s)");
    }

    // ------------------------------------------------------------ card range

    /**
     * Draw the current table, one row per range with a DELETE next to it.
     *
     * <p>A card outside every range is refused at the terminal - the PAX
     * reference does the same from the same file. The name is what the receipt
     * prints, which is how TAKAPAY and NEXUSPAY cards identify themselves
     * instead of being guessed at from the first digit of the PAN.</p>
     */
    private void showRanges() {
        if (rangeRows == null) {
            return;
        }
        rangeRows.removeAllViews();
        final java.util.List<CardRangeTable.Range> list = CardRangeTable.all(this);

        TextView head = new TextView(this);
        head.setText(list.size() + " range(s)"
                + (CardRangeTable.isEdited(this) ? "  (edited)" : "  (default table)"));
        head.setTextColor(0xFF5B7BF6);
        head.setTextSize(12);
        rangeRows.addView(head);

        for (int i = 0; i < list.size(); i++) {
            final int pos = i;
            CardRangeTable.Range r = list.get(i);

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(8), 0, dp(8));

            // ---- editable name. This is what prints on the slip, so it has to
            // be changeable in place: renaming MASTER to MASTERCARD should not
            // mean deleting the range and retyping both bounds.
            final EditText name = new EditText(this);
            name.setText(r.name);
            name.setHint("NAME ON SLIP");
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setAllCaps(true);
            name.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            block.addView(name);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView bounds = new TextView(this);
            bounds.setText(r.low + " - " + r.high);
            bounds.setTextColor(0xFF555555);
            bounds.setTextSize(12);
            bounds.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(bounds);

            // Which of these the bank issues itself. It decides the ON-US /
            // OFF-US line on every sale and void slip, so it is edited here
            // beside the range rather than in a list somewhere else.
            final android.widget.CheckBox onUs = new android.widget.CheckBox(this);
            onUs.setText("ON-US");
            onUs.setTextSize(11);
            onUs.setChecked(r.onUs);
            row.addView(onUs);

            Button save = new Button(this);
            save.setText("SAVE");
            save.setTextSize(11);
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editRange(pos, name.getText() == null ? "" : name.getText().toString(),
                            onUs.isChecked());
                }
            });
            row.addView(save);

            Button del = new Button(this);
            del.setText("DELETE");
            del.setTextSize(11);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteRange(pos);
                }
            });
            row.addView(del);

            block.addView(row);
            rangeRows.addView(block);

            View sep = new View(this);
            sep.setBackgroundColor(0xFFE4E7F0);
            sep.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            rangeRows.addView(sep);
        }
    }

    /**
     * Edit a range in place: the name the receipt prints, and whether the bank
     * issues these cards itself.
     */
    private void editRange(int pos, String raw, boolean onUs) {
        String name = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.US);
        if (name.isEmpty()) {
            toast("The name cannot be blank - it prints on the slip");
            return;
        }
        java.util.List<CardRangeTable.Range> cur = CardRangeTable.all(this);
        if (pos >= cur.size()) {
            return;
        }
        CardRangeTable.Range r = cur.get(pos);
        String was = r.name;
        boolean wasOnUs = r.onUs;
        if (name.equals(was) && onUs == wasOnUs) {
            toast("No change");
            return;
        }
        r.name = name;
        r.onUs = onUs;
        CardRangeTable.save(this, cur);
        showRanges();
        StringBuilder s = new StringBuilder("RANGE UPDATED\n\n");
        s.append(name).append('\n').append(r.low).append(" - ").append(r.high).append("\n\n");
        if (!name.equals(was)) {
            s.append("Cards in this range now print as ").append(name)
                    .append(" (was ").append(was).append(").\n");
        }
        if (onUs != wasOnUs) {
            s.append("Slips now say ").append(onUs ? "ON-US" : "OFF-US")
                    .append(" for these cards.");
        }
        status(s.toString());
    }

    private void deleteRange(int pos) {
        java.util.List<CardRangeTable.Range> cur = CardRangeTable.all(this);
        if (pos >= cur.size()) {
            return;
        }
        CardRangeTable.Range gone = cur.remove(pos);
        CardRangeTable.save(this, cur);
        showRanges();
        toast("Removed " + gone.name + " (" + gone.low + " - " + gone.high + ")");
    }

    /** Add a range from the three fields, with the checks that matter. */
    private void addRange() {
        String name = str(etRangeName).trim().toUpperCase(java.util.Locale.US);
        String lowIn = str(etRangeLow).trim();
        String highIn = str(etRangeHigh).trim();

        if (name.isEmpty()) {
            toast("Enter a name - it is what prints on the slip");
            return;
        }
        if (!lowIn.matches("\\d+") || !highIn.matches("\\d+")) {
            toast("Low and high must be digits only");
            return;
        }
        // Take a BIN of any length and widen it to the comparison width: the
        // low bound pads with zeros, the high bound with nines. Demanding
        // exactly 12 digits read as a rule about card length, which it never
        // was - the table compares the leading 12 digits of a PAN of any
        // length from 11 to 19.
        String low = CardRangeTable.widenBound(lowIn, false);
        String high = CardRangeTable.widenBound(highIn, true);
        if (low.isEmpty() || high.isEmpty()) {
            toast("Enter the range, e.g. 100002 to 100002");
            return;
        }
        if (low.compareTo(high) > 0) {
            toast("Low must not be greater than high");
            return;
        }
        java.util.List<CardRangeTable.Range> list = CardRangeTable.all(this);
        // A BIN inside a scheme range is the normal case, not a mistake: 444515
        // is a DBBL VISA card and 4xxxxx is every VISA card, and the whole
        // point of adding the first is to say something the second does not.
        // The narrower one goes to the front of the table, because the lookup
        // takes the first range that matches.
        //
        // A partial overlap is still refused. Two ranges that each cover part
        // of the other make the printed name depend on table order with no way
        // to see which is meant to win - the kind of thing nobody notices
        // until a slip says the wrong scheme.
        boolean refines = false;
        for (CardRangeTable.Range r : list) {
            boolean touches = low.compareTo(r.high) <= 0 && high.compareTo(r.low) >= 0;
            if (!touches) {
                continue;
            }
            if (low.equals(r.low) && high.equals(r.high)) {
                toast("Already in the table as " + r.name);
                return;
            }
            boolean inside = low.compareTo(r.low) >= 0 && high.compareTo(r.high) <= 0;
            if (!inside) {
                toast("Partly overlaps " + r.name + " (" + r.low + " - " + r.high + ")");
                return;
            }
            refines = true;
        }
        CardRangeTable.Range add = new CardRangeTable.Range();
        add.name = name;
        add.low = low;
        add.high = high;
        add.onUs = isChecked(R.id.cb_range_onus);
        if (refines) {
            list.add(0, add);
        } else {
            list.add(add);
        }
        CardRangeTable.save(this, list);
        etRangeName.setText("");
        etRangeLow.setText("");
        etRangeHigh.setText("");
        setChecked(R.id.cb_range_onus, false);
        showRanges();
        status("CARD RANGE ADDED\n\n" + name + "\n" + low + " - " + high
                + "\n\nCards in this range are accepted and print as " + name + "."
                + (add.onUs ? "\nSlips say ON-US for these cards."
                            : "\nSlips say OFF-US for these cards.")
                + (refines ? "\n\nPlaced ahead of the wider range it sits inside,"
                             + " so it is the one that matches." : ""));
    }

    // -------------------------------------------------------- EMI card range

    /**
     * The EMI range table, drawn the same way as the acquiring one.
     *
     * <p>Its own list rather than a tick on the ordinary rows. The acquiring
     * table says which cards the terminal takes; this says which of them carry
     * an instalment programme, and the bank hands the two lists out separately.
     * A card missing from here is still perfectly good for an ordinary sale -
     * it is only EMI that is refused, with THIS CARD DOES NOT SUPPORT EMI.</p>
     */
    private void showEmiRanges() {
        if (emiRangeRows == null) {
            return;
        }
        emiRangeRows.removeAllViews();
        final java.util.List<CardRangeTable.Range> list = CardRangeTable.allEmi(this);

        TextView head = new TextView(this);
        head.setText(list.isEmpty()
                ? "No EMI ranges - NO card can be sold on EMI"
                : list.size() + " EMI range(s)");
        head.setTextColor(0xFF5B7BF6);
        head.setTextSize(12);
        emiRangeRows.addView(head);

        for (int i = 0; i < list.size(); i++) {
            final int pos = i;
            CardRangeTable.Range r = list.get(i);

            LinearLayout block = new LinearLayout(this);
            block.setOrientation(LinearLayout.VERTICAL);
            block.setPadding(0, dp(8), 0, dp(8));

            final EditText name = new EditText(this);
            name.setText(r.name);
            name.setHint("EMI PROGRAMME");
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setAllCaps(true);
            name.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            block.addView(name);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView bounds = new TextView(this);
            bounds.setText(r.low + " - " + r.high);
            bounds.setTextColor(0xFF555555);
            bounds.setTextSize(12);
            bounds.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(bounds);

            Button save = new Button(this);
            save.setText("SAVE");
            save.setTextSize(11);
            save.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editEmiRange(pos, name.getText() == null ? "" : name.getText().toString());
                }
            });
            row.addView(save);

            Button del = new Button(this);
            del.setText("DELETE");
            del.setTextSize(11);
            del.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteEmiRange(pos);
                }
            });
            row.addView(del);

            block.addView(row);
            emiRangeRows.addView(block);

            View sep = new View(this);
            sep.setBackgroundColor(0xFFE4E7F0);
            sep.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));
            emiRangeRows.addView(sep);
        }
    }

    /** The one-line summary under the menu row, so the state is visible without opening it. */
    private void refreshEmiRangeSummary() {
        if (tvEmiRangeValue == null) {
            return;
        }
        int n = CardRangeTable.allEmi(this).size();
        tvEmiRangeValue.setText(n == 0
                ? "Not set - EMI is refused on every card"
                : n + " range(s)");
    }

    /** Rename an EMI range in place. The bounds are not editable, same as the acquiring table. */
    private void editEmiRange(int pos, String raw) {
        String name = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.US);
        if (name.isEmpty()) {
            toast("The name cannot be blank");
            return;
        }
        java.util.List<CardRangeTable.Range> cur = CardRangeTable.allEmi(this);
        if (pos >= cur.size()) {
            return;
        }
        CardRangeTable.Range r = cur.get(pos);
        if (name.equals(r.name)) {
            toast("No change");
            return;
        }
        String was = r.name;
        r.name = name;
        CardRangeTable.saveEmi(this, cur);
        showEmiRanges();
        status("EMI RANGE UPDATED\n\n" + name + "\n" + r.low + " - " + r.high
                + "\n\nWas " + was + ".");
    }

    private void deleteEmiRange(int pos) {
        java.util.List<CardRangeTable.Range> cur = CardRangeTable.allEmi(this);
        if (pos >= cur.size()) {
            return;
        }
        CardRangeTable.Range gone = cur.remove(pos);
        CardRangeTable.saveEmi(this, cur);
        showEmiRanges();
        refreshEmiRangeSummary();
        toast("Removed " + gone.name + " (" + gone.low + " - " + gone.high + ")");
        if (cur.isEmpty()) {
            status("EMI RANGES EMPTY\n\nThe last EMI range was removed, so NO card"
                    + " can be sold on EMI until a range is added.");
        }
    }

    /** Add an EMI range from the three fields. Same rules as the acquiring table. */
    private void addEmiRange() {
        String name = str(etEmiRangeName).trim().toUpperCase(java.util.Locale.US);
        String lowIn = str(etEmiRangeLow).trim();
        String highIn = str(etEmiRangeHigh).trim();

        if (name.isEmpty()) {
            toast("Enter a name for the EMI range");
            return;
        }
        if (!lowIn.matches("\\d+") || !highIn.matches("\\d+")) {
            toast("Low and high must be digits only");
            return;
        }
        String low = CardRangeTable.widenBound(lowIn, false);
        String high = CardRangeTable.widenBound(highIn, true);
        if (low.isEmpty() || high.isEmpty()) {
            toast("Enter the range, e.g. 441358 to 441358");
            return;
        }
        if (low.compareTo(high) > 0) {
            toast("Low must not be greater than high");
            return;
        }
        java.util.List<CardRangeTable.Range> list = CardRangeTable.allEmi(this);
        boolean refines = false;
        for (CardRangeTable.Range r : list) {
            boolean touches = low.compareTo(r.high) <= 0 && high.compareTo(r.low) >= 0;
            if (!touches) {
                continue;
            }
            if (low.equals(r.low) && high.equals(r.high)) {
                toast("Already in the EMI table as " + r.name);
                return;
            }
            boolean inside = low.compareTo(r.low) >= 0 && high.compareTo(r.high) <= 0;
            if (!inside) {
                toast("Partly overlaps " + r.name + " (" + r.low + " - " + r.high + ")");
                return;
            }
            refines = true;
        }
        boolean wasEmpty = list.isEmpty();
        CardRangeTable.Range add = new CardRangeTable.Range();
        add.name = name;
        add.low = low;
        add.high = high;
        if (refines) {
            list.add(0, add);
        } else {
            list.add(add);
        }
        CardRangeTable.saveEmi(this, list);
        etEmiRangeName.setText("");
        etEmiRangeLow.setText("");
        etEmiRangeHigh.setText("");
        showEmiRanges();
        refreshEmiRangeSummary();
        status("EMI CARD RANGE ADDED\n\n" + name + "\n" + low + " - " + high
                + "\n\nCards in this range can be sold on instalments."
                + (wasEmpty
                        ? "\n\nThis is the first EMI range. Cards outside the EMI"
                          + " table are refused with THIS CARD DOES NOT SUPPORT EMI."
                        : ""));
    }

    /** Emptying the table turns the EMI range check off, so it is worth a confirm. */
    private void confirmClearEmiRanges() {
        if (CardRangeTable.allEmi(this).isEmpty()) {
            toast("The EMI table is already empty");
            return;
        }
        new com.hjq.demo.ui.dialog.MessageDialog.Builder(this)
                .setTitle("CLEAR EMI RANGES")
                .setMessage("Remove every EMI card range?\n\nNO card will be able to"
                        + " do an EMI sale until a range is added again.")
                .setConfirm("CLEAR")
                .setCancel("CANCEL")
                .setListener(new com.hjq.demo.ui.dialog.MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(com.hjq.base.BaseDialog dialog) {
                        CardRangeTable.clearEmi(AdminActivity.this);
                        showEmiRanges();
                        refreshEmiRangeSummary();
                        toast("EMI ranges cleared");
                    }

                    @Override
                    public void onCancel(com.hjq.base.BaseDialog dialog) {
                        // nothing to do
                    }
                })
                .show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /**
     * Reflect each setting in its row: the value line under the title, and the
     * switch on the right for the boolean ones. The whole row is the click
     * target, so the switch is display-only - a switch that could also be
     * dragged would give two ways to change one setting and they would drift.
     */
    private void refreshToggleButtons() {
        TerminalConfig tc = TerminalConfig.get(this);
        if (tvCardMaskValue != null) {
            tvCardMaskValue.setText(CardMask.label(tc.cardMask()) + "   "
                    + CardMask.maskGrouped("4466172092075908", tc.cardMask()));
        }
        setSwitchRow(tvDebugValue, swDebug, tc.printPacketTrace(),
                "On - packet log prints on every slip", "Off");
        setSwitchRow(tvSettleDetailValue, swSettleDetail, tc.settleDetailReport(),
                "On - detail report prints before the totals", "Off");
        setSwitchRow(tvPinPadValue, swPinPad, tc.pinPadDynamic(),
                "Dynamic - keys shuffled on every entry", "Static - 1-2-3 order");

        // Re-attach per-switch listeners AFTER setChecked (setSwitchRow clears them)
        if (swDebug != null) {
            swDebug.setOnCheckedChangeListener((btn, on) -> {
                if (TerminalConfig.get(this).printPacketTrace() != on) {
                    toggleDebug();
                }
            });
        }
        if (swSettleDetail != null) {
            swSettleDetail.setOnCheckedChangeListener((btn, on) -> {
                if (TerminalConfig.get(this).settleDetailReport() != on) {
                    toggleSettleDetail();
                }
            });
        }
        if (swPinPad != null) {
            swPinPad.setOnCheckedChangeListener((btn, on) -> {
                if (TerminalConfig.get(this).pinPadDynamic() != on) {
                    togglePinPadMode();
                }
            });
        }
    }

    private void setSwitchRow(TextView value, android.widget.Switch sw,
                              boolean on, String onText, String offText) {
        if (value != null) {
            value.setText(on ? onText : offText);
        }
        if (sw != null) {
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(on);
        }
    }

    /** Step through the masking modes; each press saves immediately. */
    private void cycleCardMask() {
        TerminalConfig tc = TerminalConfig.get(this);
        int next = (tc.cardMask() + 1) % 4;
        tc.setCardMask(next);
        refreshToggleButtons();
        status("CARD MASKING: " + CardMask.label(next)
                + "\n\nexample:\n" + CardMask.maskGrouped("4466172092075908", next));
    }

    /** Flip debug printing; saves immediately. */
    private void toggleDebug() {
        TerminalConfig tc = TerminalConfig.get(this);
        boolean on = !tc.printPacketTrace();
        tc.setPrintPacketTrace(on);
        refreshToggleButtons();
        status(on ? "DEBUG ON\n\nThe full ISO-8583 request and response\n"
                        + "are printed on every slip.\n\n"
                        + "Turn this OFF for production - the trace\n"
                        + "contains track 2 data and the PIN block."
                  : "DEBUG OFF\n\nNo packet log is printed.");
    }

    /** Static keeps the digits where cardholders expect them; dynamic shuffles. */
    private void togglePinPadMode() {
        TerminalConfig tc = TerminalConfig.get(this);
        boolean dynamic = !tc.pinPadDynamic();
        tc.setPinPadDynamic(dynamic);
        refreshToggleButtons();
        status(dynamic
                ? "PIN PAD: DYNAMIC\n\nThe digits are shuffled on every PIN\n"
                        + "entry, so the PIN cannot be read from\n"
                        + "the finger positions by someone watching.\n\n"
                        + "Slower to key, and more mis-keys - older\n"
                        + "cardholders in particular find it hard."
                : "PIN PAD: STATIC\n\nDigits laid out 1-2-3 / 4-5-6 / 7-8-9,\n"
                        + "where a cardholder expects them.\n\n"
                        + "This is what the PAX terminal does.");
    }

    /** Print the per-transaction list before the settlement slip, or not. */
    private void toggleSettleDetail() {
        TerminalConfig tc = TerminalConfig.get(this);
        boolean on = !tc.settleDetailReport();
        tc.setSettleDetailReport(on);
        refreshToggleButtons();
        status(on ? "SETTLE DETAILS REPORT ON\n\nThe transaction list prints before\n"
                        + "the settlement slip, so the detail can\n"
                        + "be checked against the totals."
                  : "SETTLE DETAILS REPORT OFF\n\nOnly the settlement totals print.");
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        try {
            mPinPad = IPinPadBinderService.Stub.asInterface(
                    sdkServiceManager.getService(ServiceID.SERVICE_ID_PINPAD));
            mPrinter = IPrinterBinderService.Stub.asInterface(
                    sdkServiceManager.getService(ServiceID.SERVICE_ID_PRINTER));
            mKeyManager = new KeyManager(mPinPad);
        } catch (Exception e) {
            status("SDK bind error: " + e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btn_device_settings) {
            com.hjq.demo.utils.PasswordPrompt.ask(this, "DEVICE SETTINGS", TerminalConfig.get(this).settingsPw(),
                    () -> com.hjq.demo.utils.TerminalLockdown.openSettings(this));
        } else if (id == R.id.btn_device_lock) {
            status(com.hjq.demo.utils.TerminalLockdown.status());
            com.hjq.demo.utils.TerminalLockdown.refresh(this);
        } else if (id == R.id.btn_card_mask) {
            cycleCardMask();
            return;
        } else if (id == R.id.btn_debug) {
            toggleDebug();
            return;
        } else if (id == R.id.btn_settle_detail) {
            toggleSettleDetail();
            return;
        } else if (id == R.id.btn_pinpad_mode) {
            togglePinPadMode();
            return;
        } else if (id == R.id.btn_opt_ranges) {
            showRanges();
            showPage(PAGE_RANGES);
            return;
        } else if (id == R.id.btn_back_ranges) {
            showPage(PAGE_MENU);
            return;
        } else if (id == R.id.btn_range_add) {
            addRange();
            return;
        } else if (id == R.id.btn_opt_emi_ranges) {
            showEmiRanges();
            showPage(PAGE_EMI_RANGES);
            return;
        } else if (id == R.id.btn_back_emi_ranges) {
            refreshEmiRangeSummary();
            showPage(PAGE_MENU);
            return;
        } else if (id == R.id.btn_emi_range_add) {
            addEmiRange();
            return;
        } else if (id == R.id.btn_emi_range_clear) {
            confirmClearEmiRanges();
            return;
        } else if (id == R.id.btn_opt_clear) {
            showClear();
            showPage(PAGE_CLEAR);
            return;
        } else if (id == R.id.btn_back_clear) {
            showPage(PAGE_MENU);
            return;
        } else if (id == R.id.btn_clear_batch) {
            confirmClearBatch();
            return;
        } else if (id == R.id.btn_clear_reversal) {
            confirmClearReversal();
            return;
        } else if (id == R.id.btn_clear_preauth) {
            confirmClearPreAuth();
            return;
        } else if (id == R.id.btn_clear_errors) {
            confirmClearErrors();
            return;
        } else if (id == R.id.btn_opt_menuctl) {
            showMenuControl();
            showPage(PAGE_MENUCTL);
            return;
        } else if (id == R.id.btn_back_menuctl) {
            showPage(PAGE_MENU);
            return;
        } else if (id == R.id.btn_opt_emi) {
            showEmi();
            showPage(PAGE_EMI);
            return;
        } else if (id == R.id.btn_back_emi) {
            saveEmiNii();
            showPage(PAGE_MENU);
            return;
        } else if (id == R.id.btn_emi_enabled) {
            TerminalConfig tc = TerminalConfig.get(this);
            tc.setEmiEnabled(!tc.emiEnabled());
            showEmi();
            return;
        } else if (id == R.id.btn_emi_pkg_print) {
            TerminalConfig tc = TerminalConfig.get(this);
            tc.setEmiPackagePrint(!tc.emiPackagePrint());
            showEmi();
            return;
        } else if (id == R.id.btn_emi_add) {
            addEmiPackage();
            return;
        } else if (id == R.id.btn_emi_print) {
            printEmiPackages();
            return;
        } else if (id == R.id.btn_range_reset) {
            CardRangeTable.restoreDefaults(this);
            showRanges();
            toast("Default card ranges restored");
            return;
        }
        if (id == R.id.btn_opt_comm) {
            showPage(PAGE_COMM);
        } else if (id == R.id.btn_opt_keys) {
            showPage(PAGE_KEYS);
        } else if (id == R.id.btn_opt_pw) {
            showPage(PAGE_PW);
        } else if (id == R.id.btn_opt_logon) {
            showPage(PAGE_LOGON);
        } else if (id == R.id.btn_opt_counters) {
            showPage(PAGE_COUNTERS);
        } else if (id == R.id.btn_back_comm || id == R.id.btn_back_keys
                || id == R.id.btn_back_pw || id == R.id.btn_back_logon
                || id == R.id.btn_back_counters) {
            showPage(PAGE_MENU);
        } else if (id == R.id.btn_save_comm) {
            saveComm();
        } else if (id == R.id.btn_inject_master) {
            injectMaster();
        } else if (id == R.id.btn_inject_working) {
            injectWorking();
        } else if (id == R.id.btn_read_kcv) {
            readKcv();
        } else if (id == R.id.btn_print_kcv) {
            printKcv();
        } else if (id == R.id.btn_set_pw) {
            setPassword();
        } else if (id == R.id.btn_clear_pw) {
            clearPassword();
        } else if (id == R.id.btn_run_logon) {
            runLogonAdmin();
        } else if (id == R.id.btn_save_counters) {
            saveCounters();
        } else if (id == R.id.btn_de62_diag) {
            cycleWorkingKeyLength();
        } else if (id == R.id.btn_print_logon) {
            printLastLogon();
        } else if (id == R.id.btn_opt_param) {
            loadParams();
            showPage(PAGE_PARAM);
        } else if (id == R.id.btn_back_param) {
            showPage(PAGE_MENU);
        } else if (id == R.id.btn_save_qr) {
            if (saveQrParams(true)) toast("QR settings saved");
        } else if (id == R.id.btn_save_tip) {
            if (saveTipParams()) {
                status("TIP SETTINGS SAVED\n\nSale with Tip: " + (TerminalConfig.get(this).saleWithTip() ? "ON" : "OFF")
                        + "\nTip Adjust: " + (TerminalConfig.get(this).tipAdjustEnabled() ? "ON" : "OFF")
                        + "\nMaximum adjustment tip: " + TerminalConfig.get(this).tipPercentage() + "%");
                toast("Tip settings saved");
            }
        } else if (id == R.id.btn_save_param) {
            saveParams();
        }
    }

    private void loadParams() {
        TerminalConfig tc = TerminalConfig.get(this);
        if (etClessLimit != null) {
            etClessLimit.setText(tc.clessCvmLimitText());
        }
        // The local values, not the effective ones: showing a host-pushed name
        // in an editable box invites the operator to "correct" a field the next
        // logon overwrites anyway.
        if (etMerchantName != null) {
            etMerchantName.setText(tc.merchantNameLocal());
        }
        for (int i = 0; i < etAddr.length; i++) {
            if (etAddr[i] != null) {
                etAddr[i].setText(tc.merchantAddrLocal(i + 1));
            }
        }
        if (etIdleTimeout != null) {
            etIdleTimeout.setText(String.valueOf(tc.idleTimeoutSec()));
        }
        if (etPanMinLen != null) {
            etPanMinLen.setText(String.valueOf(tc.panMinLen()));
        }
        if (etPanMaxLen != null) {
            etPanMaxLen.setText(String.valueOf(tc.panMaxLen()));
        }
        if (cbBankLogo != null) {
            cbBankLogo.setChecked(tc.printBankLogo());
        }
        if (cbMerchantLogo != null) {
            cbMerchantLogo.setChecked(tc.printMerchantLogo());
        }
        setChecked(cbCheckExpiringDate, tc.checkExpiringDate());
        setChecked(cbCustomerCopy, tc.customerCopyEnabled());
        setChecked(cbEntryChip, tc.entryChip());
        setChecked(cbEntryCtls, tc.entryContactless());
        setChecked(cbEntrySwipe, tc.entrySwipe());
        setChecked(cbEntryFallback, tc.entryFallback());
        ((android.widget.EditText) findViewById(R.id.et_qr_nii)).setText(tc.qrNiiOverride());
        ((android.widget.EditText) findViewById(R.id.et_qr_tpdu)).setText(tc.qrTpdu());
        ((android.widget.EditText) findViewById(R.id.et_qr_mcc)).setText(tc.qrMcc());
        android.widget.EditText etQrSid = findViewById(R.id.et_qr_sid);
        if (etQrSid != null) etQrSid.setText(tc.qrSid());
        ((android.widget.EditText) findViewById(R.id.et_qr_timeout)).setText(String.valueOf(tc.qrTimeoutSec()));
        ((android.widget.CheckBox) findViewById(R.id.cb_sale_tip)).setChecked(tc.saleWithTip());
        ((android.widget.CheckBox) findViewById(R.id.cb_tip_adjust)).setChecked(tc.tipAdjustEnabled());
        ((android.widget.EditText) findViewById(R.id.et_tip_percent)).setText(String.valueOf(tc.tipPercentage()));
        android.widget.EditText etPreAuthPct = findViewById(R.id.et_preauth_percent);
        if (etPreAuthPct != null) etPreAuthPct.setText(String.valueOf(tc.preAuthPercentage()));
        setChecked(cbEntryManual, tc.entryManual());
    }

    private static void setChecked(android.widget.CheckBox b, boolean v) {
        if (b != null) {
            b.setChecked(v);
        }
    }

    private static boolean isChecked(android.widget.CheckBox b, boolean fallback) {
        return b == null ? fallback : b.isChecked();
    }

    /**
     * Save the contactless CVM Required Limit.
     *
     * <p>Stored in minor units. Rejecting an unparseable value rather than
     * silently defaulting matters here: a limit that quietly became 0 would ask
     * for a PIN on every tap, and one that quietly became huge would take no PIN
     * at all on any amount.</p>
     */
    /** Read and commit the visible values before any parameter refresh. */
    private String qrField(int id) {
        android.view.View v = findViewById(id);
        return v instanceof android.widget.EditText ? ((android.widget.EditText) v).getText().toString().trim() : "";
    }
    private boolean saveQrParams(boolean required) {
        try {
            int timeout = 60;
            String tStr = qrField(R.id.et_qr_timeout);
            if (!tStr.isEmpty()) {
                timeout = Integer.parseInt(tStr);
            }
            TerminalConfig.get(this).setQrParameters(
                    qrField(R.id.et_qr_nii),
                    qrField(R.id.et_qr_tpdu),
                    qrField(R.id.et_qr_mcc),
                    timeout,
                    qrField(R.id.et_qr_sid));
            return true;
        } catch (NumberFormatException e) {
            if (required) toast("QR timeout must be 10 to 600 seconds");
            return !required;
        } catch (RuntimeException e) {
            if (required) toast(e.getMessage());
            return !required;
        }
    }

    /** Pre Auth Percentage: 100 to 999, blank keeps 125. */
    private boolean savePreAuthPercent() {
        android.widget.EditText et = findViewById(R.id.et_preauth_percent);
        if (et == null) return true;
        String v = et.getText().toString().trim();
        int pct;
        try { pct = v.isEmpty() ? TerminalConfig.DEF_PREAUTH_PERCENT : Integer.parseInt(v); }
        catch (Exception e) { toast("Enter a Pre Auth Percentage from 100 to 999"); return false; }
        if (pct < 100 || pct > 999) { toast("Pre Auth Percentage must be 100 to 999"); return false; }
        try { TerminalConfig.get(this).setPreAuthPercentage(pct); }
        catch (RuntimeException e) { toast(e.getMessage()); return false; }
        return true;
    }

    private boolean saveTipParams() {
        int tipPercent;
        try { tipPercent = Integer.parseInt(((android.widget.EditText) findViewById(R.id.et_tip_percent)).getText().toString().trim()); }
        catch (Exception e) { toast("Enter a Tip percentage from 0 to 100"); return false; }
        if (tipPercent < 0 || tipPercent > 100) { toast("Tip percentage must be 0 to 100"); return false; }
        final boolean saleTip = ((android.widget.CheckBox) findViewById(R.id.cb_sale_tip)).isChecked();
        final boolean adjustTip = ((android.widget.CheckBox) findViewById(R.id.cb_tip_adjust)).isChecked();
        try { TerminalConfig.get(this).setTipSettings(saleTip, adjustTip, tipPercent); }
        catch (IllegalStateException e) { toast(e.getMessage()); return false; }
        return true;
    }

    private void saveParams() {
        long minor = TerminalConfig.parseMajorToMinor(str(etClessLimit));
        if (minor < 0) {
            toast("Enter an amount like 5000.00");
            return;
        }

        int minLen = intOf(etPanMinLen, TerminalConfig.DEF_PAN_MIN_LEN);
        int maxLen = intOf(etPanMaxLen, TerminalConfig.DEF_PAN_MAX_LEN);
        if (minLen < TerminalConfig.PAN_LEN_FLOOR || maxLen > TerminalConfig.PAN_LEN_CEILING
                || minLen > maxLen) {
            toast("Card length must be " + TerminalConfig.PAN_LEN_FLOOR
                    + " to " + TerminalConfig.PAN_LEN_CEILING + ", min first");
            return;
        }

        // 0 switches it off; anything else is clamped to the ceiling rather
        // than rejected, because a typo of 6000 for 600 should not cost the
        // operator the whole form.
        int idle = intOf(etIdleTimeout, TerminalConfig.DEF_IDLE_TIMEOUT);
        if (idle < 0) {
            toast("Menu timeout cannot be negative");
            return;
        }

        TerminalConfig tc = TerminalConfig.get(this);
        tc.setClessCvmLimit(minor);
        tc.setIdleTimeoutSec(idle);
        if (etMerchantName != null) {
            tc.setMerchantName(str(etMerchantName));
        }
        for (int i = 0; i < etAddr.length; i++) {
            if (etAddr[i] != null) {
                tc.setMerchantAddr(i + 1, str(etAddr[i]));
            }
        }
        tc.setPanLenRange(minLen, maxLen);
        if (cbBankLogo != null) {
            tc.setPrintBankLogo(cbBankLogo.isChecked());
        }
        if (cbMerchantLogo != null) {
            tc.setPrintMerchantLogo(cbMerchantLogo.isChecked());
        }
        if (cbCheckExpiringDate != null) {
            tc.setCheckExpiringDate(cbCheckExpiringDate.isChecked());
        }
        if (cbCustomerCopy != null) {
            tc.setCustomerCopyEnabled(cbCustomerCopy.isChecked());
        }

        // A terminal with every entry mode off cannot take a transaction at
        // all, and would fail at the card screen with nothing to explain it.
        // Refuse the save instead, while the operator is still looking at it.
        boolean chip = isChecked(cbEntryChip, tc.entryChip());
        boolean ctls = isChecked(cbEntryCtls, tc.entryContactless());
        boolean swipe = isChecked(cbEntrySwipe, tc.entrySwipe());
        boolean fallback = isChecked(cbEntryFallback, tc.entryFallback());
        boolean manual = isChecked(cbEntryManual, tc.entryManual());
        if (!chip && !ctls && !swipe && !fallback && !manual) {
            toast("Enable at least one entry mode");
            return;
        }
        if (!saveTipParams() || !savePreAuthPercent() || !saveQrParams(false)) return;
        tc.setEntryChip(chip);
        tc.setEntryContactless(ctls);
        tc.setEntrySwipe(swipe);
        tc.setEntryFallback(fallback);
        tc.setEntryManual(manual);

        loadParams();

        status("PARAMETER SAVED\n\nContactless limit: " + tc.clessCvmLimitText()
                + "\n\nA tap at or below this amount\ncompletes with no PIN."
                + "\nAbove it the terminal asks for a PIN."
                + "\n\nEntry: " + entrySummary(tc)
                + "\n\nHeader: " + tc.merchantName()
                + "\nCard length: " + tc.panLenRangeText() + " digits"
                + "\nMenu timeout: " + tc.idleTimeoutText()
                + (tc.idleTimeoutSec() == 0 ? "" : " seconds")
                + "\nLogo: " + (tc.printMerchantLogo() ? "bank + merchant (half/half)"
                        : tc.printBankLogo() ? "bank only (full)" : "none")
                + "\nCheck Expiry: " + (tc.checkExpiringDate() ? "ON" : "OFF")
                + "\nCustomer Copy: " + (tc.customerCopyEnabled() ? "ON" : "OFF"));
        toast("Parameters saved");
    }

    /**
     * Put the last logon report on paper.
     *
     * <p>The DE62 content decides which reading of the working key is right, and
     * on site there is no PC to run logcat against. A printed copy can also be
     * read to the acquirer over the phone.</p>
     */
    private void printLastLogon() {
        TerminalConfig tc = TerminalConfig.get(this);
        String text = tc.lastLogonReport();
        if (text.isEmpty()) {
            status("No logon recorded yet.\nRun LOGON first.");
            toast("Run a logon first");
            return;
        }
        text = text + "\n\n" + com.hjq.demo.ans8583.LogonData.candidateReport(
                tc.lastDe62(), tc.tmkClear());
        status(text);
        if (mPrinter != null) {
            printBitmap(textBitmap(text));
        } else {
            toast("Printer not ready - shown on screen");
        }
    }

    /**
     * Save the communication parameters, refusing an identity that cannot work.
     *
     * <p>DE41 and DE42 are fixed-length space-padded fields, so a blank TID
     * saves happily, packs happily, and produces a textbook 0800 that the host
     * answers with a connection reset. The terminal then reports a network
     * fault for what is really an empty box on this screen. Nothing here is
     * worth saving in that state, so it is refused and named.</p>
     */
    private void saveComm() {
        String tid = str(etTid);
        String mid = str(etMid);
        String ip = str(etIp);

        if (tid.isEmpty()) {
            refuseComm("Terminal ID is empty.\n\n"
                    + "DE41 is 8 characters and would go out as spaces. The\n"
                    + "host drops the connection, and the terminal reports it\n"
                    + "as a network fault.", etTid);
            return;
        }
        if (tid.length() > 8) {
            refuseComm("Terminal ID is " + tid.length() + " characters.\n\n"
                    + "DE41 is fixed at 8, so it would be truncated to\n'"
                    + tid.substring(0, 8) + "' and would not match the host.", etTid);
            return;
        }
        if (mid.isEmpty()) {
            refuseComm("Merchant ID is empty.\n\nDE42 would go out as 15 spaces.", etMid);
            return;
        }
        if (mid.length() > 15) {
            refuseComm("Merchant ID is " + mid.length() + " characters.\n\n"
                    + "DE42 is fixed at 15, so it would be truncated.", etMid);
            return;
        }

        TerminalConfig tc = TerminalConfig.get(this);
        tc.setTid(tid);
        tc.setMid(mid);
        tc.setHostIp(ip);
        tc.setHostPort(intOf(etPort, TerminalConfig.DEF_PORT));
        tc.setNii(str(etNii));
        tc.setTpdu(str(etTpdu));
        tc.setTimeout(intOf(etTimeout, TerminalConfig.DEF_TIMEOUT));
        tc.setSettleRequireDays(intOf(etSettleDays, 0));
        tc.setReversalMaxTries(intOf(etReversalTries, TerminalConfig.DEF_REVERSAL_TRIES));

        // Read back what was actually stored rather than what was typed, so a
        // save that silently did not take is visible immediately.
        StringBuilder b = new StringBuilder("PARAMETERS SAVED\n\n");
        b.append("TID  : ").append(tc.tid())
                .append(tc.tid().length() == 8 ? "" : "   (" + tc.tid().length() + " chars)")
                .append('\n');
        b.append("MID  : ").append(tc.mid()).append('\n');
        b.append("HOST : ").append(tc.hostIp().isEmpty() ? "<NOT SET>" : tc.hostIp())
                .append(':').append(tc.hostPort()).append('\n');
        b.append("NII  : ").append(tc.nii())
                .append("   TPDU: ").append(tc.tpdu()).append('\n');
        if (tc.hostIp().isEmpty()) {
            b.append("\nHost IP is empty - nothing will be sent until it is set.\n");
        }
        // Card masking, Debug and Settle Details Report are toggle buttons and
        // save themselves the moment they are pressed.
        b.append("\nCard masking: ").append(CardMask.label(tc.cardMask()))
                .append("\nDebug: ").append(tc.printPacketTrace() ? "ON" : "OFF")
                .append("\nSettle details report: ").append(tc.settleDetailReport() ? "ON" : "OFF")
                .append("\nSettle require days: ").append(tc.settleRequireDays() == 0
                        ? "off" : String.valueOf(tc.settleRequireDays()))
                .append("\nPIN pad: ").append(tc.pinPadDynamic() ? "DYNAMIC" : "STATIC")
                .append("\nReversal retry times: ").append(tc.reversalMaxTries());
        status(b.toString());
        refreshCommSummary();
        toast("Communication parameters saved");
    }

    /** Say why nothing was saved, and put the cursor where the fix goes. */
    private void refuseComm(String why, EditText focus) {
        TerminalConfig tc = TerminalConfig.get(this);
        status("NOT SAVED\n\n" + why
                + "\n\nStill stored:\n  TID '" + tc.tid() + "'\n  MID '" + tc.mid() + "'");
        toast("Not saved - " + (focus == etTid ? "Terminal ID" : "Merchant ID"));
        if (focus != null) {
            focus.requestFocus();
        }
    }

    /** Terminal identity on the admin menu, so a blank one is never hidden. */
    private void refreshCommSummary() {
        TextView v = findViewById(R.id.tv_comm_value);
        if (v == null) {
            return;
        }
        TerminalConfig tc = TerminalConfig.get(this);
        String tid = tc.tid().trim();
        if (tid.isEmpty()) {
            v.setText("TERMINAL ID NOT SET");
            v.setTextColor(0xFFD32F2F);
            return;
        }
        v.setText("TID " + tid + "   "
                + (tc.hostIp().isEmpty() ? "no host" : tc.hostIp() + ":" + tc.hostPort()));
        v.setTextColor(0xFF888888);
    }

    private void injectMaster() {
        if (mKeyManager == null) {
            toast("SDK not ready yet");
            return;
        }
        int index = intOf(etMkIndex, TerminalConfig.DEF_MK_INDEX);
        TerminalConfig.get(this).setMkIndex(index);
        String raw = str(etMasterKey);

        // A key ceremony hands over the TMK as two (or three) clear components
        // that must be XORed together. Injecting one component on its own gives
        // a terminal master key the acquirer's HSM knows nothing about, so every
        // working key it sends decrypts to noise. Accept the components here,
        // separated by space, comma, '+' or '^', and combine them.
        String[] parts = raw.split("[\\s,+^]+");
        StringBuilder work = new StringBuilder();
        String hex;
        if (parts.length > 1) {
            hex = combineComponents(parts, work);
            if (hex == null) {
                status(work.toString());
                toast("Components must be the same length");
                return;
            }
        } else {
            hex = raw;
        }

        if (hex.length() != 32 && hex.length() != 48) {
            toast("Master key must be 32 or 48 hex chars");
            return;
        }
        String before = mKeyManager.readKcv(index, 0x02);
        int ret = mKeyManager.loadMasterKey(index, hex);
        String kcv = mKeyManager.readKcv(index, 0x02);

        // Verify the key actually landed. posMasterKey can report success while
        // the slot stays empty, and an empty slot is not obvious: a key of all
        // zeros has the perfectly ordinary-looking KCV 8CA64D. Checking for that
        // is the only way to tell "no key" from "some key".
        boolean allZeroKey = KCV_ALL_ZEROS.equalsIgnoreCase(kcv);
        boolean unchanged = !before.isEmpty() && before.equalsIgnoreCase(kcv);
        if (ret != 0 || allZeroKey || unchanged) {
            String why = ret != 0 ? "posMasterKey returned " + ret
                    : allZeroKey ? "the slot reads as an ALL-ZEROS key (KCV " + kcv
                            + ") - nothing was written"
                    : "the KCV did not change (still " + kcv + ")";
            TerminalConfig.get(this).setTmkKcv("");
            status("*** MASTER KEY NOT LOADED ***\nindex=" + index
                    + "\nkey=" + hex
                    + "\n" + why
                    + "\n\nThe PIN pad does not hold this key."
                    + "\nDo not run a logon until this is fixed.");
            toast("Master key NOT loaded - " + why);
            return;
        }

        if (ret == 0) {
            // Any working key still in the pad was loaded under the OLD master
            // key, so it is now meaningless. Force a fresh logon rather than
            // letting a sale run against a stale PIN key.
            TerminalConfig.get(this).setLoggedOn(false);
            TerminalConfig.get(this).setKeyKcvs("", "");
            // Remember the TMK's KCV. From here on it must never change; every
            // logon checks it.
            TerminalConfig.get(this).setTmkKcv(kcv);
            // Kept so the logon can derive the DE62 working key itself and
            // verify the pad's own derivation against it.
            TerminalConfig.get(this).setTmkClear(hex);
        }
        status(work + "INJECT MASTER KEY\nindex=" + index
                + "\nkey=" + hex
                + "\nret=" + ret + (ret == 0 ? " (success)" : " (failed)")
                + "\nKCV=" + kcv
                + "\n\nCheck this KCV against the key\nceremony document."
                + (ret == 0 ? "\n\nWorking key cleared - run LOGON now." : ""));
        toast(ret == 0 ? "Master key injected - run logon"
                : "Master key failed (ret=" + ret + ")");
    }

    /**
     * XOR key components together into the final key.
     *
     * @param parts components as hex strings, all the same length
     * @param log   receives a human-readable breakdown for the status panel
     * @return the combined key as hex, or null if the components disagree in length
     */
    private static String combineComponents(String[] parts, StringBuilder log) {
        byte[] out = null;
        log.append("COMBINING ").append(parts.length).append(" COMPONENTS\n");
        for (String p : parts) {
            String h = p.replaceAll("[^0-9A-Fa-f]", "");
            if (h.isEmpty()) {
                continue;
            }
            byte[] b = com.hjq.demo.ans8583.Iso8583.hexToBytes(h);
            log.append("  ").append(h.toUpperCase(java.util.Locale.US)).append('\n');
            if (out == null) {
                out = b;
            } else {
                if (b.length != out.length) {
                    log.append("  !! length mismatch\n");
                    return null;
                }
                for (int i = 0; i < out.length; i++) {
                    out[i] ^= b[i];
                }
            }
        }
        if (out == null) {
            return null;
        }
        String combined = com.hjq.demo.ans8583.Iso8583.bytesToHex(out);
        log.append("  XOR = ").append(combined).append("\n\n");
        return combined;
    }

    /**
     * Cycle how a 16-byte DE62 logon block is interpreted, then re-logon.
     *
     * <p>0 = auto-detect (spec 6.27.2: 8-byte PIN key + 8-byte MAC key),
     * 16 = treat the whole field as one double-length PIN key. If DBBL's HSM
     * reports "Invalid PIN Block" (command DA, error 20) the working key we
     * loaded is not the one they expect, and this is the first thing to try.</p>
     */
    private void cycleWorkingKeyLength() {
        TerminalConfig tc = TerminalConfig.get(this);
        // 0 (whole field, default) -> 8 (spec split) -> 16 (double length) -> 0
        int next;
        switch (tc.wkLen()) {
            case 0:  next = 8;  break;
            case 8:  next = 16; break;
            default: next = 0;  break;
        }
        tc.setWkLen(next);
        tc.setLoggedOn(false);
        String desc = next == 0 ? "WHOLE FIELD = PIN key - DEFAULT"
                : next == 8 ? "8+8 split (literal spec 6.27.2)"
                : "16+16 split (double length keys)";

        // Show the last logon and what each reading would produce, so the
        // choice is settled against evidence rather than by trial and error.
        String report = com.hjq.demo.ans8583.LogonData.candidateReport(
                tc.lastDe62(), tc.tmkClear());
        String last = tc.lastLogonReport();

        status("DE62 KEY SPLIT\nnow: " + desc
                + "\n\nRun LOGON again, then retry a PIN sale.\n\n"
                + report
                + (last.isEmpty() ? "" : "\n\n" + last));
        toast("DE62 split: " + desc + " - run logon again");
    }

    private void injectWorking() {
        if (mKeyManager == null) {
            toast("SDK not ready yet");
            return;
        }
        int index = intOf(etMkIndex, TerminalConfig.DEF_MK_INDEX);
        TerminalConfig.get(this).setMkIndex(index);
        int keyId = intOf(etWkId, 3);
        String hex = str(etWkKey);
        String kcvIn = str(etWkKcv);

        // Key field empty + KCV filled = record the KCV the acquirer quoted for
        // the working key they send in DE62, rather than injecting anything.
        // Every logon then verifies the key that landed against it.
        if (hex.isEmpty() && !kcvIn.isEmpty()) {
            TerminalConfig.get(this).setExpectedTpkKcv(kcvIn);
            status("EXPECTED WORKING KEY KCV SAVED\n" + kcvIn.toUpperCase(java.util.Locale.US)
                    + "\n\nEvery logon will now check the key\nDBBL sends in DE62 against this."
                    + "\nA mismatch fails the logon with a\nclear message."
                    + "\n\nLeave the KCV box empty and press\nthis again to clear it.");
            toast("Expected KCV saved");
            return;
        }
        if (hex.isEmpty() && kcvIn.isEmpty()) {
            TerminalConfig.get(this).setExpectedTpkKcv("");
            status("EXPECTED WORKING KEY KCV CLEARED");
            toast("Expected KCV cleared");
            return;
        }

        if (hex.length() != 32 && hex.length() != 48) {
            toast("Working key must be 32 or 48 hex chars");
            return;
        }
        int ret = mKeyManager.loadWorkingKey(index, keyId, hex, kcvIn);
        String kcv = ret == 0 ? mKeyManager.readKcv(index, keyId) : "-";
        status("INJECT WORKING KEY\nindex=" + index + "  type=" + keyId
                + "\nret=" + ret + (ret == 0 ? " (success)" : " (failed)") + "\nKCV=" + kcv);
        toast(ret == 0 ? "Working key injected" : "Working key failed (ret=" + ret + ")");
    }

    /**
     * PIN block diagnostic.
     *
     * <p>Shows the PIN pad with a fixed, known PAN and prints the raw block it
     * returns. Because the clear working key can be derived offline from the
     * TMK and the DE62 logon response, the expected block for a known PIN can
     * be computed and compared - which reveals whether the pad is producing
     * ISO 9564 format 0 under the key we loaded, or something else.</p>
     *
     * <p>Enter PIN <b>1234</b> when prompted.</p>
     */
    private void pinBlockTest() {
        if (mPinPad == null) {
            toast("SDK not ready yet");
            return;
        }
        final String testPan = "4466172092075908";
        final int index = TerminalConfig.get(this).mkIndex();
        status("PIN BLOCK TEST\nPAN " + testPan + "\nEnter PIN 1234 when prompted...");
        try {
            // Goes through DynamicPinPad, so this also exercises the growing box
            // row: four boxes to start with, a fifth from the fifth digit, up to
            // twelve. Enter 1234 for the expected block printed below, or enter
            // a longer PIN to confirm the boxes follow.
            com.hjq.demo.widget.DynamicPinPad.show(this, mPinPad, index, 4, 12,
                    true, "1.00", testPan, 0, 60, 1,
                    new com.szanfu.sdk.api.pinpad.IPinPadCallback.Stub() {
                        @Override
                        public void onGetPinBlock(boolean free, final String pin) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    String text = "PIN BLOCK TEST\n"
                                            + "keyIndex : " + index + "\n"
                                            + "PAN      : " + testPan + "\n"
                                            + "PIN       : 1234\n"
                                            + "BLOCK    : " + (free ? "(bypassed)" : pin) + "\n"
                                            + "TPK KCV  : " + mKeyManager.readKcv(index, 0x03) + "\n"
                                            + "\nExpected for ISO-0 under that key:\n"
                                            + "  clear block 04 1234 FFFFFFFFFF\n"
                                            + "  XOR pan blk 0000 617209207590\n"
                                            + "  then 3DES-encrypt with the TPK.\n"
                                            + "\nIf the printed block does not match,\n"
                                            + "the pad is not using ISO-0 or not\n"
                                            + "using the DE62 key.";
                                    status(text);
                                    if (mPrinter != null) {
                                        printBitmap(textBitmap(text));
                                    }
                                }
                            });
                        }

                        @Override
                        public void onPinPadError(int errCode) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status("PIN BLOCK TEST\npad error " + errCode);
                                }
                            });
                        }

                        @Override
                        public void onTimeout() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status("PIN BLOCK TEST\ntimed out");
                                }
                            });
                        }

                        @Override
                        public void onCancelPasswordEntry() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    status("PIN BLOCK TEST\ncancelled");
                                }
                            });
                        }
                    });
        } catch (Exception e) {
            status("PIN BLOCK TEST failed: " + e.getMessage());
        }
    }

    /**
     * Save the app log to the terminal and print the lines that matter.
     *
     * <p>For UAT on site, where there is no PC. The full capture is written to
     * {@code Android/data/&lt;package&gt;/files/logs/} and can be copied off
     * over USB later; the receipt carries the key and PIN lines, which is
     * usually all that is needed to see what went wrong.</p>
     */
    private void collectLog() {
        status("Collecting log...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final LogCollector.Result res = LogCollector.capture(AdminActivity.this, 4000);
                LogCollector.prune(AdminActivity.this, 10);
                final String summary = LogCollector.keyAndPinSummary(AdminActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!res.ok) {
                            status("LOG CAPTURE FAILED\n" + res.error);
                            toast("Log capture failed");
                            return;
                        }
                        String head = "LOG SAVED\n"
                                + res.lines + " lines\n"
                                + res.file.getName() + "\n"
                                + "in Android/data/\n" + getPackageName() + "/files/logs/\n\n";
                        status(head + "--- KEY / PIN LINES ---\n" + summary);
                        if (mPrinter != null) {
                            printBitmap(textBitmap("LOG - KEY / PIN LINES\n"
                                    + "--------------------------------\n"
                                    + summary
                                    + "--------------------------------\n"
                                    + "full log: " + res.file.getName() + "\n"));
                            toast("Log saved and printed");
                        } else {
                            toast("Log saved (printer not ready)");
                        }
                    }
                });
            }
        }).start();
    }

    private void readKcv() {
        if (mKeyManager == null) {
            toast("SDK not ready yet");
            return;
        }
        int index = intOf(etMkIndex, TerminalConfig.DEF_MK_INDEX);
        int keyId = intOf(etWkId, 3);
        String mkcv = mKeyManager.readKcv(index, 0x02);
        String wkcv = mKeyManager.readKcv(index, keyId);
        status("READ KCV (index " + index + ")\nMaster (TMK) KCV = " + mkcv
                + "\nWorking (type " + keyId + ") KCV = " + wkcv);
    }

    private void printKcv() {
        if (mKeyManager == null) {
            toast("SDK not ready yet");
            return;
        }
        int index = intOf(etMkIndex, TerminalConfig.DEF_MK_INDEX);
        int wkId = intOf(etWkId, 3);
        TerminalConfig tc = TerminalConfig.get(this);
        // Read every slot the logon touches, not just one. If the DE62 split is
        // wrong, TPK and TAK are the two that will disagree with DBBL's HSM.
        // Each key is read from the slot it was written to: the TMK at the
        // master key index, the working keys at their own indices.
        String mk = mKeyManager.readKcv(index, 0x02);                          // TMK
        String tpk = mKeyManager.readKcv(index, 0x03);                          // TPK
        String tak = mKeyManager.readKcv(index, 0x04);                          // TAK
        String wk = mKeyManager.readKcv(index, wkId);
        int split = tc.wkLen();
        String text = "KCV REPORT\n"
                + "TID " + tc.tid() + "\n"
                + "Key index " + index + "\n"
                + "TMK    KCV: " + mk
                + (KCV_ALL_ZEROS.equalsIgnoreCase(mk) ? "  *** NO KEY ***" : "") + "\n"
                + "TPK(3) KCV: " + tpk + "\n"
                + "TAK(4) KCV: " + tak + "\n"
                + (wkId != 3 && wkId != 4
                        ? "WORKING(" + wkId + ") KCV: " + wk + "\n" : "")
                + "DE62 split: " + (split == 0 ? "WHOLE FIELD" : split + "-byte split") + "\n"
                + "Logged on : " + (tc.isLoggedOn() ? "yes" : "no") + "\n"
                ;
        status(text);
        if (mPrinter == null) {
            toast("Printer not ready");
            return;
        }
        printBitmap(textBitmap(text));
        toast("KCV printed");
    }

    private void setPassword() {
        int pos = spTarget == null ? 0 : spTarget.getSelectedItemPosition();
        if (pos < 0 || pos >= pwKeys.length) {
            return;
        }
        String key = pwKeys[pos];
        String pw = str(etPwValue);
        TerminalConfig tc = TerminalConfig.get(this);
        if ("__settings__".equals(key)) {
            try { tc.setSettingsPw(pw); }
            catch (RuntimeException e) { toast(e.getMessage()); return; }
            com.hjq.demo.utils.TerminalLockdown.refresh(this);
        } else if ("__admin__".equals(key)) {
            if (pw.isEmpty()) { toast("Enter a password"); return; }
            tc.setAdminPw(pw);
        } else if ("__exit__".equals(key)) {
            if (pw.isEmpty()) { toast("Enter a password"); return; }
            tc.setExitPw(pw);
        } else {
            tc.setMenuPw(key, pw);
        }
        toast(pwLabels[pos] + (pw.isEmpty() ? " password removed" : " password set"));
        etPwValue.setText("");
    }

    private void clearPassword() {
        int pos = spTarget == null ? 0 : spTarget.getSelectedItemPosition();
        if (pos < 0 || pos >= pwKeys.length) {
            return;
        }
        String key = pwKeys[pos];
        TerminalConfig tc = TerminalConfig.get(this);
        if ("__settings__".equals(key)) {
            try { tc.setSettingsPw("1235"); }
            catch (RuntimeException e) { toast(e.getMessage()); return; }
            com.hjq.demo.utils.TerminalLockdown.refresh(this);
            toast("Settings password reset to 1235");
        } else if ("__admin__".equals(key)) {
            tc.setAdminPw("");
            toast("Admin password reset to " + TerminalConfig.DEFAULT_PW);
        } else if ("__exit__".equals(key)) {
            tc.setExitPw("");
            toast("Exit password reset to " + TerminalConfig.DEFAULT_PW);
        } else {
            tc.clearMenuPw(key);
            toast(TerminalConfig.isVoidMenu(key) ? pwLabels[pos] + " now uses the Admin password" : pwLabels[pos] + " password removed");
        }
        etPwValue.setText("");
    }

    private void runLogonAdmin() {
        tvLogonLog.setText("Logon: connecting to host...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final OnlineHost.HostResult r = OnlineHost.logon(getApplicationContext(), mPinPad);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String slip = buildLogonSlip(r);
                        tvLogonLog.setText(slip);
                        if (mPrinter != null) {
                            printBitmap(textBitmap(slip));
                        }
                        toast(r.approved ? "LOGON SUCCESSFUL" : "Logon declined " + r.responseCode);
                    }
                });
            }
        }).start();
    }

    private String buildLogonSlip(OnlineHost.HostResult r) {
        StringBuilder b = new StringBuilder();
        b.append("LOGON\n--------------------------------\n");
        b.append(r.precedingTrace);
        b.append("SENDING PACKET:\n").append(r.requestDump == null ? "" : r.requestDump).append("\n");
        b.append("--------------------------------\n");
        b.append("RECEIVING PACKET:\n").append(r.responseDump == null ? "" : r.responseDump).append("\n");
        b.append("--------------------------------\n");
        b.append("00".equals(r.responseCode) ? "LOGON SUCCESSFUL\n"
                : ("LOGON FAILED " + r.responseCode + "\n"));
        return b.toString();
    }

    private Bitmap textBitmap(String text) {
        List<String> lines = new ArrayList<>();
        for (String raw : text.split("\n")) {
            if (raw.length() <= 40) {
                lines.add(raw);
            } else {
                for (int i = 0; i < raw.length(); i += 40) {
                    lines.add(raw.substring(i, Math.min(raw.length(), i + 40)));
                }
            }
        }
        // The slip paint, so an admin printout looks like every other thing
        // this terminal prints - same face, same weight, anti-aliasing off for
        // the printer's one-bit threshold.
        com.hjq.demo.utils.Slip.useFont(getApplicationContext());
        TextPaint p = com.hjq.demo.utils.Slip.paint(com.hjq.demo.utils.Slip.SMALL);
        Paint.FontMetrics fm = p.getFontMetrics();
        int lh = com.hjq.demo.utils.Slip.lineHeightOf(com.hjq.demo.utils.Slip.SMALL);
        int w = 384;
        // +120 blank rows so the printout clears the tear bar, same as the
        // transaction slips.
        int h = Math.max(80, lh * lines.size() + 40) + 48;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawColor(Color.WHITE);
        int y = 20 - (int) fm.ascent;
        for (String ln : lines) {
            c.drawText(ln, 10, y, p);
            y += lh;
        }
        return b;
    }

    private void printBitmap(Bitmap bmp) {
        try {
            // Short jobs - a long one loses its tail (see SlipPrinter).
            com.hjq.demo.utils.SlipPrinter.print(mPrinter, bmp, (ok, msg) -> {
                if (!ok) runOnUiThread(() -> toast("print err: " + msg));
            });
        } catch (Exception e) {
            toast("print err: " + e.getMessage());
        }
    }

    private void loadCounters() {
        TerminalConfig tc = TerminalConfig.get(this);
        if (etInvoice != null) {
            etInvoice.setText(String.valueOf(tc.invoice()));
        }
        if (etBatch != null) {
            etBatch.setText(String.valueOf(tc.batch()));
        }
        if (etStan != null) {
            etStan.setText(String.valueOf(tc.stan()));
        }
    }

    private void saveCounters() {
        TerminalConfig tc = TerminalConfig.get(this);
        tc.setInvoice(intOf(etInvoice, tc.invoice()));
        tc.setBatch(intOf(etBatch, tc.batch()));
        tc.setStan(intOf(etStan, tc.stan()));
        toast("Invoice / batch / STAN saved");
    }

    private void status(final String s) {
        if (tvStatus != null) {
            tvStatus.setText(s);
        }
    }

    private String str(EditText e) {
        return e == null ? "" : e.getText().toString().trim();
    }

    /** The enabled entry modes, for the admin status panel. */
    private static String entrySummary(TerminalConfig tc) {
        StringBuilder b = new StringBuilder();
        if (tc.entryChip()) {
            b.append("CHIP ");
        }
        if (tc.entryContactless()) {
            b.append("TAP ");
        }
        if (tc.entrySwipe()) {
            b.append("SWIPE ");
        }
        if (tc.entryFallback()) {
            b.append("FALLBACK ");
        }
        if (tc.entryManual()) {
            b.append("MANUAL ");
        }
        return b.length() == 0 ? "none" : b.toString().trim();
    }

    private int intOf(EditText e, int def) {
        try {
            return Integer.parseInt(str(e));
        } catch (Exception ex) {
            return def;
        }
    }
}
