package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.net.URL;

public class MainApp extends Application {

    private static final String ROOT_FXML = "/fxml/root.fxml";
    private static final String DEFAULT_CSS = "/css/default.css";

    @Override
    public void start(Stage stage) throws Exception {
        installExceptionHandler();

        URL fxml = MainApp.class.getResource(ROOT_FXML);
        if (fxml == null) {
            throw new IllegalStateException("Could not find " + ROOT_FXML + " on the classpath.");
        }
        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 700);
        URL css = MainApp.class.getResource(DEFAULT_CSS);
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }

        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(700);
        stage.setMinHeight(500);
        stage.show();
    }

    private void installExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Unexpected error");
            alert.setHeaderText("Something went wrong");
            alert.setContentText(String.valueOf(error.getMessage()));
            alert.showAndWait();
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
