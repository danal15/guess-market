package app;

import controller.ClientRootController;
import controller.LoginController;
import engine.api.dto.UserDTO;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import net.MarketClient;
import skin.SkinManager;
import util.AppIcons;
import util.Dialogs;

import java.net.URL;
import java.util.List;

/**
 * The exercise 3 client. It shows a login screen, and once a name has been
 * accepted by the server it swaps in the market itself.
 *
 * Where the server is comes from the command line if it was given, and otherwise
 * from the address the submitted war deploys to. Nothing has to be configured
 * for the application to find it.
 */
public class ClientApp extends Application {

    private static final String DEFAULT_SERVER = "http://localhost:8080/guess-market";

    private static final String LOGIN_FXML = "/fxml/login.fxml";
    private static final String ROOT_FXML = "/fxml/client-root.fxml";

    private static final int LOGIN_WIDTH = 520;
    private static final int LOGIN_HEIGHT = 340;
    private static final int MARKET_WIDTH = 1200;
    private static final int MARKET_HEIGHT = 760;

    private Stage stage;
    private MarketClient client;

    @Override
    public void start(Stage primaryStage) throws Exception {
        installExceptionHandler();
        this.stage = primaryStage;
        this.client = new MarketClient(serverUrl());

        FXMLLoader loader = new FXMLLoader(resource(LOGIN_FXML));
        Parent root = loader.load();
        LoginController login = loader.getController();
        login.start(client, this::showMarket);

        Scene scene = new Scene(root, LOGIN_WIDTH, LOGIN_HEIGHT);
        SkinManager.apply(scene, SkinManager.Skin.DEFAULT);

        stage.setTitle("Guess Market - log in");
        stage.getIcons().addAll(AppIcons.all());
        stage.setScene(scene);
        stage.setMinWidth(420);
        stage.setMinHeight(300);
        stage.show();
    }

    /** Called once the server has accepted a name. */
    private void showMarket(UserDTO user) {
        try {
            FXMLLoader loader = new FXMLLoader(resource(ROOT_FXML));
            Parent root = loader.load();
            ClientRootController controller = loader.getController();

            Scene scene = new Scene(root, MARKET_WIDTH, MARKET_HEIGHT);
            SkinManager.apply(scene, SkinManager.Skin.DEFAULT);

            stage.setTitle("Guess Market - " + user.getName());
            stage.setScene(scene);
            stage.setMinWidth(760);
            stage.setMinHeight(560);
            stage.centerOnScreen();

            controller.start(client, user);
            // The polling has to stop with the window, or the process outlives it.
            stage.setOnHidden(event -> controller.stop());
        } catch (Exception e) {
            Dialogs.error("The market could not be opened", String.valueOf(e.getMessage()));
        }
    }

    /**
     * @return the base address of the server, with the context path the war
     *         deploys under
     */
    private String serverUrl() {
        List<String> given = getParameters().getRaw();
        for (String argument : given) {
            String trimmed = argument.trim();
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                return trimmed;
            }
        }
        return DEFAULT_SERVER;
    }

    private URL resource(String path) {
        URL url = ClientApp.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Could not find " + path + " on the classpath.");
        }
        return url;
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
