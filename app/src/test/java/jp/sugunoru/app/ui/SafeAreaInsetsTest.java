package jp.sugunoru.app.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SafeAreaInsetsTest {
    @Test public void reservesStatusNavigationAndLandscapeSideBars() {
        SafeAreaInsets.Edges result = SafeAreaInsets.resolve(
                new SafeAreaInsets.Edges(36, 72, 48, 120),
                new SafeAreaInsets.Edges(24, 0, 32, 80),
                new SafeAreaInsets.Edges(0, 0, 0, 0));

        assertEquals(new SafeAreaInsets.Edges(36, 72, 48, 120), result);
    }

    @Test public void keyboardTakesPriorityOverNavigationBar() {
        SafeAreaInsets.Edges result = SafeAreaInsets.resolve(
                new SafeAreaInsets.Edges(0, 72, 0, 120),
                new SafeAreaInsets.Edges(0, 0, 0, 80),
                new SafeAreaInsets.Edges(0, 0, 0, 840));

        assertEquals(new SafeAreaInsets.Edges(0, 72, 0, 840), result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidNegativeInsets() {
        new SafeAreaInsets.Edges(0, -1, 0, 0);
    }
}
