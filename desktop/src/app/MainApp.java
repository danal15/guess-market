package app;

import controller.RootController;
import engine.api.GMEngine;
import engine.core.GuessMarketEngine;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import skin.SkinManager;
import util.Dialogs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
        stage.getIcons().addAll(appIcons());
        stage.setScene(scene);
        stage.setMinWidth(720);
        stage.setMinHeight(520);
        stage.show();
    }

    /**
     * The sizes Windows asks for: the small one goes in the title bar, the
     * middle ones on the task bar, and the large one in Alt-Tab. Handing over a
     * single image leaves the desktop to shrink it, which turns the letters to
     * mush, so each size is drawn in its own right.
     */
    private static final int[] ICON_SIZES = { 16, 24, 32, 48, 64, 128, 256 };

    /**
     * Drawn rather than shipped, so the app has an identity without a binary
     * asset. It carries the same violet to cyan run as the wordmark in the top
     * bar. The window icon is set once at startup and the desktop keeps its own
     * copy, so unlike the rest of the window it does not follow the skin.
     */
    private List<Image> appIcons() {
        List<Image> icons = new ArrayList<>();
        for (int size : ICON_SIZES) {
            icons.add(appIcon(size));
        }
        return icons;
    }

    private Image appIcon(int size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#6c5ce7")),
                new Stop(1, Color.web("#0e9fc4"))));
        // Below about 20 pixels a rounded corner just eats the shape.
        double corner = size < 20 ? 0 : size * 0.22;
        g.fillRoundRect(0, 0, size, size, corner, corner);

        g.setFill(Color.WHITE);
        g.setTextAlign(TextAlignment.CENTER);
        // At the smallest sizes two letters cannot be told apart, so one it is.
        String mark = size < 24 ? "G" : "GM";
        g.setFont(Font.font("Segoe UI", FontWeight.BOLD,
                size * (mark.length() == 1 ? 0.72 : 0.46)));
        g.fillText(mark, size / 2.0, size * (mark.length() == 1 ? 0.76 : 0.66));

        WritableImage drawn = new WritableImage(size, size);
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        canvas.snapshot(params, drawn);
        return asLoadedImage(drawn);
    }

    /**
     * A window icon only reaches the desktop when the image was read from a
     * stream. One produced by drawing on a canvas is accepted by the stage and
     * then quietly dropped, which leaves the window with the runtime's own icon
     * instead of this one. Encoding the same pixels as a PNG and reading them
     * back is what makes it stick.
     */
    private Image asLoadedImage(WritableImage drawn) {
        int width = (int) drawn.getWidth();
        int height = (int) drawn.getHeight();
        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader pixels = drawn.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(buffer, "png", bytes);
            return new Image(new ByteArrayInputStream(bytes.toByteArray()));
        } catch (IOException e) {
            // An icon is not worth failing to start over.
            return drawn;
        }
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
