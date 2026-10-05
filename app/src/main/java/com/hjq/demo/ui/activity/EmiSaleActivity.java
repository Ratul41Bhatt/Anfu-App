package com.hjq.demo.ui.activity;

import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.hjq.base.BaseDialog;
import com.hjq.demo.R;
import com.hjq.demo.ans8583.EmiPackage;
import com.hjq.demo.ans8583.EmiStore;
import com.hjq.demo.ans8583.OnlineHost;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.ui.dialog.MessageDialog;
import com.hjq.demo.utils.Slip;
import com.szanfu.sdk.api.ISdkServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * EMI sale: product, then amount, then term.
 *
 * <p>The order the Move/2500 asks in, and it is not arbitrary. The product is
 * the first digit of DE61, so it has to be settled before anything else; the
 * term is the other two digits and is chosen against a known amount, because a
 * term whose instalment the cardholder cannot see is a term nobody can consent
 * to.</p>
 *
 * <p>What goes out is {@code DE61 = <product><MM>} - {@code 006} for a
 * six-month 0% Insta Pay, {@code 112} for a twelve-month Easy Buy. No plan
 * needs to be configured for that: the terminal sends the product and the
 * term, and the host applies the rate. A configured {@link EmiPackage} only
 * lets this screen <em>show</em> an Easy Buy instalment before the card is
 * read.</p>
 */
public final class EmiSaleActivity extends AppActivity implements View.OnClickListener {

    /** Same limits as the ordinary amount pad. */
    private static final long MIN_MINOR = 1;
    private static final long MAX_MINOR = 99999999L;

    private static final int PAGE_TYPE = 0;
    private static final int PAGE_AMOUNT = 1;
    private static final int PAGE_TENURE = 2;

    private ViewFlipper flipper;
    private TextView amountView, amountHint, amountTypeChip, tenureAmount, continueBtn;
    private LinearLayout tenureRows;

    private final StringBuilder digits = new StringBuilder();
    private long amountMinor;
    private int emiType = OnlineHost.EMI_TYPE_INSTA_PAY;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_emi;
    }

    @Override
    protected void initView() {
        flipper = findViewById(R.id.emi_flipper);
        amountView = findViewById(R.id.emi_amount);
        amountHint = findViewById(R.id.emi_amount_hint);
        amountTypeChip = findViewById(R.id.emi_amount_type);
        tenureAmount = findViewById(R.id.emi_tenure_amount);
        tenureRows = findViewById(R.id.emi_tenure_rows);
        continueBtn = findViewById(R.id.emi_continue);

        bind(R.id.emi_type_insta, R.id.emi_type_easy,
                R.id.emi_back_amount, R.id.emi_back_tenure, R.id.emi_continue,
                R.id.emi_k0, R.id.emi_k1, R.id.emi_k2, R.id.emi_k3, R.id.emi_k4,
                R.id.emi_k5, R.id.emi_k6, R.id.emi_k7, R.id.emi_k8, R.id.emi_k9,
                R.id.emi_kclear, R.id.emi_kdel);

        showAmount();
        show(PAGE_TYPE);
    }

    @Override
    protected void initData() {
        TerminalConfig tc = TerminalConfig.get(this);
        // Whatever stops EMI has to say so plainly and stay on screen long
        // enough to read. This screen used to close itself behind a toast,
        // which from the menu looks exactly like a tile that does nothing.
        if (!tc.emiEnabled()) {
            refuse("EMI is switched off.\n\nParameters, EMI, EMI Enabled.");
            return;
        }
        // A configured plan is not required to sell on instalments - DE61
        // carries the product and the term, and the host applies the rate.
        // What is required is at least one term switched on.
        if (availableTenures().isEmpty()) {
            refuse("Every EMI term is switched off.\n\nParameters, EMI, then turn"
                    + " on the months this merchant offers.");
        }
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdk) {
        // Nothing to bind: the card is read by FindCardActivity.
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public void onClick(View v) {
        int id = v.getId();

        if (id == R.id.emi_type_insta) {
            chooseType(OnlineHost.EMI_TYPE_INSTA_PAY);
            return;
        }
        if (id == R.id.emi_type_easy) {
            chooseType(OnlineHost.EMI_TYPE_EASY_BUY);
            return;
        }
        if (id == R.id.emi_back_amount || id == R.id.emi_back_tenure) {
            goBack();
            return;
        }
        if (id == R.id.emi_continue) {
            onAmountEntered();
            return;
        }
        if (id == R.id.emi_kclear) {
            digits.setLength(0);
            showAmount();
            return;
        }
        if (id == R.id.emi_kdel) {
            if (digits.length() > 0) {
                digits.deleteCharAt(digits.length() - 1);
            }
            showAmount();
            return;
        }

        String d = digitFor(id);
        if (d != null) {
            // Leading zeros buy nothing and push the real digits off the
            // eight-figure limit.
            if (!(digits.length() == 0 && "0".equals(d)) && digits.length() < 8) {
                digits.append(d);
            }
            showAmount();
        }
    }

    private String digitFor(int id) {
        if (id == R.id.emi_k0) return "0";
        if (id == R.id.emi_k1) return "1";
        if (id == R.id.emi_k2) return "2";
        if (id == R.id.emi_k3) return "3";
        if (id == R.id.emi_k4) return "4";
        if (id == R.id.emi_k5) return "5";
        if (id == R.id.emi_k6) return "6";
        if (id == R.id.emi_k7) return "7";
        if (id == R.id.emi_k8) return "8";
        if (id == R.id.emi_k9) return "9";
        return null;
    }

    // ------------------------------------------------------------- product page

    private void chooseType(int type) {
        emiType = type;
        digits.setLength(0);
        if (amountTypeChip != null) {
            amountTypeChip.setText(OnlineHost.emiTypeName(type));
        }
        showAmount();
        show(PAGE_AMOUNT);
    }

    // -------------------------------------------------------------- amount page

    /** Amount text before anything has been keyed, on the pale panel. */
    private static final int AMOUNT_PLACEHOLDER = 0xFFA9B0C0;

    /** And once the panel itself has filled with blue. */
    private static final int AMOUNT_ENTERED = 0xFFFFFFFF;

    private void showAmount() {
        boolean typed = digits.length() > 0;
        if (amountView != null) {
            amountView.setText("TK " + Slip.money(minor()));
            // The panel carries the state, not the digits. An empty field is a
            // pale box with grey text; the first keypress fills the whole box
            // with the app's blue and turns the figure white. On a screen with
            // one input, that is what tells the operator - from arm's length,
            // which is where they usually are - that the terminal took it.
            amountView.setBackgroundResource(typed
                    ? R.drawable.bg_emi_amount_active
                    : R.drawable.bg_emi_amount);
            amountView.setTextColor(typed ? AMOUNT_ENTERED : AMOUNT_PLACEHOLDER);
        }
        if (amountHint != null) {
            amountHint.setText("");
        }
        // Nothing to continue to until there is an amount. Saying so with the
        // button itself beats letting the operator press it and be told off.
        if (continueBtn != null) {
            continueBtn.setEnabled(minor() >= MIN_MINOR);
        }
    }

    private long minor() {
        if (digits.length() == 0) {
            return 0;
        }
        try {
            return Long.parseLong(digits.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private void onAmountEntered() {
        long m = minor();
        if (m < MIN_MINOR) {
            say("Enter an amount");
            return;
        }
        if (m > MAX_MINOR) {
            say("Amount is too large");
            return;
        }
        amountMinor = m;
        say("");
        showTenures();
        show(PAGE_TENURE);
    }

    // ---------------------------------------------------------------- term page

    /**
     * One card per available term, each showing what the cardholder will
     * actually pay.
     */
    private void showTenures() {
        if (tenureRows == null) {
            return;
        }
        tenureRows.removeAllViews();
        if (tenureAmount != null) {
            tenureAmount.setText(OnlineHost.emiTypeName(emiType)
                    + "     TK " + Slip.money(amountMinor));
        }

        for (final int months : availableTenures()) {
            final EmiPackage p = EmiStore.get(this).byTenure(months);
            // 0% Insta Pay is the amount divided by the term, by definition -
            // no configured rate is involved and none is looked up. Easy Buy
            // uses the plan's rate when one is configured; without it the card
            // shows the plain split and says the bank sets the rate, rather
            // than showing nothing.
            final boolean rated = p != null && emiType == OnlineHost.EMI_TYPE_EASY_BUY;
            final long instal = rated
                    ? p.installment(amountMinor)
                    : Math.round((double) amountMinor / months);
            final long total = instal * months;

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setBackgroundResource(R.drawable.bg_emi_card);
            card.setClickable(true);
            card.setFocusable(true);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(10);
            card.setLayoutParams(lp);

            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            left.setLayoutParams(new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView t = new TextView(this);
            t.setText(String.format(Locale.US, "%02d MONTHS", months));
            t.setTextColor(0xFF111111);
            t.setTextSize(16);
            t.getPaint().setFakeBoldText(true);
            left.addView(t);

            TextView sub = new TextView(this);
            if (rated && !p.interestWording.isEmpty()) {
                sub.setText(p.interestWording + "   Total TK " + Slip.money(total));
            } else if (emiType == OnlineHost.EMI_TYPE_EASY_BUY) {
                sub.setText("Rate applied by the bank");
            } else {
                sub.setText("Total TK " + Slip.money(total));
            }
            sub.setTextColor(0xFF8A90A0);
            sub.setTextSize(12);
            left.addView(sub);
            card.addView(left);

            TextView amt = new TextView(this);
            amt.setText("TK " + Slip.money(instal) + "\n/month");
            amt.setTextColor(0xFF3F5BD1);
            amt.setTextSize(14);
            amt.setGravity(Gravity.END);
            amt.getPaint().setFakeBoldText(true);
            card.addView(amt);

            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startEmiSale(months, p);
                }
            });
            tenureRows.addView(card);
        }
    }

    /**
     * Terms to offer: every tenure switched on in the parameters, plus any
     * configured plan whose term is enabled.
     *
     * <p>{@link TerminalConfig#EMI_TENURES} is the same list the Parameters
     * screen draws its switches from. Reading it here rather than keeping a
     * second list of our own is the whole point - what the merchant turns on
     * in Parameters, EMI is exactly what appears at the till.</p>
     */
    private List<Integer> availableTenures() {
        TerminalConfig tc = TerminalConfig.get(this);
        TreeSet<Integer> set = new TreeSet<>();
        for (int m : TerminalConfig.EMI_TENURES) {
            if (tc.emiTenureEnabled(m)) {
                set.add(m);
            }
        }
        for (EmiPackage p : EmiStore.get(this).all()) {
            if (p.tenure > 0 && tc.emiTenureEnabled(p.tenure)) {
                set.add(p.tenure);
            }
        }
        return new ArrayList<>(set);
    }

    /** Hand the chosen product and term to the card screen. */
    private void startEmiSale(int months, EmiPackage p) {
        Intent i = new Intent(this, FindCardActivity.class);
        i.putExtra(FindCardActivity.EXTRA_TRANS_TYPE, "EMI_SALE");
        i.putExtra(com.hjq.demo.model.AFConstant.CURRENT_TRANS_AMOUNT,
                Slip.money(amountMinor));
        i.putExtra(FindCardActivity.EXTRA_EMI_TENURE, months);
        i.putExtra(FindCardActivity.EXTRA_EMI_TYPE, emiType);
        i.putExtra(FindCardActivity.EXTRA_EMI_PRODUCT, p == null ? "" : p.productCode);
        startActivity(i);
        finish();
    }

    // ---------------------------------------------------------------- plumbing

    @Override
    public void onBackPressed() {
        if (goBack()) {
            return;
        }
        super.onBackPressed();
    }

    /**
     * One step back through term, amount, product.
     *
     * @return true when a step was taken, false when there is nowhere left to
     *         go and the screen should close
     */
    private boolean goBack() {
        if (flipper == null) {
            return false;
        }
        int page = flipper.getDisplayedChild();
        if (page == PAGE_TENURE) {
            show(PAGE_AMOUNT);
            return true;
        }
        if (page == PAGE_AMOUNT) {
            show(PAGE_TYPE);
            return true;
        }
        return false;
    }

    private void show(int page) {
        if (flipper != null) {
            flipper.setDisplayedChild(page);
        }
    }

    private void say(String s) {
        if (amountHint != null) {
            amountHint.setText(s);
        }
    }

    /**
     * Say why EMI cannot run, then close - but only once the operator has
     * acknowledged it.
     */
    private void refuse(String why) {
        new MessageDialog.Builder(this)
                .setTitle("EMI")
                .setMessage(why)
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

    /** Attach this activity as the click listener for each id that exists. */
    private void bind(int... ids) {
        for (int id : ids) {
            View v = findViewById(id);
            if (v != null) {
                v.setOnClickListener(this);
            }
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }
}
