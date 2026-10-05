package com.hjq.demo.utils;

/** Tracks receipt choices after the transaction has already finished. */
public final class ReceiptFlow {
    public enum Stage { NOT_STARTED, MERCHANT, CUSTOMER_CHOICE, CUSTOMER, COMPLETE }
    private Stage stage = Stage.NOT_STARTED;

    public Stage stage() { return stage; }
    public boolean start() {
        if (stage != Stage.NOT_STARTED) return false;
        stage = Stage.MERCHANT;
        return true;
    }
    public boolean merchantDone(boolean offerCustomerCopy) {
        if (stage != Stage.MERCHANT) return false;
        stage = offerCustomerCopy ? Stage.CUSTOMER_CHOICE : Stage.COMPLETE;
        return true;
    }
    public boolean chooseCustomerCopy(boolean print) {
        if (stage != Stage.CUSTOMER_CHOICE) return false;
        stage = print ? Stage.CUSTOMER : Stage.COMPLETE;
        return true;
    }
    public boolean customerDone() {
        if (stage != Stage.CUSTOMER) return false;
        stage = Stage.COMPLETE;
        return true;
    }
}
