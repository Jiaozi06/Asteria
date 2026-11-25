package com.jingliu.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.view.View;
import android.widget.FrameLayout;

/** Replays the transcript's hardware display list behind the composer on Android 13+. */
final class GlassBackdrop extends FrameLayout {
    private static final String LENS =
        "uniform shader backdrop;\n" +
        "uniform float2 center, halfSize;\n" +
        "uniform float radius, band, bend;\n" +
        "half4 main(float2 point) {\n" +
        "    float2 p = point - center;\n" +
        "    float2 q = abs(p) - (halfSize - float2(radius));\n" +
        "    float2 corner = max(q, 0.0);\n" +
        "    float cornerLength = length(corner);\n" +
        "    float distance = cornerLength + min(max(q.x, q.y), 0.0) - radius;\n" +
        "    float2 direction = cornerLength > 0.0 ? corner / cornerLength\n" +
        "        : (q.x > q.y ? float2(1.0, 0.0) : float2(0.0, 1.0));\n" +
        "    float strength = 1.0 - smoothstep(0.0, band, -distance);\n" +
        "    return backdrop.eval(point - sign(p) * direction * (strength * bend));\n" +
        "}\n";

    private final View transcript;
    private final int backgroundColor, padding;
    private final float density;
    private final Rect area = new Rect();
    private final RenderNode sample = new RenderNode("Composer backdrop");
    private final RuntimeShader lens = new RuntimeShader(LENS);
    private final RenderEffect blur;

    GlassBackdrop(Context context, View transcript, int backgroundColor) {
        super(context);
        this.transcript = transcript;
        this.backgroundColor = backgroundColor;
        density = getResources().getDisplayMetrics().density;
        padding = Math.round(26f * density);
        blur = RenderEffect.createBlurEffect(8f * density, 8f * density, Shader.TileMode.CLAMP);
    }

    boolean drawBehind(Canvas target, View composer, float radius) {
        if (transcript.getWidth() == 0 || transcript.getHeight() == 0) return false;

        int width = composer.getWidth();
        int height = composer.getHeight();
        area.set(0, 0, width, height);
        offsetDescendantRectToMyCoords(composer, area);

        int sampleWidth = width + 2 * padding;
        int sampleHeight = height + 2 * padding;
        if (sample.setPosition(0, 0, sampleWidth, sampleHeight)) {
            lens.setFloatUniform("center", sampleWidth * .5f, sampleHeight * .5f);
            lens.setFloatUniform("halfSize", width * .5f, height * .5f);
            lens.setFloatUniform("radius", Math.min(radius, Math.min(width, height) * .5f));
            lens.setFloatUniform("band", 14f * density);
            lens.setFloatUniform("bend", 7f * density);
            RenderEffect refraction = RenderEffect.createRuntimeShaderEffect(lens, "backdrop");
            sample.setRenderEffect(RenderEffect.createChainEffect(refraction, blur));
        }

        RecordingCanvas recording = sample.beginRecording();
        try {
            recording.drawColor(backgroundColor);
            recording.translate(padding - area.left, padding - area.top);
            // Use the parent draw path to retain the live scroll position and child RenderNodes.
            super.drawChild(recording, transcript, getDrawingTime());
        } finally {
            sample.endRecording();
        }

        target.save();
        target.translate(-padding, -padding);
        target.drawRenderNode(sample);
        target.restore();
        return true;
    }
}
