package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * The EMI products this terminal offers.
 *
 * <p>Entered by hand in the admin panel. The PAX reference downloads them
 * from the host with its {@code EMI_PACKAGE} message, but DBBL do not serve
 * that, so the plans the bank agrees with the merchant are keyed in here.</p>
 *
 * <p>Which tenures are actually offered at the till is filtered separately by
 * {@link TerminalConfig#emiTenureEnabled(int)}, matching PAX's twenty
 * {@code EMI_CONTROL_TENURE_n} parameters. A package whose term the merchant
 * has switched off is kept, just not offered.</p>
 */
public final class EmiStore {

    private static final String TAG = "EmiStore";
    private static final String PREF = "emi_store";
    private static final String K_PACKAGES = "packages";
    private static final String LINE_SEP = "\n";

    private final SharedPreferences sp;
    private static EmiStore instance;

    private EmiStore(Context c) {
        sp = c.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized EmiStore get(Context c) {
        if (instance == null) {
            instance = new EmiStore(c);
        }
        return instance;
    }

    /** Every package held, whatever its tenure, sorted by term. */
    public List<EmiPackage> all() {
        List<EmiPackage> out = new ArrayList<>();
        String raw = sp.getString(K_PACKAGES, "");
        if (raw.isEmpty()) {
            return out;
        }
        for (String line : raw.split(LINE_SEP)) {
            EmiPackage p = EmiPackage.parse(line);
            if (p != null) {
                out.add(p);
            }
        }
        Collections.sort(out, new Comparator<EmiPackage>() {
            @Override
            public int compare(EmiPackage a, EmiPackage b) {
                return a.tenure - b.tenure;
            }
        });
        return out;
    }

    /**
     * The packages to offer the cardholder: held by the terminal AND enabled
     * for that tenure in the parameters.
     */
    public List<EmiPackage> offered(Context ctx) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        List<EmiPackage> out = new ArrayList<>();
        for (EmiPackage p : all()) {
            if (tc.emiTenureEnabled(p.tenure)) {
                out.add(p);
            }
        }
        return out;
    }

    public EmiPackage byTenure(int tenure) {
        for (EmiPackage p : all()) {
            if (p.tenure == tenure) {
                return p;
            }
        }
        return null;
    }

    public EmiPackage byProductCode(String code) {
        if (code == null) {
            return null;
        }
        for (EmiPackage p : all()) {
            if (code.equals(p.productCode)) {
                return p;
            }
        }
        return null;
    }

    public synchronized void save(List<EmiPackage> packages) {
        StringBuilder b = new StringBuilder();
        for (EmiPackage p : packages) {
            if (b.length() > 0) {
                b.append(LINE_SEP);
            }
            b.append(p.serialize());
        }
        sp.edit().putString(K_PACKAGES, b.toString()).apply();
        Log.i(TAG, "stored " + packages.size() + " EMI package(s)");
    }

    public synchronized void add(EmiPackage p) {
        if (p == null) {
            return;
        }
        List<EmiPackage> all = all();
        // One package per tenure: a second one for the same term would make the
        // instalment depend on table order.
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).tenure == p.tenure) {
                all.set(i, p);
                save(all);
                return;
            }
        }
        all.add(p);
        save(all);
    }

    public synchronized void remove(int tenure) {
        List<EmiPackage> all = all();
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).tenure == tenure) {
                all.remove(i);
                save(all);
                return;
            }
        }
    }

    public synchronized void clear() {
        sp.edit().remove(K_PACKAGES).apply();
        Log.i(TAG, "EMI packages cleared");
    }

    public boolean isEmpty() {
        return all().isEmpty();
    }
}
