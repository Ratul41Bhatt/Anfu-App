package com.hjq.demo.utils;

import android.content.Context;

import com.hjq.demo.ans8583.Emv55;
import com.hjq.demo.ans8583.TerminalConfig;
import com.hjq.demo.ans8583.TxnRecord;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The transaction slip - the one place it is composed.
 *
 * <p>There were two. {@code FindCardActivity} built the slip it prints at the
 * end of a transaction, and {@code ReportsActivity} built a second, shorter
 * one for reprints. They drifted, as two copies of a layout always do: the
 * reprint lost the merchant's address, then printed the real expiry where the
 * original printed {@code **}{@code /}{@code **}, then carried an APPROVED
 * banner the original had dropped, and it never had the EMV block, the
 * signature line or the cardholder agreement at all. Each was found and fixed
 * separately, which is the shape of a problem that will keep coming back.</p>
 *
 * <p>So: one builder, and a {@code reprint} flag - which is how the PAX
 * reference does it too. {@code PrinterUtils.printTransDetail} passes
 * {@code false} and {@code reprintTrans} passes {@code true} to the same
 * {@code ReceiptTransDetail}, and the flag adds one line. A reprint here is
 * the same document, with {@code REPRINT COPY} at the bottom.</p>
 *
 * <p>Emits {@link Slip.Line}. {@code FindCardActivity} has its own line class
 * with identical fields and its own renderer, so it converts; the alternative
 * was moving that renderer as well, and the slip layout is the part that was
 * actually broken.</p>
 */
public final class TxnSlip {

    /** Printed when the terminal has no TVR of its own to show. */
    private static final String TVR_NONE = "0000000000";
    private static final String TSI_NONE = "0000";

    /** The rule the cardholder signs above. Centred, not full width. */
    private static final String SIGN_RULE = "------------------------";
    /** Card number text size: a step above ordinary slip text (21). */
    public static final int CARD_NO_SIZE = 26;
    /** The signature line a sale completion carries under its amount. */
    private static final String SIGN_DOTS = "SIGN:..............................";

    private TxnSlip() { }

    /**
     * Everything the slip prints, and nothing else.
     *
     * <p>Plain public fields: this is a form to be filled in by two callers
     * that hold their data in completely different shapes - one has a live
     * EMV kernel and a host response, the other has a row out of the batch.
     * Anything either of them cannot answer is left at its default and the
     * corresponding line is skipped, which is the same rule the live slip
     * already used for an approval code the host did not send.</p>
     */
    public static final class Data {
        public String date = "", time = "";
        public String mid = "", tid = "", batch = "", invoice = "", stan = "";
        public boolean hostOnline;

        public String title = "SALE";
        public String emiPlan = "";
        public int tenure;

        public String cardMasked = "", entry = "", issuer = "", onUs = "";
        public String aid = "", appl = "";

        public String origInvoice = "", origRrn = "", origAuth = "", origDateTime = "";

        public String apprCode = "", rref = "", respCode = "";

        public String tvr = "", tsi = "", acLabel = "TC", arqc = "", atc = "";
        /**
         * Kept and stored, but no longer printed - the reference slip carries
         * neither, and they answer a question only a certification engineer
         * asks. Still on the record, so the packet trace and any future
         * diagnostic report can show them.
         */
        public boolean issuerAuth, scriptRun;

        /** Major units, unsigned - "1.00". The sign is the slip's business. */
        public String amount = "", installment = "", baseAmount = "", tipAmount = "";
        public boolean isVoid;

        /**
         * A pre-authorisation - a hold, not a charge.
         *
         * <p>Kept apart from {@link #isVoid} because they suppress the same
         * rows for different reasons, and because a completion is neither.</p>
         */
        public boolean isPreAuth;

        /**
         * A sale completion. Prints AMOUNT with no TIP / TOTAL under it and a
         * signature line in their place: the figure was settled on the
         * completion screen and nothing can be added to it afterwards.
         */
        public boolean isCompletion;

        public boolean approved, reversed;
        public String cvm = "", respText = "", cardholder = "";
        /** Remaining prepaid balance, already formatted. "" prints no line. */
        public String balance = "";

        /** Only a live transaction can decline; a reprint reads out of the batch. */
        public boolean hostApprovedCardRefused, cardDeclineReversed, cardDeclined;
    }

    // ------------------------------------------------------------ from a record

    /**
     * A stored transaction, as the slip that printed it.
     *
     * <p>Takes the receipt snapshot the transaction saved - cardholder name,
     * TSI, CVM verdict - and re-derives the rest: the cryptogram, TVR, ATC and
     * AID come back out of the DE55 that was actually sent, the scheme name
     * and on-us status out of the card range table. Older batch entries have
     * no snapshot, so those fields come back empty and their lines are
     * skipped, exactly as they are for a swiped card with no chip data.</p>
     */
    public static Data of(Context ctx, TxnRecord r) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        Data d = new Data();
        String pan = DetailReport.panOf(r);

        d.date = dateOf(r);
        d.time = timeOf(r);
        d.mid = tc.mid();
        d.tid = tc.tid();
        d.batch = tc.batchNo();
        d.invoice = r.invoice;
        d.stan = r.stan;
        d.hostOnline = r.hostOnline;

        d.title = titleOf(r);
        d.emiPlan = r.isEmi() ? emiPlanName(r.emiProduct) : "";
        d.tenure = r.isEmi() ? r.tenure : 0;

        d.cardMasked = CardMask.maskGrouped(pan, tc.cardMask());
        d.entry = DetailReport.entryLabel(r.entryMode);
        d.issuer = r.issuer != null && !r.issuer.isEmpty()
                ? r.issuer : CardRangeTable.nameFor(ctx, pan);
        d.onUs = CardRangeTable.onUsLabel(ctx, pan);

        Map<String, String> icc = Emv55.parse(r.completedIccHex.isEmpty() ? r.iccHex : r.completedIccHex);
        d.aid = nz(icc.get("84"));
        d.appl = r.appLabel;
        d.arqc = nz(icc.get("9F26"));
        d.tvr = nz(icc.get("95"));
        d.atc = nz(icc.get("9F36"));
        d.tsi = r.tsi;
        d.acLabel = acLabel(nz(icc.get("9F27")));
        d.cvm = r.cvm;
        d.cardholder = r.cardholder;
        d.issuerAuth = r.issuerAuth;
        d.scriptRun = r.scriptRun;

        d.origInvoice = r.origInvoice;
        d.origRrn = r.origRrn;
        d.origAuth = r.origAuth;
        d.origDateTime = r.origDateTime;

        d.apprCode = r.authCode;
        d.rref = r.rrn;
        d.respCode = r.respCode;

        d.balance = r.balance;
        d.amount = Slip.money(Math.abs(r.amount()));
        d.baseAmount = Slip.money(Math.abs(r.baseAmount()));
        d.tipAmount = r.baseAmountMinor.isEmpty() && !r.tipAdjusted ? "" : Slip.money(r.tip());
        d.installment = r.installment() > 0 ? Slip.money(r.installment()) : "";
        // Only when this record IS a void. r.voided means a later void
        // cancelled this sale - which does not change what the sale's own slip
        // said, and a reprint that flipped the sign and dropped the TOTAL row
        // would be a different document from the one the cardholder signed.
        // The cancellation has its own slip, and the detail report marks the
        // sale with a star.
        d.isVoid = r.isVoid();
        d.isPreAuth = r.type != null && "PREAUTH".equals(r.type);
        d.isCompletion = r.type != null && "SALE_COMPLETE".equals(r.type);

        // A batch entry is a transaction that happened. The decline and
        // reversal banners belong to the live flow, which has a host response
        // to read them off; nothing that reaches a reprint carries them.
        d.approved = r.approved;
        d.reversed = false;
        return d;
    }

    // ------------------------------------------------------------------ body

    /**
     * The whole slip: letterhead, transaction, verdict, signature, copy label.
     *
     * @param copyLabel MERCHANT COPY / CUSTOMER COPY, as it prints in the
     *                  {@code xxxxx ... xxxxx} line
     * @param reprint   true to add the reprint line at the foot. The rest of
     *                  the document is byte for byte what the original was -
     *                  that is the whole point of the flag
     */
    public static List<Slip.Line> body(Context ctx, Data d, String copyLabel, boolean reprint) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        List<Slip.Line> o = Slip.list();

        // ---- who, when, which batch
        //
        // The transaction stamps its own date and time rather than taking the
        // clock now, so a reprint reproduces when the sale happened, not when
        // the copy was printed. Otherwise this is the shared header.
        letterhead(o, tc);
        o.add(Slip.pair("DATE:" + d.date, "TIME:" + d.time));
        o.add(Slip.pair("MID : " + d.mid, "TID : " + d.tid));
        o.add(Slip.pair("BATCH NO : " + d.batch,
                isBlank(d.stan) ? "" : "TRACE NO : " + d.stan));
        o.add(Slip.text("INVOICE NO: " + d.invoice));
        if (!d.hostOnline) {
            o.add(Slip.text("OFFLINE"));
        }

        // ---- what it was
        o.add(Slip.centre(isBlank(d.emiPlan) ? d.title : d.title + " : " + d.emiPlan, Slip.BIG));
        if (d.tenure > 0) {
            o.add(Slip.centre("TENURE : " + d.tenure + " MONTHS", Slip.MID));
        }

        // ---- the card
        //
        // Number and read method share a line, the way the reference slip
        // prints them - the number needs no label, and how it was read is the
        // first thing anyone checks a disputed slip for.
        // Card number, a space, then how it was read - "4466 17** **** 5908  CHIP"
        // on ONE line, bold and a size up from ordinary text. The renderer
        // shrinks the line only if a long PAN would not fit the paper.
        o.add(new Slip.Line(isBlank(d.entry) ? d.cardMasked : d.cardMasked + "  " + d.entry,
                "", CARD_NO_SIZE, Slip.BOLD));
        o.add(Slip.pair("CARD TYPE : " + d.issuer, "EXP DATE : **/**"));

        // ---- the transaction a void cancelled
        //
        // Two lines, paired, where this used to be a heading and four rows.
        //
        // The reference terminal prints none of this, and it does not need to:
        // its void reuses the original sale's invoice number, so the INVOICE NO
        // at the top of the slip already identifies what was cancelled. Ours
        // allocates a fresh invoice for the void - which is what the batch and
        // the settlement totals are built on - so without these lines there is
        // nothing on the paper tying the void to the sale it reverses. That is
        // the one question a void slip exists to answer.
        if (!isBlank(d.origInvoice)) {
            o.add(Slip.pair("ORIG INV : " + d.origInvoice,
                    isBlank(d.origAuth) ? "" : "ORIG APPR : " + d.origAuth));
            if (!isBlank(d.origRrn) || !isBlank(d.origDateTime)) {
                o.add(Slip.pair(isBlank(d.origRrn) ? "" : "ORIG RRN : " + d.origRrn,
                        d.origDateTime));
            }
        }

        // ---- what the host returned
        if (!isBlank(d.apprCode)) {
            o.add(Slip.text("APPR CODE : " + d.apprCode));
        }
        if (!isBlank(d.rref)) {
            // Whether DBBL issued this card decides who settles the
            // transaction and the merchant cannot tell from anything else on
            // the paper, so it has to be here - but it does not deserve a line
            // of its own. The retrieval reference is the shortest row on the
            // slip and the two belong to the same question: who is carrying
            // this transaction, and under what reference.
            o.add(Slip.pair("RRN : " + d.rref, isBlank(d.onUs) ? "" : d.onUs));
        } else if (!isBlank(d.onUs)) {
            o.add(Slip.pair("", d.onUs));
        }
        // Only on a decline. On an approval the approval code above is the
        // evidence, and the reference slip carries no response code at all.
        if (!d.approved && !isBlank(d.respCode)) {
            o.add(Slip.text("RESP CODE : " + d.respCode));
        }

        // ---- EMV detail, chip and contactless only
        if (!isBlank(d.arqc) || !isBlank(d.tvr)) {
            if (!isBlank(d.arqc)) {
                // Cryptogram and ATC share a line. They are read together - the
                // ATC is what tells you which use of the card produced that
                // cryptogram.
                o.add(Slip.pair((isBlank(d.acLabel) ? "TC" : d.acLabel) + " : " + d.arqc,
                        isBlank(d.atc) ? "" : "ATC NO : " + d.atc));
            } else if (!isBlank(d.atc)) {
                o.add(Slip.text("ATC NO : " + d.atc));
            }
            if (!isBlank(d.aid)) {
                o.add(Slip.text("Application Identifier: " + d.aid));
            }
            if (!isBlank(d.appl)) {
                o.add(Slip.text("Application Name: " + d.appl));
            }
            o.add(Slip.pair("TVR : " + (isBlank(d.tvr) ? TVR_NONE : d.tvr),
                    "TSI : " + (isBlank(d.tsi) ? TSI_NONE : d.tsi)));
        } else if (!isBlank(d.aid)) {
            o.add(Slip.text("Application Identifier: " + d.aid));
        }

        // ---- the money
        //
        // Marker left-aligned so it starts at the same x on every row, figure
        // right-aligned so the decimal points line up. TIP prints with no
        // figure rather than being left out, because the block is a column the
        // merchant adds up by eye and a missing row breaks the reading.
        String cur = tc.currencySymbol();
        o.add(new Slip.Line(Slip.amountRow("AMOUNT", cur + ":", amount(d, d.isVoid || isBlank(d.baseAmount) ? d.amount : d.baseAmount)),
                "", Slip.MID, Slip.BOLD));
        // A void gets the amount and nothing else, as the reference prints it.
        // There is no tip to add to a cancellation and no subtotal to carry
        // down, so TIP and TOTAL would be a blank row and a repeat of the line
        // above - three lines to say what one already said.
        //
        // An instalment sale is the same case. The amount is the sum the plan
        // was written against, and it is fixed the moment the issuer approves
        // it: there is no tip to add afterwards and nothing to adjust it with,
        // since TIP ADJUST works on an open sale in the batch and an EMI is not
        // one. Printing an empty TIP line invites a merchant to write a figure
        // into it that the host will never see and the cardholder will never be
        // charged. The instalment line below is what belongs under the amount
        // on this slip.
        //
        // A pre-authorisation is the third. It is a hold, not a charge: the
        // tip is captured at completion, and nothing on this slip can be
        // adjusted afterwards, so an empty TIP line here invites a figure that
        // the completion will overwrite and the cardholder will never be
        // charged. TOTAL follows it out - with no tip it only repeats AMOUNT.
        boolean isEmi = d.tenure > 0 || !isBlank(d.emiPlan);
        if (!d.isVoid && !isEmi && !d.isPreAuth && !d.isCompletion) {
            o.add(new Slip.Line(Slip.amountRow("TIP", cur + ":", d.tipAmount),
                    "", Slip.MID, Slip.BOLD));
            o.add(new Slip.Line("", "-------------------", Slip.SMALL, 0));
            o.add(new Slip.Line(Slip.amountRow("TOTAL", cur + ":", amount(d, d.amount)),
                    "", Slip.MID, Slip.BOLD));
        }
        // No INSTALMENT line.
        //
        // It used to print the monthly figure under the amount. The terminal
        // is not the authority on it: the instalment the cardholder actually
        // pays is what the issuer's own EMI schedule works out, including
        // whatever processing fee and rounding the bank applies, and this
        // figure is the terminal's own arithmetic from the plan table. When
        // the two disagree the slip is the thing the cardholder holds, and it
        // is the one that is wrong. AMOUNT and TENURE are both facts of the
        // transaction; the monthly split is not, so it stays off the paper and
        // the bank's statement is left to state it.
        // A prepaid card's remaining balance. The KonaMoney spec 2.2 puts this
        // on the terminal and on the receipt for a reason: on a prepaid scheme
        // the slip is the only statement the cardholder gets, and "how much is
        // left" is the question they are actually asking.
        if (!isBlank(d.balance)) {
            o.add(new Slip.Line(Slip.amountRow("BALANCE", "", d.balance),
                    "", Slip.SMALL, Slip.BOLD));
        }
        // A sale completion has no TIP / TOTAL; its signature line sits right
        // under the amount instead.
        if (d.isCompletion) {
            o.add(Slip.blank());
            o.add(Slip.blank());
            o.add(new Slip.Line(SIGN_DOTS, "", Slip.MID, Slip.BOLD));
        }
        // One blank under the money, as the reference has it. The amount block
        // is the part of a receipt people actually read, and running the CVM
        // result straight onto the bottom of it loses the boundary.
        o.add(Slip.blank());

        // ---- verdict
        if (d.reversed) {
            o.add(Slip.centre("*** TRANSACTION REVERSED ***", Slip.MID));
            o.add(new Slip.Line("NO RESPONSE FROM HOST", "", Slip.SMALL, Slip.CENTER));
            o.add(Slip.centre("CARDHOLDER NOT CHARGED", Slip.SMALL));
        } else if (d.approved) {
            // No "APPROVED" banner. The reference slip carries none - the
            // approval code above is the evidence, and a second line saying so
            // only pushes the signature further down the paper.
            if ("NO CVM REQUIRED".equalsIgnoreCase(d.cvm == null ? "" : d.cvm.trim())) {
                // Not printed - an empty line keeps the spacing.
                o.add(Slip.blank());
            } else if (!isBlank(d.cvm)) {
                o.add(Slip.centre("(" + d.cvm + ")", Slip.SMALL));
            }
        } else {
            o.add(Slip.centre("*** DECLINED ***", Slip.MID));
            o.add(Slip.centre(d.respText, Slip.SMALL));
            if (d.hostApprovedCardRefused) {
                // ONLY when the host actually said 00. Without this the
                // merchant cannot tell an issuer decline from a card decline,
                // and cannot tell whether the cardholder was charged.
                o.add(new Slip.Line("HOST APPROVED - CARD REFUSED", "",
                        Slip.SMALL, Slip.CENTER));
                o.add(Slip.centre(d.cardDeclineReversed
                        ? "REVERSED - CARDHOLDER NOT CHARGED"
                        : "REVERSAL QUEUED - VERIFY WITH BANK", Slip.SMALL));
            } else if (d.cardDeclined) {
                o.add(new Slip.Line("CARD REFUSED AT GAC2", "", Slip.SMALL, Slip.CENTER));
                o.add(Slip.centre("CARDHOLDER NOT CHARGED", Slip.SMALL));
            }
        }

        // ---- signature, only on an approval
        //
        // A declined slip has nothing to sign for, and a reversal says in as
        // many words that the cardholder was not charged.
        if (d.approved && !d.reversed) {
            if (!d.isCompletion) {
                o.add(Slip.centre(SIGN_RULE, Slip.SMALL));
            }
            if (!isBlank(d.cardholder)) {
                o.add(new Slip.Line(d.cardholder, "", Slip.SMALL, Slip.CENTER));
            }
            o.add(new Slip.Line("I AGREE TO PAY AS PER CARD ISSUER AGREEMENT", "",
                    Slip.SMALL, Slip.CENTER));
        }
        o.add(new Slip.Line("THANK YOU FOR USING DBBL POS NETWORK", "",
                Slip.SMALL, Slip.CENTER));
        o.add(Slip.centre("*** " + copyLabel + " ***", Slip.SMALL));
        // The reprint mark goes at the foot, so everything above it is the
        // original document unchanged.
        if (reprint) {
            o.add(Slip.centre("*** REPRINT COPY ***", Slip.SMALL));
        }
        // No trailing blanks. The renderer feeds blank paper after the last
        // line so it clears the tear bar - two blank lines on top of that was
        // the same clearance paid for twice.
        return o;
    }

    /**
     * The block every printed document starts with.
     *
     * <p>Letterhead, then the date and time, then the merchant and terminal
     * identifiers - the same four rows in the same shape whether what follows
     * is a sale, a settlement, a detail report or a logon. They were written
     * out separately in five places, with four different date formats between
     * them ({@code yyyy-MM-dd}, {@code DATE / TIME} as one field, and two
     * label spellings), which is how a merchant ends up unsure whether two
     * pieces of paper came off the same terminal.</p>
     *
     * @param batch printed as BATCH NO when non-empty
     * @param trace printed as TRACE NO beside it when non-empty
     */
    public static void header(Context ctx, List<Slip.Line> o, String batch, String trace) {
        TerminalConfig tc = TerminalConfig.get(ctx);
        letterhead(o, tc);
        o.add(Slip.pair("DATE:" + todayForSlip(), "TIME:" + nowForSlip()));
        o.add(Slip.pair("MID : " + tc.mid(), "TID : " + tc.tid()));
        if (!isBlank(batch) || !isBlank(trace)) {
            o.add(Slip.pair(isBlank(batch) ? "" : "BATCH NO : " + batch,
                    isBlank(trace) ? "" : "TRACE NO : " + trace));
        }
    }

    /** Header with the terminal's current batch number. */
    public static void header(Context ctx, List<Slip.Line> o) {
        header(ctx, o, TerminalConfig.get(ctx).batchNo(), "");
    }

    /**
     * A short slip: the standard header, then a headline and whatever explains
     * it, centred.
     *
     * <p>For the things that are an outcome rather than a transaction - a
     * logon, a transaction that never completed. They used to print a raw
     * packet dump with a one-line verdict at the bottom, which told the
     * merchant nothing and told anyone who picked the paper up more than they
     * should see.</p>
     *
     * <p>No batch number in the header. A notice is not an entry in the batch -
     * a logon does not belong to one and a failed transaction never joined
     * it - so printing one invites the reader to go looking for something that
     * is not there.</p>
     *
     * @param headline the outcome, printed large: LOGON SUCCESSFUL,
     *                 TRANSACTION FAILED. It is the whole point of the slip and
     *                 the only thing most people will read
     * @param lines    centred and bold under it; blanks are skipped
     */
    public static List<Slip.Line> notice(Context ctx, String headline, String... lines) {
        List<Slip.Line> o = Slip.list();
        header(ctx, o, "", "");
        o.add(Slip.blank());
        if (!isBlank(headline)) {
            o.add(Slip.centre(headline, Slip.BIG));
        }
        for (String s : lines) {
            if (!isBlank(s)) {
                o.add(Slip.centre(s, Slip.MID));
            }
        }
        o.add(Slip.blank());
        return o;
    }

    /**
     * Logo block, merchant name and address lines 1 to 4.
     *
     * <p>The name and lines come from {@link TerminalConfig}, which prefers
     * whatever the host pushed in the DE62 logon tail over the operator's
     * entry. Empty lines are skipped rather than printed blank, so a merchant
     * with a two-line address gets a two-line header.</p>
     */
    public static void letterhead(List<Slip.Line> o, TerminalConfig tc) {
        if (tc.printBankLogo() || tc.printMerchantLogo()) {
            // A blank line above the logo. The head starts printing as soon as
            // the paper moves, so without it the artwork begins in the first
            // millimetre of the slip and reads as though it has been cropped.
            o.add(Slip.blank());
            o.add(new Slip.Line("", "", 0, Slip.LOGO));
        }
        // Skipped when it is blank. On a terminal whose logo already carries
        // the bank's name, printing it again underneath is just a second copy
        // of the same words - clearing Merchant Name in Parameters now removes
        // the line rather than restoring a default.
        String name = tc.merchantName();
        if (name != null && !name.trim().isEmpty()) {
            // Slip.centre already carries BOLD | CENTER.
            o.add(Slip.centre(name, Slip.MERCHANT));
        }
        for (int i = 1; i <= TerminalConfig.ADDR_LINES; i++) {
            String addr = tc.merchantAddr(i);
            if (addr != null && !addr.trim().isEmpty()) {
                o.add(Slip.centre(addr, Slip.SMALL));
            }
        }
    }

    // --------------------------------------------------------------- helpers

    /**
     * The figure in an amount row: "11.11", or "- 11.11" on a void.
     *
     * <p>No currency marker - that is the row's middle column, so it can be
     * left-aligned under itself down the block while the figures right-align
     * on their decimal points. The sign leads the whole figure rather than
     * sitting inside it, which is what a merchant totalling a day's paper
     * scans down the right-hand edge for.</p>
     *
     * <p>Printed text only. DE4 is always positive - the transaction code is
     * what tells the host this reverses an earlier sale.</p>
     */
    private static String amount(Data d, String value) {
        String a = value == null ? "" : value.trim();
        if (a.startsWith("-")) {
            a = a.substring(1).trim();
        }
        if (a.isEmpty()) {
            return "";
        }
        return (d.isVoid ? "- " : "") + a;
    }

    /** What the transaction is called at the top of the slip. */
    public static String titleOf(TxnRecord r) {
        if (r != null && "SALE".equals(r.type) && r.entryMode.startsWith("84")) return "QR SALE";
        String t = r == null || r.type == null ? "SALE" : r.type;
        switch (t) {
            case "VOID": return "VOID SALE";
            case "REFUND": return "REFUND";
            case "PREAUTH": return "PRE-AUTH";
            case "PREAUTH_VOID": return "PRE-AUTH VOID";
            case "SALE_COMPLETE": return "SALE COMPLETION";
            case "EMI_SALE": return "EMI SALE";
            case "EMI_VOID": return "VOID EMI SALE";
            case "OFFLINE": return "OFFLINE SALE";
            case "ADJUST": return "TIP ADJUST";
            case "CASH_WITHDRAW": return "CASH WITHDRAW";
            default: return "SALE";
        }
    }

    /**
     * The EMI plan's name, for the heading.
     *
     * <p>{@link TxnRecord#emiProduct} holds DE61 verbatim - {@code [type][tenure]}
     * - so a three-month Easy Buy is stored as {@code "103"} and a three-month
     * Insta Pay as {@code "003"}. Keeping the code in the record is right: the
     * void and the batch upload have to echo exactly what the sale sent. But it
     * is not something to print, and the slip was coming out as
     * {@code EMI SALE : 103}. The tenure already has its own line directly
     * underneath, so the only thing the heading wants out of the code is the
     * first digit, which is the product.</p>
     *
     * <p>Anything that is not a three-digit DE61 is passed through untouched,
     * so a record written before this - or one carrying a product code keyed in
     * from the parameters - still prints whatever it holds rather than being
     * silently renamed.</p>
     */
    public static String emiPlanName(String emiProduct) {
        String s = emiProduct == null ? "" : emiProduct.trim();
        if (s.length() != 3 || !s.matches("[0-9]{3}")) {
            return s;
        }
        switch (s.charAt(0)) {
            case '0': return "INSTA PAY";
            case '1': return "EASY BUY";
            default:  return s;
        }
    }

    /**
     * Name the cryptogram for what it is rather than calling every one an
     * ARQC. Cryptogram Information Data (9F27) bits 7-8: 00 = AAC (card
     * declined), 01 = TC (completed), 10 = ARQC (went online). A slip that
     * labels a TC as an ARQC tells the acquirer's dispute team the wrong thing
     * about how the transaction was authorised.
     */
    public static String acLabel(String cid) {
        String h = cid == null ? "" : cid.trim();
        if (h.length() < 2) {
            return "TC";
        }
        int v;
        try {
            v = Integer.parseInt(h.substring(0, 2), 16);
        } catch (Exception e) {
            return "TC";
        }
        switch ((v >> 6) & 0x03) {
            case 0: return "AAC";
            case 1: return "TC";
            case 2: return "ARQC";
            default: return "AC";
        }
    }

    /**
     * DE13 is MMDD with no year, so the terminal's own year completes it.
     *
     * <p>dd-MM-yyyy, which is what the reference slip prints and what a
     * Bangladeshi merchant reads. A reprint formatted any other way is a
     * different document from the one it copies, so this and
     * {@link #todayForSlip} have to agree.</p>
     */
    private static String dateOf(TxnRecord r) {
        String d = r.date == null ? "" : r.date.replaceAll("[^0-9]", "");
        if (d.length() < 4) {
            return "";
        }
        return d.substring(2, 4) + "-" + d.substring(0, 2) + "-"
                + java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
    }

    /** Today, in the slip's date format. The live path stamps its own. */
    public static String todayForSlip() {
        return new java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US)
                .format(new java.util.Date());
    }

    /** Now, in the slip's time format. */
    public static String nowForSlip() {
        return new java.text.SimpleDateFormat("HH:mm:ss", Locale.US)
                .format(new java.util.Date());
    }

    /** DE12 is hhmmss. */
    private static String timeOf(TxnRecord r) {
        String t = r.time == null ? "" : r.time.replaceAll("[^0-9]", "");
        if (t.length() < 6) {
            return "";
        }
        return t.substring(0, 2) + ":" + t.substring(2, 4) + ":" + t.substring(4, 6);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isEmpty();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
