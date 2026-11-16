package com.shiye.user.common.ai.vector;

public class VectorStoreException extends RuntimeException {

    public VectorStoreException(String message, Throwable cause) {
        super(message, cause);
    }

    public VectorStoreException(String message) {
        super(message);
    }
}
