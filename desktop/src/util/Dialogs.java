package util;

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
        alert.setHeaderText(header);
        alert.setResizable(true);
        SkinManager.style(alert.getDialogPane());
        return alert;
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
