package com.jingliu.chat;

import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static java.nio.charset.StandardCharsets.UTF_8;

final class ConversationStorage {
    private final AtomicFile file;

    ConversationStorage(AtomicFile file) { this.file = file; }

    synchronized JSONObject load() {
        try (JsonReader reader = new JsonReader(new InputStreamReader(file.openRead(), UTF_8))) {
            JSONObject state = readObject(reader, 0);
            if (reader.peek() == JsonToken.END_DOCUMENT) return state;
        } catch (IOException | JSONException | IllegalStateException | NumberFormatException ignored) { }
        return new JSONObject();
    }

    synchronized boolean save(JSONObject data) {
        FileOutputStream output = null;
        try {
            output = file.startWrite();
            JsonWriter writer = new JsonWriter(new BufferedWriter(new OutputStreamWriter(output, UTF_8)));
            write(writer, data);
            writer.flush();
            file.finishWrite(output);
            return true;
        } catch (IOException | IllegalArgumentException ignored) {
            if (output != null) file.failWrite(output);
            return false;
        }
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

    private static void write(JsonWriter writer, Object value) throws IOException {
        if (value == null || value == JSONObject.NULL) writer.nullValue();
        else if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            writer.beginObject();
            for (Iterator<String> names = object.keys(); names.hasNext();) {
                String name = names.next();
                writer.name(name);
                write(writer, object.opt(name));
            }
            writer.endObject();
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            writer.beginArray();
            for (int i = 0; i < array.length(); i++) write(writer, array.opt(i));
            writer.endArray();
        } else if (value instanceof Number) writer.value((Number) value);
        else if (value instanceof Boolean) writer.value((Boolean) value);
        else writer.value(value.toString());
    }
}
