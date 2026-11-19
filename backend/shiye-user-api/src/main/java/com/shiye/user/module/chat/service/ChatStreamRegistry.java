package com.shiye.user.module.chat.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 记录进行中的 SSE 请求，支持用户主动取消。只保存内存标记，不保存正文。
 */
@Service
public class ChatStreamRegistry {

    private final ConcurrentMap<String, Entry> active = new ConcurrentHashMap<>();

    public void register(String requestId, long userId, AtomicBoolean cancelled) {
        active.put(requestId, new Entry(userId, cancelled));
    }

    public boolean cancel(long userId, String requestId) {
        Entry entry = active.get(requestId);
        if (entry == null || entry.userId != userId) {
            return false;
        }
        entry.cancelled.set(true);
        active.remove(requestId);
        return true;
    }

    public void unregister(String requestId) {
        active.remove(requestId);
    }

    private record Entry(long userId, AtomicBoolean cancelled) {
    }
}
