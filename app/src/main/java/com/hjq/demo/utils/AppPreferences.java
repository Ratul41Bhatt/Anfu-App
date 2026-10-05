package com.hjq.demo.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class AppPreferences {
    private static final String PREFS_NAME = "AppPreferences";
    private static final String KEY_FIRST_LAUNCH = "isFirstLaunch";

    private SharedPreferences sharedPreferences;

    // 单例模式（可选）
    private static AppPreferences instance;

    private AppPreferences(Context context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized AppPreferences getInstance(Context context) {
        if (instance == null) {
            instance = new AppPreferences(context.getApplicationContext());
        }
        return instance;
    }

    // 检查是否是首次启动
    public boolean isFirstLaunch() {
        return sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true);  // 默认为true，即未导入AID,CAPK成功
    }

    // 设置首次启动标志为false
    public void setFirstLaunch(boolean isFirstLaunch) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY_FIRST_LAUNCH, isFirstLaunch);
        editor.apply();
    }

    /**
     * True once a CA public key load has actually reported success.
     *
     * <p>Separate from {@link #isFirstLaunch()}: that flag only records that
     * the app has started before, so a failed CAPK load used to leave the
     * terminal permanently without keys. Every chip card then fails offline
     * data authentication and declines with an AAC.</p>
     */
    public boolean isCapkLoaded() {
        return sharedPreferences.getBoolean(KEY_CAPK_LOADED, false);
    }

    public void setCapkLoaded(boolean loaded) {
        sharedPreferences.edit().putBoolean(KEY_CAPK_LOADED, loaded).apply();
    }

    /** Force AID/CAPK to be reloaded on the next start. */
    public void clearCapkLoaded() {
        setCapkLoaded(false);
    }

    private static final String KEY_CAPK_LOADED = "isCapkLoaded";

    // ------------------------------------------------------- AID table version

    private static final String KEY_AID_VERSION = "aidParamVersion";

    /**
     * Version stamp for the AID tables in {@code PayActivity}.
     *
     * <p><b>Bump this whenever those AID strings change.</b> The load is
     * otherwise gated on {@code isFirstLaunch() || !isCapkLoaded()}, so on a
     * terminal that has already been provisioned an edit to the AID table never
     * reaches the EMV kernel - the app keeps running against the old parameters
     * and the change looks like it did nothing.</p>
     *
     * <p>Version history:</p>
     * <ul>
     *   <li>1 - original tables</li>
     *   <li>2 - Visa AIDs A0000000031010 / 32010 / 33010: 9F09 corrected from
     *       0140 to 008C. 0140 was the decimal application version 140 written
     *       as if it were hex; 0x008C <em>is</em> 140. A working terminal on the
     *       same card and TID sends 008C, and the 5-byte Visa entry in the same
     *       table already said 008C.</li>
     * </ul>
     */
    /**
     * Bump whenever the AID or CA public key tables change, so an
     * already-provisioned terminal reloads them instead of keeping the set it
     * was commissioned with.
     *
     * <p>5 - KonaMoney / NexusPay Contactless AID (A0000002514025260010) transaction limit
     * and ceiling limit bumped to 999999999999 so contactless MSD transactions do not reject.</p>
     *
     * <p>4 - TAKAPAY (A00000091010) and NEXUSPAY (F0504442420110) AIDs added,
     * with the TakaPay CA public keys for RID A000000910.</p>
     *
     * <p>3 - CA public keys now come from assets/capk.json (78 keys, the same
     * table the PAX reference uses). The previous built-in list was missing
     * <p>6 - KonaMoney / NexusPay contactless MSD parameters and contactless ODA disabled.</p>
     * <p>7 - Anfu Bangladesh customized card AIDs updated: NexusPay (A0000002514025260010),
     * DBBL Nexus chip (F0504442420110 / F05044420110), TakaPay (A00000091010), and Diners Club (A0000001524010).</p>
     * <p>8 - Prioritized AID ordering for instantaneous contactless reads on NexusPay PureKernel,
     * DBBL Nexus, TakaPay, Visa, and Mastercard; removed duplicate foreign entries.</p>
     * <p>9 - KonaMoney / NexusPay Contactless MSD TTQ (9F6604B6000000) added for NexusPay mobile HCE app acceptance.</p>
     * <p>10 - 9F66 TTQ added to kernel runtime addition TLV buffer, and storage permission interference removed.</p>
     * <p>11 - Partial match ASI (DF010101) enabled and TTQ added to all domestic contactless AIDs.</p>
     * <p>12 - 9F09 (0002) added to NexusPay/KonaMoney AID for Kernel 2 / PURE compatibility.</p>
     */
    public static final int AID_PARAM_VERSION = 13;

    /** True when the kernel holds AID parameters older than the app's. */
    public boolean isAidParamStale() {
        return sharedPreferences.getInt(KEY_AID_VERSION, 0) < AID_PARAM_VERSION;
    }

    public void setAidParamVersionCurrent() {
        sharedPreferences.edit().putInt(KEY_AID_VERSION, AID_PARAM_VERSION).apply();
    }
}
