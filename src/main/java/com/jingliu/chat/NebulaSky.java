package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

final class NebulaSky extends View {
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final float TILT_COS = .967f, TILT_SIN = -.255f;
    private static final int ORBIT_BANDS = 8;
    private static final int[] DUST_COLORS = {0x51819be9, 0x727cbbf8, 0x749a85eb, 0x9cd0bcff, 0xb5afd8ff};
    private static final int[] MEMORY_COLORS = {0xffa4e0ff, 0xffa9bdff, 0xffd4b6ff, 0xffffd9bf, 0xfff1f6ff};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path light = new Path();
    private final float[] orbitCos = new float[ORBIT_BANDS], orbitSin = new float[ORBIT_BANDS];
    private final float[] backgroundStars = new float[200];
    private final List<Star> memories = new ArrayList<>();
    private final Cloud[] dust = clouds(DUST_COLORS.length);
    private final Cloud[] points = clouds(MEMORY_COLORS.length);
    private final float density;
    private final int touchSlop;
    private final Consumer<MemoryNebula.Entry> open;
    private List<MemoryNebula.Entry> displayed;
    private LinearGradient backdrop;
    private RadialGradient haze, blueMist, violetMist, core, starHalo;
    private ValueAnimator drift;
    private float phase, centerX, centerY, radiusX, radiusY;
    private float downX, downY, panX, panY, targetX, targetY;
    private long lastFrame, tappedAt;
    private int pointer = -1;
    private Star pressed, focused;
    private boolean dragging;

    NebulaSky(Context context, Consumer<MemoryNebula.Entry> open) {
        super(context);
        this.open = open;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        Random random = new Random(31873);
        for (int i = 0; i < backgroundStars.length; i++) backgroundStars[i] = random.nextFloat();
        for (int i = 0; i < 580; i++) {
            Star star = new Star(null, random, DUST_COLORS.length);
            dust[star.style].stars.add(star);
        }
        for (Cloud cloud : dust) cloud.resize();
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(List<MemoryNebula.Entry> entries) {
        if (entries == displayed) return;
        displayed = entries;
        String selectedId = focused == null ? null : focused.entry.id;
        focused = pressed = null;
        memories.clear();
        for (Cloud cloud : points) cloud.stars.clear();
        for (MemoryNebula.Entry entry : entries) {
            Star star = new Star(entry, new Random(seed(entry.id)), MEMORY_COLORS.length);
            memories.add(star);
            points[star.style].stars.add(star);
            if (entry.id.equals(selectedId)) focused = star;
        }
        for (Cloud cloud : points) cloud.resize();
        invalidate();
    }

    private static long seed(String id) {
        long value = 0xcbf29ce484222325L;
        for (int i = 0; i < id.length(); i++) value = (value ^ id.charAt(i)) * 0x100000001b3L;
        return value;
    }

    @Override public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        if (visible && ValueAnimator.areAnimatorsEnabled()) {
            if (drift != null) return;
            float startingFraction = phase / FULL_CIRCLE;
            drift = ValueAnimator.ofFloat(0, FULL_CIRCLE);
            drift.setInterpolator(new LinearInterpolator());
            drift.setDuration(70_000);
            drift.setRepeatCount(ValueAnimator.INFINITE);
            drift.addUpdateListener(frame -> {
                phase = (float) frame.getAnimatedValue();
                updatePan();
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

    private void updatePan() {
        long now = SystemClock.uptimeMillis();
        long frameMs = lastFrame == 0 ? 16 : Math.min(now - lastFrame, 64);
        lastFrame = now;
        float factor = 1f - (float) Math.exp(-frameMs / (dragging ? 44f : 108f));
        panX = approach(panX, targetX, factor);
        panY = approach(panY, targetY, factor);
    }

    private static float approach(float value, float target, float factor) {
        return Math.abs(target - value) < .05f ? target : value + (target - value) * factor;
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (width == 0 || height == 0) return;
        centerX = width * .53f;
        centerY = height * .53f;
        radiusX = Math.min(width * .44f, height * .96f);
        radiusY = height * .29f;
        backdrop = new LinearGradient(0f, height, width, 0f, new int[]{0xff0b1124, 0xff111733, 0xff0b1125},
            new float[]{0f, .57f, 1f}, Shader.TileMode.CLAMP);
        haze = glow(0x5b5869c2, 0x2a434a99);
        blueMist = glow(0x626ca5f3, 0x306575c8);
        violetMist = glow(0x5b9772cb, 0x2d7850a0);
        core = glow(0xb2d2d7ff, 0x687f9cf2);
        starHalo = glow(0xafa9c7ff, 0x305893f6);
    }

    private static RadialGradient glow(int center, int middle) {
        return new RadialGradient(0f, 0f, 1f, new int[]{center, middle, middle & 0xffffff},
            new float[]{0f, .28f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (backdrop == null) return;
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(backdrop);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        drawBackgroundStars(canvas);
        float cx = centerX + panX, cy = centerY + panY;
        float blueAngle = phase + .9f, violetAngle = phase + 3.7f;
        drawGlow(canvas, haze, cx, cy, radiusX * 1.18f, radiusY * 1.38f, -14, 225);
        drawGlow(canvas, blueMist, cx + radiusX * .43f * cosine(blueAngle), cy + radiusY * .54f * sine(blueAngle),
            radiusX * .74f, radiusY * .64f, -21, 191);
        drawGlow(canvas, violetMist, cx + radiusX * .57f * cosine(violetAngle), cy + radiusY * .55f * sine(violetAngle),
            radiusX * .68f, radiusY * .82f, -9, 187);
        drawGlow(canvas, core, cx, cy, radiusX * .25f, radiusY * .37f, -14, 226);

        for (int i = 0; i < ORBIT_BANDS; i++) {
            float angle = phase + .17f * sine(phase * 2f - i * .53f);
            orbitCos[i] = cosine(angle);
            orbitSin[i] = sine(angle);
        }
        float memorySize = Math.max(1.8f, 4.7f - (float) Math.sqrt(memories.size()) * .11f);
        drawClouds(canvas, dust, DUST_COLORS, 1.04f, .78f);
        drawClouds(canvas, points, MEMORY_COLORS, memorySize, 1f);
        if (focused != null) drawMemoryFocus(canvas, focused, true);
        if (memories.size() <= 12) {
            for (Star star : memories) {
                if (star != focused) drawMemoryFocus(canvas, star, false);
            }
        }
    }

    private void drawBackgroundStars(Canvas canvas) {
        int width = getWidth(), height = getHeight();
        for (int i = 0; i < backgroundStars.length; i += 2) {
            float x = backgroundStars[i], y = backgroundStars[i + 1];
            int alpha = 26 + (int) (35 * (1f + sine(phase * 4f + x * 31f + y * 41f)));
            paint.setColor((alpha << 24) | 0xa7bef1);
            canvas.drawCircle(x * width + panX * .16f, y * height + panY * .16f, density * (.36f + x * .54f), paint);
        }
    }

    private void drawGlow(Canvas canvas, RadialGradient shader, float x, float y, float rx, float ry, float tilt, int opacity) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(tilt);
        canvas.scale(rx, ry);
        paint.setShader(shader);
        paint.setAlpha(opacity);
        canvas.drawCircle(0, 0, 1f, paint);
        paint.setShader(null);
        canvas.restore();
    }

    private void drawClouds(Canvas canvas, Cloud[] clouds, int[] colors, float size, float parallax) {
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < clouds.length; i++) {
            Cloud cloud = clouds[i];
            if (cloud.coordinates.length == 0) continue;
            int index = 0;
            for (Star star : cloud.stars) {
                float x = star.orbitX * orbitCos[star.band] - star.orbitY * orbitSin[star.band];
                float y = star.orbitX * orbitSin[star.band] + star.orbitY * orbitCos[star.band];
                star.x = centerX + radiusX * x * TILT_COS - radiusY * y * TILT_SIN + panX * parallax;
                star.y = centerY + radiusX * x * TILT_SIN + radiusY * y * TILT_COS + panY * parallax;
                cloud.coordinates[index++] = star.x;
                cloud.coordinates[index++] = star.y;
            }
            paint.setColor(colors[i]);
            paint.setStrokeWidth(density * (size + i * .22f));
            canvas.drawPoints(cloud.coordinates, paint);
        }
    }

    private void drawMemoryFocus(Canvas canvas, Star star, boolean selected) {
        float x = star.x, y = star.y;
        int color = MEMORY_COLORS[star.style];
        float pulse = sine(phase * 9f + star.orbitX * 19f + star.orbitY * 23f);
        float radius = density * (selected ? 5.2f : 3.1f + .46f * pulse);
        float halo = density * (selected ? 21f : 11f);
        drawGlow(canvas, starHalo, x, y, halo, halo, 0, selected ? 234 : 135 + (int) (pulse * 30));
        paint.setColor(color);
        light.rewind();
        light.moveTo(x, y - radius * 1.55f);
        light.quadTo(x + radius * .18f, y - radius * .19f, x + radius, y);
        light.quadTo(x + radius * .18f, y + radius * .19f, x, y + radius * 1.55f);
        light.quadTo(x - radius * .18f, y + radius * .19f, x - radius, y);
        light.quadTo(x - radius * .18f, y - radius * .19f, x, y - radius * 1.55f);
        light.close();
        canvas.drawPath(light, paint);
        if (!selected) return;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(.8f * density);
        paint.setAlpha(160);
        canvas.drawCircle(x, y, 13 * density, paint);
        float ripple = (SystemClock.uptimeMillis() - tappedAt) / 550f;
        if (drift != null && ripple < 1f) {
            paint.setAlpha((int) (150 * (1f - ripple)));
            canvas.drawCircle(x, y, (13 + 23 * ripple) * density, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pointer = event.getPointerId(0);
                downX = event.getX();
                downY = event.getY();
                pressed = starAt(downX, downY);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index = event.findPointerIndex(pointer);
                if (index < 0) return true;
                float dx = event.getX(index) - downX, dy = event.getY(index) - downY;
                if (!dragging && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    pressed = null;
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
                if (!dragging && pointer >= 0 && Math.hypot(event.getX() - downX, event.getY() - downY) <= touchSlop) performClick();
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
        pointer = -1;
        pressed = null;
        targetX = targetY = 0;
        if (drift == null) {
            panX = panY = 0;
            invalidate();
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        focused = pressed;
        if (focused != null) {
            tappedAt = SystemClock.uptimeMillis();
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            open.accept(focused.entry);
        }
        invalidate();
        return true;
    }

    private Star starAt(float x, float y) {
        Star nearest = null;
        float distance = 18 * density;
        for (Star star : memories) {
            if (star.x <= 0 || star.x >= getWidth() || star.y <= 0 || star.y >= getHeight()) continue;
            float dx = x - star.x, dy = y - star.y;
            if (dx * dx + dy * dy < distance * distance) {
                nearest = star;
                distance = (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return nearest;
    }

    private static float sine(float angle) { return (float) Math.sin(angle); }
    private static float cosine(float angle) { return (float) Math.cos(angle); }

    private static Cloud[] clouds(int count) {
        Cloud[] result = new Cloud[count];
        for (int i = 0; i < count; i++) result[i] = new Cloud();
        return result;
    }

    private static final class Cloud {
        final List<Star> stars = new ArrayList<>();
        float[] coordinates = new float[0];

        void resize() { coordinates = new float[stars.size() * 2]; }
    }

    private static final class Star {
        final MemoryNebula.Entry entry;
        final float orbitX, orbitY;
        final int band, style;
        float x, y;

        Star(MemoryNebula.Entry entry, Random random, int styles) {
            this.entry = entry;
            boolean dust = entry == null;
            float radius = dust ? .055f + .94f * (float) Math.pow(random.nextFloat(), 1.1)
                : .19f + .78f * (float) Math.sqrt(random.nextFloat());
            float bend = 4.9f * radius;
            float scatter = (random.nextFloat() + random.nextFloat() - 1f) * (dust ? .93f : 1.35f);
            float angle = random.nextInt(3) * FULL_CIRCLE / 3f + bend + scatter;
            orbitX = radius * cosine(angle);
            orbitY = radius * sine(angle);
            band = Math.min(ORBIT_BANDS - 1, (int) (radius * ORBIT_BANDS));
            style = random.nextInt(styles);
        }
    }
}
