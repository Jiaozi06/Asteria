package com.jingliu.chat;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import org.json.JSONObject;

final class DeepSeek {
    private static final String ORIGIN = ApiProvider.DEEPSEEK.address;

    static final class Request {
        private final Proxy proxy;
        private final SocksAuthentication authentication;
        private final boolean credentialsUnavailable;
        RequestLog.Entry trace;
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;

        Request(Proxy proxy, PasswordAuthentication credentials, boolean credentialsUnavailable) {
            this.proxy = proxy;
            this.authentication = credentials == null ? null : new SocksAuthentication(proxy, credentials);
            this.credentialsUnavailable = credentialsUnavailable;
        }

        boolean cancelled() { return cancelled; }

        void cancel() {
            cancelled = true;
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }

        HttpURLConnection open(String path, String method, String key) throws IOException {
            return connect(ORIGIN + path, method, key);
        }

        HttpURLConnection custom(String endpoint, String path, String method, String key) throws IOException {
            if (!endpoint.equals(normalizeEndpoint(endpoint))) throw new ApiError("接口地址无效。");
            return connect(endpoint + path, method, key);
        }

        private HttpURLConnection connect(String target, String method, String key) throws IOException {
            if (credentialsUnavailable) throw new ApiError("无法读取 SOCKS5 凭据，请在设置中重新保存");
            if (authentication != null) authentication.enter();
            URL url = new URL(target);
            HttpURLConnection active = (HttpURLConnection) (proxy == null ? url.openConnection() : url.openConnection(proxy));
            connection = active;
            if (cancelled) {
                active.disconnect();
                throw new CancellationException();
            }
            active.setRequestMethod(method);
            active.setConnectTimeout(20_000);
            active.setReadTimeout(250_000);
            active.setInstanceFollowRedirects(false);
            // A shared connection must never carry another provider's SOCKS session.
            if (proxy != null) active.setRequestProperty("Connection", "close");
            if (!key.isEmpty()) active.setRequestProperty("Authorization", "Bearer " + key);
            return active;
        }

        void close() {
            if (authentication != null) authentication.leave();
            HttpURLConnection active = connection;
            if (active != null) active.disconnect();
        }
    }

    static String normalizeEndpoint(String input) {
        try {
            URI uri = new URI(input.trim()).normalize();
            boolean secure = "https".equalsIgnoreCase(uri.getScheme());
            if ((!secure && !"http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() > 65535) return null;
            String path = uri.getRawPath().replaceFirst("/+$", "");
            if (path.endsWith("/chat/completions")) path = path.substring(0, path.length() - "/chat/completions".length());
            int defaultPort = secure ? 443 : 80;
            return (secure ? "https://" : "http://") + uri.getHost().toLowerCase(Locale.ROOT)
                + (uri.getPort() == -1 || uri.getPort() == defaultPort ? "" : ":" + uri.getPort()) + path;
        } catch (URISyntaxException e) { return null; }
    }

    static InputStream checkedBody(HttpURLConnection connection) throws IOException {
        return checkedBody(connection, null);
    }

    static JSONObject balance(Request request, String key) throws Exception {
        HttpURLConnection connection = request.open("/user/balance", "GET", key);
        connection.setConnectTimeout(7_000);
        connection.setReadTimeout(7_000);
        try (InputStream input = checkedBody(connection)) {
            return new JSONObject(new String(read(input, 16 * 1024), StandardCharsets.UTF_8));
        }
    }

    private static InputStream checkedBody(HttpURLConnection connection, RequestLog.Entry trace) throws IOException {
        int status = connection.getResponseCode();
        if (trace != null) trace.status(status);
        if (status >= 200 && status < 300) return connection.getInputStream();
        String message;
        boolean contextLimit = false;
        switch (status) {
            case 401: message = "API Key 无效，请在设置中检查。"; break;
            case 402: message = "API 余额不足。"; break;
            case 403: message = "此 API Key 无权访问该接口。"; break;
            case 408: case 504: message = "接口处理超时，请重试。"; break;
            case 413: message = "文件或请求过大。"; break;
            case 429: message = "请求过于频繁，请稍后重试。"; break;
            default:
                message = status >= 500 ? "接口服务暂不可用。" : "接口请求失败（HTTP " + status + "）。";
                if (status == 400 || status == 404 || status == 422) {
                    try (InputStream error = connection.getErrorStream()) {
                        if (error != null) {
                            JSONObject payload = new JSONObject(new String(read(error, 8192), StandardCharsets.UTF_8));
                            JSONObject failure = errorDetails(payload);
                            if (failure == null) failure = payload;
                            String detail = failure.optString("message");
                            contextLimit = contextLimit(failure);
                            if (!detail.isEmpty()) message += " " + detail.substring(0, Math.min(250, detail.length()));
                        }
                    } catch (Exception ignored) { }
                }
        }
        throw new ApiError(message, status, contextLimit || status == 413);
    }

    static void jsonBody(HttpURLConnection connection, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream out = connection.getOutputStream()) { out.write(bytes); }
    }

    static void multipart(HttpURLConnection connection, String purpose, String filename, InputStream source, long limit) throws IOException {
        String boundary = "Jingliu" + UUID.randomUUID();
        String safeName = filename.replaceAll("[\\p{Cntrl}\"\\\\/]", "_");
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        connection.setDoOutput(true);
        connection.setChunkedStreamingMode(16 * 1024);
        try (OutputStream out = connection.getOutputStream()) {
            if (purpose != null) {
                write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n" + purpose + "\r\n");
            }
            write(out, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + safeName
                + "\"\r\nContent-Type: application/octet-stream\r\n\r\n");
            transfer(source, out, limit);
            write(out, "\r\n--" + boundary + "--\r\n");
        }
    }

    static void stream(Request request, String endpoint, ApiProtocol protocol, String key, String body, boolean compactContext,
            int contextWindow, boolean thinking, Consumer<JSONObject> listener) throws Exception {
        boolean official = protocol.deepSeek(endpoint);
        int budget = contextWindow > 0 ? contextWindow - protocol.outputLimit(new JSONObject(body), contextWindow) : ContextCompression.NORMAL;
        if (budget <= 0) throw new ApiError("最大输出 Token 必须小于模型上下文窗口。");
        String payload = compactContext ? ContextCompression.prepare(body, official, budget, false) : body;
        int[] retryBudgets = {Math.min(ContextCompression.RETRY, budget / 2), 0};
        int nextRetry = 0;
        while (true) {
            boolean[] forwarded = {false};
            try {
                streamWithTools(request, endpoint, protocol, key, payload, contextWindow, thinking, event -> {
                    forwarded[0] = true;
                    listener.accept(event);
                });
                return;
            } catch (ApiError error) {
                if (!compactContext || !error.contextLimit || forwarded[0]) throw error;
                String smaller = payload;
                while (smaller.equals(payload) && nextRetry < retryBudgets.length) {
                    smaller = ContextCompression.prepare(body, official, retryBudgets[nextRetry++], true);
                }
                if (smaller.equals(payload)) throw error;
                request.close();
                payload = smaller;
            }
        }
    }

    private static void streamWithTools(Request request, String endpoint, ApiProtocol protocol, String key, String body,
            int contextWindow, boolean thinking,
            Consumer<JSONObject> listener) throws Exception {
        try {
            streamOnce(request, endpoint, protocol, key, body, contextWindow, thinking, listener);
        } catch (ApiError error) {
            if (!protocol.deepSeek(endpoint) || error.contextLimit || error.status != 400 || !error.getMessage().contains("tool_choice")) throw error;
            JSONObject payload = new JSONObject(body);
            if (payload.remove("tool_choice") == null) throw error;
            request.close();
            streamOnce(request, endpoint, protocol, key, payload.toString(), contextWindow, thinking, listener);
        }
    }

    private static void streamOnce(Request request, String endpoint, ApiProtocol protocol, String key, String body,
            int contextWindow, boolean thinking, Consumer<JSONObject> listener) throws Exception {
        JSONObject payload = new JSONObject(body);
        HttpURLConnection connection = protocol.chat(request, endpoint, key, payload.optString("model"), true);
        connection.setRequestProperty("Accept", protocol == ApiProtocol.OLLAMA ? "application/x-ndjson" : "text/event-stream");
        body = protocol.encode(payload, endpoint, contextWindow, thinking).toString();
        RequestLog.Entry trace = request.trace;
        if (trace != null) trace.request(connection.getURL().toExternalForm(), body, key);
        jsonBody(connection, body);
        try (InputStream input = checkedBody(connection, trace)) {
            String type = connection.getContentType();
            Consumer<JSONObject> events = event -> protocol.response(event, listener);
            if (!protocol.deepSeek(endpoint) && type != null && type.toLowerCase(Locale.ROOT).contains("application/json")) {
                String data = new String(read(input, 16 * 1024 * 1024), StandardCharsets.UTF_8);
                if (trace != null) trace.response(data, key);
                JSONObject response = new JSONObject(data);
                if (protocol == ApiProtocol.ANTHROPIC && response.has("content")) anthropicMessage(response, listener);
                else {
                    deliver(response, events);
                    if (protocol == ApiProtocol.OPENAI) listener.accept(new JSONObject().put("type", "openai_done"));
                }
            } else if (protocol == ApiProtocol.OLLAMA) ndjson(input, events, trace, key);
            else events(input, events, trace, key);
        }
    }

    private static void anthropicMessage(JSONObject response, Consumer<JSONObject> listener) throws Exception {
        listener.accept(new JSONObject().put("type", "message_start").put("message", response));
        org.json.JSONArray content = response.getJSONArray("content");
        for (int i = 0; i < content.length(); i++) {
            listener.accept(new JSONObject().put("type", "content_block_start").put("index", i).put("content_block", content.get(i)));
        }
        listener.accept(new JSONObject().put("type", "message_delta").put("delta", response));
        listener.accept(new JSONObject().put("type", "message_stop"));
    }

    private static void ndjson(InputStream input, Consumer<JSONObject> listener, RequestLog.Entry trace, String key) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                if (trace != null) trace.response(line, key);
                JSONObject event = new JSONObject(line);
                deliver(event, listener);
                if (event.optBoolean("done") || event.has("error")) return;
            }
        }
    }

    private static void events(InputStream input, Consumer<JSONObject> listener, RequestLog.Entry trace, String key) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            StringBuilder eventData = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() && eventData.length() > 0) {
                    if (dispatch(eventData.toString(), listener, trace, key)) return;
                    eventData.setLength(0);
                } else if (line.startsWith("data:")) {
                    if (eventData.length() > 0) eventData.append('\n');
                    String value = line.substring(5);
                    eventData.append(value.startsWith(" ") ? value.substring(1) : value);
                }
            }
            if (eventData.length() > 0) dispatch(eventData.toString(), listener, trace, key);
        }
    }

    private static boolean dispatch(String data, Consumer<JSONObject> listener, RequestLog.Entry trace, String key) throws Exception {
        if (trace != null) trace.response("data: " + data, key);
        JSONObject event = "[DONE]".equals(data.trim()) ? new JSONObject().put("type", "openai_done") : new JSONObject(data);
        deliver(event, listener);
        String type = event.optString("type");
        return type.equals("message_stop") || type.equals("error") || type.equals("openai_done");
    }

    private static void deliver(JSONObject event, Consumer<JSONObject> listener) throws ApiError {
        JSONObject error = errorDetails(event);
        if (error != null && contextLimit(error)) {
            throw new ApiError(error.optString("message", "请求超过模型的上下文上限。"), 0, true);
        }
        listener.accept(event);
    }

    private static JSONObject errorDetails(JSONObject payload) {
        Object error = payload.opt("error");
        if (error instanceof JSONObject) return (JSONObject) error;
        return error instanceof String ? MainActivity.json("message", error) : null;
    }

    private static boolean contextLimit(JSONObject error) {
        String code = (error.optString("code") + " " + error.optString("type")).toLowerCase(Locale.ROOT);
        if (code.contains("context_length_exceeded") || code.contains("context_window_exceeded")
                || code.contains("prompt_too_long") || code.contains("input_too_long")) return true;
        String detail = error.optString("message").toLowerCase(Locale.ROOT);
        return detail.contains("maximum context length") || detail.contains("max context length")
            || detail.contains("context length exceeded") || detail.contains("context window exceeded")
            || detail.contains("prompt is too long") || detail.contains("prompt is too large")
            || detail.contains("input is too long") || detail.contains("input token count") && detail.contains("exceeds")
            || detail.contains("超出上下文") || detail.contains("超过上下文");
    }

    static JSONObject json(HttpURLConnection connection) throws Exception {
        try (InputStream in = checkedBody(connection)) {
            return new JSONObject(new String(read(in, 2 * 1024 * 1024), StandardCharsets.UTF_8));
        }
    }

    static byte[] read(InputStream in, long limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        transfer(in, out, limit);
        return out.toByteArray();
    }

    static void transfer(InputStream in, OutputStream out, long limit) throws IOException {
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int length;
        while ((length = in.read(buffer)) != -1) {
            total += length;
            if (total > limit) throw new ApiError("文件超过大小上限。");
            out.write(buffer, 0, length);
        }
    }

    private static void write(OutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    static final class ApiError extends IOException {
        final int status;
        final boolean contextLimit;

        ApiError(String message) { this(message, 0); }

        ApiError(String message, int status) { this(message, status, false); }

        ApiError(String message, int status, boolean contextLimit) {
            super(message);
            this.status = status;
            this.contextLimit = contextLimit;
        }
    }
}
