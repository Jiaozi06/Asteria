package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.LinearInterpolator;
import java.util.Random;
import java.util.function.Consumer;

final class NebulaSky extends View {
    private static final float[] X = {.28f, .53f, .76f, .64f, .37f};
    private static final float[] Y = {.57f, .43f, .61f, .77f, .79f};
    private static final int[][] LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 0}, {1, 3}};
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final MemoryNebula.Kind[] KINDS = MemoryNebula.Kind.values();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path light = new Path();
    private final float[][] dust = new float[120][4];
    private final RadialGradient[] glows = new RadialGradient[KINDS.length];
    private final float[] nodeX = new float[KINDS.length], nodeY = new float[KINDS.length];
    private final float density;
    private final Consumer<MemoryNebula.Kind> choose;
    private final int[] counts = new int[KINDS.length];
    private LinearGradient backdrop;
    private RadialGradient mist, violet;
    private ValueAnimator drift;
    private float phase;
    private MemoryNebula.Kind selected;
    private int pressed = -1;

    NebulaSky(Context context, Consumer<MemoryNebula.Kind> choose) {
        super(context);
        this.choose = choose;
        density = getResources().getDisplayMetrics().density;
        Random random = new Random(31873);
        for (float[] star : dust) {
            star[0] = random.nextFloat();
            star[1] = random.nextFloat();
            star[2] = .28f + random.nextFloat() * .8f;
            star[3] = random.nextFloat();
        }
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(int[] current, MemoryNebula.Kind selected) {
        System.arraycopy(current, 0, counts, 0, counts.length);
        this.selected = selected;
        invalidate();
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible && ValueAnimator.areAnimatorsEnabled()) {
            if (drift != null) return;
            float startingFraction = phase / FULL_CIRCLE;
            drift = ValueAnimator.ofFloat(0, FULL_CIRCLE);
            drift.setInterpolator(new LinearInterpolator());
            drift.setDuration(24_000);
            drift.setRepeatCount(ValueAnimator.INFINITE);
            drift.addUpdateListener(frame -> {
                phase = (float) frame.getAnimatedValue();
                invalidate();
            });
            drift.start();
            drift.setCurrentFraction(startingFraction);
        } else stopDrift();
    }

    @Override protected void onDetachedFromWindow() {
        stopDrift();
        super.onDetachedFromWindow();
    }

    private void stopDrift() {
        if (drift == null) return;
        drift.cancel();
        drift = null;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width == 0 || height == 0) return;
        backdrop = new LinearGradient(0f, 0f, width, height, new int[]{0xff101b37, 0xff12152d, 0xff101827},
            new float[]{0f, .55f, 1f}, Shader.TileMode.CLAMP);
        mist = new RadialGradient(0, 0, width * .57f,
            new int[]{0x51507baf, 0x274066a8, 0x0032598a}, null, Shader.TileMode.CLAMP);
        violet = new RadialGradient(0, 0, width * .52f,
            new int[]{0x5b654985, 0x264d3979, 0x00402e71}, null, Shader.TileMode.CLAMP);
        for (int i = 0; i < glows.length; i++) {
            int color = KINDS[i].color & 0x00ffffff;
            glows[i] = new RadialGradient(0, 0, 30 * density,
                new int[]{color | 0x6b000000, color | 0x22000000, color}, new float[]{0, .30f, 1f}, Shader.TileMode.CLAMP);
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        if (backdrop == null) return;
        int width = getWidth(), height = getHeight();
        paint.setAlpha(255);
        paint.setShader(backdrop);
        canvas.drawRect(0, 0, width, height, paint);
        drawMist(canvas, mist, width * (.29f + .055f * wave(phase)), height * (.69f + .04f * wave(phase + 1.4f)),
            190 + (int) (38 * wave(phase)));
        drawMist(canvas, violet, width * (.77f + .06f * wave(phase + 2.5f)), height * (.44f + .06f * wave(phase + 3.6f)),
            196 + (int) (40 * wave(phase + 2.5f)));

        for (float[] star : dust) {
            float sparkle = wave((2 + (int) (star[3] * 4)) * phase + star[1] * FULL_CIRCLE);
            float x = width * star[0] + 2 * density * wave(phase + star[1] * FULL_CIRCLE);
            float y = height * star[1] + 2 * density * wave(phase + star[0] * FULL_CIRCLE);
            paint.setColor(Color.argb(26 + (int) (86 * star[3] + 18 * sparkle), 192, 209, 255));
            canvas.drawCircle(x, y, star[2] * density, paint);
        }
        for (int i = 0; i < KINDS.length; i++) {
            nodeX[i] = width * X[i] + density * 6 * wave(phase + i * 1.7f);
            nodeY[i] = height * Y[i] + density * 5 * wave(phase + i * 1.2f + 1);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density * .65f);
        for (int[] edge : LINKS) {
            boolean active = counts[edge[0]] > 0 && counts[edge[1]] > 0;
            paint.setColor(active ? 0x718ea6ed : 0x3292a3cf);
            canvas.drawLine(nodeX[edge[0]], nodeY[edge[0]], nodeX[edge[1]], nodeY[edge[1]], paint);
        }
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < KINDS.length; i++) drawStar(canvas, i, nodeX[i], nodeY[i]);
    }

    private static float wave(float angle) { return (float) Math.sin(angle); }

    private void drawMist(Canvas canvas, RadialGradient gradient, float x, float y, int alpha) {
        canvas.save();
        canvas.translate(x, y);
        paint.setShader(gradient);
        paint.setAlpha(alpha);
        canvas.drawRect(-x, -y, getWidth() - x, getHeight() - y, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawStar(Canvas canvas, int index, float x, float y) {
        MemoryNebula.Kind kind = KINDS[index];
        boolean faded = selected != null && selected != kind;
        int count = counts[index];
        if (count == 0) {
            paint.setColor(0x6088a2cf);
            canvas.drawCircle(x, y, density * 2.3f, paint);
            return;
        }
        paint.setShader(glows[index]);
        float pulse = wave(3 * phase + index * 1.7f);
        paint.setAlpha(faded ? 70 : 204 + (int) (46 * pulse));
        canvas.save();
        canvas.translate(x, y);
        float spread = 1f + .12f * pulse;
        canvas.scale(spread, spread);
        canvas.drawCircle(0, 0, density * 30, paint);
        canvas.restore();
        paint.setShader(null);
        float radius = (4.1f + Math.min(3.3f, (float) Math.sqrt(count)) + .45f * pulse) * density;
        if (selected == kind) {
            paint.setColor((kind.color & 0xffffff) | 0x79000000);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            canvas.drawCircle(x, y, radius + 5 * density, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        paint.setColor(kind.color);
        paint.setAlpha(faded ? 120 : 255);
        light.rewind();
        light.moveTo(x, y - radius * 1.35f);
        light.quadTo(x + radius * .18f, y - radius * .20f, x + radius, y);
        light.quadTo(x + radius * .18f, y + radius * .20f, x, y + radius * 1.35f);
        light.quadTo(x - radius * .18f, y + radius * .20f, x - radius, y);
        light.quadTo(x - radius * .18f, y - radius * .20f, x, y - radius * 1.35f);
        light.close();
        canvas.drawPath(light, paint);
        paint.setColor(faded ? 0x82ffffff : 0xf8ffffff);
        canvas.drawCircle(x, y, radius * .34f, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = nodeAt(event.getX(), event.getY());
                return pressed >= 0;
            case MotionEvent.ACTION_UP:
                int released = nodeAt(event.getX(), event.getY());
                if (pressed >= 0 && released == pressed) performClick();
                pressed = -1;
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = -1;
                return true;
            default: return pressed >= 0;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        if (pressed >= 0) choose.accept(KINDS[pressed]);
        return true;
    }

    private int nodeAt(float x, float y) {
        int nearest = -1;
        float distance = 25 * density;
        for (int i = 0; i < KINDS.length; i++) {
            if (counts[i] == 0) continue;
            float dx = x - nodeX[i], dy = y - nodeY[i];
            float next = (float) Math.hypot(dx, dy);
            if (next < distance) {
                distance = next;
                nearest = i;
            }
        }
        return nearest;
    }
}
