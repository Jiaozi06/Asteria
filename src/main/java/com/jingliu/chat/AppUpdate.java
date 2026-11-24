package com.jingliu.chat;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

final class AppUpdate {
    private static final String RELEASES = "https://github.com/Jiaozi06/Asteria/releases";
    private static final String LATEST = "https://api.github.com/repos/Jiaozi06/Asteria/releases/latest";
    private static final String MIRROR = "https://gh-proxy.com/";
    private static final Pattern VERSION = Pattern.compile("v?[0-9]{1,9}(?:\\.[0-9]{1,9}){1,3}");

    private AppUpdate() {}

    static boolean preferMirror(Locale locale) { return "CN".equals(locale.getCountry()); }

    static String download(String tag, boolean mirror) {
        if (!VERSION.matcher(tag).matches()) throw new IllegalArgumentException("Invalid release tag");
        return (mirror ? MIRROR : "") + RELEASES + "/download/" + tag + "/Asteria.apk";
    }

    static String check(String installed, boolean mirrorFirst) throws Exception {
        try {
            return readRelease(installed, mirrorFirst);
        } catch (Exception firstFailure) {
            return readRelease(installed, !mirrorFirst);
        }
    }

    private static String readRelease(String installed, boolean mirror) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL((mirror ? MIRROR : "") + LATEST).openConnection();
        try {
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(4500);
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("User-Agent", "Asteria-Android");
            if (connection.getResponseCode() != 200) throw new IOException("Release lookup failed");
            try (InputStream in = connection.getInputStream()) {
                return newerRelease(installed, new JSONObject(new String(DeepSeek.read(in, 512 * 1024), StandardCharsets.UTF_8)));
            }
        } finally {
            connection.disconnect();
        }
    }

    static String newerRelease(String installed, JSONObject release) {
        String tag = release.optString("tag_name");
        if (release.optBoolean("draft") || release.optBoolean("prerelease") || compare(tag, installed) <= 0) return "";
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) return "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && "Asteria.apk".equals(asset.optString("name"))
                    && download(tag, false).equals(asset.optString("browser_download_url"))) return tag;
        }
        return "";
    }

    private static int compare(String tag, String installed) {
        if (!VERSION.matcher(tag).matches() || !VERSION.matcher(installed).matches()) return 0;
        String[] left = tag.replaceFirst("^v", "").split("\\.");
        String[] right = installed.replaceFirst("^v", "").split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? Integer.parseInt(left[i]) : 0;
            int b = i < right.length ? Integer.parseInt(right[i]) : 0;
            if (a != b) return Integer.compare(a, b);
        }
        return 0;
    }
}
