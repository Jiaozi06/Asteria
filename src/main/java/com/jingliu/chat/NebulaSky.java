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
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.LinearInterpolator;
import java.util.Random;
import java.util.function.Consumer;

final class NebulaSky extends View {
    private static final float[] X = {.28f, .53f, .76f, .64f, .37f};
    private static final float[] Y = {.54f, .41f, .57f, .70f, .73f};
    private static final int[][] LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 0}, {1, 3}};
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final MemoryNebula.Kind[] KINDS = MemoryNebula.Kind.values();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path light = new Path();
    private final float[][] dust = new float[120][4];
    private final RadialGradient[] glows = new RadialGradient[KINDS.length];
    private final float[] nodeX = new float[KINDS.length], nodeY = new float[KINDS.length];
    private final float[] focus = new float[KINDS.length];
    private final float density;
    private final int touchSlop;
    private final Consumer<MemoryNebula.Kind> choose;
    private final int[] counts = new int[KINDS.length];
    private LinearGradient backdrop;
    private RadialGradient mist, violet, cyan;
    private ValueAnimator drift;
    private float phase, downX, downY, panX, panY, targetX, targetY;
    private long lastFrame, tappedAt;
    private MemoryNebula.Kind selected;
    private int pressed = -1, rippling = -1, pointer = -1;
    private boolean dragging;

    NebulaSky(Context context, Consumer<MemoryNebula.Kind> choose) {
        super(context);
        this.choose = choose;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
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
        if (drift == null) {
            for (int i = 0; i < focus.length; i++) focus[i] = KINDS[i] == selected ? 1f : 0f;
        }
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
                updateMotion();
                invalidate();
            });
            drift.start();
            drift.setCurrentFraction(startingFraction);
        } else stopDrift();
    }

    @Override protected void onDetachedFromWindow() {
        endGesture();
        stopDrift();
        super.onDetachedFromWindow();
    }

    private void stopDrift() {
        if (drift == null) return;
        drift.cancel();
        drift = null;
        lastFrame = 0;
        panX = panY = targetX = targetY = 0f;
    }

    private void updateMotion() {
        long now = SystemClock.uptimeMillis();
        long frameMs = lastFrame == 0 ? 16 : Math.min(now - lastFrame, 64);
        lastFrame = now;
        float factor = 1f - (float) Math.exp(-frameMs / (dragging ? 44f : 108f));
        panX = approach(panX, targetX, factor);
        panY = approach(panY, targetY, factor);
        for (int i = 0; i < focus.length; i++) {
            focus[i] = approach(focus[i], KINDS[i] == selected ? 1f : 0f, factor);
        }
    }

    private static float approach(float value, float target, float factor) {
        return Math.abs(target - value) < .005f ? target : value + (target - value) * factor;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width == 0 || height == 0) return;
        backdrop = new LinearGradient(0f, 0f, width, height, new int[]{0xff0d1a32, 0xff111831, 0xff111827},
            new float[]{0f, .57f, 1f}, Shader.TileMode.CLAMP);
        mist = new RadialGradient(0, 0, width * .57f,
            new int[]{0x735075bb, 0x384066a8, 0x0032598a}, null, Shader.TileMode.CLAMP);
        violet = new RadialGradient(0, 0, width * .52f,
            new int[]{0x7b7750a3, 0x354d3979, 0x00402e71}, null, Shader.TileMode.CLAMP);
        cyan = new RadialGradient(0, 0, width * .30f,
            new int[]{0x512c95b4, 0x222c78aa, 0x002c78aa}, null, Shader.TileMode.CLAMP);
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
        drawMist(canvas, mist, width * (.29f + .08f * wave(phase)) + panX * .33f,
            height * (.69f + .065f * wave(phase + 1.4f)) + panY * .33f,
            190 + (int) (38 * wave(phase)));
        drawMist(canvas, violet, width * (.77f + .07f * wave(phase + 2.5f)) + panX * .43f,
            height * (.44f + .07f * wave(phase + 3.6f)) + panY * .43f,
            196 + (int) (40 * wave(phase + 2.5f)));
        drawMist(canvas, cyan, width * (.51f + .065f * wave(phase + 1.3f)) + panX * .51f,
            height * (.59f + .07f * wave(phase + 2)) + panY * .51f, 174 + (int) (44 * wave(phase + 1.3f)));
        drawGuide(canvas, width, height);

        for (float[] star : dust) {
            float sparkle = wave((2 + (int) (star[3] * 4)) * phase + star[1] * FULL_CIRCLE);
            float depth = .16f + star[3] * .65f;
            float x = width * star[0] + (2 + 6 * depth) * density * wave(phase + star[1] * FULL_CIRCLE) + panX * depth;
            float y = height * star[1] + (2 + 4 * depth) * density * wave(phase + star[0] * FULL_CIRCLE) + panY * depth;
            paint.setColor(Color.argb(26 + (int) (86 * star[3] + 18 * sparkle), 192, 209, 255));
            canvas.drawCircle(x, y, star[2] * density, paint);
        }
        for (int i = 0; i < KINDS.length; i++) {
            nodeX[i] = width * X[i] + panX + density * (9 - 4 * focus[i]) * wave(phase + i * 1.7f);
            nodeY[i] = height * Y[i] + panY + density * (7 - 3 * focus[i]) * wave(phase + i * 1.2f + 1);
        }
        for (int i = 0; i < LINKS.length; i++) drawLink(canvas, i);
        for (int i = 0; i < KINDS.length; i++) drawStar(canvas, i, nodeX[i], nodeY[i]);
        if (rippling >= 0) drawTapRipple(canvas);
    }

    private void drawGuide(Canvas canvas, int width, int height) {
        paint.setStrokeWidth(.65f * density);
        paint.setColor(0x168ab6f5);
        float tick = 2.2f * density;
        for (int column = 1; column < 8; column++) {
            float x = width * column / 8f + panX * .18f;
            for (int row = 1; row < 6; row++) {
                float y = height * row / 6f + panY * .18f;
                canvas.drawLine(x - tick, y, x + tick, y, paint);
                canvas.drawLine(x, y - tick, x, y + tick, paint);
            }
        }
    }

    private void drawLink(Canvas canvas, int index) {
        int from = LINKS[index][0], to = LINKS[index][1];
        boolean live = counts[from] > 0 && counts[to] > 0;
        float emphasis = Math.max(focus[from], focus[to]);
        float x = nodeX[from], y = nodeY[from], dx = nodeX[to] - x, dy = nodeY[to] - y;
        paint.setStrokeWidth((.6f + .55f * emphasis) * density);
        paint.setColor(live ? Color.argb(86 + (int) (86 * emphasis), 132, 177, 241) : 0x278dadf0);
        canvas.drawLine(x, y, x + dx, y + dy, paint);
        if (!live) return;
        float step = phase / FULL_CIRCLE * 3 + index * .39f;
        float head = step - (int) step;
        for (int i = 5; i >= 0; i--) {
            float along = head - i * .023f;
            if (along <= 0f) continue;
            float fade = Math.min(1f, Math.min(along, 1f - head) * 16f);
            int opacity = (int) ((122 + 72 * emphasis) * fade * (1f - i * .15f));
            paint.setColor(Color.argb(opacity, 169, 229, 255));
            canvas.drawCircle(x + dx * along, y + dy * along, (1.7f - i * .19f) * density, paint);
        }
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
        float emphasis = focus[index];
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
        float radius = (4.1f + Math.min(3.3f, (float) Math.sqrt(count)) + .7f * pulse + emphasis * 2) * density;
        if (emphasis > 0f) drawOrbit(canvas, x, y, radius + 8 * density, kind.color, emphasis);
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

    private void drawOrbit(Canvas canvas, float x, float y, float radius, int color, float emphasis) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f * density);
        float angle = phase * 360f / FULL_CIRCLE * 2;
        paint.setColor(color);
        paint.setAlpha((int) (56 * emphasis));
        canvas.drawCircle(x, y, radius, paint);
        paint.setAlpha((int) (186 * emphasis));
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, angle, 66, false, paint);
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, angle + 180, 37, false, paint);
        paint.setStyle(Paint.Style.FILL);
        float marker = radius + 6 * density;
        canvas.drawRect(x - marker, y - density * .5f, x - radius, y + density * .5f, paint);
        canvas.drawRect(x + radius, y - density * .5f, x + marker, y + density * .5f, paint);
    }

    private void drawTapRipple(Canvas canvas) {
        float fraction = (SystemClock.uptimeMillis() - tappedAt) / 620f;
        if (fraction >= 1f) {
            rippling = -1;
            return;
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(KINDS[rippling].color);
        paint.setAlpha((int) (151 * (1f - fraction)));
        canvas.drawCircle(nodeX[rippling], nodeY[rippling], (11 + 31 * fraction) * density, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointer = event.getPointerId(0);
                downX = event.getX();
                downY = event.getY();
                pressed = nodeAt(downX, downY);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(pointer);
                if (index < 0) return true;
                float dx = event.getX(index) - downX, dy = event.getY(index) - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    pressed = -1;
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (dragging) {
                    targetX = dragOffset(dx, 30 * density);
                    targetY = dragOffset(dy, 19 * density);
                    if (drift == null) {
                        panX = targetX;
                        panY = targetY;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (!dragging && pointer >= 0 && Math.hypot(event.getX() - downX, event.getY() - downY) <= touchSlop
                    && pressed == nodeAt(event.getX(), event.getY())) performClick();
                endGesture();
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if (pointer == event.getPointerId(event.getActionIndex())) endGesture();
                return true;
            case MotionEvent.ACTION_CANCEL:
                endGesture();
                return true;
            default: return true;
        }
    }

    private static float dragOffset(float distance, float max) {
        return max * distance / (max + Math.abs(distance));
    }

    private void endGesture() {
        if (dragging && getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        dragging = false;
        pointer = pressed = -1;
        targetX = targetY = 0;
        if (drift == null) {
            panX = panY = 0;
            invalidate();
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        if (pressed >= 0) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (drift != null) {
                rippling = pressed;
                tappedAt = SystemClock.uptimeMillis();
            }
            MemoryNebula.Kind kind = KINDS[pressed];
            choose.accept(kind == selected ? null : kind);
        } else if (selected != null) choose.accept(null);
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
