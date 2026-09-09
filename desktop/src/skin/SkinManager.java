package skin;

import javafx.scene.Scene;

import java.net.URL;

/**
 * Bonus: swaps the whole look of the application. Each skin is a stylesheet
 * that changes the background, the buttons and the font of every label, so
 * nothing outside this class needs to know a skin exists.
 */
public final class SkinManager {

    public enum Skin {
        DEFAULT("Default", "/css/default.css"),
        DARK("Dark", "/css/dark.css"),
        HIGH_CONTRAST("High contrast", "/css/high-contrast.css");

        private final String label;
        private final String resource;

        Skin(String label, String resource) {
            this.label = label;
            this.resource = resource;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private SkinManager() {
    }

    public static void apply(Scene scene, Skin skin) {
        scene.getStylesheets().clear();
        URL url = SkinManager.class.getResource(skin.resource);
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }
}
