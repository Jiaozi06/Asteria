package com.jingliu.chat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.function.Consumer;
import org.json.JSONArray;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.*;
import static com.jingliu.chat.MainActivity.json;

enum ApiProtocol {
    AUTO("自动"), OPENAI("OpenAI Chat Completions"), ANTHROPIC("Anthropic Messages"),
    GEMINI("Gemini GenerateContent"), OLLAMA("Ollama 原生");

    final String label;
    ApiProtocol(String label) { this.label = label; }

    static ApiProtocol saved(String name) {
        try { return valueOf(name); }
        catch (IllegalArgumentException ignored) { return AUTO; }
    }

    ApiProtocol resolve(ApiProvider provider, String endpoint) {
        if (this != AUTO) return this;
        return provider == ApiProvider.DEEPSEEK && endpoint != null && !endpoint.isEmpty() ? OPENAI : provider.protocol;
    }

    boolean deepSeek(String endpoint) { return this == ANTHROPIC && endpoint.isEmpty(); }

    String normalizeBase(String endpoint) {
        String suffix = this == ANTHROPIC && endpoint.endsWith("/v1/messages") ? "/messages"
            : this == OLLAMA && endpoint.endsWith("/api/chat") ? "/chat"
            : this == OLLAMA && endpoint.endsWith("/api/tags") ? "/tags" : "";
        return endpoint.substring(0, endpoint.length() - suffix.length());
    }

    HttpURLConnection chat(DeepSeek.Request request, String endpoint, String key, String model, boolean stream) throws IOException {
        String path;
        switch (this) {
            case OPENAI: path = "/chat/completions"; break;
            case ANTHROPIC:
                path = deepSeek(endpoint) ? "/anthropic/v1/messages" : versionPath(endpoint, "/v1") + "/messages";
                break;
            case GEMINI:
                String id = geminiModel(model);
                if (id == null) throw new DeepSeek.ApiError("Gemini 模型 ID 无效。");
                path = geminiVersion(endpoint) + "/models/" + id
                    + (stream ? ":streamGenerateContent?alt=sse" : ":generateContent");
                break;
            case OLLAMA: path = versionPath(endpoint, "/api") + "/chat"; break;
            default: throw new IllegalStateException("Unresolved API protocol");
        }
        return connect(request, endpoint, path, "POST", key);
    }

    HttpURLConnection models(DeepSeek.Request request, String endpoint, String key) throws IOException {
        String path;
        switch (this) {
            case GEMINI: path = geminiVersion(endpoint) + "/models"; break;
            case OLLAMA: path = versionPath(endpoint, "/api") + "/tags"; break;
            case ANTHROPIC: path = deepSeek(endpoint) ? "/models" : versionPath(endpoint, "/v1") + "/models"; break;
            default: path = "/models"; break;
        }
        return connect(request, endpoint, path, "GET", key);
    }

    private HttpURLConnection connect(DeepSeek.Request request, String endpoint, String path, String method, String key) throws IOException {
        boolean anthropicKey = this == ANTHROPIC && !deepSeek(endpoint);
        String bearer = anthropicKey || this == GEMINI ? "" : key;
        HttpURLConnection connection = endpoint.isEmpty() ? request.open(path, method, bearer)
            : request.custom(endpoint, path, method, bearer);
        if (!key.isEmpty()) {
            if (anthropicKey) connection.setRequestProperty("x-api-key", key);
            if (this == GEMINI) connection.setRequestProperty("x-goog-api-key", key);
        }
        if (this == ANTHROPIC && (!deepSeek(endpoint) || "POST".equals(method))) {
            connection.setRequestProperty("anthropic-version", "2023-06-01");
            if (deepSeek(endpoint)) connection.setRequestProperty("anthropic-beta", "files-api-2025-04-14");
        }
        return connection;
    }

    private static String versionPath(String endpoint, String version) { return endpoint.endsWith(version) ? "" : version; }
    private static String geminiVersion(String endpoint) { return endpoint.endsWith("/v1") ? "" : versionPath(endpoint, "/v1beta"); }

    static String geminiModel(String model) {
        String id = model.startsWith("models/") ? model.substring(7) : model;
        return id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,239}") ? id : null;
    }

    JSONArray modelList(JSONObject payload) throws Exception {
        return payload.getJSONArray(this == GEMINI || this == OLLAMA ? "models" : "data");
    }

    String modelId(JSONObject item) {
        if (this == GEMINI) {
            JSONArray methods = item.optJSONArray("supportedGenerationMethods");
            if (methods != null) {
                boolean chat = false;
                for (int i = 0; i < methods.length(); i++) chat |= "generateContent".equals(methods.optString(i));
                if (!chat) return "";
            }
            String id = geminiModel(string(item, "name"));
            return id == null ? "" : id;
        }
        if (this == OLLAMA) return item.optString("name", string(item, "model"));
        return string(item, "id");
    }

    boolean validProbe(JSONObject payload) {
        switch (this) {
            case ANTHROPIC: return payload.has("content") && !payload.has("error");
            case GEMINI: return payload.has("candidates") || payload.has("promptFeedback");
            case OLLAMA: return payload.has("message") && !payload.has("error");
            default: return payload.has("choices") && !payload.has("error");
        }
    }

    int outputLimit(JSONObject request, int window) {
        int output = request.optInt("max_completion_tokens", request.optInt("max_tokens"));
        if (output <= 0 && window > 0) return Math.min(4096, Math.max(1, window / 4));
        if (output <= 0 && this == ANTHROPIC) return 4096;
        return output;
    }

    JSONObject encode(JSONObject request, String endpoint, int window, boolean thinking) {
        int output = outputLimit(request, window);
        if (this == OPENAI || deepSeek(endpoint)) {
            if (window > 0 && !request.has("max_tokens") && !request.has("max_completion_tokens")) {
                put(request, this == OPENAI ? "max_completion_tokens" : "max_tokens", output);
            }
            return request;
        }
        JSONArray messages = array(request, "messages");
        JSONObject result = json("model", string(request, "model"), "stream", request.optBoolean("stream"));
        if (this == OLLAMA) {
            put(result, "messages", messages);
            JSONObject settings = new JSONObject();
            copy(request, settings, "temperature", "temperature");
            copy(request, settings, "top_p", "top_p");
            copy(request, settings, "seed", "seed");
            copy(request, settings, "stop", "stop");
            if (output > 0) put(settings, "num_predict", output);
            if (window > 0) put(settings, "num_ctx", window);
            if (settings.length() > 0) put(result, "options", settings);
            if (thinking) put(result, "think", true);
            if (request.has("response_format")) put(result, "format", "json");
            return result;
        }

        JSONArray content = new JSONArray();
        StringBuilder system = new StringBuilder();
        for (JSONObject message : objects(messages)) {
            String text = string(message, "content");
            String role = string(message, "role");
            if ("system".equals(role)) {
                if (system.length() != 0) system.append("\n\n");
                system.append(text);
            } else if (!text.isEmpty()) {
                content.put(this == ANTHROPIC ? json("role", role, "content", text)
                    : json("role", "assistant".equals(role) ? "model" : "user", "parts", parts(text)));
            }
        }
        if (this == ANTHROPIC) {
            put(result, "messages", content);
            put(result, "max_tokens", output);
            if (system.length() > 0) put(result, "system", system.toString());
            if (!request.isNull("temperature")) put(result, "temperature", Math.min(1d, request.optDouble("temperature")));
            else copy(request, result, "top_p", "top_p");
            copy(request, result, "stop", "stop_sequences");
            return result;
        }

        JSONObject gemini = json("contents", content);
        if (system.length() > 0) put(gemini, "systemInstruction", json("parts", parts(system.toString())));
        JSONObject settings = new JSONObject();
        copy(request, settings, "temperature", "temperature");
        copy(request, settings, "top_p", "topP");
        copy(request, settings, "seed", "seed");
        copy(request, settings, "stop", "stopSequences");
        if (thinking) put(settings, "thinkingConfig", json("includeThoughts", true));
        if (output > 0) put(settings, "maxOutputTokens", output);
        if (request.has("response_format")) put(settings, "responseMimeType", "application/json");
        if (settings.length() > 0) put(gemini, "generationConfig", settings);
        return gemini;
    }

    private static JSONArray parts(String text) { return new JSONArray().put(json("text", text)); }

    private static void copy(JSONObject from, JSONObject to, String name, String vendorName) {
        if (!from.isNull(name)) put(to, vendorName, from.opt(name));
    }

    void response(JSONObject event, Consumer<JSONObject> listener) {
        switch (this) {
            case GEMINI: gemini(event, listener); break;
            case OLLAMA: ollama(event, listener); break;
            default: listener.accept(event); break;
        }
    }

    private static void gemini(JSONObject event, Consumer<JSONObject> listener) {
        if (event.has("error")) {
            listener.accept(event);
            return;
        }
        JSONObject candidate = array(event, "candidates").optJSONObject(0);
        if (candidate == null) {
            String blocked = string(event.optJSONObject("promptFeedback"), "blockReason");
            if (!blocked.isEmpty()) listener.accept(chunk("", "", "content_filter"));
            return;
        }
        String finish = string(candidate, "finishReason");
        if ("SAFETY".equals(finish) || "BLOCKLIST".equals(finish) || "PROHIBITED_CONTENT".equals(finish)
            || "SPII".equals(finish) || "RECITATION".equals(finish)) {
            listener.accept(chunk("", "", "content_filter"));
            return;
        }
        for (JSONObject part : objects(array(candidate.optJSONObject("content"), "parts"))) {
            String text = string(part, "text");
            if (!text.isEmpty()) listener.accept(part.optBoolean("thought") ? chunk(text, "", "") : chunk("", text, ""));
        }
        if (!finish.isEmpty() && !"FINISH_REASON_UNSPECIFIED".equals(finish)) {
            listener.accept(chunk("", "", "MAX_TOKENS".equals(finish) ? "length" : "STOP".equals(finish) ? "stop" : finish));
        }
    }

    private static void ollama(JSONObject event, Consumer<JSONObject> listener) {
        String error = string(event, "error");
        if (!error.isEmpty()) {
            listener.accept(json("type", "error", "error", json("message", error)));
            return;
        }
        JSONObject message = event.optJSONObject("message");
        String finish = event.optBoolean("done") ? string(event, "done_reason") : "";
        if (event.optBoolean("done") && finish.isEmpty()) finish = "stop";
        listener.accept(chunk(string(message, "thinking"), string(message, "content"), finish));
    }

    private static JSONObject chunk(String thinking, String text, String finish) {
        JSONObject choice = json("delta", json("reasoning_content", thinking, "content", text));
        if (!finish.isEmpty()) put(choice, "finish_reason", finish);
        return json("choices", new JSONArray().put(choice));
    }
}
