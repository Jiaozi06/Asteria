package com.jingliu.chat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.SystemClock;
import android.util.Base64;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.jingliu.chat.ChatState.string;

final class MemoryNebula extends SQLiteOpenHelper {
    private static final int MAX_SOURCE_CHARS = 1000;
    private static final int MAX_BATCH_MESSAGES = 8;
    private static final Pattern SENTENCES = Pattern.compile("(?<=[。！？!?；;])|\\n+|(?<=\\.)\\s+");
    private static final Pattern SENSITIVE = Pattern.compile(
        "(?iu)(密码|口令|验证码|身份证|护照|银行卡|住址|家庭地址|手机号|私钥|助记词|病例|病历|确诊|支付密码|密钥|访问令牌|"
        + "\\b(?:api[ _-]?key|password|passphrase|bearer|secret|passport|credit card|private key|medical record|token)\\b|"
        + "[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}|\\b(?:\\+?\\d[ .()\\-]*){7,}|https?://|\\b(?:sk|ghp|gho)[_-][A-Z0-9_-]{8,})");
    private static final Pattern QUOTED_CONTEXT = Pattern.compile(
        "(?iu)(\u0060\u0060\u0060|角色扮演|你扮演|请扮演|虚构场景|翻译以下|引用如下|"
        + "\\b(?:roleplay|pretend|fictional|system prompt)\\b)");

    enum Kind {
        TOPIC("聊天话题", 0xff83b2ff), PREFERENCE("兴趣偏好", 0xffc8a3ff),
        HABIT("日常习惯", 0xff78e0d7), PERSONALITY("性格自述", 0xffefa6ca), DETAIL("小细节", 0xffffcf85);

        final String label;
        final int color;
        Kind(String label, int color) { this.label = label; this.color = color; }
    }

    static final class Entry {
        final String id, title, detail;
        final Kind kind;
        final int mentions;
        final long updated;

        Entry(String id, Kind kind, String title, String detail, int mentions, long updated) {
            this.id = id;
            this.kind = kind;
            this.title = title;
            this.detail = detail;
            this.mentions = mentions;
            this.updated = updated;
        }
    }

    static final class Snapshot {
        final List<Entry> entries;
        final boolean hasLegacy;

        Snapshot(List<Entry> entries, boolean hasLegacy) {
            this.entries = entries;
            this.hasLegacy = hasLegacy;
        }
    }

    static final class Progress {
        final int scanned, totalChats, saved, batches, deferred, unreadable;
        final boolean requesting;

        Progress(int scanned, int totalChats, int saved, int batches, int deferred, int unreadable, boolean requesting) {
            this.scanned = scanned;
            this.totalChats = totalChats;
            this.saved = saved;
            this.batches = batches;
            this.deferred = deferred;
            this.unreadable = unreadable;
            this.requesting = requesting;
        }
    }

    private static final class Source {
        final String id, text;
        final boolean legacy;

        Source(String id, String text, boolean legacy) {
            this.id = id;
            this.text = text;
            this.legacy = legacy;
        }
    }

    interface Messages { JSONArray read(String chatId) throws JSONException; }
    interface Summarize { List<NebulaSummaries.Summary> run(List<String> texts) throws Exception; }

    MemoryNebula(Context context) { super(context, "memory-nebula.db", null, 2); }

    @Override public void onCreate(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE memory (id TEXT PRIMARY KEY NOT NULL, kind TEXT NOT NULL, title TEXT NOT NULL, "
            + "detail TEXT NOT NULL, mentions INTEGER NOT NULL, updated INTEGER NOT NULL)");
        database.execSQL("CREATE TABLE seen (id TEXT PRIMARY KEY NOT NULL)");
        database.execSQL("CREATE TABLE dismissed (id TEXT PRIMARY KEY NOT NULL)");
        createLegacyTable(database);
    }

    private static void createLegacyTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE legacy_notes (id TEXT PRIMARY KEY NOT NULL, text TEXT NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase database, int from, int to) {
        if (from == 1) {
            createLegacyTable(database);
            database.execSQL("INSERT INTO legacy_notes SELECT id, CASE WHEN detail='' THEN title ELSE detail END FROM memory");
            database.delete("memory", null, null);
            database.delete("seen", null, null);
            database.delete("dismissed", null, null);
        }
    }

    Snapshot snapshot() {
        SQLiteDatabase database = getReadableDatabase();
        List<Entry> result = new ArrayList<>();
        try (Cursor rows = database.rawQuery(
                "SELECT id, kind, title, detail, mentions, updated FROM memory ORDER BY updated DESC, mentions DESC, rowid DESC", null)) {
            while (rows.moveToNext()) {
                result.add(new Entry(rows.getString(0), Kind.valueOf(rows.getString(1)), rows.getString(2), rows.getString(3),
                    rows.getInt(4), rows.getLong(5)));
            }
        }
        try (Cursor rows = database.rawQuery("SELECT 1 FROM legacy_notes LIMIT 1", null)) {
            return new Snapshot(result, rows.moveToFirst());
        }
    }

    void remember(String chatId, int index, String text, int batchChars, Summarize summarize, BooleanSupplier cancelled)
            throws Exception {
        Batch batch = new Batch(batchChars, summarize, cancelled, null, 0);
        batch.add(source(chatId, index, text, batchChars));
        batch.flush();
    }

    Progress collect(List<String> chatIds, Messages messages, int batchChars, Summarize summarize, BooleanSupplier cancelled,
            Consumer<Progress> progress) throws Exception {
        Batch batch = new Batch(batchChars, summarize, cancelled, progress, chatIds.size());
        for (String id : chatIds) {
            batch.checkCancelled();
            JSONArray chat;
            try {
                chat = messages.read(id);
            } catch (JSONException error) {
                chat = null;
                batch.unreadable++;
            }
            if (chat != null) {
                for (int index = 0; index < chat.length(); index++) {
                    JSONObject message = chat.optJSONObject(index);
                    if ("user".equals(string(message, "role"))) {
                        batch.add(source(id, index, string(message, "text"), batchChars));
                    }
                }
            }
            batch.scanned++;
            batch.publish(false, false);
        }
        List<Source> legacy = new ArrayList<>();
        try (Cursor rows = batch.database.rawQuery("SELECT id, text FROM legacy_notes ORDER BY rowid", null)) {
            while (rows.moveToNext()) {
                batch.checkCancelled();
                legacy.add(new Source(rows.getString(0), filtered(rows.getString(1), batchChars), true));
            }
        }
        for (Source source : legacy) batch.add(source);
        batch.flush();
        return batch.publish(false, true);
    }

    void remove(String id) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            if (database.delete("memory", "id=?", new String[]{id}) > 0) mark(database, "dismissed", id);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    void clear() {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete("memory", null, null);
            database.delete("seen", null, null);
            database.delete("dismissed", null, null);
            database.delete("legacy_notes", null, null);
            database.setTransactionSuccessful();
        } finally { database.endTransaction(); }
    }

    private final class Batch {
        final SQLiteDatabase database = getWritableDatabase();
        final List<Source> pending = new ArrayList<>();
        final Set<String> ids = new HashSet<>();
        final int limit;
        final Summarize summarize;
        final BooleanSupplier cancelled;
        final Consumer<Progress> progress;
        final int totalChats;
        int chars, scanned, saved, batches, deferred, unreadable, consecutiveFailures;
        long lastProgress;

        Batch(int limit, Summarize summarize, BooleanSupplier cancelled, Consumer<Progress> progress, int totalChats) {
            this.limit = limit;
            this.summarize = summarize;
            this.cancelled = cancelled;
            this.progress = progress;
            this.totalChats = totalChats;
        }

        void checkCancelled() {
            if (cancelled.getAsBoolean()) throw new CancellationException();
        }

        void add(Source source) throws Exception {
            checkCancelled();
            if (source.text.isEmpty()) {
                if (source.legacy) database.delete("legacy_notes", "id=?", new String[]{source.id});
                return;
            }
            if (ids.contains(source.id) || (!source.legacy && contains(database, "seen", source.id))) return;
            if (!pending.isEmpty() && (pending.size() == MAX_BATCH_MESSAGES || chars + source.text.length() > limit)) flush();
            ids.add(source.id);
            pending.add(source);
            chars += source.text.length();
        }

        void flush() throws Exception {
            checkCancelled();
            if (pending.isEmpty()) return;
            summarizeAndSave(pending);
            pending.clear();
            ids.clear();
            chars = 0;
        }

        Progress publish(boolean requesting, boolean force) {
            long now = SystemClock.elapsedRealtime();
            if (!force && (progress == null || now - lastProgress < 150)) return null;
            lastProgress = now;
            Progress value = new Progress(scanned, totalChats, saved, batches, deferred, unreadable, requesting);
            if (progress != null) progress.accept(value);
            return value;
        }

        private void summarizeAndSave(List<Source> sources) throws Exception {
            checkCancelled();
            List<String> inputs = new ArrayList<>();
            for (Source source : sources) inputs.add(source.text);
            if (progress != null) publish(true, true);
            List<NebulaSummaries.Summary> summaries;
            try {
                summaries = summarize.run(inputs);
            } catch (Exception error) {
                checkCancelled();
                if (progress == null || !splittable(error)) throw error;
                if (sources.size() > 1) {
                    int middle = sources.size() / 2;
                    summarizeAndSave(sources.subList(0, middle));
                    summarizeAndSave(sources.subList(middle, sources.size()));
                } else {
                    if (error instanceof SocketTimeoutException || ++consecutiveFailures >= 3) throw error;
                    deferred++;
                    publish(false, true);
                }
                return;
            }
            database.beginTransaction();
            try {
                long now = System.currentTimeMillis();
                for (NebulaSummaries.Summary summary : summaries) store(database, summary, now);
                for (Source source : sources) {
                    if (source.legacy) database.delete("legacy_notes", "id=?", new String[]{source.id});
                    else mark(database, "seen", source.id);
                }
                database.setTransactionSuccessful();
            } finally { database.endTransaction(); }
            saved += sources.size();
            batches++;
            consecutiveFailures = 0;
            if (progress != null) publish(false, true);
        }
    }

    private static boolean splittable(Exception error) {
        return error instanceof JSONException || error instanceof SocketTimeoutException
            || (error instanceof DeepSeek.ApiError && ((DeepSeek.ApiError) error).contextLimit);
    }

    private static void store(SQLiteDatabase database, NebulaSummaries.Summary summary, long now) {
        String id = digest(summary.kind.name() + '\n' + summary.subject);
        if (contains(database, "dismissed", id)) return;
        ContentValues values = new ContentValues();
        values.put("id", id);
        values.put("kind", summary.kind.name());
        values.put("title", summary.title);
        values.put("detail", summary.detail);
        values.put("mentions", summary.sources.size());
        values.put("updated", now);
        if (database.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_IGNORE) < 0) {
            database.execSQL("UPDATE memory SET title=?, detail=?, mentions=mentions+?, updated=? WHERE id=?",
                new Object[]{summary.title, summary.detail, summary.sources.size(), now, id});
        }
    }

    private static boolean contains(SQLiteDatabase database, String table, String id) {
        try (Cursor rows = database.rawQuery("SELECT 1 FROM " + table + " WHERE id=?", new String[]{id})) {
            return rows.moveToFirst();
        }
    }

    private static void mark(SQLiteDatabase database, String table, String id) {
        ContentValues values = new ContentValues();
        values.put("id", id);
        database.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static Source source(String chatId, int index, String text, int max) {
        String id = digest(chatId + '\n' + index + '\n' + text.length() + '\n' + limit(text, 2400));
        return new Source(id, filtered(text, max), false);
    }

    private static String filtered(String text, int max) {
        String sample = normalize(limit(text, 8000));
        if (QUOTED_CONTEXT.matcher(sample).find()) return "";
        StringBuilder safe = new StringBuilder();
        for (String raw : SENTENCES.split(sample)) {
            String sentence = raw.trim();
            if (sentence.isEmpty() || sensitive(sentence)) continue;
            if (safe.length() > 0) safe.append('\n');
            safe.append(sentence);
            if (safe.length() >= max || safe.length() >= MAX_SOURCE_CHARS) break;
        }
        return limit(safe.toString(), Math.min(max, MAX_SOURCE_CHARS));
    }

    private static String limit(String text, int max) {
        if (text.length() <= max) return text;
        return text.substring(0, Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max);
    }

    static boolean sensitive(String text) { return SENSITIVE.matcher(text).find(); }

    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replace('’', '\'').replaceAll("[\\t\\p{Zs}]+", " ").trim();
    }

    private static String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(Arrays.copyOf(hash, 16), Base64.NO_PADDING | Base64.NO_WRAP | Base64.URL_SAFE);
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
