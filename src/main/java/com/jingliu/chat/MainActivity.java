package com.jingliu.chat;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Rect;
import android.media.*;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final int SELECT_FILE = 10;
    private static final int RECORD_PERMISSION = 11;
    private static final long MIB = 1024 * 1024;
    private static boolean startedInProcess;
    private final ExecutorService worker = Executors.newCachedThreadPool();
    private final Map<Integer, DeepSeek.Request> streams = new ConcurrentHashMap<>();
    private NativeChat screen;
    private FrameLayout root;
    private KeyVault vault;
    private AtomicFile stateFile;
    private final Services services = new Services();
    private final RequestLog requestLog = new RequestLog();
    private Recording recording;
    private String pendingUploadKey = "";
    private SpeechRecognizer dictation;
    private boolean voicePermissionSystem, voicePermissionHold;
    private PhoneSpeech speech;
    private boolean checkingUpdate, manualUpdate;

    @Override protected void onStart() {
        super.onStart();
        services.checkUpdate(false);
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        vault = new KeyVault(this);
        speech = new PhoneSpeech(this, id -> emit("speech", json("id", id)),
            message -> emit("toast", json("message", message)));
        stateFile = new AtomicFile(new File(getFilesDir(), "conversations.json"));
        root = new FrameLayout(this);
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (screen != null) screen.updateInputHint();
        });
        if (Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                int ime = insets.getInsets(WindowInsets.Type.ime()).bottom;
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime));
                return insets;
            });
        }
        setContentView(root);
        screen = new NativeChat(this, root, services, !startedInProcess);
        startedInProcess = true;
        screen.show();
    }

    @Override public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        screen.refreshTheme();
    }

    boolean systemDark() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    void bars(boolean dark, int color) {
        root.setBackgroundColor(color);
        getWindow().setStatusBarColor(color);
        getWindow().setNavigationBarColor(color);
        if (Build.VERSION.SDK_INT >= 30) {
            int flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(dark ? 0 : flags, flags);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(dark ? 0 :
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    @Override public void onBackPressed() {
        if (!screen.back()) super.onBackPressed();
    }

    @Override protected void onStop() {
        super.onStop();
        if (recording != null) {
            recording.discard = true;
            recording.active = false;
        }
        endDictation(true);
        speech.stop();
    }

    @Override protected void onDestroy() {
        speech.shutdown();
        for (DeepSeek.Request request : streams.values()) cancel(request);
        worker.shutdown();
        super.onDestroy();
    }

    private void cancel(DeepSeek.Request request) {
        if (request != null) worker.execute(request::cancel);
    }

    private void emit(String type, JSONObject data) {
        runOnUiThread(() -> {
            if (!isDestroyed()) screen.event(type, data);
        });
    }

    static JSONObject json(Object... fields) {
        JSONObject result = new JSONObject();
        try {
            for (int i = 0; i < fields.length; i += 2) result.put((String) fields[i], fields[i + 1]);
        } catch (Exception e) { throw new IllegalArgumentException(e); }
        return result;
    }

    private static String failure(Exception error) {
        if (error instanceof DeepSeek.ApiError) return error.getMessage();
        if (error instanceof SocketTimeoutException) return "连接超时，请重试。";
        if (error instanceof IOException) return "连接或文件读取失败，请重试。";
        return "操作失败，请重试。";
    }

    private String requireKey() throws DeepSeek.ApiError {
        String key = vault.read("");
        if (key.isEmpty()) throw new DeepSeek.ApiError("请先配置 DeepSeek API Key。");
        return key;
    }

    private DeepSeek.Request apiRequest(ApiProvider provider) {
        SharedPreferences settings = getPreferences(MODE_PRIVATE);
        if (!settings.getBoolean("socksEnabled_" + provider.name(), false)) return new DeepSeek.Request(null, null, false);
        Proxy proxy = new Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(
            settings.getString("socksHost_" + provider.name(), ""), settings.getInt("socksPort_" + provider.name(), 1080)));
        try {
            return new DeepSeek.Request(proxy, services.proxyCredentials(provider), false);
        } catch (Exception error) {
            return new DeepSeek.Request(proxy, null, true);
        }
    }

    final class Services {
        boolean proxyEnabled(ApiProvider provider) {
            return getPreferences(MODE_PRIVATE).getBoolean("socksEnabled_" + provider.name(), false);
        }

        String proxyHost(ApiProvider provider) {
            return getPreferences(MODE_PRIVATE).getString("socksHost_" + provider.name(), "");
        }

        int proxyPort(ApiProvider provider) {
            return getPreferences(MODE_PRIVATE).getInt("socksPort_" + provider.name(), 1080);
        }

        PasswordAuthentication proxyCredentials(ApiProvider provider) throws Exception {
            return vault.openProxyCredentials(getPreferences(MODE_PRIVATE).getString("socksCredentials_" + provider.name(), ""));
        }

        void disableProxy(ApiProvider provider) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("socksEnabled_" + provider.name(), false).apply();
        }

        boolean saveProxy(ApiProvider provider, String host, int port, String username, String password) {
            try {
                String credentials = vault.sealProxyCredentials(username, password);
                return getPreferences(MODE_PRIVATE).edit().putBoolean("socksEnabled_" + provider.name(), true)
                    .putString("socksHost_" + provider.name(), host).putInt("socksPort_" + provider.name(), port)
                    .putString("socksCredentials_" + provider.name(), credentials).commit();
            } catch (Exception error) {
                return false;
            }
        }

        void checkUpdate(boolean manual) {
            manualUpdate |= manual;
            if (checkingUpdate) return;
            checkingUpdate = true;
            worker.execute(() -> {
                Optional<AppUpdate.Release> update = null;
                try {
                    String installed = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    update = AppUpdate.check(installed, AppUpdate.preferMirror(Locale.getDefault()));
                } catch (Exception ignored) { }
                Optional<AppUpdate.Release> result = update;
                runOnUiThread(() -> {
                    checkingUpdate = false;
                    boolean requested = manualUpdate;
                    manualUpdate = false;
                    if (!isDestroyed() && !isFinishing()) screen.updateAvailable(result, requested);
                });
            });
        }

        String loadState() {
            synchronized (stateFile) {
                try { return new String(stateFile.readFully(), StandardCharsets.UTF_8); }
                catch (IOException ignored) { return "{}"; }
            }
        }

        boolean saveState(String value) {
            synchronized (stateFile) {
                FileOutputStream out = null;
                try {
                    out = stateFile.startWrite();
                    out.write(value.getBytes(StandardCharsets.UTF_8));
                    stateFile.finishWrite(out);
                    return true;
                } catch (IOException ignored) {
                    if (out != null) stateFile.failWrite(out);
                    return false;
                }
            }
        }

        boolean hasKey(String endpoint) { return !vault.read(endpoint).isEmpty(); }

        List<KeyVault.Entry> savedKeys(String endpoint) throws Exception { return vault.entries(endpoint); }

        KeyVault.Mode keyMode(String endpoint) { return vault.mode(endpoint); }

        boolean setKeyMode(String endpoint, KeyVault.Mode mode) { return vault.setMode(endpoint, mode); }

        int keyVisibleLength(String endpoint) { return vault.visibleLength(endpoint); }

        boolean setKeyVisibleLength(String endpoint, int count) { return vault.setVisibleLength(endpoint, count); }

        String addKey(String endpoint, String key) {
            try { vault.save(endpoint, key); return ""; }
            catch (Exception error) { return failure(error).replace(key, "••••"); }
        }

        String removeKey(String endpoint, String id) {
            try { vault.remove(endpoint, id); return ""; }
            catch (Exception error) { return "无法移除已保存的密钥"; }
        }

        void discoverModels(int id, ApiProvider provider, String endpoint, String candidate) {
            worker.execute(() -> {
                DeepSeek.Request request = apiRequest(provider);
                try {
                    String key = candidate.isEmpty() ? vault.read(endpoint) : candidate;
                    HttpURLConnection connection = endpoint.isEmpty() ? request.open("/models", "GET", key)
                        : request.custom(endpoint, "/models", "GET", key);
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(15_000);
                    JSONArray models = DeepSeek.json(connection).getJSONArray("data");
                    TreeSet<String> items = new TreeSet<>();
                    for (int i = 0; i < models.length() && items.size() < 2000; i++) {
                        JSONObject model = models.optJSONObject(i);
                        if (model == null) continue;
                        String name = model.optString("id").trim();
                        if (!name.isEmpty() && name.length() <= 240) items.add(name);
                    }
                    emit("models", json("id", id, "items", new JSONArray(items)));
                } catch (Exception error) {
                    emit("models", json("id", id, "error", endpoint.isEmpty() ? "未能获取模型，仍可使用默认列表。" :
                        "未能获取上游模型，可手动填写模型 ID。"));
                } finally { request.close(); }
            });
        }

        void verify(ApiProvider provider, String endpoint, String model, String candidate, boolean test) {
            worker.execute(() -> {
                DeepSeek.Request request = apiRequest(provider);
                String key = candidate.isEmpty() ? vault.read(endpoint) : candidate;
                try {
                    if (ApiProvider.requiresKey(endpoint) && key.isEmpty()) throw new DeepSeek.ApiError("请先填写 API Key。");
                    if (test && ApiProvider.requiresKey(endpoint)) {
                        HttpURLConnection connection = endpoint.isEmpty() ? request.open("/models", "GET", key)
                            : request.custom(endpoint, "/models", "GET", key);
                        connection.setReadTimeout(25_000);
                        DeepSeek.json(connection).getJSONArray("data");
                    } else if (test) {
                        HttpURLConnection connection = request.custom(endpoint, "/chat/completions", "POST", key);
                        connection.setReadTimeout(30_000);
                        DeepSeek.jsonBody(connection, json("model", model, "max_tokens", 1, "stream", false,
                            "messages", new org.json.JSONArray().put(json("role", "user", "content", "Hi"))).toString());
                        DeepSeek.json(connection).getJSONArray("choices");
                    }
                    if (!candidate.isEmpty()) vault.save(endpoint, key);
                    String message = !test ? "接口配置已保存" : key.isEmpty() ? "连接成功，接口配置已保存" : "连接成功，API Key 已保存";
                    emit("key", json("endpoint", endpoint, "saved", true, "message", message));
                } catch (Exception e) {
                    String message = failure(e);
                    emit("key", json("endpoint", endpoint, "saved", hasKey(endpoint), "error", key.isEmpty() ? message : message.replace(key, "••••")));
                } finally { request.close(); }
            });
        }

        boolean requestLoggingEnabled() {
            return getPreferences(MODE_PRIVATE).getBoolean("requestLoggingEnabled", false);
        }

        void setRequestLoggingEnabled(boolean enabled) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("requestLoggingEnabled", enabled).apply();
            if (!enabled) requestLog.clear();
        }

        List<RequestLog.Entry> requestLogs(String chatId) { return requestLog.forChat(chatId); }

        void stream(int id, String chatId, ApiProvider provider, String endpoint, String body, boolean compactContext, String attachmentKey) {
            DeepSeek.Request request = apiRequest(provider);
            RequestLog.Entry trace = requestLoggingEnabled() ? requestLog.start(chatId, provider.label) : null;
            request.trace = trace;
            streams.put(id, request);
            worker.execute(() -> {
                String key = "";
                try {
                    if (attachmentKey == null) key = vault.next(endpoint);
                    else {
                        KeyVault.Credential attachment = vault.forAttachment(endpoint, attachmentKey);
                        if (attachment == null) throw new DeepSeek.ApiError("无法使用原附件的密钥，请重新上传附件");
                        key = attachment.value;
                    }
                    if (ApiProvider.requiresKey(endpoint) && key.isEmpty()) throw new DeepSeek.ApiError("请先配置当前接口的 API Key。");
                    DeepSeek.stream(request, endpoint, key, body, compactContext, event -> emit("stream", json("id", id, "event", event)));
                    if (trace != null) trace.finish(request.cancelled(), "", key);
                    emit("streamEnd", json("id", id));
                } catch (Exception e) {
                    String message = failure(e);
                    if (trace != null) trace.finish(request.cancelled(), message, key);
                    emit("streamEnd", json("id", id, "error", key.isEmpty() ? message : message.replace(key, "••••")));
                } finally {
                    request.close();
                    streams.remove(id, request);
                }
            });
        }

        void cancel(int id) { MainActivity.this.cancel(streams.get(id)); }

        void pickFile(boolean imagesOnly, String keyId) {
            runOnUiThread(() -> {
                pendingUploadKey = keyId == null ? "" : keyId;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .setType(imagesOnly ? "image/*" : "*/*").addCategory(Intent.CATEGORY_OPENABLE);
                try { startActivityForResult(intent, SELECT_FILE); }
                catch (ActivityNotFoundException ignored) { emit("upload", json("error", "系统文件选择器不可用。")); }
            });
        }

        boolean beginVoice(boolean system, boolean hold) { return startVoice(system, hold); }

        void endVoice(boolean discard) {
            if (recording != null) {
                recording.discard = discard;
                recording.active = false;
            }
            endDictation(discard);
        }

        void speak(String id, String text) { speech.toggle(id, text); }

        void stopSpeaking() { speech.stop(); }

        void copy(String text) {
            runOnUiThread(() -> getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), text)));
        }

        void share(String text) {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(intent, "分享回复"));
            });
        }

        void open(String target) {
            runOnUiThread(() -> {
                Uri uri = Uri.parse(target);
                if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) return;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
                catch (ActivityNotFoundException ignored) { emit("toast", json("message", "系统浏览器不可用。")); }
            });
        }

        void hideKeyboard() {
            getSystemService(InputMethodManager.class).hideSoftInputFromWindow(root.getWindowToken(), 0);
        }

        void showKeyboard(View editor) {
            editor.requestFocus();
            editor.post(() -> getSystemService(InputMethodManager.class).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT));
        }

        boolean keyboardVisible() {
            WindowInsets insets = root.getRootWindowInsets();
            if (Build.VERSION.SDK_INT >= 30 && insets != null) return insets.isVisible(WindowInsets.Type.ime());
            Rect visible = new Rect();
            root.getWindowVisibleDisplayFrame(visible);
            return getResources().getDisplayMetrics().heightPixels - visible.bottom
                > 120 * getResources().getDisplayMetrics().density;
        }

    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != SELECT_FILE) return;
        String keyId = pendingUploadKey;
        pendingUploadKey = "";
        if (result != RESULT_OK || data == null || data.getData() == null) { emit("upload", json()); return; }
        Uri uri = data.getData();
        worker.execute(() -> upload(uri, keyId));
    }

    private void upload(Uri uri, String keyId) {
        DeepSeek.Request request = apiRequest(ApiProvider.DEEPSEEK);
        String key = "";
        try {
            String name = "attachment";
            long size = -1;
            try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    if (!cursor.isNull(0)) name = cursor.getString(0);
                    if (!cursor.isNull(1)) size = cursor.getLong(1);
                }
            }
            String lower = name.toLowerCase(Locale.ROOT);
            String mime = getContentResolver().getType(uri);
            boolean image = Arrays.asList("image/jpeg", "image/png", "image/webp", "image/gif").contains(mime == null ? "" : mime)
                || lower.matches(".*\\.(jpg|jpeg|png|gif|webp)$");
            boolean parsed = !image && lower.matches(".*\\.(pdf|doc|docx|ppt|pptx|bmp|tif|tiff)$");
            long limit = image ? 64 * MIB : parsed ? 100 * MIB : 32 * MIB;
            if (size > limit) throw new DeepSeek.ApiError("文件超过 " + limit / MIB + " MiB。");
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException();
                KeyVault.Credential attachment = vault.forAttachment("", keyId);
                if (attachment == null) throw new DeepSeek.ApiError("请先配置用于附件上传的 DeepSeek 密钥");
                key = attachment.value;
                HttpURLConnection connection = request.open("/files", "POST", key);
                DeepSeek.multipart(connection, parsed ? "rich_layout_recognition" : "user_data", name, input, limit);
                JSONObject response = DeepSeek.json(connection);
                emit("upload", json("id", response.getString("id"), "name", name, "image", image, "keyId", attachment.id));
            }
        } catch (Exception e) {
            String message = failure(e);
            emit("upload", json("error", key.isEmpty() ? message : message.replace(key, "••••")));
        }
        finally { request.close(); }
    }

    private boolean startVoice(boolean system, boolean hold) {
        if (recording != null || dictation != null) return false;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            voicePermissionSystem = system;
            voicePermissionHold = hold;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_PERMISSION);
            return false;
        }
        if (system) return startDictation();
        beginRecording();
        return true;
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code != RECORD_PERMISSION) return;
        if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
            emit("voice", json("error", "使用语音输入需要麦克风权限"));
        } else if (voicePermissionHold) {
            emit("toast", json("message", "麦克风已授权，重新按住说话"));
        } else {
            startVoice(voicePermissionSystem, false);
        }
    }

    private boolean startDictation() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            emit("voice", json("error", "手机当前没有可用的系统语音识别服务"));
            return false;
        }
        speech.stop();
        SpeechRecognizer session = SpeechRecognizer.createSpeechRecognizer(this);
        dictation = session;
        session.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) {
                if (dictation == session && screen != null) screen.updateVoiceLevel((rmsdB - 1f) / 9f);
            }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onPartialResults(Bundle results) { }
            @Override public void onEvent(int eventType, Bundle params) { }
            @Override public void onEndOfSpeech() {
                if (dictation == session) emit("voice", json("transcribing", true));
            }
            @Override public void onResults(Bundle results) {
                List<String> items = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                String text = items == null || items.isEmpty() ? "" : items.get(0).trim();
                finishDictation(session, text.isEmpty() ? json("error", "没有识别到语音") : json("text", text));
            }
            @Override public void onError(int code) {
                String error = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ? "没有识别到语音" : "系统语音识别失败（" + code + "）";
                finishDictation(session, json("error", error));
            }
        });
        Intent prompt = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        try {
            session.startListening(prompt);
            emit("voice", json("recording", true));
            return true;
        } catch (RuntimeException error) {
            finishDictation(session, json("error", "无法启动系统语音识别"));
            return false;
        }
    }

    private void finishDictation(SpeechRecognizer session, JSONObject result) {
        if (dictation != session) return;
        dictation = null;
        session.destroy();
        emit("voice", result);
    }

    private void endDictation(boolean discard) {
        SpeechRecognizer session = dictation;
        if (session == null) return;
        if (discard) {
            dictation = null;
            session.cancel();
            session.destroy();
            emit("voice", json());
        } else {
            emit("voice", json("transcribing", true));
            session.stopListening();
        }
    }

    private void beginRecording() {
        speech.stop();
        if (recording != null) return;
        Recording next = new Recording();
        recording = next;
        emit("voice", json("recording", true));
        worker.execute(() -> transcribe(next));
    }

    private void transcribe(Recording session) {
        DeepSeek.Request request = apiRequest(ApiProvider.DEEPSEEK);
        try {
            byte[] pcm = session.capture(level -> runOnUiThread(() -> {
                if (recording == session && session.active && !isDestroyed()) screen.updateVoiceLevel((float) level);
            }));
            if (session.discard) { emit("voice", json()); return; }
            if (pcm.length < 6400) throw new DeepSeek.ApiError("录音太短，按住再试一次");
            emit("voice", json("transcribing", true));
            HttpURLConnection connection = request.open("/v1/tools/asr", "POST", requireKey());
            try (InputStream wav = session.wav(pcm)) {
                DeepSeek.multipart(connection, null, "voice.wav", wav, 8 * MIB);
            }
            String text = DeepSeek.json(connection).getString("text").trim();
            if (session.discard) emit("voice", json());
            else if (text.isEmpty()) emit("voice", json("error", "没有识别到语音"));
            else emit("voice", json("text", text));
        } catch (Exception e) {
            emit("voice", session.discard ? json() : json("error", failure(e)));
        } finally {
            request.close();
            runOnUiThread(() -> { if (recording == session) recording = null; });
        }
    }

    private static final class Recording {
        static final int RATE = 16000;
        static final int MAX_BYTES = RATE * 2 * 119;
        volatile boolean active = true;
        volatile boolean discard;

        byte[] capture(java.util.function.DoubleConsumer onLevel) throws IOException {
            int minimum = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum < 1) throw new DeepSeek.ApiError("设备不支持当前录音格式");
            byte[] buffer = new byte[3200];
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minimum, buffer.length * 2));
            ByteArrayOutputStream sound = new ByteArrayOutputStream();
            try {
                recorder.startRecording();
                long lastLevel = 0;
                while (active && sound.size() < MAX_BYTES) {
                    int length = recorder.read(buffer, 0, Math.min(buffer.length, MAX_BYTES - sound.size()));
                    if (length < 0) throw new DeepSeek.ApiError("麦克风录音失败");
                    sound.write(buffer, 0, length);
                    long now = android.os.SystemClock.uptimeMillis();
                    if (length >= 2 && now - lastLevel >= 40) {
                        lastLevel = now;
                        onLevel.accept(level(buffer, length));
                    }
                }
                return sound.toByteArray();
            } finally {
                if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
                recorder.release();
            }
        }

        private static double level(byte[] pcm, int length) {
            double energy = 0;
            for (int i = 0; i + 1 < length; i += 2) {
                int sample = (pcm[i] & 0xff) | (pcm[i + 1] << 8);
                energy += (double) sample * sample;
            }
            double rms = Math.sqrt(energy / (length / 2));
            double db = 20 * Math.log10(Math.max(1, rms) / 32768);
            return (db + 54) / 42;
        }

        InputStream wav(byte[] pcm) {
            ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            header.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + pcm.length);
            header.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16)
                .putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2)
                .putShort((short) 2).putShort((short) 16);
            header.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(pcm.length);
            return new SequenceInputStream(new ByteArrayInputStream(header.array()), new ByteArrayInputStream(pcm));
        }
    }

}
