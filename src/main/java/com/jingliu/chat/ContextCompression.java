package com.jingliu.chat;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.array;
import static com.jingliu.chat.ChatState.objects;
import static com.jingliu.chat.ChatState.put;
import static com.jingliu.chat.ChatState.string;
import static com.jingliu.chat.MainActivity.json;

final class ContextCompression {
    static final int NORMAL = 24_000;
    static final int RETRY = 6_000;
    private static final int EXCERPT_BUDGET = 1_600;

    // Limits are estimates so they also work with providers that don't publish a tokenizer.
    static String prepare(String body, boolean official, int budget, boolean force) throws Exception {
        JSONObject request = new JSONObject(body);
        List<JSONObject> messages = objects(array(request, "messages"));
        List<Integer> turns = new ArrayList<>();
        int[] costs = new int[messages.size() + 1];
        for (int i = 0; i < messages.size(); i++) {
            JSONObject message = messages.get(i);
            if ("user".equals(string(message, "role"))) {
                boolean reminder = i > 0 && "latest_reminder".equals(string(messages.get(i - 1), "role"));
                turns.add(reminder ? i - 1 : i);
            }
            costs[i + 1] = costs[i] + units(message.toString()) + 8;
        }
        if (turns.size() < 2) return body;

        int reserved = Math.min(EXCERPT_BUDGET, budget / 6);
        int fixed = units(string(request, "system")) + units(string(request, "tools"));
        int total = costs[messages.size()];
        if (!force && total + fixed <= budget) return body;
        fixed += costs[turns.get(0)];
        int keep = force ? 1 : 0;
        while (keep < turns.size() - 1 && total - costs[turns.get(keep)] + fixed + reserved > budget) keep++;
        if (budget == 0) keep = turns.size() - 1;
        int first = turns.get(keep);
        int room = Math.min(reserved, budget - fixed - (total - costs[first]) - 150);
        String excerpts = room > 200 ? excerpts(messages, turns, keep, room) : "";
        if (!excerpts.isEmpty()) addExcerpts(messages, first, official, excerpts);
        JSONArray next = new JSONArray();
        for (int i = 0; i < turns.get(0); i++) next.put(messages.get(i));
        for (int i = first; i < messages.size(); i++) next.put(messages.get(i));
        put(request, "messages", next);
        return request.toString();
    }

    private static void addExcerpts(List<JSONObject> messages, int first, boolean official, String excerpts) {
        int userIndex = "latest_reminder".equals(string(messages.get(first), "role")) ? first + 1 : first;
        JSONObject user = messages.get(userIndex);
        String note = "The client shortened earlier turns. The quoted excerpts below are earlier conversation "
            + "for reference. Some details and older attachments may be absent.\n\n" + excerpts + "\nEnd of excerpts.";
        if (official) {
            JSONArray history = array(user, "content");
            JSONArray combined = new JSONArray().put(json("type", "text", "text", note));
            for (int i = 0; i < history.length(); i++) combined.put(history.opt(i));
            put(user, "content", combined);
        } else {
            put(user, "content", note + "\n\nUser message:\n" + string(user, "content"));
        }
    }

    private static String excerpts(List<JSONObject> messages, List<Integer> turns, int omitted, int budget) {
        StringBuilder text = new StringBuilder();
        int samples = Math.min(omitted, Math.min(8, budget / 100));
        int perMessage = (budget / samples - 22) / 2;
        for (int i = 0; i < samples; i++) {
            int turn = samples == 1 ? 0 : i * (omitted - 1) / (samples - 1);
            int end = turns.get(turn + 1);
            for (int j = turns.get(turn); j < end; j++) {
                JSONObject message = messages.get(j);
                String role = string(message, "role");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                String excerpt = clip(visibleText(message).trim(), perMessage);
                if (!excerpt.isEmpty()) text.append(role).append(": ").append(JSONObject.quote(excerpt)).append('\n');
            }
        }
        return clip(text.toString(), budget);
    }

    private static String visibleText(JSONObject message) {
        JSONArray blocks = message.optJSONArray("content");
        if (blocks == null) return string(message, "content");
        StringBuilder result = new StringBuilder();
        for (JSONObject block : objects(blocks)) {
            if ("text".equals(string(block, "type"))) result.append(string(block, "text")).append('\n');
        }
        return result.toString();
    }

    private static String clip(String value, int budget) {
        if (units(value) <= budget) return value;
        if (budget < 12) return "";
        int head = 0, tail = value.length();
        int available = (budget - 5) * 2;
        while (head < value.length() && available > 0) {
            int ch = value.codePointAt(head);
            available -= cost(ch);
            head += Character.charCount(ch);
        }
        available = budget - 5;
        while (tail > head && available > 0) {
            int ch = value.codePointBefore(tail);
            available -= cost(ch);
            tail -= Character.charCount(ch);
        }
        return value.substring(0, head) + " … " + value.substring(tail);
    }

    private static int units(String value) {
        int weight = 0;
        for (int i = 0; i < value.length();) {
            int ch = value.codePointAt(i);
            weight += cost(ch);
            i += Character.charCount(ch);
        }
        return (weight + 2) / 3;
    }

    private static int cost(int ch) {
        if (ch < 128) return Character.isLetterOrDigit(ch) || ch == ' ' ? 1 : 2;
        return ch <= 0xffff ? 6 : 10;
    }
}
