package com.jingliu.chat;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

final class GlassComposer extends LinearLayout {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final RectF bounds = new RectF();
    private final Shader sheen;
    private final int tint, surface, edge;
    private final float radius, lineWidth;
    private final boolean dark;
    private Shader rim;
    private ValueAnimator pressAnimation;
    private float touchX, touchY, highlight;

    static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
            && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2;
    }

    GlassComposer(Context context, boolean dark, int surface, int edge, float radius) {
        super(context);
        this.dark = dark;
        this.surface = surface;
        this.edge = edge;
        this.radius = radius;
        this.tint = (surface & 0x00ffffff) | (dark ? 0xd1000000 : 0xdc000000);
        lineWidth = getResources().getDisplayMetrics().density;
        sheen = new RadialGradient(0f, 0f, 1f, new int[] {dark ? 0xffa1c9ff : 0xff729ee7, 0x00729ee7},
            null, Shader.TileMode.CLAMP);
        float blur = 15f * lineWidth;
        setBackdropRenderEffect(RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        setClipToOutline(true);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width <= 0 || height <= 0) return;
        bounds.set(0f, 0f, width, height);
        outline.rewind();
        outline.addRoundRect(bounds, radius, radius, Path.Direction.CW);
        bounds.inset(lineWidth * .5f, lineWidth * .5f);
        rim = new LinearGradient(0f, 0f, width * .3f, height,
            new int[] {dark ? 0x7ca9cfff : 0xf9ffffff, edge, dark ? 0x4092b9ef : 0x78b0c8ed},
            new float[] {0f, .54f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (rim == null) return;
        boolean blur = canvas.isHardwareAccelerated();
        canvas.save();
        canvas.clipPath(outline);
        paint.setColor(blur ? tint : surface);
        canvas.drawPath(outline, paint);
        if (highlight > 0f) {
            canvas.save();
            canvas.translate(touchX, touchY);
            canvas.scale(94f * lineWidth, 64f * lineWidth);
            paint.setShader(sheen);
            paint.setAlpha((int) ((dark ? 42f : 33f) * highlight));
            canvas.drawCircle(0f, 0f, 1f, paint);
            paint.setShader(null);
            canvas.restore();
        }
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(lineWidth);
        paint.setAlpha(255);
        if (blur) paint.setShader(rim);
        else paint.setColor(edge);
        canvas.drawRoundRect(bounds, radius - lineWidth * .5f, radius - lineWidth * .5f, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchX = event.getX();
                touchY = event.getY();
                animateHighlight(1f);
                break;
            case MotionEvent.ACTION_MOVE:
                touchX = event.getX();
                touchY = event.getY();
                if (highlight > 0f) invalidate();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                animateHighlight(0f);
                break;
            default: break;
        }
        boolean handled = super.dispatchTouchEvent(event);
        if (!handled && event.getActionMasked() == MotionEvent.ACTION_DOWN) animateHighlight(0f);
        return handled;
    }

    private void animateHighlight(float target) {
        if (pressAnimation != null) pressAnimation.cancel();
        if (highlight == target || !ValueAnimator.areAnimatorsEnabled()) {
            highlight = target;
            invalidate();
            return;
        }
        pressAnimation = ValueAnimator.ofFloat(highlight, target);
        pressAnimation.setInterpolator(new DecelerateInterpolator());
        pressAnimation.setDuration(target > 0f ? 125 : 240);
        pressAnimation.addUpdateListener(animation -> {
            highlight = (float) animation.getAnimatedValue();
            invalidate();
        });
        pressAnimation.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (pressAnimation != null) pressAnimation.cancel();
        pressAnimation = null;
        highlight = 0f;
        super.onDetachedFromWindow();
    }
}
