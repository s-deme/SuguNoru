package jp.sugunoru.app.ui;

import android.annotation.TargetApi;
import android.graphics.Insets;
import android.view.View;
import android.view.WindowInsets;

@TargetApi(30)
public final class SystemBarInsetsApplier {
    private SystemBarInsetsApplier() {}

    public static void install(View safeAreaHost) {
        safeAreaHost.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets stableBars = insets.getInsetsIgnoringVisibility(
                    WindowInsets.Type.statusBars()
                            | WindowInsets.Type.navigationBars()
                            | WindowInsets.Type.displayCutout());
            Insets gestures = insets.getInsets(WindowInsets.Type.mandatorySystemGestures());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            SafeAreaInsets.Edges safe = SafeAreaInsets.resolve(
                    toEdges(stableBars), toEdges(gestures), toEdges(ime));
            if (view.getPaddingLeft() != safe.left() || view.getPaddingTop() != safe.top()
                    || view.getPaddingRight() != safe.right()
                    || view.getPaddingBottom() != safe.bottom()) {
                view.setPadding(safe.left(), safe.top(), safe.right(), safe.bottom());
            }
            return insets;
        });
        safeAreaHost.post(safeAreaHost::requestApplyInsets);
    }

    private static SafeAreaInsets.Edges toEdges(Insets value) {
        return new SafeAreaInsets.Edges(value.left, value.top, value.right, value.bottom);
    }
}
