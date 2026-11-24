package com.jingliu.chat;

import android.util.TypedValue;
import android.widget.TextView;
import org.json.JSONObject;

final class ChatFont {
    static final int STANDARD = 2;
    private static final int[] PERCENT = {85, 93, 100, 110, 120, 135, 150};

    static int lastLevel() { return PERCENT.length - 1; }

    static int level(JSONObject settings) {
        return Math.max(0, Math.min(lastLevel(), settings.optInt("chatFontLevel", STANDARD)));
    }

    static String label(int level) {
        return level == STANDARD ? "标准" : PERCENT[level] + "%";
    }

    static boolean followsSystem(JSONObject settings) {
        return settings.optBoolean("chatFontSystem", true);
    }

    static float scale(JSONObject settings) {
        return followsSystem(settings) ? 0f : PERCENT[level(settings)] / 100f;
    }

    static void apply(TextView view, int size, float scale) {
        if (scale == 0f) view.setTextSize(size);
        else view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size * scale);
    }
}
