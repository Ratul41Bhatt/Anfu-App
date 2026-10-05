package com.hjq.demo.utils;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import com.hjq.demo.ans8583.TipAmounts;

/** Sale-style entry: each digit shifts the amount one place in minor units. */
public final class MinorAmountInput implements TextWatcher {
    private final EditText input;
    private final Runnable changed;
    private boolean enabled, updating;
    private String last = "0.00";

    public MinorAmountInput(EditText input, Runnable changed) {
        this.input = input;
        this.changed = changed;
        input.addTextChangedListener(this);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        // Accept the rendered decimal point, while requesting a digits-only IME.
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (enabled ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        if (enabled) {
            if (input.getText() == null || input.getText().length() == 0) {
                input.setText("0.00");
            } else {
                afterTextChanged(input.getText());
            }
        }
    }

    public static String format(CharSequence text) {
        String digits = text.toString().replaceAll("[^0-9]", "").replaceFirst("^0+(?!$)", "");
        if (digits.length() > 12) throw new IllegalArgumentException("Maximum amount is 9,999,999,999.99");
        return TipAmounts.major(digits.isEmpty() ? 0 : Long.parseLong(digits));
    }

    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
    @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
    @Override public void afterTextChanged(Editable text) {
        if (updating) return;
        if (enabled) {
            String formatted;
            try { formatted = format(text); }
            catch (IllegalArgumentException e) { formatted = last; input.setError(e.getMessage()); }
            updating = true;
            if (!formatted.contentEquals(text)) text.replace(0, text.length(), formatted);
            input.setSelection(text.length());
            last = formatted;
            updating = false;
        }
        changed.run();
    }
}
