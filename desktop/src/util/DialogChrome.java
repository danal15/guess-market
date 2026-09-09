package util;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import skin.SkinManager;

/**
 * Puts the application's own frame on a dialog instead of the one the desktop
 * draws. The window border is dropped, so the title strip, the close button and
 * dragging all have to be provided here.
 */
public final class DialogChrome {

    private DialogChrome() {
    }

    /**
     * @param headline the line describing what the dialog is for
     * @param badge    the coloured mark for an alert, or null for a form
     */
    public static void apply(Dialog<?> dialog, String headline, Node badge) {
        dialog.initStyle(StageStyle.TRANSPARENT);

        DialogPane pane = dialog.getDialogPane();
        // An Alert fills in its own header text ("Message", "Error"), and the
        // panel it builds for it survives behind a replacement header. Clearing
        // the text first means only our own header is ever built.
        dialog.setHeaderText(null);
        dialog.setGraphic(null);
        pane.setHeader(buildHeader(dialog, headline, badge));
        SkinManager.style(pane);

        // Without a transparent fill behind it the rounded corners would sit on
        // a white rectangle, which is worse than having no rounding at all.
        dialog.setOnShown(event -> {
            Scene scene = pane.getScene();
            if (scene != null) {
                scene.setFill(Color.TRANSPARENT);
            }
        });
    }

    private static Node buildHeader(Dialog<?> dialog, String headline, Node badge) {
        Label appName = new Label("Guess Market");
        appName.getStyleClass().add("dialog-app-name");

        Button close = new Button("✕");
        close.getStyleClass().add("dialog-close");
        close.setFocusTraversable(false);
        close.setOnAction(event -> dismiss(dialog));

        HBox titleBar = new HBox(appName, grow(), close);
        titleBar.getStyleClass().add("dialog-title-bar");
        titleBar.setAlignment(Pos.CENTER_LEFT);
        makeDraggable(titleBar);

        Label text = new Label(headline == null ? "" : headline);
        text.getStyleClass().add("dialog-headline");
        text.setWrapText(true);

        // The badge keeps the right of the headline; the close cross lives in
        // the strip above it, so the only thing that can be pressed up there
        // actually does something.
        HBox headlineRow = new HBox(text, grow());
        headlineRow.getStyleClass().add("dialog-headline-row");
        headlineRow.setAlignment(Pos.CENTER_LEFT);
        if (badge != null) {
            headlineRow.getChildren().add(badge);
        }

        VBox header = new VBox(titleBar, headlineRow);
        header.getStyleClass().add("dialog-header");
        return header;
    }

    private static Region grow() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    /**
     * Closing by the cross means the same as pressing Cancel, so a question
     * that is dismissed is never read as a yes. Every dialog here answers with
     * a ButtonType, which is what makes the cast safe.
     */
    @SuppressWarnings("unchecked")
    private static void dismiss(Dialog<?> dialog) {
        if (dialog.getDialogPane().getButtonTypes().contains(ButtonType.CANCEL)) {
            ((Dialog<ButtonType>) dialog).setResult(ButtonType.CANCEL);
        } else {
            ((Dialog<ButtonType>) dialog).setResult(ButtonType.OK);
        }
        dialog.close();
    }

    /** The strip stands in for the title bar, so it has to move the window. */
    private static void makeDraggable(Node handle) {
        final double[] grab = new double[2];
        handle.setOnMousePressed(event -> {
            grab[0] = event.getSceneX();
            grab[1] = event.getSceneY();
        });
        handle.setOnMouseDragged(event -> {
            Scene scene = handle.getScene();
            if (scene == null || !(scene.getWindow() instanceof Stage stage)) {
                return;
            }
            stage.setX(event.getScreenX() - grab[0]);
            stage.setY(event.getScreenY() - grab[1]);
        });
    }
}
