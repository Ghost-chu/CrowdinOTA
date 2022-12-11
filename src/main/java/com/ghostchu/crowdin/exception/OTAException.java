package com.ghostchu.crowdin.exception;

public class OTAException extends Exception {
    public OTAException(String message) {
        super(message);
    }

    public OTAException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
