package com.jingliu.chat;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.jingliu.chat.MainActivity.json;

final class ChatState {
    static final String FLASH = "deepseek-flash";
    static final String PRO = "deepseek-v4-pro";
    final JSONObject data;
    final List<JSONObject> attachments = new ArrayList<>();
    Job job;

    static final class Job {
        final int id;
        final String endpoint;
        final JSONObject chat;
        final JSONObject reply;
        final JSONObject request;
        boolean receivedFinish;
        String responseId = "";
        private long thinkingSinceNanos;
        boolean hasReasoning;
        boolean thinkingFinished;

        Job(int id, String endpoint, JSONObject chat, JSONObject reply, JSONObject request) {
            this.id = id;
            this.endpoint = endpoint;
            this.chat = chat;
            this.reply = reply;
            this.request = request;
        }

        void beginThinking() {
            if (thinkingSinceNanos == 0L && !thinkingFinished) thinkingSinceNanos = System.nanoTime();
        }

        void endThinking() {
            if (thinkingFinished || thinkingSinceNanos == 0L) return;
            thinkingFinished = true;
            if (hasReasoning) {
                put(reply, "thinkingMillis", Math.max(1L, (System.nanoTime() - thinkingSinceNanos) / 1_000_000L));
            }
        }
    }

    private int sequence;

    ChatState(String stored) {
        JSONObject loaded;
        try { loaded = new JSONObject(stored); }
        catch (JSONException ignored) { loaded = new JSONObject(); }
        data = loaded;
        if (data.optJSONArray("chats") == null) put(data, "chats", new JSONArray());
    }

    static void put(JSONObject object, String key, Object value) {
        try { object.put(key, value); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
    }

    static void put(JSONArray array, int index, Object value) {
        try { array.put(index, value); }
        catch (JSONException e) { throw new IllegalArgumentException(e); }
    }

    static JSONArray array(JSONObject object, String field) {
        JSONArray value = object == null ? null : object.optJSONArray(field);
        return value == null ? new JSONArray() : value;
    }

    static String string(JSONObject object, String field) {
        return object == null || object.isNull(field) ? "" : object.optString(field);
    }

    static List<JSONObject> objects(JSONArray array) {
        List<JSONObject> result = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry != null) result.add(entry);
        }
        return result;
    }

    JSONArray chats() { return array(data, "chats"); }
    JSONObject current() {
        String active = string(data, "active");
        for (JSONObject chat : objects(chats())) if (active.equals(chat.optString("id"))) return chat;
        return null;
    }
    JSONArray messages() { return array(current(), "messages"); }
    boolean custom() { return "custom".equals(data.optString("provider")); }
    String endpoint() { return custom() ? string(data, "customUrl") : ""; }
    ApiProvider apiProvider() {
        String preset = string(data, "apiPreset");
        if (ApiProvider.DEEPSEEK.name().equals(preset)) return ApiProvider.DEEPSEEK;
        if (ApiProvider.OPENAI.name().equals(preset)) return ApiProvider.OPENAI;
        return custom() ? ApiProvider.OPENAI : ApiProvider.DEEPSEEK;
    }

    String preferredAddress(ApiProvider provider) {
        if (provider == apiProvider() && !endpoint().isEmpty()) return endpoint();
        if (provider != apiProvider()) {
            String saved = string(profile(provider), "endpoint");
            if (!saved.isEmpty()) return saved;
        }
        return provider.address;
    }

    String preferredModel(ApiProvider provider, String endpoint) {
        if (endpoint == null) return "";
        if (endpoint.isEmpty()) return selectedModel();
        if (provider == apiProvider() && endpoint.equals(endpoint())) return string(data, "customModel");
        JSONObject profile = profile(provider);
        return endpoint.equals(string(profile, "endpoint")) ? string(profile, "model") : "";
    }

    private JSONObject profile(ApiProvider provider) {
        JSONObject profiles = data.optJSONObject("apiProfiles");
        return profiles == null ? null : profiles.optJSONObject(provider.name());
    }

    void saveConnection(ApiProvider provider, String endpoint, String model) {
        ApiProvider previous = apiProvider();
        String previousEndpoint = endpoint();
        JSONObject profiles = data.optJSONObject("apiProfiles");
        if (profiles == null) {
            profiles = new JSONObject();
            put(data, "apiProfiles", profiles);
        }
        if (previous != provider) put(profiles, previous.name(),
            json("endpoint", previousEndpoint, "model", previousEndpoint.isEmpty() ? selectedModel() : string(data, "customModel")));
        put(profiles, provider.name(), json("endpoint", endpoint, "model", model));
        if (!previousEndpoint.equals(endpoint)) attachments.clear();
        put(data, "apiPreset", provider.name());
        put(data, "provider", endpoint.isEmpty() ? "official" : "custom");
        if (endpoint.isEmpty()) put(data, "model", model);
        else {
            put(data, "customUrl", endpoint);
            put(data, "customModel", model);
        }
    }

    String selectedModel() {
        String saved = string(data, "model");
        return saved.isEmpty() ? FLASH : saved;
    }
    String effectiveModel() {
        return effectiveModel(data.optBoolean("search"));
    }
    String effectiveModel(boolean searchEnabled) {
        return modelFor(searchEnabled || !attachments.isEmpty(), messages());
    }
    private String modelFor(boolean searchEnabled, JSONArray messages) {
        if (custom()) return string(data, "customModel");
        if (searchEnabled) return FLASH;
        for (JSONObject message : objects(messages)) if (array(message, "files").length() > 0) return FLASH;
        return selectedModel();
    }

    boolean sameProvider() {
        JSONObject chat = current();
        return chat == null || endpoint().equals(string(chat, "apiEndpoint"));
    }

    void activate(JSONObject chat) {
        put(data, "active", chat == null ? JSONObject.NULL : chat.optString("id"));
        attachments.clear();
    }

    boolean delete(Set<String> ids) {
        boolean clearActive = ids.contains(string(data, "active"));
        JSONArray entries = chats();
        for (int i = entries.length() - 1; i >= 0; i--) {
            if (ids.contains(string(entries.optJSONObject(i), "id"))) entries.remove(i);
        }
        if (clearActive) activate(null);
        return clearActive;
    }

    static boolean pinned(JSONObject chat) { return chat.optLong("pinnedAt") > 0; }

    void setPinned(Set<String> ids, boolean value) {
        long now = System.currentTimeMillis();
        for (JSONObject chat : objects(chats())) {
            if (!ids.contains(string(chat, "id"))) continue;
            if (!value) chat.remove("pinnedAt");
            else if (!pinned(chat)) put(chat, "pinnedAt", now);
        }
    }

    List<JSONObject> history(String filter) {
        String query = filter.trim().toLowerCase(Locale.ROOT);
        List<JSONObject> result = objects(chats());
        if (!query.isEmpty()) result.removeIf(chat -> !matches(chat, query));
        result.sort(Comparator.comparingLong((JSONObject chat) -> chat.optLong("pinnedAt"))
            .thenComparingLong(chat -> chat.optLong("updated")).reversed());
        return result;
    }

    private boolean matches(JSONObject chat, String query) {
        if (string(chat, "title").toLowerCase(Locale.ROOT).contains(query)) return true;
        for (JSONObject message : objects(array(chat, "messages"))) {
            if (answer(message).toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        return false;
    }

    static String answer(JSONObject message) {
        if (message.has("text")) return string(message, "text");
        return merged(message, "text", "text");
    }

    static String merged(JSONObject message, String type, String field) {
        StringBuilder combined = new StringBuilder();
        for (JSONObject block : objects(array(message, "blocks"))) {
            if (type.equals(block.optString("type"))) combined.append(string(block, field));
        }
        return combined.toString();
    }

    Job start(JSONObject user, int replaceFrom) {
        JSONObject chat = current();
        JSONArray existing = messages();
        if (chat == null) {
            String title = string(user, "text");
            if (title.isEmpty()) title = array(user, "files").optJSONObject(0).optString("name");
            int max = Math.min(title.codePointCount(0, title.length()), 40);
            chat = json("id", UUID.randomUUID().toString(), "title", title.substring(0, title.offsetByCodePoints(0, max)),
                "apiEndpoint", endpoint());
            chats().put(chat);
            activate(chat);
        }
        JSONArray next = new JSONArray();
        for (int i = 0; i < replaceFrom; i++) next.put(existing.opt(i));
        if (!custom()) put(user, "reminder", currentReminder());
        next.put(user);
        put(chat, "messages", next);
        put(chat, "updated", System.currentTimeMillis());
        JSONArray protocol = new JSONArray();
        for (JSONObject message : objects(next)) {
            String role = message.optString("role");
            String text = answer(message);
            if ("user".equals(role)) {
                if (custom()) {
                    if (!text.isEmpty()) protocol.put(json("role", role, "content", text));
                } else {
                    String reminder = string(message, "reminder");
                    if (!reminder.isEmpty()) protocol.put(json("role", "latest_reminder", "content", reminder));
                    JSONArray content = new JSONArray();
                    for (JSONObject file : objects(array(message, "files"))) {
                        content.put(json("type", file.optBoolean("image") ? "image" : "document",
                            "source", json("type", "file", "file_id", file.optString("id"))));
                    }
                    if (!text.isEmpty()) content.put(json("type", "text", "text", text));
                    protocol.put(json("role", role, "content", content));
                }
            } else if (!text.isEmpty() && !"refusal".equals(message.optString("finish"))) {
                Object content = custom() ? text : message.optBoolean("complete") ? array(message, "blocks")
                    : new JSONArray().put(json("type", "text", "text", text));
                protocol.put(json("role", "assistant", "content", content));
            }
        }
        boolean thinkEnabled = user.optBoolean("think", data.optBoolean("think"));
        boolean searchEnabled = user.optBoolean("search", data.optBoolean("search"));
        String prompt = string(user, "text");
        boolean requestedLookup = !custom() && searchEnabled && WebSources.explicitLookup(prompt);
        String model = modelFor(searchEnabled, next);
        JSONObject request = json("model", model, "stream", true, "messages", protocol);
        if (!custom()) {
            put(request, "thinking", json("type", thinkEnabled ? "enabled" : "disabled"));
            put(request, "safety_check", json("level", "high"));
            if (replaceFrom == 0 && !chat.optBoolean("customTitle")) put(request, "title", json("enabled", true));
            if (FLASH.equals(model)) {
                JSONArray tools = new JSONArray();
                boolean forceLookup = requestedLookup && !thinkEnabled && !WebSources.containsLink(prompt)
                    && array(user, "files").length() == 0;
                if (searchEnabled) {
                    tools.put(json("type", "deepseek_web_search", "name", "deepseek_web_search"));
                    put(request, "system", "本轮智能搜索已开启，可以使用网页搜索。遇到用户要求查询或需要实时核对的信息时，"
                        + "先检索再回答，依据本轮实际返回的网页。不要把以往对话中关于联网能力的说法当成当前状态。"
                        + "用户询问能力时说明已开启搜索。日期以本轮设备提供的日期为准。");
                }
                if (forceLookup) put(request, "tool_choice", json("type", "any"));
                else if (hasLink(next)) {
                    tools.put(json("type", "deepseek_read_url", "name", "deepseek_read_url"));
                }
                if (tools.length() > 0) put(request, "tools", tools);
            }
        }
        ModelOptions.apply(this, chat, request, thinkEnabled);
        JSONObject reply = json("role", "assistant", "blocks", new JSONArray(), "status", "回复已中断，可以重新生成。");
        if (requestedLookup) put(reply, "lookupRequested", true);
        next.put(reply);
        job = new Job(++sequence, endpoint(), chat, reply, request);
        return job;
    }

    private static boolean hasLink(JSONArray messages) {
        for (JSONObject message : objects(messages)) {
            if ("user".equals(string(message, "role")) && WebSources.containsLink(answer(message))) return true;
        }
        return false;
    }

    private static String currentReminder() {
        LocalDate today = LocalDate.now();
        boolean chinese = "zh".equals(Locale.getDefault().getLanguage());
        Locale language = chinese ? Locale.SIMPLIFIED_CHINESE : Locale.ENGLISH;
        return today + "," + today.getDayOfWeek().getDisplayName(TextStyle.FULL, language)
            + ",,App," + (chinese ? "中文" : "English");
    }

    void finish(String error) {
        if (job == null) return;
        job.endThinking();
        JSONObject reply = job.reply;
        String reason = string(reply, "finish");
        String status = error;
        if ("refusal".equals(reason)) {
            put(reply, "blocks", new JSONArray());
            status = "接口未返回可显示的回复。";
        } else if (status.isEmpty()) {
            if ("max_tokens".equals(reason)) status = "回复已达到输出上限。";
            else if (answer(reply).isEmpty()) status = "接口未返回文字回复。";
            else {
                WebSources sources = WebSources.from(reply);
                if (sources.size() == 0 && (sources.searched || reply.optBoolean("lookupRequested"))) {
                    status = "本轮未返回网页来源。";
                }
            }
        }
        put(reply, "status", status);
        put(reply, "complete", error.isEmpty() && !"refusal".equals(reason));
        job = null;
    }

    void receive(JSONObject event) {
        Job active = job;
        if (active == null || event == null) return;
        JSONObject reply = active.reply;
        JSONArray blocks = array(reply, "blocks");
        String type = event.optString("type");
        String responseId = active.endpoint.isEmpty()
            ? "message_start".equals(type) ? string(event.optJSONObject("message"), "id") : ""
            : string(event, "id");
        if (!responseId.isEmpty()) {
            if (!active.responseId.isEmpty() && !active.responseId.equals(responseId)) {
                finish("接口在同一条流里返回了不同的回复，已停止拼接。");
                return;
            }
            active.responseId = responseId;
        }
        if (!active.endpoint.isEmpty()) {
            if ("openai_done".equals(type)) { finish(""); return; }
            JSONObject error = event.optJSONObject("error");
            if (error != null || "error".equals(type)) {
                String detail = string(error, "message");
                finish(detail.isEmpty() ? "兼容接口返回错误。" : detail);
                return;
            }
            JSONObject choice = array(event, "choices").optJSONObject(0);
            if (choice == null) return;
            JSONObject delta = choice.optJSONObject("delta");
            if (delta == null) delta = choice.optJSONObject("message");
            String reasoning = string(delta, "reasoning_content");
            String content = string(delta, "content");
            if (!reasoning.isEmpty()) {
                active.beginThinking();
                active.hasReasoning = true;
                appendCustom(blocks, 0, "thinking", "thinking", reasoning);
            }
            if (!content.isEmpty()) {
                active.endThinking();
                appendCustom(blocks, 1, "text", "text", content);
            }
            if (!choice.isNull("finish_reason")) {
                String reason = choice.optString("finish_reason");
                put(reply, "finish", "length".equals(reason) ? "max_tokens" : "content_filter".equals(reason) ? "refusal" : reason);
                if ("content_filter".equals(reason)) put(reply, "blocks", new JSONArray());
                active.receivedFinish = true;
            }
            put(reply, "status", "生成中…");
            return;
        }
        int index = event.optInt("index");
        switch (type) {
            case "message_start": put(reply, "status", "生成中…"); break;
            case "content_block_start": {
                JSONObject block = event.optJSONObject("content_block");
                if (block == null || index < 0 || index > 10000) break;
                put(blocks, index, block);
                if ("server_tool_use".equals(block.optString("type"))) put(reply, "status", "正在查询资料…");
                else if ("thinking".equals(block.optString("type"))) {
                    active.beginThinking();
                    if (!string(block, "thinking").isEmpty()) active.hasReasoning = true;
                } else if ("text".equals(block.optString("type"))) {
                    if (!string(block, "text").isEmpty()) active.endThinking();
                    put(reply, "status", "生成中…");
                }
                break;
            }
            case "content_block_delta": {
                JSONObject block = blocks.optJSONObject(index);
                JSONObject delta = event.optJSONObject("delta");
                if (block == null || delta == null) break;
                String name = delta.optString("type");
                if ("citations_delta".equals(name)) {
                    JSONArray citations = array(block, "citations");
                    citations.put(delta.opt("citation"));
                    put(block, "citations", citations);
                } else {
                    String field = name.replaceFirst("_delta$", "");
                    if ("text".equals(field) || "thinking".equals(field) || "signature".equals(field)) {
                        String addition = string(delta, field);
                        if ("thinking".equals(field) && !addition.isEmpty()) {
                            active.beginThinking();
                            active.hasReasoning = true;
                        } else if ("text".equals(field) && !addition.isEmpty()) active.endThinking();
                        put(block, field, string(block, field) + addition);
                    }
                }
                break;
            }
            case "message_delta": {
                JSONObject delta = event.optJSONObject("delta");
                if (delta == null) break;
                put(reply, "finish", string(delta, "stop_reason"));
                if ("refusal".equals(string(delta, "stop_reason"))) put(reply, "blocks", new JSONArray());
                if (!active.chat.optBoolean("customTitle") && !string(delta, "title").isEmpty()) put(active.chat, "title", string(delta, "title"));
                break;
            }
            case "message_stop": finish(""); break;
            case "error": {
                String detail = string(event.optJSONObject("error"), "message");
                finish(detail.isEmpty() ? "DeepSeek 返回错误。" : detail);
                break;
            }
            default: break;
        }
    }

    private static void appendCustom(JSONArray blocks, int index, String type, String field, String addition) {
        if (addition.isEmpty()) return;
        JSONObject block = blocks.optJSONObject(index);
        if (block == null) {
            block = json("type", type);
            put(blocks, index, block);
        }
        put(block, field, string(block, field) + addition);
    }
}
