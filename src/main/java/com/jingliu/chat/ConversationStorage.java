package com.jingliu.chat;

import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static java.nio.charset.StandardCharsets.UTF_8;
import static com.jingliu.chat.ChatState.*;

final class ConversationStorage {
    private static final int MESSAGE_CHUNK = 256 * 1024;
    private final AtomicFile legacy;
    private final File databaseFile;
    private final Map<String, String> savedMetadata = new HashMap<>();
    private SQLiteDatabase database;
    private String savedSettings;

    ConversationStorage(File directory) {
        legacy = new AtomicFile(new File(directory, "conversations.json"));
        databaseFile = new File(directory, "conversations.db");
    }

    synchronized JSONObject load() throws IOException, JSONException {
        if (database == null) {
            database = SQLiteDatabase.openOrCreateDatabase(databaseFile, null);
            database.enableWriteAheadLogging();
            database.execSQL("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            database.execSQL("CREATE TABLE IF NOT EXISTS chats (id TEXT PRIMARY KEY NOT NULL, metadata TEXT NOT NULL, "
                + "messages BLOB NOT NULL, search_text TEXT NOT NULL)");
        }
        database.beginTransaction();
        try {
            try (Cursor rows = database.rawQuery("SELECT value FROM settings WHERE id=1", null)) {
                savedSettings = rows.moveToFirst() ? rows.getString(0) : null;
            }
            if (savedSettings == null) {
                savedSettings = migrateLegacy().toString();
                writeSettings(savedSettings);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        legacy.delete();
        JSONObject state = new JSONObject(savedSettings);
        JSONArray chats = new JSONArray();
        try (Cursor rows = database.rawQuery("SELECT id, metadata FROM chats ORDER BY rowid", null)) {
            savedMetadata.clear();
            while (rows.moveToNext()) {
                String metadata = rows.getString(1);
                chats.put(new JSONObject(metadata));
                savedMetadata.put(rows.getString(0), metadata);
            }
        }
        put(state, "chats", chats);
        return state;
    }

    private JSONObject migrateLegacy() throws IOException, JSONException {
        JSONObject settings = new JSONObject();
        try (JsonReader reader = new JsonReader(new InputStreamReader(legacy.openRead(), UTF_8))) {
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("chats".equals(name) && reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        Object entry = readValue(reader, 1);
                        if (entry instanceof JSONObject && !string((JSONObject) entry, "id").isEmpty()) {
                            JSONObject chat = (JSONObject) entry;
                            insertChat(chat, without(chat, "messages").toString());
                        }
                    }
                    reader.endArray();
                } else settings.put(name, readValue(reader, 1));
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Invalid conversation data");
        } catch (FileNotFoundException missing) {
            if (legacy.getBaseFile().exists()) throw missing;
        }
        return settings;
    }

    synchronized boolean save(JSONObject data, boolean saveActiveMessages) {
        Map<String, String> next = new HashMap<>();
        String settings = without(data, "chats").toString();
        String active = string(data, "active");
        try {
            database.beginTransaction();
            try {
                if (!settings.equals(savedSettings)) writeSettings(settings);
                for (JSONObject chat : objects(array(data, "chats"))) {
                    String id = string(chat, "id");
                    String metadata = without(chat, "messages").toString();
                    next.put(id, metadata);
                    String previous = savedMetadata.get(id);
                    if (previous == null) insertChat(chat, metadata);
                    else {
                        ContentValues changes = new ContentValues();
                        if (!metadata.equals(previous)) changes.put("metadata", metadata);
                        if (saveActiveMessages && id.equals(active)) writeMessages(changes, array(chat, "messages"));
                        if (changes.size() != 0) database.update("chats", changes, "id=?", new String[]{id});
                    }
                }
                for (String id : savedMetadata.keySet()) {
                    if (!next.containsKey(id)) database.delete("chats", "id=?", new String[]{id});
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        } catch (SQLiteException | IllegalArgumentException ignored) { return false; }
        savedSettings = settings;
        savedMetadata.clear();
        savedMetadata.putAll(next);
        return true;
    }

    synchronized List<JSONObject> importChats(List<JSONObject> chats) {
        List<JSONObject> imported = new ArrayList<>();
        Map<String, String> metadata = new HashMap<>();
        database.beginTransaction();
        try {
            for (JSONObject chat : chats) {
                JSONObject header = without(chat, "messages");
                String serialized = header.toString();
                insertChat(chat, serialized);
                imported.add(header);
                metadata.put(string(chat, "id"), serialized);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        savedMetadata.putAll(metadata);
        return imported;
    }

    JSONArray messages(String id) throws JSONException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int offset = 1; ; offset += MESSAGE_CHUNK) {
            String[] args = {String.valueOf(offset), id};
            try (Cursor rows = database.rawQuery("SELECT substr(messages, ?, " + MESSAGE_CHUNK + ") FROM chats WHERE id=?", args)) {
                if (!rows.moveToFirst()) return null;
                byte[] chunk = rows.getBlob(0);
                output.write(chunk, 0, chunk.length);
                if (chunk.length < MESSAGE_CHUNK) return new JSONArray(new String(output.toByteArray(), UTF_8));
            }
        }
    }

    Set<String> containing(String query) {
        Set<String> matches = new HashSet<>();
        try (Cursor rows = database.rawQuery("SELECT id FROM chats WHERE instr(search_text, ?) > 0", new String[]{query})) {
            while (rows.moveToNext()) matches.add(rows.getString(0));
        }
        return matches;
    }

    synchronized void close() { if (database != null) database.close(); }

    private void writeSettings(String value) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("value", value);
        if (database.insertWithOnConflict("settings", null, values, SQLiteDatabase.CONFLICT_REPLACE) < 0) {
            throw new SQLiteException("Cannot save settings");
        }
    }

    private void insertChat(JSONObject chat, String metadata) {
        ContentValues values = new ContentValues();
        values.put("id", string(chat, "id"));
        values.put("metadata", metadata);
        writeMessages(values, array(chat, "messages"));
        database.insertOrThrow("chats", null, values);
    }

    private static void writeMessages(ContentValues values, JSONArray messages) {
        values.put("messages", messages.toString().getBytes(UTF_8));
        StringBuilder searchable = new StringBuilder();
        for (JSONObject message : objects(messages)) searchable.append(answer(message)).append('\n');
        values.put("search_text", searchable.toString().toLowerCase(Locale.ROOT));
    }

    private static JSONObject without(JSONObject source, String key) {
        JSONObject copy = new JSONObject();
        for (Iterator<String> names = source.keys(); names.hasNext();) {
            String name = names.next();
            if (!key.equals(name)) put(copy, name, source.opt(name));
        }
        return copy;
    }

    private static JSONObject readObject(JsonReader reader, int depth) throws IOException, JSONException {
        JSONObject object = new JSONObject();
        reader.beginObject();
        while (reader.hasNext()) object.put(reader.nextName(), readValue(reader, depth + 1));
        reader.endObject();
        return object;
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException, JSONException {
        if (depth > 256) throw new IOException("Stored JSON is too deeply nested");
        switch (reader.peek()) {
            case BEGIN_OBJECT: return readObject(reader, depth);
            case BEGIN_ARRAY:
                JSONArray array = new JSONArray();
                reader.beginArray();
                while (reader.hasNext()) array.put(readValue(reader, depth + 1));
                reader.endArray();
                return array;
            case STRING: return reader.nextString();
            case NUMBER:
                String number = reader.nextString();
                try { return Long.valueOf(number); }
                catch (NumberFormatException ignored) { return Double.valueOf(number); }
            case BOOLEAN: return reader.nextBoolean();
            case NULL:
                reader.nextNull();
                return JSONObject.NULL;
            default: throw new IOException("Invalid stored JSON");
        }
    }

}
