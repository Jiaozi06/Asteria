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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

final class NebulaSky extends View {
    private static final float FULL_CIRCLE = (float) (Math.PI * 2);
    private static final int MAX_VISIBLE = 14;
    private static final long MEMORY_WINDOW = 7800, STAGGER = 345, FADE = 650;
    private static final int[] DUST_COLORS = {0x357394d8, 0x5878b5e8, 0x619483d6, 0x7cbdabed, 0x84a8d3ed};
    private static final int[] MEMORY_COLORS = {0xffa4e0ff, 0xffa9bdff, 0xffd4b6ff, 0xffffd9bf, 0xfff1f6ff};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path light = new Path();
    private final float[] backgroundStars = new float[174];
    private final Cloud[] dust = new Cloud[DUST_COLORS.length];
    private final List<MemoryNebula.Entry> order = new ArrayList<>();
    private final Slot[] visible = new Slot[MAX_VISIBLE];
    private final float density;
    private final int touchSlop;
    private final Consumer<MemoryNebula.Entry> open;
    private List<MemoryNebula.Entry> displayed;
    private LinearGradient backdrop;
    private RadialGradient blueMist, violetMist, blueEdge, starHalo;
    private ValueAnimator drift;
    private float phase, downX, downY, panX, panY, targetX, targetY;
    private long lastFrame, elapsed, tappedAt;
    private int pointer = -1, visibleCount;
    private Star pressed, focused;
    private boolean dragging;

    NebulaSky(Context context, Consumer<MemoryNebula.Entry> open) {
        super(context);
        this.open = open;
        density = getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        Random random = new Random(31873);
        for (int i = 0; i < backgroundStars.length; i++) backgroundStars[i] = random.nextFloat();
        for (int i = 0; i < dust.length; i++) dust[i] = new Cloud();
        for (int i = 0; i < visible.length; i++) visible[i] = new Slot();
        for (int i = 0; i < 850; i++) {
            float x, y;
            if (i < 430) {
                x = .77f + (float) random.nextGaussian() * .24f;
                y = .73f + (float) random.nextGaussian() * .135f + (x - .77f) * .33f;
            } else if (i < 670) {
                x = .99f + (float) random.nextGaussian() * .19f;
                y = .9f + (float) random.nextGaussian() * .22f - (x - .99f) * .29f;
            } else {
                x = .57f + (float) random.nextGaussian() * .25f;
                y = .86f + (float) random.nextGaussian() * .12f - (x - .57f) * .35f;
            }
            float seed = random.nextFloat() * FULL_CIRCLE;
            dust[random.nextInt(dust.length)].stars.add(new Particle(x, y, seed));
        }
        for (Cloud cloud : dust) cloud.coordinates = new float[cloud.stars.size() * 2];
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void update(List<MemoryNebula.Entry> entries) {
        if (entries == displayed) return;
        displayed = entries;
        focused = pressed = null;
        order.clear();
        order.addAll(entries);
        Collections.shuffle(order);
        elapsed = 0;
        visibleCount = Math.min(order.size(), MAX_VISIBLE);
        for (Slot slot : visible) {
            slot.cycle = -1;
            slot.star = null;
        }
        invalidate();
    }

    @Override public void onVisibilityAggregated(boolean visibleOnScreen) {
        super.onVisibilityAggregated(visibleOnScreen);
        if (visibleOnScreen && ValueAnimator.areAnimatorsEnabled()) {
            if (drift != null) return;
            float startingFraction = phase / FULL_CIRCLE;
            drift = ValueAnimator.ofFloat(0, FULL_CIRCLE);
            drift.setInterpolator(new LinearInterpolator());
            drift.setDuration(36_000);
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
        elapsed += frameMs;
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
        backdrop = new LinearGradient(0f, 0f, width, height, new int[]{0xff0a1021, 0xff121833, 0xff151735},
            new float[]{0f, .65f, 1f}, Shader.TileMode.CLAMP);
        blueMist = glow(0x707993e4, 0x2d5166bc);
        violetMist = glow(0x777654c1, 0x42704191);
        blueEdge = glow(0x4d54aac7, 0x292d669e);
        starHalo = glow(0xafa9c7ff, 0x305893f6);
    }

    private static RadialGradient glow(int center, int middle) {
        return new RadialGradient(0f, 0f, 1f, new int[]{center, middle, middle & 0xffffff},
            new float[]{0f, .28f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        if (backdrop == null) return;
        int width = getWidth(), height = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        paint.setShader(backdrop);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setShader(null);
        drawBackgroundStars(canvas, width, height);
        drawGlow(canvas, blueMist, width * (.75f + .069f * sine(phase)) + panX * .33f,
            height * (.76f + .08f * sine(phase + 1f)) + panY * .33f, width * .79f, height * .62f, -13, 224);
        drawGlow(canvas, violetMist, width * (.99f + .09f * sine(phase + 1.8f)) + panX * .43f,
            height * (.88f + .1f * sine(phase + 2.3f)) + panY * .43f, width * .66f, height * .66f, 26, 207);
        drawGlow(canvas, blueEdge, width * (.55f + .11f * sine(phase + 3.7f)) + panX * .52f,
            height * (.9f + .09f * sine(phase + 2.7f)) + panY * .52f, width * .61f, height * .38f, -22, 192);
        drawDust(canvas, width, height);
        updateVisibleStars(width, height);
        for (int i = 0; i < visibleCount; i++) {
            Star star = visible[i].star;
            if (star.opacity > 0f) drawMemory(canvas, star);
        }
    }

    private void drawBackgroundStars(Canvas canvas, int width, int height) {
        for (int i = 0; i < backgroundStars.length; i += 2) {
            float x = backgroundStars[i], y = backgroundStars[i + 1];
            int alpha = 16 + (int) (22 * (1f + sine(phase * 3f + x * 31f + y * 41f)));
            paint.setColor((alpha << 24) | 0xa7bef1);
            canvas.drawCircle(x * width + panX * .16f, y * height + panY * .16f, density * (.36f + x * .48f), paint);
        }
    }

    private void drawDust(Canvas canvas, int width, int height) {
        paint.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < dust.length; i++) {
            Cloud cloud = dust[i];
            int index = 0;
            for (Particle particle : cloud.stars) {
                float ripple = phase + particle.phase;
                cloud.coordinates[index++] = (particle.x + .029f * sine(ripple)) * width + panX * (.34f + i * .1f);
                cloud.coordinates[index++] = (particle.y + .036f * sine(ripple + 1.8f)) * height + panY * (.34f + i * .1f);
            }
            paint.setColor(DUST_COLORS[i]);
            paint.setStrokeWidth(density * (.77f + i * .24f));
            canvas.drawPoints(cloud.coordinates, paint);
        }
    }

    private void updateVisibleStars(int width, int height) {
        boolean cycling = order.size() > MAX_VISIBLE && drift != null;
        for (int i = 0; i < visibleCount; i++) {
            Slot slot = visible[i];
            long time = cycling ? Math.max(0, elapsed - i * STAGGER) : 0;
            long cycle = time / MEMORY_WINDOW;
            long age = time % MEMORY_WINDOW;
            if (slot.cycle != cycle) {
                if (slot.star == focused) focused = null;
                int next = (int) ((cycle * visibleCount + i) % order.size());
                slot.star = new Star(order.get(next));
                slot.cycle = cycle;
            }
            Star star = slot.star;
            star.opacity = cycling ? Math.min(1f, (MEMORY_WINDOW - age) / (float) FADE) : 1f;
            if (cycle > 0) star.opacity = Math.min(star.opacity, age / (float) FADE);
            float flow = phase + star.phase;
            star.x = (star.anchorX + .035f * sine(flow) + .01f * sine(2 * flow + .5f)) * width + panX;
            star.y = (star.anchorY + .049f * sine(flow + 1.7f)) * height + panY;
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

    private void drawMemory(Canvas canvas, Star star) {
        float x = star.x, y = star.y;
        boolean selected = star == focused;
        int color = MEMORY_COLORS[star.style];
        float pulse = sine(phase * 4f + star.phase);
        float radius = density * (selected ? 5.2f : star.size + .48f * pulse);
        float halo = density * (selected ? 21f : 9f + star.size);
        drawGlow(canvas, starHalo, x, y, halo, halo, 0, (int) ((selected ? 220 : 124 + pulse * 31) * star.opacity));
        paint.setColor(color);
        paint.setAlpha((int) (255 * star.opacity));
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
        paint.setAlpha((int) (165 * star.opacity));
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
        float maxDistance = 18 * density;
        float best = maxDistance * maxDistance;
        for (int i = 0; i < visibleCount; i++) {
            Star star = visible[i].star;
            if (star == null || star.opacity < .32f || star.x <= 0 || star.x >= getWidth()
                || star.y <= 0 || star.y >= getHeight()) continue;
            float dx = x - star.x, dy = y - star.y;
            float distance = dx * dx + dy * dy;
            if (distance < best) {
                nearest = star;
                best = distance;
            }
        }
        return nearest;
    }

    private static float sine(float angle) { return (float) Math.sin(angle); }

    private static final class Cloud {
        final List<Particle> stars = new ArrayList<>();
        float[] coordinates;
    }

    private static final class Particle {
        final float x, y, phase;

        Particle(float x, float y, float phase) {
            this.x = x;
            this.y = y;
            this.phase = phase;
        }
    }

    private static final class Slot {
        long cycle = -1;
        Star star;
    }

    private static final class Star {
        final MemoryNebula.Entry entry;
        final float anchorX, anchorY, phase, size;
        final int style;
        float x, y, opacity;

        Star(MemoryNebula.Entry entry) {
            this.entry = entry;
            long seed = 0xcbf29ce484222325L;
            for (int i = 0; i < entry.id.length(); i++) seed = (seed ^ entry.id.charAt(i)) * 0x100000001b3L;
            Random random = new Random(seed);
            anchorX = .32f + .61f * (float) Math.pow(random.nextFloat(), .64f);
            anchorY = .30f + .60f * (float) Math.pow(random.nextFloat(), .76f);
            phase = random.nextFloat() * FULL_CIRCLE;
            size = 2.05f + random.nextFloat() * 1.7f;
            style = random.nextInt(MEMORY_COLORS.length);
        }
    }
}
