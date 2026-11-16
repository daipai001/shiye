package com.shiye.user.common.ai.chat;

public class ChatException extends RuntimeException {

    private final String code;

    public ChatException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ChatException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
