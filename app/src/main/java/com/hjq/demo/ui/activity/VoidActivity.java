package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.hjq.demo.R;
import com.hjq.demo.ans8583.BatchStore;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.ans8583.TxnRecord;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.base.BaseDialog;
import com.hjq.demo.utils.CardMask;
import com.szanfu.sdk.api.ISdkServiceManager;

import java.util.List;
import java.util.Locale;

/**
 * VOID, as two full screens rather than a pair of dialogs - the same shape as
 * the PAX reference ({@code SaleVoidTrans}: ENTER_TRANSNO then TRANS_DETAIL).
 *
 * <ol>
 *   <li><b>Enter the original invoice number.</b> Numeric keypad, six digits.
 *       An invoice must be entered explicitly before any transaction is selected.</li>
 *   <li><b>Confirm the transaction.</b> Type, amount, masked card number, auth
 *       code, retrieval reference, invoice and date/time - the same rows PAX
 *       shows - so the merchant cancels the transaction they meant to.</li>
 * </ol>
 *
 * <p>Nothing is sent from here. On confirmation the invoice is handed to
 * {@link FindCardActivity}, which builds and sends the 0200 (spec 7.44).</p>
 */
public final class VoidActivity extends AppActivity implements View.OnClickListener {

    /** Invoice numbers are six digits, like the batch and trace numbers. */
    private static final int INVOICE_LEN = 6;

    /** Restrict the search to EMI sales - set when the EMI VOID tile is used. */
    public static final String EXTRA_EMI_ONLY = "EMI_ONLY";

    /**
     * Restrict the search to pre-authorisations - set by the PRE-AUTH VOID
     * tile. A pre-auth is cancelled with an 0400 reversal, not the 0200 a sale
     * void sends, so offering an ordinary sale here would send the wrong
     * message for it.
     */
    public static final String EXTRA_PREAUTH_ONLY = "PREAUTH_ONLY";

    /**
     * Complete a pre-authorisation rather than cancel one - set by the SALE
     * COMPLETE tile. Same first screen, then a second question this screen
     * does not otherwise ask: the final amount.
     */
    public static final String EXTRA_COMPLETE_ONLY = "COMPLETE_ONLY";

    /** The final amount, handed to FindCardActivity with the invoice. */
    public static final String EXTRA_FINAL_AMOUNT = "FINAL_AMOUNT";

    private boolean emiOnly;
    private boolean preAuthOnly;
    private boolean completeOnly;

    /**
     * Which question the keypad is answering.
     *
     * <p>A completion needs two numbers and the screen has one keypad, so it
     * asks them in turn rather than growing a second pad: the invoice first,
     * then the amount, pre-filled with what was authorised because that is
     * what most completions settle for.</p>
     */
    private boolean askingAmount;
    private boolean authorized;
    private boolean launching;
    private final StringBuilder amountDigits = new StringBuilder();

    private ViewFlipper flipper;
    private TextView tvTitle, tvInvoice, tvHint, tvLabel;
    private LinearLayout rows;

    private final StringBuilder digits = new StringBuilder();
    /** The transaction picked on screen 1 and shown on screen 2. */
    private TxnRecord selected;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_void;
    }

    @Override
    protected void initView() {
        flipper = findViewById(R.id.void_flipper);
        tvTitle = findViewById(R.id.void_title);
        tvInvoice = findViewById(R.id.void_invoice);
        tvHint = findViewById(R.id.void_hint);
        tvLabel = findViewById(R.id.void_label);
        rows = findViewById(R.id.void_detail_rows);

        // The keypad is drawn in the layout now rather than by KeyBoardView, so
        // it can carry the same block as the PIN pad and the EMI amount screen:
        // digits three wide on the left, C and backspace and CONFIRM down the
        // right. KeyBoardView draws its own arrangement and cannot be told to
        // use another one.
        bind(R.id.void_k0, R.id.void_k1, R.id.void_k2, R.id.void_k3, R.id.void_k4,
                R.id.void_k5, R.id.void_k6, R.id.void_k7, R.id.void_k8, R.id.void_k9,
                R.id.void_kclear, R.id.void_kdel, R.id.void_lookup,
                R.id.void_confirm, R.id.void_cancel);

        showInvoice();
    }

    private void bind(int... ids) {
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    /** The digit a key stands for, or -1 when it is not a digit key. */
    private static int digitOf(int id) {
        if (id == R.id.void_k0) return 0;
        if (id == R.id.void_k1) return 1;
        if (id == R.id.void_k2) return 2;
        if (id == R.id.void_k3) return 3;
        if (id == R.id.void_k4) return 4;
        if (id == R.id.void_k5) return 5;
        if (id == R.id.void_k6) return 6;
        if (id == R.id.void_k7) return 7;
        if (id == R.id.void_k8) return 8;
        if (id == R.id.void_k9) return 9;
        return -1;
    }

    @Override
    protected void initData() {
        emiOnly = getIntent() != null && getIntent().getBooleanExtra(EXTRA_EMI_ONLY, false);
        preAuthOnly = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_PREAUTH_ONLY, false);
        completeOnly = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_COMPLETE_ONLY, false);
        if (tvTitle != null) {
            tvTitle.setText(screenTitle());
        }
        if (!completeOnly) {
            String key = preAuthOnly ? "PREAUTH_VOID" : emiOnly ? "EMI_VOID" : "VOID";
            com.hjq.demo.utils.PasswordPrompt.ask(this, screenTitle() + " password",
                    TerminalConfig.get(this).menuPw(key), () -> authorized = true, this::finish);
        } else { authorized = true; }
        // Refuse when there is nothing this terminal can void. A void can only
        // cancel a transaction still in the open batch; once a batch is settled
        // the transaction has to be refunded instead.
        //
        // Said in a dialog and not a toast. Calling finish() in the same frame
        // as the toast tore the screen down before it had drawn, so the tile
        // looked dead - press it, nothing happens - which is exactly how the
        // EMI SALE tile appeared to be broken. The operator needs to be told
        // why the screen is closing.
        if (completeOnly && voidableCount() == 0) {
            refuse(completeOnly
                    ? "There are no pre-authorisations in the open batch to complete."
                            + "\n\nA sale completion settles a hold that is still open, "
                            + "so the pre-auth must not have been cancelled or settled "
                            + "already."
                    : preAuthOnly
                    ? "There are no pre-authorisations in the open batch to cancel."
                            + "\n\nA pre-auth void releases a hold the issuer is still "
                            + "holding, so it can only cancel a pre-auth that has not "
                            + "been completed or settled."
                    : emiOnly
                    ? "ALREADY VOIDED"
                    : "There are no transactions in the open batch to void.\n\n"
                            + "A void can only cancel a transaction still in the open "
                            + "batch. Once the batch is settled it has to be refunded "
                            + "instead.");
        }
    }

    /** What this screen is, for the header and the dialogs. */
    private String screenTitle() {
        if (completeOnly) {
            return "SALE COMPLETE";
        }
        if (preAuthOnly) {
            return "PRE-AUTH VOID";
        }
        return emiOnly ? "EMI VOID" : "VOID";
    }

    /** Say why, then close. */
    private void refuse(String message) {
        new MessageDialog.Builder(this)
                .setTitle(screenTitle())
                .setMessage(message)
                .setConfirm(getString(R.string.common_confirm))
                .setCancel(null)
                .setListener(new MessageDialog.OnListener() {
                    @Override
                    public void onConfirm(BaseDialog dialog) {
                        finish();
                    }

                    @Override
                    public void onCancel(BaseDialog dialog) {
                        finish();
                    }
                })
                .show();
    }

    // ------------------------------------------------- screen 1: invoice entry

    private void showInvoice() {
        if (tvInvoice == null) {
            return;
        }
        // Show exactly what will be searched for. The lookup left-pads with
        // zeros - typing "5" finds invoice 000005 - so the field must pad the
        // same way. Padding on the right would read as 500000 and mislead.
        tvInvoice.setText(digits.length() == 0
                ? "------"
                : padInvoice(digits.toString()));
        tvInvoice.setTextColor(0xFFA9B0C0);
        if (tvLabel != null) {
            tvLabel.setText("ORIGINAL INVOICE NO.");
            tvLabel.setTextColor(0xFF8A90A0);
            tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
        }
        if (tvTitle != null) {
            tvTitle.setText(screenTitle());
        }
        if (tvHint != null) {
            tvHint.setText(digits.length() == 0
                    ? "Enter the original sale invoice to continue"
                    : voidableCount() + " transaction(s) in batch "
                            + TerminalConfig.get(this).batchNo());
        }
    }

    private int voidableCount() {
        int n = 0;
        for (TxnRecord r : BatchStore.get(this).all()) {
            if (isVoidable(r)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Whether this entry can be cancelled, applying the PAX checks that carry
     * over: it must have been approved, it must not already be voided, and a
     * void advice cannot itself be voided.
     */
    private boolean isVoidable(TxnRecord r) {
        if (r == null || !r.approved || r.voided || r.isVoid()) {
            return false;
        }
        // PRE-AUTH VOID cancels pre-authorisations only. They are reversed with
        // an 0400, not voided with an 0200, so the two lists must not mix.
        // A completion settles a pre-auth, so it draws from the same list the
        // pre-auth void does.
        if (preAuthOnly || completeOnly) {
            return "PREAUTH".equals(r.type);
        }
        // Equally, an ordinary void must not offer a pre-auth.
        if ("PREAUTH".equals(r.type)) {
            return false;
        }
        // EMI VOID cancels EMI sales only. Voiding an ordinary sale from this
        // screen would send it on the EMI route, where the host has no record
        // of it.
        return !emiOnly || r.isEmi();
    }

    /** Find what the invoice number refers to, and say clearly when it does not. */
    private void lookUp() {
        TxnRecord found;
        if (!authorized) return;
        if (digits.length() == 0) {
            toast("Enter the original invoice number");
            return;
        } else {
            String invoice = padInvoice(digits.toString());
            found = BatchStore.get(this).findByInvoice(invoice);
            if (found == null) {
                toast("Invoice " + invoice + " is not in the open batch");
                return;
            }
            if (!isVoidable(found)) {
                // The three modes cancel, release and settle. Saying "voided"
                // for all of them tells an operator on the completion screen
                // the wrong thing about what they just typed.
                if (completeOnly) {
                    toast(found.voided
                            ? "Invoice " + invoice + " was cancelled - nothing to complete"
                            : "Invoice " + invoice + " is not an open pre-authorisation");
                } else if (preAuthOnly) {
                    toast(found.voided
                            ? "Invoice " + invoice + " has already been cancelled"
                            : "Invoice " + invoice + " is not a pre-authorisation");
                } else {
                    toast(found.voided
                            ? "Invoice " + invoice + " has already been voided"
                            : "Invoice " + invoice + " cannot be voided");
                }
                return;
            }
        }
        selected = found;
        if (completeOnly) {
            // Starts at 0.00. The operator types the amount being completed;
            // the authorised figure is shown in the hint line below the box,
            // not pre-filled into it.
            askingAmount = true;
            amountDigits.setLength(0);
            showAmount();
            return;
        }
        showDetail();
    }

    /** Amounts are held as minor units, so this is 99,999,999.99. */
    private static final int AMOUNT_MAX_DIGITS = 10;

    private long amountMinor() {
        try {
            return Long.parseLong(amountDigits.toString());
        } catch (Exception e) {
            return 0L;
        }
    }

    private String amountMajor() {
        long v = amountMinor();
        return String.format(Locale.US, "%d.%02d", v / 100, v % 100);
    }

    private void showAmount() {
        if (tvInvoice != null) {
            tvInvoice.setText(amountDigits.length() == 0
                    ? TerminalConfig.get(this).currencySymbol() + " 0.00"
                    : TerminalConfig.get(this).currencySymbol() + " " + amountMajor());
            tvInvoice.setTextColor(amountDigits.length() == 0 ? 0xFFA9B0C0 : 0xFF16202F);
        }
        if (tvTitle != null) {
            tvTitle.setText("SALE COMPLETION");
        }
        if (tvLabel != null) {
            // The line above the amount box says what to type, in black and
            // larger than the grey invoice caption it replaces.
            tvLabel.setText("Enter Sale Completion Amount");
            tvLabel.setTextColor(0xFF000000);
            tvLabel.setTypeface(tvLabel.getTypeface(), android.graphics.Typeface.BOLD);
            tvLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 24);
        }
        if (tvHint != null && selected != null) {
            TerminalConfig tc = TerminalConfig.get(this);
            tvHint.setText("Pre-auth " + tc.currencySymbol() + " " + money(selected.amount())
                    + " on invoice " + selected.invoice
                    + "\nMaximum " + tc.currencySymbol() + " "
                    + money(tc.maxCompletionMinor(selected.amount()))
                    + " (" + tc.preAuthPercentage() + "%)");
        }
    }

    private static String padInvoice(String v) {
        StringBuilder b = new StringBuilder(v == null ? "" : v.trim());
        while (b.length() < INVOICE_LEN) {
            b.insert(0, '0');
        }
        return b.toString();
    }

    // -------------------------------------------------- screen 2: confirmation

    private void showDetail() {
        if (rows == null || selected == null) {
            return;
        }
        rows.removeAllViews();

        int mask = TerminalConfig.get(this).cardMask();
        String pan = selected.pan.isEmpty() ? panOfTrack(selected.track2) : selected.pan;

        addRow("Trans Type", selected.type);
        addRow("Amount", TerminalConfig.get(this).currencySymbol()
                + " " + money(selected.amount()));
        addRow("Card NO.", CardMask.maskGrouped(pan, mask));
        addRow("Auth Code", dash(selected.authCode));
        addRow("Reference NO.", dash(selected.rrn));
        addRow("Invoice NO.", dash(selected.invoice));
        addRow("Date/Time", dateTime(selected));
        addRow("Batch NO.", TerminalConfig.get(this).batchNo());
        // What is being cancelled matters more on an EMI void than on any
        // other: the same card, amount and date can carry two different
        // instalment plans, and only the term tells them apart.
        if (selected.isEmi()) {
            addRow("EMI Plan", emiPlan(selected));
            if (selected.tenure > 0) {
                addRow("Tenure", selected.tenure + " month"
                        + (selected.tenure == 1 ? "" : "s"));
            }
            if (selected.installment() > 0) {
                addRow("Instalment", TerminalConfig.get(this).currencySymbol()
                        + " " + money(selected.installment()));
            }
        }

        if (completeOnly) {
            addRow("Completing", TerminalConfig.get(this).currencySymbol()
                    + " " + amountMajor());
        }
        if (tvTitle != null) {
            tvTitle.setText(screenTitle() + " - CONFIRM");
        }
        if (flipper != null) {
            flipper.setDisplayedChild(1);
        }
    }

    /**
     * Row text, as a fraction of the screen height rather than in sp.
     *
     * <p>This terminal reports a far lower density than a phone, so an sp size
     * lands as a handful of actual pixels on an 815 px screen. Sizing against
     * the display keeps these rows readable on the hardware they are read on.</p>
     */
    private static final float ROW_TEXT_FRACTION = 0.030f;

    private float rowTextPx() {
        return getResources().getDisplayMetrics().heightPixels * ROW_TEXT_FRACTION;
    }

    private void addRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(12), 0, dp(12));

        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(0xFF6A7285);
        l.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, rowTextPx());
        l.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = new TextView(this);
        v.setText(value);
        v.setTextColor(0xFF111111);
        v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, rowTextPx());
        v.getPaint().setFakeBoldText(true);
        v.setGravity(Gravity.END);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1.5f));

        row.addView(l);
        row.addView(v);
        rows.addView(row);

        View sep = new View(this);
        sep.setBackgroundColor(0xFFE4E7F0);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        rows.addView(sep);
    }

    // ---------------------------------------------------------------- actions

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (!authorized || launching) return;

        int digit = digitOf(id);
        if (digit >= 0) {
            if (askingAmount) {
                // Leading zeros buy nothing on an amount and the field is read
                // as minor units, so 1 2 0 0 reads 12.00.
                if (amountDigits.length() < AMOUNT_MAX_DIGITS
                        && !(amountDigits.length() == 0 && digit == 0)) {
                    amountDigits.append(digit);
                }
                showAmount();
            } else {
                if (digits.length() < INVOICE_LEN) {
                    digits.append(digit);
                }
                showInvoice();
            }
            return;
        }
        if (id == R.id.void_kclear) {
            finish();
            return;
        }
        if (id == R.id.void_kdel) {
            StringBuilder b = askingAmount ? amountDigits : digits;
            if (b.length() > 0) {
                b.deleteCharAt(b.length() - 1);
            }
            if (askingAmount) {
                showAmount();
            } else {
                showInvoice();
            }
            return;
        }
        if (id == R.id.void_lookup) {
            if (askingAmount) {
                if (amountMinor() <= 0) {
                    toast("Enter the amount to complete");
                    return;
                }
                // Pre Auth Percentage: a completion may exceed the hold only
                // by what the parameter allows (125% by default).
                TerminalConfig tc = TerminalConfig.get(this);
                long max = selected == null ? Long.MAX_VALUE
                        : tc.maxCompletionMinor(selected.amount());
                if (amountMinor() > max) {
                    toast("Amount exceeds the limit - maximum " + tc.currencySymbol() + " "
                            + money(max) + " (" + tc.preAuthPercentage() + "% of the pre-auth)");
                    return;
                }
                showDetail();
            } else {
                lookUp();
            }
            return;
        }

        if (id == R.id.void_confirm) {
            if (selected == null || digits.length() == 0) {
                toast("Enter the original invoice number"); return;
            }
            TxnRecord current = BatchStore.get(this).findByInvoice(selected.invoice);
            if (!isVoidable(current)) { toast("This invoice is no longer available"); backToInvoiceEntry(); return; }
            launching = true;
            Intent i = new Intent(this, FindCardActivity.class);
            i.putExtra(FindCardActivity.EXTRA_TRANS_TYPE,
                    completeOnly ? "SALE_COMPLETE"
                            : preAuthOnly ? "PREAUTH_VOID"
                            : emiOnly ? "EMI_VOID" : "VOID");
            i.putExtra(FindCardActivity.EXTRA_VOID_INVOICE, selected.invoice);
            if (completeOnly) {
                i.putExtra(EXTRA_FINAL_AMOUNT, amountMajor());
            }
            startActivity(i);
            finish();
        } else if (id == R.id.void_cancel) {
            backToInvoiceEntry();
        }
    }

    @Override public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) {
            if (authorized && !launching && event.getRepeatCount() == 0)
                findViewById(flipper.getDisplayedChild() == 1 ? R.id.void_confirm : R.id.void_lookup).performClick();
            return true;
        }
        if (authorized && !launching && keyCode >= android.view.KeyEvent.KEYCODE_0 && keyCode <= android.view.KeyEvent.KEYCODE_9) {
            int[] keys = {R.id.void_k0, R.id.void_k1, R.id.void_k2, R.id.void_k3, R.id.void_k4,
                    R.id.void_k5, R.id.void_k6, R.id.void_k7, R.id.void_k8, R.id.void_k9};
            if (flipper.getDisplayedChild() == 0) findViewById(keys[keyCode - android.view.KeyEvent.KEYCODE_0]).performClick();
            return true;
        }
        if (keyCode == android.view.KeyEvent.KEYCODE_DEL && authorized) {
            if (flipper.getDisplayedChild() == 0) findViewById(R.id.void_kdel).performClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (flipper != null && flipper.getDisplayedChild() == 1) {
            backToInvoiceEntry();
            return;
        }
        super.onBackPressed();
    }

    /** Nothing has been sent yet, so backing out here costs nothing. */
    private void backToInvoiceEntry() {
        selected = null;
        askingAmount = false;
        amountDigits.setLength(0);
        digits.setLength(0);
        showInvoice();
        if (tvTitle != null) {
            tvTitle.setText(screenTitle());
        }
        if (flipper != null) {
            flipper.setDisplayedChild(0);
        }
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdkServiceManager) {
        // Nothing to bind: this screen only reads the batch journal. The card
        // reader, PIN pad and printer are acquired by FindCardActivity, which
        // is where the void is actually sent.
    }

    // ----------------------------------------------------------------- helpers

    /**
     * The instalment product, named the way the EMI screens name it.
     *
     * <p>The record keeps the acquirer's product code rather than a label, so
     * this reads the code back through the package table when it can and falls
     * back to the code itself when the table has no entry - a plan that was
     * sold before the packages were last downloaded still has to be voidable.</p>
     */
    private String emiPlan(TxnRecord r) {
        String code = r.emiProduct == null ? "" : r.emiProduct.trim();
        if (code.isEmpty()) {
            return r.type != null && r.type.startsWith("EMI") ? "EMI" : "-";
        }
        try {
            com.hjq.demo.ans8583.EmiPackage p =
                    com.hjq.demo.ans8583.EmiStore.get(this).byProductCode(code);
            if (p != null) {
                String label = p.productWording == null ? "" : p.productWording.trim();
                return label.isEmpty() ? p.label() : label;
            }
        } catch (Exception ignore) {
            // The package table is a downloaded parameter; a void must not
            // depend on it being present or well formed.
        }
        return code;
    }

    private static String dash(String v) {
        return v == null || v.trim().isEmpty() ? "-" : v.trim();
    }

    private static String money(long minor) {
        long a = Math.abs(minor);
        return String.format(Locale.US, "%d.%02d", a / 100, a % 100);
    }

    private static String panOfTrack(String track2) {
        if (track2 == null) {
            return "";
        }
        int i = track2.indexOf('=');
        return i > 0 ? track2.substring(0, i) : track2;
    }

    /** DE13 is MMDD and DE12 is hhmmss; show them the way the receipt does. */
    private static String dateTime(TxnRecord r) {
        String d = r.date == null ? "" : r.date;
        String t = r.time == null ? "" : r.time;
        String out = "";
        if (d.length() >= 4) {
            out = d.substring(2, 4) + "/" + d.substring(0, 2);
        }
        if (t.length() >= 6) {
            out = (out.isEmpty() ? "" : out + " ")
                    + t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6);
        }
        return out.isEmpty() ? "-" : out;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
