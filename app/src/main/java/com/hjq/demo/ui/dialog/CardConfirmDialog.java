package com.hjq.demo.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import com.hjq.demo.R;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.utils.CardMask;
import com.hjq.demo.utils.CardRangeTable;
import com.hjq.demo.utils.LogUtils;

import java.util.Locale;

/**
 * "Is this the card in your hand?", asked by drawing the card.
 *
 * <p>This replaced a plain message box whose body was the raw PAN. Two things
 * were wrong with that. The first is that it printed the card number in
 * full - the one place in the app that did, while every slip, report and log
 * had been put behind {@link CardMask}. The second is that it asked the
 * operator to compare a string in a sentence against an object in their hand,
 * which is slower and easier to get wrong than comparing two things of the same
 * shape.</p>
 *
 * <p>So the layout follows a real card: contact plate top left, number across
 * the middle in a spaced monospace-ish run, expiry top right, scheme mark
 * bottom right. The number is masked to whatever the masking parameter says -
 * including not at all, if the acquirer has set NONE for testing, because that
 * is the operator's decision to make and not this dialog's.</p>
 *
 * <p>Built on a plain {@link Dialog} rather than the app's {@code CommonDialog},
 * whose shell supplies its own title and button bar - the whole point here is
 * to replace both.</p>
 */
public final class CardConfirmDialog {

    /** {@code 0} confirmed, {@code -1} cancelled - the codes the caller expects. */
    public interface OnResult {
        void onResult(int result);
    }

    private CardConfirmDialog() { }

    /** For callers with no timeout parameter to hand. Waits indefinitely. */
    public static void show(final Activity activity, String pan, String expiry,
                            String entry, final OnResult result) {
        show(activity, pan, expiry, entry, 0, result);
    }

    /**
     * @param pan        the card number; masked here, never by the caller
     * @param expiry     YYMM as read from the card, shown masked
     * @param entry      how the card was read - CHIP, NFC, SWIPE - or empty
     * @param timeoutSec seconds before this answers itself as a cancel;
     *                   {@code 0} waits indefinitely
     */
    public static void show(final Activity activity, String pan, String expiry,
                            String entry, final int timeoutSec, final OnResult result) {
        View view = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_card_confirm, null);

        TerminalConfig tc = TerminalConfig.get(activity);
        String digits = pan == null ? "" : pan.replaceAll("[^0-9]", "");

        // The caller does not open this dialog without a card number - see
        // FindCardActivity.bestKnownPan, which looks at every path the PAN can
        // arrive on and ends the transaction if none of them has one. There is
        // no error state to draw here.
        TextView panView = view.findViewById(R.id.cc_pan);
        String shown = CardMask.maskGrouped(digits, tc.cardMask());
        panView.setText(shown);
        // A 19-digit PAN, grouped and letter-spaced, is wider than the card at
        // 21sp. Step the size down rather than let it clip.
        if (shown.length() > 22) {
            panView.setTextSize(16);
        } else if (shown.length() > 19) {
            panView.setTextSize(18);
        }

        // Masked, like every slip this terminal prints. PAN plus expiry is most
        // of a card-not-present transaction, and this dialog is held up in
        // front of whoever is standing at the counter.
        ((TextView) view.findViewById(R.id.cc_expiry)).setText(maskedExpiry(expiry));

        ((TextView) view.findViewById(R.id.cc_issuer))
                .setText(tc.merchantName().toUpperCase(Locale.US));
        ((TextView) view.findViewById(R.id.cc_entry))
                .setText(entry == null ? "" : entry.toUpperCase(Locale.US));

        // The scheme, from the card range table, so TAKAPAY and NEXUSPAY show
        // as themselves rather than being guessed from the first digit.
        String scheme = digits.isEmpty() ? "" : CardRangeTable.nameFor(activity, digits);
        showBrand(activity, view, scheme);

        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            // Leave a margin either side rather than filling the screen edge to
            // edge, so the card reads as a card sitting on the counter.
            int margin = Math.round(20 * activity.getResources()
                    .getDisplayMetrics().density);
            int width = activity.getResources().getDisplayMetrics().widthPixels
                    - margin * 2;
            w.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }

        // Deliberately no dismiss-on-outside-touch and no back-key cancel. A
        // stray tap must not answer a question about whose card is being
        // charged; the operator has to press one of the two buttons.
        final boolean[] answered = {false};
        View.OnClickListener click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (answered[0]) {
                    return;
                }
                answered[0] = true;
                boolean confirmed = v.getId() == R.id.cc_confirm;
                try {
                    dialog.dismiss();
                } catch (Exception ignore) {
                    // The activity can be gone by now; the callback still has to run.
                }
                result.onResult(confirmed ? 0 : -1);
            }
        };
        final TextView cancelView = view.findViewById(R.id.cc_cancel);
        cancelView.setOnClickListener(click);
        view.findViewById(R.id.cc_confirm).setOnClickListener(click);

        // The countdown.
        //
        // This dialog is the one place a terminal could be left standing
        // indefinitely: the kernel thread that opened it is blocked on a latch
        // waiting for an answer, so until somebody presses a button the reader
        // stays powered, the trace number stays open and the screen cannot be
        // left. An operator who walks away, or a cardholder who changes their
        // mind and wanders off, strands the unit until it is power-cycled.
        //
        // So it answers itself. The remaining seconds count down inside the
        // CANCEL button, because that is what expiry does - a dialog that runs
        // out of time has not been agreed to. Any touch anywhere on it puts the
        // full time back, so an operator reading a long card number is not
        // raced by a clock.
        final android.os.Handler ticker =
                new android.os.Handler(android.os.Looper.getMainLooper());
        final int[] left = {timeoutSec};
        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (answered[0]) {
                    return;
                }
                if (left[0] <= 0) {
                    LogUtils.i("CardConfirmDialog: timed out after "
                            + timeoutSec + "s - answering as cancel");
                    answered[0] = true;
                    try {
                        dialog.dismiss();
                    } catch (Exception ignore) {
                        // Activity already gone; the callback still has to run.
                    }
                    result.onResult(-1);
                    return;
                }
                cancelView.setText("CANCEL (" + left[0] + ")");
                left[0]--;
                ticker.postDelayed(this, 1000L);
            }
        };
        if (timeoutSec > 0) {
            // Any touch on the dialog is a person being present. Put the whole
            // countdown back rather than letting it run down under them.
            view.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, android.view.MotionEvent event) {
                    left[0] = timeoutSec;
                    return false;
                }
            });
            ticker.post(tick);
            dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(android.content.DialogInterface d) {
                    ticker.removeCallbacksAndMessages(null);
                }
            });
        }

        try {
            dialog.show();
        } catch (Exception e) {
            // Activity finishing. Answer as a cancel rather than leaving the
            // transaction waiting on a dialog that never appeared.
            LogUtils.e("CardConfirmDialog: cannot show (" + e.getMessage() + ")");
            if (!answered[0]) {
                answered[0] = true;
                result.onResult(-1);
            }
        }
    }

    /**
     * The expiry, always masked as {@code **}{@code /}{@code **}.
     *
     * <p>Not conditional on whether the card supplied one. The operator is
     * confirming the card in their hand and the expiry is printed on it; what
     * this screen must not do is put the PAN and the expiry together where a
     * bystander can read them, since between them they are most of a
     * card-not-present transaction. The slips mask it for the same reason.</p>
     */
    private static String maskedExpiry(String yymm) {
        return "**" + "/" + "**";
    }

    /**
     * Show the scheme mark.
     *
     * <p>Looks for a drawable named {@code card_brand_visa},
     * {@code card_brand_master} and so on, and falls back to the scheme name set
     * in type. No brand artwork ships with this build - if DBBL supply the
     * licensed marks, dropping them in under those names is the whole
     * change.</p>
     */
    private static void showBrand(Activity activity, View view, String scheme) {
        TextView text = view.findViewById(R.id.cc_brand);
        ImageView icon = view.findViewById(R.id.cc_brand_icon);
        String name = scheme == null ? "" : scheme.trim();
        if (name.isEmpty()) {
            text.setText("");
            icon.setVisibility(View.GONE);
            return;
        }
        String assetName = "card_brand_"
                + name.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
        int id = 0;
        try {
            id = activity.getResources().getIdentifier(
                    assetName, "drawable", activity.getPackageName());
        } catch (Exception ignore) {
            // getIdentifier is best-effort; the text fallback covers it.
        }
        if (id != 0) {
            icon.setImageResource(id);
            icon.setVisibility(View.VISIBLE);
            text.setText("");
        } else {
            icon.setVisibility(View.GONE);
            text.setText(name.toUpperCase(Locale.US));
        }
    }
}
