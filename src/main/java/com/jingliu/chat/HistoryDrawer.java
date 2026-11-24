package com.jingliu.chat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;

final class HistoryDrawer extends FrameLayout {
    private final View chat;
    private final View shade;
    private final View panel;
    private final FrameLayout card;
    private final GradientDrawable cardShape;
    private final Runnable onOpening;
    private final Runnable onClosed;
    private final int slop;
    private final float density;
    private float progress, downX, downY, dragStart;
    private boolean revealed, touching, dragging, settleOpen;
    private ValueAnimator animator;
    private VelocityTracker velocity;

    HistoryDrawer(Context context, View chat, View panel, int backdrop, int chatColor, int scrim,
            Runnable onOpening, Runnable onClosed) {
        super(context);
        this.chat = chat;
        this.panel = panel;
        this.onOpening = onOpening;
        this.onClosed = onClosed;
        this.density = getResources().getDisplayMetrics().density;
        this.slop = ViewConfiguration.get(context).getScaledTouchSlop();

        setBackgroundColor(backdrop);
        panel.setClickable(true);
        panel.setFocusableInTouchMode(true);
        addView(panel, new LayoutParams(0, LayoutParams.MATCH_PARENT, Gravity.LEFT));

        card = new FrameLayout(context);
        cardShape = new GradientDrawable();
        cardShape.setColor(chatColor);
        card.setBackground(cardShape);
        card.setClipToOutline(true);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        card.addView(chat, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        shade = new View(context);
        shade.setBackgroundColor(scrim);
        shade.setContentDescription("关闭对话侧栏");
        shade.setOnClickListener(view -> close());
        card.addView(shade, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        position(0f);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        boolean portrait = getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
        panel.getLayoutParams().width = portrait
            ? Math.min(Math.round(500 * density), Math.round(width * .80f))
            : Math.min(Math.round(360 * density), Math.round(width * .67f));
        super.onMeasure(widthSpec, heightSpec);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        position(progress);
    }

    boolean isRevealed() { return revealed; }

    void open() { settle(true, true); }

    void restoreOpen() { settle(true, false); }

    void close() { settle(false, true); }

    private void reveal() {
        if (revealed) return;
        revealed = true;
        chat.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        onOpening.run();
        position(progress);
        panel.requestFocus();
    }

    private void closed() {
        if (!revealed) return;
        revealed = false;
        chat.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        position(0f);
        onClosed.run();
    }

    private void position(float value) {
        progress = Math.max(0f, Math.min(1f, value));
        float width = panel.getLayoutParams().width;
        card.setPivotX(0f);
        card.setPivotY(card.getHeight() * .5f);
        card.setTranslationX(width * progress);
        card.setScaleX(1f - .04f * progress);
        card.setScaleY(1f - .06f * progress);
        card.setElevation(12 * density * progress);
        cardShape.setCornerRadius(24 * density * progress);
        card.invalidateOutline();
        shade.setAlpha(progress);
        shade.setVisibility(revealed ? VISIBLE : GONE);
        // Keep the panel measured even at rest so an edge gesture can reveal it immediately.
        panel.setVisibility(revealed ? VISIBLE : INVISIBLE);
    }

    private void stopAnimation() {
        if (animator == null) return;
        animator.removeAllListeners();
        animator.cancel();
        animator = null;
    }

    private void settle(boolean open, boolean animate) {
        if (animator != null && settleOpen == open) return;
        stopAnimation();
        settleOpen = open;
        if (open) reveal();
        float target = open ? 1f : 0f;
        if (!animate || !ValueAnimator.areAnimatorsEnabled() || getWidth() == 0 || progress == target) {
            position(target);
            if (!open) closed();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(Math.round(115 + 155 * Math.abs(target - progress)));
        animator.setInterpolator(new PathInterpolator(.22f, .75f, .23f, 1f));
        animator.addUpdateListener(value -> position((float) value.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ended) {
                animator = null;
                if (!open) closed();
            }
        });
        animator.start();
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                endTracking();
                downX = event.getX();
                downY = event.getY();
                touching = revealed || downX <= 26 * density;
                if (touching) {
                    velocity = VelocityTracker.obtain();
                    velocity.addMovement(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!touching) break;
                float dx = event.getX() - downX;
                float dy = event.getY() - downY;
                if (Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx)) {
                    endTracking();
                } else if (Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy) * 1.25f
                        && (revealed ? dx < 0 : dx > 0)) {
                    stopAnimation();
                    if (!revealed) reveal();
                    dragging = true;
                    dragStart = progress;
                    // The initial movement crosses touch slop. Further movement drives the panel directly.
                    downX = event.getX() - Math.signum(dx) * Math.min(Math.abs(dx), slop);
                    moveDrawer(event);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                endTracking();
                break;
            default: break;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return super.onTouchEvent(event);
        velocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moveDrawer(event);
                break;
            case MotionEvent.ACTION_UP:
                velocity.computeCurrentVelocity(1000);
                float speed = velocity.getXVelocity();
                settle(Math.abs(speed) >= 520 * density ? speed > 0 : progress >= .48f, true);
                endTracking();
                break;
            case MotionEvent.ACTION_CANCEL:
                settle(progress >= .5f, true);
                endTracking();
                break;
            default: break;
        }
        return true;
    }

    private void moveDrawer(MotionEvent event) {
        int width = panel.getLayoutParams().width;
        if (width > 0) position(dragStart + (event.getX() - downX) / width);
    }

    private void endTracking() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        dragging = touching = false;
    }

    @Override protected void onDetachedFromWindow() {
        stopAnimation();
        endTracking();
        super.onDetachedFromWindow();
    }
}
