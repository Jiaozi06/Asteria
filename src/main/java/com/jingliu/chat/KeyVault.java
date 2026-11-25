package com.jingliu.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONArray;
import org.json.JSONObject;

final class KeyVault {
    private static final String ALIAS = "jingliu.deepseek.aes";
    private final SharedPreferences storage;
    private final Map<String, Integer> positions = new HashMap<>();
    private static final String LEGACY = "legacy";

    enum Mode {
        ROUND_ROBIN("轮询"), RANDOM("随机");

        final String label;
        Mode(String label) { this.label = label; }
    }

    static final class Credential {
        final String id, value;
        Credential(String id, String value) { this.id = id; this.value = value; }
    }

    static final class Entry {
        final String id, display;
        Entry(String id, String display) { this.id = id; this.display = display; }
    }

    KeyVault(Context context) {
        storage = context.getSharedPreferences("vault", Context.MODE_PRIVATE);
    }

    private SecretKey secret() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key = (SecretKey) store.getKey(ALIAS, null);
        if (key != null) return key;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build());
        return generator.generateKey();
    }

    synchronized String sealProxyCredentials(String username, String password) throws Exception {
        if (username.isEmpty()) return "";
        JSONObject credentials = new JSONObject().put("username", username).put("password", password);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        byte[] encoded = cipher.doFinal(credentials.toString().getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." + Base64.encodeToString(encoded, Base64.NO_WRAP);
    }

    synchronized java.net.PasswordAuthentication openProxyCredentials(String stored) throws Exception {
        if (stored.isEmpty()) return null;
        String[] parts = stored.split("[.]", 2);
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encoded = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, iv));
        JSONObject credentials = new JSONObject(new String(cipher.doFinal(encoded), StandardCharsets.UTF_8));
        return new java.net.PasswordAuthentication(credentials.getString("username"), credentials.getString("password").toCharArray());
    }

    private String prefix(String endpoint) {
        if (endpoint.isEmpty()) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP | Base64.URL_SAFE) + ".";
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private void write(String endpoint, List<Credential> keys) throws Exception {
        String prefix = prefix(endpoint);
        JSONArray entries = new JSONArray();
        for (Credential key : keys) entries.put(new JSONObject().put("id", key.id).put("value", key.value));
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        byte[] encoded = cipher.doFinal(entries.toString().getBytes(StandardCharsets.UTF_8));
        if (!storage.edit().putString(prefix + "ringIv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(prefix + "ring", Base64.encodeToString(encoded, Base64.NO_WRAP))
                .remove(prefix + "iv").remove(prefix + "key").commit()) {
            throw new IllegalStateException("Unable to save encrypted API key");
        }
    }

    private String decrypt(String valueName, String ivName) throws Exception {
        String encoded = storage.getString(valueName, null);
        if (encoded == null) return null;
        byte[] iv = Base64.decode(storage.getString(ivName, ""), Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }

    private List<Credential> load(String endpoint) throws Exception {
        String prefix = prefix(endpoint);
        List<Credential> keys = new ArrayList<>();
        String encryptedList = decrypt(prefix + "ring", prefix + "ringIv");
        if (encryptedList != null) {
            JSONArray entries = new JSONArray(encryptedList);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject entry = entries.getJSONObject(i);
                keys.add(new Credential(entry.getString("id"), entry.getString("value")));
            }
        } else {
            String previous = decrypt(prefix + "key", prefix + "iv");
            if (previous != null && !previous.isEmpty()) keys.add(new Credential(LEGACY, previous));
        }
        return keys;
    }

    synchronized void save(String endpoint, String value) throws Exception {
        List<Credential> keys = load(endpoint);
        for (Credential item : keys) if (item.value.equals(value)) return;
        keys.add(new Credential(UUID.randomUUID().toString(), value));
        write(endpoint, keys);
    }

    synchronized String read(String endpoint) {
        Credential key = forAttachment(endpoint, "");
        return key == null ? "" : key.value;
    }

    synchronized List<Credential> balanceCredentials() throws Exception { return load(""); }

    synchronized Credential forAttachment(String endpoint, String id) {
        try {
            for (Credential key : load(endpoint)) if (id.isEmpty() || key.id.equals(id)) return key;
        } catch (Exception ignored) { }
        return null;
    }

    synchronized String next(String endpoint) throws Exception {
        List<Credential> keys = load(endpoint);
        if (keys.isEmpty()) return "";
        if (mode(endpoint) == Mode.RANDOM) return keys.get(ThreadLocalRandom.current().nextInt(keys.size())).value;
        int index = positions.getOrDefault(endpoint, 0) % keys.size();
        positions.put(endpoint, (index + 1) % keys.size());
        return keys.get(index).value;
    }

    synchronized List<Entry> entries(String endpoint) throws Exception {
        int visible = visibleLength(endpoint);
        List<Entry> result = new ArrayList<>();
        for (Credential key : load(endpoint)) result.add(new Entry(key.id, mask(key.value, visible)));
        return result;
    }

    static String mask(String value, int visible) {
        int length = value.codePointCount(0, value.length());
        int count = Math.min(Math.max(0, (length - 4) / 2), visible);
        return value.substring(0, value.offsetByCodePoints(0, count)) + "••••"
            + value.substring(value.offsetByCodePoints(value.length(), -count));
    }

    synchronized void remove(String endpoint, String id) throws Exception {
        List<Credential> keys = load(endpoint);
        if (keys.removeIf(key -> key.id.equals(id))) {
            write(endpoint, keys);
            positions.remove(endpoint);
        }
    }

    Mode mode(String endpoint) {
        return "RANDOM".equals(storage.getString(prefix(endpoint) + "mode", "")) ? Mode.RANDOM : Mode.ROUND_ROBIN;
    }

    synchronized boolean setMode(String endpoint, Mode mode) {
        positions.remove(endpoint);
        return storage.edit().putString(prefix(endpoint) + "mode", mode.name()).commit();
    }

    int visibleLength(String endpoint) {
        return Math.max(1, Math.min(8, storage.getInt(prefix(endpoint) + "visibleLength", 4)));
    }

    synchronized boolean setVisibleLength(String endpoint, int count) {
        return storage.edit().putInt(prefix(endpoint) + "visibleLength", count).commit();
    }
}
