package com.hjq.demo.utils;

import android.content.Context;
import com.hjq.demo.ans8583.*;
import java.util.*;

/** QR-only receipts and reports: no card signature, expiry, EMV or batch fields. */
public final class QrSlip {
    private QrSlip() { }
    public static List<Slip.Line> receipt(Context context, TxnRecord record, String copy, boolean reprint) {
        if (!record.approved) throw new IllegalArgumentException("Only confirmed QR payments have receipts");
        TerminalConfig tc = TerminalConfig.get(context);
        List<Slip.Line> out = Slip.list();
        TxnSlip.letterhead(out, tc);
        out.add(Slip.centre("QR SALE", Slip.BIG));
        out.add(Slip.centre("PAYMENT CONFIRMED", Slip.MID));
        out.add(Slip.rule());
        out.add(Slip.pair("DATE " + date(record), "TIME " + time(record)));
        out.add(Slip.text("MID: " + tc.mid())); out.add(Slip.text("TID: " + tc.tid()));
        out.add(Slip.pair("INVOICE " + record.invoice, "TRACE " + record.stan));
        out.add(Slip.text("RRN: " + record.rrn));
        out.add(Slip.text("APPROVAL: " + record.authCode));
        out.add(Slip.rule());
        out.add(Slip.centre("TK " + TipAmounts.major(record.amount()), Slip.BIG));
        out.add(Slip.centre("Paid with NexusPay QR", Slip.SMALL));
        out.add(Slip.rule());
        out.add(Slip.centre(copy, Slip.SMALL));
        if (reprint) out.add(Slip.centre("REPRINT COPY", Slip.SMALL));
        tail(out); return out;
    }
    public static List<Slip.Line> report(Context context, List<TxnRecord> paid, List<DbblQr.Session> pending) {
        List<Slip.Line> out = Slip.list();
        TxnSlip.header(context, out);
        out.add(Slip.centre("QR SALE DETAIL REPORT", Slip.MID));
        out.add(Slip.rule());
        long total = 0; int count = 0;
        for (TxnRecord r : paid) {
            if (!r.approved) continue;
            total += r.amount(); count++;
            out.add(Slip.pair("PAID  " + r.invoice, "TK " + TipAmounts.major(r.amount())));
            out.add(Slip.pair(date(r), time(r)));
            out.add(Slip.text("RRN " + r.rrn)); out.add(Slip.text("AUTH " + r.authCode));
            out.add(Slip.rule());
        }
        out.add(Slip.pair("PAID COUNT " + count, "TK " + TipAmounts.major(total)));
        if (!pending.isEmpty()) {
            out.add(Slip.blank()); out.add(Slip.centre("UNCONFIRMED - NOT SALES", Slip.MID));
            for (DbblQr.Session s : pending) {
                out.add(Slip.pair("INV " + s.record.invoice, "TK " + TipAmounts.major(s.record.amount())));
                out.add(Slip.pair(date(s.record), time(s.record)));
            }
            out.add(Slip.text("UNCONFIRMED COUNT " + pending.size()));
            out.add(Slip.text("Excluded from paid total"));
        }
        out.add(Slip.centre("END OF QR REPORT", Slip.SMALL)); tail(out); return out;
    }
    public static String date(TxnRecord r) {
        String s = r.date == null ? "" : r.date;
        return s.length() == 4 ? s.substring(2) + "/" + s.substring(0, 2) : s;
    }
    public static String time(TxnRecord r) {
        String s = r.time == null ? "" : r.time;
        return s.length() == 6 ? s.substring(0, 2) + ":" + s.substring(2, 4) + ":" + s.substring(4) : s;
    }
    private static void tail(List<Slip.Line> out) {
        out.add(Slip.centre("THANK YOU FOR USING DBBL", Slip.SMALL));
        out.add(Slip.centre("APP " + com.hjq.demo.BuildConfig.VERSION_NAME, Slip.SMALL));
        out.add(Slip.blank()); out.add(Slip.blank());
    }
}
