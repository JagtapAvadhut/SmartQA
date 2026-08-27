package com.smartqa.common.error;

public class SmartQaException extends RuntimeException {
    private final ErrorCode errorCode;

    public SmartQaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SmartQaException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
