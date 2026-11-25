package com.jingliu.chat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.array;
import static com.jingliu.chat.ChatState.objects;
import static com.jingliu.chat.ChatState.put;
import static com.jingliu.chat.ChatState.string;
import static com.jingliu.chat.MainActivity.json;
import static java.nio.charset.StandardCharsets.UTF_8;

final class DeepSeekHistoryImport {
    private static final String EXPORT_HOST = "deepseek-chat-history-exports-prod.obs.cn-east-3.myhuaweicloud.com";
    private static final long MAX_ZIP_BYTES = 512L * 1024 * 1024;
    private static final int MAX_ENTRY_BYTES = 16 * 1024 * 1024;
    private static final long MAX_JSON_BYTES = 128L * 1024 * 1024;
    private static final int MAX_SESSIONS = 10_000;

    static URI officialLink(String value) {
        try {
            URI uri = URI.create(value.trim());
            if ("https".equalsIgnoreCase(uri.getScheme()) && EXPORT_HOST.equalsIgnoreCase(uri.getHost())
                && uri.getUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                && uri.getFragment() == null && uri.getPath().endsWith(".zip") && uri.getRawQuery() != null) {
                return uri;
            }
        } catch (IllegalArgumentException ignored) { }
        return null;
    }

    static void copyArchive(InputStream input, OutputStream output) throws IOException {
        transfer(input, output, MAX_ZIP_BYTES, null);
    }

    static List<JSONObject> read(File archive, Set<String> existing) throws IOException, JSONException {
        try (ZipFile zip = new ZipFile(archive)) {
            JSONObject manifest = new JSONObject(readEntry(zip, entry(zip, "manifest.json")));
            if (manifest.optInt("export_format_version") != 3) {
                throw new ImportException("目前支持 DeepSeek 第 3 版聊天记录导出包。");
            }
            ZipEntry indexEntry = entry(zip, "chat_session_index.json");
            JSONArray index = new JSONArray(readEntry(zip, indexEntry));
            if (index.length() > MAX_SESSIONS) throw tooLarge();

            List<JSONObject> chats = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            long total = indexEntry.getSize();
            for (int i = 0; i < index.length(); i++) {
                JSONObject item = index.getJSONObject(i);
                String id = item.getString("id");
                if (!id.matches("[a-zA-Z0-9_-]{1,128}") || !seen.add(id)) throw invalidArchive();
                String localId = "deepseek-" + id;
                ZipEntry sessionEntry = entry(zip, "chat_sessions/" + id + ".json");
                total += sessionEntry.getSize();
                if (total > MAX_JSON_BYTES) throw tooLarge();
                if (existing.contains(localId)) continue;
                JSONObject session = new JSONObject(readEntry(zip, sessionEntry));
                if (!id.equals(session.getString("id"))) throw invalidArchive();
                chats.add(convert(session, item, localId));
            }
            return chats;
        }
    }

    private static JSONObject convert(JSONObject session, JSONObject item, String localId) throws IOException, JSONException {
        JSONArray nodes = session.getJSONArray("messages");
        Map<Long, JSONObject> byId = new HashMap<>();
        for (int i = 0; i < nodes.length(); i++) {
            JSONObject node = nodes.getJSONObject(i);
            long id = node.getLong("id");
            if (id <= 0 || byId.put(id, node) != null) throw invalidArchive();
        }

        List<JSONObject> branch = new ArrayList<>();
        long id = nodes.length() == 0 ? 0 : session.getLong("current_message_id");
        String childRole = "";
        while (id != 0) {
            JSONObject node = byId.get(id);
            if (node == null) throw invalidArchive();
            String role = node.getString("role");
            if ((!"USER".equals(role) && !"ASSISTANT".equals(role)) || role.equals(childRole)) throw invalidArchive();
            branch.add(convertMessage(node, "USER".equals(role)));
            childRole = role;
            long parent = node.isNull("parent_id") ? 0 : node.getLong("parent_id");
            if (parent < 0 || parent >= id) throw invalidArchive();
            id = parent;
        }
        if (nodes.length() != 0 && !"USER".equals(childRole)) throw invalidArchive();
        JSONArray messages = new JSONArray();
        for (int i = branch.size() - 1; i >= 0; i--) messages.put(branch.get(i));

        long updated = timestamp(session.optString("updated_at"), timestamp(item.optString("updated_at"), 0));
        String title = string(session, "title");
        if (title.isEmpty()) title = string(item, "title");
        JSONObject chat = json("id", localId, "title", title.isEmpty() ? "未命名对话" : title,
            "apiEndpoint", "", "updated", updated, "customTitle", true, "messages", messages);
        if (session.optBoolean("pinned", item.optBoolean("pinned"))) put(chat, "pinnedAt", Math.max(1, updated));
        return chat;
    }

    private static JSONObject convertMessage(JSONObject node, boolean user) {
        StringBuilder text = new StringBuilder();
        StringBuilder thinking = new StringBuilder();
        StringBuilder template = new StringBuilder();
        JSONArray filenames = new JSONArray();
        Map<String, JSONObject> sources = new LinkedHashMap<>();
        long thinkingMillis = 0;
        for (JSONObject fragment : objects(array(node, "fragments"))) {
            String kind = string(fragment, "type");
            String content = string(fragment, "content");
            if (user) {
                if ("REQUEST".equals(kind)) text.append(content);
                if ("FILE".equals(kind)) {
                    for (JSONObject file : objects(array(fragment, "files"))) {
                        String name = string(file, "file_name");
                        if (!name.isEmpty()) filenames.put(json("name", name));
                    }
                }
                continue;
            }
            switch (kind) {
                case "RESPONSE": text.append(content); break;
                case "THINK":
                    thinking.append(content);
                    thinkingMillis += Math.round(Math.max(0, fragment.optDouble("elapsed_secs", 0)) * 1000);
                    break;
                case "TEMPLATE_RESPONSE":
                    if (template.length() != 0) template.append('\n');
                    template.append(content);
                    break;
                case "TOOL_SEARCH":
                case "READ_LINK":
                    for (JSONObject result : objects(array(fragment, "results"))) addSource(sources, result);
                    break;
                case "TOOL_OPEN": addSource(sources, fragment); break;
                default: break;
            }
        }
        if (user) {
            JSONObject message = json("role", "user", "text", text.toString(),
                "think", node.optBoolean("thinking_enabled"), "search", node.optBoolean("search_enabled"));
            if (filenames.length() != 0) put(message, "archivedFiles", filenames);
            return message;
        }

        String status = string(node, "status");
        boolean filtered = "CONTENT_FILTER".equals(status);
        JSONArray blocks = new JSONArray();
        if (!filtered && thinking.length() > 0) blocks.put(json("type", "thinking", "thinking", thinking.toString()));
        if (!filtered && text.length() > 0) blocks.put(json("type", "text", "text", text.toString()));
        if (!filtered && !sources.isEmpty()) {
            blocks.put(json("type", "web_search_tool_result", "content", new JSONArray(sources.values())));
        }
        JSONObject message = json("role", "assistant", "blocks", blocks, "archived", true,
            "complete", "FINISHED".equals(status));
        if (thinkingMillis > 0) put(message, "thinkingMillis", thinkingMillis);
        if (template.length() > 0) put(message, "status", template.toString());
        else if (filtered) put(message, "status", "原回复已被过滤。");
        else if (!"FINISHED".equals(status)) put(message, "status", "原回复未完成。");
        if (filtered || template.length() > 0 || !("FINISHED".equals(status) || "INCOMPLETE".equals(status))) {
            put(message, "excludeFromContext", true);
        }
        return message;
    }

    private static void addSource(Map<String, JSONObject> sources, JSONObject result) {
        String url = string(result, "url");
        if (!url.isEmpty() && !sources.containsKey(url)) {
            sources.put(url, json("url", url, "title", string(result, "title"), "site_name", string(result, "site_name"),
                "snippet", string(result, "snippet"), "published_at", string(result, "published_at")));
        }
    }

    private static long timestamp(String value, long fallback) {
        try { return Instant.parse(value).toEpochMilli(); }
        catch (DateTimeParseException | ArithmeticException ignored) { return fallback; }
    }

    private static ZipEntry entry(ZipFile zip, String path) throws IOException {
        ZipEntry entry = zip.getEntry(path);
        if (entry == null || entry.isDirectory() || entry.getSize() < 0) throw invalidArchive();
        if (entry.getSize() > MAX_ENTRY_BYTES) throw tooLarge();
        return entry;
    }

    private static String readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) entry.getSize());
        CRC32 crc = new CRC32();
        try (InputStream input = zip.getInputStream(entry)) {
            transfer(input, output, entry.getSize(), crc);
        }
        if (output.size() != entry.getSize() || crc.getValue() != entry.getCrc()) throw invalidArchive();
        return output.toString(UTF_8.name());
    }

    private static void transfer(InputStream input, OutputStream output, long limit, CRC32 crc) throws IOException {
        byte[] buffer = new byte[32768];
        long length = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            length += read;
            if (length > limit) throw crc == null ? tooLarge() : invalidArchive();
            if (crc != null) crc.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
    }

    private static ImportException tooLarge() { return new ImportException("导出包超过当前导入大小限制。"); }
    private static ImportException invalidArchive() { return new ImportException("DeepSeek 导出包不完整或格式无效。"); }

    static final class ImportException extends IOException {
        ImportException(String message) { super(message); }
    }
}
