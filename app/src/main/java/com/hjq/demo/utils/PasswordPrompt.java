package com.hjq.demo.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.hjq.demo.R;

/**
 * The numeric password dialog.
 *
 * <p>This was an {@code AlertDialog} with a bare {@link EditText} dropped into
 * it: a hairline underline and platform-default type, which on a terminal that
 * under-reports its density came out roughly the size of a fingernail. It is
 * also the gate on the admin panel and on every restricted transaction, so it
 * is one of the most-used screens on the device and looked the least finished.</p>
 *
 * <p>Now: a blue header, a card, a row of large dots that fill as digits are
 * typed, and the two actions as full-height coloured buttons - red CANCEL on
 * the left, green CONFIRM on the right. The value still lives in a real (but
 * invisible) EditText so the platform keypad and the password masking are the
 * platform's rather than hand-rolled; only the dots are drawn by this class.</p>
 *
 * <p>A wrong entry is reported inside the dialog and the dialog stays open,
 * rather than closing on a toast. Retyping a password is the expected next
 * action, and re-opening the dialog to do it was the previous behaviour's
 * worst part.</p>
 */
public final class PasswordPrompt {

    public interface OnOk {
        void ok();
    }

    /** Optional: told when the operator backs out. */
    public interface OnCancel {
        void cancelled();
    }

    private PasswordPrompt() {
    }

    public static void ask(Context ctx, String title, String expected, OnOk cb) {
        ask(ctx, title, expected, cb, null);
    }

    public static void ask(final Context ctx, final String title, final String expected,
                           final OnOk cb, final OnCancel onCancel) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_password, null);

        final TextView tvTitle = v.findViewById(R.id.pw_title);
        final TextView tvDots = v.findViewById(R.id.pw_dots);
        final TextView tvError = v.findViewById(R.id.pw_error);
        final EditText etValue = v.findViewById(R.id.pw_value);
        Button btnCancel = v.findViewById(R.id.pw_cancel);
        Button btnConfirm = v.findViewById(R.id.pw_confirm);

        if (tvTitle != null && title != null && !title.trim().isEmpty()) {
            tvTitle.setText(title.trim().toUpperCase(java.util.Locale.US));
        }

        final Dialog dlg = new Dialog(ctx);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(v);
        dlg.setCancelable(false);
        Window w = dlg.getWindow();
        if (w != null) {
            // The card draws its own rounded white background, so the window
            // behind it has to be transparent or the corners sit on a grey
            // square.
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.90f);
            w.setAttributes(lp);
        }

        // The dots ARE the feedback. One filled circle per digit typed, so the
        // operator can count what they have entered without the value ever
        // being drawn on screen.
        etValue.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                int n = e == null ? 0 : e.length();
                StringBuilder d = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    d.append('●');
                }
                tvDots.setText(d.toString());
                if (tvError.getVisibility() == View.VISIBLE) {
                    tvError.setVisibility(View.GONE);
                }
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View b) {
                hideKeyboard(ctx, etValue);
                dlg.dismiss();
                if (onCancel != null) {
                    onCancel.cancelled();
                }
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View b) {
                String typed = etValue.getText() == null
                        ? "" : etValue.getText().toString().trim();
                String want = expected == null ? "" : expected;
                if (typed.equals(want)) {
                    hideKeyboard(ctx, etValue);
                    dlg.dismiss();
                    if (cb != null) {
                        cb.ok();
                    }
                    return;
                }
                // Stay open. Retyping is what happens next, and closing the
                // dialog to say "wrong password" in a toast meant the operator
                // had to find their way back in to do it.
                tvError.setText(typed.isEmpty()
                        ? "ENTER THE PASSWORD" : "WRONG PASSWORD - TRY AGAIN");
                tvError.setVisibility(View.VISIBLE);
                etValue.setText("");
                showKeyboard(ctx, etValue);
            }
        });

        dlg.show();
        etValue.requestFocus();
        showKeyboard(ctx, etValue);
    }

    private static void showKeyboard(Context ctx, View target) {
        try {
            InputMethodManager im = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) {
                im.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
            }
        } catch (Throwable ignored) {
            // no keypad on this build - the physical keys still work
        }
    }

    private static void hideKeyboard(Context ctx, View target) {
        try {
            InputMethodManager im = (InputMethodManager)
                    ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) {
                im.hideSoftInputFromWindow(target.getWindowToken(), 0);
            }
        } catch (Throwable ignored) {
            // nothing useful to do
        }
    }
}
