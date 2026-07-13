package com.iam.sdk;

public class IamSdkException extends RuntimeException {
    public IamSdkException(String message) {
        super(message);
    }

    public IamSdkException(String message, Throwable cause) {
        super(message, cause);
    }
}
