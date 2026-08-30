package jp.sugunoru.app.ui;

public final class SafeAreaInsets {
    private SafeAreaInsets() {}

    public record Edges(int left, int top, int right, int bottom) {
        public Edges {
            if (left < 0 || top < 0 || right < 0 || bottom < 0) {
                throw new IllegalArgumentException("Insets must not be negative");
            }
        }
    }

    public static Edges resolve(Edges stableBars, Edges mandatoryGestures, Edges ime) {
        return new Edges(
                Math.max(stableBars.left(), Math.max(mandatoryGestures.left(), ime.left())),
                Math.max(stableBars.top(), Math.max(mandatoryGestures.top(), ime.top())),
                Math.max(stableBars.right(), Math.max(mandatoryGestures.right(), ime.right())),
                Math.max(stableBars.bottom(), Math.max(mandatoryGestures.bottom(), ime.bottom()))
        );
    }
}
