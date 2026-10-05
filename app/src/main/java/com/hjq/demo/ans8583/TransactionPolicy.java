package com.hjq.demo.ans8583;

/** Decisions shared by the host exchange and card reader. No device dependencies. */
public final class TransactionPolicy {
    private TransactionPolicy() { }

    public static String responseFault(String requestMti, String responseMti,
                                       String requestStan, String responseStan,
                                       String responseCode) {
        String expected = String.format(java.util.Locale.US, "%04d",
                Integer.parseInt(requestMti) + 10);
        if (!expected.equals(responseMti)) return "Unexpected response MTI: " + responseMti;
        if (!requestStan.equals(responseStan)) return "Missing or mismatched response STAN";
        if (responseCode == null || !responseCode.matches("[0-9A-Z]{2}")) {
            return "Missing or invalid response code";
        }
        return "";
    }

    public static boolean reversalAccepted(boolean validResponse, String responseCode) {
        return validResponse && "00".equals(responseCode);
    }

    public static boolean chipServiceCode(String track2) {
        if (track2 == null) return false;
        String track = track2.toUpperCase(java.util.Locale.US);
        int separator = track.indexOf('=');
        if (separator < 0) separator = track.indexOf('D');
        if (separator < 0 || track.length() < separator + 8) return false;
        char first = track.charAt(separator + 5);
        return first == '2' || first == '6';
    }
}
