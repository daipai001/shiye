package com.shiye.user.module.article.support;

public class IngestException extends RuntimeException {

    private final String code;

    public IngestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public IngestException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
