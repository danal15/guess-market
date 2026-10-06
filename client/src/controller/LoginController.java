package controller;

import engine.api.dto.UserDTO;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import net.MarketClient;

import java.util.function.Consumer;

/**
 * Asks for a name and hands it to the server. A name already in use comes back
 * as a refusal, which is shown in place so the user can simply try another.
 */
public class LoginController {

    @FXML private TextField nameField;
    @FXML private Button loginButton;
    @FXML private Label messageLabel;
    @FXML private Label serverLabel;

    private MarketClient client;
    private Consumer<UserDTO> onLoggedIn;

    @FXML
    private void initialize() {
        messageLabel.setVisible(false);
        messageLabel.managedProperty().bind(messageLabel.visibleProperty());
    }

    public void start(MarketClient client, Consumer<UserDTO> onLoggedIn) {
        this.client = client;
        this.onLoggedIn = onLoggedIn;
        serverLabel.setText("Server: " + client.getBaseUrl());
        Platform.runLater(nameField::requestFocus);
    }

    @FXML
    private void onLogin() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isEmpty()) {
            show("Enter a name first.");
            return;
        }
        setBusy(true);
        show(null);

        // Off the interface thread: the server may be slow to answer, or missing
        // altogether, and the window must not freeze while we find out.
        Task<UserDTO> login = new Task<>() {
            @Override
            protected UserDTO call() {
                return client.login(name);
            }
        };
        login.setOnSucceeded(event -> {
            setBusy(false);
            onLoggedIn.accept(login.getValue());
        });
        login.setOnFailed(event -> {
            setBusy(false);
            Throwable error = login.getException();
            show(error == null ? "The login did not work." : String.valueOf(error.getMessage()));
            nameField.selectAll();
            nameField.requestFocus();
        });

        Thread thread = new Thread(login, "login");
        thread.setDaemon(true);
        thread.start();
    }

    private void setBusy(boolean busy) {
        loginButton.setDisable(busy);
        nameField.setDisable(busy);
        loginButton.setText(busy ? "Logging in..." : "Log in");
    }

    private void show(String message) {
        messageLabel.setText(message == null ? "" : message);
        messageLabel.setVisible(message != null && !message.isEmpty());
    }
}
