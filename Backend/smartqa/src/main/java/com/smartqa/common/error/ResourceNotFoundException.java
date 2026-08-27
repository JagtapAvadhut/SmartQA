package com.smartqa.common.error;

public class ResourceNotFoundException extends SmartQaException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, message);
    }
}
