package com.jingliu.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

final class RequestLog {
    private static final int MAX_ENTRIES = 12;
    private final ArrayDeque<Entry> recent = new ArrayDeque<>();

    synchronized Entry start(String chatId, String provider) {
        Entry entry = new Entry(chatId, provider);
        recent.addFirst(entry);
        if (recent.size() > MAX_ENTRIES) recent.removeLast().discard();
        return entry;
    }

    synchronized List<Entry> forChat(String chatId) {
        List<Entry> entries = new ArrayList<>();
        for (Entry entry : recent) {
            if (entry.chatId.equals(chatId)) entries.add(entry);
        }
        return entries;
    }

    synchronized void clear() {
        for (Entry entry : recent) entry.discard();
        recent.clear();
    }

    static final class Entry {
        private static final int MAX_REQUEST = 6_000;
        private static final int MAX_DETAILS = 32_000;
        private final String chatId;
        final String provider;
        final long started = System.currentTimeMillis();
        private StringBuilder details = new StringBuilder();
        private int attempts;
        private boolean truncated, discarded;
        private String state = "请求中";
        private String error = "";

        Entry(String chatId, String provider) {
            this.chatId = chatId;
            this.provider = provider;
        }

        synchronized void request(String url, String body, String key) {
            if (discarded) return;
            String payload = redact(body, key);
            if (payload.length() > MAX_REQUEST) {
                payload = payload.substring(0, MAX_REQUEST) + "\n[请求正文已截断]";
            }
            append((attempts == 0 ? "" : "\n") + "请求 " + ++attempts + " · POST " + url + "\n" + payload + "\n");
        }

        synchronized void status(int code) {
            append("HTTP " + code + "\n");
        }

        synchronized void response(String data, String key) {
            if (!truncated && !discarded) append(redact(data, key) + "\n");
        }

        synchronized void finish(boolean cancelled, String error, String key) {
            if (discarded) return;
            this.error = redact(error, key);
            state = cancelled ? "已取消" : error.isEmpty() ? "连接结束" : "请求失败";
        }

        synchronized void discard() {
            discarded = true;
            details = new StringBuilder();
            error = "";
            truncated = false;
            state = "已清除";
        }

        synchronized String state() { return state; }

        synchronized String details() {
            return "状态  " + state + (error.isEmpty() ? "" : "\n" + error) + "\n\n" + details
                + (truncated ? "\n[日志已达到长度上限]" : "");
        }

        private void append(String value) {
            if (discarded) return;
            int available = MAX_DETAILS - details.length();
            details.append(value, 0, Math.min(available, value.length()));
            truncated |= value.length() > available;
        }

        private static String redact(String value, String key) {
            if (key.isEmpty()) return value;
            String quoted = JSONObject.quote(key);
            return value.replace(quoted.substring(1, quoted.length() - 1), "••••").replace(key, "••••");
        }
    }
}
