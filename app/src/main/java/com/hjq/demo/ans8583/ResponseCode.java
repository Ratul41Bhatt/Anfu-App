package com.hjq.demo.ans8583;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * DE39 response codes and the text the terminal shows for them
 * (spec section 8, Response Code Text; section 9, Terminal Generated Error Text).
 *
 * <p>The spec sets out exactly when to use which text:</p>
 *
 * <blockquote>"The contents in the Terminal Display Text column will be
 * displayed if the host does not send any response text or if the host response
 * text is less than twenty characters in length."</blockquote>
 *
 * <p>So host text from DE63 table '22' only wins when it is at least 20
 * characters long. {@link #displayText} implements that rule.</p>
 *
 * <p>Codes marked {@code nnnnnn} in the spec take the authorisation code from
 * DE38, and code 10 takes a balance amount, so those are formatted rather than
 * looked up flat.</p>
 */
public final class ResponseCode {

    /** Minimum host text length that overrides the terminal's own text. */
    public static final int HOST_TEXT_MIN = 20;

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        // ---- host response codes, spec section 8
        TEXT.put("00", "APPROVAL");                 // + auth code
        TEXT.put("01", "PLEASE CALL");
        TEXT.put("02", "REFERRAL");
        TEXT.put("03", "ERROR-CALL HELP - SN");
        TEXT.put("05", "DO NOT HONOUR");
        TEXT.put("09", "ACCEPTED");                 // + auth code
        TEXT.put("10", "BALANCE DUE");              // + amount
        TEXT.put("12", "ERROR-CALL HELP - TR");
        TEXT.put("13", "ERROR-CALL HELP - AM");
        TEXT.put("14", "ERROR-CALL HELP - RE");
        TEXT.put("19", "RE-ENTER TRANSACTION");
        TEXT.put("25", "ERROR-CALL HELP - NT");
        TEXT.put("30", "ERROR-CALL HELP - FE");
        TEXT.put("31", "CALL HELP     - NS");
        TEXT.put("41", "PLEASE CALL   - LC");
        TEXT.put("43", "PLEASE CALL   - CC");
        TEXT.put("51", "DECLINED");
        TEXT.put("54", "EXPIRED CARD");
        TEXT.put("55", "INCORRECT PIN");
        TEXT.put("58", "INVALID TRANSACTION");
        TEXT.put("76", "ERROR-CALL HELP - DC");
        TEXT.put("77", "RECONCILE ERROR");
        TEXT.put("78", "TRANS. NOT FOUND");
        TEXT.put("79", "DECLINED - CVV2");
        TEXT.put("80", "BAD BATCH NUMBER");
        TEXT.put("82", "NO CLOSED SOC SLOTS");
        TEXT.put("83", "NO SUSP. SOC SLOTS");
        TEXT.put("85", "BATCH NOT FOUND");
        TEXT.put("89", "BAD TERMINAL ID.");
        TEXT.put("91", "ERROR-CALL HELP - NA");
        TEXT.put("94", "ERROR-CALL HELP - SQ");
        TEXT.put("95", "BATCH TRANSFER, WAIT");
        TEXT.put("96", "ERROR-CALL HELP - SE");

        // ---- terminal generated codes, spec section 9
        TEXT.put("BB", "BATCH BALANCING");
        TEXT.put("BD", "BUSI. DAY BALANCING");
        TEXT.put("BH", "CALL HELP - BAD HOST");
        TEXT.put("CE", "PLEASE TRY AGAIN -CE");
        TEXT.put("DT", "DUPLICATE TRANS.");
        TEXT.put("IA", "CALL HELP     - IA");
        TEXT.put("ID", "CALL HELP     - ID");
        TEXT.put("IM", "CALL HELP     - IM");
        TEXT.put("IR", "CALL HELP     - IR");
        TEXT.put("IS", "CALL HELP     - IS");
        TEXT.put("IT", "CALL HELP     - IT");
        TEXT.put("JF", "BATCH NEEDS SETTLED");
        TEXT.put("LC", "PLEASE TRY AGAIN-LC");
        TEXT.put("NB", "INV BAL/SETTL");
        TEXT.put("NC", "NMS CALL COMPLETE");
        TEXT.put("ND", "PLEASE TRY AGAIN-ND");
        TEXT.put("NR", "REGISTER REMOTE - NR");
        TEXT.put("OR", "CHECK FOR RECEIPT-OR");
        TEXT.put("RE", "PLEASE TRY AGAIN-RE");
        TEXT.put("T1", "CHECK FOR RECEIPT-TO");
        TEXT.put("TO", "PLEASE TRY AGAIN -TO");
        TEXT.put("UN", "ERROR - WRONG TRAN");
        TEXT.put("ZZ", "DEVICE ERROR");
    }

    /** Spec's catch-all for any code not in the table. */
    private static final String UNKNOWN = "CALL HELP      - IC";

    private ResponseCode() { }

    /** True only for the two codes the spec treats as an approval. */
    public static boolean isApproved(String de39) {
        return "00".equals(de39) || "09".equals(de39);
    }

    /**
     * The line to print and display for this response.
     *
     * @param de39     response code from DE39
     * @param authCode DE38, appended to the codes the spec shows as {@code nnnnnn}
     * @param hostText DE63 table '22' text, or null/empty when the host sent none
     */
    public static String displayText(String de39, String authCode, String hostText) {
        // Spec section 8: host text only wins at 20 characters or more.
        if (hostText != null && hostText.trim().length() >= HOST_TEXT_MIN) {
            return hostText.trim();
        }
        String code = de39 == null ? "" : de39.trim().toUpperCase(Locale.US);
        String base = TEXT.get(code);
        if (base == null) {
            return code.isEmpty() ? "NO RESPONSE FROM HOST" : UNKNOWN;
        }
        // Codes 00 and 09 carry the authorisation code.
        if (("00".equals(code) || "09".equals(code))
                && authCode != null && !authCode.trim().isEmpty()) {
            return base + "  " + authCode.trim();
        }
        return base;
    }

    /** Short label for the journal, without the auth code. */
    public static String label(String de39) {
        String code = de39 == null ? "" : de39.trim().toUpperCase(Locale.US);
        String t = TEXT.get(code);
        return t == null ? UNKNOWN : t;
    }
}
