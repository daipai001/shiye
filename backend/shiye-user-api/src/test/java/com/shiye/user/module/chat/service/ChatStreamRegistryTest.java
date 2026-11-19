package com.shiye.user.module.chat.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatStreamRegistryTest {

    private final ChatStreamRegistry registry = new ChatStreamRegistry();

    @Test
    void cancelsOnlyForOwner() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        registry.register("req_1", 10L, cancelled);

        assertFalse(registry.cancel(11L, "req_1"));
        assertFalse(cancelled.get());

        assertTrue(registry.cancel(10L, "req_1"));
        assertTrue(cancelled.get());
    }

    @Test
    void unregisterRemovesRequest() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        registry.register("req_2", 10L, cancelled);
        registry.unregister("req_2");

        assertFalse(registry.cancel(10L, "req_2"));
    }
}
