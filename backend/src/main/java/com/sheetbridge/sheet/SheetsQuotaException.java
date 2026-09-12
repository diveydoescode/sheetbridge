package com.sheetbridge.sheet;

public class SheetsQuotaException extends RuntimeException {

    public SheetsQuotaException(String message) {
        super(message);
    }

    public SheetsQuotaException(String message, Throwable cause) {
        super(message, cause);
    }
}
