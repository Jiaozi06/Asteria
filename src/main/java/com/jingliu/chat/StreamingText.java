package com.jingliu.chat;

import android.content.Context;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.Spannable;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;
import android.widget.TextView;

final class StreamingText extends TextView {
    static final long FADE_MS = 160;
    private long animatesUntil;
    private final Runnable refresh = this::refreshFade;

    StreamingText(Context context) { super(context); }

    void showNewText(CharSequence before, CharSequence after) {
        int common = 0;
        int limit = Math.min(before.length(), after.length());
        while (common < limit && before.charAt(common) == after.charAt(common)) common++;
        if (common > 0 && Character.isHighSurrogate(after.charAt(common - 1))) common--;
        int end = after.length();
        int previousEnd = before.length();
        while (end > common && previousEnd > common && after.charAt(end - 1) == before.charAt(previousEnd - 1)) {
            end--;
            previousEnd--;
        }
        if (end > common && end < after.length() && Character.isLowSurrogate(after.charAt(end))) end++;

        long now = SystemClock.uptimeMillis();
        SpannableString output = new SpannableString(after);
        animatesUntil = 0;
        if (before instanceof Spanned) {
            Spanned old = (Spanned) before;
            for (Fade previous : old.getSpans(0, common, Fade.class)) {
                int first = old.getSpanStart(previous);
                int last = Math.min(common, old.getSpanEnd(previous));
                if (first < last && previous.started + FADE_MS > now) {
                    output.setSpan(previous, first, last, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    animatesUntil = Math.max(animatesUntil, previous.started + FADE_MS);
                }
            }
        }
        if (common < end) {
            output.setSpan(new Fade(now), common, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            animatesUntil = now + FADE_MS;
        }
        setText(output, BufferType.SPANNABLE);
        removeCallbacks(refresh);
        if (animatesUntil > now && isAttachedToWindow()) postOnAnimation(refresh);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animatesUntil != 0) postOnAnimation(refresh);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(refresh);
        super.onDetachedFromWindow();
    }

    private void refreshFade() {
        if (!(getText() instanceof Spannable)) return;
        Spannable text = (Spannable) getText();
        long now = SystemClock.uptimeMillis();
        // Notify TextView so its selectable text display list redraws the affected ranges.
        for (Fade fade : text.getSpans(0, text.length(), Fade.class)) {
            int start = text.getSpanStart(fade);
            int end = text.getSpanEnd(fade);
            text.removeSpan(fade);
            if (fade.started + FADE_MS > now) text.setSpan(fade, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (animatesUntil > now) postOnAnimation(refresh);
        else animatesUntil = 0;
    }

    private static final class Fade extends CharacterStyle implements UpdateAppearance {
        final long started;
        Fade(long started) { this.started = started; }

        @Override public void updateDrawState(TextPaint paint) {
            float remaining = Math.max(0f, 1f - (SystemClock.uptimeMillis() - started) / (float) FADE_MS);
            paint.setAlpha(Math.round(paint.getAlpha() * (1f - .74f * remaining * remaining)));
        }
    }
}
