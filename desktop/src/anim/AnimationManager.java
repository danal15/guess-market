package anim;

import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * Bonus: short animations that accompany the application. Everything goes
 * through here, so switching them off really does nothing at all rather than
 * merely hiding the effect. Disabled by default.
 */
public final class AnimationManager {

    private static final Duration SHORT = Duration.millis(350);
    private static final Duration MEDIUM = Duration.millis(600);

    private static boolean enabled = false;

    private AnimationManager() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Fades a freshly built details pane in. */
    public static void fadeIn(Node node) {
        if (!enabled || node == null) {
            return;
        }
        FadeTransition fade = new FadeTransition(MEDIUM, node);
        fade.setFromValue(0.0);
        fade.setToValue(1.0);
        fade.play();
    }

    /** Slides a panel in from the side when a tab's content is rebuilt. */
    public static void slideIn(Node node) {
        if (!enabled || node == null) {
            return;
        }
        TranslateTransition slide = new TranslateTransition(MEDIUM, node);
        slide.setFromX(-25);
        slide.setToX(0);
        slide.play();
    }

    /** Gives a control a quick pulse to confirm something happened. */
    public static void pulse(Node node) {
        if (!enabled || node == null) {
            return;
        }
        ScaleTransition scale = new ScaleTransition(SHORT, node);
        scale.setFromX(1.0);
        scale.setFromY(1.0);
        scale.setToX(1.08);
        scale.setToY(1.08);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();
    }
}
