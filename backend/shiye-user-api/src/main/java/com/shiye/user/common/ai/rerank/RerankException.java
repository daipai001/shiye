package com.shiye.user.common.ai.rerank;

public class RerankException extends RuntimeException {

    private final String code;

    public RerankException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RerankException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
