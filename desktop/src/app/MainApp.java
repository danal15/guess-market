package app;

import controller.RootController;
import engine.api.GMEngine;
import engine.core.GuessMarketEngine;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
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
        stage.getIcons().add(appIcon());
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.show();
    }

    /** Drawn rather than shipped, so the app has an identity without a binary asset. */
    private Image appIcon() {
        int size = 64;
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#2f6fb0"));
        g.fillRoundRect(0, 0, size, size, 14, 14);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD, 30));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText("GM", size / 2.0, size * 0.66);
        WritableImage image = new WritableImage(size, size);
        canvas.snapshot(null, image);
        return image;
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
