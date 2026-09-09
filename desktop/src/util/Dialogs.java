package util;

import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import skin.SkinManager;

import java.util.Optional;

/**
 * Every dialog in the application is built here, so they all pick up the
 * active skin and present messages the same way.
 */
public final class Dialogs {

    /** Longer than this and the message gets its own scrollable area. */
    private static final int LONG_MESSAGE = 200;

    private Dialogs() {
    }

    public static void error(String header, String message) {
        Alert alert = base(Alert.AlertType.ERROR, header);
        alert.getDialogPane().setContent(messageNode(message));
        alert.showAndWait();
    }

    public static void info(String header, String message) {
        Alert alert = base(Alert.AlertType.INFORMATION, header);
        alert.getDialogPane().setContent(messageNode(message));
        alert.showAndWait();
    }

    public static boolean confirm(String header, String message) {
        Alert alert = base(Alert.AlertType.CONFIRMATION, header);
        alert.getDialogPane().setContent(messageNode(message));
        Optional<ButtonType> answer = alert.showAndWait();
        return answer.isPresent() && answer.get() == ButtonType.OK;
    }

    private static Alert base(Alert.AlertType type, String header) {
        Alert alert = new Alert(type);
        alert.setTitle("Guess Market");
        alert.setResizable(true);
        DialogChrome.apply(alert, header, badge(type));
        return alert;
    }

    /**
     * The stock alert graphic is drawn by the toolkit in its own blue, which
     * belongs to no skin. This replaces it with a mark the skins can colour.
     */
    private static Label badge(Alert.AlertType type) {
        String glyph;
        String modifier;
        if (type == Alert.AlertType.ERROR || type == Alert.AlertType.WARNING) {
            glyph = "!";
            modifier = "dialog-badge-error";
        } else if (type == Alert.AlertType.CONFIRMATION) {
            glyph = "?";
            modifier = "dialog-badge-confirm";
        } else {
            glyph = "i";
            modifier = "dialog-badge-info";
        }
        Label badge = new Label(glyph);
        badge.getStyleClass().addAll("dialog-badge", modifier);
        badge.setAlignment(Pos.CENTER);
        badge.setMinSize(36, 36);
        badge.setPrefSize(36, 36);
        badge.setMaxSize(36, 36);
        return badge;
    }

    /**
     * Short messages are shown as plain wrapped text; only genuinely long ones
     * (file validation detail) get the weight of a scrollable box.
     */
    private static javafx.scene.Node messageNode(String message) {
        String text = message == null ? "" : message;
        if (text.length() <= LONG_MESSAGE) {
            Label label = new Label(text);
            label.setWrapText(true);
            label.setMaxWidth(420);
            return label;
        }
        TextArea area = new TextArea(text);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(6);
        area.setPrefColumnCount(46);
        return area;
    }
}
