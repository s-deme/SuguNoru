package jp.sugunoru.app.ui;

import org.junit.Test;

import static jp.sugunoru.app.ui.UiPalette.*;
import static org.junit.Assert.assertTrue;

public class UiPaletteTest {
    @Test public void allSmallTextPairsMeetWcagAa() {
        assertReadable("本文 / 白", INK, SURFACE);
        assertReadable("補助文字 / 白", MUTED, SURFACE);
        assertReadable("補助文字 / 画面背景", MUTED, CANVAS);
        assertReadable("入力ヒント / 白", HINT, SURFACE);
        assertReadable("白文字 / ブランド", WHITE, BRAND);
        assertReadable("ブランド文字 / 淡色", BRAND_DARK, BRAND_SOFT);
        assertReadable("注意文字 / 淡色", AMBER, AMBER_SOFT);
        assertReadable("削除文字 / 淡色", DANGER, DANGER_SOFT);
    }

    @Test public void controlOutlineIsVisibleAgainstSurface() {
        assertTrue("操作枠 / 白は3:1以上", contrastRatio(CONTROL, SURFACE) >= 3.0);
    }

    private void assertReadable(String label, int foreground, int background) {
        assertTrue(label + "は4.5:1以上", contrastRatio(foreground, background) >= 4.5);
    }
}
