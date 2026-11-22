package com.shiye.common.web;

import com.shiye.common.api.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownPathReturnsNotFoundInsteadOfInternalError() {
        ResponseEntity<?> response = handler.handleNoResource(
                new NoResourceFoundException(HttpMethod.GET, "/api/v1/unknown"));

        assertEquals(ErrorCode.NOT_FOUND.httpStatus(), response.getStatusCode().value());
    }
}
