package com.shiye.user.common.ai.embedding;

public class EmbeddingException extends RuntimeException {

    private final String code;

    public EmbeddingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public EmbeddingException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
