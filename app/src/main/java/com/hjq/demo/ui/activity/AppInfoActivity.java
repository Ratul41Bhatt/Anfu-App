package com.hjq.demo.ui.activity;

import android.graphics.Bitmap;
import android.os.RemoteException;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gyf.immersionbar.ImmersionBar;
import com.hjq.demo.R;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.app.AppActivity;
import com.hjq.demo.utils.AppInfo;
import com.hjq.demo.utils.LogUtils;
import com.hjq.demo.utils.PrintUtil;
import com.hjq.demo.utils.Slip;
import com.szanfu.sdk.api.ISdkServiceManager;
import com.szanfu.sdk.api.constant.ServiceID;
import com.szanfu.sdk.api.printer.IPrinterBinderService;
import com.szanfu.sdk.api.printer.IPrinterCallback;
import com.szanfu.sdk.api.system.ISystemBinderService;

import java.util.List;

/**
 * App Information - which build is on this terminal and what it is running on.
 *
 * <p>The PAX reference splits this in two: a Version item on the terminal menu
 * that puts the version on screen, and an Application Info slip built by
 * {@code ReceiptAppInfo} that prints the version alongside the model, OS,
 * serial number and SIM details. One screen here, showing the printed slip's
 * whole contents, with a button that prints it - the on-screen list and the
 * paper come from the same {@link AppInfo#entries}, so the two cannot
 * disagree.</p>
 *
 * <p>Every value is read from the device. The screen exists to answer "what is
 * actually on this terminal" during a support call; a hardcoded answer to that
 * question is worse than no screen at all.</p>
 */
public final class AppInfoActivity extends AppActivity implements View.OnClickListener {

    private LinearLayout list;
    private LinearLayout ids;
    private TextView status;
    private IPrinterBinderService printer;
    private ISystemBinderService system;

    /** Asking for CALL_PHONE, which the *2# lookup needs. */
    private static final int REQ_CALL_PHONE = 0x51;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_app_info;
    }

    @Override
    protected void initView() {
        // Load the slip typeface while the screen is being built, not on the
        // printer thread with the paper already moving.
        Slip.useFont(getApplicationContext());

        // AppActivity runs an immersive status bar and only pushes content
        // down when the layout carries a TitleBar. This one does not, so
        // without this the first row sits underneath the clock and the
        // battery. Measured rather than a fixed dp, because the status bar is
        // not the same height on a device with a notch.
        View root = findViewById(R.id.info_root);
        if (root != null) {
            root.setPadding(root.getPaddingLeft(),
                    root.getPaddingTop() + ImmersionBar.getStatusBarHeight(this),
                    root.getPaddingRight(), root.getPaddingBottom());
        }

        list = findViewById(R.id.info_list);
        ids = findViewById(R.id.info_ids);
        status = findViewById(R.id.info_status);
        View print = findViewById(R.id.info_print);
        if (print != null) {
            print.setOnClickListener(this);
        }
        View back = findViewById(R.id.info_back);
        if (back != null) {
            back.setOnClickListener(this);
        }
        View cancel = findViewById(R.id.info_cancel);
        if (cancel != null) {
            cancel.setOnClickListener(this);
        }
    }

    @Override
    protected void initData() {
        TerminalConfig tc = TerminalConfig.get(this);
        setText(R.id.info_merchant, tc.merchantName());

        StringBuilder addr = new StringBuilder();
        for (int i = 1; i <= TerminalConfig.ADDR_LINES; i++) {
            String line = tc.merchantAddr(i);
            if (line != null && !line.trim().isEmpty()) {
                if (addr.length() > 0) {
                    addr.append('\n');
                }
                addr.append(line.trim());
            }
        }
        setText(R.id.info_address, addr.toString());
        View addrView = findViewById(R.id.info_address);
        if (addrView != null) {
            addrView.setVisibility(addr.length() == 0 ? View.GONE : View.VISIBLE);
        }
        // The identifiers, in the same row component as everything else.
        // They are not in AppInfo.entries because the printed slip already
        // carries them in its header - putting them in both would print each
        // one twice.
        if (ids != null) {
            ids.removeAllViews();
            ids.addView(row(new AppInfo.Item("MERCHANT ID", tc.mid()), ids));
            ids.addView(row(new AppInfo.Item("TERMINAL ID", tc.tid()), ids));
            ids.addView(row(new AppInfo.Item("BATCH NO", tc.batchNo()), ids));
        }

        refreshList();
        loadSimNumbers();
    }

    /** Redraw the fact rows. Called again as the serial and SIM numbers land. */
    private void refreshList() {
        if (list == null) {
            return;
        }
        list.removeAllViews();
        for (AppInfo.Item item : AppInfo.entries(this)) {
            list.addView(row(item, list));
        }
    }

    @Override
    public void onServiceConnected(ISdkServiceManager sdk) {
        try {
            printer = IPrinterBinderService.Stub.asInterface(
                    sdk.getService(ServiceID.SERVICE_ID_PRINTER));
        } catch (Exception e) {
            say("Printer not available: " + e.getMessage());
        }
        // The terminal's serial number, from the Anfu SDK rather than from
        // Android. Build.getSerial() has needed READ_PHONE_STATE since Oreo and
        // refuses on this hardware anyway, which is why the row read
        // "NOT AVAILABLE"; ISystemBinderService.getSN() is the number printed
        // on the back of the unit.
        try {
            system = ISystemBinderService.Stub.asInterface(
                    sdk.getService(ServiceID.SERVICE_ID_SYSTEM));
            if (system != null) {
                String sn = system.getSN();
                if (sn == null || sn.trim().isEmpty()) {
                    sn = system.getTUSN();
                }
                AppInfo.setSerial(sn);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                    }
                });
            }
        } catch (Exception e) {
            LogUtils.e("AppInfo: cannot read the SDK serial - " + e.getMessage());
        }
    }

    /**
     * Ask each SIM for its own number.
     *
     * <p>Needs CALL_PHONE, so ask for it first. Declining is a perfectly normal
     * answer - the rows say so and everything else on the screen still
     * works.</p>
     */
    private void loadSimNumbers() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M
                && checkSelfPermission(android.Manifest.permission.CALL_PHONE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {android.Manifest.permission.CALL_PHONE}, REQ_CALL_PHONE);
            return;
        }
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        refreshList();
                    }
                });
            }
        };
        AppInfo.requestMsisdn(this, 0, refresh);
        AppInfo.requestMsisdn(this, 1, refresh);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CALL_PHONE) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadSimNumbers();
        } else {
            AppInfo.setMsisdn(0, "NEED CALL PERMISSION");
            AppInfo.setMsisdn(1, "NEED CALL PERMISSION");
            refreshList();
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.info_print) {
            print();
        } else if (id == R.id.info_back || id == R.id.info_cancel) {
            finish();
        }
    }

    /** One label / value line, built in code so the list follows the facts. */
    private View row(AppInfo.Item item, LinearLayout parent) {
        View v = getLayoutInflater().inflate(R.layout.item_app_info, parent, false);
        ((TextView) v.findViewById(R.id.item_label)).setText(item.label);
        ((TextView) v.findViewById(R.id.item_value)).setText(item.value);
        return v;
    }

    private void setText(int id, String text) {
        TextView v = findViewById(id);
        if (v != null) {
            v.setText(text);
        }
    }

    /**
     * The printer callback, held in a field on purpose.
     *
     * <p>{@code printBitmap} is an asynchronous binder call: it hands the job
     * over and returns. The callback used to be an anonymous {@code Stub}
     * created inside a worker thread, so the only strong reference to it died
     * with that thread - and a Binder object with no local reference can be
     * collected, at which point the print service is holding a dead proxy. The
     * job goes and nothing comes out. Keeping one instance for the life of the
     * screen removes the race.</p>
     */
    private final IPrinterCallback mPrintCallback = new IPrinterCallback.Stub() {
        @Override
        public void onResult(int code, String msg, int sub) throws RemoteException {
            LogUtils.i("app info print result " + code + " " + msg + " sub " + sub);
            // Report what the printer said rather than what was hoped for. The
            // status line used to read "Application info printed" the moment
            // the job was handed over, whether or not any paper moved.
            sayOnUi(code == 0
                    ? "Application info printed"
                    : "Printer returned " + code
                            + (msg == null || msg.isEmpty() ? "" : " - " + msg));
        }
    };

    private void print() {
        if (printer == null) {
            say("Printer not ready - SDK service not bound");
            return;
        }
        final List<Slip.Line> lines = AppInfo.lines(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Ask the head how it is and log it. Out of paper,
                    // overheated and lid-open all show up here, and any of them
                    // explains a button that appears to do nothing. The job is
                    // still sent whatever comes back: the meaning of these
                    // codes is the SDK's business and refusing to print on a
                    // code this app has guessed at would be a worse bug than
                    // the one being fixed.
                    try {
                        LogUtils.i("app info: thermal head status "
                                + printer.dalThermalPrintGetStatus());
                    } catch (Exception e) {
                        LogUtils.e("app info: cannot read printer status - " + e.getMessage());
                    }

                    Slip.useFont(getApplicationContext());
                    TerminalConfig tc = TerminalConfig.get(getApplicationContext());
                    Bitmap logo = tc.printBankLogo() || tc.printMerchantLogo()
                            ? Slip.bankLogo(getApplicationContext(), Slip.LOGO_W) : null;
                    Bitmap bmp = Slip.toBitmap(lines, logo);
                    if (bmp == null || bmp.getWidth() <= 0 || bmp.getHeight() <= 0) {
                        sayOnUi("Nothing to print - the slip rendered empty");
                        return;
                    }
                    LogUtils.i("app info slip " + bmp.getWidth() + "x" + bmp.getHeight()
                            + " from " + lines.size() + " lines");
                    sayOnUi("Printing " + lines.size() + " lines...");
                    printer.printBitmap(0, PrintUtil.getBitmapData(bmp),
                            PrintUtil.getPaddingBitWidth(bmp.getWidth()), bmp.getHeight(),
                            mPrintCallback);
                } catch (Exception e) {
                    LogUtils.e("app info print failed: " + e.getMessage());
                    sayOnUi("Print failed: " + e.getMessage());
                }
            }
        }).start();
    }

    private void say(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    private void sayOnUi(final String s) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                say(s);
            }
        });
    }
}
