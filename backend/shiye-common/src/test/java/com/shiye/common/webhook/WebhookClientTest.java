package com.shiye.common.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookClientTest {

    private final WebhookClient client = new WebhookClient(new ObjectMapper());

    @Test
    void rejectsBlankAndUnsupportedUrls() {
        assertFalse(client.send(" ", Map.of("event", "ALERT_TEST")).ok());
        assertFalse(client.send("ftp://example.com/hook", Map.of()).ok());
        assertFalse(client.send("not-a-url", Map.of()).ok());
    }

    @Test
    void postsJsonWithEventHeader() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> event = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            event.set(exchange.getRequestHeaders().getFirst("X-ShiYe-Event"));
            byte[] ok = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, ok.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ok);
            }
        });
        server.start();
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event", "ALERT_TEST");
            payload.put("level", "TEST");

            WebhookClient.WebhookResult result = client.send(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/hook", payload);

            assertTrue(result.ok());
            assertEquals(200, result.status());
            assertEquals("ALERT_TEST", event.get());
            Map<?, ?> sent = new ObjectMapper().readValue(body.get(), Map.class);
            assertEquals("ALERT_TEST", sent.get("event"));
            assertEquals("TEST", sent.get("level"));
        } finally {
            server.stop(0);
        }
    }
}
