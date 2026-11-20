package com.shiye.user.module.article.controller;

import com.shiye.common.api.ErrorCode;
import com.shiye.common.api.Result;
import com.shiye.user.module.article.support.IngestException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.shiye.user.module.article")
public class ArticleExceptionHandler {

    @ExceptionHandler(IngestException.class)
    public ResponseEntity<Result<Void>> handleIngest(IngestException ex) {
        ErrorCode code = map(ex.getCode());
        return ResponseEntity.status(code.httpStatus())
                .body(Result.fail(code, ex.getMessage()));
    }

    private ErrorCode map(String code) {
        if (code == null) {
            return ErrorCode.PARSE_FAILED;
        }
        return switch (code) {
            case "INVALID_URL" -> ErrorCode.INVALID_URL;
            case "FILE_TOO_LARGE" -> ErrorCode.FILE_TOO_LARGE;
            case "UNSUPPORTED_FILE_TYPE" -> ErrorCode.UNSUPPORTED_FILE_TYPE;
            case "EMPTY_CONTENT", "EMPTY_FILE", "PARSE_EMPTY", "CHUNK_EMPTY" ->
                    ErrorCode.VALIDATION_ERROR;
            case "EMBEDDING_BAD_RESPONSE", "EMBEDDING_FAILED" -> ErrorCode.EMBEDDING_FAILED;
            case "VECTOR_STORE_UNAVAILABLE" -> ErrorCode.VECTOR_STORE_UNAVAILABLE;
            case "OCR_UNAVAILABLE", "OCR_FAILED" -> ErrorCode.PARSE_FAILED;
            default -> ErrorCode.PARSE_FAILED;
        };
    }
}
