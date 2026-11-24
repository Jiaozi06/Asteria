package com.jingliu.chat;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import java.util.function.Consumer;

final class PhoneSpeech {
    private final Context context;
    private final Consumer<String> activeChanged;
    private final Consumer<String> showError;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextToSpeech engine;
    private boolean ready;
    private boolean closed;
    private int sequence;
    private String active = "";
    private String pending = "";
    private String last = "";

    PhoneSpeech(Context context, Consumer<String> activeChanged, Consumer<String> showError) {
        this.context = context.getApplicationContext();
        this.activeChanged = activeChanged;
        this.showError = showError;
    }

    void toggle(String id, String text) {
        if (closed || text.trim().isEmpty()) return;
        if (id.equals(active)) { stop(); return; }
        stop();
        active = id;
        pending = text;
        activeChanged.accept(active);
        if (ready) read();
        else if (engine == null) {
            engine = new TextToSpeech(context, status -> main.post(() -> initialized(status)));
        }
    }

    private void initialized(int status) {
        if (closed) return;
        if (status != TextToSpeech.SUCCESS) {
            engine.shutdown();
            engine = null;
            if (!active.isEmpty()) fail("手机的文字转语音引擎不可用。");
            return;
        }
        ready = true;
        engine.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        engine.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) {}
            @Override public void onDone(String id) { main.post(() -> { if (id.equals(last)) stop(); }); }
            @Override public void onError(String id) {
                main.post(() -> {
                    if (!active.isEmpty() && id.startsWith(sequence + ":")) fail("系统朗读失败，请检查手机的文字转语音设置。");
                });
            }
        });
        if (!active.isEmpty()) read();
    }

    private void read() {
        String text = pending.trim();
        pending = "";
        int limit = TextToSpeech.getMaxSpeechInputLength();
        int number = 0;
        for (int start = 0; start < text.length();) {
            int end = Math.min(start + limit, text.length());
            if (end < text.length()) {
                if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
                for (int i = end - 1; i > Math.max(start, end - 400); i--) {
                    if ("。？！.!?\n".indexOf(text.charAt(i)) >= 0) { end = i + 1; break; }
                }
            }
            last = sequence + ":" + number++;
            if (engine.speak(text.substring(start, end), TextToSpeech.QUEUE_ADD, null, last) == TextToSpeech.ERROR) {
                fail("系统朗读失败，请检查手机的文字转语音设置。");
                return;
            }
            start = end;
        }
    }

    private void fail(String message) {
        stop();
        showError.accept(message);
    }

    void stop() {
        if (active.isEmpty()) return;
        sequence++;
        active = pending = last = "";
        if (ready) engine.stop();
        activeChanged.accept("");
    }

    void shutdown() {
        closed = true;
        stop();
        if (engine != null) engine.shutdown();
    }
}
