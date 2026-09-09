package skin;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.net.URL;

/**
 * Swaps the whole look of the application. Each skin is a stylesheet that
 * changes the background, the buttons and the font of every label.
 *
 * Dialogs own a separate scene and never inherit the window's stylesheets, so
 * the active skin is remembered here and applied to each dialog as it opens.
 */
public final class SkinManager {

    public enum Skin {
        DEFAULT("Light", "/css/default.css"),
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

    private static Skin current = Skin.DEFAULT;

    private SkinManager() {
    }

    public static void apply(Scene scene, Skin skin) {
        current = skin;
        scene.getStylesheets().clear();
        String sheet = sheetOf(skin);
        if (sheet != null) {
            scene.getStylesheets().add(sheet);
        }
    }

    /** Gives a dialog the skin the window is currently using. */
    public static void style(DialogPane pane) {
        pane.getStylesheets().clear();
        String sheet = sheetOf(current);
        if (sheet != null) {
            pane.getStylesheets().add(sheet);
        }
    }

    public static Skin getCurrent() {
        return current;
    }

    private static String sheetOf(Skin skin) {
        URL url = SkinManager.class.getResource(skin.resource);
        return url == null ? null : url.toExternalForm();
    }
}
