package com.jingliu.chat;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class KeyVault {
    private static final String ALIAS = "jingliu.deepseek.aes";
    private final SharedPreferences storage;

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

    private String prefix(String endpoint) {
        if (endpoint.isEmpty()) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hash, Base64.NO_WRAP | Base64.URL_SAFE) + ".";
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    synchronized void save(String endpoint, String text) throws Exception {
        String prefix = prefix(endpoint);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secret());
        byte[] encoded = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        if (!storage.edit().putString(prefix + "iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(prefix + "key", Base64.encodeToString(encoded, Base64.NO_WRAP)).commit()) {
            throw new IllegalStateException("Unable to save encrypted API key");
        }
    }

    synchronized String read(String endpoint) {
        try {
            String prefix = prefix(endpoint);
            String encoded = storage.getString(prefix + "key", null);
            if (encoded == null) return "";
            byte[] iv = Base64.decode(storage.getString(prefix + "iv", ""), Base64.NO_WRAP);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secret(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.NO_WRAP)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    synchronized boolean clear(String endpoint) {
        String prefix = prefix(endpoint);
        return storage.edit().remove(prefix + "iv").remove(prefix + "key").commit();
    }
}
