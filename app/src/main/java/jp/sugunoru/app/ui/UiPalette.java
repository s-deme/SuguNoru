package jp.sugunoru.app.ui;

import android.content.Context;
import android.content.res.Configuration;

public final class UiPalette {
    private UiPalette() {}

    // Semantic roles. They are resolved once when the activity is created so the
    // programmatic View UI follows the device theme without scattering literals.
    public static int CANVAS = 0xFFF4F7F5;
    public static int SURFACE = 0xFFFFFFFF;
    public static int SURFACE_VARIANT = 0xFFE9F0EC;
    public static int INK = 0xFF17211C;
    public static int MUTED = 0xFF4A5D53;
    public static int HINT = 0xFF65786E;
    public static int LINE = 0xFFD3DDD7;
    public static int OUTLINE = 0xFF7A8B82;
    public static int CONTROL = 0xFF6C7F75;
    public static int BRAND = 0xFF006C4F;
    public static int BRAND_DARK = 0xFF004D39;
    public static int BRAND_SOFT = 0xFFD6F3E5;
    public static int BRAND_SURFACE = 0xFFE9F8F1;
    public static int SEGMENT = 0xFFE4ECE8;
    public static int HERO_START = 0xFF00483A;
    public static int HERO_END = 0xFF087A5E;
    public static int AMBER_SOFT = 0xFFFFF0C2;
    public static int AMBER = 0xFF6F4500;
    public static int DANGER = 0xFFA41111;
    public static int DANGER_SOFT = 0xFFFDE3E3;
    public static int INFO = 0xFF175C8E;
    public static int INFO_SOFT = 0xFFDCEEFF;
    public static int WHITE = 0xFFFFFFFF;

    public static void applyFor(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        apply(dark);
    }

    static void apply(boolean dark) {
        if (dark) {
            CANVAS = 0xFF0F1512;
            SURFACE = 0xFF171E1A;
            SURFACE_VARIANT = 0xFF222C26;
            INK = 0xFFEDF5F0;
            MUTED = 0xFFBAC8C0;
            HINT = 0xFFAAB8B0;
            LINE = 0xFF334139;
            OUTLINE = 0xFF718178;
            CONTROL = 0xFF8B9A92;
            BRAND = 0xFF006C4F;
            BRAND_DARK = 0xFFA4EED0;
            BRAND_SOFT = 0xFF1C3B2F;
            BRAND_SURFACE = 0xFF142A22;
            SEGMENT = 0xFF27332D;
            HERO_START = 0xFF173C30;
            HERO_END = 0xFF075F49;
            AMBER_SOFT = 0xFF463817;
            AMBER = 0xFFFFD891;
            DANGER = 0xFFFFB4AB;
            DANGER_SOFT = 0xFF4A2524;
            INFO = 0xFFA9CAFF;
            INFO_SOFT = 0xFF173655;
        } else {
            CANVAS = 0xFFF4F7F5;
            SURFACE = 0xFFFFFFFF;
            SURFACE_VARIANT = 0xFFE9F0EC;
            INK = 0xFF17211C;
            MUTED = 0xFF4A5D53;
            HINT = 0xFF65786E;
            LINE = 0xFFD3DDD7;
            OUTLINE = 0xFF7A8B82;
            CONTROL = 0xFF6C7F75;
            BRAND = 0xFF006C4F;
            BRAND_DARK = 0xFF004D39;
            BRAND_SOFT = 0xFFD6F3E5;
            BRAND_SURFACE = 0xFFE9F8F1;
            SEGMENT = 0xFFE4ECE8;
            HERO_START = 0xFF00483A;
            HERO_END = 0xFF087A5E;
            AMBER_SOFT = 0xFFFFF0C2;
            AMBER = 0xFF6F4500;
            DANGER = 0xFFA41111;
            DANGER_SOFT = 0xFFFDE3E3;
            INFO = 0xFF175C8E;
            INFO_SOFT = 0xFFDCEEFF;
        }
    }

    public static double contrastRatio(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(int color) {
        double red = linear((color >> 16) & 0xff);
        double green = linear((color >> 8) & 0xff);
        double blue = linear(color & 0xff);
        return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
