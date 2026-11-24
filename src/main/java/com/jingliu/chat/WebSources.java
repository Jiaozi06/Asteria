package com.jingliu.chat;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.array;
import static com.jingliu.chat.ChatState.objects;
import static com.jingliu.chat.ChatState.string;

final class WebSources {
    private static final Pattern LOOKUP = Pattern.compile(
        "查查|查一下|帮我[查搜]|查询|搜索一下|搜一下|搜搜|最新|热搜|实时(?:数据|行情|天气|新闻)|(?:今天|刚刚|目前).{0,12}(?:新闻|天气|股价|汇率|比分|榜单)|\\b(?:latest|look up|search for|current (?:price|weather|news|results))\\b",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern LINK = Pattern.compile("https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern FENCE = Pattern.compile("[ \\t]*(?:```+|~~~+)");
    static final Pattern CITATION = Pattern.compile("\uE000([1-9][0-9]{0,4})\uE001");

    static boolean explicitLookup(String text) { return LOOKUP.matcher(text).find(); }
    static boolean containsLink(String text) { return LINK.matcher(text).find(); }

    static final class Source {
        final int number;
        final String url;
        final String host;
        String title = "";
        String name = "";
        String date = "";
        String snippet = "";

        Source(int number, URI uri, String url) {
            this.number = number;
            this.url = url;
            this.host = uri.getHost() == null ? uri.getRawAuthority() : uri.getHost();
        }

        String displayTitle() { return title.isEmpty() ? host : title; }
        String displaySite() { return name.isEmpty() ? host : name; }
    }

    private final Map<String, Source> byUrl = new LinkedHashMap<>();
    boolean searched;

    List<Source> items() { return new ArrayList<>(byUrl.values()); }
    int size() { return byUrl.size(); }

    static WebSources from(JSONObject message) {
        WebSources result = new WebSources();
        if (ChatState.hiddenReply(message)) return result;
        List<JSONObject> blocks = objects(array(message, "blocks"));
        for (JSONObject block : blocks) {
            String type = string(block, "type");
            if ("web_search_tool_result".equals(type)) {
                result.searched = true;
                for (JSONObject item : objects(array(block, "content"))) result.add(item, null);
            } else if ("web_fetch_tool_result".equals(type)) {
                JSONObject item = block.optJSONObject("content");
                if (item != null) result.add(item, item.optJSONObject("content"));
            }
        }
        for (JSONObject block : blocks) {
            for (JSONObject cite : objects(array(block, "citations"))) result.add(cite, null);
        }
        return result;
    }

    String annotatedAnswer(JSONObject message) {
        if (ChatState.hiddenReply(message)) return "";
        StringBuilder result = new StringBuilder();
        boolean hasBlocks = false;
        for (JSONObject block : objects(array(message, "blocks"))) {
            if (!"text".equals(string(block, "type"))) continue;
            hasBlocks = true;
            String value = string(block, "text");
            int end = value.length();
            while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) end--;
            result.append(value, 0, end);
            boolean afterFence = end > 0 && FENCE.matcher(value.substring(value.lastIndexOf('\n', end - 1) + 1, end)).matches();
            boolean first = true;
            for (JSONObject cite : objects(array(block, "citations"))) {
                Source source = byUrl.get(string(cite, "url").trim());
                if (source == null || end == 0) continue;
                if (first) { result.append(afterFence ? "\n\n" : " "); first = false; }
                result.append('\uE000').append(source.number).append('\uE001');
            }
            result.append(value, end, value.length());
        }
        return hasBlocks ? result.toString() : ChatState.answer(message);
    }

    private void add(JSONObject item, JSONObject document) {
        String url = string(item, "url").trim();
        if (url.isEmpty()) return;
        URI uri;
        try { uri = new URI(url); } catch (URISyntaxException invalid) { return; }
        String scheme = uri.getScheme();
        String authority = uri.getRawAuthority();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) return;
        if (authority == null || authority.isEmpty() || authority.contains("@")) return;
        Source source = byUrl.get(url);
        if (source == null) {
            source = new Source(byUrl.size() + 1, uri, url);
            byUrl.put(url, source);
        }
        if (source.title.isEmpty()) {
            source.title = string(item, "title").trim();
            if (source.title.isEmpty()) source.title = string(document, "title").trim();
        }
        if (source.name.isEmpty()) source.name = string(item, "site_name").trim();
        if (source.snippet.isEmpty()) source.snippet = string(item, "snippet").trim();
        if (source.date.isEmpty()) {
            String date = string(item, "page_age");
            if (date.isEmpty()) date = string(item, "published_at");
            if (date.matches("^\\d{4}-\\d{2}-\\d{2}.*")) source.date = date.substring(0, 10);
        }
    }
}
