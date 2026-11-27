package com.shiye.user.module.article.controller;

import com.shiye.common.api.Result;
import com.shiye.user.module.article.support.IngestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArticleExceptionHandlerTest {

    private final ArticleExceptionHandler handler = new ArticleExceptionHandler();

    @Test
    void mapsIngestInProgressTo409() {
        ResponseEntity<Result<Void>> response = handler.handleIngest(
                new IngestException("INGEST_IN_PROGRESS", "busy"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals(40904, response.getBody().code());
    }
}
