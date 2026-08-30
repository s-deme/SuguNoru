package jp.sugunoru.app.ui;

public final class UiPalette {
    private UiPalette() {}

    public static final int CANVAS = 0xFFF2F5F2;
    public static final int SURFACE = 0xFFFFFFFF;
    public static final int INK = 0xFF14212B;
    public static final int MUTED = 0xFF44545C;
    public static final int HINT = 0xFF66757A;
    public static final int LINE = 0xFFC4CEC8;
    public static final int CONTROL = 0xFF687B72;
    public static final int BRAND = 0xFF006B4F;
    public static final int BRAND_DARK = 0xFF004C38;
    public static final int BRAND_SOFT = 0xFFD9F2E8;
    public static final int SEGMENT = 0xFFDCE5E0;
    public static final int AMBER_SOFT = 0xFFFFF0C2;
    public static final int AMBER = 0xFF6F4500;
    public static final int DANGER = 0xFFA41111;
    public static final int DANGER_SOFT = 0xFFFDE3E3;
    public static final int WHITE = 0xFFFFFFFF;

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
