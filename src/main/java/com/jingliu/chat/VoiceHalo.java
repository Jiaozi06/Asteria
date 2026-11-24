package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

final class VoiceHalo extends View {
    private static final int POINTS = 64;
    private static final int[] LIGHT_BLUE = {0x633f78e7, 0x394178e7, 0x004178e7};
    private static final int[] LIGHT_CORAL = {0x64de6174, 0x36de6174, 0x00de6174};
    private static final int[] DARK_BLUE = {0xc74381ff, 0x72336ef1, 0x00336ef1};
    private static final int[] DARK_CORAL = {0xc6e06276, 0x77cd485e, 0x00cd485e};
    private static final float[] STOPS = {0f, .57f, 1f};
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arc = new Path();
    private final Path star = new Path();
    private final Shader blueGlow, coralGlow;
    private final boolean dark;
    private long started, lastFrame, lastSample;
    private float targetLevel, level, cancelBlend, reveal;
    private boolean cancelled, running;

    VoiceHalo(Context context, boolean dark) {
        super(context);
        this.dark = dark;
        blueGlow = new RadialGradient(0, 0, 1f, dark ? DARK_BLUE : LIGHT_BLUE, STOPS, Shader.TileMode.CLAMP);
        coralGlow = new RadialGradient(0, 0, 1f, dark ? DARK_CORAL : LIGHT_CORAL, STOPS, Shader.TileMode.CLAMP);
        star.moveTo(0, -1);
        star.cubicTo(0, -.41f, .41f, 0, 1, 0);
        star.cubicTo(.41f, 0, 0, .41f, 0, 1);
        star.cubicTo(0, .41f, -.41f, 0, -1, 0);
        star.cubicTo(-.41f, 0, 0, -.41f, 0, -1);
        star.close();
        paint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void start() {
        started = lastFrame = SystemClock.uptimeMillis();
        lastSample = 0;
        targetLevel = level = cancelBlend = reveal = 0;
        cancelled = false;
        running = true;
        invalidate();
    }

    void stop() {
        running = false;
    }

    void setCancelled(boolean value) {
        cancelled = value;
        invalidate();
    }

    void setLevel(float value) {
        if (!running || !Float.isFinite(value)) return;
        targetLevel = Math.max(0f, Math.min(1f, value));
        lastSample = SystemClock.uptimeMillis();
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) return;
        long now = SystemClock.uptimeMillis();
        float step = Math.min(48, now - lastFrame);
        lastFrame = now;
        boolean animate = ValueAnimator.areAnimatorsEnabled();
        if (animate) {
            float sample = now - lastSample < 230 ? targetLevel : 0;
            level += (sample - level) * Math.min(1, step / (sample > level ? 60 : 190));
            cancelBlend += ((cancelled ? 1 : 0) - cancelBlend) * Math.min(1, step / 95);
            reveal = Math.min(1f, (now - started) / 180f);
        } else {
            level = 0;
            cancelBlend = cancelled ? 1 : 0;
            reveal = 1;
        }
        float density = getResources().getDisplayMetrics().density;
        float phase = animate ? ((now - started) % 12000) * ((float) Math.PI / 2000) : 0f;
        float cx = getWidth() * .5f;
        float base = getHeight() * .99f;
        float rx = Math.min(getWidth() * .52f, 270 * density);
        float ry = Math.min(getHeight() * .78f, rx * .95f);

        paint.setStyle(Paint.Style.FILL);
        drawGlow(canvas, blueGlow, reveal * (1f - cancelBlend), cx, base, rx * 1.14f, ry * 1.17f);
        drawGlow(canvas, coralGlow, reveal * cancelBlend, cx, base, rx * 1.14f, ry * 1.17f);

        int rim = mix(dark ? 0xff9cbeff : 0xff3e68c3, dark ? 0xffffacb7 : 0xffab4155, cancelBlend);
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 0; ring < 3; ring++) {
            float scale = 1f - .115f * ring;
            arc.reset();
            for (int i = 0; i <= POINTS; i++) {
                double a = Math.PI * i / POINTS;
                float wave = (float) (Math.sin(5 * a - phase + ring * .6f)
                    + .45 * Math.sin(9 * a + phase * 2 + .4f));
                float radius = scale + (.015f + .062f * level) * wave * (float) Math.sin(a);
                float x = cx + rx * radius * (float) Math.cos(a);
                float y = base - ry * radius * (float) Math.sin(a);
                if (i == 0) arc.moveTo(x, y);
                else arc.lineTo(x, y);
            }
            paint.setColor(rim);
            paint.setAlpha(Math.round(reveal * (ring == 1 ? 140 : 67)));
            paint.setStrokeWidth(density * (ring == 1 ? 1.6f : .9f));
            canvas.drawPath(arc, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(rim);
        paint.setAlpha(Math.round(255 * reveal));
        float size = density * (13 + 6 * level + (animate ? 1.2f * (float) Math.sin(phase) : 0));
        canvas.save();
        canvas.translate(cx, base - ry * .47f);
        canvas.scale(size, size);
        canvas.drawPath(star, paint);
        canvas.restore();
        if (animate) postInvalidateOnAnimation();
    }

    private void drawGlow(Canvas canvas, Shader shader, float opacity, float x, float y, float rx, float ry) {
        if (opacity <= 0 || rx <= 0 || ry <= 0) return;
        canvas.save();
        canvas.translate(x, y);
        canvas.scale(rx, ry);
        paint.setShader(shader);
        paint.setAlpha(Math.round(255 * opacity));
        canvas.drawCircle(0, 0, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private static int mix(int a, int b, float amount) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * amount),
            Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * amount),
            Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * amount));
    }
}
