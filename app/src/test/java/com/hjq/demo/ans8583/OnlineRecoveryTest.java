package com.hjq.demo.ans8583;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Exercises the actual transaction/reversal builders and TCP transport using only loopback. */
public class OnlineRecoveryTest {
    private Context context;
    private TerminalConfig config;
    private static boolean failNextBatchCommit;
    private static boolean failNextSettingsCommit;
    @Before public void setup() throws Exception {
        reset();
        context = new ContextWrapper(null) {
            private final Map<String, SharedPreferences> stores = new HashMap<>();
            @Override public Context getApplicationContext() { return this; }
            @Override public String getPackageName() { return "com.anfu.offline.pay.demo.test"; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return stores.computeIfAbsent(name, key -> memoryPreferences(key));
            }
        };
        config = TerminalConfig.get(context);
        config.setTid("12345678"); config.setMid("123456789012345");
        config.setHostIp("127.0.0.1"); config.setTimeout(1000);
        config.setTpdu("6000080000"); config.setNii("008");
        config.setReversalMaxTries(1);
    }
    @After public void cleanup() throws Exception { reset(); }

    @Test public void tipParametersPersistBothTogglesAndPercentageAcrossConfigReload() throws Exception {
        assertFalse(config.saleWithTip()); assertFalse(config.tipAdjustEnabled());
        config.setTipSettings(true, true, 35);
        clearStatic(TerminalConfig.class, "instance"); config = TerminalConfig.get(context);
        assertTrue(config.saleWithTip()); assertTrue(config.tipAdjustEnabled()); assertEquals(35, config.tipPercentage());
        config.setTipSettings(false, true, 50);
        clearStatic(TerminalConfig.class, "instance"); config = TerminalConfig.get(context);
        assertFalse(config.saleWithTip()); assertTrue(config.tipAdjustEnabled()); assertEquals(50, config.tipPercentage());
        config.setTipSettings(true, false, 25);
        clearStatic(TerminalConfig.class, "instance"); config = TerminalConfig.get(context);
        assertTrue(config.saleWithTip()); assertFalse(config.tipAdjustEnabled()); assertEquals(25, config.tipPercentage());
    }

    @Test public void qrParametersPersistAtomicallyAndUseTheCommonMerchantHeader() throws Exception {
        context.getSharedPreferences("terminal_config", 0).edit()
                .putString("qr_merchant", "STALE NAME").putString("qr_city", "STALE CITY").commit();
        config.setMerchantName("OUR MERCHANT NAME");
        config.setMerchantAddr(1, "47 MOTIJHEEL C/A");
        config.setMerchantAddr(2, "DHAKA-1229, BANGLADESH");
        config.setQrParameters("", "6000050000", "5411", 75);
        clearStatic(TerminalConfig.class, "instance"); config = TerminalConfig.get(context);
        assertEquals("OUR MERCHANT NAME", config.qrMerchant());
        assertEquals("DHAKA", config.qrCity()); assertEquals("", config.qrNii());
        assertEquals(75, config.qrTimeoutSec()); assertEquals("5411", config.qrMcc());
        config.setMerchantName("NEW MERCHANT"); config.setMerchantAddr(2, "Sylhet, Bangladesh");
        assertEquals("NEW MERCHANT", config.qrMerchant()); assertEquals("Sylhet", config.qrCity());
        failNextSettingsCommit = true;
        assertThrows(IllegalStateException.class, () -> config.setQrParameters("009", "6000090000", "4111", 90));
        assertEquals("", config.qrNii()); assertEquals("6000050000", config.qrTpdu());
        assertEquals("5411", config.qrMcc()); assertEquals(75, config.qrTimeoutSec());
        assertThrows(IllegalArgumentException.class, () -> config.setQrParameters("9", "6000050000", "5411", 120));
        assertEquals(75, config.qrTimeoutSec());
    }
    @Test public void qrTimeoutCanBeSavedBeforeRoutingOrHeaderIsConfigured() {
        config.setMerchantName(""); config.setMerchantAddr(1, ""); config.setMerchantAddr(2, "");
        config.setQrParameters("", "6000050000", "", 80);
        assertEquals(80, config.qrTimeoutSec());
        assertThrows(IllegalArgumentException.class, () -> OnlineHost.createQrSale(context, 100));
    }
    @Test public void qrCommonHeaderFitsEmvFieldsAndDoesNotUseStreetAsCity() {
        assertEquals(25, DbblQr.merchantLabel("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length());
        assertEquals("Dhaka", DbblQr.merchantCity("47 MOTIJHEEL C/A", "Dhaka", "Bangladesh", ""));
        assertEquals("", DbblQr.merchantCity("47 MOTIJHEEL C/A", "1229", "Bangladesh"));
    }
    @Test public void saleStyleDigitsFormatExactlyWithoutFloatingPoint() {
        assertEquals("0.00", com.hjq.demo.utils.MinorAmountInput.format(""));
        assertEquals("0.01", com.hjq.demo.utils.MinorAmountInput.format("0.001"));
        assertEquals("0.12", com.hjq.demo.utils.MinorAmountInput.format("0.012"));
        assertEquals("12.34", com.hjq.demo.utils.MinorAmountInput.format("1234"));
        assertEquals("1.23", com.hjq.demo.utils.MinorAmountInput.format("12.3"));
        assertEquals("9999999999.99", com.hjq.demo.utils.MinorAmountInput.format("999999999999"));
        assertThrows(IllegalArgumentException.class, () -> com.hjq.demo.utils.MinorAmountInput.format("1000000000000"));
    }

    @Test public void qrTimeoutAndSettingsPasswordSurviveReloadAndRejectInvalidValues() throws Exception {
        assertEquals(60, config.qrTimeoutSec()); assertEquals("1235", config.settingsPw());
        config.setQrTimeoutSec(90); config.setSettingsPw("67890");
        clearStatic(TerminalConfig.class, "instance"); config = TerminalConfig.get(context);
        assertEquals(90, config.qrTimeoutSec()); assertEquals("67890", config.settingsPw());
        assertThrows(IllegalArgumentException.class, () -> config.setQrTimeoutSec(0));
        assertThrows(IllegalArgumentException.class, () -> config.setQrTimeoutSec(601));
        assertThrows(IllegalArgumentException.class, () -> config.setSettingsPw(""));
        assertEquals(90, config.qrTimeoutSec()); assertEquals("67890", config.settingsPw());
        failNextSettingsCommit = true;
        assertThrows(IllegalStateException.class, () -> config.setQrTimeoutSec(60));
        assertEquals(90, config.qrTimeoutSec());
        failNextSettingsCommit = true;
        assertThrows(IllegalStateException.class, () -> config.setSettingsPw("1235"));
        assertEquals("67890", config.settingsPw());
    }

    @Test public void qrReceiptIsSpecificToConfirmedQrAndReportSeparatesPending() throws Exception {
        try (Host host = new Host("QR_OK")) {
            qr(host); TxnRecord paid = OnlineHost.checkQrPayment(context, null).record;
            String receipt = slipText(com.hjq.demo.utils.QrSlip.receipt(context, paid, "MERCHANT COPY", false));
            assertTrue(receipt.contains("QR SALE")); assertTrue(receipt.contains("PAYMENT CONFIRMED"));
            assertTrue(receipt.contains("120.00")); assertTrue(receipt.contains(paid.rrn));
            assertFalse(receipt.contains("TVR")); assertFalse(receipt.contains("EXPIRY"));
            assertFalse(receipt.contains("PIN VERIFIED")); assertFalse(receipt.contains("SIGNATURE"));
            BatchStore.get(context).finishQr();
            DbblQr.Session pending = OnlineHost.createQrSale(context, 700);
            assertThrows(IllegalArgumentException.class, () -> com.hjq.demo.utils.QrSlip.receipt(context, pending.record, "COPY", false));
            String report = slipText(com.hjq.demo.utils.QrSlip.report(context,
                    Collections.singletonList(paid), Collections.singletonList(pending)));
            assertTrue(report.contains("PAID COUNT 1 TK 120.00"));
            assertTrue(report.contains("UNCONFIRMED - NOT SALES")); assertTrue(report.contains("7.00"));
        }
    }
    private static String slipText(List<com.hjq.demo.utils.Slip.Line> lines) {
        StringBuilder text = new StringBuilder();
        for (com.hjq.demo.utils.Slip.Line line : lines) text.append(line.left).append(' ').append(line.right).append('\n');
        return text.toString();
    }

    @Test public void vendorLockAppliesBarsEvenWhenSettingsPermissionIsRejected() throws Exception {
        Class<?> lock = com.hjq.demo.utils.TerminalLockdown.class;
        Field binder = lock.getDeclaredField("system"); binder.setAccessible(true);
        List<String> calls = new ArrayList<>();
        Class<?> api = com.szanfu.sdk.api.system.ISystemBinderService.class;
        Object service = Proxy.newProxyInstance(api.getClassLoader(), new Class[]{api}, (p, method, args) -> {
            calls.add(method.getName() + ":" + args[0]);
            if (method.getName().equals("setSettingsPwd")) return -1003;
            return 0;
        });
        binder.set(null, service);
        try {
            java.lang.reflect.Method apply = lock.getDeclaredMethod("apply", Context.class); apply.setAccessible(true);
            apply.invoke(null, context);
            assertEquals("grantVendorSystemSdkPermission:com.anfu.offline.pay.demo.test", calls.get(0));
            assertTrue(calls.contains("setSettingsPwd:1235"));
            assertTrue(calls.contains("setNavBarEnabled:false"));
            assertTrue(calls.contains("setStatusBarEnabled:false"));
            assertTrue(com.hjq.demo.utils.TerminalLockdown.status().contains("Not applied (-1003)"));
        } finally { binder.set(null, null); }
    }
    @Test public void invalidTipPercentagePreservesPreviouslySavedSettings() {
        config.setTipSettings(true, true, 40);
        assertThrows(IllegalArgumentException.class, () -> config.setTipSettings(false, false, 101));
        assertTrue(config.saleWithTip()); assertTrue(config.tipAdjustEnabled()); assertEquals(40, config.tipPercentage());
    }
    @Test public void failedSettingsWriteRestoresOldSwitchesInMemory() {
        config.setTipSettings(true, false, 40); failNextSettingsCommit = true;
        assertThrows(IllegalStateException.class, () -> config.setTipSettings(false, true, 25));
        assertTrue(config.saleWithTip()); assertFalse(config.tipAdjustEnabled()); assertEquals(40, config.tipPercentage());
    }

    private TxnRecord tipSale() {
        config.setTipSettings(true, true, 50);
        TxnRecord r = new TxnRecord();
        r.amountMinor = "10000"; r.invoice = "000377"; r.stan = "000728";
        r.rrn = "626405000728"; r.authCode = "735732";
        r.pan = "4111111111111111"; r.expiry = "3010"; r.entryMode = "051";
        r.iccHex = "9F0206000000010000"; r.time = "115243"; r.date = "0921";
        r.completedIccHex = "9F02060000000100009F270140";
        r.approved = true; r.pinUsed = true;
        BatchStore.get(context).add(r); return r;
    }

    @Test public void offlineTipSurvivesRecreationAndUpdatesOneSale() throws Exception {
        tipSale();
        TxnRecord advice = OnlineHost.queueTipAdjustment(context, "000377", 2000);
        clearStatic(BatchStore.class, "instance");
        BatchStore b = BatchStore.get(context);
        assertEquals(1, b.count()); assertEquals(1, b.totals().saleCount);
        assertEquals(12000, b.totals().saleAmt); assertTrue(b.hasPendingTips());
        assertEquals(advice.stan, b.pendingTips().get(0).stan);
        assertEquals("", b.pendingTips().get(0).track2);
        assertEquals("10000", b.pendingTips().get(0).originalAmountMinor);
        assertFalse(b.hasPendingReversal());
        assertThrows(IllegalArgumentException.class, () -> OnlineHost.queueTipAdjustment(context, "000377", 3000));
        assertThrows(IllegalStateException.class, b::clear);
    }
    @Test public void queuedTip0220MustBeAcknowledgedBeforeNew0200() throws Exception {
        try (Host host = new Host("00", "00")) {
            config.setHostPort(host.server.getLocalPort()); tipSale();
            TxnRecord advice = OnlineHost.queueTipAdjustment(context, "000377", 2000);
            OnlineHost.HostResult uploadedSale = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertTrue(uploadedSale.approved);
            assertTrue(uploadedSale.precedingTrace.contains("TIP ADJUST (0220)"));
            assertTrue(uploadedSale.packetTrace().startsWith(uploadedSale.precedingTrace));
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0220", "0200"), mtis(packets));
            Iso8583 p = packets.get(0);
            assertEquals("020000", p.get(3)); assertEquals("000000012000", p.get(4));
            assertEquals(advice.stan, p.get(11)); assertEquals("115243", p.get(12));
            assertEquals("626405000728", p.get(37)); assertEquals("735732", p.get(38));
            assertEquals(Iso8583.ascii("000000002000"), p.get(54));
            assertEquals(Iso8583.ascii("000000010000"), p.get(60));
            assertEquals(Iso8583.ascii("000377"), p.get(62));
            assertEquals("9F02060000000100009F270140", p.get(55));
            assertEquals("9F0206000000010000", BatchStore.get(context).findByInvoice("000377").iccHex);
            assertFalse(p.has(35)); assertFalse(p.has(52));
            assertFalse(BatchStore.get(context).hasPendingTips());
            assertTrue(BatchStore.get(context).tipHistory().get(0).hostOnline);
        }
    }
    @Test public void lostTipReplyBlocksSaleAndRetryKeepsOriginalStan() throws Exception {
        try (Host host = new Host(null, "00", "00")) {
            config.setHostPort(host.server.getLocalPort()); tipSale();
            OnlineHost.queueTipAdjustment(context, "000377", 2000);
            assertFalse(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
            assertTrue(BatchStore.get(context).hasPendingTips());
            assertFalse(BatchStore.get(context).hasPendingReversal());
            clearStatic(BatchStore.class, "instance");
            assertTrue(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0220", "0220", "0200"), mtis(packets));
            assertEquals(packets.get(0).get(11), packets.get(1).get(11));
            assertEquals(packets.get(0).get(62), packets.get(1).get(62));
        }
    }
    @Test public void invalidTipAcknowledgementRetainsQueueAndBlocksNewSale() throws Exception {
        for (String response : Arrays.asList("05", "BAD_STAN", "BAD_MTI", "BAD_AMOUNT", "BAD_TID")) {
            reset(); setup();
            try (Host host = new Host(response)) {
                config.setHostPort(host.server.getLocalPort()); tipSale();
                OnlineHost.queueTipAdjustment(context, "000377", 2000);
                assertFalse(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
                assertEquals(Collections.singletonList("0220"), mtis(host.requests()));
                assertTrue(BatchStore.get(context).hasPendingTips());
            }
        }
    }
    @Test public void tipQueueRefusesWrongRouteOrIneligibleInvoice() {
        tipSale();
        assertThrows(IllegalArgumentException.class, () -> OnlineHost.queueTipAdjustment(context, "000999", 2000));
        assertThrows(IllegalArgumentException.class, () -> OnlineHost.queueTipAdjustment(context, "000377", 5001));
        assertFalse(BatchStore.get(context).hasPendingTips());
        OnlineHost.queueTipAdjustment(context, "000377", 2000);
        config.setMid("999999999999999");
        assertTrue(OnlineHost.flushPendingTips(context, null).contains("restore original"));
        assertTrue(BatchStore.get(context).hasPendingTips());
    }
    @Test public void failedOfflineCommitDoesNotApproveOrChangeSale() {
        tipSale(); failNextBatchCommit = true;
        assertThrows(IllegalStateException.class, () -> OnlineHost.queueTipAdjustment(context, "000377", 2000));
        assertEquals(10000, BatchStore.get(context).findByInvoice("000377").amount());
        assertFalse(BatchStore.get(context).hasPendingTips());
        assertEquals(0, BatchStore.get(context).tipHistory().size());
    }
    @Test public void failedAcknowledgementCommitKeepsAdviceAndBlocksSale() throws Exception {
        try (Host host = new Host("00")) {
            config.setHostPort(host.server.getLocalPort()); tipSale();
            OnlineHost.queueTipAdjustment(context, "000377", 2000);
            failNextBatchCommit = true;
            assertFalse(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
            assertEquals(Collections.singletonList("0220"), mtis(host.requests()));
            assertTrue(BatchStore.get(context).hasPendingTips());
            assertFalse(BatchStore.get(context).tipHistory().get(0).hostOnline);
        }
    }
    @Test public void multipleAdjustmentsUploadInOrderWithoutDoubleCounting() throws Exception {
        try (Host host = new Host("00", "00", "00")) {
            config.setHostPort(host.server.getLocalPort()); TxnRecord first = tipSale();
            TxnRecord second = TxnRecord.parse(first.serialize()); second.invoice = "000378"; second.stan = "000729";
            BatchStore.get(context).add(second);
            OnlineHost.queueTipAdjustment(context, first.invoice, 2000);
            OnlineHost.queueTipAdjustment(context, second.invoice, 3000);
            assertEquals(25000, BatchStore.get(context).totals().saleAmt);
            assertEquals(2, BatchStore.get(context).totals().saleCount);
            assertTrue(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0220", "0220", "0200"), mtis(packets));
            assertEquals(Iso8583.ascii(first.invoice), packets.get(0).get(62));
            assertEquals(Iso8583.ascii(second.invoice), packets.get(1).get(62));
        }
    }
    @Test public void saleWithTipSendsTotalAndTipAndReversalRepeatsBoth() throws Exception {
        try (Host host = new Host(null, "00")) {
            config.setHostPort(host.server.getLocalPort()); config.setTipSettings(true, false, 50);
            OnlineHost.HostResult r = OnlineHost.transaction(context, "SALE", "150.00", "4111111111111111", null,
                    "4111111111111111=30102010000000000000", "3010", "", OnlineHost.EntryMode.MAGSTRIPE,
                    "00", null, new OnlineHost.SaleTip(10000, 5000));
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0200", "0400"), mtis(packets));
            for (Iso8583 p : packets) {
                assertEquals("000000015000", p.get(4));
                assertEquals(Iso8583.ascii("000000005000"), p.get(54));
            }
            assertEquals(10000, r.record.baseAmount()); assertEquals(5000, r.record.tip());
            assertTrue(r.reversalAcknowledged);
        }
    }

    @Test public void tipUploadPrecedesLogonAndRetainsTrace() throws Exception {
        try (Host host = new Host("00", "05")) {
            config.setHostPort(host.server.getLocalPort()); tipSale();
            OnlineHost.queueTipAdjustment(context, "000377", 2000);
            OnlineHost.HostResult result = OnlineHost.logon(context, null);
            assertEquals(Arrays.asList("0220", "0800"), mtis(host.requests()));
            assertTrue(result.precedingTrace.contains("TIP ADJUST (0220)"));
            assertTrue(result.precedingTrace.contains("Response"));
            assertFalse(BatchStore.get(context).hasPendingTips());
        }
    }
    @Test public void failedTipBlocksLogonAndRetainsActualPacketTrace() throws Exception {
        try (Host host = new Host("05")) {
            config.setHostPort(host.server.getLocalPort()); tipSale();
            OnlineHost.queueTipAdjustment(context, "000377", 2000);
            OnlineHost.HostResult result = OnlineHost.logon(context, null);
            assertEquals(Collections.singletonList("0220"), mtis(host.requests()));
            assertFalse(result.approved); assertTrue(BatchStore.get(context).hasPendingTips());
            assertTrue(result.packetTrace().contains("TIP ADJUST (0220)"));
            assertTrue(result.error.contains("New transaction not sent"));
        }
    }
    @Test public void tipUploadPrecedesEveryFinancialEntryPoint() throws Exception {
        for (String kind : Arrays.asList("VOID", "PREAUTH", "PREAUTH_VOID", "SALE_COMPLETE", "SETTLEMENT", "BATCH")) {
            reset(); setup();
            try (Host host = new Host("00", "05")) {
                config.setHostPort(host.server.getLocalPort()); TxnRecord original = tipSale();
                OnlineHost.queueTipAdjustment(context, original.invoice, 2000);
                OnlineHost.HostResult result;
                switch (kind) {
                    case "VOID": result = OnlineHost.voidSale(context, original, null, null); break;
                    case "PREAUTH_VOID": original.type = "PREAUTH"; result = OnlineHost.preAuthVoid(context, original, null); break;
                    case "SALE_COMPLETE": original.type = "PREAUTH"; result = OnlineHost.saleComplete(context, original, "100.00", null); break;
                    case "SETTLEMENT": result = OnlineHost.settle(context, null, null).settle; break;
                    case "BATCH": result = OnlineHost.batchUpload(context, original, null); break;
                    default: result = OnlineHost.transaction(context, "PREAUTH", "1.00", "4111111111111111", null,
                            "4111111111111111=30102010000000000000", "3010", "", OnlineHost.EntryMode.MAGSTRIPE, "00", null);
                }
                List<Iso8583> packets = host.requests();
                assertEquals(kind, 2, packets.size()); assertEquals(kind, "0220", packets.get(0).mti);
                assertNotNull(result); assertTrue(kind, result.precedingTrace.contains("TIP ADJUST (0220)"));
            }
        }
    }
    private DbblQr.Session qr(Host host) {
        config.setHostPort(host.server.getLocalPort());
        config.setQrSettings("009", "6000050000", "5411");
        return OnlineHost.createQrSale(context, 12000);
    }
    @Test public void qrStatusUsesSavedReferenceAndSeparateRouteAfterRestart() throws Exception {
        try (Host host = new Host("31", "QR_OK")) {
            DbblQr.Session session = qr(host);
            assertTrue(session.record.date.matches("[0-9]{4}"));
            assertTrue(session.record.time.matches("[0-9]{6}"));
            OnlineHost.HostResult pending = OnlineHost.checkQrPayment(context, null);
            assertTrue(pending.packetTrace().contains("0200"));
            assertTrue(pending.packetTrace().contains("0210"));
            assertFalse(pending.approved); assertFalse(pending.needsReversal);
            assertFalse(BatchStore.get(context).hasPendingReversal());
            clearStatic(BatchStore.class, "instance");
            assertEquals(session.reference, OnlineHost.createQrSale(context, 999).reference);
            OnlineHost.HostResult paid = OnlineHost.checkQrPayment(context, null);
            assertTrue(paid.packetTrace().contains("0200"));
            assertTrue(paid.packetTrace().contains("0210"));
            assertTrue(paid.error, paid.approved);
            assertEquals("4111111111111111", paid.record.pan);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0200", "0200"), mtis(packets));
            for (Iso8583 p : packets) {
                assertEquals("6000050000", p.tpdu); assertEquals("009", p.get(24));
                assertEquals("840", p.get(22)); assertEquals("000000012000", p.get(4));
                assertEquals(Iso8583.ascii("QR" + session.reference), p.get(63));
                assertEquals(session.record.stan, p.get(11)); assertFalse(p.has(2));
            }
            assertEquals("6000080000", config.tpdu()); assertEquals("008", config.nii());
            assertTrue(OnlineHost.checkQrPayment(context, null).approved); // no duplicate network request
            assertEquals(1, BatchStore.get(context).qrHistory().size());
            assertEquals(0, BatchStore.get(context).count());
        }
    }
    @Test public void exitingQrClearsActiveCodeAndAllowsAnotherAmount() throws Exception {
        try (Host host = new Host()) {
            DbblQr.Session old = qr(host);
            BatchStore batch = BatchStore.get(context);
            batch.dismissQr(old.reference);
            assertNull(batch.qrSession());
            assertEquals(old.reference, batch.pendingQrHistory().get(0).reference);
            clearStatic(BatchStore.class, "instance");
            batch = BatchStore.get(context);
            assertNull(batch.qrSession());
            DbblQr.Session fresh = OnlineHost.createQrSale(context, 7500);
            assertEquals(7500, fresh.record.amount());
            assertNotEquals(old.record.invoice, fresh.record.invoice);
            assertNotEquals(old.payload, fresh.payload);
        }
    }
    @Test public void closedQrScreenCannotCreateLateQr() throws Exception {
        try (Host host = new Host()) {
            qr(host); BatchStore.get(context).finishQr();
            assertThrows(IllegalStateException.class, () -> OnlineHost.createQrSale(context, 7500, () -> false));
            assertNull(BatchStore.get(context).qrSession());
        }
    }
    @Test public void lateResponseForClosedQrDoesNotRestoreItOrReplaceNewQr() throws Exception {
        try (Host host = new Host()) {
            DbblQr.Session old = qr(host);
            BatchStore batch = BatchStore.get(context);
            batch.dismissQr(old.reference);
            Thread.sleep(2); // distinct millisecond reference, as on a physical menu interaction
            DbblQr.Session fresh = OnlineHost.createQrSale(context, 7500);
            TxnRecord paid = TxnRecord.parse(old.record.serialize()); paid.approved = true;
            batch.completeQr(new DbblQr.Session(paid, old.reference, old.payload, old.route));
            assertEquals(fresh.reference, batch.qrSession().reference);
            assertEquals(7500, batch.qrSession().record.amount());
            assertEquals(1, batch.qrHistory().size());
            assertTrue(batch.pendingQrHistory().isEmpty());
            batch.dismissQr(old.reference); // a stale screen may not clear the new one
            assertEquals(fresh.reference, batch.qrSession().reference);
        }
    }
    @Test public void archivedQrCanBeCheckedWithoutRedisplayingOrRestoringIt() throws Exception {
        try (Host host = new Host("QR_OK")) {
            DbblQr.Session old = qr(host); BatchStore batch = BatchStore.get(context);
            batch.dismissQr(old.reference);
            assertTrue(OnlineHost.checkQrPayment(context, null, old.reference).approved);
            assertNull(batch.qrSession()); assertTrue(batch.pendingQrHistory().isEmpty());
            assertEquals(1, batch.qrHistory().size());
            assertEquals(Iso8583.ascii("QR" + old.reference), host.requests().get(0).get(63));
        }
    }
    @Test public void failedQrDismissalPreservesRecoveryData() throws Exception {
        try (Host host = new Host()) {
            DbblQr.Session old = qr(host); failNextBatchCommit = true;
            assertThrows(IllegalStateException.class, () -> BatchStore.get(context).dismissQr(old.reference));
            assertEquals(old.reference, BatchStore.get(context).qrSession().reference);
            assertTrue(BatchStore.get(context).pendingQrHistory().isEmpty());
        }
    }
    @Test public void closingPaidQrKeepsReceiptHistory() throws Exception {
        try (Host host = new Host("QR_OK")) {
            qr(host); assertTrue(OnlineHost.checkQrPayment(context, null).approved);
            BatchStore batch = BatchStore.get(context); batch.finishQr();
            assertNull(batch.qrSession()); assertTrue(batch.pendingQrHistory().isEmpty());
            assertEquals(1, batch.qrHistory().size()); host.requests();
        }
    }
    @Test public void menuTimeoutDefaultsToThirtyAndHonorsSavedParameter() throws Exception {
        assertEquals(30, config.idleTimeoutSec());
        config.setIdleTimeoutSec(45); clearStatic(TerminalConfig.class, "instance");
        config = TerminalConfig.get(context); assertEquals(45, config.idleTimeoutSec());
        config.setIdleTimeoutSec(0); assertEquals(0, config.idleTimeoutSec());
        config.setIdleTimeoutSec(30); assertEquals(30, config.idleTimeoutSec());
    }

    @Test public void qrTimeoutKeepsSavedPaymentWithoutInventingReversal() throws Exception {
        try (Host host = new Host((String) null)) {
            DbblQr.Session session = qr(host);
            OnlineHost.HostResult result = OnlineHost.checkQrPayment(context, null);
            assertFalse(result.approved); assertFalse(result.needsReversal);
            assertFalse(result.packetTrace().contains("Reversal queued"));
            assertEquals(session.reference, BatchStore.get(context).qrSession().reference);
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(Collections.singletonList("0200"), mtis(host.requests()));
        }
    }
    @Test public void qrRejectsMismatchedOrIncompleteApproval() throws Exception {
        for (String answer : Arrays.asList("00", "QR_BAD_AMOUNT", "QR_BAD_TID", "QR_BAD_STAN", "QR_BAD_NII")) {
            reset(); setup();
            try (Host host = new Host(answer)) {
                qr(host); assertFalse(OnlineHost.checkQrPayment(context, null).approved);
                assertFalse(BatchStore.get(context).qrSession().record.approved);
                assertEquals(0, BatchStore.get(context).count());
                assertFalse(BatchStore.get(context).hasPendingReversal()); host.requests();
            }
        }
    }
    @Test public void qrUploadAlsoFlushesOfflineTipsFirst() throws Exception {
        try (Host host = new Host("00", "QR_OK")) {
            qr(host); tipSale(); OnlineHost.queueTipAdjustment(context, "000377", 2000);
            OnlineHost.HostResult result = OnlineHost.checkQrPayment(context, null);
            assertTrue(result.approved); assertTrue(result.precedingTrace.contains("TIP ADJUST"));
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0220", "0200"), mtis(packets));
            assertEquals("6000080000", packets.get(0).tpdu); assertEquals("6000050000", packets.get(1).tpdu);
        }
    }
    @Test public void failedQrSaveDoesNotExposeAnUnjournalledCode() throws Exception {
        try (Host host = new Host()) {
            config.setQrSettings("009", "6000050000", "5411");
            failNextBatchCommit = true;
            assertThrows(IllegalStateException.class, () -> OnlineHost.createQrSale(context, 12000));
            assertNull(BatchStore.get(context).qrSession());
        }
    }
    @Test public void failedQrApprovalCommitCanRecoverWithoutDuplicateSale() throws Exception {
        try (Host host = new Host("QR_OK", "QR_OK")) {
            qr(host); failNextBatchCommit = true;
            assertFalse(OnlineHost.checkQrPayment(context, null).approved);
            assertFalse(BatchStore.get(context).qrSession().record.approved);
            assertEquals(0, BatchStore.get(context).count());
            clearStatic(BatchStore.class, "instance");
            assertTrue(OnlineHost.checkQrPayment(context, null).approved);
            assertEquals(1, BatchStore.get(context).qrHistory().size()); host.requests();
        }
    }

    @Test public void approvalCommitsBatchWithoutReversal() throws Exception {
        try (Host host = new Host("00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertTrue(r.approved); assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(1, BatchStore.get(context).count());
            assertEquals("0200", host.requests().get(0).mti);
        }
    }
    @Test public void declineDoesNotGenerateReversal() throws Exception {
        try (Host host = new Host("05")) {
            assertFalse(sale(host, OnlineHost.EntryMode.MAGSTRIPE).approved);
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(0, BatchStore.get(context).count());
            assertEquals(1, host.requests().size());
        }
    }

    private static final String PHONE_TRACK = "4000001234567892D30100001122331239999";
    private static final String PHONE_DE55 = "840A" + KonaMoney.AID;

    private OnlineHost.HostResult phoneTransaction(String type, String pin, OnlineHost.EntryMode entry) {
        return OnlineHost.transaction(context, type, "1.00", "4000001234567892", pin,
                PHONE_TRACK, "3010", PHONE_DE55, entry, "00", null);
    }

    @Test public void nexusPayPhoneUses910AcrossFinancialTypesWithAndWithoutPin() throws Exception {
        for (String type : Arrays.asList("SALE", "CASH_WITHDRAW", "SALE_AND_CASH", "REFUND",
                "PREAUTH", "BALANCE", "OFFLINE", "OFFLINE_REFUND", "ADJUST", "SALE_COMPLETE")) {
            for (String pin : Arrays.asList(null, "1122334455667788")) {
                try (Host host = new Host("00")) {
                    config.setHostPort(host.server.getLocalPort());
                    // Generic kernel path must also resolve AID/HCE data before packing.
                    assertTrue(type, phoneTransaction(type, pin, OnlineHost.EntryMode.CLESS_ICC).approved);
                    Iso8583 request = host.requests().get(0);
                    assertEquals(type, "910", request.get(22));
                    assertEquals(pin != null, request.has(52));
                }
            }
        }
    }

    @Test public void nexusPayModeIsStoredAndRepeatedByTimeoutReversal() throws Exception {
        try (Host host = new Host(null, "00")) {
            config.setHostPort(host.server.getLocalPort());
            OnlineHost.EntryMode entry = OnlineHost.entryModeFor(4, false, KonaMoney.AID, PHONE_TRACK);
            assertTrue(phoneTransaction("SALE", null, entry).reversalAcknowledged);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0200", "0400"), mtis(packets));
            assertEquals("910", packets.get(0).get(22));
            assertEquals("910", packets.get(1).get(22));
        }
        try (Host host = new Host("00")) {
            config.setHostPort(host.server.getLocalPort());
            assertTrue(phoneTransaction("SALE", null, OnlineHost.EntryMode.NEXUSPAY_PHONE_NFC).approved);
            clearStatic(BatchStore.class, "instance");
            assertEquals("910", BatchStore.get(context).all().get(0).entryMode);
        }
    }

    @Test public void nexusPaySavedModeSurvivesVoidCompletionAndTipUpload() throws Exception {
        for (String type : Arrays.asList("VOID", "PREAUTH_VOID", "SALE_COMPLETE", "BATCH", "TIP")) {
            reset(); setup();
            try (Host host = new Host("00")) {
                config.setHostPort(host.server.getLocalPort());
                TxnRecord original = tipSale();
                original.entryMode = "910";
                if (type.equals("PREAUTH_VOID") || type.equals("SALE_COMPLETE")) original.type = "PREAUTH";
                BatchStore.get(context).replaceAll(Collections.singletonList(original));
                clearStatic(BatchStore.class, "instance");
                switch (type) {
                    case "VOID": OnlineHost.voidSale(context, original, null, null); break;
                    case "PREAUTH_VOID": OnlineHost.preAuthVoid(context, original, null); break;
                    case "SALE_COMPLETE": OnlineHost.saleComplete(context, original, "100.00", null); break;
                    case "BATCH": OnlineHost.batchUpload(context, original, null); break;
                    default:
                        OnlineHost.queueTipAdjustment(context, original.invoice, 2000);
                        assertEquals("", OnlineHost.flushPendingTips(context, null));
                }
                assertEquals(type, "910", host.requests().get(0).get(22));
            }
        }
    }

    @Test public void nexusPayDetectionKeepsOtherPresentmentModes() throws Exception {
        for (OnlineHost.EntryMode entry : Arrays.asList(OnlineHost.EntryMode.ICC,
                OnlineHost.EntryMode.QR, OnlineHost.EntryMode.FALLBACK)) {
            try (Host host = new Host("05")) {
                config.setHostPort(host.server.getLocalPort());
                phoneTransaction("SALE", "1122334455667788", entry);
                String expected = entry == OnlineHost.EntryMode.ICC ? "051"
                        : entry == OnlineHost.EntryMode.QR ? "031" : "801";
                assertEquals(expected, host.requests().get(0).get(22));
            }
        }
        for (String track : Arrays.asList(PHONE_TRACK.replace("112233", "FFFFFF"),
                "4111111111111111D30102010000000000000")) {
            try (Host host = new Host("00")) {
                config.setHostPort(host.server.getLocalPort());
                String aidData = track.startsWith("4000") ? PHONE_DE55 : "8407A0000000031010";
                OnlineHost.transaction(context, "SALE", "1.00", track.substring(0, 16), null,
                        track, "3010", aidData, OnlineHost.EntryMode.CLESS_ICC, "00", null);
                assertEquals("071", host.requests().get(0).get(22));
            }
        }
    }

    @Test public void fallbackSaleUsesStripeTrackAndFallbackEntryMode() throws Exception {
        try (Host host = new Host("00")) {
            assertTrue(sale(host, OnlineHost.EntryMode.FALLBACK).approved);
            Iso8583 packet = host.requests().get(0);
            assertTrue(packet.get(22).startsWith("80"));
            assertTrue(packet.has(35));
            assertFalse(packet.has(55));
            assertFalse(BatchStore.get(context).hasPendingReversal());
        }
    }
    @Test public void lostReplySends0400AndClearsOnlyAfter0410Approval() throws Exception {
        try (Host host = new Host(null, "00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0200", "0400"), mtis(packets));
            assertEquals(packets.get(0).get(11), packets.get(1).get(11));
            assertEquals(packets.get(0).get(4), packets.get(1).get(4));
            assertEquals("98", packets.get(1).get(39));
            assertTrue(r.reversalAcknowledged);
            assertTrue(r.packetTrace().contains("0410"));
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(0, BatchStore.get(context).count());
        }
    }
    @Test public void mismatchedStanResponseRequiresReversal() throws Exception {
        try (Host host = new Host("BAD_STAN", "00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertFalse(r.approved);
            assertEquals("A0", host.requests().get(1).get(39));
            assertTrue(r.reversalAcknowledged);
        }
    }
    @Test public void unacknowledgedReversalSurvivesLimitAndBlocksNextSale() throws Exception {
        try (Host host = new Host(null, "05", "05")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertFalse(r.reversalAcknowledged);
            assertTrue(BatchStore.get(context).hasPendingReversal());
            OnlineHost.HostResult next = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertFalse(next.requestSent);
            assertTrue(next.error.contains("Previous reversal pending"));
            assertEquals(Arrays.asList("0200", "0400", "0400"), mtis(host.requests()));
            assertTrue(BatchStore.get(context).hasPendingReversal());
        }
    }
    @Test public void chipApprovalIsNotSettledUntilCardCompletes() throws Exception {
        try (Host host = new Host("00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.ICC);
            assertTrue(r.awaitingCardCompletion);
            assertTrue(BatchStore.get(context).hasPendingReversal());
            assertEquals(0, BatchStore.get(context).count());
            OnlineHost.completeCardApproval(context, r);
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(1, BatchStore.get(context).count());
            host.requests();
        }
    }
    @Test public void chipDeclineAfterHostApprovalSendsReversal() throws Exception {
        try (Host host = new Host("00", "00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.ICC);
            OnlineHost.reverseCardDecline(context, r, null);
            assertTrue(r.reversalAcknowledged);
            assertEquals(0, BatchStore.get(context).count());
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(Arrays.asList("0200", "0400"), mtis(host.requests()));
        }
    }
    @Test public void restartDuringChipCompletionRecoversPersistedTransaction() throws Exception {
        try (Host host = new Host("00", "00")) {
            sale(host, OnlineHost.EntryMode.ICC);
            clearStatic(OnlineHost.class, "sCardCompletionPending");
            clearStatic(BatchStore.class, "instance");
            OnlineHost.HostResult recovery = OnlineHost.flushPendingReversal(context, null);
            assertTrue(recovery.approved);
            assertFalse(BatchStore.get(context).hasPendingReversal());
            assertEquals(Arrays.asList("0200", "0400"), mtis(host.requests()));
        }
    }
    @Test public void refusedConnectionDoesNotLeaveReversalQueued() throws Exception {
        try (ServerSocket port = new ServerSocket(0)) { config.setHostPort(port.getLocalPort()); }
        OnlineHost.HostResult r = transact(OnlineHost.EntryMode.MAGSTRIPE);
        assertFalse(r.requestSent);
        assertFalse(BatchStore.get(context).hasPendingReversal());
    }

    @Test public void wrongMtiOnReversalCannotClearPendingRecord() throws Exception {
        try (Host host = new Host(null, "BAD_MTI")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertFalse(r.reversalAcknowledged);
            assertTrue(BatchStore.get(context).hasPendingReversal());
            host.requests();
        }
    }

    @Test public void wrongStanOnReversalCannotClearPendingRecord() throws Exception {
        try (Host host = new Host(null, "BAD_STAN")) {
            assertFalse(sale(host, OnlineHost.EntryMode.MAGSTRIPE).reversalAcknowledged);
            assertTrue(BatchStore.get(context).hasPendingReversal());
            host.requests();
        }
    }

    @Test public void preauthReversalPreservesExpirySentInOriginalRequest() throws Exception {
        try (Host host = new Host(null, "00")) {
            config.setHostPort(host.server.getLocalPort());
            OnlineHost.transaction(context, "PREAUTH", "1.00", "4111111111111111", null,
                    "4111111111111111=30102010000000000000", "3010", "",
                    OnlineHost.EntryMode.MAGSTRIPE, "00", null);
            List<Iso8583> packets = host.requests();
            assertEquals(Arrays.asList("0100", "0400"), mtis(packets));
            assertEquals("3010", packets.get(0).get(14));
            assertEquals(packets.get(0).get(14), packets.get(1).get(14));
        }
    }

    @Test public void backgroundRetryUsesOriginalTransactionAndClearsAfterAcknowledgment() throws Exception {
        config.setReversalMaxTries(2);
        try (Host host = new Host(null, null, "00")) {
            OnlineHost.HostResult r = sale(host, OnlineHost.EntryMode.MAGSTRIPE);
            assertFalse(r.reversalAcknowledged);
            assertTrue(BatchStore.get(context).hasPendingReversal());
            List<Iso8583> packets = host.task.get(40, TimeUnit.SECONDS);
            // Host completion can precede client acknowledgment processing.
            Field f = OnlineHost.class.getDeclaredField("sReversalRetry"); f.setAccessible(true);
            Thread worker = (Thread) f.get(null); worker.join(3000);
            assertFalse(worker.isAlive());
            assertEquals(Arrays.asList("0200", "0400", "0400"), mtis(packets));
            assertEquals(packets.get(1).get(11), packets.get(2).get(11));
            assertEquals(packets.get(1).get(4), packets.get(2).get(4));
            assertFalse(BatchStore.get(context).hasPendingReversal());
        }
    }

    private OnlineHost.HostResult sale(Host host, OnlineHost.EntryMode entry) {
        config.setHostPort(host.server.getLocalPort());
        return transact(entry);
    }
    private OnlineHost.HostResult transact(OnlineHost.EntryMode entry) {
        return OnlineHost.transaction(context, "SALE", "1.00", "4111111111111111", null,
                "4111111111111111=30102010000000000000", "3010", "", entry, "00", null);
    }
    private static List<String> mtis(List<Iso8583> packets) {
        List<String> result = new ArrayList<>();
        for (Iso8583 p : packets) result.add(p.mti);
        return result;
    }
    private static class Host implements AutoCloseable {
        final ServerSocket server;
        final FutureTask<List<Iso8583>> task;
        Host(String... replies) throws Exception {
            server = new ServerSocket(0, 10, java.net.InetAddress.getLoopbackAddress());
            server.setSoTimeout(40000);
            task = new FutureTask<>(() -> {
                List<Iso8583> requests = new ArrayList<>();
                for (String rc : replies) try (Socket peer = server.accept()) {
                    peer.setSoTimeout(3000);
                    DataInputStream in = new DataInputStream(peer.getInputStream());
                    byte[] raw = new byte[in.readUnsignedShort()]; in.readFully(raw);
                    Iso8583 request = Iso8583.unpack(raw); requests.add(request);
                    if (rc == null) continue; // Received request, lost response.
                    TreeMap<Integer,String> de = new TreeMap<>();
                    de.put(11, "BAD_STAN".equals(rc) ? "999999" : request.get(11));
                    de.put(39, rc.startsWith("BAD_") || rc.startsWith("QR_") ? "00" : rc);
                    if (rc.startsWith("QR_")) {
                        de.put(2, "4111111111111111"); de.put(3, "000000");
                        de.put(4, "QR_BAD_AMOUNT".equals(rc) ? "1" : request.get(4));
                        de.put(11, "QR_BAD_STAN".equals(rc) ? "999999" : request.get(11));
                        de.put(12, "123456"); de.put(13, "0927");
                        de.put(24, "QR_BAD_NII".equals(rc) ? "001" : request.get(24));
                        de.put(37, "123456789012"); de.put(38, "123456");
                        de.put(41, "QR_BAD_TID".equals(rc) ? "99999999" : request.get(41));
                    }
                    if ("BAD_AMOUNT".equals(rc)) de.put(4, "999999");
                    if ("BAD_TID".equals(rc)) de.put(41, "99999999");
                    byte[] response = Iso8583.pack("6000000008",
                            "BAD_MTI".equals(rc) ? "0210"
                                    : String.format(Locale.US, "%04d", Integer.parseInt(request.mti) + 10), de);
                    DataOutputStream out = new DataOutputStream(peer.getOutputStream());
                    out.writeShort(response.length); out.write(response); out.flush();
                }
                return requests;
            });
            Thread thread = new Thread(task, "TestHostLoopback"); thread.setDaemon(true); thread.start();
        }
        List<Iso8583> requests() throws Exception { return task.get(5, TimeUnit.SECONDS); }
        public void close() throws Exception { server.close(); }
    }
    private static void clearStatic(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(null, null);
    }
    private static void reset() throws Exception {
        failNextBatchCommit = false;
        failNextSettingsCommit = false;
        synchronized (OnlineHost.class) {
            Field f = OnlineHost.class.getDeclaredField("sReversalRetry"); f.setAccessible(true);
            Thread retry = (Thread) f.get(null); if (retry != null) retry.interrupt(); f.set(null, null);
            clearStatic(OnlineHost.class, "sRetryRecord");
            clearStatic(OnlineHost.class, "sCardCompletionPending");
            clearStatic(TerminalConfig.class, "instance"); clearStatic(BatchStore.class, "instance");
            clearStatic(ReportStore.class, "instance");
        }
    }
    /** Explicit in-memory preferences; Android methods are not used as persistence in this test. */
    private static SharedPreferences memoryPreferences(String storeName) {
        Map<String,Object> data = new HashMap<>();
        return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class[]{SharedPreferences.class}, (p, method, args) -> {
                    String name = method.getName();
                    if (name.equals("edit")) {
                        Map<String,Object> changes = new HashMap<>(); Set<String> removed = new HashSet<>();
                        boolean[] clear = {false};
                        return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                new Class[]{SharedPreferences.Editor.class}, (editor, m, a) -> {
                                    String n = m.getName();
                                    if (n.startsWith("put")) changes.put((String)a[0], a[1]);
                                    else if (n.equals("remove")) removed.add((String)a[0]);
                                    else if (n.equals("clear")) clear[0] = true;
                                    else if (n.equals("commit") || n.equals("apply")) {
                                        if (clear[0]) data.clear();
                                        for (String key : removed) data.remove(key);
                                        data.putAll(changes);
                                        if (n.equals("commit") && "batch_store".equals(storeName) && failNextBatchCommit) {
                                            failNextBatchCommit = false; return false;
                                        }
                                        if (n.equals("commit") && "terminal_config".equals(storeName) && failNextSettingsCommit) {
                                            failNextSettingsCommit = false; return false;
                                        }
                                        return n.equals("commit") ? true : null;
                                    }
                                    return editor;
                                });
                    }
                    if (name.equals("contains")) return data.containsKey(args[0]);
                    if (name.equals("getAll")) return new HashMap<>(data);
                    if (name.startsWith("get")) return data.getOrDefault(args[0], args[1]);
                    return null;
                });
    }
}

