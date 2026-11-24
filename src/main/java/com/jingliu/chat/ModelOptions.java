package com.jingliu.chat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.array;
import static com.jingliu.chat.ChatState.put;
import static com.jingliu.chat.ChatState.string;
import static com.jingliu.chat.MainActivity.json;

final class ModelOptions {
    private static final String FIELD = "modelOptions";
    private static final String[] COMPATIBLE_FIELDS = {
        "temperature", "top_p", "frequency_penalty", "presence_penalty", "seed", "reasoning_effort", "verbosity", "stop"
    };
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
        "model", "messages", "stream", "n", "max_tokens", "max_completion_tokens", "response_format",
        "tools", "tool_choice", "functions", "function_call", "modalities", "audio", "web_search_options"
    ));
    static {
        RESERVED.addAll(Arrays.asList(COMPATIBLE_FIELDS));
    }

    static JSONObject get(ChatState state) {
        JSONObject all = state.data.optJSONObject(FIELD);
        JSONObject options = all == null ? null : all.optJSONObject(scope(state));
        return options == null ? new JSONObject() : options;
    }

    static void set(ChatState state, String key, Object value) {
        JSONObject options = get(state);
        if (value == null) options.remove(key);
        else put(options, key, value);
        JSONObject all = state.data.optJSONObject(FIELD);
        if (all == null) {
            all = new JSONObject();
            put(state.data, FIELD, all);
        }
        if (options.length() == 0) all.remove(scope(state));
        else put(all, scope(state), options);
    }

    static void reset(ChatState state) {
        JSONObject all = state.data.optJSONObject(FIELD);
        if (all != null) all.remove(scope(state));
    }

    static JSONObject local(JSONObject chat) {
        JSONObject options = chat.optJSONObject(FIELD);
        return options == null ? new JSONObject() : options;
    }

    static void setLocal(JSONObject chat, String key, Object value) {
        JSONObject options = local(chat);
        if (value == null) options.remove(key);
        else put(options, key, value);
        if (options.length() == 0) chat.remove(FIELD);
        else put(chat, FIELD, options);
    }

    static void resetLocal(JSONObject chat) {
        chat.remove(FIELD);
    }

    private static JSONObject effective(ChatState state, JSONObject chat) {
        JSONObject options = new JSONObject();
        JSONObject defaults = get(state);
        Iterator<String> global = defaults.keys();
        while (global.hasNext()) {
            String name = global.next();
            put(options, name, defaults.opt(name));
        }
        JSONObject overrides = local(chat);
        Iterator<String> local = overrides.keys();
        while (local.hasNext()) {
            String name = local.next();
            if (overrides.isNull(name)) options.remove(name);
            else put(options, name, overrides.opt(name));
        }
        return options;
    }

    private static String scope(ChatState state) {
        return state.custom() ? state.endpoint() + "\n" + string(state.data, "customModel") : "official";
    }

    static String unsupportedKey(JSONObject extras) {
        Iterator<String> keys = extras.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.trim().isEmpty() || RESERVED.contains(key)) return key;
        }
        return null;
    }

    static void apply(ChatState state, JSONObject chat, JSONObject request, boolean thinking) {
        JSONObject options = effective(state, chat);
        List<String> instructions = new ArrayList<>();
        switch (string(options, "tone")) {
            case "friendly": instructions.add("Use a warm, friendly tone."); break;
            case "empathetic": instructions.add("Use an empathetic and considerate tone when appropriate."); break;
            case "professional": instructions.add("Use a professional, clear tone."); break;
            case "direct": instructions.add("Use a direct, neutral tone and lead with the answer."); break;
            case "playful": instructions.add("Use light, appropriate humor when it fits the topic."); break;
            default: break;
        }
        String prompt = string(options, "instructions");
        if (!prompt.isEmpty()) instructions.add(prompt);
        if (state.custom()) {
            for (String name : COMPATIBLE_FIELDS) copy(options, name, request, name);
            String tokenField = "legacy".equals(string(options, "tokenField")) ? "max_tokens" : "max_completion_tokens";
            copy(options, "outputLimit", request, tokenField);
            if ("json".equals(string(options, "format"))) {
                put(request, "response_format", json("type", "json_object"));
                instructions.add("Return only a valid JSON object.");
            }
            JSONObject extras = options.optJSONObject("extras");
            if (extras != null && unsupportedKey(extras) == null) {
                Iterator<String> keys = extras.keys();
                while (keys.hasNext()) {
                    String name = keys.next();
                    put(request, name, extras.opt(name));
                }
            }
            if (!instructions.isEmpty()) {
                JSONArray messages = new JSONArray().put(json("role", "system", "content", String.join("\n\n", instructions)));
                JSONArray existing = array(request, "messages");
                for (int i = 0; i < existing.length(); i++) messages.put(existing.opt(i));
                put(request, "messages", messages);
            }
        } else {
            copy(options, "outputLimit", request, "max_tokens");
            String effort = string(options, "reasoning_effort");
            if (thinking && !effort.isEmpty()) put(request, "output_config", json("effort", effort));
            String existing = string(request, "system");
            if (!existing.isEmpty()) instructions.add(existing);
            if (!instructions.isEmpty()) put(request, "system", String.join("\n\n", instructions));
        }
    }

    private static void copy(JSONObject from, String source, JSONObject to, String target) {
        if (!from.isNull(source)) put(to, target, from.opt(source));
    }
}
