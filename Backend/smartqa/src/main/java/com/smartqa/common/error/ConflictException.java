package com.smartqa.common.error;

public class ConflictException extends SmartQaException {
    public ConflictException(String message) {
        super(ErrorCode.CONFLICT, message);
    }
}
