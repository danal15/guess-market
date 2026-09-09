package app;

import controller.RootController;
import engine.api.GMEngine;
import engine.core.GuessMarketEngine;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import skin.SkinManager;
import util.Dialogs;

import java.net.URL;

public class MainApp extends Application {

    private static final String ROOT_FXML = "/fxml/root.fxml";

    @Override
    public void start(Stage stage) throws Exception {
        installExceptionHandler();

        URL fxml = MainApp.class.getResource(ROOT_FXML);
        if (fxml == null) {
            throw new IllegalStateException("Could not find " + ROOT_FXML + " on the classpath.");
        }
        FXMLLoader loader = new FXMLLoader(fxml);
        Parent root = loader.load();

        // The only place in the interface that names the concrete engine.
        GMEngine engine = new GuessMarketEngine();
        RootController controller = loader.getController();
        controller.setEngine(engine);

        Scene scene = new Scene(root, 1150, 720);
        SkinManager.apply(scene, SkinManager.Skin.DEFAULT);

        stage.setTitle("Guess Market");
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.show();
    }

    private void installExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            Dialogs.error("Something went wrong", String.valueOf(error.getMessage()));
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
